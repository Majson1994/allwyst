package pl.allegrolister.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import pl.allegrolister.allegro.AllegroApiException;
import pl.allegrolister.allegro.AllegroImageService;
import pl.allegrolister.allegro.AllegroOfferApi;
import pl.allegrolister.allegro.ApiResponse;
import pl.allegrolister.domain.AllegroAccount;
import pl.allegrolister.domain.AllegroOffer;
import pl.allegrolister.domain.OfferOperation;
import pl.allegrolister.domain.OfferTemplate;
import pl.allegrolister.domain.Product;
import pl.allegrolister.repo.AllegroAccountRepository;
import pl.allegrolister.repo.AllegroOfferRepository;
import pl.allegrolister.repo.OfferTemplateRepository;
import pl.allegrolister.repo.ProductRepository;

/**
 * Zarządzanie ofertami: import z Allegro, powiązania z magazynem i operacje grupowe.
 */
@Service
public class OfferService {

    private static final Logger log = LoggerFactory.getLogger(OfferService.class);
    public static final List<String> ALL_STATUSES = List.of("ACTIVE", "INACTIVE", "ACTIVATING", "ENDED");

    private final AllegroAccountRepository accounts;
    private final AllegroOfferRepository offers;
    private final ProductRepository products;
    private final OfferTemplateRepository templates;
    private final AllegroOfferApi offerApi;
    private final AllegroImageService images;
    private final EventLogService events;

    public OfferService(AllegroAccountRepository accounts, AllegroOfferRepository offers, ProductRepository products,
                        OfferTemplateRepository templates, AllegroOfferApi offerApi, AllegroImageService images,
                        EventLogService events) {
        this.accounts = accounts;
        this.offers = offers;
        this.products = products;
        this.templates = templates;
        this.offerApi = offerApi;
        this.images = images;
        this.events = events;
    }

    public record ImportSummary(int total, int created, int linked) {
    }

    /**
     * Pobiera oferty konta z Allegro i aktualizuje lokalną kopię (status, cena, ilość).
     * Nowe oferty są wiązane z produktami po sygnaturze (external.id == SKU).
     */
    public ImportSummary importOffers(Long accountId, List<String> statuses) {
        AllegroAccount account = account(accountId);
        int offset = 0;
        int limit = 1000;
        int total = 0;
        int created = 0;
        int linked = 0;
        while (true) {
            JsonNode page = offerApi.listOffers(account, offset, limit, statuses);
            int count = 0;
            for (JsonNode o : page.path("offers")) {
                count++;
                total++;
                String offerId = o.path("id").asText();
                AllegroOffer offer = offers.findByAccountIdAndOfferId(accountId, offerId).orElse(null);
                if (offer == null) {
                    offer = new AllegroOffer();
                    offer.setAccount(account);
                    offer.setOfferId(offerId);
                    created++;
                }
                offer.setName(o.path("name").asText());
                offer.setCategoryId(o.path("category").path("id").asText(null));
                offer.setImageUrl(o.path("primaryImage").path("url").asText(null));
                String amount = o.path("sellingMode").path("price").path("amount").asText(null);
                if (amount != null) {
                    offer.setPrice(new BigDecimal(amount));
                }
                offer.setStock(o.path("stock").path("available").asInt(0));
                String status = o.path("publication").path("status").asText(null);
                if ("ACTIVE".equals(status)) {
                    offer.setEndedByApp(false);
                }
                offer.setStatus(status);
                String external = o.path("external").path("id").asText(null);
                offer.setExternalId(external);
                if (offer.getProduct() == null && external != null && !external.isBlank()) {
                    Product p = products.findBySku(external.trim()).orElse(null);
                    if (p != null) {
                        offer.setProduct(p);
                        linked++;
                    }
                }
                offer.setLastSyncAt(Instant.now());
                offers.save(offer);
            }
            int totalCount = page.path("totalCount").asInt(0);
            offset += limit;
            if (count < limit || offset >= totalCount) {
                break;
            }
        }
        return new ImportSummary(total, created, linked);
    }

    @Async("listingExecutor")
    public void bulkAsync(Long accountId, OfferOperation op, List<Long> localIds, String value) {
        try {
            String summary = bulk(accountId, op, localIds, value);
            events.info("OFFERS", accountId, op.getLabel() + ": " + summary);
        } catch (AllegroApiException e) {
            events.error("OFFERS", accountId, op.getLabel() + " - błąd: " + e.getMessage(), e.getRawBody());
        } catch (RuntimeException e) {
            log.error("Operacja {} nie powiodła się", op, e);
            events.error("OFFERS", accountId, op.getLabel() + " - błąd: " + e.getMessage(), null);
        }
    }

