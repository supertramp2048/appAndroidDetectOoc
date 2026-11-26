package com.socialapp.feature.feed;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.tabs.TabLayout;
import com.socialapp.MyApplication;
import com.socialapp.core.navigation.Navigator;
import com.socialapp.core.network.ApiResult;
import com.socialapp.core.network.dto.PostResource;
import com.socialapp.core.pagination.PageLoader;
import com.socialapp.core.prefetch.ScreenId;
import com.socialapp.core.session.SessionManager;
import com.socialapp.core.telemetry.LoadContext;
import com.socialapp.core.telemetry.NavigationEvent;
import com.socialapp.core.ui.RequireLoginBottomSheet;
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
        binding.feedRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.feedRecyclerView.setAdapter(feedAdapter);

        PageLoader<PostResource> pageLoader = viewModel.getPostPageLoader();
        if (pageLoader != null) {
            pageLoader.attachRecyclerView(binding.feedRecyclerView);
        }
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
                    notifyContentBound();
                }
            });

            pageLoader.getStateLiveData().observe(getViewLifecycleOwner(), state -> {
                if (state == null) return;
                binding.feedLoadingSkeleton.setVisibility(state == PageLoader.State.INITIAL_LOADING ? View.VISIBLE : View.GONE);
                binding.feedEmptyState.setVisibility(state == PageLoader.State.EMPTY ? View.VISIBLE : View.GONE);
                binding.feedErrorState.setVisibility(state == PageLoader.State.ERROR ? View.VISIBLE : View.GONE);
                binding.feedRecyclerView.setVisibility((state == PageLoader.State.LOADED || state == PageLoader.State.LOADING_MORE || state == PageLoader.State.NO_MORE) ? View.VISIBLE : View.GONE);
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
        if (getActivity() instanceof HomeActivity) ((HomeActivity) getActivity()).onFeedViewDestroyed(this);
        super.onDestroyView();
        binding = null;
    }
}
