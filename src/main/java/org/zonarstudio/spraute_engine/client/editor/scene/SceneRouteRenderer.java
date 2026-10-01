package org.zonarstudio.spraute_engine.client.editor.scene;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
//? if >=1.20.1 {
import org.joml.Matrix4f;
//?} else {
/*import com.mojang.math.Matrix4f;
*///?}
import org.zonarstudio.spraute_engine.Spraute_engine;
import org.zonarstudio.spraute_engine.scene.SceneNode;
import org.zonarstudio.spraute_engine.scene.SceneNodeType;
import org.zonarstudio.spraute_engine.scene.SceneRouteOps;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Editor world overlay for ROUTE and ROUTE_POINT nodes. */
@Mod.EventBusSubscriber(modid = Spraute_engine.MODID, value = Dist.CLIENT)
public final class SceneRouteRenderer {
    /** Half-width of the camera-facing ribbon (blocks). */
    private static final float LINE_HALF = 0.05f;

    private record Cached(String key, long bucket, List<SceneRouteOps.Segment> segments) {}
    private static final Map<String, Cached> CACHE = new HashMap<>();

    private SceneRouteRenderer() {}

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        if (!ScenePreviewManager.previewsVisible() || !EditorGizmo.overlaysEnabled()) {
            CACHE.clear();
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        var document = SceneEditorSession.document();
        if (document == null) return;

        PoseStack pose = event.getPoseStack();
        var buffers = mc.renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        Vec3 camera = event.getCamera().getPosition();

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder fill = tesselator.getBuilder();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        fill.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        document.walk((route, ignored) -> {
            if (route.type != SceneNodeType.ROUTE || !route.active) return;
            // Snapshot points first: live runtime shares this tree with the server tick.
            List<SceneNode> points = SceneRouteOps.points(route);
            for (SceneNode point : points) {
                var world = document.worldTransform(point.id);
                double x = world == null ? point.transform.px : world.px;
                double y = world == null ? point.transform.py : world.py;
                double z = world == null ? point.transform.pz : world.pz;
                drawBox(pose, lines, new AABB(x - .14, y - .14, z - .14, x + .14, y + .14, z + .14),
                        camera, .25f, 1f, .55f, 1f);
                if (point.routeHasLook) {
                    float yaw = (float) Math.toRadians(world == null ? point.transform.ry : world.ry);
                    float pitch = (float) Math.toRadians(world == null ? point.transform.rx : world.rx);
                    double dx = -Math.sin(yaw) * Math.cos(pitch);
                    double dy = -Math.sin(pitch);
                    double dz = Math.cos(yaw) * Math.cos(pitch);
                    Vec3 from = new Vec3(x, y, z);
                    Vec3 to = from.add(dx * 0.85, dy * 0.85, dz * 0.85);
                    drawThickLine(pose, fill, lines, from, to, camera, 1f, .85f, .2f, 0.95f);
                }
            }
            for (SceneRouteOps.Segment segment : segments(document, route, mc)) {
                List<Vec3> segPoints = segment.points();
                float r = segment.connected() ? .15f : 1f;
                float g = segment.connected() ? .78f : .12f;
                float b = segment.connected() ? 1f : .12f;
                for (int i = 0; i + 1 < segPoints.size(); i++) {
                    drawThickLine(pose, fill, lines, segPoints.get(i), segPoints.get(i + 1), camera, r, g, b, 0.9f);
                }
            }
        });

        tesselator.end();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        buffers.endBatch(RenderType.lines());
    }

    private static List<SceneRouteOps.Segment> segments(
            org.zonarstudio.spraute_engine.scene.SceneDocument document, SceneNode route, Minecraft mc) {
        StringBuilder key = new StringBuilder(route.routeMode).append('|').append(route.routeClosed)
                .append('|').append(route.routeCurve).append('|').append(route.routeSmoothness);
        for (SceneNode point : SceneRouteOps.points(route)) {
            var world = document.worldTransform(point.id);
            key.append('|').append(world == null ? point.transform.px : world.px).append(',')
                    .append(world == null ? point.transform.py : world.py).append(',')
                    .append(world == null ? point.transform.pz : world.pz);
        }
        long bucket = mc.level.getGameTime() / 40L;
        Cached cached = CACHE.get(route.id);
        if (cached != null && cached.bucket == bucket && cached.key.equals(key.toString())) {
            return cached.segments;
        }
        List<SceneRouteOps.Segment> computed = SceneRouteOps.segments(document, route);
        CACHE.put(route.id, new Cached(key.toString(), bucket, computed));
        return computed;
    }

