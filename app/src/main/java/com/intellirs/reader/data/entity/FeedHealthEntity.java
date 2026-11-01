package com.intellirs.reader.data.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "feed_health",
        indices = {@Index(value = {"feedId"}, unique = true), @Index("status")},
        foreignKeys = @ForeignKey(entity = FeedEntity.class, parentColumns = "id",
                childColumns = "feedId", onDelete = ForeignKey.CASCADE))
public class FeedHealthEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;

    public long feedId;

    @NonNull
    public FeedHealthStatus status = FeedHealthStatus.HEALTHY;

    public int failureCount;

    public String lastError;

    public long lastCheckedAt;

    public long lastSuccessAt;

    public int lastHttpStatus;

    public long avgResponseMs;
}
