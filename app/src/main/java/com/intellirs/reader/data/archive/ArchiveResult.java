package com.intellirs.reader.data.archive;

import com.intellirs.reader.data.entity.ExtractionQuality;

public class ArchiveResult {
    public boolean success;
    public String error;
    public String contentPath;
    public String plainText = "";
    public ExtractionQuality quality = ExtractionQuality.NONE;
    public int imageCount;
    public int versionNumber;
    public long bytes;

    public static ArchiveResult failure(String message) {
        ArchiveResult result = new ArchiveResult();
        result.success = false;
        result.error = message;
        return result;
    }
}
