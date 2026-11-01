package com.intellirs.reader.data.network;

import androidx.annotation.Nullable;

import com.intellirs.reader.data.parser.FeedParseException;
import com.intellirs.reader.data.parser.FeedParser;
import com.intellirs.reader.data.parser.ParsedFeed;
import com.intellirs.reader.util.UrlUtils;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * RSS/Atom autodiscovery: validates direct feeds, examines HTML alternate links and
 * probes a small set of conventional endpoints. Every candidate is fetched and parsed
 * before it is shown to the user.
 */
public class FeedDiscovery {
    private static final int MAX_HTML_BYTES = 3 * 1024 * 1024;
    private static final int MAX_FEED_BYTES = 5 * 1024 * 1024;
    private static final String[] COMMON_PATHS = new String[]{
            "/feed", "/feed.xml", "/rss", "/rss.xml", "/atom.xml", "/index.xml",
            "/feeds/posts/default?alt=rss"
    };

    private final HttpFetcher httpFetcher;
    private final FeedParser feedParser;

    public FeedDiscovery(HttpFetcher httpFetcher, FeedParser feedParser) {
        this.httpFetcher = httpFetcher;
        this.feedParser = feedParser;
    }

    public DiscoveryResult discover(String inputUrl, boolean allowHttp) throws DiscoveryException {
        String url;
        try {
            url = UrlUtils.normalizeUserInput(inputUrl, allowHttp);
        } catch (IllegalArgumentException e) {
            throw new DiscoveryException(e.getMessage(), e);
        }

        DiscoveryResult result = new DiscoveryResult();
        result.inspectedUrl = url;
        HttpResult response;
        try {
            response = httpFetcher.fetch(url, null, null, MAX_HTML_BYTES, allowHttp);
        } catch (HttpException e) {
            throw new DiscoveryException(e.safeMessage, e);
        }
        String body = response.bodyString();
        String finalUrl = response.finalUrl == null ? url : response.finalUrl;

        // A direct feed URL is the fastest path and does not need HTML parsing.
        if (feedParser.looksLikeFeed(body)) {
            ParsedFeed parsed;
            try {
                parsed = feedParser.parse(body, finalUrl);
            } catch (FeedParseException e) {
                throw new DiscoveryException("The response looks like a feed but could not be parsed: "
                        + e.getMessage(), e);
            }
            result.candidates.add(candidateFromFeed(parsed, finalUrl));
            return result;
        }

        Set<String> discovered = new LinkedHashSet<>();
        if (looksLikeHtml(body, response.contentType)) {
            Document document = Jsoup.parse(body, finalUrl);
            discovered.addAll(extractFeedLinks(document, finalUrl));
        }

        if (discovered.isEmpty()) {
            String origin = UrlUtils.origin(finalUrl);
            for (String path : COMMON_PATHS) {
                discovered.add(origin + path);
            }
        }

        // Some publishers advertise a feed through an anchor rather than <link>.
        int attempts = 0;
        for (String candidateUrl : discovered) {
            if (attempts >= 6) {
                break;
            }
            attempts++;
            inspectCandidate(candidateUrl, allowHttp, result);
        }

        // When advertised links were stale, try conventional endpoints as a fallback.
        if (result.candidates.isEmpty()) {
            String origin = UrlUtils.origin(finalUrl);
            for (String path : COMMON_PATHS) {
                if (attempts >= 8) {
                    break;
                }
                String candidateUrl = origin + path;
                if (discovered.contains(candidateUrl)) {
                    continue;
                }
                attempts++;
                inspectCandidate(candidateUrl, allowHttp, result);
            }
        }

        if (result.candidates.isEmpty()) {
            throw new DiscoveryException(
                    "No RSS, Atom or RDF feed was found at this address. "
                            + "The site may not publish a feed, or it may block automated requests.");
        }
        return result;
    }

