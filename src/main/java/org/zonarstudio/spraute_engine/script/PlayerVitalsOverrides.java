package org.zonarstudio.spraute_engine.script;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.food.FoodData;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Disables vanilla health/hunger mechanics for a player (no damage, no drain)
 * and hides the matching HUD bars. XP hide is visual only — the bar and level
 * number, not XP gain.
 */
public final class PlayerVitalsOverrides {
    private static final List<String> HEALTH_HUD = List.of("player_health", "armor_level");
    private static final List<String> HUNGER_HUD = List.of("food_level");
    private static final List<String> XP_HUD = List.of("experience_bar");
    private static final Map<UUID, State> STATES = new ConcurrentHashMap<>();

    public record State(boolean healthDisabled, boolean hungerDisabled, boolean xpHudDisabled,
                        float frozenHp, int frozenFood, float frozenSaturation) {}

    private PlayerVitalsOverrides() {}

    public static boolean isHealthDisabled(ServerPlayer player) {
        State s = player == null ? null : STATES.get(player.getUUID());
        return s != null && s.healthDisabled();
    }

    public static boolean isHungerDisabled(ServerPlayer player) {
        State s = player == null ? null : STATES.get(player.getUUID());
        return s != null && s.hungerDisabled();
    }

    public static boolean isXpHudDisabled(ServerPlayer player) {
        State s = player == null ? null : STATES.get(player.getUUID());
        return s != null && s.xpHudDisabled();
    }

    public static void setHealthEnabled(ServerPlayer player, boolean enabled) {
        if (player == null) return;
        State prev = STATES.getOrDefault(player.getUUID(), enabledState(player));
        if (enabled) {
            put(player, new State(false, prev.hungerDisabled(), prev.xpHudDisabled(), player.getHealth(),
                    prev.frozenFood(), prev.frozenSaturation()));
            HudHideOverrides.setHidden(player, HEALTH_HUD, false);
        } else {
            put(player, new State(true, prev.hungerDisabled(), prev.xpHudDisabled(), player.getHealth(),
                    prev.frozenFood(), prev.frozenSaturation()));
            HudHideOverrides.setHidden(player, HEALTH_HUD, true);
        }
    }

    public static void setHungerEnabled(ServerPlayer player, boolean enabled) {
        if (player == null) return;
        FoodData food = player.getFoodData();
        State prev = STATES.getOrDefault(player.getUUID(), enabledState(player));
        if (enabled) {
            put(player, new State(prev.healthDisabled(), false, prev.xpHudDisabled(), prev.frozenHp(),
                    food.getFoodLevel(), food.getSaturationLevel()));
            HudHideOverrides.setHidden(player, HUNGER_HUD, false);
        } else {
            put(player, new State(prev.healthDisabled(), true, prev.xpHudDisabled(), prev.frozenHp(),
                    food.getFoodLevel(), food.getSaturationLevel()));
            HudHideOverrides.setHidden(player, HUNGER_HUD, true);
        }
    }

    public static void setXpVisible(ServerPlayer player, boolean visible) {
        if (player == null) return;
        State prev = STATES.getOrDefault(player.getUUID(), enabledState(player));
        put(player, new State(prev.healthDisabled(), prev.hungerDisabled(), !visible,
                prev.frozenHp(), prev.frozenFood(), prev.frozenSaturation()));
        HudHideOverrides.setHidden(player, XP_HUD, !visible);
    }

    public static void setVitalsEnabled(ServerPlayer player, boolean enabled) {
        setHealthEnabled(player, enabled);
        setHungerEnabled(player, enabled);
        setXpVisible(player, enabled);
    }

    public static void captureHealth(ServerPlayer player, float hp) {
        if (player == null || !isHealthDisabled(player)) return;
        State prev = STATES.get(player.getUUID());
        if (prev == null) return;
        STATES.put(player.getUUID(), new State(true, prev.hungerDisabled(), prev.xpHudDisabled(), hp,
                prev.frozenFood(), prev.frozenSaturation()));
    }

    public static void captureHunger(ServerPlayer player, int food, float saturation) {
        if (player == null || !isHungerDisabled(player)) return;
        State prev = STATES.get(player.getUUID());
        if (prev == null) return;
        STATES.put(player.getUUID(), new State(prev.healthDisabled(), true, prev.xpHudDisabled(),
                prev.frozenHp(), food, saturation));
    }

    public static void apply(ServerPlayer player) {
        if (player == null) return;
        State s = STATES.get(player.getUUID());
        if (s == null) return;
        if (s.healthDisabled()) {
            float hp = Math.min(s.frozenHp(), player.getMaxHealth());
            if (hp < 0.001f) hp = Math.min(1f, player.getMaxHealth());
            if (Math.abs(player.getHealth() - hp) > 0.001f) {
                player.setHealth(hp);
            }
            player.hurtTime = 0;
            player.hurtDuration = 0;
            player.invulnerableTime = 0;
            ensureHidden(player, "player_health", HEALTH_HUD);
        }
        if (s.hungerDisabled()) {
            FoodData food = player.getFoodData();
            food.setFoodLevel(s.frozenFood());
            food.setSaturation(s.frozenSaturation());
            ensureHidden(player, "food_level", HUNGER_HUD);
        }
        if (s.xpHudDisabled()) {
            ensureHidden(player, "experience_bar", XP_HUD);
        }
    }

    public static void clear(UUID id) {
        if (id != null) STATES.remove(id);
    }

    public static void clear(ServerPlayer player) {
        if (player == null) return;
        State prev = STATES.remove(player.getUUID());
        if (prev == null) return;
        if (prev.healthDisabled()) {
            HudHideOverrides.setHidden(player, HEALTH_HUD, false);
        }
        if (prev.hungerDisabled()) {
            HudHideOverrides.setHidden(player, HUNGER_HUD, false);
        }
        if (prev.xpHudDisabled()) {
            HudHideOverrides.setHidden(player, XP_HUD, false);
        }
    }

    private static State enabledState(ServerPlayer player) {
        FoodData food = player.getFoodData();
        return new State(false, false, false, player.getHealth(), food.getFoodLevel(), food.getSaturationLevel());
    }

    private static void ensureHidden(ServerPlayer player, String part, List<String> parts) {
        if (!HudHideOverrides.isHidden(player, part)) {
            HudHideOverrides.setHidden(player, parts, true);
        }
    }

    private static void put(ServerPlayer player, State next) {
        if (!next.healthDisabled() && !next.hungerDisabled() && !next.xpHudDisabled()) {
            STATES.remove(player.getUUID());
        } else {
            STATES.put(player.getUUID(), next);
        }
    }
}
