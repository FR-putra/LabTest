package com.frlab.overlay;

import android.accessibilityservice.AccessibilityService;
import android.graphics.PixelFormat;
import android.view.Gravity;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;

public class LabAccessibilityService extends AccessibilityService implements LabState.Listener, LyricsCanvasView.DragCallback {

    private WindowManager windowManager;
    private LyricsCanvasView overlayView;
    private WindowManager.LayoutParams overlayParams;

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

    @Override
    public void onOverlayDragged() {
        if (overlayView != null && overlayParams != null) {
            overlayParams.x = LabState.posX;
            overlayParams.y = LabState.posY;
            try {
                windowManager.updateViewLayout(overlayView, overlayParams);
            } catch (Exception ignored) {
            }
        }
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

        float winAlpha = Math.min(100, Math.max(0, LabState.opacityPercent)) / 100f;
        LabState.lastAppliedWindowAlpha = winAlpha;

        if (overlayView == null) {
            overlayParams = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    flags,
                    PixelFormat.TRANSLUCENT
            );
            overlayParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            overlayParams.x = LabState.posX;
            overlayParams.y = LabState.posY;
            overlayParams.alpha = winAlpha;

            overlayView = new LyricsCanvasView(this, false, this);
            try {
                windowManager.addView(overlayView, overlayParams);
            } catch (Exception e) {
                overlayView = null;
            }
        } else {
            overlayParams.flags = flags;
            overlayParams.x = LabState.posX;
            overlayParams.y = LabState.posY;
            overlayParams.alpha = winAlpha;
            try {
                windowManager.updateViewLayout(overlayView, overlayParams);
            } catch (Exception ignored) {
            }
            overlayView.invalidate();
        }

        LabState.activeWindowCount = overlayView != null ? 1 : 0;
    }

    private void removeOverlay() {
        if (overlayView != null) {
            try {
                windowManager.removeView(overlayView);
            } catch (Exception ignored) {
            }
            overlayView = null;
            overlayParams = null;
        }
        if (LabState.activeMethod == LabState.METHOD_1_ACCESSIBILITY) {
            LabState.activeWindowCount = 0;
        }
    }
}
