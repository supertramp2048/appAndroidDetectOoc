package com.socialapp.feature.feed;

import android.content.Context;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.AsyncListDiffer;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestManager;
import com.socialapp.MyApplication;
import com.socialapp.core.di.AppContainer;
import com.socialapp.core.navigation.Navigator;
import com.socialapp.core.network.dto.PostResource;
import com.socialapp.core.ui.AdSlotView;
import com.socialapp.core.ui.blocks.adapter.BlockScrollPreloader;
import com.socialapp.core.ui.blocks.adapter.BlockWarmupEngine;
import com.socialapp.core.ui.blocks.adapter.ContentBlockDiffCallback;
import com.socialapp.core.ui.blocks.adapter.FeedBlockListBuilder;
import com.socialapp.core.ui.blocks.adapter.PooledViewContextHelper;
import com.socialapp.core.ui.blocks.adapter.UnifiedBlockViewPool;
import com.socialapp.core.ui.blocks.layout.BlockLayoutCache;
import com.socialapp.core.ui.blocks.layout.BlockPrecomputer;
import com.socialapp.core.ui.blocks.layout.TextPaintRegistry;
import com.socialapp.core.ui.blocks.model.AdSlotBlock;
import com.socialapp.core.ui.blocks.model.BlockPayloads;
import com.socialapp.core.ui.blocks.model.ContentBlock;
import com.socialapp.core.ui.blocks.model.FeedPostBlock;
import com.socialapp.core.ui.blocks.view.SinglePassFeedPostView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Unified FeedAdapter using AsyncListDiffer (Myers O(N + D^2)) and flattened SinglePassFeedPostView.
 * Preserves AD_FREQUENCY = 5 ad slots and all NYT Editorial click targets.
 */
