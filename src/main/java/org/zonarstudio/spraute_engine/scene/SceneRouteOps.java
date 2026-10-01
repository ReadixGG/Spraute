package org.zonarstudio.spraute_engine.scene;

import net.minecraft.world.phys.Vec3;
import org.zonarstudio.spraute_engine.cameraroute.CameraFlightType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Canonical route mutations and path sampling shared by scripts and the editor. */
public final class SceneRouteOps {
    public record Segment(List<Vec3> points, boolean connected) {}

    private static final int PREVIEW_SUBDIVISIONS = 16;
    private static final int PLAYBACK_SUBDIVISIONS = 40;

    private SceneRouteOps() {}

    public static List<SceneNode> points(SceneNode route) {
        if (route == null || route.type != SceneNodeType.ROUTE) return List.of();
        return route.children.stream().filter(n -> n.type == SceneNodeType.ROUTE_POINT).toList();
    }

    public static SceneNode addPoint(SceneDocument document, SceneNode route, double x, double y, double z) {
        if (document == null || route == null || route.type != SceneNodeType.ROUTE) return null;
        int number = points(route).size() + 1;
        SceneTransform world = new SceneTransform();
        world.px = x;
        world.py = y;
        world.pz = z;
        SceneTransform parentWorld = document.worldTransform(route.id);
        SceneTransform local = SceneTransform.toLocal(parentWorld, world);
        SceneNode point = SceneNode.routePoint("Точка " + number, local.px, local.py, local.pz);
        try {
            SceneNodeIds.assignTree(document, point);
        } finally {
            SceneNodeIds.clearPending();
        }
        int insert = 0;
        while (insert < route.children.size()
                && route.children.get(insert).type == SceneNodeType.ROUTE_POINT) {
            insert++;
        }
        route.children.add(insert, point);
        return point;
    }

    /** Same as {@link #addPoint} plus an authored look (camera yaw/pitch). */
    public static SceneNode addPoint(SceneDocument document, SceneNode route,
                                     double x, double y, double z, float yaw, float pitch) {
        SceneNode point = addPoint(document, route, x, y, z);
        if (point == null) return null;
        point.routeHasLook = true;
        point.transform.ry = yaw;
        point.transform.rx = pitch;
        return point;
    }

    public static boolean removePoint(SceneNode route, int index) {
        List<SceneNode> points = points(route);
        if (index < 0 || index >= points.size()) return false;
        return route.children.remove(points.get(index));
    }

    public static void clear(SceneNode route) {
        if (route != null && route.type == SceneNodeType.ROUTE) {
            route.children.removeIf(node -> node.type == SceneNodeType.ROUTE_POINT);
        }
    }

    /** Persist the ROUTE that owns this ROUTE_POINT (or the route itself) to {@code .route.json}. */
    public static void saveOwningRoute(SceneDocument document, SceneNode node) {
        if (document == null || node == null) return;
        SceneNode route = node.type == SceneNodeType.ROUTE ? node : document.findParent(node.id);
        if (route == null || route.type != SceneNodeType.ROUTE) return;
        try {
            var gameDir = net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get();
            SceneRouteStorage.saveNode(gameDir.resolve("spraute_engine"), route);
        } catch (Exception ignored) {
        }
    }

    public static Map<String, Object> coordinates(SceneNode route, int index) {
        return coordinates(null, route, index);
    }

    public static Map<String, Object> coordinates(SceneDocument document, SceneNode route, int index) {
        List<SceneNode> points = points(route);
        if (index < 0 || index >= points.size()) return Map.of();
        SceneNode point = points.get(index);
        SceneTransform t = document == null ? point.transform : document.worldTransform(point.id);
        if (t == null) t = point.transform;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("x", t.px);
        result.put("y", t.py);
        result.put("z", t.pz);
        if (point.routeHasLook) {
            result.put("yaw", t.ry);
            result.put("pitch", t.rx);
            result.put("hasLook", true);
        } else {
            result.put("hasLook", false);
        }
        return result;
    }

    public static boolean setCoordinates(SceneNode route, int index, double x, double y, double z) {
        return setCoordinates(null, route, index, x, y, z);
    }

