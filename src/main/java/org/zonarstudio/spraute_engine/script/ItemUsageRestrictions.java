package org.zonarstudio.spraute_engine.script;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.EnumSet;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player item usage restrictions set from scripts ({@code forbidItem} / {@code allowItem}).
 * Engine enforces on mine, attack, use and wear — no level logic here.
 */
public final class ItemUsageRestrictions {

    public enum Action {
        USE("use"),
        MINE("mine"),
        ATTACK("attack"),
        WEAR("wear"),
        ALL("all");

        private final String id;

        Action(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }

        public static Action parse(String raw) {
            if (raw == null || raw.isBlank()) return ALL;
            String s = raw.trim().toLowerCase();
            for (Action a : values()) {
                if (a.id.equals(s)) return a;
            }
            return ALL;
        }
    }

    private static final Map<UUID, Map<String, EnumSet<Action>>> RESTRICTIONS = new ConcurrentHashMap<>();

    private ItemUsageRestrictions() {}

    public static String normalizeItemId(String itemId) {
        if (itemId == null || itemId.isBlank()) return "";
        String s = itemId.trim();
        if (!s.contains(":")) s = "minecraft:" + s;
        return s;
    }

    public static void forbid(ServerPlayer player, String itemId, String actionStr) {
        if (player == null) return;
        String id = normalizeItemId(itemId);
        if (id.isEmpty()) return;
        Action action = Action.parse(actionStr);
        RESTRICTIONS
                .computeIfAbsent(player.getUUID(), k -> new ConcurrentHashMap<>())
                .computeIfAbsent(id, k -> EnumSet.noneOf(Action.class))
                .add(action);
        if (action == Action.ALL) {
            EnumSet<Action> set = RESTRICTIONS.get(player.getUUID()).get(id);
            set.add(Action.USE);
            set.add(Action.MINE);
            set.add(Action.ATTACK);
            set.add(Action.WEAR);
        }
    }

    public static void allow(ServerPlayer player, String itemId, String actionStr) {
        if (player == null) return;
        String id = normalizeItemId(itemId);
        if (id.isEmpty()) return;
        Map<String, EnumSet<Action>> perPlayer = RESTRICTIONS.get(player.getUUID());
        if (perPlayer == null) return;
        EnumSet<Action> set = perPlayer.get(id);
        if (set == null) return;
        Action action = Action.parse(actionStr);
        if (action == Action.ALL) {
            perPlayer.remove(id);
        } else {
            set.remove(action);
            set.remove(Action.ALL);
            if (set.isEmpty()) perPlayer.remove(id);
        }
        if (perPlayer.isEmpty()) RESTRICTIONS.remove(player.getUUID());
    }

    public static void clear(ServerPlayer player) {
        if (player != null) RESTRICTIONS.remove(player.getUUID());
    }

    public static boolean isForbidden(ServerPlayer player, ItemStack stack, Action action) {
        if (player == null || stack == null || stack.isEmpty()) return false;
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (key == null) return false;
        return isForbidden(player, key.toString(), action);
    }

    public static boolean isForbidden(ServerPlayer player, String itemId, Action action) {
        if (player == null || action == null) return false;
        Map<String, EnumSet<Action>> perPlayer = RESTRICTIONS.get(player.getUUID());
        if (perPlayer == null) return false;
        String id = normalizeItemId(itemId);
        EnumSet<Action> forbidden = perPlayer.get(id);
        if (forbidden == null || forbidden.isEmpty()) return false;
        return forbidden.contains(Action.ALL) || forbidden.contains(action);
    }

    public static void notifyBlocked(ServerPlayer player) {
        if (player == null) return;
        player.displayClientMessage(Component.literal("§cПредмет пока недоступен — повысьте уровень прокачки"), true);
    }

    public static void clear(UUID playerId) {
        if (playerId != null) RESTRICTIONS.remove(playerId);
    }
}
