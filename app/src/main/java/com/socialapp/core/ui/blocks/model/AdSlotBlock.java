package com.socialapp.core.ui.blocks.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.socialapp.core.network.dto.AdvertisementResource;

import java.util.Objects;

/**
 * Immutable advertisement slot block inserted at AD_FREQUENCY intervals in Feed or at the end of PostDetail.
 */
public final class AdSlotBlock implements ContentBlock {

    @NonNull
    private final String blockId;
    private final int adId;
    @Nullable
    private final String title;
    @Nullable
    private final String imageUrl;
    @Nullable
    private final String linkUrl;
    @Nullable
    private final String position;
    private final int contentHash;

    public AdSlotBlock(@NonNull String blockId, @Nullable AdvertisementResource ad) {
        this.blockId = blockId;
        if (ad != null) {
            this.adId = ad.id;
            this.title = ad.title;
            this.imageUrl = ad.imageUrl;
            this.linkUrl = ad.linkUrl;
            this.position = ad.position;
        } else {
            this.adId = 0;
            this.title = null;
            this.imageUrl = null;
            this.linkUrl = null;
            this.position = null;
        }
        this.contentHash = Objects.hash(this.blockId, this.adId, this.title, this.imageUrl, this.linkUrl, this.position);
    }

    @NonNull
    @Override
    public String getBlockId() {
        return blockId;
    }

    @Override
    public int getBlockType() {
        return TYPE_AD_SLOT;
    }

    @Override
    public int getContentHash() {
        return contentHash;
    }

    @Nullable
    @Override
    public Object getChangePayload(@NonNull ContentBlock newItem) {
        return null;
    }

    @Nullable
    public AdvertisementResource toAdvertisementResource() {
        if (adId == 0 && imageUrl == null && linkUrl == null && title == null) {
            return null;
        }
        AdvertisementResource ad = new AdvertisementResource();
        ad.id = adId;
        ad.title = title;
        ad.imageUrl = imageUrl;
        ad.linkUrl = linkUrl;
        ad.position = position;
        return ad;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AdSlotBlock)) return false;
        AdSlotBlock that = (AdSlotBlock) o;
        return adId == that.adId
                && Objects.equals(blockId, that.blockId)
                && Objects.equals(title, that.title)
                && Objects.equals(imageUrl, that.imageUrl)
                && Objects.equals(linkUrl, that.linkUrl)
                && Objects.equals(position, that.position);
    }

    @Override
    public int hashCode() {
        return contentHash;
    }
}
