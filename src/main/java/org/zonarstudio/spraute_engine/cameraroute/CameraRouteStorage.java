package org.zonarstudio.spraute_engine.cameraroute;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class CameraRouteStorage {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type ROUTE_TYPE = new TypeToken<RouteFile>() {}.getType();
    public static final float DEFAULT_TOTAL_DURATION = 5f;

    public record CameraRoute(String name, CameraFlightType flightType, float totalDuration,
                              CameraRouteEndMode endMode, List<CameraRouteWaypoint> points) {}

    private CameraRouteStorage() {}

    public static Path routesDirectory(Path gameDir) {
        return gameDir.resolve("spraute_engine").resolve("routes");
    }

    /** Same root as scripts: {@code <gameDir>/spraute_engine/}. */
    public static Path workspaceRoot() {
        org.zonarstudio.spraute_engine.script.ScriptManager sm =
                org.zonarstudio.spraute_engine.script.ScriptManager.getInstance();
        if (sm != null) {
            Path scripts = sm.getScriptsDir();
            return scripts.getParent().getParent();
        }
        return net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get();
    }

    public static CameraRoute load(String name) throws IOException {
        return load(workspaceRoot(), name);
    }

    public static List<String> listRoutes() {
        return list(workspaceRoot());
    }

    public static CameraRoute load(Path gameDir, String name) throws IOException {
        String safe = sanitizeName(name);
        Path file = routesDirectory(gameDir).resolve(safe + ".json");
        if (!Files.isRegularFile(file)) {
            throw new IOException("route not found: " + safe);
        }
        try (Reader reader = Files.newBufferedReader(file)) {
            RouteFile data = GSON.fromJson(reader, ROUTE_TYPE);
            if (data == null || data.points == null || data.points.isEmpty()) {
                throw new IOException("invalid route file: " + safe);
            }
            CameraFlightType type = CameraFlightType.fromId(data.flightType);
            float total = resolveTotalDuration(data);
            CameraRouteEndMode endMode = data.endMode != null
                    ? CameraRouteEndMode.fromId(data.endMode)
                    : CameraRouteEndMode.RETURN_TO_PLAYER;
            return new CameraRoute(safe, type, total, endMode, List.copyOf(data.points));
        }
    }

    private static float resolveTotalDuration(RouteFile data) {
        if (data.totalDuration > 0) return data.totalDuration;
        if (data.segmentDuration > 0) {
            int segments = Math.max(1, data.points.size() - 1);
            return data.segmentDuration * segments;
        }
        return DEFAULT_TOTAL_DURATION;
    }

    public static void save(Path gameDir, CameraRoute route) throws IOException {
        String safe = sanitizeName(route.name());
        if (safe.isEmpty()) throw new IOException("empty route name");
        Files.createDirectories(routesDirectory(gameDir));
        RouteFile file = new RouteFile();
        file.name = safe;
        file.flightType = route.flightType().getId();
        file.totalDuration = route.totalDuration();
        file.endMode = route.endMode().getId();
        file.points = new ArrayList<>(route.points());
        Path out = routesDirectory(gameDir).resolve(safe + ".json");
        try (Writer writer = Files.newBufferedWriter(out)) {
            GSON.toJson(file, writer);
        }
        LOGGER.info("[Camera Route] Saved {}", out);
    }

    public static List<String> list(Path gameDir) {
        Path dir = routesDirectory(gameDir);
        if (!Files.isDirectory(dir)) return List.of();
        try (var stream = Files.list(dir)) {
            return stream
                    .filter(p -> p.toString().endsWith(".json"))
                    .map(p -> {
                        String fn = p.getFileName().toString();
                        return fn.substring(0, fn.length() - 5);
                    })
                    .sorted()
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    public static String sanitizeName(String name) {
        if (name == null) return "";
        return name.trim().replaceAll("[^a-zA-Z0-9_\\-а-яА-ЯёЁ]", "_");
    }

    static final class RouteFile {
        String name;
        String flightType;
        float totalDuration;
        /** @deprecated legacy per-segment time; converted to total on load */
        float segmentDuration;
        String endMode;
        List<CameraRouteWaypoint> points;
    }
}
