package com.socialapp.feature.feed;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import com.socialapp.core.mock.FakeApi;
import com.socialapp.core.network.dto.CategoryResource;
import com.socialapp.core.network.dto.PostResource;
import com.socialapp.core.pagination.PageLoader;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.List;

public class FeedViewModelTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    private FeedViewModel viewModel;

    @Before
    public void setUp() {
        FeedRepository mockRepository = new FeedRepository(null) {
            @Override
            public PageLoader.PageFetcher<PostResource> createPostPageFetcher(
                    String sort, String category, String query, Integer userId, String feed) {
                return (page, callback) -> {
                    List<PostResource> posts = FakeApi.getSamplePosts();
                    callback.onSuccess(new PageLoader.PageResult<>(posts, page, false));
                };
            }
        };

        viewModel = new FeedViewModel(mockRepository);
    }

    @Test
    public void testDefaultStateIsNewestTab() {
        assertEquals(FeedViewModel.FeedTab.NEWEST, viewModel.getActiveTabLiveData().getValue());
        assertNull(viewModel.getActiveCategoryLiveData().getValue());
        assertNotNull(viewModel.getPostPageLoader());
    }

    @Test
    public void testSelectTab() {
        viewModel.selectTab(FeedViewModel.FeedTab.HOT);
        assertEquals(FeedViewModel.FeedTab.HOT, viewModel.getActiveTabLiveData().getValue());

        viewModel.selectTab(FeedViewModel.FeedTab.FOLLOWING);
        assertEquals(FeedViewModel.FeedTab.FOLLOWING, viewModel.getActiveTabLiveData().getValue());
    }

    @Test
    public void testSelectCategory() {
        CategoryResource category = FakeApi.getSampleCategories().get(0);
        viewModel.selectCategory(category);
        assertEquals(category, viewModel.getActiveCategoryLiveData().getValue());
    }

    @Test
    public void testPostPageLoaderData() {
        PageLoader<PostResource> loader = viewModel.getPostPageLoader();
        assertNotNull(loader);

        List<PostResource> posts = loader.getItemsLiveData().getValue();
        assertNotNull(posts);
        assertEquals(10, posts.size());
    }
}
