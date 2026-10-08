package pl.allegrolister.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import pl.allegrolister.allegro.AllegroApiException;
import pl.allegrolister.allegro.AllegroOfferApi;
import pl.allegrolister.allegro.ApiResponse;
import pl.allegrolister.config.AppProperties;
import pl.allegrolister.domain.AccountSettings;
import pl.allegrolister.domain.AllegroAccount;
import pl.allegrolister.domain.AllegroOffer;
import pl.allegrolister.domain.Product;
import pl.allegrolister.repo.AllegroAccountRepository;
import pl.allegrolister.repo.AllegroOfferRepository;

/**
 * Moduł synchronizacji stanów i cen (magazyn -> Allegro), uruchamiany cyklicznie:
 * <ul>
 *   <li>ilość w ofercie = stan w magazynie</li>
 *   <li>cena w ofercie = cena z magazynu * mnożnik + dodatek</li>
 *   <li>stan 0 -> zakończ ofertę, stan > 0 -> wznów ofertę zakończoną przez synchronizację</li>
 * </ul>
 */
@Service
public class StockSyncService {

    private static final Logger log = LoggerFactory.getLogger(StockSyncService.class);

    private final AppProperties props;
    private final AllegroAccountRepository accounts;
    private final AllegroOfferRepository offers;
    private final OfferService offerService;
    private final AllegroOfferApi offerApi;
    private final EventLogService events;
    private final Set<Long> running = ConcurrentHashMap.newKeySet();

    public StockSyncService(AppProperties props, AllegroAccountRepository accounts, AllegroOfferRepository offers,
                            OfferService offerService, AllegroOfferApi offerApi, EventLogService events) {
        this.props = props;
        this.accounts = accounts;
        this.offers = offers;
        this.offerService = offerService;
        this.offerApi = offerApi;
        this.events = events;
    }

    @Scheduled(fixedDelayString = "${app.sync.interval-ms:900000}", initialDelayString = "${app.sync.initial-delay-ms:120000}")
    public void scheduledSync() {
        if (!props.getSync().isEnabled()) {
            return;
        }
        for (AllegroAccount account : accounts.findByActiveTrueOrderByIdAsc()) {
            if (account.isTokenValid() && account.getSettings().isAnySyncEnabled()) {
                syncAccount(account.getId());
            }
        }
    }

    @Async("listingExecutor")
    public void syncAccountAsync(Long accountId) {
        syncAccount(accountId);
    }

    public String syncAccount(Long accountId) {
        if (!running.add(accountId)) {
            return "synchronizacja tego konta już trwa";
        }
        try {
            String summary = doSync(accountId);
            events.info("SYNC", accountId, "Synchronizacja: " + summary);
            return summary;
        } catch (AllegroApiException e) {
            events.error("SYNC", accountId, "Synchronizacja nie powiodła się: " + e.getMessage(), e.getRawBody());
            return "błąd: " + e.getMessage();
        } catch (RuntimeException e) {
            log.error("Błąd synchronizacji konta {}", accountId, e);
            events.error("SYNC", accountId, "Synchronizacja nie powiodła się: " + e.getMessage(), null);
            return "błąd: " + e.getMessage();
        } finally {
            running.remove(accountId);
        }
    }

    private String doSync(Long accountId) {
        AllegroAccount account = accounts.findById(accountId).orElseThrow();
        AccountSettings s = account.getSettings();

        // 1. aktualny stan ofert z Allegro (sprzedaż zmienia ilości po stronie Allegro)
        offerService.importOffers(accountId, List.of("ACTIVE", "ENDED"));

        Map<Integer, List<AllegroOffer>> quantityGroups = new LinkedHashMap<>();
        Map<BigDecimal, List<AllegroOffer>> priceGroups = new LinkedHashMap<>();
        List<AllegroOffer> toEnd = new ArrayList<>();
        List<AllegroOffer> toRenew = new ArrayList<>();

        for (AllegroOffer o : offers.findByAccountIdAndProductIsNotNull(accountId)) {
            Product p = o.getProduct();
            int target = Math.max(0, p.getQuantity());
            if ("ACTIVE".equals(o.getStatus())) {
                if (s.isSyncStock()) {
                    if (target == 0) {
                        if (s.isEndWhenZero()) {
                            toEnd.add(o);
                        }
                    } else if (target != o.getStock()) {
                        quantityGroups.computeIfAbsent(target, k -> new ArrayList<>()).add(o);
                    }
                }
                if (s.isSyncPrice()) {
                    BigDecimal price = PriceCalculator.allegroPrice(p.getPrice(), s);
                    if (price != null && price.signum() > 0 && (o.getPrice() == null || price.compareTo(o.getPrice()) != 0)) {
                        priceGroups.computeIfAbsent(price, k -> new ArrayList<>()).add(o);
                    }
                }
            } else if ("ENDED".equals(o.getStatus()) && s.isRenewWhenAvailable() && o.isEndedByApp() && target > 0) {
                toRenew.add(o);
            }
        }

        List<String> parts = new ArrayList<>();
        for (Map.Entry<Integer, List<AllegroOffer>> g : quantityGroups.entrySet()) {
            parts.add(offerService.applyQuantity(account, g.getValue(), g.getKey()));
        }
        if (!priceGroups.isEmpty()) {
            List<String> errors = new ArrayList<>();
            int ok = 0;
            for (Map.Entry<BigDecimal, List<AllegroOffer>> g : priceGroups.entrySet()) {
                ok += offerService.applyPrice(account, g.getValue(), g.getKey(), errors)[0];
            }
            parts.add("ceny: " + ok + " zmienionych" + (errors.isEmpty() ? "" : ", błędy: " + errors.size()));
        }
        if (!toEnd.isEmpty()) {
            parts.add(offerService.publication(account, toEnd, "END") + " (brak stanu)");
            for (AllegroOffer o : toEnd) {
                if ("ENDED".equals(o.getStatus())) {
                    o.setEndedByApp(true);
                    offers.save(o);
                }
            }
        }
        if (!toRenew.isEmpty()) {
            parts.add(renew(account, toRenew));
        }
        return parts.isEmpty() ? "brak zmian" : String.join("; ", parts);
    }

    /** Wznowienie: ustawiamy ilość i status ACTIVE w jednym PATCH. */
    private String renew(AllegroAccount account, List<AllegroOffer> toRenew) {
        int ok = 0;
        List<String> errors = new ArrayList<>();
        for (AllegroOffer o : toRenew) {
            Map<String, Object> patch = new LinkedHashMap<>();
            patch.put("stock", Map.of("available", o.getProduct().getQuantity(), "unit", "UNIT"));
            patch.put("publication", Map.of("status", "ACTIVE"));
            ApiResponse r = offerApi.patch(account, o.getOfferId(), patch);
            if (r.isSuccess()) {
                o.setStatus(r.status() == 202 ? "ACTIVATING" : "ACTIVE");
                o.setStock(o.getProduct().getQuantity());
                o.setEndedByApp(false);
                o.setLastSyncAt(Instant.now());
                offers.save(o);
                ok++;
            } else {
                errors.add(o.getOfferId() + ": " + AllegroApiException.from(r).getMessage());
            }
        }
        return "wznowiono " + ok + " ofert" + (errors.isEmpty() ? "" : ", błędy: " + String.join("; ", errors));
    }

    public boolean isRunning(Long accountId) {
        return running.contains(accountId);
    }
}
