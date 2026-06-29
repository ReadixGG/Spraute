package org.zonarstudio.spraute_engine.registry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
//? if <1.20.1 {
/*import net.minecraft.world.level.storage.loot.LootContext;
*///?} else {
import net.minecraft.world.level.storage.loot.LootParams;
//?}
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import org.jetbrains.annotations.Nullable;
import org.zonarstudio.spraute_engine.Spraute_engine;
import org.zonarstudio.spraute_engine.script.ItemStackScriptUtil;

import java.util.ArrayList;
import java.util.List;

public class CustomGeoBlock extends Block implements EntityBlock {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    private final String modelPath;
    private final String texturePath;
    private final String dropItem;
    private final int dropCount;
    private final boolean dropReplace;
    private final List<CustomBlockRegistry.BlockDropRule> dropRules;
    private final boolean directional;
    private final int sizeW;
    private final int sizeD;
    private final int sizeH;
    private final net.minecraft.world.phys.shapes.VoxelShape shape;

    public CustomGeoBlock(Properties properties, String modelPath, String texturePath, String dropItem,
                          int dropCount, boolean dropReplace, List<CustomBlockRegistry.BlockDropRule> dropRules,
                          boolean directional, int sizeW, int sizeD, int sizeH, float[] hitbox) {
        super(properties);
        this.modelPath = modelPath;
        this.texturePath = texturePath;
        this.dropItem = dropItem;
        this.dropCount = Math.max(1, dropCount);
        this.dropReplace = dropReplace;
        this.dropRules = dropRules != null ? List.copyOf(dropRules) : List.of();
        this.directional = directional;
        this.sizeW = Math.max(1, sizeW);
        this.sizeD = Math.max(1, sizeD);
        this.sizeH = Math.max(1, sizeH);

        if (hitbox != null && hitbox.length == 6) {
            this.shape = Block.box(hitbox[0], hitbox[1], hitbox[2], hitbox[3], hitbox[4], hitbox[5]);
        } else if (this.sizeW == 1 && this.sizeD == 1 && this.sizeH == 1) {
            this.shape = net.minecraft.world.phys.shapes.Shapes.block();
        } else {
            this.shape = net.minecraft.world.phys.shapes.Shapes.block();
        }

        if (this.directional) {
            this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
        }
    }

    public CustomGeoBlock(Properties properties, String modelPath, String texturePath, String dropItem,
                          int dropCount, boolean dropReplace, List<CustomBlockRegistry.BlockDropRule> dropRules,
                          boolean directional, float[] hitbox) {
        this(properties, modelPath, texturePath, dropItem, dropCount, dropReplace, dropRules, directional, 1, 1, 1, hitbox);
    }

    public CustomGeoBlock(Properties properties, String modelPath, String texturePath, String dropItem, boolean directional, float[] hitbox) {
        this(properties, modelPath, texturePath, dropItem, 1, false, List.of(), directional, 1, 1, 1, hitbox);
    }

    public CustomGeoBlock(Properties properties, String modelPath, String texturePath, String dropItem, boolean directional) {
        this(properties, modelPath, texturePath, dropItem, 1, false, List.of(), directional, 1, 1, 1, null);
    }

    public CustomGeoBlock(Properties properties, String modelPath, String texturePath, String dropItem) {
        this(properties, modelPath, texturePath, dropItem, 1, false, List.of(), true, 1, 1, 1, null);
    }

    public int getSizeW() { return sizeW; }
    public int getSizeD() { return sizeD; }
    public int getSizeH() { return sizeH; }
    public boolean isMultiblock() { return sizeW > 1 || sizeD > 1 || sizeH > 1; }