    /**
     * Camera-facing ribbon (real thickness) + a properly-normalled hairline so the
     * path stays visible from any angle — vanilla lines with a fixed Y normal vanish
     * when the segment is nearly vertical or edge-on.
     */
    private static void drawThickLine(PoseStack pose, BufferBuilder fill, VertexConsumer lines,
                                      Vec3 a, Vec3 b, Vec3 camera,
                                      float r, float g, float blue, float alpha) {
        Vec3 along = b.subtract(a);
        if (along.lengthSqr() < 1e-10) return;
        Vec3 mid = new Vec3((a.x + b.x) * 0.5, (a.y + b.y) * 0.5, (a.z + b.z) * 0.5);
        Vec3 toCam = camera.subtract(mid);
        Vec3 side = along.cross(toCam);
        if (side.lengthSqr() < 1e-8) {
            side = along.cross(Math.abs(along.y) < 0.9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0));
        }
        side = side.normalize().scale(LINE_HALF);
        Vec3 a0 = a.add(side);
        Vec3 a1 = a.subtract(side);
        Vec3 b0 = b.add(side);
        Vec3 b1 = b.subtract(side);

        pose.pushPose();
        pose.translate(-camera.x, -camera.y, -camera.z);
        //? if >=1.20.1 {
        Matrix4f matrix = pose.last().pose();
        fill.vertex(matrix, (float) a0.x, (float) a0.y, (float) a0.z).color(r, g, blue, alpha).endVertex();
        fill.vertex(matrix, (float) a1.x, (float) a1.y, (float) a1.z).color(r, g, blue, alpha).endVertex();
        fill.vertex(matrix, (float) b1.x, (float) b1.y, (float) b1.z).color(r, g, blue, alpha).endVertex();
        fill.vertex(matrix, (float) b0.x, (float) b0.y, (float) b0.z).color(r, g, blue, alpha).endVertex();
        Vec3 n = side.normalize();
        float nx = (float) n.x, ny = (float) n.y, nz = (float) n.z;
        lines.vertex(matrix, (float) a.x, (float) a.y, (float) a.z).color(r, g, blue, 1f).normal(nx, ny, nz).endVertex();
        lines.vertex(matrix, (float) b.x, (float) b.y, (float) b.z).color(r, g, blue, 1f).normal(nx, ny, nz).endVertex();
        //?} else {
        /*com.mojang.math.Matrix4f matrix = pose.last().pose();
        fill.vertex(matrix, (float) a0.x, (float) a0.y, (float) a0.z).color(r, g, blue, alpha).endVertex();
        fill.vertex(matrix, (float) a1.x, (float) a1.y, (float) a1.z).color(r, g, blue, alpha).endVertex();
        fill.vertex(matrix, (float) b1.x, (float) b1.y, (float) b1.z).color(r, g, blue, alpha).endVertex();
        fill.vertex(matrix, (float) b0.x, (float) b0.y, (float) b0.z).color(r, g, blue, alpha).endVertex();
        Vec3 n = side.normalize();
        float nx = (float) n.x, ny = (float) n.y, nz = (float) n.z;
        lines.vertex(matrix, (float) a.x, (float) a.y, (float) a.z).color(r, g, blue, 1f)
                .normal(pose.last().normal(), nx, ny, nz).endVertex();
        lines.vertex(matrix, (float) b.x, (float) b.y, (float) b.z).color(r, g, blue, 1f)
                .normal(pose.last().normal(), nx, ny, nz).endVertex();
        *///?}
        pose.popPose();
    }

    private static void drawBox(PoseStack pose, VertexConsumer consumer, AABB box, Vec3 camera,
                                float r, float g, float b, float a) {
        pose.pushPose();
        pose.translate(-camera.x, -camera.y, -camera.z);
        LevelRenderer.renderLineBox(pose, consumer, box, r, g, b, a);
        pose.popPose();
    }
}
