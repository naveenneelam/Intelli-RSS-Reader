package com.intellirs.reader.data.parser;

import com.intellirs.reader.data.entity.ExtractionQuality;

import java.util.ArrayList;
import java.util.List;

public class ExtractionResult {
    public String title = "";
    public String author;
    public String siteName;
    public long publishedAt;
    public String cleanHtml = "";
    public String plainText = "";
    public String featuredImageUrl;
    public String videoUrl;
    public String finalUrl;
    public ExtractionQuality quality = ExtractionQuality.NONE;
    public final List<String> imageUrls = new ArrayList<>();

    public boolean isUsable() {
        return plainText != null && plainText.length() >= 120 && !cleanHtml.isEmpty();
    }
}
