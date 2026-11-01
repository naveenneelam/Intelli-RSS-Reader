package com.intellirs.reader.ui.notifications;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.intellirs.reader.R;
import com.intellirs.reader.data.entity.NotificationQueueEntity;
import com.intellirs.reader.data.entity.NotificationStatus;
import com.intellirs.reader.ui.BaseActivity;
import com.intellirs.reader.util.DateUtils;

public class NotificationHistoryActivity extends BaseActivity {
    private NotificationAdapter adapter;
    private View emptyState;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notification_history);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }
        toolbar.setNavigationOnClickListener(view -> finish());
        RecyclerView list = findViewById(R.id.history_list);
        emptyState = findViewById(R.id.empty_state);
        adapter = new NotificationAdapter();
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);
        container.database().notificationDao().observeRecent(500).observe(this, entries -> {
            adapter.submitList(entries);
            boolean empty = entries == null || entries.isEmpty();
            emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
            list.setVisibility(empty ? View.GONE : View.VISIBLE);
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.notification_history_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_clear_notification_history) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.notification_clear_history)
                    .setMessage(R.string.notification_clear_confirm)
                    .setPositiveButton(R.string.action_clear, (dialog, which) ->
                            container.ioExecutor().execute(() ->
                                    container.database().notificationDao().clearAll()))
                    .setNegativeButton(R.string.action_cancel, null)
                    .show();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private static class NotificationAdapter
            extends ListAdapter<NotificationQueueEntity, NotificationAdapter.Holder> {
        private static final DiffUtil.ItemCallback<NotificationQueueEntity> DIFF =
                new DiffUtil.ItemCallback<NotificationQueueEntity>() {
                    @Override
                    public boolean areItemsTheSame(@NonNull NotificationQueueEntity oldItem,
                                                   @NonNull NotificationQueueEntity newItem) {
                        return oldItem.id == newItem.id;
                    }

                    @Override
                    public boolean areContentsTheSame(@NonNull NotificationQueueEntity oldItem,
                                                      @NonNull NotificationQueueEntity newItem) {
                        return oldItem.id == newItem.id
                                && java.util.Objects.equals(oldItem.notificationStatus,
                                newItem.notificationStatus)
                                && oldItem.eligibility == newItem.eligibility;
                    }
                };

        NotificationAdapter() {
            super(DIFF);
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_notification_history, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            holder.bind(getItem(position));
        }

        static class Holder extends RecyclerView.ViewHolder {
            final TextView title;
            final TextView status;
            final TextView meta;
            final TextView reason;

            Holder(@NonNull View itemView) {
                super(itemView);
                title = itemView.findViewById(R.id.history_title);
                status = itemView.findViewById(R.id.history_status);
                meta = itemView.findViewById(R.id.history_meta);
                reason = itemView.findViewById(R.id.history_reason);
            }

            void bind(NotificationQueueEntity entry) {
                title.setText(entry.articleTitle == null || entry.articleTitle.isEmpty()
                        ? itemView.getContext().getString(R.string.untitled) : entry.articleTitle);
                status.setText(statusLabel(entry.notificationStatus));
                status.setTextColor(ContextCompat.getColor(itemView.getContext(),
                        statusColor(entry.notificationStatus)));
                String publisher = entry.publisher == null ? "" : entry.publisher;
                meta.setText(publisher + "  \u2022  "
                        + DateUtils.formatFullDate(entry.createdAt)
                        + (entry.postedAt > 0
                        ? "  \u2022  " + itemView.getContext()
                        .getString(R.string.notification_status_posted)
                        : ""));
                reason.setText(itemView.getContext()
                        .getString(R.string.notification_history_reason, entry.decisionReason));
            }

            private String statusLabel(NotificationStatus status) {
                switch (status) {
                    case POSTED:
                        return itemView.getContext().getString(R.string.notification_status_posted);
                    case SUPPRESSED:
                        return itemView.getContext().getString(R.string.notification_status_suppressed);
                    case FAILED:
                        return itemView.getContext().getString(R.string.notification_status_failed);
                    case SKIPPED:
                    default:
                        return itemView.getContext().getString(R.string.notification_status_skipped);
                }
            }

            private int statusColor(NotificationStatus status) {
                switch (status) {
                    case POSTED:
                        return R.color.health_healthy;
                    case FAILED:
                        return R.color.health_failing;
                    case SUPPRESSED:
                        return R.color.health_degraded;
                    default:
                        return R.color.on_surface_variant;
                }
            }
        }
    }
}
