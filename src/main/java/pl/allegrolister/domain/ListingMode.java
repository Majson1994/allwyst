package pl.allegrolister.domain;

/**
 * Tryb wystawiania względem Katalogu Produktów Allegro (produktyzacja).
 */
public enum ListingMode {
    /** Szukaj produktu w katalogu po EAN; gdy brak - utwórz nowy produkt. */
    AUTO("Automatycznie (katalog po EAN, w razie braku nowy produkt)"),
    /** Tylko powiązanie z istniejącym produktem z katalogu. */
    CATALOG("Tylko z katalogu Allegro"),
    /** Zawsze własne dane produktu (propozycja nowego produktu). */
    NEW_PRODUCT("Nowy produkt (własne dane)");

    private final String label;

    ListingMode(String label) {
        this.label = label;
    }

    public String getLabel() { return label; }
}
