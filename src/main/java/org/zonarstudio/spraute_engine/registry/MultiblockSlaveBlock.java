package org.zonarstudio.spraute_engine.registry;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** Invisible helper cell for multi-block {@link CustomGeoBlock} footprints. */
public class MultiblockSlaveBlock extends Block implements EntityBlock {

    public MultiblockSlaveBlock(Properties properties) {
        super(properties);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        //? if >=1.20.1 {
        return RenderShape.INVISIBLE;
        //?} else {
        /*return RenderShape.MODEL;
        *///?}
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MultiblockSlaveBlockEntity(pos, state);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!isMoving && !level.isClientSide && level instanceof ServerLevel serverLevel) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof MultiblockSlaveBlockEntity slave) {
                BlockPos master = slave.getMaster();
                if (master != null && !master.equals(BlockPos.ZERO)) {
                    MultiblockHelper.breakStructure(serverLevel, master);
                }
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }
}
