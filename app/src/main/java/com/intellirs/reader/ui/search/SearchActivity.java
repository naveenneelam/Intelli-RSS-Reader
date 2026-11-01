package com.intellirs.reader.ui.search;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.gson.Gson;
import com.intellirs.reader.R;
import com.intellirs.reader.data.entity.ArticleEntity;
import com.intellirs.reader.data.entity.CategoryEntity;
import com.intellirs.reader.data.entity.FeedEntity;
import com.intellirs.reader.data.entity.SavedSearchEntity;
import com.intellirs.reader.domain.model.ArticleFilter;
import com.intellirs.reader.ui.BaseActivity;
import com.intellirs.reader.ui.articles.ArticleAdapter;
import com.intellirs.reader.ui.articles.ArticleListState;
import com.intellirs.reader.ui.articles.ArticleListViewModel;
import com.intellirs.reader.ui.reader.ReaderActivity;
import com.intellirs.reader.ui.reader.ReaderContract;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SearchActivity extends BaseActivity implements ArticleAdapter.Listener {
    private ArticleListViewModel viewModel;
    private ArticleAdapter adapter;
    private TextInputEditText searchInput;
    private TextInputLayout searchLayout;
    private RecyclerView resultsView;
    private View emptyState;
    private TextView emptyText;
    private TextView filterSummary;
    private ProgressBar progress;
    private ChipGroup savedGroup;
    private View savedSection;
    private Chip unreadChip;
    private Chip starredChip;
    private Chip archivedChip;
    private Chip videoChip;
    private Chip audioChip;
    private LinearLayoutManager layoutManager;
    private final Handler debounce = new Handler(Looper.getMainLooper());
    private Runnable pendingSearch;

    private long feedFilterId = -1L;
    private long categoryFilterId = -1L;
    private String readState = "ANY";
    private String media = "ANY";
    private String importance = "ANY";
    private int minRelevance;
    private long publishedAfter;
    private long publishedBefore;
    private boolean bookmarkedOnly;
    private boolean archivedOnly;
    private boolean suppressChipCallbacks;
    private boolean hasSearched;
    private ArticleFilter currentFilter = ArticleFilter.all();
    private List<FeedEntity> feeds = new ArrayList<>();
    private List<CategoryEntity> categories = new ArrayList<>();
    private List<SavedSearchEntity> savedSearches = new ArrayList<>();
    private final Gson gson = new Gson();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }
        toolbar.setNavigationOnClickListener(view -> finish());

        searchInput = findViewById(R.id.search_input);
        searchLayout = findViewById(R.id.search_layout);
        resultsView = findViewById(R.id.search_results);
        emptyState = findViewById(R.id.empty_state);
        emptyText = findViewById(R.id.empty_text);
        filterSummary = findViewById(R.id.filter_summary);
        progress = findViewById(R.id.progress);
        savedGroup = findViewById(R.id.saved_group);
        savedSection = findViewById(R.id.saved_section);
        unreadChip = findViewById(R.id.chip_unread);
        starredChip = findViewById(R.id.chip_starred);
        archivedChip = findViewById(R.id.chip_archived);
        videoChip = findViewById(R.id.chip_video);
        audioChip = findViewById(R.id.chip_audio);

        adapter = new ArticleAdapter(this);
        layoutManager = new LinearLayoutManager(this);
        resultsView.setLayoutManager(layoutManager);
        resultsView.setAdapter(adapter);
        viewModel = new ViewModelProvider(this).get(ArticleListViewModel.class);
        viewModel.state().observe(this, this::render);
        viewModel.container().settingsRepository().observeRevision()
                .observe(this, revision -> configureAdapter());

        setupInput();
        setupChips();
        configureAdapter();
        findViewById(R.id.filter_button).setOnClickListener(view -> showFilterDialog());
        resultsView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (dy > 0 && layoutManager.findLastVisibleItemPosition()
                        >= layoutManager.getItemCount() - 6) {
                    viewModel.loadMore();
                }
            }
        });
        container.articleRepository().observeSavedSearches()
                .observe(this, saved -> {
                    savedSearches = saved == null ? new ArrayList<>() : saved;
                    renderSavedSearches();
                });
        loadFiltersData();
        showEmptyPrompt();
    }

    private void setupInput() {
        searchInput.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (pendingSearch != null) {
                    debounce.removeCallbacks(pendingSearch);
                }
                pendingSearch = () -> executeSearch();
                debounce.postDelayed(pendingSearch, 350);
                updateSavedVisibility();
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {
            }
        });
        searchInput.setOnEditorActionListener((textView, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                if (pendingSearch != null) {
                    debounce.removeCallbacks(pendingSearch);
                }
                executeSearch();
                return true;
            }
            return false;
        });
    }

    private void setupChips() {
        unreadChip.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (suppressChipCallbacks) {
                return;
            }
            readState = isChecked ? "UNREAD" : "ANY";
            onQuickFilterChanged();
        });
        starredChip.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (suppressChipCallbacks) {
                return;
            }
            bookmarkedOnly = isChecked;
            onQuickFilterChanged();
        });
        archivedChip.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (suppressChipCallbacks) {
                return;
            }
            archivedOnly = isChecked;
            onQuickFilterChanged();
        });
        videoChip.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (suppressChipCallbacks) {
                return;
            }
            if (isChecked) {
                suppressChipCallbacks = true;
                audioChip.setChecked(false);
                suppressChipCallbacks = false;
                media = "VIDEO";
            } else if ("VIDEO".equals(media)) {
                media = "ANY";
            }
            onQuickFilterChanged();
        });
        audioChip.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (suppressChipCallbacks) {
                return;
            }
            if (isChecked) {
                suppressChipCallbacks = true;
                videoChip.setChecked(false);
                suppressChipCallbacks = false;
                media = "AUDIO";
            } else if ("AUDIO".equals(media)) {
                media = "ANY";
            }
            onQuickFilterChanged();
        });
    }

    private void onQuickFilterChanged() {
        updateFilterSummary();
        if (hasCriteria()) {
            executeSearch();
        } else {
            showEmptyPrompt();
        }
    }

    private void configureAdapter() {
        if (adapter == null) {
            return;
        }
        adapter.configure(settings.getListDensity(), settings.showThumbnails(),
                settings.showSummaries(), settings.isAllowHttp());
    }

    private void executeSearch() {
        String query = searchInput.getText() == null ? ""
                : searchInput.getText().toString().trim();
        if (query.isEmpty() && !hasActiveFilters()) {
            showEmptyPrompt();
            return;
        }
        hasSearched = true;
        currentFilter = buildFilter(query);
        progress.setVisibility(View.VISIBLE);
        emptyState.setVisibility(View.GONE);
        viewModel.setFilter(currentFilter);
        viewModel.loadInitial();
        updateSavedVisibility();
    }

    private ArticleFilter buildFilter(String query) {
        ArticleFilter filter;
        if (feedFilterId > 0) {
            filter = ArticleFilter.feed(feedFilterId);
        } else if (categoryFilterId > 0) {
            filter = ArticleFilter.category(categoryFilterId);
        } else {
            filter = ArticleFilter.all();
        }
        filter.query = query;
        filter.readState = readState;
        filter.media = media;
        filter.importance = importance;
        filter.minRelevance = minRelevance;
        filter.publishedAfter = publishedAfter;
        filter.publishedBefore = publishedBefore;
        filter.bookmarkedOnly = bookmarkedOnly;
        filter.archivedOnly = archivedOnly;
        return filter;
    }

    private boolean hasActiveFilters() {
        return feedFilterId > 0 || categoryFilterId > 0
                || !"ANY".equals(readState) || !"ANY".equals(media)
                || !"ANY".equals(importance) || minRelevance > 0
                || publishedAfter > 0 || publishedBefore > 0
                || bookmarkedOnly || archivedOnly;
    }

    private boolean hasCriteria() {
        String query = searchInput.getText() == null ? ""
                : searchInput.getText().toString().trim();
        return !query.isEmpty() || hasActiveFilters();
    }

    private void render(ArticleListState state) {
        if (state == null) {
            return;
        }
        progress.setVisibility(hasSearched && (state.loading || state.loadingMore)
                ? View.VISIBLE : View.GONE);
        if (!hasSearched) {
            emptyState.setVisibility(View.VISIBLE);
            resultsView.setVisibility(View.GONE);
            return;
        }
        adapter.submitList(state.articles);
        boolean empty = !state.loading && state.articles.isEmpty() && hasSearched;
        emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
        if (empty) {
            emptyText.setText(R.string.search_no_results);
        }
        resultsView.setVisibility(empty ? View.GONE : View.VISIBLE);
        if (state.error != null) {
            Snackbar.make(findViewById(R.id.search_results),
                    getString(R.string.articles_load_error, state.error),
                    Snackbar.LENGTH_LONG).show();
        }
    }

    private void showEmptyPrompt() {
        hasSearched = false;
        progress.setVisibility(View.GONE);
        emptyState.setVisibility(View.VISIBLE);
        resultsView.setVisibility(View.GONE);
        emptyText.setText(R.string.search_empty_prompt);
        updateSavedVisibility();
    }

    private void updateSavedVisibility() {
        boolean show = savedSearches != null && !savedSearches.isEmpty()
                && !hasActiveFilters()
                && (searchInput == null || searchInput.getText() == null
                || searchInput.getText().toString().trim().isEmpty());
        savedSection.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void renderSavedSearches() {
        if (savedGroup == null) {
            return;
        }
        savedGroup.removeAllViews();
        for (SavedSearchEntity saved : savedSearches) {
            Chip chip = new Chip(this);
            chip.setText(saved.name);
            chip.setCheckable(false);
            chip.setOnClickListener(view -> applySavedSearch(saved));
            chip.setOnLongClickListener(view -> {
                new MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.search_saved)
                        .setMessage(saved.name)
                        .setPositiveButton(R.string.action_delete, (dialog, which) ->
                                container.ioExecutor().execute(() ->
                                        container.articleRepository().deleteSavedSearch(saved)))
                        .setNegativeButton(R.string.action_cancel, null)
                        .show();
                return true;
            });
            savedGroup.addView(chip);
        }
        updateSavedVisibility();
    }

    private void applySavedSearch(SavedSearchEntity saved) {
        try {
            ArticleFilter filter = gson.fromJson(saved.filterJson, ArticleFilter.class);
            if (filter == null) {
                filter = new ArticleFilter();
            }
            currentFilter = filter;
            feedFilterId = filter.mode == ArticleFilter.Mode.FEED ? filter.feedId : -1L;
            categoryFilterId = filter.mode == ArticleFilter.Mode.CATEGORY
                    ? filter.categoryId : -1L;
            readState = filter.readState == null ? "ANY" : filter.readState;
            media = filter.media == null ? "ANY" : filter.media;
            importance = filter.importance == null ? "ANY" : filter.importance;
            minRelevance = filter.minRelevance;
            publishedAfter = filter.publishedAfter;
            publishedBefore = filter.publishedBefore;
            bookmarkedOnly = filter.bookmarkedOnly;
            archivedOnly = filter.archivedOnly;
            String query = filter.query == null ? "" : filter.query;
            suppressChipCallbacks = true;
            searchInput.setText(query);
            searchInput.setSelection(searchInput.length());
            syncChipsFromState();
            suppressChipCallbacks = false;
            updateFilterSummary();
            hasSearched = true;
            progress.setVisibility(View.VISIBLE);
            viewModel.setFilter(buildFilter(query));
            viewModel.loadInitial();
            updateSavedVisibility();
        } catch (RuntimeException e) {
            Snackbar.make(resultsView, R.string.search_saved_missing,
                    Snackbar.LENGTH_LONG).show();
        }
    }

    private void syncChipsFromState() {
        boolean previous = suppressChipCallbacks;
        suppressChipCallbacks = true;
        unreadChip.setChecked("UNREAD".equals(readState));
        starredChip.setChecked(bookmarkedOnly);
        archivedChip.setChecked(archivedOnly);
        videoChip.setChecked("VIDEO".equals(media));
        audioChip.setChecked("AUDIO".equals(media));
        suppressChipCallbacks = previous;
    }

    private void updateFilterSummary() {
        int count = 0;
        if (feedFilterId > 0 || categoryFilterId > 0) {
            count++;
        }
        if (!"ANY".equals(readState)) {
            count++;
        }
        if (!"ANY".equals(media)) {
            count++;
        }
        if (!"ANY".equals(importance)) {
            count++;
        }
        if (minRelevance > 0) {
            count++;
        }
        if (publishedAfter > 0 || publishedBefore > 0) {
            count++;
        }
        if (bookmarkedOnly) {
            count++;
        }
        if (archivedOnly) {
            count++;
        }
        filterSummary.setText(count == 0 ? ""
                : getString(R.string.search_filter_summary, count));
    }

    private void loadFiltersData() {
        container.ioExecutor().execute(() -> {
            List<FeedEntity> loadedFeeds = container.feedRepository().getActiveFeeds();
            List<CategoryEntity> loadedCategories = container.feedRepository().getCategories();
            runOnUiThread(() -> {
                feeds = loadedFeeds == null ? new ArrayList<>() : loadedFeeds;
                categories = loadedCategories == null ? new ArrayList<>() : loadedCategories;
            });
        });
    }

    private void showFilterDialog() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_search_filters,
                null, false);
        Spinner feedSpinner = view.findViewById(R.id.filter_feed_spinner);
        Spinner categorySpinner = view.findViewById(R.id.filter_category_spinner);
        Spinner readSpinner = view.findViewById(R.id.filter_read_spinner);
        Spinner mediaSpinner = view.findViewById(R.id.filter_media_spinner);
        Spinner importanceSpinner = view.findViewById(R.id.filter_importance_spinner);
        SeekBar relevanceSeek = view.findViewById(R.id.filter_relevance_seek);
        TextView relevanceLabel = view.findViewById(R.id.filter_relevance_label);
        CheckBox bookmarked = view.findViewById(R.id.filter_bookmarked);
        CheckBox archived = view.findViewById(R.id.filter_archived);
        MaterialButton fromButton = view.findViewById(R.id.filter_date_from);
        MaterialButton toButton = view.findViewById(R.id.filter_date_to);
        MaterialButton clearDates = view.findViewById(R.id.filter_clear_dates);

        List<Long> feedIds = new ArrayList<>();
        List<String> feedLabels = new ArrayList<>();
        feedLabels.add(getString(R.string.search_any));
        feedIds.add(-1L);
        int feedSelection = 0;
        for (int i = 0; i < feeds.size(); i++) {
            feedLabels.add(feeds.get(i).title);
            feedIds.add(feeds.get(i).id);
            if (feeds.get(i).id == feedFilterId) {
                feedSelection = i + 1;
            }
        }
        ArrayAdapter<String> feedAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, feedLabels);
        feedAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        feedSpinner.setAdapter(feedAdapter);
        feedSpinner.setSelection(feedSelection);

        List<Long> categoryIds = new ArrayList<>();
        List<String> categoryLabels = new ArrayList<>();
        categoryLabels.add(getString(R.string.search_any));
        categoryIds.add(-1L);
        int categorySelection = 0;
        for (int i = 0; i < categories.size(); i++) {
            categoryLabels.add(categories.get(i).name);
            categoryIds.add(categories.get(i).id);
            if (categories.get(i).id == categoryFilterId) {
                categorySelection = i + 1;
            }
        }
        ArrayAdapter<String> categoryAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, categoryLabels);
        categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        categorySpinner.setAdapter(categoryAdapter);
        categorySpinner.setSelection(categorySelection);

        String[] readValues = new String[]{"ANY", "UNREAD", "READ"};
        String[] readLabels = new String[]{getString(R.string.search_any),
                getString(R.string.unread), getString(R.string.read)};
        String[] mediaValues = new String[]{"ANY", "IMAGE", "VIDEO", "AUDIO"};
        String[] mediaLabels = new String[]{getString(R.string.search_any),
                getString(R.string.search_media_image), getString(R.string.search_media_video),
                getString(R.string.search_media_audio)};
        String[] importanceValues = new String[]{"ANY", "CRITICAL", "HIGH", "NORMAL", "LOW", "MUTED"};
        String[] importanceLabels = new String[]{getString(R.string.search_any),
                getString(R.string.importance_critical), getString(R.string.importance_high),
                getString(R.string.importance_normal), getString(R.string.importance_low),
                getString(R.string.importance_muted)};
        setSpinner(readSpinner, readLabels, indexOf(readValues, readState));
        setSpinner(mediaSpinner, mediaLabels, indexOf(mediaValues, media));
        setSpinner(importanceSpinner, importanceLabels, indexOf(importanceValues, importance));
        relevanceSeek.setProgress(minRelevance);
        relevanceLabel.setText(getString(R.string.search_filter_relevance) + ": " + minRelevance);
        relevanceSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                relevanceLabel.setText(getString(R.string.search_filter_relevance)
                        + ": " + progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        bookmarked.setChecked(bookmarkedOnly);
        archived.setChecked(archivedOnly);

        final long[] dialogFrom = new long[]{publishedAfter};
        final long[] dialogTo = new long[]{publishedBefore};
        updateDateButton(fromButton, dialogFrom[0], true);
        updateDateButton(toButton, dialogTo[0], false);
        fromButton.setOnClickListener(v -> pickDate(dialogFrom[0], true, value -> {
            dialogFrom[0] = value;
            updateDateButton(fromButton, value, true);
        }));
        toButton.setOnClickListener(v -> pickDate(dialogTo[0], false, value -> {
            dialogTo[0] = value;
            updateDateButton(toButton, value, false);
        }));
        clearDates.setOnClickListener(v -> {
            dialogFrom[0] = 0;
            dialogTo[0] = 0;
            updateDateButton(fromButton, 0, true);
            updateDateButton(toButton, 0, false);
        });

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.search_filters)
                .setView(view)
                .setPositiveButton(R.string.action_apply, (dialog, which) -> {
                    long selectedFeed = feedIds.get(feedSpinner.getSelectedItemPosition());
                    long selectedCategory = categoryIds.get(
                            categorySpinner.getSelectedItemPosition());
                    feedFilterId = selectedFeed;
                    categoryFilterId = selectedFeed > 0 ? -1L : selectedCategory;
                    readState = readValues[readSpinner.getSelectedItemPosition()];
                    media = mediaValues[mediaSpinner.getSelectedItemPosition()];
                    importance = importanceValues[importanceSpinner.getSelectedItemPosition()];
                    minRelevance = relevanceSeek.getProgress();
                    publishedAfter = dialogFrom[0];
                    publishedBefore = dialogTo[0];
                    bookmarkedOnly = bookmarked.isChecked();
                    archivedOnly = archived.isChecked();
                    syncChipsFromState();
                    updateFilterSummary();
                    if (hasCriteria()) {
                        executeSearch();
                    } else {
                        showEmptyPrompt();
                    }
                })
                .setNegativeButton(R.string.action_cancel, null)
                .setNeutralButton(R.string.action_clear, (dialog, which) -> {
                    feedFilterId = -1L;
                    categoryFilterId = -1L;
                    readState = "ANY";
                    media = "ANY";
                    importance = "ANY";
                    minRelevance = 0;
                    publishedAfter = 0;
                    publishedBefore = 0;
                    bookmarkedOnly = false;
                    archivedOnly = false;
                    syncChipsFromState();
                    updateFilterSummary();
                    String query = searchInput.getText() == null ? ""
                            : searchInput.getText().toString().trim();
                    if (!query.isEmpty()) {
                        executeSearch();
                    } else {
                        showEmptyPrompt();
                    }
                });
        builder.show();
    }

    private interface DateCallback {
        void onPicked(long millis);
    }

    private void pickDate(long existing, boolean from, DateCallback callback) {
        LocalDate initial = existing > 0
                ? java.time.Instant.ofEpochMilli(existing)
                .atZone(ZoneId.systemDefault()).toLocalDate()
                : LocalDate.now();
        DatePickerDialog picker = new DatePickerDialog(this,
                (view, year, month, dayOfMonth) -> {
                    LocalDate date = LocalDate.of(year, month + 1, dayOfMonth);
                    long millis = from
                            ? date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                            : date.plusDays(1).atStartOfDay(ZoneId.systemDefault())
                            .toInstant().toEpochMilli() - 1;
                    callback.onPicked(millis);
                }, initial.getYear(), initial.getMonthValue() - 1, initial.getDayOfMonth());
        picker.show();
    }

    private void updateDateButton(MaterialButton button, long millis, boolean from) {
        if (millis <= 0) {
            button.setText(R.string.search_any_date);
        } else {
            String date = java.time.Instant.ofEpochMilli(millis)
                    .atZone(ZoneId.systemDefault()).toLocalDate()
                    .format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()));
            button.setText((from ? "\u2190 " : "\u2192 ") + date);
        }
    }

    private void setSpinner(Spinner spinner, String[] labels, int selection) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setSelection(Math.max(0, Math.min(labels.length - 1, selection)));
    }

    private int indexOf(String[] values, String value) {
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(value)) {
                return i;
            }
        }
        return 0;
    }

    @Override
    public void onArticleClick(ArticleEntity article) {
        viewModel.markRead(article.id, true);
        viewModel.reloadOnResume();
        Intent intent = new Intent(this, ReaderActivity.class);
        intent.putExtra(ReaderContract.EXTRA_ARTICLE_ID, article.id);
        intent.putExtra(ReaderContract.EXTRA_FILTER, currentFilter);
        startActivity(intent);
    }

    @Override
    public void onBookmarkClick(ArticleEntity article) {
        viewModel.toggleBookmark(article.id);
    }

    @Override
    protected void onResume() {
        super.onResume();
        configureAdapter();
        if (viewModel != null && viewModel.consumeReloadRequest() && hasSearched) {
            executeSearch();
        }
    }

    @Override
    protected void onDestroy() {
        if (pendingSearch != null) {
            debounce.removeCallbacks(pendingSearch);
        }
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.search_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_save_search) {
            saveCurrentSearch();
            return true;
        }
        if (id == R.id.action_clear_saved) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.search_clear_history)
                    .setPositiveButton(R.string.action_clear, (dialog, which) ->
                            container.ioExecutor().execute(() ->
                                    container.articleRepository().clearSavedSearches()))
                    .setNegativeButton(R.string.action_cancel, null)
                    .show();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void saveCurrentSearch() {
        String query = searchInput.getText() == null ? ""
                : searchInput.getText().toString().trim();
        if (query.isEmpty() && !hasActiveFilters()) {
            Snackbar.make(resultsView, R.string.search_nothing_to_save,
                    Snackbar.LENGTH_SHORT).show();
            return;
        }
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_text_input, null);
        TextInputLayout layout = (TextInputLayout) view;
        layout.setHint(getString(R.string.search_saved_name));
        TextInputEditText input = view.findViewById(R.id.dialog_input);
        input.setSingleLine(true);
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.search_save_current)
                .setView(view)
                .setPositiveButton(R.string.action_save, (dialog, which) -> {
                    String name = input.getText() == null ? ""
                            : input.getText().toString().trim();
                    if (name.isEmpty()) {
                        return;
                    }
                    ArticleFilter filter = buildFilter(query);
                    String json = gson.toJson(filter);
                    container.ioExecutor().execute(() -> {
                        container.articleRepository().saveSearch(name, filter, json);
                        runOnUiThread(() -> {
                            Toast.makeText(this, R.string.search_saved_added,
                                    Toast.LENGTH_SHORT).show();
                            updateSavedVisibility();
                        });
                    });
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }
}
