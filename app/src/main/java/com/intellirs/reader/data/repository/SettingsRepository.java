package com.intellirs.reader.data.repository;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.intellirs.reader.data.entity.ArchiveMode;
import com.intellirs.reader.data.entity.FeedImportance;

import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Small, synchronous settings facade backed by SharedPreferences. It is safe to read from
 * workers and exposes a revision LiveData for screens that need to react to changes.
 */
public class SettingsRepository {
    public static final String PREFS_NAME = "intellirss_settings";

    public static final String KEY_THEME = "theme";
    public static final String KEY_ACCENT = "accent";
    public static final String KEY_APP_FONT_SCALE = "app_font_scale";
    public static final String KEY_READER_FONT_SIZE = "reader_font_size";
    public static final String KEY_READER_FONT_FAMILY = "reader_font_family";
    public static final String KEY_READER_LINE_SPACING = "reader_line_spacing";
    public static final String KEY_READER_THEME = "reader_theme";
    public static final String KEY_LIST_DENSITY = "list_density";
    public static final String KEY_SHOW_THUMBNAILS = "show_thumbnails";
    public static final String KEY_SHOW_SUMMARIES = "show_summaries";
    public static final String KEY_SORT_FEEDS = "sort_feeds";
    public static final String KEY_COLLAPSED_CATEGORIES = "collapsed_categories";

    public static final String KEY_SYNC_INTERVAL = "sync_interval";
    public static final String KEY_SYNC_WIFI_ONLY = "sync_wifi_only";
    public static final String KEY_SYNC_BATTERY_NOT_LOW = "sync_battery_not_low";
    public static final String KEY_ALLOW_HTTP = "allow_http";
    public static final String KEY_LAST_SYNC_STATUS = "last_sync_status";
    public static final String KEY_LAST_SYNC_TIME = "last_sync_time";

    public static final String KEY_NOTIFICATIONS_ENABLED = "notifications_enabled";
    public static final String KEY_NOTIFICATION_PERMISSION_ASKED = "notification_permission_asked";
    public static final String KEY_NOTIFY_FEED_THRESHOLD = "notify_feed_threshold";
    public static final String KEY_NOTIFY_MIN_RELEVANCE = "notify_min_relevance";
    public static final String KEY_QUIET_ENABLED = "quiet_enabled";
    public static final String KEY_QUIET_START = "quiet_start";
    public static final String KEY_QUIET_END = "quiet_end";
    public static final String KEY_QUIET_ALLOW_CRITICAL = "quiet_allow_critical";
    public static final String KEY_NOTIFICATION_GROUPING = "notification_grouping";
    public static final String KEY_MAX_NOTIFICATIONS_HOUR = "max_notifications_hour";
    public static final String KEY_MAX_NOTIFICATIONS_DAY = "max_notifications_day";

    public static final String KEY_THRESHOLD_CRITICAL = "threshold_critical";
    public static final String KEY_THRESHOLD_HIGH = "threshold_high";
    public static final String KEY_THRESHOLD_NORMAL = "threshold_normal";
    public static final String KEY_THRESHOLD_LOW = "threshold_low";
    public static final String KEY_AUTO_FEED_PRIORITIZATION = "auto_feed_prioritization";
    public static final String KEY_DUPLICATE_MERGE = "duplicate_merge";
    public static final String KEY_SHOW_ALL_SOURCES = "show_all_sources";
    public static final String KEY_SIMILAR_ARTICLES = "similar_articles";
    public static final String KEY_PREFERRED_AUTHORS = "preferred_authors";

    public static final String KEY_ARCHIVE_MODE = "archive_mode";
    public static final String KEY_ARCHIVE_MAX_MB = "archive_max_mb";
    public static final String KEY_ARCHIVE_IMAGE_MAX_MB = "archive_image_max_mb";
    public static final String KEY_ARCHIVE_IMAGE_QUALITY = "archive_image_quality";
    public static final String KEY_ARCHIVE_AUTO_CLEANUP = "archive_auto_cleanup";
    public static final String KEY_DOWNLOAD_PODCAST = "download_podcast";
    public static final String KEY_AUDIO_MAX_MB = "audio_max_mb";

