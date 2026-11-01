package com.intellirs.reader.data.parser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.intellirs.reader.data.entity.ExtractionQuality;

import org.junit.Test;

import java.io.IOException;

public class ContentExtractorTest {
    private final ContentExtractor extractor = new ContentExtractor();

    @Test
    public void extractsReadableContentAndRemovesClutter() throws Exception {
        ExtractionResult result = extractor.extract(resource("article.html"),
                "https://news.example.com/story/bridge");

        assertTrue(result.isUsable());
        assertEquals(ExtractionQuality.FULL, result.quality);
        assertEquals("City bridge opens to pedestrians", result.title);
        assertEquals("Maya Rao", result.author);
        assertEquals("The Daily Example", result.siteName);
        assertEquals("https://news.example.com/images/bridge-hero.jpg", result.featuredImageUrl);
        assertTrue(result.plainText.contains("central arch"));
        assertFalse(result.cleanHtml.contains("window.tracker"));
        assertFalse(result.cleanHtml.contains("javascript:"));
        assertFalse(result.cleanHtml.contains("should not survive"));
        assertFalse(result.plainText.contains("Related"));
        assertFalse(result.plainText.contains("cookies"));
        assertTrue(result.imageUrls.contains("https://news.example.com/images/bridge-deck.jpg"));
        assertFalse(result.imageUrls.contains("https://news.example.com/pixel.gif"));
        assertEquals("https://www.youtube.com/embed/abc123", result.videoUrl);
    }

    @Test
    public void sanitizeFragmentDropsEventHandlersAndScripts() {
        String dirty = "<p onclick=\"steal()\">Hello <b>world</b></p>" +
                "<script>alert(1)</script><img src=\"/x.jpg\" onerror=\"alert(1)\">" +
                "<a href=\"javascript:bad()\">bad</a>";
        String clean = extractor.sanitizeFragment(dirty, "https://example.org/");
        assertFalse(clean.contains("onclick"));
        assertFalse(clean.contains("onerror"));
        assertFalse(clean.contains("javascript:"));
        assertFalse(clean.contains("<script"));
        assertTrue(clean.contains("https://example.org/x.jpg"));
        assertTrue(clean.contains("Hello"));
    }

    @Test
    public void lowTextQualityIsNotMarkedFull() {
        ExtractionResult result = extractor.extract("<html><body><p>Too short.</p></body></html>",
                "https://example.com/");
        assertNotNull(result);
        assertFalse(result.isUsable());
        assertTrue(result.quality == ExtractionQuality.NONE
                || result.quality == ExtractionQuality.FALLBACK);
    }

    private String resource(String name) throws IOException {
        try (java.io.InputStream in = getClass().getClassLoader()
                .getResourceAsStream("extraction/" + name)) {
            assertNotNull(in);
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
