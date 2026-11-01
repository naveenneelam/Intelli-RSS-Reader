package com.intellirs.reader.data.repository;

import androidx.sqlite.db.SimpleSQLiteQuery;
import androidx.sqlite.db.SupportSQLiteQuery;

import com.intellirs.reader.domain.model.ArticleFilter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Central query builder for article paging and bulk actions. All user values are bound;
 * only hard-coded field names are interpolated into SQL.
 */
public final class ArticleQueryBuilder {
    private static final Pattern SEARCH_SEPARATORS = Pattern.compile("[^\\p{L}\\p{N}]+");

    private ArticleQueryBuilder() {
    }

    public static SupportSQLiteQuery buildPage(ArticleFilter filter, int limit, int offset) {
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT a.* FROM articles a");
        appendHistoryJoin(sql, filter);
        sql.append(" WHERE ").append(whereClause(filter, args));
        if (filter.mode == ArticleFilter.Mode.HISTORY) {
            sql.append(" ORDER BY rh.lastReadAt DESC, a.id DESC");
        } else {
            sql.append(" ORDER BY a.isRead ASC, a.publishedAt DESC, a.id DESC");
        }
        sql.append(" LIMIT ? OFFSET ?");
        args.add(Math.max(1, limit));
        args.add(Math.max(0, offset));
        return new SimpleSQLiteQuery(sql.toString(), args.toArray());
    }

    public static SupportSQLiteQuery buildCount(ArticleFilter filter) {
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM articles a");
        appendHistoryJoin(sql, filter);
        sql.append(" WHERE ").append(whereClause(filter, args));
        return new SimpleSQLiteQuery(sql.toString(), args.toArray());
    }

    public static SupportSQLiteQuery buildMarkAllRead(ArticleFilter filter) {
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("UPDATE articles SET isRead = 1 WHERE id IN " +
                "(SELECT a.id FROM articles a");
        appendHistoryJoin(sql, filter);
        sql.append(" WHERE ").append(whereClause(filter, args)).append(")");
        return new SimpleSQLiteQuery(sql.toString(), args.toArray());
    }

    public static SupportSQLiteQuery buildAdjacent(ArticleFilter filter, long publishedAt,
                                                   long currentId, boolean older) {
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT a.* FROM articles a");
        appendHistoryJoin(sql, filter);
        sql.append(" WHERE ").append(whereClause(filter, args));
        if (older) {
            sql.append(" AND (a.publishedAt < ? OR (a.publishedAt = ? AND a.id < ?))");
            sql.append(" ORDER BY a.publishedAt DESC, a.id DESC LIMIT 1");
        } else {
            sql.append(" AND (a.publishedAt > ? OR (a.publishedAt = ? AND a.id > ?))");
            sql.append(" ORDER BY a.publishedAt ASC, a.id ASC LIMIT 1");
        }
        args.add(publishedAt);
        args.add(publishedAt);
        args.add(currentId);
        return new SimpleSQLiteQuery(sql.toString(), args.toArray());
    }

