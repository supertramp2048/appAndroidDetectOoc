package com.socialapp.feature.compose;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

public class ComposeViewModelFactory implements ViewModelProvider.Factory {

    private final ComposeRepository repository;

    public ComposeViewModelFactory(@NonNull ComposeRepository repository) {
        this.repository = repository;
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass.isAssignableFrom(ComposeViewModel.class)) {
            return (T) new ComposeViewModel(repository);
        }
        throw new IllegalArgumentException("Unknown ViewModel class: " + modelClass.getName());
    }
}
