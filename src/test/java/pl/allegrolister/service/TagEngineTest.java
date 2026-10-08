package pl.allegrolister.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import pl.allegrolister.domain.OfferTemplate;
import pl.allegrolister.domain.Product;
import pl.allegrolister.domain.SectionLayout;
import pl.allegrolister.domain.TemplateSection;

class TagEngineTest {

    @Test
    void replacesTagsInOnePass() {
        Product p = TestData.product();
        TagEngine.Context ctx = new TagEngine.Context(p, "Tytuł oferty", new BigDecimal("15"), 3);
        String out = TagEngine.apply("<h1>[nazwa]</h1>[opis] [cecha:kolor] [CENA] [ilosc] [nieznany]", ctx);
        assertEquals("<h1>Kubek ceramiczny &amp; spodek czerwony 300 ml</h1>"
                + "<p>Opis pierwszy</p><p>Druga linia [sku]</p> Zolty 15,00 zł 3 [nieznany]", out);
    }

    @Test
    void featuresListTag() {
        String out = TagEngine.apply("[cechy]", new TagEngine.Context(TestData.product(), null, null, null));
        assertTrue(out.startsWith("<ul><li><b>Kolor:</b> Zolty</li>"));
        assertTrue(out.endsWith("</ul>"));
    }

    @Test
    void resolvesImageTags() {
        List<String> images = Arrays.asList("A", null, "C");
        assertEquals("A", TagEngine.resolveImage("[zdjecie_1]", images));
        assertNull(TagEngine.resolveImage("[zdjecie_2]", images));
        assertEquals("C", TagEngine.resolveImage(" [ZDJECIE_3] ", images));
        assertNull(TagEngine.resolveImage("[zdjecie_4]", images));
        assertEquals("https://x/y.jpg", TagEngine.resolveImage("https://x/y.jpg", images));
        assertNull(TagEngine.resolveImage("tekst", images));
    }

    @Test
    @SuppressWarnings("unchecked")
    void rendersAllegroSections() {
        OfferTemplate t = new OfferTemplate();
        t.getSections().add(new TemplateSection(SectionLayout.IMAGE_TEXT, "<h1>[nazwa]</h1>[opis]", "[zdjecie_1]", null));
        t.getSections().add(new TemplateSection(SectionLayout.TEXT, "Producent: [producent]\nSKU: [sku]", null, null));
        t.getSections().add(new TemplateSection(SectionLayout.IMAGE_IMAGE, null, "[zdjecie_2]", "[zdjecie_3]"));
        t.getSections().add(new TemplateSection(SectionLayout.IMAGE, null, "[zdjecie_9]", null));
        Product p = TestData.product();
        List<Map<String, Object>> sections = DescriptionRenderer.render(t,
                new TagEngine.Context(p, p.getName(), p.getPrice(), p.getQuantity()), p.getImages());

        assertEquals(3, sections.size(), "sekcja bez zdjęcia ma zniknąć");
        List<Map<String, Object>> first = (List<Map<String, Object>>) sections.get(0).get("items");
        assertEquals("IMAGE", first.get(0).get("type"));
        assertEquals("https://example.com/1.jpg", first.get(0).get("url"));
        assertEquals("TEXT", first.get(1).get("type"));
        assertEquals("<h1>Kubek ceramiczny &amp; spodek czerwony 300 ml</h1><p>Opis pierwszy</p><p>Druga linia [sku]</p>",
                first.get(1).get("content"));
        List<Map<String, Object>> second = (List<Map<String, Object>>) sections.get(1).get("items");
        assertEquals("<p>Producent: Ceramix</p><p>SKU: KUB-001</p>", second.get(0).get("content"));
        List<Map<String, Object>> third = (List<Map<String, Object>>) sections.get(2).get("items");
        assertEquals(1, third.size(), "tylko istniejące zdjęcie");
    }

    @Test
    void titleFitsAllegroLimit() {
        String longName = "Bardzo długa nazwa produktu z mnóstwem słów kluczowych & dodatków które nie zmieszczą się w tytule";
        String fitted = TitleUtils.fit(longName);
        assertTrue(TitleUtils.allegroLength(fitted) <= 75);
        assertTrue(longName.startsWith(fitted));
        assertTrue(!fitted.endsWith(" "));
        assertEquals(5, TitleUtils.allegroLength("&"));
        assertEquals("Krótki", TitleUtils.fit("  Krótki  "));
    }
}
