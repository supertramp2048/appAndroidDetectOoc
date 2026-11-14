package com.socialapp.core.prefetch;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.socialapp.core.mock.FakeApi;
import com.socialapp.core.network.dto.CommentResource;
import com.socialapp.core.network.dto.PostResource;
import com.socialapp.core.network.dto.ProfileResource;
import com.socialapp.core.network.dto.UserResource;

import java.util.ArrayList;
import java.util.List;

/**
 * Cung cấp dữ liệu mẫu chuẩn cho từng màn hình phục vụ chế độ chạy mô phỏng / thí nghiệm (SimulationMode).
 */
public class FakeDataSource {

    @NonNull
    public static Object getSampleDataForScreen(@NonNull ScreenId screenId) {
        return getSampleDataForScreen(screenId, null);
    }

    @NonNull
    public static Object getSampleDataForScreen(@NonNull ScreenId screenId, @Nullable Bundle params) {
        switch (screenId) {
            case FEED_NEWEST:
            case FEED_HOT:
            case FEED_FOLLOWING:
            case SEARCH:
                return FakeApi.getSamplePosts();

            case POST_DETAIL:
                int postId = params != null ? params.getInt("postId", 1) : 1;
                List<PostResource> posts = FakeApi.getSamplePosts();
                for (PostResource p : posts) {
                    if (p.id == postId) {
                        return p;
                    }
                }
                PostResource dynamicPost = new PostResource();
                dynamicPost.id = postId;
                dynamicPost.title = "Bài viết thử nghiệm số " + postId + " - Cập nhật tin tức mới nhất trong ngày";
                dynamicPost.contentHtml = "<p>Nội dung chi tiết bài viết số " + postId + " từ server giả lập.</p>";
                dynamicPost.voteScore = postId * 5;
                dynamicPost.userVote = 0;
                dynamicPost.commentsCount = postId * 2;
                dynamicPost.createdAt = "2026-10-09 10:00:00";
                if (!posts.isEmpty()) {
                    dynamicPost.author = posts.get(0).author;
                    dynamicPost.category = posts.get(0).category;
                }
                dynamicPost.sourceName = "Báo Mới";
                return dynamicPost;

            case COMMENTS:
                List<CommentResource> comments = new ArrayList<>();
                for (int i = 1; i <= 5; i++) {
                    CommentResource c = new CommentResource();
                    c.id = i;
                    c.content = "Bình luận giả lập số " + i + " phục vụ kiểm thử prefetch.";
                    UserResource u = new UserResource();
                    u.id = i + 100;
                    u.name = "Thành viên " + i;
                    c.author = u;
                    comments.add(c);
                }
                return comments;

            case PROFILE:
                ProfileResource profile = new ProfileResource();
                UserResource user = new UserResource();
                user.id = 1;
                user.name = "Người dùng Thử nghiệm";
                user.email = "demo@socialapp.vn";
                user.role = "user";
                profile.user = user;
                profile.postsCount = 15;
                profile.followersCount = 280;
                profile.followingCount = 95;
                return profile;

            case FACT_CHECK:
            case SETTINGS:
            default:
                return "SUCCESS_DATA_" + screenId.getId();
        }
    }
}

