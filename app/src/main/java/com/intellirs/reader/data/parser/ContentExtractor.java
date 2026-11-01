package com.intellirs.reader.data.parser;

import androidx.annotation.Nullable;

import com.intellirs.reader.data.entity.ExtractionQuality;
import com.intellirs.reader.util.DateUtils;
import com.intellirs.reader.util.TextUtils;
import com.intellirs.reader.util.UrlUtils;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Native Java readability-style extractor and HTML sanitizer. It never executes scripts
 * and removes all remote content except validated HTTP(S) images and links.
 */
public class ContentExtractor {
    private static final int MAX_IMAGES = 60;
    private static final Set<String> ALLOWED_TAGS = new HashSet<>(java.util.Arrays.asList(
            "p", "br", "h1", "h2", "h3", "h4", "h5", "h6", "ul", "ol", "li",
            "blockquote", "pre", "code", "strong", "b", "em", "i", "u", "s", "small",
            "figure", "figcaption", "img", "a", "hr", "table", "thead", "tbody", "tfoot",
            "tr", "th", "td", "div", "span", "sup", "sub", "dl", "dt", "dd", "abbr"
    ));
    private static final Set<String> DISCARD_TAGS = new HashSet<>(java.util.Arrays.asList(
            "script", "style", "noscript", "template", "form", "input", "button", "select",
            "textarea", "object", "embed", "iframe", "video", "audio", "source", "track",
            "canvas", "svg", "math", "link", "meta"
    ));

    public ExtractionResult extract(String html, String baseUrl) {
        ExtractionResult result = new ExtractionResult();
        if (html == null || html.trim().isEmpty()) {
            return result;
        }
        Document doc;
        try {
            doc = Jsoup.parse(html, baseUrl);
        } catch (RuntimeException e) {
            return result;
        }
        result.finalUrl = baseUrl;
        result.title = firstNonEmpty(
                meta(doc, "property", "og:title"),
                meta(doc, "name", "twitter:title"),
                doc.title());
        if (result.title == null) {
            result.title = "";
        }
        String title = result.title;
        int pipe = title.lastIndexOf(" | ");
        int dash = title.lastIndexOf(" - ");
        if (pipe > 12) {
            title = title.substring(0, pipe);
        } else if (dash > 12) {
            title = title.substring(0, dash);
        }
        result.title = title.replaceAll("\\s+", " ").trim();

        result.author = cleanByline(firstNonEmpty(
                meta(doc, "name", "author"),
                meta(doc, "property", "article:author"),
                textOf(doc.selectFirst("[rel=author]")),
                textOf(doc.selectFirst(".byline")),
                textOf(doc.selectFirst(".author")),
                textOf(doc.selectFirst("[class*=author-name]"))));
        result.siteName = meta(doc, "property", "og:site_name");
        result.publishedAt = DateUtils.parse(firstNonEmpty(
                meta(doc, "property", "article:published_time"),
                meta(doc, "itemprop", "datePublished"),
                meta(doc, "name", "date"),
                meta(doc, "name", "pubdate")));

        String ogImage = firstNonEmpty(
                meta(doc, "property", "og:image"),
                meta(doc, "name", "twitter:image"),
                meta(doc, "property", "og:image:url"));
        result.featuredImageUrl = UrlUtils.resolve(baseUrl, ogImage);

        String video = firstNonEmpty(meta(doc, "property", "og:video"),
                meta(doc, "property", "og:video:url"),
                meta(doc, "property", "twitter:player"));
        result.videoUrl = UrlUtils.resolve(baseUrl, video);
        for (Element iframe : doc.select("iframe[src]")) {
            String src = UrlUtils.resolve(baseUrl, iframe.attr("src"));
            if (src != null && isVideo(src)) {
                result.videoUrl = src;
                break;
            }
        }
        if (result.videoUrl == null) {
            for (Element videoTag : doc.select("video")) {
                String src = videoSource(videoTag);
                String resolved = UrlUtils.resolve(baseUrl, src);
                if (resolved != null) {
                    result.videoUrl = resolved;
                    break;
                }
            }
        }

        Element root = doc.body();
        Element candidate = chooseCandidate(doc);
        if (candidate == null) {
            candidate = root;
        }
        Element clean = candidate == null ? null : candidate.clone();
        if (clean == null) {
            return result;
        }
        // The candidate tag itself is normalized to a safe container; only its inner
        // content is stored, so the original publisher wrapper is never trusted.
        clean.tagName("div");
        removeClutter(clean);
        Set<String> images = new LinkedHashSet<>();
        sanitize(clean, baseUrl, images);
        result.imageUrls.addAll(images);
        result.cleanHtml = clean.html();
        result.plainText = clean.text().replaceAll("\\s+", " ").trim();

        if (result.featuredImageUrl == null && !result.imageUrls.isEmpty()) {
            result.featuredImageUrl = result.imageUrls.iterator().next();
        }
        if (result.featuredImageUrl != null && !result.imageUrls.contains(result.featuredImageUrl)) {
            result.imageUrls.add(0, result.featuredImageUrl);
        }

        int textLength = result.plainText.length();
        if (textLength >= 400) {
            result.quality = ExtractionQuality.FULL;
        } else if (textLength >= 120) {
            result.quality = ExtractionQuality.FALLBACK;
        } else {
            result.quality = ExtractionQuality.NONE;
        }
        return result;
    }