    public String bulk(Long accountId, OfferOperation op, List<Long> localIds, String value) {
        AllegroAccount account = account(accountId);
        List<AllegroOffer> selected = offers.findAllById(localIds).stream()
                .filter(o -> o.getAccount().getId().equals(accountId))
                .toList();
        if (selected.isEmpty()) {
            return "nie wybrano ofert";
        }
        return switch (op) {
            case SYNC_STOCK -> syncStock(account, selected);
            case SYNC_PRICE -> syncPrice(account, selected);
            case SET_PRICE -> setPrice(account, selected, requireDecimal(value));
            case CHANGE_PRICE_PERCENT -> changePercent(account, selected, requireDecimal(value));
            case SET_QUANTITY -> setQuantity(account, selected, requireDecimal(value).intValue());
            case END -> publication(account, selected, "END");
            case ACTIVATE -> publication(account, selected, "ACTIVATE");
            case UPDATE_DESCRIPTION -> updateDescriptions(account, selected, null);
            case CHANGE_TEMPLATE -> updateDescriptions(account, selected, Long.valueOf(require(value)));
            case UPDATE_TITLE -> updateTitles(account, selected);
            case CHANGE_SHIPPING_RATE -> changeShippingRate(account, selected, require(value));
            case LINK_BY_SKU -> linkBySku(selected);
            case FORGET -> {
                offers.deleteAll(selected);
                yield "usunięto z pamięci " + selected.size() + " ofert";
            }
        };
    }

    // ---------------- ilość i cena ----------------

    String syncStock(AllegroAccount account, List<AllegroOffer> selected) {
        Map<Integer, List<AllegroOffer>> groups = new LinkedHashMap<>();
        int skipped = 0;
        for (AllegroOffer o : selected) {
            if (o.getProduct() == null || o.getProduct().getQuantity() < 1) {
                skipped++;
                continue;
            }
            groups.computeIfAbsent(o.getProduct().getQuantity(), k -> new ArrayList<>()).add(o);
        }
        List<String> parts = new ArrayList<>();
        for (Map.Entry<Integer, List<AllegroOffer>> g : groups.entrySet()) {
            parts.add(applyQuantity(account, g.getValue(), g.getKey()));
        }
        if (skipped > 0) {
            parts.add("pominięto " + skipped + " (brak powiązania lub stan 0 - użyj 'Zakończ oferty')");
        }
        return String.join("; ", parts);
    }

    String setQuantity(AllegroAccount account, List<AllegroOffer> selected, int qty) {
        if (qty < 1) {
            throw new IllegalArgumentException("Ilość musi być większa od 0 - żeby wyzerować ofertę, zakończ ją");
        }
        return applyQuantity(account, selected, qty);
    }

    public String applyQuantity(AllegroAccount account, List<AllegroOffer> group, int qty) {
        int ok = 0;
        List<String> errors = new ArrayList<>();
        for (List<AllegroOffer> chunk : AllegroOfferApi.chunks(group, AllegroOfferApi.COMMAND_CHUNK)) {
            List<String> ids = chunk.stream().map(AllegroOffer::getOfferId).toList();
            String cmd = offerApi.quantityCommand(account, ids, qty);
            var result = offerApi.awaitCommand(account, AllegroOfferApi.QUANTITY_COMMANDS, cmd);
            errors.addAll(result.errors());
            Set<String> failed = failedIds(result.errors());
            for (AllegroOffer o : chunk) {
                if (!failed.contains(o.getOfferId())) {
                    o.setStock(qty);
                    o.setLastSyncAt(Instant.now());
                    offers.save(o);
                    ok++;
                }
            }
        }
        return "ilość " + qty + ": " + ok + " ok" + errorsSuffix(errors);
    }

    String syncPrice(AllegroAccount account, List<AllegroOffer> selected) {
        Map<BigDecimal, List<AllegroOffer>> groups = new LinkedHashMap<>();
        int skipped = 0;
        for (AllegroOffer o : selected) {
            BigDecimal price = o.getProduct() == null ? null
                    : PriceCalculator.allegroPrice(o.getProduct().getPrice(), account.getSettings());
            if (price == null || price.signum() <= 0) {
                skipped++;
                continue;
            }
            groups.computeIfAbsent(price, k -> new ArrayList<>()).add(o);
        }
        List<String> parts = new ArrayList<>();
        int ok = 0;
        List<String> errors = new ArrayList<>();
        for (Map.Entry<BigDecimal, List<AllegroOffer>> g : groups.entrySet()) {
            int[] counter = applyPrice(account, g.getValue(), g.getKey(), errors);
            ok += counter[0];
        }
        parts.add("zmieniono cenę w " + ok + " ofertach" + errorsSuffix(errors));
        if (skipped > 0) {
            parts.add("pominięto " + skipped + " (brak powiązania lub ceny)");
        }
        return String.join("; ", parts);
    }

