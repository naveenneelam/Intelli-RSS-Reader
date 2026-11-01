package com.intellirs.reader.data.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/** A user-authored rule. Conditions and actions are stored as versioned JSON documents. */
@Entity(tableName = "filter_rules",
        indices = {@Index(value = {"enabled", "priority"}), @Index("priority")})
public class FilterRuleEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;

    @NonNull
    public String ruleName = "";

    @NonNull
    public String conditionJson = "[]";

    @NonNull
    public String actionJson = "[]";

    public boolean enabled = true;

    /** Lower numbers run first. */
    public int priority;

    public long createdAt = System.currentTimeMillis();

    public long updatedAt = System.currentTimeMillis();
}
