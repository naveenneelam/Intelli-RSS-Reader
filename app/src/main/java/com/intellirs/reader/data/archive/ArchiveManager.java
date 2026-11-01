package com.intellirs.reader.data.archive;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.intellirs.reader.data.dao.ArticleDao;
import com.intellirs.reader.data.dao.ArticleFtsDao;
import com.intellirs.reader.data.dao.ArticleImageDao;
import com.intellirs.reader.data.dao.ArticleNoteDao;
import com.intellirs.reader.data.dao.ArticleTagDao;
import com.intellirs.reader.data.dao.ArticleVersionDao;
import com.intellirs.reader.data.dao.DownloadDao;
import com.intellirs.reader.data.dao.FeedDao;
import com.intellirs.reader.data.database.AppDatabase;
import com.intellirs.reader.data.entity.ArticleEntity;
import com.intellirs.reader.data.entity.ArticleFtsEntity;
import com.intellirs.reader.data.entity.ArticleImageEntity;
import com.intellirs.reader.data.entity.ArticleNoteEntity;
import com.intellirs.reader.data.entity.ArticleTagEntity;
import com.intellirs.reader.data.entity.ArticleVersionEntity;
import com.intellirs.reader.data.entity.ExtractionQuality;
import com.intellirs.reader.data.entity.FeedEntity;
import com.intellirs.reader.data.network.HttpException;
import com.intellirs.reader.data.network.HttpFetcher;
import com.intellirs.reader.data.network.HttpResult;
import com.intellirs.reader.data.parser.ContentExtractor;
import com.intellirs.reader.data.parser.ExtractionResult;
import com.intellirs.reader.data.repository.SettingsRepository;
import com.intellirs.reader.util.HashUtils;
import com.intellirs.reader.util.ImageUtils;
import com.intellirs.reader.util.TextUtils;
import com.intellirs.reader.util.UrlUtils;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * First-class offline archive. HTML and binaries live in app-private files; Room stores only
 * relative paths. File writes happen in a temp directory and the article is marked archived
 * only after all required files have been safely moved and fsynced.
 */
public class ArchiveManager {
    private static final String TAG = "ArchiveManager";
    private static final int MAX_ARTICLE_HTML_BYTES = 10 * 1024 * 1024;
    private static final int MAX_IMAGES = 40;
    private static final int MAX_FILE_READ_BYTES = 24 * 1024 * 1024;

    private final Context context;
    private final AppDatabase database;
    private final ArticleDao articleDao;
    private final ArticleVersionDao versionDao;
    private final ArticleImageDao imageDao;
    private final ArticleNoteDao noteDao;
    private final ArticleTagDao tagDao;
    private final ArticleFtsDao ftsDao;
    private final DownloadDao downloadDao;
    private final FeedDao feedDao;
    private final SettingsRepository settings;
    private final HttpFetcher httpFetcher;
    private final ContentExtractor extractor;
    private final Gson gson = new Gson();
    private final File filesDir;
    private final File archiveRoot;

    public ArchiveManager(Context context, AppDatabase database, SettingsRepository settings,
                          HttpFetcher httpFetcher, ContentExtractor extractor) {
        this.context = context.getApplicationContext();
        this.database = database;
        this.articleDao = database.articleDao();
        this.versionDao = database.articleVersionDao();
        this.imageDao = database.articleImageDao();
        this.noteDao = database.articleNoteDao();
        this.tagDao = database.articleTagDao();
        this.ftsDao = database.articleFtsDao();
        this.downloadDao = database.downloadDao();
        this.feedDao = database.feedDao();
        this.settings = settings;
        this.httpFetcher = httpFetcher;
        this.extractor = extractor;
        this.filesDir = context.getFilesDir();
        this.archiveRoot = new File(filesDir, "archive");
    }

