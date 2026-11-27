package com.socialapp.feature.comments;

import android.content.Context;
import android.view.View;
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
import com.socialapp.core.network.dto.CommentResource;
import com.socialapp.core.network.dto.UserResource;
import com.socialapp.core.ui.blocks.adapter.BlockScrollPreloader;
import com.socialapp.core.ui.blocks.adapter.BlockWarmupEngine;
import com.socialapp.core.ui.blocks.adapter.CommentBlockTreeHelper;
import com.socialapp.core.ui.blocks.adapter.ContentBlockDiffCallback;
import com.socialapp.core.ui.blocks.adapter.PooledViewContextHelper;
import com.socialapp.core.ui.blocks.layout.BlockLayoutCache;
import com.socialapp.core.ui.blocks.layout.BlockPrecomputer;
import com.socialapp.core.ui.blocks.layout.TextPaintRegistry;
import com.socialapp.core.ui.blocks.model.BlockPayloads;
import com.socialapp.core.ui.blocks.model.CommentBlock;
import com.socialapp.core.ui.blocks.model.ContentBlock;
import com.socialapp.core.ui.blocks.view.SinglePassCommentView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Flattened CommentsAdapter backed by AsyncListDiffer (Myers O(N + D^2)) and SinglePassCommentView.
 */
public class CommentsAdapter extends RecyclerView.Adapter<CommentsAdapter.ViewHolder> {

    public interface OnCommentActionListener {
        void onReplyClick(@NonNull CommentResource comment);
        void onLoadRepliesClick(@NonNull CommentResource comment, int position);
        void onMenuClick(@NonNull CommentResource comment, @NonNull View anchor);
    }

    private final AsyncListDiffer<ContentBlock> differ =
            new AsyncListDiffer<>(this, new ContentBlockDiffCallback());
    private final Map<Integer, CommentResource> commentLookup = new HashMap<>();
    @Nullable
    private final OnCommentActionListener listener;

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

    private final SinglePassCommentView.OnCommentInteractionListener sharedInteractionListener =
            new SinglePassCommentView.OnCommentInteractionListener() {
                @Override
                public void onAuthorClick(@NonNull CommentBlock block) {
                    Context ctx = resolveNavigationContext();
                    if (ctx != null && block.getAuthorId() > 0) {
                        Navigator.navigateToUserProfile(ctx, block.getAuthorId());
                    }
                }

                @Override
                public void onReplyClick(@NonNull CommentBlock block) {
                    if (listener != null) {
                        listener.onReplyClick(resolveCommentDto(block));
                    }
                }

                @Override
                public void onLoadRepliesClick(@NonNull CommentBlock block) {
                    if (listener != null) {
                        int pos = CommentBlockTreeHelper.findCommentIndex(differ.getCurrentList(), block.getCommentId());
                        listener.onLoadRepliesClick(resolveCommentDto(block), pos);
                    }
                }

                @Override
                public void onCollapseRepliesClick(@NonNull CommentBlock block) {
                    collapseRepliesForParent(block.getCommentId());
                }

                @Override
                public void onMenuClick(@NonNull CommentBlock block, @NonNull View anchorView) {
                    if (listener != null) {
                        listener.onMenuClick(resolveCommentDto(block), anchorView);
                    }
                }
            };

    public CommentsAdapter(@Nullable OnCommentActionListener listener) {
        this.listener = listener;
    }

    public void setComments(@Nullable List<CommentResource> rootComments) {
        if (rootComments == null || rootComments.isEmpty()) {
            commentLookup.clear();
        } else {
            for (CommentResource c : rootComments) {
                if (c != null) {
                    commentLookup.put(c.id, c);
                }
            }
        }
        List<ContentBlock> next = CommentBlockTreeHelper.fromRootComments(rootComments, differ.getCurrentList());
        warmupL1IfAvailable(next);
        differ.submitList(next);
    }

    public void insertCommentAtTop(@NonNull CommentResource comment) {
        commentLookup.put(comment.id, comment);
        List<ContentBlock> next = CommentBlockTreeHelper.insertRootAtTop(differ.getCurrentList(), comment);
        warmupL1IfAvailable(next);
        differ.submitList(next);
    }

    public boolean insertReplyUnderParent(int parentId, @NonNull CommentResource reply) {
        commentLookup.put(reply.id, reply);
        CommentResource parentDto = commentLookup.get(parentId);
        if (parentDto != null) {
            parentDto.repliesCount += 1;
        }
        int parentIdx = CommentBlockTreeHelper.findCommentIndex(differ.getCurrentList(), parentId);
        List<ContentBlock> next = CommentBlockTreeHelper.insertReplyUnderParent(differ.getCurrentList(), parentId, reply);
        warmupL1IfAvailable(next);
        differ.submitList(next);
        return parentIdx >= 0;
    }

    public void insertReplies(int parentPosition, @NonNull List<CommentResource> replies) {
        List<ContentBlock> current = differ.getCurrentList();
        if (parentPosition < 0 || parentPosition >= current.size()) {
            return;
        }
        ContentBlock parentBlock = current.get(parentPosition);
        if (parentBlock instanceof CommentBlock) {
            insertRepliesForParent(((CommentBlock) parentBlock).getCommentId(), replies);
        }
    }

    public void insertRepliesForParent(int parentCommentId, @NonNull List<CommentResource> replies) {
        for (CommentResource r : replies) {
            if (r != null) {
                commentLookup.put(r.id, r);
            }
        }
        List<ContentBlock> next = CommentBlockTreeHelper.insertReplies(differ.getCurrentList(), parentCommentId, replies);
        warmupL1IfAvailable(next);
        differ.submitList(next);
    }

