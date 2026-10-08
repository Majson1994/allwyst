package pl.allegrolister.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import pl.allegrolister.allegro.model.CategoryParameter;
import pl.allegrolister.domain.AccountSettings;
import pl.allegrolister.domain.ListingItem;
import pl.allegrolister.domain.Product;
import pl.allegrolister.domain.SafetyMode;

class CsvAndPayloadTest {

    @Test
    void parsesSemicolonCsvWithQuotesAndNewlines() {
        String csv = "﻿sku;nazwa;opis\nA1;\"Kubek; duży\";\"Linia 1\nLinia \"\"2\"\"\"\r\nA2;Talerz;\n";
        List<List<String>> rows = CsvParser.parse(csv);
        assertEquals(3, rows.size());
        assertEquals(List.of("sku", "nazwa", "opis"), rows.get(0));
        assertEquals("Kubek; duży", rows.get(1).get(1));
        assertEquals("Linia 1\nLinia \"2\"", rows.get(1).get(2));
        assertEquals(List.of("A2", "Talerz", ""), rows.get(2));
    }

    @Test
    void sampleCsvHasConsistentColumns() throws Exception {
        try (var in = getClass().getResourceAsStream("/sample-products.csv")) {
            String content = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            List<List<String>> rows = CsvParser.parse(content);
            assertEquals(5, rows.size());
            for (List<String> row : rows) {
                assertEquals(14, row.size(), "liczba kolumn w wierszu " + row.get(0));
            }
            assertEquals("TAL-010", rows.get(3).get(0));
            assertTrue(rows.get(3).get(10).contains("\n"), "opis wieloliniowy w cudzysłowie");
            assertEquals(3, ProductTextFormat.parseImages(rows.get(1).get(9)).size());
            assertEquals(4, ProductTextFormat.parseFeatures(rows.get(1).get(11)).size());
        }
    }

    @Test
    void detectsCommaDelimiter() {
        assertEquals(',', CsvParser.detectDelimiter("sku,name,price\n1,2,3"));
        assertEquals('\t', CsvParser.detectDelimiter("sku\tname\tprice"));
    }

    @Test
    void parsesPolishNumbersAndFeatures() {
        assertEquals(new BigDecimal("1299.90"), ProductTextFormat.parseDecimal("1 299,90 zł"));
        assertNull(ProductTextFormat.parseDecimal("abc"));
        var features = ProductTextFormat.parseFeatures("Kolor=czerwony|Materiał: bawełna\nzła linia");
        assertEquals(2, features.size());
        assertEquals("Materiał", features.get(1).getName());
        assertEquals("bawełna", features.get(1).getValue());
    }

    @Test
    void priceCalculator() {
        AccountSettings s = new AccountSettings();
        s.setPriceMultiplier(new BigDecimal("1.1"));
        s.setPriceAddition(new BigDecimal("2"));
        assertEquals(new BigDecimal("15.75"), PriceCalculator.allegroPrice(new BigDecimal("12.50"), s));
    }

    @Test
    @SuppressWarnings("unchecked")
    void buildsNewProductPayload() {
        Product p = TestData.product();
        AccountSettings s = new AccountSettings();
        s.setShippingRateId("rate-1");
        s.setReturnPolicyId("ret-1");
        s.setSafetyMode(SafetyMode.NO_SAFETY_INFORMATION);
        ListingItem item = new ListingItem();
        item.setProduct(p);
        item.setTitle("Kubek ceramiczny czerwony");
        item.setPrice(new BigDecimal("19.9"));
        item.setQuantity(5);

        CategoryParameter stan = TestData.dict("11323", "Stan", false, "Nowy");
        CategoryParameter kolor = TestData.dict("10", "Kolor", true, "żółty");
        ResolvedParameters params = ParameterResolver.resolve(p, List.of(stan, kolor), List.of(), null, "100");

        Map<String, Object> body = OfferPayloadBuilder.build(new OfferPayloadBuilder.Input(item, p, s, true, null, "100",
                params, false, List.of("https://a.allegroimg.com/1"), List.of(), true));

        Map<String, Object> entry = ((List<Map<String, Object>>) body.get("productSet")).get(0);
        Map<String, Object> product = (Map<String, Object>) entry.get("product");
        assertEquals(Map.of("id", "100"), product.get("category"));
        assertEquals(List.of(Map.of("id", "10", "valuesIds", List.of("10_1"))), product.get("parameters"));
        assertEquals(Map.of("type", "NO_SAFETY_INFORMATION"), entry.get("safetyInformation"));
        assertEquals(List.of(Map.of("id", "11323", "valuesIds", List.of("11323_1"))), body.get("parameters"));
        assertEquals("Kubek ceramiczny czerwony", body.get("name"));
        assertEquals(Map.of("amount", "19.90", "currency", "PLN"), ((Map<String, Object>) body.get("sellingMode")).get("price"));
        assertEquals(Map.of("id", "rate-1"), ((Map<String, Object>) body.get("delivery")).get("shippingRates"));
        assertEquals(Map.of("returnPolicy", Map.of("id", "ret-1")), body.get("afterSalesServices"));
        assertEquals(Map.of("id", "KUB-001"), body.get("external"));
        assertEquals(Map.of("status", "ACTIVE"), body.get("publication"));
        assertFalse(body.containsKey("description"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void buildsCatalogPayload() {
        Product p = TestData.product();
        AccountSettings s = new AccountSettings();
        ListingItem item = new ListingItem();
        item.setProduct(p);
        item.setTitle("Tytuł");
        item.setPrice(new BigDecimal("10"));
        item.setQuantity(1);
        ResolvedParameters params = ParameterResolver.resolve(p, List.of(), List.of(), null, "100");
        Map<String, Object> body = OfferPayloadBuilder.build(new OfferPayloadBuilder.Input(item, p, s, false, "uuid-1", "100",
                params, false, List.of(), List.of(), false));
        Map<String, Object> entry = ((List<Map<String, Object>>) body.get("productSet")).get(0);
        assertEquals(Map.of("id", "uuid-1"), entry.get("product"));
        assertFalse(entry.containsKey("safetyInformation"));
        assertFalse(body.containsKey("afterSalesServices"));
        assertFalse(body.containsKey("images"));
        assertEquals(Map.of("status", "INACTIVE"), body.get("publication"));
        assertTrue(body.containsKey("delivery"));
    }
}
