package com.intellirs.reader.domain.duplicates;

public class ArticleFingerprint {
    public String canonicalUrl;
    public String guid;
    public String normalizedTitle;
    public String contentHash;
    public String publisher;

    public ArticleFingerprint() {
    }

    public ArticleFingerprint(String canonicalUrl, String guid, String normalizedTitle,
                              String contentHash, String publisher) {
        this.canonicalUrl = canonicalUrl;
        this.guid = guid;
        this.normalizedTitle = normalizedTitle;
        this.contentHash = contentHash;
        this.publisher = publisher;
    }
}
