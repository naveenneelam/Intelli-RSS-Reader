package com.intellirs.reader.data.entity;

/** Feed-level importance. This is intentionally separate from article relevance. */
public enum FeedImportance {
    CRITICAL(0),
    HIGH(1),
    NORMAL(2),
    LOW(3),
    MUTED(4);

    private final int rank;

    FeedImportance(int rank) {
        this.rank = rank;
    }

    public int rank() {
        return rank;
    }

    public static FeedImportance fromName(String value, FeedImportance fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }
}
