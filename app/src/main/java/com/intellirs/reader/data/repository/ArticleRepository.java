package com.intellirs.reader.data.repository;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;

import com.intellirs.reader.data.dao.ArticleDao;
import com.intellirs.reader.data.dao.ArticleFeedDao;
import com.intellirs.reader.data.dao.ArticleFtsDao;
import com.intellirs.reader.data.dao.ArticleImageDao;
import com.intellirs.reader.data.dao.ArticleNoteDao;
import com.intellirs.reader.data.dao.ArticleTagDao;
import com.intellirs.reader.data.dao.ArticleVersionDao;
import com.intellirs.reader.data.dao.ReadingHistoryDao;
import com.intellirs.reader.data.dao.SavedSearchDao;
import com.intellirs.reader.data.database.AppDatabase;
import com.intellirs.reader.data.entity.ArticleEntity;
import com.intellirs.reader.data.entity.ArticleFtsEntity;
import com.intellirs.reader.data.entity.ArticleImageEntity;
import com.intellirs.reader.data.entity.ArticleNoteEntity;
import com.intellirs.reader.data.entity.ArticleRelevanceLevel;
import com.intellirs.reader.data.entity.ArticleTagEntity;
import com.intellirs.reader.data.entity.ArticleVersionEntity;
import com.intellirs.reader.data.entity.ReadingHistoryEntity;
import com.intellirs.reader.data.entity.SavedSearchEntity;
import com.intellirs.reader.data.model.ArticleSource;
import com.intellirs.reader.data.model.CategoryReadCount;
import com.intellirs.reader.data.model.DailyCount;
import com.intellirs.reader.data.model.FeedReadCount;
import com.intellirs.reader.data.model.TagCount;
import com.intellirs.reader.domain.model.ArticleFilter;
import com.intellirs.reader.util.TextUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Article read/write repository. Paging is always bounded; full article HTML is never
 * loaded for list rows.
 */
public class ArticleRepository {
    private final AppDatabase database;
    private final ArticleDao articleDao;
    private final ArticleFeedDao crossRefDao;
    private final ArticleVersionDao versionDao;
    private final ArticleNoteDao noteDao;
    private final ArticleTagDao tagDao;
    private final ArticleImageDao imageDao;
    private final ReadingHistoryDao historyDao;
    private final ArticleFtsDao ftsDao;
    private final SavedSearchDao savedSearchDao;

    public ArticleRepository(AppDatabase database) {
        this.database = database;
        this.articleDao = database.articleDao();
        this.crossRefDao = database.articleFeedDao();
        this.versionDao = database.articleVersionDao();
        this.noteDao = database.articleNoteDao();
        this.tagDao = database.articleTagDao();
        this.imageDao = database.articleImageDao();
        this.historyDao = database.readingHistoryDao();
        this.ftsDao = database.articleFtsDao();
        this.savedSearchDao = database.savedSearchDao();
    }

    public LiveData<ArticleEntity> observeArticle(long id) {
        return articleDao.observeById(id);
    }

    @Nullable
    public ArticleEntity getArticle(long id) {
        return articleDao.getById(id);
    }

    public List<ArticleEntity> loadPage(ArticleFilter filter, int limit, int offset) {
        return articleDao.loadPage(ArticleQueryBuilder.buildPage(filter, limit, offset));
    }

    public int count(ArticleFilter filter) {
        return articleDao.count(ArticleQueryBuilder.buildCount(filter));
    }

    public int markAllRead(ArticleFilter filter) {
        return articleDao.executeRawUpdate(ArticleQueryBuilder.buildMarkAllRead(filter));
    }

    public void setRead(long articleId, boolean read) {
        articleDao.setRead(articleId, read);
    }

    /** Bulk read marker used when a feed list is scrolled and rows leave the top. */
    public int markReadByIds(List<Long> articleIds) {
        if (articleIds == null || articleIds.isEmpty()) {
            return 0;
        }
        int updated = 0;
        // SQLite implementations on older Android versions cap host parameters at 999,
        // so keep every IN(...) list well below that.
        for (int start = 0; start < articleIds.size(); start += 500) {
            int end = Math.min(articleIds.size(), start + 500);
            updated += articleDao.markReadByIds(articleIds.subList(start, end));
        }
        return updated;
    }

    public void toggleBookmark(long articleId) {
        ArticleEntity article = articleDao.getById(articleId);
        if (article != null) {
            articleDao.setBookmarked(articleId, !article.isBookmarked);
        }
    }

