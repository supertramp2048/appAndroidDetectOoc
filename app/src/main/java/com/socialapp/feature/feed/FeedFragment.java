package com.socialapp.feature.feed;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.tabs.TabLayout;
import com.socialapp.MyApplication;
import com.socialapp.core.navigation.Navigator;
import com.socialapp.core.network.ApiResult;
import com.socialapp.core.network.dto.PostResource;
import com.socialapp.core.pagination.PageLoader;
import com.socialapp.core.session.SessionManager;
import com.socialapp.core.ui.RequireLoginBottomSheet;
import com.socialapp.databinding.FragmentFeedBinding;

public class FeedFragment extends Fragment {

    private FragmentFeedBinding binding;
    private FeedViewModel viewModel;
    private SessionManager sessionManager;
    private CategoryChipAdapter categoryChipAdapter;
    private FeedAdapter feedAdapter;

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
    }

    private void setupSwipeRefresh() {
        binding.feedSwipeRefresh.setOnRefreshListener(() -> {
            viewModel.refresh();
            binding.feedSwipeRefresh.setRefreshing(false);
        });

        binding.feedErrorState.setOnRetryClickListener(v -> viewModel.refresh());
    }

    private void observeViewModel() {
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
                }
            });

            pageLoader.getStateLiveData().observe(getViewLifecycleOwner(), state -> {
                if (state == null) return;
                binding.feedLoadingSkeleton.setVisibility(state == PageLoader.State.INITIAL_LOADING ? View.VISIBLE : View.GONE);
                binding.feedEmptyState.setVisibility(state == PageLoader.State.EMPTY ? View.VISIBLE : View.GONE);
                binding.feedErrorState.setVisibility(state == PageLoader.State.ERROR ? View.VISIBLE : View.GONE);
                binding.feedRecyclerView.setVisibility((state == PageLoader.State.LOADED || state == PageLoader.State.LOADING_MORE || state == PageLoader.State.NO_MORE) ? View.VISIBLE : View.GONE);
            });
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
