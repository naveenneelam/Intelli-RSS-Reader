package com.intellirs.reader.domain.prioritization;

import com.intellirs.reader.data.entity.FeedImportance;
import com.intellirs.reader.data.model.FeedStats;

/**
 * Suggests feed importance from observed engagement, feed volume, recency and reliability.
 * A user's explicit choice is stored separately and always wins.
 */
public class FeedPrioritizationEngine {

    public FeedPrioritySuggestion suggest(FeedStats stats, int failureCount, long lastSuccessAt) {
        if (stats == null || stats.totalArticles == 0) {
            return new FeedPrioritySuggestion(FeedImportance.NORMAL, 20,
                    "No reading history yet; Normal is a safe starting point.");
        }
        float rate = stats.totalArticles == 0 ? 0f
                : Math.min(1f, stats.readArticles / (float) stats.totalArticles);
        int engagement = Math.round(rate * 45f);
        int bookmarkScore = Math.min(12, stats.bookmarkedArticles * 2);
        int volumeScore = Math.min(12, Math.round(stats.totalArticles / 25f));
        int reliability = failureCount == 0 ? 15 : Math.max(0, 15 - failureCount * 4);
        long age = lastSuccessAt <= 0 ? Long.MAX_VALUE
                : System.currentTimeMillis() - lastSuccessAt;
        int recency = age < 7_200_000L ? 12 : age < 24L * 3_600_000L ? 8
                : age < 7L * 86_400_000L ? 4 : 0;
        int score = 8 + engagement + bookmarkScore + volumeScore + reliability + recency;
        score = Math.max(0, Math.min(100, score));

        FeedImportance importance;
        if (score >= 82) {
            importance = FeedImportance.CRITICAL;
        } else if (score >= 62) {
            importance = FeedImportance.HIGH;
        } else if (score >= 32) {
            importance = FeedImportance.NORMAL;
        } else {
            importance = FeedImportance.LOW;
        }

        StringBuilder reason = new StringBuilder();
        reason.append("Read rate ").append(Math.round(rate * 100)).append('%');
        if (stats.bookmarkedArticles > 0) {
            reason.append(", ").append(stats.bookmarkedArticles).append(" bookmarks");
        }
        reason.append(", ").append(stats.totalArticles).append(" stored articles");
        if (failureCount > 0) {
            reason.append(", ").append(failureCount).append(" recent failures");
        }
        reason.append(". Suggestion score ").append(score).append(" of 100.");
        int confidence = Math.min(95, 20 + Math.round(rate * 50) + Math.min(15, stats.totalArticles / 10));
        return new FeedPrioritySuggestion(importance, confidence, reason.toString());
    }
}
