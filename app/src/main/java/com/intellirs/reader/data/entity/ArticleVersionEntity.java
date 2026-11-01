package com.intellirs.reader.data.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/** Immutable snapshots of archived content. Old versions are never overwritten. */
@Entity(tableName = "article_versions",
        indices = {@Index(value = {"articleId", "versionNumber"}, unique = true)},
        foreignKeys = @ForeignKey(entity = ArticleEntity.class, parentColumns = "id",
                childColumns = "articleId", onDelete = ForeignKey.CASCADE))
public class ArticleVersionEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;

    public long articleId;

    public int versionNumber;

    @NonNull
    public String contentHash = "";

    @NonNull
    public String contentPath = "";

    public String metadataPath;

    public long createdAt = System.currentTimeMillis();

    public int wordCount;
}
