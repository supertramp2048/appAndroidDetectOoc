package com.socialapp.core.network;

import com.socialapp.core.network.dto.AdvertisementResource;
import com.socialapp.core.network.dto.AuthResponse;
import com.socialapp.core.network.dto.CategoryListResponse;
import com.socialapp.core.network.dto.CategoryResource;
import com.socialapp.core.network.dto.CommentListResponse;
import com.socialapp.core.network.dto.CommentSingleResponse;
import com.socialapp.core.network.dto.LoginRequest;
import com.socialapp.core.network.dto.PostDetailResponse;
import com.socialapp.core.network.dto.PostListResponse;
import com.socialapp.core.network.dto.ProfileDetailResponse;
import com.socialapp.core.network.dto.ProfileResource;
import com.socialapp.core.network.dto.RegisterRequest;
import com.socialapp.core.network.dto.ReportRequest;
import com.socialapp.core.network.dto.SettingResource;
import com.socialapp.core.network.dto.UserDetailResponse;
import com.socialapp.core.network.dto.UserResource;
import com.socialapp.core.network.dto.UserSearchResponse;
import com.socialapp.core.network.dto.VoteResponse;

import java.util.List;
import java.util.Map;

import okhttp3.MultipartBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.HTTP;
import retrofit2.http.Multipart;
import retrofit2.http.PATCH;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Part;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface SocialAppApiService {

    // Auth
    @POST("api/register")
    Call<AuthResponse> register(@Body RegisterRequest request);

    @POST("api/login")
    Call<AuthResponse> login(@Body LoginRequest request);

    @POST("api/logout")
    Call<ResponseBody> logout();

    @GET("api/user")
    Call<UserResource> getCurrentUser();

    @POST("api/forgot-password")
    Call<ResponseBody> forgotPassword(@Body Map<String, String> body);

    @POST("api/reset-password")
    Call<ResponseBody> resetPassword(@Body Map<String, String> body);

    @POST("api/email/verification-notification")
    Call<ResponseBody> sendEmailVerification();

    // Categories
    @GET("api/categories")
    Call<CategoryListResponse> getCategories();

    // Posts
    @GET("api/posts")
    Call<PostListResponse> getPosts(
            @Query("page") Integer page,
            @Query("limit") Integer limit,
            @Query("sort") String sort,
            @Query("category") String category,
            @Query("q") String query,
            @Query("user_id") Integer userId,
            @Query("feed") String feed
    );

    @GET("api/posts/{id}")
    Call<PostDetailResponse> getPostDetail(@Path("id") int postId);

    @POST("api/posts")
    Call<PostDetailResponse> createPost(@Body Map<String, Object> body);

    @PUT("api/posts/{id}")
    Call<PostDetailResponse> updatePost(@Path("id") int postId, @Body Map<String, Object> body);

    @DELETE("api/posts/{id}")
    Call<ResponseBody> deletePost(@Path("id") int postId);

    @POST("api/posts/{id}/upvote")
    Call<VoteResponse> upvotePost(@Path("id") int postId);

    @POST("api/posts/{id}/downvote")
    Call<VoteResponse> downvotePost(@Path("id") int postId);

    @POST("api/posts/{id}/report")
    Call<ResponseBody> reportPost(@Path("id") int postId, @Body ReportRequest request);

    // Comments
    @GET("api/posts/{id}/comments")
    Call<CommentListResponse> getComments(@Path("id") int postId, @Query("page") Integer page, @Query("limit") Integer limit);

    @GET("api/comments/{id}/replies")
    Call<CommentListResponse> getCommentReplies(@Path("id") int commentId, @Query("page") Integer page);

    @POST("api/comments")
    Call<CommentSingleResponse> createComment(@Body Map<String, Object> body);

    @PATCH("api/comments/{id}")
    Call<CommentSingleResponse> updateComment(@Path("id") int commentId, @Body Map<String, String> body);

    @DELETE("api/comments/{id}")
    Call<ResponseBody> deleteComment(@Path("id") int commentId);

    @POST("api/comments/{id}/report")
    Call<ResponseBody> reportComment(@Path("id") int commentId, @Body ReportRequest request);

    // Profiles & Users
    @GET("api/profiles/{user}")
    Call<ProfileDetailResponse> getProfile(@Path("user") int userId);

    @GET("api/profiles/{user}/followers")
    Call<UserSearchResponse> getFollowers(@Path("user") int userId, @Query("page") Integer page);

    @GET("api/profiles/{user}/following")
    Call<UserSearchResponse> getFollowing(@Path("user") int userId, @Query("page") Integer page);

    @GET("api/users/search")
    Call<UserSearchResponse> searchUsers(@Query("q") String query, @Query("limit") Integer limit);

    @POST("api/users/{user}/follow")
    Call<ProfileDetailResponse> toggleFollow(@Path("user") int userId);

    @POST("api/users/{user}/report")
    Call<ResponseBody> reportUser(@Path("user") int userId, @Body ReportRequest request);

    // Settings
    @PATCH("api/profile/details")
    Call<UserDetailResponse> updateProfileDetails(@Body Map<String, String> body);

    @PATCH("api/profile/password")
    Call<ResponseBody> updatePassword(@Body Map<String, String> body);

    @Multipart
    @POST("api/user/avatar")
    Call<UserDetailResponse> updateAvatar(@Part MultipartBody.Part avatar);

    @Multipart
    @POST("api/user/cover-photo")
    Call<UserDetailResponse> updateCoverPhoto(@Part MultipartBody.Part cover);

    @HTTP(method = "DELETE", path = "api/profile/delete", hasBody = true)
    Call<ResponseBody> deleteAccount(@Body Map<String, String> body);

    // Ads & System Config
    @GET("api/advertisements")
    Call<List<AdvertisementResource>> getAdvertisements(@Query("position") String position);

    @GET("api/settings/logo")
    Call<Map<String, String>> getLogo();

    @GET("api/settings/background")
    Call<Map<String, String>> getBackground();

    @GET("api/settings/footer")
    Call<SettingResource> getFooter();

    // Media Upload
    @Multipart
    @POST("api/image-upload")
    Call<Map<String, String>> uploadImage(@Part MultipartBody.Part file);
}
