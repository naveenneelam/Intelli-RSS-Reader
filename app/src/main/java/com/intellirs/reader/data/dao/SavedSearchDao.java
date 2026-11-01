package com.intellirs.reader.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;

import com.intellirs.reader.data.entity.SavedSearchEntity;

import java.util.List;

@Dao
public interface SavedSearchDao {
    @Insert
    long insert(SavedSearchEntity search);

    @Delete
    void delete(SavedSearchEntity search);

    @Query("SELECT * FROM saved_searches ORDER BY createdAt DESC")
    LiveData<List<SavedSearchEntity>> observeAll();

    @Query("SELECT * FROM saved_searches ORDER BY createdAt DESC")
    List<SavedSearchEntity> getAll();

    @Query("DELETE FROM saved_searches")
    void clearAll();
}
