package com.intellirs.reader.data.sync;

import android.content.Context;
import android.util.Log;

import com.intellirs.reader.data.dao.ArticleDao;
import com.intellirs.reader.data.dao.ArticleFeedDao;
import com.intellirs.reader.data.dao.ArticleFtsDao;
import com.intellirs.reader.data.dao.ArticleTagDao;
import com.intellirs.reader.data.dao.CategoryDao;
import com.intellirs.reader.data.dao.FeedDao;
import com.intellirs.reader.data.dao.FeedHealthDao;
import com.intellirs.reader.data.dao.FilterRuleDao;
import com.intellirs.reader.data.dao.KeywordRuleDao;
import com.intellirs.reader.data.dao.SyncRunDao;
import com.intellirs.reader.data.database.AppDatabase;
import com.intellirs.reader.data.entity.ArticleEntity;
import com.intellirs.reader.data.entity.ArticleFeedCrossRef;
import com.intellirs.reader.data.entity.ArticleRelevanceLevel;
import com.intellirs.reader.data.entity.ArticleTagEntity;
import com.intellirs.reader.data.entity.ArticleType;
import com.intellirs.reader.data.entity.CategoryEntity;
import com.intellirs.reader.data.entity.DownloadType;
import com.intellirs.reader.data.entity.FeedEntity;
import com.intellirs.reader.data.entity.FeedHealthEntity;
import com.intellirs.reader.data.entity.FeedHealthStatus;
import com.intellirs.reader.data.entity.FeedImportance;
import com.intellirs.reader.data.entity.FilterRuleEntity;
import com.intellirs.reader.data.entity.KeywordRuleEntity;
import com.intellirs.reader.data.entity.KeywordType;
import com.intellirs.reader.data.entity.SyncRunEntity;
import com.intellirs.reader.data.model.FeedStats;
import com.intellirs.reader.data.network.FaviconFetcher;
import com.intellirs.reader.data.network.HttpException;
import com.intellirs.reader.data.network.HttpFetcher;
import com.intellirs.reader.data.network.HttpResult;
import com.intellirs.reader.data.parser.ContentExtractor;
import com.intellirs.reader.data.parser.FeedParseException;
import com.intellirs.reader.data.parser.FeedParser;
import com.intellirs.reader.data.parser.ParsedArticle;
import com.intellirs.reader.data.parser.ParsedFeed;
import com.intellirs.reader.data.repository.ArticleRepository;
import com.intellirs.reader.data.repository.DownloadRepository;
import com.intellirs.reader.data.repository.FeedRepository;
import com.intellirs.reader.data.repository.SettingsRepository;
import com.intellirs.reader.domain.duplicates.ArticleFingerprint;
import com.intellirs.reader.domain.duplicates.DuplicateDetector;
import com.intellirs.reader.domain.duplicates.DuplicateMatch;
import com.intellirs.reader.domain.filtering.FilterContext;
import com.intellirs.reader.domain.filtering.FilterEngine;
import com.intellirs.reader.domain.filtering.FilterEvaluation;
import com.intellirs.reader.domain.filtering.FilterRule;
import com.intellirs.reader.domain.filtering.FilterRuleCodec;
import com.intellirs.reader.domain.model.SyncStatus;
import com.intellirs.reader.domain.model.SyncStatusBus;
import com.intellirs.reader.domain.notification.NotificationCoordinator;
import com.intellirs.reader.domain.prioritization.ArticleScoringEngine;
import com.intellirs.reader.domain.prioritization.ScoringConfig;
import com.intellirs.reader.domain.prioritization.ScoringInput;
import com.intellirs.reader.domain.prioritization.ScoringResult;
import com.intellirs.reader.util.DateUtils;
import com.intellirs.reader.util.HashUtils;
import com.intellirs.reader.util.TextUtils;
import com.intellirs.reader.util.UrlUtils;
import com.intellirs.reader.worker.WorkScheduler;

import org.jsoup.Jsoup;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Battery-conscious feed synchronization engine. Network/parser errors are isolated to one
 * feed; articles are persisted transactionally; notifications are evaluated only after
 * persistence by NotificationCoordinator, never by the parser.
 */
