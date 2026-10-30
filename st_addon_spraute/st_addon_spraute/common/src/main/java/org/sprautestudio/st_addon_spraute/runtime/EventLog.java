package org.sprautestudio.st_addon_spraute.runtime;

import net.minecraft.world.entity.Entity;

import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;

/** Recent game events of one story, numbered so every wait and handler sees each event once. */
final class EventLog {
    record Entry(long seq, String type, Map<String, Object> data) {}

    private static final int KEEP = 128;
    private final ArrayDeque<Entry> recent = new ArrayDeque<>();
    private long seq;

    long seq() {
        return seq;
    }

    void add(String type, Map<String, Object> data, SprauteScriptInstance s) {
        Map<String, Object> d = new LinkedHashMap<>(data);
        // the entity itself is only needed to tell which story character it is
        Object en = d.remove("__entity");
        if (en instanceof Entity e) {
            d.put("ref", new EntityRef(e));
            s.characters.forEach((name, ch) -> {
                Entity ce = s.optional(ch).orElse(null);
                if (ce == e) d.put("character", name);
            });
        }
        d.putIfAbsent("character", "");
        // the killer: a story character answers to its name in "by"
        Object k = d.remove("__killer");
        if (k instanceof Entity ke) {
            d.put("killerref", new EntityRef(ke));
            s.characters.forEach((name, ch) -> {
                Entity ce = s.optional(ch).orElse(null);
                if (ce == ke) d.put("by", name);
            });
        }
        d.put("type", type);
        recent.addLast(new Entry(++seq, type, d));
        while (recent.size() > KEEP) recent.removeFirst();
    }

    /** The oldest event of a type after {@code after} accepted by the filter, or null. */
    Entry find(String type, long after, Predicate<Map<String, Object>> filter) {
        for (Entry e : recent) {
            if (e.seq > after && e.type.equals(type) && filter.test(e.data)) return e;
        }
        return null;
    }
}
