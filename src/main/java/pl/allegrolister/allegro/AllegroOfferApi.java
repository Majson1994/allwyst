package pl.allegrolister.allegro;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;

import org.springframework.stereotype.Service;

import pl.allegrolister.domain.AllegroAccount;

/**
 * Operacje na ofertach: tworzenie (POST /sale/product-offers), edycja (PATCH), status operacji,
 * lista ofert oraz komendy grupowe (ilość, cena, publikacja, modyfikacje).
 */
@Service
public class AllegroOfferApi {

    public static final String QUANTITY_COMMANDS = "/sale/offer-quantity-change-commands";
    public static final String PRICE_COMMANDS = "/sale/offer-price-change-commands";
    public static final String PUBLICATION_COMMANDS = "/sale/offer-publication-commands";
    public static final String MODIFICATION_COMMANDS = "/sale/offer-modification-commands";
    /** Maks. liczba ofert w jednej komendzie grupowej. */
    public static final int COMMAND_CHUNK = 1000;

    private final AllegroClient client;

    public AllegroOfferApi(AllegroClient client) {
        this.client = client;
    }

    public ApiResponse create(AllegroAccount a, Map<String, Object> payload) {
        return client.post(a, "/sale/product-offers", payload);
    }

    public ApiResponse patch(AllegroAccount a, String offerId, Map<String, Object> payload) {
        return client.patch(a, "/sale/product-offers/" + offerId, payload);
    }

    public JsonNode get(AllegroAccount a, String offerId) {
        return client.get(a, "/sale/product-offers/" + offerId);
    }

    /** GET na adres z nagłówka Location (202 = w toku, 303 = zakończona). */
    public ApiResponse operation(AllegroAccount a, String operationUrl) {
        return client.call(a, "GET", operationUrl, null);
    }

    /** Strona listy ofert sprzedawcy (GET /sale/offers). */
    public JsonNode listOffers(AllegroAccount a, int offset, int limit, List<String> statuses) {
        StringBuilder path = new StringBuilder("/sale/offers?limit=").append(limit).append("&offset=").append(offset);
        for (String s : statuses) {
            path.append("&publication.status=").append(s);
        }
        return client.get(a, path.toString());
    }

    public String quantityCommand(AllegroAccount a, List<String> offerIds, int quantity) {
        Map<String, Object> modification = new LinkedHashMap<>();
        modification.put("changeType", "FIXED");
        modification.put("value", quantity);
        return command(a, QUANTITY_COMMANDS, Map.of("modification", modification, "offerCriteria", criteria(offerIds)));
    }

    public String priceCommand(AllegroAccount a, List<String> offerIds, BigDecimal price) {
        Map<String, Object> modification = new LinkedHashMap<>();
        modification.put("type", "FIXED_PRICE");
        modification.put("marketplaceId", "allegro-pl");
        modification.put("price", money(price));
        return command(a, PRICE_COMMANDS, Map.of("modification", modification, "offerCriteria", criteria(offerIds)));
    }

    public String pricePercentCommand(AllegroAccount a, List<String> offerIds, BigDecimal percent) {
        Map<String, Object> modification = new LinkedHashMap<>();
        boolean increase = percent.signum() >= 0;
        modification.put("type", increase ? "INCREASE_PERCENTAGE" : "DECREASE_PERCENTAGE");
        modification.put("marketplaceId", "allegro-pl");
        modification.put("percentage", percent.abs().stripTrailingZeros().toPlainString());
        return command(a, PRICE_COMMANDS, Map.of("modification", modification, "offerCriteria", criteria(offerIds)));
    }

    /** action: ACTIVATE albo END */
    public String publicationCommand(AllegroAccount a, List<String> offerIds, String action) {
        return command(a, PUBLICATION_COMMANDS,
                Map.of("publication", Map.of("action", action), "offerCriteria", criteria(offerIds)));
    }

    /** Dowolna modyfikacja grupowa, np. {"delivery":{"shippingRates":{"id":"..."}}}. */
    public String modificationCommand(AllegroAccount a, List<String> offerIds, Map<String, Object> modification) {
        return command(a, MODIFICATION_COMMANDS, Map.of("modification", modification, "offerCriteria", criteria(offerIds)));
    }

    private String command(AllegroAccount a, String base, Map<String, Object> body) {
        String commandId = UUID.randomUUID().toString();
        ApiResponse r = client.put(a, base + "/" + commandId, body);
        AllegroClient.requireOk(r);
        return commandId;
    }

    public record CommandResult(String commandId, int total, int success, int failed, boolean completed, List<String> errors) {
    }

    /**
     * Czeka (maks. ~40 s) na zakończenie komendy grupowej i zwraca podsumowanie
     * wraz z komunikatami błędów z raportu szczegółowego.
     */
    public CommandResult awaitCommand(AllegroAccount a, String base, String commandId) {
        JsonNode summary = null;
        for (int i = 0; i < 20; i++) {
            sleep(i == 0 ? 1000 : 2000);
            summary = client.get(a, base + "/" + commandId);
            JsonNode tc = summary.path("taskCount");
            int total = tc.path("total").asInt();
            int done = tc.path("success").asInt() + tc.path("failed").asInt();
            if (total > 0 && done >= total) {
                break;
            }
        }
        JsonNode tc = summary == null ? null : summary.path("taskCount");
        int total = tc == null ? 0 : tc.path("total").asInt();
        int success = tc == null ? 0 : tc.path("success").asInt();
        int failed = tc == null ? 0 : tc.path("failed").asInt();
        List<String> errors = new ArrayList<>();
        if (failed > 0) {
            try {
                JsonNode tasks = client.get(a, base + "/" + commandId + "/tasks?limit=1000");
                for (JsonNode t : tasks.path("tasks")) {
                    if (!"SUCCESS".equalsIgnoreCase(t.path("status").asText())) {
                        String offer = t.path("offer").path("id").asText("?");
                        String msg = t.path("message").asText("");
                        List<String> detailed = AllegroApiException.errorMessages(t);
                        if (!detailed.isEmpty()) {
                            msg = String.join("; ", detailed);
                        }
                        errors.add(offer + ": " + (msg.isBlank() ? t.path("status").asText() : msg));
                    }
                }
            } catch (AllegroApiException e) {
                errors.add("Nie udało się pobrać raportu: " + e.getMessage());
            }
        }
        return new CommandResult(commandId, total, success, failed, total > 0 && success + failed >= total, errors);
    }

    public static Map<String, Object> money(BigDecimal amount) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("amount", amount.setScale(2, RoundingMode.HALF_UP).toPlainString());
        m.put("currency", "PLN");
        return m;
    }

    private static List<Map<String, Object>> criteria(List<String> offerIds) {
        List<Map<String, String>> offers = new ArrayList<>();
        for (String id : offerIds) {
            offers.add(Map.of("id", id));
        }
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("offers", offers);
        c.put("type", "CONTAINS_OFFERS");
        return List.of(c);
    }

    public static <T> List<List<T>> chunks(List<T> list, int size) {
        List<List<T>> out = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            out.add(new ArrayList<>(list.subList(i, Math.min(list.size(), i + size))));
        }
        return out;
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
