package com.intellirs.reader;

import android.app.Application;

import com.intellirs.reader.di.AppContainer;
import com.intellirs.reader.service.NotificationChannelManager;
import com.intellirs.reader.util.ThemeManager;

/** Application entry point and process-wide dependency container owner. */
public class IntelliRssApp extends Application {
    private AppContainer container;

    @Override
    public void onCreate() {
        super.onCreate();
        ThemeManager.applyNightMode(this);
        container = new AppContainer(this);
        NotificationChannelManager.createChannels(this);
        container.ioExecutor().execute(() -> {
            container.workScheduler().ensurePeriodicWork();
            container.workScheduler().ensureDailySync();
            container.workScheduler().ensureArchiveCleanup();
        });
    }

    public AppContainer getContainer() {
        return container;
    }
}
