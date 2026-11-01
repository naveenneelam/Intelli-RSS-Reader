package com.intellirs.reader.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.intellirs.reader.data.entity.FeedEntity;
import com.intellirs.reader.data.model.FeedListItem;

import java.util.List;

@Dao
public interface FeedDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    long insert(FeedEntity feed);

    @Update
    void update(FeedEntity feed);

    @Query("SELECT * FROM feeds WHERE id = :id")
    FeedEntity getById(long id);

    @Query("SELECT * FROM feeds WHERE id = :id")
    LiveData<FeedEntity> observeById(long id);

    @Query("SELECT * FROM feeds WHERE feedUrl = :url LIMIT 1")
    FeedEntity getAnyByUrl(String url);

    @Query("SELECT * FROM feeds WHERE feedUrl = :url AND isActive = 1 LIMIT 1")
    FeedEntity getActiveByUrl(String url);

    @Query("SELECT * FROM feeds WHERE isActive = 1 ORDER BY sortOrder ASC, title COLLATE NOCASE ASC")
    List<FeedEntity> getAllActive();

    @Query("SELECT COUNT(*) FROM feeds WHERE isActive = 1")
    int countActive();

    @Query("SELECT COUNT(*) FROM feeds")
    int countAll();

    @Query("SELECT MIN(CASE WHEN syncIntervalMinutes > 0 THEN syncIntervalMinutes ELSE :defaultMinutes END) " +
            "FROM feeds WHERE isActive = 1")
    Integer minEffectiveSyncInterval(int defaultMinutes);

    @Query("SELECT MAX(sortOrder) FROM feeds")
    Integer maxSortOrder();

    /**
     * Single query powering the home screen. Counts use source cross-refs so syndicated
     * articles count once in each owning feed.
     */
    @Query("SELECT f.*, c.name AS categoryName, " +
            "COALESCE((SELECT COUNT(*) FROM article_feed_cross_ref x " +
            "  INNER JOIN articles a ON a.id = x.articleId " +
            "  WHERE x.feedId = f.id AND a.isRead = 0), 0) AS unreadCount, " +
            "COALESCE((SELECT COUNT(*) FROM article_feed_cross_ref x " +
            "  INNER JOIN articles a ON a.id = x.articleId WHERE x.feedId = f.id), 0) AS totalCount, " +
            "COALESCE((SELECT MAX(a.publishedAt) FROM article_feed_cross_ref x " +
            "  INNER JOIN articles a ON a.id = x.articleId WHERE x.feedId = f.id), 0) AS latestPublishedAt, " +
            "COALESCE(h.status, 'HEALTHY') AS healthStatus " +
            "FROM feeds f " +
            "LEFT JOIN categories c ON c.id = f.categoryId " +
            "LEFT JOIN feed_health h ON h.feedId = f.id " +
            "WHERE f.isActive = 1 " +
            "ORDER BY f.sortOrder ASC, f.title COLLATE NOCASE ASC")
    LiveData<List<FeedListItem>> observeFeedList();

    @Query("SELECT f.*, c.name AS categoryName, " +
            "COALESCE((SELECT COUNT(*) FROM article_feed_cross_ref x " +
            "  INNER JOIN articles a ON a.id = x.articleId " +
            "  WHERE x.feedId = f.id AND a.isRead = 0), 0) AS unreadCount, " +
            "COALESCE((SELECT COUNT(*) FROM article_feed_cross_ref x " +
            "  INNER JOIN articles a ON a.id = x.articleId WHERE x.feedId = f.id), 0) AS totalCount, " +
            "COALESCE((SELECT MAX(a.publishedAt) FROM article_feed_cross_ref x " +
            "  INNER JOIN articles a ON a.id = x.articleId WHERE x.feedId = f.id), 0) AS latestPublishedAt, " +
            "COALESCE(h.status, 'HEALTHY') AS healthStatus " +
            "FROM feeds f " +
            "LEFT JOIN categories c ON c.id = f.categoryId " +
            "LEFT JOIN feed_health h ON h.feedId = f.id " +
            "WHERE f.id = :feedId")
    FeedListItem getFeedListItem(long feedId);

    @Query("UPDATE feeds SET lastSyncTime = :time, lastSuccessfulSync = :time, " +
            "failureCount = 0, lastHttpStatus = :httpStatus, etag = :etag, " +
            "lastModified = :lastModified WHERE id = :id")
    void updateSyncSuccess(long id, long time, int httpStatus, String etag, String lastModified);

    @Query("UPDATE feeds SET lastSyncTime = :time, failureCount = 0, " +
            "lastHttpStatus = 304 WHERE id = :id")
    void updateNotModified(long id, long time);

    @Query("UPDATE feeds SET lastSyncTime = :time, failureCount = :failures, " +
            "lastHttpStatus = :httpStatus WHERE id = :id")
    void updateSyncFailure(long id, long time, int failures, int httpStatus);

    @Query("UPDATE feeds SET isActive = :active, notificationEnabled = " +
            "CASE WHEN :active = 0 THEN 0 ELSE notificationEnabled END WHERE id = :id")
    void setActive(long id, boolean active);

    @Query("UPDATE feeds SET notificationEnabled = :enabled WHERE id = :id")
    void setNotificationsEnabled(long id, boolean enabled);

    @Query("UPDATE feeds SET sortOrder = :order WHERE id = :id")
    void updateSortOrder(long id, int order);

    @Query("UPDATE feeds SET faviconPath = :path WHERE id = :id")
    void updateFaviconPath(long id, String path);

    @Query("UPDATE feeds SET description = :description WHERE id = :id")
    void updateDescription(long id, String description);

    @Query("UPDATE feeds SET categoryId = NULL WHERE categoryId = :categoryId")
    void clearCategory(long categoryId);
}
