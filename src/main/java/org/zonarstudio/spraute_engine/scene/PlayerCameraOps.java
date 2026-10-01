package org.zonarstudio.spraute_engine.scene;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.zonarstudio.spraute_engine.cameraroute.CameraRouteEndMode;
import org.zonarstudio.spraute_engine.cameraroute.CameraRoutePath;
import org.zonarstudio.spraute_engine.cameraroute.CameraRouteWaypoint;
import org.zonarstudio.spraute_engine.network.CameraPacket;
import org.zonarstudio.spraute_engine.script.CameraControls;
import org.zonarstudio.spraute_engine.script.CameraRouteScriptUtil;
import org.zonarstudio.spraute_engine.script.CameraScriptUtil;
import org.zonarstudio.spraute_engine.script.CinematicViewTracker;

import java.util.ArrayList;
import java.util.List;

/** CAMERA-node script API (same surface as {@code player.camera}). */
public final class PlayerCameraOps {
    private PlayerCameraOps() {}

    public static Object get(SceneNode node, String prop) {
        if (node == null || node.type != SceneNodeType.CAMERA || prop == null) return null;
        return switch (prop) {
            case "x" -> node.transform.px;
            case "y" -> node.transform.py;
            case "z" -> node.transform.pz;
            case "yaw" -> node.transform.ry;
            case "pitch" -> node.transform.rx;
            case "fov" -> (double) node.cameraFov;
            case "distance", "boom", "zoom" -> (double) node.cameraDistance;
            case "orbityaw", "orbit_yaw" -> (double) node.cameraOrbitYaw;
            case "orbitpitch", "orbit_pitch" -> (double) node.cameraOrbitPitch;
            case "pivotx", "pivot_x" -> (double) node.cameraPivotX;
            case "pivoty", "pivot_y" -> (double) node.cameraPivotY;
            case "pivotz", "pivot_z" -> (double) node.cameraPivotZ;
            case "orbit" -> node.cameraOrbit;
            case "collide", "collision", "solid" -> node.cameraCollide;
            case "showhand", "show_hand", "hand" -> node.cameraShowHand;
            case "hidegui", "hide_gui" -> CameraControls.hideGuiId(node.cameraHideGui);
            case "moverelative", "move_relative", "walkrelative", "walk_relative" ->
                    node.cameraMoveRelative;
            case "separateturn", "separate_turn", "facewalk", "face_walk",
                 "playerfaceswalk", "player_faces_walk" -> node.cameraSeparateTurn;
            case "screenraycast", "screen_raycast", "aimscreen", "aim_screen",
                 "raycastcenter", "raycast_center", "crosshairraycast", "viewraycast" ->
                    node.cameraScreenRaycast;
            case "lookatinteract", "look_at_interact", "faceinteract", "face_interact",
                 "lookinteract", "look_interact" -> node.cameraLookAtInteract;
            case "disableeffects", "disable_effects", "noeffects", "no_effects",
                 "disablevanillafx", "disable_vanilla_fx", "noviewbob", "no_view_bob" ->
                    node.cameraDisableEffects;
            case "walkbob", "walk_bob", "headbob", "head_bob" -> node.cameraWalkBob;
            case "walkbobamount", "walk_bob_amount", "headbobamount", "head_bob_amount" ->
                    (double) node.cameraWalkBobAmount;
            case "turnsmooth", "turn_smooth", "bodyturnsmooth", "body_turn_smooth",
                 "facesmooth", "face_smooth", "turnblend", "turn_blend" ->
                    (double) node.cameraTurnSmooth;
            case "followplayer", "follow_player", "follow" -> node.cameraFollowPlayer;
            case "clearocclusion", "clear_occlusion", "hideocclusion", "hide_occlusion",
                 "occludeclear", "seethroughwalls", "see_through_walls" -> node.cameraClearOcclusion;
            case "occlusionalpha", "occlusion_alpha", "ghostalpha", "ghost_alpha" ->
                    (double) node.cameraOcclusionAlpha;
            case "occlusionradius", "occlusion_radius", "occlusionthickness", "occlusion_thickness",
                 "hidethickness", "hide_thickness" -> (double) node.cameraOcclusionRadius;
            case "lockyaw", "lock_yaw" -> node.cameraLockYaw;
            case "lockpitch", "lock_pitch" -> node.cameraLockPitch;
            case "lockrotation", "lock_rotation" -> node.cameraLockYaw && node.cameraLockPitch;
            case "lockx", "lock_x", "holdx", "hold_x", "freezex", "freeze_x",
                 "nofollowx", "no_follow_x" -> node.cameraLockX;
            case "locky", "lock_y", "holdy", "hold_y", "freezey", "freeze_y",
                 "nofollowy", "no_follow_y" -> node.cameraLockY;
            case "lockz", "lock_z", "holdz", "hold_z", "freezez", "freeze_z",
                 "nofollowz", "no_follow_z" -> node.cameraLockZ;
            case "followx", "follow_x" -> !node.cameraLockX;
            case "followy", "follow_y" -> !node.cameraLockY;
            case "followz", "follow_z" -> !node.cameraLockZ;
            case "worldx", "world_x", "holdworldx", "hold_world_x" -> node.cameraWorldX;
            case "worldy", "world_y", "holdworldy", "hold_world_y" -> node.cameraWorldY;
            case "worldz", "world_z", "holdworldz", "hold_world_z" -> node.cameraWorldZ;
            default -> null;
        };
    }

