package org.zonarstudio.spraute_engine.ui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import org.zonarstudio.spraute_engine.registry.CustomMenuRegistry;

public class SprauteContainerMenu extends AbstractContainerMenu {
    /** Fallback when percent extents are resolved without a real GUI size (server). */
    public static final int REF_W = 427;
    public static final int REF_H = 240;

    public final String json;
    private final SimpleContainer customContainer;
    private final boolean dropOnClose;
    /** Widget id каждого кастомного слота в порядке добавления (для uiSlotItem/uiSetSlot по id). */
    public final java.util.List<String> slotWidgetIds = new java.util.ArrayList<>();

    public SprauteContainerMenu(int id, Inventory playerInv, String json) {
        this(id, playerInv, json, null, null);
    }

    public SprauteContainerMenu(int id, Inventory playerInv, String json, SimpleContainer existingContainer) {
        this(id, playerInv, json, existingContainer, null);
    }

    /**
     * @param panelSize optional [w,h] for resolving percent slot coords; null → REF or root size[].
     */
    public SprauteContainerMenu(int id, Inventory playerInv, String json,
                                SimpleContainer existingContainer, int[] panelSize) {
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

        int[] panel = panelSize != null && panelSize.length >= 2
                ? new int[]{Math.max(1, panelSize[0]), Math.max(1, panelSize[1])}
                : resolvePanelSize(root);
        if (root != null) {
            JsonArray children = root.getAsJsonArray("children");
            int currentCustomSlotIndex = 0;
            if (children != null) {
                for (JsonElement el : children) {
                    JsonObject child = el.getAsJsonObject();
                    String type = child.get("type").getAsString();
                    if ("slot".equals(type)) {
                        int x = readCoord(child, "x", panel[0]);
                        int y = readCoord(child, "y", panel[1]);
                        if (currentCustomSlotIndex < this.customContainer.getContainerSize()) {
                            String wid = child.has("id") ? child.get("id").getAsString() : ("slot_" + currentCustomSlotIndex);
                            this.slotWidgetIds.add(wid);
                            this.addSlot(new Slot(this.customContainer, currentCustomSlotIndex++, x, y));
                        }
                    } else if ("playerInventory".equals(type) || "player_inventory".equals(type)) {
                        int startX = readCoord(child, "x", panel[0], 8);
                        int startY = readCoord(child, "y", panel[1], 84);
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

    /** Resolve panel size from JSON + optional screen size (client factory). */
    public static int[] panelSizeFor(String json, int screenW, int screenH) {
        JsonObject root = null;
        try {
            root = JsonParser.parseString(json).getAsJsonObject();
        } catch (Exception ignored) {}
        int sw = Math.max(1, screenW);
        int sh = Math.max(1, screenH);
        if (root == null) return new int[]{sw, sh};
        int pw = readRootExtent(root, "w", sw, sw);
        int ph = readRootExtent(root, "h", sh, sh);
        if (!root.has("w") && root.has("size") && root.get("size").isJsonArray()) {
            JsonArray size = root.getAsJsonArray("size");
            if (size.size() > 0) pw = Math.max(1, size.get(0).getAsInt());
            if (size.size() > 1) ph = Math.max(1, size.get(1).getAsInt());
        }
        return new int[]{pw, ph};
    }

    private static int[] resolvePanelSize(JsonObject root) {
        return panelSizeFor(root == null ? "{}" : root.toString(), REF_W, REF_H);
    }

    private static int readRootExtent(JsonObject root, String key, int screenDim, int def) {
        if (!root.has(key)) return def;
        JsonElement el = root.get(key);
        if (el == null || !el.isJsonPrimitive()) return def;
        JsonPrimitive p = el.getAsJsonPrimitive();
        if (p.isNumber()) return p.getAsInt();
        if (p.isString()) {
            String s = p.getAsString().trim();
            if (s.endsWith("%")) {
                try {
                    float pct = Float.parseFloat(s.substring(0, s.length() - 1).trim());
                    return Math.round(pct / 100f * screenDim);
                } catch (NumberFormatException e) {
                    return def;
                }
            }
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException e) {
                return def;
            }
        }
        return def;
    }

    private static int readCoord(JsonObject w, String key, int panelSize) {
        return readCoord(w, key, panelSize, 0);
    }

    private static int readCoord(JsonObject w, String key, int panelSize, int def) {
        if (w.has("pos") && w.get("pos").isJsonArray()) {
            JsonArray pos = w.getAsJsonArray("pos");
            int idx = "y".equals(key) ? 1 : 0;
            if (pos.size() > idx) {
                return parseCoordElement(pos.get(idx), panelSize, def);
            }
        }
        if (!w.has(key)) return def;
        return parseCoordElement(w.get(key), panelSize, def);
    }

    private static int parseCoordElement(JsonElement el, int panelSize, int def) {
        if (el == null || !el.isJsonPrimitive()) return def;
        JsonPrimitive p = el.getAsJsonPrimitive();
        if (p.isNumber()) return p.getAsInt();
        if (p.isString()) {
            String s = p.getAsString().trim();
            if (s.endsWith("%")) {
                try {
                    float pct = Float.parseFloat(s.substring(0, s.length() - 1).trim());
                    return Math.round(pct / 100f * panelSize);
                } catch (NumberFormatException e) {
                    return def;
                }
            }
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException e) {
                return def;
            }
        }
        return def;
    }

    public SimpleContainer getCustomContainer() {
        return customContainer;
    }

    /** Индекс кастомного слота по widget id, -1 если не найден. */
    public int slotIndexOf(String widgetId) {
        return slotWidgetIds.indexOf(widgetId);
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