    public static final String KEY_LOCAL_AI_ENABLED = "local_ai_enabled";
    public static final String KEY_OLLAMA_URL = "ollama_url";
    public static final String KEY_OLLAMA_MODEL = "ollama_model";
    public static final String KEY_LOCAL_AI_LAST_TEST = "local_ai_last_test";

    public static final String KEY_SEARCH_FILTER_JSON = "search_filter_json";

    private final SharedPreferences prefs;
    private final MutableLiveData<Long> revision = new MutableLiveData<>(0L);
    private final SharedPreferences.OnSharedPreferenceChangeListener listener;

    public SettingsRepository(Context context) {
        prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        listener = (sharedPreferences, key) -> {
            Long current = revision.getValue();
            revision.postValue(current == null ? 1L : current + 1L);
        };
        prefs.registerOnSharedPreferenceChangeListener(listener);
    }

    public LiveData<Long> observeRevision() {
        return revision;
    }

    public SharedPreferences raw() {
        return prefs;
    }

    private void putString(String key, String value) {
        prefs.edit().putString(key, value).apply();
    }

    private void putInt(String key, int value) {
        prefs.edit().putInt(key, value).apply();
    }

    private void putBoolean(String key, boolean value) {
        prefs.edit().putBoolean(key, value).apply();
    }

    public String getThemeMode() {
        return prefs.getString(KEY_THEME, "dark");
    }

    public void setThemeMode(String value) {
        putString(KEY_THEME, value);
    }

    public String getAccent() {
        return prefs.getString(KEY_ACCENT, "teal");
    }

    public void setAccent(String value) {
        putString(KEY_ACCENT, value);
    }

    public float getAppFontScale() {
        return prefs.getFloat(KEY_APP_FONT_SCALE, 1f);
    }

    public void setAppFontScale(float value) {
        prefs.edit().putFloat(KEY_APP_FONT_SCALE, value).apply();
    }

    public int getReaderFontSize() {
        return prefs.getInt(KEY_READER_FONT_SIZE, 18);
    }

    public void setReaderFontSize(int value) {
        putInt(KEY_READER_FONT_SIZE, clamp(value, 12, 32));
    }

    public String getReaderFontFamily() {
        return prefs.getString(KEY_READER_FONT_FAMILY, "serif");
    }

    public void setReaderFontFamily(String value) {
        putString(KEY_READER_FONT_FAMILY, value);
    }

    public float getReaderLineSpacing() {
        return prefs.getFloat(KEY_READER_LINE_SPACING, 1.45f);
    }

    public void setReaderLineSpacing(float value) {
        prefs.edit().putFloat(KEY_READER_LINE_SPACING, Math.max(1f, Math.min(2.2f, value))).apply();
    }

    public String getReaderTheme() {
        return prefs.getString(KEY_READER_THEME, "dark");
    }

    public void setReaderTheme(String value) {
        putString(KEY_READER_THEME, value);
    }

    public String getListDensity() {
        return prefs.getString(KEY_LIST_DENSITY, "comfortable");
    }

    public void setListDensity(String value) {
        putString(KEY_LIST_DENSITY, value);
    }

    public boolean showThumbnails() {
        return prefs.getBoolean(KEY_SHOW_THUMBNAILS, true);
    }

    public void setShowThumbnails(boolean value) {
        putBoolean(KEY_SHOW_THUMBNAILS, value);
    }

    public boolean showSummaries() {
        return prefs.getBoolean(KEY_SHOW_SUMMARIES, true);
    }

    public void setShowSummaries(boolean value) {
        putBoolean(KEY_SHOW_SUMMARIES, value);
    }

    public String getFeedSort() {
        return prefs.getString(KEY_SORT_FEEDS, "priority");
    }

    public void setFeedSort(String value) {
        putString(KEY_SORT_FEEDS, value);
    }

    public Set<String> getCollapsedCategories() {
        return prefs.getStringSet(KEY_COLLAPSED_CATEGORIES, new java.util.HashSet<>());
    }

    public void setCollapsedCategories(Set<String> ids) {
        prefs.edit().putStringSet(KEY_COLLAPSED_CATEGORIES, new java.util.HashSet<>(ids)).apply();
    }

    public int getSyncIntervalMinutes() {
        return prefs.getInt(KEY_SYNC_INTERVAL, 30);
    }

