package com.intellirs.reader.worker;

import android.content.Context;

import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.intellirs.reader.data.dao.FeedDao;
import com.intellirs.reader.data.repository.SettingsRepository;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Central WorkManager scheduling. Periodic sync is unique and battery/network constrained;
 * WorkManager persists it across reboots. No permanent foreground service is used for
 * ordinary polling.
 */
public class WorkScheduler {
    public static final String PERIODIC_SYNC_NAME = "periodic_feed_sync";
    public static final String DAILY_SYNC_NAME = "daily_feed_sync";
    public static final String MANUAL_SYNC_NAME = "manual_feed_sync";
    public static final String STARTUP_SYNC_NAME = "startup_feed_sync";
    public static final String ARCHIVE_CLEANUP_NAME = "periodic_archive_cleanup";
    public static final String TAG_FEED_SYNC = "feed_sync";
    public static final int MIN_INTERVAL_MINUTES = 15;
    /** No configured or per-feed interval may leave a feed unchecked for more than a day. */
    public static final int MAX_INTERVAL_MINUTES = 24 * 60;
    public static final long DAILY_INTERVAL_MILLIS = TimeUnit.DAYS.toMillis(1);

    private final Context context;
    private final SettingsRepository settings;
    private final FeedDao feedDao;
    private final ExecutorService schedulerExecutor =
            Executors.newSingleThreadExecutor(r -> {
                Thread thread = new Thread(r, "work-scheduler");
                thread.setDaemon(true);
                return thread;
            });

    public WorkScheduler(Context context, SettingsRepository settings, FeedDao feedDao) {
        this.context = context.getApplicationContext();
        this.settings = settings;
        this.feedDao = feedDao;
    }

    public void ensurePeriodicWorkAsync() {
        schedulerExecutor.execute(this::ensurePeriodicWork);
    }

