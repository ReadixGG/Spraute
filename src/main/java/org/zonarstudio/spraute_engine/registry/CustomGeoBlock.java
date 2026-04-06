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
    private final boolean directional;
    private final net.minecraft.world.phys.shapes.VoxelShape shape;

    public CustomGeoBlock(Properties properties, String modelPath, String texturePath, String dropItem, boolean directional, float[] hitbox) {
        super(properties);
        this.modelPath = modelPath;
        this.texturePath = texturePath;
        this.dropItem = dropItem;
        this.directional = directional;
        
        if (hitbox != null && hitbox.length == 6) {
            this.shape = Block.box(hitbox[0], hitbox[1], hitbox[2], hitbox[3], hitbox[4], hitbox[5]);
        } else {
            this.shape = net.minecraft.world.phys.shapes.Shapes.block();
        }
        
        if (this.directional) {
            this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
        }
    }

    public CustomGeoBlock(Properties properties, String modelPath, String texturePath, String dropItem, boolean directional) {
        this(properties, modelPath, texturePath, dropItem, directional, null);
    }

    public CustomGeoBlock(Properties properties, String modelPath, String texturePath, String dropItem) {
        this(properties, modelPath, texturePath, dropItem, true, null);
    }

    @Override
    public net.minecraft.world.phys.shapes.VoxelShape getShape(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos, net.minecraft.world.phys.shapes.CollisionContext context) {
        if (!directional || this.shape == net.minecraft.world.phys.shapes.Shapes.block()) {
            return this.shape;
        }
        
        Direction dir = state.getValue(FACING);
        return rotateShape(this.shape, dir);
    }

    private net.minecraft.world.phys.shapes.VoxelShape rotateShape(net.minecraft.world.phys.shapes.VoxelShape shape, Direction dir) {
        if (dir == Direction.NORTH) return shape;
        
        net.minecraft.world.phys.AABB box = shape.bounds();
        double minX = box.minX;
        double minY = box.minY;
        double minZ = box.minZ;
        double maxX = box.maxX;
        double maxY = box.maxY;
        double maxZ = box.maxZ;
        
        if (dir == Direction.SOUTH) {
            return Block.box((1 - maxX) * 16, minY * 16, (1 - maxZ) * 16, (1 - minX) * 16, maxY * 16, (1 - minZ) * 16);
        } else if (dir == Direction.WEST) {
            return Block.box(minZ * 16, minY * 16, (1 - maxX) * 16, maxZ * 16, maxY * 16, (1 - minX) * 16);
        } else if (dir == Direction.EAST) {
            return Block.box((1 - maxZ) * 16, minY * 16, minX * 16, (1 - minZ) * 16, maxY * 16, maxX * 16);
        }
        
        return shape;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        // We always add FACING property because block properties cannot be added dynamically per-instance
        // in Minecraft 1.19.2 without creating different Block classes.
        builder.add(FACING);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        if (!directional) {
            return this.defaultBlockState();
        }
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
