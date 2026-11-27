package com.socialapp.feature.post;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.widget.PopupMenu;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.browser.customtabs.CustomTabsIntent;
import androidx.core.view.OneShotPreDrawListener;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.socialapp.MyApplication;
import com.socialapp.core.di.AppContainer;
import com.socialapp.core.navigation.Navigator;
import com.socialapp.core.network.dto.PostResource;
import com.socialapp.core.network.dto.UserResource;
import com.socialapp.core.prefetch.ScreenId;
import com.socialapp.core.session.SessionManager;
import com.socialapp.core.telemetry.NavigationTiming;
import com.socialapp.core.telemetry.TelemetryActivity;
import com.socialapp.core.ui.ReportSheetBottomSheet;
import com.socialapp.core.ui.blocks.adapter.HighRefreshRateHelper;
import com.socialapp.core.ui.blocks.adapter.PostDetailBlockAssembler;
import com.socialapp.core.ui.blocks.adapter.RecyclerView120HzTuner;
import com.socialapp.core.ui.blocks.adapter.UnifiedBlockAdapter;
import com.socialapp.core.ui.blocks.model.ContentBlock;
import com.socialapp.core.ui.blocks.parser.ArticleAstCache;
import com.socialapp.databinding.ActivityPostDetailBinding;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * PostDetailActivity rendered on the unified Pillar 4.1 flattened RecyclerView architecture:
 * - Zero WebView overhead and zero Html.fromHtml() on the Main Thread
 * - Streaming HTML AST parsed off-main-thread and cached in ArticleAstCache
 * - Off-main-thread StaticLayout precomputation + API 29+ RenderNode caching in body blocks
 * - Partial DiffUtil payloads for Follow and Vote state changes with zero body rebinds
 */
public class PostDetailActivity extends TelemetryActivity {

    public static final String EXTRA_POST_ID = "EXTRA_POST_ID";

    private ActivityPostDetailBinding binding;
    private PostDetailViewModel viewModel;
    private SessionManager sessionManager;
    private AppContainer appContainer;
    private UnifiedBlockAdapter unifiedBlockAdapter;
    private int postId = -1;
    private NavigationTiming contentTiming;
    private long contentRevision;
    private boolean awaitingArticle;
    private boolean contentReady;

    private final AtomicInteger renderGeneration = new AtomicInteger(0);
    @Nullable
    private PostResource latestRenderedPost;
    @NonNull
    private List<ContentBlock> latestBodyBlocks = Collections.emptyList();
    @NonNull
    private String cachedPlainTextSummary = "";
    private int cachedPostId = -1;
    private int cachedHtmlHash = 0;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        HighRefreshRateHelper.requestHighRefreshRate(this);
        binding = ActivityPostDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        appContainer = MyApplication.getAppContainer(this);
        sessionManager = appContainer.getSessionManager();

        if (getIntent() != null) {
            if (savedInstanceState == null && getIntent().hasExtra(Navigator.EXTRA_CONTENT_REQUEST_MS)) {
                contentTiming = new NavigationTiming(getIntent().getLongExtra(Navigator.EXTRA_CONTENT_REQUEST_MS, 0));
            }
            getIntent().removeExtra(Navigator.EXTRA_CONTENT_REQUEST_MS);
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

        PostDetailRepository repository = new PostDetailRepository(appContainer.getSocialAppApiService());
        viewModel = new ViewModelProvider(this, new PostDetailViewModelFactory(repository)).get(PostDetailViewModel.class);

        setupUI();
        observeViewModel();

        viewModel.loadPostDetail(postId);
    }

    private void setupUI() {
        binding.postDetailToolbar.setNavigationOnClickListener(v -> finish());

        unifiedBlockAdapter = new UnifiedBlockAdapter();
        unifiedBlockAdapter.setDetailCallbacks(createDetailCallbacks());

        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        binding.postDetailRecyclerView.setLayoutManager(layoutManager);
        binding.postDetailRecyclerView.setRecycledViewPool(appContainer.getUnifiedBlockViewPool());
        binding.postDetailRecyclerView.setAdapter(unifiedBlockAdapter);
        RecyclerView120HzTuner.tuneReadingList(binding.postDetailRecyclerView, layoutManager, false);

        setupReadingProgressBar();

        binding.btnSharePost.setOnClickListener(v -> sharePost());
        binding.btnOverflowMenu.setOnClickListener(this::showOverflowMenu);

        binding.postDetailSwipeRefresh.setOnRefreshListener(() -> {
            viewModel.loadPostDetail(postId);
            binding.postDetailSwipeRefresh.setRefreshing(false);
        });

        binding.postDetailErrorState.setOnRetryClickListener(v -> viewModel.loadPostDetail(postId));
    }

