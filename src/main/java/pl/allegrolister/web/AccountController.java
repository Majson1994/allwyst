package pl.allegrolister.web;

import java.util.HashMap;
import java.util.Map;

import jakarta.servlet.http.HttpSession;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import pl.allegrolister.allegro.AllegroApiException;
import pl.allegrolister.allegro.AllegroAuthService;
import pl.allegrolister.allegro.AllegroCatalogService;
import pl.allegrolister.allegro.AllegroEnvironment;
import pl.allegrolister.allegro.AllegroSellerService;
import pl.allegrolister.config.AppProperties;
import pl.allegrolister.domain.AccountSettings;
import pl.allegrolister.domain.AllegroAccount;
import pl.allegrolister.domain.ListingMode;
import pl.allegrolister.domain.SafetyMode;
import pl.allegrolister.repo.AllegroAccountRepository;
import pl.allegrolister.repo.OfferTemplateRepository;
import pl.allegrolister.service.AccountService;
import pl.allegrolister.service.StockSyncService;

/**
 * Konta Allegro: podłączanie przez OAuth, ustawienia ofert i synchronizacji.
 */
@Controller
public class AccountController {

    private static final String SESSION_PENDING = "oauthPending";

    private final AllegroAccountRepository accounts;
    private final AllegroAuthService auth;
    private final AccountService accountService;
    private final AllegroSellerService seller;
    private final AllegroCatalogService catalog;
    private final OfferTemplateRepository templates;
    private final StockSyncService sync;
    private final AppProperties props;

    public AccountController(AllegroAccountRepository accounts, AllegroAuthService auth, AccountService accountService,
                             AllegroSellerService seller, AllegroCatalogService catalog, OfferTemplateRepository templates,
                             StockSyncService sync, AppProperties props) {
        this.accounts = accounts;
        this.auth = auth;
        this.accountService = accountService;
        this.seller = seller;
        this.catalog = catalog;
        this.templates = templates;
        this.sync = sync;
        this.props = props;
    }

    @GetMapping("/accounts")
    public String list(Model model) {
        model.addAttribute("accounts", accounts.findAll());
        model.addAttribute("productionConfigured", props.getAllegro().getProduction().isConfigured());
        model.addAttribute("sandboxConfigured", props.getAllegro().getSandbox().isConfigured());
        model.addAttribute("redirectUri", props.redirectUri());
        return "accounts";
    }

    @GetMapping("/accounts/connect")
    public String connect(@RequestParam AllegroEnvironment env, HttpSession session, RedirectAttributes redirect) {
        if (!auth.credentials(env).isConfigured()) {
            redirect.addFlashAttribute("error", "Brak Client ID / Client Secret dla środowiska " + env.getLabel()
                    + " - uzupełnij je w application.yml (app.allegro." + env.name().toLowerCase() + ").");
            return "redirect:/accounts";
        }
        String verifier = AllegroAuthService.newCodeVerifier();
        String state = AllegroAuthService.newState();
        pending(session).put(state, new String[]{env.name(), verifier});
        return "redirect:" + auth.buildAuthorizeUrl(env, state, AllegroAuthService.codeChallenge(verifier));
    }

    @GetMapping("/allegro/callback")
    public String callback(@RequestParam(required = false) String code, @RequestParam(required = false) String state,
                           @RequestParam(required = false) String error, HttpSession session, RedirectAttributes redirect) {
        if (error != null) {
            redirect.addFlashAttribute("error", "Allegro odrzuciło autoryzację: " + error);
            return "redirect:/accounts";
        }
        String[] data = state == null ? null : pending(session).remove(state);
        if (data == null || code == null) {
            redirect.addFlashAttribute("error", "Nieprawidłowa lub wygasła sesja autoryzacji - spróbuj połączyć konto ponownie.");
            return "redirect:/accounts";
        }
        try {
            AllegroAccount account = accountService.completeOAuth(AllegroEnvironment.valueOf(data[0]), code, data[1]);
            redirect.addFlashAttribute("msg", "Połączono konto " + account.getDisplayName() + ". Uzupełnij domyślne ustawienia ofert.");
            return "redirect:/accounts/" + account.getId() + "/settings";
        } catch (AllegroApiException e) {
            redirect.addFlashAttribute("error", "Nie udało się połączyć konta: " + e.getMessage());
            return "redirect:/accounts";
        }
    }

    @GetMapping("/accounts/{id}/settings")
    public String settings(@PathVariable Long id, Model model) {
        AllegroAccount account = accounts.findById(id).orElseThrow(() -> new IllegalArgumentException("Nie znaleziono konta"));
        model.addAttribute("account", account);
        model.addAttribute("s", account.getSettings());
        model.addAttribute("dicts", seller.dictionaries(account));
        model.addAttribute("templates", templates.findAllByOrderByNameAsc());
        model.addAttribute("listingModes", ListingMode.values());
        model.addAttribute("safetyModes", SafetyMode.values());
        return "account-settings";
    }

    @PostMapping("/accounts/{id}/settings")
    public String saveSettings(@PathVariable Long id, @ModelAttribute AccountSettings form, RedirectAttributes redirect) {
        AllegroAccount account = accounts.findById(id).orElseThrow(() -> new IllegalArgumentException("Nie znaleziono konta"));
        if (form.getPriceMultiplier() == null) {
            form.setPriceMultiplier(java.math.BigDecimal.ONE);
        }
        if (form.getPriceAddition() == null) {
            form.setPriceAddition(java.math.BigDecimal.ZERO);
        }
        if (form.getHandlingTime() == null) {
            form.setHandlingTime("PT24H");
        }
        account.setSettings(form);
        accounts.save(account);
        redirect.addFlashAttribute("msg", "Zapisano ustawienia konta " + account.getDisplayName());
        return "redirect:/accounts/" + id + "/settings";
    }

    @PostMapping("/accounts/{id}/refresh")
    public String refreshDictionaries(@PathVariable Long id, RedirectAttributes redirect) {
        seller.clearCache(id);
        catalog.clearCache();
        redirect.addFlashAttribute("msg", "Odświeżono dane z Allegro (cenniki, warunki, kategorie)");
        return "redirect:/accounts/" + id + "/settings";
    }

    @PostMapping("/accounts/{id}/active")
    public String setActive(@PathVariable Long id, @RequestParam boolean active, RedirectAttributes redirect) {
        AllegroAccount account = accounts.findById(id).orElseThrow(() -> new IllegalArgumentException("Nie znaleziono konta"));
        account.setActive(active);
        accounts.save(account);
        redirect.addFlashAttribute("msg", (active ? "Włączono" : "Wyłączono") + " konto " + account.getDisplayName());
        return "redirect:/accounts";
    }

    @PostMapping("/accounts/{id}/sync")
    public String syncNow(@PathVariable Long id, RedirectAttributes redirect) {
        sync.syncAccountAsync(id);
        redirect.addFlashAttribute("msg", "Uruchomiono synchronizację stanów i cen - wynik pojawi się w Logach.");
        return "redirect:/accounts/" + id + "/settings";
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String[]> pending(HttpSession session) {
        Map<String, String[]> map = (Map<String, String[]>) session.getAttribute(SESSION_PENDING);
        if (map == null) {
            map = new HashMap<>();
            session.setAttribute(SESSION_PENDING, map);
        }
        return map;
    }
}
