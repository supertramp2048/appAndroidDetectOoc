package com.socialapp.feature.factcheck;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.NestedScrollView;

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
    private LinearLayout containerChatMessages;
    private NestedScrollView scrollChatMessages;

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
        containerChatMessages = view.findViewById(R.id.container_chat_messages);
        scrollChatMessages = view.findViewById(R.id.scroll_chat_messages);

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

        ImageButton btnSend = view.findViewById(R.id.btn_send_ai_query);
        if (btnSend != null) {
            btnSend.setOnClickListener(v -> sendAiQuery());
        }

        if (etQuery != null) {
            etQuery.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == EditorInfo.IME_ACTION_SEND) {
                    sendAiQuery();
                    return true;
                }
                return false;
            });
        }

        addWelcomeAiMessage();
    }

    private void addWelcomeAiMessage() {
        View aiMsgView = LayoutInflater.from(requireContext()).inflate(R.layout.item_ai_chat_message, containerChatMessages, false);
        View layoutUser = aiMsgView.findViewById(R.id.layout_user_message);
        View layoutAi = aiMsgView.findViewById(R.id.layout_ai_message);
        ProgressBar progress = aiMsgView.findViewById(R.id.progress_ai_thinking);
        TextView tvAiMsg = aiMsgView.findViewById(R.id.tv_ai_message);

        layoutUser.setVisibility(View.GONE);
        layoutAi.setVisibility(View.VISIBLE);
        progress.setVisibility(View.GONE);
        tvAiMsg.setVisibility(View.VISIBLE);
        tvAiMsg.setText(R.string.ai_chat_welcome_message);

        containerChatMessages.addView(aiMsgView);
        scrollToBottom();
    }

    private void submitQuickPrompt(String prompt) {
        if (etQuery != null) {
            etQuery.setText(prompt);
            sendAiQuery();
        }
    }

    private void sendAiQuery() {
        if (etQuery == null) return;
        String query = etQuery.getText().toString().trim();
        if (query.isEmpty()) {
            Toast.makeText(requireContext(), "Vui lòng nhập chủ đề bài viết cần tìm", Toast.LENGTH_SHORT).show();
            return;
        }

        etQuery.setText("");

        addUserMessage(query);
        View aiThinkingView = addAiThinkingMessage();

        FactCheckApiService apiService = MyApplication.getAppContainer(requireContext()).getFactCheckApiService();
        ChatRequest request = new ChatRequest(query);
        String apiKey = "my_super_secret_key_2026";

        apiService.chat(apiKey, request).enqueue(new Callback<ChatResponse>() {
            @Override
            public void onResponse(@NonNull Call<ChatResponse> call, @NonNull Response<ChatResponse> response) {
                if (!isAdded()) return;

                if (response.isSuccessful() && response.body() != null) {
                    updateAiMessageSuccess(aiThinkingView, response.body());
                } else {
                    updateAiMessageError(aiThinkingView, "Lỗi phản hồi từ Trợ lý AI Tìm kiếm.");
                }
            }

            @Override
            public void onFailure(@NonNull Call<ChatResponse> call, @NonNull Throwable t) {
                if (!isAdded()) return;
                updateAiMessageError(aiThinkingView, "Lỗi kết nối Trợ lý AI: " + t.getMessage());
            }
        });
    }

    private void addUserMessage(String messageText) {
        View msgView = LayoutInflater.from(requireContext()).inflate(R.layout.item_ai_chat_message, containerChatMessages, false);
        View layoutUser = msgView.findViewById(R.id.layout_user_message);
        View layoutAi = msgView.findViewById(R.id.layout_ai_message);
        TextView tvUserMsg = msgView.findViewById(R.id.tv_user_message);

        layoutUser.setVisibility(View.VISIBLE);
        layoutAi.setVisibility(View.GONE);
        tvUserMsg.setText(messageText);

        containerChatMessages.addView(msgView);
        scrollToBottom();
    }

    private View addAiThinkingMessage() {
        View msgView = LayoutInflater.from(requireContext()).inflate(R.layout.item_ai_chat_message, containerChatMessages, false);
        View layoutUser = msgView.findViewById(R.id.layout_user_message);
        View layoutAi = msgView.findViewById(R.id.layout_ai_message);
        ProgressBar progress = msgView.findViewById(R.id.progress_ai_thinking);
        TextView tvAiMsg = msgView.findViewById(R.id.tv_ai_message);

        layoutUser.setVisibility(View.GONE);
        layoutAi.setVisibility(View.VISIBLE);
        progress.setVisibility(View.VISIBLE);
        tvAiMsg.setVisibility(View.GONE);

        containerChatMessages.addView(msgView);
        scrollToBottom();
        return msgView;
    }

    private void updateAiMessageSuccess(View aiMsgView, ChatResponse response) {
        ProgressBar progress = aiMsgView.findViewById(R.id.progress_ai_thinking);
        TextView tvAiMsg = aiMsgView.findViewById(R.id.tv_ai_message);
        TextView tvRecHeader = aiMsgView.findViewById(R.id.tv_ai_recommendations_header);
        LinearLayout containerRecs = aiMsgView.findViewById(R.id.container_ai_recommendations);

        progress.setVisibility(View.GONE);
        tvAiMsg.setVisibility(View.VISIBLE);

        String answer = response.answer != null && !response.answer.trim().isEmpty()
                ? response.answer.trim()
                : "Không tìm thấy kết quả phù hợp từ AI.";
        tvAiMsg.setText(answer);

        containerRecs.removeAllViews();
        if (response.recommendations != null && !response.recommendations.isEmpty()) {
            tvRecHeader.setVisibility(View.VISIBLE);
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
                    } else {
                        Toast.makeText(requireContext(), "Không thể mở bài viết", Toast.LENGTH_SHORT).show();
                    }
                });

                containerRecs.addView(textView);
            }
        } else {
            tvRecHeader.setVisibility(View.GONE);
        }

        scrollToBottom();
    }

    private void updateAiMessageError(View aiMsgView, String errorMessage) {
        ProgressBar progress = aiMsgView.findViewById(R.id.progress_ai_thinking);
        TextView tvAiMsg = aiMsgView.findViewById(R.id.tv_ai_message);

        progress.setVisibility(View.GONE);
        tvAiMsg.setVisibility(View.VISIBLE);
        tvAiMsg.setText(errorMessage);

        scrollToBottom();
    }

    private void scrollToBottom() {
        if (scrollChatMessages != null) {
            scrollChatMessages.post(() -> scrollChatMessages.fullScroll(View.FOCUS_DOWN));
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
