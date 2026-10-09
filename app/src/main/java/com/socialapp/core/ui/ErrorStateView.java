package com.socialapp.core.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.socialapp.R;

public class ErrorStateView extends LinearLayout {

    private TextView errorMessageText;
    private Button errorRetryButton;

    public ErrorStateView(@NonNull Context context) {
        super(context);
        init(context);
    }

    public ErrorStateView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public ErrorStateView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(@NonNull Context context) {
        LayoutInflater.from(context).inflate(R.layout.view_error_state, this, true);
        errorMessageText = findViewById(R.id.error_message_text);
        errorRetryButton = findViewById(R.id.error_retry_button);
    }

    public void setErrorMessage(@Nullable String message) {
        if (message != null && !message.trim().isEmpty()) {
            errorMessageText.setText(message);
        } else {
            errorMessageText.setText(R.string.error_generic);
        }
    }

    public void setOnRetryClickListener(@Nullable OnClickListener listener) {
        errorRetryButton.setOnClickListener(listener);
    }
}
