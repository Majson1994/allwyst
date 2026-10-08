package pl.allegrolister.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import pl.allegrolister.allegro.model.CategoryParameter;
import pl.allegrolister.domain.ParameterRule;
import pl.allegrolister.domain.Product;

/**
 * Uzupełnia parametry kategorii Allegro na podstawie produktu. Kolejność (jak w BaseLinkerze):
 * <ol>
 *   <li>wartość wpisana ręcznie w formularzu wystawiania</li>
 *   <li>reguły parametrów (najpierw reguły dla tej kategorii, potem globalne, wg priorytetu)</li>
 *   <li>automatycznie: EAN, cecha o tej samej nazwie, marka = producent, Stan = Nowy, waga</li>
 * </ol>
 * Wartości słownikowe są dopasowywane do słownika Allegro bez względu na wielkość liter i polskie znaki.
 */
public final class ParameterResolver {

    private static final Pattern NUMBER = Pattern.compile("-?\\d+(?:[.,]\\d+)?");
    private static final Pattern RANGE = Pattern.compile("^\\s*(-?\\d+(?:[.,]\\d+)?)\\s*[-–]\\s*(-?\\d+(?:[.,]\\d+)?)\\s*$");

    private ParameterResolver() {
    }

    public static ResolvedParameters resolve(Product product, List<CategoryParameter> params, List<ParameterRule> rules,
                                             Map<String, List<String>> overrides, String categoryId) {
        ResolvedParameters result = new ResolvedParameters();
        List<ParameterRule> sortedRules = sortRules(rules, categoryId);
        for (CategoryParameter p : params) {
            ResolvedParameters.Entry e = new ResolvedParameters.Entry(p);
            result.getEntries().add(e);

            List<String> override = overrides == null ? null : overrides.get(p.getId());
            if (override != null && override.stream().anyMatch(v -> v != null && !v.isBlank())) {
                applyOverride(e, override);
                if (e.isResolved()) {
                    e.setOverridden(true);
                    e.setSource("ręcznie");
                    continue;
                }
            }

            boolean done = false;
            for (ParameterRule r : sortedRules) {
                if (!ruleMatchesParam(r, p)) {
                    continue;
                }
                String raw = ruleValue(r, product);
                if (raw == null || raw.isBlank()) {
                    continue;
                }
                raw = applyValueMap(raw, r.getValueMap(), p);
                if (fill(e, raw)) {
                    e.setSource("reguła" + (r.getId() != null ? " #" + r.getId() : ""));
                    done = true;
                    break;
                }
            }
            if (!done) {
                autoFill(e, product);
            }
        }
        return result;
    }

    // ---------------- automatyczne dopasowanie ----------------

    private static void autoFill(ResolvedParameters.Entry e, Product product) {
        CategoryParameter p = e.getParam();
        if (p.isGtin() && notBlank(product.getEan())) {
            if (fill(e, product.getEan().trim())) {
                e.setSource("EAN");
                return;
            }
        }
        String feature = product.feature(p.getName());
        if (feature == null) {
            feature = featureByNormalizedName(product, p.getName());
        }
        if (notBlank(feature)) {
            String warningBefore = e.getWarning();
            if (fill(e, feature)) {
                e.setSource("cecha \"" + p.getName() + "\"");
                return;
            }
            if (warningBefore == null && e.getWarning() != null) {
                return; // wartość cechy nie pasuje do słownika - zostawiamy ostrzeżenie
            }
        }
        if (isBrandParam(p) && notBlank(product.getManufacturer())) {
            if (fill(e, product.getManufacturer().trim())) {
                e.setSource("producent");
                return;
            }
        }
        if ("11323".equals(p.getId()) || "stan".equalsIgnoreCase(p.getName())) {
            if (fill(e, "Nowy")) {
                e.setSource("domyślnie");
                e.setWarning(null);
                return;
            }
        }
        if (product.getWeight() != null && p.isNumeric() && normalize(p.getName()).startsWith("waga")) {
            BigDecimal w = product.getWeight();
            if ("g".equalsIgnoreCase(p.getUnit())) {
                w = w.multiply(BigDecimal.valueOf(1000));
            }
            if (fill(e, w.stripTrailingZeros().toPlainString())) {
                e.setSource("waga produktu");
            }
        }
    }

    private static boolean isBrandParam(CategoryParameter p) {
        String n = normalize(p.getName());
        return n.equals("marka") || n.startsWith("marka ") || n.equals("producent");
    }

    private static String featureByNormalizedName(Product product, String paramName) {
        String target = normalize(paramName);
        for (var f : product.getFeatures()) {
            if (f.getName() != null && normalize(f.getName()).equals(target)) {
                return f.getValue();
            }
        }
        return null;
    }

    // ---------------- reguły ----------------

