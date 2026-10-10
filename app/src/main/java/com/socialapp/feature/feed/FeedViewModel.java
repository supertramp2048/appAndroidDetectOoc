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

import java.util.List;

public class FeedViewModel extends ViewModel {

    public enum FeedTab {
        NEWEST("newest", null),
        HOT("hot", null),
        FOLLOWING(null, "following");

        @Nullable
        public final String sort;
        @Nullable
        public final String feed;

        FeedTab(@Nullable String sort, @Nullable String feed) {
            this.sort = sort;
            this.feed = feed;
        }
    }

    private final FeedRepository repository;

    private final MutableLiveData<FeedTab> activeTabLiveData = new MutableLiveData<>(FeedTab.NEWEST);
    private final MutableLiveData<CategoryResource> activeCategoryLiveData = new MutableLiveData<>(null);
    private final MutableLiveData<String> searchQueryLiveData = new MutableLiveData<>(null);

    private LiveData<ApiResult<List<CategoryResource>>> categoriesLiveData;
    private final PageLoader<PostResource> postPageLoader;

    public FeedViewModel(@NonNull FeedRepository repository) {
        this.repository = repository;

        PageLoader.PageFetcher<PostResource> fetcher = (page, callback) -> {
            FeedTab activeTab = activeTabLiveData.getValue() != null ? activeTabLiveData.getValue() : FeedTab.NEWEST;
            CategoryResource activeCategory = activeCategoryLiveData.getValue();
            String categoryParam = activeCategory != null ? String.valueOf(activeCategory.id) : null;
            String query = searchQueryLiveData.getValue();

            repository.createPostPageFetcher(
                    activeTab.sort,
                    categoryParam,
                    query,
                    null,
                    activeTab.feed
            ).fetch(page, callback);
        };

        this.postPageLoader = new PageLoader<>(fetcher, post -> post.id);
        this.postPageLoader.refresh();
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