    public ArchiveResult archiveArticle(long articleId, boolean manualRequest) {
        ArticleEntity article = articleDao.getById(articleId);
        if (article == null) {
            return ArchiveResult.failure("The article no longer exists");
        }
        FeedEntity feed = article.primaryFeedId == null ? null
                : feedDao.getById(article.primaryFeedId);
        FetchOutcome fetched = fetchContent(article, feed);
        if (!fetched.success) {
            return ArchiveResult.failure(fetched.error);
        }
        if (article.isArchived && fetched.quality == ExtractionQuality.NONE) {
            return ArchiveResult.failure(
                    "Could not refresh this article. The existing offline copy was preserved.");
        }

        File temp = new File(archiveRoot, ".tmp/article_" + articleId + "_"
                + System.currentTimeMillis());
        File tempImages = new File(temp, "images");
        File tempVersions = new File(temp, "versions");
        try {
            ensureDirectory(temp);
            ensureDirectory(tempImages);
            ensureDirectory(tempVersions);

            ParsedContent parsed = rewriteImages(fetched.html, fetched.baseUrl, articleId,
                    tempImages);
            if (parsed.plainText.length() < 20) {
                return ArchiveResult.failure("No readable article content could be stored");
            }

            List<DownloadedImage> records = new ArrayList<>();
            Map<String, String> urlToImage = new HashMap<>();
            for (DownloadedImage image : parsed.images) {
                records.add(image);
                urlToImage.put(image.originalUrl, image.relativePath);
                writeBytes(new File(tempImages, image.fileName), image.bytes);
            }

            String thumbnailPath = null;
            if (fetched.featuredImageUrl != null) {
                DownloadedImage featured = downloadAndStoreImage(fetched.featuredImageUrl,
                        articleId, tempImages, "FEATURED", urlToImage);
                if (featured != null) {
                    records.add(featured);
                    writeBytes(new File(tempImages, featured.fileName), featured.bytes);
                    thumbnailPath = featured.relativePath;
                }
            }
            if (thumbnailPath == null && article.thumbnailUrl != null) {
                DownloadedImage thumb = downloadAndStoreImage(article.thumbnailUrl, articleId,
                        tempImages, "THUMBNAIL", urlToImage);
                if (thumb != null) {
                    records.add(thumb);
                    writeBytes(new File(tempImages, thumb.fileName), thumb.bytes);
                    thumbnailPath = thumb.relativePath;
                }
            }
            if (thumbnailPath == null) {
                thumbnailPath = article.thumbnailPath;
            }

            String plainText = parsed.plainText;
            String contentHash = HashUtils.sha256(article.title + "\n" + plainText);
            long now = System.currentTimeMillis();
            ArchiveMetadata metadata = buildMetadata(article, fetched, plainText,
                    contentHash, now, records);
            List<ArticleVersionEntity> existingVersions = versionDao.getForArticle(articleId);
            boolean createVersion = existingVersions.isEmpty()
                    || !contentHash.equals(article.archivedContentHash);
            int versionNumber = 0;
            if (createVersion) {
                versionNumber = versionDao.maxVersionNumber(articleId) + 1;
            } else if (!existingVersions.isEmpty()) {
                versionNumber = existingVersions.get(0).versionNumber;
            }
            metadata.versionNumber = versionNumber;

            File tempContent = new File(temp, "content.html");
            File tempMetadata = new File(temp, "metadata.json");
            writeText(tempContent, parsed.html);
            writeText(tempMetadata, gson.toJson(metadata));

            File articleDir = articleDirectory(articleId);
            File finalImages = new File(articleDir, "images");
            File finalContent = new File(articleDir, "content.html");
            File finalMetadata = new File(articleDir, "metadata.json");
            File versionsDir = new File(articleDir, "versions");
            File versionDir = new File(versionsDir, "v" + versionNumber);
            ensureDirectory(finalImages);
            ensureDirectory(versionsDir);

            for (DownloadedImage image : records) {
                File source = new File(tempImages, image.fileName);
                File target = new File(finalImages, image.fileName);
                if (!target.exists()) {
                    moveFile(source, target);
                }
            }

            if (createVersion) {
                int version = versionNumber;
                if (versionDir.exists()) {
                    // Only an unrecorded orphan from an interrupted write can occupy this
                    // number; recorded versions always advance the max.
                    deleteRecursively(versionDir);
                }
                File tempVersion = new File(tempVersions, "v" + versionNumber);
                ensureDirectory(tempVersion);
                writeText(new File(tempVersion, "content.html"), parsed.html);
                writeText(new File(tempVersion, "metadata.json"), gson.toJson(metadata));
                ensureDirectory(versionDir.getParentFile());
                moveFile(tempVersion, versionDir);
            }

            moveReplacing(tempContent, finalContent);
            moveReplacing(tempMetadata, finalMetadata);

            if (!finalContent.isFile() || finalContent.length() == 0 || !finalMetadata.isFile()) {
                return ArchiveResult.failure("Archive files were not written completely");
            }

            final String relativeContentPath = "archive/articles/" + articleId + "/content.html";
            final String finalThumbnailPath = thumbnailPath;
            final int finalVersionNumber = versionNumber;
            final boolean finalCreateVersion = createVersion;
            final List<DownloadedImage> finalRecords = records;
            final ArchiveMetadata finalMetadataObject = metadata;
            final String finalPlainText = plainText;
            final ExtractionQuality finalQuality = fetched.quality;
            final String finalPlainTextForDb = plainText.length() > 200_000
                    ? plainText.substring(0, 200_000) : plainText;

            long[] versionIdHolder = new long[]{-1L};
            database.runInTransaction(() -> {
                if (article.author == null && fetched.author != null) {
                    article.author = fetched.author;
                }
                if (article.title == null || article.title.trim().isEmpty()) {
                    article.title = fetched.title;
                }
                if (article.publishedAt <= 0 && fetched.publishedAt > 0) {
                    article.publishedAt = fetched.publishedAt;
                }
                article.plainText = finalPlainTextForDb;
                articleDao.update(article);

                for (DownloadedImage record : finalRecords) {
                    if (imageDao.findByHash(articleId, record.sha256) == null) {
                        ArticleImageEntity image = new ArticleImageEntity();
                        image.articleId = articleId;
                        image.imageUrl = record.originalUrl;
                        image.localPath = record.relativePath;
                        image.imageType = record.type;
                        image.fileSize = record.bytes.length;
                        image.sha256 = record.sha256;
                        imageDao.insert(image);
                    }
                }

                Long latestVersionId = article.latestVersionId;
                if (finalCreateVersion) {
                    ArticleVersionEntity version = new ArticleVersionEntity();
                    version.articleId = articleId;
                    version.versionNumber = finalVersionNumber;
                    version.contentHash = contentHash;
                    version.contentPath = "archive/articles/" + articleId
                            + "/versions/v" + finalVersionNumber + "/content.html";
                    version.metadataPath = "archive/articles/" + articleId
                            + "/versions/v" + finalVersionNumber + "/metadata.json";
                    version.createdAt = now;
                    version.wordCount = finalMetadataObject.wordCount;
                    versionIdHolder[0] = versionDao.insert(version);
                    latestVersionId = versionIdHolder[0];
                }
                boolean manual = manualRequest || article.isManuallyArchived;
                articleDao.markArchived(articleId, manual, relativeContentPath, contentHash,
                        latestVersionId, finalQuality, finalPlainTextForDb, now,
                        finalThumbnailPath);
                rebuildFts(articleId);
            });

            ArchiveResult result = new ArchiveResult();
            result.success = true;
            result.contentPath = relativeContentPath;
            result.plainText = finalPlainTextForDb;
            result.quality = finalQuality;
            result.versionNumber = finalVersionNumber;
            result.imageCount = finalRecords.size();
            result.bytes = finalContent.length() + finalMetadata.length();
            return result;
        } catch (IOException | RuntimeException e) {
            Log.e(TAG, "Archive failed for article " + articleId, e);
            return ArchiveResult.failure("Archive write failed: " + safeMessage(e));
        } finally {
            deleteRecursively(temp);
        }
    }

