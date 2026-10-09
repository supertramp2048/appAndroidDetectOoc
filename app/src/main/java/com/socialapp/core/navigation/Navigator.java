package com.socialapp.core.navigation;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.socialapp.feature.auth.AuthActivity;
import com.socialapp.feature.comments.CommentsActivity;
import com.socialapp.feature.post.PostDetailActivity;

public class Navigator {

    public static final String ROUTE_HOME = "home";
    public static final String ROUTE_FACTCHECK = "factcheck";
    public static final String ROUTE_SEARCH = "search";
    public static final String ROUTE_PROFILE = "profile";
    public static final String ROUTE_LOGIN = "login";
    public static final String ROUTE_REGISTER = "register";
    public static final String ROUTE_FORGOT_PASSWORD = "forgot-password";
    public static final String ROUTE_RESET_PASSWORD = "reset-password";
    public static final String ROUTE_SETTINGS = "settings";
    public static final String ROUTE_ABOUT = "about";
    public static final String ROUTE_ADMIN = "admin";

    public static void navigateTo(@NonNull Context context, @NonNull String route) {
        if (route.startsWith("http://") || route.startsWith("https://")) {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(route));
                if (!(context instanceof Activity)) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                }
                context.startActivity(intent);
            } catch (Exception e) {
                Toast.makeText(context, "Không thể mở liên kết", Toast.LENGTH_SHORT).show();
            }
            return;
        }

        if (ROUTE_LOGIN.equalsIgnoreCase(route)) {
            Intent intent = new Intent(context, AuthActivity.class);
            if (!(context instanceof Activity)) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            }
            intent.putExtra(AuthActivity.EXTRA_SCREEN, AuthActivity.SCREEN_LOGIN);
            context.startActivity(intent);
            return;
        }

        if (ROUTE_REGISTER.equalsIgnoreCase(route)) {
            Intent intent = new Intent(context, AuthActivity.class);
            if (!(context instanceof Activity)) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            }
            intent.putExtra(AuthActivity.EXTRA_SCREEN, AuthActivity.SCREEN_REGISTER);
            context.startActivity(intent);
            return;
        }

        if (ROUTE_FORGOT_PASSWORD.equalsIgnoreCase(route)) {
            Intent intent = new Intent(context, AuthActivity.class);
            if (!(context instanceof Activity)) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            }
            intent.putExtra(AuthActivity.EXTRA_SCREEN, AuthActivity.SCREEN_FORGOT_PASSWORD);
            context.startActivity(intent);
            return;
        }

        if (ROUTE_RESET_PASSWORD.equalsIgnoreCase(route)) {
            Intent intent = new Intent(context, AuthActivity.class);
            if (!(context instanceof Activity)) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            }
            intent.putExtra(AuthActivity.EXTRA_SCREEN, AuthActivity.SCREEN_RESET_PASSWORD);
            context.startActivity(intent);
            return;
        }

        if (route.startsWith("post/")) {
            String pathPart = route.substring("post/".length());
            if (pathPart.contains("/comments")) {
                try {
                    String idStr = pathPart.substring(0, pathPart.indexOf("/comments"));
                    int postId = Integer.parseInt(idStr);
                    Intent intent = new Intent(context, CommentsActivity.class);
                    if (!(context instanceof Activity)) {
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    }
                    intent.putExtra(CommentsActivity.EXTRA_POST_ID, postId);
                    context.startActivity(intent);
                    return;
                } catch (Exception ignored) {
                }
            }

            if (pathPart.contains("/edit")) {
                Toast.makeText(context, "Đang mở màn sửa bài: " + route, Toast.LENGTH_SHORT).show();
                return;
            }
            if (pathPart.startsWith("new")) {
                Toast.makeText(context, "Đang mở màn tạo bài mới: " + route, Toast.LENGTH_SHORT).show();
                return;
            }

            try {
                int postId = Integer.parseInt(pathPart);
                Intent intent = new Intent(context, PostDetailActivity.class);
                if (!(context instanceof Activity)) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                }
                intent.putExtra(PostDetailActivity.EXTRA_POST_ID, postId);
                context.startActivity(intent);
                return;
            } catch (Exception e) {
                Toast.makeText(context, "Lỗi mở bài viết: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                return;
            }
        }

        Toast.makeText(context, "Đang mở: " + route, Toast.LENGTH_SHORT).show();
    }

    public static void navigateToPostDetail(@NonNull Context context, int postId) {
        navigateTo(context, "post/" + postId);
    }

    public static void navigateToPostComments(@NonNull Context context, int postId) {
        navigateTo(context, "post/" + postId + "/comments");
    }

    public static void navigateToCreatePost(@NonNull Context context, @Nullable String title,
                                            @Nullable String imageUrl, @Nullable String body) {
        StringBuilder route = new StringBuilder("post/new?");
        if (title != null) route.append("title=").append(Uri.encode(title)).append("&");
        if (imageUrl != null) route.append("imageUrl=").append(Uri.encode(imageUrl)).append("&");
        if (body != null) route.append("body=").append(Uri.encode(body));
        navigateTo(context, route.toString());
    }

    public static void navigateToUserProfile(@NonNull Context context, int userId) {
        navigateTo(context, "profile/" + userId);
    }
}
