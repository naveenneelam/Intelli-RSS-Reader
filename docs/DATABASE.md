# IntelliRSS database schema

Room database: `intellirss.db` (WAL journal mode), version **2**.
Schemas are exported under `app/schemas/com.intellirs.reader.data.database.AppDatabase/`.
No destructive fallback is registered. Version 1 -> 2 uses `AppDatabase.MIGRATION_1_2`
(adds `sourceUrl` and the source GUID index to `article_feed_cross_ref`).

All timestamps are Unix epoch milliseconds unless noted. Enums are stored by name using
explicit `TypeConverters` so databases remain inspectable.

## Tables

### `categories`
User-defined folders.

| Column | Type | Notes |
|---|---|---|
| `id` | INTEGER PK | autoincrement |
| `name` | TEXT NOT NULL | folder name |
| `parentId` | INTEGER | nested OPML folder parent |
| `sortOrder` | INTEGER | ordering |
| `muteNotifications` | INTEGER | notification category mute |
| `createdAt` | INTEGER | creation time |

Indexes: `parentId`, `sortOrder`.

### `feeds`
Subscriptions. Unsubscribe deactivates (`isActive=0`) instead of deleting the row, preserving
source attribution and archive history.

| Column | Type | Notes |
|---|---|---|
| `id` | INTEGER PK | |
| `title`, `feedUrl` | TEXT NOT NULL | `feedUrl` is unique |
| `websiteUrl`, `description`, `faviconPath` | TEXT | private relative favicon path |
| `categoryId` | INTEGER | FK → categories, `SET NULL` |
| `feedPriority` | TEXT NOT NULL | CRITICAL/HIGH/NORMAL/LOW/MUTED |
| `importanceSource` | TEXT NOT NULL | AUTO/USER |
| `notificationEnabled` | INTEGER | per-feed notification switch |
| `archiveMode` | TEXT NOT NULL | AUTOMATIC/PRIORITY/MANUAL |
| `syncIntervalMinutes` | INTEGER | 0 = app default |
| `notifyMinRelevance` | INTEGER | optional per-feed raise |
| `feedType` | TEXT | RSS/ATOM/RDF |
| `etag`, `lastModified` | TEXT | conditional HTTP validators |
| `lastSyncTime`, `lastSuccessfulSync` | INTEGER | scheduling/health |
| `failureCount`, `lastHttpStatus` | INTEGER | health |
| `isActive`, `sortOrder`, `createdAt` | INTEGER | lifecycle/order |

Indexes: unique `feedUrl`; `categoryId`, `isActive`, `feedPriority`, `sortOrder`,
`lastSuccessfulSync`. FK: `categoryId` → `categories.id ON DELETE SET NULL`.

### `articles`
Canonical stored article. A syndicated article is represented once and keeps source relationships.

| Column | Type | Notes |
|---|---|---|
| `id` | INTEGER PK | |
| `originalUrl`, `canonicalUrl`, `guid` | TEXT | original URL; canonical unique index; feed GUID |
| `title`, `description`, `author`, `publisher` | TEXT | description is sanitized summary HTML |
| `publishedAt`, `firstSeenAt`, `updatedAt`, `lastSeenAt` | INTEGER | timestamps |
| `contentHash`, `normalizedTitle` | TEXT | duplicate detection |
| `thumbnailUrl`, `thumbnailPath` | TEXT | remote and private-local thumbnail |
| `articleType` | TEXT | ARTICLE/VIDEO/PODCAST |
| `relevanceScore` | INTEGER | 0–100 independent relevance |
| `relevanceLevel` | TEXT | CRITICAL/HIGH/NORMAL/LOW/IRRELEVANT |
| `relevanceReason` | TEXT | transparent scoring/filtering explanation |
| `isRead`, `isBookmarked`, `isArchived`, `isManuallyArchived` | INTEGER | state/protection |
| `contentPath` | TEXT | relative HTML path, never HTML itself |
| `latestVersionId` | INTEGER | latest version row |
| `archivedContentHash` | TEXT | version-change detection |
| `hasImages`, `hasVideo`, `hasAudio` | INTEGER | list/filter signals |
| `audioUrl`, `audioPath`, `audioDurationMs`, `videoUrl` | | podcast/video |
| `plainText` | TEXT | extracted/search text, capped; not HTML |
| `aiSummary`, `aiTags` | TEXT | optional local AI output |
| `primaryFeedId` | INTEGER | FK → feeds, `SET NULL` |
| `extractionQuality` | TEXT | NONE/FALLBACK/FULL |
| `archivedAt` | INTEGER | |

