package org.zonarstudio.spraute_engine.registry;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class MultiblockSlaveBlockEntity extends BlockEntity {

    private BlockPos master = BlockPos.ZERO;

    public MultiblockSlaveBlockEntity(BlockPos pos, BlockState state) {
        super(CustomBlockRegistry.MULTIBLOCK_SLAVE_BLOCK_ENTITY, pos, state);
    }

    public BlockPos getMaster() {
        return master;
    }

    public void setMaster(BlockPos master) {
        this.master = master == null ? BlockPos.ZERO : master.immutable();
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putLong("Master", master.asLong());
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        master = tag.contains("Master") ? BlockPos.of(tag.getLong("Master")) : BlockPos.ZERO;
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
