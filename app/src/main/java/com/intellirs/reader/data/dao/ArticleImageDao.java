package com.intellirs.reader.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import com.intellirs.reader.data.entity.ArticleImageEntity;

import java.util.List;

@Dao
public interface ArticleImageDao {
    @Insert
    long insert(ArticleImageEntity image);

    @Query("SELECT * FROM article_images WHERE articleId = :articleId ORDER BY id ASC")
    List<ArticleImageEntity> getForArticle(long articleId);

    @Query("SELECT * FROM article_images WHERE articleId = :articleId AND sha256 = :hash LIMIT 1")
    ArticleImageEntity findByHash(long articleId, String hash);

    @Query("SELECT COALESCE(SUM(fileSize), 0) FROM article_images")
    long totalBytes();

    @Query("DELETE FROM article_images WHERE articleId = :articleId")
    void deleteForArticle(long articleId);
}
