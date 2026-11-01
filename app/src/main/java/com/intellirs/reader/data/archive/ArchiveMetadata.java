package com.intellirs.reader.data.archive;

import java.util.LinkedHashMap;
import java.util.Map;

public class ArchiveMetadata {
    public long articleId;
    public String title;
    public String author;
    public String publisher;
    public String originalUrl;
    public String canonicalUrl;
    public long publishedAt;
    public long firstSeenAt;
    public long archivedAt;
    public String contentHash;
    public int versionNumber;
    public String extractionQuality;
    public String videoUrl;
    public String audioUrl;
    public int wordCount;
    public Map<String, String> imageUrls = new LinkedHashMap<>();
}
