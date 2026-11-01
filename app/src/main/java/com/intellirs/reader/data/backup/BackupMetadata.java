package com.intellirs.reader.data.backup;

public class BackupMetadata {
    public String format = "IntelliRSS-full-backup";
    public int schemaVersion = 1;
    public String appVersion = "";
    public long createdAt;
    public int feeds;
    public int archivedArticles;
}
