package com.socialapp.feature.profile;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.PopupMenu;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
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
import com.socialapp.core.ui.ReportSheetBottomSheet;
import com.socialapp.databinding.ActivityProfileBinding;
import com.socialapp.feature.feed.FeedAdapter;

public class ProfileActivity extends AppCompatActivity {

    public static final String EXTRA_USER_ID = "EXTRA_USER_ID";

    private ActivityProfileBinding binding;
    private ProfileViewModel viewModel;
    private SessionManager sessionManager;
    private FeedAdapter userPostsAdapter;
    private int userId = -1;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityProfileBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        sessionManager = MyApplication.getAppContainer(this).getSessionManager();

        if (getIntent() != null) {
            userId = getIntent().getIntExtra(EXTRA_USER_ID, -1);
        }

        if (userId == -1) {
            Toast.makeText(this, "Không tìm thấy người dùng", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        ProfileRepository repository = new ProfileRepository(MyApplication.getAppContainer(this).getSocialAppApiService());
        viewModel = new ViewModelProvider(this, new ProfileViewModelFactory(repository)).get(ProfileViewModel.class);
        viewModel.setUserId(userId);

        setupUI();
        observeViewModel();
    }

    private void setupUI() {
        binding.profileToolbar.setNavigationOnClickListener(v -> finish());
        binding.btnProfileOverflowMenu.setOnClickListener(this::showOverflowMenu);

        userPostsAdapter = new FeedAdapter(post -> Navigator.navigateToPostDetail(this, post.id));
        binding.profilePostsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        binding.profilePostsRecyclerView.setAdapter(userPostsAdapter);

        binding.btnFollowUser.setOnClickListener(v -> {
            if (sessionManager.isLoggedIn()) {
                viewModel.toggleFollow();
            } else {
                sessionManager.requireLogin(() -> viewModel.toggleFollow(), this);
            }
        });

        binding.btnOpenFollowers.setOnClickListener(v -> {
            Intent intent = new Intent(this, UserListActivity.class);
            intent.putExtra(UserListActivity.EXTRA_USER_ID, userId);
            intent.putExtra(UserListActivity.EXTRA_LIST_TYPE, UserListActivity.TYPE_FOLLOWERS);
            startActivity(intent);
        });

        binding.btnOpenFollowing.setOnClickListener(v -> {
            Intent intent = new Intent(this, UserListActivity.class);
            intent.putExtra(UserListActivity.EXTRA_USER_ID, userId);
            intent.putExtra(UserListActivity.EXTRA_LIST_TYPE, UserListActivity.TYPE_FOLLOWING);
            startActivity(intent);
        });

        binding.profileSwipeRefresh.setOnRefreshListener(() -> {
            viewModel.refresh();
            binding.profileSwipeRefresh.setRefreshing(false);
        });

        binding.profileErrorState.setOnRetryClickListener(v -> viewModel.refresh());
    }

    private void observeViewModel() {
        viewModel.getProfileLiveData().observe(this, result -> {
            if (result == null) return;

            if (result.status == ApiResult.Status.SUCCESS && result.data != null) {
                bindProfileData(result.data);
            }
        });

        viewModel.getIsFollowingLiveData().observe(this, isFollowing -> {
            if (Boolean.TRUE.equals(isFollowing)) {
                binding.btnFollowUser.setText("Đang theo dõi");
            } else {
                binding.btnFollowUser.setText("Theo dõi");
            }
        });

        PageLoader<PostResource> pageLoader = viewModel.getUserPostsPageLoader();
        if (pageLoader != null) {
            pageLoader.attachRecyclerView(binding.profilePostsRecyclerView);

            pageLoader.getItemsLiveData().observe(this, items -> {
                if (items != null) {
                    userPostsAdapter.setPosts(items);
                }
            });

            pageLoader.getStateLiveData().observe(this, state -> {
                if (state == null) return;
                binding.profileLoadingSkeleton.setVisibility(state == PageLoader.State.INITIAL_LOADING ? View.VISIBLE : View.GONE);
                binding.profileEmptyState.setVisibility(state == PageLoader.State.EMPTY ? View.VISIBLE : View.GONE);
                binding.profileErrorState.setVisibility(state == PageLoader.State.ERROR ? View.VISIBLE : View.GONE);
                binding.profilePostsRecyclerView.setVisibility((state == PageLoader.State.LOADED || state == PageLoader.State.LOADING_MORE || state == PageLoader.State.NO_MORE) ? View.VISIBLE : View.GONE);
            });
        }
    }

    private void bindProfileData(@NonNull ProfileResource profile) {
        String profileName = profile.getName();
        binding.profileNameText.setText(profileName != null ? profileName : "Thành viên");
        binding.profileAvatar.loadAvatar(profile.getAvatarUrl());

        String role = profile.getRole();
        if (role != null && !role.trim().isEmpty() && !"user".equalsIgnoreCase(role.trim())) {
            binding.profileRoleChip.setText(role.toUpperCase());
            binding.profileRoleChip.setVisibility(View.VISIBLE);
        } else {
            binding.profileRoleChip.setVisibility(View.GONE);
        }

        UserResource currentUser = sessionManager.getCurrentUser();
        if (currentUser != null && currentUser.id == profile.getId()) {
            binding.btnFollowUser.setVisibility(View.GONE);
            binding.btnProfileOverflowMenu.setVisibility(View.GONE);
        } else {
            binding.btnFollowUser.setVisibility(View.VISIBLE);
            binding.btnProfileOverflowMenu.setVisibility(View.VISIBLE);
        }

        binding.profilePostsCountText.setText(String.valueOf(profile.postsCount));
        binding.profileFollowersCountText.setText(String.valueOf(profile.followersCount));
        binding.profileFollowingCountText.setText(String.valueOf(profile.followingCount));

        String coverUrl = profile.getCoverPhotoUrl();
        if (coverUrl != null && !coverUrl.trim().isEmpty()) {
            Glide.with(this).load(coverUrl).centerCrop().into(binding.profileCoverImage);
        }
    }

    private void showOverflowMenu(View anchor) {
        PopupMenu popupMenu = new PopupMenu(this, anchor);
        popupMenu.getMenu().add("Báo cáo người dùng");

        popupMenu.setOnMenuItemClickListener(item -> {
            if ("Báo cáo người dùng".equals(item.getTitle())) {
                if (sessionManager.isLoggedIn()) {
                    ReportSheetBottomSheet reportSheet = ReportSheetBottomSheet.newInstance();
                    reportSheet.setOnReportSubmitListener(reason -> {
                        viewModel.reportUser(reason).observe(this, result -> {
                            if (result != null && result.isSuccess()) {
                                Toast.makeText(this, result.data != null ? result.data : "Đã gửi báo cáo", Toast.LENGTH_SHORT).show();
                            } else if (result != null && result.isError()) {
                                Toast.makeText(this, result.error != null ? result.error.getMessage() : "Báo cáo thất bại", Toast.LENGTH_SHORT).show();
                            }
                        });
                    });
                    reportSheet.show(getSupportFragmentManager(), "ReportUserSheet");
                } else {
                    sessionManager.requireLogin(() -> showOverflowMenu(anchor), this);
                }
                return true;
            }
            return false;
        });

        popupMenu.show();
    }
}
