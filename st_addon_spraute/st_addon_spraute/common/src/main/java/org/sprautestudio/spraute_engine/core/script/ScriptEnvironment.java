package org.sprautestudio.spraute_engine.core.script;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.sprautestudio.spraute_engine.SprauteEngine;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.BiConsumer;

/** The stories of one world: what can start, what runs now. What runs is saved with the world and goes on after a reload. */
public final class ScriptEnvironment {
    /** Told about a story stopped by an error: its id and the error. */
    public static BiConsumer<Integer, Throwable> onError = (id, t) -> SprauteEngine.LOGGER.error("Story #{} failed", id, t);

    public final Map<Integer, Constructor<? extends ScriptInstance>> constructors = new TreeMap<>();
    public final Map<Integer, ScriptInstance> scriptInstances = new LinkedHashMap<>();
    /** Groups of stories for the "category" commands. */
    public final Map<String, int[]> categories = new LinkedHashMap<>();
    private final MinecraftServer server;
    /** How many times each key was pressed in the game (for "wait key"), by the key's name: in all, and by each player. */
    private final Map<String, Long> keys = new HashMap<>();
    private final Map<java.util.UUID, Map<String, Long>> playerKeys = new HashMap<>();
    /** A solo story runs a copy for each player: copies are told apart by the story's id plus this many times the copy's number. */
    public static final int COPY = 1_000_000;
    /** Whose the story being started is. */
    java.util.UUID startingFor;
    private CompoundTag pendingLoad;

    public ScriptEnvironment(MinecraftServer server) {
        this.server = server;
    }

    public MinecraftServer server() {
        return server;
    }

    public static int baseId(int id) {
        return id % COPY;
    }

    public void keyPressed(java.util.UUID player, String key) {
        keys.merge(key, 1L, Long::sum);
        playerKeys.computeIfAbsent(player, k -> new HashMap<>()).merge(key, 1L, Long::sum);
    }

    public long keyCount(String key) {
        return keys.getOrDefault(key, 0L);
    }

    public long keyCount(java.util.UUID player, String key) {
        Map<String, Long> m = playerKeys.get(player);
        return m == null ? 0 : m.getOrDefault(key, 0L);
    }

    private boolean solo(int base) {
        var d = org.sprautestudio.spraute_engine.runtime.SprauteRuntime.get().deployed(base);
        return d != null && d.program.header != null && d.program.header.solo;
    }

    /** The running story this player takes part in, with that id; null — none. */
    public ScriptInstance instanceFor(int base, net.minecraft.server.level.ServerPlayer p) {
        if (!solo(base)) return scriptInstances.get(base);
        for (ScriptInstance s : scriptInstances.values()) if (baseId(s.getId()) == base && p.getUUID().equals(s.owner)) return s;
        return null;
    }

    /** A running story with this id, any player's copy of it. */
    public ScriptInstance anyInstance(int id) {
        ScriptInstance s = scriptInstances.get(id);
        if (s != null) return s;
        for (ScriptInstance o : scriptInstances.values()) if (baseId(o.getId()) == id) return o;
        return null;
    }

    /** Starts a story for a player: their own copy of a solo story, else the one shared, with them as its protagonist. */
    public ScriptInstance startFor(int base, net.minecraft.server.level.ServerPlayer p) {
        ScriptInstance have = p != null ? instanceFor(base, p) : scriptInstances.get(base);
        if (have != null) return have;
        int id = base;
        if (solo(base)) {
            if (p == null) return null;
            int n = 1;
            while (scriptInstances.containsKey(base + n * COPY)) n++;
            id = base + n * COPY;
        }
        startingFor = p != null ? p.getUUID() : null;
        try {
            return start(id);
        } finally {
            startingFor = null;
        }
    }

    public ScriptInstance start(int id) {
        if (scriptInstances.containsKey(id)) return scriptInstances.get(id);
        Constructor<? extends ScriptInstance> c = constructors.get(baseId(id));
        if (c == null) return null;
        // the story sets up its values for its own player already
        java.util.UUID outerAs = org.sprautestudio.spraute_engine.runtime.SprauteRuntime.getAs();
        org.sprautestudio.spraute_engine.runtime.SprauteRuntime.setAs(startingFor);
        ScriptInstance outer = org.sprautestudio.spraute_engine.runtime.SprauteRuntime.enter(null);
        try {
            ScriptInstance s = c.newInstance(this, id);
            scriptInstances.put(id, s);
            org.sprautestudio.spraute_engine.runtime.SprauteRuntime.enter(s);
            org.sprautestudio.spraute_engine.net.SprauteNetwork.sendToAll(new org.sprautestudio.spraute_engine.net.Packets.SpeechClear(id));
            scriptEvent("scriptstart", id);
            announce();
            return s;
        } catch (InvocationTargetException e) {
            onError.accept(id, e.getCause() != null ? e.getCause() : e);
        } catch (ReflectiveOperationException e) {
            onError.accept(id, e);
        } finally {
            org.sprautestudio.spraute_engine.runtime.SprauteRuntime.setAs(outerAs);
            org.sprautestudio.spraute_engine.runtime.SprauteRuntime.enter(outer);
        }
        return null;
    }

