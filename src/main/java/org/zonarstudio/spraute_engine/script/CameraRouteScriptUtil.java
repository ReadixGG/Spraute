package org.zonarstudio.spraute_engine.script;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;
import org.zonarstudio.spraute_engine.cameraroute.CameraFlightType;
import org.zonarstudio.spraute_engine.cameraroute.CameraRouteEndMode;
import org.zonarstudio.spraute_engine.cameraroute.CameraRoutePath;
import org.zonarstudio.spraute_engine.cameraroute.CameraRouteStorage;
import org.zonarstudio.spraute_engine.cameraroute.CameraRouteWaypoint;
import org.zonarstudio.spraute_engine.network.CameraRoutePlayPacket;
import org.zonarstudio.spraute_engine.network.ModNetwork;

import java.io.IOException;
import java.util.List;

public final class CameraRouteScriptUtil {
    private CameraRouteScriptUtil() {}

    public record LookAtOverride(double x, double y, double z) {}

    public record PlayOptions(
            boolean lockMovement,
            byte hideGui,
            float returnSmooth,
            LookAtOverride lookAt,
            float totalDurationOverride,
            CameraRouteEndMode endModeOverride,
            CameraFlightType flightTypeOverride,
            float transitionSec
    ) {
        public static PlayOptions defaults() {
            return new PlayOptions(
                    CameraControls.DEFAULT_LOCK_MOVEMENT,
                    CameraControls.DEFAULT_HIDE_GUI,
                    0.8f,
                    null,
                    0f,
                    null,
                    null,
                    0f);
        }
    }

    public static float playRoute(ServerPlayer player, String routeName) throws IOException {
        return playRoute(player, routeName, PlayOptions.defaults());
    }

    public static float playRoute(ServerPlayer player, String routeName,
                                  boolean lockMovement, byte hideGui, float returnSmooth) throws IOException {
        return playRoute(player, routeName,
                new PlayOptions(lockMovement, hideGui, returnSmooth, null, 0f, null, null, 0f));
    }

    public static float playRoute(ServerPlayer player, String routeName,
                                  boolean lockMovement, byte hideGui, float returnSmooth,
                                  LookAtOverride lookAt) throws IOException {
        return playRoute(player, routeName,
                new PlayOptions(lockMovement, hideGui, returnSmooth, lookAt, 0f, null, null, 0f));
    }

