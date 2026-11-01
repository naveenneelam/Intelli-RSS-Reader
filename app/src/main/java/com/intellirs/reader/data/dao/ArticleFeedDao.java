package com.intellirs.reader.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.intellirs.reader.data.entity.ArticleFeedCrossRef;
import com.intellirs.reader.data.model.ArticleSource;

import java.util.List;

@Dao
public interface ArticleFeedDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    long insert(ArticleFeedCrossRef ref);

    @Query("SELECT * FROM article_feed_cross_ref WHERE articleId = :articleId")
    List<ArticleFeedCrossRef> getRefs(long articleId);

    @Query("SELECT COUNT(*) FROM article_feed_cross_ref WHERE articleId = :articleId")
    int sourceCount(long articleId);

    @Query("SELECT f.id AS feedId, f.title AS feedTitle, f.feedUrl AS feedUrl, " +
            "x.sourceUrl AS sourceUrl, f.feedPriority AS feedImportance, " +
            "f.notificationEnabled AS notificationEnabled, " +
            "x.sourcePublishedAt AS sourcePublishedAt " +
            "FROM article_feed_cross_ref x INNER JOIN feeds f ON f.id = x.feedId " +
            "WHERE x.articleId = :articleId ORDER BY f.feedPriority ASC, f.title COLLATE NOCASE ASC")
    List<ArticleSource> getSources(long articleId);

    @Query("SELECT feedId FROM article_feed_cross_ref WHERE articleId = :articleId")
    List<Long> getFeedIds(long articleId);

    @Query("SELECT articleId FROM article_feed_cross_ref WHERE feedId = :feedId")
    List<Long> getArticleIds(long feedId);

    @Query("SELECT articleId FROM article_feed_cross_ref WHERE feedId = :feedId " +
            "AND originalGuid = :guid AND originalGuid != '' LIMIT 1")
    Long findArticleIdByGuid(long feedId, String guid);

    @Query("UPDATE article_feed_cross_ref SET sourceTitle = :title, " +
            "sourcePublisher = :publisher, sourceUrl = :sourceUrl, " +
            "sourcePublishedAt = :publishedAt, originalGuid = :guid " +
            "WHERE articleId = :articleId AND feedId = :feedId")
    void updateSource(long articleId, long feedId, String title, String publisher,
                      String sourceUrl, long publishedAt, String guid);
}
