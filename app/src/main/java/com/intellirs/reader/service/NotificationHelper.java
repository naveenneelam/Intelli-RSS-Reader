package com.intellirs.reader.service;

import android.Manifest;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.text.TextUtils;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.intellirs.reader.R;
import com.intellirs.reader.data.entity.ArticleEntity;
import com.intellirs.reader.data.entity.FeedEntity;
import com.intellirs.reader.receiver.NotificationActionReceiver;
import com.intellirs.reader.ui.MainActivity;
import com.intellirs.reader.ui.reader.ReaderActivity;
import com.intellirs.reader.ui.reader.ReaderContract;
import com.intellirs.reader.domain.model.ArticleFilter;
import com.intellirs.reader.domain.notification.NotificationChannels;
import com.intellirs.reader.domain.notification.NotificationDecision;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Builds article notifications, group summaries and all action PendingIntents. It is only
 * invoked by the notification coordinator after synchronization and eligibility checks.
 */
public class NotificationHelper {
    private final Context context;
    private final NotificationManagerCompat manager;
    private final Map<String, GroupSummary> groups = new LinkedHashMap<>();

    public NotificationHelper(Context context) {
        this.context = context.getApplicationContext();
        this.manager = NotificationManagerCompat.from(context);
    }

    public boolean canPostNotifications() {
        if (Build.VERSION.SDK_INT >= 33
                && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        return manager.areNotificationsEnabled();
    }

    public boolean postArticle(ArticleEntity article, FeedEntity feed, String groupLabel,
                               String channelId) {
        if (!canPostNotifications()) {
            return false;
        }
        try {
            int id = notificationId(article.id);
            PendingIntent open = openArticle(article, feed);
            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId)
                    .setSmallIcon(R.drawable.ic_rss)
                    .setContentTitle(article.title == null ? context.getString(R.string.untitled)
                            : article.title)
                    .setContentText(summary(article))
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(summary(article)))
                    .setContentIntent(open)
                    .setAutoCancel(true)
                    .setOnlyAlertOnce(true)
                    .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
                    .setColor(ContextCompat.getColor(context, R.color.primary))
                    .setWhen(article.publishedAt > 0 ? article.publishedAt
                            : System.currentTimeMillis())
                    .setShowWhen(true);

            if (feed != null && feed.title != null && !feed.title.isEmpty()) {
                builder.setSubText(feed.title);
            }
            builder.addAction(R.drawable.ic_check, context.getString(R.string.notification_mark_read),
                    action(article, NotificationActionReceiver.ACTION_MARK_READ, 10));
            builder.addAction(R.drawable.ic_bookmark_border,
                    context.getString(R.string.notification_bookmark),
                    action(article, NotificationActionReceiver.ACTION_BOOKMARK, 20));
            builder.addAction(R.drawable.ic_open_in_new,
                    context.getString(R.string.notification_open), open);

            if (feed != null) {
                Intent mute = baseActionIntent(article, NotificationActionReceiver.ACTION_MUTE_FEED);
                mute.putExtra(NotificationActionReceiver.EXTRA_FEED_ID, feed.id);
                PendingIntent muteIntent = PendingIntent.getBroadcast(context,
                        requestCode(article.id, 40), mute,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                builder.addAction(R.drawable.ic_notifications_off,
                        context.getString(R.string.notification_mute_feed), muteIntent);
            }
            builder.addAction(R.drawable.ic_close,
                    context.getString(R.string.notification_disable),
                    action(article, NotificationActionReceiver.ACTION_DISABLE_ALL, 50));

            if (groupLabel != null && !groupLabel.trim().isEmpty()) {
                String groupKey = "intellirss:" + groupLabel.toLowerCase(Locale.ROOT)
                        .hashCode();
                builder.setGroup(groupKey);
                builder.setGroupSummary(false);
                GroupSummary summary = groups.computeIfAbsent(groupKey,
                        key -> new GroupSummary(groupKey, groupLabel, channelId));
                summary.add(article.title, channelId);
            }

            manager.notify(id, builder.build());
            return true;
        } catch (SecurityException e) {
            return false;
        }
    }

