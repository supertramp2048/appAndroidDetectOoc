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
import com.socialapp.core.session.SessionManager;
import com.socialapp.core.ui.RequireLoginBottomSheet;
import com.socialapp.databinding.FragmentFeedBinding;

public class FeedFragment extends Fragment {

    private FragmentFeedBinding binding;
    private FeedViewModel viewModel;
    private SessionManager sessionManager;
    private CategoryChipAdapter categoryChipAdapter;
    private FeedAdapter feedAdapter;

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
        viewModel = new ViewModelProvider(this, new FeedViewModelFactory(repository)).get(FeedViewModel.class);

        setupCategoryRecyclerView();
        setupTabLayout();
        setupFeedRecyclerView();
        setupSwipeRefresh();
        observeViewModel();
    }

    private void setupCategoryRecyclerView() {
        categoryChipAdapter = new CategoryChipAdapter(category -> viewModel.selectCategory(category));
        binding.categoriesRecyclerView.setAdapter(categoryChipAdapter);
    }

    private void setupTabLayout() {
        binding.feedTabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                int position = tab.getPosition();
                if (position == 0) {
                    viewModel.selectTab(FeedViewModel.FeedTab.NEWEST);
                } else if (position == 1) {
                    viewModel.selectTab(FeedViewModel.FeedTab.HOT);
                } else if (position == 2) {
                    if (sessionManager.isLoggedIn()) {
                        viewModel.selectTab(FeedViewModel.FeedTab.FOLLOWING);
                    } else {
                        RequireLoginBottomSheet bottomSheet = RequireLoginBottomSheet.newInstance();
                        bottomSheet.setOnLoginClickListener(() -> Navigator.navigateTo(requireContext(), Navigator.ROUTE_LOGIN));
                        bottomSheet.show(getParentFragmentManager(), "RequireLogin");

                        TabLayout.Tab firstTab = binding.feedTabLayout.getTabAt(0);
                        if (firstTab != null) {
                            firstTab.select();
                        }
                    }
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

    private void setupFeedRecyclerView() {
        feedAdapter = new FeedAdapter(post -> Navigator.navigateToPostDetail(requireContext(), post.id));
        binding.feedRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
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
            viewModel.refresh();
            binding.feedSwipeRefresh.setRefreshing(false);
        });

        binding.feedErrorState.setOnRetryClickListener(v -> viewModel.refresh());
    }

    private void observeViewModel() {
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
            });
        }
    }

    @Override
    public void onDestroyView() {
        cancelViewportDwellCheck();
        super.onDestroyView();
        binding = null;
    }
}
