package pl.allegrolister.allegro;

/**
 * Środowisko Allegro: produkcja albo sandbox (testowe).
 */
public enum AllegroEnvironment {

    PRODUCTION("Produkcja", "https://allegro.pl", "https://api.allegro.pl", "https://upload.allegro.pl"),
    SANDBOX("Sandbox", "https://allegro.pl.allegrosandbox.pl", "https://api.allegro.pl.allegrosandbox.pl",
            "https://upload.allegro.pl.allegrosandbox.pl");

    private final String label;
    private final String authBase;
    private final String apiBase;
    private final String uploadBase;

    AllegroEnvironment(String label, String authBase, String apiBase, String uploadBase) {
        this.label = label;
        this.authBase = authBase;
        this.apiBase = apiBase;
        this.uploadBase = uploadBase;
    }

    public String getLabel() { return label; }
    public String getAuthBase() { return authBase; }
    public String getApiBase() { return apiBase; }
    public String getUploadBase() { return uploadBase; }

    /** Strona oferty w serwisie (do linków w panelu). */
    public String offerUrl(String offerId) {
        return authBase + "/oferta/" + offerId;
    }
}
