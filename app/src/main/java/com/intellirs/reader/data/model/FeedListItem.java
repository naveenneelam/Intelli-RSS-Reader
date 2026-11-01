package com.intellirs.reader.data.model;

import androidx.room.Embedded;

import com.intellirs.reader.data.entity.FeedEntity;
import com.intellirs.reader.data.entity.FeedHealthStatus;

/** Efficient home-row projection: feed + category + counts + health in one query. */
public class FeedListItem {
    @Embedded
    public FeedEntity feed;

    public String categoryName;

    public int unreadCount;

    public int totalCount;

    public long latestPublishedAt;

    public FeedHealthStatus healthStatus = FeedHealthStatus.HEALTHY;
}
