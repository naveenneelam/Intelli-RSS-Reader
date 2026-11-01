package com.intellirs.reader.ui.feed;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.intellirs.reader.R;
import com.intellirs.reader.data.entity.CategoryEntity;
import com.intellirs.reader.data.network.FeedCandidate;
import com.intellirs.reader.ui.BaseActivity;

import java.util.ArrayList;
import java.util.List;

public class AddFeedActivity extends BaseActivity {
    private AddFeedViewModel viewModel;
    private TextInputLayout urlLayout;
    private TextInputEditText urlInput;
    private MaterialButton discoverButton;
    private MaterialButton subscribeButton;
    private ProgressBar progress;
    private TextView statusText;
    private TextView resultsLabel;
    private RecyclerView resultsList;
    private Spinner categorySpinner;
    private DiscoveryAdapter adapter;
    private final List<CategoryEntity> categoryOptions = new ArrayList<>();
    private List<FeedCandidate> currentCandidates = new ArrayList<>();
    private boolean categoriesLoaded;
    private boolean selectionApplied;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_feed);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }
        toolbar.setNavigationOnClickListener(view -> finish());

        urlLayout = findViewById(R.id.url_layout);
        urlInput = findViewById(R.id.url_input);
        discoverButton = findViewById(R.id.discover_button);
        subscribeButton = findViewById(R.id.subscribe_button);
        progress = findViewById(R.id.progress);
        statusText = findViewById(R.id.status_text);
        resultsLabel = findViewById(R.id.results_label);
        resultsList = findViewById(R.id.results_list);
        categorySpinner = findViewById(R.id.category_spinner);

        adapter = new DiscoveryAdapter(selected -> {
            subscribeButton.setEnabled(selected > 0);
            subscribeButton.setText(selected > 1
                    ? getString(R.string.add_feed_subscribe_selected, selected)
                    : getString(R.string.add_feed_subscribe));
        });
        resultsList.setLayoutManager(new LinearLayoutManager(this));
        resultsList.setAdapter(adapter);

        viewModel = new ViewModelProvider(this).get(AddFeedViewModel.class);
        viewModel.discovery().observe(this, this::renderDiscovery);
        viewModel.subscribeState().observe(this, this::renderSubscribe);
        viewModel.categories().observe(this, this::renderCategories);

        discoverButton.setOnClickListener(view -> startDiscovery());
        findViewById(R.id.paste_button).setOnClickListener(view -> pasteFromClipboard());
        subscribeButton.setOnClickListener(view -> subscribe());
        urlInput.setOnEditorActionListener((textView, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO) {
                startDiscovery();
                return true;
            }
            return false;
        });
    }

    private void startDiscovery() {
        String url = urlInput.getText() == null ? "" : urlInput.getText().toString().trim();
        if (url.isEmpty()) {
            urlLayout.setError(getString(R.string.add_feed_empty_url));
            return;
        }
        urlLayout.setError(null);
        hideKeyboard();
        viewModel.discover(url);
    }

    private void renderDiscovery(AddFeedViewModel.DiscoveryState state) {
        if (state == null) {
            return;
        }
        progress.setVisibility(state.loading ? android.view.View.VISIBLE : android.view.View.GONE);
        discoverButton.setEnabled(!state.loading);
        if (state.loading) {
            statusText.setText(R.string.add_feed_discovering);
            statusText.setVisibility(android.view.View.VISIBLE);
            resultsLabel.setVisibility(android.view.View.GONE);
            currentCandidates = new ArrayList<>();
            adapter.setItems(currentCandidates);
            return;
        }
        if (state.error != null) {
            statusText.setText(state.error);
            statusText.setVisibility(android.view.View.VISIBLE);
            resultsLabel.setVisibility(android.view.View.GONE);
            currentCandidates = new ArrayList<>();
            adapter.setItems(currentCandidates);
            return;
        }
        currentCandidates = state.candidates;
        if (currentCandidates.isEmpty()) {
            statusText.setText(R.string.add_feed_no_feeds);
            statusText.setVisibility(android.view.View.VISIBLE);
            resultsLabel.setVisibility(android.view.View.GONE);
        } else {
            statusText.setText(currentCandidates.size() == 1
                    ? getString(R.string.add_feed_one_found)
                    : getString(R.string.add_feed_discovered, currentCandidates.size()));
            statusText.setVisibility(android.view.View.VISIBLE);
            resultsLabel.setVisibility(android.view.View.VISIBLE);
            resultsLabel.setText(R.string.add_feed_select_all_hint);
        }
        adapter.setItems(currentCandidates);
        applySuggestedCategory();
    }

    private void renderSubscribe(AddFeedViewModel.SubscribeState state) {
        if (state == null) {
            return;
        }
        subscribeButton.setEnabled(!state.loading && !adapter.selectedCandidates().isEmpty());
        subscribeButton.setText(state.loading ? getString(R.string.saving)
                : getString(R.string.add_feed_subscribe));
        if (state.loading) {
            return;
        }
        if (state.added > 0) {
            Toast.makeText(this, getString(R.string.add_feed_subscribed, state.added),
                    Toast.LENGTH_SHORT).show();
            setResult(RESULT_OK);
            finish();
            return;
        }
        if (state.error != null) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.add_feed_subscribe)
                    .setMessage(state.error)
                    .setPositiveButton(R.string.action_close, null)
                    .show();
        } else if (state.skipped > 0) {
            Snackbar.make(urlInput, R.string.add_feed_already_subscribed,
                    Snackbar.LENGTH_LONG).show();
        }
    }

    private void subscribe() {
        List<FeedCandidate> selected = adapter.selectedCandidates();
        if (selected.isEmpty()) {
            return;
        }
        Long categoryId = null;
        int position = categorySpinner.getSelectedItemPosition();
        if (position > 0 && position - 1 < categoryOptions.size()) {
            categoryId = categoryOptions.get(position - 1).id;
        }
        viewModel.subscribe(selected, categoryId);
    }

    private void renderCategories(List<CategoryEntity> categories) {
        if (categories == null) {
            return;
        }
        int previousPosition = categorySpinner.getSelectedItemPosition();
        categoryOptions.clear();
        List<String> labels = new ArrayList<>();
        labels.add(getString(R.string.feed_none_category));
        for (CategoryEntity category : categories) {
            categoryOptions.add(category);
            labels.add(category.name);
        }
        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, labels);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        categorySpinner.setAdapter(spinnerAdapter);
        if (previousPosition > 0 && previousPosition < labels.size()) {
            categorySpinner.setSelection(previousPosition);
        }
        categoriesLoaded = true;
        applySuggestedCategory();
    }

    private void applySuggestedCategory() {
        if (!categoriesLoaded || selectionApplied || currentCandidates.isEmpty()) {
            return;
        }
        selectionApplied = true;
        String suggested = currentCandidates.get(0).suggestedCategory;
        if (suggested == null) {
            return;
        }
        for (int i = 0; i < categoryOptions.size(); i++) {
            if (suggested.equalsIgnoreCase(categoryOptions.get(i).name)) {
                categorySpinner.setSelection(i + 1);
                return;
            }
        }
    }

    private void pasteFromClipboard() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(
                Context.CLIPBOARD_SERVICE);
        if (clipboard == null || !clipboard.hasPrimaryClip()) {
            return;
        }
        ClipData clip = clipboard.getPrimaryClip();
        if (clip == null || clip.getItemCount() == 0) {
            return;
        }
        CharSequence text = clip.getItemAt(0).coerceToText(this);
        if (text != null) {
            urlInput.setText(text.toString().trim());
            urlInput.setSelection(urlInput.length());
        }
    }

    private void hideKeyboard() {
        InputMethodManager manager = (InputMethodManager) getSystemService(
                Context.INPUT_METHOD_SERVICE);
        if (manager != null) {
            manager.hideSoftInputFromWindow(urlInput.getWindowToken(), 0);
        }
    }
}
