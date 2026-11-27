package com.socialapp.core.ui.blocks.adapter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.socialapp.core.network.dto.PostResource;
import com.socialapp.core.ui.blocks.model.AdSlotBlock;
import com.socialapp.core.ui.blocks.model.ContentBlock;
import com.socialapp.core.ui.blocks.model.FeedPostBlock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Pure-Java builder converting PostResource lists into immutable ContentBlock lists
 * preserving FeedAdapter's AD_FREQUENCY = 5 ad-slot insertion rule.
 */
public final class FeedBlockListBuilder {

    public static final int AD_FREQUENCY = 5;

    private FeedBlockListBuilder() {
    }

    @NonNull
    public static List<ContentBlock> build(@Nullable List<PostResource> posts) {
        if (posts == null || posts.isEmpty()) {
            return Collections.emptyList();
        }
        int expectedAds = posts.size() / AD_FREQUENCY;
        List<ContentBlock> blocks = new ArrayList<>(posts.size() + expectedAds);
        int adSlotIndex = 0;

        for (int i = 0; i < posts.size(); i++) {
            PostResource post = posts.get(i);
            if (post == null) continue;
            blocks.add(FeedPostBlock.fromPostResource(post));
            if ((i + 1) % AD_FREQUENCY == 0) {
                blocks.add(new AdSlotBlock("feed_ad_" + adSlotIndex, null));
                adSlotIndex++;
            }
        }
        return Collections.unmodifiableList(blocks);
    }
}
