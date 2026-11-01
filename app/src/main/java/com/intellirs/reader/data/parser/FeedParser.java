package com.intellirs.reader.data.parser;

import androidx.annotation.Nullable;

import com.intellirs.reader.data.entity.ArticleType;
import com.intellirs.reader.util.DateUtils;
import com.intellirs.reader.util.HashUtils;
import com.intellirs.reader.util.TextUtils;
import com.intellirs.reader.util.UrlUtils;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Java RSS 2.0, RSS 1.0/RDF and Atom parser built on Jsoup's XML parser. It performs no
 * network I/O and is therefore safe to unit-test with fixture strings.
 */
public class FeedParser {

    public ParsedFeed parse(String xml, String feedUrl) throws FeedParseException {
        if (xml == null || xml.trim().isEmpty()) {
            throw new FeedParseException("The feed response was empty");
        }
        Document document;
        try {
            document = Jsoup.parse(xml, "", Parser.xmlParser());
        } catch (RuntimeException e) {
            throw new FeedParseException("The feed XML could not be parsed", e);
        }
        Element root = document.children().first();
        if (root == null) {
            throw new FeedParseException("The feed has no root element");
        }
        String rootName = localName(root);
        ParsedFeed feed = new ParsedFeed();
        feed.feedUrl = feedUrl;
        if ("rss".equals(rootName)) {
            parseRss(document, root, feed);
        } else if ("feed".equals(rootName)) {
            parseAtom(root, feed);
        } else if ("rdf".equals(rootName)) {
            parseRdf(document, root, feed);
        } else {
            throw new FeedParseException("Unsupported feed root element: " + root.tagName());
        }
        if (feed.title.isEmpty()) {
            feed.title = UrlUtils.host(feedUrl);
        }
        if (feed.feedType.isEmpty()) {
            feed.feedType = "RSS";
        }
        for (ParsedArticle article : feed.articles) {
            completeArticle(article, feed, feedUrl);
        }
        return feed;
    }

    public boolean looksLikeFeed(String xml) {
        if (xml == null) {
            return false;
        }
        String head = xml.trim();
        if (!head.startsWith("<")) {
            return false;
        }
        try {
            Document document = Jsoup.parse(xml, "", Parser.xmlParser());
            Element root = document.children().first();
            if (root == null) {
                return false;
            }
            String name = localName(root);
            return "rss".equals(name) || "feed".equals(name) || "rdf".equals(name);
        } catch (RuntimeException e) {
            return false;
        }
    }

    private void parseRss(Document document, Element root, ParsedFeed feed) {
        Element channel = directChild(root, "channel");
        if (channel == null) {
            channel = root;
        }
        feed.feedType = "RSS";
        feed.title = cleanText(directText(channel, "title"));
        feed.description = cleanText(directText(channel, "description"));
        String link = resolveLink(directText(channel, "link"), feed.feedUrl);
        if (link != null) {
            feed.siteUrl = link;
        } else {
            Element linkElement = directChild(channel, "link");
            if (linkElement != null) {
                feed.siteUrl = resolveLink(linkElement.attr("href"), feed.feedUrl);
            }
        }
        feed.language = cleanText(directText(channel, "language"));
        addCategories(feed.categories, channel);

        Element image = directChild(channel, "image");
        if (image != null) {
            String icon = directChildren(image, "url").isEmpty()
                    ? image.attr("href") : directText(image, "url");
            feed.iconUrl = UrlUtils.resolve(feed.feedUrl, icon);
        }

        List<Element> items = directChildren(root, "item");
        if (items.isEmpty() && root != channel) {
            items = directChildren(channel, "item");
        }
        for (Element item : items) {
            feed.articles.add(parseRssItem(item, feed.feedUrl));
        }

        // Some RSS 1.0 documents expose channel + items directly on the RDF root.
        List<Element> rdfItems = directChildren(document.children().first(), "item");
        if (rdfItems.isEmpty()) {
            rdfItems = new ArrayList<>(document.select("item"));
        }
        if (!rdfItems.isEmpty() && feed.articles.isEmpty()) {
            for (Element item : rdfItems) {
                feed.articles.add(parseRssItem(item, feed.feedUrl));
            }
            if (!feed.title.isEmpty()) {
                feed.feedType = "RDF";
            }
        }
    }