    String setPrice(AllegroAccount account, List<AllegroOffer> selected, BigDecimal price) {
        List<String> errors = new ArrayList<>();
        int[] ok = applyPrice(account, selected, price, errors);
        return "cena " + price.toPlainString() + " zł: " + ok[0] + " ok" + errorsSuffix(errors);
    }

    public int[] applyPrice(AllegroAccount account, List<AllegroOffer> group, BigDecimal price, List<String> errors) {
        int ok = 0;
        for (List<AllegroOffer> chunk : AllegroOfferApi.chunks(group, AllegroOfferApi.COMMAND_CHUNK)) {
            List<String> ids = chunk.stream().map(AllegroOffer::getOfferId).toList();
            String cmd = offerApi.priceCommand(account, ids, price);
            var result = offerApi.awaitCommand(account, AllegroOfferApi.PRICE_COMMANDS, cmd);
            errors.addAll(result.errors());
            Set<String> failed = failedIds(result.errors());
            for (AllegroOffer o : chunk) {
                if (!failed.contains(o.getOfferId())) {
                    o.setPrice(price);
                    o.setLastSyncAt(Instant.now());
                    offers.save(o);
                    ok++;
                }
            }
        }
        return new int[]{ok};
    }

    String changePercent(AllegroAccount account, List<AllegroOffer> selected, BigDecimal percent) {
        List<String> ids = selected.stream().map(AllegroOffer::getOfferId).toList();
        List<String> errors = new ArrayList<>();
        int ok = 0;
        for (List<String> chunk : AllegroOfferApi.chunks(ids, AllegroOfferApi.COMMAND_CHUNK)) {
            String cmd = offerApi.pricePercentCommand(account, chunk, percent);
            var result = offerApi.awaitCommand(account, AllegroOfferApi.PRICE_COMMANDS, cmd);
            errors.addAll(result.errors());
            ok += result.success();
        }
        return "zmiana ceny o " + percent.toPlainString() + "%: " + ok + " ok" + errorsSuffix(errors)
                + " (ceny w panelu odświeżą się po imporcie ofert)";
    }

    // ---------------- publikacja ----------------

    public String publication(AllegroAccount account, List<AllegroOffer> selected, String action) {
        List<String> errors = new ArrayList<>();
        int ok = 0;
        for (List<AllegroOffer> chunk : AllegroOfferApi.chunks(selected, AllegroOfferApi.COMMAND_CHUNK)) {
            List<String> ids = chunk.stream().map(AllegroOffer::getOfferId).toList();
            String cmd = offerApi.publicationCommand(account, ids, action);
            var result = offerApi.awaitCommand(account, AllegroOfferApi.PUBLICATION_COMMANDS, cmd);
            errors.addAll(result.errors());
            Set<String> failed = failedIds(result.errors());
            for (AllegroOffer o : chunk) {
                if (!failed.contains(o.getOfferId())) {
                    o.setStatus("END".equals(action) ? "ENDED" : "ACTIVATING");
                    if (!"END".equals(action)) {
                        o.setEndedByApp(false);
                    }
                    offers.save(o);
                    ok++;
                }
            }
        }
        return ("END".equals(action) ? "zakończono " : "aktywowano ") + ok + " ofert" + errorsSuffix(errors);
    }

    // ---------------- opis, tytuł, cennik ----------------

