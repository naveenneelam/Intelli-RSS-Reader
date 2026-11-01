package com.intellirs.reader.data.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Canonical stored article. Duplicate syndicated copies point at one row through
 * ArticleFeedCrossRef, so every source feed relationship is retained.
 *
 * Full HTML is deliberately not stored here: contentPath points to app-private files.
 */
@Entity(tableName = "articles",
        indices = {
                @Index(value = {"canonicalUrl"}, unique = true),
                @Index("guid"),
                @Index("contentHash"),
                @Index("normalizedTitle"),
                @Index("publishedAt"),
                @Index("firstSeenAt"),
                @Index("isRead"),
                @Index("isBookmarked"),
                @Index("isArchived"),
                @Index("relevanceScore"),
                @Index("articleType"),
                @Index("primaryFeedId")
        },
        foreignKeys = @ForeignKey(
                entity = FeedEntity.class,
                parentColumns = "id",
                childColumns = "primaryFeedId",
                onDelete = ForeignKey.SET_NULL))
public class ArticleEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;

    /** Original publisher URL, retained exactly for attribution and "open original". */
    public String originalUrl;

    /** Normalized/deduplicated URL used by duplicate detection. */
    @NonNull
    public String canonicalUrl = "";

    @NonNull
    public String guid = "";

    @NonNull
    public String title = "";

    public String description;

    public String author;

    public String publisher;

    public long publishedAt;

    public long firstSeenAt;

    public long updatedAt;

    public long lastSeenAt;

    /** SHA-256 of normalized title + description/body. */
    public String contentHash;

    public String normalizedTitle;

    public String thumbnailUrl;

    public String thumbnailPath;

    @NonNull
    public ArticleType articleType = ArticleType.ARTICLE;

    public int relevanceScore;

    @NonNull
    public ArticleRelevanceLevel relevanceLevel = ArticleRelevanceLevel.NORMAL;

    /** Human-readable rule/keyword explanation for the score and notification log. */
    public String relevanceReason;

    public boolean isRead;

    public boolean isBookmarked;

    public boolean isArchived;

    /** Manual downloads and archives are never removed by automatic cleanup. */
    public boolean isManuallyArchived;

    /** Relative to filesDir, for example archive/articles/12/content.html. */
    public String contentPath;

    public Long latestVersionId;

    /** Hash of the currently archived extraction, used to detect changed articles. */
    public String archivedContentHash;

    public boolean hasImages;

    public boolean hasVideo;

    public boolean hasAudio;

    public String audioUrl;

    public String audioPath;

    public long audioDurationMs;

    public String videoUrl;

    /** Plain text of extracted content for local full-text search (HTML stays on disk). */
    public String plainText;

    public String aiSummary;

    public String aiTags;

    public Long primaryFeedId;

    @NonNull
    public ExtractionQuality extractionQuality = ExtractionQuality.NONE;

    public long archivedAt;

    public ArticleEntity() {
        long now = System.currentTimeMillis();
        firstSeenAt = now;
        updatedAt = now;
        lastSeenAt = now;
    }
}
