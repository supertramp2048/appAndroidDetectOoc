package com.socialapp.feature.post;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

public class PostDetailViewModelFactory implements ViewModelProvider.Factory {

    private final PostDetailRepository repository;

    public PostDetailViewModelFactory(@NonNull PostDetailRepository repository) {
        this.repository = repository;
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass.isAssignableFrom(PostDetailViewModel.class)) {
            return (T) new PostDetailViewModel(repository);
        }
        throw new IllegalArgumentException("Unknown ViewModel class: " + modelClass.getName());
    }
}
