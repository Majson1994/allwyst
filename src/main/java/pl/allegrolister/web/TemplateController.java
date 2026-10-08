package pl.allegrolister.web;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import pl.allegrolister.domain.OfferTemplate;
import pl.allegrolister.domain.Product;
import pl.allegrolister.domain.SectionLayout;
import pl.allegrolister.domain.TemplateSection;
import pl.allegrolister.repo.OfferTemplateRepository;
import pl.allegrolister.repo.ProductRepository;
import pl.allegrolister.service.DescriptionRenderer;
import pl.allegrolister.service.TagEngine;
import pl.allegrolister.web.form.TemplateForm;

/**
 * Szablony opisów ofert (sekcje + tagi).
 */
@Controller
public class TemplateController {

    private final OfferTemplateRepository templates;
    private final ProductRepository products;

    public TemplateController(OfferTemplateRepository templates, ProductRepository products) {
        this.templates = templates;
        this.products = products;
    }

    @GetMapping("/templates")
    public String list(Model model) {
        model.addAttribute("templates", templates.findAllByOrderByNameAsc());
        return "templates";
    }

    @GetMapping("/templates/new")
    public String create(Model model) {
        OfferTemplate t = new OfferTemplate();
        t.setName("Nowy szablon");
        t.getSections().add(new TemplateSection(SectionLayout.IMAGE_TEXT, "<h1>[nazwa]</h1>\n[opis]", "[zdjecie_1]", null));
        return editor(t, model);
    }

    @GetMapping("/templates/{id}")
    public String edit(@PathVariable Long id, Model model) {
        return editor(find(id), model);
    }

    private String editor(OfferTemplate t, Model model) {
        model.addAttribute("template", t);
        model.addAttribute("layouts", SectionLayout.values());
        model.addAttribute("sampleProducts", products.findAll(PageRequest.of(0, 50)).getContent());
        return "template-form";
    }

    @PostMapping("/templates/save")
    public String save(@ModelAttribute TemplateForm form, RedirectAttributes redirect) {
        OfferTemplate t = form.getId() == null ? new OfferTemplate() : find(form.getId());
        t.setName(form.getName() == null ? "Szablon" : form.getName());
        List<TemplateSection> sections = new ArrayList<>();
        for (TemplateSection s : form.getSections()) {
            if (s != null && s.getLayout() != null) {
                sections.add(s);
            }
        }
        t.getSections().clear();
        t.getSections().addAll(sections);
        OfferTemplate saved = templates.save(t);
        redirect.addFlashAttribute("msg", "Zapisano szablon \"" + saved.getName() + "\"");
        return "redirect:/templates/" + saved.getId();
    }

    @PostMapping("/templates/{id}/copy")
    public String copy(@PathVariable Long id, RedirectAttributes redirect) {
        OfferTemplate src = find(id);
        OfferTemplate copy = new OfferTemplate();
        copy.setName(src.getName() + " (kopia)");
        for (TemplateSection s : src.getSections()) {
            copy.getSections().add(new TemplateSection(s.getLayout(), s.getText(), s.getImage1(), s.getImage2()));
        }
        OfferTemplate saved = templates.save(copy);
        redirect.addFlashAttribute("msg", "Skopiowano szablon");
        return "redirect:/templates/" + saved.getId();
    }

    @PostMapping("/templates/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirect) {
        templates.deleteById(id);
        redirect.addFlashAttribute("msg", "Usunięto szablon");
        return "redirect:/templates";
    }

    @GetMapping("/templates/{id}/preview")
    public String preview(@PathVariable Long id, @RequestParam(required = false) Long productId, Model model) {
        OfferTemplate t = find(id);
        Product p = productId != null ? products.findById(productId).orElse(null) : null;
        if (p == null) {
            p = products.findAll(PageRequest.of(0, 1)).stream().findFirst().orElse(null);
        }
        if (p == null) {
            throw new IllegalArgumentException("Dodaj najpierw produkt do magazynu, żeby zobaczyć podgląd");
        }
        var sections = DescriptionRenderer.render(t, new TagEngine.Context(p, p.getName(), p.getPrice(), p.getQuantity()), p.getImages());
        model.addAttribute("template", t);
        model.addAttribute("product", p);
        model.addAttribute("previewHtml", DescriptionRenderer.previewHtml(sections));
        model.addAttribute("sampleProducts", products.findAll(PageRequest.of(0, 50)).getContent());
        return "template-preview";
    }

    private OfferTemplate find(Long id) {
        return templates.findById(id).orElseThrow(() -> new IllegalArgumentException("Nie znaleziono szablonu"));
    }
}
