package com.intellirs.reader.data.parser;

import com.intellirs.reader.data.entity.ArticleType;

import java.util.ArrayList;
import java.util.List;

/** One normalized feed item. HTML is sanitized later by ContentExtractor. */
public class ParsedArticle {
    public String title = "";
    public String link;
    public String guid;
    public String descriptionHtml;
    public String contentHtml;
    public String author;
    public long publishedAt;
    public String thumbnailUrl;
    public String audioUrl;
    public long audioDurationMs;
    public String videoUrl;
    public boolean hasImages;
    public boolean hasVideo;
    public boolean hasAudio;
    public ArticleType articleType = ArticleType.ARTICLE;
    public final List<String> categories = new ArrayList<>();
    public final List<String> imageUrls = new ArrayList<>();
}
