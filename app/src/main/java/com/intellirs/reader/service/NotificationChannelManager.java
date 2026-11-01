package com.intellirs.reader.service;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

import androidx.core.app.NotificationManagerCompat;

import com.intellirs.reader.R;
import com.intellirs.reader.domain.notification.NotificationChannels;

/** Creates the five immutable user-visible notification channels. */
public final class NotificationChannelManager {
    private NotificationChannelManager() {
    }

    public static void createChannels(Context context) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) {
            return;
        }
        create(manager, NotificationChannels.CRITICAL,
                context.getString(R.string.channel_critical_name),
                context.getString(R.string.channel_critical_desc),
                NotificationManager.IMPORTANCE_HIGH);
        create(manager, NotificationChannels.HIGH,
                context.getString(R.string.channel_high_name),
                context.getString(R.string.channel_high_desc),
                NotificationManager.IMPORTANCE_DEFAULT);
        create(manager, NotificationChannels.GENERAL,
                context.getString(R.string.channel_general_name),
                context.getString(R.string.channel_general_desc),
                NotificationManager.IMPORTANCE_LOW);
        create(manager, NotificationChannels.SYNC,
                context.getString(R.string.channel_sync_name),
                context.getString(R.string.channel_sync_desc),
                NotificationManager.IMPORTANCE_MIN);
        create(manager, NotificationChannels.DOWNLOAD,
                context.getString(R.string.channel_download_name),
                context.getString(R.string.channel_download_desc),
                NotificationManager.IMPORTANCE_LOW);
        // Notifications can be turned off globally in Android settings; respect that.
        NotificationManagerCompat.from(context).areNotificationsEnabled();
    }

    private static void create(NotificationManager manager, String id, String name,
                               String description, int importance) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(id, name, importance);
            channel.setDescription(description);
            channel.setShowBadge(importance >= NotificationManager.IMPORTANCE_DEFAULT);
            manager.createNotificationChannel(channel);
        }
    }
}