    private void parseRdf(Document document, Element root, ParsedFeed feed) {
        feed.feedType = "RDF";
        Element channel = directChild(root, "channel");
        if (channel != null) {
            feed.title = cleanText(directText(channel, "title"));
            feed.description = cleanText(directText(channel, "description"));
            String link = resolveLink(directText(channel, "link"), feed.feedUrl);
            if (link != null) {
                feed.siteUrl = link;
            }
            feed.language = cleanText(directText(channel, "language"));
            addCategories(feed.categories, channel);
        }
        for (Element item : directChildren(root, "item")) {
            feed.articles.add(parseRssItem(item, feed.feedUrl));
        }
    }

    private ParsedArticle parseRssItem(Element item, String feedUrl) {
        ParsedArticle article = new ParsedArticle();
        article.title = cleanText(directText(item, "title"));
        article.link = resolveLink(directText(item, "link"), feedUrl);
        if (article.link == null) {
            Element linkElement = directChild(item, "link");
            if (linkElement != null) {
                article.link = resolveLink(linkElement.attr("href"), feedUrl);
            }
        }
        String guid = directText(item, "guid");
        if (guid == null || guid.trim().isEmpty()) {
            guid = directText(item, "id");
        }
        article.guid = cleanText(guid);
        article.descriptionHtml = decodeMarkup(directChild(item, "description"));
        article.contentHtml = decodeMarkup(firstDirectChild(item, "encoded", "content"));
        article.author = firstNonEmpty(
                cleanText(directText(item, "creator")),
                cleanText(directText(item, "author")));
        article.publishedAt = DateUtils.parse(firstNonEmpty(
                directText(item, "pubDate"),
                directText(item, "published"),
                directText(item, "updated"),
                directText(item, "date")));
        addCategories(article.categories, item);
        parseMedia(article, item, feedUrl);
        return article;
    }

    private void parseAtom(Element root, ParsedFeed feed) {
        feed.feedType = "ATOM";
        feed.title = cleanText(directText(root, "title"));
        feed.description = cleanText(firstNonEmpty(
                directText(root, "subtitle"), directText(root, "description")));
        feed.language = root.attr("xml:lang");
        if (feed.language.isEmpty()) {
            feed.language = directText(root, "language");
        }
        Element alternate = findAtomLink(root, "alternate");
        if (alternate != null) {
            feed.siteUrl = resolveLink(alternate.attr("href"), feed.feedUrl);
        }
        String icon = firstNonEmpty(directText(root, "icon"), directText(root, "logo"));
        feed.iconUrl = UrlUtils.resolve(feed.feedUrl, icon);
        for (Element category : directChildren(root, "category")) {
            String value = firstNonEmpty(category.attr("term"), category.attr("label"), category.text());
            if (value != null && !value.trim().isEmpty()) {
                feed.categories.add(value.trim());
            }
        }
        for (Element entry : directChildren(root, "entry")) {
            feed.articles.add(parseAtomEntry(entry, feed.feedUrl));
        }
    }

