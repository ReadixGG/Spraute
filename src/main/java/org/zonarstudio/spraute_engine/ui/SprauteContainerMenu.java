package org.zonarstudio.spraute_engine.ui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import org.zonarstudio.spraute_engine.registry.CustomMenuRegistry;

public class SprauteContainerMenu extends AbstractContainerMenu {
    public final String json;
    private final SimpleContainer customContainer;
    private final boolean dropOnClose;

    public SprauteContainerMenu(int id, Inventory playerInv, String json) {
        this(id, playerInv, json, null);
    }

    public SprauteContainerMenu(int id, Inventory playerInv, String json, SimpleContainer existingContainer) {
        super(CustomMenuRegistry.SPRAUTE_CONTAINER, id);
        this.json = json;
        this.dropOnClose = (existingContainer == null);
        
        int customSlotCount = 0;
        JsonObject root = null;
        try {
            root = JsonParser.parseString(json).getAsJsonObject();
            JsonArray children = root.getAsJsonArray("children");
            if (children != null) {
                for (JsonElement el : children) {
                    JsonObject child = el.getAsJsonObject();
                    if ("slot".equals(child.get("type").getAsString())) {
                        customSlotCount++;
                    }
                }
            }
        } catch (Exception e) {}
        
        if (existingContainer != null) {
            this.customContainer = existingContainer;
        } else {
            this.customContainer = new SimpleContainer(customSlotCount);
        }
        
        if (root != null) {
            JsonArray children = root.getAsJsonArray("children");
            int currentCustomSlotIndex = 0;
            if (children != null) {
                for (JsonElement el : children) {
                    JsonObject child = el.getAsJsonObject();
                    String type = child.get("type").getAsString();
                    if ("slot".equals(type)) {
                        int x = child.has("pos") ? child.getAsJsonArray("pos").get(0).getAsInt() : 0;
                        int y = child.has("pos") ? child.getAsJsonArray("pos").get(1).getAsInt() : 0;
                        if (currentCustomSlotIndex < this.customContainer.getContainerSize()) {
                            this.addSlot(new Slot(this.customContainer, currentCustomSlotIndex++, x, y));
                        }
                    } else if ("player_inventory".equals(type)) {
                        int startX = child.has("pos") ? child.getAsJsonArray("pos").get(0).getAsInt() : 8;
                        int startY = child.has("pos") ? child.getAsJsonArray("pos").get(1).getAsInt() : 84;
                        
                        for (int i = 0; i < 3; ++i) {
                            for (int j = 0; j < 9; ++j) {
                                this.addSlot(new Slot(playerInv, j + i * 9 + 9, startX + j * 18, startY + i * 18));
                            }
                        }
                        
                        for (int i = 0; i < 9; ++i) {
                            this.addSlot(new Slot(playerInv, i, startX + i * 18, startY + 58));
                        }
                    }
                }
            }
        }
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (this.dropOnClose && this.customContainer != null) {
            this.clearContainer(player, this.customContainer);
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack itemstack1 = slot.getItem();
            itemstack = itemstack1.copy();
            int customSize = this.customContainer.getContainerSize();
            if (index < customSize) {
                if (!this.moveItemStackTo(itemstack1, customSize, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else if (!this.moveItemStackTo(itemstack1, 0, customSize, false)) {
                return ItemStack.EMPTY;
            }
            if (itemstack1.isEmpty()) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }
        return itemstack;
    }
}