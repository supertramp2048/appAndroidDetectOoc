package com.socialapp.feature.profile;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

public class ProfileViewModelFactory implements ViewModelProvider.Factory {

    private final ProfileRepository repository;

    public ProfileViewModelFactory(@NonNull ProfileRepository repository) {
        this.repository = repository;
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass.isAssignableFrom(ProfileViewModel.class)) {
            return (T) new ProfileViewModel(repository);
        }
        throw new IllegalArgumentException("Unknown ViewModel class: " + modelClass.getName());
    }
}
