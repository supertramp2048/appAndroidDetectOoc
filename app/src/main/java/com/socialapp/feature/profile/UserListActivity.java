package com.socialapp.feature.profile;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.socialapp.MyApplication;
import com.socialapp.core.navigation.Navigator;
import com.socialapp.core.network.dto.UserResource;
import com.socialapp.core.pagination.PageLoader;
import com.socialapp.databinding.ActivityUserListBinding;
import com.socialapp.feature.feed.UserAdapter;

public class UserListActivity extends AppCompatActivity {

    public static final String EXTRA_USER_ID = "EXTRA_USER_ID";
    public static final String EXTRA_LIST_TYPE = "EXTRA_LIST_TYPE";
    public static final String TYPE_FOLLOWERS = "followers";
    public static final String TYPE_FOLLOWING = "following";

    private ActivityUserListBinding binding;
    private ProfileViewModel viewModel;
    private UserAdapter userAdapter;
    private PageLoader<UserResource> pageLoader;

    private int userId = -1;
    private String listType = TYPE_FOLLOWERS;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityUserListBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        if (getIntent() != null) {
            userId = getIntent().getIntExtra(EXTRA_USER_ID, -1);
            if (getIntent().hasExtra(EXTRA_LIST_TYPE)) {
                listType = getIntent().getStringExtra(EXTRA_LIST_TYPE);
            }
        }

        if (userId == -1) {
            Toast.makeText(this, "Không tìm thấy thông tin người dùng", Toast.LENGTH_SHORT).show();
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
        binding.userListToolbar.setNavigationOnClickListener(v -> finish());
        binding.userListToolbar.setTitle(TYPE_FOLLOWING.equalsIgnoreCase(listType) ? "Đang theo dõi" : "Người theo dõi");

        userAdapter = new UserAdapter(user -> Navigator.navigateToUserProfile(this, user.id));
        binding.userListRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        binding.userListRecyclerView.setAdapter(userAdapter);

        PageLoader.PageFetcher<UserResource> fetcher = TYPE_FOLLOWING.equalsIgnoreCase(listType)
                ? viewModel.createFollowingPageFetcher()
                : viewModel.createFollowersPageFetcher();

        pageLoader = new PageLoader<>(fetcher, user -> user.id);
        pageLoader.attachRecyclerView(binding.userListRecyclerView);

        binding.userListSwipeRefresh.setOnRefreshListener(() -> {
            if (pageLoader != null) {
                pageLoader.refresh();
            }
            binding.userListSwipeRefresh.setRefreshing(false);
        });

        binding.userListErrorState.setOnRetryClickListener(v -> {
            if (pageLoader != null) {
                pageLoader.refresh();
            }
        });
    }

    private void observeViewModel() {
        if (pageLoader == null) return;

        pageLoader.getItemsLiveData().observe(this, items -> {
            if (items != null) {
                userAdapter.setUsers(items);
            }
        });

        pageLoader.getStateLiveData().observe(this, state -> {
            if (state == null) return;
            binding.userListLoadingSkeleton.setVisibility(state == PageLoader.State.INITIAL_LOADING ? View.VISIBLE : View.GONE);
            binding.userListEmptyState.setVisibility(state == PageLoader.State.EMPTY ? View.VISIBLE : View.GONE);
            binding.userListErrorState.setVisibility(state == PageLoader.State.ERROR ? View.VISIBLE : View.GONE);
            binding.userListRecyclerView.setVisibility((state == PageLoader.State.LOADED || state == PageLoader.State.LOADING_MORE || state == PageLoader.State.NO_MORE) ? View.VISIBLE : View.GONE);
        });

        pageLoader.refresh();
    }
}
