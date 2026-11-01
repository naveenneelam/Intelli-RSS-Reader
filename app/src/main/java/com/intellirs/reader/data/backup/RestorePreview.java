package com.intellirs.reader.data.backup;

import java.io.File;

public class RestorePreview {
    public final File extractedDirectory;
    public final BackupMetadata metadata;

    public RestorePreview(File extractedDirectory, BackupMetadata metadata) {
        this.extractedDirectory = extractedDirectory;
        this.metadata = metadata;
    }
}
