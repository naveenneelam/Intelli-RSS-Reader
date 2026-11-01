package com.intellirs.reader.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.intellirs.reader.data.entity.SyncRunEntity;

import java.util.List;

@Dao
public interface SyncRunDao {
    @Insert
    long insert(SyncRunEntity run);

    @Update
    void update(SyncRunEntity run);

    @Query("SELECT * FROM sync_runs ORDER BY startedAt DESC LIMIT :limit")
    LiveData<List<SyncRunEntity>> observeRecent(int limit);

    @Query("SELECT * FROM sync_runs ORDER BY startedAt DESC LIMIT :limit")
    List<SyncRunEntity> getRecent(int limit);

    @Query("SELECT * FROM sync_runs ORDER BY startedAt DESC LIMIT 1")
    SyncRunEntity getLatest();

    @Query("DELETE FROM sync_runs WHERE startedAt < :before")
    void prune(long before);

    @Query("DELETE FROM sync_runs")
    void clearAll();
}
