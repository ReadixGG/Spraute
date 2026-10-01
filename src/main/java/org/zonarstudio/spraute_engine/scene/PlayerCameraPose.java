package org.zonarstudio.spraute_engine.scene;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.zonarstudio.spraute_engine.compat.SprauteEntityCompat;

import java.util.UUID;

/**
 * Rest pose of a player CAMERA node: offset from the eyes in camera space.
 * X = right, Y = up, Z = back; {@code distance} is extra boom along look
 * (vanilla third-person is {@code distance ≈ 4} with zero offset).
 */
public final class PlayerCameraPose {
    /** Vanilla third-person probe: eight corners of a small cube around the anchor. */
    private static final double COLLIDE_PROBE = 0.1;
    /** Blocks kept between the camera and the surface it would otherwise enter. */
    private static final double COLLIDE_GAP = 0.15;

    /**
     * How the rig behaves, as opposed to where it sits.
     *
     * <p>A locked axis holds its rest angle in world space instead of following the
     * player's look. Orbit hangs the boom on a pivot near the player and keeps the
     * camera aimed back at that pivot, so the subject stays centred while the angles
     * move the viewpoint around it.
     */
    public record Rig(boolean followPlayer, boolean lockYaw, boolean lockPitch,
                      boolean orbit, float orbitYaw, float orbitPitch,
                      double pivotX, double pivotY, double pivotZ,
                      boolean collide, boolean showHand,
                      boolean clearOcclusion, float occlusionAlpha, float occlusionRadius,
                      boolean moveRelative, boolean separateTurn,
                      boolean screenRaycast, boolean lookAtInteract, float turnSmooth,
                      boolean disableEffects, boolean walkBob, float walkBobAmount,
                      byte hideGui,
                      boolean lockX, boolean lockY, boolean lockZ,
                      double worldX, double worldY, double worldZ) {
        public static final Rig FREE = new Rig(true, false, false, false, 0, 0, 0, 0, 0,
                false, false, false, 0f, 0f, false, false, false, false, 0f,
                false, false, 1f, (byte) 0,
                false, false, false, 0, 0, 0);

        @FunctionalInterface
        public interface ClientLook {
            /** Camera yaw/pitch when {@link #separateTurn()} is on, or {@code null}. */
            float[] decoupledLook(float partialTick);
        }

        private static ClientLook clientLook;

        public static void setClientLook(ClientLook look) {
            clientLook = look;
        }

        public Rig {
            occlusionAlpha = Math.max(0f, Math.min(1f, occlusionAlpha));
            occlusionRadius = Math.max(0f, Math.min(8f, occlusionRadius));
            turnSmooth = Math.max(0f, Math.min(1f, turnSmooth));
            walkBobAmount = Math.max(0f, Math.min(2f, walkBobAmount));
        }

        public static Rig from(SceneNode node) {
            if (node == null) return FREE;
            return new Rig(node.cameraFollowPlayer, node.cameraLockYaw, node.cameraLockPitch,
                    node.cameraOrbit, node.cameraOrbitYaw, node.cameraOrbitPitch,
                    node.cameraPivotX, node.cameraPivotY, node.cameraPivotZ,
                    node.cameraCollide, node.cameraShowHand,
                    node.cameraClearOcclusion, node.cameraOcclusionAlpha, node.cameraOcclusionRadius,
                    node.cameraMoveRelative, node.cameraSeparateTurn, node.cameraScreenRaycast,
                    node.cameraLookAtInteract, node.cameraTurnSmooth,
                    node.cameraDisableEffects, node.cameraWalkBob, node.cameraWalkBobAmount,
                    node.cameraHideGui,
                    node.cameraLockX, node.cameraLockY, node.cameraLockZ,
                    node.cameraWorldX, node.cameraWorldY, node.cameraWorldZ);
        }

        /** Collide / showHand / occlusion / moveRelative / screenRaycast are ignored: they do not change the view. */
        public boolean isVanilla() {
            return followPlayer && !lockYaw && !lockPitch && !orbit
                    && Math.abs(orbitYaw) < 1e-3 && Math.abs(orbitPitch) < 1e-3
                    && Math.abs(pivotX) < 1e-4 && Math.abs(pivotY) < 1e-4 && Math.abs(pivotZ) < 1e-4
                    && !clearOcclusion
                    && !lockX && !lockY && !lockZ;
        }

        public boolean hasPositionLock() {
            return followPlayer && (lockX || lockY || lockZ);
        }

        public World applyPositionLocks(World pose) {
            if (pose == null || !hasPositionLock()) return pose;
            return new World(lockX ? worldX : pose.x(), lockY ? worldY : pose.y(),
                    lockZ ? worldZ : pose.z(), pose.yaw(), pose.pitch());
        }
    }