    public void setBookmark(long articleId, boolean bookmarked) {
        articleDao.setBookmarked(articleId, bookmarked);
    }

    /** User preference for attribution: make a syndicated source the primary publisher. */
    public void preferSource(long articleId, ArticleSource source) {
        ArticleEntity article = articleDao.getById(articleId);
        if (article == null || source == null) {
            return;
        }
        String url = source.sourceUrl;
        if (url == null || url.isEmpty()) {
            url = article.originalUrl;
        }
        articleDao.updatePrimarySource(articleId, source.feedId,
                source.feedTitle == null ? article.publisher : source.feedTitle, url);
        rebuildFts(articleId);
    }

    /** Returns {olderId, newerId}; either may be -1. */
    public long[] adjacentArticleIds(ArticleEntity current, ArticleFilter filter) {
        if (current == null) {
            return new long[]{-1L, -1L};
        }
        List<ArticleEntity> older = articleDao.loadPage(
                ArticleQueryBuilder.buildAdjacent(filter, current.publishedAt, current.id, true));
        List<ArticleEntity> newer = articleDao.loadPage(
                ArticleQueryBuilder.buildAdjacent(filter, current.publishedAt, current.id, false));
        return new long[]{
                older.isEmpty() || older.get(0).id == current.id ? -1L : older.get(0).id,
                newer.isEmpty() || newer.get(0).id == current.id ? -1L : newer.get(0).id
        };
    }

    public List<ArticleSource> getSources(long articleId) {
        return crossRefDao.getSources(articleId);
    }

    public List<ArticleVersionEntity> getVersions(long articleId) {
        return versionDao.getForArticle(articleId);
    }

    public ArticleVersionEntity getVersion(long versionId) {
        return versionDao.getById(versionId);
    }

    public List<ArticleImageEntity> getImages(long articleId) {
        return imageDao.getForArticle(articleId);
    }

    @Nullable
    public ArticleNoteEntity getNote(long articleId) {
        return noteDao.getForArticle(articleId);
    }

    public List<ArticleTagEntity> getTags(long articleId) {
        return tagDao.getForArticle(articleId);
    }

    public void saveNote(long articleId, @Nullable String text) {
        String trimmed = text == null ? "" : text.trim();
        database.runInTransaction(() -> {
            ArticleNoteEntity existing = noteDao.getForArticle(articleId);
            if (trimmed.isEmpty()) {
                if (existing != null) {
                    noteDao.deleteForArticle(articleId);
                }
            } else if (existing == null) {
                ArticleNoteEntity note = new ArticleNoteEntity();
                note.articleId = articleId;
                note.note = trimmed;
                note.createdAt = System.currentTimeMillis();
                note.updatedAt = note.createdAt;
                noteDao.save(note);
            } else {
                existing.note = trimmed;
                existing.updatedAt = System.currentTimeMillis();
                noteDao.save(existing);
            }
            rebuildFts(articleId);
        });
    }

    public void addTag(long articleId, String rawTag) {
        String tag = rawTag == null ? "" : rawTag.trim();
        if (tag.isEmpty()) {
            return;
        }
        if (tag.length() > 48) {
            tag = tag.substring(0, 48);
        }
        ArticleTagEntity entity = new ArticleTagEntity();
        entity.articleId = articleId;
        entity.tag = tag;
        tagDao.add(entity);
        rebuildFts(articleId);
    }

    public void removeTag(long articleId, String tag) {
        tagDao.remove(articleId, tag);
        rebuildFts(articleId);
    }

    public void recordOpened(long articleId) {
        long now = System.currentTimeMillis();
        ReadingHistoryEntity history = historyDao.getForArticle(articleId);
        if (history == null) {
            history = new ReadingHistoryEntity();
            history.articleId = articleId;
            history.openedAt = now;
            history.lastReadAt = now;
            history.progress = 0f;
            history.secondsRead = 0;
            historyDao.save(history);
        } else {
            history.lastReadAt = now;
            historyDao.save(history);
        }
        articleDao.setRead(articleId, true);
    }

    public void updateProgress(long articleId, float progress, long additionalSeconds) {
        long now = System.currentTimeMillis();
        ReadingHistoryEntity history = historyDao.getForArticle(articleId);
        if (history == null) {
            history = new ReadingHistoryEntity();
            history.articleId = articleId;
            history.openedAt = now;
            history.lastReadAt = now;
            history.progress = Math.max(0f, Math.min(1f, progress));
            history.secondsRead = Math.max(0, additionalSeconds);
            historyDao.save(history);
        } else {
            historyDao.updateProgress(articleId, now,
                    Math.max(0f, Math.min(1f, progress)), Math.max(0, additionalSeconds));
        }
    }

