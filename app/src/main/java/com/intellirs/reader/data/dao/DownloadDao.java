package com.intellirs.reader.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import com.intellirs.reader.data.entity.DownloadQueueEntity;
import com.intellirs.reader.data.entity.DownloadStatus;
import com.intellirs.reader.data.model.DownloadItem;

import java.util.List;

@Dao
public interface DownloadDao {
    @Insert
    long insert(DownloadQueueEntity item);

    @Query("SELECT * FROM download_queue WHERE articleId = :articleId AND downloadType = :type LIMIT 1")
    DownloadQueueEntity find(long articleId, com.intellirs.reader.data.entity.DownloadType type);

    @Query("SELECT * FROM download_queue WHERE articleId = :articleId")
    List<DownloadQueueEntity> forArticle(long articleId);

    @Query("SELECT * FROM download_queue WHERE status IN ('QUEUED', 'RUNNING', 'FAILED') " +
            "ORDER BY createdAt ASC")
    List<DownloadQueueEntity> getPending();

    @Query("SELECT * FROM download_queue WHERE status IN ('QUEUED', 'RUNNING', 'FAILED') " +
            "AND feedId = :feedId ORDER BY createdAt ASC")
    List<DownloadQueueEntity> getPendingForFeed(long feedId);

    @Query("SELECT q.*, a.title AS articleTitle, a.publisher AS publisher, " +
            "a.contentPath AS contentPath, a.audioPath AS audioPath " +
            "FROM download_queue q LEFT JOIN articles a ON a.id = q.articleId " +
            "ORDER BY q.createdAt DESC LIMIT :limit")
    LiveData<List<DownloadItem>> observeDownloads(int limit);

    @Query("UPDATE download_queue SET status = :status, updatedAt = :time, " +
            "lastError = :error WHERE id = :id")
    void updateStatus(long id, DownloadStatus status, long time, String error);

    @Query("UPDATE download_queue SET retryCount = retryCount + 1, updatedAt = :time " +
            "WHERE id = :id")
    void incrementRetry(long id, long time);

    @Query("UPDATE download_queue SET status = 'QUEUED', lastError = NULL, " +
            "updatedAt = :time WHERE id = :id")
    void setQueued(long id, long time);

    @Query("UPDATE download_queue SET retryCount = 0, lastError = NULL, " +
            "updatedAt = :time WHERE id = :id")
    void resetRetry(long id, long time);

    @Query("DELETE FROM download_queue WHERE id = :id")
    void delete(long id);

    @Query("SELECT COUNT(*) FROM download_queue WHERE status IN ('QUEUED', 'RUNNING')")
    int countActive();

    @Query("DELETE FROM download_queue WHERE status = 'COMPLETE' AND updatedAt < :before")
    void pruneCompleted(long before);

    @Query("DELETE FROM download_queue WHERE articleId = :articleId")
    void deleteForArticle(long articleId);
}
