package org.zonarstudio.spraute_engine.cameraroute;

/**
 * A single waypoint on a camera route.
 * {@code segDuration} is the time (seconds) to fly from the previous waypoint to this one.
 * 0 = distribute automatically based on the route total duration.
 */
public record CameraRouteWaypoint(double x, double y, double z, float yaw, float pitch, float segDuration) {

    /** Backward-compat constructor without segDuration (treated as auto). */
    public CameraRouteWaypoint(double x, double y, double z, float yaw, float pitch) {
        this(x, y, z, yaw, pitch, 0f);
    }
}
