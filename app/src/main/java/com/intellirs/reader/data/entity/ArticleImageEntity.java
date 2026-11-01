package com.intellirs.reader.data.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "article_images",
        indices = {@Index("articleId"), @Index("sha256"), @Index("imageType")},
        foreignKeys = @ForeignKey(entity = ArticleEntity.class, parentColumns = "id",
                childColumns = "articleId", onDelete = ForeignKey.CASCADE))
public class ArticleImageEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;

    public long articleId;

    @NonNull
    public String imageUrl = "";

    @NonNull
    public String localPath = "";

    @NonNull
    public String imageType = "INLINE";

    public long fileSize;

    @NonNull
    public String sha256 = "";

    public long createdAt = System.currentTimeMillis();
}
