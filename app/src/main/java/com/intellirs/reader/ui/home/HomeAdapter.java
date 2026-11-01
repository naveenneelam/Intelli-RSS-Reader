package com.intellirs.reader.ui.home;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.intellirs.reader.R;
import com.intellirs.reader.data.entity.FeedHealthStatus;
import com.intellirs.reader.data.entity.FeedImportance;
import com.intellirs.reader.data.entity.FeedEntity;
import com.intellirs.reader.ui.common.IconLoader;
import com.intellirs.reader.util.DateUtils;

public class HomeAdapter extends ListAdapter<HomeRow, RecyclerView.ViewHolder> {
    public interface Listener {
        void onSmartClick(HomeRow row);

        void onSmartToggleClick(HomeRow row);

        void onCategoryClick(HomeRow row);

        void onFeedClick(HomeRow row);

        void onFeedLongClick(HomeRow row);

        void onNotificationToggle(HomeRow row);

        void onFeedMenuClick(HomeRow row);
    }

    private static final DiffUtil.ItemCallback<HomeRow> DIFF =
            new DiffUtil.ItemCallback<HomeRow>() {
                @Override
                public boolean areItemsTheSame(@NonNull HomeRow oldItem, @NonNull HomeRow newItem) {
                    return oldItem.stableId == newItem.stableId && oldItem.type == newItem.type;
                }

                @Override
                public boolean areContentsTheSame(@NonNull HomeRow oldItem,
                                                  @NonNull HomeRow newItem) {
                    return oldItem.signature.equals(newItem.signature);
                }
            };
    private final Listener listener;

    public HomeAdapter(Listener listener) {
        super(DIFF);
        this.listener = listener;
        setHasStableIds(true);
    }

    @Override
    public long getItemId(int position) {
        return getItem(position).stableId;
    }

    @Override
    public int getItemViewType(int position) {
        return getItem(position).type;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == HomeRow.TYPE_SMART) {
            return new SmartHolder(inflater.inflate(R.layout.item_smart_view, parent, false));
        }
        if (viewType == HomeRow.TYPE_SECTION) {
            return new SectionHolder(inflater.inflate(R.layout.item_home_section, parent, false));
        }
        if (viewType == HomeRow.TYPE_CATEGORY) {
            return new CategoryHolder(
                    inflater.inflate(R.layout.item_category_header, parent, false));
        }
        return new FeedHolder(inflater.inflate(R.layout.item_feed, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        HomeRow row = getItem(position);
        if (holder instanceof SmartHolder) {
            ((SmartHolder) holder).bind(row, listener);
        } else if (holder instanceof SectionHolder) {
            ((SectionHolder) holder).bind(row, listener);
        } else if (holder instanceof CategoryHolder) {
            ((CategoryHolder) holder).bind(row, listener);
        } else if (holder instanceof FeedHolder) {
            ((FeedHolder) holder).bind(row, listener);
        }
    }

    private static class SmartHolder extends RecyclerView.ViewHolder {
        final TextView title;
        final TextView subtitle;
        final TextView count;
        final ImageView icon;

        SmartHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.smart_title);
            subtitle = itemView.findViewById(R.id.smart_subtitle);
            count = itemView.findViewById(R.id.smart_count);
            icon = itemView.findViewById(R.id.smart_icon);
        }

