package org.zonarstudio.spraute_engine.client.cameraroute;

import org.zonarstudio.spraute_engine.cameraroute.CameraRouteEndMode;
import org.zonarstudio.spraute_engine.cameraroute.CameraRoutePath;
import org.zonarstudio.spraute_engine.cameraroute.CameraRouteWaypoint;
import org.zonarstudio.spraute_engine.network.CameraPacket;

import java.util.List;

public final class CameraRoutePreview {
    private CameraRoutePreview() {}

    public static boolean isPlaying() {
        return CameraRoutePlayback.isPlaying();
    }

    public static void start() {
        List<CameraRouteWaypoint> points = CameraRouteManager.getWaypoints();
        if (points.size() < 2) return;

        float totalDuration = CameraRouteManager.getTotalDuration();
        totalDuration = CameraRoutePath.computeEffectiveTotalDuration(points, totalDuration);

        List<CameraRoutePath.Sample> samples = CameraRoutePath.buildSamples(
                points, CameraRouteManager.getFlightType(), CameraRoutePath.PLAYBACK_SUBDIVISIONS);
        float[] timeline = CameraRoutePath.buildTimeline(points, samples, totalDuration);

        CameraRoutePlayback.start(samples, timeline, totalDuration, 0.8f,
                true, CameraPacket.HIDE_MINECRAFT,
                false, 0, 0, 0, CameraRouteManager.getEndMode());
    }

    public static void stop() {
        CameraRoutePlayback.stop();
    }
}