    /** Safe to call from a background thread. */
    public void ensurePeriodicWork() {
        int configured = settings.getSyncIntervalMinutes();
        int min = configured;
        Integer fromFeeds = feedDao.minEffectiveSyncInterval(configured);
        if (fromFeeds != null) {
            min = Math.min(min, fromFeeds);
        }
        int interval = Math.min(MAX_INTERVAL_MINUTES,
                Math.max(MIN_INTERVAL_MINUTES, min));
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                FeedSyncWorker.class, interval, TimeUnit.MINUTES)
                .setConstraints(automaticSyncConstraints())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .addTag(TAG_FEED_SYNC)
                .build();
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_SYNC_NAME, ExistingPeriodicWorkPolicy.UPDATE, request);
    }

    /**
     * Schedules a once-a-day full sync. It is independent of the configured interval and of
     * per-feed intervals, so every active feed is fetched at least once per day whenever
     * Android lets the worker run. Safe to call from a background thread; keeps any already
     * scheduled daily run.
     */
    public void ensureDailySync() {
        enqueueDailySync(ExistingPeriodicWorkPolicy.KEEP);
    }

    /** Applies changed Wi-Fi/battery preferences to the daily fallback worker. */
    public void refreshDailySyncAsync() {
        schedulerExecutor.execute(() ->
                enqueueDailySync(ExistingPeriodicWorkPolicy.UPDATE));
    }

    private void enqueueDailySync(ExistingPeriodicWorkPolicy policy) {
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                FeedSyncWorker.class, 24, TimeUnit.HOURS)
                .setInitialDelay(24, TimeUnit.HOURS)
                .setConstraints(automaticSyncConstraints())
                .setInputData(new androidx.work.Data.Builder()
                        .putBoolean(FeedSyncWorker.KEY_FORCE, true)
                        .putLong(FeedSyncWorker.KEY_FEED_ID, -1L)
                        .putBoolean(FeedSyncWorker.KEY_MANUAL, false)
                        .putBoolean(FeedSyncWorker.KEY_DAILY, true)
                        .build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.HOURS)
                .addTag(TAG_FEED_SYNC)
                .build();
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                DAILY_SYNC_NAME, policy, request);
    }

    private Constraints automaticSyncConstraints() {
        return new Constraints.Builder()
                .setRequiredNetworkType(settings.isWifiOnly()
                        ? NetworkType.UNMETERED : NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(settings.isBatteryNotLowRequired())
                .build();
    }

    public void enqueueManualSync(boolean force) {
        WorkManager.getInstance(context).cancelUniqueWork(STARTUP_SYNC_NAME);
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(false)
                .build();
        enqueueAllFeedSync(constraints, force, true, MANUAL_SYNC_NAME);
    }

    /**
     * Automatic all-feed refresh when the main screen is opened. It honors the user's
     * Wi-Fi-only and battery-not-low preferences, unlike an explicit "sync now" action.
     */
    public void enqueueStartupSync() {
        enqueueAllFeedSync(automaticSyncConstraints(), true, false, STARTUP_SYNC_NAME);
    }

    private void enqueueAllFeedSync(Constraints constraints, boolean force, boolean manual,
                                    String uniqueName) {
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(FeedSyncWorker.class)
                .setConstraints(constraints)
                .setInputData(new androidx.work.Data.Builder()
                        .putBoolean(FeedSyncWorker.KEY_FORCE, force)
                        .putLong(FeedSyncWorker.KEY_FEED_ID, -1L)
                        .putBoolean(FeedSyncWorker.KEY_MANUAL, manual)
                        .build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .addTag(TAG_FEED_SYNC)
                .build();
        WorkManager.getInstance(context).enqueueUniqueWork(
                uniqueName, ExistingWorkPolicy.KEEP, request);
    }

    public void enqueueFeedSync(long feedId, boolean manual) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(false)
                .build();
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(FeedSyncWorker.class)
                .setConstraints(constraints)
                .setInputData(new androidx.work.Data.Builder()
                        .putBoolean(FeedSyncWorker.KEY_FORCE, true)
                        .putLong(FeedSyncWorker.KEY_FEED_ID, feedId)
                        .putBoolean(FeedSyncWorker.KEY_MANUAL, manual)
                        .build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .addTag(TAG_FEED_SYNC)
                .build();
        WorkManager.getInstance(context).enqueueUniqueWork(
                "feed_sync_" + feedId, ExistingWorkPolicy.REPLACE, request);
    }

    public void enqueueArticleDownload(long articleId, boolean manual) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(ArticleDownloadWorker.class)
                .setConstraints(constraints)
                .setInputData(new androidx.work.Data.Builder()
                        .putLong(ArticleDownloadWorker.KEY_ARTICLE_ID, articleId)
                        .putBoolean(ArticleDownloadWorker.KEY_MANUAL, manual)
                        .build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .addTag("article_download")
                .build();
        WorkManager.getInstance(context).enqueueUniqueWork(
                "article_download_" + articleId, ExistingWorkPolicy.REPLACE, request);
    }

    public void enqueueFeedArchive(long feedId) {
        WorkManager.getInstance(context).enqueueUniqueWork(
                "archive_feed_" + feedId, ExistingWorkPolicy.KEEP, archiveRequest(feedId));
    }

    /** Chains a bounded follow-up run if more queued articles arrived mid-flight. */
    public void enqueueArchiveContinuation(long feedId) {
        WorkManager.getInstance(context).enqueueUniqueWork(
                "archive_feed_chain_" + feedId, ExistingWorkPolicy.APPEND_OR_REPLACE,
                archiveRequest(feedId));
    }

    private OneTimeWorkRequest archiveRequest(long feedId) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
        return new OneTimeWorkRequest.Builder(ArticleDownloadWorker.class)
                .setConstraints(constraints)
                .setInputData(new androidx.work.Data.Builder()
                        .putLong(ArticleDownloadWorker.KEY_FEED_ID, feedId)
                        .putBoolean(ArticleDownloadWorker.KEY_BULK, true)
                        .build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .addTag("article_download")
                .build();
    }

    public void ensureArchiveCleanup() {
        Constraints constraints = new Constraints.Builder()
                .setRequiresBatteryNotLow(false)
                .build();
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                ArchiveMaintenanceWorker.class, 1, TimeUnit.DAYS)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.HOURS)
                .addTag("archive_maintenance")
                .build();
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                ARCHIVE_CLEANUP_NAME, ExistingPeriodicWorkPolicy.KEEP, request);
    }

    public void enqueueArchiveCleanupNow() {
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(ArchiveMaintenanceWorker.class)
                .addTag("archive_maintenance")
                .build();
        WorkManager.getInstance(context).enqueue(request);
    }

    public void enqueueRescore() {
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(RescoreWorker.class)
                .addTag("rescore")
                .build();
        WorkManager.getInstance(context).enqueueUniqueWork(
                "rescore_articles", ExistingWorkPolicy.KEEP, request);
    }

    public void shutdown() {
        schedulerExecutor.shutdownNow();
    }
}
