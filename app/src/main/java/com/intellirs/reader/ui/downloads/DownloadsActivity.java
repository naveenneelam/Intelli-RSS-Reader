package com.intellirs.reader.ui.downloads;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.intellirs.reader.R;
import com.intellirs.reader.data.entity.DownloadStatus;
import com.intellirs.reader.data.entity.DownloadType;
import com.intellirs.reader.data.model.DownloadItem;
import com.intellirs.reader.domain.model.ArticleFilter;
import com.intellirs.reader.ui.BaseActivity;
import com.intellirs.reader.ui.reader.ReaderActivity;
import com.intellirs.reader.ui.reader.ReaderContract;
import com.intellirs.reader.util.DateUtils;
import com.intellirs.reader.util.TextUtils;

public class DownloadsActivity extends BaseActivity {
    private View emptyState;
    private RecyclerView recyclerView;
    private DownloadAdapter adapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_downloads);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }
        toolbar.setNavigationOnClickListener(view -> finish());
        recyclerView = findViewById(R.id.downloads_list);
        emptyState = findViewById(R.id.empty_state);
        adapter = new DownloadAdapter(new DownloadAdapter.Listener() {
            @Override
            public void onOpen(DownloadItem item) {
                if (item.download.articleId <= 0) {
                    return;
                }
                Intent intent = new Intent(DownloadsActivity.this, ReaderActivity.class);
                intent.putExtra(ReaderContract.EXTRA_ARTICLE_ID, item.download.articleId);
                intent.putExtra(ReaderContract.EXTRA_FILTER, ArticleFilter.all());
                startActivity(intent);
            }

            @Override
            public void onRetry(DownloadItem item) {
                container.ioExecutor().execute(() ->
                        container.downloadRepository().retry(item.download));
            }
        });
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
        container.downloadRepository().observeDownloads().observe(this, items -> {
            adapter.submitList(items);
            boolean empty = items == null || items.isEmpty();
            emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
            recyclerView.setVisibility(empty ? View.GONE : View.VISIBLE);
        });
    }

    private static class DownloadAdapter
            extends ListAdapter<DownloadItem, DownloadAdapter.Holder> {
        interface Listener {
            void onOpen(DownloadItem item);

            void onRetry(DownloadItem item);
        }

        private static final DiffUtil.ItemCallback<DownloadItem> DIFF =
                new DiffUtil.ItemCallback<DownloadItem>() {
                    @Override
                    public boolean areItemsTheSame(@NonNull DownloadItem oldItem,
                                                   @NonNull DownloadItem newItem) {
                        return oldItem.download.id == newItem.download.id;
                    }

                    @Override
                    public boolean areContentsTheSame(@NonNull DownloadItem oldItem,
                                                      @NonNull DownloadItem newItem) {
                        return oldItem.download.id == newItem.download.id
                                && java.util.Objects.equals(oldItem.download.status,
                                newItem.download.status)
                                && oldItem.download.retryCount == newItem.download.retryCount
                                && equals(oldItem.download.lastError, newItem.download.lastError)
                                && equals(oldItem.articleTitle, newItem.articleTitle);
                    }

                    private boolean equals(String left, String right) {
                        return left == null ? right == null : left.equals(right);
                    }
                };

        private final Listener listener;

        DownloadAdapter(Listener listener) {
            super(DIFF);
            this.listener = listener;
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_download, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            holder.bind(getItem(position), listener);
        }

        static class Holder extends RecyclerView.ViewHolder {
            final ImageView icon;
            final TextView title;
            final TextView meta;
            final TextView error;
            final TextView status;
            final com.google.android.material.button.MaterialButton retry;

            Holder(@NonNull View itemView) {
                super(itemView);
                icon = itemView.findViewById(R.id.download_icon);
                title = itemView.findViewById(R.id.download_title);
                meta = itemView.findViewById(R.id.download_meta);
                error = itemView.findViewById(R.id.download_error);
                status = itemView.findViewById(R.id.download_status);
                retry = itemView.findViewById(R.id.download_retry);
            }

            void bind(DownloadItem item, Listener listener) {
                title.setText(item.articleTitle == null
                        ? itemView.getContext().getString(R.string.untitled)
                        : item.articleTitle);
                String typeLabel = item.download.downloadType == DownloadType.AUDIO
                        ? itemView.getContext().getString(R.string.article_has_audio)
                        : itemView.getContext().getString(R.string.action_download);
                String publisher = item.publisher == null ? "" : item.publisher;
                meta.setText(publisher + "  \u2022  " + typeLabel + "  \u2022  "
                        + DateUtils.relative(itemView.getContext(), item.download.createdAt));
                icon.setImageResource(item.download.downloadType == DownloadType.AUDIO
                        ? R.drawable.ic_headphones : R.drawable.ic_download);
                DownloadStatus state = item.download.status;
                status.setText(statusLabel(state));
                status.setTextColor(ContextCompat.getColor(itemView.getContext(),
                        statusColor(state)));
                error.setVisibility(item.download.lastError == null
                        || item.download.lastError.isEmpty() ? View.GONE : View.VISIBLE);
                error.setText(TextUtils.truncate(item.download.lastError, 250));
                retry.setVisibility(state == DownloadStatus.FAILED
                        ? View.VISIBLE : View.GONE);
                retry.setOnClickListener(view -> listener.onRetry(item));
                itemView.setOnClickListener(view -> listener.onOpen(item));
            }

            private String statusLabel(DownloadStatus status) {
                switch (status) {
                    case QUEUED:
                        return itemView.getContext().getString(R.string.download_status_queued);
                    case RUNNING:
                        return itemView.getContext().getString(R.string.download_status_running);
                    case COMPLETE:
                        return itemView.getContext().getString(R.string.download_status_complete);
                    case FAILED:
                        return itemView.getContext().getString(R.string.download_status_failed);
                    case CANCELLED:
                        return itemView.getContext().getString(R.string.download_status_cancelled);
                    default:
                        return "";
                }
            }

            private int statusColor(DownloadStatus status) {
                switch (status) {
                    case COMPLETE:
                        return R.color.health_healthy;
                    case FAILED:
                        return R.color.health_failing;
                    case RUNNING:
                        return R.color.primary;
                    default:
                        return R.color.on_surface_variant;
                }
            }
        }
    }
}
