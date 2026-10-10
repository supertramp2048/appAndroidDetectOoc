package com.socialapp.feature.post;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Html;
import android.view.View;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.PopupMenu;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.browser.customtabs.CustomTabsIntent;
import androidx.lifecycle.ViewModelProvider;

import com.bumptech.glide.Glide;
import com.socialapp.MyApplication;
import com.socialapp.core.navigation.Navigator;
import com.socialapp.core.network.dto.PostResource;
import com.socialapp.core.network.dto.UserResource;
import com.socialapp.core.session.SessionManager;
import com.socialapp.core.ui.ReportSheetBottomSheet;
import com.socialapp.databinding.ActivityPostDetailBinding;

public class PostDetailActivity extends AppCompatActivity {

    public static final String EXTRA_POST_ID = "EXTRA_POST_ID";

    private ActivityPostDetailBinding binding;
    private PostDetailViewModel viewModel;
    private SessionManager sessionManager;
    private int postId = -1;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityPostDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        sessionManager = MyApplication.getAppContainer(this).getSessionManager();

        if (getIntent() != null) {
            postId = getIntent().getIntExtra(EXTRA_POST_ID, -1);
            if (postId == -1 && getIntent().getData() != null) {
                Uri data = getIntent().getData();
                String path = data.getPath();
                if (path != null && path.contains("/post/")) {
                    try {
                        String idStr = path.substring(path.lastIndexOf("/") + 1);
                        postId = Integer.parseInt(idStr);
                    } catch (Exception ignored) {
                    }
                }
            }
        }