    public static boolean isCameraProperty(String prop) {
        if (prop == null) return false;
        return isFlagProperty(prop) || switch (prop) {
            case "x", "y", "z", "yaw", "pitch", "fov", "distance", "boom", "zoom",
                 "orbityaw", "orbit_yaw", "orbitpitch", "orbit_pitch",
                 "pivotx", "pivot_x", "pivoty", "pivot_y", "pivotz", "pivot_z",
                 "occlusionalpha", "occlusion_alpha", "ghostalpha", "ghost_alpha",
                 "occlusionradius", "occlusion_radius", "occlusionthickness", "occlusion_thickness",
                 "hidethickness", "hide_thickness",
                 "turnsmooth", "turn_smooth", "bodyturnsmooth", "body_turn_smooth",
                 "facesmooth", "face_smooth", "turnblend", "turn_blend",
                 "walkbobamount", "walk_bob_amount", "headbobamount", "head_bob_amount",
                 "hidegui", "hide_gui",
                 "worldx", "world_x", "holdworldx", "hold_world_x",
                 "worldy", "world_y", "holdworldy", "hold_world_y",
                 "worldz", "world_z", "holdworldz", "hold_world_z" -> true;
            default -> false;
        };
    }

    private static boolean isFlagProperty(String prop) {
        return switch (prop) {
            case "orbit", "collide", "collision", "solid",
                 "showhand", "show_hand", "hand",
                 "moverelative", "move_relative", "walkrelative", "walk_relative",
                 "separateturn", "separate_turn", "facewalk", "face_walk",
                 "playerfaceswalk", "player_faces_walk",
                 "screenraycast", "screen_raycast", "aimscreen", "aim_screen",
                 "raycastcenter", "raycast_center", "crosshairraycast", "viewraycast",
                 "lookatinteract", "look_at_interact", "faceinteract", "face_interact",
                 "lookinteract", "look_interact",
                 "disableeffects", "disable_effects", "noeffects", "no_effects",
                 "disablevanillafx", "disable_vanilla_fx", "noviewbob", "no_view_bob",
                 "walkbob", "walk_bob", "headbob", "head_bob",
                 "followplayer", "follow_player", "follow",
                 "clearocclusion", "clear_occlusion", "hideocclusion", "hide_occlusion",
                 "occludeclear", "seethroughwalls", "see_through_walls",
                 "lockyaw", "lock_yaw", "lockpitch", "lock_pitch",
                 "lockrotation", "lock_rotation",
                 "lockx", "lock_x", "holdx", "hold_x", "freezex", "freeze_x",
                 "nofollowx", "no_follow_x", "followx", "follow_x",
                 "locky", "lock_y", "holdy", "hold_y", "freezey", "freeze_y",
                 "nofollowy", "no_follow_y", "followy", "follow_y",
                 "lockz", "lock_z", "holdz", "hold_z", "freezez", "freeze_z",
                 "nofollowz", "no_follow_z", "followz", "follow_z" -> true;
            default -> false;
        };
    }

