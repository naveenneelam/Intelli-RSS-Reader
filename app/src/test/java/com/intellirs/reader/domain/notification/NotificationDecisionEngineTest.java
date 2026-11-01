package com.intellirs.reader.domain.notification;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.intellirs.reader.data.entity.ArticleRelevanceLevel;
import com.intellirs.reader.data.entity.FeedImportance;

import org.junit.Test;

public class NotificationDecisionEngineTest {
    private final NotificationDecisionEngine engine = new NotificationDecisionEngine();

    @Test
    public void importantFeedAndRelevantArticleIsEligible() {
        NotificationContext context = base();
        NotificationDecision decision = engine.decide(context);
        assertTrue(decision.eligible);
        assertEquals(NotificationChannels.HIGH, decision.channelId);
        assertTrue(decision.reason.contains("relevance 80"));
        assertTrue(decision.reason.contains("feed importance HIGH"));
    }

    @Test
    public void lowFeedIsSuppressed() {
        NotificationContext context = base();
        context.feedImportance = FeedImportance.LOW;
        NotificationDecision decision = engine.decide(context);
        assertFalse(decision.eligible);
        assertTrue(decision.reason.contains("below policy"));
    }

    @Test
    public void mutedKeywordSuppressesEvenFilterEnable() {
        NotificationContext context = base();
        context.mutedKeywordMatched = true;
        context.filterForceNotify = true;
        NotificationDecision decision = engine.decide(context);
        assertFalse(decision.eligible);
        assertTrue(decision.reason.contains("muted keyword"));
    }

    @Test
    public void quietHoursSuppressUnlessCriticalAllowed() {
        NotificationContext context = base();
        context.quietHoursActive = true;
        assertFalse(engine.decide(context).eligible);
        context.allowCriticalDuringQuietHours = true;
        context.relevanceLevel = ArticleRelevanceLevel.CRITICAL;
        assertTrue(engine.decide(context).eligible);
    }

    @Test
    public void frequencyLimitsSuppress() {
        NotificationContext context = base();
        context.notificationsLastHour = context.maxNotificationsPerHour;
        assertFalse(engine.decide(context).eligible);
        assertTrue(engine.decide(context).reason.contains("hourly notification limit"));
    }

    @Test
    public void previouslyNotifiedIsSuppressed() {
        NotificationContext context = base();
        context.alreadyNotified = true;
        assertFalse(engine.decide(context).eligible);
        assertTrue(engine.decide(context).reason.contains("already notified"));
    }

    @Test
    public void duplicateSuppressionIsExplained() {
        NotificationContext context = base();
        context.duplicateSuppressed = true;
        NotificationDecision decision = engine.decide(context);
        assertFalse(decision.eligible);
        assertTrue(decision.reason.contains("duplicate content"));
    }

    @Test
    public void filterForceNotifyBypassesThresholdsButRespectsMute() {
        NotificationContext context = base();
        context.feedImportance = FeedImportance.LOW;
        context.relevanceScore = 10;
        context.filterForceNotify = true;
        assertTrue(engine.decide(context).eligible);
        context.filterMuted = true;
        assertFalse(engine.decide(context).eligible);
    }

    @Test
    public void channelMapsCritical() {
        NotificationContext context = base();
        context.relevanceLevel = ArticleRelevanceLevel.CRITICAL;
        assertEquals(NotificationChannels.CRITICAL, engine.decide(context).channelId);
    }

    private NotificationContext base() {
        NotificationContext context = new NotificationContext();
        context.globalNotificationsEnabled = true;
        context.androidPermissionGranted = true;
        context.feedNotificationsEnabled = true;
        context.feedImportance = FeedImportance.HIGH;
        context.minimumFeedImportance = FeedImportance.HIGH;
        context.relevanceLevel = ArticleRelevanceLevel.HIGH;
        context.relevanceScore = 80;
        context.minimumRelevance = 65;
        context.maxNotificationsPerHour = 6;
        context.maxNotificationsPerDay = 40;
        context.notificationsLastHour = 2;
        context.notificationsLastDay = 10;
        return context;
    }
}
