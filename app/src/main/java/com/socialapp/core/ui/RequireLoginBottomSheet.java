package com.socialapp.core.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.socialapp.R;

public class RequireLoginBottomSheet extends BottomSheetDialogFragment {

    public interface OnLoginClickListener {
        void onLoginClick();
    }

    private OnLoginClickListener listener;

    public static RequireLoginBottomSheet newInstance() {
        return new RequireLoginBottomSheet();
    }

    public void setOnLoginClickListener(@Nullable OnLoginClickListener listener) {
        this.listener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_require_login, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Button btnCancel = view.findViewById(R.id.btn_require_login_cancel);
        Button btnSubmit = view.findViewById(R.id.btn_require_login_submit);

        btnCancel.setOnClickListener(v -> dismiss());

        btnSubmit.setOnClickListener(v -> {
            dismiss();
            if (listener != null) {
                listener.onLoginClick();
            }
        });
    }
}