    public static boolean set(SceneActiveState state, SceneNode node, String prop, Object value,
                              CommandSourceStack source) {
        if (node == null || node.type != SceneNodeType.CAMERA || !isCameraProperty(prop)) return false;
        if (isHideGuiProperty(prop)) {
            node.cameraHideGui = CameraControls.parseHideGui(value);
            pushRest(state, node, source, false);
            return true;
        }
        if (isFlagProperty(prop)) {
            boolean flag = truthy(value);
            switch (prop) {
                case "orbit" -> node.cameraOrbit = flag;
                case "collide", "collision", "solid" -> node.cameraCollide = flag;
                case "showhand", "show_hand", "hand" -> node.cameraShowHand = flag;
                case "moverelative", "move_relative", "walkrelative", "walk_relative" ->
                        node.cameraMoveRelative = flag;
                case "separateturn", "separate_turn", "facewalk", "face_walk",
                     "playerfaceswalk", "player_faces_walk" -> node.cameraSeparateTurn = flag;
                case "screenraycast", "screen_raycast", "aimscreen", "aim_screen",
                     "raycastcenter", "raycast_center", "crosshairraycast", "viewraycast" ->
                        node.cameraScreenRaycast = flag;
                case "lookatinteract", "look_at_interact", "faceinteract", "face_interact",
                     "lookinteract", "look_interact" -> node.cameraLookAtInteract = flag;
                case "disableeffects", "disable_effects", "noeffects", "no_effects",
                     "disablevanillafx", "disable_vanilla_fx", "noviewbob", "no_view_bob" ->
                        node.cameraDisableEffects = flag;
                case "walkbob", "walk_bob", "headbob", "head_bob" -> node.cameraWalkBob = flag;
                case "followplayer", "follow_player", "follow" -> node.cameraFollowPlayer = flag;
                case "clearocclusion", "clear_occlusion", "hideocclusion", "hide_occlusion",
                     "occludeclear", "seethroughwalls", "see_through_walls" ->
                        node.cameraClearOcclusion = flag;
                case "lockyaw", "lock_yaw" -> node.cameraLockYaw = flag;
                case "lockpitch", "lock_pitch" -> node.cameraLockPitch = flag;
                case "lockrotation", "lock_rotation" -> {
                    node.cameraLockYaw = flag;
                    node.cameraLockPitch = flag;
                }
                case "lockx", "lock_x", "holdx", "hold_x", "freezex", "freeze_x",
                     "nofollowx", "no_follow_x" -> setAxisLock(state, node, source, 0, flag);
                case "locky", "lock_y", "holdy", "hold_y", "freezey", "freeze_y",
                     "nofollowy", "no_follow_y" -> setAxisLock(state, node, source, 1, flag);
                case "lockz", "lock_z", "holdz", "hold_z", "freezez", "freeze_z",
                     "nofollowz", "no_follow_z" -> setAxisLock(state, node, source, 2, flag);
                case "followx", "follow_x" -> setAxisLock(state, node, source, 0, !flag);
                case "followy", "follow_y" -> setAxisLock(state, node, source, 1, !flag);
                case "followz", "follow_z" -> setAxisLock(state, node, source, 2, !flag);
                default -> {
                    return false;
                }
            }
            pushRest(state, node, source, false);
            return true;
        }
        if (!(value instanceof Number number)) return false;
        switch (prop) {
            case "x" -> node.transform.px = number.doubleValue();
            case "y" -> node.transform.py = number.doubleValue();
            case "z" -> node.transform.pz = number.doubleValue();
            case "yaw" -> node.transform.ry = number.doubleValue();
            case "pitch" -> node.transform.rx = number.doubleValue();
            case "fov" -> node.cameraFov = number.floatValue();
            case "distance", "boom", "zoom" -> node.cameraDistance = number.floatValue();
            case "orbityaw", "orbit_yaw" -> node.cameraOrbitYaw = number.floatValue();
            case "orbitpitch", "orbit_pitch" -> node.cameraOrbitPitch = number.floatValue();
            case "pivotx", "pivot_x" -> node.cameraPivotX = number.floatValue();
            case "pivoty", "pivot_y" -> node.cameraPivotY = number.floatValue();
            case "pivotz", "pivot_z" -> node.cameraPivotZ = number.floatValue();
            case "occlusionalpha", "occlusion_alpha", "ghostalpha", "ghost_alpha" ->
                    node.cameraOcclusionAlpha = Math.max(0f, Math.min(1f, number.floatValue()));
            case "occlusionradius", "occlusion_radius", "occlusionthickness", "occlusion_thickness",
                 "hidethickness", "hide_thickness" ->
                    node.cameraOcclusionRadius = Math.max(0f, Math.min(8f, number.floatValue()));
            case "turnsmooth", "turn_smooth", "bodyturnsmooth", "body_turn_smooth",
                 "facesmooth", "face_smooth", "turnblend", "turn_blend" ->
                    node.cameraTurnSmooth = Math.max(0f, Math.min(1f, number.floatValue()));
            case "walkbobamount", "walk_bob_amount", "headbobamount", "head_bob_amount" ->
                    node.cameraWalkBobAmount = Math.max(0f, Math.min(2f, number.floatValue()));
            case "worldx", "world_x", "holdworldx", "hold_world_x" -> node.cameraWorldX = number.doubleValue();
            case "worldy", "world_y", "holdworldy", "hold_world_y" -> node.cameraWorldY = number.doubleValue();
            case "worldz", "world_z", "holdworldz", "hold_world_z" -> node.cameraWorldZ = number.doubleValue();
            default -> {
                return false;
            }
        }
        pushRest(state, node, source, false);
        return true;
    }

    private static boolean isHideGuiProperty(String prop) {
        return "hidegui".equals(prop) || "hide_gui".equals(prop);
    }

    private static boolean truthy(Object value) {
        if (value instanceof Boolean flag) return flag;
        if (value instanceof Number number) return number.doubleValue() != 0;
        if (value instanceof String text) {
            String t = text.trim();
            return t.equalsIgnoreCase("true") || t.equals("1") || t.equalsIgnoreCase("yes");
        }
        return false;
    }