    public record Rest(double x, double y, double z, float yaw, float pitch, float fov, float distance, Rig rig) {
        public Rest {
            if (rig == null) rig = Rig.FREE;
        }

        public Rest(double x, double y, double z, float yaw, float pitch, float fov, float distance) {
            this(x, y, z, yaw, pitch, fov, distance, Rig.FREE);
        }

        public static Rest from(SceneNode camera) {
            if (camera == null) return VANILLA;
            return new Rest(camera.transform.px, camera.transform.py, camera.transform.pz,
                    (float) camera.transform.ry, (float) camera.transform.rx,
                    camera.cameraFov, camera.cameraDistance, Rig.from(camera));
        }

        public static Rest fromPlayers(SceneNode players) {
            if (players == null) return VANILLA;
            return new Rest(players.cameraRestX, players.cameraRestY, players.cameraRestZ,
                    players.cameraRestYaw, players.cameraRestPitch,
                    players.cameraFov, players.cameraDistance, Rig.from(players));
        }

        /**
         * True when position / look / boom / rig match vanilla first person.
         * FOV is intentionally ignored — a custom FOV alone must not detach the view.
         */
        public boolean isVanilla() {
            return Math.abs(x) < 1e-4 && Math.abs(y) < 1e-4 && Math.abs(z) < 1e-4
                    && Math.abs(yaw) < 1e-3 && Math.abs(pitch) < 1e-3
                    && Math.abs(distance) < 1e-4
                    && rig.isVanilla();
        }

        public boolean hasCustomFov() {
            return fov > 0f;
        }

        public boolean isDetached() {
            if (!rig.followPlayer()) return true;
            if (rig.hasPositionLock()) return true;
            return Math.abs(x) > 0.04 || Math.abs(y) > 0.04 || Math.abs(z) > 0.04
                    || Math.abs(distance) > 0.04
                    || Math.abs(rig.pivotX()) > 0.04 || Math.abs(rig.pivotY()) > 0.04
                    || Math.abs(rig.pivotZ()) > 0.04;
        }
    }

    public record World(double x, double y, double z, float yaw, float pitch) {}

    /**
     * Point the body where the camera is looking so WASD matches the view.
     * Used when a detached world pose is converted back to follow-player.
     */
    public static void snapLook(Entity player, float yaw, float pitch) {
        if (player == null) return;
        yaw = Mth.wrapDegrees(yaw);
        pitch = Mth.clamp(pitch, -90f, 90f);
        player.setYRot(yaw);
        player.setXRot(pitch);
        player.yRotO = yaw;
        player.xRotO = pitch;
        if (player instanceof LivingEntity living) {
            living.setYHeadRot(yaw);
            living.setYBodyRot(yaw);
            living.yHeadRotO = yaw;
        }
    }

    public static final Rest VANILLA = new Rest(0, 0, 0, 0, 0, 0, 0, Rig.FREE);

    private PlayerCameraPose() {}

    public static World compute(Entity player, Rest rest) {
        return compute(player, rest, 1f);
    }

    /**
     * @param partialTick frame blend between server ticks — without it an offset
     *                    camera snaps at 20 Hz while the world renders at 60+.
     */
    public static float lookBaseYaw(Entity player, Rest rest, float partialTick) {
        if (player == null) return 0f;
        Rest r = rest == null ? VANILLA : rest;
        if (r.rig().separateTurn() && Rig.clientLook != null) {
            float[] look = Rig.clientLook.decoupledLook(partialTick);
            if (look != null) return look[0];
        }
        return player.getViewYRot(partialTick);
    }

    public static float lookBasePitch(Entity player, Rest rest, float partialTick) {
        if (player == null) return 0f;
        Rest r = rest == null ? VANILLA : rest;
        if (r.rig().separateTurn() && Rig.clientLook != null) {
            float[] look = Rig.clientLook.decoupledLook(partialTick);
            if (look != null) return look[1];
        }
        return player.getViewXRot(partialTick);
    }

