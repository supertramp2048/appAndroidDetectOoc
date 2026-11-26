package com.socialapp.core.ui.blocks.view;

import android.content.Context;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.socialapp.R;
import com.socialapp.core.ui.UserAvatarView;
import com.socialapp.core.ui.blocks.layout.BlockPrecomputer;
import com.socialapp.core.ui.blocks.model.DetailHeaderBlock;

/**
 * Header block view for PostDetailActivity matching NYT Editorial typography and spacing 1:1.
 */
public class DetailHeaderBlockView extends LinearLayout {

    public interface OnHeaderActionListener {
        void onAuthorClick(int authorId);
        void onFollowClick();
    }

    private final TextView categoryView;
    private final TextView titleView;
    private final UserAvatarView avatarView;
    private final TextView authorNameView;
    private final TextView timeView;
    private final MaterialButton btnFollow;
    private final MaterialCardView thumbnailCard;
    private final ImageView thumbnailImage;

    @Nullable
    private OnHeaderActionListener listener;
    @Nullable
    private String boundThumbUrl;
    private int boundAuthorId = -1;

    public DetailHeaderBlockView(@NonNull Context context) {
        super(context);
        float density = context.getResources().getDisplayMetrics().density;
        setOrientation(VERTICAL);
        setBackgroundColor(ContextCompat.getColor(context, R.color.app_surface));
        setPadding(dp(18, density), dp(16, density), dp(18, density), 0);

        // 1. Category pill
        categoryView = new TextView(context);
        categoryView.setBackgroundResource(R.drawable.bg_chip_neutral);
        categoryView.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        categoryView.setLetterSpacing(0.04f);
        categoryView.setPadding(dp(10, density), dp(4, density), dp(10, density), dp(4, density));
        categoryView.setAllCaps(true);
        categoryView.setTextColor(ContextCompat.getColor(context, R.color.app_primary));
        categoryView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
        LayoutParams catLp = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        addView(categoryView, catLp);

        // 2. Headline (25sp serif bold)
        titleView = new TextView(context);
        titleView.setTypeface(Typeface.SERIF, Typeface.BOLD);
        titleView.setLineSpacing(0f, 1.2f);
        titleView.setTextColor(ContextCompat.getColor(context, R.color.app_text_primary));
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 25f);
        LayoutParams titleLp = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleLp.topMargin = dp(10, density);
        addView(titleView, titleLp);

        // 3. Byline bar
        LinearLayout bylineRow = new LinearLayout(context);
        bylineRow.setOrientation(HORIZONTAL);
        bylineRow.setGravity(Gravity.CENTER_VERTICAL);
        LayoutParams bylineLp = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bylineLp.topMargin = dp(14, density);
        addView(bylineRow, bylineLp);

        avatarView = new UserAvatarView(context);
        LayoutParams avatarLp = new LayoutParams(dp(40, density), dp(40, density));
        bylineRow.addView(avatarView, avatarLp);

        LinearLayout authorMeta = new LinearLayout(context);
        authorMeta.setOrientation(VERTICAL);
        LayoutParams metaLp = new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        metaLp.setMarginStart(dp(12, density));
        bylineRow.addView(authorMeta, metaLp);

        authorNameView = new TextView(context);
        authorNameView.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        authorNameView.setTextColor(ContextCompat.getColor(context, R.color.app_text_primary));
        authorNameView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        authorNameView.setMaxLines(1);
        authorNameView.setEllipsize(TextUtils.TruncateAt.END);
        authorMeta.addView(authorNameView);

        timeView = new TextView(context);
        timeView.setTypeface(Typeface.SANS_SERIF, Typeface.NORMAL);
        timeView.setTextColor(ContextCompat.getColor(context, R.color.app_text_secondary));
        timeView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        LayoutParams timeLp = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        timeLp.topMargin = dp(2, density);
        authorMeta.addView(timeView, timeLp);

