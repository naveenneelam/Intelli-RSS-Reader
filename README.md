# IntelliRSS

**IntelliRSS** is a privacy-focused, offline-first RSS/Atom/RDF reader for Android, written
entirely in Java with native Android Views and Material Design 3.

- Package: `com.intellirs.reader`
- Version: `1.0.0`
- Minimum SDK: 26 (Android 8.0)
- Target/compile SDK: 36 (Android 16)
- No account, backend, analytics, ads or cloud service is required.

The core reader works completely offline after synchronization. The only optional network
integration outside feed/publisher servers is a local Ollama endpoint, and it is off by default.

---

## 1. Features

### Feed management and discovery
- Add by website URL, direct RSS/Atom/RDF URL, clipboard, or OPML.
- HTML autodiscovery via `<link rel="alternate">`, feed anchors and conventional endpoints.
- Candidates are fetched and parsed before being shown; multiple candidates can be selected.
- Publisher icons are downloaded into private storage, with generated letter icons as fallback.
- Categories/folders, notification switches, importance levels, archive modes and per-feed sync intervals.
- Feed health (healthy/degraded/failing/inactive), failure counts, HTTP status and statistics.
- Unsubscribing is non-destructive: subscriptions are deactivated, never cascade-deleted with
  reading history or archive.
- Sorting by priority, latest activity, unread count, alphabet or user-defined order.

### Reading
- Vertical home list: feeds, important feeds and category/folder groups. Smart views are
  collapsed into a tap-to-expand header by default.
- Compact, comfortable and image-rich article lists with paginated loading.
- Article lists place unread items first, with bright unread titles and dimmed read titles;
  a single-feed list omits the redundant feed name on each card.
- Distraction-free native article reader (no WebView):
  - title, publisher, author, date, featured image and inline images;
  - headings, paragraphs, quotes, lists, tables, code and safe links;
  - embedded video as an attribution/preview card that opens the source;
  - podcast audio player with seek/progress and offline audio download;
  - notes, tags, bookmarks and reading progress;
  - font size/family, line spacing and dark/light/sepia reading themes;
  - text-to-speech, share, copy, open original, swipe-right next / swipe-left previous;
  - version history with textual comparison;
  - optional local Ollama summaries/tags;
  - related articles, all source publishers and preferred-source attribution.

### Intelligence
- Feed importance (Critical/High/Normal/Low/Muted) with automatic suggestions and a strict manual override.
- Independent 0-100 article relevance scoring, transparent reasons and configurable thresholds.
- Priority/muted keywords, preferred topics and authors, recency and historical engagement.
- Ordered AND/OR filter rules with boost, mute/enable notifications, mark-read, archive and tagging.
- Duplicate detection using canonical URLs, RSS GUIDs, normalized titles, publisher and content hashes.
  A canonical article retains every source-feed relationship.
- Notification decision engine with quiet hours, frequency caps, duplicate suppression, feed
  importance policy and a human-readable decision log.
- Similar-article lookup using indexed, normalized title terms.
- Optional local Ollama integration (no cloud AI fallback).

### Offline archive
- Per-feed automatic, priority-only or manual archive modes.
- Transactional HTML/image downloads in private storage; files are never marked archived until
  required content is safely stored.
- Immutable article versions when content changes.
- Bookmarked/manual/tagged/noted articles are protected from automatic cleanup.
- Configurable archive/image/audio limits, image quality and cleanup.
- Storage dashboard and archive exports in HTML, JSON and ZIP formats.
- Full local backup/restore ZIP (database, archive, icons and preferences) and JSON personal-data export.

---

## 2. Architecture

Single-module Android application, Java 17 language level, MVVM plus repositories, manual DI.

```text
UI Activities / Fragments / Adapters
        | LiveData / Activity results
ViewModels
        | synchronous repository calls on background executors
Repositories
   |-- Room / SQLite
   |-- OkHttp (HTTP, conditional GET, images, Ollama)
   |-- FeedParser + FeedDiscovery + ContentExtractor (Jsoup)
   |-- ArchiveManager (transactional files, versions, cleanup, audio)
   |-- BackupManager / ArchiveExporter / OpmlManager
   `-- WorkManager workers
            |-- FeedSyncWorker -> FeedSyncEngine
            |-- ArticleDownloadWorker -> ArchiveManager
            |-- ArchiveMaintenanceWorker
            `-- RescoreWorker
```

