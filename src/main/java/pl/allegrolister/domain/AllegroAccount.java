package pl.allegrolister.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import pl.allegrolister.allegro.AllegroEnvironment;
import pl.allegrolister.config.CryptoConverter;

/**
 * Podłączone konto sprzedawcy Allegro (OAuth).
 */
@Entity
@Table(name = "allegro_account")
public class AllegroAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String login;

    private String sellerId;

    @Enumerated(EnumType.STRING)
    private AllegroEnvironment environment;

    @Convert(converter = CryptoConverter.class)
    @Column(length = 8000)
    private String accessToken;

    @Convert(converter = CryptoConverter.class)
    @Column(length = 8000)
    private String refreshToken;

    private Instant tokenExpiresAt;

    private Instant connectedAt;

    private boolean active = true;

    @Embedded
    private AccountSettings settings = new AccountSettings();

    public String getDisplayName() {
        String env = environment == AllegroEnvironment.SANDBOX ? " (sandbox)" : "";
        return (login != null ? login : "konto #" + id) + env;
    }

    public boolean isTokenValid() {
        return accessToken != null && refreshToken != null;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getLogin() { return login; }
    public void setLogin(String login) { this.login = login; }
    public String getSellerId() { return sellerId; }
    public void setSellerId(String sellerId) { this.sellerId = sellerId; }
    public AllegroEnvironment getEnvironment() { return environment; }
    public void setEnvironment(AllegroEnvironment environment) { this.environment = environment; }
    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }
    public String getRefreshToken() { return refreshToken; }
    public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }
    public Instant getTokenExpiresAt() { return tokenExpiresAt; }
    public void setTokenExpiresAt(Instant tokenExpiresAt) { this.tokenExpiresAt = tokenExpiresAt; }
    public Instant getConnectedAt() { return connectedAt; }
    public void setConnectedAt(Instant connectedAt) { this.connectedAt = connectedAt; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public AccountSettings getSettings() {
        if (settings == null) {
            settings = new AccountSettings();
        }
        return settings;
    }

    public void setSettings(AccountSettings settings) { this.settings = settings; }
}
