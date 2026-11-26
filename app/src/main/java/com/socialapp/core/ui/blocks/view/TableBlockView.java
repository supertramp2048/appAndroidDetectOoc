package com.socialapp.core.ui.blocks.view;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.socialapp.R;
import com.socialapp.core.ui.blocks.layout.BlockPrecomputer;
import com.socialapp.core.ui.blocks.model.TableBlock;

import java.util.List;

/**
 * Horizontally scrollable TableLayout view mounted only when HTML contains <table>.
 */
public class TableBlockView extends FrameLayout {

    private final TableLayout tableLayout;
    private final float density;
    private int boundContentHash = 0;

    public TableBlockView(@NonNull Context context) {
        super(context);
        this.density = context.getResources().getDisplayMetrics().density;
        int outerPadX = BlockPrecomputer.dpToPx(BlockPrecomputer.DETAIL_HORIZONTAL_PADDING_DP, density);
        int outerPadV = BlockPrecomputer.dpToPx(12, density);
        setBackgroundColor(ContextCompat.getColor(context, R.color.app_surface));
        setPadding(outerPadX, outerPadV, outerPadX, outerPadV);

        HorizontalScrollView scrollView = new HorizontalScrollView(context);
        scrollView.setFillViewport(true);

        tableLayout = new TableLayout(context);
        tableLayout.setStretchAllColumns(true);

        scrollView.addView(tableLayout, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        addView(scrollView, new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
    }

    public void bind(@NonNull TableBlock block) {
        if (boundContentHash == block.getContentHash() && tableLayout.getChildCount() > 0) {
            return;
        }
        boundContentHash = block.getContentHash();
        tableLayout.removeAllViews();

        Context ctx = getContext();
        int cellPadH = BlockPrecomputer.dpToPx(12, density);
        int cellPadV = BlockPrecomputer.dpToPx(10, density);
        int strokePx = Math.max(1, BlockPrecomputer.dpToPx(1, density));

        List<TableBlock.TableRow> rows = block.getRows();
        for (TableBlock.TableRow row : rows) {
            TableRow tr = new TableRow(ctx);
            for (TableBlock.TableCell cell : row.cells) {
                TextView tv = new TextView(ctx);
                tv.setText(cell.text);
                tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
                tv.setTextColor(0xFF17212B);
                tv.setTypeface(Typeface.SANS_SERIF, cell.header ? Typeface.BOLD : Typeface.NORMAL);
                tv.setPadding(cellPadH, cellPadV, cellPadH, cellPadV);

                GradientDrawable cellBg = new GradientDrawable();
                cellBg.setColor(cell.header ? 0xFFEEF2F6 : 0xFFFFFFFF);
                cellBg.setStroke(strokePx, 0xFFD8E0EA);
                tv.setBackground(cellBg);

                tr.addView(tv, new TableRow.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                ));
            }
            tableLayout.addView(tr, new TableLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            ));
        }
    }
}
