package com.intellirs.reader.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.RawQuery;
import androidx.room.Update;
import androidx.sqlite.db.SupportSQLiteQuery;

import com.intellirs.reader.data.entity.ArticleEntity;
import com.intellirs.reader.data.entity.ArticleRelevanceLevel;
import com.intellirs.reader.data.model.CategoryReadCount;
import com.intellirs.reader.data.model.DailyCount;
import com.intellirs.reader.data.model.FeedReadCount;
import com.intellirs.reader.data.model.FeedStats;
import com.intellirs.reader.data.model.TagCount;

import java.util.List;

@Dao
public interface ArticleDao {
    @Insert
    long insert(ArticleEntity article);

    @Update
    void update(ArticleEntity article);

    @Query("SELECT * FROM articles WHERE id = :id")
    ArticleEntity getById(long id);

    @Query("SELECT * FROM articles WHERE id = :id")
    LiveData<ArticleEntity> observeById(long id);

    @Query("SELECT * FROM articles WHERE canonicalUrl = :url LIMIT 1")
    ArticleEntity findByCanonicalUrl(String url);

    @Query("SELECT * FROM articles WHERE contentHash = :hash ORDER BY id ASC LIMIT 1")
    ArticleEntity findByContentHash(String hash);

    @Query("SELECT * FROM articles WHERE normalizedTitle = :normalizedTitle " +
            "ORDER BY id ASC LIMIT 20")
    List<ArticleEntity> findByNormalizedTitle(String normalizedTitle);

    @RawQuery
    List<ArticleEntity> loadPage(SupportSQLiteQuery query);

    @RawQuery
    int count(SupportSQLiteQuery query);

    @Query("UPDATE articles SET isRead = :read WHERE id = :id")
    void setRead(long id, boolean read);

    @Query("UPDATE articles SET isBookmarked = :bookmarked WHERE id = :id")
    void setBookmarked(long id, boolean bookmarked);

    @Query("UPDATE articles SET primaryFeedId = :feedId, publisher = :publisher, " +
            "originalUrl = :originalUrl WHERE id = :id")
    void updatePrimarySource(long id, Long feedId, String publisher, String originalUrl);

    @Query("UPDATE articles SET isRead = 1")
    void markAllRead();

    @Query("UPDATE articles SET isRead = 1 WHERE id IN " +
            "(SELECT articleId FROM article_feed_cross_ref WHERE feedId = :feedId)")
    void markFeedRead(long feedId);

    @Query("UPDATE articles SET isRead = 1 WHERE id IN " +
            "(SELECT x.articleId FROM article_feed_cross_ref x " +
            "INNER JOIN feeds f ON f.id = x.feedId WHERE f.categoryId = :categoryId)")
    void markCategoryRead(long categoryId);

    @Query("UPDATE articles SET isRead = 1 WHERE id IN (:ids)")
    int markReadByIds(List<Long> ids);

    @Query("UPDATE articles SET isRead = 1 WHERE isRead = 0 AND " +
            "firstSeenAt >= :since")
    int markNewRead(long since);

    @Query("UPDATE articles SET relevanceScore = :score, relevanceLevel = :level, " +
            "relevanceReason = :reason WHERE id = :id")
    void updateRelevance(long id, int score, ArticleRelevanceLevel level, String reason);

    @Query("UPDATE articles SET description = :description, contentHash = :hash, " +
            "normalizedTitle = :normalizedTitle, updatedAt = :now, lastSeenAt = :now, " +
            "thumbnailUrl = :thumbnailUrl, hasImages = :hasImages, hasVideo = :hasVideo, " +
            "hasAudio = :hasAudio, audioUrl = :audioUrl, videoUrl = :videoUrl, " +
            "articleType = :articleType WHERE id = :id")
    void updateFromFeed(long id, String description, String hash, String normalizedTitle,
                        long now, String thumbnailUrl, boolean hasImages, boolean hasVideo,
                        boolean hasAudio, String audioUrl, String videoUrl,
                        com.intellirs.reader.data.entity.ArticleType articleType);

    @Query("UPDATE articles SET lastSeenAt = :now WHERE id = :id")
    void touchLastSeen(long id, long now);

    @Query("UPDATE articles SET isArchived = 1, isManuallyArchived = :manual, " +
            "contentPath = :contentPath, archivedContentHash = :hash, latestVersionId = :versionId, " +
            "extractionQuality = :quality, plainText = :plainText, archivedAt = :now, " +
            "thumbnailPath = :thumbnailPath WHERE id = :id")
    void markArchived(long id, boolean manual, String contentPath, String hash, Long versionId,
                      com.intellirs.reader.data.entity.ExtractionQuality quality,
                      String plainText, long now, String thumbnailPath);

    @Query("UPDATE articles SET isArchived = 0, contentPath = NULL, latestVersionId = NULL, " +
            "archivedContentHash = NULL, extractionQuality = 'NONE', plainText = NULL, " +
            "archivedAt = 0, thumbnailPath = NULL, audioPath = NULL WHERE id = :id")
    void clearArchive(long id);

    @Query("UPDATE articles SET audioPath = :path WHERE id = :id")
    void setAudioPath(long id, String path);

    @Query("UPDATE articles SET aiSummary = :summary WHERE id = :id")
    void updateAiSummary(long id, String summary);

    @Query("UPDATE articles SET aiTags = :tags WHERE id = :id")
    void updateAiTags(long id, String tags);

    @Query("UPDATE articles SET plainText = :text WHERE id = :id")
    void updatePlainText(long id, String text);

