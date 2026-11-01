package com.intellirs.reader.data.opml;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.intellirs.reader.data.entity.CategoryEntity;
import com.intellirs.reader.data.entity.FeedEntity;

import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class OpmlManagerTest {
    private final OpmlManager manager = new OpmlManager();

    @Test
    public void importsNestedFoldersAndFeeds() throws Exception {
        List<OpmlFeed> feeds = manager.importFrom(resource("subscriptions.opml"));
        assertEquals(5, feeds.size());
        assertEquals("Global Desk", feeds.get(0).title);
        assertEquals("https://global.example.com/rss", feeds.get(0).feedUrl);
        assertEquals("News/World", feeds.get(0).categoryPath);
        assertEquals("The Wire", feeds.get(1).title);
        assertEquals("Local Paper", feeds.get(2).title);
        assertEquals("Independent Blog", feeds.get(3).title);
        assertEquals("ATOM", feeds.get(3).feedType);
        assertEquals("not a url", feeds.get(4).feedUrl);
        assertNotNull(feeds.get(4).error);
    }

    @Test
    public void rejectsNonOpml() {
        assertThrows(OpmlException.class, () -> manager.importFrom("<html><body>No</body></html>"));
        assertThrows(OpmlException.class, () -> manager.importFrom(""));
    }

    @Test
    public void exportsNestedCategoryHierarchy() {
        CategoryEntity news = new CategoryEntity();
        news.id = 1;
        news.name = "News";
        CategoryEntity world = new CategoryEntity();
        world.id = 2;
        world.name = "World";
        world.parentId = 1L;

        FeedEntity feed = new FeedEntity();
        feed.title = "Global Desk";
        feed.feedUrl = "https://global.example.com/rss";
        feed.websiteUrl = "https://global.example.com/";
        feed.categoryId = 2L;

        FeedEntity uncategorized = new FeedEntity();
        uncategorized.title = "Independent";
        uncategorized.feedUrl = "https://independent.example.com/feed";

        List<CategoryEntity> categories = new ArrayList<>();
        categories.add(news);
        categories.add(world);
        List<FeedEntity> feeds = new ArrayList<>();
        feeds.add(feed);
        feeds.add(uncategorized);

        String xml = manager.export(feeds, categories);
        assertTrue(xml.contains("xmlUrl"));
        assertTrue(xml.contains("Global Desk"));
        assertTrue(xml.contains("World"));
        assertTrue(xml.contains("Independent"));
        assertEquals(2, count(xml, "xmlUrl=\""));
    }

    @Test
    public void roundTripsExportedOpml() throws Exception {
        CategoryEntity category = new CategoryEntity();
        category.id = 1;
        category.name = "Tech";
        FeedEntity feed = new FeedEntity();
        feed.title = "Tech Notes";
        feed.feedUrl = "https://tech.example.org/feed.xml";
        feed.categoryId = 1L;
        List<FeedEntity> feeds = new ArrayList<>();
        feeds.add(feed);
        List<CategoryEntity> categories = new ArrayList<>();
        categories.add(category);
        String xml = manager.export(feeds, categories);
        List<OpmlFeed> imported = manager.importFrom(xml);
        assertEquals(1, imported.size());
        assertEquals("Tech Notes", imported.get(0).title);
        assertEquals("Tech", imported.get(0).categoryPath);
    }

    private static int count(String value, String needle) {
        int count = 0;
        int index = 0;
        while ((index = value.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }

    private String resource(String name) throws IOException {
        try (java.io.InputStream in = getClass().getClassLoader().getResourceAsStream("opml/" + name)) {
            assertNotNull(in);
            byte[] data = in.readAllBytes();
            return new String(data, java.nio.charset.StandardCharsets.UTF_8);
        }
    }
}
