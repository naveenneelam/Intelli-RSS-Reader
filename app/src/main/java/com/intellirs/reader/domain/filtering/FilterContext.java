package com.intellirs.reader.domain.filtering;

import com.intellirs.reader.data.entity.FeedImportance;

public class FilterContext {
    public String title = "";
    public String description = "";
    public String content = "";
    public String author = "";
    public String publisher = "";
    public String category = "";
    public FeedImportance feedImportance = FeedImportance.NORMAL;
    public int relevanceScore;
    public long publishedAt;
    public boolean hasImages;
    public boolean hasVideo;
    public boolean hasAudio;
    public long now = System.currentTimeMillis();
}
