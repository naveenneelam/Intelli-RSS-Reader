package com.intellirs.reader.util;

import androidx.annotation.Nullable;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * URL validation and canonicalization. Security checks are deliberately centralized so
 * networking, file downloads and external intents all use the same rules.
 */
public final class UrlUtils {
    private static final Set<String> TRACKING_PARAMS = new LinkedHashSet<>(Arrays.asList(
            "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content",
            "gclid", "fbclid", "mc_cid", "mc_eid", "ref", "referrer", "source",
            "campaign", "at_medium", "at_campaign", "igshid", "mkt_tok", "_hsenc", "_hsmi"
    ));

    private UrlUtils() {
    }

    /**
     * Adds https:// when the scheme is omitted and validates an absolute HTTP(S) URL.
     * HTTP is only accepted when the user explicitly enables insecure feeds.
     */
    public static String normalizeUserInput(String raw, boolean allowHttp) {
        if (raw == null) {
            throw new IllegalArgumentException("URL is empty");
        }
        String value = raw.trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("URL is empty");
        }
        if (!value.matches("^[a-zA-Z][a-zA-Z0-9+.-]*://.*$")) {
            value = "https://" + value;
        }
        URI uri;
        try {
            uri = new URI(value);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid URL syntax", e);
        }
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            throw new IllegalArgumentException("Only HTTP and HTTPS URLs are supported");
        }
        if (scheme.equalsIgnoreCase("http") && !allowHttp) {
            throw new HttpUrlException("This URL uses unencrypted HTTP. Enable insecure HTTP feeds in Settings if you trust this publisher.");
        }
        if (uri.getHost() == null || uri.getHost().isEmpty()) {
            throw new IllegalArgumentException("The URL has no host");
        }
        if (uri.getUserInfo() != null) {
            throw new IllegalArgumentException("URLs containing credentials are not allowed");
        }
        if (value.length() > 4096) {
            throw new IllegalArgumentException("URL is too long");
        }
        try {
            URI normalized = new URI(
                    scheme.toLowerCase(Locale.ROOT),
                    uri.getUserInfo(),
                    uri.getHost().toLowerCase(Locale.ROOT),
                    uri.getPort(),
                    uri.getPath() == null || uri.getPath().isEmpty() ? "/" : uri.getPath(),
                    uri.getQuery(),
                    null);
            return normalized.toASCIIString();
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid URL", e);
        }
    }

    public static boolean isValidHttpUrl(@Nullable String value) {
        if (value == null || value.length() > 4096) {
            return false;
        }
        try {
            URI uri = new URI(value);
            String scheme = uri.getScheme();
            return scheme != null
                    && (scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))
                    && uri.getHost() != null
                    && !uri.getHost().isEmpty()
                    && uri.getUserInfo() == null;
        } catch (URISyntaxException e) {
            return false;
        }
    }

    /** Resolves a possibly-relative URL against a base page URL. */
    @Nullable
    public static String resolve(String baseUrl, String relative) {
        if (relative == null || relative.trim().isEmpty()) {
            return null;
        }
        String value = relative.trim()
                .replace("\n", "")
                .replace("\r", "")
                .replace("\t", "");
        if (value.startsWith("data:") || value.startsWith("javascript:")
                || value.startsWith("file:") || value.startsWith("blob:")) {
            return null;
        }
        try {
            URI base = new URI(baseUrl);
            URI resolved = base.resolve(value);
            if (!isValidHttpUrl(resolved.toString())) {
                return null;
            }
            return resolved.toASCIIString();
        } catch (URISyntaxException | IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Canonical form used for duplicate detection: lowercase host, no fragment, tracking
     * parameters removed, sorted non-tracking query keys and a stable trailing slash policy.
     * This is not used to decide whether a URL is safe to fetch.
     */
    public static String canonicalizeArticleUrl(String raw) {
        if (raw == null) {
            return "";
        }
        try {
            URI uri = new URI(raw.trim());
            if (uri.getHost() == null || uri.getScheme() == null) {
                return raw.trim();
            }
            String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
            String host = uri.getHost().toLowerCase(Locale.ROOT);
            if (host.startsWith("www.")) {
                host = host.substring(4);
            }
            String path = uri.getPath() == null ? "/" : uri.getPath();
            if (path.length() > 1 && path.endsWith("/")) {
                path = path.substring(0, path.length() - 1);
            }
            List<String> pairs = new ArrayList<>();
            String query = uri.getQuery();
            if (query != null) {
                for (String part : query.split("&")) {
                    if (part.isEmpty()) {
                        continue;
                    }
                    String key = part;
                    int equals = part.indexOf('=');
                    if (equals >= 0) {
                        key = part.substring(0, equals);
                    }
                    if (!TRACKING_PARAMS.contains(key.toLowerCase(Locale.ROOT))) {
                        pairs.add(part);
                    }
                }
            }
            pairs.sort(String::compareTo);
            String cleanQuery = pairs.isEmpty() ? null : String.join("&", pairs);
            int port = uri.getPort();
            if ((scheme.equals("http") && port == 80) || (scheme.equals("https") && port == 443)) {
                port = -1;
            }
            return new URI(scheme, null, host, port, path, cleanQuery, null).toASCIIString();
        } catch (URISyntaxException e) {
            return raw.trim();
        }
    }

    /** Identity for feed subscriptions: exact URLs minus a fragment and default port. */
    public static String normalizeFeedUrl(String raw) {
        if (raw == null) {
            return "";
        }
        try {
            URI uri = new URI(raw.trim());
            if (uri.getHost() == null || uri.getScheme() == null) {
                return raw.trim();
            }
            int port = uri.getPort();
            if ((uri.getScheme().equalsIgnoreCase("http") && port == 80)
                    || (uri.getScheme().equalsIgnoreCase("https") && port == 443)) {
                port = -1;
            }
            String path = uri.getPath() == null || uri.getPath().isEmpty() ? "/" : uri.getPath();
            return new URI(
                    uri.getScheme().toLowerCase(Locale.ROOT),
                    null,
                    uri.getHost().toLowerCase(Locale.ROOT),
                    port,
                    path,
                    uri.getQuery(),
                    null).toASCIIString();
        } catch (URISyntaxException e) {
            return raw.trim();
        }
    }

    public static String origin(String url) {
        try {
            URI uri = new URI(url);
            if (uri.getHost() == null) {
                return url;
            }
            int port = uri.getPort();
            return uri.getScheme() + "://" + uri.getHost() + (port > 0 ? ":" + port : "");
        } catch (URISyntaxException e) {
            return url;
        }
    }

    public static String host(String url) {
        try {
            URI uri = new URI(url);
            String host = uri.getHost();
            if (host == null) {
                return "";
            }
            return host.startsWith("www.") ? host.substring(4) : host;
        } catch (URISyntaxException e) {
            return "";
        }
    }

    public static String withoutFragment(String url) {
        if (url == null) {
            return null;
        }
        int hash = url.indexOf('#');
        return hash >= 0 ? url.substring(0, hash) : url;
    }

    public static class HttpUrlException extends IllegalArgumentException {
        public HttpUrlException(String message) {
            super(message);
        }
    }
}
