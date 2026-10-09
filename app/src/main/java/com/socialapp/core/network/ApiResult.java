package com.socialapp.core.network;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class ApiResult<T> {

    public enum Status {
        SUCCESS,
        ERROR,
        LOADING
    }

    @NonNull
    public final Status status;

    @Nullable
    public final T data;

    @Nullable
    public final AppError error;

    private ApiResult(@NonNull Status status, @Nullable T data, @Nullable AppError error) {
        this.status = status;
        this.data = data;
        this.error = error;
    }

    public static <T> ApiResult<T> success(@Nullable T data) {
        return new ApiResult<>(Status.SUCCESS, data, null);
    }

    public static <T> ApiResult<T> error(@NonNull AppError error) {
        return new ApiResult<>(Status.ERROR, null, error);
    }

    public static <T> ApiResult<T> loading() {
        return new ApiResult<>(Status.LOADING, null, null);
    }

    public boolean isSuccess() {
        return status == Status.SUCCESS;
    }

    public boolean isError() {
        return status == Status.ERROR;
    }

    public boolean isLoading() {
        return status == Status.LOADING;
    }
}
