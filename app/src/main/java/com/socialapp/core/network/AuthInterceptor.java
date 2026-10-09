package com.socialapp.core.network;

import androidx.annotation.NonNull;

import com.socialapp.core.session.SessionManager;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

public class AuthInterceptor implements Interceptor {

    private final SessionManager sessionManager;

    public AuthInterceptor(SessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    @NonNull
    @Override
    public Response intercept(@NonNull Chain chain) throws IOException {
        Request original = chain.request();
        Request.Builder builder = original.newBuilder()
                .header("Accept", "application/json");

        String token = sessionManager != null ? sessionManager.getAuthToken() : null;
        if (token != null && !token.trim().isEmpty()) {
            builder.header("Authorization", "Bearer " + token);
        }

        Response response = chain.proceed(builder.build());

        if (response.code() == 401 && sessionManager != null) {
            sessionManager.handleGlobalUnauthorized();
        }

        return response;
    }
}
