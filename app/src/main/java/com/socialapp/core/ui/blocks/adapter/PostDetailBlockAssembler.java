package com.socialapp.core.ui.blocks.adapter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.socialapp.core.network.dto.AdvertisementResource;
import com.socialapp.core.network.dto.PostResource;
import com.socialapp.core.ui.blocks.model.AdSlotBlock;
import com.socialapp.core.ui.blocks.model.ContentBlock;
import com.socialapp.core.ui.blocks.model.DetailActionBlock;
import com.socialapp.core.ui.blocks.model.DetailHeaderBlock;
import com.socialapp.core.ui.blocks.model.InlineImageBlock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Pure-Java assembler converting a PostResource and its pre-parsed HTML body AST blocks into
 * the flattened heterogeneous RecyclerView stream:
 * [DetailHeaderBlock] + bodyBlocks + [DetailActionBlock, AdSlotBlock].
 */
public final class PostDetailBlockAssembler {

    private PostDetailBlockAssembler() {
    }

    @NonNull
    public static List<ContentBlock> assemble(
            @NonNull PostResource post,
            int currentUserId,
            boolean isFollowingAuthor,
            @NonNull List<ContentBlock> bodyBlocks,
            @Nullable AdvertisementResource ad) {
        List<ContentBlock> items = new ArrayList<>(bodyBlocks.size() + 3);
        items.add(DetailHeaderBlock.fromPostResource(post, currentUserId, isFollowingAuthor));
        items.addAll(bodyBlocks);
        items.add(DetailActionBlock.fromPostResource(post));
        items.add(new AdSlotBlock("detail_ad_" + post.id, ad));
        return Collections.unmodifiableList(items);
    }

    /**
     * Produces a new immutable block list with only DetailHeaderBlock updated for follow state,
     * preserving body block references so AsyncListDiffer emits DetailFollowPayload with zero body rebinds.
     */
    @NonNull
    public static List<ContentBlock> withFollowState(
            @NonNull List<ContentBlock> currentBlocks,
            boolean isFollowingAuthor) {
        if (currentBlocks.isEmpty()) {
            return currentBlocks;
        }
        ContentBlock first = currentBlocks.get(0);
        if (!(first instanceof DetailHeaderBlock)) {
            return currentBlocks;
        }
        DetailHeaderBlock currentHeader = (DetailHeaderBlock) first;
        if (currentHeader.isFollowingAuthor() == isFollowingAuthor) {
            return currentBlocks;
        }
        List<ContentBlock> updated = new ArrayList<>(currentBlocks.size());
        updated.add(currentHeader.withFollowingAuthor(isFollowingAuthor));
        for (int i = 1; i < currentBlocks.size(); i++) {
            updated.add(currentBlocks.get(i));
        }
        return Collections.unmodifiableList(updated);
    }

    /**
     * Collects cover thumbnail + inline article image URLs for L2 Glide preload warmup.
     */
    @NonNull
    public static List<String> collectImageUrls(
            @NonNull PostResource post,
            @NonNull List<ContentBlock> bodyBlocks) {
        Set<String> urls = new LinkedHashSet<>();
        if (post.thumbnailUrl != null && !post.thumbnailUrl.trim().isEmpty()) {
            urls.add(post.thumbnailUrl.trim());
        }
        for (ContentBlock block : bodyBlocks) {
            if (block instanceof InlineImageBlock) {
                String url = ((InlineImageBlock) block).getImageUrl();
                if (!url.trim().isEmpty()) {
                    urls.add(url.trim());
                }
            }
        }
        return new ArrayList<>(urls);
    }
}
