package com.socialapp.feature.profile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.MutableLiveData;

import com.socialapp.core.network.ApiResult;
import com.socialapp.core.network.dto.PostResource;
import com.socialapp.core.network.dto.ProfileResource;
import com.socialapp.core.network.dto.UserResource;
import com.socialapp.core.pagination.PageLoader;

import java.util.Collections;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class ProfileViewModelTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    private ProfileViewModel viewModel;
    private ProfileResource sampleProfile;

    @Before
    public void setUp() {
        sampleProfile = new ProfileResource();
        sampleProfile.user = new UserResource();
        sampleProfile.user.id = 10;
        sampleProfile.user.name = "Nguyễn Văn A";
        sampleProfile.followersCount = 5;
        sampleProfile.followingCount = 3;
        sampleProfile.postsCount = 8;
        sampleProfile.isFollowing = false;

        ProfileRepository mockRepository = new ProfileRepository(null) {
            @Override
            public MutableLiveData<ApiResult<ProfileResource>> getProfile(int userId) {
                MutableLiveData<ApiResult<ProfileResource>> liveData = new MutableLiveData<>();
                liveData.setValue(ApiResult.success(sampleProfile));
                return liveData;
            }

            @Override
            public MutableLiveData<ApiResult<ProfileResource>> toggleFollow(int userId) {
                MutableLiveData<ApiResult<ProfileResource>> liveData = new MutableLiveData<>();
                sampleProfile.isFollowing = !sampleProfile.isFollowing;
                sampleProfile.followersCount += sampleProfile.isFollowing ? 1 : -1;
                liveData.setValue(ApiResult.success(sampleProfile));
                return liveData;
            }

            @Override
            public PageLoader.PageFetcher<PostResource> createUserPostsPageFetcher(int userId) {
                return (page, callback) -> callback.onSuccess(new PageLoader.PageResult<>(Collections.emptyList(), 1, false));
            }
        };

        viewModel = new ProfileViewModel(mockRepository);
    }

    @Test
    public void testSetUserIdAndGetProfile() {
        viewModel.setUserId(10);
        ApiResult<ProfileResource> result = viewModel.getProfileLiveData().getValue();
        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertNotNull(result.data);
        assertEquals(10, result.data.user.id);
        assertEquals("Nguyễn Văn A", result.data.user.name);
    }

    @Test
    public void testToggleFollow() {
        viewModel.setUserId(10);
        viewModel.toggleFollow();

        assertTrue(Boolean.TRUE.equals(viewModel.getIsFollowingLiveData().getValue()));
    }
}
