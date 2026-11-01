package com.intellirs.reader.domain.notification;

import android.util.Log;

import com.intellirs.reader.data.dao.ArticleDao;
import com.intellirs.reader.data.dao.ArticleTagDao;
import com.intellirs.reader.data.dao.CategoryDao;
import com.intellirs.reader.data.dao.FeedDao;
import com.intellirs.reader.data.dao.FilterRuleDao;
import com.intellirs.reader.data.dao.KeywordRuleDao;
import com.intellirs.reader.data.dao.NotificationDao;
import com.intellirs.reader.data.entity.ArticleEntity;
import com.intellirs.reader.data.entity.CategoryEntity;
import com.intellirs.reader.data.entity.FeedEntity;
import com.intellirs.reader.data.entity.FilterRuleEntity;
import com.intellirs.reader.data.entity.KeywordRuleEntity;
import com.intellirs.reader.data.entity.KeywordType;
import com.intellirs.reader.data.entity.NotificationQueueEntity;
import com.intellirs.reader.data.entity.NotificationStatus;
import com.intellirs.reader.data.repository.SettingsRepository;
import com.intellirs.reader.domain.filtering.FilterContext;
import com.intellirs.reader.domain.filtering.FilterEngine;
import com.intellirs.reader.domain.filtering.FilterEvaluation;
import com.intellirs.reader.domain.filtering.FilterRule;
import com.intellirs.reader.domain.filtering.FilterRuleCodec;
import com.intellirs.reader.service.NotificationHelper;
import com.intellirs.reader.util.HashUtils;

import java.time.LocalTime;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Runs after a sync has persisted articles. Parser code never creates notifications; this
 * coordinator applies feed preferences, independent relevance, filters, quiet hours,
 * duplicate suppression and frequency limits, and writes a reason for every decision.
 */
public class NotificationCoordinator {
    private static final String TAG = "NotificationCoordinator";

    private final NotificationDao notificationDao;
    private final ArticleDao articleDao;
    private final FeedDao feedDao;
    private final CategoryDao categoryDao;
    private final ArticleTagDao tagDao;
    private final KeywordRuleDao keywordDao;
    private final FilterRuleDao filterDao;
    private final SettingsRepository settings;
    private final NotificationHelper notificationHelper;
    private final NotificationDecisionEngine decisionEngine = new NotificationDecisionEngine();
    private final FilterEngine filterEngine = new FilterEngine();

    public NotificationCoordinator(NotificationDao notificationDao, ArticleDao articleDao,
                                   FeedDao feedDao, CategoryDao categoryDao,
                                   ArticleTagDao tagDao, KeywordRuleDao keywordDao,
                                   FilterRuleDao filterDao, SettingsRepository settings,
                                   NotificationHelper notificationHelper) {
        this.notificationDao = notificationDao;
        this.articleDao = articleDao;
        this.feedDao = feedDao;
        this.categoryDao = categoryDao;
        this.tagDao = tagDao;
        this.keywordDao = keywordDao;
        this.filterDao = filterDao;
        this.settings = settings;
        this.notificationHelper = notificationHelper;
    }

