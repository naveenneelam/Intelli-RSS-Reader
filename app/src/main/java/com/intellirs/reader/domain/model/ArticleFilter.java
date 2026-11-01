package com.intellirs.reader.domain.model;

import java.io.Serializable;

/** Serializable article-list context shared by list screens, reader and notifications. */
public class ArticleFilter implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum Mode {
        ALL,
        IMPORTANT,
        UNREAD,
        STARRED,
        ARCHIVED,
        FEED,
        CATEGORY,
        HISTORY
    }

    public Mode mode = Mode.ALL;
    public long feedId = -1L;
    public long categoryId = -1L;
    public String query = "";
    public String readState = "ANY";
    public boolean bookmarkedOnly;
    public boolean archivedOnly;
    public String media = "ANY";
    public String importance = "ANY";
    public int minRelevance;
    public long publishedAfter;
    public long publishedBefore;

    public static ArticleFilter all() {
        return new ArticleFilter();
    }

    public static ArticleFilter unread() {
        ArticleFilter filter = new ArticleFilter();
        filter.mode = Mode.UNREAD;
        return filter;
    }

    public static ArticleFilter starred() {
        ArticleFilter filter = new ArticleFilter();
        filter.mode = Mode.STARRED;
        return filter;
    }

    public static ArticleFilter important() {
        ArticleFilter filter = new ArticleFilter();
        filter.mode = Mode.IMPORTANT;
        return filter;
    }

    public static ArticleFilter archived() {
        ArticleFilter filter = new ArticleFilter();
        filter.mode = Mode.ARCHIVED;
        return filter;
    }

    public static ArticleFilter history() {
        ArticleFilter filter = new ArticleFilter();
        filter.mode = Mode.HISTORY;
        return filter;
    }

    public static ArticleFilter feed(long feedId) {
        ArticleFilter filter = new ArticleFilter();
        filter.mode = Mode.FEED;
        filter.feedId = feedId;
        return filter;
    }

    public static ArticleFilter category(long categoryId) {
        ArticleFilter filter = new ArticleFilter();
        filter.mode = Mode.CATEGORY;
        filter.categoryId = categoryId;
        return filter;
    }

    public static ArticleFilter search(String query) {
        ArticleFilter filter = new ArticleFilter();
        filter.mode = Mode.ALL;
        filter.query = query == null ? "" : query;
        return filter;
    }

    public boolean hasSearchQuery() {
        return query != null && !query.trim().isEmpty();
    }
}
