package com.intellirs.reader.data.repository;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;

import com.intellirs.reader.data.dao.ArticleDao;
import com.intellirs.reader.data.dao.CategoryDao;
import com.intellirs.reader.data.dao.FeedDao;
import com.intellirs.reader.data.dao.FeedHealthDao;
import com.intellirs.reader.data.database.AppDatabase;
import com.intellirs.reader.data.entity.ArchiveMode;
import com.intellirs.reader.data.entity.CategoryEntity;
import com.intellirs.reader.data.entity.FeedEntity;
import com.intellirs.reader.data.entity.FeedHealthEntity;
import com.intellirs.reader.data.entity.FeedHealthStatus;
import com.intellirs.reader.data.entity.FeedImportance;
import com.intellirs.reader.data.entity.ImportanceSource;
import com.intellirs.reader.data.model.FeedListItem;
import com.intellirs.reader.data.model.FeedStats;
import com.intellirs.reader.data.network.FaviconFetcher;
import com.intellirs.reader.data.network.FeedCandidate;
import com.intellirs.reader.domain.prioritization.FeedPrioritySuggestion;
import com.intellirs.reader.domain.prioritization.FeedPrioritizationEngine;
import com.intellirs.reader.util.UrlUtils;
import com.intellirs.reader.worker.WorkScheduler;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

/** Subscription, category and feed-level preference repository. */
public class FeedRepository {
    private final AppDatabase database;
    private final FeedDao feedDao;
    private final CategoryDao categoryDao;
    private final ArticleDao articleDao;
    private final FeedHealthDao healthDao;
    private final SettingsRepository settings;
    private final WorkScheduler workScheduler;
    private final FaviconFetcher faviconFetcher;
    private final ExecutorService ioExecutor;
    private final FeedPrioritizationEngine prioritizationEngine = new FeedPrioritizationEngine();

    public FeedRepository(AppDatabase database,
                          SettingsRepository settings,
                          WorkScheduler workScheduler,
                          FaviconFetcher faviconFetcher,
                          ExecutorService ioExecutor) {
        this.database = database;
        this.feedDao = database.feedDao();
        this.categoryDao = database.categoryDao();
        this.articleDao = database.articleDao();
        this.healthDao = database.feedHealthDao();
        this.settings = settings;
        this.workScheduler = workScheduler;
        this.faviconFetcher = faviconFetcher;
        this.ioExecutor = ioExecutor;
    }

    public LiveData<List<FeedListItem>> observeFeedList() {
        return feedDao.observeFeedList();
    }

    public LiveData<List<CategoryEntity>> observeCategories() {
        return categoryDao.observeAll();
    }

    public List<FeedEntity> getActiveFeeds() {
        return feedDao.getAllActive();
    }

    public List<CategoryEntity> getCategories() {
        return categoryDao.getAll();
    }

    @Nullable
    public FeedEntity getFeed(long id) {
        return feedDao.getById(id);
    }

    @Nullable
    public FeedEntity getFeedByUrl(String feedUrl) {
        return feedDao.getAnyByUrl(UrlUtils.normalizeFeedUrl(feedUrl));
    }

    @Nullable
    public FeedListItem getFeedListItem(long id) {
        return feedDao.getFeedListItem(id);
    }

    public LiveData<FeedEntity> observeFeed(long id) {
        return feedDao.observeById(id);
    }

    @Nullable
    public FeedHealthEntity getHealth(long feedId) {
        return healthDao.getForFeed(feedId);
    }

    public FeedStats getFeedStats(long feedId) {
        FeedStats stats = articleDao.getFeedStats(feedId);
        if (stats == null) {
            stats = new FeedStats();
        }
        stats.readRate = stats.totalArticles == 0
                ? 0f : Math.min(1f, stats.readArticles / (float) stats.totalArticles);
        return stats;
    }

