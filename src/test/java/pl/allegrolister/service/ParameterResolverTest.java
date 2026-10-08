package pl.allegrolister.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import pl.allegrolister.allegro.model.CategoryParameter;
import pl.allegrolister.domain.MatchField;
import pl.allegrolister.domain.ParameterRule;
import pl.allegrolister.domain.Product;
import pl.allegrolister.domain.RuleType;

class ParameterResolverTest {

    private static ResolvedParameters.Entry resolveOne(Product p, CategoryParameter param, List<ParameterRule> rules,
                                                       Map<String, List<String>> overrides) {
        return ParameterResolver.resolve(p, List.of(param), rules, overrides, "100").getEntries().get(0);
    }

    @Test
    void matchesDictionaryIgnoringCaseAndPolishLetters() {
        CategoryParameter kolor = TestData.dict("10", "Kolor", true, "czerwony", "żółty");
        ResolvedParameters.Entry e = resolveOne(TestData.product(), kolor, List.of(), null);
        assertTrue(e.isResolved());
        assertEquals(List.of("10_2"), e.getValuesIds());
        assertEquals("żółty", e.getDisplayValue());
    }

    @Test
    void constantIfContainsWithWildcard() {
        CategoryParameter kolor = TestData.dict("10", "Kolor", true, "czerwony", "żółty");
        ParameterRule r = TestData.rule(1, RuleType.CONSTANT_IF_CONTAINS, "kolor");
        r.setMatchField(MatchField.NAME);
        r.setPhrase("czerw*");
        r.setValue("Czerwony");
        r.setPriority(1);
        ResolvedParameters.Entry e = resolveOne(TestData.product(), kolor, List.of(r), null);
        assertEquals(List.of("10_1"), e.getValuesIds());
        assertEquals("reguła #1", e.getSource());
    }

    @Test
    void categorySpecificRuleWinsOverGlobal() {
        CategoryParameter stan = TestData.dict("11323", "Stan", false, "Nowy", "Używany");
        ParameterRule global = TestData.rule(1, RuleType.CONSTANT, "Stan");
        global.setValue("Nowy");
        global.setPriority(1);
        ParameterRule specific = TestData.rule(2, RuleType.CONSTANT, "Stan");
        specific.setValue("Używany");
        specific.setAllegroCategoryId("100");
        specific.setPriority(50);
        ResolvedParameters.Entry e = resolveOne(TestData.product(), stan, List.of(global, specific), null);
        assertEquals(List.of("11323_2"), e.getValuesIds());
    }

    @Test
    void valueMapTranslatesFeatureValues() {
        Product p = TestData.product();
        p.getFeatures().add(new pl.allegrolister.domain.ProductFeature("Color", "red"));
        CategoryParameter kolor = TestData.dict("10", "Kolor", true, "czerwony", "żółty");
        ParameterRule r = TestData.rule(3, RuleType.FROM_FEATURE, "Kolor");
        r.setFeatureName("Color");
        r.setValueMap("blue=niebieski;red=czerwony");
        ResolvedParameters.Entry e = resolveOne(p, kolor, List.of(r), null);
        assertEquals(List.of("10_1"), e.getValuesIds());
    }

    @Test
    void eanBrandAndDefaultCondition() {
        Product p = TestData.product();
        CategoryParameter ean = TestData.simple("225693", "EAN (GTIN)", "string");
        CategoryParameter marka = TestData.dict("20", "Marka", true, "Ceramix", "Inna");
        CategoryParameter stan = TestData.dict("11323", "Stan", false, "Nowy", "Używany");
        ResolvedParameters r = ParameterResolver.resolve(p, List.of(ean, marka, stan), List.of(), null, "100");
        assertEquals(List.of("5901234123457"), r.entry("225693").getValues());
        assertEquals("EAN", r.entry("225693").getSource());
        assertEquals(List.of("20_1"), r.entry("20").getValuesIds());
        assertEquals(List.of("11323_1"), r.entry("11323").getValuesIds());
        assertEquals(1, r.offerParameters().size());
        assertEquals(2, r.productParameters().size());
    }

