package com.intellirs.reader.data.network;

import java.io.Serializable;

/** A validated feed discovered from a website, returned for user selection. */
public class FeedCandidate implements Serializable {
    public String title = "";
    public String feedUrl = "";
    public String websiteUrl;
    public String description;
    public String feedType = "RSS";
    public String suggestedCategory = "General";
    public int articleCount;

    public FeedCandidate() {
    }

    public FeedCandidate(String title, String feedUrl, String websiteUrl, String description,
                         String feedType, String suggestedCategory, int articleCount) {
        this.title = title;
        this.feedUrl = feedUrl;
        this.websiteUrl = websiteUrl;
        this.description = description;
        this.feedType = feedType;
        this.suggestedCategory = suggestedCategory;
        this.articleCount = articleCount;
    }
}
