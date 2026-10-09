package com.socialapp.feature.post;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.socialapp.core.network.ApiResult;
import com.socialapp.core.network.dto.PostResource;
import com.socialapp.core.network.dto.ProfileResource;

public class PostDetailViewModel extends ViewModel {

    private final PostDetailRepository repository;
    private final MutableLiveData<ApiResult<PostResource>> postDetailLiveData = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isFollowingLiveData = new MutableLiveData<>(false);

    private PostResource currentPost;

    public PostDetailViewModel(@NonNull PostDetailRepository repository) {
        this.repository = repository;
    }

    public void loadPostDetail(int postId) {
        postDetailLiveData.setValue(ApiResult.loading());
        repository.getPostDetail(postId).observeForever(result -> {
            if (result != null && result.status == ApiResult.Status.SUCCESS && result.data != null) {
                currentPost = result.data;
                isFollowingLiveData.setValue(currentPost.isFollowingAuthor);
            }
            postDetailLiveData.setValue(result);
        });
    }

    public void upvote() {
        if (currentPost == null) return;

        int oldScore = currentPost.voteScore;
        int oldUserVote = currentPost.userVote;

        if (currentPost.userVote == 1) {
            currentPost.userVote = 0;
            currentPost.voteScore -= 1;
        } else if (currentPost.userVote == -1) {
            currentPost.userVote = 1;
            currentPost.voteScore += 2;
        } else {
            currentPost.userVote = 1;
            currentPost.voteScore += 1;
        }

        postDetailLiveData.setValue(ApiResult.success(currentPost));

        repository.upvotePost(currentPost.id).observeForever(result -> {
            if (result != null && result.status == ApiResult.Status.SUCCESS && result.data != null) {
                currentPost.voteScore = result.data.getVoteScore();
                currentPost.userVote = result.data.getUserVote();
                postDetailLiveData.setValue(ApiResult.success(currentPost));
            } else if (result != null && result.status == ApiResult.Status.ERROR) {
                currentPost.voteScore = oldScore;
                currentPost.userVote = oldUserVote;
                postDetailLiveData.setValue(ApiResult.success(currentPost));
            }
        });
    }

    public void downvote() {
        if (currentPost == null) return;

        int oldScore = currentPost.voteScore;
        int oldUserVote = currentPost.userVote;

        if (currentPost.userVote == -1) {
            currentPost.userVote = 0;
            currentPost.voteScore += 1;
        } else if (currentPost.userVote == 1) {
            currentPost.userVote = -1;
            currentPost.voteScore -= 2;
        } else {
            currentPost.userVote = -1;
            currentPost.voteScore -= 1;
        }

        postDetailLiveData.setValue(ApiResult.success(currentPost));

        repository.downvotePost(currentPost.id).observeForever(result -> {
            if (result != null && result.status == ApiResult.Status.SUCCESS && result.data != null) {
                currentPost.voteScore = result.data.getVoteScore();
                currentPost.userVote = result.data.getUserVote();
                postDetailLiveData.setValue(ApiResult.success(currentPost));
            } else if (result != null && result.status == ApiResult.Status.ERROR) {
                currentPost.voteScore = oldScore;
                currentPost.userVote = oldUserVote;
                postDetailLiveData.setValue(ApiResult.success(currentPost));
            }
        });
    }

    public LiveData<ApiResult<ProfileResource>> toggleFollowAuthor() {
        if (currentPost == null || currentPost.author == null) return new MutableLiveData<>();

        boolean currentFollow = Boolean.TRUE.equals(isFollowingLiveData.getValue());
        isFollowingLiveData.setValue(!currentFollow);

        MutableLiveData<ApiResult<ProfileResource>> resultLiveData = new MutableLiveData<>();
        repository.toggleFollowAuthor(currentPost.author.id).observeForever(result -> {
            if (result != null && result.status == ApiResult.Status.SUCCESS && result.data != null) {
                isFollowingLiveData.setValue(result.data.isFollowing);
            } else if (result != null && result.status == ApiResult.Status.ERROR) {
                isFollowingLiveData.setValue(currentFollow);
            }
            resultLiveData.setValue(result);
        });

        return resultLiveData;
    }

    public LiveData<ApiResult<String>> reportPost(@NonNull String reason) {
        if (currentPost == null) return new MutableLiveData<>();
        return repository.reportPost(currentPost.id, reason);
    }

    @NonNull
    public LiveData<ApiResult<PostResource>> getPostDetailLiveData() {
        return postDetailLiveData;
    }

    @NonNull
    public LiveData<Boolean> getIsFollowingLiveData() {
        return isFollowingLiveData;
    }

    @Nullable
    public PostResource getCurrentPost() {
        return currentPost;
    }
}
