package com.socialapp.feature.feed;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.socialapp.core.network.ApiErrorParser;
import com.socialapp.core.network.ApiResult;
import com.socialapp.core.network.SocialAppApiService;
import com.socialapp.core.network.dto.CategoryListResponse;
import com.socialapp.core.network.dto.CategoryResource;
import com.socialapp.core.network.dto.PostListResponse;
import com.socialapp.core.network.dto.PostResource;
import com.socialapp.core.pagination.PageLoader;

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

        return (page, callback) -> apiService.getPosts(page, 10, sort, category, query, userId, feed)
                .enqueue(new Callback<PostListResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<PostListResponse> call,
                                           @NonNull Response<PostListResponse> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            PostListResponse body = response.body();
                            List<PostResource> items = body.data != null ? body.data : java.util.Collections.emptyList();
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
}
