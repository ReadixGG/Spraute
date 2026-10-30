package org.sprautestudio.st_addon_spraute.runtime;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.openjdk.nashorn.api.scripting.ClassFilter;
import org.openjdk.nashorn.api.scripting.NashornScriptEngineFactory;
import org.openjdk.nashorn.api.scripting.ScriptObjectMirror;

import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * JavaScript inside stories ({@code js { … }}, {@code js("…")}), on the Nashorn engine the game already
 * has. Each story keeps its own engine (functions and variables of one js block are there for the next).
 * <p>
 * By default a script sees only {@code story}, {@code player} and {@code world} — friendly objects that
 * can do what a story can, and no Java classes: a story from a stranger can't touch files or the
 * computer. The world's operator may allow Java ({@code /sprautejs unsafe on}): then {@code Java.type(…)}
 * works and {@code entity.raw}, {@code world.server} give the game's own objects — for other mods.
 */
public final class JsEngine {
    private JsEngine() {
    }

    private static final Map<Integer, ScriptEngine> ENGINES = new HashMap<>();
    private static Boolean unsafe;

    // ================================================================== the switch

    private static Path settings() {
        MinecraftServer s = ServerLifecycleHooks.getCurrentServer();
        return s == null ? null : s.getWorldPath(LevelResource.ROOT).resolve("spraute").resolve("js_unsafe");
    }

    public static boolean unsafe() {
        if (unsafe == null) {
            Path p = settings();
            unsafe = p != null && Files.exists(p);
        }
        return unsafe;
    }

    public static void setUnsafe(boolean on) {
        unsafe = on;
        ENGINES.clear();
        Path p = settings();
        if (p == null) return;
        try {
            if (on) {
                Files.createDirectories(p.getParent());
                Files.writeString(p, "JavaScript of stories may use Java classes", StandardCharsets.UTF_8);
            } else Files.deleteIfExists(p);
        } catch (Exception ignored) {
        }
    }

    public static void unload() {
        ENGINES.clear();
        unsafe = null;
    }

    public static void forget(int scriptId) {
        ENGINES.remove(scriptId);
    }

    // ================================================================== running

    private static ScriptEngine engine(SprauteScriptInstance s) {
        return ENGINES.computeIfAbsent(s.getId(), id -> {
            boolean open = unsafe();
            ClassFilter filter = name -> open;
            ScriptEngine e = new NashornScriptEngineFactory().getScriptEngine(new String[]{"--language=es6"}, JsEngine.class.getClassLoader(), filter);
            Bindings b = e.getBindings(ScriptContext.ENGINE_SCOPE);
            b.put("story", new Story(s));
            b.put("world", new World());
            if (!open) {
                // no way round the filter: no Java packages, no loading of other files, and no "engine" /
                // "context" — through the engine object a script could make itself a new engine without the filter
                b.put("engine", null);
                b.put("context", null);
                try {
                    e.eval("delete this.Java; delete this.Packages; delete this.java; delete this.javax; delete this.com; delete this.org; delete this.net;"
                            + "delete this.JavaImporter; delete this.load; delete this.loadWithNewGlobal; delete this.exit; delete this.quit;"
                            + "Object.defineProperty(this,'engine',{value:undefined,writable:false,configurable:false});"
                            + "Object.defineProperty(this,'context',{value:undefined,writable:false,configurable:false});");
                } catch (Exception ex) {
                    throw new SprauteRuntimeException("JS: не удалось закрыть песочницу — код не выполнен");
                }
            }
            return e;
        });
    }

    /** Runs code; the value of its last line (or of return …) goes back to the story. */
    public static Object run(SprauteScriptInstance s, String code) {
        ScriptEngine e = engine(s);
        ServerPlayer p = SprauteRuntime.player();
        e.getBindings(ScriptContext.ENGINE_SCOPE).put("player", p == null ? null : new Ent(p));
        String body = code.contains("return") ? "(function(){\n" + code + "\n})()" : code;
        try {
            return toStory(e.eval(body));
        } catch (Exception ex) {
            String m = ex.getMessage() == null ? ex.toString() : ex.getMessage();
            if (m.contains("ClassNotFoundException") || m.contains("is not defined") && (m.contains("Java") || m.contains("Packages")))
                m += " (классы Java выключены — оператор мира может разрешить: /sprautejs unsafe on)";
            throw new SprauteRuntimeException("JS: " + m.replace("<eval>", "строка"));
        }
    }

