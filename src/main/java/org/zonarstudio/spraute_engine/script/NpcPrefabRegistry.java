package org.zonarstudio.spraute_engine.script;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Шаблоны НИПов из {@code create npc_prefab id { ... }} — без спавна, только defaults. */
public final class NpcPrefabRegistry {
    private static final Map<String, Map<String, List<ScriptNode>>> PREFABS = new ConcurrentHashMap<>();

    private NpcPrefabRegistry() {}

    public static void register(String id, Map<String, List<ScriptNode>> props) {
        if (id == null || id.isBlank()) return;
        PREFABS.put(id.toLowerCase(), props);
    }

    public static Map<String, List<ScriptNode>> get(String id) {
        if (id == null) return null;
        Map<String, List<ScriptNode>> props = PREFABS.get(id.toLowerCase());
        return props == null ? null : Collections.unmodifiableMap(props);
    }

    public static boolean has(String id) {
        return id != null && PREFABS.containsKey(id.toLowerCase());
    }

    public static Set<String> ids() {
        return PREFABS.keySet();
    }

    public static void clear() {
        PREFABS.clear();
    }

    /** Копия props для merge при спавне (ScriptNode остаются shared). */
    public static Map<String, List<ScriptNode>> copyProps(Map<String, List<ScriptNode>> src) {
        Map<String, List<ScriptNode>> out = new HashMap<>();
        if (src != null) {
            for (var e : src.entrySet()) {
                out.put(e.getKey(), e.getValue());
            }
        }
        return out;
    }
}
