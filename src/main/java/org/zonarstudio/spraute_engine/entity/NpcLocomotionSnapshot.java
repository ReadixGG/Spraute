package org.zonarstudio.spraute_engine.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Walking / route state that is lost when a chunk unloads. Scene NPCs are not
 * written to the world save, so this snapshot lives on {@code SceneActiveState}
 * and is restored after reconcile respawn. Script NPCs persist it in NBT.
 */
public final class NpcLocomotionSnapshot {
    private static final String NBT_KEY = "SprauteLocomotion";

    public final boolean followRouteActive;
    public final List<Vec3> followRoutePoints;
    public final int followRouteIndex;
    public final boolean followRouteLoop;
    public final boolean followRouteFly;
    public final double followRouteSpeed;
    public final Vec3 alwaysMovePoint;
    public final UUID alwaysMoveEntityUuid;
    public final Vec3 activeMoveGoal;
    public final double moveSpeed;
    public final boolean walkFaceDirection;
    public final boolean flying;

    public NpcLocomotionSnapshot(
            boolean followRouteActive, List<Vec3> followRoutePoints, int followRouteIndex,
            boolean followRouteLoop, boolean followRouteFly, double followRouteSpeed,
            Vec3 alwaysMovePoint, UUID alwaysMoveEntityUuid, Vec3 activeMoveGoal,
            double moveSpeed, boolean walkFaceDirection, boolean flying) {
        this.followRouteActive = followRouteActive;
        this.followRoutePoints = followRoutePoints == null || followRoutePoints.isEmpty()
                ? List.of() : List.copyOf(followRoutePoints);
        this.followRouteIndex = followRouteIndex;
        this.followRouteLoop = followRouteLoop;
        this.followRouteFly = followRouteFly;
        this.followRouteSpeed = followRouteSpeed;
        this.alwaysMovePoint = alwaysMovePoint;
        this.alwaysMoveEntityUuid = alwaysMoveEntityUuid;
        this.activeMoveGoal = activeMoveGoal;
        this.moveSpeed = moveSpeed;
        this.walkFaceDirection = walkFaceDirection;
        this.flying = flying;
    }

    public boolean isIdle() {
        return !followRouteActive && alwaysMovePoint == null && alwaysMoveEntityUuid == null && activeMoveGoal == null;
    }

    public void addChunks(Set<ChunkPos> out) {
        if (out == null) return;
        if (activeMoveGoal != null) addChunk(out, activeMoveGoal);
        if (alwaysMovePoint != null) addChunk(out, alwaysMovePoint);
        for (Vec3 point : followRoutePoints) {
            addChunk(out, point);
        }
    }

    public void writeNbt(CompoundTag parent) {
        if (parent == null || isIdle()) return;
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("FollowRoute", followRouteActive);
        tag.putBoolean("Loop", followRouteLoop);
        tag.putBoolean("Fly", followRouteFly);
        tag.putDouble("Speed", followRouteSpeed);
        tag.putInt("Index", followRouteIndex);
        tag.putBoolean("FaceWalk", walkFaceDirection);
        tag.putBoolean("Flying", flying);
        if (!followRoutePoints.isEmpty()) {
            ListTag points = new ListTag();
            for (Vec3 point : followRoutePoints) {
                points.add(vecTag(point));
            }
            tag.put("Points", points);
        }
        if (alwaysMovePoint != null) tag.put("AlwaysPoint", vecTag(alwaysMovePoint));
        if (alwaysMoveEntityUuid != null) tag.putUUID("AlwaysEntity", alwaysMoveEntityUuid);
        if (activeMoveGoal != null) tag.put("MoveGoal", vecTag(activeMoveGoal));
        parent.put(NBT_KEY, tag);
    }

    public static NpcLocomotionSnapshot readNbt(CompoundTag parent) {
        if (parent == null || !parent.contains(NBT_KEY, 10)) return null;
        CompoundTag tag = parent.getCompound(NBT_KEY);
        List<Vec3> points = new ArrayList<>();
        if (tag.contains("Points", 9)) {
            ListTag list = tag.getList("Points", 10);
            for (int i = 0; i < list.size(); i++) {
                Vec3 point = vecFrom(list.getCompound(i));
                if (point != null) points.add(point);
            }
        }
        return new NpcLocomotionSnapshot(
                tag.getBoolean("FollowRoute"),
                points,
                tag.getInt("Index"),
                tag.getBoolean("Loop"),
                tag.getBoolean("Fly"),
                tag.contains("Speed") ? tag.getDouble("Speed") : 1.0,
                tag.contains("AlwaysPoint", 10) ? vecFrom(tag.getCompound("AlwaysPoint")) : null,
                tag.hasUUID("AlwaysEntity") ? tag.getUUID("AlwaysEntity") : null,
                tag.contains("MoveGoal", 10) ? vecFrom(tag.getCompound("MoveGoal")) : null,
                tag.contains("Speed") ? tag.getDouble("Speed") : 1.0,
                tag.contains("FaceWalk") ? tag.getBoolean("FaceWalk") : true,
                tag.getBoolean("Flying"));
    }

    private static void addChunk(Set<ChunkPos> out, Vec3 point) {
        if (point == null) return;
        out.add(new ChunkPos(new BlockPos(Mth.floor(point.x), Mth.floor(point.y), Mth.floor(point.z))));
    }

    private static CompoundTag vecTag(Vec3 point) {
        CompoundTag tag = new CompoundTag();
        tag.putDouble("x", point.x);
        tag.putDouble("y", point.y);
        tag.putDouble("z", point.z);
        return tag;
    }

    private static Vec3 vecFrom(CompoundTag tag) {
        if (tag == null) return null;
        return new Vec3(tag.getDouble("x"), tag.getDouble("y"), tag.getDouble("z"));
    }
}
