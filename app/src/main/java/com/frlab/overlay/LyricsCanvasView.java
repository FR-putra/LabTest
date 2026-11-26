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

public class LyricsCanvasView extends View {

    public interface DragCallback {
        void onOverlayDragged();
    }

    private static final String[][] SAMPLE_LYRICS = new String[][]{
            {"Malam ini bintang jatuh perlahan", "Tonight the stars fall in slow motion"},
            {"Bass bergetar di balik layar kaca", "Sub-bass vibrating behind the glass"},
            {"Satu jendela terang tembus beranda", "Single window bright glow passing touch"},
            {"Tumpukan sepuluh pass di satu kanvas", "Ten internal passes inside one canvas"},
            {"Cahaya overdrive dua ratus persen", "Two hundred percent luminance overdrive"}
    };

    private final boolean isCloneLayer;
    private final DragCallback dragCallback;

    private final Paint outerAuraPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint midGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint darkStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint innerRimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Paint subAuraPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint subDarkStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint subInnerRimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint subFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Paint pillBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pillBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pillTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF pillRect = new RectF();

    private final Handler animHandler = new Handler(Looper.getMainLooper());

    private float initialTouchX;
    private float initialTouchY;
    private int initialWindowX;
    private int initialWindowY;

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            long elapsedTotal = System.currentTimeMillis() - LabState.syncEpochMs;
            long elapsed = elapsedTotal % 3000L;
            invalidate();

