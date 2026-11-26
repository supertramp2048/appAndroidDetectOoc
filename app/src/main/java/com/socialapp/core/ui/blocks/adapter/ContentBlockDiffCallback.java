package com.socialapp.core.ui.blocks.adapter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;

import com.socialapp.core.ui.blocks.model.ContentBlock;

/**
 * DiffUtil.ItemCallback for unified ContentBlock lists.
 * Runs Myers' O(N + D^2) diffing off the Main Thread via AsyncListDiffer.
 */
public class ContentBlockDiffCallback extends DiffUtil.ItemCallback<ContentBlock> {

    @Override
    public boolean areItemsTheSame(@NonNull ContentBlock oldItem, @NonNull ContentBlock newItem) {
        return oldItem.getBlockType() == newItem.getBlockType()
                && oldItem.getBlockId().equals(newItem.getBlockId());
    }

    @Override
    public boolean areContentsTheSame(@NonNull ContentBlock oldItem, @NonNull ContentBlock newItem) {
        return oldItem.getContentHash() == newItem.getContentHash() && oldItem.equals(newItem);
    }

    @Nullable
    @Override
    public Object getChangePayload(@NonNull ContentBlock oldItem, @NonNull ContentBlock newItem) {
        return oldItem.getChangePayload(newItem);
    }
}
