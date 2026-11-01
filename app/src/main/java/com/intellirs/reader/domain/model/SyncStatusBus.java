package com.intellirs.reader.domain.model;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

/** In-process sync status for the toolbar indicator. Persistent history lives in Room. */
public class SyncStatusBus {
    private final MutableLiveData<SyncStatus> status =
            new MutableLiveData<>(SyncStatus.idle());

    public LiveData<SyncStatus> observe() {
        return status;
    }

    public SyncStatus get() {
        return status.getValue();
    }

    public void publish(SyncStatus value) {
        status.postValue(value == null ? SyncStatus.idle() : value);
    }

    public void running() {
        running(0, 0);
    }

    public void running(int total) {
        running(0, total);
    }

    public void running(int completed, int total) {
        publish(new SyncStatus(SyncStatus.State.RUNNING, "Syncing feeds\u2026",
                System.currentTimeMillis(), 0, 0, 0, completed, total));
    }

    public void complete(int feeds, int failed, int newArticles) {
        publish(new SyncStatus(SyncStatus.State.COMPLETE, "Sync complete",
                System.currentTimeMillis(), feeds, failed, newArticles));
    }

    public void error(String message) {
        publish(new SyncStatus(SyncStatus.State.ERROR, message,
                System.currentTimeMillis(), 0, 0, 0));
    }
}
