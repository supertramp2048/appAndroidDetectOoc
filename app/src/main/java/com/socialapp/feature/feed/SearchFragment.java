package com.socialapp.feature.feed;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.socialapp.MyApplication;
import com.socialapp.core.navigation.Navigator;
import com.socialapp.core.network.ApiErrorParser;
import com.socialapp.core.network.SocialAppApiService;
import com.socialapp.core.network.dto.PostResource;
import com.socialapp.core.network.dto.UserResource;
import com.socialapp.core.network.dto.UserSearchResponse;
import com.socialapp.core.pagination.PageLoader;
import com.socialapp.databinding.FragmentSearchBinding;

import java.util.Collections;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SearchFragment extends Fragment {

    private FragmentSearchBinding binding;
    private FeedViewModel viewModel;
    private SocialAppApiService apiService;

    private FeedAdapter feedAdapter;
    private UserAdapter userAdapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentSearchBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        apiService = MyApplication.getAppContainer(requireContext()).getSocialAppApiService();
        FeedRepository repository = new FeedRepository(apiService);
        viewModel = new ViewModelProvider(this, new FeedViewModelFactory(repository)).get(FeedViewModel.class);

        setupUI();
        observeViewModel();
    }

    private void setupUI() {
        feedAdapter = new FeedAdapter(post -> Navigator.navigateToPostDetail(requireContext(), post.id));
        userAdapter = new UserAdapter(user -> Navigator.navigateToUserProfile(requireContext(), user.id));

        binding.searchResultsRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.searchResultsRecyclerView.setAdapter(feedAdapter);

        PageLoader<PostResource> pageLoader = viewModel.getPostPageLoader();
        if (pageLoader != null) {
            pageLoader.attachRecyclerView(binding.searchResultsRecyclerView);
        }

        // Show soft keyboard when tapping/focusing search box
        binding.searchInputEditText.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                showSoftKeyboard(v);
            }
        });

        binding.searchInputEditText.setOnClickListener(this::showSoftKeyboard);

        // Trigger search on IME Action Search / Enter key
        binding.searchInputEditText.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE
                    || (event != null && event.getKeyCode() == android.view.KeyEvent.KEYCODE_ENTER)) {
                hideSoftKeyboard(v);
                performSearch();
                return true;
            }
            return false;
        });

        // Trigger search on Search Button click
        binding.btnSearchSubmit.setOnClickListener(v -> {
            hideSoftKeyboard(v);
            performSearch();
        });

        binding.searchErrorState.setOnRetryClickListener(v -> performSearch());
    }

    private void performSearch() {
        String query = binding.searchInputEditText.getText() != null ? binding.searchInputEditText.getText().toString().trim() : "";
        boolean searchUsers = binding.checkboxSearchUsers.isChecked();

        if (query.isEmpty()) {
            Toast.makeText(requireContext(), "Vui lòng nhập từ khóa tìm kiếm", Toast.LENGTH_SHORT).show();
            return;
        }

        if (searchUsers) {
            // Search Users Mode
            binding.searchResultsRecyclerView.setAdapter(userAdapter);
            showLoading(true);

            apiService.searchUsers(query, 20).enqueue(new Callback<UserSearchResponse>() {
                @Override
                public void onResponse(@NonNull Call<UserSearchResponse> call, @NonNull Response<UserSearchResponse> response) {
                    showLoading(false);
                    if (response.isSuccessful() && response.body() != null) {
                        List<UserResource> users = response.body().data != null ? response.body().data : Collections.emptyList();
                        userAdapter.setUsers(users);
                        showResultsState(users.isEmpty(), false, null);
                    } else {
                        showResultsState(false, true, ApiErrorParser.parse(response).getMessage());
                    }
                }

                @Override
                public void onFailure(@NonNull Call<UserSearchResponse> call, @NonNull Throwable t) {
                    showLoading(false);
                    showResultsState(false, true, ApiErrorParser.parseThrowable(t).getMessage());
                }
            });
        } else {
            // Search Posts Mode
            binding.searchResultsRecyclerView.setAdapter(feedAdapter);
            viewModel.setSearchQuery(query);
        }
    }

    private void observeViewModel() {
        PageLoader<PostResource> pageLoader = viewModel.getPostPageLoader();
        if (pageLoader != null) {
            pageLoader.getItemsLiveData().observe(getViewLifecycleOwner(), items -> {
                if (!binding.checkboxSearchUsers.isChecked() && items != null) {
                    feedAdapter.setPosts(items);
                }
            });

            pageLoader.getStateLiveData().observe(getViewLifecycleOwner(), state -> {
                if (binding.checkboxSearchUsers.isChecked() || state == null) return;
                binding.searchLoadingSkeleton.setVisibility(state == PageLoader.State.INITIAL_LOADING ? View.VISIBLE : View.GONE);
                binding.searchEmptyState.setVisibility(state == PageLoader.State.EMPTY ? View.VISIBLE : View.GONE);
                binding.searchErrorState.setVisibility(state == PageLoader.State.ERROR ? View.VISIBLE : View.GONE);
                binding.searchResultsRecyclerView.setVisibility((state == PageLoader.State.LOADED || state == PageLoader.State.LOADING_MORE || state == PageLoader.State.NO_MORE) ? View.VISIBLE : View.GONE);
            });
        }
    }

    private void showLoading(boolean isLoading) {
        binding.searchLoadingSkeleton.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        if (isLoading) {
            binding.searchResultsRecyclerView.setVisibility(View.GONE);
            binding.searchEmptyState.setVisibility(View.GONE);
            binding.searchErrorState.setVisibility(View.GONE);
        }
    }

    private void showResultsState(boolean isEmpty, boolean isError, @Nullable String errorMessage) {
        binding.searchEmptyState.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        binding.searchErrorState.setVisibility(isError ? View.VISIBLE : View.GONE);
        if (isError) {
            binding.searchErrorState.setErrorMessage(errorMessage);
        }
        binding.searchResultsRecyclerView.setVisibility((!isEmpty && !isError) ? View.VISIBLE : View.GONE);
    }

    private void showSoftKeyboard(@NonNull View view) {
        view.requestFocus();
        InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    private void hideSoftKeyboard(@NonNull View view) {
        InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
