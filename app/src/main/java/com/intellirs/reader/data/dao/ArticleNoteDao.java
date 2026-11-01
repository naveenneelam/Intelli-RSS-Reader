package com.intellirs.reader.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.intellirs.reader.data.entity.ArticleNoteEntity;

@Dao
public interface ArticleNoteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long save(ArticleNoteEntity note);

    @Query("SELECT * FROM article_notes WHERE articleId = :articleId")
    ArticleNoteEntity getForArticle(long articleId);

    @Query("SELECT * FROM article_notes WHERE articleId = :articleId")
    LiveData<ArticleNoteEntity> observeForArticle(long articleId);

    @Query("DELETE FROM article_notes WHERE articleId = :articleId")
    void deleteForArticle(long articleId);

    @Query("SELECT COUNT(*) FROM article_notes WHERE articleId = :articleId")
    int hasNote(long articleId);
}
