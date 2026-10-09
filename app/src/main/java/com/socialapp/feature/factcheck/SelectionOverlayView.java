package com.socialapp.feature.factcheck;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.View;

public class SelectionOverlayView extends View {

    public interface OnRegionSelectedListener {
        void onRegionConfirmed(Rect rect);
        void onCancel();
    }

    private final Paint dimPaint = new Paint();
    private final Paint clearPaint = new Paint();
    private final Paint borderPaint = new Paint();
    private final OnRegionSelectedListener listener;

    private float startX, startY, endX, endY;
    private boolean isSelecting = false;

    public SelectionOverlayView(Context context, OnRegionSelectedListener listener) {
        super(context);
        this.listener = listener;

        dimPaint.setColor(Color.parseColor("#80000000"));

        clearPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));

        borderPaint.setColor(Color.WHITE);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(4);

        setLayerType(LAYER_TYPE_SOFTWARE, null);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawRect(0, 0, getWidth(), getHeight(), dimPaint);

        if (isSelecting) {
            Rect rect = getSelectedRect();
            canvas.drawRect(rect, clearPaint);
            canvas.drawRect(rect, borderPaint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                startX = event.getX();
                startY = event.getY();
                endX = startX;
                endY = startY;
                isSelecting = true;
                invalidate();
                return true;

            case MotionEvent.ACTION_MOVE:
                endX = event.getX();
                endY = event.getY();
                invalidate();
                return true;

            case MotionEvent.ACTION_UP:
                isSelecting = false;
                Rect selectedRect = getSelectedRect();
                if (selectedRect.width() > 50 && selectedRect.height() > 50) {
                    if (listener != null) {
                        listener.onRegionConfirmed(selectedRect);
                    }
                } else {
                    if (listener != null) {
                        listener.onCancel();
                    }
                }
                invalidate();
                return true;
        }
        return super.onTouchEvent(event);
    }

    private Rect getSelectedRect() {
        int left = (int) Math.min(startX, endX);
        int top = (int) Math.min(startY, endY);
        int right = (int) Math.max(startX, endX);
        int bottom = (int) Math.max(startY, endY);
        return new Rect(left, top, right, bottom);
    }
}
