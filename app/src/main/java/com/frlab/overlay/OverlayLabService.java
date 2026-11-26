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
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewTreeObserver;
import android.view.WindowManager;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public class OverlayLabService extends Service implements LabState.Listener, LyricsCanvasView.DragCallback {

    private static final String CHANNEL_ID = "overlay_lab_channel";
    private static final int NOTIF_ID = 1601;

    private WindowManager windowManager;

    private LyricsCanvasView primaryView;
    private WindowManager.LayoutParams primaryParams;

    private LyricsCanvasView cloneView;
    private WindowManager.LayoutParams cloneParams;

    private Object primaryInsetsProxy;
    private Object cloneInsetsProxy;

    private Class<?> cachedListenerClass;
    private Field cachedRegionField;
    private Method cachedSetInsetsMethod;
    private Method cachedAddListenerMethod;
    private Method cachedRemoveListenerMethod;

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
        removeAllOverlays();
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

    private void syncOverlay() {
        boolean shouldShowStandardWindow = LabState.overlayEnabled
                && (LabState.activeMethod == LabState.METHOD_3_DUAL_STROKE
                || LabState.activeMethod == LabState.METHOD_4_TOUCH_REGION);

        if (!shouldShowStandardWindow || !Settings.canDrawOverlays(this)) {
            removeAllOverlays();
            return;
        }

        boolean shouldEnableMethod4Hook = LabState.activeMethod == LabState.METHOD_4_TOUCH_REGION
                && LabState.lockClickThrough;

        float winAlpha = computeWindowAlpha();
        LabState.lastAppliedWindowAlpha = winAlpha;

        // 1. Primary Overlay Window
        if (primaryView == null) {
            primaryParams = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    computeFlags(false),
                    PixelFormat.TRANSLUCENT
            );
            primaryParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            primaryParams.x = LabState.posX;
            primaryParams.y = LabState.posY;
            primaryParams.alpha = winAlpha;

            primaryView = new LyricsCanvasView(this, false, this);
            primaryInsetsProxy = applyMethod4Insets(primaryView, primaryInsetsProxy, shouldEnableMethod4Hook);
            try {
                windowManager.addView(primaryView, primaryParams);
            } catch (Exception e) {
                primaryView = null;
            }
        } else {
            primaryParams.flags = computeFlags(false);
            primaryParams.x = LabState.posX;
            primaryParams.y = LabState.posY;
            primaryParams.alpha = winAlpha;
            primaryInsetsProxy = applyMethod4Insets(primaryView, primaryInsetsProxy, shouldEnableMethod4Hook);
            try {
                windowManager.updateViewLayout(primaryView, primaryParams);
            } catch (Exception ignored) {
            }
            primaryView.invalidate();
        }

        // 2. Clone Overlay Window (Double Floating Overlay yang menimpa di atasnya)
        if (LabState.doubleOverlay && primaryView != null) {
            int offsetPx = dp(LabState.doubleOffsetDp);
            if (cloneView == null) {
                cloneParams = new WindowManager.LayoutParams(
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                        computeFlags(true),
                        PixelFormat.TRANSLUCENT
                );
                cloneParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
                cloneParams.x = LabState.posX + offsetPx;
                cloneParams.y = LabState.posY + offsetPx;
                cloneParams.alpha = winAlpha;

                cloneView = new LyricsCanvasView(this, true, null);
                cloneInsetsProxy = applyMethod4Insets(cloneView, cloneInsetsProxy, shouldEnableMethod4Hook);
                try {
                    windowManager.addView(cloneView, cloneParams);
                } catch (Exception e) {
                    cloneView = null;
                }
            } else {
                cloneParams.flags = computeFlags(true);
                cloneParams.x = LabState.posX + offsetPx;
                cloneParams.y = LabState.posY + offsetPx;
                cloneParams.alpha = winAlpha;
                cloneInsetsProxy = applyMethod4Insets(cloneView, cloneInsetsProxy, shouldEnableMethod4Hook);
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

    private int computeFlags(boolean isClone) {
        int baseFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;

        // Layer duplikat selalu tembus sentuh saat mode drag agar sentuhan geser langsung ke layer utama
        if (isClone && !LabState.lockClickThrough) {
            if (LabState.activeMethod == LabState.METHOD_3_DUAL_STROKE) {
                return baseFlags | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
            }
            return baseFlags;
        }

        if (!LabState.lockClickThrough) {
            return baseFlags;
        }

        if (LabState.activeMethod == LabState.METHOD_3_DUAL_STROKE) {
            return baseFlags | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        }

        // Metode 4 tidak memakai FLAG_NOT_TOUCHABLE agar alpha 1.0f tetap diizinkan
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

    private boolean ensureMethod4ReflectionCached() {
        if (cachedListenerClass != null) {
            return true;
        }
        try {
            cachedListenerClass = Class.forName("android.view.ViewTreeObserver$OnComputeInternalInsetsListener");
            Class<?> infoClass = Class.forName("android.view.ViewTreeObserver$InternalInsetsInfo");
            cachedRegionField = infoClass.getField("touchableRegion");
            cachedSetInsetsMethod = infoClass.getMethod("setTouchableInsets", int.class);
            cachedAddListenerMethod = ViewTreeObserver.class.getMethod("addOnComputeInternalInsetsListener", cachedListenerClass);
            cachedRemoveListenerMethod = ViewTreeObserver.class.getMethod("removeOnComputeInternalInsetsListener", cachedListenerClass);
            return true;
        } catch (Throwable t) {
            LabState.method4Diagnostic = "Reflection diblokir OS: " + t.getClass().getSimpleName();
            return false;
        }
    }

    private Object applyMethod4Insets(LyricsCanvasView targetView, Object currentProxy, boolean enable) {
        if (targetView == null) {
            return null;
        }
        if (!ensureMethod4ReflectionCached()) {
            return null;
        }
        ViewTreeObserver vto = targetView.getViewTreeObserver();
        try {
            if (!enable) {
                if (currentProxy != null) {
                    cachedRemoveListenerMethod.invoke(vto, currentProxy);
                }
                if (LabState.activeMethod == LabState.METHOD_4_TOUCH_REGION) {
                    LabState.method4Diagnostic = "Mode Drag aktif (insets normal)";
                }
                return null;
            }

            if (currentProxy == null) {
                final Field regionField = cachedRegionField;
                final Method setInsets = cachedSetInsetsMethod;
                Object newProxy = Proxy.newProxyInstance(
                        cachedListenerClass.getClassLoader(),
                        new Class<?>[]{cachedListenerClass},
                        new InvocationHandler() {
                            @Override
                            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                                String name = method.getName();
                                if ("onComputeInternalInsets".equals(name) && args != null && args.length > 0) {
                                    Object info = args[0];
                                    Region region = (Region) regionField.get(info);
                                    if (region != null) {
                                        region.setEmpty();
                                    }
                                    setInsets.invoke(info, 3);
                                    return null;
                                }
                                if ("equals".equals(name)) {
                                    return args != null && args.length > 0 && proxy == args[0];
                                }
                                if ("hashCode".equals(name)) {
                                    return System.identityHashCode(proxy);
                                }
                                if ("toString".equals(name)) {
                                    return "Method4InsetsProxy";
                                }
                                return null;
                            }
                        }
                );
                cachedAddListenerMethod.invoke(vto, newProxy);
                LabState.method4Diagnostic = "Hook aktif (Region kosong, Alpha 1.0)";
                return newProxy;
            }
            return currentProxy;
        } catch (Throwable t) {
            LabState.method4Diagnostic = "Gagal hook: " + t.getClass().getSimpleName();
            return null;
        }
    }

    private void removeCloneOverlay() {
        if (cloneView != null) {
            cloneInsetsProxy = applyMethod4Insets(cloneView, cloneInsetsProxy, false);
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
            primaryInsetsProxy = applyMethod4Insets(primaryView, primaryInsetsProxy, false);
            try {
                windowManager.removeView(primaryView);
            } catch (Exception ignored) {
            }
            primaryView = null;
            primaryParams = null;
        }
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
                .setContentText("Menguji double floating overlay di Android 16")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setOngoing(true)
                .build();
    }
}