    @Test
    void unknownDictionaryValueGivesWarning() {
        CategoryParameter marka = TestData.dict("20", "Marka", true, "Inna marka");
        ResolvedParameters.Entry e = resolveOne(TestData.product(), marka, List.of(), null);
        assertFalse(e.isResolved());
        assertNotNull(e.getWarning());
    }

    @Test
    void customValuesAllowedWhenDictionaryPermits() {
        CategoryParameter marka = TestData.dict("20", "Marka", true, "Inna marka");
        marka.setCustomValuesEnabled(true);
        ResolvedParameters.Entry e = resolveOne(TestData.product(), marka, List.of(), null);
        assertTrue(e.isResolved());
        assertEquals(List.of("Ceramix"), e.getValues());
    }

    @Test
    void numbersAreNormalized() {
        Product p = TestData.product();
        CategoryParameter capacity = TestData.simple("30", "Pojemność", "float");
        assertEquals(List.of("300"), resolveOne(p, capacity, List.of(), null).getValues());
        p.getFeatures().add(new pl.allegrolister.domain.ProductFeature("Średnica", "8,50 cm"));
        assertEquals(List.of("8.5"), resolveOne(p, TestData.simple("31", "Średnica", "float"), List.of(), null).getValues());
        p.getFeatures().add(new pl.allegrolister.domain.ProductFeature("Liczba sztuk", "2,6 szt"));
        assertEquals(List.of("3"), resolveOne(p, TestData.simple("32", "Liczba sztuk", "integer"), List.of(), null).getValues());
    }

    @Test
    void weightFromProductWithUnitConversion() {
        CategoryParameter weightG = TestData.simple("40", "Waga produktu", "float");
        weightG.setUnit("g");
        assertEquals(List.of("450"), resolveOne(TestData.product(), weightG, List.of(), null).getValues());
    }

    @Test
    void rangeParameter() {
        Product p = TestData.product();
        p.getFeatures().add(new pl.allegrolister.domain.ProductFeature("Wiek dziecka", "3 - 6"));
        CategoryParameter range = TestData.simple("50", "Wiek dziecka", "integer");
        range.setRange(true);
        ResolvedParameters.Entry e = resolveOne(p, range, List.of(), null);
        assertEquals("3", e.getRangeFrom());
        assertEquals("6", e.getRangeTo());
        assertEquals(Map.of("from", "3", "to", "6"), e.toApi().get("rangeValue"));
    }

    @Test
    void multipleChoiceDictionarySplitsValues() {
        CategoryParameter material = TestData.dict("60", "Materiał", true, "ceramika", "szkło", "metal");
        material.setMultipleChoices(true);
        ResolvedParameters.Entry e = resolveOne(TestData.product(), material, List.of(), null);
        assertEquals(List.of("60_1", "60_2"), e.getValuesIds());
    }

    @Test
    void manualOverrideWins() {
        CategoryParameter kolor = TestData.dict("10", "Kolor", true, "czerwony", "żółty");
        ResolvedParameters.Entry e = resolveOne(TestData.product(), kolor, List.of(), Map.of("10", List.of("10_1")));
        assertEquals(List.of("10_1"), e.getValuesIds());
        assertTrue(e.isOverridden());
    }

    @Test
    void missingRequiredDependsOnMode() {
        CategoryParameter model = TestData.simple("70", "Model", "string");
        model.setRequiredForProduct(true);
        CategoryParameter stan = TestData.dict("11323", "Stan", false, "Nowy");
        stan.setRequired(true);
        ResolvedParameters r = ParameterResolver.resolve(TestData.product(), List.of(model, stan), List.of(), null, "100");
        assertEquals(List.of("Model"), r.missingRequired(true));
        assertEquals(List.of(), r.missingRequired(false));
    }
}
