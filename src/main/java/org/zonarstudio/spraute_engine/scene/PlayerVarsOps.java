package org.zonarstudio.spraute_engine.scene;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.zonarstudio.spraute_engine.script.ScriptExecutor;
import org.zonarstudio.spraute_engine.script.ScriptManager;
import org.zonarstudio.spraute_engine.script.ScriptWorldData;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * {@code player.data} / {@code player.savedData} — same maps scripts use.
 * PLAYERS-node defaults fill missing keys on join / scene start.
 */
public final class PlayerVarsOps {
    private PlayerVarsOps() {}

    public static Map<String, Object> snapshotData(UUID uuid) {
        Map<String, Object> raw = ScriptManager.getInstance().getPlayerSessionData(uuid);
        Map<String, Object> out = new LinkedHashMap<>();
        raw.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER))
                .forEach(e -> out.put(e.getKey(), e.getValue()));
        return out;
    }

    public static Map<String, Object> snapshotSaved(ServerPlayer player) {
        if (player == null || player.getServer() == null) return Map.of();
        return ScriptWorldData.get(player.getServer().overworld())
                .playerSavedSnapshot(player.getUUID(), player.getServer(),
                        org.zonarstudio.spraute_engine.compat.SprauteEntityCompat.serverLevel(player));
    }

    public static void set(ServerPlayer player, boolean saved, String key, Object value) {
        if (player == null || key == null || key.isBlank()) return;
        String name = key.trim();
        if (saved) {
            new ScriptExecutor.PlayerSavedDataMap(
                    player.getUUID(), player.getServer(),
                    org.zonarstudio.spraute_engine.compat.SprauteEntityCompat.serverLevel(player))
                    .put(name, value);
        } else {
            ScriptManager.getInstance().getPlayerSessionData(player.getUUID()).put(name, value);
        }
    }

    public static void remove(ServerPlayer player, boolean saved, String key) {
        if (player == null || key == null || key.isBlank()) return;
        String name = key.trim();
        if (saved) {
            new ScriptExecutor.PlayerSavedDataMap(
                    player.getUUID(), player.getServer(),
                    org.zonarstudio.spraute_engine.compat.SprauteEntityCompat.serverLevel(player))
                    .remove(name);
        } else {
            ScriptManager.getInstance().getPlayerSessionData(player.getUUID()).remove(name);
        }
    }

    public static boolean has(ServerPlayer player, boolean saved, String key) {
        if (player == null || key == null || key.isBlank()) return false;
        String name = key.trim();
        if (saved) {
            return new ScriptExecutor.PlayerSavedDataMap(
                    player.getUUID(), player.getServer(),
                    org.zonarstudio.spraute_engine.compat.SprauteEntityCompat.serverLevel(player))
                    .containsKey(name);
        }
        return ScriptManager.getInstance().getPlayerSessionData(player.getUUID()).containsKey(name);
    }

    /** Fill missing keys from every loaded scene's PLAYERS node. */
    public static void applyDefaults(ServerPlayer player) {
        if (player == null) return;
        for (String sceneId : SceneRuntime.listLoaded()) {
            SceneActiveState state = SceneRuntime.getActiveState(sceneId);
            if (state == null || state.document == null) continue;
            applyDefaults(player, state.document.ensurePlayersNode());
        }
    }

    public static void applyDefaults(ServerPlayer player, SceneNode players) {
        if (player == null || players == null) return;
        SceneNode.ensurePlayerVarMaps(players);
        applyMap(player, false, players.playerDataDefaults);
        applyMap(player, true, players.playerSavedDefaults);
    }

    public static void applyDefaultsFrom(SceneActiveState state, MinecraftServer server) {
        if (state == null || state.document == null || server == null) return;
        SceneNode players = state.document.ensurePlayersNode();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            applyDefaults(player, players);
        }
    }

    private static void applyMap(ServerPlayer player, boolean saved, Map<String, Object> defaults) {
        if (defaults == null || defaults.isEmpty()) return;
        for (Map.Entry<String, Object> e : defaults.entrySet()) {
            if (e.getKey() == null || e.getKey().isBlank()) continue;
            if (has(player, saved, e.getKey())) continue;
            set(player, saved, e.getKey(), e.getValue());
        }
    }

    public static Object parseValue(String type, String raw) {
        String text = raw == null ? "" : raw.trim();
        String kind = type == null ? "string" : type.toLowerCase(Locale.ROOT);
        return switch (kind) {
            case "number", "num", "float", "int" -> parseNumber(text);
            case "bool", "boolean" -> parseBool(text);
            default -> text;
        };
    }

    public static String inferType(Object value) {
        if (value instanceof Boolean) return "bool";
        if (value instanceof Number) return "number";
        return "string";
    }

    public static String formatValue(Object value) {
        if (value == null) return "";
        if (value instanceof Boolean || value instanceof Number || value instanceof String) {
            return String.valueOf(value);
        }
        return String.valueOf(value);
    }

    private static Object parseNumber(String text) {
        if (text.isEmpty()) return 0;
        try {
            if (text.contains(".") || text.contains("e") || text.contains("E")) {
                return Double.parseDouble(text);
            }
            long n = Long.parseLong(text);
            if (n >= Integer.MIN_VALUE && n <= Integer.MAX_VALUE) return (int) n;
            return n;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static boolean parseBool(String text) {
        return text.equalsIgnoreCase("true") || text.equals("1") || text.equalsIgnoreCase("yes");
    }
}
