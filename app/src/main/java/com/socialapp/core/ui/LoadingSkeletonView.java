package com.socialapp.core.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.socialapp.R;

public class LoadingSkeletonView extends LinearLayout {

    public LoadingSkeletonView(@NonNull Context context) {
        super(context);
        init(context);
    }

    public LoadingSkeletonView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public LoadingSkeletonView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(@NonNull Context context) {
        LayoutInflater.from(context).inflate(R.layout.view_loading_skeleton, this, true);
    }
}
