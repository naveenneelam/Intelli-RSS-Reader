package com.intellirs.reader.data.archive;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.util.Base64;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.intellirs.reader.data.backup.BackupManager;
import com.intellirs.reader.data.database.AppDatabase;
import com.intellirs.reader.data.entity.ArticleEntity;
import com.intellirs.reader.data.entity.FeedEntity;
import com.intellirs.reader.data.network.HttpFetcher;
import com.intellirs.reader.data.parser.ContentExtractor;
import com.intellirs.reader.data.repository.SettingsRepository;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

@RunWith(AndroidJUnit4.class)
public class ArchiveManagerTest {
    private static final String PNG_ONE_PIXEL =
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/p9sAAAAASUVORK5CYII=";
    private MockWebServer server;
    private AppDatabase database;
    private ArchiveManager archiveManager;
    private SettingsRepository settings;
    private long articleId;

    @Before
    public void setUp() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        BackupManager.deleteRecursively(new File(context.getFilesDir(), "archive"));
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries().build();
        settings = new SettingsRepository(context);
        settings.setAllowHttp(true);
        settings.setImageMaxMb(2);
        settings.setAudioMaxMb(10);
        server = new MockWebServer();
        server.start();
        String base = server.url("/story").toString();
        FeedEntity feed = new FeedEntity();
        feed.title = "Archive Test";
        feed.feedUrl = base;
        long feedId = database.feedDao().insert(feed);
        ArticleEntity article = new ArticleEntity();
        article.title = "A story worth archiving";
        article.canonicalUrl = base;
        article.originalUrl = base;
        article.guid = "archive-1";
        article.publisher = "Archive Test";
        article.description = "<p>Fallback summary text for the article.</p>";
        article.publishedAt = System.currentTimeMillis();
        article.primaryFeedId = feedId;
        articleId = database.articleDao().insert(article);
        archiveManager = new ArchiveManager(context, database, settings, new HttpFetcher(),
                new ContentExtractor());
    }

    @After
    public void tearDown() throws Exception {
        server.shutdown();
        database.close();
    }

    @Test
    public void archivesHtmlAndImagesAndPreservesVersion() throws Exception {
        enqueueStory("First archived version of the story. The bank left rates unchanged.",
                "first.png");
        ArchiveResult first = archiveManager.archiveArticle(articleId, true);
        assertTrue(first.error, first.success);
        ArticleEntity stored = database.articleDao().getById(articleId);
        assertTrue(stored.isArchived);
        assertNotNull(stored.contentPath);
        assertEquals(1, database.articleVersionDao().countForArticle(articleId));
        File content = archiveManager.resolveStoragePath(stored.contentPath);
        assertNotNull(content);
        assertTrue(content.isFile());
        assertTrue(database.articleImageDao().getForArticle(articleId).size() >= 1);

        enqueueStory("Second archived version with materially changed article content.",
                "second.png");
        ArchiveResult second = archiveManager.archiveArticle(articleId, false);
        assertTrue(second.error, second.success);
        assertEquals(2, database.articleVersionDao().countForArticle(articleId));
        assertTrue(second.versionNumber >= 2);
    }

    @Test
    public void deletingCacheKeepsBookmarkAndNote() {
        enqueueStory("This story is long enough to be archived by the test runner.",
                "delete.png");
        ArchiveResult result = archiveManager.archiveArticle(articleId, true);
        assertTrue(result.error, result.success);
        database.articleDao().setBookmarked(articleId, true);
        com.intellirs.reader.data.entity.ArticleNoteEntity articleNote =
                new com.intellirs.reader.data.entity.ArticleNoteEntity();
        articleNote.articleId = articleId;
        articleNote.note = "My private note";
        database.articleNoteDao().save(articleNote);
        archiveManager.deleteCachedContent(articleId);
        ArticleEntity stored = database.articleDao().getById(articleId);
        assertTrue(stored.isBookmarked);
        assertNotNull(database.articleNoteDao().getForArticle(articleId));
        assertTrue(!stored.isArchived);
    }

    private void enqueueStory(String text, String imageName) {
        String base = server.url("/story").toString();
        String imageUrl = server.url("/images/" + imageName).toString();
        String html = "<html><head><title>A story worth archiving</title></head><body>"
                + "<article><h1>A story worth archiving</h1><p>" + text + "</p>"
                + "<p>Additional reporting gives the extractor enough content to work with."
                + " The committee will meet again next month to review the decision.</p>"
                + "<img src=\"" + imageUrl + "\" alt=\"chart\"></article></body></html>";
        server.enqueue(new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "text/html; charset=utf-8").setBody(html));
        server.enqueue(new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "image/png")
                .setBody(new okio.Buffer().write(Base64.decode(PNG_ONE_PIXEL,
                        Base64.DEFAULT))));
    }
}