    public void collapseRepliesForParent(int parentCommentId) {
        List<ContentBlock> next = CommentBlockTreeHelper.collapseReplies(differ.getCurrentList(), parentCommentId);
        differ.submitList(next);
    }

    public void updateCommentContent(int commentId, @NonNull String newContent) {
        CommentResource dto = commentLookup.get(commentId);
        if (dto != null) {
            dto.content = newContent;
            dto.updatedAt = dto.createdAt + "_edited";
        }
        List<ContentBlock> next = CommentBlockTreeHelper.updateCommentContent(differ.getCurrentList(), commentId, newContent);
        warmupL1IfAvailable(next);
        differ.submitList(next);
    }

    public void removeComment(int commentId) {
        commentLookup.remove(commentId);
        List<ContentBlock> next = CommentBlockTreeHelper.removeComment(differ.getCurrentList(), commentId);
        differ.submitList(next);
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
        return ContentBlock.TYPE_COMMENT;
    }

    @Override
    public int getItemCount() {
        return differ.getCurrentList().size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        Context context = PooledViewContextHelper.getThemedApplicationContext(parent.getContext());
        AppContainer container = cachedContainer != null ? cachedContainer : getAppContainer(context);
        TextPaintRegistry registry = container != null ? container.getTextPaintRegistry() : getFallbackPaintRegistry();
        BlockPrecomputer precomputer = container != null ? container.getBlockPrecomputer() : getFallbackPrecomputer();

        SinglePassCommentView view = new SinglePassCommentView(context, registry, precomputer);
        view.setLayoutParams(new RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        view.setInteractionListener(sharedInteractionListener);
        view.setRequestManager(cachedRequestManager);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ContentBlock item = differ.getCurrentList().get(position);
        if (!(item instanceof CommentBlock)) {
            return;
        }
        CommentBlock block = (CommentBlock) item;
        SinglePassCommentView view = holder.commentView;
        AppContainer container = cachedContainer != null ? cachedContainer : getAppContainer(view.getContext());
        if (container != null) {
            view.setDependencies(container.getTextPaintRegistry(), container.getBlockPrecomputer());
        }
        view.setRequestManager(cachedRequestManager);
        view.setInteractionListener(sharedInteractionListener);
        view.bind(block, resolveAvailableWidth(view));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position, @NonNull List<Object> payloads) {
        if (payloads.isEmpty()) {
            onBindViewHolder(holder, position);
            return;
        }
        ContentBlock item = differ.getCurrentList().get(position);
        if (item instanceof CommentBlock && payloads.get(0) instanceof BlockPayloads.CommentRepliesPayload) {
            holder.commentView.applyRepliesPayload(
                    (CommentBlock) item,
                    (BlockPayloads.CommentRepliesPayload) payloads.get(0)
            );
            return;
        }
        onBindViewHolder(holder, position);
    }

    @Override
    public void onViewRecycled(@NonNull ViewHolder holder) {
        super.onViewRecycled(holder);
        holder.commentView.clearGlideTargets();
        holder.commentView.setRequestManager(null);
    }

    @Nullable
    private Context resolveNavigationContext() {
        if (attachedRecyclerView != null) {
            return attachedRecyclerView.getContext();
        }
        return MyApplication.getInstance();
    }

    @NonNull
    private CommentResource resolveCommentDto(@NonNull CommentBlock block) {
        CommentResource cached = commentLookup.get(block.getCommentId());
        if (cached != null) {
            return cached;
        }
        CommentResource fallback = new CommentResource();
        fallback.id = block.getCommentId();
        fallback.postId = block.getPostId();
        fallback.parentId = block.getParentId();
        fallback.content = block.getContent();
        fallback.status = block.getStatus();
        fallback.createdAt = block.getCreatedAt();
        fallback.updatedAt = block.getUpdatedAt();
        fallback.repliesCount = block.getRepliesCount();
        if (block.getAuthorId() > 0 || block.getAuthorName() != null) {
            UserResource author = new UserResource();
            author.id = block.getAuthorId();
            author.name = block.getAuthorName();
            author.avatarUrl = block.getAuthorAvatarUrl();
            fallback.author = author;
        }
        return fallback;
    }

    private void warmupL1IfAvailable(@NonNull List<ContentBlock> blocks) {
        if (blocks.isEmpty()) {
            return;
        }
        if (scrollPreloader != null) {
            scrollPreloader.resetWindow();
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
        int availWidth = attachedRecyclerView != null && attachedRecyclerView.getWidth() > 0
                ? Math.max(1, attachedRecyclerView.getWidth() - attachedRecyclerView.getPaddingLeft() - attachedRecyclerView.getPaddingRight())
                : ctx.getResources().getDisplayMetrics().widthPixels;
        warmupEngine.warmupL1(blocks, availWidth, density, fontScale);

        List<String> avatarUrls = new ArrayList<>();
        int limit = Math.min(blocks.size(), 10);
        for (int i = 0; i < limit; i++) {
            ContentBlock b = blocks.get(i);
            if (b instanceof CommentBlock) {
                CommentBlock cb = (CommentBlock) b;
                if (cb.getAuthorAvatarUrl() != null) {
                    avatarUrls.add(cb.getAuthorAvatarUrl());
                }
            }
        }
        if (!avatarUrls.isEmpty()) {
            int size = BlockPrecomputer.dpToPx(BlockPrecomputer.COMMENT_AVATAR_SIZE_DP, density);
            warmupEngine.warmupL2Avatars(ctx, cachedRequestManager, avatarUrls, size);
        }
    }

    private int resolveAvailableWidth(@NonNull SinglePassCommentView view) {
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

    public static class ViewHolder extends RecyclerView.ViewHolder {
        final SinglePassCommentView commentView;

        public ViewHolder(@NonNull SinglePassCommentView itemView) {
            super(itemView);
            this.commentView = itemView;
        }
    }
}
