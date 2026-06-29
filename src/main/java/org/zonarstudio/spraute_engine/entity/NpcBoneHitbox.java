package org.zonarstudio.spraute_engine.entity;

import net.minecraft.nbt.CompoundTag;

/**
 * Axis-aligned hitbox attached to a model bone (pivot follows animation on server).
 * Sizes are in Minecraft blocks; optional offsets are in model units (1/16 block per unit).
 */
public final class NpcBoneHitbox {
    public final String id;
    public final String boneName;
    public final float width;
    public final float height;
    public final float depth;
    public final float offsetX;
    public final float offsetY;
    public final float offsetZ;

    public NpcBoneHitbox(String id, String boneName, float width, float height, float depth,
                         float offsetX, float offsetY, float offsetZ) {
        this.id = id != null && !id.isEmpty() ? id : boneName;
        this.boneName = boneName;
        this.width = Math.max(0.05f, width);
        this.height = Math.max(0.05f, height);
        this.depth = Math.max(0.05f, depth);
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.offsetZ = offsetZ;
    }

    public NpcBoneHitbox(String boneName, float width, float height, float depth) {
        this(boneName, boneName, width, height, depth, 0f, 0f, 0f);
    }

    public CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", id);
        tag.putString("bone", boneName);
        tag.putFloat("w", width);
        tag.putFloat("h", height);
        tag.putFloat("d", depth);
        tag.putFloat("ox", offsetX);
        tag.putFloat("oy", offsetY);
        tag.putFloat("oz", offsetZ);
        return tag;
    }

    public static NpcBoneHitbox fromNbt(CompoundTag tag) {
        return new NpcBoneHitbox(
                tag.getString("id"),
                tag.getString("bone"),
                tag.getFloat("w"),
                tag.getFloat("h"),
                tag.getFloat("d"),
                tag.getFloat("ox"),
                tag.getFloat("oy"),
                tag.getFloat("oz")
        );
    }
}
