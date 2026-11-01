package com.intellirs.reader.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class UrlUtilsTest {
    @Test
    public void canonicalizationRemovesTrackingAndFragments() {
        String canonical = UrlUtils.canonicalizeArticleUrl(
                "https://WWW.Example.com/story/?utm_source=rss&b=2&a=1#section");
        assertEquals("https://example.com/story?a=1&b=2", canonical);
    }

    @Test
    public void userInputAddsHttps() {
        assertEquals("https://example.com/feed",
                UrlUtils.normalizeUserInput("example.com/feed", false));
    }

    @Test(expected = IllegalArgumentException.class)
    public void httpIsRejectedByDefault() {
        UrlUtils.normalizeUserInput("http://example.com/feed", false);
    }

    @Test
    public void httpCanBeExplicitlyAllowed() {
        assertEquals("http://example.com/feed",
                UrlUtils.normalizeUserInput("http://example.com/feed", true));
    }

    @Test
    public void unsafeSchemesAreRejected() {
        assertFalse(UrlUtils.isValidHttpUrl("javascript:alert(1)"));
        assertFalse(UrlUtils.isValidHttpUrl("file:///etc/passwd"));
        assertFalse(UrlUtils.isValidHttpUrl("data:text/html,x"));
        assertTrue(UrlUtils.isValidHttpUrl("https://example.com/x"));
    }

    @Test
    public void relativeLinksResolveAndUnsafeLinksDoNot() {
        assertEquals("https://example.com/images/x.jpg",
                UrlUtils.resolve("https://example.com/story/one", "/images/x.jpg"));
        assertEquals(null, UrlUtils.resolve("https://example.com/", "javascript:alert(1)"));
    }
}