    public static float viewYaw(Entity player, Rest rest, float partialTick) {
        Rest r = rest == null ? VANILLA : rest;
        return r.rig().lockYaw() ? r.yaw() : lookBaseYaw(player, r, partialTick) + r.yaw();
    }

    public static float viewPitch(Entity player, Rest rest, float partialTick) {
        Rest r = rest == null ? VANILLA : rest;
        return r.rig().lockPitch() ? r.pitch() : lookBasePitch(player, r, partialTick) + r.pitch();
    }

    public static World compute(Entity player, Rest rest, float partialTick) {
        Rest r = rest == null ? VANILLA : rest;
        Rig rig = r.rig();
        if (!rig.followPlayer()) {
            return new World(r.x(), r.y(), r.z(), r.yaw(), r.pitch());
        }
        float yaw = viewYaw(player, r, partialTick);
        float pitch = viewPitch(player, r, partialTick);
        Vec3 anchor = anchor(player, r, yaw, partialTick);
        if (!rig.orbit()) {
            Vec3 boom = boom(r.x(), r.y(), r.z() + r.distance(), yaw, pitch);
            Vec3 eye = anchor.add(boom);
            return rig.applyPositionLocks(new World(eye.x, eye.y, eye.z, yaw, pitch));
        }
        Vec3 boom = boom(r.x(), r.y(), r.z() + r.distance(),
                yaw + rig.orbitYaw(), pitch + rig.orbitPitch());
        Vec3 eye = anchor.add(boom);
        float[] look = lookAngles(eye, anchor);
        return rig.applyPositionLocks(new World(eye.x, eye.y, eye.z, look[0], look[1]));
    }

    public static World compute(Entity player, SceneNode camera) {
        return compute(player, Rest.from(camera));
    }

    /**
     * Pose the view is actually rendered from: {@link #compute} plus block collision.
     * Authoring code (inspector, gizmo) must keep using {@link #compute}, otherwise the
     * handles jump around whenever the camera happens to graze a wall.
     */
    public static World computeView(Entity player, Rest rest) {
        return computeView(player, rest, 1f);
    }

    public static World computeView(Entity player, Rest rest, float partialTick) {
        World pose = compute(player, rest, partialTick);
        Rest r = rest == null ? VANILLA : rest;
        if (!r.rig().followPlayer() || !r.rig().collide() || player == null) return pose;
        float yaw = viewYaw(player, r, partialTick);
        return r.rig().applyPositionLocks(
                pullOutOfBlocks(player, anchor(player, r, yaw, partialTick), pose));
    }

    /**
     * Crosshair ray when {@link Rig#screenRaycast()} is on: origin sits just
     * past the player along the camera look so a boomed view does not pick the
     * wall behind the camera. {@code null} when the flag is off.
     *
     * @return {@code [start, end, direction]} or {@code null}
     */
    public static Vec3[] screenPickRay(Entity player, Rest rest, float partialTick, double pickRange) {
        if (player == null || rest == null || rest.rig() == null || !rest.rig().screenRaycast()) {
            return null;
        }
        World view = computeView(player, rest, partialTick);
        Vec3 cam = new Vec3(view.x(), view.y(), view.z());
        Vec3 dir = Vec3.directionFromRotation(view.pitch(), view.yaw());
        if (dir.lengthSqr() < 1e-8) return null;
        Vec3 eyes = player.getEyePosition(partialTick);
        double skip = Math.max(0d, cam.distanceTo(eyes) - 0.2);
        Vec3 start = cam.add(dir.scale(skip));
        return new Vec3[]{start, start.add(dir.scale(Math.max(0d, pickRange))), dir};
    }

    /** Point the boom hangs from: the eyes, or the orbit pivot when orbiting. */
    private static Vec3 anchor(Entity player, Rest rest, float yaw, float partialTick) {
        Vec3 eyes = player.getEyePosition(partialTick);
        Rig rig = rest.rig();
        if (!rig.orbit()) return eyes;
        // The pivot only yaws with the player: pitching it would make the whole
        // orbit nod every time the player looks up.
        return eyes.add(boom(rig.pivotX(), rig.pivotY(), rig.pivotZ(), yaw, 0f));
    }

    /** Camera-space offset (X = right, Y = up, Z = back) as a world-space delta. */
    private static Vec3 boom(double x, double y, double z, float yaw, float pitch) {
        Vec3 forward = Vec3.directionFromRotation(pitch, yaw);
        Vec3 right = Vec3.directionFromRotation(0, yaw + 90f);
        Vec3 up = forward.cross(right).scale(-1);
        return right.scale(x).add(up.scale(y)).add(forward.scale(-z));
    }

