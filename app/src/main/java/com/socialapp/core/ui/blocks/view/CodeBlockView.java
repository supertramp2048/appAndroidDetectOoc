package com.socialapp.core.ui.blocks.view;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.socialapp.R;
import com.socialapp.core.ui.blocks.layout.BlockPrecomputer;
import com.socialapp.core.ui.blocks.model.CodeBlock;

/**
 * Horizontally scrollable monospace code block view mounted only when HTML contains <pre>.
 */
public class CodeBlockView extends FrameLayout {

    private final TextView codeTextView;

    public CodeBlockView(@NonNull Context context) {
        super(context);
        float density = context.getResources().getDisplayMetrics().density;
        int outerPadX = BlockPrecomputer.dpToPx(BlockPrecomputer.DETAIL_HORIZONTAL_PADDING_DP, density);
        int outerPadV = BlockPrecomputer.dpToPx(10, density);
        setBackgroundColor(ContextCompat.getColor(context, R.color.app_surface));
        setPadding(outerPadX, outerPadV, outerPadX, outerPadV);

        GradientDrawable boxBg = new GradientDrawable();
        boxBg.setColor(0xFFEEF2F6);
        boxBg.setCornerRadius(BlockPrecomputer.dpToPx(4, density));
        boxBg.setStroke(Math.max(1, BlockPrecomputer.dpToPx(1, density)), 0xFFD8E0EA);

        HorizontalScrollView scrollView = new HorizontalScrollView(context);
        scrollView.setBackground(boxBg);
        scrollView.setHorizontalScrollBarEnabled(true);
        scrollView.setFillViewport(true);

        codeTextView = new TextView(context);
        codeTextView.setTypeface(Typeface.MONOSPACE);
        codeTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        codeTextView.setTextColor(0xFF17212B);
        codeTextView.setLineSpacing(0f, 1.45f);
        codeTextView.setTextIsSelectable(true);
        int innerPad = BlockPrecomputer.dpToPx(12, density);
        codeTextView.setPadding(innerPad, innerPad, innerPad, innerPad);

        scrollView.addView(codeTextView, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        addView(scrollView, new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
    }

    public void bind(@NonNull CodeBlock block) {
        codeTextView.setText(block.getCodeText());
    }
}
