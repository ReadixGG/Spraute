package org.sprautestudio.st_addon_spraute.core.script;

import java.util.HashMap;
import java.util.Map;

/** Who speaks in each speech slot of a performer. */
public final class SpeechState {
    public record Speaker(String name, Integer color, String icon) {
    }

    private final Map<Integer, Speaker> slots = new HashMap<>();

    public void setEntityName(int slot, String name, Integer color, String icon) {
        slots.put(slot, new Speaker(name, color, icon));
    }

    public void setEntityName(int slot, String name, String icon) {
        setEntityName(slot, name, null, icon);
    }

    public Speaker get(int slot) {
        return slots.getOrDefault(slot, new Speaker("", null, "generic_man"));
    }
}