public class FeedSyncEngine {
    private static final String TAG = "FeedSyncEngine";
    private static final int MAX_FEED_BYTES = 5 * 1024 * 1024;
    private static final int MAX_ITEMS_PER_SYNC = 600;
    private static final Set<Long> IN_FLIGHT =
            ConcurrentHashMap.newKeySet();

    private final Context context;
    private final AppDatabase database;
    private final FeedDao feedDao;
    private final ArticleDao articleDao;
    private final ArticleFeedDao crossRefDao;
    private final ArticleTagDao tagDao;
    private final CategoryDao categoryDao;
    private final FeedHealthDao healthDao;
    private final KeywordRuleDao keywordDao;
    private final FilterRuleDao filterRuleDao;
    private final SyncRunDao syncRunDao;
    private final SettingsRepository settings;
    private final HttpFetcher httpFetcher;
    private final FeedParser feedParser;
    private final ContentExtractor extractor;
    private final FaviconFetcher faviconFetcher;
    private final ArticleRepository articleRepository;
    private final FeedRepository feedRepository;
    private final DownloadRepository downloadRepository;
    private final WorkScheduler workScheduler;
    private final NotificationCoordinator notificationCoordinator;
    private final SyncStatusBus syncStatusBus;
    private final ArticleScoringEngine scoringEngine = new ArticleScoringEngine();
    private final FilterEngine filterEngine = new FilterEngine();
    private final DuplicateDetector duplicateDetector = new DuplicateDetector();

    public FeedSyncEngine(Context context,
                          AppDatabase database,
                          SettingsRepository settings,
                          HttpFetcher httpFetcher,
                          FeedParser feedParser,
                          ContentExtractor extractor,
                          FaviconFetcher faviconFetcher,
                          ArticleRepository articleRepository,
                          FeedRepository feedRepository,
                          DownloadRepository downloadRepository,
                          WorkScheduler workScheduler,
                          NotificationCoordinator notificationCoordinator,
                          SyncStatusBus syncStatusBus) {
        this.context = context.getApplicationContext();
        this.database = database;
        this.feedDao = database.feedDao();
        this.articleDao = database.articleDao();
        this.crossRefDao = database.articleFeedDao();
        this.tagDao = database.articleTagDao();
        this.categoryDao = database.categoryDao();
        this.healthDao = database.feedHealthDao();
        this.keywordDao = database.keywordRuleDao();
        this.filterRuleDao = database.filterRuleDao();
        this.syncRunDao = database.syncRunDao();
        this.settings = settings;
        this.httpFetcher = httpFetcher;
        this.feedParser = feedParser;
        this.extractor = extractor;
        this.faviconFetcher = faviconFetcher;
        this.articleRepository = articleRepository;
        this.feedRepository = feedRepository;
        this.downloadRepository = downloadRepository;
        this.workScheduler = workScheduler;
        this.notificationCoordinator = notificationCoordinator;
        this.syncStatusBus = syncStatusBus;
    }