    private static float argFloat(List<Object> args, int index, float fallback) {
        if (args == null || index >= args.size()) return fallback;
        Object v = args.get(index);
        if (v instanceof Number n) return n.floatValue();
        return fallback;
    }

    public static Object invoke(SceneActiveState state, SceneNode node, String method, List<Object> args,
                                CommandSourceStack source) {
        if (node == null || node.type != SceneNodeType.CAMERA || method == null) {
            return SceneNodeView.UNHANDLED;
        }
        ServerPlayer player = resolvePlayer(state, node, source);
        return switch (method) {
            case "setrest", "set_rest", "set" -> {
                applySetRest(node, args);
                pushRest(state, node, source, false);
                yield SceneNodeView.HANDLED_NULL;
            }
            case "setorbit", "set_orbit", "orbit" -> {
                applySetOrbit(node, args);
                pushRest(state, node, source, false);
                yield SceneNodeView.HANDLED_NULL;
            }
            case "setpivot", "set_pivot", "pivot" -> {
                if (args != null) {
                    if (args.size() > 0 && args.get(0) instanceof Number n) node.cameraPivotX = n.floatValue();
                    if (args.size() > 1 && args.get(1) instanceof Number n) node.cameraPivotY = n.floatValue();
                    if (args.size() > 2 && args.get(2) instanceof Number n) node.cameraPivotZ = n.floatValue();
                }
                pushRest(state, node, source, false);
                yield SceneNodeView.HANDLED_NULL;
            }
            case "lockrotation", "lock_rotation" -> {
                // No arguments locks both axes; one argument locks yaw only.
                node.cameraLockYaw = args == null || args.isEmpty() || truthy(args.get(0));
                node.cameraLockPitch = args == null || args.isEmpty()
                        ? node.cameraLockYaw
                        : args.size() > 1 && truthy(args.get(1));
                pushRest(state, node, source, false);
                yield SceneNodeView.HANDLED_NULL;
            }
            case "lockaxis", "lock_axis", "holdaxis", "hold_axis" -> {
                applyLockAxis(state, node, args, source);
                pushRest(state, node, source, false);
                yield SceneNodeView.HANDLED_NULL;
            }
            case "holdat", "hold_at", "pinat", "pin_at" -> {
                applyHoldAt(node, args);
                pushRest(state, node, source, false);
                yield SceneNodeView.HANDLED_NULL;
            }
            case "moveto", "move_to" -> moveTo(node, player, args, source);
            case "lookat", "look_at" -> lookAt(state, node, player, args, source);
            case "stoplook", "stop_look", "stoplookat" -> {
                if (player != null) {
                    org.zonarstudio.spraute_engine.network.ModNetwork.CHANNEL.send(
                            net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
                            CameraPacket.look(CameraPacket.LOOK_NONE, -1, 0, 0, 0));
                }
                yield SceneNodeView.HANDLED_NULL;
            }
            case "followroute", "follow_route", "playroute", "play_route" -> followRoute(state, player, args, source);
            case "returntoplayer", "return_to_player", "return" -> {
                if (player == null) yield 0.0;
                if (args != null && !args.isEmpty()) {
                    Object first = args.get(0);
                    if (first != null) {
                        String token = String.valueOf(first).trim().toLowerCase();
                        if (token.equals("reset") || token.equals("snap") || token.equals("instant")
                                || token.equals("мгновенно")) {
                            CameraScriptUtil.sendReset(player);
                            yield 0.0;
                        }
                    }
                }
                float time = args != null && !args.isEmpty() && args.get(0) instanceof Number n ? n.floatValue() : 0.5f;
                CameraScriptUtil.sendStop(player, time);
                yield (double) time;
            }
            case "reset" -> {
                if (player != null) CameraScriptUtil.sendReset(player);
                yield SceneNodeView.HANDLED_NULL;
            }
            case "shake" -> {
                if (player != null) {
                    float intensity = argFloat(args, 0, 0.4f);
                    float duration = argFloat(args, 1, 0.45f);
                    float frequency = argFloat(args, 2, 18f);
                    org.zonarstudio.spraute_engine.script.CameraEffects.shake(
                            player, intensity, duration, frequency);
                }
                yield SceneNodeView.HANDLED_NULL;
            }
            case "punch" -> {
                if (player != null) {
                    float yaw = argFloat(args, 0, 0f);
                    float pitch = argFloat(args, 1, 0f);
                    float roll = argFloat(args, 2, 0f);
                    org.zonarstudio.spraute_engine.script.CameraEffects.punch(player, yaw, pitch, roll);
                }
                yield SceneNodeView.HANDLED_NULL;
            }
            case "stopshake", "stop_shake", "stopeffects", "stop_effects" -> {
                if (player != null) org.zonarstudio.spraute_engine.script.CameraEffects.stop(player);
                yield SceneNodeView.HANDLED_NULL;
            }
            default -> SceneNodeView.UNHANDLED;
        };
    }

