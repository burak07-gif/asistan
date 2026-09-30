package com.asistan.voice;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;
import android.view.animation.LinearInterpolator;

public final class BlackHoleView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final ValueAnimator rotation;
    private float angle;
    private float downX;
    private float downY;
    private float previousRawX;
    private float previousRawY;
    private boolean dragged;

    public BlackHoleView(Context context) {
        super(context);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        rotation = ValueAnimator.ofFloat(0f, 360f);
        rotation.setDuration(7000);
        rotation.setRepeatCount(ValueAnimator.INFINITE);
        rotation.setInterpolator(new LinearInterpolator());
        rotation.addUpdateListener(animator -> {
            angle = (float) animator.getAnimatedValue();
            invalidate();
        });
        rotation.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        rotation.cancel();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float radius = Math.min(cx, cy) * 0.83f;

        paint.setShader(new RadialGradient(
                cx, cy, radius,
                new int[]{0x0070E7FF, 0x3370E7FF, 0x9966A8FF, 0x0066A8FF},
                new float[]{0f, 0.55f, 0.82f, 1f},
                Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, radius, paint);
        paint.setShader(null);

        canvas.save();
        canvas.rotate(angle, cx, cy);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(dp(5));
        paint.setShader(new LinearGradient(
                cx - radius, cy, cx + radius, cy,
                new int[]{0x003A83FF, 0xFF85F4FF, 0xFF8E72FF, 0x003A83FF},
                null, Shader.TileMode.CLAMP));
        RectF ring = new RectF(cx - radius * 0.87f, cy - radius * 0.35f,
                cx + radius * 0.87f, cy + radius * 0.35f);
        canvas.drawOval(ring, paint);
        paint.setShader(null);
        paint.setStrokeWidth(dp(1.2f));
        paint.setColor(0x99B5F8FF);
        canvas.drawOval(
                new RectF(cx - radius * 0.73f, cy - radius * 0.21f,
                        cx + radius * 0.73f, cy + radius * 0.21f),
                paint);
        canvas.restore();

        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new RadialGradient(
                cx - radius * 0.1f, cy - radius * 0.12f, radius * 0.64f,
                new int[]{0xFF050610, 0xFF02030A, 0x0002030A},
                null, Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, radius * 0.52f, paint);
        paint.setShader(null);
        paint.setColor(0xFF02030A);
        canvas.drawCircle(cx, cy, radius * 0.30f, paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1));
        paint.setColor(0x55D7FAFF);
        canvas.drawCircle(cx, cy, radius * 0.96f, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    public void beginDrag(float rawX, float rawY) {
        downX = rawX;
        downY = rawY;
        previousRawX = rawX;
        previousRawY = rawY;
        dragged = false;
    }

    public void track(float rawX, float rawY) {
        if (Math.abs(rawX - downX) + Math.abs(rawY - downY) > dp(10)) {
            dragged = true;
        }
        previousRawX = rawX;
        previousRawY = rawY;
    }

    public float lastRawX() {
        return previousRawX;
    }

    public float lastRawY() {
        return previousRawY;
    }

    public boolean wasDragged() {
        return dragged;
    }

    public void finishDrag() {
        previousRawX = 0;
        previousRawY = 0;
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
