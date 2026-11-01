package com.intellirs.reader.util;

import androidx.annotation.Nullable;

import java.util.Locale;

/** Magic-byte image validation. SVG and unknown payloads are rejected for downloaded files. */
public final class ImageUtils {
    private ImageUtils() {
    }

    @Nullable
    public static String detectMime(byte[] bytes) {
        if (bytes == null || bytes.length < 12) {
            return null;
        }
        if ((bytes[0] & 0xFF) == 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4E
                && bytes[3] == 0x47) {
            return "image/png";
        }
        if ((bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8) {
            return "image/jpeg";
        }
        if (bytes[0] == 'G' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == '8') {
            return "image/gif";
        }
        if (bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') {
            return "image/webp";
        }
        if (bytes[0] == 'B' && bytes[1] == 'M') {
            return "image/bmp";
        }
        return null;
    }

    public static boolean isSupportedImageMime(@Nullable String mime) {
        if (mime == null) {
            return false;
        }
        String normalized = mime.toLowerCase(Locale.ROOT);
        return normalized.equals("image/png") || normalized.equals("image/jpeg")
                || normalized.equals("image/gif") || normalized.equals("image/webp")
                || normalized.equals("image/bmp");
    }

    public static String extensionForMime(String mime) {
        if (mime == null) {
            return "img";
        }
        switch (mime.toLowerCase(Locale.ROOT)) {
            case "image/png":
                return "png";
            case "image/jpeg":
                return "jpg";
            case "image/gif":
                return "gif";
            case "image/webp":
                return "webp";
            case "image/bmp":
                return "bmp";
            default:
                return "img";
        }
    }
}