    public static boolean setCoordinates(SceneDocument document, SceneNode route, int index,
                                         double x, double y, double z) {
        List<SceneNode> points = points(route);
        if (index < 0 || index >= points.size()) return false;
        SceneNode point = points.get(index);
        if (document == null) {
            point.transform.px = x;
            point.transform.py = y;
            point.transform.pz = z;
            return true;
        }
        SceneTransform world = new SceneTransform();
        world.px = x;
        world.py = y;
        world.pz = z;
        point.transform = SceneTransform.toLocal(document.worldTransform(route.id), world);
        return true;
    }

    public static List<Vec3> worldPoints(SceneDocument document, SceneNode route) {
        List<SceneNode> nodes = points(route);
        if (nodes.isEmpty()) return List.of();
        List<Vec3> out = new ArrayList<>(nodes.size());
        for (SceneNode point : nodes) {
            SceneTransform t = document == null ? point.transform : document.worldTransform(point.id);
            if (t == null) t = point.transform;
            out.add(new Vec3(t.px, t.py, t.pz));
        }
        return List.copyOf(out);
    }

    /**
     * Corner waypoints for camera flights. When {@code lookAlong} is false, every point uses
     * {@code fixedYaw}/{@code fixedPitch}. When true, uses authored look on the point if set,
     * otherwise derives look from the path tangent. Catmull smoothing of yaw/pitch is done later
     * by {@link org.zonarstudio.spraute_engine.cameraroute.CameraRoutePath}.
     */
    public static List<org.zonarstudio.spraute_engine.cameraroute.CameraRouteWaypoint> cameraCornerWaypoints(
            SceneDocument document, SceneNode route, boolean lookAlong, float fixedYaw, float fixedPitch) {
        List<SceneNode> nodes = points(route);
        if (nodes.isEmpty()) return List.of();
        List<Vec3> positions = worldPoints(document, route);
        List<org.zonarstudio.spraute_engine.cameraroute.CameraRouteWaypoint> out =
                new ArrayList<>(positions.size());
        for (int i = 0; i < positions.size(); i++) {
            Vec3 p = positions.get(i);
            float yaw;
            float pitch;
            if (!lookAlong) {
                yaw = fixedYaw;
                pitch = fixedPitch;
            } else if (nodes.get(i).routeHasLook) {
                SceneTransform t = document == null ? nodes.get(i).transform
                        : document.worldTransform(nodes.get(i).id);
                if (t == null) t = nodes.get(i).transform;
                yaw = (float) t.ry;
                pitch = (float) t.rx;
            } else {
                float[] angles = tangentLook(positions, i);
                yaw = angles[0];
                pitch = angles[1];
            }
            out.add(new org.zonarstudio.spraute_engine.cameraroute.CameraRouteWaypoint(
                    p.x, p.y, p.z, yaw, pitch, 0f));
        }
        return out;
    }

    private static float[] tangentLook(List<Vec3> positions, int index) {
        Vec3 p = positions.get(index);
        if (index + 1 < positions.size()) {
            Vec3 n = positions.get(index + 1);
            return org.zonarstudio.spraute_engine.cameraroute.CameraRoutePath.lookAtAngles(
                    p.x, p.y, p.z, n.x, n.y, n.z);
        }
        if (index > 0) {
            Vec3 prev = positions.get(index - 1);
            return org.zonarstudio.spraute_engine.cameraroute.CameraRoutePath.lookAtAngles(
                    prev.x, prev.y, prev.z, p.x, p.y, p.z);
        }
        return new float[]{0f, 0f};
    }

    /**
     * Authored route corners for ground NPC walking. Pathfinding between these
     * points is done live ({@code moveTo} / A*), never along the camera curve.
     */
    public static List<Vec3> followWaypoints(SceneDocument document, SceneNode route) {
        return worldPoints(document, route);
    }

    /** Dense polyline for camera / fly / editor preview. Honours curve + smoothness. */
    public static List<Vec3> samplePolyline(SceneDocument document, SceneNode route) {
        return samplePolyline(document, route, PLAYBACK_SUBDIVISIONS);
    }

    public static List<Vec3> samplePolyline(SceneDocument document, SceneNode route, int subdivisions) {
        List<Vec3> corners = worldPoints(document, route);
        if (corners.size() < 2 || route == null) return corners;
        String curve = SceneRouteStorage.normalizeCurve(route.routeCurve);
        if ("linear".equals(curve)) return corners;
        float amount = SceneRouteStorage.clampSmoothnessEdit(route.routeSmoothness);
        if (amount <= 1e-4f) return corners;
        return sampleCatmull(corners, route.routeClosed, Math.max(4, subdivisions), amount);
    }

