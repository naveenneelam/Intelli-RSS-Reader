package com.intellirs.reader.data.entity;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "reading_history",
        indices = {@Index(value = {"articleId"}, unique = true), @Index("lastReadAt")},
        foreignKeys = @ForeignKey(entity = ArticleEntity.class, parentColumns = "id",
                childColumns = "articleId", onDelete = ForeignKey.CASCADE))
public class ReadingHistoryEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;

    public long articleId;

    public long openedAt;

    public long lastReadAt;

    /** 0..1 vertical reading progress. */
    public float progress;

    public long secondsRead;
}
