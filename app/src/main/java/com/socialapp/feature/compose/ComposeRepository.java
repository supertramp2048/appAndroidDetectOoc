package com.socialapp.feature.compose;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.socialapp.core.network.ApiErrorParser;
import com.socialapp.core.network.ApiResult;
import com.socialapp.core.network.AppError;
import com.socialapp.core.network.SocialAppApiService;
import com.socialapp.core.network.dto.CategoryListResponse;
import com.socialapp.core.network.dto.CategoryResource;
import com.socialapp.core.network.dto.PostDetailResponse;
import com.socialapp.core.network.dto.PostResource;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ComposeRepository {

    private final SocialAppApiService apiService;

    public ComposeRepository(@NonNull SocialAppApiService apiService) {
        this.apiService = apiService;
    }

    public LiveData<ApiResult<List<CategoryResource>>> getCategories() {
        MutableLiveData<ApiResult<List<CategoryResource>>> resultLiveData = new MutableLiveData<>(ApiResult.loading());

        apiService.getCategories().enqueue(new Callback<CategoryListResponse>() {
            @Override
            public void onResponse(@NonNull Call<CategoryListResponse> call, @NonNull Response<CategoryListResponse> response) {
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

    public LiveData<ApiResult<PostResource>> createPost(@NonNull String title,
                                                        @NonNull String contentHtml,
                                                        int categoryId,
                                                        @Nullable String thumbnailUrl) {
        MutableLiveData<ApiResult<PostResource>> resultLiveData = new MutableLiveData<>(ApiResult.loading());

        Map<String, Object> body = new HashMap<>();
        body.put("title", title);
        body.put("content_html", contentHtml);
        body.put("category_id", categoryId);
        if (thumbnailUrl != null && !thumbnailUrl.trim().isEmpty()) {
            body.put("thumbnail_url", thumbnailUrl);
        }

        apiService.createPost(body).enqueue(new Callback<PostDetailResponse>() {
            @Override
            public void onResponse(@NonNull Call<PostDetailResponse> call, @NonNull Response<PostDetailResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    if (response.body().data != null) {
                        resultLiveData.setValue(ApiResult.success(response.body().data));
                    } else if (response.body().message != null && !response.body().message.trim().isEmpty()) {
                        resultLiveData.setValue(ApiResult.error(AppError.unknown(200, response.body().message)));
                    } else {
                        resultLiveData.setValue(ApiResult.error(ApiErrorParser.parse(response)));
                    }
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

    public LiveData<ApiResult<PostResource>> updatePost(int postId,
                                                        @NonNull String title,
                                                        @NonNull String contentHtml,
                                                        int categoryId,
                                                        @Nullable String thumbnailUrl) {
        MutableLiveData<ApiResult<PostResource>> resultLiveData = new MutableLiveData<>(ApiResult.loading());

        Map<String, Object> body = new HashMap<>();
        body.put("title", title);
        body.put("content_html", contentHtml);
        body.put("category_id", categoryId);
        if (thumbnailUrl != null && !thumbnailUrl.trim().isEmpty()) {
            body.put("thumbnail_url", thumbnailUrl);
        }

        apiService.updatePost(postId, body).enqueue(new Callback<PostDetailResponse>() {
            @Override
            public void onResponse(@NonNull Call<PostDetailResponse> call, @NonNull Response<PostDetailResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    if (response.body().data != null) {
                        resultLiveData.setValue(ApiResult.success(response.body().data));
                    } else if (response.body().message != null && !response.body().message.trim().isEmpty()) {
                        resultLiveData.setValue(ApiResult.error(AppError.unknown(200, response.body().message)));
                    } else {
                        resultLiveData.setValue(ApiResult.error(ApiErrorParser.parse(response)));
                    }
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

    public LiveData<ApiResult<Void>> deletePost(int postId) {
        MutableLiveData<ApiResult<Void>> resultLiveData = new MutableLiveData<>(ApiResult.loading());

        apiService.deletePost(postId).enqueue(new Callback<ResponseBody>() {
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

    public LiveData<ApiResult<String>> uploadImage(@NonNull File file) {
        MutableLiveData<ApiResult<String>> resultLiveData = new MutableLiveData<>(ApiResult.loading());

        RequestBody requestFile = RequestBody.create(MediaType.parse("image/*"), file);
        MultipartBody.Part body = MultipartBody.Part.createFormData("file", file.getName(), requestFile);

        apiService.uploadImage(body).enqueue(new Callback<Map<String, String>>() {
            @Override
            public void onResponse(@NonNull Call<Map<String, String>> call, @NonNull Response<Map<String, String>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    Map<String, String> map = response.body();
                    String location = map.get("location");
                    if (location == null) {
                        location = map.get("url");
                    }
                    if (location != null && !location.trim().isEmpty()) {
                        resultLiveData.setValue(ApiResult.success(location));
                    } else {
                        resultLiveData.setValue(ApiResult.error(ApiErrorParser.parse(response)));
                    }
                } else {
                    resultLiveData.setValue(ApiResult.error(ApiErrorParser.parse(response)));
                }
            }

            @Override
            public void onFailure(@NonNull Call<Map<String, String>> call, @NonNull Throwable t) {
                resultLiveData.setValue(ApiResult.error(ApiErrorParser.parseThrowable(t)));
            }
        });

        return resultLiveData;
    }
}
