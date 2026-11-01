package com.intellirs.reader.data.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * A subscription. Unsubscribing sets isActive=false instead of deleting the row so that
 * archived articles and their source relationships remain intact.
 */
@Entity(tableName = "feeds",
        indices = {
                @Index(value = {"feedUrl"}, unique = true),
                @Index("categoryId"),
                @Index("isActive"),
                @Index("feedPriority"),
                @Index("sortOrder"),
                @Index("lastSuccessfulSync")
        },
        foreignKeys = @ForeignKey(
                entity = CategoryEntity.class,
                parentColumns = "id",
                childColumns = "categoryId",
                onDelete = ForeignKey.SET_NULL))
public class FeedEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;

    @NonNull
    public String title = "";

    @NonNull
    public String feedUrl = "";

    public String websiteUrl;

    public String description;

    public String faviconPath;

    public Long categoryId;

    @NonNull
    public FeedImportance feedPriority = FeedImportance.NORMAL;

    @NonNull
    public ImportanceSource importanceSource = ImportanceSource.AUTO;

    public boolean notificationEnabled = true;

    @NonNull
    public ArchiveMode archiveMode = ArchiveMode.MANUAL;

    /** 0 means use the application default. Values are minutes. */
    public int syncIntervalMinutes;

    public int notifyMinRelevance;

    @NonNull
    public String feedType = "RSS";

    public String etag;

    public String lastModified;

    public long lastSyncTime;

    public long lastSuccessfulSync;

    public int failureCount;

    public boolean isActive = true;

    public int sortOrder;

    public long createdAt = System.currentTimeMillis();

    public long lastHttpStatus;
}