    private ParsedArticle parseAtomEntry(Element entry, String feedUrl) {
        ParsedArticle article = new ParsedArticle();
        Element titleElement = directChild(entry, "title");
        article.title = cleanText(titleElement == null ? "" : titleElement.text());
        Element link = findAtomLink(entry, "alternate");
        if (link == null) {
            link = findAtomLink(entry, null);
        }
        if (link != null) {
            article.link = resolveLink(link.attr("href"), feedUrl);
        }
        String id = directText(entry, "id");
        article.guid = cleanText(id);

        Element summary = directChild(entry, "summary");
        article.descriptionHtml = decodeMarkup(summary);
        Element content = directChild(entry, "content");
        article.contentHtml = decodeMarkup(content);
        if ((article.descriptionHtml == null || article.descriptionHtml.trim().isEmpty())) {
            article.descriptionHtml = article.contentHtml;
        }

        Element author = directChild(entry, "author");
        if (author != null) {
            article.author = firstNonEmpty(
                    cleanText(directText(author, "name")),
                    cleanText(directText(author, "email")));
        }
        article.publishedAt = DateUtils.parse(firstNonEmpty(
                directText(entry, "published"),
                directText(entry, "updated"),
                directText(entry, "issued"),
                directText(entry, "created")));
        for (Element category : directChildren(entry, "category")) {
            String value = firstNonEmpty(category.attr("term"), category.attr("label"), category.text());
            if (value != null && !value.trim().isEmpty()) {
                article.categories.add(value.trim());
            }
        }
        parseMedia(article, entry, feedUrl);
        return article;
    }

    private void parseMedia(ParsedArticle article, Element item, String feedUrl) {
        String thumbnail = null;
        String audio = null;
        String video = null;
        long duration = 0L;

        for (Element element : item.getAllElements()) {
            String tag = localName(element);
            String qName = element.tagName().toLowerCase(Locale.ROOT);
            if ("thumbnail".equals(tag) && (qName.startsWith("media:") || qName.contains("thumbnail"))) {
                String url = resolveLink(element.attr("url"), feedUrl);
                if (thumbnail == null && url != null) {
                    thumbnail = url;
                }
            }
            if ("content".equals(tag) && qName.startsWith("media:")) {
                String url = resolveLink(element.attr("url"), feedUrl);
                String type = element.attr("type").toLowerCase(Locale.ROOT);
                String medium = element.attr("medium").toLowerCase(Locale.ROOT);
                if (url == null) {
                    continue;
                }
                if (type.startsWith("image/") || "image".equals(medium)) {
                    if (thumbnail == null) {
                        thumbnail = url;
                    }
                } else if (type.startsWith("audio/") || "audio".equals(medium)
                        || looksLikeAudio(url)) {
                    if (audio == null) {
                        audio = url;
                        duration = parseDuration(firstNonEmpty(element.attr("duration"), ""));
                    }
                } else if (type.startsWith("video/") || "video".equals(medium)
                        || isVideoEmbed(url)) {
                    if (video == null) {
                        video = url;
                    }
                }
            }
            if ("enclosure".equals(tag) || ("link".equals(tag)
                    && ("enclosure".equalsIgnoreCase(element.attr("rel")) || element.hasAttr("enclosure")))) {
                String url = resolveLink(element.attr("url"), feedUrl);
                if (url == null) {
                    url = resolveLink(element.attr("href"), feedUrl);
                }
                if (url == null) {
                    continue;
                }
                String type = element.attr("type").toLowerCase(Locale.ROOT);
                if (type.startsWith("image/") || looksLikeImage(url)) {
                    if (thumbnail == null) {
                        thumbnail = url;
                    }
                } else if (type.startsWith("audio/") || looksLikeAudio(url)) {
                    if (audio == null) {
                        audio = url;
                    }
                } else if (type.startsWith("video/") || looksLikeVideoFile(url)) {
                    if (video == null) {
                        video = url;
                    }
                }
            }
            if ("duration".equals(tag) && qName.startsWith("itunes:")) {
                duration = parseDuration(element.text());
            }
            if ("video".equals(tag)) {
                String url = resolveLink(firstNonEmpty(element.attr("src"), element.attr("poster")), feedUrl);
                if (video == null && url != null) {
                    video = url;
                }
            }
        }

        String body = article.contentHtml != null && !article.contentHtml.isEmpty()
                ? article.contentHtml : article.descriptionHtml;
        if (body != null) {
            Document bodyDoc = Jsoup.parseBodyFragment(body, feedUrl);
            for (Element img : bodyDoc.select("img")) {
                String source = firstNonEmpty(img.attr("src"), img.attr("data-src"),
                        img.attr("data-original"), img.attr("data-lazy-src"));
                String resolved = resolveLink(source, feedUrl);
                if (resolved != null) {
                    article.imageUrls.add(resolved);
                    if (thumbnail == null) {
                        thumbnail = resolved;
                    }
                }
            }
            for (Element iframe : bodyDoc.select("iframe[src]")) {
                String resolved = resolveLink(iframe.attr("src"), feedUrl);
                if (resolved != null && video == null && isVideoEmbed(resolved)) {
                    video = resolved;
                }
            }
            for (Element videoTag : bodyDoc.select("video")) {
                if (video != null) {
                    break;
                }
                String source = videoTag.attr("src");
                if (source.isEmpty()) {
                    Element sourceTag = videoTag.selectFirst("source[src]");
                    source = sourceTag == null ? "" : sourceTag.attr("src");
                }
                String resolved = resolveLink(source, feedUrl);
                if (resolved != null) {
                    video = resolved;
                }
            }
            for (Element audioTag : bodyDoc.select("audio")) {
                if (audio != null) {
                    break;
                }
                String source = audioTag.attr("src");
                if (source.isEmpty()) {
                    Element sourceTag = audioTag.selectFirst("source[src]");
                    source = sourceTag == null ? "" : sourceTag.attr("src");
                }
                String resolved = resolveLink(source, feedUrl);
                if (resolved != null) {
                    audio = resolved;
                }
            }
        }

        article.thumbnailUrl = thumbnail;
        article.audioUrl = audio;
        article.audioDurationMs = duration;
        article.videoUrl = video;
        article.hasImages = thumbnail != null || !article.imageUrls.isEmpty();
        article.hasAudio = audio != null;
        article.hasVideo = video != null;
        if (audio != null) {
            article.articleType = ArticleType.PODCAST;
        } else if (video != null) {
            article.articleType = ArticleType.VIDEO;
        } else {
            article.articleType = ArticleType.ARTICLE;
        }
    }

