package com.intellirs.reader.data.entity;

import androidx.room.TypeConverter;

/** Explicit enum converters keep database values stable and readable. */
public final class Converters {
    private Converters() {
    }

    @TypeConverter
    public static FeedImportance toFeedImportance(String value) {
        return FeedImportance.fromName(value, FeedImportance.NORMAL);
    }

    @TypeConverter
    public static String fromFeedImportance(FeedImportance value) {
        return value == null ? FeedImportance.NORMAL.name() : value.name();
    }

    @TypeConverter
    public static ImportanceSource toImportanceSource(String value) {
        return safe(value, ImportanceSource.AUTO, ImportanceSource.class);
    }

    @TypeConverter
    public static String fromImportanceSource(ImportanceSource value) {
        return value == null ? ImportanceSource.AUTO.name() : value.name();
    }

    @TypeConverter
    public static ArchiveMode toArchiveMode(String value) {
        return safe(value, ArchiveMode.MANUAL, ArchiveMode.class);
    }

    @TypeConverter
    public static String fromArchiveMode(ArchiveMode value) {
        return value == null ? ArchiveMode.MANUAL.name() : value.name();
    }

    @TypeConverter
    public static ArticleType toArticleType(String value) {
        return safe(value, ArticleType.ARTICLE, ArticleType.class);
    }

    @TypeConverter
    public static String fromArticleType(ArticleType value) {
        return value == null ? ArticleType.ARTICLE.name() : value.name();
    }

    @TypeConverter
    public static ArticleRelevanceLevel toRelevanceLevel(String value) {
        return safe(value, ArticleRelevanceLevel.NORMAL, ArticleRelevanceLevel.class);
    }

    @TypeConverter
    public static String fromRelevanceLevel(ArticleRelevanceLevel value) {
        return value == null ? ArticleRelevanceLevel.NORMAL.name() : value.name();
    }

    @TypeConverter
    public static ExtractionQuality toExtractionQuality(String value) {
        return safe(value, ExtractionQuality.NONE, ExtractionQuality.class);
    }

    @TypeConverter
    public static String fromExtractionQuality(ExtractionQuality value) {
        return value == null ? ExtractionQuality.NONE.name() : value.name();
    }

    @TypeConverter
    public static FeedHealthStatus toFeedHealthStatus(String value) {
        return safe(value, FeedHealthStatus.HEALTHY, FeedHealthStatus.class);
    }

    @TypeConverter
    public static String fromFeedHealthStatus(FeedHealthStatus value) {
        return value == null ? FeedHealthStatus.HEALTHY.name() : value.name();
    }

    @TypeConverter
    public static DownloadStatus toDownloadStatus(String value) {
        return safe(value, DownloadStatus.QUEUED, DownloadStatus.class);
    }

    @TypeConverter
    public static String fromDownloadStatus(DownloadStatus value) {
        return value == null ? DownloadStatus.QUEUED.name() : value.name();
    }

    @TypeConverter
    public static DownloadType toDownloadType(String value) {
        return safe(value, DownloadType.ARTICLE, DownloadType.class);
    }

    @TypeConverter
    public static String fromDownloadType(DownloadType value) {
        return value == null ? DownloadType.ARTICLE.name() : value.name();
    }

    @TypeConverter
    public static NotificationStatus toNotificationStatus(String value) {
        return safe(value, NotificationStatus.SKIPPED, NotificationStatus.class);
    }

    @TypeConverter
    public static String fromNotificationStatus(NotificationStatus value) {
        return value == null ? NotificationStatus.SKIPPED.name() : value.name();
    }

    @TypeConverter
    public static KeywordType toKeywordType(String value) {
        return safe(value, KeywordType.PRIORITY, KeywordType.class);
    }

    @TypeConverter
    public static String fromKeywordType(KeywordType value) {
        return value == null ? KeywordType.PRIORITY.name() : value.name();
    }

    private static <E extends Enum<E>> E safe(String value, E fallback, Class<E> type) {
        if (value == null) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }
}
