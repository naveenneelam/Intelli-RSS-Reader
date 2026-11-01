package com.intellirs.reader.data.database;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.intellirs.reader.data.dao.ArticleDao;
import com.intellirs.reader.data.dao.ArticleFeedDao;
import com.intellirs.reader.data.dao.ArticleFtsDao;
import com.intellirs.reader.data.dao.ArticleImageDao;
import com.intellirs.reader.data.dao.ArticleNoteDao;
import com.intellirs.reader.data.dao.ArticleTagDao;
import com.intellirs.reader.data.dao.ArticleVersionDao;
import com.intellirs.reader.data.dao.CategoryDao;
import com.intellirs.reader.data.dao.DownloadDao;
import com.intellirs.reader.data.dao.FeedDao;
import com.intellirs.reader.data.dao.FeedHealthDao;
import com.intellirs.reader.data.dao.FilterRuleDao;
import com.intellirs.reader.data.dao.KeywordRuleDao;
import com.intellirs.reader.data.dao.NotificationDao;
import com.intellirs.reader.data.dao.ReadingHistoryDao;
import com.intellirs.reader.data.dao.SavedSearchDao;
import com.intellirs.reader.data.dao.SyncRunDao;
import com.intellirs.reader.data.entity.ArticleEntity;
import com.intellirs.reader.data.entity.ArticleFeedCrossRef;
import com.intellirs.reader.data.entity.ArticleFtsEntity;
import com.intellirs.reader.data.entity.ArticleImageEntity;
import com.intellirs.reader.data.entity.ArticleNoteEntity;
import com.intellirs.reader.data.entity.ArticleTagEntity;
import com.intellirs.reader.data.entity.ArticleVersionEntity;
import com.intellirs.reader.data.entity.CategoryEntity;
import com.intellirs.reader.data.entity.Converters;
import com.intellirs.reader.data.entity.DownloadQueueEntity;
import com.intellirs.reader.data.entity.FeedEntity;
import com.intellirs.reader.data.entity.FeedHealthEntity;
import com.intellirs.reader.data.entity.FilterRuleEntity;
import com.intellirs.reader.data.entity.KeywordRuleEntity;
import com.intellirs.reader.data.entity.NotificationQueueEntity;
import com.intellirs.reader.data.entity.ReadingHistoryEntity;
import com.intellirs.reader.data.entity.SavedSearchEntity;
import com.intellirs.reader.data.entity.SyncRunEntity;

/**
 * Single local Room database. No destructive fallback is configured: any future schema
 * change must ship an explicit Migration in MIGRATIONS.
 */
@Database(
        entities = {
                FeedEntity.class,
                CategoryEntity.class,
                ArticleEntity.class,
                ArticleFeedCrossRef.class,
                ArticleVersionEntity.class,
                ArticleImageEntity.class,
                ArticleNoteEntity.class,
                ArticleTagEntity.class,
                ReadingHistoryEntity.class,
                FeedHealthEntity.class,
                FilterRuleEntity.class,
                NotificationQueueEntity.class,
                DownloadQueueEntity.class,
                SavedSearchEntity.class,
                KeywordRuleEntity.class,
                ArticleFtsEntity.class,
                SyncRunEntity.class
        },
        version = 2,
        exportSchema = true)
@TypeConverters(Converters.class)
public abstract class AppDatabase extends RoomDatabase {
    private static final String DB_NAME = "intellirss.db";
    private static volatile AppDatabase instance;

    /** v1 -> v2 adds per-source URLs for preferred-source attribution. */
    public static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE article_feed_cross_ref ADD COLUMN sourceUrl TEXT");
            database.execSQL("CREATE INDEX IF NOT EXISTS " +
                    "`index_article_feed_cross_ref_feedId_originalGuid` " +
                    "ON `article_feed_cross_ref` (`feedId`, `originalGuid`)");
        }
    };

    public static final Migration[] MIGRATIONS = new Migration[]{MIGRATION_1_2};

    public abstract FeedDao feedDao();
    public abstract CategoryDao categoryDao();
    public abstract ArticleDao articleDao();
    public abstract ArticleFeedDao articleFeedDao();
    public abstract ArticleVersionDao articleVersionDao();
    public abstract ArticleImageDao articleImageDao();
    public abstract ArticleNoteDao articleNoteDao();
    public abstract ArticleTagDao articleTagDao();
    public abstract ReadingHistoryDao readingHistoryDao();
    public abstract FeedHealthDao feedHealthDao();
    public abstract FilterRuleDao filterRuleDao();
    public abstract NotificationDao notificationDao();
    public abstract DownloadDao downloadDao();
    public abstract SavedSearchDao savedSearchDao();
    public abstract KeywordRuleDao keywordRuleDao();
    public abstract ArticleFtsDao articleFtsDao();
    public abstract SyncRunDao syncRunDao();

    public static AppDatabase getInstance(@NonNull Context context) {
        if (instance == null) {
            synchronized (AppDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    AppDatabase.class,
                                    DB_NAME)
                            .addMigrations(MIGRATIONS)
                            .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                            .build();
                }
            }
        }
        return instance;
    }

    /** Used by restore; the process must be restarted afterwards. */
    public static void closeForRestore() {
        synchronized (AppDatabase.class) {
            if (instance != null) {
                instance.close();
                instance = null;
            }
        }
    }
}
