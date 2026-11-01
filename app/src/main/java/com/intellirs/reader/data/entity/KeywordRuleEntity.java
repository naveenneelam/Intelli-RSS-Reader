package com.intellirs.reader.data.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "keyword_rules",
        indices = {
                @Index(value = {"keyword", "type"}, unique = true),
                @Index("type")
        })
public class KeywordRuleEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;

    @NonNull
    public String keyword = "";

    @NonNull
    public KeywordType type = KeywordType.PRIORITY;

    /** 1..100. Used by the transparent relevance engine. */
    public int weight = 50;

    public long createdAt = System.currentTimeMillis();
}
