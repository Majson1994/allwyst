package pl.allegrolister.service;

import java.time.Instant;

import com.fasterxml.jackson.databind.JsonNode;

import org.springframework.stereotype.Service;

import pl.allegrolister.allegro.AllegroAuthService;
import pl.allegrolister.allegro.AllegroClient;
import pl.allegrolister.allegro.AllegroEnvironment;
import pl.allegrolister.allegro.AllegroHttp;
import pl.allegrolister.domain.AllegroAccount;
import pl.allegrolister.repo.AllegroAccountRepository;

/**
 * Podłączanie kont Allegro (zakończenie flow OAuth).
 */
@Service
public class AccountService {

    private final AllegroAuthService auth;
    private final AllegroHttp http;
    private final AllegroAccountRepository accounts;
    private final EventLogService events;

    public AccountService(AllegroAuthService auth, AllegroHttp http, AllegroAccountRepository accounts, EventLogService events) {
        this.auth = auth;
        this.http = http;
        this.accounts = accounts;
        this.events = events;
    }

    public AllegroAccount completeOAuth(AllegroEnvironment env, String code, String codeVerifier) {
        AllegroAuthService.TokenResponse token = auth.exchangeCode(env, code, codeVerifier);
        JsonNode me = AllegroClient.requireOk(http.sendJson("GET", env.getApiBase() + "/me", token.accessToken(), null));
        String sellerId = me.path("id").asText();
        AllegroAccount account = accounts.findByEnvironmentAndSellerId(env, sellerId).orElseGet(AllegroAccount::new);
        boolean isNew = account.getId() == null;
        account.setEnvironment(env);
        account.setSellerId(sellerId);
        account.setLogin(me.path("login").asText(sellerId));
        account.setAccessToken(token.accessToken());
        account.setRefreshToken(token.refreshToken());
        account.setTokenExpiresAt(Instant.now().plusSeconds(token.expiresIn()));
        account.setConnectedAt(Instant.now());
        account.setActive(true);
        account = accounts.save(account);
        events.info("AUTH", account.getId(), (isNew ? "Podłączono konto " : "Odnowiono autoryzację konta ") + account.getDisplayName());
        return account;
    }
}
