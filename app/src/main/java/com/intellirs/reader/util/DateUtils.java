package com.intellirs.reader.util;

import android.content.Context;

import androidx.annotation.Nullable;

import com.intellirs.reader.R;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;

/** Lenient RSS/Atom date parsing and compact display formatting. */
public final class DateUtils {
    private static final DateTimeFormatter[] ZONED_FORMATS = new DateTimeFormatter[]{
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US),
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US),
            DateTimeFormatter.ofPattern("EEE, d MMM yyyy HH:mm:ss Z", Locale.US),
            DateTimeFormatter.ofPattern("EEE, d MMM yyyy HH:mm:ss zzz", Locale.US),
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss z", Locale.US),
            DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm:ss Z", Locale.US),
            DateTimeFormatter.ofPattern("EEE MMM dd HH:mm:ss zzz yyyy", Locale.US),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US)
    };
    private static final DateTimeFormatter[] LOCAL_FORMATS = new DateTimeFormatter[]{
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.US),
            DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US)
    };

    private DateUtils() {
    }

    /** Returns epoch millis, or 0 when the value cannot be parsed. */
    public static long parse(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return 0L;
        }
        String value = raw.trim();
        try {
            return ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME)
                    .toInstant().toEpochMilli();
        } catch (DateTimeParseException ignored) {
            // Try the next known form.
        }
        try {
            return OffsetDateTime.parse(value, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                    .toInstant().toEpochMilli();
        } catch (DateTimeParseException ignored) {
            // Try the next known form.
        }
        try {
            return ZonedDateTime.parse(value, DateTimeFormatter.ISO_ZONED_DATE_TIME)
                    .toInstant().toEpochMilli();
        } catch (DateTimeParseException ignored) {
            // Try the next known form.
        }
        try {
            return Instant.parse(value).toEpochMilli();
        } catch (DateTimeParseException ignored) {
            // Try the next known form.
        }
        for (DateTimeFormatter formatter : ZONED_FORMATS) {
            try {
                return ZonedDateTime.parse(value, formatter).toInstant().toEpochMilli();
            } catch (DateTimeParseException ignored) {
                // Try the next known publisher date format.
            }
            try {
                return OffsetDateTime.parse(value, formatter).toInstant().toEpochMilli();
            } catch (DateTimeParseException ignored) {
                // Try the next known publisher date format.
            }
        }
        // Publisher weekdays are occasionally wrong. Retry without the weekday prefix.
        String withoutDay = value.replaceFirst(
                "^[A-Za-z]{3,9},\\s*", "");
        if (!withoutDay.equals(value)) {
            for (String pattern : new String[]{
                    "d MMM yyyy HH:mm:ss Z",
                    "d MMM yyyy HH:mm:ss zzz",
                    "dd MMM yyyy HH:mm:ss Z"}) {
                try {
                    return ZonedDateTime.parse(withoutDay,
                            DateTimeFormatter.ofPattern(pattern, Locale.US))
                            .toInstant().toEpochMilli();
                } catch (DateTimeParseException ignored) {
                    // Try the next publisher variant.
                }
            }
        }
        for (DateTimeFormatter formatter : LOCAL_FORMATS) {
            try {
                return LocalDateTime.parse(value, formatter)
                        .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
            } catch (DateTimeParseException ignored) {
                try {
                    return LocalDate.parse(value, formatter)
                            .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
                } catch (DateTimeParseException ignoredAgain) {
                    // Try the next known local date format.
                }
            }
        }
        try {
            return OffsetDateTime.parse(value).toInstant().toEpochMilli();
        } catch (DateTimeParseException ignored) {
            // Some feeds append a duplicated timezone abbreviation.
        }
        return 0L;
    }

    @Nullable
    public static String parseDateForDisplay(String raw) {
        long parsed = parse(raw);
        return parsed <= 0 ? null : formatDate(parsed);
    }

    public static String formatDate(long millis) {
        if (millis <= 0) {
            return "";
        }
        ZoneId zone = ZoneId.systemDefault();
        LocalDate date = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate();
        LocalDate today = LocalDate.now(zone);
        DateTimeFormatter formatter;
        if (date.equals(today)) {
            formatter = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault());
        } else if (date.equals(today.minusDays(1))) {
            return "Yesterday";
        } else if (date.getYear() == today.getYear()) {
            formatter = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault());
        } else {
            formatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault());
        }
        return Instant.ofEpochMilli(millis).atZone(zone).format(formatter);
    }

    public static String formatFullDate(long millis) {
        if (millis <= 0) {
            return "";
        }
        return Instant.ofEpochMilli(millis)
                .atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("d MMMM yyyy, HH:mm", Locale.getDefault()));
    }

    public static String relative(Context context, long millis) {
        if (millis <= 0) {
            return context.getString(R.string.never);
        }
        long delta = Math.max(0, System.currentTimeMillis() - millis);
        long minutes = delta / 60_000L;
        if (minutes < 1) {
            return context.getString(R.string.just_now);
        }
        if (minutes < 60) {
            return context.getString(R.string.minutes_ago, minutes);
        }
        long hours = minutes / 60;
        if (hours < 24) {
            return context.getString(R.string.hours_ago, hours);
        }
        long days = hours / 24;
        if (days == 1) {
            return context.getString(R.string.yesterday);
        }
        if (days < 7) {
            return context.getString(R.string.days_ago, days);
        }
        return formatDate(millis);
    }

    public static String formatDuration(long millis) {
        if (millis <= 0) {
            return "";
        }
        long totalSeconds = millis / 1000L;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        if (hours > 0) {
            return String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds);
    }

    public static String startOfDayIso(int daysBack) {
        return LocalDate.now().minusDays(daysBack).toString();
    }
}
