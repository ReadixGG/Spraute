package org.zonarstudio.spraute_engine.registry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import org.jetbrains.annotations.Nullable;

public class CustomGeoBlock extends Block implements EntityBlock {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    private final String modelPath;
    private final String texturePath;
    private final String dropItem;

    public CustomGeoBlock(Properties properties, String modelPath, String texturePath, String dropItem) {
        super(properties);
        this.modelPath = modelPath;
        this.texturePath = texturePath;
        this.dropItem = dropItem;
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
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
