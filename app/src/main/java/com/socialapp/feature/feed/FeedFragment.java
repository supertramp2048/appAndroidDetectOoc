package com.socialapp.feature.feed;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.tabs.TabLayout;
import com.socialapp.MyApplication;
import com.socialapp.core.navigation.Navigator;
import com.socialapp.core.network.ApiResult;
import com.socialapp.core.network.dto.PostResource;
import com.socialapp.core.pagination.PageLoader;
import com.socialapp.core.prefetch.PrefetchConfig;
import com.socialapp.core.prefetch.PrefetchEngine;
import com.socialapp.core.prefetch.ScreenId;
import com.socialapp.core.session.SessionManager;
import com.socialapp.core.telemetry.LoadContext;
import com.socialapp.core.telemetry.NavigationEvent;
import com.socialapp.core.ui.RequireLoginBottomSheet;
import com.socialapp.core.ui.blocks.adapter.RecyclerView120HzTuner;
import com.socialapp.databinding.FragmentFeedBinding;
import com.socialapp.feature.feed.FeedViewModel.FeedTab;
import com.socialapp.feature.home.HomeActivity;

public class FeedFragment extends Fragment {

    private static final String STATE_FEED_TAB = "feed_tab";

    private FragmentFeedBinding binding;
    private FeedViewModel viewModel;
    private SessionManager sessionManager;
    private CategoryChipAdapter categoryChipAdapter;
    private FeedAdapter feedAdapter;
    private boolean syncingTabSelection;

    // [Ý 1 - Viewport Dwell Signal]: Bộ hẹn giờ theo dõi khi người dùng dừng mắt trên 1 thẻ bài viết
    private final Handler dwellHandler = new Handler(Looper.getMainLooper());
    private Runnable pendingDwellRunnable;
    private int lastDwellPostId = -1;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentFeedBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        sessionManager = MyApplication.getAppContainer(requireContext()).getSessionManager();
        FeedRepository repository = new FeedRepository(MyApplication.getAppContainer(requireContext()).getSocialAppApiService());
        FeedTab requestedTab = consumeFeedTabRequest();
        FeedTab initialTab = requestedTab;
        if (initialTab == null && savedInstanceState != null) {
            initialTab = FeedTab.fromRouteValue(savedInstanceState.getString(STATE_FEED_TAB));
        }
        if (initialTab == null || !canSelectTab(initialTab)) {
            initialTab = FeedTab.NEWEST;
        }
        viewModel = new ViewModelProvider(this, new FeedViewModelFactory(repository, initialTab))
                .get(FeedViewModel.class);

