package com.intellirs.reader.data.opml;

import androidx.annotation.Nullable;

import com.intellirs.reader.data.entity.CategoryEntity;
import com.intellirs.reader.data.entity.FeedEntity;
import com.intellirs.reader.util.UrlUtils;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Entities;
import org.jsoup.parser.Parser;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * OPML 1.0/2.0 import and export. Parsing is intentionally lenient about missing optional
 * attributes but strict about the OPML root and feed URLs. Large files are streamed from
 * the caller's InputStream into a bounded parser.
 */
public class OpmlManager {
    private static final int MAX_BYTES = 20 * 1024 * 1024;

    public List<OpmlFeed> importFrom(InputStream inputStream) throws OpmlException {
        if (inputStream == null) {
            throw new OpmlException("No OPML input");
        }
        String xml;
        try {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            int total = 0;
            while ((read = inputStream.read(buffer)) != -1) {
                total += read;
                if (total > MAX_BYTES) {
                    throw new OpmlException("The OPML file is larger than 20 MB");
                }
                out.write(buffer, 0, read);
            }
            xml = out.toString(StandardCharsets.UTF_8.name());
        } catch (IOException e) {
            throw new OpmlException("Could not read the OPML file", e);
        }
        return importFrom(xml);
    }

    public List<OpmlFeed> importFrom(String xml) throws OpmlException {
        if (xml == null || xml.trim().isEmpty()) {
            throw new OpmlException("The OPML file is empty");
        }
        Document document;
        try {
            document = Jsoup.parse(xml, "", Parser.xmlParser());
        } catch (RuntimeException e) {
            throw new OpmlException("The OPML XML is malformed", e);
        }
        Element root = document.children().first();
        if (root == null || !"opml".equals(localName(root))) {
            throw new OpmlException("The document is not an OPML file");
        }
        Element body = directChild(root, "body");
        if (body == null) {
            throw new OpmlException("The OPML file has no body");
        }
        List<OpmlFeed> feeds = new ArrayList<>();
        parseOutlines(body, new ArrayList<>(), feeds);
        if (feeds.isEmpty()) {
            throw new OpmlException("The OPML file contains no feed subscriptions");
        }
        return feeds;
    }

    private void parseOutlines(Element parent, List<String> folders, List<OpmlFeed> feeds) {
        for (Element outline : parent.children()) {
            if (!"outline".equals(localName(outline))) {
                continue;
            }
            String xmlUrl = outline.attr("xmlUrl").trim();
            String title = firstNonEmpty(outline.attr("title"), outline.attr("text"));
            String categoryAttr = outline.attr("category").trim();
            if (!xmlUrl.isEmpty()) {
                OpmlFeed feed = new OpmlFeed();
                feed.title = title == null ? "" : title;
                String normalized;
                try {
                    normalized = UrlUtils.normalizeUserInput(xmlUrl, true);
                } catch (IllegalArgumentException e) {
                    feed.error = e.getMessage();
                    feed.feedUrl = xmlUrl;
                    feeds.add(feed);
                    continue;
                }
                feed.feedUrl = normalized;
                String htmlUrl = outline.attr("htmlUrl").trim();
                if (!htmlUrl.isEmpty()) {
                    try {
                        feed.websiteUrl = UrlUtils.normalizeUserInput(htmlUrl, true);
                    } catch (IllegalArgumentException ignored) {
                        feed.websiteUrl = null;
                    }
                }
                feed.feedType = detectType(outline.attr("type"), normalized);
                String path = categoryAttr;
                if (!folders.isEmpty()) {
                    path = String.join("/", folders);
                    if (!categoryAttr.isEmpty() && !categoryAttr.equals(path)) {
                        path = path + "/" + categoryAttr;
                    }
                }
                feed.categoryPath = path;
                if (feed.title.isEmpty()) {
                    feed.title = UrlUtils.host(normalized);
                }
                feeds.add(feed);
            } else {
                String folderName = title == null ? "" : title.trim();
                if (!folderName.isEmpty()) {
                    List<String> nested = new ArrayList<>(folders);
                    nested.add(folderName);
                    parseOutlines(outline, nested, feeds);
                } else {
                    parseOutlines(outline, folders, feeds);
                }
            }
        }
    }

