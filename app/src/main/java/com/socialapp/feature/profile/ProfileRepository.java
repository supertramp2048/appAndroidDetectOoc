package com.socialapp.feature.profile;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.socialapp.core.network.ApiErrorParser;
import com.socialapp.core.network.ApiResult;
import com.socialapp.core.network.SocialAppApiService;
import com.socialapp.core.network.dto.PostListResponse;
import com.socialapp.core.network.dto.PostResource;
import com.socialapp.core.network.dto.ProfileDetailResponse;
import com.socialapp.core.network.dto.ProfileResource;
import com.socialapp.core.network.dto.ReportRequest;
import com.socialapp.core.network.dto.UserResource;
import com.socialapp.core.network.dto.UserSearchResponse;
import com.socialapp.core.pagination.PageLoader;

import java.util.Collections;
import java.util.List;

import okhttp3.ResponseBody;
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

        apiService.getProfile(userId).enqueue(new Callback<ProfileDetailResponse>() {
            @Override
            public void onResponse(@NonNull Call<ProfileDetailResponse> call, @NonNull Response<ProfileDetailResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    resultLiveData.setValue(ApiResult.success(response.body().getProfile()));
                } else {
                    resultLiveData.setValue(ApiResult.error(ApiErrorParser.parse(response)));
                }
            }

            @Override
            public void onFailure(@NonNull Call<ProfileDetailResponse> call, @NonNull Throwable t) {
                resultLiveData.setValue(ApiResult.error(ApiErrorParser.parseThrowable(t)));
            }
        });

        return resultLiveData;
    }

    public PageLoader.PageFetcher<UserResource> getFollowersPageFetcher(int userId) {
        return (page, callback) -> apiService.getFollowers(userId, page).enqueue(new Callback<UserSearchResponse>() {
            @Override
            public void onResponse(@NonNull Call<UserSearchResponse> call, @NonNull Response<UserSearchResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<UserResource> items = response.body().data != null ? response.body().data : Collections.emptyList();
                    boolean hasNext = items.size() >= 15;
                    callback.onSuccess(new PageLoader.PageResult<>(items, page, hasNext));
                } else {
                    callback.onError(ApiErrorParser.parse(response));
                }
            }

            @Override
            public void onFailure(@NonNull Call<UserSearchResponse> call, @NonNull Throwable t) {
                callback.onError(ApiErrorParser.parseThrowable(t));
            }
        });
    }

    public PageLoader.PageFetcher<UserResource> getFollowingPageFetcher(int userId) {
        return (page, callback) -> apiService.getFollowing(userId, page).enqueue(new Callback<UserSearchResponse>() {
            @Override
            public void onResponse(@NonNull Call<UserSearchResponse> call, @NonNull Response<UserSearchResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<UserResource> items = response.body().data != null ? response.body().data : Collections.emptyList();
                    boolean hasNext = items.size() >= 15;
                    callback.onSuccess(new PageLoader.PageResult<>(items, page, hasNext));
                } else {
                    callback.onError(ApiErrorParser.parse(response));
                }
            }

            @Override
            public void onFailure(@NonNull Call<UserSearchResponse> call, @NonNull Throwable t) {
                callback.onError(ApiErrorParser.parseThrowable(t));
            }
        });
    }

    public LiveData<ApiResult<ProfileResource>> toggleFollow(int userId) {
        MutableLiveData<ApiResult<ProfileResource>> resultLiveData = new MutableLiveData<>(ApiResult.loading());

        apiService.toggleFollow(userId).enqueue(new Callback<ProfileDetailResponse>() {
            @Override
            public void onResponse(@NonNull Call<ProfileDetailResponse> call, @NonNull Response<ProfileDetailResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    resultLiveData.setValue(ApiResult.success(response.body().getProfile()));
                } else {
                    resultLiveData.setValue(ApiResult.error(ApiErrorParser.parse(response)));
                }
            }

            @Override
            public void onFailure(@NonNull Call<ProfileDetailResponse> call, @NonNull Throwable t) {
                resultLiveData.setValue(ApiResult.error(ApiErrorParser.parseThrowable(t)));
            }
        });

        return resultLiveData;
    }

    public LiveData<ApiResult<String>> reportUser(int userId, @NonNull String reason) {
        MutableLiveData<ApiResult<String>> resultLiveData = new MutableLiveData<>(ApiResult.loading());

        apiService.reportUser(userId, new ReportRequest(reason)).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    resultLiveData.setValue(ApiResult.success("Đã gửi báo cáo người dùng. Báo cáo của bạn sẽ được kiểm duyệt."));
                } else {
                    resultLiveData.setValue(ApiResult.error(ApiErrorParser.parse(response)));
                }
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
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