        setupCategoryRecyclerView();
        setupTabLayout();
        setupFeedRecyclerView();
        setupSwipeRefresh();
        observeViewModel();
        if (requestedTab != null) {
            applyRequestedTab(requestedTab);
        } else if (!canSelectTab(viewModel.getActiveTabLiveData().getValue())) {
            viewModel.selectTab(FeedTab.NEWEST);
        }
        syncTabSelection(viewModel.getActiveTabLiveData().getValue());
    }

    @Override
    public void onStart() {
        super.onStart();
        FeedTab requestedTab = consumeFeedTabRequest();
        if (requestedTab != null) {
            applyRequestedTab(requestedTab);
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        FeedTab activeTab = viewModel == null ? null : viewModel.getActiveTabLiveData().getValue();
        if (activeTab != null) {
            outState.putString(STATE_FEED_TAB, activeTab.getRouteValue());
        }
    }

    @Nullable
    private FeedTab consumeFeedTabRequest() {
        FragmentActivity activity = getActivity();
        return activity instanceof HomeActivity ? ((HomeActivity) activity).consumeFeedTabRequest() : null;
    }

    private boolean canSelectTab(@Nullable FeedTab tab) {
        return tab != FeedTab.FOLLOWING || sessionManager.isLoggedIn();
    }

    public void applyRequestedTab(@NonNull FeedTab tab) {
        if (!canSelectTab(tab)) {
            RequireLoginBottomSheet bottomSheet = RequireLoginBottomSheet.newInstance();
            bottomSheet.setOnLoginClickListener(() -> Navigator.navigateTo(requireContext(), Navigator.ROUTE_LOGIN));
            bottomSheet.show(getParentFragmentManager(), "RequireLogin");
            tab = FeedTab.NEWEST;
        }
        viewModel.selectTab(tab);
        syncTabSelection(tab);
    }

    private void setupCategoryRecyclerView() {
        categoryChipAdapter = new CategoryChipAdapter(category -> viewModel.selectCategory(category));
        binding.categoriesRecyclerView.setAdapter(categoryChipAdapter);
    }

    private void setupTabLayout() {
        binding.feedTabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                if (!syncingTabSelection) {
                    applyRequestedTab(FeedTab.values()[tab.getPosition()]);
                }
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });
    }

    private void syncTabSelection(@Nullable FeedTab tab) {
        if (tab == null || binding.feedTabLayout.getSelectedTabPosition() == tab.ordinal()) {
            return;
        }
        syncingTabSelection = true;
        try {
            binding.feedTabLayout.selectTab(binding.feedTabLayout.getTabAt(tab.ordinal()));
        } finally {
            syncingTabSelection = false;
        }
    }

    private void setupFeedRecyclerView() {
        feedAdapter = new FeedAdapter(post -> Navigator.navigateToPostDetail(requireContext(), post.id));
        LinearLayoutManager layoutManager = new LinearLayoutManager(requireContext());
        binding.feedRecyclerView.setLayoutManager(layoutManager);
        RecyclerView120HzTuner.tuneReadingList(binding.feedRecyclerView, layoutManager, true);
        binding.feedRecyclerView.setRecycledViewPool(
                MyApplication.getAppContainer(requireContext()).getUnifiedBlockViewPool()
        );
        binding.feedRecyclerView.setAdapter(feedAdapter);

        PageLoader<PostResource> pageLoader = viewModel.getPostPageLoader();
        if (pageLoader != null) {
            pageLoader.attachRecyclerView(binding.feedRecyclerView);
        }

        setupViewportDwellPrefetchListener();
    }

    /**
     * [Ý 1 - Viewport Dwell & Scroll Velocity Tracking]:
     * Theo dõi hành vi cuộn trên danh sách bài viết (feedRecyclerView):
     * - Khi người dùng đang vuốt nhanh (SCROLL_STATE_DRAGGING / SETTLING với vận tốc lớn),
     *   hủy ngay bộ đếm dwell để không tải trước lãng phí các bài chỉ lướt qua.
     * - Khi danh sách dừng lại (SCROLL_STATE_IDLE), tìm thẻ bài viết nằm gần tâm màn hình nhất.
     *   Nếu người dùng giữ mắt ở thẻ đó liên tục >= 450ms (VIEWPORT_DWELL_THRESHOLD_MS),
     *   báo cho PrefetchEngine tải trước API chi tiết bài viết đó + preload ảnh bìa & avatar qua Glide.
     */
    private void setupViewportDwellPrefetchListener() {
        binding.feedRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    scheduleViewportDwellCheck();
                } else {
                    cancelViewportDwellCheck();
                }
            }

            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                // Nếu đang cuộn nhanh thì hủy hẹn giờ cũ và đặt lại hẹn giờ 450ms sau khi ngừng di chuyển
                if (Math.abs(dy) > 24) {
                    cancelViewportDwellCheck();
                }
                if (recyclerView.getScrollState() == RecyclerView.SCROLL_STATE_IDLE) {
                    scheduleViewportDwellCheck();
                }
            }
        });
    }

    private void cancelViewportDwellCheck() {
        if (pendingDwellRunnable != null) {
            dwellHandler.removeCallbacks(pendingDwellRunnable);
            pendingDwellRunnable = null;
        }
    }

    private void scheduleViewportDwellCheck() {
        cancelViewportDwellCheck();
        if (binding == null) return;

        pendingDwellRunnable = () -> {
            if (binding == null || !isAdded()) return;
            PostResource focusedPost = findCenterViewportPost();
            if (focusedPost != null && focusedPost.id > 0 && focusedPost.id != lastDwellPostId) {
                lastDwellPostId = focusedPost.id;
                PrefetchEngine engine = MyApplication.getAppContainer(requireContext()).getPrefetchEngine();
                engine.onPostViewportDwell(focusedPost, PrefetchConfig.VIEWPORT_DWELL_THRESHOLD_MS);
            }
        };
        dwellHandler.postDelayed(pendingDwellRunnable, PrefetchConfig.VIEWPORT_DWELL_THRESHOLD_MS);
    }

    /**
     * Tìm thẻ bài viết đang hiển thị gần đường tâm ngang của RecyclerView nhất.
     */
    @Nullable
    private PostResource findCenterViewportPost() {
        if (binding == null || feedAdapter == null) return null;
        RecyclerView rv = binding.feedRecyclerView;
        RecyclerView.LayoutManager lm = rv.getLayoutManager();
        if (!(lm instanceof LinearLayoutManager)) return null;

        LinearLayoutManager linearLm = (LinearLayoutManager) lm;
        int firstPos = linearLm.findFirstVisibleItemPosition();
        int lastPos = linearLm.findLastVisibleItemPosition();
        if (firstPos == RecyclerView.NO_POSITION || lastPos == RecyclerView.NO_POSITION) {
            return null;
        }

        int rvCenterY = rv.getHeight() / 2;
        int bestPos = -1;
        int minDistance = Integer.MAX_VALUE;

        for (int pos = firstPos; pos <= lastPos; pos++) {
            if (feedAdapter.getPostAtAdapterPosition(pos) == null) continue;
            View itemView = linearLm.findViewByPosition(pos);
            if (itemView == null) continue;
            int itemCenterY = (itemView.getTop() + itemView.getBottom()) / 2;
            int distance = Math.abs(itemCenterY - rvCenterY);
            if (distance < minDistance) {
                minDistance = distance;
                bestPos = pos;
            }
        }

        return bestPos != -1 ? feedAdapter.getPostAtAdapterPosition(bestPos) : null;
    }

    private void setupSwipeRefresh() {
        binding.feedSwipeRefresh.setOnRefreshListener(() -> {
            reloadFeed(LoadContext.Trigger.REFRESH);
            binding.feedSwipeRefresh.setRefreshing(false);
        });

        binding.feedErrorState.setOnRetryClickListener(v -> reloadFeed(LoadContext.Trigger.RETRY));
    }

    private void reloadFeed(LoadContext.Trigger trigger) {
        NavigationEvent proof = MyApplication.getAppContainer(requireContext()).getNavigationObserver()
                .getConfirmedFeedNavigation(this, getActiveScreenId());
        viewModel.refresh(trigger, proof);
    }

    private void observeViewModel() {
        viewModel.getActiveTabLiveData().observe(getViewLifecycleOwner(), tab -> {
            syncTabSelection(tab);
            FragmentActivity activity = getActivity();
            if (activity instanceof HomeActivity) {
                ((HomeActivity) activity).onHomeScreenChanged();
            }
        });
        binding.feedEmailBanner.setupObserver(getViewLifecycleOwner());

        viewModel.getCategories().observe(getViewLifecycleOwner(), result -> {
            if (result != null && result.status == ApiResult.Status.SUCCESS && result.data != null) {
                categoryChipAdapter.setCategories(result.data);
            }
        });

        viewModel.getActiveCategoryLiveData().observe(getViewLifecycleOwner(), category -> categoryChipAdapter.setSelectedCategory(category));

        PageLoader<PostResource> pageLoader = viewModel.getPostPageLoader();
        if (pageLoader != null) {
            pageLoader.getItemsLiveData().observe(getViewLifecycleOwner(), items -> {
                if (items != null) {
                    feedAdapter.setPosts(items);
                    if (!items.isEmpty() && binding != null) {
                        binding.feedRecyclerView.post(this::scheduleViewportDwellCheck);
                    }
                    notifyContentBound();
                }
            });

            pageLoader.getStateLiveData().observe(getViewLifecycleOwner(), state -> {
                if (state == null) return;
                binding.feedLoadingSkeleton.setVisibility(state == PageLoader.State.INITIAL_LOADING ? View.VISIBLE : View.GONE);
                binding.feedEmptyState.setVisibility(state == PageLoader.State.EMPTY ? View.VISIBLE : View.GONE);
                binding.feedErrorState.setVisibility(state == PageLoader.State.ERROR ? View.VISIBLE : View.GONE);
                boolean isListVisible = (state == PageLoader.State.LOADED || state == PageLoader.State.LOADING_MORE || state == PageLoader.State.NO_MORE);
                binding.feedRecyclerView.setVisibility(isListVisible ? View.VISIBLE : View.GONE);
                if (isListVisible) {
                    binding.feedRecyclerView.post(this::scheduleViewportDwellCheck);
                }
                notifyContentBound();
            });
        }
    }

    private void notifyContentBound() {
        if (getActivity() instanceof HomeActivity) ((HomeActivity) getActivity()).onFeedContentBound(this);
    }

    public boolean isSuccessfulContentBound() {
        if (binding == null || viewModel == null || feedAdapter == null) return false;
        PageLoader<PostResource> loader = viewModel.getPostPageLoader();
        PageLoader.State state = loader == null ? null : loader.getStateLiveData().getValue();
        if (state == PageLoader.State.EMPTY) return binding.feedEmptyState.isShown();
        return (state == PageLoader.State.LOADED || state == PageLoader.State.NO_MORE || state == PageLoader.State.LOADING_MORE)
                && binding.feedRecyclerView.isShown() && feedAdapter.getItemCount() > 0;
    }

    public boolean hasLaidOutContent() {
        return isSuccessfulContentBound() && (binding.feedEmptyState.isShown()
                || binding.feedRecyclerView.findViewHolderForAdapterPosition(0) != null);
    }

    @Override
    public void onResume() {
        super.onResume();
        notifyContentBound();
    }

    @Nullable
    public ScreenId getActiveScreenId() {
        FeedTab tab = viewModel == null ? null : viewModel.getActiveTabLiveData().getValue();
        return tab == null ? null : tab.screenId;
    }

    @Override
    public void onDestroyView() {
        cancelViewportDwellCheck();
        if (getActivity() instanceof HomeActivity) ((HomeActivity) getActivity()).onFeedViewDestroyed(this);
        super.onDestroyView();
        binding = null;
    }
}
