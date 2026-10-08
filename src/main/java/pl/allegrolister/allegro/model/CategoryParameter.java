package pl.allegrolister.allegro.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Parametr kategorii Allegro (GET /sale/categories/{id}/parameters).
 */
public class CategoryParameter {

    private String id;
    private String name;
    /** dictionary / string / integer / float */
    private String type;
    private boolean required;
    private boolean requiredForProduct;
    /** true = parametr produktowy (productSet[].product.parameters), false = ofertowy (parameters). */
    private boolean describesProduct;
    private boolean customValuesEnabled;
    private boolean multipleChoices;
    private boolean range;
    private String unit;
    private String ambiguousValueId;
    private Integer allowedNumberOfValues;
    private final List<DictionaryValue> dictionary = new ArrayList<>();

    public record DictionaryValue(String id, String value) {
    }

    public boolean isDictionary() {
        return "dictionary".equalsIgnoreCase(type);
    }

    public boolean isNumeric() {
        return "integer".equalsIgnoreCase(type) || "float".equalsIgnoreCase(type);
    }

    public boolean isGtin() {
        String n = name == null ? "" : name.toUpperCase(Locale.ROOT);
        return n.equals("EAN") || n.startsWith("EAN ") || n.contains("GTIN") || n.equals("ISBN") || n.equals("ISSN");
    }

    public String dictionaryLabel(String valueId) {
        for (DictionaryValue v : dictionary) {
            if (v.id().equals(valueId)) {
                return v.value();
            }
        }
        return valueId;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public boolean isRequired() { return required; }
    public void setRequired(boolean required) { this.required = required; }
    public boolean isRequiredForProduct() { return requiredForProduct; }
    public void setRequiredForProduct(boolean requiredForProduct) { this.requiredForProduct = requiredForProduct; }
    public boolean isDescribesProduct() { return describesProduct; }
    public void setDescribesProduct(boolean describesProduct) { this.describesProduct = describesProduct; }
    public boolean isCustomValuesEnabled() { return customValuesEnabled; }
    public void setCustomValuesEnabled(boolean customValuesEnabled) { this.customValuesEnabled = customValuesEnabled; }
    public boolean isMultipleChoices() { return multipleChoices; }
    public void setMultipleChoices(boolean multipleChoices) { this.multipleChoices = multipleChoices; }
    public boolean isRange() { return range; }
    public void setRange(boolean range) { this.range = range; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public String getAmbiguousValueId() { return ambiguousValueId; }
    public void setAmbiguousValueId(String ambiguousValueId) { this.ambiguousValueId = ambiguousValueId; }
    public Integer getAllowedNumberOfValues() { return allowedNumberOfValues; }
    public void setAllowedNumberOfValues(Integer allowedNumberOfValues) { this.allowedNumberOfValues = allowedNumberOfValues; }
    public List<DictionaryValue> getDictionary() { return dictionary; }
}
