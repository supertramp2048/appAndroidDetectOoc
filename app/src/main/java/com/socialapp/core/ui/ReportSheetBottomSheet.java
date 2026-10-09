package com.socialapp.core.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.socialapp.R;

public class ReportSheetBottomSheet extends BottomSheetDialogFragment {

    public interface OnReportSubmitListener {
        void onSubmit(@NonNull String reason);
    }

    private OnReportSubmitListener listener;

    public static ReportSheetBottomSheet newInstance() {
        return new ReportSheetBottomSheet();
    }

    public void setOnReportSubmitListener(@Nullable OnReportSubmitListener listener) {
        this.listener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_report, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        EditText reasonInput = view.findViewById(R.id.report_reason_input);
        TextView errorText = view.findViewById(R.id.report_error_text);
        Button btnCancel = view.findViewById(R.id.btn_report_cancel);
        Button btnSubmit = view.findViewById(R.id.btn_report_submit);

        btnCancel.setOnClickListener(v -> dismiss());

        btnSubmit.setOnClickListener(v -> {
            String reason = reasonInput.getText().toString().trim();
            if (reason.length() < 10 || reason.length() > 500) {
                errorText.setText("Lý do báo cáo phải từ 10 đến 500 ký tự.");
                errorText.setVisibility(View.VISIBLE);
                return;
            }

            errorText.setVisibility(View.GONE);
            dismiss();
            if (listener != null) {
                listener.onSubmit(reason);
            }
        });
    }
}