    @Query("SELECT * FROM articles WHERE isArchived = 1 " +
            "AND isBookmarked = 0 AND isManuallyArchived = 0 " +
            "AND id NOT IN (SELECT articleId FROM article_notes) " +
            "AND id NOT IN (SELECT articleId FROM article_tags) " +
            "ORDER BY publishedAt ASC")
    List<ArticleEntity> getCleanupCandidates();

    @Query("SELECT * FROM articles WHERE isArchived = 1 ORDER BY archivedAt DESC")
    List<ArticleEntity> getArchived();

    @Query("SELECT * FROM articles WHERE isArchived = 1 ORDER BY id ASC LIMIT :limit OFFSET :offset")
    List<ArticleEntity> getArchivedPage(int limit, int offset);

    @Query("SELECT * FROM articles ORDER BY id ASC LIMIT :limit OFFSET :offset")
    List<ArticleEntity> getPage(int limit, int offset);

    @Query("SELECT * FROM articles WHERE isRead = 0 ORDER BY id ASC LIMIT :limit OFFSET :offset")
    List<ArticleEntity> getUnreadPage(int limit, int offset);

    @Query("SELECT id FROM articles ORDER BY id ASC")
    List<Long> getAllIds();

    @Query("SELECT id FROM articles WHERE isRead = 0 ORDER BY relevanceScore DESC")
    List<Long> getUnreadIds();

    @Query("SELECT COUNT(*) FROM articles")
    int countAll();

    @Query("SELECT COUNT(*) FROM articles WHERE isRead = 0")
    int countUnread();

    @Query("SELECT COUNT(*) FROM articles WHERE isRead = 1")
    int countRead();

    @Query("SELECT COUNT(*) FROM articles WHERE isBookmarked = 1")
    int countBookmarked();

    @Query("SELECT COUNT(*) FROM articles WHERE isArchived = 1")
    int countArchived();

    @Query("SELECT COUNT(*) FROM articles WHERE hasAudio = 1")
    int countAudio();

    @Query("SELECT COUNT(*) AS totalArticles, " +
            "COALESCE(SUM(CASE WHEN a.isRead = 0 THEN 1 ELSE 0 END), 0) AS unreadArticles, " +
            "COALESCE(SUM(CASE WHEN a.isRead = 1 THEN 1 ELSE 0 END), 0) AS readArticles, " +
            "COALESCE(SUM(CASE WHEN a.isBookmarked = 1 THEN 1 ELSE 0 END), 0) AS bookmarkedArticles, " +
            "COALESCE(SUM(CASE WHEN a.isArchived = 1 THEN 1 ELSE 0 END), 0) AS archivedArticles, " +
            "COALESCE((SELECT SUM(h.secondsRead) FROM reading_history h " +
            "  INNER JOIN article_feed_cross_ref x2 ON x2.articleId = h.articleId " +
            "  WHERE x2.feedId = :feedId), 0) AS secondsRead, " +
            "COALESCE(MAX(a.publishedAt), 0) AS latestPublishedAt, 0.0 AS readRate " +
            "FROM articles a INNER JOIN article_feed_cross_ref x ON x.articleId = a.id " +
            "WHERE x.feedId = :feedId")
    FeedStats getFeedStats(long feedId);

    @Query("SELECT * FROM articles WHERE isArchived = 0 AND hasAudio = 1 " +
            "AND audioPath IS NULL LIMIT :limit")
    List<ArticleEntity> getAudioDownloadCandidates(int limit);

    @Query("SELECT * FROM articles WHERE id IN (:ids)")
    List<ArticleEntity> getByIds(List<Long> ids);

    @RawQuery
    int executeRawUpdate(SupportSQLiteQuery query);

    @Query("SELECT COALESCE(SUM(secondsRead), 0) FROM reading_history")
    long totalSecondsRead();

    @Query("SELECT date(h.lastReadAt / 1000, 'unixepoch', 'localtime') AS day, " +
            "COUNT(DISTINCT h.articleId) AS count FROM reading_history h " +
            "WHERE h.lastReadAt >= :since GROUP BY day ORDER BY day ASC")
    List<DailyCount> getDailyReadCounts(long since);

    @Query("SELECT f.id AS feedId, f.title AS feedTitle, " +
            "COUNT(DISTINCT h.articleId) AS readCount, " +
            "(SELECT COUNT(*) FROM article_feed_cross_ref x2 WHERE x2.feedId = f.id) AS totalCount " +
            "FROM reading_history h " +
            "INNER JOIN article_feed_cross_ref x ON x.articleId = h.articleId " +
            "INNER JOIN feeds f ON f.id = x.feedId " +
            "GROUP BY f.id ORDER BY readCount DESC, f.title COLLATE NOCASE ASC LIMIT :limit")
    List<FeedReadCount> getMostReadFeeds(int limit);

    @Query("SELECT c.id AS categoryId, c.name AS categoryName, " +
            "COUNT(DISTINCT h.articleId) AS readCount, " +
            "(SELECT COUNT(*) FROM article_feed_cross_ref x2 INNER JOIN feeds f2 ON f2.id=x2.feedId " +
            " WHERE f2.categoryId = c.id) AS totalCount " +
            "FROM reading_history h " +
            "INNER JOIN article_feed_cross_ref x ON x.articleId = h.articleId " +
            "INNER JOIN feeds f ON f.id = x.feedId " +
            "INNER JOIN categories c ON c.id = f.categoryId " +
            "GROUP BY c.id ORDER BY readCount DESC, c.name COLLATE NOCASE ASC LIMIT :limit")
    List<CategoryReadCount> getMostReadCategories(int limit);

    @Query("SELECT t.tag AS tag, COUNT(*) AS count FROM article_tags t " +
            "GROUP BY t.tag ORDER BY count DESC, t.tag COLLATE NOCASE ASC LIMIT :limit")
    List<TagCount> getTopTags(int limit);
}
