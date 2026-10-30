package org.sprautestudio.st_addon_spraute.runtime;

import net.minecraft.world.entity.Entity;
import org.sprautestudio.st_addon_spraute.St_addon_spraute;
import org.sprautestudio.st_addon_spraute.platform.Hooks;
import org.sprautestudio.st_addon_spraute.platform.TickPhase;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Runs something on an entity once it is in the world (a character spawned a tick ago); gives up after 5 s. */
public final class WhenReady {
    public static void registerHooks() {
        Hooks.SERVER_TICK.add(WhenReady::onTick);
    }

    private WhenReady() {
    }

    private record Pending(Supplier<Entity> entity, Consumer<Entity> action, int[] ttl) {}

    private static final List<Pending> PENDING = new ArrayList<>();

    public static void run(Supplier<Entity> entity, Consumer<Entity> action) {
        PENDING.add(new Pending(entity, action, new int[]{100}));
    }

    public static void onTick(Hooks.ServerTick ev) {
        if (ev.phase != TickPhase.END || PENDING.isEmpty()) return;
        for (Iterator<Pending> it = new ArrayList<>(PENDING).iterator(); it.hasNext(); ) {
            Pending p = it.next();
            Entity e;
            try {
                e = p.entity().get();
            } catch (RuntimeException ex) {
                e = null;
            }
            if (e != null) {
                try {
                    p.action().accept(e);
                } catch (RuntimeException ex) {
                    St_addon_spraute.LOGGER.warn("Spraute: {}", ex.getMessage());
                }
                PENDING.remove(p);
            } else if (--p.ttl()[0] <= 0) PENDING.remove(p);
        }
    }
}