    public static SupportSQLiteQuery buildRelated(String[] words, long articleId, int limit) {
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
                "SELECT a.* FROM articles a INNER JOIN article_fts f ON f.rowid = a.id " +
                        "WHERE a.id != ?");
        args.add(articleId);
        List<String> terms = new ArrayList<>();
        if (words != null) {
            for (String word : words) {
                if (word != null && !word.trim().isEmpty()) {
                    String normalized = word.toLowerCase(Locale.ROOT)
                            .replaceAll("[^\\p{L}\\p{N}]", "");
                    if (!normalized.isEmpty()) {
                        terms.add(normalized + "*");
                    }
                }
            }
        }
        if (terms.isEmpty()) {
            sql.append(" AND 1 = 0");
        } else {
            sql.append(" AND article_fts MATCH ?");
            args.add(String.join(" OR ", terms));
        }
        sql.append(" ORDER BY a.relevanceScore DESC, a.publishedAt DESC LIMIT ?");
        args.add(Math.max(1, limit));
        return new SimpleSQLiteQuery(sql.toString(), args.toArray());
    }

    private static void appendHistoryJoin(StringBuilder sql, ArticleFilter filter) {
        if (filter.mode == ArticleFilter.Mode.HISTORY) {
            sql.append(" INNER JOIN reading_history rh ON rh.articleId = a.id");
        }
    }

    private static String whereClause(ArticleFilter filter, List<Object> args) {
        List<String> clauses = new ArrayList<>();
        clauses.add("1 = 1");
        if (filter.mode == ArticleFilter.Mode.FEED) {
            clauses.add("EXISTS (SELECT 1 FROM article_feed_cross_ref xf " +
                    "WHERE xf.articleId = a.id AND xf.feedId = ?)");
            args.add(filter.feedId);
        } else if (filter.mode == ArticleFilter.Mode.CATEGORY) {
            clauses.add("EXISTS (SELECT 1 FROM article_feed_cross_ref xc " +
                    "INNER JOIN feeds fc ON fc.id = xc.feedId " +
                    "WHERE xc.articleId = a.id AND fc.categoryId = ?)");
            args.add(filter.categoryId);
        } else if (filter.mode == ArticleFilter.Mode.IMPORTANT) {
            clauses.add("EXISTS (SELECT 1 FROM article_feed_cross_ref xi " +
                    "INNER JOIN feeds fi ON fi.id = xi.feedId " +
                    "WHERE xi.articleId = a.id AND fi.feedPriority IN ('CRITICAL','HIGH'))");
        } else if (filter.mode == ArticleFilter.Mode.UNREAD) {
            clauses.add("a.isRead = 0");
        } else if (filter.mode == ArticleFilter.Mode.STARRED) {
            clauses.add("a.isBookmarked = 1");
        } else if (filter.mode == ArticleFilter.Mode.ARCHIVED) {
            clauses.add("a.isArchived = 1");
        }

        if (filter.hasSearchQuery()) {
            String match = toFtsQuery(filter.query);
            if (!match.isEmpty()) {
                clauses.add("a.id IN (SELECT rowid FROM article_fts WHERE article_fts MATCH ?)");
                args.add(match);
            } else {
                clauses.add("1 = 0");
            }
        }
        if ("UNREAD".equalsIgnoreCase(filter.readState)) {
            clauses.add("a.isRead = 0");
        } else if ("READ".equalsIgnoreCase(filter.readState)) {
            clauses.add("a.isRead = 1");
        }
        if (filter.bookmarkedOnly) {
            clauses.add("a.isBookmarked = 1");
        }
        if (filter.archivedOnly) {
            clauses.add("a.isArchived = 1");
        }
        if ("IMAGE".equalsIgnoreCase(filter.media)
                || "IMAGES".equalsIgnoreCase(filter.media)) {
            clauses.add("a.hasImages = 1");
        } else if ("VIDEO".equalsIgnoreCase(filter.media)) {
            clauses.add("a.hasVideo = 1");
        } else if ("AUDIO".equalsIgnoreCase(filter.media)
                || "PODCAST".equalsIgnoreCase(filter.media)) {
            clauses.add("a.hasAudio = 1");
        }
        if (filter.importance != null && !filter.importance.isEmpty()
                && !"ANY".equalsIgnoreCase(filter.importance)) {
            clauses.add("EXISTS (SELECT 1 FROM article_feed_cross_ref xp " +
                    "INNER JOIN feeds fp ON fp.id = xp.feedId " +
                    "WHERE xp.articleId = a.id AND fp.feedPriority = ?)");
            args.add(filter.importance.toUpperCase(Locale.ROOT));
        }
        if (filter.minRelevance > 0) {
            clauses.add("a.relevanceScore >= ?");
            args.add(filter.minRelevance);
        }
        if (filter.publishedAfter > 0) {
            clauses.add("a.publishedAt >= ?");
            args.add(filter.publishedAfter);
        }
        if (filter.publishedBefore > 0) {
            clauses.add("a.publishedAt <= ?");
            args.add(filter.publishedBefore);
        }
        return String.join(" AND ", clauses);
    }

    /** Builds a safe FTS4 conjunction with prefix matching. */
    public static String toFtsQuery(String raw) {
        if (raw == null) {
            return "";
        }
        String value = raw.length() > 200 ? raw.substring(0, 200) : raw;
        String[] parts = SEARCH_SEPARATORS.split(value.toLowerCase(Locale.ROOT).trim());
        List<String> terms = new ArrayList<>();
        for (String part : parts) {
            if (!part.isEmpty()) {
                terms.add(part + "*");
            }
            if (terms.size() >= 12) {
                break;
            }
        }
        return String.join(" ", terms);
    }

}
