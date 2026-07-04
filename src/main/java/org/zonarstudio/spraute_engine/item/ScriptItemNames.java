package org.zonarstudio.spraute_engine.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Shared display name and right-click behaviour for script-declared items. */
public final class ScriptItemNames {
    private ScriptItemNames() {}

    public static Component resolveName(String displayName, ItemStack stack, java.util.function.Supplier<Component> fallback) {
        if (displayName != null && !displayName.isEmpty()) {
            return Component.literal(displayName.replace("&", "§"));
        }
        return fallback.get();
    }

    public static InteractionResultHolder<ItemStack> scriptUse(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}
