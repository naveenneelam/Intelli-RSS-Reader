package com.intellirs.reader.domain.prioritization;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Transparent rule-based article relevance. It never reads FeedImportance: a critical
 * feed is not allowed to make an unrelated article relevant.
 *
 * The engine is deliberately dependency-free so it can be tested exhaustively and later
 * augmented by a local AI scoring provider behind the same interface.
 */
public class ArticleScoringEngine {
    public ScoringResult score(ScoringInput input, ScoringConfig config) {
        if (input == null) {
            input = new ScoringInput();
        }
        if (config == null) {
            config = new ScoringConfig();
        }
        List<String> reasons = new ArrayList<>();
        int score = 25;
        reasons.add("base 25");

        String title = lower(input.title);
        String description = lower(input.description);
        String content = lower(input.content);
        String author = lower(input.author);
        String allText = title + " " + description + " " + content;
        boolean muted = false;

        int priorityBoost = 0;
        for (ScoringConfig.ScoringKeyword keyword : config.priorityKeywords) {
            String term = lower(keyword.keyword);
            if (term.isEmpty()) {
                continue;
            }
            int boost = 0;
            if (title.contains(term)) {
                boost += 12 + Math.round(keyword.weight * 0.18f);
            } else if (description.contains(term)) {
                boost += 6 + Math.round(keyword.weight * 0.09f);
            } else if (content.contains(term)) {
                boost += 4 + Math.round(keyword.weight * 0.06f);
            }
            if (boost > 0) {
                priorityBoost += boost;
                reasons.add("+" + boost + " priority keyword \"" + keyword.keyword + "\"");
            }
        }
        priorityBoost = Math.min(35, priorityBoost);
        score += priorityBoost;

        int topicBoost = 0;
        for (ScoringConfig.ScoringKeyword topic : config.topics) {
            String term = lower(topic.keyword);
            if (!term.isEmpty() && allText.contains(term)) {
                int boost = 6 + Math.round(topic.weight * 0.10f);
                topicBoost += boost;
                reasons.add("+" + boost + " preferred topic \"" + topic.keyword + "\"");
            }
        }
        topicBoost = Math.min(20, topicBoost);
        score += topicBoost;

        int mutedPenalty = 0;
        for (ScoringConfig.ScoringKeyword keyword : config.mutedKeywords) {
            String term = lower(keyword.keyword);
            if (term.isEmpty()) {
                continue;
            }
            if (title.contains(term)) {
                mutedPenalty += 45;
                muted = true;
                reasons.add("-45 muted keyword in title \"" + keyword.keyword + "\"");
            } else if (description.contains(term) || content.contains(term)) {
                mutedPenalty += 25;
                muted = true;
                reasons.add("-25 muted keyword \"" + keyword.keyword + "\"");
            }
        }
        mutedPenalty = Math.min(65, mutedPenalty);
        score -= mutedPenalty;

        if (!author.isEmpty()) {
            for (String preferred : config.preferredAuthors) {
                String normalized = lower(preferred);
                if (!normalized.isEmpty() && author.contains(normalized)) {
                    score += 12;
                    reasons.add("+12 preferred author \"" + preferred + "\"");
                    break;
                }
            }
        }

        if (input.publishedAt > 0) {
            long age = Math.max(0, input.now - input.publishedAt);
            if (age <= 6L * 3_600_000L) {
                score += 14;
                reasons.add("+14 published in the last 6 hours");
            } else if (age <= 24L * 3_600_000L) {
                score += 10;
                reasons.add("+10 published today");
            } else if (age <= 3L * 86_400_000L) {
                score += 6;
                reasons.add("+6 published in the last 3 days");
            } else if (age <= 7L * 86_400_000L) {
                score += 2;
                reasons.add("+2 published this week");
            } else if (age > 30L * 86_400_000L) {
                score -= 8;
                reasons.add("-8 older than 30 days");
            }
        }

        if (input.feedReadRate > 0f) {
            int engagement = Math.min(12, Math.round(input.feedReadRate * 12f));
            if (engagement > 0) {
                score += engagement;
                reasons.add("+" + engagement + " historical engagement with this feed");
            }
        }

        if (input.hasMedia) {
            score += 2;
            reasons.add("+2 contains media");
        }
        if (input.description != null && input.description.length() > 400) {
            score += 3;
            reasons.add("+3 substantive summary");
        }
        if (title.isEmpty() && description.isEmpty() && content.isEmpty()) {
            score = 0;
            reasons.add("no article text available");
        }

        return ScoringResult.fromScore(score, config, reasons, muted);
    }

    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