    @NonNull
    private UnifiedBlockAdapter.DetailCallbacks createDetailCallbacks() {
        return new UnifiedBlockAdapter.DetailCallbacks() {
            @Override
            public void onAuthorClick(int authorId) {
                if (authorId > 0) {
                    Navigator.navigateToUserProfile(PostDetailActivity.this, authorId);
                }
            }

            @Override
            public void onFollowClick() {
                if (sessionManager.isLoggedIn()) {
                    viewModel.toggleFollowAuthor();
                } else {
                    sessionManager.requireLogin(() -> viewModel.toggleFollowAuthor(), PostDetailActivity.this);
                }
            }

            @Override
            public void onLinkClick(@NonNull String url) {
                openCustomTab(url);
            }

            @Override
            public void onSourceClick(@NonNull String sourceUrl) {
                openCustomTab(sourceUrl);
            }

            @Override
            public void onUpvoteClick() {
                if (sessionManager.isLoggedIn()) {
                    viewModel.upvote();
                } else {
                    sessionManager.requireLogin(() -> viewModel.upvote(), PostDetailActivity.this);
                }
            }

            @Override
            public void onDownvoteClick() {
                if (sessionManager.isLoggedIn()) {
                    viewModel.downvote();
                } else {
                    sessionManager.requireLogin(() -> viewModel.downvote(), PostDetailActivity.this);
                }
            }

            @Override
            public void onCommentsClick(int targetPostId) {
                Navigator.navigateToPostComments(
                        PostDetailActivity.this,
                        targetPostId > 0 ? targetPostId : postId
                );
            }

            @Override
            public void onFactCheckClick() {
                navigateToFactCheck();
            }
        };
    }

    private void navigateToFactCheck() {
        PostResource post = viewModel.getCurrentPost();
        if (post == null || post.thumbnailUrl == null || post.thumbnailUrl.trim().isEmpty()) {
            return;
        }
        String summary = cachedPlainTextSummary;
        if (summary.isEmpty() && post.contentHtml != null && !post.contentHtml.trim().isEmpty()) {
            ArticleAstCache.ParsedArticle cached = appContainer.getArticleAstCache().getIfPresent(post.id, post.contentHtml);
            if (cached != null) {
                summary = cached.plainTextSummary;
            }
        }
        String title = post.title != null ? post.title : "";
        String caption = title + (summary.isEmpty() ? "" : "\n" + summary);
        String route = "factcheck?imageUrl=" + Uri.encode(post.thumbnailUrl)
                + "&postId=" + post.id
                + "&caption=" + Uri.encode(caption);
        Navigator.navigateTo(this, route);
    }

