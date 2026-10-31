package org.sprautestudio.spraute_engine.editor.project;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.Minecraft;
import org.sprautestudio.spraute_engine.SprauteEngine;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/** Root folder of all projects (.minecraft/spraute) and editor settings. */
public final class Workspace {
    public static final class Settings {
        public int uiScale;
        /** The editor's size in percent: 100 — twice Minecraft's pixels (the old "2x"); 0 — as Minecraft. */
        public int uiPercent = -1;

        /** Physical pixels per editor pixel (0 — as Minecraft). */
        public double physicalScale() {
            if (uiPercent < 0) uiPercent = uiScale > 0 ? uiScale * 50 : 0;
            return uiPercent <= 0 ? 0 : uiPercent / 50.0;
        }
        public boolean minimap = true;
        public String lastProject;
        public int sidebarW = 200, rightW = 250, bottomH = 170;
        public boolean showSidebar = true, showRight = true, showBottom = true;
        /** "/editor on": the editor key opens and closes the editor. */
        public boolean editorMode;
        /** Draw the project's zones in the world while the editor mode is on. */
        public boolean showZones = true;
        /** The MCP server for an AI assistant (only this computer can connect). */
        public boolean mcp = true;
        public int mcpPort = 25599;
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Settings settings;

    public static Path root() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("spraute");
    }

    public static Path projectsDir() {
        return root().resolve("projects");
    }

    public static Settings settings() {
        if (settings == null) {
            Path f = root().resolve("settings.json");
            try {
                if (Files.exists(f)) settings = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), Settings.class);
            } catch (Exception e) {
                SprauteEngine.LOGGER.warn("Failed to read Spraute settings", e);
            }
            if (settings == null) settings = new Settings();
        }
        return settings;
    }

    public static void saveSettings() {
        try {
            Files.createDirectories(root());
            Files.writeString(root().resolve("settings.json"), GSON.toJson(settings()), StandardCharsets.UTF_8);
        } catch (IOException e) {
            SprauteEngine.LOGGER.warn("Failed to save Spraute settings", e);
        }
    }

    public record ProjectInfo(String name, Path dir, long opened, int files) {}

    public static List<ProjectInfo> projects() {
        List<ProjectInfo> list = new ArrayList<>();
        Path dir = projectsDir();
        if (!Files.isDirectory(dir)) return list;
        try (Stream<Path> s = Files.list(dir)) {
            for (Path p : (Iterable<Path>) s.filter(Files::isDirectory)::iterator) {
                try {
                    Project pr = Project.openReadOnly(p);
                    list.add(new ProjectInfo(pr.name(), p, pr.meta.opened, pr.files().size()));
                } catch (Exception e) {
                    list.add(new ProjectInfo(p.getFileName().toString(), p, 0, 0));
                }
            }
        } catch (IOException e) {
            SprauteEngine.LOGGER.warn("Failed to list projects", e);
        }
        list.sort(Comparator.comparingLong(ProjectInfo::opened).reversed());
        return list;
    }

    public static Project createProject(String name) throws IOException {
        Path dir = projectsDir().resolve(Project.sanitize(name));
        if (Files.exists(dir)) throw new IOException("Проект с таким именем уже существует");
        return Project.create(dir, name);
    }

    public static void deleteProject(Path dir) throws IOException {
        if (!dir.startsWith(projectsDir())) throw new IOException("Not a project directory");
        try (Stream<Path> s = Files.walk(dir)) {
            List<Path> paths = s.sorted(Comparator.reverseOrder()).toList();
            for (Path p : paths) Files.deleteIfExists(p);
        }
    }
}