    /** Sanitizes a feed description fragment for safe display and storage. */
    public String sanitizeFragment(@Nullable String html, String baseUrl) {
        if (html == null || html.trim().isEmpty()) {
            return "";
        }
        Document doc = Jsoup.parseBodyFragment(html, baseUrl);
        Element body = doc.body();
        removeClutter(body);
        sanitize(body, baseUrl, new LinkedHashSet<>());
        return body.html();
    }

    public String sanitizeToText(@Nullable String html, String baseUrl) {
        return Jsoup.parse(sanitizeFragment(html, baseUrl)).text();
    }

    private Element chooseCandidate(Document doc) {
        Elements explicit = doc.select(
                "article, main, [role=main], .article-body, .article-content, .article__body, " +
                        ".entry-content, .post-content, .story-body, .post-body, .content-body, " +
                        "#article-body, #content");
        Element best = null;
        double bestScore = 0;
        for (Element element : explicit) {
            double score = score(element);
            if (score > bestScore) {
                best = element;
                bestScore = score;
            }
        }
        if (best != null && score(best) >= 180) {
            return best;
        }
        // Last resort: score block divs/sections, then body.
        Elements blocks = doc.select("div, section");
        for (Element element : blocks) {
            if (element.select("p").size() < 2) {
                continue;
            }
            double score = score(element);
            if (score > bestScore) {
                best = element;
                bestScore = score;
            }
        }
        if (best != null && bestScore >= 250) {
            return best;
        }
        return doc.body();
    }

    private double score(Element element) {
        Elements paragraphs = element.select("p");
        int paragraphText = 0;
        for (Element p : paragraphs) {
            paragraphText += Math.max(0, p.text().length() - 30);
        }
        int totalText = element.text().length();
        if (totalText == 0) {
            return 0;
        }
        int linkText = 0;
        for (Element a : element.select("a")) {
            linkText += a.text().length();
        }
        double linkDensity = (double) linkText / totalText;
        int headings = element.select("h2, h3").size();
        return paragraphText + totalText * 0.18 + paragraphs.size() * 45.0
                + headings * 20.0 - linkDensity * totalText * 1.4;
    }

    private void removeClutter(Element root) {
        root.select("script, style, noscript, template, form, input, button, select, textarea, " +
                "nav, aside, footer, header, [role=navigation], [aria-hidden=true], " +
                ".ad, .ads, .advert, .advertisement, .sponsor, .promo, .newsletter, " +
                ".subscribe, .subscription, .cookie, .cookies, .comments, .comment, " +
                ".share, .sharing, .social, .related, .recommended, .sidebar, .breadcrumb, " +
                ".breadcrumbs, .pagination, .pager, .popup, .modal, .paywall, .toolbar")
                .remove();
        for (Element element : new ArrayList<>(root.getAllElements())) {
            if (DISCARD_TAGS.contains(element.tagName().toLowerCase(Locale.ROOT))) {
                element.remove();
            }
        }
    }

