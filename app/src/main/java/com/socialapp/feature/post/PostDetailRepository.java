package com.socialapp.feature.post;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.socialapp.core.network.ApiErrorParser;
import com.socialapp.core.network.ApiResult;
import com.socialapp.core.network.SocialAppApiService;
import com.socialapp.core.network.dto.PostDetailResponse;
import com.socialapp.core.network.dto.PostResource;
import com.socialapp.core.network.dto.ProfileResource;
import com.socialapp.core.network.dto.ReportRequest;
import com.socialapp.core.network.dto.VoteResponse;

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

        apiService.getPostDetail(postId).enqueue(new Callback<PostDetailResponse>() {
            @Override
            public void onResponse(@NonNull Call<PostDetailResponse> call, @NonNull Response<PostDetailResponse> response) {
                if (response.isSuccessful() && response.body() != null && response.body().data != null) {
                    resultLiveData.setValue(ApiResult.success(response.body().data));
                } else {
                    resultLiveData.setValue(ApiResult.error(ApiErrorParser.parse(response)));
                }
            }

            @Override
            public void onFailure(@NonNull Call<PostDetailResponse> call, @NonNull Throwable t) {
                resultLiveData.setValue(ApiResult.error(ApiErrorParser.parseThrowable(t)));
            }
        });

        return resultLiveData;
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

        apiService.toggleFollow(authorId).enqueue(new Callback<ProfileResource>() {
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