    static List<ParameterRule> sortRules(List<ParameterRule> rules, String categoryId) {
        List<ParameterRule> out = new ArrayList<>();
        if (rules == null) {
            return out;
        }
        for (ParameterRule r : rules) {
            if (!r.isEnabled()) {
                continue;
            }
            boolean global = r.getAllegroCategoryId() == null || r.getAllegroCategoryId().isBlank();
            if (global || r.getAllegroCategoryId().trim().equals(categoryId)) {
                out.add(r);
            }
        }
        out.sort(Comparator
                .comparing((ParameterRule r) -> r.getAllegroCategoryId() == null || r.getAllegroCategoryId().isBlank())
                .thenComparingInt(ParameterRule::getPriority)
                .thenComparing(r -> r.getId() == null ? Long.MAX_VALUE : r.getId()));
        return out;
    }

    static boolean ruleMatchesParam(ParameterRule r, CategoryParameter p) {
        if (notBlank(r.getParameterId())) {
            return r.getParameterId().trim().equals(p.getId());
        }
        return notBlank(r.getParameterName()) && normalize(r.getParameterName()).equals(normalize(p.getName()));
    }

    static String ruleValue(ParameterRule r, Product product) {
        if (r.getRuleType() == null) {
            return null;
        }
        return switch (r.getRuleType()) {
            case FROM_FEATURE -> featureOrSpecial(r.getFeatureName(), product);
            case CONSTANT -> r.getValue();
            case CONSTANT_IF_CONTAINS -> matchesPhrase(fieldText(r, product), r.getPhrase()) ? r.getValue() : null;
        };
    }

    static String featureOrSpecial(String featureName, Product p) {
        if (featureName == null) {
            return null;
        }
        String f = featureName.trim();
        return switch (f.toLowerCase(Locale.ROOT)) {
            case "@ean" -> p.getEan();
            case "@sku" -> p.getSku();
            case "@producent" -> p.getManufacturer();
            case "@nazwa" -> p.getName();
            case "@kategoria" -> p.getShopCategory();
            case "@waga" -> p.getWeight() == null ? null : p.getWeight().stripTrailingZeros().toPlainString();
            default -> p.feature(f);
        };
    }

    private static String fieldText(ParameterRule r, Product p) {
        if (r.getMatchField() == null) {
            return p.getName();
        }
        return switch (r.getMatchField()) {
            case NAME -> p.getName();
            case MANUFACTURER -> p.getManufacturer();
            case DESCRIPTION -> AllegroHtmlSanitizer.toPlainText(p.getDescription());
            case SHOP_CATEGORY -> p.getShopCategory();
            case SKU -> p.getSku();
        };
    }

    /** Fraza z gwiazdkami: "czerw*" pasuje do "czerwony", "Czerwień" itd. (dowolne miejsce tekstu). */
    static boolean matchesPhrase(String text, String phrase) {
        if (text == null || phrase == null || phrase.isBlank()) {
            return false;
        }
        List<String> quoted = new ArrayList<>();
        for (String part : phrase.trim().split("\\*", -1)) {
            String n = normalize(part);
            quoted.add(n.isEmpty() ? "" : Pattern.quote(n));
        }
        return Pattern.compile(String.join(".*", quoted), Pattern.DOTALL).matcher(normalize(text)).find();
    }

    /** "red=czerwony;blue=niebieski" - mapowanie całych wartości (bez względu na wielkość liter). */
    static String applyValueMap(String raw, String valueMap, CategoryParameter p) {
        if (valueMap == null || valueMap.isBlank()) {
            return raw;
        }
        List<String> mapped = new ArrayList<>();
        for (String v : splitValues(raw, p)) {
            String result = v;
            for (String pair : valueMap.split("[;\\n]")) {
                int eq = pair.indexOf('=');
                if (eq > 0 && normalize(pair.substring(0, eq)).equals(normalize(v))) {
                    result = pair.substring(eq + 1).trim();
                    break;
                }
            }
            mapped.add(result);
        }
        return String.join("|", mapped);
    }

    // ---------------- konwersja wartości ----------------

    private static void applyOverride(ResolvedParameters.Entry e, List<String> override) {
        CategoryParameter p = e.getParam();
        List<String> vals = override.stream().filter(v -> v != null && !v.isBlank()).map(String::trim).toList();
        if (p.isRange()) {
            String from = vals.isEmpty() ? null : vals.get(0);
            String to = vals.size() > 1 ? vals.get(1) : from;
            if (from != null) {
                fill(e, from + "-" + to);
            }
            return;
        }
        if (p.isDictionary()) {
            for (String v : vals) {
                boolean isId = p.getDictionary().stream().anyMatch(d -> d.id().equals(v));
                if (isId) {
                    e.getValuesIds().add(v);
                }
            }
            if (!e.getValuesIds().isEmpty()) {
                e.setResolved(true);
                return;
            }
        }
        fill(e, String.join("|", vals));
    }