        if (postId == -1) {
            Toast.makeText(this, "Không tìm thấy bài viết", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        PostDetailRepository repository = new PostDetailRepository(MyApplication.getAppContainer(this).getSocialAppApiService());
        viewModel = new ViewModelProvider(this, new PostDetailViewModelFactory(repository)).get(PostDetailViewModel.class);

        setupUI();
        observeViewModel();

        viewModel.loadPostDetail(postId);
    }

    private void setupUI() {
        binding.postDetailToolbar.setNavigationOnClickListener(v -> finish());

        setupWebView();

        binding.btnSharePost.setOnClickListener(v -> sharePost());
        binding.btnOverflowMenu.setOnClickListener(this::showOverflowMenu);

        binding.btnUpvote.setOnClickListener(v -> {
            if (sessionManager.isLoggedIn()) {
                viewModel.upvote();
            } else {
                sessionManager.requireLogin(() -> viewModel.upvote(), this);
            }
        });

        binding.btnDownvote.setOnClickListener(v -> {
            if (sessionManager.isLoggedIn()) {
                viewModel.downvote();
            } else {
                sessionManager.requireLogin(() -> viewModel.downvote(), this);
            }
        });

        binding.btnFollowAuthor.setOnClickListener(v -> {
            if (sessionManager.isLoggedIn()) {
                viewModel.toggleFollowAuthor();
            } else {
                sessionManager.requireLogin(() -> viewModel.toggleFollowAuthor(), this);
            }
        });

        binding.btnComments.setOnClickListener(v -> Navigator.navigateToPostComments(this, postId));

        binding.btnFactcheck.setOnClickListener(v -> {
            PostResource post = viewModel.getCurrentPost();
            if (post != null && post.thumbnailUrl != null && !post.thumbnailUrl.trim().isEmpty()) {
                String plainTextSummary = "";
                if (post.contentHtml != null && !post.contentHtml.trim().isEmpty()) {
                    String fullText = Html.fromHtml(post.contentHtml, Html.FROM_HTML_MODE_LEGACY).toString().trim();
                    if (fullText.length() > 300) {
                        plainTextSummary = fullText.substring(0, 300) + "...";
                    } else {
                        plainTextSummary = fullText;
                    }
                }
                String caption = (post.title != null ? post.title : "") + (plainTextSummary.isEmpty() ? "" : "\n" + plainTextSummary);
                String route = "factcheck?imageUrl=" + Uri.encode(post.thumbnailUrl) + "&postId=" + post.id + "&caption=" + Uri.encode(caption);
                Navigator.navigateTo(this, route);
            }
        });

        binding.postDetailSwipeRefresh.setOnRefreshListener(() -> {
            viewModel.loadPostDetail(postId);
            binding.postDetailSwipeRefresh.setRefreshing(false);
        });

        binding.postDetailErrorState.setOnRetryClickListener(v -> viewModel.loadPostDetail(postId));
    }

    private void setupWebView() {
        binding.postDetailWebView.getSettings().setJavaScriptEnabled(false);
        binding.postDetailWebView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (request != null && request.getUrl() != null) {
                    openCustomTab(request.getUrl().toString());
                    return true;
                }
                return super.shouldOverrideUrlLoading(view, request);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (url != null) {
                    openCustomTab(url);
                    return true;
                }
                return super.shouldOverrideUrlLoading(view, url);
            }
        });
    }

    private void openCustomTab(String url) {
        try {
            CustomTabsIntent intent = new CustomTabsIntent.Builder().build();
            intent.launchUrl(this, Uri.parse(url));
        } catch (Exception e) {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
        }
    }

    private void observeViewModel() {
        viewModel.getPostDetailLiveData().observe(this, result -> {
            if (result == null) return;

            if (result.isLoading()) {
                binding.postDetailLoadingSkeleton.setVisibility(View.VISIBLE);
                binding.postDetailErrorState.setVisibility(View.GONE);
                binding.postDetailContentContainer.setVisibility(View.GONE);
            } else if (result.isSuccess() && result.data != null) {
                binding.postDetailLoadingSkeleton.setVisibility(View.GONE);
                binding.postDetailErrorState.setVisibility(View.GONE);
                binding.postDetailContentContainer.setVisibility(View.VISIBLE);
                bindPostData(result.data);
            } else if (result.isError()) {
                binding.postDetailLoadingSkeleton.setVisibility(View.GONE);
                binding.postDetailContentContainer.setVisibility(View.GONE);
                binding.postDetailErrorState.setVisibility(View.VISIBLE);
                if (result.error != null) {
                    binding.postDetailErrorState.setErrorMessage(result.error.getMessage());
                }
            }
        });

        viewModel.getIsFollowingLiveData().observe(this, isFollowing -> {
            if (Boolean.TRUE.equals(isFollowing)) {
                binding.btnFollowAuthor.setText("Đang theo dõi");
            } else {
                binding.btnFollowAuthor.setText("Theo dõi");
            }
        });
    }

    private void bindPostData(@NonNull PostResource post) {
        binding.postDetailTitle.setText(post.title != null ? post.title : "");
        binding.postDetailTime.setText(post.createdAt != null ? post.createdAt : "");

        if (post.category != null && post.category.name != null) {
            binding.postDetailCategory.setText(post.category.name);
            binding.postDetailCategory.setVisibility(View.VISIBLE);
        } else {
            binding.postDetailCategory.setVisibility(View.GONE);
        }

        if (post.author != null) {
            binding.postDetailAuthorName.setText(post.author.name != null ? post.author.name : "Nặc danh");
            binding.postDetailAuthorAvatar.loadAvatar(post.author.getAvatarUrl());

            UserResource currentUser = sessionManager.getCurrentUser();
            if (currentUser != null && currentUser.id == post.author.id) {
                binding.btnFollowAuthor.setVisibility(View.GONE);
            } else {
                binding.btnFollowAuthor.setVisibility(View.VISIBLE);
            }
        }

        if (post.thumbnailUrl != null && !post.thumbnailUrl.trim().isEmpty()) {
            binding.postDetailThumbnail.setVisibility(View.VISIBLE);
            Glide.with(this).load(post.thumbnailUrl).centerCrop().into(binding.postDetailThumbnail);

            binding.btnFactcheck.setVisibility(View.VISIBLE);
        } else {
            binding.postDetailThumbnail.setVisibility(View.GONE);
            binding.btnFactcheck.setVisibility(View.GONE);
        }

        // HTML WebView
        if (post.contentHtml != null) {
            String htmlData = "<html><head><style>" +
                    "body { font-family: sans-serif; margin: 0; padding: 0; color: #333; line-height: 1.6; }" +
                    "img { max-width: 100% !important; height: auto !important; border-radius: 8px; }" +
                    "table { width: 100% !important; border-collapse: collapse; }" +
                    "pre, code { background: #f5f5f5; padding: 8px; overflow-x: auto; }" +
                    "</style></head><body>" + post.contentHtml + "</body></html>";
            binding.postDetailWebView.loadDataWithBaseURL(null, htmlData, "text/html", "UTF-8", null);
        }

        // Source URL
        if (post.sourceName != null && !post.sourceName.trim().isEmpty()) {
            binding.postDetailSourceText.setText("Nguồn: " + post.sourceName);
            binding.postDetailSourceText.setVisibility(View.VISIBLE);
            if (post.sourceUrl != null && !post.sourceUrl.trim().isEmpty()) {
                binding.postDetailSourceText.setOnClickListener(v -> openCustomTab(post.sourceUrl));
            }
        } else {
            binding.postDetailSourceText.setVisibility(View.GONE);
        }

        // Vote score
        binding.postDetailVoteScore.setText(String.valueOf(post.voteScore));
        if (post.userVote == 1) {
            binding.btnUpvote.setAlpha(1.0f);
            binding.btnUpvote.setText("▲ Upvoted");
            binding.btnDownvote.setAlpha(0.5f);
            binding.btnDownvote.setText("▼ Downvote");
        } else if (post.userVote == -1) {
            binding.btnUpvote.setAlpha(0.5f);
            binding.btnUpvote.setText("▲ Upvote");
            binding.btnDownvote.setAlpha(1.0f);
            binding.btnDownvote.setText("▼ Downvoted");
        } else {
            binding.btnUpvote.setAlpha(0.9f);
            binding.btnUpvote.setText("▲ Upvote");
            binding.btnDownvote.setAlpha(0.9f);
            binding.btnDownvote.setText("▼ Downvote");
        }

        binding.btnComments.setText("💬 Bình luận (" + post.commentsCount + ")");
    }

    private void showOverflowMenu(View anchor) {
        PostResource post = viewModel.getCurrentPost();
        if (post == null) return;

        PopupMenu popupMenu = new PopupMenu(this, anchor);
        UserResource currentUser = sessionManager.getCurrentUser();

        boolean isAuthor = currentUser != null && post.author != null && currentUser.id == post.author.id;

        if (isAuthor) {
            popupMenu.getMenu().add("Sửa bài viết");
        } else {
            popupMenu.getMenu().add("Báo cáo bài viết");
        }

        popupMenu.setOnMenuItemClickListener(item -> {
            if ("Sửa bài viết".equals(item.getTitle())) {
                Navigator.navigateTo(this, "post/" + post.id + "/edit");
                return true;
            } else if ("Báo cáo bài viết".equals(item.getTitle())) {
                if (sessionManager.isLoggedIn()) {
                    ReportSheetBottomSheet reportSheet = ReportSheetBottomSheet.newInstance();
                    reportSheet.setOnReportSubmitListener(reason -> {
                        viewModel.reportPost(reason).observe(this, result -> {
                            if (result != null && result.isSuccess()) {
                                Toast.makeText(this, result.data != null ? result.data : "Đã gửi báo cáo", Toast.LENGTH_SHORT).show();
                            } else if (result != null && result.isError()) {
                                Toast.makeText(this, result.error != null ? result.error.getMessage() : "Báo cáo thất bại", Toast.LENGTH_SHORT).show();
                            }
                        });
                    });
                    reportSheet.show(getSupportFragmentManager(), "ReportSheet");
                } else {
                    sessionManager.requireLogin(() -> showOverflowMenu(anchor), this);
                }
                return true;
            }
            return false;
        });

        popupMenu.show();
    }

    private void sharePost() {
        PostResource post = viewModel.getCurrentPost();
        if (post == null) return;

        String shareContent = post.title + "\nhttps://socialappbe-8tjc.onrender.com/posts/" + post.id;
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_SUBJECT, post.title);
        intent.putExtra(Intent.EXTRA_TEXT, shareContent);
        startActivity(Intent.createChooser(intent, "Chia sẻ bài viết qua"));
    }
}
