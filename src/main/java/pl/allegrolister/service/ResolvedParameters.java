package pl.allegrolister.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import pl.allegrolister.allegro.model.CategoryParameter;

/**
 * Wynik dopasowania parametrów kategorii Allegro do produktu.
 */
public class ResolvedParameters {

    private final List<Entry> entries = new ArrayList<>();

    public static class Entry {
        private final CategoryParameter param;
        private final List<String> valuesIds = new ArrayList<>();
        private final List<String> values = new ArrayList<>();
        private String rangeFrom;
        private String rangeTo;
        private String source;
        private String warning;
        private boolean resolved;
        private boolean overridden;

        public Entry(CategoryParameter param) {
            this.param = param;
        }

        /** Format API Allegro: {"id":..., "valuesIds":[...]} / {"values":[...]} / {"rangeValue":{...}}. */
        public Map<String, Object> toApi() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", param.getId());
            if (param.isRange()) {
                Map<String, Object> range = new LinkedHashMap<>();
                range.put("from", rangeFrom);
                range.put("to", rangeTo);
                m.put("rangeValue", range);
            } else if (!valuesIds.isEmpty()) {
                m.put("valuesIds", new ArrayList<>(valuesIds));
            } else {
                m.put("values", new ArrayList<>(values));
            }
            return m;
        }

        /** Czytelna wartość do wyświetlenia w panelu. */
        public String getDisplayValue() {
            if (!resolved) {
                return "";
            }
            if (param.isRange()) {
                return rangeFrom + " - " + rangeTo;
            }
            List<String> labels = new ArrayList<>();
            for (String id : valuesIds) {
                labels.add(param.dictionaryLabel(id));
            }
            labels.addAll(values);
            return String.join(", ", labels);
        }

        public CategoryParameter getParam() { return param; }
        public List<String> getValuesIds() { return valuesIds; }
        public List<String> getValues() { return values; }
        public String getRangeFrom() { return rangeFrom; }
        public void setRangeFrom(String rangeFrom) { this.rangeFrom = rangeFrom; }
        public String getRangeTo() { return rangeTo; }
        public void setRangeTo(String rangeTo) { this.rangeTo = rangeTo; }
        public String getSource() { return source; }
        public void setSource(String source) { this.source = source; }
        public String getWarning() { return warning; }
        public void setWarning(String warning) { this.warning = warning; }
        public boolean isResolved() { return resolved; }
        public void setResolved(boolean resolved) { this.resolved = resolved; }
        public boolean isOverridden() { return overridden; }
        public void setOverridden(boolean overridden) { this.overridden = overridden; }
    }

    public List<Entry> getEntries() {
        return entries;
    }

    public Entry entry(String paramId) {
        for (Entry e : entries) {
            if (e.getParam().getId().equals(paramId)) {
                return e;
            }
        }
        return null;
    }

    public List<Map<String, Object>> productParameters() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Entry e : entries) {
            if (e.isResolved() && e.getParam().isDescribesProduct()) {
                out.add(e.toApi());
            }
        }
        return out;
    }

    public List<Map<String, Object>> offerParameters() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Entry e : entries) {
            if (e.isResolved() && !e.getParam().isDescribesProduct()) {
                out.add(e.toApi());
            }
        }
        return out;
    }

    /**
     * Brakujące wymagane parametry.
     * @param newProduct true = tworzymy nowy produkt (wymagane też parametry produktowe),
     *                   false = oferta z katalogu (produkt dostarcza swoje parametry, liczą się ofertowe)
     */
    public List<String> missingRequired(boolean newProduct) {
        List<String> out = new ArrayList<>();
        for (Entry e : entries) {
            if (e.isResolved()) {
                continue;
            }
            CategoryParameter p = e.getParam();
            boolean needed = newProduct
                    ? (p.isRequired() || p.isRequiredForProduct())
                    : (p.isRequired() && !p.isDescribesProduct());
            if (needed) {
                out.add(p.getName());
            }
        }
        return out;
    }

    public List<String> warnings() {
        List<String> out = new ArrayList<>();
        for (Entry e : entries) {
            if (e.getWarning() != null) {
                out.add(e.getWarning());
            }
        }
        return out;
    }
}
