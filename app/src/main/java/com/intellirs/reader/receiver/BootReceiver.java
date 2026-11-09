package com.intellirs.reader.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.intellirs.reader.IntelliRssApp;
import com.intellirs.reader.di.AppContainer;

/**
 * WorkManager persists periodic work itself; this receiver is a belt-and-braces reschedule
 * for manufacturer-specific boot cleanup. It never starts a polling loop or service.
 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            return;
        }
        AppContainer container = ((IntelliRssApp) context.getApplicationContext()).getContainer();
        container.ioExecutor().execute(() -> {
            container.workScheduler().ensurePeriodicWork();
            container.workScheduler().ensureDailySync();
            container.workScheduler().ensureArchiveCleanup();
        });
    }
}