    public static CameraFlightType cameraFlightType(SceneNode route) {
        if (route == null) return CameraFlightType.SMOOTH_CAMERA;
        return switch (SceneRouteStorage.normalizeCurve(route.routeCurve)) {
            case "smooth" -> CameraFlightType.SMOOTH;
            case "smooth_camera" -> CameraFlightType.SMOOTH_CAMERA;
            default -> CameraFlightType.LINEAR;
        };
    }

    public static List<Segment> segments(SceneNode route) {
        return segments(null, route);
    }

    public static List<Segment> segments(SceneDocument document, SceneNode route) {
        List<SceneNode> nodes = points(route);
        if (nodes.size() < 2) return List.of();
        String curve = SceneRouteStorage.normalizeCurve(route.routeCurve);

        if (!"linear".equals(curve)) {
            List<Vec3> polyline = samplePolyline(document, route, PREVIEW_SUBDIVISIONS);
            if (polyline.size() < 2) return List.of();
            return List.of(new Segment(polyline, true));
        }

        int count = route.routeClosed ? nodes.size() : nodes.size() - 1;
        List<Segment> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            SceneTransform a = document == null ? nodes.get(i).transform
                    : document.worldTransform(nodes.get(i).id);
            SceneTransform b = document == null ? nodes.get((i + 1) % nodes.size()).transform
                    : document.worldTransform(nodes.get((i + 1) % nodes.size()).id);
            if (a == null) a = nodes.get(i).transform;
            if (b == null) b = nodes.get((i + 1) % nodes.size()).transform;
            Vec3 from = new Vec3(a.px, a.py, a.pz);
            Vec3 to = new Vec3(b.px, b.py, b.pz);
            result.add(new Segment(List.of(from, to), true));
        }
        return result;
    }

    /**
     * Catmull-Rom through {@code corners}. {@code amount} blends toward the straight
     * chord (0 = linear, 1 = pure spline) so authors can soften corners without a full curve.
     */
    static List<Vec3> sampleCatmull(List<Vec3> corners, boolean closed, int subdivisions, float amount) {
        int n = corners.size();
        if (n < 2) return List.copyOf(corners);
        float blend = Math.max(0f, Math.min(1f, amount));
        int segments = closed ? n : n - 1;
        List<Vec3> out = new ArrayList<>(segments * subdivisions + 1);
        for (int i = 0; i < segments; i++) {
            Vec3 p0 = corners.get(wrap(i - 1, n, closed));
            Vec3 p1 = corners.get(i % n);
            Vec3 p2 = corners.get((i + 1) % n);
            Vec3 p3 = corners.get(wrap(i + 2, n, closed));
            int steps = (i == segments - 1 && !closed) ? subdivisions + 1 : subdivisions;
            for (int s = 0; s < steps; s++) {
                float t = s / (float) subdivisions;
                double sx = catmull(p0.x, p1.x, p2.x, p3.x, t);
                double sy = catmull(p0.y, p1.y, p2.y, p3.y, t);
                double sz = catmull(p0.z, p1.z, p2.z, p3.z, t);
                double lx = p1.x + (p2.x - p1.x) * t;
                double ly = p1.y + (p2.y - p1.y) * t;
                double lz = p1.z + (p2.z - p1.z) * t;
                out.add(new Vec3(
                        lx + (sx - lx) * blend,
                        ly + (sy - ly) * blend,
                        lz + (sz - lz) * blend));
            }
        }
        if (closed && !out.isEmpty()) {
            out.add(out.get(0));
        }
        return List.copyOf(out);
    }

    private static int wrap(int index, int n, boolean closed) {
        if (closed) {
            int m = index % n;
            return m < 0 ? m + n : m;
        }
        return Math.max(0, Math.min(n - 1, index));
    }

    private static double catmull(double p0, double p1, double p2, double p3, float t) {
        double t2 = t * t;
        double t3 = t2 * t;
        return 0.5 * ((2 * p1)
                + (-p0 + p2) * t
                + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2
                + (-p0 + 3 * p1 - 3 * p2 + p3) * t3);
    }
}
