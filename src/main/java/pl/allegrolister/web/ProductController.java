package pl.allegrolister.web;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import pl.allegrolister.domain.AllegroOffer;
import pl.allegrolister.domain.ListingJob;
import pl.allegrolister.domain.Product;
import pl.allegrolister.repo.AllegroOfferRepository;
import pl.allegrolister.repo.ListingItemRepository;
import pl.allegrolister.repo.ProductRepository;
import pl.allegrolister.service.ListingService;
import pl.allegrolister.service.ProductImportService;
import pl.allegrolister.service.ProductTextFormat;

/**
 * Magazyn produktów: lista, edycja, import CSV i start wystawiania zaznaczonych produktów.
 */
@Controller
public class ProductController {

    private final ProductRepository products;
    private final ListingItemRepository listingItems;
    private final AllegroOfferRepository offers;
    private final ProductImportService importService;
    private final ListingService listingService;

    public ProductController(ProductRepository products, ListingItemRepository listingItems, AllegroOfferRepository offers,
                             ProductImportService importService, ListingService listingService) {
        this.products = products;
        this.listingItems = listingItems;
        this.offers = offers;
        this.importService = importService;
        this.listingService = listingService;
    }

    @GetMapping("/products")
    public String list(@RequestParam(defaultValue = "") String q, @RequestParam(defaultValue = "") String cat,
                       @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size, Model model) {
        int pageSize = Math.min(Math.max(size, 10), 500);
        Page<Product> result = products.search(q.trim(), cat.trim(), PageRequest.of(Math.max(page, 0), pageSize, Sort.by("name")));
        model.addAttribute("page", result);
        model.addAttribute("q", q);
        model.addAttribute("cat", cat);
        model.addAttribute("size", pageSize);
        model.addAttribute("categories", products.findDistinctShopCategories());
        return "products";
    }

    @GetMapping("/products/new")
    public String create(Model model) {
        Product p = new Product();
        model.addAttribute("product", p);
        model.addAttribute("imagesText", "");
        model.addAttribute("featuresText", "");
        return "product-form";
    }

    @GetMapping("/products/{id}")
    public String edit(@PathVariable Long id, Model model) {
        Product p = products.findById(id).orElseThrow(() -> new IllegalArgumentException("Nie znaleziono produktu"));
        model.addAttribute("product", p);
        model.addAttribute("imagesText", ProductTextFormat.formatImages(p.getImages()));
        model.addAttribute("featuresText", ProductTextFormat.formatFeatures(p.getFeatures()));
        return "product-form";
    }

    @PostMapping("/products/save")
    public String save(@RequestParam Map<String, String> form, RedirectAttributes redirect) {
        String idText = trim(form.get("id"));
        Product p = idText == null ? new Product()
                : products.findById(Long.valueOf(idText)).orElseThrow(() -> new IllegalArgumentException("Nie znaleziono produktu"));
        String sku = trim(form.get("sku"));
        String name = trim(form.get("name"));
        if (sku == null || name == null) {
            throw new IllegalArgumentException("SKU i nazwa są wymagane");
        }
        products.findBySku(sku).ifPresent(other -> {
            if (!other.getId().equals(p.getId())) {
                throw new IllegalArgumentException("Produkt z SKU " + sku + " już istnieje");
            }
        });
        p.setSku(sku);
        p.setName(name);
        p.setEan(trim(form.get("ean")));
        p.setManufacturer(trim(form.get("manufacturer")));
        p.setPrice(ProductTextFormat.parseDecimal(form.get("price")));
        p.setQuantity(Math.max(0, ProductTextFormat.parseInt(form.get("quantity"), 0)));
        var vat = ProductTextFormat.parseDecimal(form.get("vatRate"));
        p.setVatRate(vat == null ? null : vat.intValue());
        p.setWeight(ProductTextFormat.parseDecimal(form.get("weight")));
        p.setShopCategory(trim(form.get("shopCategory")));
        p.setAllegroCategoryId(trim(form.get("allegroCategoryId")));
        p.setDescription(form.get("description"));
        p.setSafetyInfo(trim(form.get("safetyInfo")));
        p.getImages().clear();
        p.getImages().addAll(ProductTextFormat.parseImages(form.get("imagesText")));
        p.getFeatures().clear();
        p.getFeatures().addAll(ProductTextFormat.parseFeatures(form.get("featuresText")));
        Product saved = products.save(p);
        redirect.addFlashAttribute("msg", "Zapisano produkt " + saved.getSku());
        return "redirect:/products/" + saved.getId();
    }

    @PostMapping("/products/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirect) {
        if (listingItems.existsByProductId(id)) {
            throw new IllegalArgumentException("Produkt jest użyty w formularzu wystawiania - najpierw usuń go z formularza.");
        }
        for (AllegroOffer o : offers.findByProductId(id)) {
            o.setProduct(null);
            offers.save(o);
        }
        products.deleteById(id);
        redirect.addFlashAttribute("msg", "Usunięto produkt");
        return "redirect:/products";
    }

    @PostMapping("/products/import")
    public String importCsv(@RequestParam("file") MultipartFile file, RedirectAttributes redirect) throws IOException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Wybierz plik CSV");
        }
        var result = importService.importCsv(new String(file.getBytes(), StandardCharsets.UTF_8));
        String msg = "Import: dodano " + result.created() + ", zaktualizowano " + result.updated();
        if (!result.errors().isEmpty()) {
            redirect.addFlashAttribute("error", String.join(" | ", result.errors().subList(0, Math.min(20, result.errors().size()))));
        }
        redirect.addFlashAttribute("msg", msg);
        return "redirect:/products";
    }

    @GetMapping("/products/sample.csv")
    public ResponseEntity<byte[]> sampleCsv() throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/sample-products.csv")) {
            byte[] bytes = in == null ? new byte[0] : in.readAllBytes();
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"produkty-przyklad.csv\"")
                    .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                    .body(bytes);
        }
    }

    /** Wystaw zaznaczone (formularz) - tworzy formularz wystawiania dla wybranego konta. */
    @PostMapping("/products/list-selected")
    public String listSelected(@RequestParam Long accountId, @RequestParam(required = false) List<Long> productIds,
                               RedirectAttributes redirect) {
        if (productIds == null || productIds.isEmpty()) {
            throw new IllegalArgumentException("Zaznacz produkty do wystawienia");
        }
        ListingJob job = listingService.createJob(accountId, productIds);
        redirect.addFlashAttribute("msg", "Utworzono formularz wystawiania (" + job.getItems().size() + " produktów)");
        return "redirect:/listing/" + job.getId();
    }

    private static String trim(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
