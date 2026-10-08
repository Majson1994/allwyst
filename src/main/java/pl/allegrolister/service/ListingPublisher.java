package pl.allegrolister.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import pl.allegrolister.allegro.AllegroApiException;
import pl.allegrolister.allegro.AllegroCatalogService;
import pl.allegrolister.allegro.AllegroImageService;
import pl.allegrolister.allegro.AllegroOfferApi;
import pl.allegrolister.allegro.ApiResponse;
import pl.allegrolister.allegro.model.CatalogProduct;
import pl.allegrolister.allegro.model.CategoryParameter;
import pl.allegrolister.domain.AllegroAccount;
import pl.allegrolister.domain.AllegroOffer;
import pl.allegrolister.domain.ListingItem;
import pl.allegrolister.domain.ListingMode;
import pl.allegrolister.domain.ListingStatus;
import pl.allegrolister.domain.OfferTemplate;
import pl.allegrolister.domain.Product;
import pl.allegrolister.repo.AllegroOfferRepository;
import pl.allegrolister.repo.ListingItemRepository;
import pl.allegrolister.repo.OfferTemplateRepository;
import pl.allegrolister.repo.ParameterRuleRepository;

/**
 * Wystawia pojedynczą pozycję: dopasowanie do katalogu, parametry, zdjęcia, opis, POST /sale/product-offers,
 * a dla odpowiedzi 202 - odpytywanie statusu operacji (scheduler co 15 s).
 */
@Service
public class ListingPublisher {

    private static final Logger log = LoggerFactory.getLogger(ListingPublisher.class);
    private static final Duration OPERATION_TIMEOUT = Duration.ofHours(2);
    private static final Duration ACTIVATION_GRACE = Duration.ofMinutes(10);
    private static final TypeReference<Map<String, List<String>>> OVERRIDES_TYPE = new TypeReference<>() {
    };

    private final ListingItemRepository items;
    private final AllegroOfferRepository offers;
    private final OfferTemplateRepository templates;
    private final ParameterRuleRepository rules;
    private final AllegroCatalogService catalog;
    private final AllegroImageService images;
    private final AllegroOfferApi offerApi;
    private final EventLogService events;
    private final ObjectMapper mapper;

    public ListingPublisher(ListingItemRepository items, AllegroOfferRepository offers, OfferTemplateRepository templates,
                            ParameterRuleRepository rules, AllegroCatalogService catalog, AllegroImageService images,
                            AllegroOfferApi offerApi, EventLogService events, ObjectMapper mapper) {
        this.items = items;
        this.offers = offers;
        this.templates = templates;
        this.rules = rules;
        this.catalog = catalog;
        this.images = images;
        this.offerApi = offerApi;
        this.events = events;
        this.mapper = mapper;
    }

    @Async("listingExecutor")
    public void publishAsync(Long itemId) {
        publish(itemId);
    }

