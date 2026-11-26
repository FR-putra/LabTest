package com.frlab.overlay;

import android.accessibilityservice.AccessibilityService;
import android.graphics.PixelFormat;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;

import java.util.ArrayList;
import java.util.List;

public class LabAccessibilityService extends AccessibilityService implements LabState.Listener, LyricsCanvasView.DragCallback {

    private WindowManager windowManager;
    private final List<LyricsCanvasView> layerViews = new ArrayList<>();
    private final List<WindowManager.LayoutParams> layerParams = new ArrayList<>();

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        LabState.accessibilityConnected = true;
        LabState.addListener(this);
        LabState.notifyChanged();
        syncAccessibilityOverlay();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public void onDestroy() {
        LabState.accessibilityConnected = false;
        LabState.removeListener(this);
        removeAllLayers();
        LabState.notifyChanged();
        super.onDestroy();
    }

    @Override
    public void onStateChanged() {
        syncAccessibilityOverlay();
    }

    @Override
    public void onOverlayDragged() {
        int offsetPx = dp(LabState.doubleOffsetDp);
        for (int i = 0; i < layerViews.size(); i++) {
            LyricsCanvasView view = layerViews.get(i);
            WindowManager.LayoutParams lp = layerParams.get(i);
            lp.x = LabState.posX + (i * offsetPx);
            lp.y = LabState.posY + (i * offsetPx);
            try {
                windowManager.updateViewLayout(view, lp);
            } catch (Exception ignored) {
            }
        }
    }

    private void syncAccessibilityOverlay() {
        boolean shouldShow = LabState.overlayEnabled
                && LabState.activeMethod == LabState.METHOD_1_ACCESSIBILITY;

        if (!shouldShow) {
            removeAllLayers();
            return;
        }

        int targetLayers = Math.min(10, Math.max(1, LabState.stackLayers));
        float winAlpha = Math.min(100, Math.max(0, LabState.opacityPercent)) / 100f;
        LabState.lastAppliedWindowAlpha = winAlpha;
        int offsetPx = dp(LabState.doubleOffsetDp);

        while (layerViews.size() > targetLayers) {
            int lastIdx = layerViews.size() - 1;
            LyricsCanvasView view = layerViews.remove(lastIdx);
            layerParams.remove(lastIdx);
            try {
                windowManager.removeView(view);
            } catch (Exception ignored) {
            }
        }

        while (layerViews.size() < targetLayers) {
            int idx = layerViews.size();
            boolean isClone = idx > 0;
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    computeFlags(isClone),
                    PixelFormat.TRANSLUCENT
            );
            lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            lp.x = LabState.posX + (idx * offsetPx);
            lp.y = LabState.posY + (idx * offsetPx);
            lp.alpha = winAlpha;

            LyricsCanvasView view = new LyricsCanvasView(this, isClone, isClone ? null : this);
            try {
                windowManager.addView(view, lp);
                layerViews.add(view);
                layerParams.add(lp);
            } catch (Exception e) {
                break;
            }
        }

        for (int i = 0; i < layerViews.size(); i++) {
            boolean isClone = i > 0;
            LyricsCanvasView view = layerViews.get(i);
            WindowManager.LayoutParams lp = layerParams.get(i);
            lp.flags = computeFlags(isClone);
            lp.x = LabState.posX + (i * offsetPx);
            lp.y = LabState.posY + (i * offsetPx);
            lp.alpha = winAlpha;
            try {
                windowManager.updateViewLayout(view, lp);
            } catch (Exception ignored) {
            }
            view.invalidate();
        }

        LabState.activeWindowCount = layerViews.size();
    }

    private int computeFlags(boolean isClone) {
        int baseFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
        if (isClone || LabState.lockClickThrough) {
            return baseFlags | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        }
        return baseFlags;
    }

    private void removeAllLayers() {
        for (int i = layerViews.size() - 1; i >= 0; i--) {
            try {
                windowManager.removeView(layerViews.get(i));
            } catch (Exception ignored) {
            }
        }
        layerViews.clear();
        layerParams.clear();
        if (LabState.activeMethod == LabState.METHOD_1_ACCESSIBILITY) {
            LabState.activeWindowCount = 0;
        }
    }

    private int dp(int val) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, val, getResources().getDisplayMetrics()
        ));
    }
}
