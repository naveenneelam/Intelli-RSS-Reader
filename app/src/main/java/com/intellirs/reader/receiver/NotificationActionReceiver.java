package com.intellirs.reader.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import com.intellirs.reader.IntelliRssApp;
import com.intellirs.reader.R;
import com.intellirs.reader.di.AppContainer;
import com.intellirs.reader.service.NotificationHelper;

/**
 * Handles notification actions off the main thread. No parser or network work is started
 * here; these are local database updates only.
 */
public class NotificationActionReceiver extends BroadcastReceiver {
    public static final String ACTION_MARK_READ = "com.intellirs.reader.action.MARK_READ";
    public static final String ACTION_BOOKMARK = "com.intellirs.reader.action.BOOKMARK";
    public static final String ACTION_MUTE_FEED = "com.intellirs.reader.action.MUTE_FEED";
    public static final String ACTION_DISABLE_ALL = "com.intellirs.reader.action.DISABLE_ALL";
    public static final String EXTRA_ARTICLE_ID = "article_id";
    public static final String EXTRA_FEED_ID = "feed_id";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) {
            return;
        }
        String action = intent.getAction();
        long articleId = intent.getLongExtra(EXTRA_ARTICLE_ID, -1L);
        long feedId = intent.getLongExtra(EXTRA_FEED_ID, -1L);
        PendingResult pendingResult = goAsync();
        AppContainer container = ((IntelliRssApp) context.getApplicationContext()).getContainer();
        container.ioExecutor().execute(() -> {
            try {
                boolean handled = true;
                switch (action) {
                    case ACTION_MARK_READ:
                        if (articleId > 0) {
                            container.articleRepository().setRead(articleId, true);
                            NotificationHelper.cancel(context, articleId);
                        }
                        break;
                    case ACTION_BOOKMARK:
                        if (articleId > 0) {
                            container.articleRepository().toggleBookmark(articleId);
                        }
                        break;
                    case ACTION_MUTE_FEED:
                        if (feedId > 0) {
                            container.feedRepository().setNotificationsEnabled(feedId, false);
                        }
                        break;
                    case ACTION_DISABLE_ALL:
                        container.settingsRepository().setNotificationsEnabled(false);
                        break;
                    default:
                        handled = false;
                        break;
                }
                if (handled) {
                    String message = action.equals(ACTION_MUTE_FEED)
                            ? context.getString(R.string.notification_muted_feed)
                            : action.equals(ACTION_DISABLE_ALL)
                            ? context.getString(R.string.notification_disabled_global)
                            : action.equals(ACTION_BOOKMARK)
                            ? context.getString(R.string.notification_bookmark_added)
                            : context.getString(R.string.notification_read);
                    new Handler(Looper.getMainLooper()).post(() ->
                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show());
                }
            } catch (RuntimeException e) {
                new Handler(Looper.getMainLooper()).post(() ->
                        Toast.makeText(context, R.string.notification_actions_failed,
                                Toast.LENGTH_SHORT).show());
            } finally {
                pendingResult.finish();
            }
        });
    }
}
