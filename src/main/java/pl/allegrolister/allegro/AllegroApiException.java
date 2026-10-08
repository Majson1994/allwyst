package pl.allegrolister.allegro;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Błąd zwrócony przez API Allegro. Zawiera czytelne komunikaty (userMessage) i kody błędów.
 */
public class AllegroApiException extends RuntimeException {

    private final int status;
    private final String rawBody;
    private final List<String> userMessages;
    private final List<String> codes;

    public AllegroApiException(int status, String message, String rawBody, List<String> userMessages, List<String> codes) {
        super(message);
        this.status = status;
        this.rawBody = rawBody;
        this.userMessages = userMessages;
        this.codes = codes;
    }

    public static AllegroApiException from(ApiResponse r) {
        List<String> messages = errorMessages(r.body());
        List<String> codes = new ArrayList<>();
        if (r.body() != null && r.body().path("errors").isArray()) {
            for (JsonNode e : r.body().path("errors")) {
                codes.add(e.path("code").asText(""));
            }
        }
        String msg = messages.isEmpty()
                ? "HTTP " + r.status() + (r.raw() != null && !r.raw().isBlank() ? ": " + abbreviate(r.raw(), 500) : "")
                : String.join("; ", messages);
        return new AllegroApiException(r.status(), msg, r.raw(), messages, codes);
    }

    /** Wyciąga komunikaty błędów w formacie Allegro ({"errors":[{"userMessage":...}]}) lub OAuth. */
    public static List<String> errorMessages(JsonNode body) {
        List<String> out = new ArrayList<>();
        if (body == null) {
            return out;
        }
        if (body.path("errors").isArray()) {
            for (JsonNode e : body.path("errors")) {
                String m = e.path("userMessage").asText("");
                if (m.isBlank()) {
                    m = e.path("message").asText("");
                }
                String path = e.path("path").asText("");
                if (!m.isBlank()) {
                    out.add(path.isBlank() || "null".equals(path) ? m : m + " [" + path + "]");
                }
            }
        }
        if (out.isEmpty() && body.hasNonNull("error_description")) {
            out.add(body.path("error_description").asText());
        } else if (out.isEmpty() && body.hasNonNull("error") && body.path("error").isTextual()) {
            out.add(body.path("error").asText());
        }
        return out;
    }

    public boolean hasCode(String fragment) {
        return codes.stream().anyMatch(c -> c != null && c.contains(fragment))
                || (rawBody != null && rawBody.contains(fragment));
    }

    private static String abbreviate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    public int getStatus() { return status; }
    public String getRawBody() { return rawBody; }
    public List<String> getUserMessages() { return userMessages; }
    public List<String> getCodes() { return codes; }
}
