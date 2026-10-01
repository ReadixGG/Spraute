package org.zonarstudio.spraute_engine.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.zonarstudio.spraute_engine.Spraute_engine;
import org.zonarstudio.spraute_engine.scene.PlayerCameraPose;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Client-only: temporarily turn solid blocks between the camera and the look
 * subject into air, optionally drawing translucent ghosts so walls do not
 * cover a third-person / cinematic shot.
 */
@Mod.EventBusSubscriber(modid = Spraute_engine.MODID, value = Dist.CLIENT)
public final class CameraOcclusionClient {
    private static final double STEP = 0.28;
    private static final double END_MARGIN = 0.45;
    private static final int MAX_BLOCKS = 2048;
    private static final float MAX_RADIUS = 8f;

    private static final Map<BlockPos, BlockState> HIDDEN = new HashMap<>();
    private static float ghostAlpha;

    private CameraOcclusionClient() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        tick();
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        clearAll();
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        if (HIDDEN.isEmpty() || ghostAlpha < 0.02f) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        PoseStack pose = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();
        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buf = tess.getBuilder();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        float a = Math.min(0.85f, ghostAlpha);
        float r = 0.72f, g = 0.78f, b = 0.86f;
        for (BlockPos pos : HIDDEN.keySet()) {
            float x0 = (float) (pos.getX() - cam.x);
            float y0 = (float) (pos.getY() - cam.y);
            float z0 = (float) (pos.getZ() - cam.z);
            float x1 = x0 + 1f, y1 = y0 + 1f, z1 = z0 + 1f;
            // -Z
            vert(buf, pose, x0, y0, z0, r, g, b, a);
            vert(buf, pose, x1, y0, z0, r, g, b, a);
            vert(buf, pose, x1, y1, z0, r, g, b, a);
            vert(buf, pose, x0, y1, z0, r, g, b, a);
            // +Z
            vert(buf, pose, x0, y0, z1, r, g, b, a);
            vert(buf, pose, x0, y1, z1, r, g, b, a);
            vert(buf, pose, x1, y1, z1, r, g, b, a);
            vert(buf, pose, x1, y0, z1, r, g, b, a);
            // -Y
            vert(buf, pose, x0, y0, z0, r, g, b, a);
            vert(buf, pose, x0, y0, z1, r, g, b, a);
            vert(buf, pose, x1, y0, z1, r, g, b, a);
            vert(buf, pose, x1, y0, z0, r, g, b, a);
            // +Y
            vert(buf, pose, x0, y1, z0, r, g, b, a);
            vert(buf, pose, x1, y1, z0, r, g, b, a);
            vert(buf, pose, x1, y1, z1, r, g, b, a);
            vert(buf, pose, x0, y1, z1, r, g, b, a);
            // -X
            vert(buf, pose, x0, y0, z0, r, g, b, a);
            vert(buf, pose, x0, y1, z0, r, g, b, a);
            vert(buf, pose, x0, y1, z1, r, g, b, a);
            vert(buf, pose, x0, y0, z1, r, g, b, a);
            // +X
            vert(buf, pose, x1, y0, z0, r, g, b, a);
            vert(buf, pose, x1, y0, z1, r, g, b, a);
            vert(buf, pose, x1, y1, z1, r, g, b, a);
            vert(buf, pose, x1, y1, z0, r, g, b, a);
        }
        tess.end();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private static void vert(BufferBuilder buf, PoseStack pose,
                             float x, float y, float z, float r, float g, float b, float a) {
        buf.vertex(pose.last().pose(), x, y, z).color(r, g, b, a).endVertex();
    }

