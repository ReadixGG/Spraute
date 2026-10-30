package org.sprautestudio.st_addon_spraute.content;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.sprautestudio.st_addon_spraute.St_addon_spraute;
import org.sprautestudio.st_addon_spraute.net.Packets;
import org.sprautestudio.st_addon_spraute.net.SprauteNetwork;
import org.sprautestudio.st_addon_spraute.runtime.Images;
import org.sprautestudio.st_addon_spraute.runtime.SprauteRuntime;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * A project's models, pictures, sounds and entity types on the server. Stories run on the server but these files live in
 * the author's folder: the author's client uploads them, the server keeps them with the world and hands them to whoever joins.
 */
public final class ProjectContent {
    private ProjectContent() {
    }

    private static final List<String> FOLDERS = List.of("entities/", "models/", "images/", "sounds/");
    private static final Set<String> EXTENSIONS = Set.of(".sent", ".json", ".png", ".jpg", ".jpeg", ".ogg", ".wav");
    private static final long MAX_FILE = 32L << 20;
    /** Bytes in one piece of a file sent to the server, which takes small packets, and from it; pieces per tick to a player. */
    public static final int UP_PIECE = 24 * 1024, DOWN_PIECE = 48 * 1024;
    private static final int PIECES_PER_TICK = 3;

    public record Entry(String path, long size, String hash) {
        public String line() {
            return path + "\t" + size + "\t" + hash;
        }

        public static Entry of(String line) {
            String[] p = line.split("\t");
            return new Entry(p[0], Long.parseLong(p[1]), p[2]);
        }
    }

    // ------------------------------------------------------------------ what is a project's content

    public static boolean allowed(String path) {
        if (path == null || path.isEmpty() || path.contains("..") || path.contains(":") || path.startsWith("/") || path.contains("\\")) return false;
        if (path.equals("project.json")) return true;
        if (FOLDERS.stream().noneMatch(path::startsWith)) return false;
        int dot = path.lastIndexOf('.');
        return dot > 0 && EXTENSIONS.contains(path.substring(dot).toLowerCase(Locale.ROOT));
    }

    /** The files of a project folder; works on either side. */
    public static Map<String, Entry> manifest(Path dir) throws IOException {
        Map<String, Entry> out = new LinkedHashMap<>();
        if (!Files.isDirectory(dir)) return out;
        try (Stream<Path> files = Files.walk(dir)) {
            for (Path f : (Iterable<Path>) files.filter(Files::isRegularFile).sorted()::iterator) {
                String rel = dir.relativize(f).toString().replace('\\', '/');
                if (!allowed(rel) || Files.size(f) > MAX_FILE) continue;
                out.put(rel, new Entry(rel, Files.size(f), hash(Files.readAllBytes(f))));
            }
        }
        return out;
    }

