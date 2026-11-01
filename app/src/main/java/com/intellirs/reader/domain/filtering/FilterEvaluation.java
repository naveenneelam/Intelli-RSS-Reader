package com.intellirs.reader.domain.filtering;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class FilterEvaluation {
    public int scoreAdjustment;
    public boolean forceNotify;
    public boolean muteNotification;
    public boolean markRead;
    public boolean archive;
    public final Set<String> tags = new LinkedHashSet<>();
    public final List<String> appliedRuleNames = new ArrayList<>();
    public final List<String> reasons = new ArrayList<>();

    public int adjustedScore(int baseScore) {
        return Math.max(0, Math.min(100, baseScore + scoreAdjustment));
    }

    public boolean notificationEnabled() {
        if (muteNotification) {
            return false;
        }
        return forceNotify;
    }
}