    private FetchOutcome fetchContent(ArticleEntity article, @Nullable FeedEntity feed) {
        FetchOutcome outcome = new FetchOutcome();
        String url = article.originalUrl;
        if (url == null || !UrlUtils.isValidHttpUrl(url)) {
            url = UrlUtils.isValidHttpUrl(article.canonicalUrl) ? article.canonicalUrl : null;
        }
        if (url != null) {
            try {
                HttpResult response = httpFetcher.fetch(url, null, null,
                        MAX_ARTICLE_HTML_BYTES, settings.isAllowHttp(),
                        "text/html, application/xhtml+xml, text/plain;q=0.8");
                String type = response.contentType.toLowerCase(Locale.ROOT);
                if (type.contains("application/pdf") || type.contains("image/")) {
                    throw new IOException("Unsupported article content type");
                }
                ExtractionResult extraction = extractor.extract(response.bodyString(), url);
                if (extraction.isUsable()) {
                    outcome.success = true;
                    outcome.html = extraction.cleanHtml;
                    outcome.plainText = extraction.plainText;
                    outcome.featuredImageUrl = extraction.featuredImageUrl;
                    outcome.title = extraction.title;
                    outcome.author = extraction.author;
                    outcome.publishedAt = extraction.publishedAt;
                    outcome.quality = extraction.quality;
                    outcome.baseUrl = response.finalUrl == null ? url : response.finalUrl;
                    return outcome;
                }
                outcome.error = "Article extraction did not produce enough readable text";
            } catch (HttpException | IOException e) {
                outcome.error = e.getLocalizedMessage();
            }
        } else {
            outcome.error = "This article has no original web address";
        }

        if (article.isArchived) {
            outcome.success = false;
            outcome.error = "Could not refresh this article. The existing offline copy was preserved.";
            return outcome;
        }

        String fallbackHtml = article.description;
        if (fallbackHtml == null || fallbackHtml.trim().isEmpty()) {
            outcome.success = false;
            outcome.error = outcome.error == null ? "No article content is available" : outcome.error;
            return outcome;
        }
        String base = url != null ? url
                : feed != null && feed.websiteUrl != null ? feed.websiteUrl
                : article.canonicalUrl;
        String clean = extractor.sanitizeFragment(fallbackHtml, base);
        String plain = Jsoup.parse(clean).text();
        if (plain.trim().length() < 20) {
            outcome.success = false;
            outcome.error = "The feed summary is too short to archive";
            return outcome;
        }
        outcome.success = true;
        outcome.html = clean;
        outcome.plainText = plain;
        outcome.featuredImageUrl = article.thumbnailUrl;
        outcome.title = article.title;
        outcome.author = article.author;
        outcome.publishedAt = article.publishedAt;
        outcome.quality = ExtractionQuality.FALLBACK;
        outcome.baseUrl = base;
        return outcome;
    }

