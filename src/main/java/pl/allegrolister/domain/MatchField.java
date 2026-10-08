package pl.allegrolister.domain;

/**
 * Pole produktu przeszukiwane przez regułę typu CONSTANT_IF_CONTAINS.
 */
public enum MatchField {
    NAME("Nazwa"),
    MANUFACTURER("Producent"),
    DESCRIPTION("Opis"),
    SHOP_CATEGORY("Kategoria w sklepie"),
    SKU("SKU");

    private final String label;

    MatchField(String label) {
        this.label = label;
    }

    public String getLabel() { return label; }
}