    private void completeArticle(ParsedArticle article, ParsedFeed feed, String feedUrl) {
        if (article.title != null && article.title.contains("<")) {
            article.title = TextUtils.stripHtmlOrEmpty(article.title);
        }
        if (article.title == null || article.title.trim().isEmpty()) {
            article.title = firstNonEmpty(article.guid, "Untitled article");
        }
        if (article.guid == null || article.guid.trim().isEmpty()) {
            article.guid = HashUtils.sha256(
                    firstNonEmpty(article.link, article.title) + "|" + article.publishedAt);
        }
        if (article.link != null) {
            article.link = UrlUtils.withoutFragment(article.link);
            if (!UrlUtils.isValidHttpUrl(article.link)) {
                article.link = null;
            }
        }
        if (article.descriptionHtml == null && article.contentHtml != null) {
            article.descriptionHtml = article.contentHtml;
        }
        if (article.contentHtml == null && article.descriptionHtml != null) {
            article.contentHtml = article.descriptionHtml;
        }
        if (article.publishedAt <= 0) {
            feed.warnings.add("One item is missing a valid publication date");
        }
    }

    private Element findAtomLink(Element parent, @Nullable String rel) {
        for (Element link : directChildren(parent, "link")) {
            String linkRel = link.attr("rel");
            if (rel == null) {
                if (!linkRel.isEmpty() && !"alternate".equalsIgnoreCase(linkRel)) {
                    continue;
                }
            } else if (!linkRel.isEmpty() && !rel.equalsIgnoreCase(linkRel)) {
                continue;
            } else if (linkRel.isEmpty() && !"alternate".equals(rel)) {
                continue;
            }
            String href = link.attr("href");
            if (href != null && !href.trim().isEmpty()) {
                return link;
            }
        }
        return null;
    }

