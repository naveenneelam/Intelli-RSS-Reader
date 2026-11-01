package com.intellirs.reader.data.network;

import androidx.annotation.Nullable;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class HttpResult {
    private static final Pattern XML_ENCODING =
            Pattern.compile("encoding\\s*=\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);

    public final int statusCode;
    public final byte[] body;
    public final String contentType;
    public final String etag;
    public final String lastModified;
    public final String finalUrl;
    public final long contentLength;

    public HttpResult(int statusCode, byte[] body, String contentType, String etag,
                      String lastModified, String finalUrl, long contentLength) {
        this.statusCode = statusCode;
        this.body = body == null ? new byte[0] : body;
        this.contentType = contentType == null ? "" : contentType;
        this.etag = etag;
        this.lastModified = lastModified;
        this.finalUrl = finalUrl;
        this.contentLength = contentLength;
    }

    public boolean isSuccessful() {
        return statusCode >= 200 && statusCode < 300;
    }

    public boolean isNotModified() {
        return statusCode == 304;
    }

    public String bodyString() {
        return decode(body, contentType);
    }

    @Nullable
    public String header(String name) {
        if ("etag".equalsIgnoreCase(name)) {
            return etag;
        }
        if ("last-modified".equalsIgnoreCase(name)) {
            return lastModified;
        }
        if ("content-type".equalsIgnoreCase(name)) {
            return contentType;
        }
        return null;
    }

    public static String decode(byte[] bytes, String contentType) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }
        Charset charset = charsetFromContentType(contentType);
        if (charset == null) {
            charset = charsetFromBom(bytes);
        }
        if (charset == null) {
            String head = new String(bytes, 0, Math.min(bytes.length, 512), StandardCharsets.US_ASCII);
            Matcher matcher = XML_ENCODING.matcher(head);
            if (matcher.find()) {
                try {
                    charset = Charset.forName(matcher.group(1).trim());
                } catch (RuntimeException ignored) {
                    charset = null;
                }
            }
        }
        if (charset == null) {
            charset = StandardCharsets.UTF_8;
        }
        int offset = 0;
        if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF
                && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF) {
            offset = 3;
        }
        return new String(bytes, offset, bytes.length - offset, charset);
    }

    private static Charset charsetFromContentType(String contentType) {
        if (contentType == null) {
            return null;
        }
        for (String part : contentType.split(";")) {
            String trimmed = part.trim();
            if (trimmed.toLowerCase(java.util.Locale.ROOT).startsWith("charset=")) {
                String name = trimmed.substring(8).trim().replace("\"", "");
                try {
                    return Charset.forName(name);
                } catch (RuntimeException ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    private static Charset charsetFromBom(byte[] bytes) {
        if (bytes.length >= 2 && (bytes[0] & 0xFF) == 0xFE && (bytes[1] & 0xFF) == 0xFF) {
            return StandardCharsets.UTF_16BE;
        }
        if (bytes.length >= 2 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xFE) {
            return StandardCharsets.UTF_16LE;
        }
        return null;
    }
}
