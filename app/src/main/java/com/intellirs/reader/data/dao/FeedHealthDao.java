package com.intellirs.reader.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.intellirs.reader.data.entity.FeedHealthEntity;

import java.util.List;

@Dao
public interface FeedHealthDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long save(FeedHealthEntity health);

    @Query("SELECT * FROM feed_health WHERE feedId = :feedId")
    FeedHealthEntity getForFeed(long feedId);

    @Query("SELECT * FROM feed_health WHERE feedId = :feedId")
    LiveData<FeedHealthEntity> observeForFeed(long feedId);

    @Query("SELECT * FROM feed_health ORDER BY status DESC")
    List<FeedHealthEntity> getAll();

    @Query("DELETE FROM feed_health WHERE feedId = :feedId")
    void deleteForFeed(long feedId);
}
