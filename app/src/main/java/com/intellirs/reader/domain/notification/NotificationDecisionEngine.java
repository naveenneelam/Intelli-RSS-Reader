package com.intellirs.reader.domain.notification;

import com.intellirs.reader.data.entity.ArticleRelevanceLevel;
import com.intellirs.reader.data.entity.FeedImportance;

import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic, fully documented notification eligibility. Every blocker is appended to
 * the audit reason, whether or not a previous blocker already decided the result.
 */
public class NotificationDecisionEngine {

    public NotificationDecision decide(NotificationContext context) {
        if (context == null) {
            return new NotificationDecision(false, "No notification context", NotificationChannels.GENERAL);
        }
        List<String> reasons = new ArrayList<>();
        boolean eligible = true;

        if (!context.globalNotificationsEnabled) {
            eligible = false;
            reasons.add("global notifications are disabled");
        } else {
            reasons.add("global notifications enabled");
        }
        if (!context.androidPermissionGranted) {
            eligible = false;
            reasons.add("Android notification permission is not granted");
        }
        if (!context.feedNotificationsEnabled) {
            eligible = false;
            reasons.add("feed notifications are disabled");
        }
        boolean importancePass = context.feedImportance.rank() <= context.minimumFeedImportance.rank();
        if (!importancePass && !context.filterForceNotify) {
            eligible = false;
            reasons.add("feed " + context.feedImportance.name() + " is below policy "
                    + context.minimumFeedImportance.name());
        } else if (!importancePass) {
            reasons.add("feed importance bypassed by a filter rule");
        } else {
            reasons.add("feed importance " + context.feedImportance.name() + " meets policy "
                    + context.minimumFeedImportance.name());
        }
        if (context.categoryMuted) {
            eligible = false;
            reasons.add("category is muted");
        }
        if (context.mutedKeywordMatched) {
            eligible = false;
            reasons.add("article matched a muted keyword");
        }
        if (context.filterMuted) {
            eligible = false;
            reasons.add("a filter rule muted notifications");
        }
        if (context.isRead) {
            eligible = false;
            reasons.add("article is already read");
        }
        boolean relevancePass = context.relevanceScore >= context.minimumRelevance;
        if (!relevancePass && !context.filterForceNotify) {
            eligible = false;
            reasons.add("relevance " + context.relevanceScore + " is below threshold "
                    + context.minimumRelevance);
        } else if (!relevancePass) {
            reasons.add("relevance threshold bypassed by a filter rule");
        } else {
            reasons.add("relevance " + context.relevanceScore + " meets threshold "
                    + context.minimumRelevance);
        }
        if (context.alreadyNotified) {
            eligible = false;
            reasons.add("article was already notified");
        }
        if (context.duplicateSuppressed) {
            eligible = false;
            reasons.add("duplicate content was suppressed");
        }
        boolean criticalExempt = context.quietHoursActive
                && context.allowCriticalDuringQuietHours
                && context.relevanceLevel == ArticleRelevanceLevel.CRITICAL;
        if (context.quietHoursActive && !criticalExempt) {
            eligible = false;
            reasons.add("quiet hours are active");
        } else if (criticalExempt) {
            reasons.add("Critical article allowed during quiet hours");
        }
        if (context.notificationsLastHour >= context.maxNotificationsPerHour) {
            eligible = false;
            reasons.add("hourly notification limit reached ("
                    + context.notificationsLastHour + "/" + context.maxNotificationsPerHour + ")");
        } else {
            reasons.add("hourly limit " + context.notificationsLastHour + "/"
                    + context.maxNotificationsPerHour);
        }
        if (context.notificationsLastDay >= context.maxNotificationsPerDay) {
            eligible = false;
            reasons.add("daily notification limit reached ("
                    + context.notificationsLastDay + "/" + context.maxNotificationsPerDay + ")");
        } else {
            reasons.add("daily limit " + context.notificationsLastDay + "/"
                    + context.maxNotificationsPerDay);
        }
        if (context.filterForceNotify) {
            reasons.add("a filter rule explicitly enabled notification");
        }
        reasons.add("feed priority: " + context.feedImportance.name()
                + " (article relevance is independent)");

        String channel;
        if (context.relevanceLevel == ArticleRelevanceLevel.CRITICAL) {
            channel = NotificationChannels.CRITICAL;
        } else if (context.relevanceLevel == ArticleRelevanceLevel.HIGH) {
            channel = NotificationChannels.HIGH;
        } else {
            channel = NotificationChannels.GENERAL;
        }
        String reason = (eligible ? "Eligible: " : "Suppressed: ") + String.join("; ", reasons);
        return new NotificationDecision(eligible, reason, channel);
    }

    public FeedImportance minimumImportanceForPolicy(String policy) {
        if (policy == null) {
            return FeedImportance.HIGH;
        }
        switch (policy) {
            case "CRITICAL":
                return FeedImportance.CRITICAL;
            case "HIGH":
                return FeedImportance.HIGH;
            case "NORMAL":
                return FeedImportance.NORMAL;
            case "ALL":
                return FeedImportance.MUTED;
            default:
                return FeedImportance.HIGH;
        }
    }
}
