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
        setImageResource(android.R.drawable.ic_menu_gallery);
    }

    public void loadAvatar(@Nullable String url) {
        if (url != null && !url.trim().isEmpty()) {
            Glide.with(this)
                    .load(url)
                    .placeholder(android.R.drawable.ic_menu_gallery)
                    .error(android.R.drawable.ic_menu_gallery)
                    .circleCrop()
                    .into(this);
        } else {
            setImageResource(android.R.drawable.ic_menu_gallery);
        }
    }
}