    private void sanitize(Element root, String baseUrl, Set<String> imageUrls) {
        List<Element> elements = new ArrayList<>(root.getAllElements());
        for (Element element : elements) {
            if (element == root) {
                element.clearAttributes();
                continue;
            }
            String tag = element.tagName().toLowerCase(Locale.ROOT);
            if (DISCARD_TAGS.contains(tag)) {
                element.remove();
                continue;
            }
            if (!ALLOWED_TAGS.contains(tag)) {
                if (tag.equals("picture") || tag.equals("span") || tag.equals("font")) {
                    element.unwrap();
                } else {
                    element.unwrap();
                }
                continue;
            }
            if ("img".equals(tag)) {
                String source = firstNonEmpty(element.attr("src"), element.attr("data-src"),
                        element.attr("data-original"), element.attr("data-lazy-src"));
                String resolved = UrlUtils.resolve(baseUrl, source);
                if (resolved == null) {
                    element.remove();
                    continue;
                }
                if (isTinyImage(element)) {
                    element.remove();
                    continue;
                }
                if (imageUrls.size() >= MAX_IMAGES) {
                    element.remove();
                    continue;
                }
                String alt = element.attr("alt");
                String title = element.attr("title");
                element.clearAttributes();
                element.attr("src", resolved);
                if (!alt.isEmpty()) {
                    element.attr("alt", alt);
                }
                if (!title.isEmpty()) {
                    element.attr("title", title);
                }
                imageUrls.add(resolved);
            } else if ("a".equals(tag)) {
                String href = UrlUtils.resolve(baseUrl, element.attr("href"));
                String title = element.attr("title");
                element.clearAttributes();
                if (href != null) {
                    element.attr("href", href);
                }
                if (!title.isEmpty()) {
                    element.attr("title", title);
                }
            } else {
                String title = element.attr("title");
                element.clearAttributes();
                if (!title.isEmpty() && ("abbr".equals(tag) || "figure".equals(tag))) {
                    element.attr("title", title);
                }
            }
            if (element.text().trim().isEmpty() && element.children().isEmpty()
                    && !"br".equals(tag) && !"hr".equals(tag) && !"img".equals(tag)) {
                element.remove();
            }
        }
    }

    private boolean isTinyImage(Element element) {
        try {
            String width = element.attr("width");
            String height = element.attr("height");
            if (!width.isEmpty() && Integer.parseInt(width.replaceAll("[^0-9]", "")) <= 2) {
                return true;
            }
            if (!height.isEmpty() && Integer.parseInt(height.replaceAll("[^0-9]", "")) <= 2) {
                return true;
            }
        } catch (NumberFormatException ignored) {
            return false;
        }
        return false;
    }

    private static String meta(Document doc, String attribute, String value) {
        Element element = doc.selectFirst("meta[" + attribute + "=\"" + value + "\"]");
        return element == null ? null : element.attr("content");
    }

    private static String textOf(@Nullable Element element) {
        return element == null ? null : element.text();
    }

    private static String firstNonEmpty(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return null;
    }

    private static String cleanByline(String value) {
        if (value == null) {
            return null;
        }
        return value.replaceAll("^(By|Written by)\\s+", "").replaceAll("\\s+", " ").trim();
    }

    private static String videoSource(Element video) {
        String src = video.attr("src");
        if (src.isEmpty()) {
            Element source = video.selectFirst("source[src]");
            src = source == null ? "" : source.attr("src");
        }
        return src;
    }

    private static boolean isVideo(String url) {
        String lower = url.toLowerCase(Locale.ROOT);
        return lower.contains("youtube.com") || lower.contains("youtu.be")
                || lower.contains("vimeo.com") || lower.contains("dailymotion.com")
                || lower.endsWith(".mp4") || lower.endsWith(".webm") || lower.endsWith(".m4v");
    }
}
