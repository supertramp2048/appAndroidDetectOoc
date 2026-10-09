package com.socialapp.feature.comments;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.socialapp.core.network.ApiErrorParser;
import com.socialapp.core.network.ApiResult;
import com.socialapp.core.network.SocialAppApiService;
import com.socialapp.core.network.dto.CommentListResponse;
import com.socialapp.core.network.dto.CommentResource;
import com.socialapp.core.network.dto.CommentSingleResponse;
import com.socialapp.core.network.dto.ReportRequest;
import com.socialapp.core.pagination.PageLoader;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class CommentsRepository {

    private final SocialAppApiService apiService;

    public CommentsRepository(@NonNull SocialAppApiService apiService) {
        this.apiService = apiService;
    }

    public PageLoader.PageFetcher<CommentResource> createRootCommentsPageFetcher(int postId) {
        return (page, callback) -> apiService.getComments(postId, page, 10).enqueue(new Callback<CommentListResponse>() {
            @Override
            public void onResponse(@NonNull Call<CommentListResponse> call, @NonNull Response<CommentListResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    CommentListResponse body = response.body();
                    List<CommentResource> items = body.data != null ? body.data : Collections.emptyList();
                    boolean hasNext = body.links != null && body.links.hasNext();
                    int currentPage = (body.meta != null && body.meta.currentPage > 0) ? body.meta.currentPage : page;
                    callback.onSuccess(new PageLoader.PageResult<>(items, currentPage, hasNext));
                } else {
                    callback.onError(ApiErrorParser.parse(response));
                }
            }

            @Override
            public void onFailure(@NonNull Call<CommentListResponse> call, @NonNull Throwable t) {
                callback.onError(ApiErrorParser.parseThrowable(t));
            }
        });
    }

    public PageLoader.PageFetcher<CommentResource> createRepliesPageFetcher(int commentId) {
        return (page, callback) -> apiService.getCommentReplies(commentId, page).enqueue(new Callback<CommentListResponse>() {
            @Override
            public void onResponse(@NonNull Call<CommentListResponse> call, @NonNull Response<CommentListResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    CommentListResponse body = response.body();
                    List<CommentResource> items = body.data != null ? body.data : Collections.emptyList();
                    boolean hasNext = body.links != null && body.links.hasNext();
                    int currentPage = (body.meta != null && body.meta.currentPage > 0) ? body.meta.currentPage : page;
                    callback.onSuccess(new PageLoader.PageResult<>(items, currentPage, hasNext));
                } else {
                    callback.onError(ApiErrorParser.parse(response));
                }
            }

            @Override
            public void onFailure(@NonNull Call<CommentListResponse> call, @NonNull Throwable t) {
                callback.onError(ApiErrorParser.parseThrowable(t));
            }
        });
    }

    public LiveData<ApiResult<CommentResource>> createComment(int postId, @Nullable Integer parentId, @NonNull String content) {
        MutableLiveData<ApiResult<CommentResource>> resultLiveData = new MutableLiveData<>(ApiResult.loading());

        Map<String, Object> body = new HashMap<>();
        body.put("post_id", postId);
        body.put("content", content);
        if (parentId != null) {
            body.put("parent_id", parentId);
        }

        apiService.createComment(body).enqueue(new Callback<CommentSingleResponse>() {
            @Override
            public void onResponse(@NonNull Call<CommentSingleResponse> call, @NonNull Response<CommentSingleResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    CommentResource comment = response.body().getComment();
                    if (comment != null) {
                        resultLiveData.setValue(ApiResult.success(comment));
                    } else {
                        resultLiveData.setValue(ApiResult.error(ApiErrorParser.parse(response)));
                    }
                } else {
                    resultLiveData.setValue(ApiResult.error(ApiErrorParser.parse(response)));
                }
            }

            @Override
            public void onFailure(@NonNull Call<CommentSingleResponse> call, @NonNull Throwable t) {
                resultLiveData.setValue(ApiResult.error(ApiErrorParser.parseThrowable(t)));
            }
        });

        return resultLiveData;
    }

    public LiveData<ApiResult<CommentResource>> updateComment(int commentId, @NonNull String content) {
        MutableLiveData<ApiResult<CommentResource>> resultLiveData = new MutableLiveData<>(ApiResult.loading());

        Map<String, String> body = new HashMap<>();
        body.put("content", content);

        apiService.updateComment(commentId, body).enqueue(new Callback<CommentSingleResponse>() {
            @Override
            public void onResponse(@NonNull Call<CommentSingleResponse> call, @NonNull Response<CommentSingleResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    CommentResource comment = response.body().getComment();
                    if (comment != null) {
                        resultLiveData.setValue(ApiResult.success(comment));
                    } else {
                        resultLiveData.setValue(ApiResult.error(ApiErrorParser.parse(response)));
                    }
                } else {
                    resultLiveData.setValue(ApiResult.error(ApiErrorParser.parse(response)));
                }
            }

            @Override
            public void onFailure(@NonNull Call<CommentSingleResponse> call, @NonNull Throwable t) {
                resultLiveData.setValue(ApiResult.error(ApiErrorParser.parseThrowable(t)));
            }
        });

        return resultLiveData;
    }

    public LiveData<ApiResult<Void>> deleteComment(int commentId) {
        MutableLiveData<ApiResult<Void>> resultLiveData = new MutableLiveData<>(ApiResult.loading());

        apiService.deleteComment(commentId).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    resultLiveData.setValue(ApiResult.success(null));
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

    public LiveData<ApiResult<String>> reportComment(int commentId, @NonNull String reason) {
        MutableLiveData<ApiResult<String>> resultLiveData = new MutableLiveData<>(ApiResult.loading());

        apiService.reportComment(commentId, new ReportRequest(reason)).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    resultLiveData.setValue(ApiResult.success("Đã gửi báo cáo bình luận."));
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
