package com.frlab.overlay;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

public class LyricsCanvasView extends View {

    private static final String[][] SAMPLE_LYRICS = new String[][]{
            {"Malam ini bintang jatuh perlahan", "Tonight the stars fall in slow motion"},
            {"Bass bergetar di balik layar kaca", "Sub-bass vibrating behind the glass"},
            {"Tembus sentuhan tanpa meredup cahaya", "Click-through active without losing glow"},
            {"Overdrive dua ratus persen menyala", "Two hundred percent luminance overdrive"},
            {"Lirik ganda mengikuti irama lagu", "Stacked double lyrics riding the beat"}
    };

    private final WindowManager windowManager;
    private final WindowManager.LayoutParams layoutParams;

    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint subStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint subFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pillBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pillTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF pillRect = new RectF();

    private final Handler animHandler = new Handler(Looper.getMainLooper());
    private int lyricIndex = 0;
    private long lineStartMs = System.currentTimeMillis();

    private float initialTouchX;
    private float initialTouchY;
    private int initialWindowX;
    private int initialWindowY;

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            long now = System.currentTimeMillis();
            long elapsed = now - lineStartMs;
            if (elapsed >= 3000L) {
                lyricIndex = (lyricIndex + 1) % SAMPLE_LYRICS.length;
                lineStartMs = now;
                elapsed = 0L;
            }
            invalidate();

