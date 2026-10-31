package org.sprautestudio.spraute_engine.core.script;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.function.Consumer;

/**
 * A running story: its main performer, the parallel ones it started, its flags and the record of what was said.
 * After a reload it goes on from its last checkpoint; what was said stays in the record.
 */
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
    /** The state at the last checkpoint passed; null — none yet. */
    private CompoundTag checkpoint;
    /** Which step last set each part of the characters' state: what a reload runs again, to put it all back. */
    private final Map<String, String> notes = new LinkedHashMap<>();
    private boolean replaying;
    /** What was said, oldest first. */
    final List<Line> journal = new ArrayList<>();

    public record Line(String name, int color, String icon, String text, boolean think) {
    }

    /** The player the story is about; they may be away. Null: no one yet. */
    public java.util.UUID owner;

    protected ScriptInstance(ScriptEnvironment env, int id) {
        this.env = env;
        this.id = id;
        this.owner = env.startingFor;
    }

    /** Each player has their own copy of the story, seen by them alone. */
    public boolean solo() {
        return false;
    }

    /** The story's player: its owner; when they left a shared story, whoever is on the server. */
    public net.minecraft.server.level.ServerPlayer protagonist() {
        var players = env.server().getPlayerList();
        var p = owner != null ? players.getPlayer(owner) : null;
        if (p != null || solo()) return p;
        return players.getPlayers().isEmpty() ? null : players.getPlayers().get(0);
    }

    /** A key's presses that count for this story: its player's in a solo story or an "as" block, anybody's in a shared one. */
    long keyCount(String key) {
        java.util.UUID as = org.sprautestudio.spraute_engine.runtime.SprauteRuntime.getAs();
        if (as != null) return env.keyCount(as, key);
        if (solo() && owner != null) return env.keyCount(owner, key);
        return env.keyCount(key);
    }

    /** A player came to the running story: what it shows on the screen now. */
    public void catchUp(net.minecraft.server.level.ServerPlayer p) {
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

    void note(String key, ScriptPerformer by) {
        if (replaying) return;
        String name = by == main ? "" : null;
        if (name == null) for (var en : known.entrySet()) if (en.getValue() == by) name = en.getKey();
        if (name == null) return;
        notes.remove(key);
        notes.put(key, by.getActionIndex() + "/" + name);
    }

    void forgetNotes(String prefix) {
        notes.keySet().removeIf(k -> k.startsWith(prefix));
    }

    boolean replaying() {
        return replaying;
    }

    /** The state steps passed before the checkpoint run again; waits, lines and other doings do not. */
    private void replay() {
        replaying = true;
        try {
            for (String where : new ArrayList<>(notes.values())) {
                int cut = where.indexOf('/');
                int index = Integer.parseInt(where.substring(0, cut));
                String name = where.substring(cut + 1);
                ScriptPerformer p = name.isEmpty() ? main : known.get(name);
                if (p != null && index < p.actions.size()) p.actions.get(index).update();
            }
        } finally {
            replaying = false;
        }
    }

    /** How strongly this story holds a character against other stories. */
    public int priorityOf(Character<?> c) {
        return 0;
    }

    public List<Line> journal() {
        return journal;
    }

    /** The story's end, for whatever reason. */
    public void handleStop() {
    }

    void stopPerformers() {
        if (main != null) main.stop();
        for (ScriptPerformer p : running.values()) p.stop();
    }

    protected Map<String, Consumer<Player>> getTriggers() {
        return new HashMap<>();
    }

    /** A trigger from outside: its own action if it has one; every waiting "wait trigger" is released either way. */
    public void trigger(String name, Player p) {
        Consumer<Player> t = getTriggers().get(name);
        if (t != null) t.accept(p);
        triggerSeq++;
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
        for (ScriptPerformer p : new ArrayList<>(running.values())) p.tick();
    }

    /** A checkpoint was passed by a performer: from here on a reload comes back to it. */
    void passCheckpoint(String name, ScriptPerformer by) {
        by.setStage(name);
        CompoundTag tag = serializeNBT();
        // the checkpoint step itself is done
        if (by == main) tag.putInt("main", main.getActionIndex() + 1);
        else running.forEach((k, p) -> {
            if (p == by) tag.getCompound("parallel").putInt(k, p.getActionIndex() + 1);
        });
        tag.putString("stage", name);
        checkpoint = tag;
        Map<String, Object> d = new HashMap<>();
        d.put("id", name);
        org.sprautestudio.spraute_engine.runtime.GameEvents.fire("checkpoint", null, d);
    }

    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.put("flags", flags.save());
        tag.putBoolean("paused", paused);
        if (main != null) tag.putInt("main", main.getActionIndex());
        CompoundTag par = new CompoundTag();
        running.forEach((k, p) -> par.putInt(k, p.getActionIndex()));
        tag.put("parallel", par);
        ListTag kept = new ListTag();
        notes.forEach((k, v) -> {
            CompoundTag t = new CompoundTag();
            t.putString("key", k);
            t.putString("step", v);
            kept.add(t);
        });
        tag.put("notes", kept);
        return tag;
    }

    public void deserializeNBT(CompoundTag tag) {
        flags.load(tag.getCompound("flags"));
        paused = tag.getBoolean("paused");
        if (main != null) {
            main.jumpToIndex(tag.getInt("main"));
            main.setStage(tag.getString("stage"));
        }
        CompoundTag par = tag.getCompound("parallel");
        running.clear();
        for (String k : par.getAllKeys()) {
            ScriptPerformer p = known.get(k);
            if (p == null) continue;
            p.jumpToIndex(par.getInt(k));
            running.put(k, p);
        }
        notes.clear();
        ListTag kept = tag.getList("notes", Tag.TAG_COMPOUND);
        for (int i = 0; i < kept.size(); i++) notes.put(kept.getCompound(i).getString("key"), kept.getCompound(i).getString("step"));
        replay();
    }

    /** What goes into the world's save: the record of what was said and the last checkpoint. */
    CompoundTag saveState() {
        CompoundTag tag = new CompoundTag();
        if (owner != null) tag.putUUID("owner", owner);
        if (checkpoint != null) tag.put("checkpoint", checkpoint);
        ListTag lines = new ListTag();
        for (Line l : journal) {
            CompoundTag t = new CompoundTag();
            t.putString("name", l.name());
            t.putInt("color", l.color());
            t.putString("icon", l.icon());
            t.putString("text", l.text());
            t.putBoolean("think", l.think());
            lines.add(t);
        }
        tag.put("journal", lines);
        return tag;
    }

    void loadState(CompoundTag tag) {
        journal.clear();
        ListTag lines = tag.getList("journal", Tag.TAG_COMPOUND);
        for (int i = 0; i < lines.size(); i++) {
            CompoundTag t = lines.getCompound(i);
            journal.add(new Line(t.getString("name"), t.getInt("color"), t.getString("icon"), t.getString("text"), t.getBoolean("think")));
        }
        if (tag.contains("checkpoint")) {
            checkpoint = tag.getCompound("checkpoint");
            deserializeNBT(checkpoint);
        }
    }
}
