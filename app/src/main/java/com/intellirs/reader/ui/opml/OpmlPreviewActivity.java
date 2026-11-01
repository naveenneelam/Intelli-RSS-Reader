package com.intellirs.reader.ui.opml;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.intellirs.reader.R;
import com.intellirs.reader.data.network.FeedCandidate;
import com.intellirs.reader.data.opml.OpmlFeed;
import com.intellirs.reader.ui.BaseActivity;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class OpmlPreviewActivity extends BaseActivity {
    public static final String EXTRA_PREVIEW_FILE = "preview_file";
    private OpmlAdapter adapter;
    private TextView summary;
    private MaterialButton importButton;
    private MaterialButton selectAllButton;
    private List<OpmlFeed> feeds = new ArrayList<>();
    private String previewPath;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_opml_preview);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }
        toolbar.setNavigationOnClickListener(view -> finish());
        summary = findViewById(R.id.opml_summary);
        importButton = findViewById(R.id.opml_import_button);
        selectAllButton = findViewById(R.id.opml_select_all);
        RecyclerView list = findViewById(R.id.opml_list);
        adapter = new OpmlAdapter(this::updateSelectionUi);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);
        previewPath = getIntent().getStringExtra(EXTRA_PREVIEW_FILE);
        loadFile();
        selectAllButton.setOnClickListener(view ->
                adapter.selectAll(!adapter.allSelectableSelected()));
        importButton.setOnClickListener(view -> importSelected());
    }

    private void loadFile() {
        if (previewPath == null) {
            finish();
            return;
        }
        container.ioExecutor().execute(() -> {
            try {
                String json;
                try (InputStream input = new FileInputStream(previewPath)) {
                    java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = input.read(buffer)) != -1) {
                        out.write(buffer, 0, read);
                    }
                    json = out.toString(StandardCharsets.UTF_8.name());
                }
                Type type = new TypeToken<List<OpmlFeed>>() {
                }.getType();
                List<OpmlFeed> loaded = new Gson().fromJson(json, type);
                runOnUiThread(() -> {
                    feeds = loaded == null ? new ArrayList<>() : loaded;
                    adapter.setFeeds(feeds);
                    updateSelectionUi();
                });
            } catch (Exception e) {
                Toast.makeText(this, R.string.opml_import_malformed, Toast.LENGTH_LONG).show();
                finish();
            }
        });
    }

    private void updateSelectionUi() {
        int total = feeds.size();
        int duplicates = 0;
        int errors = 0;
        for (OpmlFeed feed : feeds) {
            if (feed.duplicate) {
                duplicates++;
            }
            if (feed.error != null) {
                errors++;
            }
        }
        int selectable = total - duplicates - errors;
        int selected = adapter.selectedCount();
        summary.setText(getString(R.string.opml_import_summary, total,
                Math.max(0, selectable), duplicates));
        if (errors > 0) {
            summary.append("  \u2022  " + errors + " invalid");
        }
        importButton.setEnabled(selected > 0);
        importButton.setText(selected > 0
                ? getString(R.string.opml_import_button) + " (" + selected + ")"
                : getString(R.string.opml_import_button));
        selectAllButton.setText(adapter.allSelectableSelected()
                ? R.string.action_deselect_all : R.string.action_select_all);
    }

    private void importSelected() {
        List<OpmlFeed> selected = adapter.selectedFeeds();
        if (selected.isEmpty()) {
            return;
        }
        importButton.setEnabled(false);
        importButton.setText(R.string.saving);
        container.ioExecutor().execute(() -> {
            int added = 0;
            int skipped = 0;
            List<String> errors = new ArrayList<>();
            for (OpmlFeed feed : selected) {
                if (feed.error != null || feed.duplicate) {
                    skipped++;
                    continue;
                }
                try {
                    Long categoryId = null;
                    if (feed.categoryPath != null && !feed.categoryPath.isEmpty()) {
                        com.intellirs.reader.data.entity.CategoryEntity category =
                                container.feedRepository().ensureCategoryPath(feed.categoryPath);
                        if (category != null) {
                            categoryId = category.id;
                        }
                    }
                    com.intellirs.reader.data.entity.FeedEntity existing =
                            container.feedRepository().getFeedByUrl(feed.feedUrl);
                    if (existing != null && existing.isActive) {
                        skipped++;
                        continue;
                    }
                    FeedCandidate candidate = new FeedCandidate(feed.title, feed.feedUrl,
                            feed.websiteUrl, null, feed.feedType, feed.categoryPath, 0);
                    container.feedRepository().subscribe(candidate, categoryId, false, false);
                    added++;
                } catch (Exception e) {
                    errors.add((feed.title == null ? feed.feedUrl : feed.title) + ": "
                            + (e.getMessage() == null ? "failed" : e.getMessage()));
                }
            }
            if (added > 0) {
                container.workScheduler().enqueueManualSync(true);
            }
            final int addedFinal = added;
            final int skippedFinal = skipped;
            final String errorText = errors.isEmpty() ? null : String.join("\n", errors);
            runOnUiThread(() -> {
                Toast.makeText(this, getString(R.string.opml_import_success,
                        addedFinal, skippedFinal), Toast.LENGTH_LONG).show();
                if (errorText != null) {
                    new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                            .setTitle(R.string.opml_import_errors_title)
                            .setMessage(errorText)
                            .setPositiveButton(R.string.action_close, null)
                            .show();
                }
                cleanupPreview();
                setResult(RESULT_OK);
                finish();
            });
        });
    }

    private void cleanupPreview() {
        if (previewPath != null) {
            File file = new File(previewPath);
            if (file.exists()) {
                file.delete();
            }
        }
    }

    @Override
    protected void onDestroy() {
        if (isFinishing()) {
            cleanupPreview();
        }
        super.onDestroy();
    }

    private static class OpmlAdapter
            extends ListAdapter<OpmlFeed, OpmlAdapter.Holder> {
        interface Listener {
            void onSelectionChanged();
        }

        private static final DiffUtil.ItemCallback<OpmlFeed> DIFF =
                new DiffUtil.ItemCallback<OpmlFeed>() {
                    @Override
                    public boolean areItemsTheSame(@NonNull OpmlFeed oldItem,
                                                   @NonNull OpmlFeed newItem) {
                        return oldItem.feedUrl.equals(newItem.feedUrl);
                    }

                    @Override
                    public boolean areContentsTheSame(@NonNull OpmlFeed oldItem,
                                                      @NonNull OpmlFeed newItem) {
                        return oldItem.title.equals(newItem.title)
                                && oldItem.duplicate == newItem.duplicate
                                && oldItem.feedUrl.equals(newItem.feedUrl);
                    }
                };
        private final Set<String> selected = new HashSet<>();
        private final Listener listener;

        OpmlAdapter(Listener listener) {
            super(DIFF);
            this.listener = listener;
        }

        void setFeeds(List<OpmlFeed> feeds) {
            selected.clear();
            for (OpmlFeed feed : feeds) {
                if (feed.error == null && !feed.duplicate) {
                    selected.add(feed.feedUrl);
                }
            }
            submitList(new ArrayList<>(feeds), listener::onSelectionChanged);
        }

        List<OpmlFeed> selectedFeeds() {
            List<OpmlFeed> result = new ArrayList<>();
            for (OpmlFeed feed : getCurrentList()) {
                if (selected.contains(feed.feedUrl) && feed.error == null && !feed.duplicate) {
                    result.add(feed);
                }
            }
            return result;
        }

        int selectedCount() {
            int count = 0;
            for (OpmlFeed feed : getCurrentList()) {
                if (selected.contains(feed.feedUrl) && feed.error == null && !feed.duplicate) {
                    count++;
                }
            }
            return count;
        }

        void selectAll(boolean select) {
            selected.clear();
            if (select) {
                for (OpmlFeed feed : getCurrentList()) {
                    if (feed.error == null && !feed.duplicate) {
                        selected.add(feed.feedUrl);
                    }
                }
            }
            notifyDataSetChanged();
            listener.onSelectionChanged();
        }

        boolean allSelectableSelected() {
            int selectable = 0;
            for (OpmlFeed feed : getCurrentList()) {
                if (feed.error == null && !feed.duplicate) {
                    selectable++;
                }
            }
            return selectable > 0 && selectedCount() == selectable;
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_opml_feed, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            OpmlFeed feed = getItem(position);
            holder.bind(feed, selected.contains(feed.feedUrl), checked -> {
                if (checked) {
                    selected.add(feed.feedUrl);
                } else {
                    selected.remove(feed.feedUrl);
                }
                listener.onSelectionChanged();
            });
        }

        static class Holder extends RecyclerView.ViewHolder {
            interface Toggle {
                void onToggle(boolean checked);
            }

            final CheckBox check;
            final TextView title;
            final TextView url;
            final TextView category;
            final TextView status;

            Holder(@NonNull View itemView) {
                super(itemView);
                check = itemView.findViewById(R.id.opml_check);
                title = itemView.findViewById(R.id.opml_title);
                url = itemView.findViewById(R.id.opml_url);
                category = itemView.findViewById(R.id.opml_category);
                status = itemView.findViewById(R.id.opml_status);
            }

            void bind(OpmlFeed feed, boolean checked, Toggle toggle) {
                title.setText(feed.title == null || feed.title.isEmpty()
                        ? itemView.getContext().getString(R.string.unnamed_feed) : feed.title);
                url.setText(feed.feedUrl);
                if (feed.categoryPath == null || feed.categoryPath.isEmpty()) {
                    category.setVisibility(View.GONE);
                } else {
                    category.setVisibility(View.VISIBLE);
                    category.setText(feed.categoryPath);
                }
                boolean selectable = feed.error == null && !feed.duplicate;
                check.setOnCheckedChangeListener(null);
                check.setChecked(checked && selectable);
                check.setEnabled(selectable);
                check.setOnCheckedChangeListener((buttonView, isChecked) -> toggle.onToggle(isChecked));
                if (feed.error != null) {
                    status.setVisibility(View.VISIBLE);
                    status.setText(feed.error);
                    status.setTextColor(itemView.getContext().getColor(R.color.error));
                } else if (feed.duplicate) {
                    status.setVisibility(View.VISIBLE);
                    status.setText(R.string.opml_duplicate);
                    status.setTextColor(itemView.getContext().getColor(R.color.health_degraded));
                } else {
                    status.setVisibility(View.GONE);
                }
                itemView.setOnClickListener(view -> {
                    if (selectable) {
                        check.setChecked(!check.isChecked());
                    }
                });
            }
        }
    }
}
