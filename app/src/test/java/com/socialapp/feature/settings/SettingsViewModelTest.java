package com.socialapp.feature.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;
import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.MutableLiveData;

import com.socialapp.core.network.ApiResult;
import com.socialapp.core.network.dto.UserResource;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class SettingsViewModelTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    private SettingsViewModel viewModel;
    private UserResource sampleUser;

    @Before
    public void setUp() {
        sampleUser = new UserResource();
        sampleUser.id = 1;
        sampleUser.name = "Tên Mới";
        sampleUser.email = "test@example.com";

        SettingsRepository mockRepository = new SettingsRepository(null, null) {
            @Override
            public MutableLiveData<ApiResult<UserResource>> updateName(@NonNull String name) {
                MutableLiveData<ApiResult<UserResource>> liveData = new MutableLiveData<>();
                sampleUser.name = name;
                liveData.setValue(ApiResult.success(sampleUser));
                return liveData;
            }

            @Override
            public MutableLiveData<ApiResult<String>> updatePassword(@NonNull String currentPassword,
                                                                     @NonNull String newPassword,
                                                                     @NonNull String confirmPassword) {
                MutableLiveData<ApiResult<String>> liveData = new MutableLiveData<>();
                liveData.setValue(ApiResult.success("Đổi mật khẩu thành công."));
                return liveData;
            }

            @Override
            public MutableLiveData<ApiResult<Void>> deleteAccount(@NonNull String password) {
                MutableLiveData<ApiResult<Void>> liveData = new MutableLiveData<>();
                liveData.setValue(ApiResult.success(null));
                return liveData;
            }
        };

        viewModel = new SettingsViewModel(mockRepository);
    }

    @Test
    public void testUpdateName() {
        ApiResult<UserResource> result = viewModel.updateName("Tên Mới Cập Nhật").getValue();
        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertNotNull(result.data);
        assertEquals("Tên Mới Cập Nhật", result.data.name);
    }

    @Test
    public void testUpdatePassword() {
        ApiResult<String> result = viewModel.updatePassword("password123", "newpassword123", "newpassword123").getValue();
        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals("Đổi mật khẩu thành công.", result.data);
    }
}
