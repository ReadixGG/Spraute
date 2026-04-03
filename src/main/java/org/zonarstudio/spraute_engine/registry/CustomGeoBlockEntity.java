package org.zonarstudio.spraute_engine.registry;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class CustomGeoBlockEntity extends BlockEntity {

    public CustomGeoBlockEntity(BlockPos pos, BlockState state) {
        super(CustomBlockRegistry.CUSTOM_GEO_BLOCK_ENTITY, pos, state);
    }
}
