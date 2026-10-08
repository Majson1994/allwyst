package pl.allegrolister.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import pl.allegrolister.domain.CategoryMapping;
import pl.allegrolister.domain.MatchField;
import pl.allegrolister.domain.ParameterRule;
import pl.allegrolister.domain.RuleType;
import pl.allegrolister.repo.CategoryMappingRepository;
import pl.allegrolister.repo.OfferTemplateRepository;
import pl.allegrolister.repo.ParameterRuleRepository;
import pl.allegrolister.repo.ProductRepository;

/**
 * Powiązania kategorii (sklep -> Allegro) i reguły parametrów.
 */
@Controller
public class MappingController {

    private final CategoryMappingRepository mappings;
    private final ParameterRuleRepository rules;
    private final ProductRepository products;
    private final OfferTemplateRepository templates;

    public MappingController(CategoryMappingRepository mappings, ParameterRuleRepository rules,
                             ProductRepository products, OfferTemplateRepository templates) {
        this.mappings = mappings;
        this.rules = rules;
        this.products = products;
        this.templates = templates;
    }

    @GetMapping("/mappings")
    public String page(@RequestParam(required = false) Long editRule, @RequestParam(required = false) Long editMapping, Model model) {
        model.addAttribute("mappings", mappings.findAllByOrderByShopCategoryAsc());
        model.addAttribute("rules", rules.findAllByOrderByPriorityAscIdAsc());
        model.addAttribute("shopCategories", products.findDistinctShopCategories());
        model.addAttribute("templates", templates.findAllByOrderByNameAsc());
        model.addAttribute("ruleTypes", RuleType.values());
        model.addAttribute("matchFields", MatchField.values());
        model.addAttribute("rule", editRule == null ? new ParameterRule() : rules.findById(editRule).orElse(new ParameterRule()));
        model.addAttribute("mapping", editMapping == null ? new CategoryMapping()
                : mappings.findById(editMapping).orElse(new CategoryMapping()));
        return "mappings";
    }

    @PostMapping("/mappings/category/save")
    public String saveMapping(@ModelAttribute CategoryMapping form, RedirectAttributes redirect) {
        if (form.getShopCategory() == null || form.getAllegroCategoryId() == null) {
            throw new IllegalArgumentException("Podaj kategorię sklepu i kategorię Allegro");
        }
        CategoryMapping m = mappings.findByShopCategory(form.getShopCategory()).orElse(null);
        if (form.getId() != null) {
            m = mappings.findById(form.getId()).orElse(m);
        }
        if (m == null) {
            m = new CategoryMapping();
        }
        m.setShopCategory(form.getShopCategory());
        m.setAllegroCategoryId(form.getAllegroCategoryId());
        m.setAllegroCategoryName(form.getAllegroCategoryName());
        m.setTemplateId(form.getTemplateId());
        mappings.save(m);
        redirect.addFlashAttribute("msg", "Zapisano powiązanie kategorii \"" + m.getShopCategory() + "\"");
        return "redirect:/mappings";
    }

    @PostMapping("/mappings/category/{id}/delete")
    public String deleteMapping(@PathVariable Long id, RedirectAttributes redirect) {
        mappings.deleteById(id);
        redirect.addFlashAttribute("msg", "Usunięto powiązanie");
        return "redirect:/mappings";
    }

    @PostMapping("/mappings/rule/save")
    public String saveRule(@ModelAttribute ParameterRule form, RedirectAttributes redirect) {
        if (form.getParameterName() == null && form.getParameterId() == null) {
            throw new IllegalArgumentException("Podaj nazwę albo ID parametru Allegro");
        }
        if (form.getRuleType() == RuleType.FROM_FEATURE && form.getFeatureName() == null) {
            throw new IllegalArgumentException("Podaj nazwę cechy produktu (albo pole specjalne, np. @producent)");
        }
        if (form.getRuleType() != RuleType.FROM_FEATURE && form.getValue() == null) {
            throw new IllegalArgumentException("Podaj wartość parametru");
        }
        if (form.getRuleType() == RuleType.CONSTANT_IF_CONTAINS && form.getPhrase() == null) {
            throw new IllegalArgumentException("Podaj frazę do wyszukania");
        }
        ParameterRule saved = rules.save(form);
        redirect.addFlashAttribute("msg", "Zapisano regułę #" + saved.getId());
        return "redirect:/mappings#rules";
    }

    @PostMapping("/mappings/rule/{id}/toggle")
    public String toggleRule(@PathVariable Long id) {
        rules.findById(id).ifPresent(r -> {
            r.setEnabled(!r.isEnabled());
            rules.save(r);
        });
        return "redirect:/mappings#rules";
    }

    @PostMapping("/mappings/rule/{id}/delete")
    public String deleteRule(@PathVariable Long id, RedirectAttributes redirect) {
        rules.deleteById(id);
        redirect.addFlashAttribute("msg", "Usunięto regułę");
        return "redirect:/mappings#rules";
    }
}
