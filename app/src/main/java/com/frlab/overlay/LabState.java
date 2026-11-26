package com.frlab.overlay;

import java.util.concurrent.CopyOnWriteArrayList;

public class LabState {

    public interface Listener {
        void onStateChanged();
    }

    public static final int METHOD_1_ACCESSIBILITY = 1;
    public static final int METHOD_3_DUAL_STROKE = 3;
    public static final int METHOD_4_TOUCH_REGION = 4;

    public static volatile boolean overlayEnabled = false;
    public static volatile int activeMethod = METHOD_3_DUAL_STROKE;
    public static volatile boolean lockClickThrough = false;
    public static volatile int opacityPercent = 100;
    public static volatile boolean capAlpha80InMethod3 = true;

    // Jumlah tumpukan window overlay (1 sampai 10 lapis)
    public static volatile int stackLayers = 1;
    public static volatile int doubleOffsetDp = 0;
    public static volatile boolean showDiagnosticPill = true;

    public static volatile int posX = 0;
    public static volatile int posY = 220;
    public static volatile long syncEpochMs = System.currentTimeMillis();

    public static volatile boolean accessibilityConnected = false;
    public static volatile float lastAppliedWindowAlpha = 1.0f;
    public static volatile int activeWindowCount = 0;
    public static volatile String method4Diagnostic = "Dinonaktifkan (Diblokir Android 16 saat Lock)";

    private static final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();

    public static void addListener(Listener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public static void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    public static void notifyChanged() {
        for (Listener listener : listeners) {
            listener.onStateChanged();
        }
    }
}
