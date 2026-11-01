package com.intellirs.reader.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.intellirs.reader.data.entity.CategoryEntity;

import java.util.List;

@Dao
public interface CategoryDao {
    @Insert
    long insert(CategoryEntity category);

    @Update
    void update(CategoryEntity category);

    @Delete
    void delete(CategoryEntity category);

    @Query("SELECT * FROM categories ORDER BY sortOrder ASC, name COLLATE NOCASE ASC")
    LiveData<List<CategoryEntity>> observeAll();

    @Query("SELECT * FROM categories ORDER BY sortOrder ASC, name COLLATE NOCASE ASC")
    List<CategoryEntity> getAll();

    @Query("SELECT * FROM categories WHERE id = :id")
    CategoryEntity getById(long id);

    @Query("SELECT * FROM categories WHERE name = :name COLLATE NOCASE AND " +
            "((parentId IS NULL AND :parentId IS NULL) OR parentId = :parentId) LIMIT 1")
    CategoryEntity findByName(String name, Long parentId);

    @Query("SELECT COUNT(*) FROM categories")
    int count();

    @Query("UPDATE categories SET muteNotifications = :muted WHERE id = :id")
    void setMuted(long id, boolean muted);

    @Query("UPDATE categories SET sortOrder = :order WHERE id = :id")
    void updateSortOrder(long id, int order);

    @Query("UPDATE categories SET parentId = NULL WHERE parentId = :parentId")
    void clearParent(long parentId);
}
