package org.zonarstudio.spraute_engine.script.function;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.zonarstudio.spraute_engine.script.ScriptContext;

import java.util.List;

/**
 * get_slot(player, slot_number) -> item ID string (or "" if empty)
 * Slots: 0-8 = hotbar, 9-35 = inventory, 36-39 = armor (feet, legs, chest, head), 40 = offhand
 */
public class GetSlotFunction implements ScriptFunction {
    @Override public String getName() { return "getSlot"; }
    @Override public int getArgCount() { return 2; }
    @Override public Class<?>[] getArgTypes() { return new Class[]{Object.class, Number.class}; }

    @Override
    public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
        if (args.size() < 2) return "";
        Object target = args.get(0);
        int slot = ((Number) args.get(1)).intValue();

        Player player = resolvePlayer(target, source);
        if (player == null) return "";

        if (slot < 0 || slot > 40) return "";
        ItemStack stack = player.getInventory().getItem(slot);
        if (stack.isEmpty()) return "";
        return net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem()).toString();
    }

    private Player resolvePlayer(Object target, CommandSourceStack source) {
        if (target instanceof Player p) return p;
        if (target instanceof String name && source.getLevel() != null) {
            return source.getLevel().getServer().getPlayerList().getPlayerByName(name);
        }
        return null;
    }
}