### Package map
```text
com.intellirs.reader
  di/                  AppContainer (manual dependency injection)
  data/
    database/          AppDatabase + migration 1->2
    entity/            Room entities and enums
    dao/               Room DAO interfaces
    model/             Query projections (feed list, stats, sources, downloads)
    repository/        Feed, article, download, settings repositories
    network/           HttpFetcher, FeedDiscovery, FaviconFetcher
    parser/            FeedParser, ContentExtractor, parsed models
    archive/           ArchiveManager, ArchiveExporter, storage models
    backup/            Full backup/restore and personal-data export
    opml/              OPML import/export
    sync/              FeedSyncEngine
    ai/                Optional Ollama client
  domain/
    model/             ArticleFilter, sync status
    prioritization/    Feed/suggestion and article relevance engines
    filtering/         Rules, codec, evaluation
    duplicates/        Conservative duplicate matcher
    notification/      Decision engine, coordinator, channel IDs
  worker/              WorkManager entry points and WorkScheduler
  service/             Notification channel/helper code
  receiver/            Notification actions, boot rescheduling
  ui/                  Main shell, home, lists, reader, feed detail, settings, etc.
  util/                URLs, dates, HTML text, hashing, theme, files
```

### Threading
- Network, parsing and Room writes never run on the main thread in normal flows.
- Feed synchronization uses a bounded pool of three feed workers inside WorkManager;
  article persistence is serialized to protect the canonical URL constraint.
- UI pagination is limit/offset based and never loads all articles.
- WorkManager provides retry/backoff, network constraints, battery-not-low option, unique work
  and reboot persistence.

### Background scheduling
- Periodic unique work, minimum 15 minutes, default 30 minutes.
- Opening the main app requests an immediate all-feed sync. It respects the Wi-Fi-only and
  battery-not-low preferences, and a determinate progress bar shows completed feeds whenever
  synchronization is running.
- The periodic cadence is the shortest effective global/per-feed interval (floor 15 minutes).
- Every feed also has its own interval check when work runs.
- Conditional GET uses ETag / Last-Modified.
- Android can defer work because of Doze, battery policies or manufacturer restrictions; exact
  sync times are never promised.
- No polling loop and no permanent foreground service. Periodic work is WorkManager-only.

---

## 3. Database relationships

See [docs/DATABASE.md](docs/DATABASE.md) for the full table and index summary.

- `Category 1-N Feed` (`Feed.categoryId`, `ON DELETE SET NULL`).
- `Feed N-M Article` through `article_feed_cross_ref`, preserving every source relationship.
- `Article 1-N ArticleVersion`, `ArticleImage`, `ArticleTag`.
- `Article 1-1 ArticleNote`, `Article 1-1 ReadingHistory`.
- `Feed 1-1 FeedHealth`.
- `Article 1-1 ArticleFts` (FTS4 rowid = article id).
- `NotificationQueue` is denormalized so notification history survives article cleanup.
- `DownloadQueue`, `SyncRun`, `FilterRule`, `KeywordRule` and `SavedSearch` are independent tables.
- Full HTML/images/audio are stored under app-private `files/`; Room stores paths and metadata.
- Database version 2 includes migration `MIGRATION_1_2` (adds per-source URLs and a GUID index).
  Destructive recreation is not configured.

### Archive file layout
```text
files/
  archive/
    articles/<articleId>/
      content.html
      metadata.json
      images/img_<hash>.<ext>
      versions/vN/{content.html,metadata.json}
      audio/episode_<hash>.<ext>
    .tmp/                      # transactional workspace, cleaned on maintenance
  icons/feed_<feedId>.<ext>
```

---

## 4. Navigation

```text
MainActivity (NavigationDrawer + toolbar + sync/notification indicators)
+-- Home (vertical feed list)
|   +-- Smart views toggle (collapsed by default; All, Unread, Important, Starred,
|   |   Saved offline, Recently read when expanded)
|   +-- Important feeds
|   +-- Category/folder groups (collapsible)
|   `-- Feed rows -> ArticleListFragment
+-- ArticleListFragment (feed/category/smart view/search contexts; unread first)
|   `-- ReaderActivity (swipe-left previous, swipe-right next)
|       +-- VersionHistoryActivity
|       `-- Settings/editor dialogs
+-- AddFeedActivity
+-- FeedDetailActivity
+-- CategoriesActivity
+-- SearchActivity
+-- DownloadsActivity
+-- FilterListActivity -> FilterEditActivity
+-- NotificationHistoryActivity
+-- StorageActivity
+-- StatisticsActivity
`-- SettingsActivity
    +-- KeywordListActivity
    +-- FeedNotificationActivity
    `-- OPMLPreviewActivity
