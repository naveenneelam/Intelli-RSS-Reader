package com.intellirs.reader.domain.prioritization;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.intellirs.reader.data.entity.ArticleRelevanceLevel;

import org.junit.Test;

public class ArticleScoringEngineTest {
    private final ArticleScoringEngine engine = new ArticleScoringEngine();

    @Test
    public void priorityKeywordAndRecencyRaiseRelevance() {
        ScoringConfig config = new ScoringConfig();
        config.priorityKeywords.add(new ScoringConfig.ScoringKeyword("Reserve Bank", 80));
        ScoringInput input = new ScoringInput();
        input.title = "Reserve Bank holds interest rates";
        input.description = "The central bank kept rates unchanged.";
        input.publishedAt = System.currentTimeMillis() - 3_600_000L;
        input.now = System.currentTimeMillis();
        input.feedReadRate = 0.5f;
        ScoringResult result = engine.score(input, config);
        assertTrue(result.score >= config.highThreshold);
        assertEquals(ArticleRelevanceLevel.HIGH, result.level);
        assertFalse(result.mutedKeywordMatched);
        assertTrue(result.reasonText.contains("priority keyword"));
    }

    @Test
    public void mutedKeywordLowersScoreAndBlocksNotificationSignal() {
        ScoringConfig config = new ScoringConfig();
        config.mutedKeywords.add(new ScoringConfig.ScoringKeyword("celebrity", 50));
        ScoringInput input = new ScoringInput();
        input.title = "Celebrity gossip roundup";
        input.description = "A celebrity story with no substance.";
        input.now = System.currentTimeMillis();
        ScoringResult result = engine.score(input, config);
        assertTrue(result.mutedKeywordMatched);
        assertTrue(result.score < config.normalThreshold);
    }

    @Test
    public void preferredAuthorAddsTransparentReason() {
        ScoringConfig config = new ScoringConfig();
        config.preferredAuthors.add("Asha Nair");
        ScoringInput input = new ScoringInput();
        input.title = "A plain headline";
        input.author = "Asha Nair";
        ScoringResult result = engine.score(input, config);
        assertTrue(result.reasonText.contains("preferred author"));
    }

    @Test
    public void thresholdsAreConfigurable() {
        ScoringConfig config = new ScoringConfig();
        config.criticalThreshold = 70;
        config.highThreshold = 50;
        config.normalThreshold = 20;
        config.lowThreshold = 5;
        ScoringInput input = new ScoringInput();
        input.title = "Reserve Bank policy";
        config.priorityKeywords.add(new ScoringConfig.ScoringKeyword("Reserve Bank", 80));
        input.publishedAt = System.currentTimeMillis() - 3_600_000L;
        ScoringResult result = engine.score(input, config);
        assertEquals(ScoringResult.levelFor(result.score, config), result.level);
        if (result.score >= 70) {
            assertEquals(ArticleRelevanceLevel.CRITICAL, result.level);
        }
    }

    @Test
    public void emptyArticleScoresZero() {
        ScoringInput input = new ScoringInput();
        ScoringResult result = engine.score(input, new ScoringConfig());
        assertEquals(0, result.score);
        assertEquals(ArticleRelevanceLevel.IRRELEVANT, result.level);
    }
}
