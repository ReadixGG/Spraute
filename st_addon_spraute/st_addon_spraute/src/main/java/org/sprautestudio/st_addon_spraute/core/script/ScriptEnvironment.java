package org.sprautestudio.st_addon_spraute.core.script;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.sprautestudio.st_addon_spraute.St_addon_spraute;

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
    public static BiConsumer<Integer, Throwable> onError = (id, t) -> St_addon_spraute.LOGGER.error("Story #{} failed", id, t);

    public final Map<Integer, Constructor<? extends ScriptInstance>> constructors = new TreeMap<>();
    public final Map<Integer, ScriptInstance> scriptInstances = new LinkedHashMap<>();
    /** Groups of stories for the "category" commands. */
    public final Map<String, int[]> categories = new LinkedHashMap<>();
    private final MinecraftServer server;
    /** How many times each key was pressed in the game (for "wait key"), by the key's name. */
    private final Map<String, Long> keys = new HashMap<>();
    private CompoundTag pendingLoad;

    public ScriptEnvironment(MinecraftServer server) {
        this.server = server;
    }

    public MinecraftServer server() {
        return server;
    }

    public void keyPressed(String key) {
        keys.merge(key, 1L, Long::sum);
    }

    public long keyCount(String key) {
        return keys.getOrDefault(key, 0L);
    }

    public ScriptInstance start(int id) {
        if (scriptInstances.containsKey(id)) return scriptInstances.get(id);
        Constructor<? extends ScriptInstance> c = constructors.get(id);
        if (c == null) return null;
        try {
            ScriptInstance s = c.newInstance(this, id);
            scriptInstances.put(id, s);
            org.sprautestudio.st_addon_spraute.net.SprauteNetwork.sendToAll(new org.sprautestudio.st_addon_spraute.net.Packets.SpeechClear(id));
            scriptEvent("scriptstart", id);
            announce();
            return s;
        } catch (InvocationTargetException e) {
            onError.accept(id, e.getCause() != null ? e.getCause() : e);
        } catch (ReflectiveOperationException e) {
            onError.accept(id, e);
        }
        return null;
    }

    public void stop(int id) {
        ScriptInstance s = scriptInstances.remove(id);
        if (s == null) return;
        s.stopPerformers();
        s.handleStop();
        Characters.release(id);
        scriptEvent("scriptstop", id);
        announce();
    }

    private void scriptEvent(String name, int id) {
        Map<String, Object> d = new HashMap<>();
        d.put("id", String.valueOf(id));
        org.sprautestudio.st_addon_spraute.runtime.GameEvents.fire(name, null, d);
    }

    /** The newest running story: its record is the one the speech history shows. */
    public int newest() {
        int last = -1;
        for (int id : scriptInstances.keySet()) last = id;
        return last;
    }

    /** Tells the clients which story's record to show, with the record itself when asked. */
    public void announce() {
        org.sprautestudio.st_addon_spraute.net.SprauteNetwork.sendToAll(new org.sprautestudio.st_addon_spraute.net.Packets.SpeechNewest(newest()));
    }

    public void sendTo(net.minecraft.server.level.ServerPlayer p) {
        int id = newest();
        ScriptInstance s = scriptInstances.get(id);
        org.sprautestudio.st_addon_spraute.net.SprauteNetwork.sendTo(p, new org.sprautestudio.st_addon_spraute.net.Packets.SpeechNewest(id));
        if (s != null) for (ScriptInstance.Line l : s.journal) org.sprautestudio.st_addon_spraute.net.SprauteNetwork.sendTo(p,
                new org.sprautestudio.st_addon_spraute.net.Packets.Speech(id, l.name(), l.color(), l.icon(), l.text(), "", l.think(), false, true));
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
            try {
                s.tick();
                ScriptPerformer main = s.getMainPerformer();
                if (main == null || main.isFinished()) stop(s.getId());
            } catch (Throwable t) {
                onError.accept(s.getId(), t);
                stop(s.getId());
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
                St_addon_spraute.LOGGER.warn("Failed to save story {}", id, e);
            }
        });
        root.put("scripts", list);
        try {
            Files.createDirectories(file().getParent());
            NbtIo.writeCompressed(root, file().toFile());
        } catch (IOException e) {
            St_addon_spraute.LOGGER.warn("Failed to save running stories", e);
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
            St_addon_spraute.LOGGER.warn("Failed to read running stories", e);
        }
    }

    private void restore() {
        if (pendingLoad == null) return;
        CompoundTag list = pendingLoad.getCompound("scripts");
        pendingLoad = null;
        for (String k : list.getAllKeys()) {
            try {
                ScriptInstance s = start(Integer.parseInt(k));
                if (s != null) s.loadState(list.getCompound(k));
            } catch (RuntimeException e) {
                St_addon_spraute.LOGGER.warn("Failed to restore story {}", k, e);
            }
        }
        Characters.afterRestore(this);
    }
}
