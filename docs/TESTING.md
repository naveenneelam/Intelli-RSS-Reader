# Testing report

## Results in this environment

| Check | Result |
|---|---|
| `:app:assembleDebug` | **PASS** |
| `:app:assembleRelease` | **PASS** - unsigned release APK; no production keystore is checked in |
| `:app:testDebugUnitTest` | **PASS - 56 tests, 0 failures, 0 errors** |
| `:app:lintDebug` | **PASS after fixes** (warnings remain; no lint errors) |
| `:app:compileDebugAndroidTestJavaWithJavac` | **PASS** |
| Debug APK signature (`apksigner verify`) | **PASS - APK Signature Scheme v2** |
| Instrumentation execution on a device/emulator | **NOT RUN** - no device connected and the SDK emulator/system images are not installed |

No claim of successful device installation/UI execution is made.

## JVM unit suite (56 tests)

- `FeedParserTest` - RSS 2.0, Atom/XHTML, RDF, invalid HTML, generated GUIDs, media/audio/video.
- `ContentExtractorTest` - readable extraction, clutter removal, HTML sanitization, fallback quality.
- `OpmlManagerTest` - nested folder import, malformed rejection, nested export, round-trip.
- `ArticleScoringEngineTest` - keyword/topic relevance, recency, muted keywords, authors,
  configurable thresholds, empty text.
- `FeedPrioritizationEngineTest` - engagement suggestions, failure/low suggestion, no-history default.
- `FilterEngineTest` - AND/OR, boosts, tags, mute-wins conflict, importance/media, JSON codec.
- `NotificationDecisionEngineTest` - eligibility, feed policy, relevance threshold, muted keyword,
  quiet hours/critical exemption, hourly cap, previously notified, duplicate suppression,
  filter force/enable conflict, channel mapping.
- `DuplicateDetectorTest` - canonical URL, content hash, title+publisher, conservative rejection.
- `UrlUtilsTest` - tracking removal, HTTPS default, insecure opt-in, unsafe schemes, relative resolution.
- `DateUtilsTest` - RFC 1123, Atom ISO variants, single-digit days/wrong weekday tolerance, invalid input.
- `ArticleQueryBuilderTest` - FTS prefix safety, unread-first feed/history/search SQL,
  mark-all-read SQL, related-article FTS SQL, punctuation search behavior.
- `FeedDiscoveryTest` - RSS/Atom `<link>` autodiscovery and anchor heuristics, unsafe link rejection.

Run:
```powershell
.\gradlew.bat :app:testDebugUnitTest
```
HTML report: `app/build/reports/tests/testDebugUnitTest/index.html`.

## Instrumentation suite (compiled; needs a device/emulator)

- `DatabaseMigrationTest` - opens schema v1, runs `MIGRATION_1_2`, validates the new `sourceUrl`
  column and GUID index against the exported schema.
- `RoomIntegrationTest` - real Room/FTS4 insert/search, tags/notes search, feed filters,
  unread stats, mark-all-read with a filter, pagination query paths.
- `WorkSchedulerTest` - enqueues unique periodic/manual WorkManager requests.
- `ArchiveManagerTest` - MockWebServer article/image archive, version preservation across content
  changes, cache deletion preserving bookmark/note.

Compile:
```powershell
.\gradlew.bat :app:compileDebugAndroidTestJavaWithJavac
```
Run on a connected device/emulator:
```powershell
.\gradlew.bat :app:connectedDebugAndroidTest
```

## Required scenario mapping

| # | Scenario | Coverage / status |
|---|---|---|
| 1 | Add website and discover RSS | `FeedDiscoveryTest` covers link discovery; full network discovery is manually testable |
| 2 | Subscribe to multiple feeds | Add-feed multi-select UI; OPML selective import; repository |
| 3 | New articles during background sync | `FeedSyncWorker`/`FeedSyncEngine` path; WorkScheduler instrumentation smoke test |
| 4 | Duplicate articles from multiple feeds | `DuplicateDetectorTest`, cross-ref schema/transaction logic |
| 5 | Low-priority feed does not notify | `NotificationDecisionEngineTest.lowFeedIsSuppressed` |
| 6 | High-priority feed only relevant articles | `NotificationDecisionEngineTest` plus relevance tests |
| 7 | Muted keyword suppresses | `NotificationDecisionEngineTest.mutedKeywordSuppressesEvenFilterEnable` |
| 8 | Automatic archive | `ArchiveManagerTest` archive/versions; archive modes used by sync |
| 9 | Unsubscribe keeps archive | Non-destructive `unsubscribe()` design; integration test covers cache/bookmark preservation |
| 10 | Publisher removes article, archive remains | No feed-removal deletion logic; archive stored under article id; inactive feeds retained |
| 11 | Reboot restores sync | WorkManager persistence plus idempotent `BootReceiver`; WorkScheduler test |
| 12 | Network loss during download/recovery | WorkManager retry plus transactional temp/archive design; execution needs a device |
| 13 | OPML import/export | `OpmlManagerTest` round-trip and malformed tests |
| 14 | Restore a backup | `BackupManager` implementation with path/size validation; needs device execution |
| 15 | 100,000 articles without loading all | Limit/offset query design, indexed DAO paging, FTS and bounded pages; no large-data benchmark was run here |
| 16 | Startup sync and feed progress | `MainActivity` requests a forced all-feed WorkManager sync on launch; `SyncStatusBus` publishes completed/total feed counts. Execution needs a device/emulator |
| 17 | Collapsed smart views and unread-first lists | Home smart-view toggle defaults collapsed; `ArticleQueryBuilderTest` covers unread-first SQL. Gesture/visual behavior needs device/emulator verification |

## Lint

Lint passes with warnings only. Main warning groups: unused resources, localizable hardcoded
strings/plurals, dependency update notices, overdraw, and `InsecureBaseConfiguration` (intentional
cleartext allowance at the platform layer for an explicitly configured local Ollama server).
Feed HTTP is still blocked at the application layer unless the user opts in.

Report: `app/build/reports/lint-results-debug.html`.
