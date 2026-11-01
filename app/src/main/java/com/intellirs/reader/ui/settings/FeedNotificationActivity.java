package com.intellirs.reader.ui.settings;

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

import com.google.android.material.materialswitch.MaterialSwitch;
import com.intellirs.reader.R;
import com.intellirs.reader.data.entity.FeedImportance;
import com.intellirs.reader.data.model.FeedListItem;
import com.intellirs.reader.ui.BaseActivity;

public class FeedNotificationActivity extends BaseActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_feed_notifications);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }
        toolbar.setNavigationOnClickListener(view -> finish());
        RecyclerView list = findViewById(R.id.feed_notifications_list);
        FeedNotificationAdapter adapter = new FeedNotificationAdapter((item, enabled) ->
                container.ioExecutor().execute(() ->
                        container.feedRepository().setNotificationsEnabled(
                                item.feed.id, enabled)));
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);
        container.feedRepository().observeFeedList().observe(this, adapter::submitList);
    }

    private static class FeedNotificationAdapter
            extends ListAdapter<FeedListItem, FeedNotificationAdapter.Holder> {
        interface Listener {
            void onToggle(FeedListItem item, boolean enabled);
        }

        private static final DiffUtil.ItemCallback<FeedListItem> DIFF =
                new DiffUtil.ItemCallback<FeedListItem>() {
                    @Override
                    public boolean areItemsTheSame(@NonNull FeedListItem oldItem,
                                                   @NonNull FeedListItem newItem) {
                        return oldItem.feed.id == newItem.feed.id;
                    }

                    @Override
                    public boolean areContentsTheSame(@NonNull FeedListItem oldItem,
                                                      @NonNull FeedListItem newItem) {
                        return oldItem.feed.id == newItem.feed.id
                                && oldItem.feed.notificationEnabled
                                == newItem.feed.notificationEnabled
                                && java.util.Objects.equals(oldItem.feed.feedPriority,
                                newItem.feed.feedPriority)
                                && oldItem.feed.title.equals(newItem.feed.title);
                    }
                };

        private final Listener listener;

        FeedNotificationAdapter(Listener listener) {
            super(DIFF);
            this.listener = listener;
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_feed_notification, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            holder.bind(getItem(position), listener);
        }

        static class Holder extends RecyclerView.ViewHolder {
            final TextView title;
            final TextView meta;
            final MaterialSwitch toggle;

            Holder(@NonNull View itemView) {
                super(itemView);
                title = itemView.findViewById(R.id.feed_notification_title);
                meta = itemView.findViewById(R.id.feed_notification_meta);
                toggle = itemView.findViewById(R.id.feed_notification_switch);
            }

            void bind(FeedListItem item, Listener listener) {
                title.setText(item.feed.title);
                meta.setText(importanceLabel(item.feed.feedPriority)
                        + "  \u2022  " + item.unreadCount + " "
                        + itemView.getContext().getString(R.string.unread).toLowerCase());
                toggle.setOnCheckedChangeListener(null);
                toggle.setChecked(item.feed.notificationEnabled);
                toggle.setOnCheckedChangeListener((buttonView, isChecked) ->
                        listener.onToggle(item, isChecked));
            }

            private String importanceLabel(FeedImportance importance) {
                switch (importance) {
                    case CRITICAL:
                        return itemView.getContext().getString(R.string.importance_critical);
                    case HIGH:
                        return itemView.getContext().getString(R.string.importance_high);
                    case LOW:
                        return itemView.getContext().getString(R.string.importance_low);
                    case MUTED:
                        return itemView.getContext().getString(R.string.importance_muted);
                    case NORMAL:
                    default:
                        return itemView.getContext().getString(R.string.importance_normal);
                }
            }
        }
    }
}
