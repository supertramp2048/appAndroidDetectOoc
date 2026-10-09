package com.socialapp.core.pagination;

import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.socialapp.core.network.AppError;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

public class PageLoader<T> {

    public enum State {
        INITIAL_LOADING,
        LOADED,
        LOADING_MORE,
        ERROR,
        EMPTY,
        NO_MORE
    }

    public interface IdExtractor<T> {
        @NonNull
        Object getId(@NonNull T item);
    }

    public interface PageCallback<T> {
        void onSuccess(@NonNull PageResult<T> result);
        void onError(@NonNull AppError error);
    }

    public interface PageFetcher<T> {
        void fetch(int page, @NonNull PageCallback<T> callback);
    }

    public static class PageResult<T> {
        @NonNull
        public final List<T> items;
        public final int currentPage;
        public final boolean hasNext;

        public PageResult(@NonNull List<T> items, int currentPage, boolean hasNext) {
            this.items = items;
            this.currentPage = currentPage;
            this.hasNext = hasNext;
        }
    }

    private final PageFetcher<T> pageFetcher;
    private final IdExtractor<T> idExtractor;
    private final AtomicLong generationCounter = new AtomicLong(0);

    private final MutableLiveData<List<T>> itemsLiveData = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<State> stateLiveData = new MutableLiveData<>(State.INITIAL_LOADING);
    private final MutableLiveData<AppError> errorLiveData = new MutableLiveData<>();

    private final List<T> currentItems = new ArrayList<>();
    private final Set<Object> seenIds = new HashSet<>();

    private int currentPage = 1;
    private boolean hasNextPage = true;
    private boolean isLoading = false;

    public PageLoader(@NonNull PageFetcher<T> pageFetcher, @Nullable IdExtractor<T> idExtractor) {
        this.pageFetcher = pageFetcher;
        this.idExtractor = idExtractor != null ? idExtractor : item -> item;
    }

    public synchronized void refresh() {
        long currentGen = generationCounter.incrementAndGet();
        isLoading = true;
        currentPage = 1;
        hasNextPage = true;
        currentItems.clear();
        seenIds.clear();

        postState(State.INITIAL_LOADING);
        postItems(new ArrayList<>());

        pageFetcher.fetch(currentPage, new PageCallback<T>() {
            @Override
            public void onSuccess(@NonNull PageResult<T> result) {
                if (currentGen != generationCounter.get()) return;

                isLoading = false;
                hasNextPage = result.hasNext;
                currentPage = result.currentPage;

                appendDeduplicatedItems(result.items);

                if (currentItems.isEmpty()) {
                    postState(State.EMPTY);
                } else if (!hasNextPage) {
                    postState(State.NO_MORE);
                } else {
                    postState(State.LOADED);
                }
            }

            @Override
            public void onError(@NonNull AppError error) {
                if (currentGen != generationCounter.get()) return;

                isLoading = false;
                postError(error);
                postState(State.ERROR);
            }
        });
    }

    public synchronized void loadNext() {
        if (isLoading || !hasNextPage || stateLiveData.getValue() == State.NO_MORE) {
            return;
        }

        long currentGen = generationCounter.get();
        isLoading = true;
        int nextPage = currentPage + 1;

        postState(State.LOADING_MORE);

        pageFetcher.fetch(nextPage, new PageCallback<T>() {
            @Override
            public void onSuccess(@NonNull PageResult<T> result) {
                if (currentGen != generationCounter.get()) return;

                isLoading = false;
                hasNextPage = result.hasNext;
                currentPage = result.currentPage;

                appendDeduplicatedItems(result.items);

                if (!hasNextPage) {
                    postState(State.NO_MORE);
                } else {
                    postState(State.LOADED);
                }
            }

            @Override
            public void onError(@NonNull AppError error) {
                if (currentGen != generationCounter.get()) return;

                isLoading = false;
                postError(error);
                postState(State.ERROR);
            }
        });
    }

    private void appendDeduplicatedItems(@NonNull List<T> newItems) {
        for (T item : newItems) {
            if (item == null) continue;
            Object id = idExtractor.getId(item);
            if (seenIds.add(id)) {
                currentItems.add(item);
            }
        }
        postItems(new ArrayList<>(currentItems));
    }

    public void attachRecyclerView(@NonNull RecyclerView recyclerView) {
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                super.onScrolled(rv, dx, dy);
                if (dy <= 0) return;

                RecyclerView.LayoutManager layoutManager = rv.getLayoutManager();
                if (layoutManager instanceof LinearLayoutManager) {
                    LinearLayoutManager lm = (LinearLayoutManager) layoutManager;
                    int totalItemCount = lm.getItemCount();
                    int lastVisibleItem = lm.findLastVisibleItemPosition();

                    if (totalItemCount <= (lastVisibleItem + 3)) {
                        loadNext();
                    }
                }
            }
        });
    }

    private void postState(State state) {
        try {
            stateLiveData.setValue(state);
        } catch (Throwable e) {
            stateLiveData.postValue(state);
        }
    }

    private void postItems(List<T> items) {
        try {
            itemsLiveData.setValue(items);
        } catch (Throwable e) {
            itemsLiveData.postValue(items);
        }
    }

    private void postError(AppError error) {
        try {
            errorLiveData.setValue(error);
        } catch (Throwable e) {
            errorLiveData.postValue(error);
        }
    }

    @NonNull
    public LiveData<List<T>> getItemsLiveData() {
        return itemsLiveData;
    }

    @NonNull
    public LiveData<State> getStateLiveData() {
        return stateLiveData;
    }

    @NonNull
    public LiveData<AppError> getErrorLiveData() {
        return errorLiveData;
    }

    public int getCurrentPage() {
        return currentPage;
    }

    public boolean hasNextPage() {
        return hasNextPage;
    }
}
