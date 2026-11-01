package com.intellirs.reader.data.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/** One private note per article. Protected from automatic cache cleanup. */
@Entity(tableName = "article_notes",
        indices = {@Index(value = {"articleId"}, unique = true)},
        foreignKeys = @ForeignKey(entity = ArticleEntity.class, parentColumns = "id",
                childColumns = "articleId", onDelete = ForeignKey.CASCADE))
public class ArticleNoteEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;

    public long articleId;

    @NonNull
    public String note = "";

    public long createdAt = System.currentTimeMillis();

    public long updatedAt = System.currentTimeMillis();
}
