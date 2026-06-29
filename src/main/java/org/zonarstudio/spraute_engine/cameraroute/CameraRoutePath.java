package org.zonarstudio.spraute_engine.cameraroute;

import java.util.ArrayList;
import java.util.List;

public final class CameraRoutePath {
    public record Sample(double x, double y, double z, float yaw, float pitch) {}

    private CameraRoutePath() {}

    /** Subdivisions per segment for playback (high enough for smooth interpolation). */
    public static final int PLAYBACK_SUBDIVISIONS = 40;
    /** Subdivisions for preview polyline in world-space overlay. */
    public static final int PREVIEW_SUBDIVISIONS = 16;

    public static List<Sample> buildSamples(List<CameraRouteWaypoint> points, CameraFlightType type, int subdivisions) {
        if (points == null || points.isEmpty()) return List.of();
        if (points.size() == 1) {
            CameraRouteWaypoint p = points.get(0);
            return List.of(new Sample(p.x(), p.y(), p.z(), p.yaw(), p.pitch()));
        }
        return switch (type) {
            case SMOOTH -> sampleSmooth(points, Math.max(4, subdivisions));
            case SMOOTH_CAMERA -> sampleSmoothCamera(points, Math.max(4, subdivisions));
            default -> sampleLinear(points, Math.max(2, subdivisions));
        };
    }

    public static List<Sample> buildPreviewPolyline(List<CameraRouteWaypoint> points, CameraFlightType type) {
        return buildSamples(points, type, PREVIEW_SUBDIVISIONS);
    }

    /**
     * Build per-sample uniform timeline (seconds). Camera flies the whole route at constant speed:
     * sample {@code i} arrives at time {@code i / (n-1) * totalDuration}.
     *
     * @return float array of length {@code samples.size()}.
     */
    public static float[] buildTimeline(List<CameraRouteWaypoint> waypoints, List<Sample> samples, float totalDuration) {
        if (samples.isEmpty()) return new float[0];
        int n = samples.size();
        if (n == 1) return new float[]{0f};
        float[] timeline = new float[n];
        for (int i = 0; i < n; i++) {
            timeline[i] = totalDuration * i / (n - 1);
        }
        return timeline;
    }

    public static float estimateDuration(List<Sample> samples, float totalDuration, float returnSmooth,
                                         CameraRouteEndMode endMode) {
        if (samples == null || samples.size() < 2) return 0f;
        float fly = Math.max(0.05f, totalDuration);
        if (endMode == CameraRouteEndMode.RETURN_TO_PLAYER) {
            return fly + Math.max(0f, returnSmooth);
        }
        return fly;
    }

    /** Returns the route total duration (uniform speed — no per-waypoint overrides). */
    public static float computeEffectiveTotalDuration(List<CameraRouteWaypoint> waypoints, float totalDuration) {
        return Math.max(0.05f, totalDuration);
    }

    /** Per-segment time so the full path completes in {@code totalDuration} seconds. */
    public static float segmentDurationForTotal(List<Sample> samples, float totalDuration) {
        if (samples == null || samples.size() < 2) return 0.05f;
        return Math.max(0.05f, totalDuration / (samples.size() - 1));
    }

    private static List<Sample> sampleLinear(List<CameraRouteWaypoint> points, int subdivisions) {
        List<Sample> out = new ArrayList<>();
        for (int i = 0; i < points.size() - 1; i++) {
            CameraRouteWaypoint a = points.get(i);
            CameraRouteWaypoint b = points.get(i + 1);
            int steps = i == points.size() - 2 ? subdivisions + 1 : subdivisions;
            for (int s = 0; s < steps; s++) {
                float t = s / (float) subdivisions;
                out.add(lerpSample(a, b, t));
            }
        }
        return out;
    }

