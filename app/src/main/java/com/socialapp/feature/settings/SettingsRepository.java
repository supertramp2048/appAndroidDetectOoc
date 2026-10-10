package com.socialapp.feature.settings;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.socialapp.core.network.ApiErrorParser;
import com.socialapp.core.network.ApiResult;
import com.socialapp.core.network.SocialAppApiService;
import com.socialapp.core.network.dto.UserDetailResponse;
import com.socialapp.core.network.dto.UserResource;
import com.socialapp.core.session.SessionManager;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SettingsRepository {

    private final SocialAppApiService apiService;
    private final SessionManager sessionManager;

    public SettingsRepository(@NonNull SocialAppApiService apiService, @NonNull SessionManager sessionManager) {
        this.apiService = apiService;
        this.sessionManager = sessionManager;
    }

    public LiveData<ApiResult<UserResource>> updateName(@NonNull String name) {
        MutableLiveData<ApiResult<UserResource>> resultLiveData = new MutableLiveData<>(ApiResult.loading());

        Map<String, String> body = new HashMap<>();
        body.put("name", name);

        apiService.updateProfileDetails(body).enqueue(new Callback<UserDetailResponse>() {
            @Override
            public void onResponse(@NonNull Call<UserDetailResponse> call, @NonNull Response<UserDetailResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    UserResource user = response.body().getUser();
                    sessionManager.updateUser(user);
                    resultLiveData.setValue(ApiResult.success(user));
                } else {
                    resultLiveData.setValue(ApiResult.error(ApiErrorParser.parse(response)));
                }
            }

            @Override
            public void onFailure(@NonNull Call<UserDetailResponse> call, @NonNull Throwable t) {
                resultLiveData.setValue(ApiResult.error(ApiErrorParser.parseThrowable(t)));
            }
        });

        return resultLiveData;
    }

    public LiveData<ApiResult<UserResource>> updateAvatar(@NonNull File avatarFile) {
        MutableLiveData<ApiResult<UserResource>> resultLiveData = new MutableLiveData<>(ApiResult.loading());

        RequestBody requestFile = RequestBody.create(MediaType.parse("image/*"), avatarFile);
        MultipartBody.Part body = MultipartBody.Part.createFormData("avatar", avatarFile.getName(), requestFile);

        apiService.updateAvatar(body).enqueue(new Callback<UserDetailResponse>() {
            @Override
            public void onResponse(@NonNull Call<UserDetailResponse> call, @NonNull Response<UserDetailResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    UserResource user = response.body().getUser();
                    sessionManager.updateUser(user);
                    resultLiveData.setValue(ApiResult.success(user));
                } else {
                    resultLiveData.setValue(ApiResult.error(ApiErrorParser.parse(response)));
                }
            }

            @Override
            public void onFailure(@NonNull Call<UserDetailResponse> call, @NonNull Throwable t) {
                resultLiveData.setValue(ApiResult.error(ApiErrorParser.parseThrowable(t)));
            }
        });

        return resultLiveData;
    }

    public LiveData<ApiResult<UserResource>> updateCoverPhoto(@NonNull File coverFile) {
        MutableLiveData<ApiResult<UserResource>> resultLiveData = new MutableLiveData<>(ApiResult.loading());

        RequestBody requestFile = RequestBody.create(MediaType.parse("image/*"), coverFile);
        MultipartBody.Part body = MultipartBody.Part.createFormData("cover", coverFile.getName(), requestFile);

        apiService.updateCoverPhoto(body).enqueue(new Callback<UserDetailResponse>() {
            @Override
            public void onResponse(@NonNull Call<UserDetailResponse> call, @NonNull Response<UserDetailResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    UserResource user = response.body().getUser();
                    sessionManager.updateUser(user);
                    resultLiveData.setValue(ApiResult.success(user));
                } else {
                    resultLiveData.setValue(ApiResult.error(ApiErrorParser.parse(response)));
                }
            }

            @Override
            public void onFailure(@NonNull Call<UserDetailResponse> call, @NonNull Throwable t) {
                resultLiveData.setValue(ApiResult.error(ApiErrorParser.parseThrowable(t)));
            }
        });

        return resultLiveData;
    }

    public LiveData<ApiResult<String>> updatePassword(@NonNull String currentPassword,
                                                     @NonNull String newPassword,
                                                     @NonNull String confirmPassword) {
        MutableLiveData<ApiResult<String>> resultLiveData = new MutableLiveData<>(ApiResult.loading());

        Map<String, String> body = new HashMap<>();
        body.put("current_password", currentPassword);
        body.put("password", newPassword);
        body.put("password_confirmation", confirmPassword);

        apiService.updatePassword(body).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    resultLiveData.setValue(ApiResult.success("Đổi mật khẩu thành công."));
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

    public LiveData<ApiResult<Void>> deleteAccount(@NonNull String password) {
        MutableLiveData<ApiResult<Void>> resultLiveData = new MutableLiveData<>(ApiResult.loading());

        Map<String, String> body = new HashMap<>();
        body.put("password", password);

        apiService.deleteAccount(body).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    sessionManager.logout();
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
}
