package com.example.myapplication;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;

public class SelectionOverlayView extends FrameLayout {

    private Paint paint;
    private Paint backgroundPaint;
    private Rect selectionRect;
    private float startX, startY;
    private OnRegionSelectedListener listener;
    private Button confirmButton;

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

        // Paint for the selection rectangle border
        paint = new Paint();
        paint.setColor(Color.RED);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(5f);

        // Paint for the darkened background
        backgroundPaint = new Paint();
        backgroundPaint.setColor(Color.parseColor("#99000000")); // Slightly darker semi-transparent black
        backgroundPaint.setStyle(Paint.Style.FILL);

        selectionRect = new Rect();

        confirmButton = new Button(getContext());
        confirmButton.setText("Confirm");
        confirmButton.setVisibility(GONE);
        confirmButton.setOnClickListener(v -> {
            if (listener != null) listener.onRegionConfirmed(new Rect(selectionRect));
        });
        
        LayoutParams lp = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        addView(confirmButton, lp);
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
                confirmButton.setVisibility(GONE);
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
                    confirmButton.setVisibility(VISIBLE);
                    confirmButton.setX(selectionRect.centerX() - confirmButton.getWidth() / 2f);
                    confirmButton.setY(selectionRect.bottom + 20);
                } else {
                    // If it was just a tap outside the buttons, we should probably close
                    if (listener != null) listener.onCancel();
                }
                return true;
        }
        return super.onTouchEvent(event);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        // Draw 4 rectangles around the selection to create the "hole" effect (Hardware acceleration friendly)
        // Top
        canvas.drawRect(0, 0, getWidth(), selectionRect.top, backgroundPaint);
        // Bottom
        canvas.drawRect(0, selectionRect.bottom, getWidth(), getHeight(), backgroundPaint);
        // Left
        canvas.drawRect(0, selectionRect.top, selectionRect.left, selectionRect.bottom, backgroundPaint);
        // Right
        canvas.drawRect(selectionRect.right, selectionRect.top, getWidth(), selectionRect.bottom, backgroundPaint);

        // Draw the red border around the bright hole
        canvas.drawRect(selectionRect, paint);
    }
}
