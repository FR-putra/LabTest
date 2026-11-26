package com.frlab.overlay;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.graphics.Region;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.ViewTreeObserver;
import android.view.WindowManager;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public class OverlayLabService extends Service implements LabState.Listener {

    private static final String CHANNEL_ID = "overlay_lab_channel";
    private static final int NOTIF_ID = 1601;

    private WindowManager windowManager;
    private LyricsCanvasView overlayView;
    private WindowManager.LayoutParams params;
    private Object internalInsetsProxy;

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

    private void syncOverlay() {
        boolean shouldShowStandardWindow = LabState.overlayEnabled
                && (LabState.activeMethod == LabState.METHOD_3_DUAL_STROKE
                || LabState.activeMethod == LabState.METHOD_4_TOUCH_REGION);

        if (!shouldShowStandardWindow || !Settings.canDrawOverlays(this)) {
            removeOverlay();
            return;
        }

        boolean shouldEnableMethod4Hook = LabState.activeMethod == LabState.METHOD_4_TOUCH_REGION
                && LabState.lockClickThrough;

        if (overlayView == null) {
            params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    computeFlags(),
                    PixelFormat.TRANSLUCENT
            );
            params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            params.x = LabState.posX;
            params.y = LabState.posY;
            params.alpha = computeWindowAlpha();
            LabState.lastAppliedWindowAlpha = params.alpha;

            overlayView = new LyricsCanvasView(this, windowManager, params);
            applyMethod4InsetsIfNeeded(shouldEnableMethod4Hook);
            try {
                windowManager.addView(overlayView, params);
            } catch (Exception e) {
                overlayView = null;
            }
        } else {
            params.flags = computeFlags();
            params.alpha = computeWindowAlpha();
            LabState.lastAppliedWindowAlpha = params.alpha;
            applyMethod4InsetsIfNeeded(shouldEnableMethod4Hook);
            try {
                windowManager.updateViewLayout(overlayView, params);
            } catch (Exception ignored) {
            }
            overlayView.invalidate();
        }
    }

    private int computeFlags() {
        int baseFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;

        if (!LabState.lockClickThrough) {
            return baseFlags;
        }

        if (LabState.activeMethod == LabState.METHOD_3_DUAL_STROKE) {
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

    private void applyMethod4InsetsIfNeeded(boolean enable) {
        if (overlayView == null) {
            return;
        }
        ViewTreeObserver vto = overlayView.getViewTreeObserver();
        try {
            Class<?> listenerClass = Class.forName("android.view.ViewTreeObserver$OnComputeInternalInsetsListener");
            if (!enable) {
                if (internalInsetsProxy != null) {
                    Method removeMethod = ViewTreeObserver.class.getMethod("removeOnComputeInternalInsetsListener", listenerClass);
                    removeMethod.invoke(vto, internalInsetsProxy);
                    internalInsetsProxy = null;
                }
                if (LabState.activeMethod == LabState.METHOD_4_TOUCH_REGION) {
                    LabState.method4Diagnostic = "Mode Drag aktif (insets normal)";
                }
                return;
            }

            if (internalInsetsProxy == null) {
                internalInsetsProxy = Proxy.newProxyInstance(
                        listenerClass.getClassLoader(),
                        new Class<?>[]{listenerClass},
                        new InvocationHandler() {
                            @Override
                            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                                if ("onComputeInternalInsets".equals(method.getName()) && args != null && args.length > 0) {
                                    Object info = args[0];
                                    Field regionField = info.getClass().getField("touchableRegion");
                                    Region region = (Region) regionField.get(info);
                                    if (region != null) {
                                        region.setEmpty();
                                    }
                                    Method setInsets = info.getClass().getMethod("setTouchableInsets", int.class);
                                    setInsets.invoke(info, 3);
                                }
                                return null;
                            }
                        }
                );
                Method addMethod = ViewTreeObserver.class.getMethod("addOnComputeInternalInsetsListener", listenerClass);
                addMethod.invoke(vto, internalInsetsProxy);
            }
            LabState.method4Diagnostic = "Hook reflection aktif (Region kosong, Alpha 1.0)";
        } catch (Throwable t) {
            LabState.method4Diagnostic = "Diblokir Android 16: " + t.getClass().getSimpleName();
        }
    }

    private void removeOverlay() {
        if (overlayView != null) {
            applyMethod4InsetsIfNeeded(false);
            try {
                windowManager.removeView(overlayView);
            } catch (Exception ignored) {
            }
            overlayView = null;
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
                .setContentText("Menguji floating overlay di Android 16")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setOngoing(true)
                .build();
    }
}
