package com.intellirs.reader.domain.model;

public class SyncStatus {
    public enum State {
        IDLE,
        RUNNING,
        COMPLETE,
        ERROR
    }

    public final State state;
    public final String message;
    public final long time;
    public final int feedsSucceeded;
    public final int feedsFailed;
    public final int newArticles;
    public final int feedsCompleted;
    public final int feedsTotal;

    public SyncStatus(State state, String message, long time, int feedsSucceeded,
                      int feedsFailed, int newArticles) {
        this(state, message, time, feedsSucceeded, feedsFailed, newArticles,
                feedsSucceeded + feedsFailed, feedsSucceeded + feedsFailed);
    }

    public SyncStatus(State state, String message, long time, int feedsSucceeded,
                      int feedsFailed, int newArticles, int feedsCompleted, int feedsTotal) {
        this.state = state;
        this.message = message == null ? "" : message;
        this.time = time;
        this.feedsSucceeded = feedsSucceeded;
        this.feedsFailed = feedsFailed;
        this.newArticles = newArticles;
        this.feedsTotal = Math.max(0, feedsTotal);
        this.feedsCompleted = Math.max(0, Math.min(feedsCompleted, this.feedsTotal));
    }

    public static SyncStatus idle() {
        return new SyncStatus(State.IDLE, "", 0, 0, 0, 0);
    }
}
