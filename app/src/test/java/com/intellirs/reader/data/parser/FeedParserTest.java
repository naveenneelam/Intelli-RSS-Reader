package com.intellirs.reader.data.parser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.intellirs.reader.data.entity.ArticleType;

import org.junit.Test;

import java.io.IOException;

public class FeedParserTest {
    private final FeedParser parser = new FeedParser();

    @Test
    public void parsesRssTwoWithMediaAndAuthor() throws Exception {
        ParsedFeed feed = parser.parse(resource("rss2.xml"),
                "https://journal.example.com/feed.xml");

        assertEquals("River City Journal", feed.title);
        assertEquals("RSS", feed.feedType);
        assertEquals("https://journal.example.com/", feed.siteUrl);
        assertEquals(3, feed.articles.size());
        assertEquals("https://journal.example.com/logo.png", feed.iconUrl);

        ParsedArticle first = feed.articles.get(0);
        assertEquals("Reserve Bank holds the line on interest rates", first.title);
        assertTrue(first.contentHtml.contains("cautious pause"));
        assertTrue(first.contentHtml, first.contentHtml.contains("rates-chart.png"));
        assertTrue(first.imageUrls.toString(), first.imageUrls.contains("https://journal.example.com/images/rates-chart.png"));
        assertEquals("https://journal.example.com/images/rates-thumb.jpg", first.thumbnailUrl);
        assertEquals("Asha Nair", first.author);
        assertTrue(first.hasImages);
        assertTrue(first.publishedAt > 0);
        assertEquals("river-bank-rates-001", first.guid);
        assertTrue(first.categories.contains("Economy"));

        ParsedArticle podcast = feed.articles.get(1);
        assertEquals(ArticleType.PODCAST, podcast.articleType);
        assertTrue(podcast.hasAudio);
        assertEquals("https://journal.example.com/audio/week-44.mp3", podcast.audioUrl);
        assertEquals(1_694_000L, podcast.audioDurationMs);

        ParsedArticle video = feed.articles.get(2);
        assertEquals(ArticleType.VIDEO, video.articleType);
        assertTrue(video.hasVideo);
        assertEquals("https://journal.example.com/video/bridge.mp4", video.videoUrl);
    }

    @Test
    public void parsesAtomWithRelativeLinksAndXhtml() throws Exception {
        ParsedFeed feed = parser.parse(resource("atom.xml"),
                "https://orbit.example.org/atom.xml");

        assertEquals("Orbital Notes", feed.title);
        assertEquals("ATOM", feed.feedType);
        assertEquals("https://orbit.example.org/", feed.siteUrl);
        assertEquals("https://orbit.example.org/icon.png", feed.iconUrl);
        assertEquals(2, feed.articles.size());

        ParsedArticle first = feed.articles.get(0);
        assertEquals("A new eye on the Southern Sky", first.title);
        assertEquals("https://orbit.example.org/stories/southern-sky", first.link);
        assertEquals("Dr. Lina Ortega", first.author);
        assertTrue(first.contentHtml.contains("faint galaxies"));
        assertEquals("https://orbit.example.org/images/telescope.jpg", first.thumbnailUrl);
        assertTrue(first.imageUrls.contains("https://orbit.example.org/images/telescope.jpg"));
        assertTrue(first.categories.contains("Astronomy"));

        ParsedArticle second = feed.articles.get(1);
        assertEquals(ArticleType.PODCAST, second.articleType);
        assertEquals("https://orbit.example.org/audio/week-12.ogg", second.audioUrl);
    }

    @Test
    public void parsesRdf() throws Exception {
        ParsedFeed feed = parser.parse(resource("rdf.xml"),
                "https://archive.example.net/feed.rdf");

        assertEquals("Archive Dispatch", feed.title);
        assertEquals("RDF", feed.feedType);
        assertEquals(1, feed.articles.size());
        assertEquals("Letter from the harbour master, 1924", feed.articles.get(0).title);
        assertEquals("Archives Team", feed.articles.get(0).author);
        assertTrue(feed.articles.get(0).publishedAt > 0);
    }

    @Test
    public void rejectsHtmlPage() throws Exception {
        assertThrows(FeedParseException.class,
                () -> parser.parse(resource("invalid-html.html"),
                        "https://example.com/"));
        assertFalse(parser.looksLikeFeed(resource("invalid-html.html")));
    }

    @Test
    public void generatesGuidWhenFeedHasNone() throws Exception {
        String xml = "<?xml version=\"1.0\"?><rss><channel><title>T</title>" +
                "<item><title>No guid here</title><description>Body</description>" +
                "<pubDate>Mon, 12 Oct 2026 08:30:00 GMT</pubDate></item></channel></rss>";
        ParsedFeed feed = parser.parse(xml, "https://t.example.com/rss");
        assertNotNull(feed.articles.get(0).guid);
        assertFalse(feed.articles.get(0).guid.isEmpty());
    }

    private String resource(String name) throws IOException {
        try (java.io.InputStream in = getClass().getClassLoader().getResourceAsStream("feeds/" + name)) {
            assertNotNull("Missing test resource " + name, in);
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return out.toString(java.nio.charset.StandardCharsets.UTF_8);
        }
    }
}