    static void pushRest(SceneActiveState state, SceneNode camera, CommandSourceStack source, boolean authoring) {
        if (camera == null) return;
        ServerPlayer player = resolvePlayer(state, camera, source);
        PlayerCameraPose.Rest rest = PlayerCameraPose.Rest.from(camera);
        if (authoring) {
            // PLAYERS-node defaults only — never used for player.camera / one CAMERA child.
            PlayerCameraSync.applyAuthoring(rest);
            return;
        }
        if (player != null) {
            PlayerCameraSync.applyPlayer(player.getUUID().toString(), rest);
        }
    }

    private static void applySetRest(SceneNode node, List<Object> args) {
        if (args == null) return;
        if (args.size() > 0 && args.get(0) instanceof Number n) node.transform.px = n.doubleValue();
        if (args.size() > 1 && args.get(1) instanceof Number n) node.transform.py = n.doubleValue();
        if (args.size() > 2 && args.get(2) instanceof Number n) node.transform.pz = n.doubleValue();
        if (args.size() > 3 && args.get(3) instanceof Number n) node.transform.ry = n.doubleValue();
        if (args.size() > 4 && args.get(4) instanceof Number n) node.transform.rx = n.doubleValue();
        if (args.size() > 5 && args.get(5) instanceof Number n) node.cameraDistance = n.floatValue();
        if (args.size() > 6 && args.get(6) instanceof Number n) node.cameraFov = n.floatValue();
    }

    /**
     * {@code setOrbit(orbitYaw, orbitPitch [, distance, pivotX, pivotY, pivotZ])} —
     * turns orbit on and aims the whole rig in one call, so a visual block stays one statement.
     */
    private static void setAxisLock(SceneActiveState state, SceneNode node, CommandSourceStack source,
                                    int axis, boolean lock) {
        boolean was = axis == 0 ? node.cameraLockX : axis == 1 ? node.cameraLockY : node.cameraLockZ;
        if (axis == 0) node.cameraLockX = lock;
        else if (axis == 1) node.cameraLockY = lock;
        else node.cameraLockZ = lock;
        if (lock && !was) seedWorldAxis(state, node, source, axis);
    }

    private static void seedWorldAxis(SceneActiveState state, SceneNode node, CommandSourceStack source, int axis) {
        ServerPlayer player = resolvePlayer(state, node, source);
        if (player == null) return;
        boolean sx = node.cameraLockX;
        boolean sy = node.cameraLockY;
        boolean sz = node.cameraLockZ;
        node.cameraLockX = false;
        node.cameraLockY = false;
        node.cameraLockZ = false;
        PlayerCameraPose.World world = PlayerCameraPose.compute(player, node);
        node.cameraLockX = sx;
        node.cameraLockY = sy;
        node.cameraLockZ = sz;
        if (axis == 0) node.cameraWorldX = world.x();
        else if (axis == 1) node.cameraWorldY = world.y();
        else node.cameraWorldZ = world.z();
    }

    /**
     * {@code lockAxis()} — pin all three at the current world pose.
     * {@code lockAxis(true, false, true)} — lock X and Z (Y still follows).
     */
    private static void applyLockAxis(SceneActiveState state, SceneNode node, List<Object> args,
                                      CommandSourceStack source) {
        if (args == null || args.isEmpty()) {
            setAxisLock(state, node, source, 0, true);
            setAxisLock(state, node, source, 1, true);
            setAxisLock(state, node, source, 2, true);
            return;
        }
        boolean lockX = truthy(args.get(0));
        boolean lockY = args.size() > 1 ? truthy(args.get(1)) : lockX;
        boolean lockZ = args.size() > 2 ? truthy(args.get(2)) : lockX;
        setAxisLock(state, node, source, 0, lockX);
        setAxisLock(state, node, source, 1, lockY);
        setAxisLock(state, node, source, 2, lockZ);
    }

    /** {@code holdAt(x, y, z)} — follow the player but pin all three axes to these world coords. */
    private static void applyHoldAt(SceneNode node, List<Object> args) {
        if (args == null || args.size() < 3) return;
        if (!(args.get(0) instanceof Number x) || !(args.get(1) instanceof Number y)
                || !(args.get(2) instanceof Number z)) {
            return;
        }
        node.cameraFollowPlayer = true;
        node.cameraLockX = true;
        node.cameraLockY = true;
        node.cameraLockZ = true;
        node.cameraWorldX = x.doubleValue();
        node.cameraWorldY = y.doubleValue();
        node.cameraWorldZ = z.doubleValue();
    }