        btnFollow = new MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        btnFollow.setInsetTop(0);
        btnFollow.setInsetBottom(0);
        btnFollow.setPadding(dp(14, density), 0, dp(14, density), 0);
        btnFollow.setText("Theo dõi");
        btnFollow.setTextColor(ContextCompat.getColor(context, R.color.app_primary));
        btnFollow.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        btnFollow.setCornerRadius(dp(18, density));
        btnFollow.setStrokeColorResource(R.color.app_primary);
        btnFollow.setStrokeWidth(dp(1, density));
        LayoutParams followLp = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(36, density));
        bylineRow.addView(btnFollow, followLp);

        // 4. Hairline divider
        View divider = new View(context);
        divider.setBackgroundColor(ContextCompat.getColor(context, R.color.app_border));
        LayoutParams divLp = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(1, density)));
        divLp.topMargin = dp(14, density);
        divLp.bottomMargin = dp(16, density);
        addView(divider, divLp);

        // 5. Cover Thumbnail
        thumbnailCard = new MaterialCardView(context);
        thumbnailCard.setRadius(dp(4, density));
        thumbnailCard.setCardElevation(0f);
        thumbnailCard.setStrokeColor(ContextCompat.getColor(context, R.color.app_border));
        thumbnailCard.setStrokeWidth(Math.max(1, dp(1, density)));
        LayoutParams cardLp = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(220, density));
        cardLp.bottomMargin = dp(16, density);

        thumbnailImage = new ImageView(context);
        thumbnailImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
        thumbnailImage.setContentDescription("Ảnh bài viết");
        thumbnailCard.addView(thumbnailImage, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        addView(thumbnailCard, cardLp);

        OnClickListener authorClick = v -> {
            if (listener != null && boundAuthorId > 0) {
                listener.onAuthorClick(boundAuthorId);
            }
        };
        avatarView.setOnClickListener(authorClick);
        authorNameView.setOnClickListener(authorClick);
        btnFollow.setOnClickListener(v -> {
            if (listener != null) {
                listener.onFollowClick();
            }
        });
    }

    public void setInteractionListener(@Nullable OnHeaderActionListener listener) {
        this.listener = listener;
    }

    public void bind(@NonNull DetailHeaderBlock block) {
        titleView.setText(block.getTitle());
        timeView.setText(block.getFormattedDate());

        if (block.getCategoryName() != null) {
            categoryView.setText(block.getCategoryName());
            categoryView.setVisibility(VISIBLE);
        } else {
            categoryView.setVisibility(GONE);
        }

        boundAuthorId = block.getAuthorId();
        if (block.getAuthorName() != null || block.getAuthorId() > 0) {
            authorNameView.setText(block.getAuthorName() != null ? "Bởi " + block.getAuthorName() : "Nặc danh");
            avatarView.loadAvatar(block.getAuthorAvatarUrl());
            avatarView.setVisibility(VISIBLE);
            btnFollow.setVisibility(block.isOwnPost() ? GONE : VISIBLE);
            applyFollowState(block.isFollowingAuthor());
        } else {
            authorNameView.setText("Nặc danh");
            avatarView.loadAvatar(null);
            btnFollow.setVisibility(GONE);
        }

        String nextThumb = block.getThumbnailUrl();
        if (nextThumb != null) {
            thumbnailCard.setVisibility(VISIBLE);
            if (!nextThumb.equals(boundThumbUrl)) {
                boundThumbUrl = nextThumb;
                Glide.with(this).load(nextThumb).centerCrop().into(thumbnailImage);
            }
        } else {
            boundThumbUrl = null;
            thumbnailCard.setVisibility(GONE);
        }
    }

    public void applyFollowState(boolean isFollowing) {
        btnFollow.setText(isFollowing ? "Đang theo dõi" : "Theo dõi");
    }

    public void clearGlide() {
        boundThumbUrl = null;
        try {
            Glide.with(this).clear(thumbnailImage);
            Glide.with(this).clear(avatarView);
        } catch (Throwable ignored) {
        }
    }

    private static int dp(int dp, float density) {
        return BlockPrecomputer.dpToPx(dp, density);
    }
}
