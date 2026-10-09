package com.socialapp.feature.comments;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.socialapp.core.network.ApiResult;
import com.socialapp.core.network.dto.CommentResource;
import com.socialapp.core.pagination.PageLoader;

public class CommentsViewModel extends ViewModel {

    private final CommentsRepository repository;
    private final MutableLiveData<CommentResource> replyingToLiveData = new MutableLiveData<>(null);

    private int postId = -1;
    private PageLoader<CommentResource> rootCommentsPageLoader;

    public CommentsViewModel(@NonNull CommentsRepository repository) {
        this.repository = repository;
    }

    public void init(int postId) {
        if (this.postId == postId && rootCommentsPageLoader != null) {
            return;
        }
        this.postId = postId;
        PageLoader.PageFetcher<CommentResource> fetcher = repository.createRootCommentsPageFetcher(postId);
        rootCommentsPageLoader = new PageLoader<>(fetcher, comment -> comment.id);
        rootCommentsPageLoader.refresh();
    }

    public void refresh() {
        if (rootCommentsPageLoader != null) {
            rootCommentsPageLoader.refresh();
        }
    }

    public void setReplyingTo(@Nullable CommentResource comment) {
        replyingToLiveData.setValue(comment);
    }

    public void clearReplyingTo() {
        replyingToLiveData.setValue(null);
    }

    @NonNull
    public LiveData<CommentResource> getReplyingToLiveData() {
        return replyingToLiveData;
    }

    public LiveData<ApiResult<CommentResource>> createComment(@NonNull String content) {
        CommentResource replyingTo = replyingToLiveData.getValue();
        Integer parentId = replyingTo != null ? replyingTo.id : null;
        return repository.createComment(postId, parentId, content);
    }

    public LiveData<ApiResult<CommentResource>> updateComment(int commentId, @NonNull String content) {
        return repository.updateComment(commentId, content);
    }

    public LiveData<ApiResult<Void>> deleteComment(int commentId) {
        return repository.deleteComment(commentId);
    }

    public LiveData<ApiResult<String>> reportComment(int commentId, @NonNull String reason) {
        return repository.reportComment(commentId, reason);
    }

    public PageLoader.PageFetcher<CommentResource> createRepliesPageFetcher(int commentId) {
        return repository.createRepliesPageFetcher(commentId);
    }

    @Nullable
    public PageLoader<CommentResource> getRootCommentsPageLoader() {
        return rootCommentsPageLoader;
    }

    public int getPostId() {
        return postId;
    }
}
