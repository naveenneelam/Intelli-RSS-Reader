package com.intellirs.reader.worker;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.intellirs.reader.IntelliRssApp;
import com.intellirs.reader.data.sync.FeedSyncEngine;
import com.intellirs.reader.di.AppContainer;

/** WorkManager entry point for periodic, daily and user-requested synchronization. */
public class FeedSyncWorker extends Worker {
    public static final String KEY_FEED_ID = "feed_id";
    public static final String KEY_FORCE = "force";
    public static final String KEY_MANUAL = "manual";
    public static final String KEY_DAILY = "daily";
    private static final String TAG = "FeedSyncWorker";

    public FeedSyncWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        AppContainer container = ((IntelliRssApp) getApplicationContext()).getContainer();
        long feedId = getInputData().getLong(KEY_FEED_ID, -1L);
        boolean force = getInputData().getBoolean(KEY_FORCE, false);
        boolean manual = getInputData().getBoolean(KEY_MANUAL, false);
        if (getInputData().getBoolean(KEY_DAILY, false)) {
            Log.i(TAG, "Starting daily all-feed sync");
        }
        try {
            FeedSyncEngine.SyncOutcome outcome = container.feedSyncEngine()
                    .sync(force, feedId, manual);
            if (outcome.transientFailure && getRunAttemptCount() < 2) {
                return Result.retry();
            }
            return Result.success();
        } catch (RuntimeException e) {
            Log.e(TAG, "Synchronization worker failed", e);
            container.syncStatusBus().error("Sync failed: " + safe(e.getMessage()));
            container.settingsRepository().setLastSyncStatus(
                    "Sync failed: " + safe(e.getMessage()));
            if (getRunAttemptCount() < 2) {
                return Result.retry();
            }
            return Result.failure();
        }
    }

    private static String safe(String message) {
        return message == null || message.trim().isEmpty() ? "unknown error" : message;
    }
}
