package org.sprautestudio.st_addon_spraute.core.script;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.function.Consumer;

/** A running story: its main performer, the parallel ones it started, its flags. */
public abstract class ScriptInstance {
    public final ScriptEnvironment env;
    private final int id;
    private final Random random = new Random();
    private final ScriptFlags flags = new ScriptFlags();
    private ScriptPerformer main;
    /** Every parallel performer the story can start, by name. */
    final Map<String, ScriptPerformer> known = new HashMap<>();
    /** The ones running now. */
    final Map<String, ScriptPerformer> running = new LinkedHashMap<>();
    private boolean paused;
    int triggerSeq;

    protected ScriptInstance(ScriptEnvironment env, int id) {
        this.env = env;
        this.id = id;
    }

    public int getId() {
        return id;
    }

    public Random getRandom() {
        return random;
    }

    public ScriptFlags getFlags() {
        return flags;
    }

    public ScriptPerformer getMainPerformer() {
        return main;
    }

    protected void registerMainPerformer(ScriptPerformer p) {
        main = p;
    }

    public boolean isPaused() {
        return paused;
    }

    public void setPaused(boolean p) {
        paused = p;
    }

    public <T extends Entity> Optional<T> optional(Character<T> c) {
        return Characters.find(c);
    }

    /** The story's end, for whatever reason. */
    public void handleStop() {
    }

    protected Map<String, Consumer<Player>> getTriggers() {
        Map<String, Consumer<Player>> map = new HashMap<>();
        map.put("next", p -> triggerSeq++);
        return map;
    }

    public boolean trigger(String name, Player p) {
        Consumer<Player> t = getTriggers().get(name);
        if (t == null) return false;
        t.accept(p);
        return true;
    }

    /** Moves a performer that has this label to it: the main one, else a running parallel one. */
    public boolean goTo(String label) {
        if (main != null && main.goTo(label)) return true;
        for (ScriptPerformer p : running.values()) if (p.goTo(label)) return true;
        return false;
    }

    void tick() {
        if (paused || main == null) return;
        main.tick();
        for (ScriptPerformer p : new java.util.ArrayList<>(running.values())) p.tick();
    }

    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.put("flags", flags.save());
        tag.putBoolean("paused", paused);
        if (main != null) tag.putInt("main", main.getActionIndex());
        CompoundTag par = new CompoundTag();
        running.forEach((k, p) -> par.putInt(k, p.getActionIndex()));
        tag.put("parallel", par);
        return tag;
    }

    public void deserializeNBT(CompoundTag tag) {
        flags.load(tag.getCompound("flags"));
        paused = tag.getBoolean("paused");
        if (main != null) main.jumpToIndex(tag.getInt("main"));
        CompoundTag par = tag.getCompound("parallel");
        running.clear();
        for (String k : par.getAllKeys()) {
            ScriptPerformer p = known.get(k);
            if (p == null) continue;
            p.jumpToIndex(par.getInt(k));
            running.put(k, p);
        }
    }
}