    /**
     * Adds a subscription. Existing inactive subscriptions are reactivated without
     * overwriting archived content. Existing active feeds are returned untouched.
     */
    public FeedEntity subscribe(FeedCandidate candidate, @Nullable Long categoryId)
            throws SubscriptionException {
        return subscribe(candidate, categoryId, true, true);
    }

    /**
     * @param scheduleFeedSync start a one-time feed sync; false for bulk OPML import where a
     *                         single global sync is more efficient.
     * @param fetchFavicon     fetch the publisher icon immediately; false defers it to sync.
     */
    public FeedEntity subscribe(FeedCandidate candidate, @Nullable Long categoryId,
                                boolean scheduleFeedSync, boolean fetchFavicon)
            throws SubscriptionException {
        if (candidate == null || candidate.feedUrl == null) {
            throw new SubscriptionException("No feed URL was provided");
        }
        String normalized;
        try {
            normalized = UrlUtils.normalizeUserInput(candidate.feedUrl, settings.isAllowHttp());
        } catch (IllegalArgumentException e) {
            throw new SubscriptionException(e.getMessage(), e);
        }
        FeedEntity existing = feedDao.getAnyByUrl(UrlUtils.normalizeFeedUrl(normalized));
        long feedId;
        if (existing != null) {
            if (existing.isActive) {
                return existing;
            }
            existing.isActive = true;
            existing.notificationEnabled = true;
            existing.title = preferredTitle(candidate.title, existing.title, normalized);
            existing.websiteUrl = candidate.websiteUrl != null
                    ? candidate.websiteUrl : existing.websiteUrl;
            existing.description = candidate.description != null
                    ? candidate.description : existing.description;
            existing.feedType = candidate.feedType == null ? existing.feedType : candidate.feedType;
            if (categoryId != null) {
                existing.categoryId = categoryId;
            }
            existing.failureCount = 0;
            feedDao.update(existing);
            feedId = existing.id;
        } else {
            FeedEntity feed = new FeedEntity();
            feed.title = preferredTitle(candidate.title, null, normalized);
            feed.feedUrl = normalized;
            feed.websiteUrl = candidate.websiteUrl;
            feed.description = candidate.description;
            feed.feedType = candidate.feedType == null ? "RSS" : candidate.feedType;
            feed.categoryId = categoryId;
            feed.feedPriority = FeedImportance.NORMAL;
            feed.importanceSource = ImportanceSource.AUTO;
            feed.notificationEnabled = true;
            feed.archiveMode = settings.getDefaultArchiveMode();
            feed.syncIntervalMinutes = 0;
            feed.isActive = true;
            feed.sortOrder = nextSortOrder();
            feed.createdAt = System.currentTimeMillis();
            feedId = feedDao.insert(feed);
            feed.id = feedId;
        }
        ensureHealth(feedId);
        FeedEntity stored = feedDao.getById(feedId);
        if (fetchFavicon) {
            ioExecutor.execute(() -> faviconFetcher.fetchAndStore(stored));
        }
        if (scheduleFeedSync) {
            workScheduler.enqueueFeedSync(feedId, true);
        }
        workScheduler.ensurePeriodicWorkAsync();
        return stored;
    }

    private int nextSortOrder() {
        Integer max = feedDao.maxSortOrder();
        return (max == null ? 0 : max) + 1;
    }

    private void ensureHealth(long feedId) {
        FeedHealthEntity health = healthDao.getForFeed(feedId);
        if (health == null) {
            health = new FeedHealthEntity();
            health.feedId = feedId;
            health.status = FeedHealthStatus.HEALTHY;
            healthDao.save(health);
        }
    }

    public FeedHealthStatus healthStatusFor(long feedId) {
        FeedHealthEntity health = healthDao.getForFeed(feedId);
        return health == null ? FeedHealthStatus.HEALTHY : health.status;
    }

