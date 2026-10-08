package pl.allegrolister.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pl.allegrolister.allegro.AllegroApiException;
import pl.allegrolister.allegro.AllegroCatalogService;
import pl.allegrolister.allegro.model.CategoryNode;
import pl.allegrolister.allegro.model.CategoryParameter;
import pl.allegrolister.domain.AccountSettings;
import pl.allegrolister.domain.AllegroAccount;
import pl.allegrolister.domain.CategoryMapping;
import pl.allegrolister.domain.ListingItem;
import pl.allegrolister.domain.ListingJob;
import pl.allegrolister.domain.ListingMode;
import pl.allegrolister.domain.ListingStatus;
import pl.allegrolister.domain.OfferTemplate;
import pl.allegrolister.domain.Product;
import pl.allegrolister.repo.AllegroAccountRepository;
import pl.allegrolister.repo.CategoryMappingRepository;
import pl.allegrolister.repo.ListingItemRepository;
import pl.allegrolister.repo.ListingJobRepository;
import pl.allegrolister.repo.OfferTemplateRepository;
import pl.allegrolister.repo.ParameterRuleRepository;
import pl.allegrolister.repo.ProductRepository;
import pl.allegrolister.web.form.ItemForm;
import pl.allegrolister.web.form.ListingForm;

/**
 * Formularz wystawiania: tworzenie partii z produktów, uzupełnianie domyślnych wartości,
 * operacje grupowe, walidacja i zlecenie publikacji.
 */
@Service
public class ListingService {

    private static final int AUTO_MATCH_LIMIT = 100;
    private static final TypeReference<Map<String, List<String>>> OVERRIDES_TYPE = new TypeReference<>() {
    };

    private final ListingJobRepository jobs;
    private final ListingItemRepository items;
    private final ProductRepository products;
    private final AllegroAccountRepository accounts;
    private final OfferTemplateRepository templates;
    private final CategoryMappingRepository mappings;
    private final ParameterRuleRepository rules;
    private final AllegroCatalogService catalog;
    private final ListingPublisher publisher;
    private final ObjectMapper mapper;

    public ListingService(ListingJobRepository jobs, ListingItemRepository items, ProductRepository products,
                          AllegroAccountRepository accounts, OfferTemplateRepository templates,
                          CategoryMappingRepository mappings, ParameterRuleRepository rules,
                          AllegroCatalogService catalog, ListingPublisher publisher, ObjectMapper mapper) {
        this.jobs = jobs;
        this.items = items;
        this.products = products;
        this.accounts = accounts;
        this.templates = templates;
        this.mappings = mappings;
        this.rules = rules;
        this.catalog = catalog;
        this.publisher = publisher;
        this.mapper = mapper;
    }

    @Transactional
    public ListingJob createJob(Long accountId, List<Long> productIds) {
        AllegroAccount account = accounts.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Nie znaleziono konta"));
        ListingJob job = new ListingJob();
        job.setAccount(account);
        job.setName("Wystawianie " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));

        Map<String, CategoryMapping> mappingByCategory = new HashMap<>();
        for (CategoryMapping m : mappings.findAll()) {
            mappingByCategory.put(m.getShopCategory(), m);
        }
        for (Product product : products.findAllById(productIds)) {
            job.getItems().add(defaults(job, product, account, mappingByCategory));
        }
        jobs.save(job);

