package com.intellirs.reader.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.intellirs.reader.data.entity.ArticleFtsEntity;

@Dao
public interface ArticleFtsDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    void index(ArticleFtsEntity entry);

    @Query("DELETE FROM article_fts WHERE rowid = :articleId")
    void remove(long articleId);

    @Query("SELECT COUNT(*) FROM article_fts")
    int count();
}
