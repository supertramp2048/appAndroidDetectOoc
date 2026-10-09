package com.socialapp.feature.comments;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;

import android.widget.PopupMenu;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.socialapp.MyApplication;
import com.socialapp.core.navigation.Navigator;
import com.socialapp.core.network.dto.CommentResource;
import com.socialapp.core.network.dto.UserResource;
import com.socialapp.core.pagination.PageLoader;
import com.socialapp.core.session.SessionManager;
import com.socialapp.core.ui.ConfirmDialog;
import com.socialapp.core.ui.ReportSheetBottomSheet;
import com.socialapp.databinding.ActivityCommentsBinding;

public class CommentsActivity extends AppCompatActivity {

    public static final String EXTRA_POST_ID = "EXTRA_POST_ID";

    private ActivityCommentsBinding binding;
    private CommentsViewModel viewModel;
    private SessionManager sessionManager;
    private CommentsAdapter commentsAdapter;
    private int postId = -1;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCommentsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        sessionManager = MyApplication.getAppContainer(this).getSessionManager();

        if (getIntent() != null) {
            postId = getIntent().getIntExtra(EXTRA_POST_ID, -1);
        }

        if (postId == -1) {
            Toast.makeText(this, "Không tìm thấy bài viết", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        CommentsRepository repository = new CommentsRepository(MyApplication.getAppContainer(this).getSocialAppApiService());
        viewModel = new ViewModelProvider(this, new CommentsViewModelFactory(repository)).get(CommentsViewModel.class);

        viewModel.init(postId);
        setupUI();
        observeViewModel();
    }

    private void setupUI() {
        binding.commentsToolbar.setNavigationOnClickListener(v -> finish());

        commentsAdapter = new CommentsAdapter(new CommentsAdapter.OnCommentActionListener() {
            @Override
            public void onReplyClick(@NonNull CommentResource comment) {
                if (sessionManager.isLoggedIn()) {
                    viewModel.setReplyingTo(comment);
                    binding.commentInputEditText.requestFocus();
                    showSoftKeyboard(binding.commentInputEditText);
                } else {
                    sessionManager.requireLogin(() -> onReplyClick(comment), CommentsActivity.this);
                }
            }

            @Override
            public void onLoadRepliesClick(@NonNull CommentResource comment, int position) {
                loadCommentReplies(comment, position);
            }

            @Override
            public void onMenuClick(@NonNull CommentResource comment, @NonNull View anchor) {
                showCommentMenu(comment, anchor);
            }
        });

        binding.commentsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        binding.commentsRecyclerView.setAdapter(commentsAdapter);

        binding.btnCancelReply.setOnClickListener(v -> viewModel.clearReplyingTo());

        binding.btnSendComment.setOnClickListener(v -> handleSendComment());

        binding.commentGuestPrompt.setOnClickListener(v -> Navigator.navigateTo(this, Navigator.ROUTE_LOGIN));

        binding.commentsSwipeRefresh.setOnRefreshListener(() -> {
            viewModel.refresh();
            binding.commentsSwipeRefresh.setRefreshing(false);
        });

        binding.commentsErrorState.setOnRetryClickListener(v -> viewModel.refresh());
    }

    private void observeViewModel() {
        sessionManager.getSessionLiveData().observe(this, state -> {
            if (state != null && state.isLoggedIn) {
                binding.commentInputContainer.setVisibility(View.VISIBLE);
                binding.commentGuestPrompt.setVisibility(View.GONE);
            } else {
                binding.commentInputContainer.setVisibility(View.GONE);
                binding.commentGuestPrompt.setVisibility(View.VISIBLE);
            }
        });

        viewModel.getReplyingToLiveData().observe(this, replyingTo -> {
            if (replyingTo != null) {
                binding.replyingToContainer.setVisibility(View.VISIBLE);
                String name = replyingTo.author != null && replyingTo.author.name != null ? replyingTo.author.name : "Thành viên";
                binding.replyingToText.setText("Đang trả lời @" + name);
            } else {
                binding.replyingToContainer.setVisibility(View.GONE);
            }
        });

        PageLoader<CommentResource> pageLoader = viewModel.getRootCommentsPageLoader();
        if (pageLoader != null) {
            pageLoader.attachRecyclerView(binding.commentsRecyclerView);

            pageLoader.getItemsLiveData().observe(this, items -> {
                if (items != null) {
                    commentsAdapter.setComments(items);
                }
            });

            pageLoader.getStateLiveData().observe(this, state -> {
                if (state == null) return;
                binding.commentsLoadingSkeleton.setVisibility(state == PageLoader.State.INITIAL_LOADING ? View.VISIBLE : View.GONE);
                binding.commentsEmptyState.setVisibility(state == PageLoader.State.EMPTY ? View.VISIBLE : View.GONE);
                binding.commentsErrorState.setVisibility(state == PageLoader.State.ERROR ? View.VISIBLE : View.GONE);
                binding.commentsRecyclerView.setVisibility((state == PageLoader.State.LOADED || state == PageLoader.State.LOADING_MORE || state == PageLoader.State.NO_MORE) ? View.VISIBLE : View.GONE);
            });
        }
    }

    private void handleSendComment() {
        String content = binding.commentInputEditText.getText() != null ? binding.commentInputEditText.getText().toString().trim() : "";
        if (content.isEmpty()) {
            Toast.makeText(this, "Vui lòng nhập nội dung bình luận", Toast.LENGTH_SHORT).show();
            return;
        }

        hideSoftKeyboard(binding.commentInputEditText);

        viewModel.createComment(content).observe(this, result -> {
            if (result != null && result.isSuccess() && result.data != null) {
                Toast.makeText(this, "Đã gửi bình luận", Toast.LENGTH_SHORT).show();
                binding.commentInputEditText.setText("");
                viewModel.clearReplyingTo();

                if (result.data.parentId != null) {
                    commentsAdapter.insertReplyUnderParent(result.data.parentId, result.data);
                } else {
                    commentsAdapter.insertCommentAtTop(result.data);
                }

                binding.commentsEmptyState.setVisibility(View.GONE);
                binding.commentsRecyclerView.setVisibility(View.VISIBLE);
            } else if (result != null && result.isError()) {
                Toast.makeText(this, result.error != null ? result.error.getMessage() : "Không thể gửi bình luận", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadCommentReplies(CommentResource comment, int parentPosition) {
        PageLoader.PageFetcher<CommentResource> repliesFetcher = viewModel.createRepliesPageFetcher(comment.id);
        repliesFetcher.fetch(1, new PageLoader.PageCallback<CommentResource>() {
            @Override
            public void onSuccess(@NonNull PageLoader.PageResult<CommentResource> result) {
                if (result.items != null && !result.items.isEmpty()) {
                    commentsAdapter.insertReplies(parentPosition, result.items);
                } else {
                    Toast.makeText(CommentsActivity.this, "Không có phản hồi thêm", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onError(@NonNull com.socialapp.core.network.AppError error) {
                Toast.makeText(CommentsActivity.this, error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showCommentMenu(CommentResource comment, View anchor) {
        PopupMenu popupMenu = new PopupMenu(this, anchor);
        UserResource currentUser = sessionManager.getCurrentUser();

        boolean isAuthor = currentUser != null && comment.author != null && currentUser.id == comment.author.id;

        if (isAuthor) {
            popupMenu.getMenu().add("Sửa");
            popupMenu.getMenu().add("Xóa");
        } else {
            popupMenu.getMenu().add("Báo cáo");
        }

        popupMenu.setOnMenuItemClickListener(item -> {
            if ("Sửa".equals(item.getTitle())) {
                showEditCommentDialog(comment);
                return true;
            } else if ("Xóa".equals(item.getTitle())) {
                ConfirmDialog confirmDialog = new ConfirmDialog(
                        this,
                        "Xóa bình luận",
                        "Bạn có chắc chắn muốn xóa bình luận này?",
                        () -> {
                            viewModel.deleteComment(comment.id).observe(this, result -> {
                                if (result != null && result.isSuccess()) {
                                    Toast.makeText(this, "Đã xóa bình luận", Toast.LENGTH_SHORT).show();
                                    commentsAdapter.removeComment(comment.id);
                                } else if (result != null && result.isError()) {
                                    Toast.makeText(this, result.error != null ? result.error.getMessage() : "Xóa thất bại", Toast.LENGTH_SHORT).show();
                                }
                            });
                        }
                );
                confirmDialog.show();
                return true;
            } else if ("Báo cáo".equals(item.getTitle())) {
                if (sessionManager.isLoggedIn()) {
                    ReportSheetBottomSheet reportSheet = ReportSheetBottomSheet.newInstance();
                    reportSheet.setOnReportSubmitListener(reason -> {
                        viewModel.reportComment(comment.id, reason).observe(this, result -> {
                            if (result != null && result.isSuccess()) {
                                Toast.makeText(this, result.data != null ? result.data : "Đã gửi báo cáo", Toast.LENGTH_SHORT).show();
                            } else if (result != null && result.isError()) {
                                Toast.makeText(this, result.error != null ? result.error.getMessage() : "Báo cáo thất bại", Toast.LENGTH_SHORT).show();
                            }
                        });
                    });
                    reportSheet.show(getSupportFragmentManager(), "ReportCommentSheet");
                } else {
                    sessionManager.requireLogin(() -> showCommentMenu(comment, anchor), this);
                }
                return true;
            }
            return false;
        });

        popupMenu.show();
    }

    private void showEditCommentDialog(CommentResource comment) {
        EditText input = new EditText(this);
        input.setText(comment.content != null ? comment.content : "");
        input.setSelection(input.getText().length());

        new AlertDialog.Builder(this)
                .setTitle("Sửa bình luận")
                .setView(input)
                .setPositiveButton("Lưu", (dialog, which) -> {
                    String newContent = input.getText().toString().trim();
                    if (!newContent.isEmpty()) {
                        viewModel.updateComment(comment.id, newContent).observe(this, result -> {
                            if (result != null && result.isSuccess()) {
                                Toast.makeText(this, "Đã cập nhật bình luận", Toast.LENGTH_SHORT).show();
                                commentsAdapter.updateCommentContent(comment.id, newContent);
                            } else if (result != null && result.isError()) {
                                Toast.makeText(this, result.error != null ? result.error.getMessage() : "Cập nhật thất bại", Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void showSoftKeyboard(View view) {
        view.requestFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    private void hideSoftKeyboard(View view) {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }
}
