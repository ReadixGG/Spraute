package org.zonarstudio.spraute_engine.script.function;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.zonarstudio.spraute_engine.script.ScriptContext;

import java.util.List;

/**
 * has_item(player, "minecraft:diamond") -> true/false
 * Checks all inventory slots including armor and offhand.
 */
public class HasItemFunction implements ScriptFunction {
    @Override public String getName() { return "hasItem"; }
    @Override public int getArgCount() { return 2; }
    @Override public Class<?>[] getArgTypes() { return new Class[]{Object.class, String.class}; }

    @Override
    public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
        if (args.size() < 2) return false;
        Object target = args.get(0);
        String itemId = String.valueOf(args.get(1));

        Player player = resolvePlayer(target, source);
        if (player == null) return false;

        ResourceLocation searchRL = new ResourceLocation(itemId);
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty()) {
                ResourceLocation stackRL = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem());
                if (stackRL.equals(searchRL)) return true;
            }
        }
        return false;
    }

    private Player resolvePlayer(Object target, CommandSourceStack source) {
        if (target instanceof Player p) return p;
        if (target instanceof String name && source.getLevel() != null) {
            return source.getLevel().getServer().getPlayerList().getPlayerByName(name);
        }
        return null;
    }
}
