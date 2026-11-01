package com.intellirs.reader.data.backup;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.os.Process;
import android.util.Log;

import androidx.work.WorkManager;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonWriter;
import com.intellirs.reader.BuildConfig;
import com.intellirs.reader.data.dao.ArticleDao;
import com.intellirs.reader.data.dao.ArticleNoteDao;
import com.intellirs.reader.data.dao.ArticleTagDao;
import com.intellirs.reader.data.database.AppDatabase;
import com.intellirs.reader.data.entity.ArticleEntity;
import com.intellirs.reader.data.entity.ArticleNoteEntity;
import com.intellirs.reader.data.entity.ArticleTagEntity;
import com.intellirs.reader.data.entity.CategoryEntity;
import com.intellirs.reader.data.entity.FeedEntity;
import com.intellirs.reader.data.entity.ReadingHistoryEntity;
import com.intellirs.reader.data.repository.SettingsRepository;
import com.intellirs.reader.util.FileUtils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Full local backup/restore and personal-data export. Restore validates the archive, keeps
 * a safety copy of the previous database/archive, and requires a process restart afterwards.
 */
public class BackupManager {
    private static final String TAG = "BackupManager";
    public static final int SCHEMA_VERSION = 2;
    private static final String DB_NAME = "intellirss.db";
    private static final long MAX_TOTAL_UNCOMPRESSED = 4L * 1024 * 1024 * 1024;
    private static final int MAX_ENTRIES = 500_000;
    private static final int PAGE = 200;

    private final Context context;
    private final AppDatabase database;
    private final SettingsRepository settings;
    private final Gson gson = new Gson();

    public BackupManager(Context context, AppDatabase database, SettingsRepository settings) {
        this.context = context.getApplicationContext();
        this.database = database;
        this.settings = settings;
    }

