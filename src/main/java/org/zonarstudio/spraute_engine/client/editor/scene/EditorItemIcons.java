package org.zonarstudio.spraute_engine.client.editor.scene;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

/**
 * Gives the ImGui editor access to vanilla/mod item icons (particle sprite of
 * the item model on the block atlas) so pickers can show real previews.
 */
public final class EditorItemIcons {
    /** UVs of one sprite on the block/item atlas. */
    public record Icon(int atlasGlId, float u0, float v0, float u1, float v1) {}

    private static List<String> allItems;
    private static List<String> allBlocks;

    private EditorItemIcons() {}

    /** Sorted registry ids of every item ("minecraft:stone", ...). */
    public static List<String> items() {
        if (allItems == null) {
            allItems = new ArrayList<>();
            for (ResourceLocation key : ForgeRegistries.ITEMS.getKeys()) allItems.add(key.toString());
            allItems.sort(String::compareTo);
        }
        return allItems;
    }

    /** Sorted registry ids of every block. */
    public static List<String> blocks() {
        if (allBlocks == null) {
            allBlocks = new ArrayList<>();
            for (ResourceLocation key : ForgeRegistries.BLOCKS.getKeys()) allBlocks.add(key.toString());
            allBlocks.sort(String::compareTo);
        }
        return allBlocks;
    }

    private static List<String> allMobs;

    /** Sorted registry ids of living entity types (spawnable mobs). */
    public static List<String> mobs() {
        if (allMobs == null) {
            allMobs = new ArrayList<>();
            for (var entry : ForgeRegistries.ENTITY_TYPES.getEntries()) {
                // MISC covers non-living things (arrows, item frames, boats...).
                if (entry.getValue().getCategory() == net.minecraft.world.entity.MobCategory.MISC) continue;
                allMobs.add(entry.getKey().location().toString());
            }
            allMobs.sort(String::compareTo);
        }
        return allMobs;
    }

    /** Icon (atlas texture + UVs) for an item registry id, or null. */
    public static Icon icon(String itemId) {
        if (itemId == null || itemId.isBlank()) return null;
        String id = org.zonarstudio.spraute_engine.script.ItemStackScriptUtil.parseSpec(itemId).id();
        if (id.isBlank()) return null;
        if (!id.contains(":")) id = "minecraft:" + id;
        ResourceLocation key = ResourceLocation.tryParse(id);
        if (key == null) return null;
        Item item = ForgeRegistries.ITEMS.getValue(key);
        if (item == null) return null;
        Minecraft mc = Minecraft.getInstance();
        try {
            BakedModel model = mc.getItemRenderer().getItemModelShaper().getItemModel(item);
            if (model == null) return null;
            @SuppressWarnings("deprecation")
            TextureAtlasSprite sprite = model.getParticleIcon();
            if (sprite == null) return null;
            int atlasId = mc.getTextureManager().getTexture(InventoryMenu.BLOCK_ATLAS).getId();
            return new Icon(atlasId, sprite.getU0(), sprite.getV0(), sprite.getU1(), sprite.getV1());
        } catch (Exception e) {
            return null;
        }
    }
}
