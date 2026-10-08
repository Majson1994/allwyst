package pl.allegrolister.web;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import pl.allegrolister.allegro.AllegroApiException;
import pl.allegrolister.allegro.AllegroSellerService;
import pl.allegrolister.domain.ListingItem;
import pl.allegrolister.domain.ListingJob;
import pl.allegrolister.domain.ListingMode;
import pl.allegrolister.domain.ListingStatus;
import pl.allegrolister.repo.ListingItemRepository;
import pl.allegrolister.repo.ListingJobRepository;
import pl.allegrolister.repo.OfferTemplateRepository;
import pl.allegrolister.service.ListingService;
import pl.allegrolister.web.form.ListingForm;

/**
 * Formularz wystawiania (jak "Wystaw zaznaczone (formularz)" w BaseLinkerze).
 */
@Controller
public class ListingController {

    private final ListingService listingService;
    private final ListingJobRepository jobs;
    private final ListingItemRepository items;
    private final OfferTemplateRepository templates;
    private final AllegroSellerService seller;

    public ListingController(ListingService listingService, ListingJobRepository jobs, ListingItemRepository items,
                             OfferTemplateRepository templates, AllegroSellerService seller) {
        this.listingService = listingService;
        this.jobs = jobs;
        this.items = items;
        this.templates = templates;
        this.seller = seller;
    }

    @GetMapping("/listing")
    public String jobs(Model model) {
        model.addAttribute("jobs", jobs.findTop50ByOrderByIdDesc());
        return "listing-jobs";
    }

    @GetMapping("/listing/{id}")
    public String form(@PathVariable Long id, Model model) {
        ListingJob job = listingService.job(id);
        model.addAttribute("job", job);
        model.addAttribute("items", items.findByJobIdOrderByIdAsc(id));
        model.addAttribute("dicts", seller.dictionaries(job.getAccount()));
        model.addAttribute("templates", templates.findAllByOrderByNameAsc());
        model.addAttribute("listingModes", ListingMode.values());
        Map<ListingStatus, Long> counts = new LinkedHashMap<>();
        for (ListingStatus s : ListingStatus.values()) {
            long c = job.countByStatus(s);
            if (c > 0) {
                counts.put(s, c);
            }
        }
        model.addAttribute("counts", counts);
        return "listing-form";
    }

    /**
     * Jeden endpoint dla wszystkich przycisków formularza: najpierw zapis pól, potem wybrana akcja.
     */
    @PostMapping("/listing/{id}")
    public String submit(@PathVariable Long id, @ModelAttribute ListingForm form,
                         @RequestParam(defaultValue = "save") String action,
                         @RequestParam(required = false) List<Long> selectedIds,
                         @RequestParam(required = false) String bulkField,
                         @RequestParam(required = false) String bulkValue,
                         @RequestParam(required = false) String bulkLabel,
                         RedirectAttributes redirect) {
        listingService.saveForm(id, form);
        List<Long> selected = selectedIds == null ? new ArrayList<>() : selectedIds;
        String scope = selected.isEmpty() ? "wszystkich" : "zaznaczonych (" + selected.size() + ")";
        switch (action) {
            case "save" -> redirect.addFlashAttribute("msg", "Zapisano zmiany");
            case "bulk" -> {
                if (bulkField == null) {
                    throw new IllegalArgumentException("Wybierz operację grupową");
                }
                int n = listingService.applyBulk(id, selected, bulkField, bulkValue, bulkLabel);
                redirect.addFlashAttribute("msg", "Zmieniono " + n + " pozycji (" + scope + ")");
            }
            case "match" -> {
                int n = listingService.matchCategories(id, selected.isEmpty());
                redirect.addFlashAttribute("msg", "Dopasowano kategorie dla " + n + " pozycji");
            }
            case "validate" -> {
                int problems = listingService.validate(id);
                if (problems == 0) {
                    redirect.addFlashAttribute("msg", "Wszystko gotowe do wystawienia");
                } else {
                    redirect.addFlashAttribute("error", "Pozycje z uwagami: " + problems + " - szczegóły przy produktach");
                }
            }
            case "publish" -> {
                int n = listingService.publish(id, selected);
                redirect.addFlashAttribute("msg", "Wysłano do wystawienia: " + n + " (" + scope + "). Statusy odświeżają się automatycznie.");
            }
            default -> throw new IllegalArgumentException("Nieznana akcja: " + action);
        }
        return "redirect:/listing/" + id;
    }

    @PostMapping("/listing/item/{itemId}/remove")
    public String removeItem(@PathVariable Long itemId, RedirectAttributes redirect) {
        ListingItem item = items.findById(itemId).orElseThrow(() -> new IllegalArgumentException("Nie znaleziono pozycji"));
        Long jobId = item.getJob().getId();
        listingService.removeItem(itemId);
        redirect.addFlashAttribute("msg", "Usunięto pozycję z formularza");
        return "redirect:/listing/" + jobId;
    }

    @GetMapping("/listing/item/{itemId}/parameters")
    public String parameters(@PathVariable Long itemId, Model model) {
        ListingItem item = items.findById(itemId).orElseThrow(() -> new IllegalArgumentException("Nie znaleziono pozycji"));
        if (item.getCategoryId() == null) {
            throw new IllegalArgumentException("Najpierw wybierz kategorię Allegro dla tej pozycji");
        }
        model.addAttribute("item", item);
        model.addAttribute("data", listingService.parametersFor(item));
        model.addAttribute("overrides", listingService.readOverrides(item));
        return "listing-params";
    }

    @PostMapping("/listing/item/{itemId}/parameters")
    public String saveParameters(@PathVariable Long itemId, @RequestParam MultiValueMap<String, String> params,
                                 RedirectAttributes redirect) {
        Map<String, List<String>> overrides = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> e : params.entrySet()) {
            String key = e.getKey();
            if (!key.startsWith("p_")) {
                continue;
            }
            if (key.endsWith("_from") || key.endsWith("_to")) {
                String paramId = key.substring(2, key.lastIndexOf('_'));
                List<String> range = overrides.computeIfAbsent(paramId, k -> new ArrayList<>(List.of("", "")));
                range.set(key.endsWith("_from") ? 0 : 1, e.getValue().isEmpty() ? "" : e.getValue().get(0));
            } else {
                overrides.put(key.substring(2), e.getValue());
            }
        }
        listingService.saveOverrides(itemId, overrides);
        ListingItem item = items.findById(itemId).orElseThrow();
        redirect.addFlashAttribute("msg", "Zapisano parametry dla: " + item.getProduct().getName());
        if (params.containsKey("stay")) {
            return "redirect:/listing/item/" + itemId + "/parameters";
        }
        return "redirect:/listing/" + item.getJob().getId();
    }

    @GetMapping("/listing/item/{itemId}/preview")
    public String preview(@PathVariable Long itemId, Model model) {
        ListingItem item = items.findById(itemId).orElseThrow(() -> new IllegalArgumentException("Nie znaleziono pozycji"));
        model.addAttribute("item", item);
        try {
            model.addAttribute("previewHtml", listingService.previewDescription(item));
        } catch (AllegroApiException | IllegalArgumentException e) {
            model.addAttribute("previewHtml", "<p>Nie udało się zbudować podglądu: " + e.getMessage() + "</p>");
        }
        return "listing-preview";
    }
}
