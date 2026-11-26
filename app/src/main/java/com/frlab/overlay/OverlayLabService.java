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
import android.util.TypedValue;
import android.view.Gravity;
import android.view.WindowManager;

import java.util.ArrayList;
import java.util.List;

public class OverlayLabService extends Service implements LabState.Listener, LyricsCanvasView.DragCallback {

    private static final String CHANNEL_ID = "overlay_lab_channel";
    private static final int NOTIF_ID = 1601;

    private WindowManager windowManager;
    private final List<LyricsCanvasView> layerViews = new ArrayList<>();
    private final List<WindowManager.LayoutParams> layerParams = new ArrayList<>();

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
        removeAllLayers();
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

    private void syncOverlay() {
        boolean shouldShowStandardWindow = LabState.overlayEnabled
                && (LabState.activeMethod == LabState.METHOD_3_DUAL_STROKE
                || LabState.activeMethod == LabState.METHOD_4_TOUCH_REGION);

        if (!shouldShowStandardWindow || !Settings.canDrawOverlays(this)) {
            removeAllLayers();
            return;
        }

        int targetLayers = Math.min(10, Math.max(1, LabState.stackLayers));
        float winAlpha = computeWindowAlpha();
        LabState.lastAppliedWindowAlpha = winAlpha;
        int offsetPx = dp(LabState.doubleOffsetDp);

        // Hapus kelebihan layer jika slider diturunkan
        while (layerViews.size() > targetLayers) {
            int lastIdx = layerViews.size() - 1;
            LyricsCanvasView view = layerViews.remove(lastIdx);
            layerParams.remove(lastIdx);
            try {
                windowManager.removeView(view);
            } catch (Exception ignored) {
            }
        }

        // Tambah layer baru jika slider dinaikkan (1 sampai 10)
        while (layerViews.size() < targetLayers) {
            int idx = layerViews.size();
            boolean isClone = idx > 0;
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
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

        // Update seluruh layer aktif secara serentak
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

    private float computeWindowAlpha() {
        float requested = Math.min(100, Math.max(0, LabState.opacityPercent)) / 100f;
        if (LabState.lockClickThrough
                && LabState.activeMethod == LabState.METHOD_3_DUAL_STROKE
                && LabState.capAlpha80InMethod3) {
            return Math.min(0.80f, requested);
        }
        return requested;
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
        if (LabState.activeMethod != LabState.METHOD_1_ACCESSIBILITY) {
            LabState.activeWindowCount = 0;
        }
    }

    private int dp(int val) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, val, getResources().getDisplayMetrics()
        ));
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
                .setContentText("Menguji 1-10 lapis floating overlay di Android 16")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setOngoing(true)
                .build();
    }
}
