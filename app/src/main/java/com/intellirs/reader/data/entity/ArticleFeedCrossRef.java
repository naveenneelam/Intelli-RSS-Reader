package com.intellirs.reader.data.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;

/**
 * Many-to-many source relationship. Duplicate detection merges articles but never discards
 * which feeds published them.
 */
@Entity(tableName = "article_feed_cross_ref",
        primaryKeys = {"articleId", "feedId"},
        indices = {@Index("articleId"), @Index("feedId"),
                @Index(value = {"feedId", "originalGuid"})},
        foreignKeys = {
                @ForeignKey(entity = ArticleEntity.class, parentColumns = "id",
                        childColumns = "articleId", onDelete = ForeignKey.CASCADE),
                @ForeignKey(entity = FeedEntity.class, parentColumns = "id",
                        childColumns = "feedId", onDelete = ForeignKey.CASCADE)
        })
public class ArticleFeedCrossRef {
    public long articleId;
    public long feedId;

    @NonNull
    public String originalGuid = "";

    public String sourceTitle;

    public String sourcePublisher;

    /** URL of this copy, used when the user chooses a preferred source publisher. */
    public String sourceUrl;

    public long sourcePublishedAt;

    public long addedAt = System.currentTimeMillis();
}
