package com.intellirs.reader.ui.feed;

import com.intellirs.reader.data.entity.CategoryEntity;
import com.intellirs.reader.data.entity.FeedEntity;
import com.intellirs.reader.data.entity.FeedHealthEntity;
import com.intellirs.reader.data.model.FeedStats;
import com.intellirs.reader.domain.prioritization.FeedPrioritySuggestion;

import java.util.ArrayList;
import java.util.List;

public class FeedDetailData {
    public FeedEntity feed;
    public FeedStats stats;
    public FeedHealthEntity health;
    public FeedPrioritySuggestion suggestion;
    public String categoryName;
    public List<CategoryEntity> categories = new ArrayList<>();
}