    private ParsedContent rewriteImages(String html, String baseUrl, long articleId,
                                       File tempImages) {
        ParsedContent parsed = new ParsedContent();
        Document body = Jsoup.parseBodyFragment(html, baseUrl);
        Set<String> seen = new HashSet<>();
        int count = 0;
        for (Element img : new ArrayList<>(body.select("img"))) {
            if (count >= MAX_IMAGES) {
                img.remove();
                continue;
            }
            String original = img.attr("src");
            String resolved = UrlUtils.resolve(baseUrl, original);
            if (resolved == null) {
                img.remove();
                continue;
            }
            DownloadedImage image = downloadImage(resolved, articleId);
            if (image == null) {
                img.remove();
                continue;
            }
            if (!seen.add(image.sha256)) {
                // Duplicate image within the same article: reuse the first stored file.
                for (DownloadedImage existing : parsed.images) {
                    if (existing.sha256.equals(image.sha256)) {
                        image = existing;
                        break;
                    }
                }
            } else {
                parsed.images.add(image);
                count++;
            }
            img.attr("src", image.relativePath);
            img.attr("data-original-src", resolved);
            img.attr("alt", img.attr("alt"));
        }
        parsed.html = body.html();
        parsed.plainText = body.text().replaceAll("\\s+", " ").trim();
        return parsed;
    }

