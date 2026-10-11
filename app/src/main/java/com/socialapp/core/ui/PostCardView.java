package com.socialapp.core.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.google.android.material.card.MaterialCardView;
import com.socialapp.R;
import com.socialapp.core.navigation.Navigator;
import com.socialapp.core.network.dto.PostResource;
import com.socialapp.core.util.DateTimeUtils;

public class PostCardView extends MaterialCardView {

    private View postCardRoot;
    private UserAvatarView authorAvatar;
    private TextView authorName;
    private TextView timeText;
    private TextView categoryChip;
    private View thumbnailCard;
    private ImageView thumbnail;
    private TextView title;
    private TextView sourceText;
    private TextView voteScore;
    private TextView commentsCount;

    public PostCardView(@NonNull Context context) {
        super(context);
        init(context);
    }

    public PostCardView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public PostCardView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(@NonNull Context context) {
        setCardElevation(0f);
        setStrokeWidth(0);
        setCardBackgroundColor(0);

        LayoutInflater.from(context).inflate(R.layout.view_post_card, this, true);
        postCardRoot = findViewById(R.id.post_card_root);
        authorAvatar = findViewById(R.id.post_author_avatar);
        authorName = findViewById(R.id.post_author_name);
        timeText = findViewById(R.id.post_time_text);
        categoryChip = findViewById(R.id.post_category_chip);
        thumbnailCard = findViewById(R.id.post_thumbnail_card);
        thumbnail = findViewById(R.id.post_thumbnail);
        title = findViewById(R.id.post_title);
        sourceText = findViewById(R.id.post_source_text);
        voteScore = findViewById(R.id.post_vote_score);
        commentsCount = findViewById(R.id.post_comments_count);
    }

    public void setCardClickListener(@Nullable OnClickListener listener) {
        if (postCardRoot != null) {
            postCardRoot.setOnClickListener(listener);
        }
        setOnClickListener(listener);
    }

    public void bind(@NonNull PostResource post) {
        title.setText(post.title != null ? post.title : "");

        if (post.author != null && post.author.name != null && !post.author.name.trim().isEmpty()) {
            authorName.setText("Bởi " + post.author.name);
            authorAvatar.loadAvatar(post.author.getAvatarUrl());
            authorAvatar.setVisibility(VISIBLE);

            OnClickListener authorClickListener = v -> {
                if (post.author != null && post.author.id > 0) {
                    Navigator.navigateToUserProfile(getContext(), post.author.id);
                }
            };
            authorAvatar.setOnClickListener(authorClickListener);
            authorName.setOnClickListener(authorClickListener);
        } else if (post.sourceName != null && !post.sourceName.trim().isEmpty()) {
            authorName.setText("Bởi " + post.sourceName);
            authorAvatar.loadAvatar(null);
            authorAvatar.setVisibility(GONE);
            authorAvatar.setOnClickListener(null);
            authorName.setOnClickListener(null);
        } else {
            authorName.setText("Bởi Tác giả");
            authorAvatar.loadAvatar(null);
            authorAvatar.setVisibility(GONE);
            authorAvatar.setOnClickListener(null);
            authorName.setOnClickListener(null);
        }

        if (post.category != null && post.category.name != null && !post.category.name.trim().isEmpty()) {
            categoryChip.setText(post.category.name.toUpperCase());
            categoryChip.setVisibility(VISIBLE);
        } else {
            categoryChip.setVisibility(GONE);
        }

        if (post.thumbnailUrl != null && !post.thumbnailUrl.trim().isEmpty()) {
            if (thumbnailCard != null) {
                thumbnailCard.setVisibility(VISIBLE);
            }
            thumbnail.setVisibility(VISIBLE);
            Glide.with(this)
                    .load(post.thumbnailUrl)
                    .centerCrop()
                    .into(thumbnail);
        } else {
            if (thumbnailCard != null) {
                thumbnailCard.setVisibility(GONE);
            }
            thumbnail.setVisibility(GONE);
        }

        String formattedTime = DateTimeUtils.formatRelativeTime(post.createdAt);
        if (!formattedTime.isEmpty()) {
            timeText.setText(" • " + formattedTime);
            timeText.setVisibility(VISIBLE);
        } else {
            timeText.setVisibility(GONE);
        }

        if (post.sourceName != null && !post.sourceName.trim().isEmpty() &&
                (post.author == null || !post.sourceName.equalsIgnoreCase(post.author.name))) {
            sourceText.setText("Nguồn: " + post.sourceName);
            sourceText.setVisibility(VISIBLE);
        } else {
            sourceText.setVisibility(GONE);
        }

        if (post.voteScore > 0) {
            voteScore.setText("▲ " + post.voteScore);
            voteScore.setTextColor(ContextCompat.getColor(getContext(), R.color.app_primary));
        } else if (post.voteScore < 0) {
            voteScore.setText("▼ " + Math.abs(post.voteScore));
            voteScore.setTextColor(ContextCompat.getColor(getContext(), R.color.app_danger));
        } else {
            voteScore.setText("• 0");
            voteScore.setTextColor(ContextCompat.getColor(getContext(), R.color.app_text_secondary));
        }

        commentsCount.setText("💬 " + post.commentsCount);
        commentsCount.setOnClickListener(v -> Navigator.navigateToPostComments(getContext(), post.id));
    }
}
