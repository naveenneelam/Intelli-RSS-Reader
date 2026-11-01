package com.intellirs.reader.domain.filtering;

import java.util.ArrayList;
import java.util.List;

public class FilterRule {
    public long id;
    public String name = "";
    public boolean enabled = true;
    public int priority;
    public boolean matchAll = true;
    public final List<FilterCondition> conditions = new ArrayList<>();
    public final List<FilterAction> actions = new ArrayList<>();

    public boolean hasConditions() {
        return !conditions.isEmpty();
    }
}