    private void setupReadingProgressBar() {
        binding.postDetailRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                updateReadingProgress();
            }
        });
    }

    private void updateReadingProgress() {
        int offset = binding.postDetailRecyclerView.computeVerticalScrollOffset();
        int range = binding.postDetailRecyclerView.computeVerticalScrollRange();
        int extent = binding.postDetailRecyclerView.computeVerticalScrollExtent();
        int totalScrollableDistance = range - extent;
        if (totalScrollableDistance > 0) {
            int progress = Math.round(((float) offset / totalScrollableDistance) * 100f);
            binding.readingProgressBar.setProgress(Math.min(100, Math.max(0, progress)));
        } else {
            binding.readingProgressBar.setProgress(0);
        }
    }

    private void openCustomTab(@NonNull String url) {
        String trimmed = url.trim();
        if (trimmed.isEmpty()) {
            return;
        }
        Uri uri = Uri.parse(trimmed);
        if (uri.getScheme() == null) {
            uri = Uri.parse("https://" + trimmed);
        }
        try {
            CustomTabsIntent intent = new CustomTabsIntent.Builder().build();
            intent.launchUrl(this, uri);
        } catch (Exception e) {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                startActivity(intent);
            } catch (Exception ignored) {
                Toast.makeText(this, "Không thể mở liên kết", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void observeViewModel() {
        viewModel.getPostDetailLiveData().observe(this, result -> {
            if (result == null) return;

            if (result.isLoading()) {
                renderGeneration.incrementAndGet();
                resetContentMeasurement();
                binding.postDetailLoadingSkeleton.setVisibility(View.VISIBLE);
                binding.postDetailErrorState.setVisibility(View.GONE);
                binding.postDetailRecyclerView.setVisibility(View.GONE);
            } else if (result.isSuccess() && result.data != null) {
                resetContentMeasurement();
                bindPostData(result.data);
            } else if (result.isError()) {
                renderGeneration.incrementAndGet();
                resetContentMeasurement();
                binding.postDetailLoadingSkeleton.setVisibility(View.GONE);
                binding.postDetailRecyclerView.setVisibility(View.GONE);
                binding.postDetailErrorState.setVisibility(View.VISIBLE);
                if (result.error != null) {
                    binding.postDetailErrorState.setErrorMessage(result.error.getMessage());
                }
            }
        });

        viewModel.getIsFollowingLiveData().observe(this, isFollowing -> {
            if (latestRenderedPost == null) {
                return;
            }
            List<ContentBlock> current = unifiedBlockAdapter.getCurrentBlocks();
            if (!current.isEmpty()) {
                unifiedBlockAdapter.submitBlocks(
                        PostDetailBlockAssembler.withFollowState(current, Boolean.TRUE.equals(isFollowing))
                );
            }
        });
    }

    private void resetContentMeasurement() {
        contentRevision++;
        awaitingArticle = false;
        contentReady = false;
    }

    private void scheduleContentMeasurement() {
        NavigationTiming timing = contentTiming;
        View content = binding.postDetailRecyclerView;
        if (timing == null || !contentReady || !content.isAttachedToWindow()) return;
        long revision = contentRevision;
        OneShotPreDrawListener.add(content, () -> content.post(() -> {
            if (timing != contentTiming || contentRevision != revision || !contentReady || !content.isShown()
                    || isFinishing() || isDestroyed() || !getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.RESUMED)) return;
            long now = SystemClock.elapsedRealtime();
            if (!timing.markReady(now)) return;
            contentTiming = null;
            AppContainer app = MyApplication.getAppContainer(this);
            app.getPerformanceTelemetry().contentReady(ScreenId.POST_DETAIL, timing,
                    app.getNavigationObserver().getContentNavigation(this, ScreenId.POST_DETAIL, timing.startedAtMs), now);
        }));
        content.invalidate();
    }

    private void bindPostData(@NonNull PostResource post) {
        int htmlHash = post.contentHtml != null ? post.contentHtml.hashCode() : 0;

        // Fast reuse during optimistic Vote/Follow updates on the currently open article
        if (cachedPostId == post.id && cachedHtmlHash == htmlHash && latestRenderedPost != null) {
            renderGeneration.incrementAndGet();
            submitAssembledBlocks(post, latestBodyBlocks, cachedPlainTextSummary, htmlHash);
            return;
        }

        // Fast hit from Repository off-main-thread AST warmup
        ArticleAstCache.ParsedArticle cached = appContainer.getArticleAstCache().getIfPresent(post.id, post.contentHtml);
        if (cached != null) {
            renderGeneration.incrementAndGet();
            submitAssembledBlocks(post, cached.bodyBlocks, cached.plainTextSummary, htmlHash);
            return;
        }

        // Cold fallback: parse HTML AST + shape StaticLayouts off the Main Thread
        final int generation = renderGeneration.incrementAndGet();
        final int rvWidth = binding.postDetailRecyclerView.getWidth();
        final int targetWidth = rvWidth > 0 ? rvWidth : getResources().getDisplayMetrics().widthPixels;
        final float density = getResources().getDisplayMetrics().density;
        final float fontScale = getResources().getConfiguration().fontScale;

        try {
            appContainer.getBlockPrecomputer().submitBackgroundTask(() -> {
                ArticleAstCache.ParsedArticle parsed =
                        appContainer.getArticleAstCache().getOrParse(post.id, post.contentHtml);
                if (!appContainer.getBlockWarmupEngine().isMemoryPressureActive()) {
                    appContainer.getBlockPrecomputer().precomputeBlocksSync(
                            parsed.bodyBlocks, targetWidth, density, fontScale
                    );
                }
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed() || generation != renderGeneration.get()) {
                        return;
                    }
                    submitAssembledBlocks(post, parsed.bodyBlocks, parsed.plainTextSummary, htmlHash);
                });
            });
        } catch (Throwable ignored) {
            ArticleAstCache.ParsedArticle parsed =
                    appContainer.getArticleAstCache().getOrParse(post.id, post.contentHtml);
            submitAssembledBlocks(post, parsed.bodyBlocks, parsed.plainTextSummary, htmlHash);
        }
    }

    private void submitAssembledBlocks(
            @NonNull PostResource post,
            @NonNull List<ContentBlock> bodyBlocks,
            @NonNull String plainTextSummary,
            int htmlHash) {
        this.latestRenderedPost = post;
        this.cachedPostId = post.id;
        this.cachedHtmlHash = htmlHash;
        this.latestBodyBlocks = bodyBlocks;
        this.cachedPlainTextSummary = plainTextSummary;

        UserResource currentUser = sessionManager.getCurrentUser();
        int currentUserId = currentUser != null ? currentUser.id : -1;
        Boolean followingVal = viewModel.getIsFollowingLiveData().getValue();
        boolean isFollowing = followingVal != null ? followingVal : post.isFollowingAuthor;

        List<ContentBlock> assembled = PostDetailBlockAssembler.assemble(
                post,
                currentUserId,
                isFollowing,
                bodyBlocks,
                null
        );

        binding.postDetailLoadingSkeleton.setVisibility(View.GONE);
        binding.postDetailErrorState.setVisibility(View.GONE);
        binding.postDetailRecyclerView.setVisibility(View.VISIBLE);

        unifiedBlockAdapter.submitBlocks(assembled);
        if (contentTiming != null) {
            contentReady = true;
            scheduleContentMeasurement();
        }
        binding.postDetailRecyclerView.post(() -> {
            if (!isFinishing() && !isDestroyed()) {
                updateReadingProgress();
            }
        });
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

    @Override
    protected void onResume() {
        super.onResume();
        scheduleContentMeasurement();
    }

    @Override
    protected void onDestroy() {
        renderGeneration.incrementAndGet();
        if (unifiedBlockAdapter != null) {
            unifiedBlockAdapter.setDetailCallbacks(null);
        }
        if (binding != null) {
            binding.postDetailRecyclerView.setAdapter(null);
        }
        super.onDestroy();
    }
}