    private static List<Sample> sampleSmooth(List<CameraRouteWaypoint> points, int subdivisions) {
        List<Sample> out = new ArrayList<>();
        int n = points.size();
        for (int i = 0; i < n - 1; i++) {
            CameraRouteWaypoint p0 = points.get(Math.max(0, i - 1));
            CameraRouteWaypoint p1 = points.get(i);
            CameraRouteWaypoint p2 = points.get(i + 1);
            CameraRouteWaypoint p3 = points.get(Math.min(n - 1, i + 2));
            int steps = i == n - 2 ? subdivisions + 1 : subdivisions;
            for (int s = 0; s < steps; s++) {
                float t = s / (float) subdivisions;
                double x = catmull(p0.x(), p1.x(), p2.x(), p3.x(), t);
                double y = catmull(p0.y(), p1.y(), p2.y(), p3.y(), t);
                double z = catmull(p0.z(), p1.z(), p2.z(), p3.z(), t);
                // Linear yaw/pitch between waypoints (simple per-segment interpolation)
                float yaw = lerpAngle(p1.yaw(), p2.yaw(), t);
                float pitch = p1.pitch() + (p2.pitch() - p1.pitch()) * t;
                out.add(new Sample(x, y, z, yaw, pitch));
            }
        }
        return out;
    }

    /**
     * Catmull-Rom for position AND yaw/pitch. Rotation changes gradually across waypoints,
     * eliminating sudden direction snaps at each point.
     */
    private static List<Sample> sampleSmoothCamera(List<CameraRouteWaypoint> points, int subdivisions) {
        List<Sample> out = new ArrayList<>();
        int n = points.size();
        for (int i = 0; i < n - 1; i++) {
            CameraRouteWaypoint p0 = points.get(Math.max(0, i - 1));
            CameraRouteWaypoint p1 = points.get(i);
            CameraRouteWaypoint p2 = points.get(i + 1);
            CameraRouteWaypoint p3 = points.get(Math.min(n - 1, i + 2));
            int steps = i == n - 2 ? subdivisions + 1 : subdivisions;
            for (int s = 0; s < steps; s++) {
                float t = s / (float) subdivisions;
                double x = catmull(p0.x(), p1.x(), p2.x(), p3.x(), t);
                double y = catmull(p0.y(), p1.y(), p2.y(), p3.y(), t);
                double z = catmull(p0.z(), p1.z(), p2.z(), p3.z(), t);
                // Catmull-Rom for yaw and pitch → smooth camera rotation through waypoints
                float yaw = catmullAngle(p0.yaw(), p1.yaw(), p2.yaw(), p3.yaw(), t);
                float pitch = (float) catmull(p0.pitch(), p1.pitch(), p2.pitch(), p3.pitch(), t);
                out.add(new Sample(x, y, z, yaw, pitch));
            }
        }
        return out;
    }

    private static Sample lerpSample(CameraRouteWaypoint a, CameraRouteWaypoint b, float t) {
        return new Sample(
                a.x() + (b.x() - a.x()) * t,
                a.y() + (b.y() - a.y()) * t,
                a.z() + (b.z() - a.z()) * t,
                lerpAngle(a.yaw(), b.yaw(), t),
                a.pitch() + (b.pitch() - a.pitch()) * t
        );
    }

    private static double catmull(double p0, double p1, double p2, double p3, float t) {
        double t2 = t * t;
        double t3 = t2 * t;
        return 0.5 * ((2 * p1)
                + (-p0 + p2) * t
                + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2
                + (-p0 + 3 * p1 - 3 * p2 + p3) * t3);
    }

    /** Catmull-Rom for angles, wrapping through ±180° correctly. */
    private static float catmullAngle(float a0, float a1, float a2, float a3, float t) {
        // Unwrap angles relative to a1
        float d1 = wrapAngleDiff(a0 - a1);
        float d2 = wrapAngleDiff(a2 - a1);
        float d3 = wrapAngleDiff(a3 - a1);
        float result = (float) catmull(a1 + d1, a1, a1 + d2, a1 + d3, t);
        return result;
    }

    private static float wrapAngleDiff(float diff) {
        while (diff > 180f) diff -= 360f;
        while (diff < -180f) diff += 360f;
        return diff;
    }

    public static float lerpAngle(float from, float to, float t) {
        float diff = to - from;
        while (diff > 180f) diff -= 360f;
        while (diff < -180f) diff += 360f;
        return from + diff * t;
    }

    public static float[] lookAtAngles(double camX, double camY, double camZ,
                                       double targetX, double targetY, double targetZ) {
        double dx = targetX - camX;
        double dy = targetY - camY;
        double dz = targetZ - camZ;
        double dist = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (-Math.toDegrees(Math.atan2(dx, dz)));
        float pitch = (float) (-Math.toDegrees(Math.atan2(dy, dist)));
        return new float[]{yaw, pitch};
    }
}