    private void addCategories(List<String> target, Element parent) {
        for (Element category : directChildren(parent, "category")) {
            String value = firstNonEmpty(category.attr("term"), category.attr("label"), category.text());
            if (value != null && !value.trim().isEmpty() && !target.contains(value.trim())) {
                target.add(value.trim());
            }
        }
    }

    private static Element directChild(Element parent, String localName) {
        for (Element child : parent.children()) {
            if (localName(child).equalsIgnoreCase(localName)) {
                return child;
            }
        }
        return null;
    }

    private static Element firstDirectChild(Element parent, String... localNames) {
        for (String name : localNames) {
            Element child = directChild(parent, name);
            if (child != null) {
                return child;
            }
        }
        return null;
    }

    private static List<Element> directChildren(Element parent, String localName) {
        List<Element> result = new ArrayList<>();
        if (parent == null) {
            return result;
        }
        for (Element child : parent.children()) {
            if (localName(child).equalsIgnoreCase(localName)) {
                result.add(child);
            }
        }
        return result;
    }

    private static String localName(Element element) {
        String name = element.tagName();
        int colon = name.indexOf(':');
        return (colon >= 0 ? name.substring(colon + 1) : name).toLowerCase(Locale.ROOT);
    }

    private static String directText(Element parent, String localName) {
        Element child = directChild(parent, localName);
        return child == null ? "" : child.text();
    }

    @Nullable
    private static String decodeMarkup(@Nullable Element element) {
        if (element == null) {
            return null;
        }
        String raw = element.html();
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        // Jsoup's XML parser preserves CDATA markers in some html() paths. They are
        // transport syntax, not article content, so unwrap them before sanitizing.
        raw = raw.replace("<![CDATA[", "").replace("]]>", "");
        return Parser.unescapeEntities(raw, true);
    }

    private static String cleanText(@Nullable String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }

    @Nullable
    private static String resolveLink(@Nullable String raw, String baseUrl) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        String value = raw.trim();
        if (UrlUtils.isValidHttpUrl(value)) {
            return value;
        }
        return UrlUtils.resolve(baseUrl, value);
    }

    private static String firstNonEmpty(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return null;
    }

    private static boolean looksLikeImage(String url) {
        String lower = url.toLowerCase(Locale.ROOT);
        return lower.matches(".*\\.(jpg|jpeg|png|gif|webp|avif)(\\?.*)?$");
    }

    private static boolean looksLikeAudio(String url) {
        String lower = url.toLowerCase(Locale.ROOT);
        return lower.matches(".*\\.(mp3|m4a|aac|ogg|opus|wav|flac)(\\?.*)?$");
    }

    private static boolean looksLikeVideoFile(String url) {
        String lower = url.toLowerCase(Locale.ROOT);
        return lower.matches(".*\\.(mp4|m4v|webm|mov|ogv)(\\?.*)?$");
    }

    private static boolean isVideoEmbed(String url) {
        String lower = url.toLowerCase(Locale.ROOT);
        return lower.contains("youtube.com/embed") || lower.contains("youtube-nocookie.com")
                || lower.contains("youtu.be") || lower.contains("vimeo.com")
                || lower.contains("dailymotion.com") || lower.contains("/embed/")
                || lower.matches(".*\\.(mp4|m4v|webm|mov|ogv)(\\?.*)?$");
    }

    private static long parseDuration(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return 0L;
        }
        String value = raw.trim();
        try {
            if (value.contains(":")) {
                String[] parts = value.split(":");
                long total = 0;
                for (String part : parts) {
                    total = total * 60 + Long.parseLong(part.trim());
                }
                return total * 1000L;
            }
            return (long) (Double.parseDouble(value) * 1000L);
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }
}
