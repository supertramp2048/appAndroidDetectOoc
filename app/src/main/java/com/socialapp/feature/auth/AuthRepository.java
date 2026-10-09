package com.socialapp.feature.auth;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.socialapp.core.network.ApiErrorParser;
import com.socialapp.core.network.ApiResult;
import com.socialapp.core.network.SocialAppApiService;
import com.socialapp.core.network.dto.AuthResponse;
import com.socialapp.core.network.dto.LoginRequest;
import com.socialapp.core.network.dto.RegisterRequest;
import com.socialapp.core.network.dto.UserResource;
import com.socialapp.core.session.SessionManager;

import java.util.HashMap;
import java.util.Map;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AuthRepository {

    private final SocialAppApiService apiService;
    private final SessionManager sessionManager;

    public AuthRepository(@NonNull SocialAppApiService apiService, @NonNull SessionManager sessionManager) {
        this.apiService = apiService;
        this.sessionManager = sessionManager;
    }

    public LiveData<ApiResult<UserResource>> login(@NonNull String email, @NonNull String password, @Nullable Boolean remember) {
        MutableLiveData<ApiResult<UserResource>> resultLiveData = new MutableLiveData<>(ApiResult.loading());

        apiService.login(new LoginRequest(email, password, remember)).enqueue(new Callback<AuthResponse>() {
            @Override
            public void onResponse(@NonNull Call<AuthResponse> call, @NonNull Response<AuthResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    AuthResponse authResponse = response.body();
                    String token = authResponse.token;
                    UserResource user = authResponse.user != null ? authResponse.user : new UserResource();
                    user.email = email; // Populate email from request if missing in AuthResponse

                    sessionManager.saveSession(token, user);

                    // Fetch full user details from /api/user in background
                    apiService.getCurrentUser().enqueue(new Callback<UserResource>() {
                        @Override
                        public void onResponse(@NonNull Call<UserResource> callUser, @NonNull Response<UserResource> responseUser) {
                            if (responseUser.isSuccessful() && responseUser.body() != null) {
                                UserResource fullUser = responseUser.body();
                                if (fullUser.email == null || fullUser.email.isEmpty()) {
                                    fullUser.email = email;
                                }
                                sessionManager.updateUser(fullUser);
                                resultLiveData.setValue(ApiResult.success(fullUser));
                            } else {
                                resultLiveData.setValue(ApiResult.success(user));
                            }
                        }

                        @Override
                        public void onFailure(@NonNull Call<UserResource> callUser, @NonNull Throwable t) {
                            resultLiveData.setValue(ApiResult.success(user));
                        }
                    });
                } else {
                    resultLiveData.setValue(ApiResult.error(ApiErrorParser.parse(response)));
                }
            }

            @Override
            public void onFailure(@NonNull Call<AuthResponse> call, @NonNull Throwable t) {
                resultLiveData.setValue(ApiResult.error(ApiErrorParser.parseThrowable(t)));
            }
        });

        return resultLiveData;
    }

    public LiveData<ApiResult<UserResource>> register(@NonNull String name, @NonNull String email,
                                                      @NonNull String password, @NonNull String passwordConfirmation) {
        MutableLiveData<ApiResult<UserResource>> resultLiveData = new MutableLiveData<>(ApiResult.loading());

        apiService.register(new RegisterRequest(name, email, password, passwordConfirmation))
                .enqueue(new Callback<AuthResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<AuthResponse> call, @NonNull Response<AuthResponse> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            AuthResponse authResponse = response.body();
                            String token = authResponse.token;
                            UserResource user = authResponse.user != null ? authResponse.user : new UserResource();
                            if (user.email == null) user.email = email;

                            sessionManager.saveSession(token, user);
                            resultLiveData.setValue(ApiResult.success(user));
                        } else {
                            resultLiveData.setValue(ApiResult.error(ApiErrorParser.parse(response)));
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<AuthResponse> call, @NonNull Throwable t) {
                        resultLiveData.setValue(ApiResult.error(ApiErrorParser.parseThrowable(t)));
                    }
                });

        return resultLiveData;
    }

    public LiveData<ApiResult<String>> forgotPassword(@NonNull String email) {
        MutableLiveData<ApiResult<String>> resultLiveData = new MutableLiveData<>(ApiResult.loading());

        Map<String, String> body = new HashMap<>();
        body.put("email", email);

        apiService.forgotPassword(body).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    resultLiveData.setValue(ApiResult.success("Đã gửi hướng dẫn đặt lại mật khẩu đến email của bạn."));
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

    public LiveData<ApiResult<String>> resetPassword(@NonNull String token, @NonNull String email,
                                                     @NonNull String password, @NonNull String passwordConfirmation) {
        MutableLiveData<ApiResult<String>> resultLiveData = new MutableLiveData<>(ApiResult.loading());

        Map<String, String> body = new HashMap<>();
        body.put("token", token);
        body.put("email", email);
        body.put("password", password);
        body.put("password_confirmation", passwordConfirmation);

        apiService.resetPassword(body).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    resultLiveData.setValue(ApiResult.success("Đặt lại mật khẩu thành công. Bạn có thể đăng nhập ngay."));
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

    public LiveData<ApiResult<String>> sendEmailVerification() {
        MutableLiveData<ApiResult<String>> resultLiveData = new MutableLiveData<>(ApiResult.loading());

        apiService.sendEmailVerification().enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    resultLiveData.setValue(ApiResult.success("Đã gửi lại thư xác thực email. Vui lòng kiểm tra hòm thư."));
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

    public LiveData<ApiResult<UserResource>> refreshUser() {
        MutableLiveData<ApiResult<UserResource>> resultLiveData = new MutableLiveData<>(ApiResult.loading());

        apiService.getCurrentUser().enqueue(new Callback<UserResource>() {
            @Override
            public void onResponse(@NonNull Call<UserResource> call, @NonNull Response<UserResource> response) {
                if (response.isSuccessful() && response.body() != null) {
                    UserResource user = response.body();
                    sessionManager.updateUser(user);
                    resultLiveData.setValue(ApiResult.success(user));
                } else {
                    resultLiveData.setValue(ApiResult.error(ApiErrorParser.parse(response)));
                }
            }

            @Override
            public void onFailure(@NonNull Call<UserResource> call, @NonNull Throwable t) {
                resultLiveData.setValue(ApiResult.error(ApiErrorParser.parseThrowable(t)));
            }
        });

        return resultLiveData;
    }
}
