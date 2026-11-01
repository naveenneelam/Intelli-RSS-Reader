package com.intellirs.reader.domain.notification;

import com.intellirs.reader.data.entity.ArticleRelevanceLevel;
import com.intellirs.reader.data.entity.FeedImportance;

/** All inputs used by the transparent notification eligibility decision. */
public class NotificationContext {
    public boolean globalNotificationsEnabled = true;
    public boolean androidPermissionGranted = true;
    public boolean feedNotificationsEnabled = true;
    public FeedImportance feedImportance = FeedImportance.NORMAL;
    public FeedImportance minimumFeedImportance = FeedImportance.HIGH;
    public boolean categoryMuted;
    public boolean mutedKeywordMatched;
    public boolean filterMuted;
    public boolean filterForceNotify;
    public ArticleRelevanceLevel relevanceLevel = ArticleRelevanceLevel.NORMAL;
    public int relevanceScore;
    public int minimumRelevance = 65;
    public boolean alreadyNotified;
    public boolean duplicateSuppressed;
    public boolean isRead;
    public boolean quietHoursActive;
    public boolean allowCriticalDuringQuietHours;
    public int notificationsLastHour;
    public int notificationsLastDay;
    public int maxNotificationsPerHour = 6;
    public int maxNotificationsPerDay = 40;
}
