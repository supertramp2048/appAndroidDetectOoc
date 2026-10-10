package com.socialapp.core.ui;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.google.android.material.imageview.ShapeableImageView;
import com.socialapp.R;

public class UserAvatarView extends ShapeableImageView {

    public UserAvatarView(@NonNull Context context) {
        super(context);
        init();
    }

    public UserAvatarView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public UserAvatarView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setImageResource(R.drawable.ic_profile_placeholder);
    }

    public void loadAvatar(@Nullable String url) {
        if (url != null && !url.trim().isEmpty()) {
            Glide.with(this)
                    .load(url)
                    .placeholder(R.drawable.ic_profile_placeholder)
                    .error(R.drawable.ic_profile_placeholder)
                    .circleCrop()
                    .into(this);
        } else {
            setImageResource(R.drawable.ic_profile_placeholder);
        }
    }
}
