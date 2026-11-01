package com.intellirs.reader.worker;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.intellirs.reader.IntelliRssApp;
import com.intellirs.reader.data.archive.CleanupResult;
import com.intellirs.reader.di.AppContainer;

/** Daily storage maintenance: protected articles are never removed by cleanup. */
public class ArchiveMaintenanceWorker extends Worker {
    public ArchiveMaintenanceWorker(@NonNull Context context,
                                    @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        AppContainer container = ((IntelliRssApp) getApplicationContext()).getContainer();
        try {
            container.archiveManager().cleanStaleTemporaryFiles();
            container.database().notificationDao().prune(
                    System.currentTimeMillis() - 90L * 86_400_000L);
            if (container.settingsRepository().archiveAutoCleanup()) {
                CleanupResult result = container.archiveManager().cleanupIfNeeded();
                container.syncStatusBus().complete(0, 0, 0);
                if (result.articlesRemoved > 0) {
                    container.settingsRepository().setLastSyncStatus(
                            "Archive cleanup removed " + result.articlesRemoved + " cached articles");
                }
            }
            return Result.success();
        } catch (RuntimeException e) {
            return Result.retry();
        }
    }
}
