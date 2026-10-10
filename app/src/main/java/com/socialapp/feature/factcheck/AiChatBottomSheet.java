package com.socialapp.feature.factcheck;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.chip.Chip;
import com.socialapp.MyApplication;
import com.socialapp.R;
import com.socialapp.core.navigation.Navigator;
import com.socialapp.core.network.FactCheckApiService;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AiChatBottomSheet extends BottomSheetDialogFragment {

    private EditText etQuery;
    private ProgressBar progressBar;
    private View cardAnswer;
    private TextView tvAnswer;
    private TextView tvRecLabel;
    private LinearLayout containerRecs;

    public static AiChatBottomSheet newInstanceForSearch() {
        return new AiChatBottomSheet();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_ai_chat, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        etQuery = view.findViewById(R.id.et_ai_chat_query);
        progressBar = view.findViewById(R.id.ai_chat_progress);
        cardAnswer = view.findViewById(R.id.card_ai_answer);
        tvAnswer = view.findViewById(R.id.tv_ai_answer);
        tvRecLabel = view.findViewById(R.id.tv_recommendations_label);
        containerRecs = view.findViewById(R.id.container_ai_recommendations);

        Chip chipTech = view.findViewById(R.id.chip_prompt_tech);
        Chip chipWar = view.findViewById(R.id.chip_prompt_war);
        Chip chipEconomy = view.findViewById(R.id.chip_prompt_economy);

        if (chipTech != null) {
            chipTech.setOnClickListener(v -> submitQuickPrompt("Tìm các bài viết về công nghệ và trí tuệ nhân tạo AI"));
        }
        if (chipWar != null) {
            chipWar.setOnClickListener(v -> submitQuickPrompt("Tìm các bài viết về chiến tranh và tình hình địa chính trị"));
        }
        if (chipEconomy != null) {
            chipEconomy.setOnClickListener(v -> submitQuickPrompt("Tìm các bài viết về kinh tế và thị trường tài chính"));
        }

        view.findViewById(R.id.btn_close_ai_chat).setOnClickListener(v -> dismiss());

        Button btnSend = view.findViewById(R.id.btn_send_ai_query);
        btnSend.setOnClickListener(v -> sendAiQuery());
    }

    private void submitQuickPrompt(String prompt) {
        if (etQuery != null) {
            etQuery.setText(prompt);
            sendAiQuery();
        }
    }

    private void sendAiQuery() {
        String query = etQuery.getText().toString().trim();
        if (query.isEmpty()) {
            Toast.makeText(requireContext(), "Vui lòng nhập chủ đề bài viết cần tìm", Toast.LENGTH_SHORT).show();
            return;
        }

        progressBar.setVisibility(View.VISIBLE);
        cardAnswer.setVisibility(View.GONE);

        FactCheckApiService apiService = MyApplication.getAppContainer(requireContext()).getFactCheckApiService();
        ChatRequest request = new ChatRequest(query);
        String apiKey = "my_super_secret_key_2026";

        apiService.chat(apiKey, request).enqueue(new Callback<ChatResponse>() {
            @Override
            public void onResponse(@NonNull Call<ChatResponse> call, @NonNull Response<ChatResponse> response) {
                if (!isAdded()) return;
                progressBar.setVisibility(View.GONE);

                if (response.isSuccessful() && response.body() != null) {
                    ChatResponse result = response.body();
                    displayAnswer(result);
                } else {
                    Toast.makeText(requireContext(), "Lỗi phản hồi từ Trợ lý AI Tìm kiếm", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<ChatResponse> call, @NonNull Throwable t) {
                if (!isAdded()) return;
                progressBar.setVisibility(View.GONE);
                Toast.makeText(requireContext(), "Lỗi kết nối Trợ lý AI: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void displayAnswer(ChatResponse response) {
        cardAnswer.setVisibility(View.VISIBLE);
        tvAnswer.setText(response.answer != null && !response.answer.trim().isEmpty() ? response.answer : "Không tìm thấy kết quả phù hợp từ AI.");

        containerRecs.removeAllViews();
        if (response.recommendations != null && !response.recommendations.isEmpty()) {
            tvRecLabel.setVisibility(View.VISIBLE);
            for (ChatResponse.ArticleRec rec : response.recommendations) {
                if (rec == null) continue;

                TextView textView = new TextView(requireContext());
                String title = rec.title != null && !rec.title.trim().isEmpty() ? rec.title : "Bài viết tham khảo";
                textView.setText("📄 " + title);
                textView.setTextColor(requireContext().getColor(R.color.app_primary));
                textView.setTextSize(14);
                textView.setPadding(0, dp(6), 0, dp(6));

                textView.setOnClickListener(v -> {
                    if (rec.id != null && !rec.id.trim().isEmpty()) {
                        try {
                            int postId = Integer.parseInt(rec.id.trim());
                            Navigator.navigateToPostDetail(requireContext(), postId);
                            dismiss();
                            return;
                        } catch (Exception ignored) {
                        }
                    }
                    if (rec.url != null && !rec.url.trim().isEmpty()) {
                        Navigator.navigateTo(requireContext(), rec.url);
                        dismiss();
                    }
                });
                containerRecs.addView(textView);
            }
        } else {
            tvRecLabel.setVisibility(View.GONE);
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
