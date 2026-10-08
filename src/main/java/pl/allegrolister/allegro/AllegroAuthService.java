package pl.allegrolister.allegro;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import pl.allegrolister.config.AppProperties;
import pl.allegrolister.domain.AllegroAccount;
import pl.allegrolister.repo.AllegroAccountRepository;

/**
 * OAuth 2.0 Allegro: Authorization Code flow z PKCE + odświeżanie tokenów.
 * Access token ważny 12 h, refresh token 3 miesiące (każde odświeżenie daje nową parę).
 */
@Service
public class AllegroAuthService {

    private static final Logger log = LoggerFactory.getLogger(AllegroAuthService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    /** Odśwież token, gdy do wygaśnięcia zostało mniej niż tyle sekund. */
    private static final long REFRESH_MARGIN_SECONDS = 300;

    private final AppProperties props;
    private final AllegroHttp http;
    private final AllegroAccountRepository accounts;
    private final Map<Long, Object> locks = new ConcurrentHashMap<>();

    public AllegroAuthService(AppProperties props, AllegroHttp http, AllegroAccountRepository accounts) {
        this.props = props;
        this.http = http;
        this.accounts = accounts;
    }

    public record TokenResponse(String accessToken, String refreshToken, long expiresIn) {
    }

    public AppProperties.Credentials credentials(AllegroEnvironment env) {
        return env == AllegroEnvironment.SANDBOX ? props.getAllegro().getSandbox() : props.getAllegro().getProduction();
    }

    public String buildAuthorizeUrl(AllegroEnvironment env, String state, String codeChallenge) {
        AppProperties.Credentials c = credentials(env);
        return env.getAuthBase() + "/auth/oauth/authorize"
                + "?response_type=code"
                + "&client_id=" + enc(c.getClientId())
                + "&redirect_uri=" + enc(props.redirectUri())
                + "&code_challenge_method=S256"
                + "&code_challenge=" + enc(codeChallenge)
                + "&prompt=confirm"
                + "&state=" + enc(state);
    }

    public TokenResponse exchangeCode(AllegroEnvironment env, String code, String codeVerifier) {
        AppProperties.Credentials c = credentials(env);
        Map<String, String> form = new LinkedHashMap<>();
        form.put("grant_type", "authorization_code");
        form.put("code", code);
        form.put("redirect_uri", props.redirectUri());
        form.put("code_verifier", codeVerifier);
        form.put("client_id", c.getClientId());
        ApiResponse r = http.sendForm(env.getAuthBase() + "/auth/oauth/token", form, null);
        return parseToken(r);
    }

    public TokenResponse refresh(AllegroEnvironment env, String refreshToken) {
        AppProperties.Credentials c = credentials(env);
        Map<String, String> form = new LinkedHashMap<>();
        form.put("grant_type", "refresh_token");
        form.put("refresh_token", refreshToken);
        form.put("redirect_uri", props.redirectUri());
        ApiResponse r = http.sendForm(env.getAuthBase() + "/auth/oauth/token", form, basic(c));
        return parseToken(r);
    }

    private TokenResponse parseToken(ApiResponse r) {
        if (!r.isSuccess() || r.body() == null || !r.body().hasNonNull("access_token")) {
            throw AllegroApiException.from(r);
        }
        return new TokenResponse(
                r.body().path("access_token").asText(),
                r.body().path("refresh_token").asText(null),
                r.body().path("expires_in").asLong(43199));
    }

    /**
     * Zwraca ważny access token konta - w razie potrzeby odświeża go i zapisuje nową parę tokenów.
     */
    public String validAccessToken(AllegroAccount account) {
        if (account.getTokenExpiresAt() != null
                && account.getAccessToken() != null
                && account.getTokenExpiresAt().isAfter(Instant.now().plusSeconds(REFRESH_MARGIN_SECONDS))) {
            return account.getAccessToken();
        }
        return refreshLocked(account, false);
    }

    /** Wymusza odświeżenie (np. po 401). */
    public String forceRefresh(AllegroAccount account) {
        return refreshLocked(account, true);
    }

    private String refreshLocked(AllegroAccount account, boolean force) {
        Object lock = locks.computeIfAbsent(account.getId(), k -> new Object());
        synchronized (lock) {
            // inny wątek mógł już odświeżyć token - czytamy najnowszy stan z bazy
            AllegroAccount fresh = accounts.findById(account.getId()).orElse(account);
            boolean stillValid = fresh.getTokenExpiresAt() != null && fresh.getAccessToken() != null
                    && fresh.getTokenExpiresAt().isAfter(Instant.now().plusSeconds(REFRESH_MARGIN_SECONDS));
            boolean changedMeanwhile = fresh.getAccessToken() != null && !fresh.getAccessToken().equals(account.getAccessToken());
            if (stillValid && (!force || changedMeanwhile)) {
                copyTokens(fresh, account);
                return fresh.getAccessToken();
            }
            if (fresh.getRefreshToken() == null) {
                throw new AllegroApiException(401, "Konto " + fresh.getDisplayName()
                        + " nie ma ważnego tokena - połącz je ponownie w zakładce Konta.", null, java.util.List.of(), java.util.List.of());
            }
            log.info("Odświeżam token Allegro dla konta {}", fresh.getDisplayName());
            TokenResponse t;
            try {
                t = refresh(fresh.getEnvironment(), fresh.getRefreshToken());
            } catch (AllegroApiException e) {
                throw new AllegroApiException(401, "Nie udało się odświeżyć tokena konta " + fresh.getDisplayName()
                        + " (" + e.getMessage() + "). Połącz konto ponownie.", e.getRawBody(), e.getUserMessages(), e.getCodes());
            }
            fresh.setAccessToken(t.accessToken());
            if (t.refreshToken() != null) {
                fresh.setRefreshToken(t.refreshToken());
            }
            fresh.setTokenExpiresAt(Instant.now().plusSeconds(t.expiresIn()));
            accounts.save(fresh);
            copyTokens(fresh, account);
            return fresh.getAccessToken();
        }
    }

    private static void copyTokens(AllegroAccount from, AllegroAccount to) {
        to.setAccessToken(from.getAccessToken());
        to.setRefreshToken(from.getRefreshToken());
        to.setTokenExpiresAt(from.getTokenExpiresAt());
    }

    // --- PKCE ---

    public static String newCodeVerifier() {
        byte[] bytes = new byte[64];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String codeChallenge(String verifier) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String newState() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String basic(AppProperties.Credentials c) {
        String raw = c.getClientId() + ":" + c.getClientSecret();
        return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private static String enc(String s) {
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
    }
}
