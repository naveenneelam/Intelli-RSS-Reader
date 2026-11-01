package com.intellirs.reader.data.network;

import android.content.Context;

import androidx.annotation.Nullable;

import com.intellirs.reader.data.dao.FeedDao;
import com.intellirs.reader.data.entity.FeedEntity;
import com.intellirs.reader.data.repository.SettingsRepository;
import com.intellirs.reader.util.ImageUtils;
import com.intellirs.reader.util.UrlUtils;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Downloads publisher icons into app-private storage. It never relies on a third-party
 * favicon service and uses fallback generated icons when a publisher has no usable icon.
 */
public class FaviconFetcher {
    private static final int MAX_HTML = 512 * 1024;
    private static final int MAX_ICON = 768 * 1024;
    private final Context context;
    private final HttpFetcher httpFetcher;
    private final FeedDao feedDao;
    private final SettingsRepository settings;

    public FaviconFetcher(Context context, HttpFetcher httpFetcher, FeedDao feedDao,
                          SettingsRepository settings) {
        this.context = context.getApplicationContext();
        this.httpFetcher = httpFetcher;
        this.feedDao = feedDao;
        this.settings = settings;
    }

    /** Returns a relative favicon path, or null. */
    @Nullable
    public String fetchAndStore(FeedEntity feed) {
        if (feed == null) {
            return null;
        }
        String page = firstNonEmpty(feed.websiteUrl, feed.feedUrl);
        if (page == null) {
            return null;
        }
        List<String> candidates = new ArrayList<>();
        try {
            HttpResult response = httpFetcher.fetch(page, null, null, MAX_HTML,
                    settings.isAllowHttp());
            if (response.isSuccessful()) {
                Document document = Jsoup.parse(response.bodyString(), page);
                for (Element link : document.select("link[href]")) {
                    String rel = link.attr("rel").toLowerCase(Locale.ROOT);
                    if (rel.contains("icon") || rel.contains("apple-touch-icon")) {
                        String resolved = UrlUtils.resolve(page, link.attr("href"));
                        if (resolved != null && !candidates.contains(resolved)) {
                            candidates.add(resolved);
                        }
                    }
                }
            }
        } catch (HttpException ignored) {
            // Fall through to conventional favicon paths.
        }
        String origin = UrlUtils.origin(page);
        candidates.add(origin + "/favicon.ico");
        candidates.add(origin + "/apple-touch-icon.png");

        for (String iconUrl : candidates) {
            try {
                HttpResult response = httpFetcher.fetch(iconUrl, null, null, MAX_ICON,
                        settings.isAllowHttp(), "image/png, image/jpeg, image/webp, image/gif, image/*;q=0.8");
                if (!response.isSuccessful() || response.body.length < 20) {
                    continue;
                }
                String mime = ImageUtils.detectMime(response.body);
                if (!ImageUtils.isSupportedImageMime(mime)) {
                    continue;
                }
                String extension = ImageUtils.extensionForMime(mime);
                File directory = new File(context.getFilesDir(), "icons");
                if (!directory.exists() && !directory.mkdirs()) {
                    return null;
                }
                File temp = new File(directory, "feed_" + feed.id + ".tmp");
                File target = new File(directory, "feed_" + feed.id + "." + extension);
                try (FileOutputStream out = new FileOutputStream(temp)) {
                    out.write(response.body);
                    out.flush();
                    out.getFD().sync();
                }
                if (target.exists() && !target.delete()) {
                    temp.delete();
                    return null;
                }
                if (!temp.renameTo(target)) {
                    temp.delete();
                    return null;
                }
                String relative = "icons/" + target.getName();
                feedDao.updateFaviconPath(feed.id, relative);
                return relative;
            } catch (HttpException | IOException ignored) {
                // Try the next conventional icon candidate.
            }
        }
        return null;
    }

    @Nullable
    private static String firstNonEmpty(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return null;
    }
}
