package pl.allegrolister.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Konfiguracja aplikacji (application.yml, prefiks "app").
 */
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    /** Publiczny adres aplikacji - z niego budowany jest redirect_uri dla OAuth Allegro. */
    private String baseUrl = "http://localhost:8080";

    /** Klucz do szyfrowania tokenów w bazie (AES-GCM). ZMIEŃ na produkcji. */
    private String encryptionKey = "zmien-ten-klucz-na-produkcji";

    /** Wczytaj przykładowe produkty przy pierwszym uruchomieniu. */
    private boolean seedSampleData = true;

    private final BasicAuth basicAuth = new BasicAuth();
    private final Allegro allegro = new Allegro();
    private final Sync sync = new Sync();

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getEncryptionKey() { return encryptionKey; }
    public void setEncryptionKey(String encryptionKey) { this.encryptionKey = encryptionKey; }
    public boolean isSeedSampleData() { return seedSampleData; }
    public void setSeedSampleData(boolean seedSampleData) { this.seedSampleData = seedSampleData; }
    public BasicAuth getBasicAuth() { return basicAuth; }
    public Allegro getAllegro() { return allegro; }
    public Sync getSync() { return sync; }

    public String redirectUri() {
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return base + "/allegro/callback";
    }

    public static class BasicAuth {
        private String username = "admin";
        /** Puste hasło = logowanie wyłączone. */
        private String password = "";

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }

    public static class Allegro {
        private final Credentials production = new Credentials();
        private final Credentials sandbox = new Credentials();
        private String userAgent = "AllegroLister/1.0";

        public Credentials getProduction() { return production; }
        public Credentials getSandbox() { return sandbox; }
        public String getUserAgent() { return userAgent; }
        public void setUserAgent(String userAgent) { this.userAgent = userAgent; }
    }

    public static class Credentials {
        private String clientId = "";
        private String clientSecret = "";

        public String getClientId() { return clientId; }
        public void setClientId(String clientId) { this.clientId = clientId; }
        public String getClientSecret() { return clientSecret; }
        public void setClientSecret(String clientSecret) { this.clientSecret = clientSecret; }

        public boolean isConfigured() {
            return clientId != null && !clientId.isBlank() && clientSecret != null && !clientSecret.isBlank();
        }
    }

    public static class Sync {
        /** Globalny włącznik schedulera synchronizacji stanów i cen. */
        private boolean enabled = true;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }
}
