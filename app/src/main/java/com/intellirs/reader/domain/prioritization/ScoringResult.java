package com.intellirs.reader.domain.prioritization;

import com.intellirs.reader.data.entity.ArticleRelevanceLevel;

import java.util.ArrayList;
import java.util.List;

public class ScoringResult {
    public int score;
    public ArticleRelevanceLevel level = ArticleRelevanceLevel.NORMAL;
    public boolean mutedKeywordMatched;
    public final List<String> reasons = new ArrayList<>();
    public String reasonText = "";

    public static ScoringResult fromScore(int score, ScoringConfig config, List<String> reasons,
                                          boolean muted) {
        ScoringResult result = new ScoringResult();
        result.score = Math.max(0, Math.min(100, score));
        result.mutedKeywordMatched = muted;
        result.reasons.addAll(reasons);
        result.level = levelFor(result.score, config);
        result.reasonText = String.join("; ", result.reasons);
        return result;
    }

    public static ArticleRelevanceLevel levelFor(int score, ScoringConfig config) {
        if (score >= config.criticalThreshold) {
            return ArticleRelevanceLevel.CRITICAL;
        }
        if (score >= config.highThreshold) {
            return ArticleRelevanceLevel.HIGH;
        }
        if (score >= config.normalThreshold) {
            return ArticleRelevanceLevel.NORMAL;
        }
        if (score >= config.lowThreshold) {
            return ArticleRelevanceLevel.LOW;
        }
        return ArticleRelevanceLevel.IRRELEVANT;
    }
}
