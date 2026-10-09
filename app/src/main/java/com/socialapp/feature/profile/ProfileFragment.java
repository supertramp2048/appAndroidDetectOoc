package com.socialapp.feature.profile;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.bumptech.glide.Glide;
import com.socialapp.MyApplication;
import com.socialapp.core.navigation.Navigator;
import com.socialapp.core.network.ApiResult;
import com.socialapp.core.network.dto.PostResource;
import com.socialapp.core.network.dto.ProfileResource;
import com.socialapp.core.network.dto.UserResource;
import com.socialapp.core.pagination.PageLoader;
import com.socialapp.core.session.SessionManager;
import com.socialapp.core.ui.ConfirmDialog;
import com.socialapp.databinding.FragmentProfileBinding;
import com.socialapp.feature.feed.FeedAdapter;

public class ProfileFragment extends Fragment {

    private FragmentProfileBinding binding;
    private SessionManager sessionManager;
    private ProfileViewModel viewModel;
    private FeedAdapter userPostsAdapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentProfileBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        sessionManager = MyApplication.getAppContainer(requireContext()).getSessionManager();

        binding.btnProfileGuestLogin.setOnClickListener(v -> Navigator.navigateTo(requireContext(), Navigator.ROUTE_LOGIN));

        binding.btnProfileLogout.setOnClickListener(v -> {
            ConfirmDialog dialog = new ConfirmDialog(
                    requireContext(),
                    "Đăng xuất",
                    "Bạn có chắc chắn muốn đăng xuất khỏi tài khoản này?",
                    () -> {
                        sessionManager.logout();
                        Toast.makeText(requireContext(), "Đã đăng xuất", Toast.LENGTH_SHORT).show();
                    }
            );
            dialog.show();
        });

        setupPostsRecyclerView();
        setupSwipeRefresh();
        observeSession();
    }

    private void setupPostsRecyclerView() {
        userPostsAdapter = new FeedAdapter(post -> Navigator.navigateToPostDetail(requireContext(), post.id));
        binding.profilePostsRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.profilePostsRecyclerView.setAdapter(userPostsAdapter);
    }

    private void setupSwipeRefresh() {
        binding.profileSwipeRefresh.setOnRefreshListener(() -> {
            if (viewModel != null) {
                viewModel.refresh();
            }
            binding.profileSwipeRefresh.setRefreshing(false);
        });

        binding.profileErrorState.setOnRetryClickListener(v -> {
            if (viewModel != null) {
                viewModel.refresh();
            }
        });
    }

    private void observeSession() {
        binding.profileEmailBanner.setupObserver(getViewLifecycleOwner());

        sessionManager.getSessionLiveData().observe(getViewLifecycleOwner(), state -> {
            if (state != null && state.isLoggedIn && state.user != null) {
                showLoggedInProfile(state.user);
            } else {
                showGuestProfile();
            }
        });
    }

    private void showGuestProfile() {
        binding.profileGuestContainer.setVisibility(View.VISIBLE);
        binding.profileSwipeRefresh.setVisibility(View.GONE);
    }

    private void showLoggedInProfile(@NonNull UserResource currentUser) {
        binding.profileGuestContainer.setVisibility(View.GONE);
        binding.profileSwipeRefresh.setVisibility(View.VISIBLE);

        binding.profileNameText.setText(currentUser.name != null ? currentUser.name : "Thành viên");
        binding.profileEmailText.setText(currentUser.email != null ? currentUser.email : "");
        binding.profileRoleChip.setText(currentUser.role != null ? currentUser.role.toUpperCase() : "USER");

        binding.profileAvatar.loadAvatar(currentUser.getAvatarUrl());

        ProfileRepository repository = new ProfileRepository(MyApplication.getAppContainer(requireContext()).getSocialAppApiService());
        viewModel = new ViewModelProvider(this, new ProfileViewModelFactory(repository)).get(ProfileViewModel.class);
        viewModel.setUserId(currentUser.id);

        observeProfileViewModel();
    }

    private void observeProfileViewModel() {
        if (viewModel == null) return;

        viewModel.getProfileLiveData().observe(getViewLifecycleOwner(), result -> {
            if (result == null) return;

            if (result.status == ApiResult.Status.SUCCESS && result.data != null) {
                ProfileResource profile = result.data;

                binding.profilePostsCountText.setText(String.valueOf(profile.postsCount));
                binding.profileFollowersCountText.setText(String.valueOf(profile.followersCount));
                binding.profileFollowingCountText.setText(String.valueOf(profile.followingCount));

                if (profile.coverPhotoUrl != null && !profile.coverPhotoUrl.trim().isEmpty()) {
                    Glide.with(this)
                            .load(profile.coverPhotoUrl)
                            .centerCrop()
                            .into(binding.profileCoverImage);
                }
            }
        });

        PageLoader<PostResource> pageLoader = viewModel.getUserPostsPageLoader();
        if (pageLoader != null) {
            pageLoader.attachRecyclerView(binding.profilePostsRecyclerView);

            pageLoader.getItemsLiveData().observe(getViewLifecycleOwner(), items -> {
                if (items != null) {
                    userPostsAdapter.setPosts(items);
                }
            });

            pageLoader.getStateLiveData().observe(getViewLifecycleOwner(), state -> {
                if (state == null) return;
                binding.profileLoadingSkeleton.setVisibility(state == PageLoader.State.INITIAL_LOADING ? View.VISIBLE : View.GONE);
                binding.profileEmptyState.setVisibility(state == PageLoader.State.EMPTY ? View.VISIBLE : View.GONE);
                binding.profileErrorState.setVisibility(state == PageLoader.State.ERROR ? View.VISIBLE : View.GONE);
                binding.profilePostsRecyclerView.setVisibility((state == PageLoader.State.LOADED || state == PageLoader.State.LOADING_MORE || state == PageLoader.State.NO_MORE) ? View.VISIBLE : View.GONE);
            });
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
