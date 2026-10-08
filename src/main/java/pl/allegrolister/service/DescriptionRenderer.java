package pl.allegrolister.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import pl.allegrolister.domain.OfferTemplate;
import pl.allegrolister.domain.TemplateSection;

/**
 * Buduje opis oferty w formacie Allegro: {"sections":[{"items":[{"type":"TEXT","content":"..."},{"type":"IMAGE","url":"..."}]}]}.
 */
public final class DescriptionRenderer {

    private DescriptionRenderer() {
    }

    /**
     * @param images adresy zdjęć w kolejności produktu (pozycja N = [zdjecie_N]); null = brak zdjęcia
     */
    public static List<Map<String, Object>> render(OfferTemplate template, TagEngine.Context ctx, List<String> images) {
        List<Map<String, Object>> sections = new ArrayList<>();
        for (TemplateSection s : template.getSections()) {
            if (s.getLayout() == null) {
                continue;
            }
            List<Map<String, Object>> items = new ArrayList<>();
            String text = s.getLayout().isHasText() ? renderText(s.getText(), ctx) : "";
            String img1 = s.getLayout().isHasImage() ? TagEngine.resolveImage(s.getImage1(), images) : null;
            String img2 = s.getLayout().isTwoImages() ? TagEngine.resolveImage(s.getImage2(), images) : null;
            switch (s.getLayout()) {
                case TEXT -> addText(items, text);
                case IMAGE -> addImage(items, img1);
                case IMAGE_TEXT -> {
                    addImage(items, img1);
                    addText(items, text);
                }
                case TEXT_IMAGE -> {
                    addText(items, text);
                    addImage(items, img1);
                }
                case IMAGE_IMAGE -> {
                    addImage(items, img1);
                    addImage(items, img2);
                }
            }
            if (!items.isEmpty()) {
                sections.add(section(items));
            }
        }
        return sections;
    }

    /** Opis bez szablonu - sam opis produktu w jednej sekcji tekstowej. */
    public static List<Map<String, Object>> fromProductDescription(String description) {
        String html = AllegroHtmlSanitizer.sanitize(description);
        List<Map<String, Object>> sections = new ArrayList<>();
        if (!html.isEmpty()) {
            List<Map<String, Object>> items = new ArrayList<>();
            addText(items, html);
            sections.add(section(items));
        }
        return sections;
    }

    static String renderText(String templateText, TagEngine.Context ctx) {
        String withParagraphs = AllegroHtmlSanitizer.ensureHtml(templateText);
        return AllegroHtmlSanitizer.sanitize(TagEngine.apply(withParagraphs, ctx));
    }

    /** HTML podglądu opisu (przybliżenie wyglądu na Allegro). */
    @SuppressWarnings("unchecked")
    public static String previewHtml(List<Map<String, Object>> sections) {
        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> section : sections) {
            List<Map<String, Object>> items = (List<Map<String, Object>>) section.get("items");
            sb.append("<div class=\"desc-section items-").append(items.size()).append("\">");
            for (Map<String, Object> item : items) {
                if ("IMAGE".equals(item.get("type"))) {
                    sb.append("<div class=\"desc-item desc-image\"><img src=\"")
                            .append(TagEngine.escape(String.valueOf(item.get("url")))).append("\" alt=\"\"></div>");
                } else {
                    sb.append("<div class=\"desc-item desc-text\">").append(item.get("content")).append("</div>");
                }
            }
            sb.append("</div>");
        }
        return sb.toString();
    }

    private static void addText(List<Map<String, Object>> items, String html) {
        if (html != null && !html.isBlank()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("type", "TEXT");
            item.put("content", html);
            items.add(item);
        }
    }

    private static void addImage(List<Map<String, Object>> items, String url) {
        if (url != null && !url.isBlank()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("type", "IMAGE");
            item.put("url", url);
            items.add(item);
        }
    }

    private static Map<String, Object> section(List<Map<String, Object>> items) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("items", items);
        return s;
    }
}
