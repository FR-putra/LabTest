package com.frlab.overlay;

import android.accessibilityservice.AccessibilityService;
import android.graphics.PixelFormat;
import android.view.Gravity;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;

public class LabAccessibilityService extends AccessibilityService implements LabState.Listener {

    private WindowManager windowManager;
    private LyricsCanvasView overlayView;
    private WindowManager.LayoutParams params;

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
        removeOverlay();
        LabState.notifyChanged();
        super.onDestroy();
    }

    @Override
    public void onStateChanged() {
        syncAccessibilityOverlay();
    }

    private void syncAccessibilityOverlay() {
        boolean shouldShow = LabState.overlayEnabled
                && LabState.activeMethod == LabState.METHOD_1_ACCESSIBILITY;

        if (!shouldShow) {
            removeOverlay();
            return;
        }

        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
        if (LabState.lockClickThrough) {
            flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        }

        // TYPE_ACCESSIBILITY_OVERLAY dipercaya sistem: alpha 1.0f tetap tembus klik tanpa diredupkan
        float winAlpha = Math.min(100, Math.max(0, LabState.opacityPercent)) / 100f;
        LabState.lastAppliedWindowAlpha = winAlpha;

        if (overlayView == null) {
            params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    flags,
                    PixelFormat.TRANSLUCENT
            );
            params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            params.x = LabState.posX;
            params.y = LabState.posY;
            params.alpha = winAlpha;

            overlayView = new LyricsCanvasView(this, windowManager, params);
            try {
                windowManager.addView(overlayView, params);
            } catch (Exception e) {
                overlayView = null;
            }
        } else {
            params.flags = flags;
            params.alpha = winAlpha;
            try {
                windowManager.updateViewLayout(overlayView, params);
            } catch (Exception ignored) {
            }
            overlayView.invalidate();
        }
    }

    private void removeOverlay() {
        if (overlayView != null) {
            try {
                windowManager.removeView(overlayView);
            } catch (Exception ignored) {
            }
            overlayView = null;
        }
    }
}