    public static String hash(byte[] data) {
        try {
            StringBuilder sb = new StringBuilder();
            for (byte b : MessageDigest.getInstance("SHA-1").digest(data)) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static void removeEmpty(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) return;
        try (Stream<Path> all = Files.walk(dir)) {
            for (Path p : (Iterable<Path>) all.sorted(java.util.Comparator.reverseOrder())::iterator) {
                if (p.equals(dir) || !Files.isDirectory(p)) continue;
                try (Stream<Path> inside = Files.list(p)) {
                    if (inside.findAny().isEmpty()) Files.delete(p);
                }
            }
        }
    }

    public static List<String> lines(Map<String, Entry> m) {
        List<String> out = new ArrayList<>();
        for (Entry e : m.values()) out.add(e.line());
        return out;
    }

    // ------------------------------------------------------------------ the server's copy

    /** Where the server keeps the projects; null with no server. */
    public static Path root() {
        MinecraftServer s = ServerLifecycleHooks.getCurrentServer();
        return s == null ? null : s.getWorldPath(LevelResource.ROOT).resolve("spraute").resolve("content");
    }

    private static Path dir(String project) {
        return root().resolve(Images.slug(project));
    }

    private static final class Upload {
        final UUID from;
        final Map<String, Entry> want = new HashMap<>();
        final Map<String, ByteArrayOutputStream> parts = new HashMap<>();
        boolean changed;

        Upload(UUID from) {
            this.from = from;
        }
    }

    private static final Map<String, Upload> UPLOADS = new HashMap<>();
    private static final Map<UUID, Deque<Packets.ContentData>> OUTGOING = new HashMap<>();

    /** The author offers the project as it is now: what differs is asked for, what is gone is removed. */
    public static void offer(ServerPlayer p, Packets.ContentOffer msg) {
        try {
            Path d = dir(msg.project());
            Files.createDirectories(d);
            Map<String, Entry> have = manifest(d), offered = new LinkedHashMap<>();
            for (String line : msg.entries()) {
                Entry e = Entry.of(line);
                if (allowed(e.path()) && e.size() <= MAX_FILE) offered.put(e.path(), e);
            }
            Upload u = new Upload(p.getUUID());
            for (String old : have.keySet()) {
                if (offered.containsKey(old)) continue;
                Files.deleteIfExists(d.resolve(old));
                u.changed = true;
            }
            removeEmpty(d);
            for (Entry e : offered.values()) {
                Entry mine = have.get(e.path());
                if (mine == null || !mine.hash().equals(e.hash())) u.want.put(e.path(), e);
            }
            if (u.want.isEmpty()) {
                if (u.changed) publish();
                SprauteNetwork.sendTo(p, new Packets.ContentWant(msg.project(), List.of()));
                return;
            }
            UPLOADS.put(msg.project(), u);
            SprauteNetwork.sendTo(p, new Packets.ContentWant(msg.project(), new ArrayList<>(u.want.keySet())));
        } catch (IOException | RuntimeException e) {
            St_addon_spraute.LOGGER.warn("Spraute: cannot take the project {}", msg.project(), e);
            SprauteNetwork.sendTo(p, new Packets.ContentWant(msg.project(), List.of()));
        }
    }

    /** A piece of a file from the author. */
    public static void received(ServerPlayer p, Packets.ContentData msg) {
        Upload u = UPLOADS.get(msg.project());
        if (u == null || !u.from.equals(p.getUUID())) return;
        Entry e = u.want.get(msg.path());
        if (e == null) return;
        ByteArrayOutputStream part = u.parts.computeIfAbsent(msg.path(), k -> new ByteArrayOutputStream());
        part.writeBytes(msg.data());
        if (part.size() > e.size()) {
            u.parts.remove(msg.path());
            u.want.remove(msg.path());
        } else if (msg.index() + 1 >= msg.total()) {
            byte[] all = part.toByteArray();
            u.parts.remove(msg.path());
            u.want.remove(msg.path());
            if (hash(all).equals(e.hash())) {
                try {
                    Path f = dir(msg.project()).resolve(msg.path()).normalize();
                    if (f.startsWith(dir(msg.project()))) {
                        Files.createDirectories(f.getParent());
                        Files.write(f, all);
                        u.changed = true;
                    }
                } catch (IOException ex) {
                    St_addon_spraute.LOGGER.warn("Spraute: cannot save {} of {}", msg.path(), msg.project(), ex);
                }
            } else {
                St_addon_spraute.LOGGER.warn("Spraute: {} of {} came damaged", msg.path(), msg.project());
            }
        }
        if (u.want.isEmpty()) {
            UPLOADS.remove(msg.project());
            if (u.changed) publish();
            SprauteNetwork.sendTo(p, new Packets.ContentWant(msg.project(), List.of()));
        }
    }

    /** The content changed: a dedicated server reads it itself, and every player gets the new list. */
    private static void publish() {
        if (FMLEnvironment.dist.isDedicatedServer()) org.sprautestudio.st_addon_spraute.runtime.models.ModelLibrary.scan();
        for (ServerPlayer p : SprauteRuntime.players()) sendIndex(p);
    }

    /** What the server has, for a player who has just come. */
    public static void sendIndex(ServerPlayer p) {
        Path root = root();
        if (root == null || !Files.isDirectory(root)) return;
        try (Stream<Path> dirs = Files.list(root)) {
            for (Path d : (Iterable<Path>) dirs.filter(Files::isDirectory)::iterator) {
                String name = org.sprautestudio.st_addon_spraute.runtime.models.ModelLibrary.projectNameOf(d);
                SprauteNetwork.sendTo(p, new Packets.ContentIndex(name, lines(manifest(d))));
            }
        } catch (IOException e) {
            St_addon_spraute.LOGGER.warn("Spraute: cannot list the projects", e);
        }
    }

    /** A player asks for the files missing on their side. */
    public static void requested(ServerPlayer p, Packets.ContentRequest msg) {
        Deque<Packets.ContentData> queue = OUTGOING.computeIfAbsent(p.getUUID(), k -> new ArrayDeque<>());
        for (String path : msg.paths()) {
            if (!allowed(path)) continue;
            try {
                Path f = dir(msg.project()).resolve(path).normalize();
                if (!f.startsWith(dir(msg.project())) || !Files.isRegularFile(f)) continue;
                pieces(msg.project(), path, Files.readAllBytes(f), DOWN_PIECE, queue);
            } catch (IOException e) {
                St_addon_spraute.LOGGER.warn("Spraute: cannot read {} of {}", path, msg.project(), e);
            }
        }
    }

    public static void pieces(String project, String path, byte[] all, int size, Deque<Packets.ContentData> queue) {
        int total = Math.max(1, (all.length + size - 1) / size);
        for (int i = 0; i < total; i++) {
            queue.add(new Packets.ContentData(project, path, i, total, Arrays.copyOfRange(all, i * size, Math.min(all.length, (i + 1) * size))));
        }
    }

    public static void tick(MinecraftServer server) {
        OUTGOING.entrySet().removeIf(en -> {
            ServerPlayer p = server.getPlayerList().getPlayer(en.getKey());
            if (p == null) return true;
            for (int i = 0; i < PIECES_PER_TICK && !en.getValue().isEmpty(); i++) SprauteNetwork.sendTo(p, en.getValue().poll());
            return en.getValue().isEmpty();
        });
    }

    public static void unload() {
        UPLOADS.clear();
        OUTGOING.clear();
    }
}
