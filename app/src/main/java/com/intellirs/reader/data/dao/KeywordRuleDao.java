package com.intellirs.reader.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.intellirs.reader.data.entity.KeywordRuleEntity;
import com.intellirs.reader.data.entity.KeywordType;

import java.util.List;

@Dao
public interface KeywordRuleDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    long insert(KeywordRuleEntity rule);

    @Update
    void update(KeywordRuleEntity rule);

    @Delete
    void delete(KeywordRuleEntity rule);

    @Query("SELECT * FROM keyword_rules WHERE type = :type ORDER BY weight DESC, keyword COLLATE NOCASE ASC")
    LiveData<List<KeywordRuleEntity>> observeByType(KeywordType type);

    @Query("SELECT * FROM keyword_rules WHERE type = :type ORDER BY weight DESC, keyword COLLATE NOCASE ASC")
    List<KeywordRuleEntity> getByType(KeywordType type);

    @Query("SELECT * FROM keyword_rules")
    List<KeywordRuleEntity> getAll();

    @Query("SELECT * FROM keyword_rules WHERE id = :id")
    KeywordRuleEntity getById(long id);

    @Query("SELECT COUNT(*) FROM keyword_rules WHERE keyword = :keyword COLLATE NOCASE AND type = :type")
    int exists(String keyword, KeywordType type);
}
