package com.intellirs.reader.ui.feed;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.intellirs.reader.IntelliRssApp;
import com.intellirs.reader.data.entity.CategoryEntity;
import com.intellirs.reader.data.entity.FeedEntity;
import com.intellirs.reader.di.AppContainer;

import java.util.List;

public class FeedDetailViewModel extends AndroidViewModel {
    private final AppContainer container;
    private final MutableLiveData<FeedDetailData> data = new MutableLiveData<>();
    private long feedId = -1L;

    public FeedDetailViewModel(@NonNull Application application) {
        super(application);
        container = ((IntelliRssApp) application).getContainer();
    }

    public LiveData<FeedDetailData> data() {
        return data;
    }

    public LiveData<List<CategoryEntity>> categories() {
        return container.feedRepository().observeCategories();
    }

    public void load(long id) {
        feedId = id;
        reload();
    }

    public void reload() {
        if (feedId <= 0) {
            return;
        }
        container.ioExecutor().execute(() -> {
            FeedEntity feed = container.feedRepository().getFeed(feedId);
            if (feed == null) {
                return;
            }
            FeedDetailData detail = new FeedDetailData();
            detail.feed = feed;
            detail.stats = container.feedRepository().getFeedStats(feedId);
            detail.health = container.feedRepository().healthOrCreate(feedId);
            detail.suggestion = container.feedRepository().suggestImportance(feedId);
            if (feed.categoryId != null) {
                CategoryEntity category = container.feedRepository().getCategory(feed.categoryId);
                detail.categoryName = category == null ? "" : category.name;
            }
            detail.categories = container.feedRepository().getCategories();
            data.postValue(detail);
        });
    }

    public AppContainer container() {
        return container;
    }

    public long feedId() {
        return feedId;
    }
}