    public void createBackup(OutputStream outputStream) throws IOException {
        if (outputStream == null) {
            throw new IOException("No output stream");
        }
        ZipOutputStream zip = new ZipOutputStream(
                new BufferedOutputStream(outputStream), StandardCharsets.UTF_8);
        JsonObject manifest = new JsonObject();
        manifest.addProperty("format", "IntelliRSS-full-backup");
        manifest.addProperty("schemaVersion", SCHEMA_VERSION);
        manifest.addProperty("appVersion", BuildConfig.VERSION_NAME);
        manifest.addProperty("createdAt", System.currentTimeMillis());
        manifest.addProperty("feeds", database.feedDao().countAll());
        manifest.addProperty("archivedArticles", database.articleDao().countArchived());
        manifest.add("preferences", preferencesToJson());
        zip.putNextEntry(new ZipEntry("backup.json"));
        zip.write(gson.toJson(manifest).getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();

        File vacuumed = new File(context.getCacheDir(), "backup_" + System.currentTimeMillis()
                + ".db");
        boolean consistent = false;
        try {
            database.getOpenHelper().getWritableDatabase()
                    .execSQL("VACUUM INTO '" + escapeSql(vacuumed.getAbsolutePath()) + "'");
            if (vacuumed.isFile()) {
                addFile(zip, vacuumed, "database/" + DB_NAME);
                consistent = true;
            }
        } catch (SQLiteException e) {
            Log.i(TAG, "VACUUM INTO is unavailable; using WAL checkpoint snapshot");
        } finally {
            if (!consistent && vacuumed.exists()) {
                vacuumed.delete();
            }
        }
        if (!consistent) {
            try {
                Cursor cursor = database.getOpenHelper().getWritableDatabase()
                        .query("PRAGMA wal_checkpoint(FULL)");
                if (cursor != null) {
                    cursor.moveToFirst();
                    cursor.close();
                }
            } catch (RuntimeException e) {
                Log.w(TAG, "WAL checkpoint failed", e);
            }
            File db = context.getDatabasePath(DB_NAME);
            if (!db.isFile()) {
                throw new IOException("Database file is missing");
            }
            addFile(zip, db, "database/" + DB_NAME);
            addIfExists(zip, new File(db.getPath() + "-wal"), "database/" + DB_NAME + "-wal");
            addIfExists(zip, new File(db.getPath() + "-shm"), "database/" + DB_NAME + "-shm");
        } else {
            vacuumed.delete();
        }

        File archive = new File(context.getFilesDir(), "archive");
        addDirectory(zip, archive, "files/archive/");
        File icons = new File(context.getFilesDir(), "icons");
        addDirectory(zip, icons, "files/icons/");
        zip.finish();
        zip.flush();
        outputStream.flush();
    }

    public RestorePreview prepareRestore(InputStream inputStream) throws IOException {
        File temp = new File(context.getCacheDir(), "restore_" + System.currentTimeMillis());
        if (!temp.mkdirs()) {
            throw new IOException("Could not create restore workspace");
        }
        long total = 0;
        int entries = 0;
        try (ZipInputStream zip = new ZipInputStream(
                new BufferedInputStream(inputStream), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = zip.getNextEntry()) != null) {
                entries++;
                if (entries > MAX_ENTRIES) {
                    throw new IOException("Backup contains too many files");
                }
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName().replace('\\', '/');
                if (!isAllowedEntry(name)) {
                    throw new IOException("Backup contains an unsafe path: " + name);
                }
                File target = new File(temp, name);
                String canonicalBase = temp.getCanonicalPath() + File.separator;
                if (!target.getCanonicalPath().startsWith(canonicalBase)) {
                    throw new IOException("Backup path traversal detected");
                }
                File parent = target.getParentFile();
                if (parent != null && !parent.exists() && !parent.mkdirs()) {
                    throw new IOException("Could not create restore directory");
                }
                try (OutputStream out = new BufferedOutputStream(new FileOutputStream(target))) {
                    int read;
                    while ((read = zip.read(buffer)) != -1) {
                        total += read;
                        if (total > MAX_TOTAL_UNCOMPRESSED) {
                            throw new IOException("Backup is larger than the supported limit");
                        }
                        out.write(buffer, 0, read);
                    }
                }
            }
        } catch (IOException | RuntimeException e) {
            deleteRecursively(temp);
            throw new IOException(e.getMessage() == null ? "Invalid backup archive" : e.getMessage(), e);
        }
        File manifestFile = new File(temp, "backup.json");
        File db = new File(temp, "database/" + DB_NAME);
        if (!manifestFile.isFile() || !db.isFile()) {
            deleteRecursively(temp);
            throw new IOException("This is not a complete IntelliRSS backup");
        }
        BackupMetadata metadata;
        try {
            metadata = gson.fromJson(readText(manifestFile), BackupMetadata.class);
        } catch (RuntimeException e) {
            deleteRecursively(temp);
            throw new IOException("Backup metadata is malformed", e);
        }
        if (metadata == null || !"IntelliRSS-full-backup".equals(metadata.format)) {
            deleteRecursively(temp);
            throw new IOException("Unrecognized backup format");
        }
        if (metadata.schemaVersion > SCHEMA_VERSION) {
            deleteRecursively(temp);
            throw new IOException("This backup was created by a newer database schema");
        }
        return new RestorePreview(temp, metadata);
    }

