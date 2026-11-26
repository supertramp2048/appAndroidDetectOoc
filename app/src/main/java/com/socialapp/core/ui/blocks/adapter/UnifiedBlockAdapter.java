package com.socialapp.core.ui.blocks.adapter;

import android.content.Context;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.AsyncListDiffer;
import androidx.recyclerview.widget.RecyclerView;

import com.socialapp.MyApplication;
import com.socialapp.core.di.AppContainer;
import com.socialapp.core.ui.AdSlotView;
import com.socialapp.core.ui.blocks.layout.BlockLayoutCache;
import com.socialapp.core.ui.blocks.layout.BlockPrecomputer;
import com.socialapp.core.ui.blocks.layout.TextPaintRegistry;
import com.socialapp.core.ui.blocks.model.AdSlotBlock;
import com.socialapp.core.ui.blocks.model.BlockPayloads;
import com.socialapp.core.ui.blocks.model.CodeBlock;
import com.socialapp.core.ui.blocks.model.ContentBlock;
import com.socialapp.core.ui.blocks.model.DetailActionBlock;
import com.socialapp.core.ui.blocks.model.DetailHeaderBlock;
import com.socialapp.core.ui.blocks.model.InlineImageBlock;
import com.socialapp.core.ui.blocks.model.QuoteBlock;
import com.socialapp.core.ui.blocks.model.RichTextBlock;
import com.socialapp.core.ui.blocks.model.TableBlock;
import com.socialapp.core.ui.blocks.view.CodeBlockView;
import com.socialapp.core.ui.blocks.view.DetailActionBlockView;
import com.socialapp.core.ui.blocks.view.DetailHeaderBlockView;
import com.socialapp.core.ui.blocks.view.InlineImageBlockView;
import com.socialapp.core.ui.blocks.view.SinglePassQuoteView;
import com.socialapp.core.ui.blocks.view.SinglePassRichTextView;
import com.socialapp.core.ui.blocks.view.TableBlockView;

import java.util.List;

/**
 * Multi-type UnifiedBlockAdapter backed by AsyncListDiffer<ContentBlock> for PostDetailActivity
 * (and extensible to any heterogeneous block stream).
 */
