package pl.allegrolister.domain;

/**
 * Co wysłać w productSet[].safetyInformation (GPSR), gdy produkt nie ma własnego tekstu.
 */
public enum SafetyMode {
    OMIT("Nie wysyłaj (uzupełnię na Allegro)"),
    NO_SAFETY_INFORMATION("Produkt nie wymaga informacji o bezpieczeństwie"),
    TEXT("Domyślny tekst z ustawień konta");

    private final String label;

    SafetyMode(String label) {
        this.label = label;
    }

    public String getLabel() { return label; }
}
