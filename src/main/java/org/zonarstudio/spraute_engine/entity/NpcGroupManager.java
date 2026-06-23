package org.zonarstudio.spraute_engine.entity;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/** Реестр всех именованных групп НПС. */
public final class NpcGroupManager {

    private static final Map<String, NpcGroup> GROUPS = new ConcurrentHashMap<>();

    private NpcGroupManager() {}

    public static NpcGroup getOrCreate(String name) {
        return GROUPS.computeIfAbsent(name, NpcGroup::new);
    }

    public static NpcGroup get(String name) {
        return GROUPS.get(name);
    }

    public static void remove(String name) {
        GROUPS.remove(name);
    }

    public static void clearAll() {
        GROUPS.clear();
    }

    public static Collection<NpcGroup> all() {
        return GROUPS.values();
    }
}