    /** A JavaScript value as a story value. */
    static Object toStory(Object v) {
        if (v == null) return "";
        if (v instanceof Number n) return n.doubleValue();
        if (v instanceof Boolean || v instanceof String) return v;
        if (v instanceof Ent en) return new EntityRef(en.e);
        if (v instanceof ScriptObjectMirror m) {
            if (m.isArray()) {
                List<Object> out = new ArrayList<>();
                for (Object x : m.values()) out.add(toStory(x));
                return out;
            }
            Map<String, Object> out = new LinkedHashMap<>();
            for (String k : m.keySet()) out.put(k, toStory(m.get(k)));
            return out;
        }
        if (v instanceof Entity en) return new EntityRef(en);
        if (v instanceof List<?> l) {
            List<Object> out = new ArrayList<>();
            for (Object x : l) out.add(toStory(x));
            return out;
        }
        return String.valueOf(v);
    }

    /** A story value as a JavaScript value. */
    static Object toJs(Object v) {
        if (v instanceof EntityRef r) {
            Entity e = r.get();
            return e == null ? null : new Ent(e);
        }
        if (v instanceof List<?> l) {
            List<Object> out = new ArrayList<>();
            for (Object x : l) out.add(toJs(x));
            return out;
        }
        return v;
    }

    // ================================================================== what scripts see

    /** The story: its variables, characters, log. */
    public static final class Story {
        private final SprauteScriptInstance s;

        Story(SprauteScriptInstance s) {
            this.s = s;
        }

        public Object get(String name) {
            return toJs(s.vars.get(name));
        }

        public void set(String name, Object value) {
            s.vars.put(name, toStory(value));
        }

        /** A character of the story by its name (null — not in the world). */
        public Ent character(String name) {
            var ch = s.characters.get(name);
            if (ch == null) return null;
            Entity e = s.optional(ch).orElse(null);
            return e == null ? null : new Ent(e);
        }

        public void log(Object text) {
            SprauteRuntime.get().log(s.getId(), String.valueOf(text));
        }

        public Object global(String name) {
            return toJs(Globals.get(name, null));
        }

        public void setGlobal(String name, Object value) {
            Globals.set(name, toStory(value));
        }

        public int getId() {
            return s.getId();
        }
    }

    /** The world: commands, blocks, entities, time. */
    public static final class World {
        public String command(String cmd) {
            return Bridges.run(cmd).output();
        }

        public int commandValue(String cmd) {
            return Bridges.run(cmd).value();
        }

        public String block(double x, double y, double z) {
            return String.valueOf(BuiltInRegistries.BLOCK.getKey(WorldApi.level().getBlockState(BlockPos.containing(x, y, z)).getBlock()));
        }

        public void setBlock(double x, double y, double z, String id) {
            var b = BuiltInRegistries.BLOCK.get(new ResourceLocation(id.contains(":") ? id : "minecraft:" + id));
            WorldApi.level().setBlockAndUpdate(BlockPos.containing(x, y, z), b.defaultBlockState());
        }

        public long getTime() {
            return WorldApi.level().getDayTime();
        }

        public void setTime(long t) {
            WorldApi.level().setDayTime(t);
        }

        public String getDimension() {
            return WorldApi.level().dimension().location().toString();
        }

        /** Entities of a type ("minecraft:zombie", "" — any) within r blocks of a point. */
        public List<Ent> entities(String type, double x, double y, double z, double r) {
            List<Ent> out = new ArrayList<>();
            var box = new net.minecraft.world.phys.AABB(x - r, y - r, z - r, x + r, y + r, z + r);
            for (Entity e : WorldApi.level().getEntities((Entity) null, box, en -> true)) {
                String t = String.valueOf(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()));
                if (type == null || type.isEmpty() || t.equals(type) || t.equals("minecraft:" + type)) out.add(new Ent(e));
            }
            return out;
        }

