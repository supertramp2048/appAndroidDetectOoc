package com.socialapp.feature.feed;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import com.socialapp.core.mock.FakeApi;
import com.socialapp.core.network.dto.CategoryResource;
import com.socialapp.core.network.dto.PostResource;
import com.socialapp.core.pagination.PageLoader;
import com.socialapp.core.prefetch.ScreenId;
import com.socialapp.core.telemetry.LoadContext;
import com.socialapp.core.telemetry.NavigationEvent;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class FeedViewModelTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    private FeedViewModel viewModel;
    private FeedRepository repository;
    private final List<String> pageRequests = new ArrayList<>();
    private final List<LoadContext.Trigger> triggers = new ArrayList<>();

    @Before
    public void setUp() {
        repository = new FeedRepository(null) {
            @Override
            public PageLoader.PageFetcher<PostResource> createPostPageFetcher(
                    String sort, String category, String query, Integer userId, String feed,
                    LoadContext.Trigger trigger, NavigationEvent navigation) {
                return (page, callback) -> {
                    pageRequests.add(sort + "/" + feed);
                    triggers.add(trigger);
                    List<PostResource> posts = FakeApi.getSamplePosts();
                    callback.onSuccess(new PageLoader.PageResult<>(posts, page, false));
                };
            }
        };

        viewModel = new FeedViewModel(repository);
    }

    @Test
    public void explicitRefreshAndRetryKeepTheirLoadTriggers() {
        viewModel.refresh(LoadContext.Trigger.REFRESH, null);
        viewModel.refresh(LoadContext.Trigger.RETRY, null);
        assertEquals(Arrays.asList(LoadContext.Trigger.INITIAL, LoadContext.Trigger.REFRESH,
                LoadContext.Trigger.RETRY), triggers);
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

    @Test
    public void testDefaultFactoryStillLoadsNewest() {
        pageRequests.clear();
        FeedViewModel created = new FeedViewModelFactory(repository).create(FeedViewModel.class);
        assertEquals(FeedViewModel.FeedTab.NEWEST, created.getActiveTabLiveData().getValue());
        assertEquals(Collections.singletonList("newest/null"), pageRequests);
    }

    @Test
    public void testHotInitialTabMakesOnlyOneHotRequest() {
        pageRequests.clear();
        FeedViewModel created = new FeedViewModelFactory(repository, FeedViewModel.FeedTab.HOT)
                .create(FeedViewModel.class);
        created.selectTab(FeedViewModel.FeedTab.HOT);
        assertEquals(FeedViewModel.FeedTab.HOT, created.getActiveTabLiveData().getValue());
        assertEquals(Collections.singletonList("hot/null"), pageRequests);
    }

    @Test
    public void testFollowingInitialTabAndExistingViewModelSwitch() {
        pageRequests.clear();
        FeedViewModel created = new FeedViewModel(repository, FeedViewModel.FeedTab.FOLLOWING);
        assertEquals(FeedViewModel.FeedTab.FOLLOWING, created.getActiveTabLiveData().getValue());
        created.selectTab(FeedViewModel.FeedTab.NEWEST);
        created.selectTab(FeedViewModel.FeedTab.NEWEST);
        assertEquals(Arrays.asList("null/following", "newest/null"), pageRequests);
    }

    @Test
    public void testReplayFeedRouteValues() {
        assertFeedRoute(ScreenId.FEED_NEWEST, FeedViewModel.FeedTab.NEWEST, "newest");
        assertFeedRoute(ScreenId.FEED_HOT, FeedViewModel.FeedTab.HOT, "hot");
        assertFeedRoute(ScreenId.FEED_FOLLOWING, FeedViewModel.FeedTab.FOLLOWING, "following");
        assertNull(FeedViewModel.FeedTab.fromRouteValue(null));
        assertNull(FeedViewModel.FeedTab.fromRouteValue("unknown"));
    }

    private void assertFeedRoute(ScreenId screen, FeedViewModel.FeedTab tab, String value) {
        assertEquals("home?tab=" + value, screen.getDefaultRoute());
        assertEquals(value, tab.getRouteValue());
        assertEquals(tab, FeedViewModel.FeedTab.fromRouteValue(value));
        assertEquals(screen, tab.screenId);
    }
}
