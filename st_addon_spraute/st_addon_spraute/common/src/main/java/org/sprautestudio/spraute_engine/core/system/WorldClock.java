package org.sprautestudio.spraute_engine.core.system;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.saveddata.SavedData;
import org.sprautestudio.spraute_engine.runtime.GameEvents;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * The world's own clock, in place of the usual day cycle while it is on. Time goes by tasks: the one with the
 * highest priority runs. The default one cannot be removed, and its day is much longer than usual.
 */
public final class WorldClock {
    private WorldClock() {
    }

    public static final long DAY = 24000;
    /** Time per tick of the default task: a day of 80 minutes instead of 20. */
    private static final double DEFAULT_SPEED = 0.25;

    /** A way for time to go: how fast, until when. */
    public static final class Task {
        final String id;
        final int priority;
        double speed, multiplier = 1;
        BooleanSupplier until;
        public boolean stopped;

        Task(String id, int priority, double speed, BooleanSupplier until) {
            this.id = id;
            this.priority = priority;
            this.speed = speed;
            this.until = until;
        }
    }

    private static final class Saved extends SavedData {
        long time;
        long day;
        boolean frozen;
        long frozenAt;
        double factor = 1;

        @Override
        public CompoundTag save(CompoundTag t) {
            t.putLong("time", time);
            t.putLong("day", day);
            t.putBoolean("frozen", frozen);
            t.putLong("frozenAt", frozenAt);
            t.putDouble("factor", factor);
            return t;
        }

        static Saved load(CompoundTag t) {
            Saved s = new Saved();
            s.time = t.getLong("time");
            s.day = t.getLong("day");
            s.frozen = t.getBoolean("frozen");
            s.frozenAt = t.getLong("frozenAt");
            s.factor = t.contains("factor") ? t.getDouble("factor") : 1;
            return s;
        }
    }

    private static final Task DEFAULT = new Task("default", Integer.MIN_VALUE, DEFAULT_SPEED, null);
    private static final List<Task> TASKS = new ArrayList<>(List.of(DEFAULT));
    private static double carry;

    private static Saved saved(MinecraftServer s) {
        return s.overworld().getDataStorage().computeIfAbsent(Saved::load, () -> {
            Saved n = new Saved();
            n.time = s.overworld().getDayTime();
            n.day = n.time / DAY;
            return n;
        }, "spraute_clock");
    }

    public static Task addTask(String id, int priority, double speed, BooleanSupplier until) {
        removeTask(id);
        Task t = new Task(id, priority, speed, until);
        TASKS.add(t);
        return t;
    }

    public static void removeTask(String id) {
        TASKS.removeIf(t -> t != DEFAULT && t.id.equals(id));
    }

    private static Task running() {
        Task best = DEFAULT;
        for (Task t : TASKS) if (!t.stopped && t.priority > best.priority) best = t;
        return best;
    }

    public static void tick(MinecraftServer s) {
        Saved d = saved(s);
        server(s).set(false, s);
        TASKS.removeIf(t -> t != DEFAULT && t.until != null && t.until.getAsBoolean());
        if (!d.frozen) {
            Task t = running();
            carry += t.speed * t.multiplier * d.factor;
            long whole = (long) carry;
            if (whole != 0) {
                carry -= whole;
                move(s, d, d.time + whole);
            }
        }
        if (s.getTickCount() % 20 == 0) apply(s, d);
    }

    private static GameRules.BooleanValue server(MinecraftServer s) {
        return s.getGameRules().getRule(GameRules.RULE_DAYLIGHT);
    }

    /** Back to the usual day cycle: the clock was switched off. */
    public static void release(MinecraftServer s) {
        server(s).set(true, s);
    }

    /** Passing midnight is a new day. */
    private static void move(MinecraftServer s, Saved d, long to) {
        d.time = to;

        // the game's day starts at dawn; midnight is 18000 of it
        long night = Math.floorDiv(to + 6000, DAY);
        if (night > d.day) {
            d.day = night;
            var data = new HashMap<String, Object>();
            data.put("amount", (double) night);
            GameEvents.fire("newday", null, data);
        }
        d.setDirty();
        apply(s, d);
    }

    /** All dimensions, and the clients at once. */
    private static void apply(MinecraftServer s, Saved d) {
        for (ServerLevel l : s.getAllLevels()) l.setDayTime(d.time);
        for (ServerPlayer p : s.getPlayerList().getPlayers()) {
            p.connection.send(new ClientboundSetTimePacket(p.level().getGameTime(), d.time, false));
        }
    }

    /** The time of day, this day. */
    public static void setTimeOfDay(MinecraftServer s, long timeOfDay) {
        Saved d = saved(s);
        move(s, d, Math.floorDiv(d.time, DAY) * DAY + Math.floorMod(timeOfDay, DAY));
    }

    /** The time of day on a given day. */
    public static void set(MinecraftServer s, long day, long timeOfDay) {
        Saved d = saved(s);
        d.day = day;
        move(s, d, day * DAY + Math.floorMod(timeOfDay, DAY));
    }

    public static void add(MinecraftServer s, long ticks) {
        Saved d = saved(s);
        move(s, d, d.time + ticks);
    }

    public static void setFactor(MinecraftServer s, double factor) {
        Saved d = saved(s);
        d.factor = Math.max(0, factor);
        d.setDirty();
    }

    public static void freeze(MinecraftServer s) {
        Saved d = saved(s);
        if (d.frozen) return;
        d.frozen = true;
        d.frozenAt = d.time;
        d.setDirty();
    }

    /** Time goes again; {@code back} — from the moment it was frozen. */
    public static void unfreeze(MinecraftServer s, boolean back) {
        Saved d = saved(s);
        if (!d.frozen) return;
        d.frozen = false;
        if (back) move(s, d, d.frozenAt);
        d.setDirty();
    }

    public static long day(MinecraftServer s) {
        return saved(s).day;
    }

    public static void unload() {
        TASKS.clear();
        TASKS.add(DEFAULT);
        carry = 0;
    }
}
