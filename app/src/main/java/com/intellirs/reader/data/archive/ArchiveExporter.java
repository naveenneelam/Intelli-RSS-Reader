package com.intellirs.reader.data.archive;

import com.google.gson.Gson;
import com.google.gson.stream.JsonWriter;
import com.intellirs.reader.data.dao.ArticleDao;
import com.intellirs.reader.data.dao.ArticleNoteDao;
import com.intellirs.reader.data.dao.ArticleTagDao;
import com.intellirs.reader.data.dao.ArticleVersionDao;
import com.intellirs.reader.data.database.AppDatabase;
import com.intellirs.reader.data.entity.ArticleEntity;
import com.intellirs.reader.data.entity.ArticleNoteEntity;
import com.intellirs.reader.data.entity.ArticleTagEntity;
import com.intellirs.reader.data.entity.ArticleVersionEntity;
import com.intellirs.reader.util.DateUtils;
import com.intellirs.reader.util.TextUtils;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Streams archive exports so large libraries are never loaded into memory at once. */
public class ArchiveExporter {
    public enum Format {
        HTML,
        JSON,
        ZIP
    }

    private static final int PAGE = 200;
    private final AppDatabase database;
    private final ArchiveManager archiveManager;
    private final Gson gson = new Gson();

    public ArchiveExporter(AppDatabase database, ArchiveManager archiveManager) {
        this.database = database;
        this.archiveManager = archiveManager;
    }

    public int export(OutputStream outputStream, Format format) throws IOException {
        if (outputStream == null) {
            throw new IOException("No output stream");
        }
        switch (format) {
            case HTML:
                return exportHtml(outputStream);
            case ZIP:
                return exportZip(outputStream);
            case JSON:
            default:
                return exportJson(outputStream);
        }
    }

