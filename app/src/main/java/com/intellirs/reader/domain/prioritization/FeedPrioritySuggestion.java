package com.intellirs.reader.domain.prioritization;

import com.intellirs.reader.data.entity.FeedImportance;

public class FeedPrioritySuggestion {
    public final FeedImportance importance;
    public final int confidence;
    public final String reason;

    public FeedPrioritySuggestion(FeedImportance importance, int confidence, String reason) {
        this.importance = importance;
        this.confidence = Math.max(0, Math.min(100, confidence));
        this.reason = reason;
    }
}
