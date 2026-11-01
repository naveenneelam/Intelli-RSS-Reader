package com.intellirs.reader.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.intellirs.reader.data.entity.ReadingHistoryEntity;

@Dao
public interface ReadingHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long save(ReadingHistoryEntity history);

    @Query("SELECT * FROM reading_history WHERE articleId = :articleId")
    ReadingHistoryEntity getForArticle(long articleId);

    @Query("UPDATE reading_history SET lastReadAt = :time, progress = :progress, " +
            "secondsRead = secondsRead + :seconds WHERE articleId = :articleId")
    void updateProgress(long articleId, long time, float progress, long seconds);

    @Query("DELETE FROM reading_history")
    void clearAll();

    @Query("SELECT COUNT(*) FROM reading_history")
    int count();

    @Query("SELECT * FROM reading_history ORDER BY lastReadAt DESC LIMIT :limit OFFSET :offset")
    java.util.List<ReadingHistoryEntity> getPage(int limit, int offset);
}
