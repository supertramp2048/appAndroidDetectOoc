package com.socialapp.feature.profile;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.socialapp.core.network.ApiErrorParser;
import com.socialapp.core.network.ApiResult;
import com.socialapp.core.network.SocialAppApiService;
import com.socialapp.core.network.dto.PostListResponse;
import com.socialapp.core.network.dto.PostResource;
import com.socialapp.core.network.dto.ProfileResource;
import com.socialapp.core.pagination.PageLoader;

import java.util.Collections;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ProfileRepository {

    private final SocialAppApiService apiService;

    public ProfileRepository(@NonNull SocialAppApiService apiService) {
        this.apiService = apiService;
    }

    public LiveData<ApiResult<ProfileResource>> getProfile(int userId) {
        MutableLiveData<ApiResult<ProfileResource>> resultLiveData = new MutableLiveData<>(ApiResult.loading());

        apiService.getProfile(userId).enqueue(new Callback<ProfileResource>() {
            @Override
            public void onResponse(@NonNull Call<ProfileResource> call, @NonNull Response<ProfileResource> response) {
                if (response.isSuccessful() && response.body() != null) {
                    resultLiveData.setValue(ApiResult.success(response.body()));
                } else {
                    resultLiveData.setValue(ApiResult.error(ApiErrorParser.parse(response)));
                }
            }

            @Override
            public void onFailure(@NonNull Call<ProfileResource> call, @NonNull Throwable t) {
                resultLiveData.setValue(ApiResult.error(ApiErrorParser.parseThrowable(t)));
            }
        });

        return resultLiveData;
    }

    public PageLoader.PageFetcher<PostResource> createUserPostsPageFetcher(int userId) {
        return (page, callback) -> apiService.getPosts(page, 10, "newest", null, null, userId, null)
                .enqueue(new Callback<PostListResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<PostListResponse> call, @NonNull Response<PostListResponse> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            PostListResponse body = response.body();
                            List<PostResource> items = body.data != null ? body.data : Collections.emptyList();
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
