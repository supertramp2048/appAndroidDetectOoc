package com.socialapp.core.ui.blocks.view;

import android.content.Context;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.socialapp.R;
import com.socialapp.core.ui.blocks.layout.BlockPrecomputer;
import com.socialapp.core.ui.blocks.model.BlockPayloads;
import com.socialapp.core.ui.blocks.model.DetailActionBlock;

/**
 * Bottom editorial action bar block for PostDetailActivity (Source attribution, Upvote/Downvote,
 * Comments button, and Fact-Check button) matching activity_post_detail.xml 1:1.
 */
public class DetailActionBlockView extends LinearLayout {

    public interface OnDetailActionListener {
        void onSourceClick(@NonNull String sourceUrl);
        void onUpvoteClick();
        void onDownvoteClick();
        void onCommentsClick(int postId);
        void onFactCheckClick();
    }

    private final TextView sourceTextView;
    private final MaterialButton btnUpvote;
    private final TextView voteScoreTextView;
    private final MaterialButton btnDownvote;
    private final MaterialButton btnComments;
    private final MaterialButton btnFactcheck;

    @Nullable
    private OnDetailActionListener listener;
    @Nullable
    private DetailActionBlock boundBlock;

    public DetailActionBlockView(@NonNull Context context) {
        super(context);
        float density = context.getResources().getDisplayMetrics().density;
        setOrientation(VERTICAL);
        setBackgroundColor(ContextCompat.getColor(context, R.color.app_surface));
        setPadding(dp(18, density), 0, dp(18, density), dp(16, density));

        // 1. Source attribution box
        sourceTextView = new TextView(context);
        sourceTextView.setBackgroundResource(R.drawable.bg_source_item);
        sourceTextView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        sourceTextView.setPadding(dp(14, density), dp(10, density), dp(14, density), dp(10, density));
        sourceTextView.setTextColor(ContextCompat.getColor(context, R.color.app_primary));
        sourceTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        LayoutParams srcLp = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        srcLp.topMargin = dp(16, density);
        addView(sourceTextView, srcLp);

        // 2. Top hairline divider
        View topDivider = new View(context);
        topDivider.setBackgroundColor(ContextCompat.getColor(context, R.color.app_border));
        LayoutParams topDivLp = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(1, density)));
        topDivLp.topMargin = dp(24, density);
        addView(topDivider, topDivLp);

        // 3. Action bar row
        LinearLayout actionRow = new LinearLayout(context);
        actionRow.setOrientation(HORIZONTAL);
        actionRow.setGravity(Gravity.CENTER_VERTICAL);
        addView(actionRow, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56, density)));

        btnUpvote = createTextButton(context, "▲", 20f, density, dp(10, density));
        actionRow.addView(btnUpvote, new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(44, density)));

        voteScoreTextView = new TextView(context);
        voteScoreTextView.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        voteScoreTextView.setTextColor(ContextCompat.getColor(context, R.color.app_text_primary));
        voteScoreTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16.5f);
        LayoutParams scoreLp = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        scoreLp.setMarginStart(dp(6, density));
        scoreLp.setMarginEnd(dp(6, density));
        actionRow.addView(voteScoreTextView, scoreLp);

        btnDownvote = createTextButton(context, "▼", 20f, density, dp(10, density));
        actionRow.addView(btnDownvote, new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(44, density)));

        View spacer = new View(context);
        actionRow.addView(spacer, new LayoutParams(0, 0, 1f));

        btnComments = createTextButton(context, "💬 0 bình luận", 15f, density, dp(12, density));
        btnComments.setId(R.id.btn_comments);
        btnComments.setTextColor(ContextCompat.getColor(context, R.color.app_text_primary));
        actionRow.addView(btnComments, new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(44, density)));

        btnFactcheck = createTextButton(context, "🔍 Kiểm chứng", 15f, density, dp(12, density));
        btnFactcheck.setTextColor(ContextCompat.getColor(context, R.color.app_primary));
        LayoutParams fcLp = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(44, density));
        fcLp.setMarginStart(dp(8, density));
        actionRow.addView(btnFactcheck, fcLp);

        // 4. Bottom hairline divider
        View bottomDivider = new View(context);
        bottomDivider.setBackgroundColor(ContextCompat.getColor(context, R.color.app_border));
        LayoutParams botDivLp = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(1, density)));
        botDivLp.bottomMargin = dp(8, density);
        addView(bottomDivider, botDivLp);

        sourceTextView.setOnClickListener(v -> {
            if (listener != null && boundBlock != null && boundBlock.getSourceUrl() != null) {
                listener.onSourceClick(boundBlock.getSourceUrl());
            }
        });
        btnUpvote.setOnClickListener(v -> {
            if (listener != null) listener.onUpvoteClick();
        });
        btnDownvote.setOnClickListener(v -> {
            if (listener != null) listener.onDownvoteClick();
        });
        btnComments.setOnClickListener(v -> {
            if (listener != null && boundBlock != null) listener.onCommentsClick(boundBlock.getPostId());
        });
        btnFactcheck.setOnClickListener(v -> {
            if (listener != null) listener.onFactCheckClick();
        });
    }

    @NonNull
    private static MaterialButton createTextButton(
            @NonNull Context context,
            @NonNull String label,
            float textSizeSp,
            float density,
            int padH) {
        MaterialButton btn = new MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        btn.setStrokeWidth(0);
        btn.setBackgroundColor(0x00000000);
        btn.setInsetTop(0);
        btn.setInsetBottom(0);
        btn.setMinWidth(0);
        btn.setMinimumWidth(0);
        btn.setPadding(padH, 0, padH, 0);
        btn.setText(label);
        btn.setAllCaps(false);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp);
        btn.setTypeface(Typeface.SANS_SERIF, Typeface.BOLD);
        btn.setTextColor(ContextCompat.getColor(context, R.color.app_text_secondary));
        return btn;
    }

    public void setInteractionListener(@Nullable OnDetailActionListener listener) {
        this.listener = listener;
    }

    public void bind(@NonNull DetailActionBlock block) {
        this.boundBlock = block;
        if (block.getSourceName() != null) {
            sourceTextView.setText("Nguồn: " + block.getSourceName());
            sourceTextView.setVisibility(VISIBLE);
            sourceTextView.setClickable(block.getSourceUrl() != null);
        } else {
            sourceTextView.setVisibility(GONE);
        }

        btnFactcheck.setVisibility(block.isShowFactCheck() ? VISIBLE : GONE);
        applyMetrics(block.getVoteScore(), block.getUserVote(), block.getCommentsCount());
    }

    public void applyPayload(@NonNull DetailActionBlock updatedBlock, @NonNull BlockPayloads.DetailActionPayload payload) {
        this.boundBlock = updatedBlock;
        applyMetrics(payload.voteScore, payload.userVote, payload.commentsCount);
    }

    private void applyMetrics(int voteScore, int userVote, int commentsCount) {
        Context context = getContext();
        int primaryColor = ContextCompat.getColor(context, R.color.app_primary);
        int dangerColor = ContextCompat.getColor(context, R.color.app_danger);
        int secondaryTextColor = ContextCompat.getColor(context, R.color.app_text_secondary);
        int primaryTextColor = ContextCompat.getColor(context, R.color.app_text_primary);

        voteScoreTextView.setText(String.valueOf(voteScore));
        if (userVote == 1) {
            btnUpvote.setAlpha(1.0f);
            btnUpvote.setTextColor(primaryColor);
            btnDownvote.setAlpha(0.6f);
            btnDownvote.setTextColor(secondaryTextColor);
            voteScoreTextView.setTextColor(primaryColor);
        } else if (userVote == -1) {
            btnUpvote.setAlpha(0.6f);
            btnUpvote.setTextColor(secondaryTextColor);
            btnDownvote.setAlpha(1.0f);
            btnDownvote.setTextColor(dangerColor);
            voteScoreTextView.setTextColor(dangerColor);
        } else {
            btnUpvote.setAlpha(1.0f);
            btnUpvote.setTextColor(secondaryTextColor);
            btnDownvote.setAlpha(1.0f);
            btnDownvote.setTextColor(secondaryTextColor);
            voteScoreTextView.setTextColor(primaryTextColor);
        }

        btnComments.setText("💬 " + commentsCount + " bình luận");
    }

    private static int dp(int dp, float density) {
        return BlockPrecomputer.dpToPx(dp, density);
    }
}
