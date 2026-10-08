package pl.allegrolister.allegro;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Surowa odpowiedź z API Allegro.
 */
public record ApiResponse(int status, Map<String, List<String>> headers, String raw, JsonNode body) {

    public boolean isSuccess() {
        return status >= 200 && status < 300;
    }

    public Optional<String> header(String name) {
        if (headers == null) {
            return Optional.empty();
        }
        for (Map.Entry<String, List<String>> e : headers.entrySet()) {
            if (e.getKey() != null && e.getKey().equalsIgnoreCase(name) && e.getValue() != null && !e.getValue().isEmpty()) {
                return Optional.ofNullable(e.getValue().get(0));
            }
        }
        return Optional.empty();
    }
}
