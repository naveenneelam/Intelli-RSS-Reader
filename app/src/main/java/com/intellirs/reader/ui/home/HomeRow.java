package com.intellirs.reader.ui.home;

import com.intellirs.reader.data.entity.CategoryEntity;
import com.intellirs.reader.data.model.FeedListItem;
import com.intellirs.reader.domain.model.ArticleFilter;

/** Generic row model for the vertically scrolling home list. */
public class HomeRow {
    public static final int TYPE_SMART = 1;
    public static final int TYPE_SECTION = 2;
    public static final int TYPE_CATEGORY = 3;
    public static final int TYPE_FEED = 4;

    public int type;
    public long stableId;
    public String title = "";
    public String subtitle = "";
    public String meta = "";
    public int count;
    public int iconRes;
    public boolean collapsed;
    public boolean smartToggle;
    public boolean expanded;
    public int indent;
    public FeedListItem feedItem;
    public CategoryEntity category;
    public ArticleFilter filter;
    public String signature = "";

    public static HomeRow smart(long id, String title, String subtitle, int count,
                                int iconRes, ArticleFilter filter) {
        HomeRow row = new HomeRow();
        row.type = TYPE_SMART;
        row.stableId = id;
        row.title = title;
        row.subtitle = subtitle;
        row.count = count;
        row.iconRes = iconRes;
        row.filter = filter;
        row.signature = "smart|" + id + "|" + count + "|" + title + "|" + subtitle;
        return row;
    }

    public static HomeRow section(long id, String title, String meta) {
        HomeRow row = new HomeRow();
        row.type = TYPE_SECTION;
        row.stableId = id;
        row.title = title;
        row.meta = meta;
        row.signature = "section|" + id + "|" + title + "|" + meta;
        return row;
    }

    public static HomeRow smartToggle(String title, String meta, boolean expanded) {
        HomeRow row = section(-10, title, meta);
        row.smartToggle = true;
        row.expanded = expanded;
        row.signature = row.signature + "|toggle|" + expanded;
        return row;
    }

    public static HomeRow category(CategoryEntity category, String displayName, int unread,
                                   int indent, boolean collapsed) {
        HomeRow row = new HomeRow();
        row.type = TYPE_CATEGORY;
        row.stableId = 1_000_000_000L + (category == null ? -1L : category.id);
        row.category = category;
        row.title = displayName;
        row.count = unread;
        row.indent = indent;
        row.collapsed = collapsed;
        row.signature = "category|" + row.stableId + "|" + displayName + "|" + unread
                + "|" + indent + "|" + collapsed
                + "|" + (category != null && category.muteNotifications);
        return row;
    }

    public static HomeRow feed(FeedListItem item) {
        HomeRow row = new HomeRow();
        row.type = TYPE_FEED;
        row.stableId = item.feed.id;
        row.feedItem = item;
        row.title = item.feed.title;
        row.signature = "feed|" + item.feed.id + "|" + item.feed.title + "|"
                + item.unreadCount + "|" + item.totalCount + "|" + item.latestPublishedAt
                + "|" + item.feed.feedPriority + "|" + item.feed.notificationEnabled
                + "|" + item.feed.faviconPath + "|" + item.feed.description
                + "|" + item.feed.categoryId + "|" + item.categoryName
                + "|" + item.healthStatus + "|" + item.feed.sortOrder;
        return row;
    }
}
