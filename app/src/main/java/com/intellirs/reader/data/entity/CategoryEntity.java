package com.intellirs.reader.data.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/** A user-defined feed folder. Parent ids allow OPML folder hierarchies. */
@Entity(tableName = "categories",
        indices = {@Index("parentId"), @Index("sortOrder")})
public class CategoryEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;

    @NonNull
    public String name = "";

    public Long parentId;

    public int sortOrder;

    /** Muted categories are still fetched but never generate notifications. */
    public boolean muteNotifications;

    public long createdAt = System.currentTimeMillis();
}
