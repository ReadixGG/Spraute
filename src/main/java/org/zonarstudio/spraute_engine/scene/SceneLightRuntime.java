package org.zonarstudio.spraute_engine.scene;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Places an invisible {@code minecraft:light} block at the node world position
 * so the scene can emit real block light (vanilla levels 0–15, white only).
 */
public final class SceneLightRuntime {
    private BlockPos placedAt;
    private int placedLevel = -1;

    SceneLightRuntime(SceneNode node) {
        // Placement happens on first tick / sync with a ServerLevel.
    }

    BlockPos placedPos() {
        return placedAt;
    }

    void syncFromNode(SceneNode node) {
        // Level/enabled come from the node; placement is applied in tick().
    }

    void tick(SceneActiveState state, ServerLevel level, SceneNode node) {
        if (state == null || level == null || node == null) return;
        boolean on = node.active && node.lightEnabled && node.lightLevel > 0;
        if (!on) {
            clear(level, state);
            return;
        }
        SceneTransform world = state.document.worldTransform(node.id);
        BlockPos target = new BlockPos(Mth.floor(world.px), Mth.floor(world.py), Mth.floor(world.pz));
        int lvl = Mth.clamp(node.lightLevel, 0, 15);
        if (target.equals(placedAt) && lvl == placedLevel && isLightAt(level, target, lvl)) {
            return;
        }
        clear(level, state);
        if (tryPlace(level, target, lvl)) {
            placedAt = target.immutable();
            placedLevel = lvl;
        }
    }

    void clear(ServerLevel level) {
        clear(level, null);
    }

    /**
     * Removes this runtime's claim. The physical {@code minecraft:light} is removed only if
     * no other light runtime in {@code state} still claims the same cell (duplicate/overlap).
     */
    void clear(ServerLevel level, SceneActiveState state) {
        BlockPos pos = placedAt;
        placedAt = null;
        placedLevel = -1;
        if (pos == null) return;
        if (state != null && state.isLightPosClaimed(pos)) return;
        clearLightBlock(level, pos);
    }

    /** True when {@code pos} already has {@code minecraft:light} at {@code lightLevel}. */
    public static boolean isLightAt(ServerLevel level, BlockPos pos, int lightLevel) {
        if (level == null || pos == null) return false;
        BlockState cur = level.getBlockState(pos);
        if (!cur.is(Blocks.LIGHT)) return false;
        return cur.getValue(LightBlock.LEVEL) == Mth.clamp(lightLevel, 0, 15);
    }

    /**
     * Places {@code minecraft:light} if the cell is air / replaceable / already a light.
     * @return true if the light block is now at {@code pos}
     */
    public static boolean tryPlace(ServerLevel level, BlockPos pos, int lightLevel) {
        if (level == null || pos == null) return false;
        BlockState existing = level.getBlockState(pos);
        // Never overwrite solid world geometry — only air / replaceable / existing light.
        //? if >=1.20.1 {
        boolean replaceable = existing.canBeReplaced();
        //?} else {
        /*boolean replaceable = existing.getMaterial().isReplaceable();
        *///?}
        if (!existing.isAir() && !replaceable && !existing.is(Blocks.LIGHT)) {
            return false;
        }
        int lvl = Mth.clamp(lightLevel, 0, 15);
        if (existing.is(Blocks.LIGHT) && existing.getValue(LightBlock.LEVEL) == lvl) {
            return true;
        }
        BlockState state = Blocks.LIGHT.defaultBlockState()
                .setValue(LightBlock.LEVEL, lvl);
        level.setBlock(pos, state, 3);
        return true;
    }

    /** Removes a light block at {@code pos} if one is there (no-op otherwise). */
    public static void clearLightBlock(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null) return;
        BlockState cur = level.getBlockState(pos);
        if (cur.is(Blocks.LIGHT)) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        }
    }
}
