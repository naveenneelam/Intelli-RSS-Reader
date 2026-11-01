package com.intellirs.reader.util;

import androidx.annotation.Nullable;

import java.util.Locale;

public final class TextUtils {
    private TextUtils() {
    }

    @Nullable
    public static String stripHtml(@Nullable String html) {
        if (html == null || html.trim().isEmpty()) {
            return null;
        }
        String text = org.jsoup.Jsoup.parse(html).text();
        return text.isEmpty() ? null : text;
    }

    public static String stripHtmlOrEmpty(@Nullable String html) {
        String text = stripHtml(html);
        return text == null ? "" : text;
    }

    public static String truncate(@Nullable String value, int max) {
        if (value == null) {
            return "";
        }
        String normalized = value.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= max) {
            return normalized;
        }
        int cut = normalized.lastIndexOf(' ', max);
        if (cut < max / 2) {
            cut = max;
        }
        return normalized.substring(0, cut).trim() + "\u2026";
    }

    public static String summary(@Nullable String htmlOrText, int max) {
        return truncate(stripHtmlOrEmpty(htmlOrText), max);
    }

    public static String humanBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024L * 1024L) {
            return String.format(Locale.getDefault(), "%.1f KB", bytes / 1024.0);
        }
        if (bytes < 1024L * 1024L * 1024L) {
            return String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024.0));
        }
        return String.format(Locale.getDefault(), "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }

    public static String initials(String title) {
        if (title == null || title.trim().isEmpty()) {
            return "?";
        }
        String[] words = title.trim().split("\\s+");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty() && result.length() < 2) {
                result.append(Character.toUpperCase(word.charAt(0)));
            }
        }
        return result.length() == 0 ? "?" : result.toString();
    }

    public static String[] significantWords(String value, int max) {
        if (value == null) {
            return new String[0];
        }
        String cleaned = value.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (cleaned.isEmpty()) {
            return new String[0];
        }
        String[] stop = new String[]{"the", "and", "for", "with", "that", "this", "from",
                "have", "will", "are", "was", "were", "has", "not", "you", "your", "its",
                "how", "why", "what", "when", "who", "into", "about", "after", "over",
                "new", "says", "said", "amid", "than", "more", "most"};
        java.util.List<String> result = new java.util.ArrayList<>();
        for (String word : cleaned.split(" ")) {
            if (word.length() < 4 || java.util.Arrays.asList(stop).contains(word)) {
                continue;
            }
            if (!result.contains(word)) {
                result.add(word);
            }
            if (result.size() >= max) {
                break;
            }
        }
        return result.toArray(new String[0]);
    }
}
