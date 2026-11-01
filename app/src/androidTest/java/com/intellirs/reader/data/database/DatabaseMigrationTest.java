package com.intellirs.reader.data.database;

import static org.junit.Assert.assertTrue;

import androidx.room.testing.MigrationTestHelper;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

@RunWith(AndroidJUnit4.class)
public class DatabaseMigrationTest {
    private static final String TEST_DB = "migration-test.db";

    @Rule
    public MigrationTestHelper helper = new MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            AppDatabase.class.getCanonicalName(),
            new FrameworkSQLiteOpenHelperFactory());

    @Test
    public void migratesFromOneToTwoAndPreservesSchema() throws IOException {
        SupportSQLiteDatabase database = helper.createDatabase(TEST_DB, 1);
        database.close();
        database = helper.runMigrationsAndValidate(TEST_DB, 2, true,
                AppDatabase.MIGRATION_1_2);
        android.database.Cursor cursor = database.query(
                "PRAGMA table_info(article_feed_cross_ref)");
        boolean hasSourceUrl = false;
        while (cursor.moveToNext()) {
            String name = cursor.getString(cursor.getColumnIndex("name"));
            if ("sourceUrl".equals(name)) {
                hasSourceUrl = true;
            }
        }
        cursor.close();
        android.database.Cursor indexes = database.query(
                "PRAGMA index_list(article_feed_cross_ref)");
        boolean hasGuidIndex = false;
        while (indexes.moveToNext()) {
            String name = indexes.getString(indexes.getColumnIndex("name"));
            if ("index_article_feed_cross_ref_feedId_originalGuid".equals(name)) {
                hasGuidIndex = true;
            }
        }
        indexes.close();
        database.close();
        assertTrue("sourceUrl column must exist after migration", hasSourceUrl);
        assertTrue("GUID lookup index must exist after migration", hasGuidIndex);
    }
}