        void bind(HomeRow row, Listener listener) {
            title.setText(row.title);
            icon.setImageResource(row.iconRes);
            if (row.subtitle == null || row.subtitle.isEmpty()) {
                subtitle.setVisibility(View.GONE);
            } else {
                subtitle.setVisibility(View.VISIBLE);
                subtitle.setText(row.subtitle);
            }
            if (row.count > 0) {
                count.setVisibility(View.VISIBLE);
                count.setText(String.valueOf(row.count));
            } else {
                count.setVisibility(View.GONE);
            }
            itemView.setOnClickListener(view -> listener.onSmartClick(row));
        }
    }

    private static class SectionHolder extends RecyclerView.ViewHolder {
        final TextView title;
        final TextView count;
        final ImageView expand;

        SectionHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.section_title);
            count = itemView.findViewById(R.id.section_count);
            expand = itemView.findViewById(R.id.section_expand);
        }

        void bind(HomeRow row, Listener listener) {
            title.setText(row.title);
            if (row.meta != null && !row.meta.isEmpty()) {
                count.setVisibility(View.VISIBLE);
                count.setText(row.meta);
            } else {
                count.setVisibility(View.GONE);
            }
            if (row.smartToggle) {
                expand.setVisibility(View.VISIBLE);
                expand.setImageResource(row.expanded
                        ? R.drawable.ic_expand_less : R.drawable.ic_expand_more);
                itemView.setClickable(true);
                itemView.setFocusable(true);
                itemView.setOnClickListener(view -> listener.onSmartToggleClick(row));
                itemView.setContentDescription(row.title + ", " + row.meta);
            } else {
                expand.setVisibility(View.GONE);
                itemView.setClickable(false);
                itemView.setFocusable(false);
                itemView.setOnClickListener(null);
                itemView.setContentDescription(null);
            }
        }
    }

    private static class CategoryHolder extends RecyclerView.ViewHolder {
        final TextView name;
        final TextView count;
        final ImageView expand;
        final ImageView muted;

        CategoryHolder(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.category_name);
            count = itemView.findViewById(R.id.category_count);
            expand = itemView.findViewById(R.id.category_expand);
            muted = itemView.findViewById(R.id.category_muted_icon);
        }

        void bind(HomeRow row, Listener listener) {
            name.setText(row.title);
            int basePadding = dp(itemView.getContext(), 14);
            itemView.setPadding(basePadding + dp(itemView.getContext(), row.indent * 14),
                    itemView.getPaddingTop(), itemView.getPaddingRight(),
                    itemView.getPaddingBottom());
            expand.setImageResource(row.collapsed
                    ? R.drawable.ic_chevron_right : R.drawable.ic_expand_more);
            count.setText(String.valueOf(row.count));
            muted.setVisibility(row.category != null && row.category.muteNotifications
                    ? View.VISIBLE : View.GONE);
            itemView.setOnClickListener(view -> listener.onCategoryClick(row));
        }
    }

    private static class FeedHolder extends RecyclerView.ViewHolder {
        final TextView title;
        final TextView subtitle;
        final TextView time;
        final TextView unreadText;
        final TextView unread;
        final ImageView icon;
        final ImageView notification;
        final ImageView feedMenu;
        final View healthDot;
        final View importanceDot;

        FeedHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.feed_title);
            subtitle = itemView.findViewById(R.id.feed_subtitle);
            time = itemView.findViewById(R.id.feed_time);
            unreadText = itemView.findViewById(R.id.feed_unread_text);
            unread = itemView.findViewById(R.id.feed_unread);
            icon = itemView.findViewById(R.id.feed_icon);
            notification = itemView.findViewById(R.id.notification_icon);
            feedMenu = itemView.findViewById(R.id.feed_menu);
            healthDot = itemView.findViewById(R.id.health_dot);
            importanceDot = itemView.findViewById(R.id.importance_dot);
        }

        void bind(HomeRow row, Listener listener) {
            Context context = itemView.getContext();
            FeedEntity feed = row.feedItem.feed;
            title.setText(feed.title);
            IconLoader.loadFeedIcon(icon, feed, 12);
            String category = row.feedItem.categoryName;
            if (category == null || category.isEmpty()) {
                category = feed.description == null ? context.getString(R.string.home_uncategorized)
                        : feed.description;
            }
            subtitle.setText(category);
            String relative = feed.lastSuccessfulSync <= 0
                    ? context.getString(R.string.home_never_synced)
                    : DateUtils.relative(context, row.feedItem.latestPublishedAt > 0
                    ? row.feedItem.latestPublishedAt : feed.lastSuccessfulSync);
            time.setText(relative);
            unreadText.setText(row.feedItem.unreadCount > 0
                    ? context.getString(R.string.home_feed_unread, row.feedItem.unreadCount)
                    : context.getString(R.string.home_feed_all_read));
            if (row.feedItem.unreadCount > 0) {
                unread.setVisibility(View.VISIBLE);
                unread.setText(String.valueOf(row.feedItem.unreadCount));
            } else {
                unread.setVisibility(View.GONE);
            }

            importanceDot.getBackground().mutate().setTint(importanceColor(context,
                    feed.feedPriority));
            importanceDot.setContentDescription(context.getString(
                    R.string.importance_label, importanceLabel(context, feed.feedPriority)));

            notification.setImageResource(feed.notificationEnabled
                    ? R.drawable.ic_notifications : R.drawable.ic_notifications_off);
            notification.setContentDescription(context.getString(feed.notificationEnabled
                    ? R.string.feed_notifications_on : R.string.feed_notifications_off));
            notification.setAlpha(feed.notificationEnabled ? 1f : 0.55f);
            notification.setOnClickListener(view -> listener.onNotificationToggle(row));
            feedMenu.setOnClickListener(view -> listener.onFeedMenuClick(row));

            FeedHealthStatus health = row.feedItem.healthStatus == null
                    ? FeedHealthStatus.HEALTHY : row.feedItem.healthStatus;
            healthDot.getBackground().mutate().setTint(healthColor(context, health));
            healthDot.setContentDescription(context.getString(healthLabel(health)));

            itemView.setOnClickListener(view -> listener.onFeedClick(row));
            itemView.setOnLongClickListener(view -> {
                listener.onFeedLongClick(row);
                return true;
            });
        }
    }

    private static int importanceColor(Context context, FeedImportance importance) {
        switch (importance == null ? FeedImportance.NORMAL : importance) {
            case CRITICAL:
                return ContextCompat.getColor(context, R.color.importance_critical);
            case HIGH:
                return ContextCompat.getColor(context, R.color.importance_high);
            case LOW:
                return ContextCompat.getColor(context, R.color.importance_low);
            case MUTED:
                return ContextCompat.getColor(context, R.color.importance_muted);
            case NORMAL:
            default:
                return ContextCompat.getColor(context, R.color.importance_normal);
        }
    }

    private static String importanceLabel(Context context, FeedImportance importance) {
        if (importance == null) {
            return context.getString(R.string.importance_normal);
        }
        switch (importance) {
            case CRITICAL:
                return context.getString(R.string.importance_critical);
            case HIGH:
                return context.getString(R.string.importance_high);
            case LOW:
                return context.getString(R.string.importance_low);
            case MUTED:
                return context.getString(R.string.importance_muted);
            case NORMAL:
            default:
                return context.getString(R.string.importance_normal);
        }
    }

    private static int healthColor(Context context, FeedHealthStatus status) {
        switch (status) {
            case FAILING:
                return ContextCompat.getColor(context, R.color.health_failing);
            case DEGRADED:
                return ContextCompat.getColor(context, R.color.health_degraded);
            case INACTIVE:
                return ContextCompat.getColor(context, R.color.health_inactive);
            case HEALTHY:
            default:
                return ContextCompat.getColor(context, R.color.health_healthy);
        }
    }

    private static int healthLabel(FeedHealthStatus status) {
        switch (status) {
            case FAILING:
                return R.string.feed_health_failing;
            case DEGRADED:
                return R.string.feed_health_degraded;
            case INACTIVE:
                return R.string.feed_health_inactive;
            case HEALTHY:
            default:
                return R.string.feed_health_healthy;
        }
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