    @Nullable
    private DownloadedImage downloadAndStoreImage(String url, long articleId, File tempImages,
                                                  String type,
                                                  Map<String, String> existingByUrl) {
        if (url == null) {
            return null;
        }
        String existing = existingByUrl.get(url);
        if (existing != null) {
            DownloadedImage image = new DownloadedImage();
            image.originalUrl = url;
            image.relativePath = existing;
            image.type = type;
            File file = new File(tempImages, new File(existing).getName());
            try {
                byte[] bytes = readFileLimited(file, MAX_FILE_READ_BYTES);
                image.bytes = bytes;
                image.sha256 = HashUtils.sha256(bytes);
                image.fileName = file.getName();
                return image;
            } catch (IOException e) {
                return null;
            }
        }
        DownloadedImage downloaded = downloadImage(url, articleId);
        if (downloaded != null) {
            downloaded.type = type;
            existingByUrl.put(url, downloaded.relativePath);
        }
        return downloaded;
    }

    @Nullable
    private DownloadedImage downloadImage(String url, long articleId) {
        if (!UrlUtils.isValidHttpUrl(url)) {
            return null;
        }
        long maxBytes = settings.getImageMaxBytes();
        try {
            HttpResult response = httpFetcher.fetch(url, null, null, (int) Math.min(Integer.MAX_VALUE, maxBytes),
                    settings.isAllowHttp(), "image/png, image/jpeg, image/webp, image/gif, image/*;q=0.8");
            if (!response.isSuccessful() || response.body.length < 16) {
                return null;
            }
            String detected = ImageUtils.detectMime(response.body);
            if (!ImageUtils.isSupportedImageMime(detected)) {
                return null;
            }
            ProcessedImage processed = compressImage(response.body, detected,
                    settings.getImageQuality());
            if (processed.bytes.length > maxBytes) {
                return null;
            }
            DownloadedImage image = new DownloadedImage();
            image.originalUrl = url;
            image.sha256 = HashUtils.sha256(processed.bytes);
            image.fileName = "img_" + image.sha256.substring(0, 12) + "."
                    + ImageUtils.extensionForMime(processed.mime);
            image.relativePath = "archive/articles/" + articleId + "/images/" + image.fileName;
            image.bytes = processed.bytes;
            image.type = "INLINE";
            return image;
        } catch (HttpException e) {
            Log.w(TAG, "Image download failed: " + url + " (" + e.safeMessage + ")");
            return null;
        }
    }

    private ProcessedImage compressImage(byte[] original, String mime, int quality) {
        ProcessedImage result = new ProcessedImage();
        result.bytes = original;
        result.mime = mime;
        if (quality >= 100 || "image/gif".equals(mime) || original.length < 120_000) {
            return result;
        }
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(original, 0, original.length, bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                return result;
            }
            BitmapFactory.Options options = new BitmapFactory.Options();
            int largest = Math.max(bounds.outWidth, bounds.outHeight);
            int sample = 1;
            while (largest / sample > 1800) {
                sample *= 2;
            }
            options.inSampleSize = sample;
            Bitmap bitmap = BitmapFactory.decodeByteArray(original, 0, original.length, options);
            if (bitmap == null) {
                return result;
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Bitmap.CompressFormat format = android.os.Build.VERSION.SDK_INT >= 30
                    ? Bitmap.CompressFormat.WEBP_LOSSY : Bitmap.CompressFormat.WEBP;
            boolean compressed = bitmap.compress(format, quality, out);
            bitmap.recycle();
            if (compressed && out.size() > 0 && out.size() < original.length) {
                result.bytes = out.toByteArray();
                result.mime = "image/webp";
            }
        } catch (OutOfMemoryError | RuntimeException e) {
            Log.w(TAG, "Image compression skipped", e);
        }
        return result;
    }

