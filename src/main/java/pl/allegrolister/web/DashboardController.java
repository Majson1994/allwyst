package pl.allegrolister.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import pl.allegrolister.domain.ListingStatus;
import pl.allegrolister.repo.AllegroAccountRepository;
import pl.allegrolister.repo.AllegroOfferRepository;
import pl.allegrolister.repo.EventLogRepository;
import pl.allegrolister.repo.ListingItemRepository;
import pl.allegrolister.repo.ProductRepository;

@Controller
public class DashboardController {

    private final AllegroAccountRepository accounts;
    private final ProductRepository products;
    private final AllegroOfferRepository offers;
    private final ListingItemRepository items;
    private final EventLogRepository logs;

    public DashboardController(AllegroAccountRepository accounts, ProductRepository products, AllegroOfferRepository offers,
                               ListingItemRepository items, EventLogRepository logs) {
        this.accounts = accounts;
        this.products = products;
        this.offers = offers;
        this.items = items;
        this.logs = logs;
    }

    @GetMapping("/")
    public String dashboard(Model model) {
        model.addAttribute("accountCount", accounts.count());
        model.addAttribute("productCount", products.count());
        model.addAttribute("activeOffers", offers.countByStatus("ACTIVE"));
        model.addAttribute("pendingItems", items.countByStatus(ListingStatus.PENDING) + items.countByStatus(ListingStatus.QUEUED)
                + items.countByStatus(ListingStatus.PROCESSING));
        model.addAttribute("errorItems", items.countByStatus(ListingStatus.ERROR));
        model.addAttribute("recentLogs", logs.findTop10ByOrderByIdDesc());
        return "dashboard";
    }
}
