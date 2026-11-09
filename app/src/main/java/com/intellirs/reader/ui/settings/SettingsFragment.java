package com.intellirs.reader.ui.settings;

import android.Manifest;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TimePicker;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LiveData;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;
import androidx.preference.SeekBarPreference;
import androidx.preference.SwitchPreferenceCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.gson.Gson;
import com.intellirs.reader.BuildConfig;
import com.intellirs.reader.R;
import com.intellirs.reader.data.archive.ArchiveExporter;
import com.intellirs.reader.data.archive.CleanupResult;
import com.intellirs.reader.data.backup.BackupManager;
import com.intellirs.reader.data.backup.RestorePreview;
import com.intellirs.reader.data.entity.ArchiveMode;
import com.intellirs.reader.data.entity.CategoryEntity;
import com.intellirs.reader.data.entity.FeedEntity;
import com.intellirs.reader.data.entity.FeedImportance;
import com.intellirs.reader.data.entity.KeywordType;
import com.intellirs.reader.data.repository.SettingsRepository;
import com.intellirs.reader.ui.BaseActivity;
import com.intellirs.reader.ui.archive.StorageActivity;
import com.intellirs.reader.ui.common.OpmlImportFlow;
import com.intellirs.reader.ui.filters.FilterListActivity;
import com.intellirs.reader.ui.notifications.NotificationHistoryActivity;
import com.intellirs.reader.ui.opml.OpmlPreviewActivity;
import com.intellirs.reader.util.DateUtils;
import com.intellirs.reader.util.ThemeManager;
import com.intellirs.reader.worker.WorkScheduler;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/** Programmatic Material preference screen with all settings wired to real behavior. */
public class SettingsFragment extends PreferenceFragmentCompat {

    private interface StringConsumer {
        void accept(String value);
    }

    private interface ClickAction {
        void onClick(Preference preference);
    }

