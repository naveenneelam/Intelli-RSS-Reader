package com.intellirs.reader.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.snackbar.Snackbar;
import com.intellirs.reader.R;
import com.intellirs.reader.data.entity.CategoryEntity;
import com.intellirs.reader.data.entity.FeedEntity;
import com.intellirs.reader.data.entity.SyncRunEntity;
import com.intellirs.reader.domain.model.ArticleFilter;
import com.intellirs.reader.domain.model.SyncStatus;
import com.intellirs.reader.ui.articles.ArticleListFragment;
import com.intellirs.reader.ui.categories.CategoriesActivity;
import com.intellirs.reader.ui.downloads.DownloadsActivity;
import com.intellirs.reader.ui.feed.AddFeedActivity;
import com.intellirs.reader.ui.feed.FeedDetailActivity;
import com.intellirs.reader.ui.filters.FilterListActivity;
import com.intellirs.reader.ui.home.HomeFragment;
import com.intellirs.reader.ui.notifications.NotificationHistoryActivity;
import com.intellirs.reader.ui.search.SearchActivity;
import com.intellirs.reader.ui.settings.SettingsActivity;
import com.intellirs.reader.ui.statistics.StatisticsActivity;
import com.intellirs.reader.util.DateUtils;

import java.util.List;

public class MainActivity extends BaseActivity {
    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private Toolbar toolbar;
    private TextView syncStatus;
    private LinearProgressIndicator syncProgress;
    private FloatingActionButton fabAdd;
    private boolean homeVisible = true;
    private ActivityResultLauncher<String> notificationPermissionLauncher;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        toolbar = findViewById(R.id.toolbar);
        syncStatus = findViewById(R.id.sync_status);
        syncProgress = findViewById(R.id.sync_progress);
        fabAdd = findViewById(R.id.fab_add);
        drawerLayout = findViewById(R.id.drawer_layout);
        navigationView = findViewById(R.id.nav_view);

        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(view -> drawerLayout.openDrawer(navigationView));
        navigationView.setNavigationItemSelectedListener(this::onNavigationItemSelected);
        fabAdd.setOnClickListener(view -> openAddFeed());
        syncStatus.setOnClickListener(view -> showSyncHistory());

        notificationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(), granted -> {
                    invalidateOptionsMenu();
                    if (!granted) {
                        Snackbar.make(drawerLayout, R.string.permission_notifications_denied,
                                        Snackbar.LENGTH_LONG)
                                .setAction(R.string.snackbar_settings,
                                        v -> openNotificationSystemSettings())
                                .show();
                    }
                });

        container.syncStatusBus().observe().observe(this, this::renderSyncStatus);
        renderStoredSyncStatus();
        if (savedInstanceState == null) {
            showHome();
            startStartupSync();
        } else {
            String title = savedInstanceState.getString("screen_title", getString(R.string.home_title));
            setScreenTitle(title);
            homeVisible = savedInstanceState.getBoolean("home_visible", true);
            setAddFeedVisible(homeVisible);
        }

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (drawerLayout.isDrawerOpen(navigationView)) {
                    drawerLayout.closeDrawer(navigationView);
                } else if (!homeVisible) {
                    showHome();
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });

        maybeAskNotificationPermission();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        startStartupSync();
    }

    @Override
    protected void onResume() {
        super.onResume();
        invalidateOptionsMenu();
        updateSyncDrawableTint();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("screen_title", toolbar.getTitle() == null ? ""
                : toolbar.getTitle().toString());
        outState.putBoolean("home_visible", homeVisible);
    }

    private boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        drawerLayout.closeDrawers();
        if (id == R.id.nav_home) {
            showHome();
        } else if (id == R.id.nav_all_articles) {
            openArticles(ArticleFilter.all(), getString(R.string.nav_all_articles));
        } else if (id == R.id.nav_important) {
            openArticles(ArticleFilter.important(), getString(R.string.nav_important));
        } else if (id == R.id.nav_unread) {
            openArticles(ArticleFilter.unread(), getString(R.string.nav_unread));
        } else if (id == R.id.nav_starred) {
            openArticles(ArticleFilter.starred(), getString(R.string.nav_starred));
        } else if (id == R.id.nav_archive) {
            openArticles(ArticleFilter.archived(), getString(R.string.nav_archive));
        } else if (id == R.id.nav_history) {
            openArticles(ArticleFilter.history(), getString(R.string.nav_history));
        } else if (id == R.id.nav_categories) {
            startActivity(new Intent(this, CategoriesActivity.class));
        } else if (id == R.id.nav_downloads) {
            startActivity(new Intent(this, DownloadsActivity.class));
        } else if (id == R.id.nav_search) {
            startActivity(new Intent(this, SearchActivity.class));
        } else if (id == R.id.nav_statistics) {
            startActivity(new Intent(this, StatisticsActivity.class));
        } else if (id == R.id.nav_filters) {
            startActivity(new Intent(this, FilterListActivity.class));
        } else if (id == R.id.nav_notification_history) {
            startActivity(new Intent(this, NotificationHistoryActivity.class));
        } else if (id == R.id.nav_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
        } else {
            return false;
        }
        return true;
    }

    public void showHome() {
        Fragment fragment = new HomeFragment();
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, fragment, "home")
                .commit();
        homeVisible = true;
        setScreenTitle(getString(R.string.home_title));
        setAddFeedVisible(true);
        navigationView.setCheckedItem(R.id.nav_home);
        invalidateOptionsMenu();
    }

    public void openArticles(ArticleFilter filter, String title) {
        openArticlesInternal(filter, title, navItemForMode(filter.mode));
    }

    public void openFeedArticles(FeedEntity feed) {
        if (feed == null) {
            return;
        }
        openArticlesInternal(ArticleFilter.feed(feed.id), feed.title, -1);
    }

    public void openCategoryArticles(CategoryEntity category) {
        if (category == null) {
            return;
        }
        openArticlesInternal(ArticleFilter.category(category.id), category.name, -1);
    }

    private void openArticlesInternal(ArticleFilter filter, String title, int navItem) {
        Fragment fragment = ArticleListFragment.newInstance(filter, title);
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, fragment, "articles")
                .commit();
        homeVisible = false;
        setScreenTitle(title);
        setAddFeedVisible(false);
        if (navItem != -1) {
            navigationView.setCheckedItem(navItem);
        }
        invalidateOptionsMenu();
    }

    private int navItemForMode(ArticleFilter.Mode mode) {
        switch (mode) {
            case ALL:
                return R.id.nav_all_articles;
            case IMPORTANT:
                return R.id.nav_important;
            case UNREAD:
                return R.id.nav_unread;
            case STARRED:
                return R.id.nav_starred;
            case ARCHIVED:
                return R.id.nav_archive;
            case HISTORY:
                return R.id.nav_history;
            default:
                return -1;
        }
    }

    public void setScreenTitle(String title) {
        if (toolbar != null) {
            toolbar.setTitle(title == null || title.trim().isEmpty()
                    ? getString(R.string.app_name) : title);
        }
    }

    public void setAddFeedVisible(boolean visible) {
        if (fabAdd != null) {
            fabAdd.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    public void openAddFeed() {
        startActivity(new Intent(this, AddFeedActivity.class));
    }

    public void openFeedDetails(long feedId) {
        Intent intent = new Intent(this, FeedDetailActivity.class);
        intent.putExtra(FeedDetailActivity.EXTRA_FEED_ID, feedId);
        startActivity(intent);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem notifications = menu.findItem(R.id.action_notifications);
        if (notifications != null) {
            boolean active = isNotificationActive();
            notifications.setIcon(active ? R.drawable.ic_notifications
                    : R.drawable.ic_notifications_off);
            notifications.setTitle(active ? R.string.notifications_status_on
                    : R.string.notifications_status_off);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_search) {
            startActivity(new Intent(this, SearchActivity.class));
            return true;
        }
        if (id == R.id.action_sync) {
            container.workScheduler().enqueueManualSync(true);
            Snackbar.make(drawerLayout, R.string.settings_sync_started, Snackbar.LENGTH_SHORT)
                    .show();
            return true;
        }
        if (id == R.id.action_notifications) {
            toggleNotifications();
            return true;
        }
        if (id == R.id.action_add) {
            openAddFeed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void toggleNotifications() {
        if (settings.notificationsEnabled()) {
            settings.setNotificationsEnabled(false);
            Snackbar.make(drawerLayout, R.string.notifications_status_off,
                    Snackbar.LENGTH_SHORT).show();
            invalidateOptionsMenu();
            return;
        }
        settings.setNotificationsEnabled(true);
        invalidateOptionsMenu();
        if (Build.VERSION.SDK_INT >= 33
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestNotificationPermissionWithRationale();
            Snackbar.make(drawerLayout, R.string.permission_notifications_rationale,
                    Snackbar.LENGTH_LONG).show();
        } else {
            Snackbar.make(drawerLayout, R.string.notifications_status_on,
                    Snackbar.LENGTH_SHORT).show();
        }
    }

    private boolean isNotificationActive() {
        return settings.notificationsEnabled() && container.notificationHelper()
                .canPostNotifications();
    }

    private void maybeAskNotificationPermission() {
        if (Build.VERSION.SDK_INT < 33 || !settings.notificationsEnabled()
                || settings.isNotificationPermissionAsked()) {
            return;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) {
            return;
        }
        drawerLayout.postDelayed(this::requestNotificationPermissionWithRationale, 1200);
    }

    private void requestNotificationPermissionWithRationale() {
        if (Build.VERSION.SDK_INT < 33) {
            return;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) {
            invalidateOptionsMenu();
            return;
        }
        if (!settings.isNotificationPermissionAsked()) {
            settings.setNotificationPermissionAsked(true);
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.settings_notifications_permission)
                    .setMessage(R.string.permission_notifications_rationale)
                    .setPositiveButton(R.string.action_enable,
                            (dialog, which) -> notificationPermissionLauncher.launch(
                                    Manifest.permission.POST_NOTIFICATIONS))
                    .setNegativeButton(R.string.action_cancel, null)
                    .show();
        } else {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
        }
    }

    private void openNotificationSystemSettings() {
        Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
        intent.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
        startActivity(intent);
    }

    private void renderStoredSyncStatus() {
        long last = settings.getLastSyncTime();
        String stored = settings.getLastSyncStatus();
        if (last <= 0) {
            syncStatus.setText(R.string.sync_status_never);
        } else {
            String message = stored == null || stored.trim().isEmpty()
                    ? getString(R.string.sync_status_complete, 0, 0) : stored;
            syncStatus.setText(message + " \u2022 " + DateUtils.relative(this, last));
        }
        updateSyncDrawableTint();
    }

    private void renderSyncStatus(SyncStatus status) {
        if (status == null || syncStatus == null) {
            return;
        }
        if (status.state == SyncStatus.State.RUNNING) {
            if (syncProgress != null) {
                syncProgress.setVisibility(View.VISIBLE);
                if (status.feedsTotal > 0) {
                    syncProgress.setIndeterminate(false);
                    syncProgress.setMax(status.feedsTotal);
                    syncProgress.setProgressCompat(status.feedsCompleted, true);
                    syncStatus.setText(getString(R.string.sync_status_progress,
                            status.feedsCompleted, status.feedsTotal));
                } else {
                    syncProgress.setIndeterminate(true);
                    syncStatus.setText(R.string.sync_status_syncing);
                }
            } else {
                syncStatus.setText(R.string.sync_status_syncing);
            }
            setSyncTint(ContextCompat.getColor(this, R.color.sync_active));
        } else if (status.state == SyncStatus.State.ERROR) {
            hideSyncProgress();
            syncStatus.setText(status.message);
            setSyncTint(ContextCompat.getColor(this, R.color.sync_error));
        } else if (status.state == SyncStatus.State.COMPLETE) {
            hideSyncProgress();
            String message = status.message;
            if (message == null || message.trim().isEmpty()) {
                message = getString(R.string.sync_status_complete,
                        status.feedsSucceeded, status.newArticles);
            }
            syncStatus.setText(message + " \u2022 "
                    + DateUtils.relative(this, status.time));
            setSyncTint(ContextCompat.getColor(this, R.color.on_surface_variant));
        } else {
            hideSyncProgress();
            renderStoredSyncStatus();
        }
    }

    private void hideSyncProgress() {
        if (syncProgress != null) {
            syncProgress.setVisibility(View.GONE);
        }
    }

    private void startStartupSync() {
        container.ioExecutor().execute(() -> {
            try {
                SyncStatus current = container.syncStatusBus().get();
                if (current != null && current.state == SyncStatus.State.RUNNING) {
                    return;
                }
                if (container.database().feedDao().countActive() > 0) {
                    container.workScheduler().enqueueStartupSync();
                }
            } catch (RuntimeException e) {
                Log.w("MainActivity", "Could not start startup sync", e);
            }
        });
    }

    private void setSyncTint(int color) {
        Drawable[] drawables = syncStatus.getCompoundDrawablesRelative();
        if (drawables.length > 0 && drawables[0] != null) {
            Drawable drawable = DrawableCompat.wrap(drawables[0]).mutate();
            DrawableCompat.setTint(drawable, color);
            syncStatus.setCompoundDrawablesRelative(drawable, null, null, null);
        }
        syncStatus.setTextColor(ContextCompat.getColor(this, R.color.on_surface_variant));
    }

    private void updateSyncDrawableTint() {
        if (syncStatus == null) {
            return;
        }
        setSyncTint(ContextCompat.getColor(this, R.color.on_surface_variant));
    }

    private void showSyncHistory() {
        container.ioExecutor().execute(() -> {
            List<SyncRunEntity> runs = container.database().syncRunDao().getRecent(20);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                if (runs == null || runs.isEmpty()) {
                    new MaterialAlertDialogBuilder(this)
                            .setTitle(R.string.sync_status_dialog)
                            .setMessage(R.string.sync_status_never)
                            .setPositiveButton(R.string.action_close, null)
                            .show();
                    return;
                }
                StringBuilder message = new StringBuilder();
                for (SyncRunEntity run : runs) {
                    message.append(run.status)
                            .append(" \u2022 ")
                            .append(DateUtils.relative(this, run.startedAt))
                            .append(" \u2022 ")
                            .append(run.feedsSucceeded).append(" ok, ")
                            .append(run.feedsFailed).append(" failed, ")
                            .append(run.newArticles).append(" new");
                    if (run.errorSummary != null && !run.errorSummary.isEmpty()) {
                        message.append("\n").append(run.errorSummary);
                    }
                    message.append("\n\n");
                }
                new MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.sync_status_dialog)
                        .setMessage(message.toString().trim())
                        .setPositiveButton(R.string.action_close, null)
                        .show();
            });
        });
    }
}