    String updateDescriptions(AllegroAccount account, List<AllegroOffer> selected, Long newTemplateId) {
        int ok = 0;
        List<String> errors = new ArrayList<>();
        OfferTemplate forced = newTemplateId == null ? null : templates.findById(newTemplateId)
                .orElseThrow(() -> new IllegalArgumentException("Nie znaleziono szablonu #" + newTemplateId));
        for (AllegroOffer o : selected) {
            Product p = o.getProduct();
            if (p == null) {
                errors.add(o.getOfferId() + ": brak powiązania z produktem");
                continue;
            }
            Long templateId = forced != null ? forced.getId()
                    : (o.getTemplateId() != null ? o.getTemplateId() : account.getSettings().getDefaultTemplateId());
            OfferTemplate template = forced != null ? forced
                    : (templateId == null ? null : templates.findById(templateId).orElse(null));
            try {
                AllegroImageService.UploadResult upload = images.uploadAll(account, p.getImages());
                TagEngine.Context ctx = new TagEngine.Context(p, o.getName(), o.getPrice(), o.getStock());
                List<Map<String, Object>> sections = template != null
                        ? DescriptionRenderer.render(template, ctx, upload.positional())
                        : DescriptionRenderer.fromProductDescription(p.getDescription());
                Map<String, Object> patch = new LinkedHashMap<>();
                if (!upload.urls().isEmpty()) {
                    patch.put("images", upload.urls());
                }
                if (!sections.isEmpty()) {
                    patch.put("description", Map.of("sections", sections));
                }
                if (patch.isEmpty()) {
                    errors.add(o.getOfferId() + ": brak opisu i zdjęć do wysłania");
                    continue;
                }
                ApiResponse r = offerApi.patch(account, o.getOfferId(), patch);
                if (r.isSuccess()) {
                    o.setTemplateId(templateId);
                    offers.save(o);
                    ok++;
                } else {
                    errors.add(o.getOfferId() + ": " + AllegroApiException.from(r).getMessage());
                }
            } catch (AllegroApiException e) {
                errors.add(o.getOfferId() + ": " + e.getMessage());
            }
        }
        return "zaktualizowano opis w " + ok + " ofertach" + errorsSuffix(errors);
    }

    String updateTitles(AllegroAccount account, List<AllegroOffer> selected) {
        int ok = 0;
        List<String> errors = new ArrayList<>();
        for (AllegroOffer o : selected) {
            if (o.getProduct() == null) {
                errors.add(o.getOfferId() + ": brak powiązania z produktem");
                continue;
            }
            String title = TitleUtils.fit(o.getProduct().getName());
            ApiResponse r = offerApi.patch(account, o.getOfferId(), Map.of("name", title));
            if (r.isSuccess()) {
                o.setName(title);
                offers.save(o);
                ok++;
            } else {
                errors.add(o.getOfferId() + ": " + AllegroApiException.from(r).getMessage());
            }
        }
        return "zmieniono tytuł w " + ok + " ofertach" + errorsSuffix(errors);
    }

    String changeShippingRate(AllegroAccount account, List<AllegroOffer> selected, String rateId) {
        List<String> ids = selected.stream().map(AllegroOffer::getOfferId).toList();
        List<String> errors = new ArrayList<>();
        int ok = 0;
        Map<String, Object> modification = Map.of("delivery", Map.of("shippingRates", Map.of("id", rateId)));
        for (List<String> chunk : AllegroOfferApi.chunks(ids, AllegroOfferApi.COMMAND_CHUNK)) {
            String cmd = offerApi.modificationCommand(account, chunk, modification);
            var result = offerApi.awaitCommand(account, AllegroOfferApi.MODIFICATION_COMMANDS, cmd);
            errors.addAll(result.errors());
            ok += result.success();
        }
        return "zmieniono cennik w " + ok + " ofertach" + errorsSuffix(errors);
    }

    String linkBySku(List<AllegroOffer> selected) {
        int linked = 0;
        for (AllegroOffer o : selected) {
            if (o.getExternalId() == null || o.getExternalId().isBlank()) {
                continue;
            }
            Product p = products.findBySku(o.getExternalId().trim()).orElse(null);
            if (p != null) {
                o.setProduct(p);
                offers.save(o);
                linked++;
            }
        }
        return "powiązano " + linked + " z " + selected.size() + " ofert";
    }

    // ---------------- pomocnicze ----------------

    private AllegroAccount account(Long id) {
        return accounts.findById(id).orElseThrow(() -> new IllegalArgumentException("Nie znaleziono konta #" + id));
    }

    static Set<String> failedIds(List<String> errors) {
        return errors.stream()
                .map(e -> e.contains(":") ? e.substring(0, e.indexOf(':')).trim() : e)
                .collect(Collectors.toSet());
    }

    private static String errorsSuffix(List<String> errors) {
        if (errors.isEmpty()) {
            return "";
        }
        List<String> shown = errors.size() > 10 ? errors.subList(0, 10) : errors;
        return ", błędy (" + errors.size() + "): " + String.join("; ", shown) + (errors.size() > 10 ? "…" : "");
    }

    private static String require(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Ta operacja wymaga podania wartości");
        }
        return value.trim();
    }

    private static BigDecimal requireDecimal(String value) {
        BigDecimal d = ProductTextFormat.parseDecimal(require(value));
        if (d == null) {
            throw new IllegalArgumentException("Nieprawidłowa liczba: " + value);
        }
        return d;
    }
}
