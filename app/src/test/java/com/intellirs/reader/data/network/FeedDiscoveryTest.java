package com.intellirs.reader.data.network;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

public class FeedDiscoveryTest {
    @Test
    public void findsRssAndAtomAlternateLinks() {
        String html = "<html><head>" +
                "<link rel=\"alternate\" type=\"application/rss+xml\" href=\"/rss.xml\">" +
                "<link rel=\"alternate\" type=\"application/atom+xml\" href=\"https://example.com/atom\">" +
                "</head><body><a href=\"/feed\">RSS</a></body></html>";
        List<String> links = FeedDiscovery.extractFeedLinks(html, "https://example.com/news/");
        assertTrue(links.contains("https://example.com/rss.xml"));
        assertTrue(links.contains("https://example.com/atom"));
        assertTrue(links.contains("https://example.com/feed"));
    }

    @Test
    public void ignoresJavascriptAndNonFeedLinks() {
        String html = "<html><body><a href=\"javascript:alert(1)\">x</a>" +
                "<a href=\"/about\">About</a></body></html>";
        List<String> links = FeedDiscovery.extractFeedLinks(html, "https://example.com/");
        assertTrue(links.isEmpty());
    }
}
