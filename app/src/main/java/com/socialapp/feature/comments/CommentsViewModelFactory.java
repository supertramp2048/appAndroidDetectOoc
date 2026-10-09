package com.socialapp.feature.comments;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

public class CommentsViewModelFactory implements ViewModelProvider.Factory {

    private final CommentsRepository repository;

    public CommentsViewModelFactory(@NonNull CommentsRepository repository) {
        this.repository = repository;
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass.isAssignableFrom(CommentsViewModel.class)) {
            return (T) new CommentsViewModel(repository);
        }
        throw new IllegalArgumentException("Unknown ViewModel class: " + modelClass.getName());
    }
}