public class UnifiedBlockAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public interface DetailCallbacks {
        void onAuthorClick(int authorId);
        void onFollowClick();
        void onLinkClick(@NonNull String url);
        void onSourceClick(@NonNull String sourceUrl);
        void onUpvoteClick();
        void onDownvoteClick();
        void onCommentsClick(int postId);
        void onFactCheckClick();
    }

    private final AsyncListDiffer<ContentBlock> differ =
            new AsyncListDiffer<>(this, new ContentBlockDiffCallback());
    @Nullable
    private DetailCallbacks callbacks;

    @Nullable
    private RecyclerView attachedRecyclerView;
    @Nullable
    private TextPaintRegistry fallbackPaintRegistry;
    @Nullable
    private BlockPrecomputer fallbackPrecomputer;

    private final DetailHeaderBlockView.OnHeaderActionListener sharedHeaderListener =
            new DetailHeaderBlockView.OnHeaderActionListener() {
                @Override
                public void onAuthorClick(int authorId) {
                    if (callbacks != null) callbacks.onAuthorClick(authorId);
                }

                @Override
                public void onFollowClick() {
                    if (callbacks != null) callbacks.onFollowClick();
                }
            };

    private final SinglePassRichTextView.OnLinkClickListener sharedLinkListener = url -> {
        if (callbacks != null) callbacks.onLinkClick(url);
    };

    private final DetailActionBlockView.OnDetailActionListener sharedDetailActionListener =
            new DetailActionBlockView.OnDetailActionListener() {
                @Override
                public void onSourceClick(@NonNull String sourceUrl) {
                    if (callbacks != null) callbacks.onSourceClick(sourceUrl);
                }

                @Override
                public void onUpvoteClick() {
                    if (callbacks != null) callbacks.onUpvoteClick();
                }

                @Override
                public void onDownvoteClick() {
                    if (callbacks != null) callbacks.onDownvoteClick();
                }

                @Override
                public void onCommentsClick(int postId) {
                    if (callbacks != null) callbacks.onCommentsClick(postId);
                }

                @Override
                public void onFactCheckClick() {
                    if (callbacks != null) callbacks.onFactCheckClick();
                }
            };

    public void setDetailCallbacks(@Nullable DetailCallbacks callbacks) {
        this.callbacks = callbacks;
    }

    public void submitBlocks(@Nullable List<ContentBlock> blocks) {
        differ.submitList(blocks);
    }

    @NonNull
    public List<ContentBlock> getCurrentBlocks() {
        return differ.getCurrentList();
    }

    @Override
    public void onAttachedToRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onAttachedToRecyclerView(recyclerView);
        this.attachedRecyclerView = recyclerView;
    }

    @Override
    public void onDetachedFromRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onDetachedFromRecyclerView(recyclerView);
        if (this.attachedRecyclerView == recyclerView) {
            this.attachedRecyclerView = null;
        }
    }

    @Override
    public int getItemViewType(int position) {
        return differ.getCurrentList().get(position).getBlockType();
    }

    @Override
    public int getItemCount() {
        return differ.getCurrentList().size();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        Context context = parent.getContext();
        BlockPrecomputer precomputer = resolvePrecomputer(context);

        switch (viewType) {
            case ContentBlock.TYPE_DETAIL_HEADER: {
                DetailHeaderBlockView view = new DetailHeaderBlockView(context);
                view.setLayoutParams(matchWrapParams());
                return new DetailHeaderHolder(view);
            }
            case ContentBlock.TYPE_RICH_TEXT: {
                SinglePassRichTextView view = new SinglePassRichTextView(context, precomputer);
                view.setLayoutParams(matchWrapParams());
                return new RichTextHolder(view);
            }
            case ContentBlock.TYPE_QUOTE: {
                SinglePassQuoteView view = new SinglePassQuoteView(context, precomputer);
                view.setLayoutParams(matchWrapParams());
                return new QuoteHolder(view);
            }
            case ContentBlock.TYPE_INLINE_IMAGE: {
                InlineImageBlockView view = new InlineImageBlockView(context);
                view.setLayoutParams(matchWrapParams());
                return new InlineImageHolder(view);
            }
            case ContentBlock.TYPE_CODE: {
                CodeBlockView view = new CodeBlockView(context);
                view.setLayoutParams(matchWrapParams());
                return new CodeHolder(view);
            }
            case ContentBlock.TYPE_TABLE: {
                TableBlockView view = new TableBlockView(context);
                view.setLayoutParams(matchWrapParams());
                return new TableHolder(view);
            }
            case ContentBlock.TYPE_DETAIL_ACTION: {
                DetailActionBlockView view = new DetailActionBlockView(context);
                view.setLayoutParams(matchWrapParams());
                return new DetailActionHolder(view);
            }
            case ContentBlock.TYPE_AD_SLOT:
            default: {
                AdSlotView view = new AdSlotView(context);
                view.setLayoutParams(matchWrapParams());
                return new AdSlotHolder(view);
            }
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ContentBlock item = differ.getCurrentList().get(position);
        Context context = holder.itemView.getContext();
        BlockPrecomputer precomputer = resolvePrecomputer(context);
        int availWidth = resolveAvailableWidth(holder.itemView);

        if (holder instanceof DetailHeaderHolder && item instanceof DetailHeaderBlock) {
            DetailHeaderBlockView view = ((DetailHeaderHolder) holder).view;
            view.setInteractionListener(sharedHeaderListener);
            view.bind((DetailHeaderBlock) item);
        } else if (holder instanceof RichTextHolder && item instanceof RichTextBlock) {
            SinglePassRichTextView view = ((RichTextHolder) holder).view;
            view.setPrecomputer(precomputer);
            view.setOnLinkClickListener(sharedLinkListener);
            view.bind((RichTextBlock) item, availWidth);
        } else if (holder instanceof QuoteHolder && item instanceof QuoteBlock) {
            SinglePassQuoteView view = ((QuoteHolder) holder).view;
            view.setPrecomputer(precomputer);
            view.setOnLinkClickListener(sharedLinkListener);
            view.bind((QuoteBlock) item, availWidth);
        } else if (holder instanceof InlineImageHolder && item instanceof InlineImageBlock) {
            ((InlineImageHolder) holder).view.bind((InlineImageBlock) item);
        } else if (holder instanceof CodeHolder && item instanceof CodeBlock) {
            ((CodeHolder) holder).view.bind((CodeBlock) item);
        } else if (holder instanceof TableHolder && item instanceof TableBlock) {
            ((TableHolder) holder).view.bind((TableBlock) item);
        } else if (holder instanceof DetailActionHolder && item instanceof DetailActionBlock) {
            DetailActionBlockView view = ((DetailActionHolder) holder).view;
            view.setInteractionListener(sharedDetailActionListener);
            view.bind((DetailActionBlock) item);
        } else if (holder instanceof AdSlotHolder && item instanceof AdSlotBlock) {
            ((AdSlotHolder) holder).view.setAdvertisement(((AdSlotBlock) item).toAdvertisementResource());
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position, @NonNull List<Object> payloads) {
        if (payloads.isEmpty()) {
            onBindViewHolder(holder, position);
            return;
        }
        ContentBlock item = differ.getCurrentList().get(position);
        Object first = payloads.get(0);
        if (holder instanceof DetailHeaderHolder && first instanceof BlockPayloads.DetailFollowPayload) {
            ((DetailHeaderHolder) holder).view.applyFollowState(((BlockPayloads.DetailFollowPayload) first).followingAuthor);
            return;
        }
        if (holder instanceof DetailActionHolder && item instanceof DetailActionBlock && first instanceof BlockPayloads.DetailActionPayload) {
            ((DetailActionHolder) holder).view.applyPayload((DetailActionBlock) item, (BlockPayloads.DetailActionPayload) first);
            return;
        }
        onBindViewHolder(holder, position);
    }

    @Override
    public void onViewRecycled(@NonNull RecyclerView.ViewHolder holder) {
        super.onViewRecycled(holder);
        if (holder instanceof DetailHeaderHolder) {
            ((DetailHeaderHolder) holder).view.clearGlide();
        } else if (holder instanceof InlineImageHolder) {
            ((InlineImageHolder) holder).view.clearGlide();
        } else if (holder instanceof AdSlotHolder) {
            ((AdSlotHolder) holder).view.clearImage();
        }
    }

    private int resolveAvailableWidth(@NonNull android.view.View view) {
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
    private BlockPrecomputer resolvePrecomputer(@NonNull Context context) {
        Context appCtx = context.getApplicationContext();
        if (appCtx instanceof MyApplication) {
            AppContainer container = ((MyApplication) appCtx).getAppContainer();
            if (container != null) {
                return container.getBlockPrecomputer();
            }
        }
        if (fallbackPrecomputer == null) {
            if (fallbackPaintRegistry == null) {
                fallbackPaintRegistry = new TextPaintRegistry();
            }
            fallbackPrecomputer = new BlockPrecomputer(fallbackPaintRegistry, new BlockLayoutCache(64));
        }
        return fallbackPrecomputer;
    }

    private static RecyclerView.LayoutParams matchWrapParams() {
        return new RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
    }

    static class DetailHeaderHolder extends RecyclerView.ViewHolder {
        final DetailHeaderBlockView view;
        DetailHeaderHolder(@NonNull DetailHeaderBlockView view) { super(view); this.view = view; }
    }

    static class RichTextHolder extends RecyclerView.ViewHolder {
        final SinglePassRichTextView view;
        RichTextHolder(@NonNull SinglePassRichTextView view) { super(view); this.view = view; }
    }

    static class QuoteHolder extends RecyclerView.ViewHolder {
        final SinglePassQuoteView view;
        QuoteHolder(@NonNull SinglePassQuoteView view) { super(view); this.view = view; }
    }

    static class InlineImageHolder extends RecyclerView.ViewHolder {
        final InlineImageBlockView view;
        InlineImageHolder(@NonNull InlineImageBlockView view) { super(view); this.view = view; }
    }

    static class CodeHolder extends RecyclerView.ViewHolder {
        final CodeBlockView view;
        CodeHolder(@NonNull CodeBlockView view) { super(view); this.view = view; }
    }

    static class TableHolder extends RecyclerView.ViewHolder {
        final TableBlockView view;
        TableHolder(@NonNull TableBlockView view) { super(view); this.view = view; }
    }

    static class DetailActionHolder extends RecyclerView.ViewHolder {
        final DetailActionBlockView view;
        DetailActionHolder(@NonNull DetailActionBlockView view) { super(view); this.view = view; }
    }

    static class AdSlotHolder extends RecyclerView.ViewHolder {
        final AdSlotView view;
        AdSlotHolder(@NonNull AdSlotView view) { super(view); this.view = view; }
    }
}
