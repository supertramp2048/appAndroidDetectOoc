package com.socialapp.core.prefetch;

import androidx.annotation.NonNull;

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
        switch (screenId) {
            case FEED_NEWEST:
            case FEED_HOT:
            case FEED_FOLLOWING:
            case SEARCH:
                return FakeApi.getSamplePosts();

            case POST_DETAIL:
                List<PostResource> posts = FakeApi.getSamplePosts();
                return !posts.isEmpty() ? posts.get(0) : new PostResource();

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

