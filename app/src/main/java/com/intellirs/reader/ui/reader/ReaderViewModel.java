package com.intellirs.reader.ui.reader;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.intellirs.reader.IntelliRssApp;
import com.intellirs.reader.data.entity.ArticleEntity;
import com.intellirs.reader.data.entity.CategoryEntity;
import com.intellirs.reader.data.entity.FeedEntity;
import com.intellirs.reader.data.entity.ReadingHistoryEntity;
import com.intellirs.reader.di.AppContainer;
import com.intellirs.reader.domain.model.ArticleFilter;
import com.intellirs.reader.util.TextUtils;

import org.jsoup.Jsoup;

import java.util.ArrayList;

public class ReaderViewModel extends AndroidViewModel {
    private final AppContainer container;
    private final MutableLiveData<ReaderState> state = new MutableLiveData<>();
    private long currentArticleId = -1L;
    private ArticleFilter filter = ArticleFilter.all();
    private int generation;

    public ReaderViewModel(@NonNull Application application) {
        super(application);
        container = ((IntelliRssApp) application).getContainer();
    }

    public LiveData<ReaderState> state() {
        return state;
    }

    public long currentArticleId() {
        return currentArticleId;
    }

    public ArticleFilter filter() {
        return filter;
    }

    public void load(long articleId, ArticleFilter filter, boolean markOpened) {
        if (filter != null) {
            this.filter = filter;
        }
        boolean newArticle = currentArticleId != articleId;
        currentArticleId = articleId;
        int request = ++generation;
        container.ioExecutor().execute(() -> {
            if (isStale(request)) {
                return;
            }
            if (markOpened && newArticle) {
                container.articleRepository().recordOpened(articleId);
            }
            ReaderState loaded = buildState(articleId, filter);
            if (!isStale(request)) {
                state.postValue(loaded);
            }
        });
    }

    public void reloadCurrent(boolean markOpened) {
        if (currentArticleId > 0) {
            int request = ++generation;
            container.ioExecutor().execute(() -> {
                ReaderState loaded = buildState(currentArticleId, filter);
                if (!isStale(request)) {
                    state.postValue(loaded);
                }
            });
        }
    }

    private boolean isStale(int request) {
        return request != generation;
    }

    private ReaderState buildState(long articleId, ArticleFilter filter) {
        ReaderState result = new ReaderState();
        ArticleEntity article = container.articleRepository().getArticle(articleId);
        if (article == null) {
            result.error = "Article not found";
            return result;
        }
        result.article = article;
        FeedEntity feed = article.primaryFeedId == null ? null
                : container.feedRepository().getFeed(article.primaryFeedId);
        result.feed = feed;
        if (feed != null && feed.categoryId != null) {
            CategoryEntity category = container.feedRepository().getCategory(feed.categoryId);
            result.categoryName = category == null ? "" : category.name;
        }
        result.sources = container.articleRepository().getSources(articleId);
        result.tags = container.articleRepository().getTags(articleId);
        result.note = container.articleRepository().getNote(articleId);
        result.versions = container.articleRepository().getVersions(articleId);
        result.related = container.settingsRepository().similarArticlesEnabled()
                ? container.articleRepository().findRelated(article, 5)
                : new ArrayList<>();
        ReadingHistoryEntity history = container.database().readingHistoryDao()
                .getForArticle(articleId);
        result.savedProgress = history == null ? 0f : Math.max(0f, Math.min(1f, history.progress));
        String baseUrl = article.originalUrl;
        if (baseUrl == null || baseUrl.isEmpty()) {
            baseUrl = article.canonicalUrl;
        }
        if (baseUrl == null || baseUrl.isEmpty()) {
            baseUrl = feed == null ? null : feed.websiteUrl;
        }
        result.baseUrl = baseUrl;
        String archived = container.archiveManager().readArchivedHtml(article);
        if (archived != null && !archived.trim().isEmpty()) {
            result.html = archived;
            result.fallbackContent = false;
        } else {
            String description = container.contentExtractor()
                    .sanitizeFragment(article.description, baseUrl);
            if (description.trim().isEmpty() && article.plainText != null) {
                description = "<p>" + Jsoup.parse(article.plainText).text() + "</p>";
            }
            result.html = description;
            result.fallbackContent = true;
        }
        long[] adjacent = container.articleRepository().adjacentArticleIds(article, filter);
        result.olderArticleId = adjacent[0];
        result.newerArticleId = adjacent[1];
        return result;
    }

    public void setRead(boolean read) {
        if (currentArticleId <= 0) {
            return;
        }
        long id = currentArticleId;
        container.ioExecutor().execute(() -> {
            container.articleRepository().setRead(id, read);
            ReaderState loaded = buildState(id, filter);
            state.postValue(loaded);
        });
    }

    public void toggleBookmark() {
        if (currentArticleId <= 0) {
            return;
        }
        long id = currentArticleId;
        container.ioExecutor().execute(() -> {
            container.articleRepository().toggleBookmark(id);
            ReaderState loaded = buildState(id, filter);
            state.postValue(loaded);
        });
    }

    public void saveNote(String note) {
        if (currentArticleId <= 0) {
            return;
        }
        long id = currentArticleId;
        container.ioExecutor().execute(() -> {
            container.articleRepository().saveNote(id, note);
            ReaderState loaded = buildState(id, filter);
            state.postValue(loaded);
        });
    }

    public void addTag(String tag) {
        if (currentArticleId <= 0) {
            return;
        }
        long id = currentArticleId;
        container.ioExecutor().execute(() -> {
            container.articleRepository().addTag(id, tag);
            ReaderState loaded = buildState(id, filter);
            state.postValue(loaded);
        });
    }

    public void removeTag(String tag) {
        if (currentArticleId <= 0) {
            return;
        }
        long id = currentArticleId;
        container.ioExecutor().execute(() -> {
            container.articleRepository().removeTag(id, tag);
            ReaderState loaded = buildState(id, filter);
            state.postValue(loaded);
        });
    }

    public void preferSource(com.intellirs.reader.data.model.ArticleSource source) {
        if (currentArticleId <= 0 || source == null) {
            return;
        }
        long id = currentArticleId;
        container.ioExecutor().execute(() -> {
            container.articleRepository().preferSource(id, source);
            ReaderState loaded = buildState(id, filter);
            state.postValue(loaded);
        });
    }

    public AppContainer container() {
        return container;
    }
}
