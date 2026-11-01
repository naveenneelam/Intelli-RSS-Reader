package com.intellirs.reader.domain.prioritization;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ScoringConfig {
    public int criticalThreshold = 85;
    public int highThreshold = 65;
    public int normalThreshold = 35;
    public int lowThreshold = 15;
    public final List<ScoringKeyword> priorityKeywords = new ArrayList<>();
    public final List<ScoringKeyword> mutedKeywords = new ArrayList<>();
    public final List<ScoringKeyword> topics = new ArrayList<>();
    public final Set<String> preferredAuthors = new HashSet<>();

    public static class ScoringKeyword {
        public final String keyword;
        public final int weight;

        public ScoringKeyword(String keyword, int weight) {
            this.keyword = keyword == null ? "" : keyword.trim();
            this.weight = Math.max(1, Math.min(100, weight));
        }
    }
}
