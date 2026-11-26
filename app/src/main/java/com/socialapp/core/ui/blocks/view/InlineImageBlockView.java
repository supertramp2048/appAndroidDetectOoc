package com.socialapp.core.ui.blocks.view;

import android.content.Context;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.google.android.material.card.MaterialCardView;
import com.socialapp.R;
import com.socialapp.core.ui.blocks.layout.BlockPrecomputer;
import com.socialapp.core.ui.blocks.model.InlineImageBlock;

/**
 * Inline image block view reserving fixed aspect ratio (metadata or 16:9 fallback)
 * with skeleton background before Glide finishes loading, eliminating layout shifts (CLS).
 */
public class InlineImageBlockView extends LinearLayout {

    private final MaterialCardView imageCard;
    private final ImageView imageView;
    private final TextView captionView;
    private final int padX;

    private float aspectRatio = InlineImageBlock.DEFAULT_ASPECT_RATIO;
    @Nullable
    private String boundImageUrl;

    public InlineImageBlockView(@NonNull Context context) {
        super(context);
        float density = context.getResources().getDisplayMetrics().density;
        this.padX = BlockPrecomputer.dpToPx(BlockPrecomputer.DETAIL_HORIZONTAL_PADDING_DP, density);
        setOrientation(VERTICAL);
        setBackgroundColor(ContextCompat.getColor(context, R.color.app_surface));
        setPadding(padX, BlockPrecomputer.dpToPx(12, density), padX, BlockPrecomputer.dpToPx(14, density));

        imageCard = new MaterialCardView(context);
        imageCard.setRadius(BlockPrecomputer.dpToPx(4, density));
        imageCard.setCardElevation(0f);
        imageCard.setCardBackgroundColor(ContextCompat.getColor(context, R.color.app_surface_variant));
        imageCard.setStrokeColor(ContextCompat.getColor(context, R.color.app_border));
        imageCard.setStrokeWidth(Math.max(1, BlockPrecomputer.dpToPx(1, density)));

        imageView = new ImageView(context);
        imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        imageCard.addView(imageView, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        addView(imageCard, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        captionView = new TextView(context);
        captionView.setTypeface(Typeface.SANS_SERIF, Typeface.ITALIC);
        captionView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        captionView.setTextColor(0xFF64748B);
        captionView.setLineSpacing(0f, 1.4f);
        LayoutParams capLp = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        capLp.topMargin = BlockPrecomputer.dpToPx(6, density);
        addView(captionView, capLp);
    }

    public void bind(@NonNull InlineImageBlock block) {
        float nextRatio = block.getAspectRatio();
        boolean ratioChanged = Math.abs(nextRatio - aspectRatio) > 0.01f;
        this.aspectRatio = nextRatio;

        imageView.setContentDescription(block.getAltText() != null ? block.getAltText() : "Hình ảnh bài viết");

        if (block.getCaption() != null) {
            captionView.setText(block.getCaption());
            captionView.setVisibility(VISIBLE);
        } else {
            captionView.setVisibility(GONE);
        }

        if (ratioChanged) {
            requestLayout();
        }

        String nextUrl = block.getImageUrl();
        if (!nextUrl.equals(boundImageUrl)) {
            boundImageUrl = nextUrl;
            Glide.with(this)
                    .load(nextUrl)
                    .centerCrop()
                    .into(imageView);
        }
    }

    public void clearGlide() {
        boundImageUrl = null;
        try {
            Glide.with(this).clear(imageView);
        } catch (Throwable ignored) {
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int totalWidth = MeasureSpec.getSize(widthMeasureSpec);
        if (totalWidth > 0) {
            int contentWidth = Math.max(1, totalWidth - getPaddingLeft() - getPaddingRight());
            int targetImageHeight = Math.max(1, Math.round(contentWidth / aspectRatio));
            ViewGroup.LayoutParams lp = imageCard.getLayoutParams();
            if (lp.height != targetImageHeight) {
                lp.height = targetImageHeight;
            }
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }
}