    //? if <1.20.1 {
    /*@Override
    public List<ItemStack> getDrops(BlockState state, LootContext.Builder builder) {
        List<ItemStack> custom = computeCustomDrops(extractRandom1_19(state, builder));
        if (custom != null) return custom;
        return super.getDrops(state, builder);
    }

    private static RandomSource extractRandom1_19(BlockState state, LootContext.Builder builder) {
        LootContext ctx = builder.withParameter(LootContextParams.BLOCK_STATE, state)
                .withParameter(LootContextParams.TOOL, ItemStack.EMPTY)
                .create(LootContextParamSets.BLOCK);
        return ctx.getRandom();
    }
    *///?} else {
    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        LootParams lootParams = params.withParameter(LootContextParams.BLOCK_STATE, state)
                .withParameter(LootContextParams.TOOL, ItemStack.EMPTY)
                .create(LootContextParamSets.BLOCK);
        List<ItemStack> custom = computeCustomDrops(lootParams.getLevel().getRandom());
        if (custom != null) return custom;
        return super.getDrops(state, params);
    }
    //?}

    @Nullable
    private List<ItemStack> computeCustomDrops(RandomSource random) {
        if (!dropRules.isEmpty()) {
            List<ItemStack> out = new ArrayList<>();
            for (CustomBlockRegistry.BlockDropRule rule : dropRules) {
                if (random.nextInt(100) >= rule.chance) continue;
                Item item = resolveDropItem(rule.itemId);
                if (item == null) continue;
                int min = Math.max(1, rule.min);
                int max = Math.max(min, rule.max);
                int count = min + (max > min ? random.nextInt(max - min + 1) : 0);
                out.add(new ItemStack(item, count));
            }
            if (!out.isEmpty() || dropReplace) return out;
        }

        if (dropItem != null && !dropItem.isEmpty()) {
            if ("none".equalsIgnoreCase(dropItem) || "air".equalsIgnoreCase(dropItem)) {
                return List.of();
            }
            Item item = resolveDropItem(dropItem);
            if (item != null) {
                return List.of(new ItemStack(item, dropCount));
            }
            if (dropReplace) return List.of();
        }

        if (dropReplace) return List.of();
        return null;
    }

    private static Item resolveDropItem(String id) {
        Item item = ItemStackScriptUtil.resolveItem(id);
        if (item != null) return item;
        if (id != null && !id.contains(":")) {
            return ItemStackScriptUtil.resolveItem(Spraute_engine.MODID + ":" + id);
        }
        return null;
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
        builder.add(FACING);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getOpposite();
        BlockPos origin = context.getClickedPos().relative(context.getClickedFace());
        if (isMultiblock() && !MultiblockHelper.canPlaceFootprint(context.getLevel(), origin, facing, sizeW, sizeD, sizeH)) {
            return null;
        }
        if (!directional) {
            BlockState state = this.defaultBlockState();
            if (state.hasProperty(FACING)) {
                state = state.setValue(FACING, facing);
            }
            return state;
        }
        return this.defaultBlockState().setValue(FACING, facing);
    }

    @Override
    public void onPlace(BlockState state, net.minecraft.world.level.Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        if (!level.isClientSide && isMultiblock() && level instanceof ServerLevel serverLevel) {
            Direction facing = state.hasProperty(FACING) ? state.getValue(FACING) : Direction.NORTH;
            MultiblockHelper.placeFootprint(serverLevel, pos, facing, state, sizeW, sizeD, sizeH);
        }
        super.onPlace(state, level, pos, oldState, isMoving);
    }

    @Override
    public void onRemove(BlockState state, net.minecraft.world.level.Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!isMoving && !level.isClientSide && isMultiblock() && level instanceof ServerLevel serverLevel) {
            MultiblockHelper.clearSlavesOnly(serverLevel, pos);
        }
        super.onRemove(state, level, pos, newState, isMoving);
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
        return (modelPath != null && !modelPath.isEmpty()) ? RenderShape.ENTITYBLOCK_ANIMATED : RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        boolean hasModel = modelPath != null && !modelPath.isEmpty();
        if (hasModel || isMultiblock()) {
            return new CustomGeoBlockEntity(pos, state);
        }
        return null;
    }
}
