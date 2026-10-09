package com.socialapp.core.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.socialapp.R;

public class EmptyStateView extends LinearLayout {

    private TextView emptyMessageText;

    public EmptyStateView(@NonNull Context context) {
        super(context);
        init(context);
    }

    public EmptyStateView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public EmptyStateView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(@NonNull Context context) {
        LayoutInflater.from(context).inflate(R.layout.view_empty_state, this, true);
        emptyMessageText = findViewById(R.id.empty_message_text);
    }

    public void setEmptyMessage(@Nullable String message) {
        if (message != null && !message.trim().isEmpty()) {
            emptyMessageText.setText(message);
        } else {
            emptyMessageText.setText(R.string.empty_generic);
        }
    }
}
