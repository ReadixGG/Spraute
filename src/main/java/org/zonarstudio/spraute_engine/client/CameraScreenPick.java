package org.zonarstudio.spraute_engine.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.zonarstudio.spraute_engine.scene.PlayerCameraPose;

/**
 * Replaces vanilla {@code GameRenderer.pick}: the crosshair ray follows the
 * camera (screen centre), not the player's body look.
 */
public final class CameraScreenPick {
    private CameraScreenPick() {}

    public static boolean apply(Minecraft mc, float partialTick) {
        if (mc.player == null || mc.level == null || mc.gameMode == null) return false;
        if (!CameraHandler.screenRaycastActive()) return false;
        Entity cameraEntity = mc.getCameraEntity() != null ? mc.getCameraEntity() : mc.player;
        PlayerCameraPose.World aim = CameraHandler.aimView(cameraEntity, partialTick);
        if (aim == null) return false;

        Vec3 cam = new Vec3(aim.x(), aim.y(), aim.z());
        Vec3 dir = Vec3.directionFromRotation(aim.pitch(), aim.yaw());
        if (dir.lengthSqr() < 1e-8) return false;
        Vec3 eyes = cameraEntity.getEyePosition(partialTick);
        double skip = Math.max(0d, cam.distanceTo(eyes) - 0.2);
        Vec3 start = cam.add(dir.scale(skip));

        double blockReach;
        double entityReach;
        //? if >=1.20.1 {
        blockReach = mc.player.getBlockReach();
        entityReach = mc.player.getEntityReach();
        //?} else {
        /*blockReach = mc.gameMode.getPickRange();
        entityReach = blockReach;
        *///?}
        boolean far = mc.gameMode.hasFarPickRange();
        if (far) {
            blockReach = Math.max(blockReach, 6.0);
            entityReach = Math.max(entityReach, 6.0);
        }

        Vec3 blockEnd = start.add(dir.scale(blockReach));
        HitResult blockHit = mc.level.clip(new ClipContext(
                start, blockEnd, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, cameraEntity));
        double blockDistSq = blockHit.getType() != HitResult.Type.MISS
                ? start.distanceToSqr(blockHit.getLocation())
                : blockReach * blockReach;

        double entityLimit = Math.max(entityReach, Math.sqrt(blockDistSq));
        AABB sweep = new AABB(start, start.add(dir.scale(entityLimit))).inflate(1.0);
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(
                cameraEntity, start, start.add(dir.scale(entityLimit)), sweep,
                e -> !e.isSpectator() && e.isPickable() && e != cameraEntity,
                entityReach * entityReach);

        mc.crosshairPickEntity = null;
        if (entityHit != null) {
            double entityDistSq = start.distanceToSqr(entityHit.getLocation());
            boolean tooFar = entityDistSq > entityReach * entityReach;
            if (tooFar && entityReach > 3.0 && entityDistSq > 9.0) {
                Vec3 at = entityHit.getLocation();
                mc.hitResult = BlockHitResult.miss(at, Direction.getNearest(dir.x, dir.y, dir.z), containing(at));
            } else if (entityDistSq < blockDistSq || blockHit.getType() == HitResult.Type.MISS) {
                mc.hitResult = entityHit;
                Entity hit = entityHit.getEntity();
                if (hit instanceof LivingEntity || hit instanceof ItemFrame) {
                    mc.crosshairPickEntity = hit;
                }
            } else {
                mc.hitResult = blockHit;
            }
        } else {
            mc.hitResult = blockHit;
        }
        return true;
    }

    private static BlockPos containing(Vec3 at) {
        //? if >=1.20.1 {
        return BlockPos.containing(at);
        //?} else {
        /*return new BlockPos(at);
        *///?}
    }
}
