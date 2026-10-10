package com.socialapp.feature.compose;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.socialapp.core.network.ApiResult;
import com.socialapp.core.network.dto.CategoryResource;
import com.socialapp.core.network.dto.PostResource;

import java.io.File;
import java.util.List;

public class ComposeViewModel extends ViewModel {

    private static final String PREF_DRAFT = "ComposeDraftPrefs";
    private static final String KEY_DRAFT_TITLE = "draft_title";
    private static final String KEY_DRAFT_CONTENT = "draft_content";

    private final ComposeRepository repository;
    private final MutableLiveData<ApiResult<List<CategoryResource>>> categoriesLiveData = new MutableLiveData<>();
    private final MutableLiveData<ApiResult<PostResource>> postDetailLiveData = new MutableLiveData<>();

    private int postId = -1;
    private String title = "";
    private String contentHtml = "";
    private String thumbnailUrl = "";
    private int categoryId = -1;

    public ComposeViewModel(@NonNull ComposeRepository repository) {
        this.repository = repository;
    }

    public void loadCategories() {
        repository.getCategories().observeForever(categoriesLiveData::setValue);
    }

    public void loadPostDetail(int postId) {
        this.postId = postId;
        repository.getPostDetail(postId).observeForever(result -> {
            if (result != null && result.status == ApiResult.Status.SUCCESS && result.data != null) {
                PostResource post = result.data;
                this.title = post.title != null ? post.title : "";
                this.contentHtml = post.contentHtml != null ? post.contentHtml : "";
                this.thumbnailUrl = post.thumbnailUrl != null ? post.thumbnailUrl : "";
                if (post.category != null) {
                    this.categoryId = post.category.id;
                }
            }
            postDetailLiveData.setValue(result);
        });
    }

    public void saveDraft(@NonNull Context context) {
        if (postId > 0) return; // Don't overwrite draft when editing existing post
        SharedPreferences prefs = context.getApplicationContext().getSharedPreferences(PREF_DRAFT, Context.MODE_PRIVATE);
        prefs.edit()
                .putString(KEY_DRAFT_TITLE, title)
                .putString(KEY_DRAFT_CONTENT, contentHtml)
                .apply();
    }

    public void loadDraft(@NonNull Context context) {
        if (postId > 0) return;
        SharedPreferences prefs = context.getApplicationContext().getSharedPreferences(PREF_DRAFT, Context.MODE_PRIVATE);
        this.title = prefs.getString(KEY_DRAFT_TITLE, "");
        this.contentHtml = prefs.getString(KEY_DRAFT_CONTENT, "");
    }

    public void clearDraft(@NonNull Context context) {
        SharedPreferences prefs = context.getApplicationContext().getSharedPreferences(PREF_DRAFT, Context.MODE_PRIVATE);
        prefs.edit().clear().apply();
    }

    public LiveData<ApiResult<PostResource>> submitPost() {
        if (postId > 0) {
            return repository.updatePost(postId, title, contentHtml, categoryId, thumbnailUrl);
        } else {
            return repository.createPost(title, contentHtml, categoryId, thumbnailUrl);
        }
    }

    public LiveData<ApiResult<Void>> deletePost() {
        return repository.deletePost(postId);
    }

    public LiveData<ApiResult<String>> uploadImage(@NonNull File file) {
        return repository.uploadImage(file);
    }

    public LiveData<ApiResult<List<CategoryResource>>> getCategoriesLiveData() {
        return categoriesLiveData;
    }

    public LiveData<ApiResult<PostResource>> getPostDetailLiveData() {
        return postDetailLiveData;
    }

    public int getPostId() {
        return postId;
    }

    public void setPostId(int postId) {
        this.postId = postId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContentHtml() {
        return contentHtml;
    }

    public void setContentHtml(String contentHtml) {
        this.contentHtml = contentHtml;
    }

    @Nullable
    public String getThumbnailUrl() {
        return thumbnailUrl;
    }

    public void setThumbnailUrl(@Nullable String thumbnailUrl) {
        this.thumbnailUrl = thumbnailUrl;
    }

    public int getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(int categoryId) {
        this.categoryId = categoryId;
    }
}
