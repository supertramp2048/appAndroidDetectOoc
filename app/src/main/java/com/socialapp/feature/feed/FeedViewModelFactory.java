package com.socialapp.feature.feed;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

public class FeedViewModelFactory implements ViewModelProvider.Factory {

    private final FeedRepository repository;
    private final FeedViewModel.FeedTab initialTab;

    public FeedViewModelFactory(@NonNull FeedRepository repository) {
        this(repository, FeedViewModel.FeedTab.NEWEST);
    }

    public FeedViewModelFactory(@NonNull FeedRepository repository, @NonNull FeedViewModel.FeedTab initialTab) {
        this.repository = repository;
        this.initialTab = initialTab;
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass.isAssignableFrom(FeedViewModel.class)) {
            return (T) new FeedViewModel(repository, initialTab);
        }
        throw new IllegalArgumentException("Unknown ViewModel class: " + modelClass.getName());
    }
}