    private ActivityResultLauncher<String> notificationPermission;
    private ActivityResultLauncher<String[]> opmlImport;
    private ActivityResultLauncher<String> opmlExport;
    private ActivityResultLauncher<String> backupCreate;
    private ActivityResultLauncher<String[]> backupRestore;
    private ActivityResultLauncher<String> archiveExport;
    private ActivityResultLauncher<String> dataExport;
    private ArchiveExporter.Format pendingArchiveFormat;
    private Preference lastSyncPreference;
    private Preference notificationPermissionPreference;
    private Preference quietStartPreference;
    private Preference quietEndPreference;
    private Preference aiTestPreference;
    private final Gson gson = new Gson();

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        notificationPermission = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(), granted -> {
                    updateNotificationPermissionSummary();
                    Toast.makeText(requireContext(), granted
                                    ? R.string.notifications_status_on
                                    : R.string.permission_notifications_denied,
                            Toast.LENGTH_SHORT).show();
                });
        opmlImport = registerForActivityResult(new ActivityResultContracts.OpenDocument(),
                uri -> {
                    if (uri != null) {
                        importOpml(uri);
                    }
                });
        opmlExport = registerForActivityResult(new ActivityResultContracts.CreateDocument("text/xml"),
                this::writeOpmlExport);
        backupCreate = registerForActivityResult(
                new ActivityResultContracts.CreateDocument("application/zip"),
                this::writeFullBackup);
        backupRestore = registerForActivityResult(new ActivityResultContracts.OpenDocument(),
                this::prepareRestore);
        archiveExport = registerForActivityResult(
                new ActivityResultContracts.CreateDocument("*/*"), this::writeArchiveExport);
        dataExport = registerForActivityResult(
                new ActivityResultContracts.CreateDocument("application/json"),
                this::writePersonalData);
    }

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        SettingsRepository settings = container().settingsRepository();
        PreferenceScreen screen = getPreferenceManager()
                .createPreferenceScreen(requireContext());
        setPreferenceScreen(screen);

        // Appearance -------------------------------------------------------------------
        PreferenceCategory appearance = category(R.string.settings_appearance);
        screen.addPreference(appearance);
        appearance.addPreference(listPreference(R.string.settings_theme,
                new String[]{"Dark", "Light", "System"},
                new String[]{"dark", "light", "system"}, settings.getThemeMode(),
                value -> {
                    settings.setThemeMode(value);
                    ThemeManager.applyNightMode(requireContext());
                }));
        appearance.addPreference(listPreference(R.string.settings_accent,
                new String[]{"Teal", "Blue", "Cyan", "Amber"},
                new String[]{"teal", "blue", "cyan", "amber"}, settings.getAccent(),
                value -> {
                    settings.setAccent(value);
                    requireActivity().recreate();
                }));
        appearance.addPreference(listPreference(R.string.settings_font_size,
                new String[]{"Small", "Default", "Large", "Larger"},
                new String[]{"0.90", "1.00", "1.15", "1.30"},
                String.format(Locale.US, "%.2f", settings.getAppFontScale()),
                value -> {
                    settings.setAppFontScale(Float.parseFloat(value));
                    requireActivity().recreate();
                }));
        appearance.addPreference(listPreference(R.string.settings_reader_font_size,
                new String[]{"14 sp", "16 sp", "18 sp", "20 sp", "22 sp", "24 sp", "26 sp"},
                new String[]{"14", "16", "18", "20", "22", "24", "26"},
                String.valueOf(settings.getReaderFontSize()),
                value -> settings.setReaderFontSize(Integer.parseInt(value))));
        appearance.addPreference(listPreference(R.string.settings_reader_font,
                new String[]{"Serif", "Sans serif", "Monospace"},
                new String[]{"serif", "sans", "mono"}, settings.getReaderFontFamily(),
                value -> settings.setReaderFontFamily(value)));
        appearance.addPreference(listPreference(R.string.settings_reader_line_spacing,
                new String[]{"Compact (1.20)", "Normal (1.45)", "Relaxed (1.70)", "Airy (2.00)"},
                new String[]{"1.20", "1.45", "1.70", "2.00"},
                String.format(Locale.US, "%.2f", settings.getReaderLineSpacing()),
                value -> settings.setReaderLineSpacing(Float.parseFloat(value))));
        appearance.addPreference(listPreference(R.string.settings_reader_theme,
                new String[]{"Dark", "Light", "Sepia"},
                new String[]{"dark", "light", "sepia"}, settings.getReaderTheme(),
                value -> settings.setReaderTheme(value)));
        appearance.addPreference(listPreference(R.string.settings_list_density,
                new String[]{"Compact", "Comfortable", "Image rich"},
                new String[]{"compact", "comfortable", "image_rich"},
                settings.getListDensity(), value -> settings.setListDensity(value)));
        appearance.addPreference(switchPreference(R.string.show_thumbnails,
                settings.showThumbnails(), value -> settings.setShowThumbnails(value)));
        appearance.addPreference(switchPreference(R.string.show_summaries,
                settings.showSummaries(), value -> settings.setShowSummaries(value)));

        // Synchronization --------------------------------------------------------------
        PreferenceCategory sync = category(R.string.settings_sync);
        screen.addPreference(sync);
        sync.addPreference(listPreference(R.string.settings_sync_interval,
                new String[]{"15 minutes", "30 minutes", "1 hour", "3 hours", "6 hours",
                        "12 hours", "24 hours"},
                new String[]{"15", "30", "60", "180", "360", "720", "1440"},
                String.valueOf(settings.getSyncIntervalMinutes()),
                value -> {
                    settings.setSyncIntervalMinutes(Integer.parseInt(value));
                    container().workScheduler().ensurePeriodicWorkAsync();
                }));
        sync.addPreference(switchPreference(R.string.settings_sync_wifi,
                settings.isWifiOnly(), value -> {
                    settings.setWifiOnly(value);
                    container().workScheduler().ensurePeriodicWorkAsync();
                    container().workScheduler().refreshDailySyncAsync();
                }));
        sync.addPreference(switchPreference(R.string.settings_sync_battery,
                settings.isBatteryNotLowRequired(), value -> {
                    settings.setBatteryNotLowRequired(value);
                    container().workScheduler().ensurePeriodicWorkAsync();
                    container().workScheduler().refreshDailySyncAsync();
                }));
        Preference batteryHint = infoPreference(R.string.settings_sync_battery_hint);
        sync.addPreference(batteryHint);
        sync.addPreference(infoPreference(R.string.settings_sync_daily_hint));
        sync.addPreference(switchPreference(R.string.settings_sync_allow_http,
                settings.isAllowHttp(), settings::setAllowHttp,
                R.string.settings_sync_allow_http_hint));
        sync.addPreference(clickPreference(R.string.settings_sync_now,
                R.string.settings_sync_info, preference -> {
                    container().workScheduler().enqueueManualSync(true);
                    Toast.makeText(requireContext(), R.string.settings_sync_started,
                            Toast.LENGTH_SHORT).show();
                }));
        lastSyncPreference = infoPreference(R.string.settings_last_sync);
        sync.addPreference(lastSyncPreference);

        // Notifications ----------------------------------------------------------------
        PreferenceCategory notifications = category(R.string.settings_notifications);
        screen.addPreference(notifications);
        notifications.addPreference(switchPreference(R.string.settings_notifications_enabled,
                settings.notificationsEnabled(), settings::setNotificationsEnabled));
        notifications.addPreference(clickPreference(R.string.settings_feed_notifications,
                R.string.settings_feed_notifications_hint, preference ->
                        startActivity(new Intent(requireContext(),
                                FeedNotificationActivity.class))));
        notificationPermissionPreference = clickPreference(
                R.string.settings_notifications_permission, "",
                preference -> requestNotificationPermission());
        notifications.addPreference(notificationPermissionPreference);
        notifications.addPreference(listPreference(R.string.settings_notification_threshold_feed,
                new String[]{"Critical feeds only", "Critical and High feeds",
                        "All except Low and Muted feeds", "All feeds"},
                new String[]{"CRITICAL", "HIGH", "NORMAL", "ALL"},
                policyValue(settings.getNotificationFeedThreshold()),
                value -> {
                    if ("ALL".equals(value)) {
                        settings.setNotificationFeedThreshold(FeedImportance.MUTED);
                    } else {
                        settings.setNotificationFeedThreshold(FeedImportance.valueOf(value));
                    }
                }));
        SeekBarPreference relevance = seekPreference(R.string.settings_notification_threshold_article,
                settings.getNotificationMinRelevance(), 0, 100,
                value -> settings.setNotificationMinRelevance(value));
        notifications.addPreference(relevance);
        notifications.addPreference(switchPreference(R.string.settings_notification_quiet_hours,
                settings.quietHoursEnabled(), settings::setQuietHoursEnabled));
        quietStartPreference = clickPreference(R.string.settings_notification_quiet_start,
                formatTime(settings.getQuietStartMinutes()),
                preference -> pickTime(settings.getQuietStartMinutes(), minutes -> {
                    settings.setQuietStartMinutes(minutes);
                    quietStartPreference.setSummary(formatTime(minutes));
                }));
        notifications.addPreference(quietStartPreference);
        quietEndPreference = clickPreference(R.string.settings_notification_quiet_end,
                formatTime(settings.getQuietEndMinutes()),
                preference -> pickTime(settings.getQuietEndMinutes(), minutes -> {
                    settings.setQuietEndMinutes(minutes);
                    quietEndPreference.setSummary(formatTime(minutes));
                }));
        notifications.addPreference(quietEndPreference);
        notifications.addPreference(switchPreference(
                R.string.settings_notification_quiet_critical,
                settings.allowCriticalDuringQuietHours(),
                settings::setAllowCriticalDuringQuietHours));
        notifications.addPreference(listPreference(R.string.settings_notification_grouping,
                new String[]{"By publisher", "By topic", "No grouping"},
                new String[]{"PUBLISHER", "TOPIC", "NONE"},
                settings.getNotificationGrouping(), settings::setNotificationGrouping));
        notifications.addPreference(seekPreference(R.string.settings_notification_max_hour,
                settings.getMaxNotificationsPerHour(), 1, 30,
                settings::setMaxNotificationsPerHour));
        notifications.addPreference(seekPreference(R.string.settings_notification_max_day,
                settings.getMaxNotificationsPerDay(), 1, 200,
                settings::setMaxNotificationsPerDay));
        notifications.addPreference(clickPreference(R.string.settings_muted_keywords,
                getString(R.string.keywords_muted_title), preference ->
                        openKeywords(KeywordType.MUTED)));
        notifications.addPreference(clickPreference(R.string.settings_notification_history,
                "", preference -> startActivity(new Intent(requireContext(),
                        NotificationHistoryActivity.class))));
        notifications.addPreference(clickPreference(R.string.settings_notification_test,
                "", preference -> {
                    boolean posted = container().notificationHelper().postTestNotification();
                    Toast.makeText(requireContext(), posted
                                    ? R.string.settings_notification_test_sent
                                    : R.string.settings_notification_test_unavailable,
                            Toast.LENGTH_LONG).show();
                }));
        notifications.addPreference(clickPreference(R.string.settings_permissions,
                R.string.permission_notifications_rationale, preference ->
                        openAppNotificationSettings()));

        // Archive ----------------------------------------------------------------------
        PreferenceCategory archive = category(R.string.settings_archive);
        screen.addPreference(archive);
        archive.addPreference(listPreference(R.string.settings_archive_default_mode,
                new String[]{"Automatic", "Priority", "Manual"},
                new String[]{"AUTOMATIC", "PRIORITY", "MANUAL"},
                settings.getDefaultArchiveMode().name(),
                value -> settings.setDefaultArchiveMode(ArchiveMode.valueOf(value))));
        archive.addPreference(numberPreference(R.string.settings_archive_max_mb,
                String.valueOf(settings.getArchiveMaxMb()),
                value -> settings.setArchiveMaxMb(value)));
        archive.addPreference(numberPreference(R.string.settings_archive_image_mb,
                String.valueOf(settings.getImageMaxMb()),
                value -> settings.setImageMaxMb(value)));
        archive.addPreference(listPreference(R.string.settings_archive_image_quality,
                new String[]{"Original", "Smaller (80%)", "Smallest (60%)"},
                new String[]{"100", "80", "60"},
                String.valueOf(settings.getImageQuality()),
                value -> settings.setImageQuality(Integer.parseInt(value))));
        archive.addPreference(switchPreference(R.string.settings_archive_auto_cleanup,
                settings.archiveAutoCleanup(), settings::setArchiveAutoCleanup));
        archive.addPreference(switchPreference(R.string.settings_archive_download_audio,
                settings.downloadPodcast(), settings::setDownloadPodcast));
        archive.addPreference(numberPreference(R.string.settings_archive_max_audio_mb,
                String.valueOf(settings.getAudioMaxMb()),
                value -> settings.setAudioMaxMb(value)));
        Preference cleanupHint = infoPreference(R.string.settings_archive_cleanup_hint);
        archive.addPreference(cleanupHint);
        archive.addPreference(clickPreference(R.string.settings_archive_cleanup_now,
                "", preference -> runCleanup()));
        archive.addPreference(clickPreference(R.string.settings_archive_storage,
                "", preference -> startActivity(new Intent(requireContext(),
                        StorageActivity.class))));
        archive.addPreference(clickPreference(R.string.settings_archive_export,
                "", preference -> chooseArchiveFormat()));

        // Intelligence -----------------------------------------------------------------
        PreferenceCategory intelligence = category(R.string.settings_intelligence);
        screen.addPreference(intelligence);
        intelligence.addPreference(switchPreference(R.string.settings_auto_prioritization,
                settings.autoFeedPrioritization(), settings::setAutoFeedPrioritization,
                R.string.settings_auto_prioritization_hint));
        intelligence.addPreference(clickPreference(R.string.settings_relevance_thresholds,
                thresholdsSummary(), preference -> showThresholdDialog()));
        intelligence.addPreference(clickPreference(R.string.settings_rescore,
                "", preference -> {
                    container().workScheduler().enqueueRescore();
                    Toast.makeText(requireContext(), R.string.settings_rescore_started,
                            Toast.LENGTH_SHORT).show();
                }));
        intelligence.addPreference(clickPreference(R.string.settings_filter_rules,
                "", preference -> startActivity(new Intent(requireContext(),
                        FilterListActivity.class))));
        intelligence.addPreference(switchPreference(R.string.settings_duplicate_detection,
                settings.mergeDuplicates(), settings::setMergeDuplicates,
                R.string.settings_duplicate_hint));
        intelligence.addPreference(switchPreference(R.string.settings_show_all_sources,
                settings.showAllSources(), settings::setShowAllSources));
        intelligence.addPreference(switchPreference(R.string.settings_similar_articles,
                settings.similarArticlesEnabled(), settings::setSimilarArticlesEnabled));
        intelligence.addPreference(clickPreference(R.string.settings_priority_keywords,
                "", preference -> openKeywords(KeywordType.PRIORITY)));
        intelligence.addPreference(clickPreference(R.string.settings_topics,
                "", preference -> openKeywords(KeywordType.TOPIC)));
        EditTextPreference authors = editPreference(R.string.settings_people,
                settings.getPreferredAuthors(), settings::setPreferredAuthors);
        intelligence.addPreference(authors);
        intelligence.addPreference(switchPreference(R.string.settings_local_ai_enabled,
                settings.localAiEnabled(), settings::setLocalAiEnabled,
                R.string.settings_local_ai_hint));
        intelligence.addPreference(editPreference(R.string.settings_ollama_url,
                settings.getOllamaUrl(), settings::setOllamaUrl));
        intelligence.addPreference(editPreference(R.string.settings_ollama_model,
                settings.getOllamaModel(), settings::setOllamaModel));
        aiTestPreference = clickPreference(R.string.settings_ollama_test,
                "", preference -> testOllama());
        intelligence.addPreference(aiTestPreference);

        // Privacy ----------------------------------------------------------------------
        PreferenceCategory privacy = category(R.string.settings_privacy);
        screen.addPreference(privacy);
        privacy.addPreference(infoPreference(R.string.settings_privacy_summary));
        privacy.addPreference(clickPreference(R.string.settings_clear_history,
                "", preference -> confirmClearHistory()));
        privacy.addPreference(clickPreference(R.string.settings_export_data,
                "", preference -> {
                    dataExport.launch("intellirss-personal-data-"
                            + java.time.LocalDate.now() + ".json");
                }));
        privacy.addPreference(clickPreference(R.string.settings_delete_data,
                R.string.settings_delete_data_confirm, preference -> confirmDeleteAll()));

        // Backup and restore -----------------------------------------------------------
        PreferenceCategory backup = category(R.string.settings_backup);
        screen.addPreference(backup);
        backup.addPreference(infoPreference(R.string.settings_backup_summary));
        backup.addPreference(clickPreference(R.string.settings_backup_opml_export,
                "", preference -> opmlExport.launch("intellirss-subscriptions-"
                        + java.time.LocalDate.now() + ".opml")));
        backup.addPreference(clickPreference(R.string.settings_backup_opml_import,
                "", preference -> opmlImport.launch(new String[]{
                        "text/xml", "application/xml", "text/x-opml", "*/*"})));
        backup.addPreference(clickPreference(R.string.settings_backup_full,
                R.string.settings_backup_summary, preference -> backupCreate.launch(
                        "intellirss-backup-" + java.time.LocalDate.now() + ".zip")));
        backup.addPreference(clickPreference(R.string.settings_backup_restore,
                "", preference -> backupRestore.launch(new String[]{
                        "application/zip", "application/octet-stream", "*/*"})));

        // About ------------------------------------------------------------------------
        PreferenceCategory about = category(R.string.settings_about);
        screen.addPreference(about);
        about.addPreference(infoPreference(R.string.settings_about_body));
        about.addPreference(infoPreference(getString(R.string.settings_version,
                BuildConfig.VERSION_NAME)));
        about.addPreference(infoPreference(R.string.about_open_source));

        updateNotificationPermissionSummary();
        updateLastSyncSummary();
    }

    private com.intellirs.reader.di.AppContainer container() {
        return ((BaseActivity) requireActivity()).container();
    }

    private PreferenceCategory category(int title) {
        PreferenceCategory category = new PreferenceCategory(requireContext());
        category.setTitle(title);
        category.setIconSpaceReserved(false);
        return category;
    }

    private Preference infoPreference(int summary) {
        return infoPreference(getString(summary));
    }

    private Preference infoPreference(String summary) {
        Preference preference = new Preference(requireContext());
        preference.setSummary(summary);
        preference.setPersistent(false);
        preference.setSelectable(false);
        return preference;
    }

    private SwitchPreferenceCompat switchPreference(int title, boolean initial,
                                                    java.util.function.Consumer<Boolean> consumer) {
        return switchPreference(getString(title), initial, consumer, 0);
    }

    private SwitchPreferenceCompat switchPreference(int title, boolean initial,
                                                    java.util.function.Consumer<Boolean> consumer,
                                                    int summary) {
        return switchPreference(getString(title), initial, consumer, summary);
    }

    private SwitchPreferenceCompat switchPreference(String title, boolean initial,
                                                    java.util.function.Consumer<Boolean> consumer,
                                                    int summary) {
        SwitchPreferenceCompat preference = new SwitchPreferenceCompat(requireContext());
        preference.setTitle(title);
        if (summary != 0) {
            preference.setSummary(summary);
        }
        preference.setIconSpaceReserved(false);
        preference.setPersistent(false);
        preference.setChecked(initial);
        preference.setOnPreferenceChangeListener((pref, newValue) -> {
            consumer.accept((Boolean) newValue);
            return true;
        });
        return preference;
    }

    private ListPreference listPreference(int title, String[] entries, String[] values,
                                          String initial, StringConsumer consumer) {
        ListPreference preference = new ListPreference(requireContext());
        preference.setTitle(title);
        preference.setEntries(entries);
        preference.setEntryValues(values);
        preference.setIconSpaceReserved(false);
        preference.setPersistent(false);
        preference.setValue(initial);
        preference.setSummary(labelFor(values, entries, initial));
        preference.setOnPreferenceChangeListener((pref, newValue) -> {
            String value = (String) newValue;
            consumer.accept(value);
            pref.setSummary(labelFor(values, entries, value));
            return true;
        });
        return preference;
    }

    private SeekBarPreference seekPreference(int title, int initial, int min, int max,
                                             java.util.function.IntConsumer consumer) {
        SeekBarPreference preference = new SeekBarPreference(requireContext());
        preference.setTitle(title);
        preference.setMin(min);
        preference.setMax(max);
        preference.setSeekBarIncrement(1);
        preference.setValue(initial);
        preference.setIconSpaceReserved(false);
        preference.setPersistent(false);
        preference.setSummary(String.valueOf(initial));
        preference.setOnPreferenceChangeListener((pref, newValue) -> {
            int value = (Integer) newValue;
            consumer.accept(value);
            pref.setSummary(String.valueOf(value));
            return true;
        });
        return preference;
    }

    private EditTextPreference numberPreference(int title, String initial,
                                                java.util.function.IntConsumer consumer) {
        EditTextPreference preference = editPreference(title, initial, value -> {
            try {
                consumer.accept(Integer.parseInt(value.trim()));
            } catch (NumberFormatException ignored) {
                // Keep the previous value for invalid numeric input.
            }
        });
        preference.setOnBindEditTextListener(editText -> {
            editText.setInputType(InputType.TYPE_CLASS_NUMBER);
            editText.setSelection(editText.length());
        });
        return preference;
    }

    private EditTextPreference editPreference(int title, String initial,
                                              StringConsumer consumer) {
        return editPreference(getString(title), initial, consumer);
    }

    private EditTextPreference editPreference(String title, String initial,
                                              StringConsumer consumer) {
        EditTextPreference preference = new EditTextPreference(requireContext());
        preference.setTitle(title);
        preference.setIconSpaceReserved(false);
        preference.setPersistent(false);
        preference.setText(initial == null ? "" : initial);
        preference.setSummary(initial == null || initial.trim().isEmpty()
                ? getString(R.string.not_available) : initial);
        preference.setOnPreferenceChangeListener((pref, newValue) -> {
            String value = newValue == null ? "" : String.valueOf(newValue);
            consumer.accept(value);
            pref.setSummary(value.trim().isEmpty()
                    ? getString(R.string.not_available) : value);
            return true;
        });
        return preference;
    }

    private Preference clickPreference(int title, int summary, ClickAction action) {
        return clickPreference(getString(title),
                summary == 0 ? "" : getString(summary), action);
    }

    private Preference clickPreference(int title, String summary, ClickAction action) {
        return clickPreference(getString(title), summary, action);
    }

    private Preference clickPreference(String title, String summary, ClickAction action) {
        Preference preference = new Preference(requireContext());
        preference.setTitle(title);
        if (summary != null && !summary.isEmpty()) {
            preference.setSummary(summary);
        }
        preference.setIconSpaceReserved(false);
        preference.setPersistent(false);
        preference.setOnPreferenceClickListener(p -> {
            action.onClick(p);
            return true;
        });
        return preference;
    }

    private String labelFor(String[] values, String[] labels, String value) {
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(value)) {
                return labels[i];
            }
        }
        return value == null ? "" : value;
    }

    private String policyValue(FeedImportance importance) {
        return importance == FeedImportance.MUTED ? "ALL" : importance.name();
    }

    private String thresholdsSummary() {
        SettingsRepository settings = container().settingsRepository();
        return settings.getCriticalThreshold() + " / " + settings.getHighThreshold()
                + " / " + settings.getNormalThreshold() + " / "
                + settings.getLowThreshold();
    }

    private String formatTime(int minutes) {
        int hour = minutes / 60;
        int minute = minutes % 60;
        return String.format(Locale.getDefault(), "%02d:%02d", hour, minute);
    }

    private interface MinutesConsumer {
        void accept(int minutes);
    }

    private void pickTime(int initialMinutes, MinutesConsumer consumer) {
        TimePickerDialog dialog = new TimePickerDialog(requireContext(),
                (TimePicker view, int hourOfDay, int minute) ->
                        consumer.accept(hourOfDay * 60 + minute),
                initialMinutes / 60, initialMinutes % 60, true);
        dialog.show();
    }

    private void updateNotificationPermissionSummary() {
        if (notificationPermissionPreference == null) {
            return;
        }
        boolean granted = container().notificationHelper().canPostNotifications();
        notificationPermissionPreference.setSummary(granted
                ? getString(R.string.settings_notifications_permission_granted)
                : getString(R.string.settings_notifications_permission_denied));
    }

    private void updateLastSyncSummary() {
        if (lastSyncPreference == null) {
            return;
        }
        long time = container().settingsRepository().getLastSyncTime();
        String status = container().settingsRepository().getLastSyncStatus();
        if (time <= 0) {
            lastSyncPreference.setSummary(R.string.sync_status_never);
        } else {
            lastSyncPreference.setSummary((status == null || status.isEmpty()
                    ? getString(R.string.sync_status_complete, 0, 0) : status)
                    + "  \u2022  " + DateUtils.relative(requireContext(), time));
        }
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33
                && ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
        } else if (!container().notificationHelper().canPostNotifications()) {
            openAppNotificationSettings();
        } else {
            Toast.makeText(requireContext(),
                    R.string.settings_notifications_permission_granted,
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void openAppNotificationSettings() {
        Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
        intent.putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().getPackageName());
        startActivity(intent);
    }

    private void openKeywords(KeywordType type) {
        Intent intent = new Intent(requireContext(), KeywordListActivity.class);
        intent.putExtra(KeywordListActivity.EXTRA_TYPE, type.name());
        startActivity(intent);
    }

    private void runCleanup() {
        Toast.makeText(requireContext(), R.string.loading, Toast.LENGTH_SHORT).show();
        container().ioExecutor().execute(() -> {
            CleanupResult result = container().archiveManager().cleanupIfNeeded();
            if (isAdded()) {
                requireActivity().runOnUiThread(() -> Toast.makeText(requireContext(),
                        getString(R.string.settings_archive_cleanup_result,
                                com.intellirs.reader.util.TextUtils.humanBytes(result.bytesFreed),
                                result.articlesRemoved), Toast.LENGTH_LONG).show());
            }
        });
    }

    private void chooseArchiveFormat() {
        String[] labels = new String[]{
                getString(R.string.settings_archive_export_html),
                getString(R.string.settings_archive_export_json),
                getString(R.string.settings_archive_export_zip)
        };
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.settings_archive_export_format)
                .setItems(labels, (dialog, which) -> {
                    pendingArchiveFormat = which == 0 ? ArchiveExporter.Format.HTML
                            : which == 1 ? ArchiveExporter.Format.JSON
                            : ArchiveExporter.Format.ZIP;
                    archiveExport.launch(ArchiveExporter.suggestedFileName(pendingArchiveFormat));
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void writeArchiveExport(Uri uri) {
        if (uri == null || pendingArchiveFormat == null) {
            return;
        }
        ArchiveExporter.Format format = pendingArchiveFormat;
        Toast.makeText(requireContext(), R.string.export_progress, Toast.LENGTH_SHORT).show();
        container().ioExecutor().execute(() -> {
            try (OutputStream out = requireContext().getContentResolver()
                    .openOutputStream(uri)) {
                if (out == null) {
                    throw new IllegalStateException("Could not open export destination");
                }
                int count = container().archiveExporter().export(out, format);
                requireActivity().runOnUiThread(() -> Toast.makeText(requireContext(),
                        getString(R.string.settings_archive_export) + " (" + count + ")",
                        Toast.LENGTH_SHORT).show());
            } catch (Exception e) {
                requireActivity().runOnUiThread(() -> Toast.makeText(requireContext(),
                        getString(R.string.archive_export_error, e.getMessage()),
                        Toast.LENGTH_LONG).show());
            }
        });
    }

    private void writeOpmlExport(Uri uri) {
        if (uri == null) {
            return;
        }
        container().ioExecutor().execute(() -> {
            try (OutputStream out = requireContext().getContentResolver().openOutputStream(uri)) {
                if (out == null) {
                    throw new IllegalStateException("Could not open export destination");
                }
                List<FeedEntity> feeds = container().feedRepository().getActiveFeeds();
                List<CategoryEntity> categories = container().feedRepository().getCategories();
                if (feeds.isEmpty()) {
                    requireActivity().runOnUiThread(() -> Toast.makeText(requireContext(),
                            R.string.opml_no_subs, Toast.LENGTH_LONG).show());
                    return;
                }
                String xml = container().opmlManager().export(feeds, categories);
                out.write(xml.getBytes(StandardCharsets.UTF_8));
                out.flush();
                requireActivity().runOnUiThread(() -> Toast.makeText(requireContext(),
                        R.string.opml_export_success, Toast.LENGTH_SHORT).show());
            } catch (Exception e) {
                requireActivity().runOnUiThread(() -> Toast.makeText(requireContext(),
                        getString(R.string.opml_export_error, e.getMessage()),
                        Toast.LENGTH_LONG).show());
            }
        });
    }

    private void importOpml(Uri uri) {
        OpmlImportFlow.prepare(requireContext(), container(), uri,
                new OpmlImportFlow.Callback() {
                    @Override
                    public void onReady(File previewFile) {
                        if (!isAdded()) {
                            return;
                        }
                        Intent intent = new Intent(requireContext(), OpmlPreviewActivity.class);
                        intent.putExtra(OpmlPreviewActivity.EXTRA_PREVIEW_FILE,
                                previewFile.getAbsolutePath());
                        startActivity(intent);
                    }

                    @Override
                    public void onError(String message) {
                        if (isAdded()) {
                            Toast.makeText(requireContext(),
                                    getString(R.string.opml_import_error, message),
                                    Toast.LENGTH_LONG).show();
                        }
                    }
                });
    }

    private void writeFullBackup(Uri uri) {
        if (uri == null) {
            return;
        }
        Toast.makeText(requireContext(), R.string.backup_progress, Toast.LENGTH_SHORT).show();
        container().ioExecutor().execute(() -> {
            try (OutputStream out = requireContext().getContentResolver().openOutputStream(uri)) {
                if (out == null) {
                    throw new IllegalStateException("Could not open backup destination");
                }
                container().backupManager().createBackup(out);
                requireActivity().runOnUiThread(() -> Toast.makeText(requireContext(),
                        R.string.settings_backup_created, Toast.LENGTH_LONG).show());
            } catch (Exception e) {
                requireActivity().runOnUiThread(() -> Toast.makeText(requireContext(),
                        getString(R.string.settings_backup_failed, e.getMessage()),
                        Toast.LENGTH_LONG).show());
            }
        });
    }

    private void prepareRestore(Uri uri) {
        if (uri == null) {
            return;
        }
        Toast.makeText(requireContext(), R.string.restore_progress, Toast.LENGTH_SHORT).show();
        container().ioExecutor().execute(() -> {
            try (InputStream input = requireContext().getContentResolver().openInputStream(uri)) {
                if (input == null) {
                    throw new IllegalStateException("Could not open backup file");
                }
                RestorePreview preview = container().backupManager().prepareRestore(input);
                requireActivity().runOnUiThread(() -> confirmRestore(preview));
            } catch (Exception e) {
                requireActivity().runOnUiThread(() -> Toast.makeText(requireContext(),
                        getString(R.string.settings_restore_invalid, e.getMessage()),
                        Toast.LENGTH_LONG).show());
            }
        });
    }

    private void confirmRestore(RestorePreview preview) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.settings_restore_title)
                .setMessage(getString(R.string.settings_restore_message)
                        + "\n\n" + getString(R.string.settings_version,
                        preview.metadata.appVersion)
                        + "\n" + getString(R.string.feed_count, preview.metadata.feeds))
                .setPositiveButton(R.string.settings_restore_confirm, (dialog, which) -> {
                    container().ioExecutor().execute(() -> {
                        try {
                            container().backupManager().applyRestore(preview);
                            requireActivity().runOnUiThread(() -> {
                                Toast.makeText(requireContext(),
                                        R.string.settings_restore_done,
                                        Toast.LENGTH_LONG).show();
                                BackupManager.restartProcess();
                            });
                        } catch (Exception e) {
                            requireActivity().runOnUiThread(() ->
                                    new MaterialAlertDialogBuilder(requireContext())
                                            .setTitle(R.string.settings_restore_title)
                                            .setMessage(getString(R.string.settings_restore_invalid,
                                                    e.getMessage()) + "\n\n"
                                                    + getString(R.string.app_restart_required))
                                            .setPositiveButton(R.string.action_done,
                                                    (restartDialog, ignored) ->
                                                            BackupManager.restartProcess())
                                            .setCancelable(false)
                                            .show());
                        }
                    });
                })
                .setNegativeButton(R.string.action_cancel,
                        (dialog, which) -> container().backupManager()
                                .cleanupPreparedRestore(preview))
                .show();
    }

    private void writePersonalData(Uri uri) {
        if (uri == null) {
            return;
        }
        container().ioExecutor().execute(() -> {
            try (OutputStream out = requireContext().getContentResolver().openOutputStream(uri)) {
                if (out == null) {
                    throw new IllegalStateException("Could not open export destination");
                }
                container().backupManager().exportPersonalData(out);
                requireActivity().runOnUiThread(() -> Toast.makeText(requireContext(),
                        R.string.settings_export_data_done, Toast.LENGTH_LONG).show());
            } catch (Exception e) {
                requireActivity().runOnUiThread(() -> Toast.makeText(requireContext(),
                        getString(R.string.settings_data_export_failed, e.getMessage()),
                        Toast.LENGTH_LONG).show());
            }
        });
    }

    private void confirmClearHistory() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.settings_clear_history)
                .setMessage(R.string.settings_clear_history_confirm)
                .setPositiveButton(R.string.action_clear, (dialog, which) ->
                        container().ioExecutor().execute(() ->
                                container().articleRepository().clearHistory()))
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void confirmDeleteAll() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.settings_delete_data)
                .setMessage(R.string.settings_delete_data_confirm)
                .setPositiveButton(R.string.settings_delete_data_final, (dialog, which) ->
                        container().ioExecutor().execute(() -> {
                            container().backupManager().deleteAllData();
                            requireActivity().runOnUiThread(() -> {
                                Toast.makeText(requireContext(),
                                        R.string.settings_delete_data_done,
                                        Toast.LENGTH_LONG).show();
                                BackupManager.restartProcess();
                            });
                        }))
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void showThresholdDialog() {
        SettingsRepository settings = container().settingsRepository();
        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = Math.round(20 * getResources().getDisplayMetrics().density);
        layout.setPadding(padding, padding / 2, padding, padding / 2);
        TextInputLayout criticalLayout = thresholdInput(layout, getString(
                R.string.settings_threshold_critical), settings.getCriticalThreshold());
        TextInputLayout highLayout = thresholdInput(layout, getString(
                R.string.settings_threshold_high), settings.getHighThreshold());
        TextInputLayout normalLayout = thresholdInput(layout, getString(
                R.string.settings_threshold_normal), settings.getNormalThreshold());
        TextInputLayout lowLayout = thresholdInput(layout, getString(
                R.string.settings_threshold_low), settings.getLowThreshold());
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.settings_relevance_thresholds)
                .setView(layout)
                .setPositiveButton(R.string.action_save, null)
                .setNegativeButton(R.string.action_cancel, null);
        androidx.appcompat.app.AlertDialog dialog = builder.create();
        dialog.setOnShowListener(d -> dialog.getButton(
                androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try {
                int critical = Integer.parseInt(textOf(criticalLayout));
                int high = Integer.parseInt(textOf(highLayout));
                int normal = Integer.parseInt(textOf(normalLayout));
                int low = Integer.parseInt(textOf(lowLayout));
                if (critical <= high || high <= normal || normal <= low || low < 0
                        || critical > 100) {
                    Toast.makeText(requireContext(), R.string.settings_threshold_validation,
                            Toast.LENGTH_LONG).show();
                    return;
                }
                settings.setThresholds(critical, high, normal, low);
                dialog.dismiss();
                recreatePreferenceScreen();
            } catch (NumberFormatException e) {
                Toast.makeText(requireContext(), R.string.settings_threshold_validation,
                        Toast.LENGTH_LONG).show();
            }
        }));
        dialog.show();
    }

    private TextInputLayout thresholdInput(LinearLayout parent, String hint, int value) {
        TextInputLayout layout = new TextInputLayout(requireContext());
        layout.setHint(hint);
        EditText edit = new EditText(requireContext());
        edit.setInputType(InputType.TYPE_CLASS_NUMBER);
        edit.setText(String.valueOf(value));
        edit.setSingleLine(true);
        layout.addView(edit);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = Math.round(6 * getResources().getDisplayMetrics().density);
        parent.addView(layout, params);
        return layout;
    }

    private String textOf(TextInputLayout layout) {
        EditText edit = layout.getEditText();
        return edit == null || edit.getText() == null ? "" : edit.getText().toString().trim();
    }

    private void recreatePreferenceScreen() {
        if (getPreferenceScreen() != null) {
            getPreferenceScreen().removeAll();
        }
        onCreatePreferences(null, null);
    }

    private void testOllama() {
        aiTestPreference.setSummary(R.string.settings_ollama_testing);
        container().ioExecutor().execute(() -> {
            try {
                container().localAiClient().testConnection();
                requireActivity().runOnUiThread(() -> aiTestPreference.setSummary(
                        R.string.settings_ollama_ok));
            } catch (Exception e) {
                requireActivity().runOnUiThread(() -> aiTestPreference.setSummary(
                        getString(R.string.settings_ollama_fail, e.getMessage())));
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        updateNotificationPermissionSummary();
        updateLastSyncSummary();
        SettingsRepository settings = container().settingsRepository();
        if (quietStartPreference != null) {
            quietStartPreference.setSummary(formatTime(settings.getQuietStartMinutes()));
        }
        if (quietEndPreference != null) {
            quietEndPreference.setSummary(formatTime(settings.getQuietEndMinutes()));
        }
    }
}