    public DownloadResult downloadAudio(long articleId) {
        DownloadResult result = new DownloadResult();
        ArticleEntity article = articleDao.getById(articleId);
        if (article == null || article.audioUrl == null
                || !UrlUtils.isValidHttpUrl(article.audioUrl)) {
            result.error = "This article has no downloadable audio";
            return result;
        }
        try {
            HttpResult response = httpFetcher.fetch(article.audioUrl, null, null,
                    (int) Math.min(Integer.MAX_VALUE, settings.getAudioMaxBytes()),
                    settings.isAllowHttp(), "audio/*, application/ogg;q=0.8");
            if (!response.isSuccessful() || response.body.length < 100) {
                result.error = "Audio download failed";
                return result;
            }
            String type = response.contentType.toLowerCase(Locale.ROOT);
            if (!type.startsWith("audio/") && !type.contains("ogg") && !type.contains("mpeg")) {
                result.error = "The enclosure is not a supported audio file";
                return result;
            }
            File articleDir = articleDirectory(articleId);
            File audioDir = new File(articleDir, "audio");
            ensureDirectory(audioDir);
            String extension = audioExtension(type, article.audioUrl);
            String name = "episode_" + HashUtils.sha256(article.audioUrl).substring(0, 10)
                    + "." + extension;
            File target = new File(audioDir, name);
            File temp = new File(audioDir, name + ".tmp");
            writeBytes(temp, response.body);
            moveReplacing(temp, target);
            String relative = "archive/articles/" + articleId + "/audio/" + name;
            articleDao.setAudioPath(articleId, relative);
            result.success = true;
            result.path = relative;
            result.bytes = response.body.length;
            return result;
        } catch (HttpException | IOException e) {
            result.error = e.getMessage();
            return result;
        }
    }

    private static String audioExtension(String contentType, String url) {
        if (contentType.contains("mpeg") || url.toLowerCase(Locale.ROOT).contains(".mp3")) {
            return "mp3";
        }
        if (contentType.contains("ogg") || url.toLowerCase(Locale.ROOT).contains(".ogg")) {
            return "ogg";
        }
        if (contentType.contains("mp4") || contentType.contains("m4a")
                || url.toLowerCase(Locale.ROOT).contains(".m4a")) {
            return "m4a";
        }
        if (contentType.contains("wav")) {
            return "wav";
        }
        return "audio";
    }

    public void deleteCachedContent(long articleId) {
        deleteCachedContentInternal(articleId);
    }

    private void deleteCachedContentInternal(long articleId) {
        deleteRecursively(articleDirectory(articleId));
        database.runInTransaction(() -> {
            imageDao.deleteForArticle(articleId);
            versionDao.deleteForArticle(articleId);
            downloadDao.deleteForArticle(articleId);
            articleDao.clearArchive(articleId);
            rebuildFts(articleId);
        });
    }

    public CleanupResult cleanupIfNeeded() {
        CleanupResult cleanup = new CleanupResult();
        long limit = settings.getArchiveMaxBytes();
        StorageInfo before = getStorageInfo();
        if (before.totalBytes <= limit) {
            cleanStaleTemporaryFiles();
            return cleanup;
        }
        long total = before.totalBytes;
        List<ArticleEntity> candidates = articleDao.getCleanupCandidates();
        for (ArticleEntity candidate : candidates) {
            if (total <= limit) {
                break;
            }
            File directory = articleDirectory(candidate.id);
            long size = directorySize(directory);
            deleteCachedContentInternal(candidate.id);
            total -= size;
            cleanup.bytesFreed += size;
            cleanup.articlesRemoved++;
        }
        cleanStaleTemporaryFiles();
        downloadDao.pruneCompleted(System.currentTimeMillis() - 30L * 86_400_000L);
        return cleanup;
    }

    public StorageInfo getStorageInfo() {
        StorageInfo info = new StorageInfo();
        walkStorage(archiveRoot, info);
        File icons = new File(filesDir, "icons");
        info.iconBytes = directorySize(icons);
        info.totalBytes += info.iconBytes;
        info.archivedArticles = articleDao.countArchived();
        info.freeDeviceBytes = filesDir.getUsableSpace();
        return info;
    }

