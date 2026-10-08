package pl.allegrolister.service;

/**
 * Tytuł oferty Allegro: maks. 75 znaków, przy czym część znaków jest zamieniana na encje
 * i zajmuje więcej miejsca (np. & = 5 znaków).
 */
public final class TitleUtils {

    public static final int MAX_LENGTH = 75;

    private TitleUtils() {
    }

    public static int allegroLength(String title) {
        if (title == null) {
            return 0;
        }
        int len = 0;
        for (int i = 0; i < title.length(); i++) {
            len += charCost(title.charAt(i));
        }
        return len;
    }

    private static int charCost(char c) {
        return switch (c) {
            case '&' -> 5;   // &amp;
            case '"' -> 6;   // &quot;
            case '<', '>' -> 4; // &lt; &gt;
            default -> 1;
        };
    }

    /** Skraca tytuł do limitu Allegro, tnąc na granicy słowa, gdy to możliwe. */
    public static String fit(String title) {
        if (title == null) {
            return "";
        }
        String t = title.replaceAll("\\s+", " ").trim();
        if (allegroLength(t) <= MAX_LENGTH) {
            return t;
        }
        StringBuilder sb = new StringBuilder();
        int len = 0;
        for (int i = 0; i < t.length(); i++) {
            int cost = charCost(t.charAt(i));
            if (len + cost > MAX_LENGTH) {
                break;
            }
            sb.append(t.charAt(i));
            len += cost;
        }
        String cut = sb.toString();
        int lastSpace = cut.lastIndexOf(' ');
        if (lastSpace > MAX_LENGTH / 2) {
            cut = cut.substring(0, lastSpace);
        }
        return cut.trim();
    }
}
