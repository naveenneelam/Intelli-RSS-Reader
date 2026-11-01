package com.intellirs.reader.data.database;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.intellirs.reader.data.entity.ArticleEntity;
import com.intellirs.reader.data.entity.ArticleFeedCrossRef;
import com.intellirs.reader.data.entity.FeedEntity;
import com.intellirs.reader.data.repository.ArticleRepository;
import com.intellirs.reader.domain.model.ArticleFilter;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@RunWith(AndroidJUnit4.class)
public class RoomIntegrationTest {
    private AppDatabase database;
    private ArticleRepository repository;
    private long feedId;
    private long articleId;

    @Before
    public void setUp() {
        database = Room.inMemoryDatabaseBuilder(
                        ApplicationProvider.getApplicationContext(), AppDatabase.class)
                .allowMainThreadQueries()
                .build();
        repository = new ArticleRepository(database);
        FeedEntity feed = new FeedEntity();
        feed.title = "Test Feed";
        feed.feedUrl = "https://example.com/rss";
        feedId = database.feedDao().insert(feed);
        ArticleEntity article = new ArticleEntity();
        article.title = "Central bank holds rates";
        article.description = "<p>The central bank kept interest rates steady.</p>";
        article.canonicalUrl = "https://example.com/story";
        article.guid = "guid-1";
        article.publisher = "Test Feed";
        article.publishedAt = System.currentTimeMillis();
        article.primaryFeedId = feedId;
        articleId = database.articleDao().insert(article);
        ArticleFeedCrossRef crossRef = new ArticleFeedCrossRef();
        crossRef.articleId = articleId;
        crossRef.feedId = feedId;
        crossRef.originalGuid = "guid-1";
        database.articleFeedDao().insert(crossRef);
        repository.rebuildFts(articleId);
    }

    @After
    public void tearDown() {
        database.close();
    }

    @Test
    public void ftsSearchReturnsPersistedArticle() {
        List<ArticleEntity> results = repository.loadPage(
                ArticleFilter.search("central"), 20, 0);
        assertEquals(1, results.size());
        assertEquals("Central bank holds rates", results.get(0).title);
    }

    @Test
    public void feedFilterAndUnreadCountWork() {
        List<ArticleEntity> results = repository.loadPage(ArticleFilter.feed(feedId), 20, 0);
        assertEquals(1, results.size());
        assertEquals(1, database.articleDao().getFeedStats(feedId).unreadArticles);
    }

    @Test
    public void markAllReadWithFilterKeepsOtherArticles() {
        ArticleFilter filter = ArticleFilter.feed(feedId);
        repository.markAllRead(filter);
        ArticleEntity reloaded = database.articleDao().getById(articleId);
        assertNotNull(reloaded);
        assertTrue(reloaded.isRead);
    }

    @Test
    public void tagsAndNotesBecomeSearchable() {
        repository.addTag(articleId, "Economy");
        repository.saveNote(articleId, "Follow up on rate decision");
        List<ArticleEntity> tagResults = repository.loadPage(
                ArticleFilter.search("Economy"), 20, 0);
        assertEquals(1, tagResults.size());
        List<ArticleEntity> noteResults = repository.loadPage(
                ArticleFilter.search("follow"), 20, 0);
        assertEquals(1, noteResults.size());
    }
}