    public void applyRestore(RestorePreview preview) throws IOException {
        File extracted = preview.extractedDirectory;
        File safety = new File(context.getCacheDir(), "restore_safety_" + System.currentTimeMillis());
        File safetyDatabase = new File(safety, "database");
        File safetyFiles = new File(safety, "files");
        if (!safety.mkdirs() || !safetyDatabase.mkdirs() || !safetyFiles.mkdirs()) {
            throw new IOException("Could not prepare restore safety area");
        }
        AppDatabase.closeForRestore();
        boolean movedOld = false;
        try {
            File db = context.getDatabasePath(DB_NAME);
            moveIfExists(db, new File(safetyDatabase, DB_NAME));
            moveIfExists(new File(db.getPath() + "-wal"),
                    new File(safetyDatabase, DB_NAME + "-wal"));
            moveIfExists(new File(db.getPath() + "-shm"),
                    new File(safetyDatabase, DB_NAME + "-shm"));
            File archive = new File(context.getFilesDir(), "archive");
            File icons = new File(context.getFilesDir(), "icons");
            moveIfExists(archive, new File(safetyFiles, "archive"));
            moveIfExists(icons, new File(safetyFiles, "icons"));
            movedOld = true;

            File restoredDb = new File(extracted, "database/" + DB_NAME);
            copy(restoredDb, db);
            copyIfExists(new File(extracted, "database/" + DB_NAME + "-wal"),
                    new File(db.getPath() + "-wal"));
            copyIfExists(new File(extracted, "database/" + DB_NAME + "-shm"),
                    new File(db.getPath() + "-shm"));
            File extractedFiles = new File(extracted, "files");
            File extractedArchive = new File(extractedFiles, "archive");
            File extractedIcons = new File(extractedFiles, "icons");
            if (extractedArchive.isDirectory()) {
                ensureDir(archive);
                mergeDirectory(extractedArchive, archive);
            }
            if (extractedIcons.isDirectory()) {
                ensureDir(icons);
                mergeDirectory(extractedIcons, icons);
            }
            restorePreferences(extracted);
            WorkManager.getInstance(context).cancelAllWork();
            deleteRecursively(safety);
            deleteRecursively(new File(context.getCacheDir(), "image_manager_disk_cache"));
        } catch (IOException | RuntimeException e) {
            if (movedOld) {
                try {
                    File db = context.getDatabasePath(DB_NAME);
                    deleteRecursively(db);
                    deleteRecursively(new File(db.getPath() + "-wal"));
                    deleteRecursively(new File(db.getPath() + "-shm"));
                    File archive = new File(context.getFilesDir(), "archive");
                    File icons = new File(context.getFilesDir(), "icons");
                    deleteRecursively(archive);
                    deleteRecursively(icons);
                    moveIfExists(new File(safetyDatabase, DB_NAME), db);
                    moveIfExists(new File(safetyDatabase, DB_NAME + "-wal"),
                            new File(db.getPath() + "-wal"));
                    moveIfExists(new File(safetyDatabase, DB_NAME + "-shm"),
                            new File(db.getPath() + "-shm"));
                    moveIfExists(new File(safetyFiles, "archive"), archive);
                    moveIfExists(new File(safetyFiles, "icons"), icons);
                } catch (IOException restoreError) {
                    Log.e(TAG, "Could not roll back failed restore", restoreError);
                }
            }
            throw new IOException("Restore failed: " + e.getMessage(), e);
        } finally {
            deleteRecursively(safety);
            deleteRecursively(extracted);
        }
    }

    public void cleanupPreparedRestore(RestorePreview preview) {
        if (preview != null) {
            deleteRecursively(preview.extractedDirectory);
        }
    }

