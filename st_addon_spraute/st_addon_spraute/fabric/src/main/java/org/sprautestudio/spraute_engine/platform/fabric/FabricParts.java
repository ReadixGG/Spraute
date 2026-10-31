package org.sprautestudio.spraute_engine.platform.fabric;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.sprautestudio.spraute_engine.runtime.models.BodyPart;
import org.sprautestudio.spraute_engine.runtime.models.SprauteModelEntity;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/** The characters of each level, to find their extra hitboxes: Forge has this list built in. */
public final class FabricParts {
    private FabricParts() {
    }

    private static final Map<Level, Set<SprauteModelEntity>> BODIES = Collections.synchronizedMap(new WeakHashMap<>());

    static void loaded(Entity e, Level level) {
        if (e instanceof SprauteModelEntity b) BODIES.computeIfAbsent(level, l -> ConcurrentHashMap.newKeySet()).add(b);
    }

    static void unloaded(Entity e, Level level) {
        if (e instanceof SprauteModelEntity) {
            Set<SprauteModelEntity> set = BODIES.get(level);
            if (set != null) set.remove(e);
        }
    }

    public static Collection<SprauteModelEntity> bodies(Level level) {
        Set<SprauteModelEntity> set = BODIES.get(level);
        return set != null ? set : List.of();
    }

    /** The parts take the ids right after the body. */
    public static Entity byId(Level level, int id) {
        for (SprauteModelEntity b : bodies(level)) {
            int i = id - b.getId() - 1;
            if (i < 0 || i >= SprauteModelEntity.MAX_PARTS) continue;
            for (BodyPart p : b.parts()) if (p.index() == i) return p.entity();
        }
        return null;
    }
}
