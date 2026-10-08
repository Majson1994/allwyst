package pl.allegrolister.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pl.allegrolister.domain.Product;
import pl.allegrolister.repo.ProductRepository;

/**
 * Import produktów z CSV (upsert po SKU). Kolumny (nagłówek, kolejność dowolna, wielkość liter bez znaczenia):
 * sku; ean; nazwa; producent; cena; ilosc; vat; waga; kategoria; zdjecia; opis; cechy; kategoria_allegro; bezpieczenstwo
 * (akceptowane są też nazwy angielskie: name, manufacturer, price, quantity, weight, category, images,
 * description, features, allegro_category_id, safety_info).
 */
@Service
public class ProductImportService {

    private static final Map<String, String> ALIASES = new HashMap<>();

    static {
        alias("sku", "sku", "symbol", "kod");
        alias("ean", "ean", "gtin", "kod_kreskowy");
        alias("name", "name", "nazwa");
        alias("manufacturer", "manufacturer", "producent", "marka", "brand");
        alias("price", "price", "cena", "cena_brutto");
        alias("quantity", "quantity", "ilosc", "ilość", "stan", "stock");
        alias("vat", "vat", "stawka_vat");
        alias("weight", "weight", "waga");
        alias("category", "category", "kategoria");
        alias("images", "images", "zdjecia", "zdjęcia", "image", "zdjecie");
        alias("description", "description", "opis");
        alias("features", "features", "cechy", "parametry");
        alias("allegro_category_id", "allegro_category_id", "kategoria_allegro", "allegro_category");
        alias("safety_info", "safety_info", "bezpieczenstwo", "bezpieczeństwo", "gpsr");
    }

    private static void alias(String key, String... names) {
        for (String n : names) {
            ALIASES.put(n, key);
        }
    }

    public record ImportResult(int created, int updated, List<String> errors) {
    }

    private final ProductRepository products;

    public ProductImportService(ProductRepository products) {
        this.products = products;
    }

    @Transactional
    public ImportResult importCsv(String content) {
        List<List<String>> rows = CsvParser.parse(content);
        List<String> errors = new ArrayList<>();
        if (rows.isEmpty()) {
            errors.add("Plik jest pusty");
            return new ImportResult(0, 0, errors);
        }
        Map<String, Integer> col = new HashMap<>();
        List<String> header = rows.get(0);
        for (int i = 0; i < header.size(); i++) {
            String key = ALIASES.get(header.get(i).trim().toLowerCase(Locale.ROOT).replace(' ', '_'));
            if (key != null) {
                col.put(key, i);
            }
        }
        if (!col.containsKey("sku") || !col.containsKey("name")) {
            errors.add("Brak wymaganych kolumn 'sku' i 'nazwa' w nagłówku");
            return new ImportResult(0, 0, errors);
        }
        int created = 0;
        int updated = 0;
        for (int r = 1; r < rows.size(); r++) {
            List<String> row = rows.get(r);
            String sku = get(row, col, "sku");
            if (sku == null || sku.isBlank()) {
                if (row.stream().anyMatch(s -> !s.isBlank())) {
                    errors.add("Wiersz " + (r + 1) + ": brak SKU - pominięto");
                }
                continue;
            }
            String name = get(row, col, "name");
            if (name == null || name.isBlank()) {
                errors.add("Wiersz " + (r + 1) + " (" + sku + "): brak nazwy - pominięto");
                continue;
            }
            Product existing = products.findBySku(sku.trim()).orElse(null);
            boolean isNew = existing == null;
            final Product p = isNew ? new Product() : existing;
            if (isNew) {
                p.setSku(sku.trim());
            }
            p.setName(name.trim());
            setIfPresent(row, col, "ean", v -> p.setEan(v));
            setIfPresent(row, col, "manufacturer", p::setManufacturer);
            setIfPresent(row, col, "price", v -> p.setPrice(ProductTextFormat.parseDecimal(v)));
            setIfPresent(row, col, "quantity", v -> p.setQuantity(Math.max(0, ProductTextFormat.parseInt(v, 0))));
            setIfPresent(row, col, "vat", v -> {
                var d = v == null ? null : ProductTextFormat.parseDecimal(v.replace("%", ""));
                p.setVatRate(d == null ? null : d.intValue());
            });
            setIfPresent(row, col, "weight", v -> p.setWeight(ProductTextFormat.parseDecimal(v)));
            setIfPresent(row, col, "category", p::setShopCategory);
            setIfPresent(row, col, "description", p::setDescription);
            setIfPresent(row, col, "allegro_category_id", p::setAllegroCategoryId);
            setIfPresent(row, col, "safety_info", p::setSafetyInfo);
            setIfPresent(row, col, "images", v -> {
                p.getImages().clear();
                p.getImages().addAll(ProductTextFormat.parseImages(v));
            });
            setIfPresent(row, col, "features", v -> {
                p.getFeatures().clear();
                p.getFeatures().addAll(ProductTextFormat.parseFeatures(v));
            });
            products.save(p);
            if (isNew) {
                created++;
            } else {
                updated++;
            }
        }
        return new ImportResult(created, updated, errors);
    }

    private interface Setter {
        void set(String value);
    }

    private static void setIfPresent(List<String> row, Map<String, Integer> col, String key, Setter setter) {
        if (!col.containsKey(key)) {
            return;
        }
        String v = get(row, col, key);
        setter.set(v == null || v.isBlank() ? null : v.trim());
    }

    private static String get(List<String> row, Map<String, Integer> col, String key) {
        Integer idx = col.get(key);
        return idx == null || idx >= row.size() ? null : row.get(idx);
    }
}
