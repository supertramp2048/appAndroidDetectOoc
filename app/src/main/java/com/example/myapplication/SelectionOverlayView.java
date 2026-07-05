package com.example.myapplication;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

public class SelectionOverlayView extends FrameLayout {

    private Paint paint;
    private Paint backgroundPaint;
    private Rect selectionRect;
    private float startX, startY;
    private OnRegionSelectedListener listener;
    private LinearLayout actionBar;
    private Button confirmButton;
    private Button cancelButton;

    public interface OnRegionSelectedListener {
        void onRegionConfirmed(Rect rect);
        void onCancel();
    }

    public SelectionOverlayView(Context context, OnRegionSelectedListener listener) {
        super(context);
        this.listener = listener;
        init();
    }

    private void init() {
        setWillNotDraw(false);

        paint = new Paint();
        paint.setColor(Color.parseColor("#1E5AA8"));
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(3));

        backgroundPaint = new Paint();
        backgroundPaint.setColor(Color.parseColor("#99000000"));
        backgroundPaint.setStyle(Paint.Style.FILL);

        selectionRect = new Rect();

        actionBar = new LinearLayout(getContext());
        actionBar.setOrientation(LinearLayout.HORIZONTAL);
        actionBar.setVisibility(GONE);

        cancelButton = new Button(getContext());
        cancelButton.setText("Hủy");
        cancelButton.setAllCaps(false);
        cancelButton.setTextColor(Color.parseColor("#17212B"));
        cancelButton.setBackground(createButtonBackground(Color.parseColor("#EEF2F6"), Color.parseColor("#D8E0EA")));
        cancelButton.setOnClickListener(v -> {
            if (listener != null) listener.onCancel();
        });

        confirmButton = new Button(getContext());
        confirmButton.setText("Dùng vùng này");
        confirmButton.setAllCaps(false);
        confirmButton.setTextColor(Color.WHITE);
        confirmButton.setBackground(createButtonBackground(Color.parseColor("#1E5AA8"), Color.parseColor("#1E5AA8")));
        confirmButton.setOnClickListener(v -> {
            if (listener != null) listener.onRegionConfirmed(new Rect(selectionRect));
        });

        LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        cancelParams.setMargins(dp(4), 0, dp(4), 0);

        LinearLayout.LayoutParams confirmParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        confirmParams.setMargins(dp(4), 0, dp(4), 0);

        actionBar.addView(cancelButton, cancelParams);
        actionBar.addView(confirmButton, confirmParams);

        LayoutParams lp = new LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        addView(actionBar, lp);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // If we are not touching and no selection has started, we shouldn't consume events
        // However, for regional selection, we need to intercept the first touch.
        
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                startX = event.getX();
                startY = event.getY();
                selectionRect.set((int) startX, (int) startY, (int) startX, (int) startY);
                actionBar.setVisibility(GONE);
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                selectionRect.set(
                        (int) Math.min(startX, event.getX()),
                        (int) Math.min(startY, event.getY()),
                        (int) Math.max(startX, event.getX()),
                        (int) Math.max(startY, event.getY())
                );
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
                if (selectionRect.width() > 10 && selectionRect.height() > 10) {
                    actionBar.setVisibility(VISIBLE);
                    actionBar.post(this::positionActionBar);
                } else {
                    if (listener != null) listener.onCancel();
                }
                return true;
        }
        return super.onTouchEvent(event);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        canvas.drawRect(0, 0, getWidth(), selectionRect.top, backgroundPaint);
        canvas.drawRect(0, selectionRect.bottom, getWidth(), getHeight(), backgroundPaint);
        canvas.drawRect(0, selectionRect.top, selectionRect.left, selectionRect.bottom, backgroundPaint);
        canvas.drawRect(selectionRect.right, selectionRect.top, getWidth(), selectionRect.bottom, backgroundPaint);

        canvas.drawRect(selectionRect, paint);
    }

    private void positionActionBar() {
        float x = selectionRect.centerX() - actionBar.getWidth() / 2f;
        x = Math.max(dp(12), Math.min(x, getWidth() - actionBar.getWidth() - dp(12)));

        float y = selectionRect.bottom + dp(12);
        if (y + actionBar.getHeight() > getHeight() - dp(12)) {
            y = selectionRect.top - actionBar.getHeight() - dp(12);
        }
        y = Math.max(dp(12), y);

        actionBar.setX(x);
        actionBar.setY(y);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private GradientDrawable createButtonBackground(int fillColor, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fillColor);
        drawable.setCornerRadius(dp(14));
        drawable.setStroke(dp(1), strokeColor);
        return drawable;
    }
}
