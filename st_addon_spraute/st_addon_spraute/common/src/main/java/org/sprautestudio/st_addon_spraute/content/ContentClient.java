package org.sprautestudio.st_addon_spraute.content;

import net.minecraft.client.Minecraft;
import org.sprautestudio.st_addon_spraute.St_addon_spraute;
import org.sprautestudio.st_addon_spraute.editor.project.Project;
import org.sprautestudio.st_addon_spraute.editor.project.Workspace;
import org.sprautestudio.st_addon_spraute.net.Packets;
import org.sprautestudio.st_addon_spraute.net.SprauteNetwork;
import org.sprautestudio.st_addon_spraute.runtime.Images;
import org.sprautestudio.st_addon_spraute.platform.ClientHooks;
import org.sprautestudio.st_addon_spraute.platform.TickPhase;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The client's side of a project's files: uploading the author's, and keeping a copy of the server's while connected to it. */
public final class ContentClient {
    public static void registerHooks() {
        ClientHooks.CLIENT_TICK.add(ContentClient::onTick);
        ClientHooks.LOGGING_OUT.add(ContentClient::onLogout);
    }

    private ContentClient() {
    }

    private static final int PIECES_PER_TICK = 2;
    private static final int UPLOAD_TIMEOUT_TICKS = 20 * 120;

    /** project -> its folder here, for the projects this server gave. */
    private static final Map<String, Path> ACTIVE = new LinkedHashMap<>();
    private static final Map<String, Map<String, ProjectContent.Entry>> MISSING = new HashMap<>();
    private static final Map<String, ByteArrayOutputStream> PARTS = new HashMap<>();
    private static final Deque<Packets.ContentData> UP = new ArrayDeque<>();
    private static final Map<String, Runnable> AFTER_UPLOAD = new HashMap<>();
    private static final Map<String, Integer> UPLOAD_AGE = new HashMap<>();

    private static Path cache() {
        return Workspace.root().resolve("server_content");
    }

    public static Collection<Path> activeDirs() {
        return ACTIVE.values();
    }

    /** The server's copy of a project while connected to that server; null otherwise. */
    public static Path dir(String project) {
        return ACTIVE.get(project);
    }

    // ------------------------------------------------------------------ as the author

    /** Sends the project's files to the server, then does {@code then}: a story must not start before its models are there. */
    public static void upload(String project, Runnable then) {
        Path dir = null;
        for (Workspace.ProjectInfo i : Workspace.projects()) if (i.name().equals(project)) dir = i.dir();
        if (dir == null) {
            if (then != null) then.run();
            return;
        }
        try {
            List<String> lines = ProjectContent.lines(ProjectContent.manifest(dir));
            if (then != null) {
                AFTER_UPLOAD.put(project, then);
                UPLOAD_AGE.put(project, 0);
            }
            SprauteNetwork.sendToServer(new Packets.ContentOffer(project, lines));
        } catch (IOException e) {
            St_addon_spraute.LOGGER.warn("Spraute: cannot read the project {}", project, e);
            if (then != null) then.run();
        }
    }

    /** What the server still wants; nothing — it has it all. */
    public static void wanted(Packets.ContentWant msg) {
        if (msg.paths().isEmpty()) {
            UPLOAD_AGE.remove(msg.project());
            Runnable then = AFTER_UPLOAD.remove(msg.project());
            if (then != null) then.run();
            return;
        }
        Path dir = null;
        for (Workspace.ProjectInfo i : Workspace.projects()) if (i.name().equals(msg.project())) dir = i.dir();
        if (dir == null) return;
        for (String path : msg.paths()) {
            try {
                ProjectContent.pieces(msg.project(), path, Files.readAllBytes(dir.resolve(path)), ProjectContent.UP_PIECE, UP);
            } catch (IOException e) {
                St_addon_spraute.LOGGER.warn("Spraute: cannot read {}", path, e);
            }
        }
    }

    // ------------------------------------------------------------------ as a player of someone else's server