    /** Yaw/pitch that aims {@code from} at {@code to}. */
    private static float[] lookAngles(Vec3 from, Vec3 to) {
        Vec3 d = to.subtract(from);
        double flat = Math.sqrt(d.x * d.x + d.z * d.z);
        float yaw = (float) (Math.toDegrees(Math.atan2(-d.x, d.z)));
        float pitch = (float) (-Math.toDegrees(Math.atan2(d.y, flat)));
        return new float[]{yaw, pitch};
    }

    /**
     * Vanilla third-person pull-in: probe the eight corners of a small cube so the
     * camera does not slip through a block edge, then stop short of the nearest hit.
     */
    private static World pullOutOfBlocks(Entity player, Vec3 anchor, World pose) {
        Level level = SprauteEntityCompat.level(player);
        if (level == null) return pose;
        Vec3 target = new Vec3(pose.x(), pose.y(), pose.z());
        Vec3 delta = target.subtract(anchor);
        double reach = delta.length();
        if (reach < 1e-4) return pose;

        double nearest = reach;
        for (int corner = 0; corner < 8; corner++) {
            Vec3 probe = new Vec3(
                    (corner & 1) == 0 ? -COLLIDE_PROBE : COLLIDE_PROBE,
                    (corner & 2) == 0 ? -COLLIDE_PROBE : COLLIDE_PROBE,
                    (corner & 4) == 0 ? -COLLIDE_PROBE : COLLIDE_PROBE);
            Vec3 from = anchor.add(probe);
            BlockHitResult hit = level.clip(new ClipContext(from, from.add(delta),
                    ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, player));
            if (hit.getType() == HitResult.Type.MISS) continue;
            double distance = hit.getLocation().distanceTo(from);
            if (distance < nearest) nearest = distance;
        }
        if (nearest >= reach) return pose;
        Vec3 pulled = anchor.add(delta.scale(Math.max(0.0, nearest - COLLIDE_GAP) / reach));
        return new World(pulled.x, pulled.y, pulled.z, pose.yaw(), pose.pitch());
    }

    public static SceneTransform worldTransform(SceneDocument document, SceneNode camera) {
        SceneTransform fallback = camera == null || camera.transform == null
                ? new SceneTransform() : camera.transform.copy();
        Entity player = findPlayer(document, camera);
        if (player == null) return fallback;
        World world = compute(player, camera);
        SceneTransform t = new SceneTransform();
        t.px = world.x();
        t.py = world.y();
        t.pz = world.z();
        t.rx = world.pitch();
        t.ry = world.yaw();
        t.sx = 1;
        t.sy = 1;
        t.sz = 1;
        return t;
    }

    public static void writeLocalFromWorld(SceneDocument document, SceneNode camera, SceneTransform world) {
        if (camera == null || world == null) return;
        if (!camera.cameraFollowPlayer) {
            camera.transform.px = world.px;
            camera.transform.py = world.py;
            camera.transform.pz = world.pz;
            camera.transform.ry = world.ry;
            camera.transform.rx = world.rx;
            return;
        }
        Entity player = findPlayer(document, camera);
        // Dedicated client has no integrated server: if the owner is not in the
        // client level, do not treat world XYZ as an eye-local rest (that sends
        // the camera hundreds of blocks away the moment the player is found).
        if (player == null) return;
        writeLocalFromWorld(player, camera, world);
    }

    /** Eye-local rest from a world pose, given an explicit player (PLAYERS defaults / no scene tree). */
    public static void writeLocalFromWorld(Entity player, SceneNode camera, SceneTransform world) {
        if (player == null || camera == null || world == null) return;
        Rest rest = Rest.from(camera);
        writeLocalFromWorld(player.getEyePosition(1f),
                lookBaseYaw(player, rest, 1f), lookBasePitch(player, rest, 1f),
                camera, world);
    }