    private static void applySetOrbit(SceneNode node, List<Object> args) {
        node.cameraOrbit = true;
        if (args == null) return;
        if (args.size() > 0 && args.get(0) instanceof Number n) node.cameraOrbitYaw = n.floatValue();
        if (args.size() > 1 && args.get(1) instanceof Number n) node.cameraOrbitPitch = n.floatValue();
        if (args.size() > 2 && args.get(2) instanceof Number n) node.cameraDistance = n.floatValue();
        if (args.size() > 3 && args.get(3) instanceof Number n) node.cameraPivotX = n.floatValue();
        if (args.size() > 4 && args.get(4) instanceof Number n) node.cameraPivotY = n.floatValue();
        if (args.size() > 5 && args.get(5) instanceof Number n) node.cameraPivotZ = n.floatValue();
    }

    private static Object moveTo(SceneNode node, ServerPlayer player, List<Object> args,
                                 CommandSourceStack source) {
        if (player == null || args == null || args.size() < 3) return 0.0;
        double x = ((Number) args.get(0)).doubleValue();
        double y = ((Number) args.get(1)).doubleValue();
        double z = ((Number) args.get(2)).doubleValue();
        float yaw = args.size() > 3 && args.get(3) instanceof Number n ? n.floatValue() : player.getYRot();
        float pitch = args.size() > 4 && args.get(4) instanceof Number n ? n.floatValue() : player.getXRot();
        float time;
        int cineFrom = 6;
        if (args.size() > 5 && args.get(5) instanceof Number n) {
            time = n.floatValue();
        } else {
            time = 0f;
            cineFrom = 5;
        }
        CineOpts cine = CineOpts.parse(args, cineFrom);
        CameraScriptUtil.LookAt look = CameraScriptUtil.LookAt.none();
        if (CinematicViewTracker.isActive(player)) {
            CameraScriptUtil.sendMove(player, x, y, z, yaw, pitch, time, look, cine.controls);
        } else {
            CameraScriptUtil.sendStart(player, x, y, z, yaw, pitch, 0f, time > 0f, time, null, look, cine.controls);
        }
        return applyAfterEnd(player, Math.max(0f, time), cine);
    }

    private static Object lookAt(SceneActiveState state, SceneNode camera, ServerPlayer player,
                                 List<Object> args, CommandSourceStack source) {
        if (player == null || args == null || args.isEmpty()) return null;
        CameraScriptUtil.LookAt lookAt;
        int cineFrom;
        if (args.size() >= 3 && args.get(0) instanceof Number
                && args.get(1) instanceof Number && args.get(2) instanceof Number) {
            lookAt = CameraScriptUtil.parseLookAtCoords(args, 0, true);
            cineFrom = 3;
        } else {
            lookAt = CameraScriptUtil.parseLookAt(args.get(0), true, source);
            cineFrom = 1;
        }
        boolean track = true;
        if (args.size() > cineFrom && CameraScriptUtil.isExplicitBool(args.get(cineFrom))) {
            track = CameraScriptUtil.optionalBool(args, cineFrom, true);
            cineFrom++;
        }
        lookAt = new CameraScriptUtil.LookAt(
                track ? CameraPacket.LOOK_TRACK : CameraPacket.LOOK_ONCE,
                lookAt.entityId(), lookAt.x(), lookAt.y(), lookAt.z());
        float time = 0f;
        if (args.size() > cineFrom && args.get(cineFrom) instanceof Number n) {
            time = n.floatValue();
            cineFrom++;
        } else {
            time = 0f;
        }
        if (lookAt.mode() == CameraPacket.LOOK_NONE) return null;
        CineOpts cine = CineOpts.parse(args, cineFrom);
        float flown = CameraScriptUtil.sendLook(player, lookAt, time, cine.controls);
        return applyAfterEnd(player, flown, cine);
    }

