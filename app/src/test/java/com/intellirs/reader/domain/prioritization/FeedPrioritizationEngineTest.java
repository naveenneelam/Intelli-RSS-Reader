package com.intellirs.reader.domain.prioritization;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.intellirs.reader.data.entity.FeedImportance;
import com.intellirs.reader.data.model.FeedStats;

import org.junit.Test;

public class FeedPrioritizationEngineTest {
    private final FeedPrioritizationEngine engine = new FeedPrioritizationEngine();

    @Test
    public void highEngagementFeedBecomesCritical() {
        FeedStats stats = new FeedStats();
        stats.totalArticles = 100;
        stats.readArticles = 95;
        stats.bookmarkedArticles = 12;
        FeedPrioritySuggestion suggestion = engine.suggest(stats, 0,
                System.currentTimeMillis() - 3_600_000L);
        assertEquals(FeedImportance.CRITICAL, suggestion.importance);
        assertTrue(suggestion.confidence > 50);
        assertTrue(suggestion.reason.contains("Read rate"));
    }

    @Test
    public void failingUnreadFeedSuggestLow() {
        FeedStats stats = new FeedStats();
        stats.totalArticles = 20;
        stats.readArticles = 0;
        stats.bookmarkedArticles = 0;
        FeedPrioritySuggestion suggestion = engine.suggest(stats, 4,
                System.currentTimeMillis() - 60L * 86_400_000L);
        assertEquals(FeedImportance.LOW, suggestion.importance);
        assertTrue(suggestion.reason.contains("failures"));
    }

    @Test
    public void noHistorySuggestsNormal() {
        FeedPrioritySuggestion suggestion = engine.suggest(new FeedStats(), 0, 0);
        assertEquals(FeedImportance.NORMAL, suggestion.importance);
    }
}
