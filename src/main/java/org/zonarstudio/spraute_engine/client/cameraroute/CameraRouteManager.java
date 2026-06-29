package org.zonarstudio.spraute_engine.client.cameraroute;

import com.mojang.logging.LogUtils;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;
import org.zonarstudio.spraute_engine.cameraroute.CameraFlightType;
import org.zonarstudio.spraute_engine.cameraroute.CameraRouteEndMode;
import org.zonarstudio.spraute_engine.cameraroute.CameraRouteStorage;
import org.zonarstudio.spraute_engine.cameraroute.CameraRouteWaypoint;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class CameraRouteManager {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final List<CameraRouteWaypoint> waypoints = new ArrayList<>();
    private static CameraFlightType flightType = CameraFlightType.SMOOTH;
    private static float totalDuration = CameraRouteStorage.DEFAULT_TOTAL_DURATION;
    private static CameraRouteEndMode endMode = CameraRouteEndMode.RETURN_TO_PLAYER;

    private CameraRouteManager() {}

    public static List<CameraRouteWaypoint> getWaypoints() {
        return Collections.unmodifiableList(waypoints);
    }

    public static CameraFlightType getFlightType() {
        return flightType;
    }

    public static void setFlightType(CameraFlightType type) {
        flightType = type != null ? type : CameraFlightType.SMOOTH;
    }

    public static float getTotalDuration() {
        return totalDuration;
    }

    public static void setTotalDuration(float seconds) {
        totalDuration = Math.max(0.5f, seconds);
    }

    /** @deprecated use {@link #getTotalDuration()} */
    public static float getSegmentDuration() {
        return getTotalDuration();
    }

    /** @deprecated use {@link #setTotalDuration(float)} */
    public static void setSegmentDuration(float seconds) {
        setTotalDuration(seconds);
    }

    public static CameraRouteEndMode getEndMode() {
        return endMode;
    }

    public static void setEndMode(CameraRouteEndMode mode) {
        endMode = mode != null ? mode : CameraRouteEndMode.RETURN_TO_PLAYER;
    }

    public static void clear() {
        waypoints.clear();
    }

    public static void setWaypoints(List<CameraRouteWaypoint> points) {
        waypoints.clear();
        if (points != null) {
            waypoints.addAll(points);
        }
    }

    public static void addWaypointFromPlayer(Player player) {
        waypoints.add(new CameraRouteWaypoint(
                player.getX(),
                player.getEyeY(),
                player.getZ(),
                player.getYRot(),
                player.getXRot(),
                0f));
    }

    /**
     * Update the per-segment duration for the waypoint at {@code index}.
     * {@code duration} ≤ 0 resets to auto.
     */
    public static void setWaypointSegDuration(int index, float duration) {
        if (index < 0 || index >= waypoints.size()) return;
        CameraRouteWaypoint old = waypoints.get(index);
        waypoints.set(index, new CameraRouteWaypoint(old.x(), old.y(), old.z(),
                old.yaw(), old.pitch(), Math.max(0f, duration)));
    }

    public static boolean removeLastWaypoint() {
        if (waypoints.isEmpty()) return false;
        waypoints.remove(waypoints.size() - 1);
        return true;
    }

    public static CameraRouteWaypoint getLastWaypoint() {
        return waypoints.isEmpty() ? null : waypoints.get(waypoints.size() - 1);
    }

    public static Path routesDirectory() {
        return CameraRouteStorage.routesDirectory(CameraRouteStorage.workspaceRoot());
    }

    public static void saveRoute(String name) throws IOException {
        String safe = CameraRouteStorage.sanitizeName(name);
        if (safe.isEmpty()) throw new IOException("empty route name");
        CameraRouteStorage.save(CameraRouteStorage.workspaceRoot(),
                new CameraRouteStorage.CameraRoute(safe, flightType, totalDuration, endMode, List.copyOf(waypoints)));
    }

    public static void loadRoute(String name) throws IOException {
        CameraRouteStorage.CameraRoute route = CameraRouteStorage.load(name);
        flightType = route.flightType();
        totalDuration = route.totalDuration();
        endMode = route.endMode();
        waypoints.clear();
        waypoints.addAll(route.points());
    }

    public static List<String> listSavedRoutes() {
        return CameraRouteStorage.listRoutes();
    }
}
