package pl.allegrolister.allegro;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.databind.JsonNode;

import org.springframework.stereotype.Service;

import pl.allegrolister.allegro.model.NamedRef;
import pl.allegrolister.domain.AllegroAccount;

/**
 * Ustawienia sprzedawcy pobierane z Allegro: cenniki dostawy, warunki zwrotów, reklamacji,
 * gwarancje i dane GPSR (producenci oraz osoby odpowiedzialne). Wyniki są cache'owane na 10 minut.
 */
@Service
public class AllegroSellerService {

    private static final Duration TTL = Duration.ofMinutes(10);

    private final AllegroClient client;
    private final Map<String, CachedList> cache = new ConcurrentHashMap<>();

    private record CachedList(List<NamedRef> items, Instant at) {
    }

    public AllegroSellerService(AllegroClient client) {
        this.client = client;
    }

    public List<NamedRef> shippingRates(AllegroAccount a) {
        return cached(a, "shipping", () -> list(a, "/sale/shipping-rates", "shippingRates"));
    }

    public List<NamedRef> returnPolicies(AllegroAccount a) {
        return cached(a, "returns", () -> list(a,
                "/after-sales-service-conditions/return-policies?seller.id=" + enc(a.getSellerId()), "returnPolicies"));
    }

    public List<NamedRef> impliedWarranties(AllegroAccount a) {
        return cached(a, "implied", () -> list(a,
                "/after-sales-service-conditions/implied-warranties?seller.id=" + enc(a.getSellerId()), "impliedWarranties"));
    }

    public List<NamedRef> warranties(AllegroAccount a) {
        return cached(a, "warranties", () -> list(a,
                "/after-sales-service-conditions/warranties?seller.id=" + enc(a.getSellerId()), "warranties"));
    }

    public List<NamedRef> responsibleProducers(AllegroAccount a) {
        return cached(a, "producers", () -> paged(a, "/sale/responsible-producers", "responsibleProducers"));
    }

    public List<NamedRef> responsiblePersons(AllegroAccount a) {
        return cached(a, "persons", () -> paged(a, "/sale/responsible-persons", "responsiblePersons"));
    }

    /** Dane zalogowanego użytkownika (GET /me). */
    public JsonNode me(AllegroAccount a) {
        return client.get(a, "/me");
    }

    public void clearCache(Long accountId) {
        cache.keySet().removeIf(k -> k.startsWith(accountId + ":"));
    }

    /** Wszystkie słowniki naraz - do formularzy. Błędy pojedynczych list nie blokują reszty. */
    public SellerDictionaries dictionaries(AllegroAccount a) {
        SellerDictionaries d = new SellerDictionaries();
        d.shippingRates = safe(() -> shippingRates(a), d);
        d.returnPolicies = safe(() -> returnPolicies(a), d);
        d.impliedWarranties = safe(() -> impliedWarranties(a), d);
        d.warranties = safe(() -> warranties(a), d);
        d.responsibleProducers = safe(() -> responsibleProducers(a), d);
        d.responsiblePersons = safe(() -> responsiblePersons(a), d);
        return d;
    }

    public static class SellerDictionaries {
        public List<NamedRef> shippingRates = List.of();
        public List<NamedRef> returnPolicies = List.of();
        public List<NamedRef> impliedWarranties = List.of();
        public List<NamedRef> warranties = List.of();
        public List<NamedRef> responsibleProducers = List.of();
        public List<NamedRef> responsiblePersons = List.of();
        public final List<String> errors = new ArrayList<>();

        public List<NamedRef> getShippingRates() { return shippingRates; }
        public List<NamedRef> getReturnPolicies() { return returnPolicies; }
        public List<NamedRef> getImpliedWarranties() { return impliedWarranties; }
        public List<NamedRef> getWarranties() { return warranties; }
        public List<NamedRef> getResponsibleProducers() { return responsibleProducers; }
        public List<NamedRef> getResponsiblePersons() { return responsiblePersons; }
        public List<String> getErrors() { return errors; }

        public String nameOf(List<NamedRef> list, String id) {
            if (id == null) {
                return null;
            }
            for (NamedRef r : list) {
                if (r.id().equals(id)) {
                    return r.name();
                }
            }
            return id;
        }
    }

    private interface Loader {
        List<NamedRef> load();
    }

    private List<NamedRef> safe(Loader loader, SellerDictionaries d) {
        try {
            return loader.load();
        } catch (AllegroApiException e) {
            String msg = e.getMessage();
            if (!d.errors.contains(msg)) {
                d.errors.add(msg);
            }
            return List.of();
        }
    }

    private List<NamedRef> cached(AllegroAccount a, String type, Loader loader) {
        String k = a.getId() + ":" + type;
        CachedList c = cache.get(k);
        if (c != null && c.at().plus(TTL).isAfter(Instant.now())) {
            return c.items();
        }
        List<NamedRef> items = loader.load();
        cache.put(k, new CachedList(items, Instant.now()));
        return items;
    }

    private List<NamedRef> list(AllegroAccount a, String path, String field) {
        JsonNode body = client.get(a, path);
        JsonNode arr = body.has(field) ? body.path(field) : AllegroCatalogService.firstArray(body);
        List<NamedRef> out = new ArrayList<>();
        for (JsonNode n : arr) {
            out.add(new NamedRef(n.path("id").asText(), n.path("name").asText(n.path("id").asText())));
        }
        return out;
    }

    private List<NamedRef> paged(AllegroAccount a, String path, String field) {
        List<NamedRef> out = new ArrayList<>();
        int offset = 0;
        int limit = 100;
        while (offset < 5000) {
            JsonNode body = client.get(a, path + "?limit=" + limit + "&offset=" + offset);
            JsonNode arr = body.has(field) ? body.path(field) : AllegroCatalogService.firstArray(body);
            int count = 0;
            for (JsonNode n : arr) {
                out.add(new NamedRef(n.path("id").asText(), n.path("name").asText(n.path("id").asText())));
                count++;
            }
            if (count < limit) {
                break;
            }
            offset += limit;
        }
        return out;
    }

    private static String enc(String s) {
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
    }
}