    public String export(List<FeedEntity> feeds, List<CategoryEntity> categories) {
        Document document = Jsoup.parse("<opml></opml>", "", Parser.xmlParser());
        document.outputSettings()
                .syntax(Document.OutputSettings.Syntax.xml)
                .escapeMode(Entities.EscapeMode.xhtml)
                .charset(StandardCharsets.UTF_8)
                .prettyPrint(true);
        Element root = document.selectFirst("opml");
        if (root == null) {
            root = document.appendElement("opml");
        }
        root.attr("version", "2.0");
        Element head = root.appendElement("head");
        head.appendElement("title").text("IntelliRSS subscriptions");
        head.appendElement("dateCreated").text(java.time.ZonedDateTime.now()
                .format(java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME));
        Element body = root.appendElement("body");

        Map<Long, CategoryEntity> categoryById = new HashMap<>();
        Map<Long, List<CategoryEntity>> children = new LinkedHashMap<>();
        for (CategoryEntity category : categories) {
            categoryById.put(category.id, category);
            Long parent = category.parentId;
            children.computeIfAbsent(parent == null ? 0L : parent, key -> new ArrayList<>())
                    .add(category);
        }
        Map<Long, List<FeedEntity>> feedsByCategory = new LinkedHashMap<>();
        List<FeedEntity> uncategorized = new ArrayList<>();
        for (FeedEntity feed : feeds) {
            if (feed.categoryId == null) {
                uncategorized.add(feed);
            } else {
                feedsByCategory.computeIfAbsent(feed.categoryId, key -> new ArrayList<>())
                        .add(feed);
            }
        }
        for (FeedEntity feed : uncategorized) {
            appendFeedOutline(body, feed, null);
        }
        for (CategoryEntity category : children.getOrDefault(0L, new ArrayList<>())) {
            appendCategory(body, category, children, feedsByCategory, "");
        }
        return document.outerHtml();
    }

    private void appendCategory(Element parent, CategoryEntity category,
                                Map<Long, List<CategoryEntity>> children,
                                Map<Long, List<FeedEntity>> feedsByCategory,
                                String parentPath) {
        Element outline = parent.appendElement("outline");
        outline.attr("text", category.name);
        outline.attr("title", category.name);
        String path = parentPath.isEmpty() ? category.name : parentPath + "/" + category.name;
        for (FeedEntity feed : feedsByCategory.getOrDefault(category.id, new ArrayList<>())) {
            appendFeedOutline(outline, feed, path);
        }
        for (CategoryEntity child : children.getOrDefault(category.id, new ArrayList<>())) {
            appendCategory(outline, child, children, feedsByCategory, path);
        }
    }

    private void appendFeedOutline(Element parent, FeedEntity feed, @Nullable String categoryPath) {
        Element outline = parent.appendElement("outline");
        outline.attr("text", feed.title == null ? "" : feed.title);
        outline.attr("title", feed.title == null ? "" : feed.title);
        outline.attr("type", "rss");
        outline.attr("xmlUrl", feed.feedUrl);
        if (feed.websiteUrl != null && !feed.websiteUrl.isEmpty()) {
            outline.attr("htmlUrl", feed.websiteUrl);
        }
        if (categoryPath != null && !categoryPath.isEmpty()) {
            outline.attr("category", categoryPath);
        }
    }

    private static Element directChild(Element parent, String localName) {
        for (Element element : parent.children()) {
            if (localName(element).equalsIgnoreCase(localName)) {
                return element;
            }
        }
        return null;
    }

    private static String localName(Element element) {
        String tag = element.tagName();
        int colon = tag.indexOf(':');
        return (colon >= 0 ? tag.substring(colon + 1) : tag).toLowerCase(Locale.ROOT);
    }

    private static String detectType(String declared, String feedUrl) {
        String lowerUrl = feedUrl.toLowerCase(Locale.ROOT);
        if (lowerUrl.endsWith(".rdf") || lowerUrl.contains("rdf")) {
            return "RDF";
        }
        if (lowerUrl.contains("atom")) {
            return "ATOM";
        }
        if (declared != null && declared.toLowerCase(Locale.ROOT).contains("atom")) {
            return "ATOM";
        }
        return "RSS";
    }

    private static String firstNonEmpty(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return null;
    }
}