    public void setSyncIntervalMinutes(int value) {
        putInt(KEY_SYNC_INTERVAL, Math.max(15, value));
    }

    public boolean isWifiOnly() {
        return prefs.getBoolean(KEY_SYNC_WIFI_ONLY, false);
    }

    public void setWifiOnly(boolean value) {
        putBoolean(KEY_SYNC_WIFI_ONLY, value);
    }

    public boolean isBatteryNotLowRequired() {
        return prefs.getBoolean(KEY_SYNC_BATTERY_NOT_LOW, false);
    }

    public void setBatteryNotLowRequired(boolean value) {
        putBoolean(KEY_SYNC_BATTERY_NOT_LOW, value);
    }

    public boolean isAllowHttp() {
        return prefs.getBoolean(KEY_ALLOW_HTTP, false);
    }

    public void setAllowHttp(boolean value) {
        putBoolean(KEY_ALLOW_HTTP, value);
    }

    public String getLastSyncStatus() {
        return prefs.getString(KEY_LAST_SYNC_STATUS, "");
    }

    public void setLastSyncStatus(String value) {
        putString(KEY_LAST_SYNC_STATUS, value);
    }

    public long getLastSyncTime() {
        return prefs.getLong(KEY_LAST_SYNC_TIME, 0);
    }

    public void setLastSyncTime(long value) {
        prefs.edit().putLong(KEY_LAST_SYNC_TIME, value).apply();
    }

    public boolean notificationsEnabled() {
        return prefs.getBoolean(KEY_NOTIFICATIONS_ENABLED, true);
    }

    public void setNotificationsEnabled(boolean value) {
        putBoolean(KEY_NOTIFICATIONS_ENABLED, value);
    }

    public boolean isNotificationPermissionAsked() {
        return prefs.getBoolean(KEY_NOTIFICATION_PERMISSION_ASKED, false);
    }

    public void setNotificationPermissionAsked(boolean value) {
        putBoolean(KEY_NOTIFICATION_PERMISSION_ASKED, value);
    }

    public FeedImportance getNotificationFeedThreshold() {
        return FeedImportance.fromName(
                prefs.getString(KEY_NOTIFY_FEED_THRESHOLD, FeedImportance.HIGH.name()),
                FeedImportance.HIGH);
    }

    public void setNotificationFeedThreshold(FeedImportance value) {
        putString(KEY_NOTIFY_FEED_THRESHOLD, value.name());
    }

    public int getNotificationMinRelevance() {
        return prefs.getInt(KEY_NOTIFY_MIN_RELEVANCE, 65);
    }

    public void setNotificationMinRelevance(int value) {
        putInt(KEY_NOTIFY_MIN_RELEVANCE, clamp(value, 0, 100));
    }

    public boolean quietHoursEnabled() {
        return prefs.getBoolean(KEY_QUIET_ENABLED, false);
    }

    public void setQuietHoursEnabled(boolean value) {
        putBoolean(KEY_QUIET_ENABLED, value);
    }

    public int getQuietStartMinutes() {
        return prefs.getInt(KEY_QUIET_START, 22 * 60);
    }

    public void setQuietStartMinutes(int value) {
        putInt(KEY_QUIET_START, clamp(value, 0, 1439));
    }

    public int getQuietEndMinutes() {
        return prefs.getInt(KEY_QUIET_END, 7 * 60);
    }

    public void setQuietEndMinutes(int value) {
        putInt(KEY_QUIET_END, clamp(value, 0, 1439));
    }

    public boolean allowCriticalDuringQuietHours() {
        return prefs.getBoolean(KEY_QUIET_ALLOW_CRITICAL, false);
    }

    public void setAllowCriticalDuringQuietHours(boolean value) {
        putBoolean(KEY_QUIET_ALLOW_CRITICAL, value);
    }

    public String getNotificationGrouping() {
        return prefs.getString(KEY_NOTIFICATION_GROUPING, "PUBLISHER");
    }

    public void setNotificationGrouping(String value) {
        putString(KEY_NOTIFICATION_GROUPING, value);
    }

    public int getMaxNotificationsPerHour() {
        return prefs.getInt(KEY_MAX_NOTIFICATIONS_HOUR, 6);
    }