    private static Object followRoute(SceneActiveState state, ServerPlayer player, List<Object> args,
                                      CommandSourceStack source) {
        if (player == null || args == null || args.isEmpty()) return 0.0;
        float duration = args.size() > 1 && args.get(1) instanceof Number n ? n.floatValue() : 0f;
        boolean lookAlong = true;
        if (args.size() > 2) {
            Object flag = args.get(2);
            if (flag instanceof Boolean b) lookAlong = b;
            else if (flag instanceof Number n) lookAlong = n.doubleValue() != 0;
            else if (flag != null) {
                String s = String.valueOf(flag).trim().toLowerCase();
                lookAlong = !(s.equals("false") || s.equals("0") || s.equals("no") || s.equals("off")
                        || s.equals("нет"));
            }
        }
        CineOpts cine = args.size() > 3 ? CineOpts.parse(args, 3) : CineOpts.legacyRoute();
        CameraRouteEndMode endMode = cine.returnsImmediately()
                ? CameraRouteEndMode.RETURN_TO_PLAYER
                : CameraRouteEndMode.STAY_AT_LAST;
        var interp = parseInterpOverride(args, 3);
        CameraRouteScriptUtil.PlayOptions playOpts = new CameraRouteScriptUtil.PlayOptions(
                cine.controls.lockMovement(), cine.controls.hideGui(), cine.controls.lockBlocks(),
                cine.returnSmooth, null, duration, endMode, interp, 0f);
        ResolvedRoute resolved = resolveRoute(state, args.get(0), source);
        float played = 0f;
        if (resolved.route() != null && resolved.document() != null) {
            try {
                float yaw = player.getYRot();
                float pitch = player.getXRot();
                var waypoints = SceneRouteOps.cameraCornerWaypoints(
                        resolved.document(), resolved.route(), lookAlong, yaw, pitch);
                if (waypoints.size() >= 2) {
                    played = CameraRouteScriptUtil.playWaypoints(player, waypoints, duration,
                            flightFor(resolved.route(), resolved.points()), playOpts);
                }
            } catch (Exception e) {
                return 0.0;
            }
        } else if (resolved.points().size() >= 2) {
            try {
                played = CameraRouteScriptUtil.playWaypoints(player,
                        waypointsAlong(resolved.points()), duration,
                        flightFor(resolved.route(), resolved.points()), playOpts);
            } catch (Exception e) {
                return 0.0;
            }
        } else if (args.get(0) != null) {
            try {
                played = CameraRouteScriptUtil.playRoute(player, String.valueOf(args.get(0)), playOpts);
            } catch (Exception e) {
                return 0.0;
            }
        }
        if (played <= 0f) return 0.0;
        if (cine.holds()) {
            CameraScriptUtil.scheduleStop(player, played + cine.hold, cine.returnSmooth, currentGeneration(player));
            return (double) (played + cine.hold + Math.max(0f, cine.returnSmooth));
        }
        return (double) played;
    }

    private static double applyAfterEnd(ServerPlayer player, float travelSec, CineOpts cine) {
        long gen = currentGeneration(player);
        if (cine.holds()) {
            CameraScriptUtil.scheduleStop(player, travelSec + cine.hold, cine.returnSmooth, gen);
            return (double) (travelSec + cine.hold + Math.max(0f, cine.returnSmooth));
        }
        if (cine.returnsImmediately()) {
            CameraScriptUtil.scheduleStop(player, travelSec, cine.returnSmooth, gen);
            return (double) (travelSec + Math.max(0f, cine.returnSmooth));
        }
        return (double) travelSec;
    }

    private static long currentGeneration(ServerPlayer player) {
        CinematicViewTracker.View view = CinematicViewTracker.view(player.getUUID());
        return view == null ? 0L : view.generation();
    }

    private record CineOpts(CameraControls.Settings controls, String after, float returnSmooth, float hold) {
        static CineOpts gameplay() {
            return new CineOpts(new CameraControls.Settings(false, CameraPacket.HIDE_NONE, false),
                    "stay", 0.8f, 0f);
        }

        /** Old {@code followRoute(route, time, lookAlong)} returned to the player at the end. */
        static CineOpts legacyRoute() {
            return new CineOpts(new CameraControls.Settings(false, CameraPacket.HIDE_NONE, false),
                    "return", 0.5f, 0f);
        }

        static CineOpts parse(List<Object> args, int start) {
            if (args == null || args.size() <= start) return gameplay();
            CameraControls.Settings controls = CameraControls.parseTrailing(args, start, start + 1, start + 2);
            String after = "stay";
            float ret = 0.8f;
            float hold = 0f;
            int i = start + 3;
            if (args.size() > i && !(args.get(i) instanceof Number) && !(args.get(i) instanceof Boolean)) {
                after = String.valueOf(args.get(i)).trim().toLowerCase();
                i++;
            }
            if (after.equals("hold") || after.equals("holdreturn") || after.equals("удержать")) {
                after = "hold";
                if (args.size() > i && args.get(i) instanceof Number n) {
                    hold = Math.max(0f, n.floatValue());
                    i++;
                }
                if (args.size() > i && args.get(i) instanceof Number n) ret = n.floatValue();
            } else if (after.equals("return") || after.equals("back") || after.equals("вернуть")) {
                after = "return";
                if (args.size() > i && args.get(i) instanceof Number n) ret = n.floatValue();
            } else {
                after = "stay";
            }
            return new CineOpts(controls, after, ret, hold);
        }

        boolean returnsImmediately() {
            return "return".equals(after);
        }

        boolean holds() {
            return "hold".equals(after);
        }
    }

    private static org.zonarstudio.spraute_engine.cameraroute.CameraFlightType parseInterpOverride(
            List<Object> args, int start) {
        if (args == null) return null;
        for (int i = start; i < args.size() - 1; i++) {
            if (!(args.get(i) instanceof String raw)) continue;
            String key = raw.trim().toLowerCase();
            if (!key.equals("interp") && !key.equals("interpolation")) continue;
            String value = String.valueOf(args.get(i + 1)).trim().toLowerCase();
            if (value.equals("route") || value.equals("file") || value.equals("from_file")) return null;
            return org.zonarstudio.spraute_engine.cameraroute.CameraFlightType.fromId(value);
        }
        return null;
    }

