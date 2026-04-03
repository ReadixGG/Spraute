package org.zonarstudio.spraute_engine.registry;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class CustomGeoBlock extends Block implements EntityBlock {

    private final String modelPath;
    private final String texturePath;
    private final String dropItem;

    public CustomGeoBlock(Properties properties, String modelPath, String texturePath, String dropItem) {
        super(properties);
        this.modelPath = modelPath;
        this.texturePath = texturePath;
        this.dropItem = dropItem;
    }

    public String getDropItem() {
        return dropItem;
    }

    public String getModelPath() {
        return modelPath;
    }

    public String getTexturePath() {
        return texturePath;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        // If it has a model, we don't render the default block shape, we render the BlockEntity
        return (modelPath != null && !modelPath.isEmpty()) ? RenderShape.ENTITYBLOCK_ANIMATED : RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        if (modelPath == null || modelPath.isEmpty()) return null;
        return new CustomGeoBlockEntity(pos, state);
    }
}
