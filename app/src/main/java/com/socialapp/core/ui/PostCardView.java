package com.socialapp.core.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.google.android.material.card.MaterialCardView;
import com.socialapp.R;
import com.socialapp.core.network.dto.PostResource;

public class PostCardView extends MaterialCardView {

    private View postCardRoot;
    private UserAvatarView authorAvatar;
    private TextView authorName;
    private TextView timeText;
    private TextView categoryChip;
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
        LayoutInflater.from(context).inflate(R.layout.view_post_card, this, true);
        postCardRoot = findViewById(R.id.post_card_root);
        authorAvatar = findViewById(R.id.post_author_avatar);
        authorName = findViewById(R.id.post_author_name);
        timeText = findViewById(R.id.post_time_text);
        categoryChip = findViewById(R.id.post_category_chip);
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

        if (post.author != null) {
            authorName.setText(post.author.name != null ? post.author.name : "Nặc danh");
            authorAvatar.loadAvatar(post.author.getAvatarUrl());
        } else {
            authorName.setText("Nặc danh");
            authorAvatar.loadAvatar(null);
        }

        if (post.category != null && post.category.name != null) {
            categoryChip.setText(post.category.name);
            categoryChip.setVisibility(VISIBLE);
        } else {
            categoryChip.setVisibility(GONE);
        }

        if (post.thumbnailUrl != null && !post.thumbnailUrl.trim().isEmpty()) {
            thumbnail.setVisibility(VISIBLE);
            Glide.with(this)
                    .load(post.thumbnailUrl)
                    .centerCrop()
                    .into(thumbnail);
        } else {
            thumbnail.setVisibility(GONE);
        }

        timeText.setText(post.createdAt != null ? post.createdAt : "");

        if (post.sourceName != null && !post.sourceName.trim().isEmpty()) {
            sourceText.setText("Nguồn: " + post.sourceName);
            sourceText.setVisibility(VISIBLE);
        } else {
            sourceText.setVisibility(GONE);
        }

        voteScore.setText("▲ " + post.voteScore);
        commentsCount.setText("💬 " + post.commentsCount);
    }
}