    public void clearHistory() {
        historyDao.clearAll();
    }

    public void rebuildFts(long articleId) {
        ArticleEntity article = articleDao.getById(articleId);
        if (article == null) {
            ftsDao.remove(articleId);
            return;
        }
        List<String> tags = tagDao.getTags(articleId);
        ArticleNoteEntity note = noteDao.getForArticle(articleId);
        ArticleFtsEntity entry = new ArticleFtsEntity();
        entry.rowId = articleId;
        entry.title = article.title;
        entry.description = TextUtils.stripHtmlOrEmpty(article.description);
        entry.bodyText = article.plainText == null ? "" : limit(article.plainText, 40_000);
        entry.publisher = article.publisher == null ? "" : article.publisher;
        entry.author = article.author == null ? "" : article.author;
        entry.tags = String.join(" ", tags);
        entry.notes = note == null ? "" : limit(note.note, 10_000);
        ftsDao.remove(articleId);
        ftsDao.index(entry);
    }

    public void updateRelevance(long articleId, int score, ArticleRelevanceLevel level,
                                String reason) {
        articleDao.updateRelevance(articleId, score, level, reason);
    }

    public void updatePlainText(long articleId, String plainText) {
        articleDao.updatePlainText(articleId, limit(plainText, 40_000));
        rebuildFts(articleId);
    }

    public void saveAiSummary(long articleId, String summary) {
        articleDao.updateAiSummary(articleId, limit(summary, 8000));
    }

    public void saveAiTags(long articleId, String tags) {
        articleDao.updateAiTags(articleId, limit(tags, 1000));
    }

    public List<ArticleEntity> findRelated(ArticleEntity article, int limit) {
        if (article == null) {
            return new ArrayList<>();
        }
        String[] words = TextUtils.significantWords(article.title, 3);
        if (words.length == 0) {
            words = TextUtils.significantWords(article.description, 3);
        }
        return articleDao.loadPage(ArticleQueryBuilder.buildRelated(words, article.id, limit));
    }

    public SavedSearchEntity saveSearch(String name, ArticleFilter filter, String filterJson) {
        SavedSearchEntity entity = new SavedSearchEntity();
        entity.name = name == null ? "" : name.trim();
        entity.query = filter.query == null ? "" : filter.query;
        entity.filterJson = filterJson == null ? "{}" : filterJson;
        entity.createdAt = System.currentTimeMillis();
        entity.id = savedSearchDao.insert(entity);
        return entity;
    }

    public LiveData<List<SavedSearchEntity>> observeSavedSearches() {
        return savedSearchDao.observeAll();
    }

    public List<SavedSearchEntity> getSavedSearches() {
        return savedSearchDao.getAll();
    }

    public void deleteSavedSearch(SavedSearchEntity entity) {
        savedSearchDao.delete(entity);
    }

    public void clearSavedSearches() {
        savedSearchDao.clearAll();
    }

    public int countAll() {
        return articleDao.countAll();
    }

    public int countUnread() {
        return articleDao.countUnread();
    }

    public int countRead() {
        return articleDao.countRead();
    }

    public int countBookmarked() {
        return articleDao.countBookmarked();
    }

    public int countArchived() {
        return articleDao.countArchived();
    }

    public long totalSecondsRead() {
        return articleDao.totalSecondsRead();
    }

    public List<DailyCount> dailyReadCounts(int days) {
        long since = System.currentTimeMillis() - Math.max(1, days) * 86_400_000L;
        return articleDao.getDailyReadCounts(since);
    }

    public List<FeedReadCount> mostReadFeeds(int limit) {
        return articleDao.getMostReadFeeds(limit);
    }

    public List<CategoryReadCount> mostReadCategories(int limit) {
        return articleDao.getMostReadCategories(limit);
    }

    public List<TagCount> topTags(int limit) {
        return articleDao.getTopTags(limit);
    }

    public List<ArticleEntity> getArchivedArticles() {
        return articleDao.getArchived();
    }

    public List<ArticleEntity> getByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        List<ArticleEntity> result = new ArrayList<>();
        for (int start = 0; start < ids.size(); start += 500) {
            int end = Math.min(ids.size(), start + 500);
            result.addAll(articleDao.getByIds(ids.subList(start, end)));
        }
        return result;
    }

    public AppDatabase database() {
        return database;
    }

    private static String limit(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
