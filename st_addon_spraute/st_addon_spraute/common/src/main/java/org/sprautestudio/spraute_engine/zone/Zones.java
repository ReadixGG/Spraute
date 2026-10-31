package org.sprautestudio.spraute_engine.zone;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import org.sprautestudio.spraute_engine.SprauteEngine;
import org.sprautestudio.spraute_engine.net.Packets;
import org.sprautestudio.spraute_engine.net.SprauteNetwork;
import org.sprautestudio.spraute_engine.runtime.GameEvents;
import org.sprautestudio.spraute_engine.runtime.SprauteRuntime;
import org.sprautestudio.spraute_engine.platform.Platform;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * The zones on the server: every tick looks where each player is, tells the stories who came in and
 * who went out (zoneenter / zoneleave), shows the zone's name and holds the walls. What the stories
 * changed (on / off, walls) and whose titles were already seen is kept in {@code <world>/spraute/zones.json}.
 */
public final class Zones {
    private Zones() {
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** What stories changed in a zone (null — as in the file). */
    static final class Override {
        Boolean enabled;
        String wall;
    }

    static final class Saved {
        Map<String, Override> zones = new LinkedHashMap<>();
        /** uuid -> zones whose title the player has seen. */
        Map<String, Set<String>> seen = new LinkedHashMap<>();
    }

    private static Saved saved;
    private static final Map<String, ZoneDef> PARSED = new HashMap<>();
    private static final Map<String, String> PARSED_FROM = new HashMap<>();
    /** uuid -> zones the player is in. */
    private static final Map<UUID, Set<String>> INSIDE = new HashMap<>();
    /** uuid -> where the player stood on the last tick (where a wall puts them back). */
    private static final Map<UUID, Vec3> PREV = new HashMap<>();
    private static final Map<UUID, Long> WARNED = new HashMap<>();
    private static final Random RANDOM = new Random();

    // ------------------------------------------------------------------ definitions

    public static String key(String project, String name) {
        return project + "/" + name;
    }

    private static ZoneDef parse(String key, String json) {
        if (json == null) return null;
        if (!json.equals(PARSED_FROM.get(key))) {
            // the zone was changed in the editor: what stories set (on/off, walls) gives way to the file
            if (PARSED_FROM.containsKey(key) && saved().zones.remove(key) != null) {
                save();
                shownJson = "";
            }
            PARSED.put(key, ZoneDef.fromJson(json));
            PARSED_FROM.put(key, json);
        }
        return PARSED.get(key);
    }

    public static ZoneDef def(String project, String name) {
        return parse(key(project, name), SprauteRuntime.get().gui(project, "zone:" + name));
    }

    /** Every zone of every deployed project: key -> definition. */
    private static Map<String, ZoneDef> all() {
        Map<String, ZoneDef> out = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, String>> p : SprauteRuntime.get().allGuis().entrySet())
            for (Map.Entry<String, String> e : p.getValue().entrySet())
                if (e.getKey().startsWith("zone:")) {
                    String key = key(p.getKey(), e.getKey().substring(5));
                    ZoneDef d = parse(key, e.getValue());
                    if (d != null) out.put(key, d);
                }
        return out;
    }

    private static boolean enabled(String key, ZoneDef d) {
        Override o = saved().zones.get(key);
        return o != null && o.enabled != null ? o.enabled : d.enabled;
    }

    private static String wall(String key, ZoneDef d) {
        Override o = saved().zones.get(key);
        return o != null && o.wall != null ? o.wall : d.wall;
    }

    // ------------------------------------------------------------------ storage

    private static Path file() {
        MinecraftServer s = Platform.get().server();
        return s == null ? null : s.getWorldPath(LevelResource.ROOT).resolve("spraute").resolve("zones.json");
    }

