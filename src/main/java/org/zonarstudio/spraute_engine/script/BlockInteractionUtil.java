package org.zonarstudio.spraute_engine.script;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;

/**
 * Helpers for chest/door script events.
 */
public final class BlockInteractionUtil {
    private BlockInteractionUtil() {}

    public static boolean isChestLike(Block block) {
        return block instanceof ChestBlock
                || block instanceof BarrelBlock
                || block instanceof ShulkerBoxBlock;
    }

    public static boolean isDoorLike(Block block) {
        return block instanceof DoorBlock
                || block instanceof TrapDoorBlock
                || block instanceof FenceGateBlock;
    }

    /** Block entity position for chest/barrel (handles double chest). */
    public static BlockPos resolveContainerPos(Level level, BlockPos pos, BlockState state) {
        if (state.getBlock() instanceof ChestBlock) {
            ChestType type = state.getValue(ChestBlock.TYPE);
            if (type != ChestType.SINGLE) {
                Direction facing = state.getValue(ChestBlock.FACING);
                BlockPos other = pos.relative(type == ChestType.LEFT ? facing.getClockWise() : facing.getCounterClockWise());
                BlockEntity be = level.getBlockEntity(other);
                if (be instanceof ChestBlockEntity) {
                    return other;
                }
            }
        }
        return pos;
    }

    public static String lootKey(String dimensionId, BlockPos pos) {
        return "chest_loot_" + dimensionId.replace(':', '_') + "_" + pos.getX() + "_" + pos.getY() + "_" + pos.getZ();
    }

    public static String lootAppliedKey(String dimensionId, BlockPos pos) {
        return "chest_loot_applied_" + dimensionId.replace(':', '_') + "_" + pos.getX() + "_" + pos.getY() + "_" + pos.getZ();
    }
}