    /** Stops a story; by a story's own id, every player's copy of it too. */
    public void stop(int id) {
        if (id < COPY) {
            for (int other : new ArrayList<>(scriptInstances.keySet())) if (other >= COPY && baseId(other) == id) stop(other);
        }
        ScriptInstance s = scriptInstances.remove(id);
        if (s == null) return;
        ScriptInstance outer = org.sprautestudio.spraute_engine.runtime.SprauteRuntime.enter(s);
        try {
            s.stopPerformers();
            s.handleStop();
            Characters.release(id);
            scriptEvent("scriptstop", id);
        } finally {
            org.sprautestudio.spraute_engine.runtime.SprauteRuntime.enter(outer);
        }
        announce();
    }

    private void scriptEvent(String name, int id) {
        Map<String, Object> d = new HashMap<>();
        d.put("id", String.valueOf(id));
        org.sprautestudio.spraute_engine.runtime.GameEvents.fire(name, null, d);
    }

    /** The newest running story this player takes part in: its record is the one the speech history shows. */
    public int newest(net.minecraft.server.level.ServerPlayer p) {
        int last = -1;
        for (ScriptInstance s : scriptInstances.values()) if (!s.solo() || p.getUUID().equals(s.owner)) last = s.getId();
        return last;
    }

    /** Tells the clients which story's record to show, with the record itself when asked. */
    public void announce() {
        for (net.minecraft.server.level.ServerPlayer p : server.getPlayerList().getPlayers()) {
            org.sprautestudio.spraute_engine.net.SprauteNetwork.sendTo(p, new org.sprautestudio.spraute_engine.net.Packets.SpeechNewest(newest(p)));
        }
    }

    /** A player came: the record of the story, and what it shows on the screen now. */
    public void sendTo(net.minecraft.server.level.ServerPlayer p) {
        int id = newest(p);
        ScriptInstance s = scriptInstances.get(id);
        org.sprautestudio.spraute_engine.net.SprauteNetwork.sendTo(p, new org.sprautestudio.spraute_engine.net.Packets.SpeechNewest(id));
        if (s != null) for (ScriptInstance.Line l : s.journal) org.sprautestudio.spraute_engine.net.SprauteNetwork.sendTo(p,
                new org.sprautestudio.spraute_engine.net.Packets.Speech(id, l.name(), l.color(), l.icon(), l.text(), "", l.think(), false, true));
        for (ScriptInstance o : scriptInstances.values()) if (!o.solo() || p.getUUID().equals(o.owner)) o.catchUp(p);
    }

    public void stopAll() {
        for (int id : new ArrayList<>(scriptInstances.keySet())) stop(id);
    }

    /** Stories go while a player is in the world; the flag is for running them on a test server with nobody on it. */
    private static final boolean WITHOUT_PLAYERS = Boolean.getBoolean("spraute.tickWithoutPlayers");

    public void tick() {
        restore();
        if (!WITHOUT_PLAYERS && server.getPlayerCount() == 0) return;
        for (ScriptInstance s : new ArrayList<>(scriptInstances.values())) {
            // a solo story waits for its player to come back
            if (s.solo() && s.protagonist() == null) continue;
            org.sprautestudio.spraute_engine.runtime.SprauteRuntime.enter(s);
            try {
                s.tick();
                ScriptPerformer main = s.getMainPerformer();
                if (main == null || main.isFinished()) stop(s.getId());
            } catch (Throwable t) {
                onError.accept(s.getId(), t);
                stop(s.getId());
            } finally {
                org.sprautestudio.spraute_engine.runtime.SprauteRuntime.setAs(null);
                org.sprautestudio.spraute_engine.runtime.SprauteRuntime.enter(null);
            }
        }
    }

    public List<Integer> category(String name) {
        List<Integer> out = new ArrayList<>();
        int[] ids = categories.get(name);
        if (ids != null) for (int i : ids) out.add(i);
        return out;
    }

    // ================================================================== saved with the world

    private Path file() {
        return server.getWorldPath(LevelResource.ROOT).resolve("spraute").resolve("running.dat");
    }

    public void save() {
        CompoundTag root = new CompoundTag();
        root.put("characters", Characters.save());
        CompoundTag list = new CompoundTag();
        scriptInstances.forEach((id, s) -> {
            try {
                list.put(String.valueOf(id), s.saveState());
            } catch (RuntimeException e) {
                SprauteEngine.LOGGER.warn("Failed to save story {}", id, e);
            }
        });
        root.put("scripts", list);
        try {
            Files.createDirectories(file().getParent());
            NbtIo.writeCompressed(root, file().toFile());
        } catch (IOException e) {
            SprauteEngine.LOGGER.warn("Failed to save running stories", e);
        }
    }

    /** Reads what was running; the stories go on from the first tick, when the deployed scripts are registered. */
    public void load() {
        Characters.clear();
        if (!Files.isRegularFile(file())) return;
        try {
            pendingLoad = NbtIo.readCompressed(file().toFile());
            Characters.load(pendingLoad.getCompound("characters"));
        } catch (IOException e) {
            SprauteEngine.LOGGER.warn("Failed to read running stories", e);
        }
    }

    private void restore() {
        if (pendingLoad == null) return;
        CompoundTag list = pendingLoad.getCompound("scripts");
        pendingLoad = null;
        for (String k : list.getAllKeys()) {
            try {
                CompoundTag state = list.getCompound(k);
                startingFor = state.hasUUID("owner") ? state.getUUID("owner") : null;
                ScriptInstance s = start(Integer.parseInt(k));
                startingFor = null;
                if (s != null) s.loadState(state);
            } catch (RuntimeException e) {
                SprauteEngine.LOGGER.warn("Failed to restore story {}", k, e);
            }
        }
        Characters.afterRestore(this);
    }
}