    public static void clearAll() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && !HIDDEN.isEmpty()) {
            for (Map.Entry<BlockPos, BlockState> e : HIDDEN.entrySet()) {
                if (mc.level.isLoaded(e.getKey())) {
                    mc.level.setBlock(e.getKey(), e.getValue(), 18);
                }
            }
        }
        HIDDEN.clear();
        ghostAlpha = 0f;
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.isPaused()) {
            clearAll();
            return;
        }
        PlayerCameraPose.Rest rest = CameraHandler.restPose();
        PlayerCameraPose.Rig rig = rest == null ? PlayerCameraPose.Rig.FREE : rest.rig();
        if (rig == null || !rig.clearOcclusion()) {
            clearAll();
            return;
        }
        if (!CameraHandler.isOverridingView()) {
            clearAll();
            return;
        }
        double[] seg = new double[6];
        if (!CameraHandler.resolveOcclusionSegment(seg)) {
            clearAll();
            return;
        }
        ghostAlpha = Math.max(0f, Math.min(1f, rig.occlusionAlpha()));
        Set<BlockPos> desired = collect(mc, seg[0], seg[1], seg[2], seg[3], seg[4], seg[5],
                rig.occlusionRadius());
        applyDesired(mc, desired);
    }

    private static Set<BlockPos> collect(Minecraft mc,
                                         double cx, double cy, double cz,
                                         double tx, double ty, double tz,
                                         float radius) {
        Set<BlockPos> out = new HashSet<>();
        Vec3 from = new Vec3(cx, cy, cz);
        Vec3 to = new Vec3(tx, ty, tz);
        double dist = from.distanceTo(to);
        if (dist < 0.75) return out;
        Vec3 dir = to.subtract(from).normalize();
        double end = Math.max(0.1, dist - END_MARGIN);
        float r = Math.max(0f, Math.min(MAX_RADIUS, radius));
        if (r < 0.05f) {
            collectLine(mc, out, from, dir, end);
            return out;
        }
        collectCapsule(mc, out, from, to, dir, end, r);
        return out;
    }

    private static void collectLine(Minecraft mc, Set<BlockPos> out, Vec3 from, Vec3 dir, double end) {
        for (double t = STEP; t < end && out.size() < MAX_BLOCKS; t += STEP) {
            consider(mc, out, from.add(dir.scale(t)));
        }
    }

    /**
     * Hide every solid block whose centre is within {@code radius} of the
     * camera→subject segment (a corridor, not a single ray).
     */
    private static void collectCapsule(Minecraft mc, Set<BlockPos> out,
                                       Vec3 from, Vec3 to, Vec3 dir, double end, float radius) {
        double pad = radius + 0.51;
        int minX = (int) Math.floor(Math.min(from.x, to.x) - pad);
        int minY = (int) Math.floor(Math.min(from.y, to.y) - pad);
        int minZ = (int) Math.floor(Math.min(from.z, to.z) - pad);
        int maxX = (int) Math.ceil(Math.max(from.x, to.x) + pad);
        int maxY = (int) Math.ceil(Math.max(from.y, to.y) + pad);
        int maxZ = (int) Math.ceil(Math.max(from.z, to.z) + pad);
        double limit = radius + 0.51;
        double limitSq = limit * limit;
        Vec3 ab = to.subtract(from);
        double len2 = ab.lengthSqr();
        for (int y = minY; y <= maxY && out.size() < MAX_BLOCKS; y++) {
            for (int x = minX; x <= maxX && out.size() < MAX_BLOCKS; x++) {
                for (int z = minZ; z <= maxZ && out.size() < MAX_BLOCKS; z++) {
                    double px = x + 0.5;
                    double py = y + 0.5;
                    double pz = z + 0.5;
                    double t = 0d;
                    if (len2 > 1e-8) {
                        t = ((px - from.x) * ab.x + (py - from.y) * ab.y + (pz - from.z) * ab.z) / len2;
                        t = t < 0d ? 0d : Math.min(1d, t);
                    }
                    double qx = from.x + ab.x * t;
                    double qy = from.y + ab.y * t;
                    double qz = from.z + ab.z * t;
                    double dx = px - qx, dy = py - qy, dz = pz - qz;
                    if (dx * dx + dy * dy + dz * dz > limitSq) continue;
                    double along = (qx - from.x) * dir.x + (qy - from.y) * dir.y + (qz - from.z) * dir.z;
                    if (along < STEP || along > end) continue;
                    consider(mc, out, px, py, pz);
                }
            }
        }
    }

    private static void consider(Minecraft mc, Set<BlockPos> out, Vec3 at) {
        consider(mc, out, at.x, at.y, at.z);
    }

    private static void consider(Minecraft mc, Set<BlockPos> out, double x, double y, double z) {
        //? if >=1.20.1 {
        BlockPos pos = BlockPos.containing(x, y, z);
        //?} else {
        /*BlockPos pos = new BlockPos(x, y, z);
        *///?}
        if (!mc.level.isLoaded(pos)) return;
        BlockState state = effectiveState(mc, pos);
        if (state.isAir()) return;
        if (state.getCollisionShape(mc.level, pos).isEmpty()) return;
        out.add(pos.immutable());
    }

    /** Prefer the pre-hide state so we do not treat our own air as empty forever. */
    private static BlockState effectiveState(Minecraft mc, BlockPos pos) {
        BlockState hidden = HIDDEN.get(pos);
        if (hidden != null) return hidden;
        return mc.level.getBlockState(pos);
    }

    private static void applyDesired(Minecraft mc, Set<BlockPos> desired) {
        Iterator<Map.Entry<BlockPos, BlockState>> it = HIDDEN.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<BlockPos, BlockState> e = it.next();
            if (desired.contains(e.getKey())) continue;
            if (mc.level.isLoaded(e.getKey())) {
                mc.level.setBlock(e.getKey(), e.getValue(), 18);
            }
            it.remove();
        }
        for (BlockPos pos : desired) {
            if (HIDDEN.containsKey(pos)) continue;
            if (!mc.level.isLoaded(pos)) continue;
            BlockState state = mc.level.getBlockState(pos);
            if (state.isAir()) continue;
            HIDDEN.put(pos.immutable(), state);
            mc.level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 18);
        }
    }
}
