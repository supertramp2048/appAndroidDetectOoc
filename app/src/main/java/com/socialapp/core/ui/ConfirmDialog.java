package com.socialapp.core.ui;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.socialapp.R;

public class ConfirmDialog extends Dialog {

    public interface OnConfirmListener {
        void onConfirm();
    }

    private final String titleText;
    private final String messageText;
    private final OnConfirmListener listener;

    public ConfirmDialog(@NonNull Context context, @NonNull String titleText,
                         @NonNull String messageText, @Nullable OnConfirmListener listener) {
        super(context);
        this.titleText = titleText;
        this.messageText = messageText;
        this.listener = listener;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        View view = LayoutInflater.from(getContext()).inflate(R.layout.dialog_confirm, null);
        setContentView(view);

        TextView tvTitle = view.findViewById(R.id.dialog_title);
        TextView tvMessage = view.findViewById(R.id.dialog_message);
        Button btnCancel = view.findViewById(R.id.btn_cancel);
        Button btnConfirm = view.findViewById(R.id.btn_confirm);

        tvTitle.setText(titleText);
        tvMessage.setText(messageText);

        btnCancel.setOnClickListener(v -> dismiss());
        btnConfirm.setOnClickListener(v -> {
            dismiss();
            if (listener != null) {
                listener.onConfirm();
            }
        });
    }
}
