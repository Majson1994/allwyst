package pl.allegrolister.domain;

/**
 * Typ reguły uzupełniania parametru Allegro (jak "Reguły parametrów" w BaseLinkerze).
 */
public enum RuleType {
    FROM_FEATURE("Wartość z cechy produktu"),
    CONSTANT("Stała wartość"),
    CONSTANT_IF_CONTAINS("Stała wartość, gdy pole zawiera frazę");

    private final String label;

    RuleType(String label) {
        this.label = label;
    }

    public String getLabel() { return label; }
}
