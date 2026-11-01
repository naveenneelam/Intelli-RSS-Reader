package com.intellirs.reader.domain.duplicates;

import java.util.Locale;

/**
 * Conservative duplicate matching for syndicated stories. URL and content hash are strong
 * signals; normalized title is only accepted when publisher evidence agrees.
 */
public class DuplicateDetector {

    public DuplicateMatch match(ArticleFingerprint incoming, ArticleFingerprint existing) {
        if (incoming == null || existing == null) {
            return new DuplicateMatch(false, 0, "missing fingerprint");
        }
        if (notEmpty(incoming.canonicalUrl) && notEmpty(existing.canonicalUrl)
                && incoming.canonicalUrl.equals(existing.canonicalUrl)) {
            return new DuplicateMatch(true, 100, "same canonical URL");
        }
        if (notEmpty(incoming.contentHash) && notEmpty(existing.contentHash)
                && incoming.contentHash.equals(existing.contentHash)) {
            return new DuplicateMatch(true, 95, "same normalized content hash");
        }
        if (notEmpty(incoming.normalizedTitle) && notEmpty(existing.normalizedTitle)
                && incoming.normalizedTitle.equals(existing.normalizedTitle)) {
            String left = normalizedPublisher(incoming.publisher);
            String right = normalizedPublisher(existing.publisher);
            if (!left.isEmpty() && left.equals(right)) {
                return new DuplicateMatch(true, 85, "same normalized title and publisher");
            }
            if (!left.isEmpty() && !right.isEmpty()
                    && (left.contains(right) || right.contains(left))) {
                return new DuplicateMatch(true, 80, "syndicated title with related publisher");
            }
        }
        return new DuplicateMatch(false, 0, "not a duplicate");
    }

    private static String normalizedPublisher(String publisher) {
        if (publisher == null) {
            return "";
        }
        return publisher.toLowerCase(Locale.ROOT)
                .replace("www.", "")
                .replaceAll("[^a-z0-9]", "");
    }

    private static boolean notEmpty(String value) {
        return value != null && !value.isEmpty();
    }
}
