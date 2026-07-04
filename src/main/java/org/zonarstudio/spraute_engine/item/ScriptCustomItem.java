package org.zonarstudio.spraute_engine.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Custom item declared via {@code create item} in a .spr script with a fixed display name. */
public class ScriptCustomItem extends Item {
    private final String displayName;

    public ScriptCustomItem(Properties properties, String displayName) {
        super(properties);
        this.displayName = displayName;
    }

    @Override
    public Component getName(ItemStack stack) {
        return ScriptItemNames.resolveName(displayName, stack, () -> super.getName(stack));
    }

    /**
     * A plain item right-clicked in air returns PASS, so the client never sends a use packet and the
     * server-side {@code RightClickItem} event (which drives {@code on action("use", ...)}) never fires.
     * Returning sided success forces the packet so script handlers run.
     */
    @Override
    public net.minecraft.world.InteractionResultHolder<ItemStack> use(
            net.minecraft.world.level.Level level,
            net.minecraft.world.entity.player.Player player,
            net.minecraft.world.InteractionHand hand) {
        return ScriptItemNames.scriptUse(level, player, hand);
    }

    //? if >=1.20.1 {
    @Override
    public Component getDescription() {
        return ScriptItemNames.resolveName(displayName, ItemStack.EMPTY, () -> super.getDescription());
    }
    //?}
}
