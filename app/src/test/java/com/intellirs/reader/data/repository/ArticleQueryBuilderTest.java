package com.intellirs.reader.data.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.intellirs.reader.domain.model.ArticleFilter;

import org.junit.Test;

import androidx.sqlite.db.SimpleSQLiteQuery;

public class ArticleQueryBuilderTest {
    @Test
    public void ftsQueryIsSafeAndPrefixed() {
        assertEquals("reserve* bank*", ArticleQueryBuilder.toFtsQuery("Reserve Bank!"));
        assertEquals("", ArticleQueryBuilder.toFtsQuery("!!!"));
        assertTrue(ArticleQueryBuilder.toFtsQuery("many words here").endsWith("*"));
    }

    @Test
    public void feedFilterUsesSourceRelationship() {
        SimpleSQLiteQuery query = (SimpleSQLiteQuery) ArticleQueryBuilder.buildPage(
                ArticleFilter.feed(42L), 20, 0);
        assertTrue(query.getSql().contains("article_feed_cross_ref"));
        assertTrue(query.getSql().contains("xf.feedId"));
        assertTrue(query.getSql().contains(
                "ORDER BY a.isRead ASC, a.publishedAt DESC, a.id DESC"));
        assertEquals(3, query.getArgCount());
    }

    @Test
    public void searchUsesFts() {
        ArticleFilter filter = ArticleFilter.search("market");
        SimpleSQLiteQuery query = (SimpleSQLiteQuery) ArticleQueryBuilder.buildPage(
                filter, 10, 0);
        assertTrue(query.getSql().contains("article_fts"));
        assertTrue(query.getSql().contains("MATCH"));
    }

    @Test
    public void historyUsesReadingHistoryOrdering() {
        SimpleSQLiteQuery query = (SimpleSQLiteQuery) ArticleQueryBuilder.buildPage(
                ArticleFilter.history(), 20, 0);
        assertTrue(query.getSql().contains("reading_history"));
        assertTrue(query.getSql().contains("lastReadAt DESC"));
    }

    @Test
    public void relatedArticlesUseTheFtsIndex() {
        SimpleSQLiteQuery query = (SimpleSQLiteQuery) ArticleQueryBuilder.buildRelated(
                new String[]{"central", "bank"}, 9L, 5);
        assertTrue(query.getSql().contains("article_fts MATCH"));
        assertTrue(query.getSql().contains("a.id != ?"));
    }

    @Test
    public void markAllReadKeepsCurrentFilter() {
        ArticleFilter filter = ArticleFilter.feed(7L);
        SimpleSQLiteQuery query = (SimpleSQLiteQuery) ArticleQueryBuilder.buildMarkAllRead(filter);
        assertTrue(query.getSql().startsWith("UPDATE articles SET isRead = 1"));
        assertTrue(query.getSql().contains("xf.feedId"));
    }
}
