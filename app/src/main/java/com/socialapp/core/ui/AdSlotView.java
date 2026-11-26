package com.socialapp.core.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.RequestManager;
import com.socialapp.R;
import com.socialapp.core.network.dto.AdvertisementResource;
import com.socialapp.core.ui.blocks.adapter.PooledViewContextHelper;

/**
 * Lightweight programmatic ad banner view replacing XML LayoutInflater inflation overhead.
 */
public class AdSlotView extends FrameLayout {

    private ImageView adBannerImage;
    private AdvertisementResource currentAd;
    @Nullable
    private String loadedImageUrl;
    @Nullable
    private RequestManager externalRequestManager;

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
        float density = context.getResources().getDisplayMetrics().density;
        int bannerHeightPx = Math.round(100f * density);

        adBannerImage = new ImageView(context);
        adBannerImage.setId(R.id.ad_banner_image);
        adBannerImage.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, bannerHeightPx));
        adBannerImage.setContentDescription("Quảng cáo");
        adBannerImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
        addView(adBannerImage);

        TextView badgeView = new TextView(context);
        LayoutParams badgeLp = new LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.END
        );
        badgeView.setLayoutParams(badgeLp);
        badgeView.setBackgroundColor(0x80000000);
        int padH = Math.round(6f * density);
        int padV = Math.round(2f * density);
        badgeView.setPadding(padH, padV, padH, padV);
        badgeView.setText("Quảng cáo");
        badgeView.setTextColor(0xFFFFFFFF);
        badgeView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f);
        addView(badgeView);

        setVisibility(GONE);

        setOnClickListener(v -> {
            if (currentAd != null && currentAd.linkUrl != null && !currentAd.linkUrl.trim().isEmpty()) {
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(currentAd.linkUrl));
                    if (!(context instanceof Activity)) {
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    }
                    context.startActivity(intent);
                } catch (Exception ignored) {
                }
            }
        });
    }

    public void setRequestManager(@Nullable RequestManager requestManager) {
        this.externalRequestManager = requestManager;
    }

    public void setAdvertisement(@Nullable AdvertisementResource ad) {
        this.currentAd = ad;
        if (ad != null && ad.imageUrl != null && !ad.imageUrl.trim().isEmpty()) {
            setVisibility(VISIBLE);
            if (ad.imageUrl.equals(loadedImageUrl)) {
                return;
            }
            loadedImageUrl = ad.imageUrl;
            RequestManager rm = PooledViewContextHelper.resolveSafeRequestManager(this, externalRequestManager);
            if (rm != null) {
                try {
                    rm.load(ad.imageUrl)
                            .centerCrop()
                            .into(adBannerImage);
                } catch (Throwable ignored) {
                }
            }
        } else {
            clearImage();
            setVisibility(GONE);
        }
    }

    public void clearImage() {
        loadedImageUrl = null;
        if (adBannerImage != null) {
            RequestManager rm = PooledViewContextHelper.resolveSafeRequestManager(this, externalRequestManager);
            if (rm != null) {
                try {
                    rm.clear(adBannerImage);
                } catch (Throwable ignored) {
                }
            }
            adBannerImage.setImageDrawable(null);
        }
    }
}
