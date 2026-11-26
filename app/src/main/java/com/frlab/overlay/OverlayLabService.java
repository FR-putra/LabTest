package com.frlab.overlay;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.WindowManager;

public class OverlayLabService extends Service implements LabState.Listener, LyricsCanvasView.DragCallback {

    private static final String CHANNEL_ID = "overlay_lab_channel";
    private static final int NOTIF_ID = 1601;

    private WindowManager windowManager;
    private LyricsCanvasView overlayView;
    private WindowManager.LayoutParams overlayParams;

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        createNotificationChannel();
        Notification notification = buildNotification();
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
            } else {
                startForeground(NOTIF_ID, notification);
            }
        } catch (Exception ignored) {
        }
        LabState.addListener(this);
        syncOverlay();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        syncOverlay();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        LabState.removeListener(this);
        removeOverlay();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onStateChanged() {
        syncOverlay();
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

    private void syncOverlay() {
        boolean shouldShowStandardWindow = LabState.overlayEnabled
                && (LabState.activeMethod == LabState.METHOD_3_DUAL_STROKE
                || LabState.activeMethod == LabState.METHOD_4_TOUCH_REGION);

        if (!shouldShowStandardWindow || !Settings.canDrawOverlays(this)) {
            removeOverlay();
            return;
        }

        float winAlpha = computeWindowAlpha();
        LabState.lastAppliedWindowAlpha = winAlpha;

        if (overlayView == null) {
            overlayParams = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    computeFlags(),
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
            overlayParams.flags = computeFlags();
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

    private int computeFlags() {
        int baseFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;

        if (LabState.lockClickThrough) {
            return baseFlags | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        }
        return baseFlags;
    }

    private float computeWindowAlpha() {
        float requested = Math.min(100, Math.max(0, LabState.opacityPercent)) / 100f;
        if (LabState.lockClickThrough
                && LabState.activeMethod == LabState.METHOD_3_DUAL_STROKE
                && LabState.capAlpha80InMethod3) {
            return Math.min(0.80f, requested);
        }
        return requested;
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
        if (LabState.activeMethod != LabState.METHOD_1_ACCESSIBILITY) {
            LabState.activeWindowCount = 0;
        }
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "OverlayLab16 Service",
                NotificationManager.IMPORTANCE_LOW
        );
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) {
            nm.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification() {
        return new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("OverlayLab16 Aktif")
                .setContentText("Single-Window Multi-Pass Overlay (1xWIN)")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setOngoing(true)
                .build();
    }
}
