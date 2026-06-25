package org.zonarstudio.spraute_engine.script;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.Map;

/** Shared player method dispatch for expressions and CALL_METHOD instructions. */
public final class PlayerScriptMethods {

    private PlayerScriptMethods() {}

    public static boolean isKnown(String methodName) {
        if (methodName == null) return false;
        return switch (methodName.toLowerCase()) {
            case "raycast", "slot", "slotcount", "slotnbt", "helditem", "helditemnbt", "hasitem", "countitem" -> true;
            default -> false;
        };
    }

    public static Object invoke(Player player, String methodName, List<Object> methodArgs,
                                java.util.function.BiFunction<Player, Double, Map<String, Object>> raycastFn) {
        if (player == null || methodName == null) return null;
        String method = methodName.toLowerCase();
        return switch (method) {
            case "raycast" -> {
                double dist = methodArgs.isEmpty() ? 50.0 : ((Number) methodArgs.get(0)).doubleValue();
                yield raycastFn != null ? raycastFn.apply(player, dist) : null;
            }
            case "slot" -> {
                if (!methodArgs.isEmpty()) {
                    int slot = ((Number) methodArgs.get(0)).intValue();
                    if (slot >= 0 && slot <= 40) {
                        ItemStack stack = player.getInventory().getItem(slot);
                        yield stack.isEmpty() ? ""
                                : ForgeRegistries.ITEMS.getKey(stack.getItem()).toString();
                    }
                }
                yield "";
            }
            case "slotcount" -> {
                if (!methodArgs.isEmpty()) {
                    int slot = ((Number) methodArgs.get(0)).intValue();
                    if (slot >= 0 && slot <= 40) {
                        yield player.getInventory().getItem(slot).getCount();
                    }
                }
                yield 0;
            }
            case "slotnbt" -> {
                if (!methodArgs.isEmpty()) {
                    int slot = ((Number) methodArgs.get(0)).intValue();
                    if (slot >= 0 && slot <= 40) {
                        ItemStack stack = player.getInventory().getItem(slot);
                        yield stack.hasTag() ? stack.getTag().toString() : "";
                    }
                }
                yield "";
            }
            case "helditem" -> {
                String hand = methodArgs.isEmpty() ? "right" : String.valueOf(methodArgs.get(0)).toLowerCase();
                ItemStack stack = (hand.equals("left") || hand.equals("offhand"))
                        ? player.getOffhandItem() : player.getMainHandItem();
                yield stack.isEmpty() ? "" : ForgeRegistries.ITEMS.getKey(stack.getItem()).toString();
            }
            case "helditemnbt" -> {
                String hand = methodArgs.isEmpty() ? "right" : String.valueOf(methodArgs.get(0)).toLowerCase();
                ItemStack stack = (hand.equals("left") || hand.equals("offhand"))
                        ? player.getOffhandItem() : player.getMainHandItem();
                yield stack.hasTag() ? stack.getTag().toString() : "";
            }
            case "hasitem" -> {
                if (!methodArgs.isEmpty()) {
                    yield countMatching(player, String.valueOf(methodArgs.get(0))) > 0;
                }
                yield false;
            }
            case "countitem" -> {
                if (!methodArgs.isEmpty()) {
                    yield countMatching(player, String.valueOf(methodArgs.get(0)));
                }
                yield 0;
            }
            default -> null;
        };
    }

    private static int countMatching(Player player, String itemId) {
        Item item = ItemStackScriptUtil.resolveItem(itemId);
        if (item == null) return 0;
        ResourceLocation searchRL = ForgeRegistries.ITEMS.getKey(item);
        int total = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty()) {
                ResourceLocation stackRL = ForgeRegistries.ITEMS.getKey(stack.getItem());
                if (stackRL.equals(searchRL)) total += stack.getCount();
            }
        }
        return total;
    }
}
