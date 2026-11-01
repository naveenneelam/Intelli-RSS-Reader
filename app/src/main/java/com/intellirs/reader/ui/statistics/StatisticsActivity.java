package com.intellirs.reader.ui.statistics;

import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.intellirs.reader.R;
import com.intellirs.reader.data.model.CategoryReadCount;
import com.intellirs.reader.data.model.DailyCount;
import com.intellirs.reader.data.model.FeedReadCount;
import com.intellirs.reader.data.model.TagCount;
import com.intellirs.reader.ui.BaseActivity;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class StatisticsActivity extends BaseActivity {
    private TextView read;
    private TextView unread;
    private TextView starred;
    private TextView archived;
    private TextView time;
    private ReadingBarChartView chart;
    private LinearLayout topFeeds;
    private LinearLayout topCategories;
    private ChipGroup topTags;
    private TextView noData;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_statistics);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }
        toolbar.setNavigationOnClickListener(view -> finish());
        read = findViewById(R.id.stat_read);
        unread = findViewById(R.id.stat_unread);
        starred = findViewById(R.id.stat_starred);
        archived = findViewById(R.id.stat_archived);
        time = findViewById(R.id.stat_time);
        chart = findViewById(R.id.read_chart);
        topFeeds = findViewById(R.id.top_feeds_container);
        topCategories = findViewById(R.id.top_categories_container);
        topTags = findViewById(R.id.top_tags_group);
        noData = findViewById(R.id.empty_stats);
        load();
    }

    @Override
    protected void onResume() {
        super.onResume();
        load();
    }

    private void load() {
        container.ioExecutor().execute(() -> {
            int readCount = container.articleRepository().countRead();
            int unreadCount = container.articleRepository().countUnread();
            int starredCount = container.articleRepository().countBookmarked();
            int archivedCount = container.articleRepository().countArchived();
            long seconds = container.articleRepository().totalSecondsRead();
            List<DailyCount> daily = container.articleRepository().dailyReadCounts(14);
            List<FeedReadCount> feeds = container.articleRepository().mostReadFeeds(5);
            List<CategoryReadCount> categories =
                    container.articleRepository().mostReadCategories(5);
            List<TagCount> tags = container.articleRepository().topTags(12);
            runOnUiThread(() -> bind(readCount, unreadCount, starredCount, archivedCount,
                    seconds, daily, feeds, categories, tags));
        });
    }

    private void bind(int readCount, int unreadCount, int starredCount, int archivedCount,
                      long seconds, List<DailyCount> daily, List<FeedReadCount> feeds,
                      List<CategoryReadCount> categories, List<TagCount> tags) {
        read.setText(String.valueOf(readCount));
        unread.setText(String.valueOf(unreadCount));
        starred.setText(String.valueOf(starredCount));
        archived.setText(String.valueOf(archivedCount));
        long minutes = seconds / 60;
        if (minutes >= 60) {
            time.setText(getString(R.string.stats_reading_time) + ": "
                    + getString(R.string.stats_hours, minutes / 60, minutes % 60));
        } else {
            time.setText(getString(R.string.stats_reading_time) + ": "
                    + getString(R.string.stats_minutes, minutes));
        }

        int[] values = new int[14];
        Map<String, Integer> byDay = new HashMap<>();
        if (daily != null) {
            for (DailyCount count : daily) {
                byDay.put(count.day, count.count);
            }
        }
        LocalDate today = LocalDate.now();
        for (int i = 0; i < 14; i++) {
            String key = today.minusDays(13 - i).toString();
            values[i] = byDay.getOrDefault(key, 0);
        }
        chart.setValues(values);

        topFeeds.removeAllViews();
        if (feeds != null && !feeds.isEmpty()) {
            for (FeedReadCount item : feeds) {
                topFeeds.addView(row(item.feedTitle, item.readCount + " " +
                        getString(R.string.read).toLowerCase(Locale.getDefault())));
            }
        } else {
            topFeeds.addView(row(getString(R.string.stats_no_data), ""));
        }

        topCategories.removeAllViews();
        if (categories != null && !categories.isEmpty()) {
            for (CategoryReadCount item : categories) {
                topCategories.addView(row(item.categoryName, item.readCount + " " +
                        getString(R.string.read).toLowerCase(Locale.getDefault())));
            }
        } else {
            topCategories.addView(row(getString(R.string.stats_no_data), ""));
        }

        topTags.removeAllViews();
        if (tags != null && !tags.isEmpty()) {
            for (TagCount tag : tags) {
                Chip chip = new Chip(this);
                chip.setText(tag.tag + "  " + tag.count);
                chip.setCheckable(false);
                topTags.addView(chip);
            }
        } else {
            TextView empty = new TextView(this);
            empty.setText(R.string.stats_no_data);
            empty.setTextColor(ContextCompat.getColor(this, R.color.on_surface_variant));
            topTags.addView(empty);
        }
        noData.setVisibility(readCount == 0 ? View.VISIBLE : View.GONE);
    }

    private LinearLayout row(String label, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, 5, 0, 5);
        TextView title = new TextView(this);
        title.setText(label);
        title.setTextColor(ContextCompat.getColor(this, R.color.on_surface));
        title.setTextSize(14);
        title.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView meta = new TextView(this);
        meta.setText(value);
        meta.setTextColor(ContextCompat.getColor(this, R.color.on_surface_variant));
        meta.setTextSize(13);
        row.addView(title);
        row.addView(meta);
        return row;
    }
}
