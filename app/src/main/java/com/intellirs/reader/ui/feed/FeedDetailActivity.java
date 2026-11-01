package com.intellirs.reader.ui.feed;

import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.intellirs.reader.R;
import com.intellirs.reader.data.entity.ArchiveMode;
import com.intellirs.reader.data.entity.CategoryEntity;
import com.intellirs.reader.data.entity.FeedEntity;
import com.intellirs.reader.data.entity.FeedHealthEntity;
import com.intellirs.reader.data.entity.FeedImportance;
import com.intellirs.reader.data.entity.ImportanceSource;
import com.intellirs.reader.data.model.FeedStats;
import com.intellirs.reader.domain.prioritization.FeedPrioritySuggestion;
import com.intellirs.reader.ui.BaseActivity;
import com.intellirs.reader.ui.common.IconLoader;
import com.intellirs.reader.util.DateUtils;
import com.intellirs.reader.util.TextUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class FeedDetailActivity extends BaseActivity {
    public static final String EXTRA_FEED_ID = "feed_id";

    private FeedDetailViewModel viewModel;
    private FeedEntity loadedFeed;
    private List<CategoryEntity> categories = new ArrayList<>();
    private final List<Long> categoryIds = new ArrayList<>();
    private FeedImportance initialImportance;
    private Spinner categorySpinner;
    private Spinner importanceSpinner;
    private Spinner archiveSpinner;
    private Spinner syncSpinner;
    private MaterialSwitch notificationsSwitch;
    private TextInputEditText titleInput;
    private TextInputEditText websiteInput;
    private TextInputEditText descriptionInput;
    private TextView feedHeaderTitle;
    private TextView feedHealth;
    private TextView statsText;
    private TextView healthText;
    private TextView suggestionText;
    private MaterialButton applySuggestion;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_feed_detail);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }
        toolbar.setNavigationOnClickListener(view -> finish());

        categorySpinner = findViewById(R.id.feed_category_spinner);
        importanceSpinner = findViewById(R.id.feed_importance_spinner);
        archiveSpinner = findViewById(R.id.feed_archive_spinner);
        syncSpinner = findViewById(R.id.feed_sync_spinner);
        notificationsSwitch = findViewById(R.id.feed_notifications_switch);
        titleInput = findViewById(R.id.feed_title_input);
        websiteInput = findViewById(R.id.feed_website_input);
        descriptionInput = findViewById(R.id.feed_description_input);
        feedHeaderTitle = findViewById(R.id.feed_header_title);
        feedHealth = findViewById(R.id.feed_health);
        statsText = findViewById(R.id.feed_stats_text);
        healthText = findViewById(R.id.feed_health_text);
        suggestionText = findViewById(R.id.importance_suggestion);
        applySuggestion = findViewById(R.id.apply_suggestion);

        viewModel = new ViewModelProvider(this).get(FeedDetailViewModel.class);
        long feedId = getIntent().getLongExtra(EXTRA_FEED_ID, -1L);
        if (feedId <= 0) {
            finish();
            return;
        }
        viewModel.data().observe(this, data -> {
            if (data != null && data.feed != null) {
                populate(data);
            }
        });
        viewModel.load(feedId);

        findViewById(R.id.save_feed).setOnClickListener(view -> save());
        findViewById(R.id.refresh_feed).setOnClickListener(view -> refreshFeed());
        findViewById(R.id.unsubscribe_feed).setOnClickListener(view -> confirmUnsubscribe());
        findViewById(R.id.move_up).setOnClickListener(view -> move(true));
        findViewById(R.id.move_down).setOnClickListener(view -> move(false));
        applySuggestion.setOnClickListener(view -> applySuggestion());
    }

    private void populate(FeedDetailData data) {
        loadedFeed = data.feed;
        categories = data.categories == null ? new ArrayList<>() : data.categories;
        initialImportance = data.feed.feedPriority;

        feedHeaderTitle.setText(data.feed.title);
        IconLoader.loadFeedIcon(findViewById(R.id.feed_icon), data.feed, 12);
        titleInput.setText(data.feed.title);
        ((TextInputEditText) findViewById(R.id.feed_url_input)).setText(data.feed.feedUrl);
        websiteInput.setText(data.feed.websiteUrl == null ? "" : data.feed.websiteUrl);
        descriptionInput.setText(data.feed.description == null ? "" : data.feed.description);
        notificationsSwitch.setChecked(data.feed.notificationEnabled);

        populateCategories(data.feed.categoryId);
        populateImportance(data.feed.feedPriority);
        populateArchiveModes(data.feed.archiveMode);
        populateSyncIntervals(data.feed.syncIntervalMinutes);
        populateStats(data);
    }

    private void populateCategories(Long selectedId) {
        List<String> labels = new ArrayList<>();
        categoryIds.clear();
        labels.add(getString(R.string.feed_none_category));
        categoryIds.add(null);
        int selectedPosition = 0;
        for (int i = 0; i < categories.size(); i++) {
            CategoryEntity category = categories.get(i);
            labels.add(category.name);
            categoryIds.add(category.id);
            if (selectedId != null && selectedId.equals(category.id)) {
                selectedPosition = i + 1;
            }
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        categorySpinner.setAdapter(adapter);
        categorySpinner.setSelection(selectedPosition);
    }

    private void populateImportance(FeedImportance importance) {
        String[] labels = new String[]{
                getString(R.string.importance_critical),
                getString(R.string.importance_high),
                getString(R.string.importance_normal),
                getString(R.string.importance_low),
                getString(R.string.importance_muted)
        };
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        importanceSpinner.setAdapter(adapter);
        importanceSpinner.setSelection(Math.max(0, importance.rank()));
    }

    private void populateArchiveModes(ArchiveMode mode) {
        String[] labels = new String[]{
                getString(R.string.archive_mode_automatic),
                getString(R.string.archive_mode_priority),
                getString(R.string.archive_mode_manual)
        };
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        archiveSpinner.setAdapter(adapter);
        archiveSpinner.setSelection(mode == ArchiveMode.AUTOMATIC ? 0
                : mode == ArchiveMode.PRIORITY ? 1 : 2);
    }

    private void populateSyncIntervals(int currentMinutes) {
        int[] values = new int[]{0, 15, 30, 60, 180, 360, 720, 1440};
        List<String> labels = new ArrayList<>();
        int selection = 0;
        for (int i = 0; i < values.length; i++) {
            int value = values[i];
            if (value == 0) {
                labels.add(getString(R.string.feed_sync_default,
                        settings.getSyncIntervalMinutes()));
            } else {
                labels.add(intervalLabel(value));
            }
            if (value == currentMinutes) {
                selection = i;
            }
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        syncSpinner.setAdapter(adapter);
        syncSpinner.setSelection(selection);
    }

    private String intervalLabel(int minutes) {
        switch (minutes) {
            case 15:
                return getString(R.string.interval_15);
            case 30:
                return getString(R.string.interval_30);
            case 60:
                return getString(R.string.interval_60);
            case 180:
                return getString(R.string.interval_180);
            case 360:
                return getString(R.string.interval_360);
            case 720:
                return getString(R.string.interval_720);
            case 1440:
                return getString(R.string.interval_1440);
            default:
                return minutes + " minutes";
        }
    }

    private void populateStats(FeedDetailData data) {
        FeedStats stats = data.stats == null ? new FeedStats() : data.stats;
        statsText.setText(getString(R.string.feed_stat_articles) + ": " + stats.totalArticles
                + "\n" + getString(R.string.feed_stat_unread) + ": " + stats.unreadArticles
                + "\n" + getString(R.string.feed_stat_read) + ": " + stats.readArticles
                + "\n" + getString(R.string.feed_stat_bookmarked) + ": " + stats.bookmarkedArticles
                + "\n" + getString(R.string.feed_stat_last_success) + ": "
                + DateUtils.formatFullDate(data.feed.lastSuccessfulSync));
        FeedHealthEntity health = data.health;
        if (health != null) {
            String status = health.status == null ? getString(R.string.feed_health_healthy)
                    : healthStatusLabel(health.status.name());
            feedHealth.setText(status + "  \u2022  " + getString(R.string.feed_stat_articles)
                    + " " + stats.totalArticles);
            StringBuilder healthBuilder = new StringBuilder();
            healthBuilder.append(getString(R.string.feed_health)).append(": ").append(status)
                    .append("\n")
                    .append(getString(R.string.feed_stat_failures)).append(": ")
                    .append(health.failureCount).append("\n")
                    .append(getString(R.string.feed_stat_http)).append(": ")
                    .append(health.lastHttpStatus == 0
                            ? getString(R.string.not_available) : health.lastHttpStatus)
                    .append("\n")
                    .append(getString(R.string.feed_stat_last_attempt)).append(": ")
                    .append(DateUtils.formatFullDate(health.lastCheckedAt));
            if (health.lastError != null && !health.lastError.isEmpty()) {
                healthBuilder.append("\n").append(getString(R.string.feed_stat_last_error))
                        .append(": ").append(TextUtils.truncate(health.lastError, 300));
            }
            healthText.setText(healthBuilder.toString());
        } else {
            feedHealth.setText(R.string.feed_health_healthy);
            healthText.setText(R.string.not_available);
        }
        FeedPrioritySuggestion suggestion = data.suggestion;
        if (suggestion != null) {
            suggestionText.setVisibility(View.VISIBLE);
            suggestionText.setText(getString(R.string.feed_stat_suggested,
                    importanceLabel(suggestion.importance))
                    + " \u2022 " + getString(R.string.feed_stat_suggest_reason, suggestion.reason));
            applySuggestion.setVisibility(View.VISIBLE);
        } else {
            suggestionText.setVisibility(View.GONE);
            applySuggestion.setVisibility(View.GONE);
        }
    }

    private String healthStatusLabel(String status) {
        switch (status) {
            case "FAILING":
                return getString(R.string.feed_health_failing);
            case "DEGRADED":
                return getString(R.string.feed_health_degraded);
            case "INACTIVE":
                return getString(R.string.feed_health_inactive);
            case "HEALTHY":
            default:
                return getString(R.string.feed_health_healthy);
        }
    }

    private String importanceLabel(FeedImportance importance) {
        if (importance == null) {
            return getString(R.string.importance_normal);
        }
        switch (importance) {
            case CRITICAL:
                return getString(R.string.importance_critical);
            case HIGH:
                return getString(R.string.importance_high);
            case LOW:
                return getString(R.string.importance_low);
            case MUTED:
                return getString(R.string.importance_muted);
            case NORMAL:
            default:
                return getString(R.string.importance_normal);
        }
    }

    private void save() {
        if (loadedFeed == null) {
            return;
        }
        String title = titleInput.getText() == null ? "" : titleInput.getText().toString().trim();
        if (title.isEmpty()) {
            titleInput.setError(getString(R.string.feed_title));
            return;
        }
        FeedEntity form = loadedFeed;
        form.title = title;
        form.websiteUrl = websiteInput.getText() == null ? null
                : emptyToNull(websiteInput.getText().toString());
        form.description = descriptionInput.getText() == null ? null
                : emptyToNull(descriptionInput.getText().toString());
        int categoryPosition = categorySpinner.getSelectedItemPosition();
        form.categoryId = categoryPosition >= 0 && categoryPosition < categoryIds.size()
                ? categoryIds.get(categoryPosition) : null;
        FeedImportance selectedImportance = FeedImportance.values()[
                Math.max(0, Math.min(FeedImportance.values().length - 1,
                        importanceSpinner.getSelectedItemPosition()))];
        form.feedPriority = selectedImportance;
        // Only an actual importance change is a manual override. Spinner callbacks can fire
        // after initial population, so do not rely on a "touched" boolean.
        if (selectedImportance != initialImportance) {
            form.importanceSource = ImportanceSource.USER;
        } else {
            form.importanceSource = loadedFeed.importanceSource;
        }
        int archivePosition = archiveSpinner.getSelectedItemPosition();
        form.archiveMode = archivePosition == 0 ? ArchiveMode.AUTOMATIC
                : archivePosition == 1 ? ArchiveMode.PRIORITY : ArchiveMode.MANUAL;
        int[] intervals = new int[]{0, 15, 30, 60, 180, 360, 720, 1440};
        form.syncIntervalMinutes = intervals[Math.max(0, Math.min(intervals.length - 1,
                syncSpinner.getSelectedItemPosition()))];
        form.notificationEnabled = notificationsSwitch.isChecked();

        container.ioExecutor().execute(() -> {
            try {
                container.feedRepository().updateFeed(form);
                runOnUiThread(() -> {
                    Snackbar.make(findViewById(R.id.save_feed), R.string.feed_saved,
                            Snackbar.LENGTH_SHORT).show();
                    viewModel.reload();
                });
            } catch (Exception e) {
                runOnUiThread(() -> Snackbar.make(findViewById(R.id.save_feed),
                        getString(R.string.feed_save_error, e.getMessage()),
                        Snackbar.LENGTH_LONG).show());
            }
        });
    }

    private void applySuggestion() {
        if (loadedFeed == null) {
            return;
        }
        container.ioExecutor().execute(() -> {
            container.feedRepository().applySuggestedImportance(loadedFeed.id);
            runOnUiThread(() -> {
                Snackbar.make(findViewById(R.id.save_feed), R.string.feed_saved,
                        Snackbar.LENGTH_SHORT).show();
                viewModel.reload();
            });
        });
    }

    private void refreshFeed() {
        if (loadedFeed == null) {
            return;
        }
        container.feedRepository().refreshFeed(loadedFeed.id);
        Snackbar.make(findViewById(R.id.refresh_feed), R.string.feed_refresh_started,
                Snackbar.LENGTH_SHORT).show();
    }

    private void move(boolean up) {
        if (loadedFeed == null) {
            return;
        }
        container.ioExecutor().execute(() ->
                container.feedRepository().moveFeed(loadedFeed.id, up));
        Snackbar.make(findViewById(R.id.save_feed), R.string.feed_saved,
                Snackbar.LENGTH_SHORT).show();
    }

    private void confirmUnsubscribe() {
        if (loadedFeed == null) {
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.feed_unsubscribe_title)
                .setMessage(R.string.feed_unsubscribe_message)
                .setPositiveButton(R.string.action_unsubscribe, (dialog, which) -> {
                    long id = loadedFeed.id;
                    container.ioExecutor().execute(() ->
                            container.feedRepository().unsubscribe(id));
                    Toast.makeText(this, R.string.feed_unsubscribed, Toast.LENGTH_LONG).show();
                    finish();
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private static String emptyToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

}