    public void updateFeed(FeedEntity feed) throws SubscriptionException {
        if (feed == null) {
            throw new SubscriptionException("Feed no longer exists");
        }
        FeedEntity stored = feedDao.getById(feed.id);
        if (stored == null) {
            throw new SubscriptionException("Feed no longer exists");
        }
        stored.title = feed.title == null || feed.title.trim().isEmpty()
                ? UrlUtils.host(stored.feedUrl) : feed.title.trim();
        stored.categoryId = feed.categoryId;
        stored.feedPriority = feed.feedPriority;
        stored.importanceSource = feed.importanceSource;
        stored.notificationEnabled = feed.notificationEnabled;
        stored.archiveMode = feed.archiveMode;
        stored.syncIntervalMinutes = feed.syncIntervalMinutes;
        stored.notifyMinRelevance = feed.notifyMinRelevance;
        stored.description = feed.description;
        stored.websiteUrl = feed.websiteUrl;
        feedDao.update(stored);
        workScheduler.ensurePeriodicWorkAsync();
    }

    public void setImportance(long feedId, FeedImportance importance, ImportanceSource source) {
        FeedEntity feed = feedDao.getById(feedId);
        if (feed == null) {
            return;
        }
        feed.feedPriority = importance;
        feed.importanceSource = source;
        feedDao.update(feed);
    }

    public void setNotificationsEnabled(long feedId, boolean enabled) {
        feedDao.setNotificationsEnabled(feedId, enabled);
    }

    public void setSyncInterval(long feedId, int minutes) {
        FeedEntity feed = feedDao.getById(feedId);
        if (feed == null) {
            return;
        }
        feed.syncIntervalMinutes = Math.max(0, minutes);
        feedDao.update(feed);
        workScheduler.ensurePeriodicWorkAsync();
    }

    public void setArchiveMode(long feedId, ArchiveMode mode) {
        FeedEntity feed = feedDao.getById(feedId);
        if (feed == null) {
            return;
        }
        feed.archiveMode = mode;
        feedDao.update(feed);
    }

    /** Unsubscribe is intentionally non-destructive: the feed row is retained for source attribution. */
    public void unsubscribe(long feedId) {
        feedDao.setActive(feedId, false);
        workScheduler.ensurePeriodicWorkAsync();
    }

    public void markFeedRead(long feedId) {
        articleDao.markFeedRead(feedId);
    }

    public void refreshFeed(long feedId) {
        workScheduler.enqueueFeedSync(feedId, true);
    }

    public FeedPrioritySuggestion suggestImportance(long feedId) {
        FeedStats stats = getFeedStats(feedId);
        FeedEntity feed = feedDao.getById(feedId);
        int failures = feed == null ? 0 : feed.failureCount;
        long lastSuccess = feed == null ? 0 : feed.lastSuccessfulSync;
        return prioritizationEngine.suggest(stats, failures, lastSuccess);
    }

    public void applySuggestedImportance(long feedId) {
        FeedPrioritySuggestion suggestion = suggestImportance(feedId);
        setImportance(feedId, suggestion.importance, ImportanceSource.USER);
        workScheduler.ensurePeriodicWorkAsync();
    }

    /** Called after a successful sync to keep AUTO feeds ordered by engagement. */
    public void applyAutomaticPrioritization(long feedId) {
        if (!settings.autoFeedPrioritization()) {
            return;
        }
        FeedEntity feed = feedDao.getById(feedId);
        if (feed == null || feed.importanceSource != ImportanceSource.AUTO || !feed.isActive) {
            return;
        }
        FeedStats stats = getFeedStats(feedId);
        if (stats.totalArticles < 5) {
            return;
        }
        FeedPrioritySuggestion suggestion = prioritizationEngine.suggest(
                stats, feed.failureCount, feed.lastSuccessfulSync);
        if (suggestion.importance != feed.feedPriority) {
            feed.feedPriority = suggestion.importance;
            feedDao.update(feed);
        }
    }

