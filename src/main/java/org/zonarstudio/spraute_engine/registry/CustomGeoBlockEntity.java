package org.zonarstudio.spraute_engine.registry;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;

public class CustomGeoBlockEntity extends BlockEntity {

    public static class BlockDisplay {
        public int displayType; // 0 = item, 1 = model, 2 = block
        public String itemOrModel;
        public String texture;
        public float ox, oy, oz;
        public float rx, ry, rz;
        public float scale;

        public BlockDisplay(int displayType, String itemOrModel, String texture, float ox, float oy, float oz, float rx, float ry, float rz, float scale) {
            this.displayType = displayType;
            this.itemOrModel = itemOrModel;
            this.texture = texture;
            this.ox = ox; this.oy = oy; this.oz = oz;
            this.rx = rx; this.ry = ry; this.rz = rz;
            this.scale = scale;
        }
    }

    public final Map<String, BlockDisplay> displays = new HashMap<>();

    public final SimpleContainer inventory = new SimpleContainer(54) {
        @Override
        public void setChanged() {
            super.setChanged();
            CustomGeoBlockEntity.this.setChanged();
        }
    };

    public CustomGeoBlockEntity(BlockPos pos, BlockState state) {
        super(CustomBlockRegistry.CUSTOM_GEO_BLOCK_ENTITY, pos, state);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("Inventory", inventory.createTag());
        
        ListTag displaysList = new ListTag();
        for (Map.Entry<String, BlockDisplay> entry : displays.entrySet()) {
            CompoundTag dt = new CompoundTag();
            dt.putString("Id", entry.getKey());
            dt.putInt("DisplayType", entry.getValue().displayType);
            dt.putString("ItemOrModel", entry.getValue().itemOrModel);
            dt.putString("Texture", entry.getValue().texture == null ? "" : entry.getValue().texture);
            dt.putFloat("ox", entry.getValue().ox);
            dt.putFloat("oy", entry.getValue().oy);
            dt.putFloat("oz", entry.getValue().oz);
            dt.putFloat("rx", entry.getValue().rx);
            dt.putFloat("ry", entry.getValue().ry);
            dt.putFloat("rz", entry.getValue().rz);
            dt.putFloat("sc", entry.getValue().scale);
            displaysList.add(dt);
        }
        tag.put("Displays", displaysList);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("Inventory")) {
            inventory.fromTag(tag.getList("Inventory", Tag.TAG_COMPOUND));
        }
        
        displays.clear();
        if (tag.contains("Displays", Tag.TAG_LIST)) {
            ListTag list = tag.getList("Displays", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag dt = list.getCompound(i);
                int displayType = 0;
                if (dt.contains("DisplayType")) displayType = dt.getInt("DisplayType");
                else if (dt.contains("IsModel") && dt.getBoolean("IsModel")) displayType = 1;
                
                String itemOrModel = dt.contains("ItemOrModel") ? dt.getString("ItemOrModel") : dt.getString("Item");
                String tex = dt.getString("Texture");
                displays.put(dt.getString("Id"), new BlockDisplay(
                    displayType, itemOrModel, tex,
                    dt.getFloat("ox"), dt.getFloat("oy"), dt.getFloat("oz"),
                    dt.getFloat("rx"), dt.getFloat("ry"), dt.getFloat("rz"),
                    dt.getFloat("sc")
                ));
            }
        }
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
