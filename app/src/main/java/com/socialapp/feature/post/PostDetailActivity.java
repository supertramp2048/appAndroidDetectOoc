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
import androidx.core.content.ContextCompat;
import androidx.core.widget.NestedScrollView;
import androidx.lifecycle.ViewModelProvider;

import android.content.res.ColorStateList;

import com.bumptech.glide.Glide;
import com.socialapp.MyApplication;
import com.socialapp.R;
import com.socialapp.core.navigation.Navigator;
import com.socialapp.core.network.dto.PostResource;
import com.socialapp.core.network.dto.UserResource;
import com.socialapp.core.session.SessionManager;
import com.socialapp.core.ui.ReportSheetBottomSheet;
import com.socialapp.core.util.DateTimeUtils;
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
        setupReadingProgressBar();

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

    private void setupReadingProgressBar() {
        binding.postDetailContentContainer.setOnScrollChangeListener((NestedScrollView.OnScrollChangeListener) (v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
            if (v.getChildCount() > 0) {
                int totalScrollableDistance = v.getChildAt(0).getMeasuredHeight() - v.getMeasuredHeight();
                if (totalScrollableDistance > 0) {
                    int progress = (int) (((float) scrollY / totalScrollableDistance) * 100);
                    binding.readingProgressBar.setProgress(Math.min(100, Math.max(0, progress)));
                } else {
                    binding.readingProgressBar.setProgress(0);
                }
            }
        });
    }

    private void setupWebView() {
        binding.postDetailWebView.setBackgroundColor(0); // Transparent background
        binding.postDetailWebView.getSettings().setJavaScriptEnabled(false);
        binding.postDetailWebView.getSettings().setDefaultTextEncodingName("UTF-8");
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
        binding.postDetailTime.setText(DateTimeUtils.formatFullDateTime(post.createdAt));

        if (post.category != null && post.category.name != null) {
            binding.postDetailCategory.setText(post.category.name);
            binding.postDetailCategory.setVisibility(View.VISIBLE);
        } else {
            binding.postDetailCategory.setVisibility(View.GONE);
        }

        if (post.author != null) {
            binding.postDetailAuthorName.setText(post.author.name != null ? "Bởi " + post.author.name : "Nặc danh");
            binding.postDetailAuthorAvatar.loadAvatar(post.author.getAvatarUrl());

            View.OnClickListener authorClickListener = v -> {
                if (post.author != null && post.author.id > 0) {
                    Navigator.navigateToUserProfile(PostDetailActivity.this, post.author.id);
                }
            };
            binding.postDetailAuthorAvatar.setOnClickListener(authorClickListener);
            binding.postDetailAuthorName.setOnClickListener(authorClickListener);

            UserResource currentUser = sessionManager.getCurrentUser();
            if (currentUser != null && currentUser.id == post.author.id) {
                binding.btnFollowAuthor.setVisibility(View.GONE);
            } else {
                binding.btnFollowAuthor.setVisibility(View.VISIBLE);
            }
        }

        if (post.thumbnailUrl != null && !post.thumbnailUrl.trim().isEmpty()) {
            binding.postDetailThumbnailCard.setVisibility(View.VISIBLE);
            binding.postDetailThumbnail.setVisibility(View.VISIBLE);
            Glide.with(this).load(post.thumbnailUrl).centerCrop().into(binding.postDetailThumbnail);

            binding.btnFactcheck.setVisibility(View.VISIBLE);
        } else {
            binding.postDetailThumbnailCard.setVisibility(View.GONE);
            binding.postDetailThumbnail.setVisibility(View.GONE);
            binding.btnFactcheck.setVisibility(View.GONE);
        }

        // HTML WebView with editorial typography styling
        if (post.contentHtml != null && !post.contentHtml.trim().isEmpty()) {
            String htmlData = buildEditorialHtml(post.contentHtml);
            binding.postDetailWebView.setVisibility(View.VISIBLE);
            binding.postDetailWebView.loadDataWithBaseURL(null, htmlData, "text/html", "UTF-8", null);
        } else {
            binding.postDetailWebView.setVisibility(View.GONE);
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
        int primaryColor = ContextCompat.getColor(this, R.color.app_primary);
        int dangerColor = ContextCompat.getColor(this, R.color.app_danger);
        int secondaryTextColor = ContextCompat.getColor(this, R.color.app_text_secondary);
        int primaryTextColor = ContextCompat.getColor(this, R.color.app_text_primary);

        if (post.userVote == 1) {
            binding.btnUpvote.setAlpha(1.0f);
            binding.btnUpvote.setText("▲");
            binding.btnUpvote.setTextColor(primaryColor);

            binding.btnDownvote.setAlpha(0.6f);
            binding.btnDownvote.setText("▼");
            binding.btnDownvote.setTextColor(secondaryTextColor);

            binding.postDetailVoteScore.setTextColor(primaryColor);
        } else if (post.userVote == -1) {
            binding.btnUpvote.setAlpha(0.6f);
            binding.btnUpvote.setText("▲");
            binding.btnUpvote.setTextColor(secondaryTextColor);

            binding.btnDownvote.setAlpha(1.0f);
            binding.btnDownvote.setText("▼");
            binding.btnDownvote.setTextColor(dangerColor);

            binding.postDetailVoteScore.setTextColor(dangerColor);
        } else {
            binding.btnUpvote.setAlpha(1.0f);
            binding.btnUpvote.setText("▲");
            binding.btnUpvote.setTextColor(secondaryTextColor);

            binding.btnDownvote.setAlpha(1.0f);
            binding.btnDownvote.setText("▼");
            binding.btnDownvote.setTextColor(secondaryTextColor);

            binding.postDetailVoteScore.setTextColor(primaryTextColor);
        }

        binding.btnComments.setText("💬 " + post.commentsCount + " bình luận");
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

    @NonNull
    private String buildEditorialHtml(@NonNull String contentHtml) {
        return "<!DOCTYPE html><html><head><meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no\"><style>"
                + "* { box-sizing: border-box; }"
                + "body {"
                + "  font-family: Georgia, 'Times New Roman', Cambria, serif;"
                + "  font-size: 18px;"
                + "  line-height: 1.85;"
                + "  color: #17212B;"
                + "  background-color: transparent;"
                + "  margin: 0;"
                + "  padding: 4px 0 24px 0;"
                + "  word-wrap: break-word;"
                + "  letter-spacing: 0.01em;"
                + "}"
                + "p {"
                + "  margin: 0 0 18px 0;"
                + "  text-align: left;"
                + "}"
                + "p:first-of-type, .lead {"
                + "  font-size: 19px;"
                + "  font-weight: normal;"
                + "  color: #17212B;"
                + "  line-height: 1.8;"
                + "  margin-bottom: 22px;"
                + "}"
                + "img {"
                + "  max-width: 100% !important;"
                + "  height: auto !important;"
                + "  display: block;"
                + "  margin: 20px auto 8px auto;"
                + "  border-radius: 4px;"
                + "  border: 1px solid #D8E0EA;"
                + "}"
                + "figcaption, .caption, em {"
                + "  display: block;"
                + "  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;"
                + "  font-size: 13px;"
                + "  font-style: italic;"
                + "  color: #64748B;"
                + "  text-align: left;"
                + "  margin: 6px auto 18px auto;"
                + "  line-height: 1.5;"
                + "}"
                + "h1, h2, h3, h4 {"
                + "  font-family: Georgia, 'Times New Roman', serif;"
                + "  color: #17212B;"
                + "  font-weight: 700;"
                + "  margin: 28px 0 12px 0;"
                + "  line-height: 1.35;"
                + "}"
                + "h2 { font-size: 21px; }"
                + "h3 { font-size: 19px; }"
                + "blockquote {"
                + "  margin: 22px 0;"
                + "  padding: 14px 20px;"
                + "  background-color: #F8FAFC;"
                + "  border-left: 3px solid #1E5AA8;"
                + "  border-radius: 0 4px 4px 0;"
                + "  font-family: Georgia, serif;"
                + "  font-style: italic;"
                + "  font-size: 18px;"
                + "  line-height: 1.7;"
                + "  color: #334155;"
                + "}"
                + "table {"
                + "  width: 100% !important;"
                + "  border-collapse: collapse;"
                + "  margin: 20px 0;"
                + "  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;"
                + "  font-size: 14px;"
                + "}"
                + "th, td {"
                + "  border: 1px solid #D8E0EA;"
                + "  padding: 10px 12px;"
                + "  text-align: left;"
                + "}"
                + "th {"
                + "  background-color: #EEF2F6;"
                + "  font-weight: 600;"
                + "  color: #17212B;"
                + "}"
                + "pre, code {"
                + "  background: #EEF2F6;"
                + "  padding: 4px 8px;"
                + "  border-radius: 4px;"
                + "  font-size: 14px;"
                + "  color: #17212B;"
                + "  overflow-x: auto;"
                + "}"
                + "a {"
                + "  color: #1E5AA8;"
                + "  text-decoration: underline;"
                + "  font-weight: 500;"
                + "}"
                + "</style></head><body>"
                + contentHtml
                + "</body></html>";
    }
}