    public SyncOutcome sync(boolean force, long onlyFeedId, boolean manual) {
        long startedAt = System.currentTimeMillis();
        SyncRunEntity run = new SyncRunEntity();
        run.startedAt = startedAt;
        run.manual = manual;
        run.status = "RUNNING";
        long runId = syncRunDao.insert(run);
        syncStatusBus.running();
        settings.setLastSyncStatus(context.getString(
                com.intellirs.reader.R.string.sync_status_syncing));

        List<FeedEntity> activeFeeds = new ArrayList<>();
        if (onlyFeedId > 0) {
            FeedEntity feed = feedDao.getById(onlyFeedId);
            if (feed != null && feed.isActive) {
                activeFeeds.add(feed);
            }
        } else {
            activeFeeds.addAll(feedDao.getAllActive());
        }
        List<FeedEntity> due = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (FeedEntity feed : activeFeeds) {
            if (!feed.isActive) {
                continue;
            }
            if (force || feed.lastSyncTime <= 0) {
                due.add(feed);
                continue;
            }
            int interval = feed.syncIntervalMinutes > 0
                    ? feed.syncIntervalMinutes : settings.getSyncIntervalMinutes();
            long dueAt = feed.lastSyncTime + interval * 60_000L;
            if (now >= dueAt) {
                due.add(feed);
            }
        }

        SyncOutcome outcome = new SyncOutcome();
        syncStatusBus.running(0, due.size());
        if (due.isEmpty()) {
            finishRun(runId, run, startedAt, 0, 0, 0, 0, null, false);
            syncStatusBus.complete(0, 0, 0);
            settings.setLastSyncTime(System.currentTimeMillis());
            settings.setLastSyncStatus("No feeds were due");
            outcome.feedsSucceeded = 0;
            outcome.feedsFailed = 0;
            outcome.noFeedsDue = true;
            return outcome;
        }

        ScoringConfig scoringConfig = buildScoringConfig();
        List<FilterRule> filters = FilterRuleCodec.decodeList(filterRuleDao.getEnabled());
        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();
        AtomicInteger newArticles = new AtomicInteger();
        List<Long> notificationCandidates = Collections.synchronizedList(new ArrayList<>());
        List<Long> succeededIds = Collections.synchronizedList(new ArrayList<>());
        List<String> errors = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger transientFailures = new AtomicInteger();

        int poolSize = Math.min(3, due.size());
        ExecutorService executor = Executors.newFixedThreadPool(Math.max(1, poolSize), runnable -> {
            Thread thread = new Thread(runnable, "rss-sync");
            thread.setDaemon(true);
            return thread;
        });
        List<Future<FeedResult>> futures = new ArrayList<>();
        for (FeedEntity feed : due) {
            futures.add(executor.submit(() -> syncOneFeed(feed.id, scoringConfig, filters)));
        }
        int completed = 0;
        for (Future<FeedResult> future : futures) {
            try {
                FeedResult result = future.get();
                if (result != null) {
                    if (result.success) {
                        succeeded.incrementAndGet();
                        succeededIds.add(result.feedId);
                        newArticles.addAndGet(result.newArticles);
                        if (!result.firstSync) {
                            notificationCandidates.addAll(result.newArticleIds);
                        }
                    } else {
                        failed.incrementAndGet();
                        if (result.transientFailure) {
                            transientFailures.incrementAndGet();
                        }
                        if (result.error != null && errors.size() < 8) {
                            errors.add(result.feedTitle + ": " + result.error);
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Sync task failed", e);
                failed.incrementAndGet();
                if (errors.size() < 8) {
                    errors.add(TextUtils.truncate(e.getMessage(), 180));
                }
            } finally {
                completed++;
                syncStatusBus.running(completed, due.size());
            }
        }
        executor.shutdownNow();

        for (Long feedId : successesSnapshot(succeededIds)) {
            try {
                feedRepository.applyAutomaticPrioritization(feedId);
            } catch (RuntimeException e) {
                Log.w(TAG, "Auto prioritisation failed for feed " + feedId, e);
            }
        }

        int posted = 0;
        if (!notificationCandidates.isEmpty()) {
            try {
                posted = notificationCoordinator.evaluateAndPost(
                        new ArrayList<>(notificationCandidates));
            } catch (RuntimeException e) {
                Log.e(TAG, "Notification evaluation failed", e);
            }
        }

        String errorSummary = errors.isEmpty() ? null : TextUtils.truncate(String.join("; ", errors), 1000);
        String status = failed.get() == 0 ? "SUCCESS" : (succeeded.get() == 0 ? "FAILED" : "PARTIAL");
        outcome.feedsSucceeded = succeeded.get();
        outcome.feedsFailed = failed.get();
        outcome.newArticles = newArticles.get();
        outcome.notificationsPosted = posted;
        outcome.transientFailure = succeeded.get() == 0 && transientFailures.get() > 0;
        outcome.firstError = errors.isEmpty() ? null : errors.get(0);
        finishRun(runId, run, startedAt, due.size(), succeeded.get(), failed.get(),
                newArticles.get(), errorSummary, manual);
        long finishedAt = System.currentTimeMillis();
        settings.setLastSyncTime(finishedAt);
        if (failed.get() == 0) {
            syncStatusBus.complete(succeeded.get(), 0, newArticles.get());
            settings.setLastSyncStatus("Synced " + succeeded.get() + " feeds, "
                    + newArticles.get() + " new articles");
        } else {
            String message = "Synced " + succeeded.get() + " feeds; " + failed.get() + " failed";
            syncStatusBus.publish(new SyncStatus(SyncStatus.State.ERROR, message, finishedAt,
                    succeeded.get(), failed.get(), newArticles.get()));
            settings.setLastSyncStatus(message);
        }
        Log.i(TAG, "Sync finished: " + succeeded.get() + " ok, " + failed.get()
                + " failed, " + newArticles.get() + " new, " + posted + " notified");
        return outcome;
    }

    private List<Long> successesSnapshot(List<Long> ids) {
        synchronized (ids) {
            return new ArrayList<>(ids);
        }
    }

    private FeedResult syncOneFeed(long feedId, ScoringConfig scoringConfig,
                                   List<FilterRule> filters) {
        FeedResult result = new FeedResult();
        result.feedId = feedId;
        if (!IN_FLIGHT.add(feedId)) {
            result.success = true;
            return result;
        }
        FeedEntity feed = feedDao.getById(feedId);
        if (feed == null || !feed.isActive) {
            IN_FLIGHT.remove(feedId);
            result.success = false;
            result.error = "Feed is not active";
            result.feedTitle = feed == null ? "Unknown feed" : feed.title;
            return result;
        }
        result.feedTitle = feed.title;
        result.firstSync = feed.lastSuccessfulSync <= 0;
        long requestStart = System.currentTimeMillis();
        try {
            HttpResult response = httpFetcher.fetch(feed.feedUrl, feed.etag,
                    feed.lastModified, MAX_FEED_BYTES, settings.isAllowHttp());
            long now = System.currentTimeMillis();
            if (response.isNotModified()) {
                feedDao.updateNotModified(feed.id, now);
                recordSuccess(feed, now, 304, requestStart);
                result.success = true;
                return result;
            }
            ParsedFeed parsed = feedParser.parse(response.bodyString(),
                    response.finalUrl == null ? feed.feedUrl : response.finalUrl);
            if (parsed.title != null && (feed.title == null || feed.title.trim().isEmpty())) {
                feed.title = parsed.title;
            }
            if (feed.websiteUrl == null || feed.websiteUrl.isEmpty()) {
                feed.websiteUrl = parsed.siteUrl;
            }
            if (feed.description == null || feed.description.isEmpty()) {
                feed.description = parsed.description;
            }
            feedDao.update(feed);

            FeedStats stats = articleDao.getFeedStats(feed.id);
            if (stats == null) {
                stats = new FeedStats();
            }
            stats.readRate = stats.totalArticles == 0 ? 0f
                    : Math.min(1f, stats.readArticles / (float) stats.totalArticles);
            CategoryEntity category = feed.categoryId == null ? null
                    : categoryDao.getById(feed.categoryId);
            String categoryName = category == null ? "" : category.name;
            long firstSeen = now;
            boolean archiveRequested = false;

            int processed = 0;
            for (ParsedArticle item : parsed.articles) {
                if (processed++ >= MAX_ITEMS_PER_SYNC) {
                    break;
                }
                ArticleChange change = processItem(feed, item, categoryName, scoringConfig,
                        filters, stats, firstSeen);
                if (change.newlyInserted) {
                    result.newArticles++;
                    result.newArticleIds.add(change.articleId);
                }
                archiveRequested |= change.archiveRequested;
            }

            feedDao.updateSyncSuccess(feed.id, now, response.statusCode,
                    response.etag, response.lastModified);
            feed.lastSyncTime = now;
            feed.lastSuccessfulSync = now;
            feed.failureCount = 0;
            feed.lastHttpStatus = response.statusCode;
            feed.etag = response.etag;
            feed.lastModified = response.lastModified;
            recordSuccess(feed, now, response.statusCode, requestStart);
            if (feed.faviconPath == null) {
                try {
                    faviconFetcher.fetchAndStore(feed);
                } catch (RuntimeException e) {
                    Log.w(TAG, "Favicon fetch failed for " + feed.title, e);
                }
            }
            if (archiveRequested && feed.isActive) {
                workScheduler.enqueueFeedArchive(feed.id);
            }
            result.success = true;
            return result;
        } catch (HttpException e) {
            result.success = false;
            result.transientFailure = e.transientFailure;
            result.error = e.safeMessage;
            recordFailure(feed, e.safeMessage, e.statusCode);
            return result;
        } catch (FeedParseException e) {
            result.success = false;
            result.error = "Feed parsing failed: " + e.getMessage();
            recordFailure(feed, result.error, 200);
            return result;
        } catch (RuntimeException e) {
            Log.e(TAG, "Unexpected sync failure for " + feed.feedUrl, e);
            result.success = false;
            result.transientFailure = true;
            result.error = "Unexpected error: " + TextUtils.truncate(e.getMessage(), 180);
            recordFailure(feed, result.error, -1);
            return result;
        } finally {
            IN_FLIGHT.remove(feedId);
        }
    }

    /**
     * Synchronized because the app may process feeds concurrently. Lookup + insert + cross-ref
     * must be atomic relative to other feeds so a shared canonical article cannot race the
     * unique URL constraint.
     */
    private synchronized ArticleChange processItem(FeedEntity feed, ParsedArticle item,
                                                   String categoryName,
                                                   ScoringConfig scoringConfig,
                                                   List<FilterRule> filters,
                                                   FeedStats stats, long firstSeen) {
        ArticleChange change = new ArticleChange();
        long now = System.currentTimeMillis();
        String title = TextUtils.stripHtmlOrEmpty(item.title);
        if (title.trim().isEmpty()) {
            title = "Untitled article";
        }
        String baseUrl = item.link != null ? item.link
                : feed.websiteUrl != null ? feed.websiteUrl : feed.feedUrl;
        String descriptionHtml = extractor.sanitizeFragment(
                limit(item.descriptionHtml == null ? item.contentHtml : item.descriptionHtml,
                        2_000_000), baseUrl);
        String contentHtml = extractor.sanitizeFragment(
                limit(item.contentHtml == null ? item.descriptionHtml : item.contentHtml,
                        2_000_000), baseUrl);
        String plainDescription = Jsoup.parse(descriptionHtml).text().replaceAll("\\s+", " ").trim();
        String plainContent = Jsoup.parse(contentHtml).text().replaceAll("\\s+", " ").trim();
        if (plainDescription.isEmpty()) {
            plainDescription = plainContent;
        }
        String searchText = plainContent.isEmpty() ? plainDescription : plainContent;
        if (searchText.length() > 40_000) {
            searchText = searchText.substring(0, 40_000);
        }
        if (descriptionHtml.length() > 250_000) {
            descriptionHtml = descriptionHtml.substring(0, 250_000);
        }
        String normalizedTitle = HashUtils.normalizedTitle(title);
        String contentHash = HashUtils.normalizedContentHash(title,
                plainDescription.isEmpty() ? plainContent : plainDescription);
        String originalUrl = UrlUtils.isValidHttpUrl(item.link) ? item.link : null;
        String canonical = originalUrl == null
                ? "urn:intellirss:" + HashUtils.sha256(feed.id + "|" + item.guid + "|" + title)
                : UrlUtils.canonicalizeArticleUrl(originalUrl);
        if (canonical.isEmpty()) {
            canonical = "urn:intellirss:" + HashUtils.sha256(feed.id + "|" + title);
        }
        long publishedAt = item.publishedAt > 0 ? item.publishedAt : now;

        ArticleEntity existing = null;
        if (item.guid != null && !item.guid.trim().isEmpty()) {
            Long guidArticleId = crossRefDao.findArticleIdByGuid(feed.id, item.guid);
            if (guidArticleId != null) {
                existing = articleDao.getById(guidArticleId);
            }
        }
        if (existing == null) {
            existing = articleDao.findByCanonicalUrl(canonical);
        }
        if (existing == null && settings.mergeDuplicates()) {
            ArticleFingerprint incoming = new ArticleFingerprint(canonical, item.guid,
                    normalizedTitle, contentHash, feed.title);
            existing = articleDao.findByContentHash(contentHash);
            if (existing != null) {
                DuplicateMatch match = duplicateDetector.match(incoming,
                        fingerprint(existing));
                if (!match.match) {
                    existing = null;
                }
            }
            if (existing == null && normalizedTitle != null && !normalizedTitle.isEmpty()) {
                List<ArticleEntity> titleCandidates = articleDao.findByNormalizedTitle(normalizedTitle);
                for (ArticleEntity candidate : titleCandidates) {
                    DuplicateMatch match = duplicateDetector.match(incoming, fingerprint(candidate));
                    if (match.match) {
                        existing = candidate;
                        break;
                    }
                }
            }
        }

        FilterOutcome filterOutcome = scoreAndFilter(item, title, plainDescription,
                plainContent, feed, categoryName, stats, publishedAt, scoringConfig, filters);
        if (existing != null) {
            long articleId = existing.id;
            boolean contentChanged = existing.contentHash == null
                    || !existing.contentHash.equals(contentHash);
            final String finalDescriptionHtml = descriptionHtml;
            final String finalThumbnail = validUrl(item.thumbnailUrl);
            final String finalAudioUrl = validUrl(item.audioUrl);
            final String finalVideoUrl = validUrl(item.videoUrl);
            final String finalSearchText = searchText;
            database.runInTransaction(() -> {
                insertCrossRef(articleId, feed, item, publishedAt);
                articleDao.touchLastSeen(articleId, now);
                if (contentChanged) {
                    articleDao.updateFromFeed(articleId, finalDescriptionHtml, contentHash,
                            normalizedTitle, now, finalThumbnail, item.hasImages, item.hasVideo,
                            item.hasAudio, finalAudioUrl, finalVideoUrl, item.articleType);
                    articleDao.updatePlainText(articleId, finalSearchText);
                    articleDao.updateRelevance(articleId, filterOutcome.finalScore,
                            filterOutcome.level, filterOutcome.reason);
                }
                applyTags(articleId, filterOutcome.evaluation.tags);
                if (filterOutcome.evaluation.markRead) {
                    articleDao.setRead(articleId, true);
                }
            });
            articleRepository.rebuildFts(articleId);
            if (contentChanged && existing.isArchived) {
                downloadRepository.enqueueAutoArchive(articleId, feed.id, DownloadType.ARTICLE);
                change.archiveRequested = true;
            }
            if (filterOutcome.evaluation.archive && !existing.isArchived) {
                downloadRepository.enqueueAutoArchive(articleId, feed.id, DownloadType.ARTICLE);
                change.archiveRequested = true;
            }
            return change;
        }

        ArticleEntity article = new ArticleEntity();
        article.originalUrl = originalUrl;
        article.canonicalUrl = canonical;
        article.guid = item.guid == null ? "" : item.guid;
        article.title = title;
        article.description = descriptionHtml;
        article.author = item.author;
        article.publisher = feed.title;
        article.publishedAt = publishedAt;
        article.firstSeenAt = firstSeen;
        article.updatedAt = now;
        article.lastSeenAt = now;
        article.contentHash = contentHash;
        article.normalizedTitle = normalizedTitle;
        article.thumbnailUrl = validUrl(item.thumbnailUrl);
        article.articleType = item.articleType == null ? ArticleType.ARTICLE : item.articleType;
        article.relevanceScore = filterOutcome.finalScore;
        article.relevanceLevel = filterOutcome.level;
        article.relevanceReason = filterOutcome.reason;
        article.isRead = filterOutcome.evaluation.markRead;
        article.hasImages = item.hasImages;
        article.hasVideo = item.hasVideo;
        article.hasAudio = item.hasAudio;
        article.audioUrl = validUrl(item.audioUrl);
        article.audioDurationMs = item.audioDurationMs;
        article.videoUrl = validUrl(item.videoUrl);
        article.plainText = searchText;
        article.primaryFeedId = feed.id;

        long[] idHolder = new long[]{-1L};
        database.runInTransaction(() -> {
            idHolder[0] = articleDao.insert(article);
            insertCrossRef(idHolder[0], feed, item, publishedAt);
            applyTags(idHolder[0], filterOutcome.evaluation.tags);
        });
        articleRepository.rebuildFts(idHolder[0]);
        change.newlyInserted = true;
        change.articleId = idHolder[0];

        boolean shouldArchive = filterOutcome.evaluation.archive
                || feed.archiveMode == com.intellirs.reader.data.entity.ArchiveMode.AUTOMATIC
                || (feed.archiveMode == com.intellirs.reader.data.entity.ArchiveMode.PRIORITY
                && (feed.feedPriority == FeedImportance.CRITICAL
                || feed.feedPriority == FeedImportance.HIGH));
        if (shouldArchive) {
            downloadRepository.enqueueAutoArchive(idHolder[0], feed.id, DownloadType.ARTICLE);
            change.archiveRequested = true;
        }
        if (item.hasAudio && settings.downloadPodcast()) {
            downloadRepository.enqueueAutoArchive(idHolder[0], feed.id, DownloadType.AUDIO);
            change.archiveRequested = true;
        }
        return change;
    }

    private void insertCrossRef(long articleId, FeedEntity feed, ParsedArticle item,
                                long publishedAt) {
        ArticleFeedCrossRef ref = new ArticleFeedCrossRef();
        ref.articleId = articleId;
        ref.feedId = feed.id;
        ref.originalGuid = item.guid == null ? "" : item.guid;
        ref.sourceTitle = feed.title;
        ref.sourcePublisher = feed.title;
        ref.sourceUrl = item.link;
        ref.sourcePublishedAt = publishedAt;
        ref.addedAt = System.currentTimeMillis();
        crossRefDao.insert(ref);
        crossRefDao.updateSource(articleId, feed.id, feed.title, feed.title,
                item.link, publishedAt, item.guid == null ? "" : item.guid);
    }

    private void applyTags(long articleId, java.util.Set<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return;
        }
        for (String tag : tags) {
            ArticleTagEntity entity = new ArticleTagEntity();
            entity.articleId = articleId;
            entity.tag = tag;
            tagDao.add(entity);
        }
    }

    private FilterOutcome scoreAndFilter(ParsedArticle item, String title,
                                         String description, String content, FeedEntity feed,
                                         String categoryName, FeedStats stats, long publishedAt,
                                         ScoringConfig scoringConfig, List<FilterRule> filters) {
        ScoringInput input = new ScoringInput();
        input.title = title;
        input.description = description;
        input.content = content;
        input.author = item.author;
        input.publisher = feed.title;
        input.category = categoryName;
        input.publishedAt = publishedAt;
        input.now = System.currentTimeMillis();
        input.feedReadRate = stats == null ? 0f : stats.readRate;
        input.hasMedia = item.hasImages || item.hasVideo || item.hasAudio;
        ScoringResult scoring = scoringEngine.score(input, scoringConfig);

        FilterContext context = new FilterContext();
        context.title = title;
        context.description = description;
        context.content = content;
        context.author = item.author == null ? "" : item.author;
        context.publisher = feed.title;
        context.category = categoryName == null ? "" : categoryName;
        context.feedImportance = feed.feedPriority;
        context.relevanceScore = scoring.score;
        context.publishedAt = publishedAt;
        context.hasImages = item.hasImages;
        context.hasVideo = item.hasVideo;
        context.hasAudio = item.hasAudio;
        FilterEvaluation evaluation = filterEngine.evaluate(filters, context);
        int finalScore = evaluation.adjustedScore(scoring.score);
        ArticleRelevanceLevel level = ScoringResult.levelFor(finalScore, scoringConfig);
        List<String> reasons = new ArrayList<>(scoring.reasons);
        reasons.addAll(evaluation.reasons);
        reasons.add("feed importance is not an input to article relevance");
        FilterOutcome outcome = new FilterOutcome();
        outcome.evaluation = evaluation;
        outcome.finalScore = finalScore;
        outcome.level = level;
        outcome.reason = TextUtils.truncate(String.join("; ", reasons), 2000);
        return outcome;
    }

    private ScoringConfig buildScoringConfig() {
        ScoringConfig config = new ScoringConfig();
        config.criticalThreshold = settings.getCriticalThreshold();
        config.highThreshold = settings.getHighThreshold();
        config.normalThreshold = settings.getNormalThreshold();
        config.lowThreshold = settings.getLowThreshold();
        addKeywords(config.priorityKeywords, KeywordType.PRIORITY);
        addKeywords(config.mutedKeywords, KeywordType.MUTED);
        addKeywords(config.topics, KeywordType.TOPIC);
        String authors = settings.getPreferredAuthors();
        if (authors != null) {
            for (String author : authors.split(",")) {
                if (!author.trim().isEmpty()) {
                    config.preferredAuthors.add(author.trim());
                }
            }
        }
        return config;
    }

    private void addKeywords(List<ScoringConfig.ScoringKeyword> target, KeywordType type) {
        List<KeywordRuleEntity> rules = keywordDao.getByType(type);
        for (KeywordRuleEntity rule : rules) {
            target.add(new ScoringConfig.ScoringKeyword(rule.keyword, rule.weight));
        }
    }

    private void recordSuccess(FeedEntity feed, long now, int httpStatus, long requestStart) {
        FeedHealthEntity health = healthDao.getForFeed(feed.id);
        if (health == null) {
            health = new FeedHealthEntity();
            health.feedId = feed.id;
        }
        long elapsed = Math.max(0, now - requestStart);
        health.avgResponseMs = health.avgResponseMs <= 0
                ? elapsed : (health.avgResponseMs * 3 + elapsed) / 4;
        health.status = FeedHealthStatus.HEALTHY;
        health.failureCount = 0;
        health.lastError = null;
        health.lastCheckedAt = now;
        health.lastSuccessAt = now;
        health.lastHttpStatus = httpStatus;
        healthDao.save(health);
    }

    private void recordFailure(FeedEntity feed, String error, int httpStatus) {
        long now = System.currentTimeMillis();
        int failures = feed.failureCount + 1;
        feedDao.updateSyncFailure(feed.id, now, failures, httpStatus);
        FeedHealthEntity health = healthDao.getForFeed(feed.id);
        if (health == null) {
            health = new FeedHealthEntity();
            health.feedId = feed.id;
        }
        health.failureCount = failures;
        health.lastError = TextUtils.truncate(error, 500);
        health.lastCheckedAt = now;
        health.lastHttpStatus = httpStatus;
        health.status = failures >= 3 ? FeedHealthStatus.FAILING
                : failures >= 1 ? FeedHealthStatus.DEGRADED : FeedHealthStatus.HEALTHY;
        healthDao.save(health);
    }

    private void finishRun(long runId, SyncRunEntity run, long startedAt, int attempted,
                           int succeeded, int failed, int newArticles, String error,
                           boolean manual) {
        run.id = runId;
        run.finishedAt = System.currentTimeMillis();
        run.feedsAttempted = attempted;
        run.feedsSucceeded = succeeded;
        run.feedsFailed = failed;
        run.newArticles = newArticles;
        run.status = failed == 0 ? "SUCCESS" : succeeded == 0 ? "FAILED" : "PARTIAL";
        run.errorSummary = error;
        run.manual = manual;
        syncRunDao.update(run);
        syncRunDao.prune(System.currentTimeMillis() - 90L * 86_400_000L);
    }

    private static String validUrl(String value) {
        return UrlUtils.isValidHttpUrl(value) ? value : null;
    }

    private static String limit(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static ArticleFingerprint fingerprint(ArticleEntity article) {
        return new ArticleFingerprint(article.canonicalUrl, article.guid,
                article.normalizedTitle, article.contentHash, article.publisher);
    }

    public static class SyncOutcome {
        public int feedsSucceeded;
        public int feedsFailed;
        public int newArticles;
        public int notificationsPosted;
        public boolean transientFailure;
        public boolean noFeedsDue;
        public String firstError;
    }

    private static class FeedResult {
        long feedId;
        String feedTitle = "";
        boolean success;
        boolean transientFailure;
        boolean firstSync;
        int newArticles;
        final List<Long> newArticleIds = new ArrayList<>();
        String error;
    }

    private static class ArticleChange {
        boolean newlyInserted;
        long articleId = -1L;
        boolean archiveRequested;
    }

    private static class FilterOutcome {
        FilterEvaluation evaluation;
        int finalScore;
        ArticleRelevanceLevel level;
        String reason;
    }
}
