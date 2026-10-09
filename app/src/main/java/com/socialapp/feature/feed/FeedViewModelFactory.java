package com.socialapp.feature.feed;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

public class FeedViewModelFactory implements ViewModelProvider.Factory {

    private final FeedRepository repository;

    public FeedViewModelFactory(@NonNull FeedRepository repository) {
        this.repository = repository;
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass.isAssignableFrom(FeedViewModel.class)) {
            return (T) new FeedViewModel(repository);
        }
        throw new IllegalArgumentException("Unknown ViewModel class: " + modelClass.getName());
    }
}
