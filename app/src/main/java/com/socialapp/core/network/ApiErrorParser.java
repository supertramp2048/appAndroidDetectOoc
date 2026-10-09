package com.socialapp.core.network;

import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.socialapp.core.network.dto.ErrorResponse;

import java.io.IOException;

import retrofit2.Response;

public final class ApiErrorParser {

    private static final Gson GSON = new Gson();

    private ApiErrorParser() {
    }

    @NonNull
    public static AppError parse(@NonNull Response<?> response) {
        int code = response.code();
        String errorJson = null;
        if (response.errorBody() != null) {
            try {
                errorJson = response.errorBody().string();
            } catch (IOException ignored) {
            }
        }

        ErrorResponse errorResponse = null;
        if (errorJson != null && !errorJson.trim().isEmpty()) {
            try {
                errorResponse = GSON.fromJson(errorJson, ErrorResponse.class);
            } catch (Exception ignored) {
            }
        }

        String serverMessage = errorResponse != null ? errorResponse.message : null;

        switch (code) {
            case 401:
                return AppError.unauthorized(serverMessage);
            case 403:
                return AppError.forbidden(serverMessage);
            case 404:
                return AppError.notFound(serverMessage);
            case 422:
                return AppError.validation(code, serverMessage,
                        errorResponse != null ? errorResponse.errors : null);
            case 429:
                return AppError.tooManyRequests(serverMessage);
            default:
                if (code >= 500) {
                    return AppError.serverError(code, serverMessage);
                }
                return AppError.unknown(code, serverMessage);
        }
    }

    @NonNull
    public static AppError parseThrowable(@NonNull Throwable throwable) {
        if (throwable instanceof IOException) {
            return AppError.networkError("Không thể kết nối mạng. Vui lòng kiểm tra lại kết nối.");
        }
        return AppError.unknown(0, throwable.getMessage());
    }
}
