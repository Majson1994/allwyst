package pl.allegrolister.service;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Zamienia dowolny HTML (np. opis ze sklepu) na HTML dozwolony w opisach Allegro:
 * tylko &lt;h1&gt;, &lt;h2&gt;, &lt;p&gt;, &lt;ul&gt;, &lt;ol&gt;, &lt;li&gt; i &lt;b&gt;, bez atrybutów.
 * <ul>
 *   <li>h3-h6 -&gt; h2, strong -&gt; b, div/table/br itp. -&gt; podział akapitu</li>
 *   <li>tekst poza blokami trafia do &lt;p&gt;, &lt;b&gt; tylko wewnątrz &lt;p&gt; i &lt;li&gt;</li>
 *   <li>script/style/iframe są usuwane razem z treścią, puste elementy są pomijane</li>
 * </ul>
 */
public final class AllegroHtmlSanitizer {

    private static final Pattern COMMENTS = Pattern.compile("(?s)<!--.*?-->");
    private static final Pattern REMOVE_BLOCKS = Pattern.compile(
            "(?is)<(script|style|head|iframe|noscript|object|svg|template)\\b[^>]*>.*?</\\1\\s*>");
    private static final Pattern DECLARATIONS = Pattern.compile("(?i)<![^>]*>|<\\?[^>]*>");
    private static final Pattern TAG = Pattern.compile("<(/?)([a-zA-Z][a-zA-Z0-9]*)\\b[^>]*?(/?)>");
    private static final Pattern HAS_TAG = Pattern.compile("</?[a-zA-Z][a-zA-Z0-9]*\\b[^>]*>");
    private static final Pattern ENTITY = Pattern.compile("&(#[0-9]+|#[xX][0-9a-fA-F]+|[a-zA-Z][a-zA-Z0-9]*);");

    private static final Set<String> BLOCK_BREAK = Set.of("p", "div", "section", "article", "header", "footer",
            "blockquote", "pre", "table", "thead", "tbody", "tfoot", "tr", "center", "address", "figure",
            "figcaption", "dl", "dt", "dd", "main", "aside", "nav", "form", "fieldset", "hr", "caption");

    private static final Map<String, String> NAMED = new HashMap<>();

    static {
        String[][] entities = {
                {"nbsp", " "}, {"amp", "&"}, {"lt", "<"}, {"gt", ">"}, {"quot", "\""}, {"apos", "'"},
                {"oacute", "ó"}, {"Oacute", "Ó"}, {"ndash", "–"}, {"mdash", "—"}, {"hellip", "…"},
                {"laquo", "«"}, {"raquo", "»"}, {"bdquo", "„"}, {"rdquo", "”"}, {"ldquo", "“"},
                {"rsquo", "’"}, {"lsquo", "‘"}, {"deg", "°"}, {"times", "×"}, {"euro", "€"},
                {"copy", "©"}, {"reg", "®"}, {"trade", "™"}, {"sup2", "²"}, {"sup3", "³"},
                {"middot", "·"}, {"bull", "•"}, {"frac12", "½"}, {"micro", "µ"}, {"plusmn", "±"}
        };
        for (String[] e : entities) {
            NAMED.put(e[0], e[1]);
        }
    }

    private AllegroHtmlSanitizer() {
    }

    /** Czysty tekst (bez znaczników HTML) -> akapity &lt;p&gt; linia po linii. HTML zostaje bez zmian. */
    public static String ensureHtml(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        if (HAS_TAG.matcher(text).find()) {
            return text;
        }
        StringBuilder sb = new StringBuilder();
        for (String line : text.split("\\r?\\n")) {
            if (!line.isBlank()) {
                sb.append("<p>").append(line.trim()).append("</p>");
            }
        }
        return sb.toString();
    }

    public static String sanitize(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }
        String html = ensureHtml(input);
        html = COMMENTS.matcher(html).replaceAll(" ");
        html = REMOVE_BLOCKS.matcher(html).replaceAll(" ");
        html = DECLARATIONS.matcher(html).replaceAll(" ");

