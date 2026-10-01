package org.zonarstudio.spraute_engine.client;

/** Client-side billboard visibility (presentation reset / cinematic hide). */
public final class BillboardClientState {
    private static boolean suppressRendering;

    private BillboardClientState() {}

    public static void suppressRendering() {
        suppressRendering = true;
    }

    public static void clearSuppressRendering() {
        suppressRendering = false;
    }

    public static boolean shouldRender() {
        return !suppressRendering;
    }
}
