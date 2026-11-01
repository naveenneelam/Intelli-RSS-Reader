package com.intellirs.reader.domain.duplicates;

public class DuplicateMatch {
    public final boolean match;
    public final int confidence;
    public final String reason;

    public DuplicateMatch(boolean match, int confidence, String reason) {
        this.match = match;
        this.confidence = confidence;
        this.reason = reason;
    }
}