    private void walkStorage(File file, StorageInfo info) {
        if (file == null || !file.exists()) {
            return;
        }
        if (file.isFile()) {
            long length = file.length();
            info.totalBytes += length;
            String path = file.getPath().toLowerCase(Locale.ROOT);
            if (path.endsWith(".html")) {
                info.htmlBytes += length;
            } else if (path.contains(File.separator + "images" + File.separator)) {
                info.imageBytes += length;
            } else if (path.contains(File.separator + "audio" + File.separator)
                    || path.endsWith(".mp3") || path.endsWith(".m4a")
                    || path.endsWith(".ogg") || path.endsWith(".wav")) {
                info.audioBytes += length;
            } else {
                info.otherBytes += length;
            }
            return;
        }
        File[] children = file.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            walkStorage(child, info);
        }
    }

    @Nullable
    public String readArchivedHtml(ArticleEntity article) {
        if (article == null || article.contentPath == null) {
            return null;
        }
        File file = resolveStoragePath(article.contentPath);
        if (file == null || !file.isFile()) {
            return null;
        }
        try {
            return new String(readFileLimited(file, MAX_FILE_READ_BYTES), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    @Nullable
    public String readVersionHtml(ArticleVersionEntity version) {
        if (version == null) {
            return null;
        }
        File file = resolveStoragePath(version.contentPath);
        if (file == null || !file.isFile()) {
            return null;
        }
        try {
            return new String(readFileLimited(file, MAX_FILE_READ_BYTES), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    @Nullable
    public File resolveStoragePath(@Nullable String relativePath) {
        if (relativePath == null || relativePath.trim().isEmpty()) {
            return null;
        }
        String normalized = relativePath.replace('\\', '/');
        if (normalized.startsWith("/") || normalized.contains("../")
                || normalized.contains("/..")) {
            return null;
        }
        try {
            File file = new File(filesDir, normalized);
            String root = filesDir.getCanonicalPath();
            String target = file.getCanonicalPath();
            if (!target.startsWith(root + File.separator) && !target.equals(root)) {
                return null;
            }
            return file;
        } catch (IOException e) {
            return null;
        }
    }

    public void cleanStaleTemporaryFiles() {
        File tempRoot = new File(archiveRoot, ".tmp");
        File[] children = tempRoot.listFiles();
        if (children == null) {
            return;
        }
        long cutoff = System.currentTimeMillis() - 24L * 3_600_000L;
        for (File child : children) {
            if (child.lastModified() < cutoff) {
                deleteRecursively(child);
            }
        }
    }

    private ArchiveMetadata buildMetadata(ArticleEntity article, FetchOutcome fetched,
                                          String plainText, String contentHash, long now,
                                          List<DownloadedImage> images) {
        ArchiveMetadata metadata = new ArchiveMetadata();
        metadata.articleId = article.id;
        metadata.title = article.title;
        metadata.author = article.author != null ? article.author : fetched.author;
        metadata.publisher = article.publisher;
        metadata.originalUrl = article.originalUrl;
        metadata.canonicalUrl = article.canonicalUrl;
        metadata.publishedAt = article.publishedAt > 0 ? article.publishedAt : fetched.publishedAt;
        metadata.firstSeenAt = article.firstSeenAt;
        metadata.archivedAt = now;
        metadata.contentHash = contentHash;
        metadata.extractionQuality = fetched.quality.name();
        metadata.videoUrl = article.videoUrl;
        metadata.audioUrl = article.audioUrl;
        metadata.wordCount = plainText.isEmpty() ? 0 : plainText.split("\\s+").length;
        for (DownloadedImage image : images) {
            metadata.imageUrls.put(image.originalUrl, image.relativePath);
        }
        return metadata;
    }

    private void rebuildFts(long articleId) {
        ArticleEntity article = articleDao.getById(articleId);
        if (article == null) {
            ftsDao.remove(articleId);
            return;
        }
        List<ArticleTagEntity> tags = tagDao.getForArticle(articleId);
        List<String> tagValues = new ArrayList<>();
        for (ArticleTagEntity tag : tags) {
            tagValues.add(tag.tag);
        }
        ArticleNoteEntity note = noteDao.getForArticle(articleId);
        ArticleFtsEntity entry = new ArticleFtsEntity();
        entry.rowId = articleId;
        entry.title = article.title;
        entry.description = TextUtils.stripHtmlOrEmpty(article.description);
        entry.bodyText = article.plainText == null ? ""
                : article.plainText.length() > 40_000
                ? article.plainText.substring(0, 40_000) : article.plainText;
        entry.publisher = article.publisher == null ? "" : article.publisher;
        entry.author = article.author == null ? "" : article.author;
        entry.tags = String.join(" ", tagValues);
        entry.notes = note == null ? ""
                : note.note.length() > 10_000 ? note.note.substring(0, 10_000) : note.note;
        ftsDao.remove(articleId);
        ftsDao.index(entry);
    }

    public File articleDirectory(long articleId) {
        return new File(archiveRoot, "articles/" + articleId);
    }

    private static void writeText(File file, String text) throws IOException {
        writeBytes(file, text.getBytes(StandardCharsets.UTF_8));
    }

    private static void writeBytes(File file, byte[] bytes) throws IOException {
        ensureDirectory(file.getParentFile());
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(bytes);
            out.flush();
            out.getFD().sync();
        }
    }

    private static byte[] readFileLimited(File file, int max) throws IOException {
        if (!file.isFile() || file.length() > max) {
            throw new IOException("File is missing or exceeds the read limit");
        }
        try (FileInputStream in = new FileInputStream(file)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream((int) file.length());
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        }
    }

    private static void moveReplacing(File source, File target) throws IOException {
        ensureDirectory(target.getParentFile());
        if (target.exists() && !target.delete()) {
            throw new IOException("Could not replace " + target.getName());
        }
        moveFile(source, target);
    }

    private static void moveFile(File source, File target) throws IOException {
        ensureDirectory(target.getParentFile());
        if (target.exists()) {
            if (!target.delete()) {
                throw new IOException("Could not remove existing target");
            }
        }
        if (source.renameTo(target)) {
            fsyncFile(target);
            return;
        }
        // Cross-volume fallback; copy + fsync then remove the source.
        try (FileInputStream in = new FileInputStream(source);
             FileOutputStream out = new FileOutputStream(target)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            out.flush();
            out.getFD().sync();
        }
        if (!source.delete()) {
            Log.w(TAG, "Could not delete temporary file " + source.getPath());
        }
    }

    private static void fsyncFile(File file) {
        try (FileInputStream in = new FileInputStream(file)) {
            in.getFD().sync();
        } catch (IOException ignored) {
            // rename is atomic; fsync is best-effort for the directory entry on Android.
        }
    }

    private static void ensureDirectory(@Nullable File directory) throws IOException {
        if (directory == null) {
            return;
        }
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("Could not create directory " + directory.getName());
        }
        if (!directory.isDirectory()) {
            throw new IOException(directory.getName() + " is not a directory");
        }
    }

    public static void deleteRecursively(@Nullable File file) {
        if (file == null || !file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        if (!file.delete()) {
            Log.w(TAG, "Could not delete " + file.getPath());
        }
    }

    public static long directorySize(@Nullable File file) {
        if (file == null || !file.exists()) {
            return 0;
        }
        if (file.isFile()) {
            return file.length();
        }
        long size = 0;
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                size += directorySize(child);
            }
        }
        return size;
    }

    private static String safeMessage(Throwable throwable) {
        String message = throwable.getMessage();
        return message == null || message.trim().isEmpty()
                ? "Unknown file error" : message;
    }

    private static final class FetchOutcome {
        boolean success;
        String html;
        String plainText;
        String featuredImageUrl;
        String title;
        String author;
        long publishedAt;
        String baseUrl;
        ExtractionQuality quality = ExtractionQuality.NONE;
        String error;
    }

    private static final class ParsedContent {
        String html = "";
        String plainText = "";
        final List<DownloadedImage> images = new ArrayList<>();
    }

    private static final class ProcessedImage {
        byte[] bytes;
        String mime;
    }

    private static final class DownloadedImage {
        String originalUrl;
        String relativePath;
        String fileName;
        String sha256;
        byte[] bytes;
        String type = "INLINE";
    }
}
