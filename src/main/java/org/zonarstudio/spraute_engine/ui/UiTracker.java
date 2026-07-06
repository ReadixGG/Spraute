package org.zonarstudio.spraute_engine.ui;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Серверное состояние скриптовых UI: открыт ли экран, текст полей ввода, позиция скролла.
 */
public final class UiTracker {
    private static final Set<UUID> OPEN = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, Map<String, String>> INPUTS = new ConcurrentHashMap<>();
    private static final Map<UUID, Map<String, Float>> SCROLLS = new ConcurrentHashMap<>();

    private UiTracker() {}

    public static void markOpen(UUID player) {
        if (player != null) OPEN.add(player);
    }

    public static void markClosed(UUID player) {
        if (player == null) return;
        OPEN.remove(player);
        INPUTS.remove(player);
        SCROLLS.remove(player);
    }

    public static boolean isOpen(UUID player) {
        return player != null && OPEN.contains(player);
    }

    public static void setInput(UUID player, String widgetId, String text) {
        if (player == null || widgetId == null || widgetId.isEmpty()) return;
        INPUTS.computeIfAbsent(player, k -> new ConcurrentHashMap<>()).put(widgetId, text != null ? text : "");
    }

    public static String getInput(UUID player, String widgetId) {
        if (player == null || widgetId == null) return "";
        Map<String, String> m = INPUTS.get(player);
        if (m == null) return "";
        return m.getOrDefault(widgetId, "");
    }

    public static void setScroll(UUID player, String widgetId, float offset) {
        if (player == null || widgetId == null || widgetId.isEmpty()) return;
        SCROLLS.computeIfAbsent(player, k -> new ConcurrentHashMap<>()).put(widgetId, offset);
    }

    public static float getScroll(UUID player, String widgetId) {
        if (player == null || widgetId == null) return 0f;
        Map<String, Float> m = SCROLLS.get(player);
        if (m == null) return 0f;
        return m.getOrDefault(widgetId, 0f);
    }
}
