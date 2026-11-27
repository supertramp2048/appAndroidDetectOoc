package com.socialapp.feature.feed;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.socialapp.BuildConfig;
import com.socialapp.MyApplication;
import com.socialapp.core.di.AppContainer;
import com.socialapp.core.mock.FakeApi;
import com.socialapp.core.network.ApiErrorParser;
import com.socialapp.core.network.ApiResult;
import com.socialapp.core.network.SocialAppApiService;
import com.socialapp.core.network.dto.CategoryListResponse;
import com.socialapp.core.network.dto.CategoryResource;
import com.socialapp.core.network.dto.PostListResponse;
import com.socialapp.core.network.dto.PostResource;
import com.socialapp.core.pagination.PageLoader;
import com.socialapp.core.prefetch.HitType;
import com.socialapp.core.prefetch.Repository;
import com.socialapp.core.prefetch.ScreenId;
import com.socialapp.core.prefetch.ScreenLoadCallback;
import com.socialapp.core.telemetry.LoadContext;
import com.socialapp.core.telemetry.NavigationEvent;
import com.socialapp.core.ui.blocks.adapter.FeedBlockListBuilder;

import java.util.Collections;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class FeedRepository {

    private final SocialAppApiService apiService;

    public FeedRepository(@NonNull SocialAppApiService apiService) {
        this.apiService = apiService;
    }

    public LiveData<ApiResult<List<CategoryResource>>> getCategories() {
        if (BuildConfig.PROFILE_FIXTURES) {
            return new MutableLiveData<>(ApiResult.success(FakeApi.getSampleCategories()));
        }
        MutableLiveData<ApiResult<List<CategoryResource>>> resultLiveData = new MutableLiveData<>(ApiResult.loading());

        apiService.getCategories().enqueue(new Callback<CategoryListResponse>() {
            @Override
            public void onResponse(@NonNull Call<CategoryListResponse> call,
                                   @NonNull Response<CategoryListResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<CategoryResource> categories = response.body().data != null ? response.body().data : Collections.emptyList();
                    resultLiveData.setValue(ApiResult.success(categories));
                } else {
                    resultLiveData.setValue(ApiResult.error(ApiErrorParser.parse(response)));
                }
            }

            @Override
            public void onFailure(@NonNull Call<CategoryListResponse> call, @NonNull Throwable t) {
                resultLiveData.setValue(ApiResult.error(ApiErrorParser.parseThrowable(t)));
            }
        });

        return resultLiveData;
    }

    public PageLoader.PageFetcher<PostResource> createPostPageFetcher(
            @Nullable String sort,
            @Nullable String category,
            @Nullable String query,
            @Nullable Integer userId,
            @Nullable String feed) {
        return createPostPageFetcher(sort, category, query, userId, feed, LoadContext.Trigger.UNKNOWN, null);
    }

    PageLoader.PageFetcher<PostResource> createPostPageFetcher(
            @Nullable String sort, @Nullable String category, @Nullable String query,
            @Nullable Integer userId, @Nullable String feed, LoadContext.Trigger trigger,
            @Nullable NavigationEvent navigation) {

        return (page, callback) -> {
            ScreenId screenId = ScreenId.FEED_NEWEST;
            if ("hot".equalsIgnoreCase(sort)) {
                screenId = ScreenId.FEED_HOT;
            } else if ("following".equalsIgnoreCase(feed)) {
                screenId = ScreenId.FEED_FOLLOWING;
            } else if (query != null && !query.trim().isEmpty()) {
                screenId = ScreenId.SEARCH;
            }

            if (page == 1) {
                Bundle params = new Bundle();
                params.putString("sort", sort);
                params.putString("category", category);
                params.putString("query", query);
                params.putString("feed", feed);

                Repository.load(screenId, params, new Repository.RealDataLoader<List<PostResource>>() {
                    @Override
                    public void execute(@NonNull ScreenLoadCallback<List<PostResource>> screenCallback, @NonNull LoadContext load) {
                        apiService.getPostsMeasured(page, 10, sort, category, query, userId, feed, load)
                                .enqueue(new Callback<PostListResponse>() {
                                    @Override
                                    public void onResponse(@NonNull Call<PostListResponse> call,
                                                           @NonNull Response<PostListResponse> response) {
                                        if (response.isSuccessful() && response.body() != null) {
                                            List<PostResource> items = response.body().data != null ? response.body().data : Collections.emptyList();
                                            screenCallback.onLoaded(HitType.MISS, 0, items);
                                        } else {
                                            screenCallback.onError(new Exception(ApiErrorParser.parse(response).getMessage()));
                                        }
                                    }

                                    @Override
                                    public void onFailure(@NonNull Call<PostListResponse> call, @NonNull Throwable t) {
                                        if (call.isCanceled()) screenCallback.onCancelled(t);
                                        else screenCallback.onError(t);
                                    }
                                });
                    }
                }, new ScreenLoadCallback<List<PostResource>>() {
                    @Override
                    public void onLoaded(@NonNull HitType hitType, long latencyMs, @Nullable List<PostResource> data) {
                        List<PostResource> items = data != null ? data : Collections.emptyList();
                        warmupFeedPostsL1(items);
                        callback.onSuccess(new PageLoader.PageResult<>(items, 1, items.size() >= 10));
                    }

                    @Override
                    public void onError(@NonNull Throwable error) {
                        callback.onError(ApiErrorParser.parseThrowable(error));
                    }
                }, trigger, navigation);
            } else {
                apiService.getPosts(page, 10, sort, category, query, userId, feed)
                        .enqueue(new Callback<PostListResponse>() {
                            @Override
                            public void onResponse(@NonNull Call<PostListResponse> call,
                                                   @NonNull Response<PostListResponse> response) {
                                if (response.isSuccessful() && response.body() != null) {
                                    PostListResponse body = response.body();
                                    List<PostResource> items = body.data != null ? body.data : Collections.emptyList();
                                    warmupFeedPostsL1(items);
                                    boolean hasNext = body.links != null && body.links.hasNext();
                                    int currentPage = (body.meta != null && body.meta.currentPage > 0) ? body.meta.currentPage : page;
                                    callback.onSuccess(new PageLoader.PageResult<>(items, currentPage, hasNext));
                                } else {
                                    callback.onError(ApiErrorParser.parse(response));
                                }
                            }

                            @Override
                            public void onFailure(@NonNull Call<PostListResponse> call, @NonNull Throwable t) {
                                callback.onError(ApiErrorParser.parseThrowable(t));
                            }
                        });
            }
        };
    }

    private static void warmupFeedPostsL1(@NonNull List<PostResource> posts) {
        if (posts.isEmpty()) return;
        MyApplication app = MyApplication.getInstance();
        AppContainer container = app != null ? app.getAppContainer() : null;
        if (container != null) {
            container.getBlockWarmupEngine().warmupL1(app, FeedBlockListBuilder.build(posts));
        }
    }
}