    /**
     * Inverse of {@link #compute} with a frozen look basis. Dedicated servers
     * rewrite player yaw every tick; using the live look while a gizmo drag
     * still holds the start-frame world pose maps the same point to huge offsets.
     */
    public static void writeLocalFromWorld(Vec3 eyes, float lookYaw, float lookPitch,
                                           SceneNode camera, SceneTransform world) {
        if (eyes == null || camera == null || world == null) return;
        Rest rest = Rest.from(camera);
        Rig rig = rest.rig();
        float yaw = rig.lockYaw() ? rest.yaw() : lookYaw + rest.yaw();
        float pitch = rig.lockPitch() ? rest.pitch() : lookPitch + rest.pitch();
        float boomYaw = rig.orbit() ? yaw + rig.orbitYaw() : yaw;
        float boomPitch = rig.orbit() ? pitch + rig.orbitPitch() : pitch;
        Vec3 anchor = rig.orbit()
                ? eyes.add(boom(rig.pivotX(), rig.pivotY(), rig.pivotZ(), yaw, 0f))
                : eyes;
        Vec3 rel = new Vec3(world.px, world.py, world.pz).subtract(anchor);
        Vec3 forward = Vec3.directionFromRotation(boomPitch, boomYaw);
        Vec3 right = Vec3.directionFromRotation(0, boomYaw + 90f);
        Vec3 up = forward.cross(right).scale(-1);
        if (rig.lockX()) camera.cameraWorldX = world.px;
        else camera.transform.px = rel.dot(right);
        if (rig.lockY()) camera.cameraWorldY = world.py;
        else camera.transform.py = rel.dot(up);
        if (rig.lockZ()) camera.cameraWorldZ = world.pz;
        else camera.transform.pz = -rel.dot(forward) - camera.cameraDistance;
        if (!rig.orbit()) {
            camera.transform.ry = rig.lockYaw() ? world.ry : world.ry - lookYaw;
            camera.transform.rx = rig.lockPitch() ? world.rx : world.rx - lookPitch;
        }
    }

    public static Entity findPlayer(SceneDocument document, SceneNode camera) {
        if (document == null || camera == null) return null;
        SceneNode parent = document.findParent(camera.id);
        if (parent == null || parent.type != SceneNodeType.PLAYER) return null;
        Entity found = findPlayer(parent.playerUuid);
        if (found != null) return found;
        return FMLEnvironment.dist == Dist.CLIENT ? ClientPlayers.findByName(parent.name) : null;
    }

    public static Entity findPlayer(String uuidText) {
        UUID uuid = PlayerSkinOps.parseUuid(uuidText);
        if (uuid == null) return null;
        if (FMLEnvironment.dist == Dist.CLIENT) {
            Entity client = ClientPlayers.find(uuid);
            if (client != null) return client;
        }
        var server = ServerLifecycleHooks.getCurrentServer();
        return server == null ? null : server.getPlayerList().getPlayer(uuid);
    }

    public static Player ownerOf(SceneDocument document, SceneNode camera) {
        Entity entity = findPlayer(document, camera);
        return entity instanceof Player player ? player : null;
    }

    /** Client: rest packet / local apply is meant for this machine's player. */
    public static boolean isOwnedByLocalPlayer(String playerUuid) {
        return FMLEnvironment.dist == Dist.CLIENT && ClientPlayers.isLocal(playerUuid);
    }

    private static final class ClientPlayers {
        static Entity find(UUID uuid) {
            var mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.player != null && uuid.equals(mc.player.getUUID())) return mc.player;
            if (mc.level == null) return null;
            Player byId = mc.level.getPlayerByUUID(uuid);
            if (byId != null) return byId;
            for (Player player : mc.level.players()) {
                if (uuid.equals(player.getUUID())) return player;
            }
            return null;
        }

        static Entity findByName(String name) {
            if (name == null || name.isBlank()) return null;
            var mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.player != null && name.equalsIgnoreCase(mc.player.getGameProfile().getName())) {
                return mc.player;
            }
            if (mc.level == null) return null;
            for (Player player : mc.level.players()) {
                if (name.equalsIgnoreCase(player.getGameProfile().getName())) return player;
            }
            return null;
        }

        static boolean isLocal(String playerUuid) {
            var mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.player == null) return false;
            if (playerUuid == null || playerUuid.isBlank()) return true;
            UUID uuid = PlayerSkinOps.parseUuid(playerUuid);
            if (uuid != null && uuid.equals(mc.player.getUUID())) return true;
            var connection = mc.getConnection();
            if (connection == null || uuid == null) return false;
            var info = connection.getPlayerInfo(uuid);
            return info != null && info.getProfile().getName()
                    .equalsIgnoreCase(mc.player.getGameProfile().getName());
        }
    }
}
