package com.intellirs.reader.worker;

import static org.junit.Assert.assertFalse;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import com.intellirs.reader.data.database.AppDatabase;
import com.intellirs.reader.data.repository.SettingsRepository;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class WorkSchedulerTest {
    private AppDatabase database;
    private WorkScheduler scheduler;
    private WorkManager workManager;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        workManager = WorkManager.getInstance(context);
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();
        scheduler = new WorkScheduler(context, new SettingsRepository(context),
                database.feedDao());
    }

    @After
    public void tearDown() {
        workManager.cancelAllWork();
        scheduler.shutdown();
        database.close();
    }

    @Test
    public void periodicSyncIsUniqueAndScheduled() throws Exception {
        scheduler.ensurePeriodicWork();
        scheduler.ensurePeriodicWork();
        List<WorkInfo> infos = workManager
                .getWorkInfosForUniqueWork(WorkScheduler.PERIODIC_SYNC_NAME)
                .get(10, TimeUnit.SECONDS);
        assertFalse(infos.isEmpty());
    }

    @Test
    public void manualSyncAcceptsInput() throws Exception {
        scheduler.enqueueManualSync(true);
        List<WorkInfo> infos = workManager
                .getWorkInfosForUniqueWork(WorkScheduler.MANUAL_SYNC_NAME)
                .get(10, TimeUnit.SECONDS);
        assertFalse(infos.isEmpty());
    }

    @Test
    public void startupSyncIsScheduledSeparately() throws Exception {
        scheduler.enqueueStartupSync();
        List<WorkInfo> infos = workManager
                .getWorkInfosForUniqueWork(WorkScheduler.STARTUP_SYNC_NAME)
                .get(10, TimeUnit.SECONDS);
        assertFalse(infos.isEmpty());
    }
}
