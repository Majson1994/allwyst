package pl.allegrolister.web;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import pl.allegrolister.allegro.AllegroApiException;
import pl.allegrolister.allegro.AllegroCatalogService;
import pl.allegrolister.allegro.model.CategoryNode;
import pl.allegrolister.allegro.model.CategoryParameter;
import pl.allegrolister.domain.AllegroAccount;
import pl.allegrolister.domain.ListingItem;
import pl.allegrolister.domain.Product;
import pl.allegrolister.repo.AllegroAccountRepository;
import pl.allegrolister.repo.ListingItemRepository;
import pl.allegrolister.repo.ProductRepository;
import pl.allegrolister.service.ProductTextFormat;

/**
 * Endpointy JSON: wybór kategorii w panelu, statusy wystawiania oraz aktualizacja stanów
 * z zewnętrznych systemów (np. ERP albo sklepu): PUT /api/products/{sku}/stock.
 */
@RestController
@RequestMapping("/api")
public class ApiController {

    private final AllegroAccountRepository accounts;
    private final AllegroCatalogService catalog;
    private final ListingItemRepository items;
    private final ProductRepository products;

    public ApiController(AllegroAccountRepository accounts, AllegroCatalogService catalog, ListingItemRepository items,
                         ProductRepository products) {
        this.accounts = accounts;
        this.catalog = catalog;
        this.items = items;
        this.products = products;
    }

    @GetMapping("/categories")
    public List<CategoryNode> categories(@RequestParam Long accountId, @RequestParam(required = false) String parentId) {
        return catalog.categories(account(accountId), parentId);
    }

    @GetMapping("/categories/match")
    public List<CategoryNode> match(@RequestParam Long accountId, @RequestParam String name) {
        return catalog.matchCategories(account(accountId), name);
    }

    @GetMapping("/categories/{id}/path")
    public Map<String, Object> path(@PathVariable String id, @RequestParam Long accountId) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("path", catalog.pathName(account(accountId), id));
        return m;
    }

    @GetMapping("/categories/{id}/parameters")
    public List<Map<String, Object>> parameters(@PathVariable String id, @RequestParam Long accountId) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (CategoryParameter p : catalog.parameters(account(accountId), id)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", p.getId());
            m.put("name", p.getName());
            m.put("type", p.getType());
            m.put("required", p.isRequired());
            m.put("describesProduct", p.isDescribesProduct());
            m.put("dictionarySize", p.getDictionary().size());
            out.add(m);
        }
        return out;
    }

    @GetMapping("/listing/{jobId}/status")
    public List<Map<String, Object>> listingStatus(@PathVariable Long jobId) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (ListingItem i : items.findByJobIdOrderByIdAsc(jobId)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", i.getId());
            m.put("status", i.getStatus().name());
            m.put("label", i.getStatus().getLabel());
            m.put("message", i.getMessage());
            m.put("offerId", i.getOfferId());
            m.put("offerUrl", i.getOfferId() == null ? null : i.getJob().getAccount().getEnvironment().offerUrl(i.getOfferId()));
            out.add(m);
        }
        return out;
    }

    @GetMapping("/products/{sku}")
    public Map<String, Object> product(@PathVariable String sku) {
        Product p = products.findBySku(sku).orElseThrow(() -> new IllegalArgumentException("Nie znaleziono produktu " + sku));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("sku", p.getSku());
        m.put("ean", p.getEan());
        m.put("name", p.getName());
        m.put("price", p.getPrice());
        m.put("quantity", p.getQuantity());
        return m;
    }

    /**
     * Aktualizacja stanu i/lub ceny produktu, np. z ERP: {"quantity": 5, "price": "99.99"}.
     * Zmiana trafi na Allegro przy najbliższej synchronizacji (jeśli jest włączona dla konta).
     */
    @PutMapping("/products/{sku}/stock")
    public Map<String, Object> updateStock(@PathVariable String sku, @RequestBody Map<String, Object> body) {
        Product p = products.findBySku(sku).orElseThrow(() -> new IllegalArgumentException("Nie znaleziono produktu " + sku));
        if (body.containsKey("quantity") && body.get("quantity") != null) {
            p.setQuantity(Math.max(0, ProductTextFormat.parseInt(String.valueOf(body.get("quantity")), p.getQuantity())));
        }
        if (body.containsKey("price") && body.get("price") != null) {
            BigDecimal price = ProductTextFormat.parseDecimal(String.valueOf(body.get("price")));
            if (price != null) {
                p.setPrice(price);
            }
        }
        products.save(p);
        return product(sku);
    }

    @ExceptionHandler(AllegroApiException.class)
    public ResponseEntity<Map<String, String>> allegroError(AllegroApiException e) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<Map<String, String>> badRequest(RuntimeException e) {
        return ResponseEntity.badRequest().body(Map.of("error", String.valueOf(e.getMessage())));
    }

    private AllegroAccount account(Long id) {
        return accounts.findById(id).orElseThrow(() -> new IllegalArgumentException("Nie znaleziono konta #" + id));
    }
}
