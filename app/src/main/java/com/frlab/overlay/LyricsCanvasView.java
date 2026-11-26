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
            {"Tembus sentuhan tanpa meredup cahaya", "Click-through active without losing glow"},
            {"Double floating menumpuk dua lapisan", "Stacked dual overlay windows in sync"},
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
        Typeface medType = Typeface.create(Typeface.DEFAULT, Typeface.BOLD);

        setupStrokePaint(outerAuraPaint, boldType);
        setupStrokePaint(midGlowPaint, boldType);
        setupStrokePaint(darkStrokePaint, boldType);
        setupStrokePaint(innerRimPaint, boldType);
        setupFillPaint(fillPaint, boldType);

        setupStrokePaint(subAuraPaint, medType);
        setupStrokePaint(subDarkStrokePaint, medType);
        setupStrokePaint(subInnerRimPaint, medType);
        setupFillPaint(subFillPaint, medType);

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
        int h = (int) dp(130f);
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

        // Sinkronisasi frame antara Window 1 dan Window 2 menggunakan epoch global
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
            String layerTag = LabState.doubleOverlay ? "2xWIN" : "1xWIN";
            String modeTag = "M" + LabState.activeMethod
                    + " | " + layerTag
                    + " | " + (LabState.lockClickThrough ? "LOCK" : "DRAG")
                    + " | Alpha=" + String.format("%.2f", LabState.lastAppliedWindowAlpha)
                    + " | " + opacityPct + "%";
            pillTextPaint.setTextSize(dp(10f));
            int pillTextAlpha = Math.min(255, Math.round(coreAlpha * (0.85f + 0.15f * overdriveRatio)));
            pillTextPaint.setColor(Color.argb(pillTextAlpha, 255, 255, 255));

            float textW = pillTextPaint.measureText(modeTag);
            float padH = dp(10f);
            float pillH = dp(19f);
            pillRect.set(centerX - textW / 2f - padH, dp(5f), centerX + textW / 2f + padH, dp(5f) + pillH);

            int bgAlpha = Math.min(240, Math.round((150f + 70f * overdriveRatio) * baseAlphaRatio));
            pillBgPaint.setColor(Color.argb(bgAlpha, 12, 22, 42));
            canvas.drawRoundRect(pillRect, pillH / 2f, pillH / 2f, pillBgPaint);

            // Border cahaya pada badge diagnostik
            pillBorderPaint.setStrokeWidth(dp(1.2f + overdriveRatio * 1.4f));
            int borderAlpha = Math.min(255, Math.round(coreAlpha * (0.45f + 0.55f * overdriveRatio)));
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

        drawSuperBrightLine(
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

        float gapPx = dp(10f);
        float subTextSize = dp(14.5f);
        float subY = cursorY + mainTextSize + gapPx + (1f - followEase) * dp(10f);
        int subAlpha = Math.round(coreAlpha * 0.95f * (0.25f + 0.75f * followEase));

        drawSuperBrightLine(
                canvas,
                secondaryLine,
                centerX,
                subY,
                subTextSize,
                subAlpha,
                overdriveRatio,
                Color.rgb(224, 242, 254),
                Color.rgb(192, 132, 252),
                false
        );
    }

    private void drawSuperBrightLine(
            Canvas canvas,
            String text,
            float x,
            float y,
            float textSize,
            int alpha,
            float overdrive,
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

        // 1. Wide Outer Ambient Light Halo (cahaya luas di sekeliling luar teks)
        float wideW = dp(isPrimary ? 9.5f : 7.5f) + overdrive * dp(11.0f);
        auraP.setStrokeWidth(wideW);
        int wideAlpha = Math.min(255, Math.round(alpha * (0.22f + 0.45f * overdrive)));
        auraP.setColor(Color.argb(wideAlpha, Color.red(auraColor), Color.green(auraColor), Color.blue(auraColor)));
        canvas.drawText(text, x, y, auraP);

        // 2. Intense Mid Neon Bloom (cahaya terang di dekat tepi huruf)
        if (isPrimary) {
            midGlowPaint.setTextSize(textSize);
            midGlowPaint.setStrokeWidth(dp(5.5f) + overdrive * dp(6.0f));
            int midAlpha = Math.min(255, Math.round(alpha * (0.35f + 0.55f * overdrive)));
            int r = Math.min(255, Color.red(auraColor) + Math.round(160 * overdrive));
            int g = Math.min(255, Color.green(auraColor) + Math.round(66 * overdrive));
            int b = 255;
            midGlowPaint.setColor(Color.argb(midAlpha, r, g, b));
            canvas.drawText(text, x, y, midGlowPaint);
        }

        // 3. Crisp Dark Contrast Outline (menjaga keterbacaan di latar putih)
        darkP.setStrokeWidth(dp(isPrimary ? 3.5f : 2.8f));
        darkP.setColor(Color.argb(alpha, 2, 6, 23));
        canvas.drawText(text, x, y, darkP);

        // 4. Bright Glowing Inner Rim (membuat luaran/pinggiran huruf menyala terang)
        if (overdrive > 0.02f) {
            rimP.setStrokeWidth(dp(1.2f) + overdrive * dp(1.8f));
            int rimAlpha = Math.min(255, Math.round(alpha * (0.35f + 0.65f * overdrive)));
            rimP.setColor(Color.argb(rimAlpha, 224, 242, 254));
            canvas.drawText(text, x, y, rimP);
        }

        // 5. Core Text Fill
        fillP.setColor(Color.argb(alpha, Color.red(coreColor), Color.green(coreColor), Color.blue(coreColor)));
        canvas.drawText(text, x, y, fillP);

        // 6. Overdrive White-Hot Luminance Passes (101% - 200%)
        if (overdrive > 0.01f) {
            int boost1 = Math.min(255, Math.round(alpha * overdrive));
            fillP.setColor(Color.argb(boost1, 255, 255, 255));
            canvas.drawText(text, x, y, fillP);

            if (overdrive > 0.50f) {
                int boost2 = Math.min(255, Math.round(alpha * (overdrive - 0.50f) * 2f));
                fillP.setColor(Color.argb(boost2, 255, 255, 255));
                canvas.drawText(text, x, y, fillP);
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