    public int evaluateAndPost(List<Long> articleIds) {
        if (articleIds == null || articleIds.isEmpty()) {
            return 0;
        }
        long now = System.currentTimeMillis();
        boolean androidPermission = notificationHelper.canPostNotifications();
        List<FilterRuleEntity> filterEntities = filterDao.getEnabled();
        List<FilterRule> rules = FilterRuleCodec.decodeList(filterEntities);
        List<KeywordRuleEntity> mutedKeywords = keywordDao.getByType(KeywordType.MUTED);
        String grouping = settings.getNotificationGrouping();
        LocalTime nowTime = LocalTime.now();

        int hourly = notificationDao.countPostedSince(now - 3_600_000L);
        int daily = notificationDao.countPostedSince(now - 86_400_000L);
        int postedCount = 0;
        Set<String> postedHashes = new HashSet<>();

        for (long articleId : articleIds) {
            ArticleEntity article = articleDao.getById(articleId);
            if (article == null) {
                continue;
            }
            FeedEntity feed = article.primaryFeedId == null ? null
                    : feedDao.getById(article.primaryFeedId);
            CategoryEntity category = feed == null || feed.categoryId == null ? null
                    : categoryDao.getById(feed.categoryId);

            FilterContext filterContext = filterContext(article, feed, category);
            FilterEvaluation evaluation = filterEngine.evaluate(rules, filterContext);
            NotificationContext context = new NotificationContext();
            context.globalNotificationsEnabled = settings.notificationsEnabled();
            context.androidPermissionGranted = androidPermission;
            context.feedNotificationsEnabled = feed == null || feed.notificationEnabled;
            context.feedImportance = feed == null
                    ? com.intellirs.reader.data.entity.FeedImportance.NORMAL
                    : feed.feedPriority;
            context.minimumFeedImportance = settings.getNotificationFeedThreshold();
            context.categoryMuted = category != null && category.muteNotifications;
            context.mutedKeywordMatched = matchesMutedKeyword(article, mutedKeywords);
            context.filterMuted = evaluation.muteNotification;
            context.filterForceNotify = evaluation.forceNotify;
            context.relevanceLevel = article.relevanceLevel;
            context.relevanceScore = article.relevanceScore;
            context.minimumRelevance = feed != null && feed.notifyMinRelevance > 0
                    ? Math.max(feed.notifyMinRelevance, settings.getNotificationMinRelevance())
                    : settings.getNotificationMinRelevance();
            context.alreadyNotified = notificationDao.countPostedForArticle(articleId) > 0;
            context.duplicateSuppressed = !context.alreadyNotified
                    && (postedHashes.contains(safeHash(article))
                    || notificationDao.countPostedForHash(safeHash(article), -1L) > 0);
            context.isRead = article.isRead;
            context.quietHoursActive = settings.isWithinQuietHours(nowTime);
            context.allowCriticalDuringQuietHours = settings.allowCriticalDuringQuietHours();
            context.notificationsLastHour = hourly;
            context.notificationsLastDay = daily;
            context.maxNotificationsPerHour = settings.getMaxNotificationsPerHour();
            context.maxNotificationsPerDay = settings.getMaxNotificationsPerDay();

            NotificationDecision decision = decisionEngine.decide(context);
            String groupLabel = groupLabel(grouping, article, feed, category);
            NotificationQueueEntity log = new NotificationQueueEntity();
            log.articleId = articleId;
            log.feedId = feed == null ? -1L : feed.id;
            log.articleTitle = article.title == null ? "" : article.title;
            log.publisher = feed == null ? (article.publisher == null ? "" : article.publisher)
                    : feed.title;
            log.contentHash = safeHash(article);
            log.eligibility = decision.eligible;
            log.decisionReason = decision.reason;
            log.notificationStatus = NotificationStatus.SKIPPED;
            log.groupKey = groupLabel == null ? "" : groupLabel;
            log.channelId = decision.channelId;
            long queueId = notificationDao.insert(log);

            if (decision.eligible) {
                boolean posted = notificationHelper.postArticle(article, feed, groupLabel,
                        decision.channelId);
                if (posted) {
                    notificationDao.updateResult(queueId, NotificationStatus.POSTED, now);
                    postedCount++;
                    hourly++;
                    daily++;
                    postedHashes.add(safeHash(article));
                } else {
                    notificationDao.updateResult(queueId, NotificationStatus.FAILED, 0);
                    Log.w(TAG, "Notification posting failed for article " + articleId);
                }
            } else {
                notificationDao.updateResult(queueId, NotificationStatus.SUPPRESSED, 0);
            }
        }
        notificationHelper.finishGroups();
        return postedCount;
    }

    private FilterContext filterContext(ArticleEntity article, FeedEntity feed,
                                        CategoryEntity category) {
        FilterContext context = new FilterContext();
        context.title = article.title;
        context.description = article.description;
        context.content = article.plainText;
        context.author = article.author;
        context.publisher = feed == null ? article.publisher : feed.title;
        context.category = category == null ? "" : category.name;
        context.feedImportance = feed == null
                ? com.intellirs.reader.data.entity.FeedImportance.NORMAL
                : feed.feedPriority;
        context.relevanceScore = article.relevanceScore;
        context.publishedAt = article.publishedAt;
        context.hasImages = article.hasImages;
        context.hasVideo = article.hasVideo;
        context.hasAudio = article.hasAudio;
        return context;
    }

    private boolean matchesMutedKeyword(ArticleEntity article,
                                        List<KeywordRuleEntity> mutedKeywords) {
        if (mutedKeywords == null || mutedKeywords.isEmpty()) {
            return false;
        }
        String text = HashUtils.normalizeText(article.title + " "
                + article.description + " " + article.plainText);
        for (KeywordRuleEntity keyword : mutedKeywords) {
            String term = HashUtils.normalizeText(keyword.keyword);
            if (!term.isEmpty() && text.contains(term)) {
                return true;
            }
        }
        return false;
    }

    private String groupLabel(String grouping, ArticleEntity article, FeedEntity feed,
                              CategoryEntity category) {
        if ("NONE".equalsIgnoreCase(grouping)) {
            return null;
        }
        if ("TOPIC".equalsIgnoreCase(grouping)) {
            List<String> tags = tagDao.getTags(article.id);
            if (!tags.isEmpty()) {
                return tags.get(0);
            }
            if (category != null && category.name != null) {
                return category.name;
            }
            return "General";
        }
        if (feed != null && feed.title != null && !feed.title.isEmpty()) {
            return feed.title;
        }
        return article.publisher == null ? "News" : article.publisher;
    }

    private static String safeHash(ArticleEntity article) {
        return article.contentHash == null ? "article-" + article.id : article.contentHash;
    }
}