    /** The server's list of a project's files. */
    public static void index(Packets.ContentIndex msg) {
        // the host's own game reads the author's folder
        if (Minecraft.getInstance().hasSingleplayerServer()) return;
        try {
            Path d = cache().resolve(Images.slug(msg.project()));
            Files.createDirectories(d);
            Map<String, ProjectContent.Entry> have = ProjectContent.manifest(d), wanted = new LinkedHashMap<>();
            Map<String, ProjectContent.Entry> theirs = new LinkedHashMap<>();
            for (String line : msg.entries()) {
                ProjectContent.Entry e = ProjectContent.Entry.of(line);
                if (ProjectContent.allowed(e.path())) theirs.put(e.path(), e);
            }
            for (String old : have.keySet()) if (!theirs.containsKey(old)) Files.deleteIfExists(d.resolve(old));
            ProjectContent.removeEmpty(d);
            for (ProjectContent.Entry e : theirs.values()) {
                ProjectContent.Entry mine = have.get(e.path());
                if (mine == null || !mine.hash().equals(e.hash())) wanted.put(e.path(), e);
            }
            ACTIVE.put(msg.project(), d);
            if (wanted.isEmpty()) {
                MISSING.remove(msg.project());
                ready(msg.project());
            } else {
                MISSING.put(msg.project(), wanted);
                // in several packets: the game takes little at a time from a client
                List<String> batch = new ArrayList<>();
                int size = 0;
                for (String path : wanted.keySet()) {
                    batch.add(path);
                    size += path.length() * 3 + 4;
                    if (size > 20000) {
                        SprauteNetwork.sendToServer(new Packets.ContentRequest(msg.project(), batch));
                        batch = new ArrayList<>();
                        size = 0;
                    }
                }
                if (!batch.isEmpty()) SprauteNetwork.sendToServer(new Packets.ContentRequest(msg.project(), batch));
            }
        } catch (IOException | RuntimeException e) {
            St_addon_spraute.LOGGER.warn("Spraute: cannot take the project {} from the server", msg.project(), e);
        }
    }

    public static void received(Packets.ContentData msg) {
        Map<String, ProjectContent.Entry> missing = MISSING.get(msg.project());
        ProjectContent.Entry e = missing == null ? null : missing.get(msg.path());
        Path d = ACTIVE.get(msg.project());
        if (e == null || d == null) return;
        String key = msg.project() + "/" + msg.path();
        ByteArrayOutputStream part = PARTS.computeIfAbsent(key, k -> new ByteArrayOutputStream());
        part.writeBytes(msg.data());
        if (msg.index() + 1 < msg.total()) return;
        PARTS.remove(key);
        missing.remove(msg.path());
        byte[] all = part.toByteArray();
        if (ProjectContent.hash(all).equals(e.hash())) {
            try {
                Path f = d.resolve(msg.path()).normalize();
                if (f.startsWith(d)) {
                    Files.createDirectories(f.getParent());
                    Files.write(f, all);
                }
            } catch (IOException ex) {
                St_addon_spraute.LOGGER.warn("Spraute: cannot save {}", msg.path(), ex);
            }
        } else {
            St_addon_spraute.LOGGER.warn("Spraute: {} of {} came damaged", msg.path(), msg.project());
        }
        if (missing.isEmpty()) {
            MISSING.remove(msg.project());
            ready(msg.project());
        }
    }

    private static void ready(String project) {
        org.sprautestudio.st_addon_spraute.runtime.models.ModelLibrary.scan();
        try {
            org.sprautestudio.st_addon_spraute.editor.assets.ProjectImages.register(Project.openReadOnly(ACTIVE.get(project)));
        } catch (Exception e) {
            St_addon_spraute.LOGGER.warn("Spraute: cannot load the pictures of {}", project, e);
        }
    }

    // ------------------------------------------------------------------ going

    public static void onTick(ClientHooks.ClientTick e) {
        if (e.phase != TickPhase.END) return;
        if (Minecraft.getInstance().getConnection() == null) return;
        for (int i = 0; i < PIECES_PER_TICK && !UP.isEmpty(); i++) SprauteNetwork.sendToServer(UP.poll());
        // a server that never answers must not hold a deploy back for ever
        if (!UPLOAD_AGE.isEmpty()) {
            for (String project : new ArrayList<>(UPLOAD_AGE.keySet())) {
                int age = UPLOAD_AGE.merge(project, 1, Integer::sum);
                if (age < UPLOAD_TIMEOUT_TICKS) continue;
                UPLOAD_AGE.remove(project);
                Runnable then = AFTER_UPLOAD.remove(project);
                if (then != null) then.run();
            }
        }
    }

    public static void onLogout(ClientHooks.LoggingOut e) {
        boolean had = !ACTIVE.isEmpty();
        ACTIVE.clear();
        MISSING.clear();
        PARTS.clear();
        UP.clear();
        AFTER_UPLOAD.clear();
        UPLOAD_AGE.clear();
        if (had) org.sprautestudio.st_addon_spraute.runtime.models.ModelLibrary.scan();
    }
}
