package org.zonarstudio.spraute_engine.script.function;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.zonarstudio.spraute_engine.script.ScriptContext;

import java.util.List;

/**
 * count_item(player, "minecraft:diamond") -> number of that item across all slots
 */
public class CountItemFunction implements ScriptFunction {
    @Override public String getName() { return "countItem"; }
    @Override public int getArgCount() { return 2; }
    @Override public Class<?>[] getArgTypes() { return new Class[]{Object.class, String.class}; }

    @Override
    public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
        if (args.size() < 2) return 0;
        Object target = args.get(0);
        String itemId = String.valueOf(args.get(1));

        Player player = resolvePlayer(target, source);
        if (player == null) return 0;

        ResourceLocation searchRL = new ResourceLocation(itemId);
        int total = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty()) {
                ResourceLocation stackRL = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem());
                if (stackRL.equals(searchRL)) total += stack.getCount();
            }
        }
        return total;
    }

    private Player resolvePlayer(Object target, CommandSourceStack source) {
        if (target instanceof Player p) return p;
        if (target instanceof String name && source.getLevel() != null) {
            return source.getLevel().getServer().getPlayerList().getPlayerByName(name);
        }
        return null;
    }
}
