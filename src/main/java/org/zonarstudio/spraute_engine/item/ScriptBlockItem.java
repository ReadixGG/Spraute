package org.zonarstudio.spraute_engine.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

/** Block item for {@code create block} with an optional display name from {@code name = "..."}. */
public class ScriptBlockItem extends BlockItem {
    private final String displayName;

    public ScriptBlockItem(Block block, Properties properties, String displayName) {
        super(block, properties);
        this.displayName = displayName;
    }

    private Component buildName() {
        if (displayName != null && !displayName.isEmpty()) {
            return Component.literal(displayName.replace("&", "§"));
        }
        return null;
    }

    @Override
    public Component getName(ItemStack stack) {
        Component c = buildName();
        return c != null ? c : super.getName(stack);
    }

    //? if >=1.20.1 {
    @Override
    public Component getDescription() {
        Component c = buildName();
        return c != null ? c : super.getDescription();
    }
    //?}
}
