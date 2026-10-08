package pl.allegrolister.allegro;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.StringJoiner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import pl.allegrolister.config.AppProperties;

/**
 * Niskopoziomowa komunikacja HTTP z Allegro (java.net.http).
 * Obsługuje ponawianie przy 429/5xx i nie podąża za przekierowaniami
 * (303 z endpointu operacji jest informacją, a nie przekierowaniem do śledzenia).
 */
@Component
public class AllegroHttp {

    public static final String MEDIA_V1 = "application/vnd.allegro.public.v1+json";
    private static final Logger log = LoggerFactory.getLogger(AllegroHttp.class);
    private static final int MAX_RETRIES = 3;

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    private final ObjectMapper mapper;
    private final AppProperties props;

    public AllegroHttp(ObjectMapper mapper, AppProperties props) {
        this.mapper = mapper;
        this.props = props;
    }

    /** Żądanie JSON do REST API Allegro. body może być null, String (gotowy JSON) albo obiektem do serializacji. */
    public ApiResponse sendJson(String method, String url, String bearerToken, Object body) {
        String json = null;
        if (body instanceof String s) {
            json = s;
        } else if (body != null) {
            try {
                json = mapper.writeValueAsString(body);
            } catch (IOException e) {
                throw new IllegalArgumentException("Nie można zserializować żądania", e);
            }
        }
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(60))
                .header("Accept", MEDIA_V1)
                .header("Accept-Language", "pl-PL")
                .header("User-Agent", props.getAllegro().getUserAgent());
        if (bearerToken != null) {
            b.header("Authorization", "Bearer " + bearerToken);
        }
        if (json != null) {
            b.header("Content-Type", MEDIA_V1);
            b.method(method, HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8));
        } else {
            b.method(method, HttpRequest.BodyPublishers.noBody());
        }
        return execute(b.build());
    }

    /** Żądanie application/x-www-form-urlencoded (endpointy OAuth). */
    public ApiResponse sendForm(String url, Map<String, String> form, String basicAuthHeader) {
        StringJoiner joiner = new StringJoiner("&");
        form.forEach((k, v) -> {
            if (v != null) {
                joiner.add(URLEncoder.encode(k, StandardCharsets.UTF_8) + "=" + URLEncoder.encode(v, StandardCharsets.UTF_8));
            }
        });
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .header("User-Agent", props.getAllegro().getUserAgent())
                .POST(HttpRequest.BodyPublishers.ofString(joiner.toString(), StandardCharsets.UTF_8));
        if (basicAuthHeader != null) {
            b.header("Authorization", basicAuthHeader);
        }
        return execute(b.build());
    }

    private ApiResponse execute(HttpRequest request) {
        int attempt = 0;
        while (true) {
            attempt++;
            try {
                HttpResponse<String> resp = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                int status = resp.statusCode();
                if ((status == 429 || status == 502 || status == 503 || status == 504) && attempt <= MAX_RETRIES) {
                    long waitMs = resp.headers().firstValue("Retry-After")
                            .map(AllegroHttp::parseSeconds).orElse(2L * attempt) * 1000L;
                    log.warn("Allegro zwróciło {} dla {} {} - ponawiam za {} ms", status, request.method(), request.uri(), waitMs);
                    Thread.sleep(Math.min(waitMs, 30_000L));
                    continue;
                }
                String raw = resp.body();
                JsonNode node = null;
                if (raw != null && !raw.isBlank()) {
                    try {
                        node = mapper.readTree(raw);
                    } catch (IOException ignored) {
                        // odpowiedź nie-JSON (np. HTML z błędem) - zostaje w raw
                    }
                }
                return new ApiResponse(status, resp.headers().map(), raw, node);
            } catch (IOException e) {
                if (attempt <= MAX_RETRIES) {
                    log.warn("Błąd połączenia z Allegro ({}), próba {}/{}", e.getMessage(), attempt, MAX_RETRIES);
                    sleepQuietly(1000L * attempt);
                    continue;
                }
                throw new AllegroApiException(0, "Brak połączenia z Allegro: " + e.getMessage(), null, java.util.List.of(), java.util.List.of());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AllegroApiException(0, "Przerwano żądanie do Allegro", null, java.util.List.of(), java.util.List.of());
            }
        }
    }

    private static long parseSeconds(String value) {
        try {
            return Math.max(1L, Long.parseLong(value.trim()));
        } catch (NumberFormatException e) {
            return 2L;
        }
    }

    private static void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public ObjectMapper mapper() {
        return mapper;
    }
}
