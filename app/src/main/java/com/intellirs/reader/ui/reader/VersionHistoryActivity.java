package com.intellirs.reader.ui.reader;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.intellirs.reader.R;
import com.intellirs.reader.data.entity.ArticleEntity;
import com.intellirs.reader.data.entity.ArticleVersionEntity;
import com.intellirs.reader.ui.BaseActivity;
import com.intellirs.reader.util.DateUtils;
import com.intellirs.reader.util.TextUtils;

import org.jsoup.Jsoup;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class VersionHistoryActivity extends BaseActivity {
    public static final String EXTRA_ARTICLE_ID = "extra_article_id";
    private VersionAdapter adapter;
    private View emptyState;
    private ArticleEntity article;
    private List<ArticleVersionEntity> versions = new ArrayList<>();
    private final List<String> versionTexts = new ArrayList<>();
    private String currentText = "";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_version_history);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }
        toolbar.setNavigationOnClickListener(view -> finish());
        RecyclerView list = findViewById(R.id.version_list);
        emptyState = findViewById(R.id.empty_state);
        adapter = new VersionAdapter(new VersionAdapter.Listener() {
            @Override
            public void onView(int position) {
                showVersion(position);
            }

            @Override
            public void onCompare(int position) {
                showCompare(position);
            }
        });
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);
        long articleId = getIntent().getLongExtra(EXTRA_ARTICLE_ID, -1L);
        if (articleId <= 0) {
            finish();
            return;
        }
        container.ioExecutor().execute(() -> {
            ArticleEntity loaded = container.articleRepository().getArticle(articleId);
            List<ArticleVersionEntity> loadedVersions =
                    container.articleRepository().getVersions(articleId);
            List<String> texts = new ArrayList<>();
            for (ArticleVersionEntity version : loadedVersions) {
                String html = container.archiveManager().readVersionHtml(version);
                texts.add(html == null ? "" : Jsoup.parse(html).text());
            }
            String current = loaded == null ? ""
                    : container.archiveManager().readArchivedHtml(loaded);
            String currentPlain = current == null ? "" : Jsoup.parse(current).text();
            runOnUiThread(() -> {
                article = loaded;
                versions = loadedVersions;
                versionTexts.clear();
                versionTexts.addAll(texts);
                currentText = currentPlain;
                adapter.submitList(versions);
                boolean empty = versions.isEmpty();
                emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
                list.setVisibility(empty ? View.GONE : View.VISIBLE);
            });
        });
    }

    private void showVersion(int position) {
        if (position < 0 || position >= versionTexts.size()) {
            return;
        }
        String text = versionTexts.get(position);
        TextView textView = new TextView(this);
        textView.setText(text == null || text.isEmpty()
                ? getString(R.string.reader_bad_version) : text);
        textView.setTextIsSelectable(true);
        textView.setTextSize(15);
        textView.setLineSpacing(0, 1.3f);
        int padding = Math.round(20 * getResources().getDisplayMetrics().density);
        textView.setPadding(padding, padding, padding, padding);
        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        scroll.addView(textView);
        ArticleVersionEntity version = versions.get(position);
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.reader_version_number, version.versionNumber))
                .setView(scroll)
                .setPositiveButton(R.string.action_close, null)
                .show();
    }

    private void showCompare(int position) {
        if (position < 0 || position >= versionTexts.size()) {
            return;
        }
        String oldText = versionTexts.get(position);
        String newText = currentText;
        Set<String> oldParts = paragraphs(oldText);
        Set<String> newParts = paragraphs(newText);
        List<String> added = new ArrayList<>();
        List<String> removed = new ArrayList<>();
        for (String part : newParts) {
            if (!oldParts.contains(part)) {
                added.add(part);
            }
        }
        for (String part : oldParts) {
            if (!newParts.contains(part)) {
                removed.add(part);
            }
        }
        StringBuilder message = new StringBuilder();
        if (added.isEmpty() && removed.isEmpty()) {
            message.append(getString(R.string.reader_version_no_diff));
        } else {
            if (!added.isEmpty()) {
                message.append(getString(R.string.reader_version_added)).append(":\n");
                for (int i = 0; i < Math.min(4, added.size()); i++) {
                    message.append("\u2022 ").append(TextUtils.truncate(added.get(i), 220))
                            .append('\n');
                }
                message.append('\n');
            }
            if (!removed.isEmpty()) {
                message.append(getString(R.string.reader_version_removed)).append(":\n");
                for (int i = 0; i < Math.min(4, removed.size()); i++) {
                    message.append("\u2022 ").append(TextUtils.truncate(removed.get(i), 220))
                            .append('\n');
                }
            }
        }
        ArticleVersionEntity version = versions.get(position);
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.reader_compare_versions) + "  \u2022  v"
                        + version.versionNumber)
                .setMessage(message.toString().trim())
                .setPositiveButton(R.string.action_close, null)
                .show();
    }

    private Set<String> paragraphs(String text) {
        Set<String> parts = new HashSet<>();
        if (text == null) {
            return parts;
        }
        for (String line : text.split("\\n+")) {
            String normalized = line.trim().replaceAll("\\s+", " ");
            if (normalized.length() > 20) {
                parts.add(normalized);
            }
        }
        return parts;
    }

    private static class VersionAdapter
            extends ListAdapter<ArticleVersionEntity, VersionAdapter.Holder> {
        interface Listener {
            void onView(int position);

            void onCompare(int position);
        }

        private static final DiffUtil.ItemCallback<ArticleVersionEntity> DIFF =
                new DiffUtil.ItemCallback<ArticleVersionEntity>() {
                    @Override
                    public boolean areItemsTheSame(@NonNull ArticleVersionEntity oldItem,
                                                   @NonNull ArticleVersionEntity newItem) {
                        return oldItem.id == newItem.id;
                    }

                    @Override
                    public boolean areContentsTheSame(@NonNull ArticleVersionEntity oldItem,
                                                      @NonNull ArticleVersionEntity newItem) {
                        return oldItem.id == newItem.id
                                && oldItem.versionNumber == newItem.versionNumber;
                    }
                };

        private final Listener listener;

        VersionAdapter(Listener listener) {
            super(DIFF);
            this.listener = listener;
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_article_version, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            holder.bind(getItem(position), position, listener);
        }

        static class Holder extends RecyclerView.ViewHolder {
            final TextView title;
            final TextView meta;
            final com.google.android.material.button.MaterialButton compare;

            Holder(@NonNull View itemView) {
                super(itemView);
                title = itemView.findViewById(R.id.version_title);
                meta = itemView.findViewById(R.id.version_meta);
                compare = itemView.findViewById(R.id.version_compare);
            }

            void bind(ArticleVersionEntity version, int position, Listener listener) {
                title.setText(itemView.getContext().getString(
                        R.string.reader_version_number, version.versionNumber));
                meta.setText(DateUtils.formatFullDate(version.createdAt)
                        + "  \u2022  " + version.wordCount + " words");
                compare.setVisibility(position == 0 ? View.GONE : View.VISIBLE);
                compare.setOnClickListener(view -> listener.onCompare(position));
                itemView.setOnClickListener(view -> listener.onView(position));
            }
        }
    }
}