```

Back behavior: drawer closes first, then article lists return home, and the reader/settings
finish normally. The notification content intent opens the reader directly.

---

## 5. Building

### Prerequisites
- Android Studio with a full JDK 17 (must include `jlink`; some trimmed JetBrains runtimes do not).
- Android SDK Platform 36, Build-Tools 36.1.0 or 35.0.0, Platform-Tools.
- Internet access for Gradle/dependency downloads.

### Setup
1. Open the project folder in Android Studio.
2. Set `sdk.dir` in `local.properties` if needed (this workspace uses `E\:/Android/Sdk`).
3. Ensure the Gradle JDK is a full JDK 17.
4. Start a Gradle sync.

### Debug APK
```powershell
.\gradlew.bat :app:assembleDebug
```
Output: `app/build/outputs/apk/debug/app-debug.apk`.

A packaged copy is provided at `dist/IntelliRSS-1.0.0-debug.apk`. The debug APK is APK
Signature Scheme v2 verified and installable on Android 8.0+.

### Release build
The release variant intentionally has no checked-in signing key. Create your own keystore and
configure a release signing config in Android Studio or Gradle before publishing. Do not reuse
the debug key for a production release.

### Install
```powershell
adb install -r dist/IntelliRSS-1.0.0-debug.apk
```

### Tests
```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:compileDebugAndroidTestJavaWithJavac
# With a connected device/emulator:
.\gradlew.bat :app:connectedDebugAndroidTest
```

The JVM suite has 56 tests and passes. The instrumentation suite is compiled but was not
executed in this environment because no Android device was connected and the SDK emulator
and system images are not installed. See [docs/TESTING.md](docs/TESTING.md).

---

## 6. Permissions

| Permission | Reason |
|---|---|
| `INTERNET` | Fetch feeds, articles, images and the explicitly configured local Ollama endpoint |
| `ACCESS_NETWORK_STATE` | WorkManager network constraints and status |
| `POST_NOTIFICATIONS` | Android 13+ notification permission; requested with an explanation |
| `RECEIVE_BOOT_COMPLETED` | Idempotent rescheduling of periodic WorkManager work |
| `WAKE_LOCK` | WorkManager guarantee while doing bounded work |
| `FOREGROUND_SERVICE` | Merged from WorkManager; IntelliRSS itself never starts a permanent foreground service |
| `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | Merged AndroidX signature permission for non-exported dynamic receivers |

No storage permission is required: imports and exports use the Storage Access Framework.
No location, contacts, camera, microphone, analytics or advertising permissions are requested.

`android:allowBackup="false"` prevents cloud auto-backup; IntelliRSS has its own local
backup/restore. Feed HTTPS is the default. HTTP feeds require an explicit opt-in. Cleartext
transport is permitted at the OS layer so a user-supplied local Ollama HTTP endpoint on a LAN
can work; IntelliRSS's own feed/fetch layer still rejects HTTP unless the user enables it.

---

## 7. Data ownership and security

- All data is in app-private storage.
- No account, telemetry, ad SDK, Firebase, or external server.
- HTML is sanitized with an explicit allowlist; scripts, event handlers and unsafe URL schemes are removed.
- File writes use temp files, fsync where practical, canonical path checks and relative database paths.
- Downloads enforce maximum response sizes, redirect bounds, timeouts and image magic-byte validation.
- ZIP restore validates entry paths, entry counts and total uncompressed size before applying.
- Local AI is off by default; article text is sent only to the configured endpoint on explicit user action.
- No WebView is used anywhere.
- Certificates are never bypassed and trust-all is not configured.

---

## 8. Sample data

Fixtures live in [`sample_feeds/`](sample_feeds/) and are bundled at `assets/samples/`:
RSS 2.0, Atom, RSS 1.0/RDF, a cluttered extraction page and an OPML file. They are also used
by the JVM unit tests.

---

## 9. Implementation checklist

- [x] Phase 1 - Gradle/Java/Material project, dark theme, navigation, Room, feeds/categories
- [x] Phase 2 - RSS/Atom/RDF parser, autodiscovery, subscriptions, article lists, reader, images
- [x] Phase 3 - WorkManager sync, conditional HTTP, retries, reboot persistence, feed health
- [x] Phase 4 - Feed priority, article relevance, filters, notification queue/channels/logs
- [x] Phase 5 - Extraction, transactional archive, images, versions, export/restore
- [x] Phase 6 - OPML, FTS search, duplicate merging, statistics, notes/tags, related articles, podcasts
- [x] Phase 7 - Optional local Ollama summary/tags integration
- [x] Phase 8 - Compile, unit tests, lint, APK signing, architecture/security/troubleshooting docs