    private static Saved saved() {
        if (saved != null) return saved;
        saved = new Saved();
        Path f = file();
        if (f != null && Files.exists(f)) {
            try {
                Saved s = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), Saved.class);
                if (s != null) {
                    if (s.zones == null) s.zones = new LinkedHashMap<>();
                    if (s.seen == null) s.seen = new LinkedHashMap<>();
                    saved = s;
                }
            } catch (Exception e) {
                SprauteEngine.LOGGER.warn("Spraute: cannot read zones", e);
            }
        }
        return saved;
    }

    private static void save() {
        Path f = file();
        if (f == null || saved == null) return;
        try {
            Files.createDirectories(f.getParent());
            Files.writeString(f, GSON.toJson(saved), StandardCharsets.UTF_8);
        } catch (IOException e) {
            SprauteEngine.LOGGER.warn("Spraute: cannot save zones", e);
        }
    }

    public static void unload() {
        saved = null;
        shownJson = "";
        PARSED.clear();
        PARSED_FROM.clear();
        INSIDE.clear();
        PREV.clear();
        WARNED.clear();
    }

    // ------------------------------------------------------------------ every tick

    private static String shownJson = "";
    private static int shownAt;

    /** The zones every player sees (on, not hidden in play) as JSON: key -> definition. */
    private static String shownNow(Map<String, ZoneDef> zones) {
        com.google.gson.JsonObject o = new com.google.gson.JsonObject();
        for (Map.Entry<String, ZoneDef> e : zones.entrySet()) {
            ZoneDef d = e.getValue();
            String w = wall(e.getKey(), d);
            if (!enabled(e.getKey(), d) || (d.hideInGame && w.equals("none"))) continue;
            ZoneDef c = ZoneDef.fromJson(d.toJson());
            c.wall = w;
            o.add(e.getKey(), com.google.gson.JsonParser.parseString(c.toJson()));
        }
        return o.toString();
    }

    /** A player came in: what zones to draw. */
    public static void syncTo(ServerPlayer p) {
        SprauteNetwork.sendTo(p, new Packets.ZoneShow(shownNow(all())));
    }

    public static void tick(MinecraftServer server) {
        Map<String, ZoneDef> zones = all();
        if (++shownAt >= 40) {
            shownAt = 0;
            String js = shownNow(zones);
            if (!js.equals(shownJson)) {
                shownJson = js;
                SprauteNetwork.sendToAll(new Packets.ZoneShow(js));
            }
        }
        if (zones.isEmpty()) {
            INSIDE.clear();
            return;
        }
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.isSpectator()) continue;
            UUID id = p.getUUID();
            Set<String> was = INSIDE.computeIfAbsent(id, k -> new HashSet<>());
            Vec3 pos = p.position();
            String dim = p.level().dimension().location().toString();
            boolean pushed = false;
            // a teleport (a jump of more than a few blocks in a tick) is not stopped by walls
            Vec3 before = PREV.get(id);
            boolean teleported = before == null || before.distanceToSqr(pos) > 9;
            if (teleported) pushed = true;
            Set<String> now = new HashSet<>();
            for (Map.Entry<String, ZoneDef> e : zones.entrySet()) {
                ZoneDef d = e.getValue();
                String key = e.getKey();
                if (!enabled(key, d) || (!d.dimension.isEmpty() && !d.dimension.equals(dim))) continue;
                boolean in = d.holds(pos.x, pos.y, pos.z);
                String w = wall(key, d);
                // the walls: back to where the player stood a tick ago
                if (!pushed && w.equals("in") && was.contains(key) && !in) {
                    pushed = pushBack(p, d);
                    in = true;
                } else if (!pushed && w.equals("out") && !was.contains(key) && in) {
                    pushed = pushBack(p, d);
                    in = false;
                }
                if (in) now.add(key);
            }
            for (String key : now) if (!was.contains(key)) entered(p, key, zones.get(key));
            for (String key : was) if (!now.contains(key)) post(p, "zoneleave", key);
            INSIDE.put(id, now);
            if (!pushed) PREV.put(id, pos);
        }
        INSIDE.keySet().removeIf(u -> server.getPlayerList().getPlayer(u) == null);
    }

    private static boolean pushBack(ServerPlayer p, ZoneDef d) {
        Vec3 back = PREV.get(p.getUUID());
        if (back == null) return false;
        p.teleportTo(p.serverLevel(), back.x, back.y, back.z, p.getYRot(), p.getXRot());
        p.setDeltaMovement(Vec3.ZERO);
        p.hurtMarked = true;
        long t = System.currentTimeMillis();
        if (!d.wallText.isEmpty() && t - WARNED.getOrDefault(p.getUUID(), 0L) > 2000) {
            WARNED.put(p.getUUID(), t);
            p.displayClientMessage(Component.literal(d.wallText).withStyle(ChatFormatting.GOLD), true);
        }
        return true;
    }

    private static void entered(ServerPlayer p, String key, ZoneDef d) {
        if (!d.title.isEmpty() && !d.titleMode.equals("never")) {
            Set<String> seen = saved().seen.computeIfAbsent(p.getUUID().toString(), k -> new LinkedHashSet<>());
            if (d.titleMode.equals("always") || !seen.contains(key)) {
                if (seen.add(key)) save();
                SprauteNetwork.sendTo(p, new Packets.ZoneTitle(d.title, d.subtitle));
            }
        }
        post(p, "zoneenter", key);
    }

    private static void post(ServerPlayer p, String type, String key) {
        Map<String, Object> data = new HashMap<>();
        data.put("zone", key.substring(key.indexOf('/') + 1));
        GameEvents.post(type, p, data);
    }

    // ------------------------------------------------------------------ the story's commands and questions

    public static boolean setEnabled(String project, String name, boolean on) {
        if (def(project, name) == null) return false;
        saved().zones.computeIfAbsent(key(project, name), k -> new Override()).enabled = on;
        save();
        shownAt = 40;
        return true;
    }

    /** Saved from the editor: the files win — what stories changed in this project's zones (on/off, walls) is dropped. */
    public static void sourcesSaved(String project) {
        if (saved().zones.keySet().removeIf(k -> k.startsWith(project + "/"))) {
            save();
            shownAt = 40;
            shownJson = "";
        }
    }

    public static boolean setWall(String project, String name, String mode) {
        if (def(project, name) == null) return false;
        String m = switch (mode.toLowerCase()) {
            case "in", "inside", "внутри", "держать" -> "in";
            case "out", "outside", "снаружи", "не пускать" -> "out";
            default -> "none";
        };
        saved().zones.computeIfAbsent(key(project, name), k -> new Override()).wall = m;
        save();
        shownAt = 40;
        return true;
    }

    /** Is the player in the zone (by the last tick; an off zone holds nobody). */
    public static boolean playerIn(ServerPlayer p, String project, String name) {
        if (p == null) return false;
        Set<String> in = INSIDE.get(p.getUUID());
        if (in != null && in.contains(key(project, name))) return true;
        ZoneDef d = def(project, name);
        return d != null && enabled(key(project, name), d) && d.holds(p.getX(), p.getY(), p.getZ());
    }

    public static boolean contains(String project, String name, Vec3 at) {
        ZoneDef d = require(project, name);
        return d.holds(at.x, at.y, at.z);
    }

    public static ZoneDef require(String project, String name) {
        ZoneDef d = def(project, name);
        if (d == null) throw new org.sprautestudio.spraute_engine.runtime.SprauteRuntimeException("Зона «" + name + "» не найдена в папке «Зоны» проекта");
        return d;
    }

    public static Vec3 randomPoint(String project, String name) {
        return require(project, name).randomPoint(RANDOM);
    }

    /** Names of the zones (of this project) the player is in now. */
    public static List<String> zonesOf(ServerPlayer p, String project) {
        List<String> out = new ArrayList<>();
        if (p == null) return out;
        Set<String> in = INSIDE.get(p.getUUID());
        if (in == null) return out;
        for (String k : in) if (k.startsWith(project + "/")) out.add(k.substring(project.length() + 1));
        Collections.sort(out);
        return out;
    }
}
