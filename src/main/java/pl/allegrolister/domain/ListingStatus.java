package pl.allegrolister.domain;

public enum ListingStatus {
    DRAFT("Do wystawienia"),
    QUEUED("W kolejce"),
    PROCESSING("Wysyłanie"),
    PENDING("Weryfikacja Allegro"),
    ACTIVE("Wystawiona"),
    INACTIVE("Szkic na Allegro"),
    ERROR("Błąd");

    private final String label;

    ListingStatus(String label) {
        this.label = label;
    }

    public String getLabel() { return label; }

    public boolean isFinished() {
        return this == ACTIVE || this == INACTIVE || this == ERROR;
    }
}
