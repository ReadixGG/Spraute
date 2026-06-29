package org.zonarstudio.spraute_engine.script.function;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;
import org.zonarstudio.spraute_engine.cameraroute.CameraRoutePath;
import org.zonarstudio.spraute_engine.cameraroute.CameraRouteStorage;
import org.zonarstudio.spraute_engine.cameraroute.CameraRouteWaypoint;
import org.zonarstudio.spraute_engine.network.CameraPacket;
import org.zonarstudio.spraute_engine.network.ModNetwork;
import org.zonarstudio.spraute_engine.script.CameraControls;
import org.zonarstudio.spraute_engine.script.CameraScriptUtil;
import org.zonarstudio.spraute_engine.script.ScriptContext;

import java.io.IOException;
import java.util.List;

/**
 * Script camera API — set position/rotation, look at targets, smooth animation.
 * Works on 1.19.2 and 1.20.1 (client-side {@link org.zonarstudio.spraute_engine.client.CameraHandler}).
 */
public final class CameraFunctions {
    private CameraFunctions() {}

    /**
     * setCamera(player, x, y, z)
     * setCamera(player, x, y, z, yaw, pitch)
     * setCamera(player, x, y, z, yaw, pitch, holdTime)
     * setCamera(player, x, y, z, yaw, pitch, holdTime, smoothTime)
     * setCamera(..., lockMovement, hideGui) — smoothTime=0 → мгновенно
     */
    public static class SetCamera implements ScriptFunction {
        @Override public String getName() { return "setCamera"; }
        @Override public int getArgCount() { return -1; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{Object.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.size() < 4) return 0.0;
            ServerPlayer player = CameraScriptUtil.resolvePlayer(args.get(0), source);
            if (player == null) return 0.0;

            double x = ((Number) args.get(1)).doubleValue();
            double y = ((Number) args.get(2)).doubleValue();
            double z = ((Number) args.get(3)).doubleValue();
            float yaw = args.size() > 4 ? ((Number) args.get(4)).floatValue() : player.getYRot();
            float pitch = args.size() > 5 ? ((Number) args.get(5)).floatValue() : player.getXRot();
            float holdTime = CameraScriptUtil.optionalFloat(args, 6, 0f);
            float smoothTime = CameraScriptUtil.optionalFloat(args, 7, 0f);
            boolean smooth = smoothTime > 0f;
            CameraControls.Settings controls = CameraControls.parseTrailing(args, 8, 9);

            CameraScriptUtil.sendStart(player, x, y, z, yaw, pitch, holdTime, smooth, smoothTime,
                    null, CameraScriptUtil.LookAt.none(), controls);
            // Return total time so await time(setCamera(...)) works
            return (double) (smoothTime + holdTime);
        }
    }

    /**
     * setCameraLookAt(player, camX, camY, camZ, lookX, lookY, lookZ, [holdTime, smoothTime, track, lockMovement, hideGui])
     * setCameraLookAt(player, camX, camY, camZ, entityOrNpc, [holdTime, smoothTime, track, lockMovement, hideGui])
     */
    public static class SetCameraLookAt implements ScriptFunction {
        @Override public String getName() { return "setCameraLookAt"; }
        @Override public int getArgCount() { return -1; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{Object.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.size() < 5) return null;
            ServerPlayer player = CameraScriptUtil.resolvePlayer(args.get(0), source);
            if (player == null) return null;

            double x = ((Number) args.get(1)).doubleValue();
            double y = ((Number) args.get(2)).doubleValue();
            double z = ((Number) args.get(3)).doubleValue();

            CameraScriptUtil.LookAt lookAt;
            float holdTime;
            float smoothTime;
            boolean track;
            int lockIndex;
            int hideIndex;

            if (args.size() >= 7
                    && args.get(4) instanceof Number
                    && args.get(5) instanceof Number
                    && args.get(6) instanceof Number) {
                track = CameraScriptUtil.optionalBool(args, 9, false);
                lookAt = CameraScriptUtil.parseLookAtCoords(args, 4, track);
                holdTime = CameraScriptUtil.optionalFloat(args, 7, 0f);
                smoothTime = CameraScriptUtil.optionalFloat(args, 8, 0.5f);
                lockIndex = 10;
                hideIndex = 11;
            } else {
                track = CameraScriptUtil.optionalBool(args, 7, false);
                lookAt = CameraScriptUtil.parseLookAt(args.get(4), track, source);
                holdTime = CameraScriptUtil.optionalFloat(args, 5, 0f);
                smoothTime = CameraScriptUtil.optionalFloat(args, 6, 0.5f);
                lockIndex = 8;
                hideIndex = 9;
            }

            if (lookAt.mode() == org.zonarstudio.spraute_engine.network.CameraPacket.LOOK_NONE) return 0.0;

            CameraControls.Settings controls = CameraControls.parseTrailing(args, lockIndex, hideIndex);
            CameraScriptUtil.sendStart(player, x, y, z, player.getYRot(), player.getXRot(),
                    holdTime, smoothTime > 0f, smoothTime, null, lookAt, controls);
            // Return total time so await time(setCameraLookAt(...)) works
            return (double) (smoothTime + holdTime);
        }
    }

