package com.socialapp.feature.profile;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.socialapp.core.network.ApiResult;
import com.socialapp.core.network.dto.PostResource;
import com.socialapp.core.network.dto.ProfileResource;
import com.socialapp.core.network.dto.UserResource;
import com.socialapp.core.pagination.PageLoader;

public class ProfileViewModel extends ViewModel {

    private final ProfileRepository repository;
    private final MutableLiveData<Integer> userIdLiveData = new MutableLiveData<>(-1);
    private final MutableLiveData<Boolean> isFollowingLiveData = new MutableLiveData<>(false);

    private LiveData<ApiResult<ProfileResource>> profileLiveData;
    private PageLoader<PostResource> userPostsPageLoader;

    public ProfileViewModel(@NonNull ProfileRepository repository) {
        this.repository = repository;
    }

    public void setUserId(int userId) {
        if (userIdLiveData.getValue() != null && userIdLiveData.getValue() == userId && userIdLiveData.getValue() != -1) {
            return;
        }
        userIdLiveData.setValue(userId);

        profileLiveData = repository.getProfile(userId);
        PageLoader.PageFetcher<PostResource> fetcher = repository.createUserPostsPageFetcher(userId);
        userPostsPageLoader = new PageLoader<>(fetcher, post -> post.id);
        userPostsPageLoader.refresh();
    }

    public LiveData<ApiResult<ProfileResource>> toggleFollow() {
        int targetUserId = userIdLiveData.getValue() != null ? userIdLiveData.getValue() : -1;
        if (targetUserId <= 0) return new MutableLiveData<>();

        boolean currentFollow = Boolean.TRUE.equals(isFollowingLiveData.getValue());
        isFollowingLiveData.setValue(!currentFollow);

        MutableLiveData<ApiResult<ProfileResource>> resultLiveData = new MutableLiveData<>();
        repository.toggleFollow(targetUserId).observeForever(result -> {
            if (result != null && result.status == ApiResult.Status.SUCCESS && result.data != null) {
                isFollowingLiveData.setValue(result.data.isFollowing);
                profileLiveData = repository.getProfile(targetUserId);
            } else if (result != null && result.status == ApiResult.Status.ERROR) {
                isFollowingLiveData.setValue(currentFollow);
            }
            resultLiveData.setValue(result);
        });

        return resultLiveData;
    }

    public LiveData<ApiResult<String>> reportUser(@NonNull String reason) {
        int targetUserId = userIdLiveData.getValue() != null ? userIdLiveData.getValue() : -1;
        if (targetUserId <= 0) return new MutableLiveData<>();

        return repository.reportUser(targetUserId, reason);
    }

    public PageLoader.PageFetcher<UserResource> createFollowersPageFetcher() {
        int targetUserId = userIdLiveData.getValue() != null ? userIdLiveData.getValue() : -1;
        return repository.getFollowersPageFetcher(targetUserId);
    }

    public PageLoader.PageFetcher<UserResource> createFollowingPageFetcher() {
        int targetUserId = userIdLiveData.getValue() != null ? userIdLiveData.getValue() : -1;
        return repository.getFollowingPageFetcher(targetUserId);
    }

    public LiveData<ApiResult<ProfileResource>> getProfileLiveData() {
        return profileLiveData;
    }

    public LiveData<Boolean> getIsFollowingLiveData() {
        return isFollowingLiveData;
    }

    public PageLoader<PostResource> getUserPostsPageLoader() {
        return userPostsPageLoader;
    }

    public void refresh() {
        if (userIdLiveData.getValue() != null && userIdLiveData.getValue() != -1) {
            profileLiveData = repository.getProfile(userIdLiveData.getValue());
            if (userPostsPageLoader != null) {
                userPostsPageLoader.refresh();
            }
        }
    }
}