public class FeedAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public interface OnPostClickListener {
        void onPostClick(@NonNull PostResource post);
    }

    private final AsyncListDiffer<ContentBlock> differ =
            new AsyncListDiffer<>(this, new ContentBlockDiffCallback());
    private final Map<Integer, PostResource> postLookup = new HashMap<>();
    @Nullable
    private final OnPostClickListener listener;

    @Nullable
    private TextPaintRegistry fallbackPaintRegistry;
    @Nullable
    private BlockPrecomputer fallbackPrecomputer;
    @Nullable
    private RecyclerView attachedRecyclerView;
    @Nullable
    private AppContainer cachedContainer;
    @Nullable
    private RequestManager cachedRequestManager;
    @Nullable
    private BlockScrollPreloader scrollPreloader;

    private final SinglePassFeedPostView.OnFeedPostInteractionListener sharedInteractionListener =
            new SinglePassFeedPostView.OnFeedPostInteractionListener() {
                @Override
                public void onCardClick(@NonNull FeedPostBlock block) {
                    if (listener != null) {
                        PostResource post = postLookup.get(block.getPostId());
                        if (post == null) {
                            post = toFallbackPostResource(block);
                        }
                        listener.onPostClick(post);
                    }
                }

                @Override
                public void onAuthorClick(@NonNull FeedPostBlock block) {
                    Context ctx = resolveNavigationContext();
                    if (ctx != null && block.getAuthorId() > 0) {
                        Navigator.navigateToUserProfile(ctx, block.getAuthorId());
                    }
                }

                @Override
                public void onCommentsClick(@NonNull FeedPostBlock block) {
                    Context ctx = resolveNavigationContext();
                    if (ctx != null) {
                        Navigator.navigateToPostComments(ctx, block.getPostId());
                    }
                }
            };

    public FeedAdapter(@Nullable OnPostClickListener listener) {
        this.listener = listener;
    }

    public void setPosts(@Nullable List<PostResource> posts) {
        postLookup.clear();
        if (posts != null) {
            for (PostResource p : posts) {
                if (p != null) {
                    postLookup.put(p.id, p);
                }
            }
        }
        List<ContentBlock> blocks = FeedBlockListBuilder.build(posts);
        triggerWarmupIfAvailable(blocks);
        if (scrollPreloader != null) {
            scrollPreloader.resetWindow();
        }
        differ.submitList(blocks);
    }

    @Override
    public void onAttachedToRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onAttachedToRecyclerView(recyclerView);
        this.attachedRecyclerView = recyclerView;
        this.cachedContainer = getAppContainer(recyclerView.getContext());
        try {
            this.cachedRequestManager = Glide.with(recyclerView);
        } catch (Throwable ignored) {
            this.cachedRequestManager = null;
        }
        if (scrollPreloader == null && cachedContainer != null) {
            scrollPreloader = new BlockScrollPreloader(
                    cachedContainer.getBlockWarmupEngine(),
                    differ::getCurrentList
            );
        }
        if (scrollPreloader != null) {
            scrollPreloader.setRequestManager(cachedRequestManager);
            recyclerView.removeOnScrollListener(scrollPreloader);
            recyclerView.addOnScrollListener(scrollPreloader);
        }
    }

    @Override
    public void onDetachedFromRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onDetachedFromRecyclerView(recyclerView);
        if (scrollPreloader != null) {
            recyclerView.removeOnScrollListener(scrollPreloader);
            scrollPreloader.detachCleanup();
        }
        this.cachedRequestManager = null;
        if (this.attachedRecyclerView == recyclerView) {
            this.attachedRecyclerView = null;
        }
    }

    @Override
    public int getItemViewType(int position) {
        ContentBlock item = differ.getCurrentList().get(position);
        return item.getBlockType();
    }

    /**
     * [Ý 1 - Viewport Dwell Tracker]: Lấy đối tượng PostResource tại một vị trí của RecyclerView Adapter.
     * Trả về null nếu vị trí đó là ô quảng cáo (AdSlot) hoặc nằm ngoài danh sách.
     */
    @Nullable
    public PostResource getPostAtAdapterPosition(int position) {
        List<ContentBlock> list = differ.getCurrentList();
        if (position < 0 || position >= list.size()) {
            return null;
        }
        ContentBlock item = list.get(position);
        if (!(item instanceof FeedPostBlock)) {
            return null;
        }
        FeedPostBlock block = (FeedPostBlock) item;
        PostResource post = postLookup.get(block.getPostId());
        return post != null ? post : toFallbackPostResource(block);
    }

    @Override
    public int getItemCount() {
        return differ.getCurrentList().size();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        Context context = PooledViewContextHelper.getThemedApplicationContext(parent.getContext());
        if (viewType == ContentBlock.TYPE_AD_SLOT) {
            AdSlotView adSlotView = new AdSlotView(context);
            adSlotView.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            adSlotView.setRequestManager(cachedRequestManager);
            return new AdViewHolder(adSlotView);
        }

        SinglePassFeedPostView postView = createFeedPostView(context);
        postView.setInteractionListener(sharedInteractionListener);
        postView.setRequestManager(cachedRequestManager);
        return new PostViewHolder(postView);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ContentBlock item = differ.getCurrentList().get(position);
        if (holder instanceof PostViewHolder && item instanceof FeedPostBlock) {
            FeedPostBlock block = (FeedPostBlock) item;
            SinglePassFeedPostView view = (SinglePassFeedPostView) holder.itemView;
            bindDependencies(view);
            view.setRequestManager(cachedRequestManager);
            view.setInteractionListener(sharedInteractionListener);
            view.bind(block, resolveAvailableWidth(view));
        } else if (holder instanceof AdViewHolder && item instanceof AdSlotBlock) {
            AdSlotView adSlotView = (AdSlotView) holder.itemView;
            adSlotView.setRequestManager(cachedRequestManager);
            adSlotView.setAdvertisement(((AdSlotBlock) item).toAdvertisementResource());
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position, @NonNull List<Object> payloads) {
        if (payloads.isEmpty()) {
            onBindViewHolder(holder, position);
            return;
        }
        ContentBlock item = differ.getCurrentList().get(position);
        if (holder instanceof PostViewHolder && item instanceof FeedPostBlock) {
            Object first = payloads.get(0);
            if (first instanceof BlockPayloads.FeedMetricsPayload) {
                SinglePassFeedPostView view = (SinglePassFeedPostView) holder.itemView;
                view.applyPayload((FeedPostBlock) item, (BlockPayloads.FeedMetricsPayload) first);
                return;
            }
        }
        onBindViewHolder(holder, position);
    }

    @Override
    public void onViewRecycled(@NonNull RecyclerView.ViewHolder holder) {
        super.onViewRecycled(holder);
        if (holder instanceof PostViewHolder) {
            SinglePassFeedPostView view = (SinglePassFeedPostView) holder.itemView;
            view.clearGlideTargets();
            view.setRequestManager(null);
        } else if (holder instanceof AdViewHolder) {
            AdSlotView adView = (AdSlotView) holder.itemView;
            adView.clearImage();
            adView.setRequestManager(null);
        }
    }

    @Nullable
    private Context resolveNavigationContext() {
        if (attachedRecyclerView != null) {
            return attachedRecyclerView.getContext();
        }
        return MyApplication.getInstance();
    }

    private void triggerWarmupIfAvailable(@NonNull List<ContentBlock> blocks) {
        if (blocks.isEmpty()) {
            return;
        }
        MyApplication app = MyApplication.getInstance();
        AppContainer container = cachedContainer != null ? cachedContainer : (app != null ? app.getAppContainer() : null);
        if (container == null) {
            return;
        }
        Context ctx = attachedRecyclerView != null ? attachedRecyclerView.getContext() : app;
        if (ctx == null) {
            return;
        }
        BlockWarmupEngine warmupEngine = container.getBlockWarmupEngine();
        float density = ctx.getResources().getDisplayMetrics().density;
        float fontScale = ctx.getResources().getConfiguration().fontScale;
        int availWidth = resolveAdapterWidth(ctx);
        warmupEngine.warmupL1(blocks, availWidth, density, fontScale);

        List<String> thumbUrls = new ArrayList<>();
        List<String> avatarUrls = new ArrayList<>();
        int limit = Math.min(blocks.size(), 10);
        for (int i = 0; i < limit; i++) {
            ContentBlock b = blocks.get(i);
            if (b instanceof FeedPostBlock) {
                FeedPostBlock fp = (FeedPostBlock) b;
                if (fp.getThumbnailUrl() != null) {
                    thumbUrls.add(fp.getThumbnailUrl());
                }
                if (fp.getAuthorName() != null && fp.getAuthorAvatarUrl() != null) {
                    avatarUrls.add(fp.getAuthorAvatarUrl());
                }
            }
        }
        if (!thumbUrls.isEmpty()) {
            int w = BlockPrecomputer.dpToPx(BlockPrecomputer.FEED_THUMB_WIDTH_DP, density);
            int h = BlockPrecomputer.dpToPx(BlockPrecomputer.FEED_THUMB_HEIGHT_DP, density);
            int radiusPx = Math.max(1, BlockPrecomputer.dpToPx(4, density));
            warmupEngine.warmupL2FeedThumbnails(ctx, cachedRequestManager, thumbUrls, w, h, radiusPx);
        }
        if (!avatarUrls.isEmpty()) {
            int size = BlockPrecomputer.dpToPx(BlockPrecomputer.FEED_AVATAR_SIZE_DP, density);
            warmupEngine.warmupL2Avatars(ctx, cachedRequestManager, avatarUrls, size);
        }
    }

    private int resolveAdapterWidth(@NonNull Context ctx) {
        if (attachedRecyclerView != null && attachedRecyclerView.getWidth() > 0) {
            return Math.max(1, attachedRecyclerView.getWidth()
                    - attachedRecyclerView.getPaddingLeft()
                    - attachedRecyclerView.getPaddingRight());
        }
        return ctx.getResources().getDisplayMetrics().widthPixels;
    }

    private int resolveAvailableWidth(@NonNull SinglePassFeedPostView view) {
        if (view.getWidth() > 0) {
            return view.getWidth();
        }
        if (attachedRecyclerView != null && attachedRecyclerView.getWidth() > 0) {
            return attachedRecyclerView.getWidth()
                    - attachedRecyclerView.getPaddingLeft()
                    - attachedRecyclerView.getPaddingRight();
        }
        return view.getResources().getDisplayMetrics().widthPixels;
    }

    @NonNull
    private SinglePassFeedPostView createFeedPostView(@NonNull Context context) {
        AppContainer container = getAppContainer(context);
        TextPaintRegistry registry = container != null ? container.getTextPaintRegistry() : getFallbackPaintRegistry();
        BlockPrecomputer precomputer = container != null ? container.getBlockPrecomputer() : getFallbackPrecomputer();

        SinglePassFeedPostView view = new SinglePassFeedPostView(context, registry, precomputer);
        view.setLayoutParams(new RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        return view;
    }

    private void bindDependencies(@NonNull SinglePassFeedPostView view) {
        AppContainer container = cachedContainer != null ? cachedContainer : getAppContainer(view.getContext());
        if (container != null) {
            view.setDependencies(container.getTextPaintRegistry(), container.getBlockPrecomputer());
        }
    }

    @NonNull
    private TextPaintRegistry getFallbackPaintRegistry() {
        if (fallbackPaintRegistry == null) {
            fallbackPaintRegistry = new TextPaintRegistry();
        }
        return fallbackPaintRegistry;
    }

    @NonNull
    private BlockPrecomputer getFallbackPrecomputer() {
        if (fallbackPrecomputer == null) {
            fallbackPrecomputer = new BlockPrecomputer(getFallbackPaintRegistry(), new BlockLayoutCache(64));
        }
        return fallbackPrecomputer;
    }

    @Nullable
    private static AppContainer getAppContainer(@NonNull Context context) {
        Context appCtx = context.getApplicationContext();
        if (appCtx instanceof MyApplication) {
            return ((MyApplication) appCtx).getAppContainer();
        }
        return null;
    }

    @NonNull
    private static PostResource toFallbackPostResource(@NonNull FeedPostBlock block) {
        PostResource post = new PostResource();
        post.id = block.getPostId();
        post.title = block.getTitle();
        post.thumbnailUrl = block.getThumbnailUrl();
        post.sourceName = block.getSourceName();
        post.createdAt = block.getCreatedAt();
        post.voteScore = block.getVoteScore();
        post.userVote = block.getUserVote();
        post.commentsCount = block.getCommentsCount();
        return post;
    }

    /**
     * L3 IdleHandler pre-allocation helper: creates empty PostViewHolders into the shared pool
     * strictly after the first frame is drawn.
     */
    public static void preallocatePostHolders(
            @NonNull Context context,
            @NonNull UnifiedBlockViewPool pool,
            int count) {
        AppContainer container = getAppContainer(context);
        if (container == null) {
            return;
        }
        Context viewCtx = PooledViewContextHelper.getThemedApplicationContext(context);
        TextPaintRegistry registry = container.getTextPaintRegistry();
        BlockPrecomputer precomputer = container.getBlockPrecomputer();
        for (int i = 0; i < count; i++) {
            SinglePassFeedPostView view = new SinglePassFeedPostView(viewCtx, registry, precomputer);
            view.setLayoutParams(new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            pool.putRecycledView(new PostViewHolder(view));
        }
    }

    public static void preallocateAdHolders(
            @NonNull Context context,
            @NonNull UnifiedBlockViewPool pool,
            int count) {
        Context viewCtx = PooledViewContextHelper.getThemedApplicationContext(context);
        for (int i = 0; i < count; i++) {
            AdSlotView adSlotView = new AdSlotView(viewCtx);
            adSlotView.setLayoutParams(new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            pool.putRecycledView(new AdViewHolder(adSlotView));
        }
    }

    public static class PostViewHolder extends RecyclerView.ViewHolder {
        public PostViewHolder(@NonNull SinglePassFeedPostView itemView) {
            super(itemView);
        }
    }

    public static class AdViewHolder extends RecyclerView.ViewHolder {
        public AdViewHolder(@NonNull AdSlotView itemView) {
            super(itemView);
        }
    }
}
