package com.intellirs.reader.util;

import android.content.Context;

import androidx.annotation.Nullable;

import java.io.File;
import java.io.IOException;

/** Safe resolution of database-stored relative paths inside app-private storage. */
public final class FileUtils {
    private FileUtils() {
    }

    @Nullable
    public static File resolvePrivate(Context context, @Nullable String relativePath) {
        if (relativePath == null || relativePath.trim().isEmpty()) {
            return null;
        }
        String normalized = relativePath.replace('\\', '/');
        if (normalized.startsWith("/") || normalized.contains("../")
                || normalized.contains("/..")) {
            return null;
        }
        try {
            File base = context.getFilesDir();
            File file = new File(base, normalized);
            String basePath = base.getCanonicalPath();
            String targetPath = file.getCanonicalPath();
            if (!targetPath.startsWith(basePath + File.separator)) {
                return null;
            }
            return file;
        } catch (IOException e) {
            return null;
        }
    }
}
