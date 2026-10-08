package pl.allegrolister.service;

import java.math.BigDecimal;
import java.util.List;

import pl.allegrolister.allegro.model.CategoryParameter;
import pl.allegrolister.domain.ParameterRule;
import pl.allegrolister.domain.Product;
import pl.allegrolister.domain.ProductFeature;
import pl.allegrolister.domain.RuleType;

final class TestData {

    private TestData() {
    }

    static Product product() {
        Product p = new Product();
        p.setSku("KUB-001");
        p.setEan("5901234123457");
        p.setName("Kubek ceramiczny & spodek czerwony 300 ml");
        p.setManufacturer("Ceramix");
        p.setPrice(new BigDecimal("12.50"));
        p.setQuantity(7);
        p.setWeight(new BigDecimal("0.45"));
        p.setShopCategory("Kuchnia/Kubki");
        p.setDescription("Opis pierwszy\nDruga linia [sku]");
        p.getImages().addAll(List.of("https://example.com/1.jpg", "https://example.com/2.jpg"));
        p.getFeatures().add(new ProductFeature("Kolor", "Zolty"));
        p.getFeatures().add(new ProductFeature("Pojemność", "300 ml"));
        p.getFeatures().add(new ProductFeature("Materiał", "ceramika, szkło"));
        return p;
    }

    static CategoryParameter dict(String id, String name, boolean describesProduct, String... values) {
        CategoryParameter p = new CategoryParameter();
        p.setId(id);
        p.setName(name);
        p.setType("dictionary");
        p.setDescribesProduct(describesProduct);
        for (int i = 0; i < values.length; i++) {
            p.getDictionary().add(new CategoryParameter.DictionaryValue(id + "_" + (i + 1), values[i]));
        }
        return p;
    }

    static CategoryParameter simple(String id, String name, String type) {
        CategoryParameter p = new CategoryParameter();
        p.setId(id);
        p.setName(name);
        p.setType(type);
        p.setDescribesProduct(true);
        return p;
    }

    static ParameterRule rule(long id, RuleType type, String paramName) {
        ParameterRule r = new ParameterRule();
        r.setId(id);
        r.setRuleType(type);
        r.setParameterName(paramName);
        return r;
    }
}