    private record ResolvedRoute(SceneNode route, SceneDocument document, List<Vec3> points) {}

    private static ResolvedRoute resolveRoute(SceneActiveState state, Object arg, CommandSourceStack source) {
        SceneNode route = null;
        SceneDocument document = state == null ? null : state.document;
        if (arg instanceof SceneNodeView view) {
            SceneActiveState viewState = SceneRuntime.getActiveState(view.getSceneId());
            if (viewState != null) {
                document = viewState.document;
                SceneNode node = document.findNode(view.getNodeId());
                if (node != null && node.type == SceneNodeType.ROUTE) route = node;
            }
        } else if (arg != null && document != null) {
            SceneNode node = document.findNode(String.valueOf(arg).trim());
            if (node != null && node.type == SceneNodeType.ROUTE) route = node;
        }
        if (route == null || document == null) return new ResolvedRoute(null, document, List.of());
        String curve = SceneRouteStorage.normalizeCurve(route.routeCurve);
        // Full smooth modes leave Catmull sampling to CameraRoutePath (corners only).
        // Partial smoothness densifies here with a linear↔spline blend, then plays linear.
        float amount = SceneRouteStorage.clampSmoothnessEdit(route.routeSmoothness);
        List<Vec3> points;
        if ("linear".equals(curve) || amount <= 1e-4f) {
            points = SceneRouteOps.worldPoints(document, route);
        } else if (amount >= 0.999f) {
            points = SceneRouteOps.worldPoints(document, route);
        } else {
            points = SceneRouteOps.samplePolyline(document, route);
        }
        return new ResolvedRoute(route, document, points);
    }

    /** Flight type for a resolved scene route — LINEAR when points were already densified. */
    static org.zonarstudio.spraute_engine.cameraroute.CameraFlightType flightFor(SceneNode route, List<Vec3> points) {
        if (route == null) return org.zonarstudio.spraute_engine.cameraroute.CameraFlightType.SMOOTH_CAMERA;
        String curve = SceneRouteStorage.normalizeCurve(route.routeCurve);
        float amount = SceneRouteStorage.clampSmoothnessEdit(route.routeSmoothness);
        if ("linear".equals(curve) || amount <= 1e-4f) {
            return org.zonarstudio.spraute_engine.cameraroute.CameraFlightType.LINEAR;
        }
        // Densified blend polyline must not be Catmull'd again.
        if (amount < 0.999f && points != null && points.size() > SceneRouteOps.points(route).size() + 1) {
            return org.zonarstudio.spraute_engine.cameraroute.CameraFlightType.LINEAR;
        }
        return SceneRouteOps.cameraFlightType(route);
    }

    private static List<Vec3> resolveRoutePoints(SceneActiveState state, Object arg, CommandSourceStack source) {
        return resolveRoute(state, arg, source).points();
    }

    public static List<CameraRouteWaypoint> waypointsAlong(List<Vec3> points) {
        List<CameraRouteWaypoint> out = new ArrayList<>();
        for (int i = 0; i < points.size(); i++) {
            Vec3 p = points.get(i);
            Vec3 look = i + 1 < points.size() ? points.get(i + 1) : (i > 0 ? points.get(i - 1) : p);
            float[] angles = i + 1 < points.size() || i == 0
                    ? CameraRoutePath.lookAtAngles(p.x, p.y, p.z, look.x, look.y, look.z)
                    : CameraRoutePath.lookAtAngles(look.x, look.y, look.z, p.x, p.y, p.z);
            if (i + 1 >= points.size() && i > 0) {
                Vec3 prev = points.get(i - 1);
                angles = CameraRoutePath.lookAtAngles(prev.x, prev.y, prev.z, p.x, p.y, p.z);
            }
            out.add(new CameraRouteWaypoint(p.x, p.y, p.z, angles[0], angles[1], 0f));
        }
        return out;
    }

    private static ServerPlayer resolvePlayer(SceneActiveState state, SceneNode camera, CommandSourceStack source) {
        if (camera == null) return null;
        SceneNode parent = state == null || state.document == null ? null : state.document.findParent(camera.id);
        if (parent != null && parent.type == SceneNodeType.PLAYER && state != null) {
            Object obj = state.getRuntimeObject(parent.id);
            if (obj instanceof ServerPlayer sp) return sp;
        }
        var entity = PlayerCameraPose.findPlayer(state == null ? null : state.document, camera);
        if (entity instanceof ServerPlayer sp) return sp;
        return CameraScriptUtil.resolvePlayer(null, source);
    }
}
