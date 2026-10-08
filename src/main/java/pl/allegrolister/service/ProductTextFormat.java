package pl.allegrolister.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import pl.allegrolister.domain.ProductFeature;

/**
 * Konwersje tekst <-> listy dla formularza produktu i importu CSV.
 */
public final class ProductTextFormat {

    private ProductTextFormat() {
    }

    /** Adresy zdjęć: po jednym w linii albo rozdzielone "|". */
    public static List<String> parseImages(String text) {
        List<String> out = new ArrayList<>();
        if (text == null) {
            return out;
        }
        for (String s : text.split("[\\r\\n|]+")) {
            String t = s.trim();
            if (!t.isEmpty()) {
                out.add(t);
            }
        }
        return out;
    }

    public static String formatImages(List<String> images) {
        return images == null ? "" : String.join("\n", images);
    }

    /** Cechy: "Nazwa=Wartość" (albo "Nazwa: Wartość") w liniach lub rozdzielone "|". */
    public static List<ProductFeature> parseFeatures(String text) {
        List<ProductFeature> out = new ArrayList<>();
        if (text == null) {
            return out;
        }
        for (String s : text.split("[\\r\\n|]+")) {
            String t = s.trim();
            if (t.isEmpty()) {
                continue;
            }
            int idx = t.indexOf('=');
            if (idx < 0) {
                idx = t.indexOf(':');
            }
            if (idx > 0) {
                String name = t.substring(0, idx).trim();
                String value = t.substring(idx + 1).trim();
                if (!name.isEmpty() && !value.isEmpty()) {
                    out.add(new ProductFeature(name, value));
                }
            }
        }
        return out;
    }

    public static String formatFeatures(List<ProductFeature> features) {
        if (features == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (ProductFeature f : features) {
            if (!sb.isEmpty()) {
                sb.append('\n');
            }
            sb.append(f.getName()).append('=').append(f.getValue());
        }
        return sb.toString();
    }

    /** Liczba z przecinkiem lub kropką, np. "1 299,90" -> 1299.90. Pusty tekst -> null. */
    public static BigDecimal parseDecimal(String text) {
        if (text == null) {
            return null;
        }
        String t = text.replace(' ', ' ').replace(" ", "").replace("zł", "").replace("PLN", "").replace(',', '.').trim();
        if (t.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(t);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static int parseInt(String text, int fallback) {
        BigDecimal d = parseDecimal(text);
        return d == null ? fallback : d.intValue();
    }
}