        public Ent summon(String type, double x, double y, double z) {
            Object r = WorldApi.summon(type, WorldApi.list(new net.minecraft.world.phys.Vec3(x, y, z)));
            return r instanceof EntityRef er && er.get() != null ? new Ent(er.get()) : null;
        }

        public boolean hasMod(String id) {
            return Bridges.hasMod(id);
        }

        public double score(Object who, String objective) {
            return Bridges.score(who instanceof Ent en ? new EntityRef(en.e) : who, objective);
        }

        public void setScore(Object who, String objective, double v) {
            Bridges.setScore(who instanceof Ent en ? new EntityRef(en.e) : who, objective, v, false);
        }

        public void say(String text) {
            ServerPlayer p = SprauteRuntime.player();
            if (p != null) p.sendSystemMessage(Component.literal(text));
        }

        /** The game's server object — only with Java allowed. */
        public Object getServer() {
            if (!unsafe()) throw new SecurityException("world.server — только при /sprautejs unsafe on");
            return ServerLifecycleHooks.getCurrentServer();
        }
    }

    /** An entity: the player, a character, a mob. Properties: x y z, health, name, type… */
    public static final class Ent {
        final Entity e;

        Ent(Entity e) {
            this.e = e;
        }

        public double getX() {
            return e.getX();
        }

        public double getY() {
            return e.getY();
        }

        public double getZ() {
            return e.getZ();
        }

        public String getName() {
            return e.getName().getString();
        }

        public String getType() {
            return String.valueOf(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()));
        }

        public boolean isAlive() {
            return e.isAlive();
        }

        public double getHealth() {
            return e instanceof LivingEntity le ? le.getHealth() : 0;
        }

        public void setHealth(double v) {
            if (e instanceof LivingEntity le) le.setHealth((float) v);
        }

        public double getMaxHealth() {
            return e instanceof LivingEntity le ? le.getMaxHealth() : 0;
        }

        public float getYaw() {
            return e.getYRot();
        }

        public void tp(double x, double y, double z) {
            e.teleportTo(x, y, z);
        }

        public void push(double x, double y, double z) {
            e.push(x, y, z);
            e.hurtMarked = true;
        }

        public void setGlowing(boolean on) {
            e.setGlowingTag(on);
        }

        public void kill() {
            e.kill();
        }

        public Object nbt(String path) {
            return toJs(Bridges.nbt(new EntityRef(e), path));
        }

        public void setNbt(String path, Object value) {
            Bridges.setNbt(new EntityRef(e), path, toStory(value));
        }

        public double attr(String id) {
            return Bridges.attr(new EntityRef(e), id);
        }

        public void setAttr(String id, double v) {
            Bridges.setAttr(new EntityRef(e), id, v);
        }

        public void effect(String id, double seconds, int level) {
            if (!(e instanceof LivingEntity le)) return;
            var eff = BuiltInRegistries.MOB_EFFECT.get(new ResourceLocation(id.contains(":") ? id : "minecraft:" + id));
            if (eff != null) le.addEffect(new MobEffectInstance(eff, (int) Math.round(seconds * 20), Math.max(0, level - 1)));
        }

        public void give(String item, int count) {
            if (!(e instanceof net.minecraft.world.entity.player.Player p)) return;
            p.getInventory().add(ItemSpec.stack(item, count));
        }

        public String getHand() {
            return e instanceof LivingEntity le ? String.valueOf(BuiltInRegistries.ITEM.getKey(le.getMainHandItem().getItem())) : "";
        }

        public double distance(Ent other) {
            return e.distanceTo(other.e);
        }

        /** The game's own entity object — only with Java allowed. */
        public Object getRaw() {
            if (!unsafe()) throw new SecurityException("entity.raw — только при /sprautejs unsafe on");
            return e;
        }

        @Override
        public String toString() {
            return getType() + " «" + getName() + "»";
        }
    }
}
