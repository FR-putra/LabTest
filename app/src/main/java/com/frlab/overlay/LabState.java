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
    public static volatile int opacityPercent = 200;
    public static volatile boolean capAlpha80InMethod3 = true;

    // Double Floating Overlay: menumpuk 2 window overlay terpisah secara identik
    public static volatile boolean doubleOverlay = true;
    public static volatile int doubleOffsetDp = 0;
    public static volatile boolean showDiagnosticPill = true;

    public static volatile int posX = 0;
    public static volatile int posY = 220;
    public static volatile long syncEpochMs = System.currentTimeMillis();

    public static volatile boolean accessibilityConnected = false;
    public static volatile float lastAppliedWindowAlpha = 1.0f;
    public static volatile int activeWindowCount = 0;
    public static volatile String method4Diagnostic = "Belum diuji";

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