        if (account.getSettings().isAutoMatchCategory() && job.getItems().size() <= AUTO_MATCH_LIMIT) {
            for (ListingItem item : job.getItems()) {
                if (isBlank(item.getCategoryId())) {
                    matchCategory(account, item);
                }
            }
        }
        return job;
    }

    ListingItem defaults(ListingJob job, Product product, AllegroAccount account, Map<String, CategoryMapping> mappingByCategory) {
        AccountSettings s = account.getSettings();
        ListingItem item = new ListingItem();
        item.setJob(job);
        item.setProduct(product);
        item.setTitle(TitleUtils.fit(product.getName()));
        item.setListingMode(s.getDefaultListingMode() == null ? ListingMode.AUTO : s.getDefaultListingMode());
        item.setPrice(PriceCalculator.allegroPrice(product.getPrice(), s));
        item.setQuantity(Math.max(0, product.getQuantity()));

        CategoryMapping mapping = product.getShopCategory() == null ? null : mappingByCategory.get(product.getShopCategory());
        if (!isBlank(product.getAllegroCategoryId())) {
            item.setCategoryId(product.getAllegroCategoryId().trim());
            item.setCategoryName(safePathName(account, item.getCategoryId()));
        } else if (mapping != null && !isBlank(mapping.getAllegroCategoryId())) {
            item.setCategoryId(mapping.getAllegroCategoryId());
            item.setCategoryName(mapping.getAllegroCategoryName());
        } else if (!isBlank(s.getDefaultCategoryId())) {
            item.setCategoryId(s.getDefaultCategoryId());
            item.setCategoryName(s.getDefaultCategoryName());
        }
        item.setTemplateId(mapping != null && mapping.getTemplateId() != null ? mapping.getTemplateId() : s.getDefaultTemplateId());
        item.setShippingRateId(s.getShippingRateId());
        item.setReturnPolicyId(s.getReturnPolicyId());
        item.setImpliedWarrantyId(s.getImpliedWarrantyId());
        item.setWarrantyId(s.getWarrantyId());
        item.setResponsibleProducerId(s.getResponsibleProducerId());
        item.setResponsiblePersonId(s.getResponsiblePersonId());
        item.setStatus(ListingStatus.DRAFT);
        return item;
    }

    private String safePathName(AllegroAccount account, String categoryId) {
        try {
            return catalog.pathName(account, categoryId);
        } catch (RuntimeException e) {
            return categoryId;
        }
    }

    /** Dopasowanie kategorii przez Allegro (GET /sale/matching-categories) - pierwsza propozycja. */
    private boolean matchCategory(AllegroAccount account, ListingItem item) {
        try {
            String name = item.getProduct().getName();
            List<CategoryNode> found = catalog.matchCategories(account, name);
            if (!found.isEmpty()) {
                item.setCategoryId(found.get(0).id());
                item.setCategoryName(found.get(0).name());
                return true;
            }
        } catch (AllegroApiException e) {
            appendWarning(item, "Dopasowanie kategorii: " + e.getMessage());
        }
        return false;
    }

    @Transactional
    public int matchCategories(Long jobId, boolean onlyEmpty) {
        ListingJob job = job(jobId);
        int matched = 0;
        for (ListingItem item : job.getItems()) {
            if (!isEditable(item) || (onlyEmpty && !isBlank(item.getCategoryId()))) {
                continue;
            }
            if (matchCategory(job.getAccount(), item)) {
                matched++;
            }
        }
        return matched;
    }

    @Transactional
    public void saveForm(Long jobId, ListingForm form) {
        ListingJob job = job(jobId);
        Map<Long, ListingItem> byId = new HashMap<>();
        for (ListingItem i : job.getItems()) {
            byId.put(i.getId(), i);
        }
        for (ItemForm f : form.getItems()) {
            ListingItem item = f.getId() == null ? null : byId.get(f.getId());
            if (item == null || !isEditable(item)) {
                continue;
            }
            item.setTitle(f.getTitle() == null ? null : f.getTitle().trim());
            if (!java.util.Objects.equals(item.getCategoryId(), blankToNull(f.getCategoryId()))) {
                item.setCategoryId(blankToNull(f.getCategoryId()));
                item.setCategoryName(blankToNull(f.getCategoryName()));
                item.setParameterOverrides(null); // parametry zależą od kategorii
            } else if (!isBlank(f.getCategoryName())) {
                item.setCategoryName(f.getCategoryName());
            }
            if (f.getListingMode() != null) {
                item.setListingMode(f.getListingMode());
            }
            item.setAllegroProductId(blankToNull(f.getAllegroProductId()));
            BigDecimal price = ProductTextFormat.parseDecimal(f.getPrice());
            item.setPrice(price);
            item.setQuantity(Math.max(0, ProductTextFormat.parseInt(f.getQuantity(), item.getQuantity())));
            item.setTemplateId(f.getTemplateId());
            item.setShippingRateId(blankToNull(f.getShippingRateId()));
            item.setReturnPolicyId(blankToNull(f.getReturnPolicyId()));
            item.setImpliedWarrantyId(blankToNull(f.getImpliedWarrantyId()));
            item.setWarrantyId(blankToNull(f.getWarrantyId()));
            item.setResponsibleProducerId(blankToNull(f.getResponsibleProducerId()));
            item.setResponsiblePersonId(blankToNull(f.getResponsiblePersonId()));
        }
    }

    /**
     * Operacja grupowa - ustawia jedno pole we wszystkich edytowalnych pozycjach (albo zaznaczonych).
     */
    @Transactional
    public int applyBulk(Long jobId, List<Long> selectedIds, String field, String value, String valueLabel) {
        ListingJob job = job(jobId);
        int changed = 0;
        for (ListingItem item : job.getItems()) {
            if (!isEditable(item) || (selectedIds != null && !selectedIds.isEmpty() && !selectedIds.contains(item.getId()))) {
                continue;
            }
            String v = blankToNull(value);
            switch (field) {
                case "category" -> {
                    item.setCategoryId(v);
                    item.setCategoryName(blankToNull(valueLabel));
                    item.setParameterOverrides(null);
                }
                case "template" -> item.setTemplateId(v == null ? null : Long.valueOf(v));
                case "shippingRate" -> item.setShippingRateId(v);
                case "returnPolicy" -> item.setReturnPolicyId(v);
                case "impliedWarranty" -> item.setImpliedWarrantyId(v);
                case "warranty" -> item.setWarrantyId(v);
                case "responsibleProducer" -> item.setResponsibleProducerId(v);
                case "responsiblePerson" -> item.setResponsiblePersonId(v);
                case "listingMode" -> item.setListingMode(v == null ? ListingMode.AUTO : ListingMode.valueOf(v));
                case "quantity" -> item.setQuantity(Math.max(0, ProductTextFormat.parseInt(v, item.getQuantity())));
                case "pricePercent" -> {
                    BigDecimal pct = ProductTextFormat.parseDecimal(v);
                    if (pct != null && item.getPrice() != null) {
                        item.setPrice(item.getPrice().multiply(BigDecimal.ONE.add(pct.movePointLeft(2)))
                                .setScale(2, java.math.RoundingMode.HALF_UP));
                    }
                }
                case "priceFromProduct" -> item.setPrice(PriceCalculator.allegroPrice(item.getProduct().getPrice(), job.getAccount().getSettings()));
                case "titlePrefix" -> item.setTitle(TitleUtils.fit((v == null ? "" : v + " ") + item.getTitle()));
                case "titleSuffix" -> item.setTitle(TitleUtils.fit(item.getTitle() + (v == null ? "" : " " + v)));
                case "titleFromProduct" -> item.setTitle(TitleUtils.fit(item.getProduct().getName()));
                default -> throw new IllegalArgumentException("Nieznana operacja: " + field);
            }
            changed++;
        }
        return changed;
    }

    /**
     * Sprawdza pozycje przed wysłaniem: kategoria, cena, ilość, tytuł, zdjęcia i wymagane parametry.
     * Ostrzeżenia zapisuje w pozycji. Zwraca liczbę pozycji z problemami.
     */
    @Transactional
    public int validate(Long jobId) {
        ListingJob job = job(jobId);
        AllegroAccount account = job.getAccount();
        var allRules = rules.findByEnabledTrue();
        int withProblems = 0;
        for (ListingItem item : job.getItems()) {
            if (!isEditable(item)) {
                continue;
            }
            List<String> problems = new ArrayList<>();
            Product p = item.getProduct();
            if (item.getPrice() == null || item.getPrice().signum() <= 0) {
                problems.add("brak ceny");
            }
            if (item.getQuantity() < 1) {
                problems.add("ilość musi być większa od 0");
            }
            if (isBlank(item.getTitle())) {
                problems.add("brak tytułu");
            } else if (TitleUtils.allegroLength(item.getTitle()) > TitleUtils.MAX_LENGTH) {
                problems.add("tytuł dłuższy niż 75 znaków");
            }
            boolean likelyNewProduct = item.getListingMode() == ListingMode.NEW_PRODUCT
                    || (isBlank(p.getEan()) && isBlank(item.getAllegroProductId()));
            if (item.getListingMode() == ListingMode.CATALOG && isBlank(p.getEan()) && isBlank(item.getAllegroProductId())) {
                problems.add("tryb katalogowy wymaga EAN albo ID produktu Allegro");
            }
            if (likelyNewProduct && p.getImages().isEmpty()) {
                problems.add("brak zdjęć");
            }
            if (isBlank(item.getCategoryId())) {
                if (likelyNewProduct) {
                    problems.add("brak kategorii");
                }
            } else {
                try {
                    List<CategoryParameter> params = catalog.parameters(account, item.getCategoryId());
                    ResolvedParameters resolved = ParameterResolver.resolve(p, params, allRules, readOverrides(item), item.getCategoryId());
                    List<String> missing = resolved.missingRequired(likelyNewProduct);
                    if (!missing.isEmpty()) {
                        problems.add("brak parametrów: " + String.join(", ", missing));
                    }
                    problems.addAll(resolved.warnings());
                } catch (AllegroApiException e) {
                    problems.add("nie udało się pobrać parametrów kategorii: " + e.getMessage());
                }
            }
            item.setWarnings(problems.isEmpty() ? null : cut(String.join("; ", problems), 8000));
            if (!problems.isEmpty()) {
                withProblems++;
            }
        }
        return withProblems;
    }

    /**
     * Zleca wystawienie pozycji (DRAFT i ERROR, opcjonalnie tylko zaznaczonych). Metoda celowo bez
     * transakcji: status QUEUED musi być zapisany, zanim wątki publikujące odczytają pozycje.
     */
    public int publish(Long jobId, List<Long> selectedIds) {
        List<Long> toSend = new ArrayList<>();
        for (ListingItem item : items.findByJobIdOrderByIdAsc(jobId)) {
            if (!isEditable(item)) {
                continue;
            }
            if (selectedIds != null && !selectedIds.isEmpty() && !selectedIds.contains(item.getId())) {
                continue;
            }
            item.setStatus(ListingStatus.QUEUED);
            item.setMessage(null);
            items.save(item);
            toSend.add(item.getId());
        }
        for (Long id : toSend) {
            publisher.publishAsync(id);
        }
        return toSend.size();
    }

    @Transactional
    public void removeItem(Long itemId) {
        items.findById(itemId).ifPresent(item -> {
            ListingJob job = item.getJob();
            job.getItems().removeIf(i -> i.getId().equals(itemId));
        });
    }

    // --- parametry pozycji (edytor) ---

    public record ItemParameters(List<CategoryParameter> params, ResolvedParameters resolved, boolean newProduct) {
    }

    public ItemParameters parametersFor(ListingItem item) {
        List<CategoryParameter> params = catalog.parameters(item.getJob().getAccount(), item.getCategoryId());
        ResolvedParameters resolved = ParameterResolver.resolve(item.getProduct(), params, rules.findByEnabledTrue(),
                readOverrides(item), item.getCategoryId());
        boolean newProduct = item.getListingMode() == ListingMode.NEW_PRODUCT
                || (isBlank(item.getProduct().getEan()) && isBlank(item.getAllegroProductId()));
        return new ItemParameters(params, resolved, newProduct);
    }

    @Transactional
    public void saveOverrides(Long itemId, Map<String, List<String>> overrides) {
        ListingItem item = items.findById(itemId).orElseThrow();
        Map<String, List<String>> clean = new LinkedHashMap<>();
        overrides.forEach((k, v) -> {
            List<String> values = v.stream().filter(x -> x != null && !x.isBlank()).map(String::trim).toList();
            if (!values.isEmpty()) {
                clean.put(k, values);
            }
        });
        try {
            item.setParameterOverrides(clean.isEmpty() ? null : mapper.writeValueAsString(clean));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        items.save(item);
    }

    public Map<String, List<String>> readOverrides(ListingItem item) {
        if (isBlank(item.getParameterOverrides())) {
            return Map.of();
        }
        try {
            return mapper.readValue(item.getParameterOverrides(), OVERRIDES_TYPE);
        } catch (Exception e) {
            return Map.of();
        }
    }

    /** Podgląd opisu - z oryginalnymi adresami zdjęć produktu (bez wgrywania na Allegro). */
    public String previewDescription(ListingItem item) {
        Product p = item.getProduct();
        TagEngine.Context ctx = new TagEngine.Context(p, item.getTitle(), item.getPrice(), item.getQuantity());
        OfferTemplate t = item.getTemplateId() == null ? null : templates.findById(item.getTemplateId()).orElse(null);
        var sections = t != null
                ? DescriptionRenderer.render(t, ctx, p.getImages())
                : DescriptionRenderer.fromProductDescription(p.getDescription());
        return DescriptionRenderer.previewHtml(sections);
    }

    public ListingJob job(Long jobId) {
        return jobs.findById(jobId).orElseThrow(() -> new IllegalArgumentException("Nie znaleziono formularza #" + jobId));
    }

    public static boolean isEditable(ListingItem item) {
        return item.getStatus() == ListingStatus.DRAFT || item.getStatus() == ListingStatus.ERROR;
    }

    private static void appendWarning(ListingItem item, String w) {
        item.setWarnings(cut(item.getWarnings() == null ? w : item.getWarnings() + "; " + w, 8000));
    }

    private static String cut(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String blankToNull(String s) {
        return isBlank(s) ? null : s.trim();
    }
}
