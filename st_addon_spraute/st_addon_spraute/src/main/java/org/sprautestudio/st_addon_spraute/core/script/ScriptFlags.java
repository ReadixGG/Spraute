package org.sprautestudio.st_addon_spraute.core.script;

import net.minecraft.nbt.CompoundTag;

import java.util.HashMap;
import java.util.Map;

/** Named marks of a story, kept with it. */
public final class ScriptFlags {
    private final Map<String, String> flags = new HashMap<>();

    public boolean has(String name) {
        return flags.containsKey(name);
    }

    public String get(String name) {
        return flags.get(name);
    }

    public void set(String name) {
        flags.put(name, "");
    }

    public void set(String name, String value) {
        flags.put(name, value);
    }

    public void remove(String name) {
        flags.remove(name);
    }

    CompoundTag save() {
        CompoundTag t = new CompoundTag();
        flags.forEach(t::putString);
        return t;
    }

    void load(CompoundTag t) {
        flags.clear();
        for (String k : t.getAllKeys()) flags.put(k, t.getString(k));
    }
}
