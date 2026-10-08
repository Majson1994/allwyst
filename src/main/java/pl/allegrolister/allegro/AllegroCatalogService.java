package pl.allegrolister.allegro;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.databind.JsonNode;

import org.springframework.stereotype.Service;

import pl.allegrolister.allegro.model.CatalogProduct;
import pl.allegrolister.allegro.model.CategoryNode;
import pl.allegrolister.allegro.model.CategoryParameter;
import pl.allegrolister.domain.AllegroAccount;

/**
 * Kategorie, parametry kategorii, dopasowanie kategorii i Katalog Produktów Allegro.
 */
@Service
public class AllegroCatalogService {

    private static final Duration PARAMS_TTL = Duration.ofHours(6);
    private static final Duration CATEGORY_TTL = Duration.ofHours(24);

    private final AllegroClient client;
    private final Map<String, Cached<List<CategoryParameter>>> paramsCache = new ConcurrentHashMap<>();
    private final Map<String, Cached<CategoryNode>> categoryCache = new ConcurrentHashMap<>();

    private record Cached<T>(T value, Instant at) {
        boolean fresh(Duration ttl) {
            return at.plus(ttl).isAfter(Instant.now());
        }
    }

    public AllegroCatalogService(AllegroClient client) {
        this.client = client;
    }

    /** Podkategorie (parentId == null -> kategorie główne). */
    public List<CategoryNode> categories(AllegroAccount account, String parentId) {
        String path = "/sale/categories" + (parentId == null || parentId.isBlank() ? "" : "?parent.id=" + enc(parentId));
        JsonNode body = client.get(account, path);
        List<CategoryNode> out = new ArrayList<>();
        for (JsonNode c : body.path("categories")) {
            CategoryNode node = toNode(c);
            categoryCache.put(key(account, node.id()), new Cached<>(node, Instant.now()));
            out.add(node);
        }
        return out;
    }

    public CategoryNode category(AllegroAccount account, String categoryId) {
        String k = key(account, categoryId);
        Cached<CategoryNode> c = categoryCache.get(k);
        if (c != null && c.fresh(CATEGORY_TTL)) {
            return c.value();
        }
        CategoryNode node = toNode(client.get(account, "/sale/categories/" + enc(categoryId)));
        categoryCache.put(k, new Cached<>(node, Instant.now()));
        return node;
    }

    /** Ścieżka od kategorii głównej do wskazanej. */
    public List<CategoryNode> path(AllegroAccount account, String categoryId) {
        List<CategoryNode> path = new ArrayList<>();
        String current = categoryId;
        int guard = 0;
        while (current != null && !current.isBlank() && guard++ < 12) {
            CategoryNode node = category(account, current);
            path.add(node);
            current = node.parentId();
        }
        Collections.reverse(path);
        return path;
    }

    public String pathName(AllegroAccount account, String categoryId) {
        try {
            List<String> names = new ArrayList<>();
            for (CategoryNode n : path(account, categoryId)) {
                names.add(n.name());
            }
            return String.join(" > ", names);
        } catch (AllegroApiException e) {
            return categoryId;
        }
    }

    public List<CategoryParameter> parameters(AllegroAccount account, String categoryId) {
        String k = key(account, categoryId);
        Cached<List<CategoryParameter>> c = paramsCache.get(k);
        if (c != null && c.fresh(PARAMS_TTL)) {
            return c.value();
        }
        JsonNode body = client.get(account, "/sale/categories/" + enc(categoryId) + "/parameters");
        List<CategoryParameter> out = new ArrayList<>();
        for (JsonNode p : body.path("parameters")) {
            out.add(parseParameter(p));
        }
        paramsCache.put(k, new Cached<>(out, Instant.now()));
        return out;
    }

    static CategoryParameter parseParameter(JsonNode p) {
        CategoryParameter cp = new CategoryParameter();
        cp.setId(p.path("id").asText());
        cp.setName(p.path("name").asText());
        cp.setType(p.path("type").asText());
        cp.setRequired(p.path("required").asBoolean(false));
        cp.setRequiredForProduct(p.path("requiredForProduct").asBoolean(false));
        cp.setUnit(p.hasNonNull("unit") ? p.path("unit").asText() : null);
        JsonNode options = p.path("options");
        cp.setDescribesProduct(options.path("describesProduct").asBoolean(false));
        cp.setCustomValuesEnabled(options.path("customValuesEnabled").asBoolean(false));
        cp.setAmbiguousValueId(options.hasNonNull("ambiguousValueId") ? options.path("ambiguousValueId").asText() : null);
        JsonNode restrictions = p.path("restrictions");
        cp.setMultipleChoices(restrictions.path("multipleChoices").asBoolean(false));
        cp.setRange(restrictions.path("range").asBoolean(false));
        if (restrictions.hasNonNull("allowedNumberOfValues")) {
            cp.setAllowedNumberOfValues(restrictions.path("allowedNumberOfValues").asInt());
        }
        for (JsonNode d : p.path("dictionary")) {
            cp.getDictionary().add(new CategoryParameter.DictionaryValue(d.path("id").asText(), d.path("value").asText()));
        }
        return cp;
    }

    /** Propozycje kategorii dla nazwy produktu (GET /sale/matching-categories). */
    public List<CategoryNode> matchCategories(AllegroAccount account, String productName) {
        JsonNode body = client.get(account, "/sale/matching-categories?name=" + enc(productName));
        List<CategoryNode> out = new ArrayList<>();
        JsonNode list = body.has("matchingCategories") ? body.path("matchingCategories") : firstArray(body);
        for (JsonNode c : list) {
            List<String> names = new ArrayList<>();
            JsonNode cur = c;
            int guard = 0;
            while (cur != null && !cur.isMissingNode() && !cur.isNull() && guard++ < 12) {
                names.add(cur.path("name").asText());
                cur = cur.path("parent");
            }
            Collections.reverse(names);
            out.add(new CategoryNode(c.path("id").asText(), String.join(" > ", names), true, null));
        }
        return out;
    }

    /** Szukanie produktu w Katalogu Allegro po kodzie GTIN (EAN/ISBN). */
    public List<CatalogProduct> searchByGtin(AllegroAccount account, String gtin) {
        JsonNode body = client.get(account, "/sale/products?mode=GTIN&language=pl-PL&phrase=" + enc(gtin));
        List<CatalogProduct> out = new ArrayList<>();
        for (JsonNode p : body.path("products")) {
            String img = p.path("images").isArray() && p.path("images").size() > 0
                    ? p.path("images").get(0).path("url").asText(null) : null;
            out.add(new CatalogProduct(
                    p.path("id").asText(),
                    p.path("name").asText(),
                    p.path("category").path("id").asText(null),
                    p.path("publication").path("status").asText(null),
                    img));
        }
        return out;
    }

    public void clearCache() {
        paramsCache.clear();
        categoryCache.clear();
    }

    private static CategoryNode toNode(JsonNode c) {
        String parent = c.path("parent").isObject() && c.path("parent").hasNonNull("id")
                ? c.path("parent").path("id").asText() : null;
        return new CategoryNode(c.path("id").asText(), c.path("name").asText(), c.path("leaf").asBoolean(false), parent);
    }

    static JsonNode firstArray(JsonNode body) {
        if (body != null) {
            var it = body.fields();
            while (it.hasNext()) {
                var e = it.next();
                if (e.getValue().isArray()) {
                    return e.getValue();
                }
            }
        }
        return com.fasterxml.jackson.databind.node.MissingNode.getInstance();
    }

    private static String key(AllegroAccount account, String id) {
        return account.getEnvironment() + ":" + id;
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
