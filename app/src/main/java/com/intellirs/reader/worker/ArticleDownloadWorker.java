package com.intellirs.reader.worker;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.intellirs.reader.IntelliRssApp;
import com.intellirs.reader.data.archive.ArchiveResult;
import com.intellirs.reader.data.archive.DownloadResult;
import com.intellirs.reader.data.entity.ArticleEntity;
import com.intellirs.reader.data.entity.DownloadQueueEntity;
import com.intellirs.reader.data.entity.DownloadStatus;
import com.intellirs.reader.data.entity.DownloadType;
import com.intellirs.reader.data.repository.DownloadRepository;
import com.intellirs.reader.di.AppContainer;

import java.util.ArrayList;
import java.util.List;

/** Processes transactional offline article/audio downloads with bounded retries. */
public class ArticleDownloadWorker extends Worker {
    public static final String KEY_ARTICLE_ID = "article_id";
    public static final String KEY_FEED_ID = "feed_id";
    public static final String KEY_MANUAL = "manual";
    public static final String KEY_BULK = "bulk";
    private static final String TAG = "ArticleDownloadWorker";

    public ArticleDownloadWorker(@NonNull Context context,
                                 @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        AppContainer container = ((IntelliRssApp) getApplicationContext()).getContainer();
        DownloadRepository downloads = container.downloadRepository();
        long articleId = getInputData().getLong(KEY_ARTICLE_ID, -1L);
        long feedId = getInputData().getLong(KEY_FEED_ID, -1L);
        boolean manual = getInputData().getBoolean(KEY_MANUAL, false);
        List<DownloadQueueEntity> items = new ArrayList<>();
        if (feedId > 0) {
            items.addAll(downloads.pendingForFeed(feedId));
        } else if (articleId > 0) {
            items.addAll(downloads.forArticle(articleId));
            if (items.isEmpty()) {
                ArticleEntity article = container.articleRepository().getArticle(articleId);
                if (article != null) {
                    long primaryFeed = article.primaryFeedId == null ? -1L : article.primaryFeedId;
                    downloads.enqueueArticle(articleId, primaryFeed, false);
                    DownloadQueueEntity created = downloads.find(articleId, DownloadType.ARTICLE);
                    if (created != null) {
                        items.add(created);
                    }
                }
            }
        }
        if (items.isEmpty()) {
            return Result.success();
        }

        boolean bulkRun = feedId > 0;
        int processedThisRun = 0;
        for (DownloadQueueEntity item : items) {
            if (bulkRun && processedThisRun >= 25) {
                break;
            }
            processedThisRun++;
            if (item.status == DownloadStatus.COMPLETE) {
                continue;
            }
            downloads.markRunning(item.id);
            if (item.downloadType == DownloadType.AUDIO) {
                DownloadResult audio = container.archiveManager().downloadAudio(item.articleId);
                if (audio.success) {
                    downloads.markComplete(item.id);
                } else {
                    downloads.markFailed(item.id, audio.error);
                    downloads.incrementRetry(item.id);
                }
            } else {
                ArchiveResult result = container.archiveManager()
                        .archiveArticle(item.articleId, manual);
                if (result.success) {
                    downloads.markComplete(item.id);
                } else {
                    downloads.markFailed(item.id, result.error);
                    downloads.incrementRetry(item.id);
                    Log.w(TAG, "Article " + item.articleId + " archive failed: " + result.error);
                }
            }
        }

        List<DownloadQueueEntity> remaining = feedId > 0
                ? downloads.pendingForFeed(feedId) : downloads.forArticle(articleId);
        boolean retry = false;
        boolean queued = false;
        for (DownloadQueueEntity item : remaining) {
            if (item.status == DownloadStatus.FAILED && item.retryCount < 3) {
                retry = true;
            }
            if (item.status == DownloadStatus.QUEUED) {
                queued = true;
            }
        }
        if (retry && getRunAttemptCount() < 3) {
            return Result.retry();
        }
        if (queued && feedId > 0) {
            container.workScheduler().enqueueArchiveContinuation(feedId);
        }
        return Result.success();
    }
}