    public void moveFeed(long feedId, boolean up) {
        List<FeedEntity> feeds = feedDao.getAllActive();
        List<FeedEntity> ordered = new ArrayList<>(feeds);
        ordered.sort((left, right) -> {
            int compare = Integer.compare(left.sortOrder, right.sortOrder);
            return compare != 0 ? compare : left.title.compareToIgnoreCase(right.title);
        });
        int index = -1;
        for (int i = 0; i < ordered.size(); i++) {
            if (ordered.get(i).id == feedId) {
                index = i;
                break;
            }
        }
        if (index < 0) {
            return;
        }
        int target = up ? index - 1 : index + 1;
        if (target < 0 || target >= ordered.size()) {
            return;
        }
        final int fromIndex = index;
        final int toIndex = target;
        database.runInTransaction(() -> {
            // Normalize all orders first so ties do not make dragging unpredictable.
            for (int i = 0; i < ordered.size(); i++) {
                ordered.get(i).sortOrder = i * 10;
            }
            FeedEntity moving = ordered.get(fromIndex);
            FeedEntity other = ordered.get(toIndex);
            int movingOrder = moving.sortOrder;
            moving.sortOrder = other.sortOrder;
            other.sortOrder = movingOrder;
            for (FeedEntity feed : ordered) {
                feedDao.updateSortOrder(feed.id, feed.sortOrder);
            }
        });
    }

    // ---- Categories ----------------------------------------------------------------

    public CategoryEntity createCategory(String rawName, @Nullable Long parentId) {
        String name = rawName == null ? "" : rawName.trim().replaceAll("\\s+", " ");
        if (name.isEmpty()) {
            return null;
        }
        if (name.length() > 80) {
            name = name.substring(0, 80);
        }
        CategoryEntity existing = categoryDao.findByName(name, parentId);
        if (existing != null) {
            return existing;
        }
        CategoryEntity category = new CategoryEntity();
        category.name = name;
        category.parentId = parentId;
        category.sortOrder = categoryDao.getAll().size();
        category.id = categoryDao.insert(category);
        return category;
    }

    /** Creates nested categories for an OPML path such as "News/World". */
    @Nullable
    public CategoryEntity ensureCategoryPath(@Nullable String path) {
        if (path == null || path.trim().isEmpty()) {
            return null;
        }
        Long parentId = null;
        CategoryEntity current = null;
        for (String part : path.split("/")) {
            String name = part.trim();
            if (name.isEmpty()) {
                continue;
            }
            current = categoryDao.findByName(name, parentId);
            if (current == null) {
                current = createCategory(name, parentId);
            }
            if (current == null) {
                return null;
            }
            parentId = current.id;
        }
        return current;
    }

    public void renameCategory(long categoryId, String newName) {
        CategoryEntity category = categoryDao.getById(categoryId);
        if (category == null || newName == null || newName.trim().isEmpty()) {
            return;
        }
        category.name = newName.trim();
        categoryDao.update(category);
    }

    public void deleteCategory(long categoryId) {
        CategoryEntity category = categoryDao.getById(categoryId);
        if (category == null) {
            return;
        }
        database.runInTransaction(() -> {
            feedDao.clearCategory(categoryId);
            categoryDao.clearParent(categoryId);
            categoryDao.delete(category);
        });
    }

    public void setCategoryMuted(long categoryId, boolean muted) {
        categoryDao.setMuted(categoryId, muted);
    }

    @Nullable
    public CategoryEntity getCategory(long id) {
        return categoryDao.getById(id);
    }

    public FeedHealthEntity healthOrCreate(long feedId) {
        FeedHealthEntity health = healthDao.getForFeed(feedId);
        if (health == null) {
            ensureHealth(feedId);
            health = healthDao.getForFeed(feedId);
        }
        return health;
    }

    private static String preferredTitle(@Nullable String title, @Nullable String existing,
                                         String feedUrl) {
        String candidate = title;
        if (candidate == null || candidate.trim().isEmpty()) {
            candidate = existing;
        }
        if (candidate == null || candidate.trim().isEmpty()) {
            candidate = UrlUtils.host(feedUrl);
        }
        return candidate.trim();
    }

    public static class SubscriptionException extends Exception {
        public SubscriptionException(String message) {
            super(message);
        }

        public SubscriptionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