    public static float playRoute(ServerPlayer player, String routeName, PlayOptions options) throws IOException {
        CameraRouteStorage.CameraRoute route = CameraRouteStorage.load(routeName);
        List<CameraRouteWaypoint> waypoints = route.points();
        PlayOptions opts = options != null ? options : PlayOptions.defaults();
        CameraFlightType flightType = opts.flightTypeOverride() != null
                ? opts.flightTypeOverride()
                : route.flightType();
        List<CameraRoutePath.Sample> samples = CameraRoutePath.buildSamples(
                waypoints, flightType, CameraRoutePath.PLAYBACK_SUBDIVISIONS);
        if (samples.size() < 2) {
            throw new IOException("route needs at least 2 points: " + routeName);
        }
        float totalDuration = opts.totalDurationOverride() > 0
                ? opts.totalDurationOverride()
                : CameraRoutePath.computeEffectiveTotalDuration(waypoints, route.totalDuration());
        CameraRouteEndMode endMode = opts.endModeOverride() != null
                ? opts.endModeOverride()
                : route.endMode();
        float[] timeline = CameraRoutePath.buildTimeline(waypoints, samples, totalDuration);
        ModNetwork.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                CameraRoutePlayPacket.from(samples, totalDuration, opts.returnSmooth(),
                        opts.lockMovement(), opts.hideGui(), opts.lookAt(), endMode, timeline, opts.transitionSec())
        );
        return CameraRoutePath.estimateDuration(samples, totalDuration, opts.returnSmooth(), endMode);
    }

    public static PlayOptions parsePlayOptions(List<Object> args, int lockIndex) {
        boolean lock = args.size() > lockIndex
                ? CameraControls.parseLockMovement(args.get(lockIndex))
                : CameraControls.DEFAULT_LOCK_MOVEMENT;
        byte hide = args.size() > lockIndex + 1
                ? CameraControls.parseHideGui(args.get(lockIndex + 1))
                : CameraControls.DEFAULT_HIDE_GUI;
        float returnSmooth = parseReturnSmooth(args, lockIndex + 2, 0.8f);

        int i = lockIndex + 3;
        float totalOverride = 0f;
        LookAtOverride lookAt = null;
        CameraRouteEndMode endMode = null;
        CameraFlightType flightType = null;
        float transitionSec = 0f;

        if (args.size() > i) {
            float[] totalBox = new float[]{0f};
            LookAtOverride[] lookBox = new LookAtOverride[]{null};
            CameraRouteEndMode[] endBox = new CameraRouteEndMode[]{null};
            CameraFlightType[] interpBox = new CameraFlightType[]{null};
            float[] transBox = new float[]{0f};

            if (isLegacyLookAtArgs(args, i)) {
                lookBox[0] = parseLookAtOverride(args, i);
            } else {
                if (args.get(i) instanceof Number) {
                    totalBox[0] = Math.max(0f, ((Number) args.get(i)).floatValue());
                    i++;
                }
                consumeTrailingRouteArgs(args, i, totalBox, lookBox, endBox, interpBox, transBox);
            }
            totalOverride = totalBox[0];
            lookAt = lookBox[0];
            endMode = endBox[0];
            flightType = interpBox[0];
            transitionSec = transBox[0];
        }

        return new PlayOptions(lock, hide, returnSmooth, lookAt, totalOverride, endMode, flightType, transitionSec);
    }

    /** Legacy: playCameraRoute(p, route, lock, hide, ret, lookX, lookY, lookZ) */
    private static boolean isLegacyLookAtArgs(List<Object> args, int i) {
        return args.size() >= i + 3
                && args.get(i) instanceof Number
                && args.get(i + 1) instanceof Number
                && args.get(i + 2) instanceof Number
                && args.size() == i + 3;
    }

    private static void consumeTrailingRouteArgs(List<Object> args, int i,
                                                 float[] totalBox,
                                                 LookAtOverride[] lookBox,
                                                 CameraRouteEndMode[] endBox,
                                                 CameraFlightType[] interpBox,
                                                 float[] transBox) {
        while (i < args.size()) {
            Object arg = args.get(i);
            if (arg instanceof String s) {
                String sl = s.trim().toLowerCase();
                if ("lookat".equals(sl) || "look_at".equals(sl)) {
                    LookAtOverride la = parseLookAtOverride(args, i + 1);
                    if (la != null) lookBox[0] = la;
                    i += 4;
                } else if ("interp".equals(sl) || "interpolation".equals(sl)) {
                    if (i + 1 < args.size()) {
                        String interpStr = String.valueOf(args.get(i + 1)).trim().toLowerCase();
                        if (!"route".equals(interpStr)) {
                            interpBox[0] = CameraFlightType.fromId(interpStr);
                        }
                        i += 2;
                    } else {
                        i++;
                    }
                } else if ("transition".equals(sl)) {
                    if (i + 1 < args.size() && args.get(i + 1) instanceof Number n) {
                        transBox[0] = Math.max(0f, n.floatValue());
                        i += 2;
                    } else {
                        i++;
                    }
                } else if ("route".equals(sl)) {
                    i++;
                } else if ("return".equals(sl) || "stay".equals(sl) || "stop".equals(sl)) {
                    endBox[0] = CameraRouteEndMode.parse(arg);
                    i++;
                } else {
                    i++;
                }
            } else if (arg instanceof Number && totalBox[0] <= 0f) {
                totalBox[0] = Math.max(0f, ((Number) arg).floatValue());
                i++;
            } else {
                break;
            }
        }
    }

    public static LookAtOverride parseLookAtOverride(List<Object> args, int xIndex) {
        if (args.size() <= xIndex + 2) return null;
        if (!(args.get(xIndex) instanceof Number)
                || !(args.get(xIndex + 1) instanceof Number)
                || !(args.get(xIndex + 2) instanceof Number)) {
            return null;
        }
        return new LookAtOverride(
                ((Number) args.get(xIndex)).doubleValue(),
                ((Number) args.get(xIndex + 1)).doubleValue(),
                ((Number) args.get(xIndex + 2)).doubleValue());
    }

    public static float parseTotalDurationOverride(List<Object> args, int index) {
        if (args.size() <= index || !(args.get(index) instanceof Number n)) return 0f;
        return Math.max(0f, n.floatValue());
    }

    public static CameraRouteEndMode parseEndMode(List<Object> args, int index) {
        if (args.size() <= index) return null;
        return CameraRouteEndMode.parse(args.get(index));
    }

    public static CameraControls.Settings parseControls(List<Object> args, int lockIndex, int hideIndex, int returnIndex) {
        boolean lock = args.size() > lockIndex
                ? CameraControls.parseLockMovement(args.get(lockIndex))
                : CameraControls.DEFAULT_LOCK_MOVEMENT;
        byte hide = args.size() > hideIndex
                ? CameraControls.parseHideGui(args.get(hideIndex))
                : CameraControls.DEFAULT_HIDE_GUI;
        return new CameraControls.Settings(lock, hide);
    }

    public static float parseReturnSmooth(List<Object> args, int index, float defaultVal) {
        if (args.size() <= index || !(args.get(index) instanceof Number n)) return defaultVal;
        return Math.max(0f, n.floatValue());
    }
}
