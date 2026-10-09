package com.socialapp.core.ui;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.socialapp.R;
import com.socialapp.core.network.dto.AdvertisementResource;

public class AdSlotView extends FrameLayout {

    private ImageView adBannerImage;
    private AdvertisementResource currentAd;

    public AdSlotView(@NonNull Context context) {
        super(context);
        init(context);
    }

    public AdSlotView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public AdSlotView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(@NonNull Context context) {
        LayoutInflater.from(context).inflate(R.layout.view_ad_slot, this, true);
        adBannerImage = findViewById(R.id.ad_banner_image);
        setVisibility(GONE);

        setOnClickListener(v -> {
            if (currentAd != null && currentAd.linkUrl != null && !currentAd.linkUrl.trim().isEmpty()) {
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(currentAd.linkUrl));
                    context.startActivity(intent);
                } catch (Exception ignored) {
                }
            }
        });
    }

    public void setAdvertisement(@Nullable AdvertisementResource ad) {
        this.currentAd = ad;
        if (ad != null && ad.imageUrl != null && !ad.imageUrl.trim().isEmpty()) {
            setVisibility(VISIBLE);
            Glide.with(this)
                    .load(ad.imageUrl)
                    .centerCrop()
                    .into(adBannerImage);
        } else {
            setVisibility(GONE);
        }
    }
}
