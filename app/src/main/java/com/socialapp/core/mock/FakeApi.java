package com.socialapp.core.mock;

import androidx.annotation.NonNull;

import com.socialapp.core.network.dto.CategoryResource;
import com.socialapp.core.network.dto.PostResource;
import com.socialapp.core.network.dto.UserResource;

import java.util.ArrayList;
import java.util.List;

public class FakeApi {

    @NonNull
    public static List<CategoryResource> getSampleCategories() {
        List<CategoryResource> categories = new ArrayList<>();

        CategoryResource c1 = new CategoryResource();
        c1.id = 1;
        c1.name = "Thời sự";
        c1.slug = "thoi-su";
        c1.description = "Tin tức thời sự trong nước và quốc tế";
        categories.add(c1);

        CategoryResource c2 = new CategoryResource();
        c2.id = 2;
        c2.name = "Công nghệ";
        c2.slug = "cong-nghe";
        c2.description = "Công nghệ, trí tuệ nhân tạo, thiết bị số";
        categories.add(c2);

        CategoryResource c3 = new CategoryResource();
        c3.id = 3;
        c3.name = "Xã hội";
        c3.slug = "xa-hoi";
        c3.description = "Tin tức đời sống xã hội";
        categories.add(c3);

        return categories;
    }

    @NonNull
    public static List<PostResource> getSamplePosts() {
        List<PostResource> posts = new ArrayList<>();

        UserResource author = new UserResource();
        author.id = 101;
        author.name = "Ban Biên Tập";
        author.role = "admin";

        CategoryResource category = getSampleCategories().get(0);

        for (int i = 1; i <= 10; i++) {
            PostResource post = new PostResource();
            post.id = i;
            post.title = "Bài viết thử nghiệm số " + i + " - Cập nhật tin tức mới nhất trong ngày";
            post.contentHtml = "<p>Nội dung chi tiết bài viết số " + i + " từ server giả lập.</p>";
            post.voteScore = i * 5;
            post.userVote = 0;
            post.commentsCount = i * 2;
            post.createdAt = "2026-10-09 10:00:00";
            post.author = author;
            post.category = category;
            post.sourceName = "Báo Mới";
            posts.add(post);
        }

        return posts;
    }
}
