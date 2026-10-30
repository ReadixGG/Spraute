package org.sprautestudio.st_addon_spraute.platform;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/** One kind of game event: the engine's listeners by priority. A loader's bridge fires each priority at its own place. */
public final class Hook<E> {
    private final Map<Priority, List<Consumer<E>>> listeners = new EnumMap<>(Priority.class);
    private final boolean receivesCanceled;

    public Hook() {
        this(false);
    }

    /** @param receivesCanceled listeners hear the event even when someone has canceled it */
    public Hook(boolean receivesCanceled) {
        this.receivesCanceled = receivesCanceled;
    }

    public void add(Consumer<E> listener) {
        add(Priority.NORMAL, listener);
    }

    public void add(Priority priority, Consumer<E> listener) {
        listeners.computeIfAbsent(priority, p -> new ArrayList<>()).add(listener);
    }

    public Set<Priority> priorities() {
        return listeners.keySet();
    }

    public boolean receivesCanceled() {
        return receivesCanceled;
    }

    public void fire(Priority priority, E event) {
        List<Consumer<E>> list = listeners.get(priority);
        if (list == null) return;
        for (Consumer<E> l : list) {
            if (!receivesCanceled && event instanceof Cancellable c && c.isCanceled()) return;
            l.accept(event);
        }
    }

    /** All priorities in order: for a loader with no priorities of its own. */
    public void fire(E event) {
        for (Priority p : Priority.values()) fire(p, event);
    }
}
