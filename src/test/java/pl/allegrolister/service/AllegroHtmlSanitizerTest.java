package pl.allegrolister.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class AllegroHtmlSanitizerTest {

    @Test
    void mapsHeadingsBoldAndDropsScriptsAndUnknownTags() {
        String in = "<div class=\"x\"><h3>Tytuł</h3><p>Tekst <strong>pogrubiony</strong> i <span style='c'>span</span></p>"
                + "<script>alert(1)</script><img src=\"a.jpg\"></div>";
        assertEquals("<h2>Tytuł</h2><p>Tekst <b>pogrubiony</b> i span</p>", AllegroHtmlSanitizer.sanitize(in));
    }

    @Test
    void plainTextBecomesParagraphs() {
        assertEquals("<p>Linia 1</p><p>Linia 2</p>", AllegroHtmlSanitizer.sanitize("Linia 1\n\nLinia 2\n"));
    }

    @Test
    void keepsListsAndBoldInsideItems() {
        assertEquals("<ul><li>A</li><li><b>B</b> c</li></ul>",
                AllegroHtmlSanitizer.sanitize("<ul>\n <li>A</li>\n <li><b>B</b> c</li>\n</ul>"));
    }

    @Test
    void lineBreakSplitsParagraph() {
        assertEquals("<p>Hello</p><p>World</p>", AllegroHtmlSanitizer.sanitize("Hello<br>World"));
    }

    @Test
    void decodesAndReEscapesEntities() {
        assertEquals("<p>ó&amp;&lt;tag&gt; x</p>", AllegroHtmlSanitizer.sanitize("<p>&oacute;&amp;&lt;tag&gt; &nbsp;x</p>"));
    }

    @Test
    void flattensNestedLists() {
        assertEquals("<ul><li>a</li><li>b</li><li>c</li></ul>",
                AllegroHtmlSanitizer.sanitize("<ul><li>a<ul><li>b</li></ul></li><li>c</li></ul>"));
    }

    @Test
    void wrapsOrphanListItems() {
        assertEquals("<ul><li>x</li></ul>", AllegroHtmlSanitizer.sanitize("<li>x</li>"));
    }

    @Test
    void tableCellsBecomeText() {
        assertEquals("<p>A B</p>", AllegroHtmlSanitizer.sanitize("<table><tr><td>A</td><td>B</td></tr></table>"));
    }

    @Test
    void dropsEmptyElements() {
        assertEquals("", AllegroHtmlSanitizer.sanitize("<p> </p><div></div><b></b>"));
    }

    @Test
    void boldContinuesAcrossParagraphs() {
        assertEquals("<p><b>a</b></p><p><b>b</b></p>", AllegroHtmlSanitizer.sanitize("<b>a<br>b</b>"));
    }

    @Test
    void noBoldInHeadings() {
        assertEquals("<h1>X</h1><p>y</p>", AllegroHtmlSanitizer.sanitize("<h1><b>X</b></h1>y"));
    }

    @Test
    void orderedListStaysOrdered() {
        assertEquals("<ol><li>jeden</li><li>dwa</li></ol>", AllegroHtmlSanitizer.sanitize("<ol><li>jeden<li>dwa</ol>"));
    }
}
