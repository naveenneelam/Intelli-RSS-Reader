package com.intellirs.reader.ui.articles;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.intellirs.reader.IntelliRssApp;
import com.intellirs.reader.data.entity.ArticleEntity;
import com.intellirs.reader.di.AppContainer;
import com.intellirs.reader.domain.model.ArticleFilter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class ArticleListViewModel extends AndroidViewModel {
    private static final int PAGE_SIZE = 60;
    private final AppContainer container;
    private final MutableLiveData<ArticleListState> state =
            new MutableLiveData<>(ArticleListState.initial());
    private ArticleFilter filter = ArticleFilter.all();
    private final List<ArticleEntity> loaded = new ArrayList<>();
    private final AtomicBoolean loadingMore = new AtomicBoolean(false);
    private volatile int generation;
    private volatile int initialRequest;
    private volatile boolean endReached;
    private volatile boolean initialized;
    private volatile boolean reloadRequested;

    public ArticleListViewModel(@NonNull Application application) {
        super(application);
        container = ((IntelliRssApp) application).getContainer();
    }

    public LiveData<ArticleListState> state() {
        return state;
    }

    public AppContainer container() {
        return container;
    }

    public ArticleFilter filter() {
        return filter;
    }

    public void setFilter(ArticleFilter filter) {
        if (filter == null) {
            return;
        }
        if (this.filter == filter) {
            return;
        }
        this.filter = filter;
        generation++;
        initialized = false;
        endReached = false;
        synchronized (loaded) {
            loaded.clear();
        }
    }

    public void loadInitialIfNeeded() {
        if (!initialized) {
            initialized = true;
            loadInitial();
        }
    }

    public void reloadOnResume() {
        if (initialized) {
            reloadRequested = true;
        }
    }

    public boolean consumeReloadRequest() {
        if (reloadRequested) {
            reloadRequested = false;
            return true;
        }
        return false;
    }

    public void loadInitial() {
        final int request = ++initialRequest;
        final int requestGeneration = generation;
        final ArticleFilter requestFilter = filter;
        endReached = false;
        ArticleListState old = state.getValue();
        state.postValue(new ArticleListState(old == null ? new ArrayList<>() : old.articles,
                true, false, false, null));
        container.ioExecutor().execute(() -> {
            try {
                List<ArticleEntity> page = container.articleRepository()
                        .loadPage(requestFilter, PAGE_SIZE + 1, 0);
                boolean more = page.size() > PAGE_SIZE;
                if (more) {
                    page = new ArrayList<>(page.subList(0, PAGE_SIZE));
                }
                if (request != initialRequest || requestGeneration != generation) {
                    return;
                }
                synchronized (loaded) {
                    loaded.clear();
                    loaded.addAll(page);
                }
                endReached = !more;
                state.postValue(new ArticleListState(page, false, false, endReached, null));
            } catch (RuntimeException e) {
                if (request != initialRequest || requestGeneration != generation) {
                    return;
                }
                state.postValue(new ArticleListState(loaded, false, false, false,
                        e.getMessage() == null ? "Query failed" : e.getMessage()));
            }
        });
    }

    public void loadMore() {
        if (endReached || loadingMore.get()) {
            return;
        }
        if (!loadingMore.compareAndSet(false, true)) {
            return;
        }
        final int requestGeneration = generation;
        final int requestInitial = initialRequest;
        final ArticleFilter requestFilter = filter;
        int offset;
        synchronized (loaded) {
            offset = loaded.size();
        }
        ArticleListState old = state.getValue();
        if (old != null) {
            state.setValue(new ArticleListState(old.articles, false, true, false, null));
        }
        container.ioExecutor().execute(() -> {
            try {
                List<ArticleEntity> page = container.articleRepository()
                        .loadPage(requestFilter, PAGE_SIZE + 1, offset);
                if (requestGeneration != generation || requestInitial != initialRequest) {
                    return;
                }
                boolean more = page.size() > PAGE_SIZE;
                if (more) {
                    page = new ArrayList<>(page.subList(0, PAGE_SIZE));
                }
                synchronized (loaded) {
                    for (ArticleEntity article : page) {
                        boolean duplicate = false;
                        for (ArticleEntity existing : loaded) {
                            if (existing.id == article.id) {
                                duplicate = true;
                                break;
                            }
                        }
                        if (!duplicate) {
                            loaded.add(article);
                        }
                    }
                }
                endReached = !more;
                state.postValue(new ArticleListState(loaded, false, false, endReached, null));
            } catch (RuntimeException e) {
                if (requestGeneration != generation || requestInitial != initialRequest) {
                    return;
                }
                ArticleListState current = state.getValue();
                state.postValue(new ArticleListState(current == null
                        ? new ArrayList<>() : current.articles, false, false, false,
                        e.getMessage()));
            } finally {
                loadingMore.set(false);
            }
        });
    }

    public void markRead(long articleId, boolean read) {
        container.ioExecutor().execute(() ->
                container.articleRepository().setRead(articleId, read));
        ArticleListState current = state.getValue();
        if (current == null) {
            return;
        }
        List<ArticleEntity> updated = new ArrayList<>();
        for (ArticleEntity article : current.articles) {
            if (article.id == articleId) {
                ArticleEntity copy = copy(article);
                copy.isRead = read;
                updated.add(copy);
            } else {
                updated.add(article);
            }
        }
        state.setValue(new ArticleListState(updated, current.loading, current.loadingMore,
                current.endReached, current.error));
    }

    public void toggleBookmark(long articleId) {
        ArticleListState current = state.getValue();
        if (current == null) {
            return;
        }
        List<ArticleEntity> updated = new ArrayList<>();
        boolean newValue = false;
        for (ArticleEntity article : current.articles) {
            if (article.id == articleId) {
                ArticleEntity copy = copy(article);
                copy.isBookmarked = !copy.isBookmarked;
                newValue = copy.isBookmarked;
                updated.add(copy);
            } else {
                updated.add(article);
            }
        }
        state.setValue(new ArticleListState(updated, current.loading, current.loadingMore,
                current.endReached, current.error));
        final boolean bookmark = newValue;
        container.ioExecutor().execute(() ->
                container.articleRepository().setBookmark(articleId, bookmark));
    }

    public void markAllRead() {
        container.ioExecutor().execute(() -> {
            container.articleRepository().markAllRead(filter);
            loadInitial();
        });
    }

    private ArticleEntity copy(ArticleEntity source) {
        ArticleEntity copy = new ArticleEntity();
        copy.id = source.id;
        copy.originalUrl = source.originalUrl;
        copy.canonicalUrl = source.canonicalUrl;
        copy.guid = source.guid;
        copy.title = source.title;
        copy.description = source.description;
        copy.author = source.author;
        copy.publisher = source.publisher;
        copy.publishedAt = source.publishedAt;
        copy.firstSeenAt = source.firstSeenAt;
        copy.updatedAt = source.updatedAt;
        copy.lastSeenAt = source.lastSeenAt;
        copy.contentHash = source.contentHash;
        copy.normalizedTitle = source.normalizedTitle;
        copy.thumbnailUrl = source.thumbnailUrl;
        copy.thumbnailPath = source.thumbnailPath;
        copy.articleType = source.articleType;
        copy.relevanceScore = source.relevanceScore;
        copy.relevanceLevel = source.relevanceLevel;
        copy.relevanceReason = source.relevanceReason;
        copy.isRead = source.isRead;
        copy.isBookmarked = source.isBookmarked;
        copy.isArchived = source.isArchived;
        copy.isManuallyArchived = source.isManuallyArchived;
        copy.contentPath = source.contentPath;
        copy.latestVersionId = source.latestVersionId;
        copy.archivedContentHash = source.archivedContentHash;
        copy.hasImages = source.hasImages;
        copy.hasVideo = source.hasVideo;
        copy.hasAudio = source.hasAudio;
        copy.audioUrl = source.audioUrl;
        copy.audioPath = source.audioPath;
        copy.audioDurationMs = source.audioDurationMs;
        copy.videoUrl = source.videoUrl;
        copy.plainText = source.plainText;
        copy.aiSummary = source.aiSummary;
        copy.aiTags = source.aiTags;
        copy.primaryFeedId = source.primaryFeedId;
        copy.extractionQuality = source.extractionQuality;
        copy.archivedAt = source.archivedAt;
        return copy;
    }
}
