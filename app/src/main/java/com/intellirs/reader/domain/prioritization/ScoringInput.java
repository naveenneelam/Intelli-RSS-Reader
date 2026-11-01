package com.intellirs.reader.domain.prioritization;

public class ScoringInput {
    public String title = "";
    public String description = "";
    public String content = "";
    public String author = "";
    public String publisher = "";
    public String category = "";
    public long publishedAt;
    public long now = System.currentTimeMillis();
    public float feedReadRate;
    public boolean hasMedia;
}
