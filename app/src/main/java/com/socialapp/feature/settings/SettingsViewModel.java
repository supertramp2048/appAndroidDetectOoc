package com.socialapp.feature.settings;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;

import com.socialapp.core.network.ApiResult;
import com.socialapp.core.network.dto.UserResource;

import java.io.File;

public class SettingsViewModel extends ViewModel {

    private final SettingsRepository repository;

    public SettingsViewModel(@NonNull SettingsRepository repository) {
        this.repository = repository;
    }

    public LiveData<ApiResult<UserResource>> updateName(@NonNull String name) {
        return repository.updateName(name);
    }

    public LiveData<ApiResult<UserResource>> updateAvatar(@NonNull File avatarFile) {
        return repository.updateAvatar(avatarFile);
    }

    public LiveData<ApiResult<UserResource>> updateCoverPhoto(@NonNull File coverFile) {
        return repository.updateCoverPhoto(coverFile);
    }

    public LiveData<ApiResult<String>> updatePassword(@NonNull String currentPassword,
                                                     @NonNull String newPassword,
                                                     @NonNull String confirmPassword) {
        return repository.updatePassword(currentPassword, newPassword, confirmPassword);
    }

    public LiveData<ApiResult<Void>> deleteAccount(@NonNull String password) {
        return repository.deleteAccount(password);
    }
}
