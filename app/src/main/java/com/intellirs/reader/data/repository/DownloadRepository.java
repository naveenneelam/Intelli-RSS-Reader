package com.intellirs.reader.data.repository;

import androidx.lifecycle.LiveData;

import com.intellirs.reader.data.dao.DownloadDao;
import com.intellirs.reader.data.entity.DownloadQueueEntity;
import com.intellirs.reader.data.entity.DownloadStatus;
import com.intellirs.reader.data.entity.DownloadType;
import com.intellirs.reader.data.model.DownloadItem;
import com.intellirs.reader.worker.WorkScheduler;

import java.util.List;

/** Tracks offline article/audio jobs and keeps the user-visible Downloads screen accurate. */
public class DownloadRepository {
    private final DownloadDao dao;
    private final WorkScheduler scheduler;

    public DownloadRepository(DownloadDao dao, WorkScheduler scheduler) {
        this.dao = dao;
        this.scheduler = scheduler;
    }

    public LiveData<List<DownloadItem>> observeDownloads() {
        return dao.observeDownloads(300);
    }

    public List<DownloadQueueEntity> pendingForFeed(long feedId) {
        return dao.getPendingForFeed(feedId);
    }

    public List<DownloadQueueEntity> allPending() {
        return dao.getPending();
    }

    public DownloadQueueEntity find(long articleId, DownloadType type) {
        return dao.find(articleId, type);
    }

    public List<DownloadQueueEntity> forArticle(long articleId) {
        return dao.forArticle(articleId);
    }

    public long enqueueArticle(long articleId, long feedId, boolean startImmediately) {
        long id = enqueueInternal(articleId, feedId, DownloadType.ARTICLE);
        if (startImmediately) {
            scheduler.enqueueArticleDownload(articleId, true);
        }
        return id;
    }

    public long enqueueAudio(long articleId, long feedId, boolean startImmediately) {
        long id = enqueueInternal(articleId, feedId, DownloadType.AUDIO);
        if (startImmediately) {
            scheduler.enqueueArticleDownload(articleId, true);
        }
        return id;
    }

    /** Called by sync for automatic archiving; a single bulk worker drains the feed queue. */
    public void enqueueAutoArchive(long articleId, long feedId, DownloadType type) {
        enqueueInternal(articleId, feedId, type);
    }

    private synchronized long enqueueInternal(long articleId, long feedId, DownloadType type) {
        long now = System.currentTimeMillis();
        DownloadQueueEntity existing = dao.find(articleId, type);
        if (existing != null) {
            if (existing.status != DownloadStatus.RUNNING) {
                dao.setQueued(existing.id, now);
            }
            return existing.id;
        }
        DownloadQueueEntity item = new DownloadQueueEntity();
        item.articleId = articleId;
        item.feedId = feedId;
        item.downloadType = type;
        item.status = DownloadStatus.QUEUED;
        item.createdAt = now;
        item.updatedAt = now;
        return dao.insert(item);
    }

    public void markRunning(long queueId) {
        dao.updateStatus(queueId, DownloadStatus.RUNNING, System.currentTimeMillis(), null);
    }

    public void markComplete(long queueId) {
        dao.updateStatus(queueId, DownloadStatus.COMPLETE, System.currentTimeMillis(), null);
    }

    public void markFailed(long queueId, String error) {
        dao.updateStatus(queueId, DownloadStatus.FAILED, System.currentTimeMillis(), error);
    }

    public void incrementRetry(long queueId) {
        dao.incrementRetry(queueId, System.currentTimeMillis());
    }

    public void retry(DownloadQueueEntity item) {
        dao.resetRetry(item.id, System.currentTimeMillis());
        dao.setQueued(item.id, System.currentTimeMillis());
        if (item.downloadType == DownloadType.AUDIO) {
            scheduler.enqueueArticleDownload(item.articleId, true);
        } else {
            scheduler.enqueueArticleDownload(item.articleId, true);
        }
    }

    public void remove(long queueId) {
        dao.delete(queueId);
    }
}