            if (elapsed < 480L) {
                animHandler.postDelayed(this, 33L);
            } else {
                long waitMs = Math.max(40L, 3000L - elapsed);
                animHandler.postDelayed(this, waitMs);
            }
        }
    };

    public LyricsCanvasView(Context context, boolean isCloneLayer, DragCallback dragCallback) {
        super(context);
        this.isCloneLayer = isCloneLayer;
        this.dragCallback = dragCallback;
        setBackgroundColor(Color.TRANSPARENT);

        Typeface boldType = Typeface.create(Typeface.DEFAULT, Typeface.BOLD);

        setupStrokePaint(outerAuraPaint, boldType);
        setupStrokePaint(midGlowPaint, boldType);
        setupStrokePaint(darkStrokePaint, boldType);
        setupStrokePaint(innerRimPaint, boldType);
        setupFillPaint(fillPaint, boldType);

        setupStrokePaint(subAuraPaint, boldType);
        setupStrokePaint(subDarkStrokePaint, boldType);
        setupStrokePaint(subInnerRimPaint, boldType);
        setupFillPaint(subFillPaint, boldType);

        pillBgPaint.setStyle(Paint.Style.FILL);
        pillBorderPaint.setStyle(Paint.Style.STROKE);
        pillTextPaint.setTypeface(boldType);
        pillTextPaint.setTextAlign(Paint.Align.CENTER);
    }

    private void setupStrokePaint(Paint p, Typeface tf) {
        p.setTypeface(tf);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setTextAlign(Paint.Align.CENTER);
    }

    private void setupFillPaint(Paint p, Typeface tf) {
        p.setTypeface(tf);
        p.setStyle(Paint.Style.FILL);
        p.setTextAlign(Paint.Align.CENTER);
    }

    private float dp(float val) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, val, getResources().getDisplayMetrics());
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
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
        int w = (int) (screenW * 0.92f);
        int h = (int) dp(134f);
        setMeasuredDimension(w, h);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int passes = Math.min(10, Math.max(1, LabState.stackLayers));
        int opacityPct = LabState.opacityPercent;
        float baseAlphaRatio = Math.min(100, Math.max(0, opacityPct)) / 100f;
        float overdriveRatio = opacityPct > 100 ? (opacityPct - 100) / 100f : 0f;

        int coreAlpha = Math.round(255f * baseAlphaRatio);
        if (coreAlpha <= 0) {
            return;
        }

        long elapsedTotal = Math.max(0L, System.currentTimeMillis() - LabState.syncEpochMs);
        int lyricIndex = (int) ((elapsedTotal / 3000L) % SAMPLE_LYRICS.length);
        long elapsed = elapsedTotal % 3000L;

        float enterProgress = Math.min(1f, elapsed / 260f);
        float ease = 1f - (1f - enterProgress) * (1f - enterProgress);

        float followProgress = Math.min(1f, Math.max(0f, (elapsed - 110L) / 300f));
        float followEase = 1f - (1f - followProgress) * (1f - followProgress);

        float centerX = getWidth() / 2f;
        float cursorY;

        if (LabState.showDiagnosticPill) {
            String modeTag = "M" + LabState.activeMethod
                    + " | 1xWIN | Pass=" + passes + "x"
                    + " | " + (LabState.lockClickThrough ? "LOCK" : "DRAG")
                    + " | Alpha=" + String.format("%.2f", LabState.lastAppliedWindowAlpha)
                    + " | " + opacityPct + "%";
            pillTextPaint.setTextSize(dp(10f));
            int pillTextAlpha = Math.min(255, Math.round(coreAlpha * (0.88f + 0.12f * overdriveRatio)));
            pillTextPaint.setColor(Color.argb(pillTextAlpha, 255, 255, 255));

            float textW = pillTextPaint.measureText(modeTag);
            float padH = dp(10f);
            float pillH = dp(19f);
            pillRect.set(centerX - textW / 2f - padH, dp(5f), centerX + textW / 2f + padH, dp(5f) + pillH);

            float passBoost = Math.min(1f, (passes - 1) / 9f);
            int bgAlpha = Math.min(245, Math.round((150f + 55f * overdriveRatio + 35f * passBoost) * baseAlphaRatio));
            pillBgPaint.setColor(Color.argb(bgAlpha, 10, 18, 36));
            canvas.drawRoundRect(pillRect, pillH / 2f, pillH / 2f, pillBgPaint);

            pillBorderPaint.setStrokeWidth(dp(1.2f + overdriveRatio * 1.1f + passBoost * 0.6f));
            int borderAlpha = Math.min(255, Math.round(coreAlpha * (0.50f + 0.50f * Math.max(overdriveRatio, passBoost))));
            pillBorderPaint.setColor(Color.argb(borderAlpha, 125, 211, 252));
            canvas.drawRoundRect(pillRect, pillH / 2f, pillH / 2f, pillBorderPaint);

            canvas.drawText(modeTag, centerX, dp(5f) + pillH * 0.72f, pillTextPaint);
            cursorY = dp(52f);
        } else {
            cursorY = dp(38f);
        }

        String primaryLine = SAMPLE_LYRICS[lyricIndex][0];
        String secondaryLine = SAMPLE_LYRICS[lyricIndex][1];

        float mainTextSize = dp(19.5f);
        float mainOffsetY = (1f - ease) * dp(8f);
        int animCoreAlpha = Math.round(coreAlpha * (0.35f + 0.65f * ease));

        drawMultiPassLine(
                canvas,
                primaryLine,
                centerX,
                cursorY + mainOffsetY,
                mainTextSize,
                animCoreAlpha,
                overdriveRatio,
                passes,
                Color.rgb(255, 255, 255),
                Color.rgb(56, 189, 248),
                true
        );

        float gapPx = dp(10f);
        float subTextSize = dp(14.5f);
        float subY = cursorY + mainTextSize + gapPx + (1f - followEase) * dp(10f);
        int subAlpha = Math.round(coreAlpha * 0.96f * (0.25f + 0.75f * followEase));

        drawMultiPassLine(
                canvas,
                secondaryLine,
                centerX,
                subY,
                subTextSize,
                subAlpha,
                overdriveRatio,
                passes,
                Color.rgb(224, 242, 254),
                Color.rgb(192, 132, 252),
                false
        );
    }

    private void drawMultiPassLine(
            Canvas canvas,
            String text,
            float x,
            float y,
            float textSize,
            int alpha,
            float overdrive,
            int passes,
            int coreColor,
            int auraColor,
            boolean isPrimary
    ) {
        Paint auraP = isPrimary ? outerAuraPaint : subAuraPaint;
        Paint darkP = isPrimary ? darkStrokePaint : subDarkStrokePaint;
        Paint rimP = isPrimary ? innerRimPaint : subInnerRimPaint;
        Paint fillP = isPrimary ? fillPaint : subFillPaint;

        auraP.setTextSize(textSize);
        darkP.setTextSize(textSize);
        rimP.setTextSize(textSize);
        fillP.setTextSize(textSize);
        if (isPrimary) {
            midGlowPaint.setTextSize(textSize);
        }

        float stepShiftPx = dp(LabState.doubleOffsetDp) * 0.35f;

        // Tahap 1: Tumpuk Outer Ambient Halo & Mid Neon Bloom (1..10 pass dalam 1 Canvas)
        for (int p = 0; p < passes; p++) {
            float px = x + (p * stepShiftPx);
            float py = y + (p * stepShiftPx);
            float layerRatio = passes > 1 ? (float) p / (passes - 1) : 0f;

            float wideW = dp(isPrimary ? 8.5f : 6.8f) + overdrive * dp(9.0f) + layerRatio * dp(4.5f);
            auraP.setStrokeWidth(wideW);
            int wideAlpha = Math.min(255, Math.round(alpha * (0.14f + 0.24f * overdrive)));
            auraP.setColor(Color.argb(wideAlpha, Color.red(auraColor), Color.green(auraColor), Color.blue(auraColor)));
            canvas.drawText(text, px, py, auraP);

            if (isPrimary) {
                midGlowPaint.setStrokeWidth(dp(4.8f) + overdrive * dp(4.5f) + layerRatio * dp(2.2f));
                int midAlpha = Math.min(255, Math.round(alpha * (0.22f + 0.34f * overdrive)));
                int r = Math.min(255, Color.red(auraColor) + Math.round(140 * overdrive));
                int g = Math.min(255, Color.green(auraColor) + Math.round(60 * overdrive));
                midGlowPaint.setColor(Color.argb(midAlpha, r, g, 255));
                canvas.drawText(text, px, py, midGlowPaint);
            }
        }

        // Tahap 2: Tumpuk Outline Hitam Pekat & Inner Rim agar pinggiran anti-alias 100% solid
        for (int p = 0; p < passes; p++) {
            float px = x + (p * stepShiftPx);
            float py = y + (p * stepShiftPx);

            darkP.setStrokeWidth(dp(isPrimary ? 3.6f : 2.9f));
            darkP.setColor(Color.argb(alpha, 2, 6, 23));
            canvas.drawText(text, px, py, darkP);

            if (passes > 1 || overdrive > 0.02f) {
                float rimBoost = Math.min(1f, ((passes - 1) / 5f) + overdrive);
                rimP.setStrokeWidth(dp(1.1f) + rimBoost * dp(1.4f));
                int rimAlpha = Math.min(255, Math.round(alpha * (0.25f + 0.55f * rimBoost)));
                rimP.setColor(Color.argb(rimAlpha, 224, 242, 254));
                canvas.drawText(text, px, py, rimP);
            }

            fillP.setColor(Color.argb(alpha, Color.red(coreColor), Color.green(coreColor), Color.blue(coreColor)));
            canvas.drawText(text, px, py, fillP);
        }

        // Tahap 3: Puncak Putih Terang (Overdrive 101% - 200%) di lapisan paling atas
        if (overdrive > 0.01f) {
            float topX = x + ((passes - 1) * stepShiftPx);
            float topY = y + ((passes - 1) * stepShiftPx);
            int boost1 = Math.min(255, Math.round(alpha * overdrive));
            fillP.setColor(Color.argb(boost1, 255, 255, 255));
            canvas.drawText(text, topX, topY, fillP);

            if (overdrive > 0.50f) {
                int boost2 = Math.min(255, Math.round(alpha * (overdrive - 0.50f) * 2f));
                fillP.setColor(Color.argb(boost2, 255, 255, 255));
                canvas.drawText(text, topX, topY, fillP);
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (isCloneLayer || LabState.lockClickThrough) {
            return false;
        }
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                initialWindowX = LabState.posX;
                initialWindowY = LabState.posY;
                initialTouchX = event.getRawX();
                initialTouchY = event.getRawY();
                return true;

            case MotionEvent.ACTION_MOVE:
                LabState.posX = initialWindowX + Math.round(event.getRawX() - initialTouchX);
                LabState.posY = initialWindowY + Math.round(event.getRawY() - initialTouchY);
                if (dragCallback != null) {
                    dragCallback.onOverlayDragged();
                }
                return true;

            default:
                return super.onTouchEvent(event);
        }
    }
}