    /**
     * Wpisuje surową wartość do parametru wg jego typu. Zwraca true, gdy wartość została przyjęta.
     */
    static boolean fill(ResolvedParameters.Entry e, String raw) {
        CategoryParameter p = e.getParam();
        e.getValues().clear();
        e.getValuesIds().clear();
        if (raw == null || raw.isBlank()) {
            return false;
        }
        if (p.isRange()) {
            Matcher m = RANGE.matcher(raw);
            String from;
            String to;
            if (m.matches()) {
                from = number(m.group(1), p);
                to = number(m.group(2), p);
            } else {
                from = number(raw, p);
                to = from;
            }
            if (from == null || to == null) {
                e.setWarning("Parametr \"" + p.getName() + "\": nieprawidłowy zakres \"" + raw + "\"");
                return false;
            }
            e.setRangeFrom(from);
            e.setRangeTo(to);
            e.setResolved(true);
            e.setWarning(null);
            return true;
        }

        List<String> parts = splitValues(raw, p);
        int limit = p.isMultipleChoices() ? Integer.MAX_VALUE
                : (p.getAllowedNumberOfValues() != null && p.getAllowedNumberOfValues() > 0 ? p.getAllowedNumberOfValues() : 1);
        if (parts.size() > limit) {
            parts = parts.subList(0, limit);
        }

        if (p.isDictionary()) {
            List<String> unmatched = new ArrayList<>();
            for (String part : parts) {
                String id = matchDictionary(p, part);
                if (id != null) {
                    if (!e.getValuesIds().contains(id)) {
                        e.getValuesIds().add(id);
                    }
                } else {
                    unmatched.add(part);
                }
            }
            if (!unmatched.isEmpty() && e.getValuesIds().isEmpty() && p.isCustomValuesEnabled()) {
                e.getValues().addAll(unmatched);
                unmatched.clear();
            }
            if (!unmatched.isEmpty()) {
                e.setWarning("Parametr \"" + p.getName() + "\": wartość \"" + String.join(", ", unmatched)
                        + "\" nie występuje w słowniku Allegro");
            }
            boolean ok = !e.getValuesIds().isEmpty() || !e.getValues().isEmpty();
            e.setResolved(ok);
            if (ok && unmatched.isEmpty()) {
                e.setWarning(null);
            }
            return ok;
        }

        if (p.isNumeric()) {
            for (String part : parts) {
                String n = number(part, p);
                if (n != null) {
                    e.getValues().add(n);
                }
            }
            if (e.getValues().isEmpty()) {
                e.setWarning("Parametr \"" + p.getName() + "\": \"" + raw + "\" nie jest liczbą");
                return false;
            }
            e.setResolved(true);
            e.setWarning(null);
            return true;
        }

        for (String part : parts) {
            e.getValues().add(part);
        }
        e.setResolved(!e.getValues().isEmpty());
        if (e.isResolved()) {
            e.setWarning(null);
        }
        return e.isResolved();
    }

    static List<String> splitValues(String raw, CategoryParameter p) {
        String regex;
        if (p.isDictionary() && p.isMultipleChoices()) {
            regex = "[|;,]";
        } else if (p.isMultipleChoices() || (p.getAllowedNumberOfValues() != null && p.getAllowedNumberOfValues() > 1)) {
            regex = "[|;]";
        } else {
            regex = "\\|";
        }
        List<String> out = new ArrayList<>();
        for (String s : raw.split(regex)) {
            if (!s.isBlank()) {
                out.add(s.trim());
            }
        }
        return out;
    }

    static String matchDictionary(CategoryParameter p, String value) {
        String n = normalize(value);
        for (CategoryParameter.DictionaryValue d : p.getDictionary()) {
            if (normalize(d.value()).equals(n)) {
                return d.id();
            }
        }
        String compact = n.replaceAll("[^a-z0-9]", "");
        if (compact.isEmpty()) {
            return null;
        }
        for (CategoryParameter.DictionaryValue d : p.getDictionary()) {
            if (normalize(d.value()).replaceAll("[^a-z0-9]", "").equals(compact)) {
                return d.id();
            }
        }
        return null;
    }

    static String number(String raw, CategoryParameter p) {
        Matcher m = NUMBER.matcher(raw);
        if (!m.find()) {
            return null;
        }
        BigDecimal value = new BigDecimal(m.group().replace(',', '.'));
        if ("integer".equalsIgnoreCase(p.getType())) {
            return value.setScale(0, RoundingMode.HALF_UP).toPlainString();
        }
        return value.stripTrailingZeros().toPlainString();
    }

    /** Małe litery, bez polskich znaków i nadmiarowych spacji. */
    static String normalize(String s) {
        if (s == null) {
            return "";
        }
        String lower = s.toLowerCase(Locale.ROOT).replace('ł', 'l');
        String noAccents = Normalizer.normalize(lower, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return noAccents.replaceAll("\\s+", " ").trim();
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
