package com.socialapp.core.network;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class AppError {

    public enum ErrorType {
        UNAUTHORIZED,       // 401
        FORBIDDEN,          // 403
        NOT_FOUND,          // 404
        VALIDATION_ERROR,   // 422
        TOO_MANY_REQUESTS,  // 429
        SERVER_ERROR,       // 5xx
        NETWORK_ERROR,      // IOException / timeout / offline
        UNKNOWN             // Others
    }

    private final int statusCode;
    @NonNull
    private final ErrorType type;
    @NonNull
    private final String message;
    @NonNull
    private final Map<String, List<String>> fieldErrors;

    public AppError(int statusCode, @NonNull ErrorType type, @NonNull String message,
                    @Nullable Map<String, List<String>> fieldErrors) {
        this.statusCode = statusCode;
        this.type = type;
        this.message = message;
        this.fieldErrors = fieldErrors != null ? fieldErrors : Collections.emptyMap();
    }

    public static AppError unauthorized(@Nullable String msg) {
        return new AppError(401, ErrorType.UNAUTHORIZED,
                msg != null ? msg : "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.", null);
    }

    public static AppError forbidden(@Nullable String msg) {
        return new AppError(403, ErrorType.FORBIDDEN,
                msg != null ? msg : "Bạn không có quyền thực hiện thao tác này.", null);
    }

    public static AppError notFound(@Nullable String msg) {
        return new AppError(404, ErrorType.NOT_FOUND,
                msg != null ? msg : "Nội dung này không tồn tại hoặc đã bị gỡ bỏ.", null);
    }

    public static AppError validation(int code, @Nullable String msg, @Nullable Map<String, List<String>> fieldErrors) {
        return new AppError(code, ErrorType.VALIDATION_ERROR,
                msg != null ? msg : "Dữ liệu nhập vào không hợp lệ.", fieldErrors);
    }

    public static AppError tooManyRequests(@Nullable String msg) {
        return new AppError(429, ErrorType.TOO_MANY_REQUESTS,
                msg != null ? msg : "Yêu cầu quá nhiều lần. Vui lòng thử lại sau.", null);
    }

    public static AppError serverError(int code, @Nullable String msg) {
        return new AppError(code, ErrorType.SERVER_ERROR,
                msg != null ? msg : "Lỗi hệ thống máy chủ. Vui lòng thử lại sau.", null);
    }

    public static AppError networkError(@Nullable String msg) {
        return new AppError(0, ErrorType.NETWORK_ERROR,
                msg != null ? msg : "Không thể kết nối mạng. Vui lòng kiểm tra kết nối.", null);
    }

    public static AppError unknown(int code, @Nullable String msg) {
        return new AppError(code, ErrorType.UNKNOWN,
                msg != null ? msg : "Đã xảy ra lỗi không xác định.", null);
    }

    public int getStatusCode() {
        return statusCode;
    }

    @NonNull
    public ErrorType getType() {
        return type;
    }

    @NonNull
    public String getMessage() {
        return message;
    }

    @NonNull
    public Map<String, List<String>> getFieldErrors() {
        return fieldErrors;
    }

    @Nullable
    public String getFirstFieldError(@NonNull String field) {
        List<String> errors = fieldErrors.get(field);
        if (errors != null && !errors.isEmpty()) {
            return errors.get(0);
        }
        return null;
    }
}
