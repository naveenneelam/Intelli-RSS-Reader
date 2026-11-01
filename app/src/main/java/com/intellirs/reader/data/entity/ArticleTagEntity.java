package com.intellirs.reader.data.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "article_tags",
        indices = {@Index(value = {"articleId", "tag"}, unique = true), @Index("tag")},
        foreignKeys = @ForeignKey(entity = ArticleEntity.class, parentColumns = "id",
                childColumns = "articleId", onDelete = ForeignKey.CASCADE))
public class ArticleTagEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;

    public long articleId;

    @NonNull
    public String tag = "";

    public long createdAt = System.currentTimeMillis();
}