            if (elapsed < 460L) {
                animHandler.postDelayed(this, 33L);
            } else {
                long waitMs = Math.max(50L, 3000L - elapsed);
                animHandler.postDelayed(this, waitMs);
            }
        }
    };

    public LyricsCanvasView(Context context, WindowManager wm, WindowManager.LayoutParams params) {
        super(context);
        this.windowManager = wm;
        this.layoutParams = params;
        setBackgroundColor(Color.TRANSPARENT);

        Typeface boldType = Typeface.create(Typeface.DEFAULT, Typeface.BOLD);
        Typeface medType = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL);

        strokePaint.setTypeface(boldType);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeJoin(Paint.Join.ROUND);
        strokePaint.setStrokeCap(Paint.Cap.ROUND);
        strokePaint.setTextAlign(Paint.Align.CENTER);

        glowPaint.setTypeface(boldType);
        glowPaint.setStyle(Paint.Style.STROKE);
        glowPaint.setStrokeJoin(Paint.Join.ROUND);
        glowPaint.setStrokeCap(Paint.Cap.ROUND);
        glowPaint.setTextAlign(Paint.Align.CENTER);

        fillPaint.setTypeface(boldType);
        fillPaint.setStyle(Paint.Style.FILL);
        fillPaint.setTextAlign(Paint.Align.CENTER);

        subStrokePaint.setTypeface(medType);
        subStrokePaint.setStyle(Paint.Style.STROKE);
        subStrokePaint.setStrokeJoin(Paint.Join.ROUND);
        subStrokePaint.setStrokeCap(Paint.Cap.ROUND);
        subStrokePaint.setTextAlign(Paint.Align.CENTER);

        subFillPaint.setTypeface(medType);
        subFillPaint.setStyle(Paint.Style.FILL);
        subFillPaint.setTextAlign(Paint.Align.CENTER);

        pillBgPaint.setStyle(Paint.Style.FILL);
        pillTextPaint.setTypeface(boldType);
        pillTextPaint.setTextAlign(Paint.Align.CENTER);
    }

    private float dp(float val) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, val, getResources().getDisplayMetrics());
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        lineStartMs = System.currentTimeMillis();
        animHandler.removeCallbacks(ticker);
        animHandler.post(ticker);
    }

    @Override
    protected void onDetachedFromWindow() {
        animHandler.removeCallbacks(ticker);
        super.onDetachedFromWindow();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int screenW = getResources().getDisplayMetrics().widthPixels;
        int w = (int) (screenW * 0.90f);
        int h = (int) dp(125f);
        setMeasuredDimension(w, h);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int opacityPct = LabState.opacityPercent;
        float baseAlphaRatio = Math.min(100, Math.max(0, opacityPct)) / 100f;
        float overdriveRatio = opacityPct > 100 ? (opacityPct - 100) / 100f : 0f;

        int coreAlpha = Math.round(255f * baseAlphaRatio);
        if (coreAlpha <= 0) {
            return;
        }

        long elapsed = System.currentTimeMillis() - lineStartMs;
        float enterProgress = Math.min(1f, elapsed / 260f);
        float ease = 1f - (1f - enterProgress) * (1f - enterProgress);

        float followProgress = Math.min(1f, Math.max(0f, (elapsed - 120L) / 300f));
        float followEase = 1f - (1f - followProgress) * (1f - followProgress);

        float centerX = getWidth() / 2f;
        float cursorY;

        if (LabState.showDiagnosticPill) {
            String modeTag = "M" + LabState.activeMethod
                    + " | " + (LabState.lockClickThrough ? "LOCK" : "DRAG")
                    + " | WinAlpha=" + String.format("%.2f", LabState.lastAppliedWindowAlpha)
                    + " | Op=" + opacityPct + "%";
            pillTextPaint.setTextSize(dp(10.5f));
            pillTextPaint.setColor(Color.argb(coreAlpha, 224, 242, 254));
            float textW = pillTextPaint.measureText(modeTag);
            float padH = dp(10f);
            float pillH = dp(18f);
            pillRect.set(centerX - textW / 2f - padH, dp(4f), centerX + textW / 2f + padH, dp(4f) + pillH);
            pillBgPaint.setColor(Color.argb(Math.round(160f * baseAlphaRatio), 15, 23, 42));
            canvas.drawRoundRect(pillRect, pillH / 2f, pillH / 2f, pillBgPaint);
            canvas.drawText(modeTag, centerX, dp(4f) + pillH * 0.72f, pillTextPaint);
            cursorY = dp(48f);
        } else {
            cursorY = dp(34f);
        }

        String primaryLine = SAMPLE_LYRICS[lyricIndex][0];
        String secondaryLine = SAMPLE_LYRICS[lyricIndex][1];

        float mainTextSize = dp(19f);
        float mainOffsetY = (1f - ease) * dp(8f);
        int animCoreAlpha = Math.round(coreAlpha * (0.35f + 0.65f * ease));

        drawEnhancedLine(
                canvas,
                primaryLine,
                centerX,
                cursorY + mainOffsetY,
                mainTextSize,
                animCoreAlpha,
                overdriveRatio,
                Color.rgb(255, 255, 255),
                Color.rgb(56, 189, 248),
                true
        );

        if (LabState.doubleLyrics) {
            float gapPx = dp(LabState.doubleGapDp);
            float subTextSize = dp(14.5f);
            float subY = cursorY + mainTextSize + gapPx + (1f - followEase) * dp(10f);
            int subAlpha = Math.round(coreAlpha * 0.90f * (0.25f + 0.75f * followEase));

            drawEnhancedLine(
                    canvas,
                    secondaryLine,
                    centerX,
                    subY,
                    subTextSize,
                    subAlpha,
                    overdriveRatio,
                    Color.rgb(186, 230, 253),
                    Color.rgb(168, 85, 247),
                    false
            );
        }
    }

    private void drawEnhancedLine(
            Canvas canvas,
            String text,
            float x,
            float y,
            float textSize,
            int alpha,
            float overdrive,
            int fillColor,
            int glowColor,
            boolean isPrimary
    ) {
        Paint sPaint = isPrimary ? strokePaint : subStrokePaint;
        Paint fPaint = isPrimary ? fillPaint : subFillPaint;

        sPaint.setTextSize(textSize);
        fPaint.setTextSize(textSize);

        // Pass 1: Outer Neon Aura Stroke (murni vektor GPU tanpa ShadowLayer)
        if (isPrimary || overdrive > 0.05f) {
            glowPaint.setTextSize(textSize);
            glowPaint.setStrokeWidth(dp(isPrimary ? 6.0f : 4.5f) + overdrive * dp(4.5f));
            int glowAlpha = Math.min(255, Math.round(alpha * (0.28f + 0.52f * overdrive)));
            glowPaint.setColor(Color.argb(glowAlpha, Color.red(glowColor), Color.green(glowColor), Color.blue(glowColor)));
            canvas.drawText(text, x, y, glowPaint);
        }

        // Pass 2: Outline hitam pekat untuk kontras tinggi di latar terang
        float blackStrokeW = dp(isPrimary ? 3.8f : 3.0f) + (overdrive * dp(1.2f));
        sPaint.setStrokeWidth(blackStrokeW);
        sPaint.setColor(Color.argb(alpha, 0, 0, 0));
        canvas.drawText(text, x, y, sPaint);

        // Pass 3: Core text fill
        fPaint.setColor(Color.argb(alpha, Color.red(fillColor), Color.green(fillColor), Color.blue(fillColor)));
        canvas.drawText(text, x, y, fPaint);

        // Pass 4: Overdrive extra luminance pass (101% - 200%)
        if (overdrive > 0.01f) {
            int boostAlpha = Math.min(255, Math.round(alpha * overdrive));
            fPaint.setColor(Color.argb(boostAlpha, 255, 255, 255));
            canvas.drawText(text, x, y, fPaint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (LabState.lockClickThrough) {
            return false;
        }
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                initialWindowX = layoutParams.x;
                initialWindowY = layoutParams.y;
                initialTouchX = event.getRawX();
                initialTouchY = event.getRawY();
                return true;

            case MotionEvent.ACTION_MOVE:
                layoutParams.x = initialWindowX + Math.round(event.getRawX() - initialTouchX);
                layoutParams.y = initialWindowY + Math.round(event.getRawY() - initialTouchY);
                LabState.posX = layoutParams.x;
                LabState.posY = layoutParams.y;
                try {
                    windowManager.updateViewLayout(this, layoutParams);
                } catch (Exception ignored) {
                }
                return true;

            default:
                return super.onTouchEvent(event);
        }
    }
}
