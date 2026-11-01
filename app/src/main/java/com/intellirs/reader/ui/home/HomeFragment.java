package com.intellirs.reader.ui.home;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.intellirs.reader.R;
import com.intellirs.reader.data.entity.CategoryEntity;
import com.intellirs.reader.data.entity.FeedEntity;
import com.intellirs.reader.data.entity.FeedImportance;
import com.intellirs.reader.data.model.FeedListItem;
import com.intellirs.reader.domain.model.ArticleFilter;
import com.intellirs.reader.domain.model.SyncStatus;
import com.intellirs.reader.ui.MainActivity;
import com.intellirs.reader.ui.categories.CategoriesActivity;
import com.intellirs.reader.ui.common.OpmlImportFlow;
import com.intellirs.reader.ui.opml.OpmlPreviewActivity;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class HomeFragment extends Fragment implements HomeAdapter.Listener {
    private HomeViewModel viewModel;
    private HomeAdapter adapter;
    private RecyclerView recyclerView;
    private SwipeRefreshLayout swipeRefresh;
    private View emptyState;
    private List<FeedListItem> feeds = new ArrayList<>();
    private List<CategoryEntity> categories = new ArrayList<>();
    private SmartCounts counts = new SmartCounts();
    private Set<String> collapsed = new HashSet<>();
    private boolean feedsLoaded;
    private boolean smartViewsExpanded;
    private ActivityResultLauncher<String[]> opmlPicker;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        opmlPicker = registerForActivityResult(new ActivityResultContracts.OpenDocument(),
                uri -> {
                    if (uri != null) {
                        importOpml(uri);
                    }
                });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        recyclerView = view.findViewById(R.id.home_list);
        swipeRefresh = view.findViewById(R.id.swipe_refresh);
        emptyState = view.findViewById(R.id.empty_state);
        view.findViewById(R.id.empty_add).setOnClickListener(v -> openAddFeed());

        adapter = new HomeAdapter(this);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);

        viewModel = new ViewModelProvider(this).get(HomeViewModel.class);
        collapsed = new HashSet<>(viewModel.container().settingsRepository()
                .getCollapsedCategories());
        viewModel.categories().observe(getViewLifecycleOwner(), categories -> {
            this.categories = categories == null ? new ArrayList<>() : categories;
            rebuildRows();
        });
        viewModel.feeds().observe(getViewLifecycleOwner(), feedList -> {
            feeds = feedList == null ? new ArrayList<>() : feedList;
            feedsLoaded = true;
            rebuildRows();
            viewModel.refreshCounts();
        });
        viewModel.counts().observe(getViewLifecycleOwner(), smartCounts -> {
            counts = smartCounts == null ? new SmartCounts() : smartCounts;
            rebuildRows();
        });
        viewModel.container().syncStatusBus().observe().observe(getViewLifecycleOwner(),
                this::onSyncStatus);
        swipeRefresh.setColorSchemeResources(R.color.primary);
        swipeRefresh.setOnRefreshListener(() -> {
            swipeRefresh.setRefreshing(true);
            viewModel.container().workScheduler().enqueueManualSync(true);
            Snackbar.make(swipeRefresh, R.string.settings_sync_started, Snackbar.LENGTH_SHORT)
                    .show();
        });
        viewModel.refreshCounts();
    }

    private void onSyncStatus(SyncStatus status) {
        if (status == null) {
            return;
        }
        if (status.state != SyncStatus.State.RUNNING) {
            swipeRefresh.setRefreshing(false);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (viewModel != null) {
            viewModel.refreshCounts();
        }
        if (adapter != null) {
            rebuildRows();
        }
    }

    private void rebuildRows() {
        if (adapter == null) {
            return;
        }
        List<HomeRow> rows = new ArrayList<>();
        if (!feeds.isEmpty()) {
            addSmartRows(rows);
            addImportantFeeds(rows);
            addCategoryGroups(rows);
        }
        adapter.submitList(rows);
        boolean empty = feedsLoaded && feeds.isEmpty();
        emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
        recyclerView.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private void addSmartRows(List<HomeRow> rows) {
        rows.add(HomeRow.smartToggle(getString(R.string.home_smart_views),
                getString(smartViewsExpanded ? R.string.home_smart_views_hide
                        : R.string.home_smart_views_show), smartViewsExpanded));
        if (!smartViewsExpanded) {
            return;
        }
        rows.add(HomeRow.smart(-101, getString(R.string.nav_all_articles), "",
                counts.all, R.drawable.ic_inbox, ArticleFilter.all()));
        rows.add(HomeRow.smart(-102, getString(R.string.nav_unread), "",
                counts.unread, R.drawable.ic_label, ArticleFilter.unread()));
        rows.add(HomeRow.smart(-103, getString(R.string.nav_important), "",
                counts.important, R.drawable.ic_priority_high, ArticleFilter.important()));
        rows.add(HomeRow.smart(-104, getString(R.string.nav_starred), "",
                counts.starred, R.drawable.ic_star, ArticleFilter.starred()));
        rows.add(HomeRow.smart(-105, getString(R.string.smart_saved), "",
                counts.archived, R.drawable.ic_download, ArticleFilter.archived()));
        rows.add(HomeRow.smart(-106, getString(R.string.smart_recently_read), "",
                counts.history, R.drawable.ic_history, ArticleFilter.history()));
    }

    private void addImportantFeeds(List<HomeRow> rows) {
        List<FeedListItem> important = new ArrayList<>();
        for (FeedListItem item : feeds) {
            if (item.feed.feedPriority == FeedImportance.CRITICAL
                    || item.feed.feedPriority == FeedImportance.HIGH) {
                important.add(item);
            }
        }
        if (important.isEmpty()) {
            return;
        }
        important.sort(feedComparator());
        rows.add(HomeRow.section(-20, getString(R.string.home_important_feeds),
                getString(R.string.feed_count, important.size())));
        for (FeedListItem item : important) {
            rows.add(HomeRow.feed(item));
        }
    }

    private void addCategoryGroups(List<HomeRow> rows) {
        Map<Long, CategoryEntity> categoryMap = new HashMap<>();
        Map<Long, Integer> depthMap = new HashMap<>();
        Map<Long, String> pathMap = new HashMap<>();
        for (CategoryEntity category : categories) {
            categoryMap.put(category.id, category);
        }
        for (CategoryEntity category : categories) {
            computePath(category, categoryMap, depthMap, pathMap, new HashSet<>());
        }
        List<CategoryEntity> orderedCategories = new ArrayList<>(categories);
        orderedCategories.sort(Comparator.comparing(
                category -> pathMap.getOrDefault(category.id,
                        category.name == null ? "" : category.name),
                String.CASE_INSENSITIVE_ORDER));

        Map<Long, List<FeedListItem>> grouped = new LinkedHashMap<>();
        List<FeedListItem> uncategorized = new ArrayList<>();
        for (FeedListItem item : feeds) {
            if (item.feed.feedPriority == FeedImportance.CRITICAL
                    || item.feed.feedPriority == FeedImportance.HIGH) {
                continue;
            }
            Long categoryId = item.feed.categoryId;
            if (categoryId == null) {
                uncategorized.add(item);
            } else {
                grouped.computeIfAbsent(categoryId, key -> new ArrayList<>()).add(item);
            }
        }

        List<HomeRow> groupRows = new ArrayList<>();
        for (CategoryEntity category : orderedCategories) {
            List<FeedListItem> categoryFeeds = grouped.get(category.id);
            if (categoryFeeds == null || categoryFeeds.isEmpty()) {
                continue;
            }
            String key = String.valueOf(category.id);
            boolean isCollapsed = collapsed.contains(key);
            groupRows.add(HomeRow.category(category, category.name,
                    unreadCount(categoryFeeds), depthMap.getOrDefault(category.id, 0),
                    isCollapsed));
            if (!isCollapsed) {
                categoryFeeds.sort(feedComparator());
                for (FeedListItem item : categoryFeeds) {
                    groupRows.add(HomeRow.feed(item));
                }
            }
        }
        if (!uncategorized.isEmpty()) {
            boolean isCollapsed = collapsed.contains("uncat");
            groupRows.add(HomeRow.category(null, getString(R.string.home_uncategorized),
                    unreadCount(uncategorized), 0, isCollapsed));
            if (!isCollapsed) {
                uncategorized.sort(feedComparator());
                for (FeedListItem item : uncategorized) {
                    groupRows.add(HomeRow.feed(item));
                }
            }
        }
        if (!groupRows.isEmpty()) {
            rows.add(HomeRow.section(-30, getString(R.string.home_all_feeds),
                    getString(R.string.feed_count, feeds.size())));
            rows.addAll(groupRows);
        }
    }

    private int unreadCount(List<FeedListItem> items) {
        int total = 0;
        for (FeedListItem item : items) {
            total += item.unreadCount;
        }
        return total;
    }

    private Comparator<FeedListItem> feedComparator() {
        String sort = viewModel.container().settingsRepository().getFeedSort();
        switch (sort) {
            case "activity":
                return (left, right) -> Long.compare(right.latestPublishedAt,
                        left.latestPublishedAt);
            case "unread":
                return (left, right) -> Integer.compare(right.unreadCount, left.unreadCount);
            case "alphabetical":
                return (left, right) -> left.feed.title.compareToIgnoreCase(right.feed.title);
            case "custom":
                return (left, right) -> Integer.compare(left.feed.sortOrder, right.feed.sortOrder);
            case "priority":
            default:
                return (left, right) -> {
                    int priority = Integer.compare(left.feed.feedPriority.rank(),
                            right.feed.feedPriority.rank());
                    return priority != 0 ? priority
                            : left.feed.title.compareToIgnoreCase(right.feed.title);
                };
        }
    }

    private void computePath(CategoryEntity category, Map<Long, CategoryEntity> byId,
                             Map<Long, Integer> depths, Map<Long, String> paths,
                             Set<Long> visiting) {
        if (paths.containsKey(category.id)) {
            return;
        }
        if (!visiting.add(category.id)) {
            paths.put(category.id, category.name == null ? "" : category.name);
            depths.put(category.id, 0);
            return;
        }
        CategoryEntity parent = category.parentId == null ? null : byId.get(category.parentId);
        if (parent == null) {
            depths.put(category.id, 0);
            paths.put(category.id, category.name == null ? "" : category.name);
        } else {
            computePath(parent, byId, depths, paths, visiting);
            depths.put(category.id, depths.getOrDefault(parent.id, 0) + 1);
            paths.put(category.id, paths.getOrDefault(parent.id, "")
                    + "/" + (category.name == null ? "" : category.name));
        }
        visiting.remove(category.id);
    }

    @Override
    public void onSmartClick(HomeRow row) {
        if (row.filter != null && getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).openArticles(row.filter, row.title);
        }
    }

    @Override
    public void onSmartToggleClick(HomeRow row) {
        smartViewsExpanded = !smartViewsExpanded;
        rebuildRows();
    }

    @Override
    public void onCategoryClick(HomeRow row) {
        String key = row.category == null ? "uncat" : String.valueOf(row.category.id);
        if (!collapsed.remove(key)) {
            collapsed.add(key);
        }
        viewModel.container().settingsRepository().setCollapsedCategories(collapsed);
        row.collapsed = collapsed.contains(key);
        rebuildRows();
    }

    @Override
    public void onFeedClick(HomeRow row) {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).openFeedArticles(row.feedItem.feed);
        }
    }

    @Override
    public void onFeedLongClick(HomeRow row) {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).openFeedDetails(row.feedItem.feed.id);
        }
    }

    @Override
    public void onFeedMenuClick(HomeRow row) {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).openFeedDetails(row.feedItem.feed.id);
        }
    }

    @Override
    public void onNotificationToggle(HomeRow row) {
        boolean newValue = !row.feedItem.feed.notificationEnabled;
        viewModel.container().ioExecutor().execute(() -> {
            viewModel.container().feedRepository().setNotificationsEnabled(
                    row.feedItem.feed.id, newValue);
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    if (isAdded()) {
                        Snackbar.make(requireView(),
                                newValue ? R.string.feed_notifications_on
                                        : R.string.feed_notifications_off,
                                Snackbar.LENGTH_SHORT).show();
                    }
                });
            }
        });
        viewModel.refreshCounts();
    }

    private void openAddFeed() {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).openAddFeed();
        }
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull android.view.MenuInflater inflater) {
        inflater.inflate(R.menu.home_menu, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_sort_feeds) {
            showSortDialog();
            return true;
        }
        if (id == R.id.action_import_opml) {
            opmlPicker.launch(new String[]{"text/xml", "application/xml", "text/x-opml",
                    "application/octet-stream", "*/*"});
            return true;
        }
        if (id == R.id.action_manage_categories) {
            startActivity(new Intent(requireContext(), CategoriesActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showSortDialog() {
        String[] values = new String[]{"priority", "activity", "unread", "alphabetical", "custom"};
        String[] labels = new String[]{
                getString(R.string.sort_by_priority),
                getString(R.string.sort_by_activity),
                getString(R.string.sort_by_unread),
                getString(R.string.sort_by_alphabetical),
                getString(R.string.sort_by_custom)
        };
        String current = viewModel.container().settingsRepository().getFeedSort();
        int selected = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(current)) {
                selected = i;
                break;
            }
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.sort_dialog_title)
                .setSingleChoiceItems(labels, selected, (dialog, which) -> {
                    viewModel.container().settingsRepository().setFeedSort(values[which]);
                    dialog.dismiss();
                    rebuildRows();
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void importOpml(Uri uri) {
        OpmlImportFlow.prepare(requireContext(), viewModel.container(), uri,
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
                        if (!isAdded()) {
                            return;
                        }
                        Toast.makeText(requireContext(),
                                getString(R.string.opml_import_error, message),
                                Toast.LENGTH_LONG).show();
                    }
                });
    }
}