    public void publish(Long itemId) {
        ListingItem item = items.findById(itemId).orElse(null);
        if (item == null || item.getStatus() == ListingStatus.ACTIVE || item.getStatus() == ListingStatus.PENDING) {
            return;
        }
        AllegroAccount account = item.getJob().getAccount();
        Product product = item.getProduct();
        item.setStatus(ListingStatus.PROCESSING);
        item.setMessage(null);
        item.setSubmittedAt(Instant.now());
        item = items.save(item);

        List<String> notes = new ArrayList<>();
        try {
            if (item.getPrice() == null || item.getPrice().signum() <= 0) {
                throw new ListingException("Brak ceny");
            }
            if (item.getQuantity() < 1) {
                throw new ListingException("Ilość musi być większa od 0");
            }

            // 1. Produktyzacja: szukamy produktu w Katalogu Allegro po EAN
            String catalogProductId = blankToNull(item.getAllegroProductId());
            String categoryId = blankToNull(item.getCategoryId());
            if (catalogProductId == null && item.getListingMode() != ListingMode.NEW_PRODUCT && notBlank(product.getEan())) {
                List<CatalogProduct> found = catalog.searchByGtin(account, product.getEan().trim());
                CatalogProduct pick = found.stream().filter(CatalogProduct::isListed).findFirst()
                        .orElse(found.isEmpty() ? null : found.get(0));
                if (pick != null) {
                    catalogProductId = pick.id();
                    if (pick.categoryId() != null) {
                        categoryId = pick.categoryId();
                    }
                    notes.add("Powiązano z produktem z katalogu: " + pick.name());
                }
            }
            boolean newProduct = catalogProductId == null;
            if (newProduct && item.getListingMode() == ListingMode.CATALOG) {
                throw new ListingException("Nie znaleziono produktu w Katalogu Allegro (EAN: "
                        + (product.getEan() == null ? "brak" : product.getEan()) + "). Zmień tryb na automatyczny albo nowy produkt.");
            }
            if (categoryId == null) {
                throw new ListingException("Wybierz kategorię Allegro");
            }

            // 2. Parametry
            List<CategoryParameter> params = catalog.parameters(account, categoryId);
            ResolvedParameters resolved = ParameterResolver.resolve(product, params, rules.findByEnabledTrue(),
                    readOverrides(item), categoryId);
            List<String> missing = resolved.missingRequired(newProduct);
            if (!missing.isEmpty()) {
                throw new ListingException("Brak wymaganych parametrów: " + String.join(", ", missing)
                        + ". Uzupełnij je w edytorze parametrów albo dodaj regułę.");
            }

            // 3. Zdjęcia
            AllegroImageService.UploadResult upload = images.uploadAll(account, product.getImages());
            notes.addAll(upload.errors());
            if (newProduct && upload.urls().isEmpty()) {
                throw new ListingException("Brak zdjęć - nowy produkt wymaga co najmniej jednego zdjęcia");
            }

            // 4. Opis
            OfferTemplate template = item.getTemplateId() == null ? null : templates.findById(item.getTemplateId()).orElse(null);
            TagEngine.Context ctx = new TagEngine.Context(product, item.getTitle(), item.getPrice(), item.getQuantity());
            List<Map<String, Object>> sections = template != null
                    ? DescriptionRenderer.render(template, ctx, upload.positional())
                    : DescriptionRenderer.fromProductDescription(product.getDescription());

            // 5. Wysłanie
            boolean publishNow = account.getSettings().isPublishImmediately();
            Map<String, Object> payload = OfferPayloadBuilder.build(new OfferPayloadBuilder.Input(item, product,
                    account.getSettings(), newProduct, catalogProductId, categoryId, resolved, false,
                    upload.urls(), sections, publishNow));
            item.setLastRequest(toJson(payload));
            ApiResponse r = offerApi.create(account, payload);

            if (r.status() == 422 && !newProduct && AllegroApiException.from(r).hasCode("MissingRequiredParameters")) {
                // produkt z katalogu nie ma kompletu parametrów - dosyłamy nasze parametry produktowe
                payload = OfferPayloadBuilder.build(new OfferPayloadBuilder.Input(item, product,
                        account.getSettings(), false, catalogProductId, categoryId, resolved, true,
                        upload.urls(), sections, publishNow));
                item.setLastRequest(toJson(payload));
                r = offerApi.create(account, payload);
            }

            item.setCategoryId(categoryId);
            handleCreateResponse(item, account, product, r, notes);
        } catch (ListingException e) {
            fail(item, e.getMessage(), notes);
        } catch (AllegroApiException e) {
            fail(item, e.getMessage(), notes);
        } catch (RuntimeException e) {
            log.error("Błąd wystawiania pozycji {}", itemId, e);
            fail(item, "Błąd aplikacji: " + e.getMessage(), notes);
        }
    }

    private void handleCreateResponse(ListingItem item, AllegroAccount account, Product product, ApiResponse r, List<String> notes) {
        JsonNode body = r.body();
        if (r.status() == 201 || r.status() == 200) {
            String offerId = body.path("id").asText(null);
            item.setOfferId(offerId);
            List<String> validationErrors = validationErrors(body);
            String status = body.path("publication").path("status").asText("");
            registerOffer(account, item, product, offerId, status);
            if (!validationErrors.isEmpty()) {
                fail(item, "Oferta utworzona jako szkic, ale ma błędy: " + String.join("; ", validationErrors), notes);
                return;
            }
            if ("ACTIVE".equals(status) || !account.getSettings().isPublishImmediately()) {
                finish(item, "ACTIVE".equals(status) ? ListingStatus.ACTIVE : ListingStatus.INACTIVE, notes);
            } else {
                item.setStatus(ListingStatus.PENDING); // aktywacja w toku - sprawdzi scheduler
                item.setMessage(joinNotes("Oferta utworzona, trwa aktywacja.", notes));
                items.save(item);
            }
            events.info("LISTING", account.getId(), "Utworzono ofertę " + offerId + " (" + product.getSku() + ")");
        } else if (r.status() == 202) {
            String offerId = body == null ? null : body.path("id").asText(null);
            item.setOfferId(offerId);
            item.setOperationUrl(r.header("Location").orElse(null));
            item.setStatus(ListingStatus.PENDING);
            item.setMessage(joinNotes("Allegro weryfikuje ofertę (to może potrwać kilka minut).", notes));
            items.save(item);
            if (offerId != null) {
                registerOffer(account, item, product, offerId, "ACTIVATING");
            }
            events.info("LISTING", account.getId(), "Oferta " + offerId + " (" + product.getSku() + ") przyjęta do weryfikacji");
        } else {
            AllegroApiException e = AllegroApiException.from(r);
            fail(item, e.getMessage(), notes);
        }
    }

    /** Sprawdza oferty czekające na weryfikację Allegro. */
    @Scheduled(fixedDelay = 15000, initialDelay = 20000)
    public void pollPending() {
        for (ListingItem item : items.findByStatus(ListingStatus.PENDING)) {
            try {
                pollItem(item);
            } catch (AllegroApiException e) {
                log.warn("Błąd sprawdzania operacji dla pozycji {}: {}", item.getId(), e.getMessage());
            } catch (RuntimeException e) {
                log.error("Błąd sprawdzania pozycji {}", item.getId(), e);
            }
        }
    }

