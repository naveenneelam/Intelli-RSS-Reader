package com.intellirs.reader.ui.common;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import com.google.gson.Gson;
import com.intellirs.reader.data.entity.FeedEntity;
import com.intellirs.reader.data.opml.OpmlFeed;
import com.intellirs.reader.di.AppContainer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Parses an OPML document off the main thread and prepares the preview Activity. */
public final class OpmlImportFlow {
    public interface Callback {
        void onReady(File previewFile);

        void onError(String message);
    }

    private OpmlImportFlow() {
    }

    public static void prepare(Context context, AppContainer container, Uri uri,
                               Callback callback) {
        Context appContext = context.getApplicationContext();
        Handler handler = new Handler(Looper.getMainLooper());
        container.ioExecutor().execute(() -> {
            try {
                ContentResolver resolver = appContext.getContentResolver();
                List<OpmlFeed> feeds;
                try (InputStream input = resolver.openInputStream(uri)) {
                    if (input == null) {
                        throw new IllegalStateException("OPML file could not be opened");
                    }
                    feeds = container.opmlManager().importFrom(input);
                }
                for (OpmlFeed feed : feeds) {
                    FeedEntity existing = container.feedRepository().getFeedByUrl(feed.feedUrl);
                    feed.duplicate = existing != null && existing.isActive;
                }
                File preview = new File(appContext.getCacheDir(),
                        "opml_preview_" + System.currentTimeMillis() + ".json");
                String json = new Gson().toJson(feeds);
                try (FileOutputStream out = new FileOutputStream(preview)) {
                    out.write(json.getBytes(StandardCharsets.UTF_8));
                    out.flush();
                    out.getFD().sync();
                }
                handler.post(() -> callback.onReady(preview));
            } catch (Exception e) {
                String message = e.getMessage() == null ? "Unknown OPML import error"
                        : e.getMessage();
                handler.post(() -> callback.onError(message));
            }
        });
    }
}
