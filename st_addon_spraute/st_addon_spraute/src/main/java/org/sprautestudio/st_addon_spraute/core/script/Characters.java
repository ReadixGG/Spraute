package org.sprautestudio.st_addon_spraute.core.script;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.sprautestudio.st_addon_spraute.core.entity.AnimatedEntity;
import org.sprautestudio.st_addon_spraute.core.world.Location;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * The bodies of story characters. Stories hold a character through claims: the one with the highest
 * priority (of equal ones, the earliest) acts on the body, the others wait and take over when it lets go.
 * A claim's state is what its story last set, so it can be put back on a new body at any time.
 */
public final class Characters {
    private Characters() {
    }

    /** Marks a body as a character's: bodies of nobody's character are removed when they load. */
    private static final String TAG = "SprauteCharacter";

    private static final class Claim {
        final int script;
        final int priority;
        final long order;
        Location at;
        final Map<String, Consumer<Entity>> state = new LinkedHashMap<>();

        Claim(int script, int priority, long order) {
            this.script = script;
            this.priority = priority;
            this.order = order;
        }
    }

    private static final class Entry {
        final String id;
        Character<?> character;
        UUID body;
        Location last;
        boolean dead;
        /** State was set while the body is not loaded: it goes on when the body comes. */
        boolean needsApply;
        final List<Claim> claims = new ArrayList<>();
        Claim active;

        Entry(String id) {
            this.id = id;
        }
    }

    private static final Map<String, Entry> ENTRIES = new HashMap<>();
    private static long orders;

    private static Entry entry(Character<?> c) {
        Entry e = ENTRIES.computeIfAbsent(c.getId(), Entry::new);
        e.character = c;
        return e;
    }

    private static Claim claim(ScriptInstance s, Character<?> c) {
        Entry e = entry(c);
        for (Claim cl : e.claims) if (cl.script == s.getId()) return cl;
        Claim cl = new Claim(s.getId(), s.priorityOf(c), orders++);
        e.claims.add(cl);
        return cl;
    }

