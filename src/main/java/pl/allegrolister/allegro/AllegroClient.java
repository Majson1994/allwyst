package pl.allegrolister.allegro;

import com.fasterxml.jackson.databind.JsonNode;

import org.springframework.stereotype.Service;

import pl.allegrolister.domain.AllegroAccount;

/**
 * Wywołania REST API w kontekście konkretnego konta (token, środowisko, ponowienie po 401).
 */
@Service
public class AllegroClient {

    private final AllegroHttp http;
    private final AllegroAuthService auth;

    public AllegroClient(AllegroHttp http, AllegroAuthService auth) {
        this.http = http;
        this.auth = auth;
    }

    public ApiResponse call(AllegroAccount account, String method, String pathOrUrl, Object body) {
        String url = pathOrUrl.startsWith("http") ? pathOrUrl : account.getEnvironment().getApiBase() + pathOrUrl;
        String token = auth.validAccessToken(account);
        ApiResponse r = http.sendJson(method, url, token, body);
        if (r.status() == 401) {
            token = auth.forceRefresh(account);
            r = http.sendJson(method, url, token, body);
        }
        return r;
    }

    /** Upload na serwer upload.allegro.pl (zdjęcia). */
    public ApiResponse upload(AllegroAccount account, String path, Object body) {
        return call(account, "POST", account.getEnvironment().getUploadBase() + path, body);
    }

    /** GET, który rzuca wyjątek przy błędzie i zwraca treść JSON. */
    public JsonNode get(AllegroAccount account, String pathOrUrl) {
        return requireOk(call(account, "GET", pathOrUrl, null));
    }

    public ApiResponse post(AllegroAccount account, String path, Object body) {
        return call(account, "POST", path, body);
    }

    public ApiResponse patch(AllegroAccount account, String path, Object body) {
        return call(account, "PATCH", path, body);
    }

    public ApiResponse put(AllegroAccount account, String path, Object body) {
        return call(account, "PUT", path, body);
    }

    public static JsonNode requireOk(ApiResponse r) {
        if (!r.isSuccess()) {
            throw AllegroApiException.from(r);
        }
        return r.body();
    }
}
