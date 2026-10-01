package org.zonarstudio.spraute_engine.scene;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/** Persistence for generic scene routes in {@code spraute_engine/routes/*.route.json}. */
public final class SceneRouteStorage {
    public static final String EXTENSION = ".route.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private SceneRouteStorage() {}

    public static void loadRoutes(SceneDocument document, Path sceneFile) {
        if (document == null || sceneFile == null) return;
        Path workspace = workspaceFromScene(sceneFile);
        document.walk((node, ignored) -> {
            if (node.type != SceneNodeType.ROUTE) return;
            Path file = routePath(workspace, node);
            if (!Files.isRegularFile(file)) return;
            try {
                RouteFile data = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), RouteFile.class);
                if (data == null) return;
                node.routeMode = normalizeMode(data.mode);
                node.routeClosed = data.closed;
                node.routeCurve = normalizeCurve(data.curve);
                node.routeSmoothness = data.smoothness == null ? 1f : clampSmoothnessEdit(data.smoothness);
                node.routeLayoutSpacing = data.layoutSpacing == null ? 2f
                        : Math.max(0f, data.layoutSpacing);
                node.routeLayoutOffset = data.layoutOffset == null ? node.routeLayoutOffset : data.layoutOffset;
                node.routeLayoutAnchor = SceneRouteLayout.normalizeAnchor(data.layoutAnchor);
                node.routeLayoutOverflow = SceneRouteLayout.normalizeOverflow(data.layoutOverflow);
                node.routeLayoutReverse = data.layoutReverse != null && data.layoutReverse;
                node.routeLayoutAlignYaw = data.layoutAlignYaw == null || data.layoutAlignYaw;
                node.routeLayoutAlignPitch = data.layoutAlignPitch != null && data.layoutAlignPitch;
                // External route files own points only. Spatial children stay in the scene JSON.
                node.children.removeIf(child -> child.type == SceneNodeType.ROUTE_POINT);
                List<SceneNode> loadedPoints = new ArrayList<>();
                if (data.points != null) {
                    int index = 1;
                    for (PointFile point : data.points) {
                        SceneNode child = SceneNode.routePoint("Точка " + index, point.x, point.y, point.z);
                        child.id = point.id == null || point.id.isBlank() ? "point" + index : point.id;
                        if (point.hasLook != null && point.hasLook) {
                            child.routeHasLook = true;
                            child.transform.ry = point.yaw == null ? 0 : point.yaw;
                            child.transform.rx = point.pitch == null ? 0 : point.pitch;
                        }
                        loadedPoints.add(child);
                        index++;
                    }
                }
                node.children.addAll(0, loadedPoints);
            } catch (IOException | RuntimeException ignoredException) {
                // Keep the scene copy when the external route is temporarily unreadable.
            }
        });
    }

    public static void saveRoutes(SceneDocument document, Path sceneFile) throws IOException {
        if (document == null || sceneFile == null) return;
        Path workspace = workspaceFromScene(sceneFile);
        List<IOException> errors = new ArrayList<>();
        document.walk((node, ignored) -> {
            if (node.type != SceneNodeType.ROUTE) return;
            try {
                saveNode(workspace, node);
            } catch (IOException e) {
                errors.add(e);
            }
        });
        if (!errors.isEmpty()) throw errors.get(0);
    }

    public static void saveNode(Path workspace, SceneNode route) throws IOException {
        if (workspace == null || route == null || route.type != SceneNodeType.ROUTE) return;
        RouteFile data = new RouteFile();
        data.format = 2;
        data.mode = normalizeMode(route.routeMode);
        data.closed = route.routeClosed;
        data.curve = normalizeCurve(route.routeCurve);
        data.smoothness = clampSmoothnessEdit(route.routeSmoothness);
        data.layoutSpacing = Math.max(0f, route.routeLayoutSpacing);
        data.layoutOffset = route.routeLayoutOffset;
        data.layoutAnchor = SceneRouteLayout.normalizeAnchor(route.routeLayoutAnchor);
        data.layoutOverflow = SceneRouteLayout.normalizeOverflow(route.routeLayoutOverflow);
        data.layoutReverse = route.routeLayoutReverse;
        data.layoutAlignYaw = route.routeLayoutAlignYaw;
        data.layoutAlignPitch = route.routeLayoutAlignPitch;
        data.points = new ArrayList<>();
        for (SceneNode child : route.children) {
            if (child.type != SceneNodeType.ROUTE_POINT) continue;
            PointFile point = new PointFile();
            point.id = child.id;
            point.x = child.transform.px;
            point.y = child.transform.py;
            point.z = child.transform.pz;
            if (child.routeHasLook) {
                point.hasLook = true;
                point.yaw = (float) child.transform.ry;
                point.pitch = (float) child.transform.rx;
            }
            data.points.add(point);
        }
        Path out = routePath(workspace, route);
        Files.createDirectories(out.getParent());
        Path tmp = out.resolveSibling(out.getFileName() + ".tmp");
        Files.writeString(tmp, GSON.toJson(data), StandardCharsets.UTF_8);
        try {
            Files.move(tmp, out, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException | UnsupportedOperationException e) {
            Files.move(tmp, out, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static Path routePath(Path workspace, SceneNode route) {
        String file = route.routeFile == null ? "" : route.routeFile.trim().replace('\\', '/');
        if (file.startsWith("routes/")) file = file.substring("routes/".length());
        if (file.endsWith(EXTENSION)) file = file.substring(0, file.length() - EXTENSION.length());
        else if (file.endsWith(".json")) file = file.substring(0, file.length() - 5);
        if (file.isBlank()) file = route.id == null || route.id.isBlank() ? "route" : route.id;
        file = sanitize(file);
        route.routeFile = "routes/" + file + EXTENSION;
        return workspace.resolve("routes").resolve(file + EXTENSION).normalize();
    }

    /** Always free. Legacy {@code blocks} in saved files is ignored. */
    public static String normalizeMode(String mode) {
        return "free";
    }

    public static String normalizeCurve(String curve) {
        if (curve == null) return "linear";
        return switch (curve.trim().toLowerCase()) {
            case "smooth", "curve", "catmull", "плавная", "плавный" -> "smooth";
            case "smooth_camera", "smoothcamera", "smooth_cam", "camera",
                 "плавная_камера", "плавная камера" -> "smooth_camera";
            default -> "linear";
        };
    }

    public static float clampSmoothnessEdit(float value) {
        if (Float.isNaN(value)) return 1f;
        return Math.max(0f, Math.min(1f, value));
    }

    private static Path workspaceFromScene(Path sceneFile) {
        Path scenes = sceneFile.toAbsolutePath().normalize().getParent();
        return scenes == null || scenes.getParent() == null ? sceneFile.toAbsolutePath().getParent() : scenes.getParent();
    }

    private static String sanitize(String value) {
        String safe = value.replaceAll("[^a-zA-Z0-9_\\-а-яА-ЯёЁ]", "_");
        return safe.isBlank() ? "route" : safe;
    }

    private static final class RouteFile {
        int format;
        String mode;
        boolean closed;
        String curve;
        Float smoothness;
        Float layoutSpacing;
        Float layoutOffset;
        String layoutAnchor;
        String layoutOverflow;
        Boolean layoutReverse;
        Boolean layoutAlignYaw;
        Boolean layoutAlignPitch;
        List<PointFile> points;
    }

    private static final class PointFile {
        String id;
        double x;
        double y;
        double z;
        Boolean hasLook;
        Float yaw;
        Float pitch;
    }
}
