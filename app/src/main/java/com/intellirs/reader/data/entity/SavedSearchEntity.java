package com.intellirs.reader.data.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "saved_searches",
        indices = {@Index("createdAt")})
public class SavedSearchEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;

    @NonNull
    public String name = "";

    @NonNull
    public String query = "";

    /** Serialized SearchFilter document. */
    @NonNull
    public String filterJson = "{}";

    public long createdAt = System.currentTimeMillis();
}
