package com.socialapp.feature.post;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.socialapp.core.network.ApiErrorParser;
import com.socialapp.core.network.ApiResult;
import com.socialapp.core.network.SocialAppApiService;
import com.socialapp.core.network.dto.PostDetailResponse;
import com.socialapp.core.network.dto.PostResource;
import com.socialapp.core.network.dto.ProfileDetailResponse;
import com.socialapp.core.network.dto.ProfileResource;
import com.socialapp.core.network.dto.ReportRequest;
import com.socialapp.core.network.dto.VoteResponse;
import com.socialapp.core.prefetch.HitType;
import com.socialapp.core.prefetch.Repository;
import com.socialapp.core.prefetch.ScreenId;
import com.socialapp.core.prefetch.ScreenLoadCallback;

import com.socialapp.core.network.sim.ScreenPayloadConfig;
import com.socialapp.core.prefetch.PrefetchManager;
import com.socialapp.core.prefetch.PrefetchResult;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class PostDetailRepository {

    private final SocialAppApiService apiService;

    public PostDetailRepository(@NonNull SocialAppApiService apiService) {
        this.apiService = apiService;
    }

    public LiveData<ApiResult<PostResource>> getPostDetail(int postId) {
        MutableLiveData<ApiResult<PostResource>> resultLiveData = new MutableLiveData<>(ApiResult.loading());

        Bundle params = new Bundle();
        params.putInt("postId", postId);

        Repository.load(ScreenId.POST_DETAIL, params, new Repository.RealDataLoader<PostResource>() {
            @Override
            public void execute(@NonNull ScreenLoadCallback<PostResource> callback) {
                String cacheKey = "post_" + postId;
                PrefetchManager prefetchManager = PrefetchManager.getInstance();

                // 1. Try acquire from Prefetch Engine (FULL_HIT, LATE_HIT, or MISS)
                prefetchManager.acquire(
                        ScreenPayloadConfig.SCREEN_POST_DETAIL,
                        cacheKey,
                        () -> {
                            try {
                                Response<PostDetailResponse> resp = apiService.getPostDetail(postId).execute();
                                if (resp.isSuccessful() && resp.body() != null && resp.body().data != null) {
                                    return resp.body().data;
                                }
                            } catch (Throwable ignored) {}
                            return null;
                        }
                ).thenAccept(result -> {
                    PostResource post = result.getData();
                    if (post != null) {
                        HitType hitType = HitType.valueOf(result.getHitType().name());
                        callback.onLoaded(hitType, result.getWaitMs(), post);
                    } else {
                        fetchViaRetrofit(postId, callback);
                    }
                }).exceptionally(ex -> {
                    fetchViaRetrofit(postId, callback);
                    return null;
                });
            }
        }, new ScreenLoadCallback<PostResource>() {
            @Override
            public void onLoaded(@NonNull HitType hitType, long latencyMs, @Nullable PostResource data) {
                if (data != null) {
                    resultLiveData.postValue(ApiResult.success(data));
                } else {
                    resultLiveData.postValue(ApiResult.error(com.socialapp.core.network.AppError.unknown(0, "Không có dữ liệu bài viết")));
                }
            }

            @Override
            public void onError(@NonNull Throwable error) {
                resultLiveData.postValue(ApiResult.error(ApiErrorParser.parseThrowable(error)));
            }
        });

        return resultLiveData;
    }

    private void fetchViaRetrofit(int postId, @NonNull ScreenLoadCallback<PostResource> callback) {
        apiService.getPostDetail(postId).enqueue(new Callback<PostDetailResponse>() {
            @Override
            public void onResponse(@NonNull Call<PostDetailResponse> call, @NonNull Response<PostDetailResponse> response) {
                if (response.isSuccessful() && response.body() != null && response.body().data != null) {
                    callback.onLoaded(HitType.MISS, 0, response.body().data);
                } else {
                    callback.onError(new Exception(ApiErrorParser.parse(response).getMessage()));
                }
            }

            @Override
            public void onFailure(@NonNull Call<PostDetailResponse> call, @NonNull Throwable t) {
                callback.onError(t);
            }
        });
    }

    public LiveData<ApiResult<VoteResponse>> upvotePost(int postId) {
        MutableLiveData<ApiResult<VoteResponse>> resultLiveData = new MutableLiveData<>(ApiResult.loading());

        apiService.upvotePost(postId).enqueue(new Callback<VoteResponse>() {
            @Override
            public void onResponse(@NonNull Call<VoteResponse> call, @NonNull Response<VoteResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    resultLiveData.setValue(ApiResult.success(response.body()));
                } else {
                    resultLiveData.setValue(ApiResult.error(ApiErrorParser.parse(response)));
                }
            }

            @Override
            public void onFailure(@NonNull Call<VoteResponse> call, @NonNull Throwable t) {
                resultLiveData.setValue(ApiResult.error(ApiErrorParser.parseThrowable(t)));
            }
        });

        return resultLiveData;
    }

    public LiveData<ApiResult<VoteResponse>> downvotePost(int postId) {
        MutableLiveData<ApiResult<VoteResponse>> resultLiveData = new MutableLiveData<>(ApiResult.loading());

        apiService.downvotePost(postId).enqueue(new Callback<VoteResponse>() {
            @Override
            public void onResponse(@NonNull Call<VoteResponse> call, @NonNull Response<VoteResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    resultLiveData.setValue(ApiResult.success(response.body()));
                } else {
                    resultLiveData.setValue(ApiResult.error(ApiErrorParser.parse(response)));
                }
            }

            @Override
            public void onFailure(@NonNull Call<VoteResponse> call, @NonNull Throwable t) {
                resultLiveData.setValue(ApiResult.error(ApiErrorParser.parseThrowable(t)));
            }
        });

        return resultLiveData;
    }

    public LiveData<ApiResult<ProfileResource>> toggleFollowAuthor(int authorId) {
        MutableLiveData<ApiResult<ProfileResource>> resultLiveData = new MutableLiveData<>(ApiResult.loading());

        apiService.toggleFollow(authorId).enqueue(new Callback<ProfileDetailResponse>() {
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

    public LiveData<ApiResult<String>> reportPost(int postId, @NonNull String reason) {
        MutableLiveData<ApiResult<String>> resultLiveData = new MutableLiveData<>(ApiResult.loading());

        apiService.reportPost(postId, new ReportRequest(reason)).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    resultLiveData.setValue(ApiResult.success("Đã gửi báo cáo bài viết. Báo cáo của bạn sẽ được kiểm duyệt."));
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
}
