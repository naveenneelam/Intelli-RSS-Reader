package com.intellirs.reader.data.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/** Persistent synchronization history, independent of process lifetime. */
@Entity(tableName = "sync_runs",
        indices = {@Index("startedAt"), @Index("status")})
public class SyncRunEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;

    public long startedAt;

    public long finishedAt;

    @NonNull
    public String status = "RUNNING";

    public int feedsAttempted;

    public int feedsSucceeded;

    public int feedsFailed;

    public int newArticles;

    public String errorSummary;

    public boolean manual;
}