    private static Entity bodyOf(Entry e) {
        MinecraftServer s = ServerLifecycleHooks.getCurrentServer();
        if (e.body == null || s == null) return null;
        for (ServerLevel l : s.getAllLevels()) {
            Entity b = l.getEntity(e.body);
            if (b != null) return b.isAlive() ? b : null;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public static <T extends Entity> Optional<T> find(Character<T> c) {
        Entry e = c == null ? null : ENTRIES.get(c.getId());
        return e == null ? Optional.empty() : Optional.ofNullable((T) bodyOf(e));
    }

    /** True when this story's claim is the one acting on the body. */
    public static boolean controls(ScriptInstance s, Character<?> c) {
        Entry e = ENTRIES.get(c.getId());
        return e != null && e.active != null && e.active.script == s.getId();
    }

    /** Runs on the body now, if this story is the one acting on it. Not kept in the state. */
    public static void act(ScriptInstance s, Character<?> c, Consumer<Entity> action) {
        if (!controls(s, c) || s.replaying()) return;
        find(c).ifPresent(action);
    }

    /** Remembers a part of the character's state under a key and, if this story acts on the body, applies it. */
    public static void state(ScriptInstance s, Character<?> c, String key, Consumer<Entity> action) {
        Claim cl = claim(s, c);
        cl.state.remove(key);
        cl.state.put(key, action);
        Entry e = entry(c);
        update(e);
        if (!controls(s, c)) return;
        Optional<? extends Entity> body = find(c);
        if (body.isPresent()) action.accept(body.get());
        else e.needsApply = true;
    }

    public static void forget(ScriptInstance s, Character<?> c, String keyPrefix) {
        Entry e = ENTRIES.get(c.getId());
        s.forgetNotes(c.getId() + "|" + keyPrefix);
        if (e == null) return;
        for (Claim cl : e.claims) if (cl.script == s.getId()) cl.state.keySet().removeIf(k -> k.startsWith(keyPrefix));
    }

    /** The story puts the character at a point: it moves there if it has a body, else one appears. */
    public static void place(ScriptInstance s, Character<?> c, Location at) {
        if (at == null) return;
        Claim cl = claim(s, c);
        cl.at = at;
        Entry e = entry(c);
        e.dead = false;
        update(e);
        if (e.active == cl) put(e, at, cl);
    }

    /** Sends the body to the point or makes a new one; a new one gets the claim's whole state. */
    private static void put(Entry e, Location at, Claim cl) {
        MinecraftServer s = ServerLifecycleHooks.getCurrentServer();
        if (s == null) return;
        ServerLevel level = s.getLevel(at.dimension);
        if (level == null) level = s.overworld();
        Entity old = bodyOf(e);
        if (old != null) {
            if (old.level() == level) {
                old.teleportTo(at.x, at.y, at.z);
                face(old, at.yaw);
            } else {
                old.teleportTo(level, at.x, at.y, at.z, java.util.Set.of(), at.yaw, at.pitch);
            }
            e.last = at;
            return;
        }
        spawn(e, level, at, cl);
    }

    /** Body and head turned together: a living body draws by its body's turn, not by its yaw. */
    private static void face(Entity e, float yaw) {
        e.setYRot(yaw);
        e.yRotO = yaw;
        e.setYHeadRot(yaw);
        if (e instanceof LivingEntity le) {
            le.yBodyRot = yaw;
            le.yBodyRotO = yaw;
            le.yHeadRotO = yaw;
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void spawn(Entry e, ServerLevel level, Location at, Claim cl) {
        Character c = e.character;
        if (c == null) return;
        Entity b = c.getType().create(level, null, null, BlockPos.containing(at), MobSpawnType.COMMAND, false, false);
        if (b == null) throw new IllegalStateException("Сущность " + c.getId() + " не создаётся");
        b.moveTo(at.x, at.y, at.z, at.yaw, at.pitch);
        face(b, at.yaw);
        b.setCustomName(Component.literal(c.getName()));
        b.setCustomNameVisible(false);
        b.getPersistentData().putString(TAG, e.id);
        if (b instanceof AnimatedEntity ae && c.texture() != null) ae.setTextureOverride(c.texture());
        if (b instanceof LivingEntity le && c.speed() != null) {
            var a = le.getAttribute(Attributes.MOVEMENT_SPEED);
            if (a != null) a.setBaseValue(0.25 * c.speed());
        }
        e.body = b.getUUID();
        e.last = at;
        level.addFreshEntity(b);
        e.needsApply = false;
        if (cl != null) for (Consumer<Entity> part : new ArrayList<>(cl.state.values())) part.accept(b);
        org.sprautestudio.st_addon_spraute.runtime.GameEvents.fire("charspawn", null, eventData(e, b));
    }

    private static Map<String, Object> eventData(Entry e, Entity b) {
        Map<String, Object> d = new HashMap<>();
        d.put("id", e.id);
        d.put("name", b.getName().getString());
        d.put("__entity", b);
        d.put("x", Math.floor(b.getX()));
        d.put("y", Math.floor(b.getY()));
        d.put("z", Math.floor(b.getZ()));
        return d;
    }

    /** Picks the claim that acts now; when it changes, the old one's doings stop and the new one's state goes on the body. */
    private static void update(Entry e) {
        Claim best = null;
        for (Claim c : e.claims) {
            if (best == null || c.priority > best.priority || c.priority == best.priority && c.order < best.order) best = c;
        }
        if (best == e.active) return;
        Entity b = bodyOf(e);
        // a story coming back after a reload takes up its own claim: nothing to quiet
        if (e.active != null && b != null && !e.claims.isEmpty()) quiet(b);
        e.active = best;
        if (best == null) {
            if (b != null) {
                org.sprautestudio.st_addon_spraute.runtime.GameEvents.fire("charremove", null, eventData(e, b));
                b.discard();
            }
            ENTRIES.remove(e.id);
            return;
        }
        if (best.at != null) put(e, best.at, best);
        Entity now = bodyOf(e);
        if (now != null) for (Consumer<Entity> part : new ArrayList<>(best.state.values())) part.accept(now);
    }

    /** Stops what the body is doing: behaviours, animations, where it looks. */
    private static void quiet(Entity b) {
        Behaviours.clear(b);
        if (b instanceof AnimatedEntity ae) {
            ae.setLookTarget(null, false);
            for (String l : ae.getAnimationSystem().getLayerNames()) if (!l.equals("movement")) ae.getAnimationSystem().stopAnimation(l, 0.2f);
            ae.clearAnimationOverrides();
        }
    }

    /** The story has stopped: its claims go; a body nobody holds any more goes with them. */
    public static void release(int script) {
        for (Entry e : new ArrayList<>(ENTRIES.values())) {
            if (e.claims.removeIf(c -> c.script == script)) update(e);
        }
    }

    /** The story takes the character out of the world, as far as it is concerned. */
    public static void remove(ScriptInstance s, Character<?> c) {
        Entry e = ENTRIES.get(c.getId());
        s.forgetNotes(c.getId() + "|");
        if (e != null && e.claims.removeIf(cl -> cl.script == s.getId())) update(e);
    }

    /** The story lets go of the body: it stays in the world, nobody's character any more. */
    public static void free(Character<?> c) {
        Entry e = ENTRIES.remove(c.getId());
        Entity b = e != null ? bodyOf(e) : null;
        if (b != null) {
            b.getPersistentData().remove(TAG);
            Behaviours.clear(b);
        }
    }

    /** What the story's characters do when it goes back to a checkpoint: looks and behaviours are undone. */
    public static void rollback(int script) {
        for (Entry e : ENTRIES.values()) {
            if (e.active == null || e.active.script != script) continue;
            Entity b = bodyOf(e);
            if (b == null) continue;
            Behaviours.clear(b);
            if (b instanceof AnimatedEntity ae) ae.setLookTarget(null, false);
            e.active.state.remove("look");
        }
    }

    /** A body that has loaded: false — it is nobody's character, or an old copy, and has to go. */
    static boolean belongs(Entity b) {
        String id = b.getPersistentData().getString(TAG);
        if (id.isEmpty()) return true;
        Entry e = ENTRIES.get(id);
        return e != null && b.getUUID().equals(e.body);
    }

    /** A body the story let die stays dead; any other comes back while its character is active. */
    static void died(Entity b) {
        if (b instanceof AnimatedEntity ae && !ae.canDie) return;
        for (Entry e : ENTRIES.values()) if (b.getUUID().equals(e.body)) e.dead = true;
    }

    /** Every tick: remembers where the bodies are, and brings back those that went missing while their character is active. */
    static void tick(MinecraftServer s) {
        for (Entry e : new ArrayList<>(ENTRIES.values())) {
            if (e.active == null) continue;
            Entity b = bodyOf(e);
            if (b != null) {
                e.last = new Location(b);
                if (e.needsApply) {
                    e.needsApply = false;
                    for (Consumer<Entity> part : new ArrayList<>(e.active.state.values())) part.accept(b);
                }
                continue;
            }
            if (e.dead || e.last == null || s.getTickCount() % 20 != 0) continue;
            ServerLevel level = s.getLevel(e.last.dimension);
            if (level != null && level.hasChunkAt(BlockPos.containing(e.last))) spawn(e, level, e.last, e.active);
        }
    }

    static CompoundTag save() {
        CompoundTag root = new CompoundTag();
        ENTRIES.forEach((id, e) -> {
            CompoundTag t = new CompoundTag();
            if (e.body != null) t.putUUID("body", e.body);
            t.putBoolean("dead", e.dead);
            if (e.last != null) {
                t.putString("dim", e.last.dimension.location().toString());
                t.putDouble("x", e.last.x);
                t.putDouble("y", e.last.y);
                t.putDouble("z", e.last.z);
                t.putFloat("yaw", e.last.yaw);
            }
            ListTag claims = new ListTag();
            for (Claim c : e.claims) {
                CompoundTag ct = new CompoundTag();
                ct.putInt("script", c.script);
                ct.putInt("priority", c.priority);
                ct.putLong("order", c.order);
                claims.add(ct);
            }
            t.put("claims", claims);
            if (e.active != null) t.putInt("active", e.active.script);
            root.put(id, t);
        });
        return root;
    }

    static void load(CompoundTag root) {
        ENTRIES.clear();
        for (String id : root.getAllKeys()) {
            CompoundTag t = root.getCompound(id);
            Entry e = new Entry(id);
            if (t.hasUUID("body")) e.body = t.getUUID("body");
            e.dead = t.getBoolean("dead");
            if (t.contains("dim")) {
                var dim = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                        new net.minecraft.resources.ResourceLocation(t.getString("dim")));
                e.last = new Location(dim, t.getDouble("x"), t.getDouble("y"), t.getDouble("z"), t.getFloat("yaw"), 0);
            }
            ListTag claims = t.getList("claims", Tag.TAG_COMPOUND);
            for (int i = 0; i < claims.size(); i++) {
                CompoundTag ct = claims.getCompound(i);
                Claim c = new Claim(ct.getInt("script"), ct.getInt("priority"), ct.getLong("order"));
                orders = Math.max(orders, c.order + 1);
                e.claims.add(c);
                if (t.contains("active") && t.getInt("active") == c.script) e.active = c;
            }
            ENTRIES.put(id, e);
        }
    }

    /** After a reload: stories that did not come back no longer hold anything; characters get their descriptions back. */
    static void afterRestore(ScriptEnvironment env) {
        for (Entry e : new ArrayList<>(ENTRIES.values())) {
            e.character = e.character != null ? e.character : Character.getById(e.id);
            if (e.claims.removeIf(c -> !env.scriptInstances.containsKey(c.script))) update(e);
        }
    }

    static void clear() {
        ENTRIES.clear();
    }
}
