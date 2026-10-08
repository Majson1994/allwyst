package pl.allegrolister.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import pl.allegrolister.domain.Product;
import pl.allegrolister.domain.ProductFeature;

/**
 * Podstawianie tagów w szablonach opisu (składnia jak w BaseLinkerze).
 *
 * <pre>
 * [nazwa]         nazwa produktu z magazynu
 * [nazwa_aukcji]  tytuł oferty
 * [opis]          opis produktu (HTML)
 * [producent]     producent / marka
 * [sku] [ean]     kody produktu
 * [cena]          cena na Allegro
 * [ilosc]         liczba sztuk
 * [waga]          waga w kg
 * [kategoria]     kategoria w sklepie
 * [cechy]         lista wszystkich cech produktu (&lt;ul&gt;)
 * [cecha:Kolor]   wartość pojedynczej cechy
 * [zdjecie_N]     N-te zdjęcie (tylko w polach zdjęć sekcji)
 * </pre>
 */
public final class TagEngine {

    private static final Pattern ANY_TAG = Pattern.compile(
            "\\[(?:cecha:([^\\]]+)|(nazwa_aukcji|nazwa|opis|producent|sku|ean|cena|ilosc|waga|kategoria|cechy))]",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern IMAGE_TAG = Pattern.compile("^\\s*\\[zdjecie_(\\d+)]\\s*$", Pattern.CASE_INSENSITIVE);

    private TagEngine() {
    }

    /** Kontekst podstawiania: produkt + dane oferty. */
    public record Context(Product product, String offerTitle, BigDecimal price, Integer quantity) {
    }

    public static Map<String, String> tags(Context ctx) {
        Product p = ctx.product();
        Map<String, String> m = new LinkedHashMap<>();
        m.put("[nazwa]", escape(p.getName()));
        m.put("[nazwa_aukcji]", escape(ctx.offerTitle() != null ? ctx.offerTitle() : p.getName()));
        m.put("[opis]", AllegroHtmlSanitizer.ensureHtml(p.getDescription()));
        m.put("[producent]", escape(p.getManufacturer()));
        m.put("[sku]", escape(p.getSku()));
        m.put("[ean]", escape(p.getEan()));
        BigDecimal price = ctx.price() != null ? ctx.price() : p.getPrice();
        m.put("[cena]", price == null ? "" : price.setScale(2, RoundingMode.HALF_UP).toPlainString().replace('.', ',') + " zł");
        m.put("[ilosc]", String.valueOf(ctx.quantity() != null ? ctx.quantity() : p.getQuantity()));
        m.put("[waga]", p.getWeight() == null ? "" : p.getWeight().stripTrailingZeros().toPlainString().replace('.', ',') + " kg");
        m.put("[kategoria]", escape(p.getShopCategory()));
        m.put("[cechy]", featuresList(p.getFeatures()));
        return m;
    }

    /**
     * Podstawia tagi tekstowe w jednym przebiegu (treść podstawionego opisu nie jest ponownie
     * przetwarzana). Nieznane tagi zostają bez zmian.
     */
    public static String apply(String text, Context ctx) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        Map<String, String> tags = tags(ctx);
        Matcher m = ANY_TAG.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String replacement;
            if (m.group(1) != null) {
                replacement = escape(ctx.product().feature(m.group(1)));
            } else {
                replacement = tags.getOrDefault("[" + m.group(2).toLowerCase() + "]", m.group());
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /**
     * Rozwiązuje pole zdjęcia sekcji: [zdjecie_N] -> N-ty adres z listy (1-based),
     * bezpośredni URL zostaje bez zmian. Zwraca null, gdy zdjęcia brak.
     */
    public static String resolveImage(String imageField, List<String> images) {
        if (imageField == null || imageField.isBlank()) {
            return null;
        }
        Matcher m = IMAGE_TAG.matcher(imageField);
        if (m.matches()) {
            int index = Integer.parseInt(m.group(1)) - 1;
            if (images == null || index < 0 || index >= images.size()) {
                return null;
            }
            return images.get(index);
        }
        String trimmed = imageField.trim();
        return trimmed.startsWith("http") ? trimmed : null;
    }

    static String featuresList(List<ProductFeature> features) {
        if (features == null || features.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("<ul>");
        for (ProductFeature f : features) {
            if (f.getName() == null || f.getName().isBlank() || f.getValue() == null || f.getValue().isBlank()) {
                continue;
            }
            sb.append("<li><b>").append(escape(f.getName().trim())).append(":</b> ")
                    .append(escape(f.getValue().trim())).append("</li>");
        }
        sb.append("</ul>");
        return sb.length() == "<ul></ul>".length() ? "" : sb.toString();
    }

    public static String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
