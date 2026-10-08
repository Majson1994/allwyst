package pl.allegrolister.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import pl.allegrolister.allegro.AllegroOfferApi;
import pl.allegrolister.domain.AccountSettings;
import pl.allegrolister.domain.ListingItem;
import pl.allegrolister.domain.Product;
import pl.allegrolister.domain.SafetyMode;

/**
 * Składa body dla POST /sale/product-offers.
 * <ul>
 *   <li>tryb katalogowy: productSet[0].product = {id: UUID produktu z Katalogu Allegro}</li>
 *   <li>nowy produkt: productSet[0].product = {name, category, parameters, images}</li>
 * </ul>
 * Parametry produktowe trafiają do productSet[].product.parameters, ofertowe (np. Stan) do parameters.
 */
public final class OfferPayloadBuilder {

    private OfferPayloadBuilder() {
    }

    public record Input(
            ListingItem item,
            Product product,
            AccountSettings settings,
            boolean newProduct,
            String catalogProductId,
            String categoryId,
            ResolvedParameters params,
            boolean includeProductParams,
            List<String> images,
            List<Map<String, Object>> descriptionSections,
            boolean publish) {
    }

    public static Map<String, Object> build(Input in) {
        ListingItem item = in.item();
        Product product = in.product();
        AccountSettings s = in.settings();

        Map<String, Object> productNode = new LinkedHashMap<>();
        if (in.newProduct()) {
            productNode.put("name", TitleUtils.fit(product.getName()));
            productNode.put("category", Map.of("id", in.categoryId()));
            productNode.put("parameters", in.params().productParameters());
            if (!in.images().isEmpty()) {
                productNode.put("images", new ArrayList<>(in.images()));
            }
        } else {
            productNode.put("id", in.catalogProductId());
            if (in.includeProductParams()) {
                productNode.put("parameters", in.params().productParameters());
            }
        }

        Map<String, Object> productSetEntry = new LinkedHashMap<>();
        productSetEntry.put("product", productNode);
        putRef(productSetEntry, "responsibleProducer", firstNonBlank(item.getResponsibleProducerId(), s.getResponsibleProducerId()));
        putRef(productSetEntry, "responsiblePerson", firstNonBlank(item.getResponsiblePersonId(), s.getResponsiblePersonId()));
        Map<String, Object> safety = safetyInformation(product, s);
        if (safety != null) {
            productSetEntry.put("safetyInformation", safety);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("productSet", List.of(productSetEntry));
        body.put("name", TitleUtils.fit(item.getTitle() != null && !item.getTitle().isBlank() ? item.getTitle() : product.getName()));

        List<Map<String, Object>> offerParams = in.params().offerParameters();
        if (!offerParams.isEmpty()) {
            body.put("parameters", offerParams);
        }
        if (!in.images().isEmpty()) {
            body.put("images", new ArrayList<>(in.images()));
        }
        if (in.descriptionSections() != null && !in.descriptionSections().isEmpty()) {
            body.put("description", Map.of("sections", in.descriptionSections()));
        }

        Map<String, Object> sellingMode = new LinkedHashMap<>();
        sellingMode.put("format", "BUY_NOW");
        sellingMode.put("price", AllegroOfferApi.money(item.getPrice()));
        body.put("sellingMode", sellingMode);

        Map<String, Object> stock = new LinkedHashMap<>();
        stock.put("available", item.getQuantity());
        stock.put("unit", "UNIT");
        body.put("stock", stock);

        Map<String, Object> delivery = new LinkedHashMap<>();
        delivery.put("handlingTime", firstNonBlank(s.getHandlingTime(), "PT24H"));
        putRef(delivery, "shippingRates", firstNonBlank(item.getShippingRateId(), s.getShippingRateId()));
        body.put("delivery", delivery);

        Map<String, Object> afterSales = new LinkedHashMap<>();
        putRef(afterSales, "returnPolicy", firstNonBlank(item.getReturnPolicyId(), s.getReturnPolicyId()));
        putRef(afterSales, "impliedWarranty", firstNonBlank(item.getImpliedWarrantyId(), s.getImpliedWarrantyId()));
        putRef(afterSales, "warranty", firstNonBlank(item.getWarrantyId(), s.getWarrantyId()));
        if (!afterSales.isEmpty()) {
            body.put("afterSalesServices", afterSales);
        }

        body.put("payments", Map.of("invoice", firstNonBlank(s.getInvoiceType(), "VAT")));
        if (product.getSku() != null) {
            body.put("external", Map.of("id", product.getSku()));
        }
        body.put("publication", Map.of("status", in.publish() ? "ACTIVE" : "INACTIVE"));
        body.put("language", "pl-PL");
        return body;
    }

    static Map<String, Object> safetyInformation(Product product, AccountSettings s) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (product.getSafetyInfo() != null && !product.getSafetyInfo().isBlank()) {
            m.put("type", "TEXT");
            m.put("description", product.getSafetyInfo().trim());
            return m;
        }
        SafetyMode mode = s.getSafetyMode() == null ? SafetyMode.OMIT : s.getSafetyMode();
        switch (mode) {
            case NO_SAFETY_INFORMATION -> {
                m.put("type", "NO_SAFETY_INFORMATION");
                return m;
            }
            case TEXT -> {
                if (s.getDefaultSafetyText() == null || s.getDefaultSafetyText().isBlank()) {
                    return null;
                }
                m.put("type", "TEXT");
                m.put("description", s.getDefaultSafetyText().trim());
                return m;
            }
            default -> {
                return null;
            }
        }
    }

    private static void putRef(Map<String, Object> target, String key, String id) {
        if (id != null && !id.isBlank()) {
            target.put(key, Map.of("id", id));
        }
    }

    static String firstNonBlank(String a, String b) {
        return a != null && !a.isBlank() ? a : b;
    }
}
