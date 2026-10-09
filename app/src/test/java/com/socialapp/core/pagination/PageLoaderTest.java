package com.socialapp.core.pagination;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import com.socialapp.core.network.AppError;

import org.junit.Rule;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class PageLoaderTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    public static class TestItem {
        public final int id;
        public final String name;

        public TestItem(int id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    @Test
    public void testRefreshPage1Success() {
        PageLoader<TestItem> pageLoader = new PageLoader<>((page, callback) -> {
            List<TestItem> items = new ArrayList<>();
            items.add(new TestItem(1, "Item 1"));
            items.add(new TestItem(2, "Item 2"));
            callback.onSuccess(new PageLoader.PageResult<>(items, page, true));
        }, item -> item.id);

        pageLoader.refresh();

        assertEquals(1, pageLoader.getCurrentPage());
        assertTrue(pageLoader.hasNextPage());
        List<TestItem> loadedItems = pageLoader.getItemsLiveData().getValue();
        assertNotNull(loadedItems);
        assertEquals(2, loadedItems.size());
        assertEquals(PageLoader.State.LOADED, pageLoader.getStateLiveData().getValue());
    }

    @Test
    public void testLoadNextIncrementsPageAndDeduplicates() {
        PageLoader<TestItem> pageLoader = new PageLoader<>((page, callback) -> {
            List<TestItem> items = new ArrayList<>();
            if (page == 1) {
                items.add(new TestItem(1, "Item 1"));
                items.add(new TestItem(2, "Item 2"));
                callback.onSuccess(new PageLoader.PageResult<>(items, 1, true));
            } else if (page == 2) {
                items.add(new TestItem(2, "Item 2 (Duplicate)"));
                items.add(new TestItem(3, "Item 3"));
                callback.onSuccess(new PageLoader.PageResult<>(items, 2, false));
            }
        }, item -> item.id);

        pageLoader.refresh();
        assertEquals(1, pageLoader.getCurrentPage());
        assertEquals(2, pageLoader.getItemsLiveData().getValue().size());

        pageLoader.loadNext();
        assertEquals(2, pageLoader.getCurrentPage());
        assertFalse(pageLoader.hasNextPage());

        List<TestItem> allItems = pageLoader.getItemsLiveData().getValue();
        assertEquals(3, allItems.size());
        assertEquals(PageLoader.State.NO_MORE, pageLoader.getStateLiveData().getValue());
    }

    @Test
    public void testRefreshEmptyState() {
        PageLoader<TestItem> pageLoader = new PageLoader<>((page, callback) -> {
            callback.onSuccess(new PageLoader.PageResult<>(new ArrayList<>(), 1, false));
        }, item -> item.id);

        pageLoader.refresh();

        assertEquals(PageLoader.State.EMPTY, pageLoader.getStateLiveData().getValue());
        assertEquals(0, pageLoader.getItemsLiveData().getValue().size());
    }

    @Test
    public void testErrorState() {
        PageLoader<TestItem> pageLoader = new PageLoader<>((page, callback) -> {
            callback.onError(AppError.networkError("Lỗi kết nối"));
        }, item -> item.id);

        pageLoader.refresh();

        assertEquals(PageLoader.State.ERROR, pageLoader.getStateLiveData().getValue());
        assertNotNull(pageLoader.getErrorLiveData().getValue());
    }
}
