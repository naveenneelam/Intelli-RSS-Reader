package com.intellirs.reader.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.intellirs.reader.data.entity.ArticleTagEntity;

import java.util.List;

@Dao
public interface ArticleTagDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    long add(ArticleTagEntity tag);

    @Query("SELECT * FROM article_tags WHERE articleId = :articleId ORDER BY tag COLLATE NOCASE ASC")
    List<ArticleTagEntity> getForArticle(long articleId);

    @Query("SELECT * FROM article_tags WHERE articleId = :articleId ORDER BY tag COLLATE NOCASE ASC")
    LiveData<List<ArticleTagEntity>> observeForArticle(long articleId);

    @Query("DELETE FROM article_tags WHERE articleId = :articleId AND tag = :tag COLLATE NOCASE")
    void remove(long articleId, String tag);

    @Query("DELETE FROM article_tags WHERE articleId = :articleId")
    void deleteForArticle(long articleId);

    @Query("SELECT tag FROM article_tags WHERE articleId = :articleId")
    List<String> getTags(long articleId);
}
