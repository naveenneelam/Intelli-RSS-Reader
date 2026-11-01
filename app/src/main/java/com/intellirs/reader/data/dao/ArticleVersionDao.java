package com.intellirs.reader.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import com.intellirs.reader.data.entity.ArticleVersionEntity;

import java.util.List;

@Dao
public interface ArticleVersionDao {
    @Insert
    long insert(ArticleVersionEntity version);

    @Query("SELECT * FROM article_versions WHERE articleId = :articleId " +
            "ORDER BY versionNumber DESC")
    List<ArticleVersionEntity> getForArticle(long articleId);

    @Query("SELECT * FROM article_versions WHERE id = :id")
    ArticleVersionEntity getById(long id);

    @Query("SELECT * FROM article_versions WHERE articleId = :articleId " +
            "ORDER BY versionNumber DESC LIMIT 1")
    ArticleVersionEntity getLatest(long articleId);

    @Query("SELECT COUNT(*) FROM article_versions WHERE articleId = :articleId")
    int countForArticle(long articleId);

    @Query("SELECT COALESCE(MAX(versionNumber), 0) FROM article_versions WHERE articleId = :articleId")
    int maxVersionNumber(long articleId);

    @Query("DELETE FROM article_versions WHERE articleId = :articleId")
    void deleteForArticle(long articleId);
}
