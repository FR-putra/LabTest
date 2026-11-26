package com.frlab.overlay;

import android.accessibilityservice.AccessibilityService;
import android.graphics.PixelFormat;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;

public class LabAccessibilityService extends AccessibilityService implements LabState.Listener, LyricsCanvasView.DragCallback {

    private WindowManager windowManager;

    private LyricsCanvasView primaryView;
    private WindowManager.LayoutParams primaryParams;

    private LyricsCanvasView cloneView;
    private WindowManager.LayoutParams cloneParams;

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
        removeAllOverlays();
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
        if (primaryView != null && primaryParams != null) {
            primaryParams.x = LabState.posX;
            primaryParams.y = LabState.posY;
            try {
                windowManager.updateViewLayout(primaryView, primaryParams);
            } catch (Exception ignored) {
            }
        }
        if (cloneView != null && cloneParams != null) {
            cloneParams.x = LabState.posX + offsetPx;
            cloneParams.y = LabState.posY + offsetPx;
            try {
                windowManager.updateViewLayout(cloneView, cloneParams);
            } catch (Exception ignored) {
            }
        }
    }

    private void syncAccessibilityOverlay() {
        boolean shouldShow = LabState.overlayEnabled
                && LabState.activeMethod == LabState.METHOD_1_ACCESSIBILITY;

        if (!shouldShow) {
            removeAllOverlays();
            return;
        }

        int primaryFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
        if (LabState.lockClickThrough) {
            primaryFlags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        }

        int cloneFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;

        float winAlpha = Math.min(100, Math.max(0, LabState.opacityPercent)) / 100f;
        LabState.lastAppliedWindowAlpha = winAlpha;

        if (primaryView == null) {
            primaryParams = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    primaryFlags,
                    PixelFormat.TRANSLUCENT
            );
            primaryParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            primaryParams.x = LabState.posX;
            primaryParams.y = LabState.posY;
            primaryParams.alpha = winAlpha;

            primaryView = new LyricsCanvasView(this, false, this);
            try {
                windowManager.addView(primaryView, primaryParams);
            } catch (Exception e) {
                primaryView = null;
            }
        } else {
            primaryParams.flags = primaryFlags;
            primaryParams.x = LabState.posX;
            primaryParams.y = LabState.posY;
            primaryParams.alpha = winAlpha;
            try {
                windowManager.updateViewLayout(primaryView, primaryParams);
            } catch (Exception ignored) {
            }
            primaryView.invalidate();
        }

        if (LabState.doubleOverlay && primaryView != null) {
            int offsetPx = dp(LabState.doubleOffsetDp);
            if (cloneView == null) {
                cloneParams = new WindowManager.LayoutParams(
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                        cloneFlags,
                        PixelFormat.TRANSLUCENT
                );
                cloneParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
                cloneParams.x = LabState.posX + offsetPx;
                cloneParams.y = LabState.posY + offsetPx;
                cloneParams.alpha = winAlpha;

                cloneView = new LyricsCanvasView(this, true, null);
                try {
                    windowManager.addView(cloneView, cloneParams);
                } catch (Exception e) {
                    cloneView = null;
                }
            } else {
                cloneParams.flags = cloneFlags;
                cloneParams.x = LabState.posX + offsetPx;
                cloneParams.y = LabState.posY + offsetPx;
                cloneParams.alpha = winAlpha;
                try {
                    windowManager.updateViewLayout(cloneView, cloneParams);
                } catch (Exception ignored) {
                }
                cloneView.invalidate();
            }
        } else {
            removeCloneOverlay();
        }

        int count = (primaryView != null ? 1 : 0) + (cloneView != null ? 1 : 0);
        LabState.activeWindowCount = count;
    }

    private void removeCloneOverlay() {
        if (cloneView != null) {
            try {
                windowManager.removeView(cloneView);
            } catch (Exception ignored) {
            }
            cloneView = null;
            cloneParams = null;
        }
    }

    private void removeAllOverlays() {
        removeCloneOverlay();
        if (primaryView != null) {
            try {
                windowManager.removeView(primaryView);
            } catch (Exception ignored) {
            }
            primaryView = null;
            primaryParams = null;
        }
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
