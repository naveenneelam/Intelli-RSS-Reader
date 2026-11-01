package com.intellirs.reader.ui.home;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.intellirs.reader.IntelliRssApp;
import com.intellirs.reader.data.entity.CategoryEntity;
import com.intellirs.reader.data.model.FeedListItem;
import com.intellirs.reader.di.AppContainer;
import com.intellirs.reader.domain.model.ArticleFilter;

import java.util.List;

public class HomeViewModel extends AndroidViewModel {
    private final AppContainer container;
    private final MutableLiveData<SmartCounts> counts = new MutableLiveData<>(new SmartCounts());

    public HomeViewModel(@NonNull Application application) {
        super(application);
        container = ((IntelliRssApp) application).getContainer();
    }

    public LiveData<List<FeedListItem>> feeds() {
        return container.feedRepository().observeFeedList();
    }

    public LiveData<List<CategoryEntity>> categories() {
        return container.feedRepository().observeCategories();
    }

    public LiveData<SmartCounts> counts() {
        return counts;
    }

    public AppContainer container() {
        return container;
    }

    public void refreshCounts() {
        container.ioExecutor().execute(() -> {
            SmartCounts value = new SmartCounts();
            try {
                value.all = container.articleRepository().count(ArticleFilter.all());
                value.unread = container.articleRepository().count(ArticleFilter.unread());
                value.important = container.articleRepository().count(ArticleFilter.important());
                value.starred = container.articleRepository().count(ArticleFilter.starred());
                value.archived = container.articleRepository().count(ArticleFilter.archived());
                value.history = container.articleRepository().count(ArticleFilter.history());
            } catch (RuntimeException e) {
                android.util.Log.w("HomeViewModel", "Could not refresh smart view counts", e);
            }
            counts.postValue(value);
        });
    }
}