Indexes: unique `canonicalUrl`; `guid`, `contentHash`, `normalizedTitle`, `publishedAt`,
`firstSeenAt`, `isRead`, `isBookmarked`, `isArchived`, `relevanceScore`, `articleType`,
`primaryFeedId`. FK: primary feed `SET NULL`.

### `article_feed_cross_ref`
N:M source ownership. A GUID collision never removes another feed's relationship.

`articleId` + `feedId` composite primary key; `originalGuid`, `sourceTitle`,
`sourcePublisher`, `sourceUrl`, `sourcePublishedAt`, `addedAt`.
FKs cascade only when the article or feed row itself is purged (normal unsubscribe does not
delete the feed row). Indexes on both keys and a composite (`feedId`,`originalGuid`) index for
stable GUID updates within a source feed.

### `article_versions`
Immutable archive history metadata. Each new extracted content hash gets a new version;
`versions/vN/content.html` files are never overwritten.

`id` PK, `articleId` FK cascade, `versionNumber`, `contentHash`, `contentPath`,
`metadataPath`, `createdAt`, `wordCount`. Unique (`articleId`,`versionNumber`).

### `article_images`
Offline image records: `imageUrl`, `localPath`, `imageType`, `fileSize`, SHA-256 `sha256`.
Indexed by article/hash/type.

### `article_notes`
One private note per article: `articleId` unique FK cascade, `note`, `createdAt`, `updatedAt`.

### `article_tags`
`articleId` FK cascade, `tag`, unique (`articleId`,`tag`), indexed by tag.

### `reading_history`
One row per article: `openedAt`, `lastReadAt`, `progress` (0–1), `secondsRead`.
Unique `articleId`; indexed `lastReadAt`.

### `feed_health`
One row per feed: `status`, `failureCount`, `lastError`, `lastCheckedAt`,
`lastSuccessAt`, `lastHttpStatus`, `avgResponseMs`.
Unique feed FK cascade.

### `filter_rules`
`ruleName`, `conditionJson` (`{matchAll, conditions[]}`), `actionJson` (array), `enabled`,
`priority`, `createdAt`, `updatedAt`. Indexed enabled/priority. Higher UI rules run earlier;
mute wins over enable in conflict resolution.

### `notification_queue`
Deliberately denormalized audit log (no FK) so history survives article/feed cleanup:
`articleId`, `feedId`, `articleTitle`, `publisher`, `contentHash`, `eligibility`,
`decisionReason`, `notificationStatus`, `groupKey`, `channelId`, `createdAt`, `postedAt`.
Indexed for status/time/hash/group.

### `download_queue`
`articleId`, `feedId`, `downloadType`, `status`, `retryCount`, `lastError`, timestamps.
Unique (`articleId`,`downloadType`); indexed status/feed/created.

### `saved_searches`
`name`, `query`, serialized `filterJson`, `createdAt`.

### `keyword_rules`
`keyword`, `type` (PRIORITY/MUTED/TOPIC), `weight` 1–100, `createdAt`.
Unique (`keyword`,`type`); indexed type.

### `article_fts` (FTS4)
Searchable text columns: `title`, `description`, `body_text`, `publisher`, `author`,
`tags`, `notes`; `rowid` equals `articles.id`. Only text is indexed; HTML/images remain files.
The index is updated transactionally from `ArticleRepository.rebuildFts()` and
`ArchiveManager`.

### `sync_runs`
`startedAt`, `finishedAt`, `status` (RUNNING/SUCCESS/PARTIAL/FAILED), feeds attempted/
succeeded/failed, new article count, error summary, `manual`. Pruned after 90 days.

## File storage relationships
- `articles.contentPath` → `files/archive/articles/<id>/content.html`.
- `article_versions.contentPath` → `files/archive/articles/<id>/versions/vN/content.html`.
- `article_images.localPath` → `files/archive/articles/<id>/images/...`.
- `articles.audioPath` → `files/archive/articles/<id>/audio/...`.
- `feeds.faviconPath` → `files/icons/...`.
- Archive metadata lives beside content in `metadata.json`, including image URL/local-path mapping.

## Integrity rules
- Image and content files are written to `archive/.tmp` and moved into place before the database
  marks the article archived.
- Cleanup excludes bookmarked, manually archived, tagged and noted articles.
- Archive deletion removes local files and image/version/download rows but never the article,
  bookmark, notes, tags or history.
- Full backup includes a consistent SQLite snapshot, archive files and preferences.
