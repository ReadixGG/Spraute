package org.zonarstudio.spraute_engine.client.cameraroute;

import org.zonarstudio.spraute_engine.Spraute_engine;
import org.zonarstudio.spraute_engine.cameraroute.CameraRouteEndMode;
import org.zonarstudio.spraute_engine.cameraroute.CameraRoutePath;
import org.zonarstudio.spraute_engine.client.CameraHandler;
import org.zonarstudio.spraute_engine.network.CameraPacket;

import java.util.List;

/**
 * Client-side camera route playback.
 * Delegates all interpolation to {@link CameraHandler#startRoute} which runs
 * frame-by-frame inside {@code ViewportEvent.ComputeCameraAngles} — no smoothstep,
 * no per-tick jumps, perfectly smooth regardless of TPS or frame-rate.
 */
public final class CameraRoutePlayback {

    private CameraRoutePlayback() {}

    public static boolean isPlaying() {
        return CameraHandler.isRouteActive();
    }

    // ---- Convenience overloads ----

    public static void start(List<CameraRoutePath.Sample> samples, float totalDuration, float returnSmooth,
                             boolean lockMovement, byte hideGui) {
        start(samples, null, totalDuration, returnSmooth, lockMovement, hideGui,
                false, 0, 0, 0, CameraRouteEndMode.RETURN_TO_PLAYER);
    }

    public static void start(List<CameraRoutePath.Sample> samples, float totalDuration, float returnSmooth,
                             boolean lockMovement, byte hideGui,
                             boolean lookOverride, double lx, double ly, double lz) {
        start(samples, null, totalDuration, returnSmooth, lockMovement, hideGui,
                lookOverride, lx, ly, lz, CameraRouteEndMode.RETURN_TO_PLAYER);
    }

    public static void start(List<CameraRoutePath.Sample> samples, float totalDuration, float returnSmooth,
                             boolean lockMovement, byte hideGui,
                             boolean lookOverride, double lx, double ly, double lz,
                             CameraRouteEndMode endMode) {
        start(samples, null, totalDuration, returnSmooth, lockMovement, hideGui,
                lookOverride, lx, ly, lz, endMode);
    }

    /**
     * Full start method — accepts an optional pre-computed {@code timeline} and transition time.
     * If {@code timeline} is {@code null}, uniform sample distribution is used.
     * {@code transitionSec > 0} blends from the current camera position to route[0].
     */
    public static void start(List<CameraRoutePath.Sample> samples, float[] timeline,
                             float totalDuration, float returnSmooth,
                             boolean lockMovement, byte hideGui,
                             boolean lookOverride, double lx, double ly, double lz,
                             CameraRouteEndMode endMode, float transitionSec) {
        if (samples == null || samples.size() < 2) return;
        CameraHandler.startRoute(samples, timeline, totalDuration, returnSmooth,
                lockMovement, hideGui, lookOverride, lx, ly, lz, endMode, transitionSec);
    }

    /** Backward-compat overload — no transition. */
    public static void start(List<CameraRoutePath.Sample> samples, float[] timeline,
                             float totalDuration, float returnSmooth,
                             boolean lockMovement, byte hideGui,
                             boolean lookOverride, double lx, double ly, double lz,
                             CameraRouteEndMode endMode) {
        start(samples, timeline, totalDuration, returnSmooth, lockMovement, hideGui,
                lookOverride, lx, ly, lz, endMode, 0f);
    }

    public static void stop() {
        CameraHandler.resetCamera();
    }
}
