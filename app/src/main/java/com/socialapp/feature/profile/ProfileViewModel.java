package com.socialapp.feature.profile;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.socialapp.core.network.ApiResult;
import com.socialapp.core.network.dto.PostResource;
import com.socialapp.core.network.dto.ProfileResource;
import com.socialapp.core.pagination.PageLoader;

public class ProfileViewModel extends ViewModel {

    private final ProfileRepository repository;
    private final MutableLiveData<Integer> userIdLiveData = new MutableLiveData<>(-1);

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

    public LiveData<ApiResult<ProfileResource>> getProfileLiveData() {
        return profileLiveData;
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
