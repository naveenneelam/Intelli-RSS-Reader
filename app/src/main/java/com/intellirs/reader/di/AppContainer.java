package com.intellirs.reader.di;

import android.content.Context;

import com.intellirs.reader.data.ai.LocalAiClient;
import com.intellirs.reader.data.archive.ArchiveExporter;
import com.intellirs.reader.data.archive.ArchiveManager;
import com.intellirs.reader.data.backup.BackupManager;
import com.intellirs.reader.data.database.AppDatabase;
import com.intellirs.reader.data.network.FaviconFetcher;
import com.intellirs.reader.data.network.FeedDiscovery;
import com.intellirs.reader.data.network.HttpFetcher;
import com.intellirs.reader.data.opml.OpmlManager;
import com.intellirs.reader.data.parser.ContentExtractor;
import com.intellirs.reader.data.parser.FeedParser;
import com.intellirs.reader.data.repository.ArticleRepository;
import com.intellirs.reader.data.repository.DownloadRepository;
import com.intellirs.reader.data.repository.FeedRepository;
import com.intellirs.reader.data.repository.SettingsRepository;
import com.intellirs.reader.data.sync.FeedSyncEngine;
import com.intellirs.reader.domain.model.SyncStatusBus;
import com.intellirs.reader.domain.notification.NotificationCoordinator;
import com.intellirs.reader.service.NotificationHelper;
import com.intellirs.reader.worker.WorkScheduler;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * Manual dependency container. It is tiny, explicit and avoids a DI framework in a
 * Java-only app. Everything here lives for the application process lifetime.
 */
public class AppContainer {
    private final Context context;
    private final SettingsRepository settingsRepository;
    private final AppDatabase database;
    private final ExecutorService ioExecutor;
    private final HttpFetcher httpFetcher;
    private final FeedParser feedParser;
    private final ContentExtractor contentExtractor;
    private final WorkScheduler workScheduler;
    private final SyncStatusBus syncStatusBus;
    private final FaviconFetcher faviconFetcher;
    private final DownloadRepository downloadRepository;
    private final ArticleRepository articleRepository;
    private final FeedRepository feedRepository;
    private final ArchiveManager archiveManager;
    private final NotificationHelper notificationHelper;
    private final NotificationCoordinator notificationCoordinator;
    private final FeedSyncEngine feedSyncEngine;
    private final FeedDiscovery feedDiscovery;
    private final OpmlManager opmlManager;
    private final LocalAiClient localAiClient;
    private final ArchiveExporter archiveExporter;
    private final BackupManager backupManager;

    public AppContainer(Context applicationContext) {
        this.context = applicationContext.getApplicationContext();
        this.ioExecutor = Executors.newFixedThreadPool(4, new ThreadFactory() {
            private int index;

            @Override
            public synchronized Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, "intellirss-io-" + (++index));
                thread.setDaemon(true);
                return thread;
            }
        });
        this.settingsRepository = new SettingsRepository(context);
        this.database = AppDatabase.getInstance(context);
        this.httpFetcher = new HttpFetcher();
        this.feedParser = new FeedParser();
        this.contentExtractor = new ContentExtractor();
        this.workScheduler = new WorkScheduler(context, settingsRepository, database.feedDao());
        this.syncStatusBus = new SyncStatusBus();
        this.faviconFetcher = new FaviconFetcher(context, httpFetcher,
                database.feedDao(), settingsRepository);
        this.downloadRepository = new DownloadRepository(database.downloadDao(), workScheduler);
        this.articleRepository = new ArticleRepository(database);
        this.feedRepository = new FeedRepository(database, settingsRepository, workScheduler,
                faviconFetcher, ioExecutor);
        this.archiveManager = new ArchiveManager(context, database, settingsRepository,
                httpFetcher, contentExtractor);
        this.notificationHelper = new NotificationHelper(context);
        this.notificationCoordinator = new NotificationCoordinator(
                database.notificationDao(), database.articleDao(), database.feedDao(),
                database.categoryDao(), database.articleTagDao(), database.keywordRuleDao(),
                database.filterRuleDao(), settingsRepository, notificationHelper);
        this.feedSyncEngine = new FeedSyncEngine(context, database, settingsRepository,
                httpFetcher, feedParser, contentExtractor, faviconFetcher,
                articleRepository, feedRepository, downloadRepository, workScheduler,
                notificationCoordinator, syncStatusBus);
        this.feedDiscovery = new FeedDiscovery(httpFetcher, feedParser);
        this.opmlManager = new OpmlManager();
        this.localAiClient = new LocalAiClient(settingsRepository);
        this.archiveExporter = new ArchiveExporter(database, archiveManager);
        this.backupManager = new BackupManager(context, database, settingsRepository);
    }

    public Context context() {
        return context;
    }

    public SettingsRepository settingsRepository() {
        return settingsRepository;
    }

    public AppDatabase database() {
        return database;
    }

    public ExecutorService ioExecutor() {
        return ioExecutor;
    }

    public HttpFetcher httpFetcher() {
        return httpFetcher;
    }

    public FeedParser feedParser() {
        return feedParser;
    }

    public ContentExtractor contentExtractor() {
        return contentExtractor;
    }

    public WorkScheduler workScheduler() {
        return workScheduler;
    }

    public SyncStatusBus syncStatusBus() {
        return syncStatusBus;
    }

    public FaviconFetcher faviconFetcher() {
        return faviconFetcher;
    }

    public DownloadRepository downloadRepository() {
        return downloadRepository;
    }

    public ArticleRepository articleRepository() {
        return articleRepository;
    }

    public FeedRepository feedRepository() {
        return feedRepository;
    }

    public ArchiveManager archiveManager() {
        return archiveManager;
    }

    public NotificationHelper notificationHelper() {
        return notificationHelper;
    }

    public NotificationCoordinator notificationCoordinator() {
        return notificationCoordinator;
    }

    public FeedSyncEngine feedSyncEngine() {
        return feedSyncEngine;
    }

    public FeedDiscovery feedDiscovery() {
        return feedDiscovery;
    }

    public OpmlManager opmlManager() {
        return opmlManager;
    }

    public LocalAiClient localAiClient() {
        return localAiClient;
    }

    public ArchiveExporter archiveExporter() {
        return archiveExporter;
    }

    public BackupManager backupManager() {
        return backupManager;
    }
}