        Builder b = new Builder();
        Matcher m = TAG.matcher(html);
        int last = 0;
        while (m.find()) {
            b.text(decodeEntities(html.substring(last, m.start())));
            b.tag(m.group(2).toLowerCase(Locale.ROOT), !m.group(1).isEmpty(), !m.group(3).isEmpty());
            last = m.end();
        }
        b.text(decodeEntities(html.substring(last)));
        return b.finish();
    }

    /** Usuwa wszystkie znaczniki - do podglądów i porównań. */
    public static String toPlainText(String html) {
        if (html == null) {
            return "";
        }
        String noTags = TAG.matcher(REMOVE_BLOCKS.matcher(html).replaceAll(" ")).replaceAll(" ");
        return decodeEntities(noTags).replaceAll("\\s+", " ").trim();
    }

    static String decodeEntities(String s) {
        if (s.indexOf('&') < 0) {
            return s;
        }
        Matcher m = ENTITY.matcher(s);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String code = m.group(1);
            String rep;
            try {
                if (code.startsWith("#x") || code.startsWith("#X")) {
                    rep = new String(Character.toChars(Integer.parseInt(code.substring(2), 16)));
                } else if (code.startsWith("#")) {
                    rep = new String(Character.toChars(Integer.parseInt(code.substring(1))));
                } else {
                    rep = NAMED.getOrDefault(code, m.group());
                }
            } catch (IllegalArgumentException e) {
                rep = m.group();
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(rep));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    /** Maszyna stanów budująca poprawny HTML Allegro. */
    private static final class Builder {
        private final StringBuilder out = new StringBuilder();
        private StringBuilder buf = new StringBuilder();
        /** Bieżący blok: p / h1 / h2 / li albo null. */
        private String block;
        private String listTag;
        private StringBuilder listBuf;
        private int listDepth;
        private int boldDepth;
        private boolean bOpen;

        void text(String raw) {
            if (raw.isEmpty()) {
                return;
            }
            String t = raw.replaceAll("[\\s\\u00A0]+", " ");
            if (t.isBlank()) {
                if (block != null) {
                    appendSpace();
                }
                return;
            }
            ensureTextContainer();
            if (boldDepth > 0 && !bOpen && boldAllowed()) {
                buf.append("<b>");
                bOpen = true;
            }
            if (buf.isEmpty() || endsWithOpeningTag()) {
                t = t.stripLeading();
            }
            buf.append(escape(t));
        }

        void tag(String name, boolean closing, boolean selfClosing) {
            switch (name) {
                case "b", "strong" -> {
                    if (closing) {
                        boldDepth = Math.max(0, boldDepth - 1);
                        if (boldDepth == 0) {
                            closeBold();
                        }
                    } else if (!selfClosing) {
                        boldDepth++;
                    }
                }
                case "h1" -> heading("h1", closing);
                case "h2", "h3", "h4", "h5", "h6" -> heading("h2", closing);
                case "ul", "ol" -> {
                    if (closing) {
                        closeList(false);
                    } else {
                        openList(name);
                    }
                }
                case "li" -> {
                    if (closing) {
                        closeLi();
                    } else {
                        if (listTag == null) {
                            openList("ul");
                        }
                        closeLi();
                        openLi();
                    }
                }
                case "br" -> lineBreak();
                case "td", "th" -> {
                    if (block != null) {
                        appendSpace();
                    }
                }
                default -> {
                    if (BLOCK_BREAK.contains(name)) {
                        lineBreak();
                    }
                    // pozostałe znaczniki (span, a, em, img...) są usuwane, treść zostaje
                }
            }
        }

        private void heading(String tag, boolean closing) {
            if (!closing) {
                closeAll();
                block = tag;
                buf = new StringBuilder();
            } else if ("h1".equals(block) || "h2".equals(block)) {
                closeBlock();
            }
        }

        private void lineBreak() {
            if ("li".equals(block) || "h1".equals(block) || "h2".equals(block)) {
                appendSpace();
            } else if (listTag == null) {
                closeBlock();
            }
        }

        private void ensureTextContainer() {
            if (listTag != null) {
                if (!"li".equals(block)) {
                    openLi();
                }
            } else if (block == null) {
                block = "p";
                buf = new StringBuilder();
            }
        }

        private boolean boldAllowed() {
            return "p".equals(block) || "li".equals(block);
        }

        private void appendSpace() {
            if (!buf.isEmpty() && buf.charAt(buf.length() - 1) != ' ' && !endsWithOpeningTag()) {
                buf.append(' ');
            }
        }

        private boolean endsWithOpeningTag() {
            return buf.length() >= 3 && buf.substring(buf.length() - 3).equals("<b>");
        }

        private void closeBold() {
            if (bOpen) {
                trimTrailingSpace();
                buf.append("</b>");
                bOpen = false;
            }
        }

        private void trimTrailingSpace() {
            while (!buf.isEmpty() && buf.charAt(buf.length() - 1) == ' ') {
                buf.setLength(buf.length() - 1);
            }
        }

        private String takeContent() {
            closeBold();
            String content = buf.toString().replaceAll("<b>\\s*</b>", " ").trim();
            buf = new StringBuilder();
            return content;
        }

        private void closeBlock() {
            if (block == null || "li".equals(block)) {
                return;
            }
            String content = takeContent();
            if (!content.isEmpty()) {
                out.append('<').append(block).append('>').append(content).append("</").append(block).append('>');
            }
            block = null;
        }

        private void openList(String tag) {
            closeBlock();
            if (listTag != null) {
                listDepth++; // zagnieżdżone listy są spłaszczane
                closeLi();
                return;
            }
            listTag = tag;
            listBuf = new StringBuilder();
            listDepth = 1;
        }

        private void openLi() {
            block = "li";
            buf = new StringBuilder();
        }

        private void closeLi() {
            if (!"li".equals(block)) {
                return;
            }
            String content = takeContent();
            if (!content.isEmpty()) {
                listBuf.append("<li>").append(content).append("</li>");
            }
            block = null;
        }

        private void closeList(boolean force) {
            if (listTag == null) {
                return;
            }
            closeLi();
            listDepth--;
            if (listDepth > 0 && !force) {
                return;
            }
            if (!listBuf.isEmpty()) {
                out.append('<').append(listTag).append('>').append(listBuf).append("</").append(listTag).append('>');
            }
            listTag = null;
            listBuf = null;
            listDepth = 0;
        }

        private void closeAll() {
            closeBlock();
            closeList(true);
        }

        String finish() {
            closeAll();
            return out.toString();
        }
    }
}
