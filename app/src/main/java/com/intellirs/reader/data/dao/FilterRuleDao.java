package com.intellirs.reader.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.intellirs.reader.data.entity.FilterRuleEntity;

import java.util.List;

@Dao
public interface FilterRuleDao {
    @Insert
    long insert(FilterRuleEntity rule);

    @Update
    void update(FilterRuleEntity rule);

    @Delete
    void delete(FilterRuleEntity rule);

    @Query("SELECT * FROM filter_rules ORDER BY priority ASC, id ASC")
    LiveData<List<FilterRuleEntity>> observeAll();

    @Query("SELECT * FROM filter_rules WHERE enabled = 1 ORDER BY priority ASC, id ASC")
    List<FilterRuleEntity> getEnabled();

    @Query("SELECT * FROM filter_rules ORDER BY priority ASC, id ASC")
    List<FilterRuleEntity> getAll();

    @Query("SELECT * FROM filter_rules WHERE id = :id")
    FilterRuleEntity getById(long id);

    @Query("SELECT COALESCE(MAX(priority), 0) + 1 FROM filter_rules")
    int nextPriority();

    @Query("UPDATE filter_rules SET enabled = :enabled, updatedAt = :time WHERE id = :id")
    void setEnabled(long id, boolean enabled, long time);

    @Query("UPDATE filter_rules SET priority = :priority WHERE id = :id")
    void setPriority(long id, int priority);
}
