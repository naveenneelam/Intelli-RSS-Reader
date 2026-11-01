package com.intellirs.reader.domain.duplicates;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DuplicateDetectorTest {
    private final DuplicateDetector detector = new DuplicateDetector();

    @Test
    public void canonicalUrlIsStrongestMatch() {
        DuplicateMatch match = detector.match(
                new ArticleFingerprint("https://a.example/story", "guid1", "title", "hash1", "A"),
                new ArticleFingerprint("https://a.example/story", "guid2", "other", "hash2", "B"));
        assertTrue(match.match);
        assertTrue(match.reason.contains("canonical"));
    }

    @Test
    public void contentHashMatchesSyndicatedCopy() {
        DuplicateMatch match = detector.match(
                new ArticleFingerprint("https://a.example/1", "g1", "Title", "same-hash", "A"),
                new ArticleFingerprint("https://b.example/2", "g2", "Diff", "same-hash", "B"));
        assertTrue(match.match);
        assertTrue(match.reason.contains("content hash"));
    }

    @Test
    public void sameTitleSamePublisherMatches() {
        DuplicateMatch match = detector.match(
                new ArticleFingerprint("https://a.example/1", "g1", "title", "h1", "Example News"),
                new ArticleFingerprint("https://a.example/2", "g2", "title", "h2", "Example News"));
        assertTrue(match.match);
    }

    @Test
    public void titleAloneAcrossDifferentPublishersDoesNotMatch() {
        DuplicateMatch match = detector.match(
                new ArticleFingerprint("https://a.example/1", "g1", "title", "h1", "Publisher A"),
                new ArticleFingerprint("https://b.example/2", "g2", "title", "h2", "Publisher B"));
        assertFalse(match.match);
    }
}