    private void inspectCandidate(String candidateUrl, boolean allowHttp, DiscoveryResult result) {
        try {
            HttpResult feedResponse = httpFetcher.fetch(candidateUrl, null, null,
                    MAX_FEED_BYTES, allowHttp);
            String feedBody = feedResponse.bodyString();
            if (!feedParser.looksLikeFeed(feedBody)) {
                result.warnings.add(candidateUrl + " is not a feed");
                return;
            }
            ParsedFeed parsed = feedParser.parse(feedBody,
                    feedResponse.finalUrl == null ? candidateUrl : feedResponse.finalUrl);
            FeedCandidate candidate = candidateFromFeed(parsed,
                    feedResponse.finalUrl == null ? candidateUrl : feedResponse.finalUrl);
            boolean alreadyPresent = false;
            for (FeedCandidate existing : result.candidates) {
                if (UrlUtils.normalizeFeedUrl(existing.feedUrl)
                        .equals(UrlUtils.normalizeFeedUrl(candidate.feedUrl))) {
                    alreadyPresent = true;
                    break;
                }
            }
            if (!alreadyPresent) {
                result.candidates.add(candidate);
            }
        } catch (HttpException e) {
            result.warnings.add(candidateUrl + ": " + e.safeMessage);
        } catch (FeedParseException e) {
            result.warnings.add(candidateUrl + " could not be parsed");
        }
    }

    /**
     * Public for tests and for reuse by the favicon fetcher. Uses rel=alternate feed types
     * plus conservative anchor heuristics.
     */
    public static List<String> extractFeedLinks(String html, String baseUrl) {
        return extractFeedLinks(Jsoup.parse(html, baseUrl), baseUrl);
    }

    private static List<String> extractFeedLinks(Document document, String baseUrl) {
        Set<String> links = new LinkedHashSet<>();
        Elements elements = document.select("link[href], a[href]");
        for (Element element : elements) {
            String rel = element.attr("rel").toLowerCase(Locale.ROOT);
            String type = element.attr("type").toLowerCase(Locale.ROOT);
            String href = element.attr("href");
            boolean feedType = type.contains("application/rss+xml")
                    || type.contains("application/atom+xml")
                    || type.contains("application/rdf+xml")
                    || type.contains("application/rss")
                    || type.contains("text/xml");
            boolean alternate = rel.contains("alternate") || element.tagName().equals("a");
            boolean feedHint = href.toLowerCase(Locale.ROOT).matches(
                    ".*(rss|atom|feed|feeds|\\?feed=|/index\\.xml|\\.rdf|/feed/).*");
            if ((feedType && alternate) || (element.tagName().equals("a") && feedHint
                    && (type.isEmpty() || type.contains("xml")))) {
                String resolved = UrlUtils.resolve(baseUrl, href);
                if (resolved != null) {
                    links.add(resolved);
                }
            }
        }
        return new ArrayList<>(links);
    }

    private static FeedCandidate candidateFromFeed(ParsedFeed feed, String finalUrl) {
        FeedCandidate candidate = new FeedCandidate();
        candidate.title = feed.title == null || feed.title.isEmpty()
                ? UrlUtils.host(finalUrl) : feed.title;
        candidate.feedUrl = finalUrl;
        candidate.websiteUrl = feed.siteUrl;
        candidate.description = feed.description;
        candidate.feedType = feed.feedType;
        candidate.suggestedCategory = feed.categories.isEmpty()
                ? "General" : feed.categories.get(0);
        candidate.articleCount = feed.articles.size();
        return candidate;
    }

    private static boolean looksLikeHtml(String body, String contentType) {
        String type = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        return type.contains("text/html") || type.contains("application/xhtml")
                || body.trim().toLowerCase(Locale.ROOT).startsWith("<!doctype html")
                || body.trim().toLowerCase(Locale.ROOT).startsWith("<html");
    }

    @Nullable
    public static FeedCandidate toCandidate(FeedCandidate candidate) {
        return candidate;
    }
}