    void pollItem(ListingItem item) {
        AllegroAccount account = item.getJob().getAccount();
        Instant submitted = item.getSubmittedAt() == null ? Instant.now() : item.getSubmittedAt();
        if (item.getOperationUrl() != null) {
            ApiResponse r = offerApi.operation(account, item.getOperationUrl());
            if (r.status() == 202) {
                if (submitted.plus(OPERATION_TIMEOUT).isBefore(Instant.now())) {
                    fail(item, "Allegro nie zakończyło weryfikacji w ciągu 2 godzin - sprawdź ofertę na Allegro", List.of());
                }
                return;
            }
            if (r.status() != 303 && r.status() != 200 && r.status() != 404) {
                fail(item, "Weryfikacja nie powiodła się: " + AllegroApiException.from(r).getMessage(), List.of());
                return;
            }
            item.setOperationUrl(null);
        }
        if (item.getOfferId() == null) {
            fail(item, "Brak numeru oferty w odpowiedzi Allegro", List.of());
            return;
        }
        JsonNode offer = offerApi.get(account, item.getOfferId());
        String status = offer.path("publication").path("status").asText("");
        List<String> errors = validationErrors(offer);
        updateOfferStatus(account, item.getOfferId(), status);
        if (!errors.isEmpty()) {
            fail(item, "Allegro odrzuciło ofertę: " + String.join("; ", errors), List.of());
        } else if ("ACTIVE".equals(status)) {
            finish(item, ListingStatus.ACTIVE, List.of());
        } else if ("ENDED".equals(status)) {
            fail(item, "Oferta została zakończona", List.of());
        } else if ("INACTIVE".equals(status) && submitted.plus(ACTIVATION_GRACE).isBefore(Instant.now())) {
            finish(item, ListingStatus.INACTIVE, List.of("Oferta jest szkicem na Allegro - aktywuj ją w Zarządzaniu ofertami."));
        } else {
            items.save(item); // nadal ACTIVATING/INACTIVE - sprawdzimy ponownie
        }
    }

    private void registerOffer(AllegroAccount account, ListingItem item, Product product, String offerId, String status) {
        if (offerId == null) {
            return;
        }
        AllegroOffer offer = offers.findByAccountIdAndOfferId(account.getId(), offerId).orElseGet(AllegroOffer::new);
        offer.setAccount(account);
        offer.setOfferId(offerId);
        offer.setProduct(product);
        offer.setName(item.getTitle());
        offer.setPrice(item.getPrice());
        offer.setStock(item.getQuantity());
        offer.setStatus(status);
        offer.setCategoryId(item.getCategoryId());
        offer.setExternalId(product.getSku());
        offer.setTemplateId(item.getTemplateId());
        offer.setImageUrl(product.getMainImage());
        offer.setCreatedByApp(true);
        offer.setLastSyncAt(Instant.now());
        offers.save(offer);
    }

    private void updateOfferStatus(AllegroAccount account, String offerId, String status) {
        offers.findByAccountIdAndOfferId(account.getId(), offerId).ifPresent(o -> {
            o.setStatus(status);
            o.setLastSyncAt(Instant.now());
            offers.save(o);
        });
    }

    private void finish(ListingItem item, ListingStatus status, List<String> notes) {
        item.setStatus(status);
        item.setFinishedAt(Instant.now());
        item.setMessage(notes.isEmpty() ? null : cut(String.join("; ", notes)));
        items.save(item);
    }

    private void fail(ListingItem item, String message, List<String> notes) {
        item.setStatus(ListingStatus.ERROR);
        item.setFinishedAt(Instant.now());
        item.setMessage(cut(joinNotes(message, notes)));
        items.save(item);
        events.warn("LISTING", item.getJob().getAccount().getId(),
                "Błąd wystawiania " + item.getProduct().getSku() + ": " + message, item.getLastRequest());
    }

    static List<String> validationErrors(JsonNode body) {
        List<String> out = new ArrayList<>();
        if (body != null) {
            for (JsonNode e : body.path("validation").path("errors")) {
                String m = e.path("userMessage").asText("");
                out.add(m.isBlank() ? e.path("message").asText("") : m);
            }
        }
        return out;
    }

    private Map<String, List<String>> readOverrides(ListingItem item) {
        if (item.getParameterOverrides() == null || item.getParameterOverrides().isBlank()) {
            return Map.of();
        }
        try {
            return mapper.readValue(item.getParameterOverrides(), OVERRIDES_TYPE);
        } catch (Exception e) {
            return Map.of();
        }
    }

    private String toJson(Object o) {
        try {
            return cut(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(o), 100000);
        } catch (Exception e) {
            return null;
        }
    }

    private static String joinNotes(String message, List<String> notes) {
        if (notes == null || notes.isEmpty()) {
            return message;
        }
        return message + " | " + String.join("; ", notes);
    }

    private static String cut(String s) {
        return cut(s, 8000);
    }

    private static String cut(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    /** Błąd walidacji po stronie aplikacji (przed wysłaniem do Allegro). */
    static class ListingException extends RuntimeException {
        ListingException(String message) {
            super(message);
        }
    }
}
