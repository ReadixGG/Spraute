package org.zonarstudio.spraute_engine.client;

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
import org.zonarstudio.spraute_engine.Spraute_engine;
import org.zonarstudio.spraute_engine.entity.NpcBoneHitbox;
import org.zonarstudio.spraute_engine.entity.NpcHitboxUtil;
import org.zonarstudio.spraute_engine.entity.SprauteNpcEntity;
import org.zonarstudio.spraute_engine.entity.client.SprauteNpcRenderer;

@Mod.EventBusSubscriber(modid = Spraute_engine.MODID, value = Dist.CLIENT)
public final class NpcHitboxDebugRenderer {
    private NpcHitboxDebugRenderer() {}

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        //? if >=1.20.1 {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        //?} else {
        /*if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        *///?}
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        PoseStack pose = event.getPoseStack();
        VertexConsumer lines = mc.renderBuffers().bufferSource().getBuffer(RenderType.lines());
        double camX = event.getCamera().getPosition().x;
        double camY = event.getCamera().getPosition().y;
        double camZ = event.getCamera().getPosition().z;

        for (var entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof SprauteNpcEntity npc) || !npc.isShowHitboxDebug()) continue;

            AABB main = NpcHitboxUtil.mainHitbox(npc,
                    npc.getHitboxWidth(), npc.getHitboxHeight(),
                    npc.getHitboxOffsetX(), npc.getHitboxOffsetY(), npc.getHitboxOffsetZ());
            drawBox(pose, lines, main, camX, camY, camZ, 0f, 1f, 0f, 1f);

            if (npc.getBoneHitboxes().isEmpty()) continue;
            var renderer = mc.getEntityRenderDispatcher().getRenderer(npc);
            if (!(renderer instanceof SprauteNpcRenderer npcRenderer)) continue;

            for (NpcBoneHitbox hb : npc.getBoneHitboxes()) {
                var pos = npcRenderer.getBoneWorldPosition(npc, hb.boneName, event.getPartialTick());
                if (pos == null) continue;
                double hw = hb.width * 0.5;
                double hh = hb.height * 0.5;
                double hd = hb.depth * 0.5;
                double[] rot = rotateOffset(hb.offsetX / 16.0, hb.offsetZ / 16.0, npc.yBodyRot);
                AABB box = new AABB(
                        pos.x + rot[0] - hw, pos.y + hb.offsetY / 16.0 - hh, pos.z + rot[1] - hd,
                        pos.x + rot[0] + hw, pos.y + hb.offsetY / 16.0 + hh, pos.z + rot[1] + hd);
                drawBox(pose, lines, box, camX, camY, camZ, 1f, 0.3f, 0.1f, 1f);
            }
        }

        mc.renderBuffers().bufferSource().endBatch(RenderType.lines());
    }

    private static double[] rotateOffset(double lx, double lz, float bodyYaw) {
        float rad = (float) Math.toRadians(180.0 - bodyYaw);
        double cos = Math.cos(rad);
        double sin = Math.sin(rad);
        return new double[]{lx * cos + lz * sin, -lx * sin + lz * cos};
    }

    private static void drawBox(PoseStack pose, VertexConsumer consumer, AABB box,
                                double camX, double camY, double camZ,
                                float r, float g, float b, float a) {
        pose.pushPose();
        pose.translate(-camX, -camY, -camZ);
        LevelRenderer.renderLineBox(pose, consumer, box, r, g, b, a);
        pose.popPose();
    }
}
