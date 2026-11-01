package com.intellirs.reader.data.model;

/** Aggregated per-feed statistics used by feed details and prioritisation suggestions. */
public class FeedStats {
    public int totalArticles;
    public int unreadArticles;
    public int readArticles;
    public int bookmarkedArticles;
    public int archivedArticles;
    public long secondsRead;
    public long latestPublishedAt;
    public float readRate;
}