    /**
     * animateCamera(player, x, y, z, yaw, pitch, smoothTime, [lockMovement, hideGui])
     */
    public static class AnimateCamera implements ScriptFunction {
        @Override public String getName() { return "animateCamera"; }
        @Override public int getArgCount() { return -1; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{Object.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.size() < 6) return null;
            ServerPlayer player = CameraScriptUtil.resolvePlayer(args.get(0), source);
            if (player == null) return null;

            double x = ((Number) args.get(1)).doubleValue();
            double y = ((Number) args.get(2)).doubleValue();
            double z = ((Number) args.get(3)).doubleValue();
            float yaw = ((Number) args.get(4)).floatValue();
            float pitch = ((Number) args.get(5)).floatValue();
            float smoothTime = CameraScriptUtil.optionalFloat(args, 6, 0.5f);
            CameraControls.Settings controls = CameraControls.parseTrailing(args, 7, 8);

            CameraScriptUtil.sendMove(player, x, y, z, yaw, pitch, smoothTime,
                    CameraScriptUtil.LookAt.none(), controls);
            return (double) smoothTime; // allow await time(animateCamera(...))
        }
    }

    /**
     * cameraLookAt(player, x, y, z)                      — точечный взгляд (coords)
     * cameraLookAt(player, x, y, z, track)               — coords + следить
     * cameraLookAt(player, npcOrTarget)                   — сущность, одноразово
     * cameraLookAt(player, npcOrTarget, track)            — сущность, следить
     */
    public static class CameraLookAt implements ScriptFunction {
        @Override public String getName() { return "cameraLookAt"; }
        @Override public int getArgCount() { return -1; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{Object.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.size() < 2) return null;
            ServerPlayer player = CameraScriptUtil.resolvePlayer(args.get(0), source);
            if (player == null) return null;

            CameraScriptUtil.LookAt lookAt;
            if (args.size() >= 4
                    && args.get(1) instanceof Number
                    && args.get(2) instanceof Number
                    && args.get(3) instanceof Number) {
                boolean track = CameraScriptUtil.optionalBool(args, 4, false);
                lookAt = CameraScriptUtil.parseLookAtCoords(args, 1, track);
            } else {
                boolean track = CameraScriptUtil.optionalBool(args, 2, false);
                lookAt = CameraScriptUtil.parseLookAt(args.get(1), track, source);
            }

            if (lookAt.mode() == CameraPacket.LOOK_NONE) return null;

            CameraPacket pkt = CameraPacket.look(lookAt.mode(), lookAt.entityId(),
                    lookAt.x(), lookAt.y(), lookAt.z());
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), pkt);
            return null;
        }
    }

    /**
     * cameraWaypointTime(routeName, waypointIndex) → float seconds
     * Returns the time (in seconds from route start) when the camera reaches waypoint N (1-based).
     * Useful with {@code await time(...)} to trigger actions at specific route points.
     */
    public static class CameraWaypointTime implements ScriptFunction {
        @Override public String getName() { return "cameraWaypointTime"; }
        @Override public int getArgCount() { return 2; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{String.class, Number.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.size() < 2) return 0.0;
            String routeName = String.valueOf(args.get(0));
            int waypointIndex = ((Number) args.get(1)).intValue(); // 1-based

            try {
                CameraRouteStorage.CameraRoute route = CameraRouteStorage.load(routeName);
                List<CameraRouteWaypoint> waypoints = route.points();
                if (waypoints == null || waypoints.isEmpty()) return 0.0;

                float totalDuration = CameraRoutePath.computeEffectiveTotalDuration(
                        waypoints, route.totalDuration());
                List<CameraRoutePath.Sample> samples = CameraRoutePath.buildSamples(
                        waypoints, route.flightType(), CameraRoutePath.PLAYBACK_SUBDIVISIONS);
                float[] timeline = CameraRoutePath.buildTimeline(waypoints, samples, totalDuration);

                // Waypoint N (1-based) starts at sample index (N-1)*SUBDIVISIONS
                int sampleIdx = Math.max(0, (waypointIndex - 1)) * CameraRoutePath.PLAYBACK_SUBDIVISIONS;
                sampleIdx = Math.min(sampleIdx, timeline.length - 1);
                return (double) timeline[sampleIdx];
            } catch (IOException e) {
                return 0.0;
            }
        }
    }

    /**
     * cameraRouteStopAtPoint(player, routeName, pointIndex, [returnSmooth]) → float seconds
     * Schedules a camera stop on the server at the exact moment the camera reaches waypoint N,
     * and returns the waypoint time so that {@code await time(cameraRouteStopAtPoint(...))} waits
     * until that point and the camera stops simultaneously.
     * pointIndex is 1-based. returnSmooth=0 = instant stop.
     */
    public static class CameraRouteStopAtPoint implements ScriptFunction {
        @Override public String getName() { return "cameraRouteStopAtPoint"; }
        @Override public int getArgCount() { return -1; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{Object.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.size() < 3) return 0.0;
            ServerPlayer player = CameraScriptUtil.resolvePlayer(args.get(0), source);
            if (player == null) return 0.0;

            String routeName = String.valueOf(args.get(1));
            int pointIndex = ((Number) args.get(2)).intValue();
            float smooth = args.size() > 3 ? ((Number) args.get(3)).floatValue() : 0f;

            double waypointTime;
            try {
                org.zonarstudio.spraute_engine.cameraroute.CameraRouteStorage.CameraRoute route =
                        org.zonarstudio.spraute_engine.cameraroute.CameraRouteStorage.load(routeName);
                java.util.List<org.zonarstudio.spraute_engine.cameraroute.CameraRouteWaypoint> wps = route.points();
                if (wps == null || wps.isEmpty()) return 0.0;
                float totalDuration = CameraRoutePath.computeEffectiveTotalDuration(wps, route.totalDuration());
                java.util.List<CameraRoutePath.Sample> samples =
                        CameraRoutePath.buildSamples(wps, route.flightType(), CameraRoutePath.PLAYBACK_SUBDIVISIONS);
                float[] timeline = CameraRoutePath.buildTimeline(wps, samples, totalDuration);
                int sampleIdx = Math.min(
                        Math.max(0, (pointIndex - 1)) * CameraRoutePath.PLAYBACK_SUBDIVISIONS,
                        timeline.length - 1);
                waypointTime = timeline[sampleIdx];
            } catch (IOException e) {
                return 0.0;
            }

            // Schedule the camera stop to fire at exactly the waypoint time
            final float finalSmooth = smooth;
            final double finalTime = waypointTime;
            int ticks = Math.max(1, (int) Math.round(finalTime * 20.0));
            if (player.getServer() != null) {
                final ServerPlayer fp = player;
                player.getServer().tell(new net.minecraft.server.TickTask(
                        player.getServer().getTickCount() + ticks,
                        () -> {
                            if (finalSmooth > 0f) {
                                CameraScriptUtil.sendStop(fp, finalSmooth);
                            } else {
                                ModNetwork.CHANNEL.send(
                                        PacketDistributor.PLAYER.with(() -> fp),
                                        org.zonarstudio.spraute_engine.network.CameraPacket.reset());
                            }
                        }));
            }
            return finalTime; // caller does: await time(cameraRouteStopAtPoint(...))
        }
    }

    /**
     * animateCameraLookAt(player, x, y, z, lookTarget, smoothTime, [track, lockMovement, hideGui])
     */
    public static class AnimateCameraLookAt implements ScriptFunction {
        @Override public String getName() { return "animateCameraLookAt"; }
        @Override public int getArgCount() { return -1; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{Object.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.size() < 5) return null;
            ServerPlayer player = CameraScriptUtil.resolvePlayer(args.get(0), source);
            if (player == null) return null;

            double x = ((Number) args.get(1)).doubleValue();
            double y = ((Number) args.get(2)).doubleValue();
            double z = ((Number) args.get(3)).doubleValue();

            CameraScriptUtil.LookAt lookAt;
            float smoothTime;
            boolean track;
            int lockIndex;
            int hideIndex;

            if (args.size() >= 7
                    && args.get(4) instanceof Number
                    && args.get(5) instanceof Number
                    && args.get(6) instanceof Number) {
                track = CameraScriptUtil.optionalBool(args, 8, false);
                lookAt = CameraScriptUtil.parseLookAtCoords(args, 4, track);
                smoothTime = CameraScriptUtil.optionalFloat(args, 7, 0.5f);
                lockIndex = 9;
                hideIndex = 10;
            } else {
                track = CameraScriptUtil.optionalBool(args, 6, false);
                lookAt = CameraScriptUtil.parseLookAt(args.get(4), track, source);
                smoothTime = CameraScriptUtil.optionalFloat(args, 5, 0.5f);
                lockIndex = 7;
                hideIndex = 8;
            }

            if (lookAt.mode() == org.zonarstudio.spraute_engine.network.CameraPacket.LOOK_NONE) return 0.0;

            CameraControls.Settings controls = CameraControls.parseTrailing(args, lockIndex, hideIndex);
            CameraScriptUtil.sendMove(player, x, y, z, player.getYRot(), player.getXRot(), smoothTime, lookAt, controls);
            return (double) smoothTime; // allow await time(animateCameraLookAt(...))
        }
    }
}
