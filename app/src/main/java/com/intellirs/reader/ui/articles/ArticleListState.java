package com.intellirs.reader.ui.articles;

import com.intellirs.reader.data.entity.ArticleEntity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ArticleListState {
    public final List<ArticleEntity> articles;
    public final boolean loading;
    public final boolean loadingMore;
    public final boolean endReached;
    public final String error;

    public ArticleListState(List<ArticleEntity> articles, boolean loading, boolean loadingMore,
                            boolean endReached, String error) {
        this.articles = articles == null ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(articles));
        this.loading = loading;
        this.loadingMore = loadingMore;
        this.endReached = endReached;
        this.error = error;
    }

    public static ArticleListState initial() {
        return new ArticleListState(Collections.emptyList(), true, false, false, null);
    }
}
