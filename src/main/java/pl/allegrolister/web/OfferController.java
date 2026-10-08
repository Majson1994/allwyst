package pl.allegrolister.web;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import pl.allegrolister.allegro.AllegroSellerService;
import pl.allegrolister.domain.AllegroAccount;
import pl.allegrolister.domain.AllegroOffer;
import pl.allegrolister.domain.ListingJob;
import pl.allegrolister.domain.OfferOperation;
import pl.allegrolister.repo.AllegroAccountRepository;
import pl.allegrolister.repo.AllegroOfferRepository;
import pl.allegrolister.repo.OfferTemplateRepository;
import pl.allegrolister.service.ListingService;
import pl.allegrolister.service.OfferService;
import pl.allegrolister.service.StockSyncService;

/**
 * Zarządzanie ofertami (lista, import, operacje grupowe, wystaw na innym koncie).
 */
@Controller
public class OfferController {

    private final AllegroAccountRepository accounts;
    private final AllegroOfferRepository offers;
    private final OfferTemplateRepository templates;
    private final OfferService offerService;
    private final ListingService listingService;
    private final StockSyncService sync;
    private final AllegroSellerService seller;

    public OfferController(AllegroAccountRepository accounts, AllegroOfferRepository offers, OfferTemplateRepository templates,
                           OfferService offerService, ListingService listingService, StockSyncService sync,
                           AllegroSellerService seller) {
        this.accounts = accounts;
        this.offers = offers;
        this.templates = templates;
        this.offerService = offerService;
        this.listingService = listingService;
        this.sync = sync;
        this.seller = seller;
    }

    @GetMapping("/offers")
    public String list(@RequestParam(required = false) Long accountId, @RequestParam(defaultValue = "") String status,
                       @RequestParam(defaultValue = "") String q, @RequestParam(defaultValue = "0") int page, Model model) {
        List<AllegroAccount> active = accounts.findByActiveTrueOrderByIdAsc();
        if (active.isEmpty()) {
            model.addAttribute("noAccounts", true);
            return "offers";
        }
        AllegroAccount account = accountId == null ? active.get(0)
                : accounts.findById(accountId).orElse(active.get(0));
        Page<AllegroOffer> result = offers.search(account.getId(), status.trim(), q.trim(),
                PageRequest.of(Math.max(page, 0), 100, Sort.by(Sort.Direction.DESC, "id")));
        model.addAttribute("account", account);
        model.addAttribute("page", result);
        model.addAttribute("status", status);
        model.addAttribute("q", q);
        model.addAttribute("operations", OfferOperation.values());
        model.addAttribute("templates", templates.findAllByOrderByNameAsc());
        model.addAttribute("dicts", seller.dictionaries(account));
        model.addAttribute("syncRunning", sync.isRunning(account.getId()));
        return "offers";
    }

    @PostMapping("/offers/import")
    public String importOffers(@RequestParam Long accountId, RedirectAttributes redirect) {
        var summary = offerService.importOffers(accountId, OfferService.ALL_STATUSES);
        redirect.addFlashAttribute("msg", "Zaimportowano " + summary.total() + " ofert (nowych: " + summary.created()
                + ", powiązanych z magazynem: " + summary.linked() + ")");
        return "redirect:/offers?accountId=" + accountId;
    }

    @PostMapping("/offers/bulk")
    public String bulk(@RequestParam Long accountId, @RequestParam OfferOperation operation,
                       @RequestParam(required = false) List<Long> ids, @RequestParam(required = false) String value,
                       @RequestParam(required = false) String shippingRateId, @RequestParam(required = false) Long templateId,
                       RedirectAttributes redirect) {
        if (ids == null || ids.isEmpty()) {
            throw new IllegalArgumentException("Zaznacz oferty");
        }
        String v = switch (operation) {
            case CHANGE_SHIPPING_RATE -> shippingRateId;
            case CHANGE_TEMPLATE -> templateId == null ? null : templateId.toString();
            default -> value;
        };
        if (operation.isNeedsValue() && (v == null || v.isBlank())) {
            throw new IllegalArgumentException("Operacja \"" + operation.getLabel() + "\" wymaga podania wartości");
        }
        if (operation == OfferOperation.FORGET || operation == OfferOperation.LINK_BY_SKU) {
            String result = offerService.bulk(accountId, operation, ids, v);
            redirect.addFlashAttribute("msg", operation.getLabel() + ": " + result);
        } else {
            offerService.bulkAsync(accountId, operation, ids, v);
            redirect.addFlashAttribute("msg", "Zlecono: " + operation.getLabel() + " (" + ids.size()
                    + " ofert). Wynik pojawi się w Logach za chwilę.");
        }
        return "redirect:/offers?accountId=" + accountId;
    }

    /** Wystaw na innym koncie - nowy formularz wystawiania z produktami powiązanymi z ofertami. */
    @PostMapping("/offers/copy")
    public String copyToAccount(@RequestParam Long accountId, @RequestParam Long targetAccountId,
                                @RequestParam(required = false) List<Long> ids, RedirectAttributes redirect) {
        if (ids == null || ids.isEmpty()) {
            throw new IllegalArgumentException("Zaznacz oferty");
        }
        Set<Long> productIds = new LinkedHashSet<>();
        int withoutProduct = 0;
        for (AllegroOffer o : offers.findAllById(ids)) {
            if (o.getProduct() != null) {
                productIds.add(o.getProduct().getId());
            } else {
                withoutProduct++;
            }
        }
        if (productIds.isEmpty()) {
            throw new IllegalArgumentException("Zaznaczone oferty nie są powiązane z produktami w magazynie");
        }
        ListingJob job = listingService.createJob(targetAccountId, new ArrayList<>(productIds));
        redirect.addFlashAttribute("msg", "Utworzono formularz wystawiania na drugim koncie (" + productIds.size() + " produktów"
                + (withoutProduct > 0 ? ", pominięto " + withoutProduct + " ofert bez powiązania" : "") + ")");
        return "redirect:/listing/" + job.getId();
    }

    @PostMapping("/offers/sync")
    public String syncNow(@RequestParam Long accountId, RedirectAttributes redirect) {
        sync.syncAccountAsync(accountId);
        redirect.addFlashAttribute("msg", "Uruchomiono synchronizację stanów i cen - wynik w Logach.");
        return "redirect:/offers?accountId=" + accountId;
    }
}
