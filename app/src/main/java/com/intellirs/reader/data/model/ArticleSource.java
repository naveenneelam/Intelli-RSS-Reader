package com.intellirs.reader.data.model;

import com.intellirs.reader.data.entity.FeedImportance;

/** One source feed of a canonical article. */
public class ArticleSource {
    public long feedId;
    public String feedTitle;
    public String feedUrl;
    public String sourceUrl;
    public FeedImportance feedImportance;
    public boolean notificationEnabled;
    public long sourcePublishedAt;
}
