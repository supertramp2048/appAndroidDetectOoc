package com.socialapp.feature.feed;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.socialapp.core.network.ApiResult;
import com.socialapp.core.network.dto.CategoryResource;
import com.socialapp.core.network.dto.PostResource;
import com.socialapp.core.pagination.PageLoader;
import com.socialapp.core.prefetch.ScreenId;
import com.socialapp.core.telemetry.LoadContext;
import com.socialapp.core.telemetry.NavigationEvent;

import java.util.List;
import java.util.Locale;

public class FeedViewModel extends ViewModel {

    public enum FeedTab {
        NEWEST("newest", null, ScreenId.FEED_NEWEST),
        HOT("hot", null, ScreenId.FEED_HOT),
        FOLLOWING(null, "following", ScreenId.FEED_FOLLOWING);

        @Nullable
        public final String sort;
        @Nullable
        public final String feed;
        public final ScreenId screenId;

        FeedTab(@Nullable String sort, @Nullable String feed, @NonNull ScreenId screenId) {
            this.sort = sort;
            this.feed = feed;
            this.screenId = screenId;
        }

        public String getRouteValue() {
            return name().toLowerCase(Locale.ROOT);
        }

        @Nullable
        public static FeedTab fromRouteValue(@Nullable String value) {
            for (FeedTab tab : values()) {
                if (tab.getRouteValue().equals(value)) {
                    return tab;
                }
            }
            return null;
        }
    }

    private final FeedRepository repository;

    private final MutableLiveData<FeedTab> activeTabLiveData;
    private final MutableLiveData<CategoryResource> activeCategoryLiveData = new MutableLiveData<>(null);
    private final MutableLiveData<String> searchQueryLiveData = new MutableLiveData<>(null);

    private LiveData<ApiResult<List<CategoryResource>>> categoriesLiveData;
    private final PageLoader<PostResource> postPageLoader;

    public FeedViewModel(@NonNull FeedRepository repository) {
        this(repository, FeedTab.NEWEST);
    }

    public FeedViewModel(@NonNull FeedRepository repository, @NonNull FeedTab initialTab) {
        this.repository = repository;
        this.activeTabLiveData = new MutableLiveData<>(initialTab);

        this.postPageLoader = new PageLoader<>(postFetcher(LoadContext.Trigger.UNKNOWN, null), post -> post.id);
        refresh(LoadContext.Trigger.INITIAL, null);
    }

    private PageLoader.PageFetcher<PostResource> postFetcher(LoadContext.Trigger trigger,
                                                           @Nullable NavigationEvent navigation) {
        return (page, callback) -> {
            FeedTab activeTab = activeTabLiveData.getValue() != null ? activeTabLiveData.getValue() : FeedTab.NEWEST;
            CategoryResource category = activeCategoryLiveData.getValue();
            String categoryParam = category != null ? String.valueOf(category.id) : null;
            repository.createPostPageFetcher(activeTab.sort, categoryParam, searchQueryLiveData.getValue(), null,
                    activeTab.feed, trigger, navigation).fetch(page, callback);
        };
    }

    public void selectTab(@NonNull FeedTab tab) {
        if (activeTabLiveData.getValue() == tab) return;
        activeTabLiveData.setValue(tab);
        postPageLoader.refresh();
    }

    public void selectCategory(@Nullable CategoryResource category) {
        CategoryResource current = activeCategoryLiveData.getValue();
        if (current == category || (current != null && category != null && current.id == category.id)) {
            return;
        }
        activeCategoryLiveData.setValue(category);
        postPageLoader.refresh();
    }

    public void setSearchQuery(@Nullable String query) {
        String current = searchQueryLiveData.getValue();
        if ((current == null && query == null) || (current != null && current.equals(query))) {
            return;
        }
        searchQueryLiveData.setValue(query);
        postPageLoader.refresh();
    }

    public void refresh() {
        if (postPageLoader != null) {
            postPageLoader.refresh();
        }
    }

    void refresh(LoadContext.Trigger trigger, @Nullable NavigationEvent navigation) {
        postPageLoader.refresh(postFetcher(trigger, navigation));
    }

    public void loadNext() {
        if (postPageLoader != null) {
            postPageLoader.loadNext();
        }
    }

    public LiveData<ApiResult<List<CategoryResource>>> getCategories() {
        if (categoriesLiveData == null) {
            categoriesLiveData = repository.getCategories();
        }
        return categoriesLiveData;
    }

    @NonNull
    public LiveData<FeedTab> getActiveTabLiveData() {
        return activeTabLiveData;
    }

    @NonNull
    public LiveData<CategoryResource> getActiveCategoryLiveData() {
        return activeCategoryLiveData;
    }

    @NonNull
    public PageLoader<PostResource> getPostPageLoader() {
        return postPageLoader;
    }
}
