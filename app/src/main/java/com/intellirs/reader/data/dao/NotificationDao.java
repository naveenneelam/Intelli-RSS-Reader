package com.intellirs.reader.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import com.intellirs.reader.data.entity.NotificationQueueEntity;
import com.intellirs.reader.data.entity.NotificationStatus;

import java.util.List;

@Dao
public interface NotificationDao {
    @Insert
    long insert(NotificationQueueEntity entry);

    @Query("UPDATE notification_queue SET notificationStatus = :status, postedAt = :postedAt " +
            "WHERE id = :id")
    void updateResult(long id, NotificationStatus status, long postedAt);

    @Query("SELECT COUNT(*) FROM notification_queue WHERE notificationStatus = 'POSTED' " +
            "AND postedAt >= :since")
    int countPostedSince(long since);

    @Query("SELECT COUNT(*) FROM notification_queue WHERE articleId = :articleId " +
            "AND notificationStatus = 'POSTED'")
    int countPostedForArticle(long articleId);

    @Query("SELECT COUNT(*) FROM notification_queue WHERE contentHash = :hash " +
            "AND notificationStatus = 'POSTED' AND id != :excludeId")
    int countPostedForHash(String hash, long excludeId);

    @Query("SELECT * FROM notification_queue ORDER BY createdAt DESC LIMIT :limit")
    LiveData<List<NotificationQueueEntity>> observeRecent(int limit);

    @Query("SELECT * FROM notification_queue ORDER BY createdAt DESC LIMIT :limit")
    List<NotificationQueueEntity> getRecent(int limit);

    @Query("DELETE FROM notification_queue")
    void clearAll();

    @Query("DELETE FROM notification_queue WHERE createdAt < :before")
    void prune(long before);
}