    private int exportJson(OutputStream outputStream) throws IOException {
        ArticleDao articleDao = database.articleDao();
        ArticleTagDao tagDao = database.articleTagDao();
        ArticleNoteDao noteDao = database.articleNoteDao();
        ArticleVersionDao versionDao = database.articleVersionDao();
        OutputStreamWriter writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8);
        JsonWriter json = new JsonWriter(writer);
        json.setIndent("  ");
        int exported = 0;
        json.beginArray();
        int offset = 0;
        while (true) {
            List<ArticleEntity> page = articleDao.getArchivedPage(PAGE, offset);
            if (page.isEmpty()) {
                break;
            }
            for (ArticleEntity article : page) {
                json.beginObject();
                json.name("id").value(article.id);
                json.name("title").value(nullSafe(article.title));
                json.name("originalUrl").value(nullSafe(article.originalUrl));
                json.name("canonicalUrl").value(nullSafe(article.canonicalUrl));
                json.name("author").value(nullSafe(article.author));
                json.name("publisher").value(nullSafe(article.publisher));
                json.name("publishedAt").value(article.publishedAt);
                json.name("published").value(DateUtils.formatFullDate(article.publishedAt));
                json.name("firstSeenAt").value(article.firstSeenAt);
                json.name("archivedAt").value(article.archivedAt);
                json.name("relevanceScore").value(article.relevanceScore);
                json.name("relevanceLevel").value(article.relevanceLevel.name());
                json.name("description").value(nullSafe(article.description));
                json.name("plainText").value(nullSafe(article.plainText));
                json.name("contentHtml").value(nullSafe(archiveManager.readArchivedHtml(article)));
                json.name("audioUrl").value(nullSafe(article.audioUrl));
                json.name("videoUrl").value(nullSafe(article.videoUrl));
                json.name("tags").beginArray();
                for (ArticleTagEntity tag : tagDao.getForArticle(article.id)) {
                    json.value(tag.tag);
                }
                json.endArray();
                ArticleNoteEntity note = noteDao.getForArticle(article.id);
                json.name("note").value(note == null ? "" : note.note);
                json.name("versions").beginArray();
                for (ArticleVersionEntity version : versionDao.getForArticle(article.id)) {
                    json.beginObject();
                    json.name("number").value(version.versionNumber);
                    json.name("createdAt").value(version.createdAt);
                    json.name("contentHash").value(version.contentHash);
                    json.name("wordCount").value(version.wordCount);
                    json.endObject();
                }
                json.endArray();
                json.endObject();
                json.flush();
                exported++;
            }
            offset += page.size();
            if (page.size() < PAGE) {
                break;
            }
        }
        json.endArray();
        json.flush();
        writer.flush();
        return exported;
    }

    private int exportHtml(OutputStream outputStream) throws IOException {
        ArticleDao articleDao = database.articleDao();
        OutputStreamWriter writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8);
        BufferedWriter out = new BufferedWriter(writer);
        out.write("<!doctype html><html><head><meta charset=\"utf-8\">");
        out.write("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">");
        out.write("<title>IntelliRSS archive</title><style>");
        out.write("body{font-family:Georgia,serif;max-width:760px;margin:0 auto;padding:24px;");
        out.write("line-height:1.55;color:#1d2427;background:#faf9f6}");
        out.write("header{border-bottom:1px solid #ddd;margin-bottom:24px}");
        out.write(".article{border-bottom:1px solid #e2e4e2;padding-bottom:28px;margin-bottom:32px}");
        out.write("h1{font-size:2rem;line-height:1.2} h2,h3{margin-top:1.3em}");
        out.write("a{color:#00695f} .meta{color:#5a6468;font-size:.9em}");
        out.write("img{max-width:100%;height:auto;border-radius:8px}");
        out.write("blockquote{border-left:3px solid #00695f;margin-left:0;padding-left:16px;color:#4d5a5e}");
        out.write("pre{background:#f0efea;padding:12px;border-radius:8px;overflow:auto}");
        out.write("</style></head><body><header><h1>IntelliRSS offline archive</h1>");
        out.write("<p class=\"meta\">Exported " + DateUtils.formatFullDate(System.currentTimeMillis())
                + "</p></header>");
        int exported = 0;
        int offset = 0;
        while (true) {
            List<ArticleEntity> page = articleDao.getArchivedPage(PAGE, offset);
            if (page.isEmpty()) {
                break;
            }
            for (ArticleEntity article : page) {
                String html = archiveManager.readArchivedHtml(article);
                if (html == null || html.trim().isEmpty()) {
                    html = article.description == null ? "" : article.description;
                }
                html = makeExportable(html);
                out.write("<article class=\"article\">");
                out.write("<h2>" + escape(article.title) + "</h2>");
                StringBuilder meta = new StringBuilder();
                if (article.publisher != null) {
                    meta.append(escape(article.publisher));
                }
                if (article.author != null) {
                    if (meta.length() > 0) {
                        meta.append(" &bull; ");
                    }
                    meta.append(escape(article.author));
                }
                if (article.publishedAt > 0) {
                    if (meta.length() > 0) {
                        meta.append(" &bull; ");
                    }
                    meta.append(escape(DateUtils.formatFullDate(article.publishedAt)));
                }
                if (article.originalUrl != null) {
                    if (meta.length() > 0) {
                        meta.append(" &bull; ");
                    }
                    meta.append("<a href=\"" + escapeAttribute(article.originalUrl)
                            + "\">Original article</a>");
                }
                out.write("<p class=\"meta\">" + meta + "</p>");
                out.write(html);
                out.write("</article>");
                out.newLine();
                exported++;
            }
            offset += page.size();
            if (page.size() < PAGE) {
                break;
            }
        }
        out.write("<p class=\"meta\">End of archive. " + exported
                + " articles exported by IntelliRSS.</p></body></html>");
        out.flush();
        return exported;
    }

    private int exportZip(OutputStream outputStream) throws IOException {
        ZipOutputStream zip = new ZipOutputStream(outputStream, StandardCharsets.UTF_8);
        ArticleDao articleDao = database.articleDao();
        int exported = 0;
        ZipEntry readme = new ZipEntry("README.txt");
        zip.putNextEntry(readme);
        String readmeText = "IntelliRSS offline archive\n\n"
                + "Each folder under archive/articles contains content.html, metadata.json,\n"
                + "images and optional podcast audio. Article metadata, notes and tags are\n"
                + "available in manifest.json. Open content.html in any browser.\n";
        zip.write(readmeText.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();

        ZipEntry manifestEntry = new ZipEntry("manifest.json");
        zip.putNextEntry(manifestEntry);
        zip.write(buildManifest(articleDao).getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();

        int offset = 0;
        while (true) {
            List<ArticleEntity> page = articleDao.getArchivedPage(PAGE, offset);
            if (page.isEmpty()) {
                break;
            }
            for (ArticleEntity article : page) {
                File articleDir = archiveManager.articleDirectory(article.id);
                if (!articleDir.isDirectory()) {
                    continue;
                }
                addDirectory(zip, articleDir, "archive/articles/" + article.id + "/");
                exported++;
            }
            offset += page.size();
            if (page.size() < PAGE) {
                break;
            }
        }
        zip.finish();
        zip.flush();
        return exported;
    }

    private String buildManifest(ArticleDao articleDao) {
        ArchiveManifest manifest = new ArchiveManifest();
        manifest.app = "IntelliRSS";
        manifest.exportedAt = System.currentTimeMillis();
        int offset = 0;
        while (true) {
            List<ArticleEntity> page = articleDao.getArchivedPage(PAGE, offset);
            if (page.isEmpty()) {
                break;
            }
            for (ArticleEntity article : page) {
                ArchiveManifest.Entry entry = new ArchiveManifest.Entry();
                entry.id = article.id;
                entry.title = article.title;
                entry.publisher = article.publisher;
                entry.author = article.author;
                entry.url = article.originalUrl;
                entry.publishedAt = article.publishedAt;
                entry.archivedAt = article.archivedAt;
                entry.directory = "archive/articles/" + article.id + "/";
                manifest.articles.add(entry);
            }
            offset += page.size();
            if (page.size() < PAGE) {
                break;
            }
        }
        return gson.toJson(manifest);
    }

    private void addDirectory(ZipOutputStream zip, File directory, String prefix)
            throws IOException {
        File[] children = directory.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            if (child.isDirectory()) {
                addDirectory(zip, child, prefix + child.getName() + "/");
            } else {
                ZipEntry entry = new ZipEntry(prefix + child.getName());
                zip.putNextEntry(entry);
                try (InputStream in = new FileInputStream(child)) {
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        zip.write(buffer, 0, read);
                    }
                }
                zip.closeEntry();
            }
        }
    }

    private String makeExportable(String html) {
        if (html == null || html.trim().isEmpty()) {
            return "";
        }
        Document document = Jsoup.parseBodyFragment(html);
        for (org.jsoup.nodes.Element image : document.select("img[src]")) {
            String original = image.attr("data-original-src");
            if (original.startsWith("http://") || original.startsWith("https://")) {
                image.attr("src", original);
            } else {
                image.remove();
            }
        }
        return document.body().html();
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String escapeAttribute(String value) {
        return escape(value).replace("\"", "&quot;");
    }

    public static String suggestedFileName(Format format) {
        String date = LocalDate.now().toString();
        switch (format) {
            case HTML:
                return "intellirss-archive-" + date + ".html";
            case ZIP:
                return "intellirss-archive-" + date + ".zip";
            case JSON:
            default:
                return "intellirss-archive-" + date + ".json";
        }
    }

    public static String mimeType(Format format) {
        switch (format) {
            case HTML:
                return "text/html";
            case ZIP:
                return "application/zip";
            case JSON:
            default:
                return "application/json";
        }
    }

    public static class ArchiveManifest {
        public String app;
        public long exportedAt;
        public List<Entry> articles = new ArrayList<>();

        public static class Entry {
            public long id;
            public String title;
            public String publisher;
            public String author;
            public String url;
            public long publishedAt;
            public long archivedAt;
            public String directory;
        }
    }
}
