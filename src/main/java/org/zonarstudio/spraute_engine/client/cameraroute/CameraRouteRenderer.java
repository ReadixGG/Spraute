package org.zonarstudio.spraute_engine.client.cameraroute;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.AABB;
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
import org.zonarstudio.spraute_engine.cameraroute.CameraRoutePath;
import org.zonarstudio.spraute_engine.cameraroute.CameraRouteWaypoint;
import org.zonarstudio.spraute_engine.item.CameraItem;

import java.util.List;

@Mod.EventBusSubscriber(modid = Spraute_engine.MODID, value = Dist.CLIENT)
public final class CameraRouteRenderer {
    private CameraRouteRenderer() {}

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (!isHoldingCamera(mc)) return;

        List<CameraRouteWaypoint> points = CameraRouteManager.getWaypoints();
        if (points.isEmpty()) return;

        PoseStack pose = event.getPoseStack();
        VertexConsumer lines = mc.renderBuffers().bufferSource().getBuffer(RenderType.lines());
        double camX = event.getCamera().getPosition().x;
        double camY = event.getCamera().getPosition().y;
        double camZ = event.getCamera().getPosition().z;

        for (CameraRouteWaypoint p : points) {
            AABB box = new AABB(p.x() - 0.15, p.y() - 0.15, p.z() - 0.15,
                    p.x() + 0.15, p.y() + 0.15, p.z() + 0.15);
            drawBox(pose, lines, box, camX, camY, camZ, 0.2f, 0.85f, 1f, 1f);
            drawLookVector(pose, lines, p, camX, camY, camZ);
        }

        List<CameraRoutePath.Sample> polyline = CameraRoutePath.buildPreviewPolyline(points, CameraRouteManager.getFlightType());
        for (int i = 0; i < polyline.size() - 1; i++) {
            CameraRoutePath.Sample a = polyline.get(i);
            CameraRoutePath.Sample b = polyline.get(i + 1);
            drawLine(pose, lines, a.x(), a.y(), a.z(), b.x(), b.y(), b.z(), camX, camY, camZ, 1f, 0.85f, 0.1f, 1f);
        }

        mc.renderBuffers().bufferSource().endBatch(RenderType.lines());
    }

    private static void drawLookVector(PoseStack pose, VertexConsumer consumer, CameraRouteWaypoint p,
                                       double camX, double camY, double camZ) {
        double radYaw = Math.toRadians(p.yaw());
        double radPitch = Math.toRadians(p.pitch());
        double dx = -Math.sin(radYaw) * Math.cos(radPitch);
        double dy = -Math.sin(radPitch);
        double dz = Math.cos(radYaw) * Math.cos(radPitch);
        drawLine(pose, consumer, p.x(), p.y(), p.z(),
                p.x() + dx * 1.2, p.y() + dy * 1.2, p.z() + dz * 1.2,
                camX, camY, camZ, 1f, 0.3f, 0.9f, 1f);
    }

    private static void drawLine(PoseStack pose, VertexConsumer consumer,
                                 double x1, double y1, double z1,
                                 double x2, double y2, double z2,
                                 double camX, double camY, double camZ,
                                 float r, float g, float b, float a) {
        pose.pushPose();
        pose.translate(-camX, -camY, -camZ);
        //? if >=1.20.1 {
        Matrix4f matrix = pose.last().pose();
        consumer.vertex(matrix, (float) x1, (float) y1, (float) z1).color(r, g, b, a).normal(0, 1, 0).endVertex();
        consumer.vertex(matrix, (float) x2, (float) y2, (float) z2).color(r, g, b, a).normal(0, 1, 0).endVertex();
        //?} else {
        /*com.mojang.math.Matrix4f matrix = pose.last().pose();
        consumer.vertex(matrix, (float) x1, (float) y1, (float) z1).color(r, g, b, a).normal(pose.last().normal(), 0f, 1f, 0f).endVertex();
        consumer.vertex(matrix, (float) x2, (float) y2, (float) z2).color(r, g, b, a).normal(pose.last().normal(), 0f, 1f, 0f).endVertex();
        *///?}
        pose.popPose();
    }

    private static void drawBox(PoseStack pose, VertexConsumer consumer, AABB box,
                                double camX, double camY, double camZ,
                                float r, float g, float b, float a) {
        pose.pushPose();
        pose.translate(-camX, -camY, -camZ);
        LevelRenderer.renderLineBox(pose, consumer, box, r, g, b, a);
        pose.popPose();
    }

    static boolean isHoldingCamera(Minecraft mc) {
        if (mc.player == null) return false;
        return mc.player.getMainHandItem().getItem() instanceof CameraItem
                || mc.player.getOffhandItem().getItem() instanceof CameraItem;
    }
}
