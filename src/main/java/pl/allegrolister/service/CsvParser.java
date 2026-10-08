package pl.allegrolister.service;

import java.util.ArrayList;
import java.util.List;

/**
 * Prosty parser CSV (RFC 4180): cudzysłowy, "" w polu, nowe linie w cudzysłowie.
 * Separator (; , albo tab) wykrywany automatycznie z pierwszej linii.
 */
public final class CsvParser {

    private CsvParser() {
    }

    public static List<List<String>> parse(String content) {
        if (content == null) {
            return List.of();
        }
        String text = content.startsWith("﻿") ? content.substring(1) : content;
        return parse(text, detectDelimiter(text));
    }

    public static char detectDelimiter(String text) {
        int semicolons = 0;
        int commas = 0;
        int tabs = 0;
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"') {
                quoted = !quoted;
            } else if (!quoted) {
                if (c == '\n' || c == '\r') {
                    break;
                }
                if (c == ';') {
                    semicolons++;
                } else if (c == ',') {
                    commas++;
                } else if (c == '\t') {
                    tabs++;
                }
            }
        }
        if (tabs > semicolons && tabs > commas) {
            return '\t';
        }
        return semicolons >= commas ? ';' : ',';
    }

    public static List<List<String>> parse(String text, char delimiter) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        boolean fieldStarted = false;
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    field.append(c);
                }
            } else if (c == '"' && field.isEmpty()) {
                quoted = true;
                fieldStarted = true;
            } else if (c == delimiter) {
                row.add(field.toString());
                field.setLength(0);
                fieldStarted = true;
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                    i++;
                }
                row.add(field.toString());
                field.setLength(0);
                if (!(row.size() == 1 && row.get(0).isEmpty() && !fieldStarted)) {
                    rows.add(row);
                }
                row = new ArrayList<>();
                fieldStarted = false;
            } else {
                field.append(c);
                fieldStarted = true;
            }
            i++;
        }
        if (fieldStarted || !field.isEmpty() || !row.isEmpty()) {
            row.add(field.toString());
            rows.add(row);
        }
        return rows;
    }
}
