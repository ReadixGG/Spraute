package org.zonarstudio.spraute_engine.registry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Footprint placement / break for multi-cell {@link CustomGeoBlock}s. */
public final class MultiblockHelper {

    private static final Set<BlockPos> BREAKING = ConcurrentHashMap.newKeySet();

    private MultiblockHelper() {}

    public static BlockPos localToWorldOffset(int lx, int ly, int lz, Direction facing) {
        return switch (facing) {
            case SOUTH -> new BlockPos(-lx, ly, -lz);
            case EAST -> new BlockPos(-lz, ly, lx);
            case WEST -> new BlockPos(lz, ly, -lx);
            default -> new BlockPos(lx, ly, lz);
        };
    }

    public static List<BlockPos> footprint(BlockPos origin, Direction facing, int sizeW, int sizeD, int sizeH) {
        List<BlockPos> out = new ArrayList<>(Math.max(1, sizeW * sizeD * sizeH));
        for (int ly = 0; ly < sizeH; ly++) {
            for (int lz = 0; lz < sizeD; lz++) {
                for (int lx = 0; lx < sizeW; lx++) {
                    out.add(origin.offset(localToWorldOffset(lx, ly, lz, facing)));
                }
            }
        }
        return out;
    }

    public static boolean canPlaceFootprint(Level level, BlockPos origin, Direction facing,
                                            int sizeW, int sizeD, int sizeH) {
        if (sizeW <= 1 && sizeD <= 1 && sizeH <= 1) return true;
        for (BlockPos pos : footprint(origin, facing, sizeW, sizeD, sizeH)) {
            BlockState existing = level.getBlockState(pos);
            if (!canReplaceForPlacement(existing)) return false;
        }
        return true;
    }

    private static boolean canReplaceForPlacement(BlockState state) {
        //? if >=1.20.1 {
        return state.canBeReplaced();
        //?} else {
        /*return state.isAir() || state.getMaterial().isReplaceable();
        *///?}
    }

    public static void placeFootprint(ServerLevel level, BlockPos origin, Direction facing,
                                      BlockState masterState, int sizeW, int sizeD, int sizeH) {
        if (sizeW <= 1 && sizeD <= 1 && sizeH <= 1) return;
        Block slave = CustomBlockRegistry.MULTIBLOCK_SLAVE_BLOCK;
        if (slave == null) return;

        List<BlockPos> parts = new ArrayList<>();
        for (BlockPos pos : footprint(origin, facing, sizeW, sizeD, sizeH)) {
            if (pos.equals(origin)) continue;
            level.setBlock(pos, slave.defaultBlockState(), Block.UPDATE_ALL);
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof MultiblockSlaveBlockEntity slaveBe) {
                slaveBe.setMaster(origin);
                slaveBe.setChanged();
            }
            parts.add(pos.immutable());
        }

        BlockEntity masterBe = level.getBlockEntity(origin);
        if (masterBe instanceof CustomGeoBlockEntity geo) {
            geo.multiblockParts.clear();
            geo.multiblockParts.addAll(parts);
            geo.setChanged();
        }
    }

    public static void breakStructure(ServerLevel level, BlockPos masterPos) {
        if (!BREAKING.add(masterPos)) return;
        try {
            List<BlockPos> toClear = new ArrayList<>();
            BlockEntity be = level.getBlockEntity(masterPos);
            if (be instanceof CustomGeoBlockEntity geo && !geo.multiblockParts.isEmpty()) {
                toClear.addAll(geo.multiblockParts);
            }
            toClear.add(masterPos.immutable());
            for (BlockPos pos : toClear) {
                BREAKING.add(pos);
            }
            for (BlockPos pos : toClear) {
                if (!level.getBlockState(pos).isAir()) {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                }
                BREAKING.remove(pos);
            }
        } finally {
            BREAKING.remove(masterPos);
        }
    }

    public static void clearSlavesOnly(ServerLevel level, BlockPos masterPos) {
        BlockEntity be = level.getBlockEntity(masterPos);
        if (!(be instanceof CustomGeoBlockEntity geo) || geo.multiblockParts.isEmpty()) return;
        for (BlockPos part : new ArrayList<>(geo.multiblockParts)) {
            if (!BREAKING.add(part)) continue;
            try {
                if (!level.getBlockState(part).isAir()) {
                    level.setBlock(part, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                }
            } finally {
                BREAKING.remove(part);
            }
        }
        geo.multiblockParts.clear();
        geo.setChanged();
    }

    public static BlockPos resolveMaster(Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof MultiblockSlaveBlockEntity slave) {
            return slave.getMaster();
        }
        if (be instanceof CustomGeoBlockEntity) {
            return pos;
        }
        return null;
    }

    public static VoxelShape fullCube() {
        return Shapes.block();
    }
}