    public void exportPersonalData(OutputStream outputStream) throws IOException {
        ArticleDao articleDao = database.articleDao();
        ArticleTagDao tagDao = database.articleTagDao();
        ArticleNoteDao noteDao = database.articleNoteDao();
        OutputStreamWriter writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8);
        JsonWriter json = new JsonWriter(writer);
        json.setIndent("  ");
        json.beginObject();
        json.name("exportedAt").value(System.currentTimeMillis());
        json.name("appVersion").value(BuildConfig.VERSION_NAME);
        json.name("preferences").jsonValue(gson.toJson(preferencesToJson()));
        json.name("feeds").jsonValue(gson.toJson(database.feedDao().getAllActive()));
        json.name("categories").jsonValue(gson.toJson(database.categoryDao().getAll()));
        json.name("keywordRules").jsonValue(gson.toJson(database.keywordRuleDao().getAll()));
        json.name("filterRules").jsonValue(gson.toJson(database.filterRuleDao().getAll()));
        json.name("savedSearches").jsonValue(gson.toJson(database.savedSearchDao().getAll()));
        json.name("articles").beginArray();
        int offset = 0;
        while (true) {
            java.util.List<ArticleEntity> page = articleDao.getPage(PAGE, offset);
            if (page.isEmpty()) {
                break;
            }
            for (ArticleEntity article : page) {
                json.beginObject();
                json.name("id").value(article.id);
                json.name("title").value(article.title == null ? "" : article.title);
                json.name("originalUrl").value(article.originalUrl == null
                        ? "" : article.originalUrl);
                json.name("publisher").value(article.publisher == null
                        ? "" : article.publisher);
                json.name("author").value(article.author == null ? "" : article.author);
                json.name("publishedAt").value(article.publishedAt);
                json.name("firstSeenAt").value(article.firstSeenAt);
                json.name("isRead").value(article.isRead);
                json.name("isBookmarked").value(article.isBookmarked);
                json.name("isArchived").value(article.isArchived);
                json.name("relevanceScore").value(article.relevanceScore);
                json.name("tags").beginArray();
                for (ArticleTagEntity tag : tagDao.getForArticle(article.id)) {
                    json.value(tag.tag);
                }
                json.endArray();
                ArticleNoteEntity note = noteDao.getForArticle(article.id);
                json.name("note").value(note == null ? "" : note.note);
                json.name("history").beginObject();
                ReadingHistoryEntity history = database.readingHistoryDao()
                        .getForArticle(article.id);
                if (history != null) {
                    json.name("openedAt").value(history.openedAt);
                    json.name("lastReadAt").value(history.lastReadAt);
                    json.name("progress").value(history.progress);
                    json.name("secondsRead").value(history.secondsRead);
                }
                json.endObject();
                json.endObject();
                json.flush();
            }
            offset += page.size();
            if (page.size() < PAGE) {
                break;
            }
        }
        json.endArray();
        json.name("notificationHistory").jsonValue(gson.toJson(
                database.notificationDao().getRecent(5000)));
        json.endObject();
        json.flush();
        writer.flush();
    }

    public void deleteAllData() {
        WorkManager.getInstance(context).cancelAllWork();
        AppDatabase.closeForRestore();
        SharedPreferences prefs = context.getSharedPreferences(
                SettingsRepository.PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
        File files = context.getFilesDir();
        File[] children = files.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursively(child);
            }
        }
        File dbDir = context.getDatabasePath(DB_NAME).getParentFile();
        if (dbDir != null) {
            File[] dbFiles = dbDir.listFiles();
            if (dbFiles != null) {
                for (File file : dbFiles) {
                    if (file.getName().startsWith(DB_NAME)) {
                        deleteRecursively(file);
                    }
                }
            }
        }
        File cache = context.getCacheDir();
        File[] cacheFiles = cache.listFiles();
        if (cacheFiles != null) {
            for (File child : cacheFiles) {
                deleteRecursively(child);
            }
        }
        File external = context.getExternalFilesDir(null);
        deleteRecursively(external);
    }

    public static void restartProcess() {
        Process.killProcess(Process.myPid());
        System.exit(0);
    }

    private void restorePreferences(File extracted) throws IOException {
        File manifestFile = new File(extracted, "backup.json");
        JsonObject manifest = JsonParser.parseString(readText(manifestFile)).getAsJsonObject();
        JsonObject prefs = manifest.has("preferences")
                && manifest.get("preferences").isJsonObject()
                ? manifest.getAsJsonObject("preferences") : new JsonObject();
        Map<String, Object> values = preferencesFromJson(prefs);
        settings.restore(values);
    }

    private JsonObject preferencesToJson() {
        JsonObject root = new JsonObject();
        for (Map.Entry<String, ?> entry : settings.snapshot().entrySet()) {
            JsonObject item = new JsonObject();
            Object value = entry.getValue();
            if (value instanceof Boolean) {
                item.addProperty("type", "boolean");
                item.addProperty("value", (Boolean) value);
            } else if (value instanceof Integer) {
                item.addProperty("type", "int");
                item.addProperty("value", (Integer) value);
            } else if (value instanceof Long) {
                item.addProperty("type", "long");
                item.addProperty("value", (Long) value);
            } else if (value instanceof Float) {
                item.addProperty("type", "float");
                item.addProperty("value", (Float) value);
            } else if (value instanceof Collection) {
                item.addProperty("type", "set");
                JsonArray array = new JsonArray();
                for (Object element : (Collection<?>) value) {
                    array.add(String.valueOf(element));
                }
                item.add("value", array);
            } else {
                item.addProperty("type", "string");
                item.addProperty("value", value == null ? "" : String.valueOf(value));
            }
            root.add(entry.getKey(), item);
        }
        return root;
    }

    private Map<String, Object> preferencesFromJson(JsonObject root) {
        Map<String, Object> values = new HashMap<>();
        for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
            if (!entry.getValue().isJsonObject()) {
                continue;
            }
            JsonObject item = entry.getValue().getAsJsonObject();
            String type = item.has("type") ? item.get("type").getAsString() : "string";
            JsonElement value = item.get("value");
            switch (type) {
                case "boolean":
                    values.put(entry.getKey(), value.getAsBoolean());
                    break;
                case "int":
                    values.put(entry.getKey(), value.getAsInt());
                    break;
                case "long":
                    values.put(entry.getKey(), value.getAsLong());
                    break;
                case "float":
                    values.put(entry.getKey(), value.getAsFloat());
                    break;
                case "set":
                    java.util.HashSet<String> set = new java.util.HashSet<>();
                    if (value.isJsonArray()) {
                        for (JsonElement element : value.getAsJsonArray()) {
                            set.add(element.getAsString());
                        }
                    }
                    values.put(entry.getKey(), set);
                    break;
                default:
                    values.put(entry.getKey(), value.getAsString());
                    break;
            }
        }
        return values;
    }

    private void addDirectory(ZipOutputStream zip, File directory, String prefix)
            throws IOException {
        if (directory == null || !directory.isDirectory()) {
            return;
        }
        File[] children = directory.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            if (child.getName().equals(".tmp")) {
                continue;
            }
            if (child.isDirectory()) {
                addDirectory(zip, child, prefix + child.getName() + "/");
            } else {
                addFile(zip, child, prefix + child.getName());
            }
        }
    }

    private void addIfExists(ZipOutputStream zip, File file, String name) throws IOException {
        if (file.isFile()) {
            addFile(zip, file, name);
        }
    }

    private void addFile(ZipOutputStream zip, File file, String name) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        entry.setTime(file.lastModified());
        zip.putNextEntry(entry);
        try (InputStream in = new FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                zip.write(buffer, 0, read);
            }
        }
        zip.closeEntry();
    }

    private static boolean isAllowedEntry(String name) {
        if (name.startsWith("/") || name.contains("..") || name.contains(":")) {
            return false;
        }
        return name.equals("backup.json")
                || name.equals("database/" + DB_NAME)
                || name.equals("database/" + DB_NAME + "-wal")
                || name.equals("database/" + DB_NAME + "-shm")
                || name.startsWith("files/archive/")
                || name.startsWith("files/icons/");
    }

    private void mergeDirectory(File source, File target) throws IOException {
        File[] children = source.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            File dest = new File(target, child.getName());
            if (child.isDirectory()) {
                ensureDir(dest);
                mergeDirectory(child, dest);
            } else {
                copy(child, dest);
            }
        }
    }

    private static void ensureDir(File directory) throws IOException {
        if (directory != null && !directory.exists() && !directory.mkdirs()) {
            throw new IOException("Could not create directory");
        }
    }

    private static void copy(File source, File target) throws IOException {
        ensureDir(target.getParentFile());
        try (InputStream in = new FileInputStream(source);
             OutputStream out = new BufferedOutputStream(new FileOutputStream(target))) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            out.flush();
        }
    }

    private static void copyIfExists(File source, File target) throws IOException {
        if (source.isFile()) {
            copy(source, target);
        }
    }

    private static void moveIfExists(File source, File target) throws IOException {
        if (source == null || !source.exists()) {
            return;
        }
        ensureDir(target.getParentFile());
        if (target.exists()) {
            deleteRecursively(target);
        }
        if (source.renameTo(target)) {
            return;
        }
        if (source.isDirectory()) {
            ensureDir(target);
            mergeDirectoryStatic(source, target);
            deleteRecursively(source);
        } else {
            copy(source, target);
            deleteRecursively(source);
        }
    }

    private static void mergeDirectoryStatic(File source, File target) throws IOException {
        File[] children = source.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            File dest = new File(target, child.getName());
            if (child.isDirectory()) {
                ensureDir(dest);
                mergeDirectoryStatic(child, dest);
            } else {
                copy(child, dest);
            }
        }
    }

    private static String readText(File file) throws IOException {
        try (InputStream input = new FileInputStream(file)) {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return out.toString(StandardCharsets.UTF_8.name());
        }
    }

    private static String escapeSql(String value) {
        return value.replace("'", "''");
    }

    public static void deleteRecursively(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        if (!file.delete()) {
            Log.w(TAG, "Could not delete " + file.getAbsolutePath());
        }
    }
}
