package com.intellirs.reader.data.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "download_queue",
        indices = {
                @Index(value = {"articleId", "downloadType"}, unique = true),
                @Index("status"),
                @Index("feedId"),
                @Index("createdAt")
        })
public class DownloadQueueEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;

    public long articleId;

    public long feedId;

    @NonNull
    public DownloadType downloadType = DownloadType.ARTICLE;

    @NonNull
    public DownloadStatus status = DownloadStatus.QUEUED;

    public int retryCount;

    public String lastError;

    public long createdAt = System.currentTimeMillis();

    public long updatedAt = System.currentTimeMillis();
}