    public void setMaxNotificationsPerHour(int value) {
        putInt(KEY_MAX_NOTIFICATIONS_HOUR, clamp(value, 1, 60));
    }

    public int getMaxNotificationsPerDay() {
        return prefs.getInt(KEY_MAX_NOTIFICATIONS_DAY, 40);
    }

    public void setMaxNotificationsPerDay(int value) {
        putInt(KEY_MAX_NOTIFICATIONS_DAY, clamp(value, 1, 500));
    }

    public int getCriticalThreshold() {
        return prefs.getInt(KEY_THRESHOLD_CRITICAL, 85);
    }

    public int getHighThreshold() {
        return prefs.getInt(KEY_THRESHOLD_HIGH, 65);
    }

    public int getNormalThreshold() {
        return prefs.getInt(KEY_THRESHOLD_NORMAL, 35);
    }

    public int getLowThreshold() {
        return prefs.getInt(KEY_THRESHOLD_LOW, 15);
    }

    public void setThresholds(int critical, int high, int normal, int low) {
        if (critical <= high || high <= normal || normal <= low || low < 0 || critical > 100) {
            throw new IllegalArgumentException("Relevance thresholds must decrease from 100 to 0");
        }
        prefs.edit()
                .putInt(KEY_THRESHOLD_CRITICAL, critical)
                .putInt(KEY_THRESHOLD_HIGH, high)
                .putInt(KEY_THRESHOLD_NORMAL, normal)
                .putInt(KEY_THRESHOLD_LOW, low)
                .apply();
    }

    public boolean autoFeedPrioritization() {
        return prefs.getBoolean(KEY_AUTO_FEED_PRIORITIZATION, true);
    }

    public void setAutoFeedPrioritization(boolean value) {
        putBoolean(KEY_AUTO_FEED_PRIORITIZATION, value);
    }

    public boolean mergeDuplicates() {
        return prefs.getBoolean(KEY_DUPLICATE_MERGE, true);
    }

    public void setMergeDuplicates(boolean value) {
        putBoolean(KEY_DUPLICATE_MERGE, value);
    }

    public boolean showAllSources() {
        return prefs.getBoolean(KEY_SHOW_ALL_SOURCES, true);
    }

    public void setShowAllSources(boolean value) {
        putBoolean(KEY_SHOW_ALL_SOURCES, value);
    }

    public boolean similarArticlesEnabled() {
        return prefs.getBoolean(KEY_SIMILAR_ARTICLES, true);
    }

    public void setSimilarArticlesEnabled(boolean value) {
        putBoolean(KEY_SIMILAR_ARTICLES, value);
    }

    public String getPreferredAuthors() {
        return prefs.getString(KEY_PREFERRED_AUTHORS, "");
    }

    public void setPreferredAuthors(String value) {
        putString(KEY_PREFERRED_AUTHORS, value == null ? "" : value.trim());
    }

    public ArchiveMode getDefaultArchiveMode() {
        return ArchiveMode.valueOf(prefs.getString(KEY_ARCHIVE_MODE, ArchiveMode.PRIORITY.name()));
    }

    public void setDefaultArchiveMode(ArchiveMode value) {
        putString(KEY_ARCHIVE_MODE, value.name());
    }

    public long getArchiveMaxBytes() {
        return prefs.getInt(KEY_ARCHIVE_MAX_MB, 2048) * 1024L * 1024L;
    }

    public int getArchiveMaxMb() {
        return prefs.getInt(KEY_ARCHIVE_MAX_MB, 2048);
    }

    public void setArchiveMaxMb(int value) {
        putInt(KEY_ARCHIVE_MAX_MB, Math.max(50, value));
    }

    public long getImageMaxBytes() {
        return prefs.getInt(KEY_ARCHIVE_IMAGE_MAX_MB, 8) * 1024L * 1024L;
    }

    public int getImageMaxMb() {
        return prefs.getInt(KEY_ARCHIVE_IMAGE_MAX_MB, 8);
    }

    public void setImageMaxMb(int value) {
        putInt(KEY_ARCHIVE_IMAGE_MAX_MB, Math.max(1, value));
    }

    public int getImageQuality() {
        return prefs.getInt(KEY_ARCHIVE_IMAGE_QUALITY, 80);
    }