---

## 10. Known limitations

These are deliberate scope/technical limits, not hidden dummy controls:

1. **Duplicate display policy** is canonical grouping. Every source feed and URL is retained;
   the reader can show all publishers and choose a preferred one. A separate "show every
   duplicate copy as a distinct row" mode is not implemented.
2. **Full-text index size**: extracted article text is indexed up to 40,000 characters per
   article to keep the database bounded. The complete HTML remains on disk.
3. **Archive media caps**: up to 40 images per article, image/audio size and quality limits are
   enforced. Images that fail download or exceed limits are omitted from the offline copy.
4. **Podcast playback** uses Android `MediaPlayer` in the reader, not a background MediaSession.
   Playback pauses when the reader leaves the foreground. Offline audio downloading is supported.
5. **Backup, archive export and OPML export** run in the app process. Keep IntelliRSS open until
   they finish; they can be interrupted if Android kills the process. Restore requires an app restart.
6. **Nested categories** can be imported/exported from OPML and feeds can be assigned to any
   existing category. The category manager creates top-level folders and renames/deletes them;
   changing parent folders is done through OPML.
7. **Initial synchronization does not notify** to avoid an archive-sized notification burst.
   New articles on subsequent syncs follow normal eligibility rules.
8. **Local AI** requires a reachable Ollama server. The default `10.0.2.2` address is for the
   Android emulator. The feature is optional and disabled by default.
9. **HTML rendering** supports a practical reading subset; unsupported interactive embed types
   are represented by an "open original source" video card rather than executing publisher scripts.
10. **Instrumentation tests** could not be executed in this environment (no connected device,
    and the SDK emulator/system images are not installed). They compile successfully and are
    included for execution in Android Studio or on a physical device.
11. **Exact sync time is not guaranteed**. WorkManager, Doze, battery optimization and OEM
    policies can defer periodic work.
12. **HTML archive export** keeps image URLs pointing at the original publisher when the archived
    copy is not used; use the ZIP or full backup to retain local images.

---

## 11. Troubleshooting

| Problem | What to check |
|---|---|
| No feeds found at a website | The site may not publish RSS/Atom; try the direct feed URL; some sites block automated requests |
| TLS/SSL error | Publisher certificate may be invalid/expired; IntelliRSS does not bypass TLS validation |
| HTTP feed rejected | Enable Settings > Synchronization > Allow insecure HTTP feeds only if you trust the publisher |
| Sync seems delayed | WorkManager is constrained by Android; check battery optimization, Doze, Wi-Fi-only and battery-not-low settings |
| No notifications | Check Android notification permission, app notifications, per-feed switch, feed importance policy, article relevance threshold, quiet hours, frequency caps and the decision log |
| Notification missing for a newly added feed | The first synchronization is intentionally silent |
| No full article text | Publisher blocks extraction or lacks content; use Download to save the feed summary or open the original |
| Archive download failed | Check network, storage limits, image/audio maximum MB and Downloads screen; WorkManager retries transient failures |
| OPML import skips a feed | Duplicate URL, unsupported scheme, HTTP disabled, or invalid outline; preview shows status |
| Local AI test fails | Verify Ollama is running, the base URL is reachable from the device and the model name exists |
| Restore fails | Use a backup generated by IntelliRSS; the archive is schema-checked and path-validated; keep the app open until restore completes |
| Build fails on `jlink` | Use a full JDK 17, not a trimmed runtime; a full Temurin/OpenJDK 17 is required |

For bugs, capture the sync history from the toolbar status bar, the notification decision log,
and any feed health error shown in Feed settings. Logcat tags are prefixed with
`ArchiveManager`, `FeedSyncEngine`, `BackupManager` and `RescoreWorker`.

---

## 12. Third-party libraries

- AndroidX AppCompat, Fragment, RecyclerView, ConstraintLayout, DrawerLayout, Preference, DocumentFile
- Material Components for Android (Material 3)
- AndroidX Room (SQLite), WorkManager, Lifecycle, SavedState
- OkHttp (network)
- Jsoup (HTML/XML parsing, sanitization)
- Gson (parameter JSON)
- Glide (image loading/cache)
- JUnit, Mockito, AndroidX Test, Espresso, Room Testing, WorkManager Testing, MockWebServer (tests)

No analytics, Firebase, ad SDK, WebView or cloud service dependencies are present.
