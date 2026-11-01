package com.intellirs.reader.ui.feed;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.intellirs.reader.IntelliRssApp;
import com.intellirs.reader.data.entity.FeedEntity;
import com.intellirs.reader.data.network.DiscoveryResult;
import com.intellirs.reader.data.network.FeedCandidate;
import com.intellirs.reader.di.AppContainer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class AddFeedViewModel extends AndroidViewModel {
    public static class DiscoveryState {
        public final boolean loading;
        public final String error;
        public final List<FeedCandidate> candidates;

        public DiscoveryState(boolean loading, String error, List<FeedCandidate> candidates) {
            this.loading = loading;
            this.error = error;
            this.candidates = candidates == null ? new ArrayList<>() : candidates;
        }
    }

    public static class SubscribeState {
        public final boolean loading;
        public final int added;
        public final int skipped;
        public final String error;

        public SubscribeState(boolean loading, int added, int skipped, String error) {
            this.loading = loading;
            this.added = added;
            this.skipped = skipped;
            this.error = error;
        }
    }

    private final AppContainer container;
    private final MutableLiveData<DiscoveryState> discovery =
            new MutableLiveData<>(new DiscoveryState(false, null, new ArrayList<>()));
    private final MutableLiveData<SubscribeState> subscribeState = new MutableLiveData<>();
    private final AtomicBoolean discovering = new AtomicBoolean(false);
    private final AtomicBoolean subscribing = new AtomicBoolean(false);

    public AddFeedViewModel(@NonNull Application application) {
        super(application);
        container = ((IntelliRssApp) application).getContainer();
    }

    public LiveData<DiscoveryState> discovery() {
        return discovery;
    }

    public LiveData<SubscribeState> subscribeState() {
        return subscribeState;
    }

    public LiveData<List<com.intellirs.reader.data.entity.CategoryEntity>> categories() {
        return container.feedRepository().observeCategories();
    }

    public void discover(String input) {
        if (!discovering.compareAndSet(false, true)) {
            return;
        }
        discovery.postValue(new DiscoveryState(true, null, new ArrayList<>()));
        container.ioExecutor().execute(() -> {
            try {
                DiscoveryResult result = container.feedDiscovery()
                        .discover(input, container.settingsRepository().isAllowHttp());
                discovery.postValue(new DiscoveryState(false, null, result.candidates));
            } catch (Exception e) {
                discovery.postValue(new DiscoveryState(false,
                        e.getMessage() == null ? "Discovery failed" : e.getMessage(),
                        new ArrayList<>()));
            } finally {
                discovering.set(false);
            }
        });
    }

    public void subscribe(List<FeedCandidate> candidates, Long categoryId) {
        if (candidates == null || candidates.isEmpty()) {
            return;
        }
        if (!subscribing.compareAndSet(false, true)) {
            return;
        }
        subscribeState.postValue(new SubscribeState(true, 0, 0, null));
        container.ioExecutor().execute(() -> {
            int added = 0;
            int skipped = 0;
            List<String> errors = new ArrayList<>();
            for (FeedCandidate candidate : candidates) {
                try {
                    FeedEntity existing = container.feedRepository()
                            .getFeedByUrl(candidate.feedUrl);
                    if (existing != null && existing.isActive) {
                        skipped++;
                        continue;
                    }
                    container.feedRepository().subscribe(candidate, categoryId);
                    added++;
                } catch (Exception e) {
                    errors.add((candidate.title == null ? candidate.feedUrl : candidate.title)
                            + ": " + (e.getMessage() == null ? "failed" : e.getMessage()));
                }
            }
            String error = errors.isEmpty() ? null : String.join("\n", errors);
            subscribeState.postValue(new SubscribeState(false, added, skipped, error));
            subscribing.set(false);
        });
    }

    public AppContainer container() {
        return container;
    }
}