    public void setImageQuality(int value) {
        putInt(KEY_ARCHIVE_IMAGE_QUALITY, clamp(value, 40, 100));
    }

    public boolean archiveAutoCleanup() {
        return prefs.getBoolean(KEY_ARCHIVE_AUTO_CLEANUP, true);
    }

    public void setArchiveAutoCleanup(boolean value) {
        putBoolean(KEY_ARCHIVE_AUTO_CLEANUP, value);
    }

    public boolean downloadPodcast() {
        return prefs.getBoolean(KEY_DOWNLOAD_PODCAST, false);
    }

    public void setDownloadPodcast(boolean value) {
        putBoolean(KEY_DOWNLOAD_PODCAST, value);
    }

    public long getAudioMaxBytes() {
        return prefs.getInt(KEY_AUDIO_MAX_MB, 100) * 1024L * 1024L;
    }

    public int getAudioMaxMb() {
        return prefs.getInt(KEY_AUDIO_MAX_MB, 100);
    }

    public void setAudioMaxMb(int value) {
        putInt(KEY_AUDIO_MAX_MB, Math.max(10, value));
    }

    public boolean localAiEnabled() {
        return prefs.getBoolean(KEY_LOCAL_AI_ENABLED, false);
    }

    public void setLocalAiEnabled(boolean value) {
        putBoolean(KEY_LOCAL_AI_ENABLED, value);
    }

    public String getOllamaUrl() {
        return prefs.getString(KEY_OLLAMA_URL, "http://10.0.2.2:11434");
    }

    public void setOllamaUrl(String value) {
        putString(KEY_OLLAMA_URL, value == null ? "" : value.trim());
    }

    public String getOllamaModel() {
        return prefs.getString(KEY_OLLAMA_MODEL, "llama3.2");
    }

    public void setOllamaModel(String value) {
        putString(KEY_OLLAMA_MODEL, value == null ? "" : value.trim());
    }

    public String getSearchFilterJson() {
        return prefs.getString(KEY_SEARCH_FILTER_JSON, "");
    }

    public void setSearchFilterJson(String json) {
        putString(KEY_SEARCH_FILTER_JSON, json);
    }

    public boolean isWithinQuietHours(LocalTime now) {
        if (!quietHoursEnabled()) {
            return false;
        }
        LocalTime start = LocalTime.of(getQuietStartMinutes() / 60, getQuietStartMinutes() % 60);
        LocalTime end = LocalTime.of(getQuietEndMinutes() / 60, getQuietEndMinutes() % 60);
        if (start.equals(end)) {
            return true;
        }
        if (start.isBefore(end)) {
            return !now.isBefore(start) && now.isBefore(end);
        }
        return !now.isBefore(start) || now.isBefore(end);
    }

    public Map<String, Object> snapshot() {
        Map<String, Object> map = new HashMap<>();
        for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
            map.put(entry.getKey(), entry.getValue());
        }
        return map;
    }

    public void restore(Map<String, Object> values) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.clear();
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String) {
                editor.putString(entry.getKey(), (String) value);
            } else if (value instanceof Boolean) {
                editor.putBoolean(entry.getKey(), (Boolean) value);
            } else if (value instanceof Integer) {
                editor.putInt(entry.getKey(), (Integer) value);
            } else if (value instanceof Long) {
                editor.putLong(entry.getKey(), (Long) value);
            } else if (value instanceof Float) {
                editor.putFloat(entry.getKey(), (Float) value);
            } else if (value instanceof Number) {
                double number = ((Number) value).doubleValue();
                if (number == Math.rint(number)) {
                    editor.putInt(entry.getKey(), (int) number);
                } else {
                    editor.putFloat(entry.getKey(), (float) number);
                }
            } else if (value instanceof java.util.Collection) {
                try {
                    java.util.HashSet<String> set = new java.util.HashSet<>();
                    for (Object item : (java.util.Collection<?>) value) {
                        set.add(String.valueOf(item));
                    }
                    editor.putStringSet(entry.getKey(), set);
                } catch (RuntimeException ignored) {
                    // Unknown preference shape: skip this key instead of corrupting settings.
                }
            }
        }
        editor.apply();
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