    public void finishGroups() {
        for (GroupSummary summary : groups.values()) {
            if (summary.count <= 1 || !canPostNotifications()) {
                continue;
            }
            try {
                int id = (summary.groupKey.hashCode() & 0x7fffffff);
                Intent open = new Intent(context, MainActivity.class);
                PendingIntent openIntent = PendingIntent.getActivity(context, id, open,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                NotificationCompat.InboxStyle style = new NotificationCompat.InboxStyle()
                        .setBigContentTitle(context.getString(
                                R.string.notification_summary_title, summary.count));
                for (String line : summary.lines) {
                    style.addLine(line);
                }
                NotificationCompat.Builder builder = new NotificationCompat.Builder(
                        context, notificationChannel(summary.highestChannel))
                        .setSmallIcon(R.drawable.ic_rss)
                        .setContentTitle(summary.label + " \u2022 " + summary.count)
                        .setContentText(context.getString(
                                R.string.notification_summary_title, summary.count))
                        .setStyle(style)
                        .setGroup(summary.groupKey)
                        .setGroupSummary(true)
                        .setAutoCancel(true)
                        .setContentIntent(openIntent)
                        .setColor(ContextCompat.getColor(context, R.color.primary))
                        .addAction(R.drawable.ic_close,
                                context.getString(R.string.notification_disable),
                                globalDisableIntent());
                manager.notify(id, builder.build());
            } catch (SecurityException ignored) {
                // Permission was revoked while notifications were being generated.
            }
        }
        groups.clear();
    }

    public boolean postTestNotification() {
        if (!canPostNotifications()) {
            return false;
        }
        try {
            Intent open = new Intent(context, MainActivity.class);
            PendingIntent pendingIntent = PendingIntent.getActivity(context, 9001, open,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            NotificationCompat.Builder builder = new NotificationCompat.Builder(
                    context, NotificationChannels.GENERAL)
                    .setSmallIcon(R.drawable.ic_rss)
                    .setContentTitle(context.getString(R.string.notification_test_title))
                    .setContentText(context.getString(R.string.notification_test_body))
                    .setStyle(new NotificationCompat.BigTextStyle()
                            .bigText(context.getString(R.string.notification_test_body)))
                    .setAutoCancel(true)
                    .setContentIntent(pendingIntent)
                    .setColor(ContextCompat.getColor(context, R.color.primary));
            manager.notify(9001, builder.build());
            return true;
        } catch (SecurityException e) {
            return false;
        }
    }

    public static void cancel(Context context, long articleId) {
        NotificationManagerCompat.from(context).cancel(notificationId(articleId));
    }

    public static int notificationId(long articleId) {
        return (int) (articleId % Integer.MAX_VALUE);
    }

    private PendingIntent openArticle(ArticleEntity article, FeedEntity feed) {
        Intent intent = new Intent(context, ReaderActivity.class);
        intent.putExtra(ReaderContract.EXTRA_ARTICLE_ID, article.id);
        ArticleFilter filter = feed != null ? ArticleFilter.feed(feed.id) : ArticleFilter.all();
        intent.putExtra(ReaderContract.EXTRA_FILTER, filter);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(context, requestCode(article.id, 1), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private PendingIntent action(ArticleEntity article, String action, int offset) {
        Intent intent = baseActionIntent(article, action);
        return PendingIntent.getBroadcast(context, requestCode(article.id, offset), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private Intent baseActionIntent(ArticleEntity article, String action) {
        Intent intent = new Intent(context, NotificationActionReceiver.class);
        intent.setAction(action);
        intent.putExtra(NotificationActionReceiver.EXTRA_ARTICLE_ID, article.id);
        if (article.primaryFeedId != null) {
            intent.putExtra(NotificationActionReceiver.EXTRA_FEED_ID,
                    (long) article.primaryFeedId);
        }
        return intent;
    }

    private PendingIntent globalDisableIntent() {
        Intent intent = new Intent(context, NotificationActionReceiver.class);
        intent.setAction(NotificationActionReceiver.ACTION_DISABLE_ALL);
        return PendingIntent.getBroadcast(context, 9002, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private String summary(ArticleEntity article) {
        String text = null;
        if (article.description != null) {
            text = org.jsoup.Jsoup.parse(article.description).text();
        }
        if (TextUtils.isEmpty(text) && article.plainText != null) {
            text = article.plainText;
        }
        if (TextUtils.isEmpty(text)) {
            text = article.publisher == null ? "" : article.publisher;
        }
        if (text.length() > 240) {
            text = text.substring(0, 240).trim() + "\u2026";
        }
        return text;
    }

    private static String notificationChannel(String current) {
        if (NotificationChannels.CRITICAL.equals(current)) {
            return NotificationChannels.CRITICAL;
        }
        if (NotificationChannels.HIGH.equals(current)) {
            return NotificationChannels.HIGH;
        }
        return NotificationChannels.GENERAL;
    }

    private static int requestCode(long articleId, int offset) {
        return (int) ((articleId * 31 + offset) & 0x7fffffff);
    }

    private static final class GroupSummary {
        final String groupKey;
        final String label;
        final List<String> lines = new ArrayList<>();
        int count;
        String highestChannel;

        GroupSummary(String groupKey, String label, String channel) {
            this.groupKey = groupKey;
            this.label = label;
            this.highestChannel = channel;
        }

        void add(String title, String channel) {
            count++;
            if (lines.size() < 7 && title != null) {
                lines.add(title);
            }
            if (NotificationChannels.CRITICAL.equals(channel)) {
                highestChannel = channel;
            } else if (NotificationChannels.HIGH.equals(channel)
                    && !NotificationChannels.CRITICAL.equals(highestChannel)) {
                highestChannel = channel;
            }
        }
    }
}
