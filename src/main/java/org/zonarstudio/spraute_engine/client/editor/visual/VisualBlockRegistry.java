package org.zonarstudio.spraute_engine.client.editor.visual;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.zonarstudio.spraute_engine.client.editor.EditorI18n;

/**
 * Loads visual block plugins from {@code spraute_engine/plugins/} and exposes
 * the block definitions to the in-game visual editor.
 *
 * <p>Plugin layout (same as Spraute Studio):
 * <pre>
 * plugins/&lt;name&gt;/
 *   plugin.json        — { name, enabled, ... } (optional; folder alone is enough)
 *   categories.json    — category → "#hex" | { color, note }
 *   blocks_order.json  — category → [blockId, ...]
 *   blocks/*.spr       — block definitions (#\ prefix optional)
 *   scripts/*.spr      — runtime scripts, mirrored to scripts/plugins/&lt;name&gt;/
 *                        (included in .splugin export; installed into scripts/ on import)
 *   lang/en.json       — optional RU→EN UI map for blocks/categories/plugin meta
 * </pre>
 *
 * <p>Bundled plugins shipped inside the mod jar ({@code /bundled_plugins/...})
 * are unpacked into the workspace on first load so users can inspect and edit them.
 */
public final class VisualBlockRegistry {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final class Category {
        public final String name;
        public String colorHex = "#555555";
        public String note;
        public final List<String> blockIds = new ArrayList<>();

        Category(String name) { this.name = name; }
    }

    /** Metadata of one plugin folder for the plugin manager UI. */
    public static final class PluginInfo {
        public String folder;          // folder name inside plugins/
        public String name;
        public String author = "";
        public String description = "";
        public String version = "";
        public boolean enabled = true;
        public boolean bundled;        // shipped inside the mod jar (procode etc.)
        public int blockCount;
        public int categoryCount;
        public int scriptCount;
    }

    private static final Map<String, VisualBlockDef> blocks = new LinkedHashMap<>();
    private static final Map<String, Category> categories = new LinkedHashMap<>();
    private static List<Category> orderedCategoriesCache;
    private static final Map<String, VisualBlockDef> missingPlaceholders = new LinkedHashMap<>();
    private static final List<PluginInfo> plugins = new ArrayList<>();
    private static final java.util.Set<String> bundledNames = new java.util.HashSet<>();
    private static boolean loaded;

    private VisualBlockRegistry() {}

    public static synchronized void ensureLoaded() {
        if (!loaded) reload();
    }

    public static synchronized void reload() {
        blocks.clear();
        categories.clear();
        orderedCategoriesCache = null;
        missingPlaceholders.clear();
        plugins.clear();
        PluginI18n.clear();
        Path root = workspaceRoot();
        Path pluginsDir = root.resolve("plugins");
        try {
            Files.createDirectories(pluginsDir);
            bundledNames.clear();
            bundledNames.addAll(org.zonarstudio.spraute_engine.resource.BundledPluginUnpacker.unpackInto(root));
        } catch (IOException e) {
            LOGGER.error("[VisualBlocks] Cannot prepare plugins dir: {}", e.toString());
        }
        List<Path> pluginRoots = new ArrayList<>();
        try (var stream = Files.list(pluginsDir)) {
            stream.filter(Files::isDirectory).sorted().forEach(pluginRoots::add);
        } catch (IOException ignored) {
        }
        // Order: explicit plugins_order.json first, then procode, then alphabetical
        List<String> order = readPluginOrder();
        pluginRoots.sort((a, b) -> {
            String na = a.getFileName().toString(), nb = b.getFileName().toString();
            int ia = indexInOrder(order, na), ib = indexInOrder(order, nb);
            if (ia != ib) return Integer.compare(ia, ib);
            boolean pa = na.equalsIgnoreCase("procode"), pb = nb.equalsIgnoreCase("procode");
            if (pa != pb) return pa ? -1 : 1;
            return na.compareToIgnoreCase(nb);
        });
        for (Path pluginRoot : pluginRoots) {
            try {
                loadPlugin(pluginRoot, root);
            } catch (Exception e) {
                LOGGER.error("[VisualBlocks] Failed to load plugin {}: {}", pluginRoot.getFileName(), e.toString());
            }
        }
        applySavedCategoryOrder();
        registerBuiltinBlocks();
        loaded = true;
        LOGGER.info("[VisualBlocks] Loaded {} blocks in {} categories from {} plugins",
                blocks.size(), categories.size(), pluginRoots.size());
    }

    /**
     * Engine-owned blocks (not shipped as procode). Not added to any palette category —
     * created only via editor UI (e.g. canvas context menu → mark).
     */
    private static void registerBuiltinBlocks() {
        VisualBlockDef mark = new VisualBlockDef("mark", "spraute");
        mark.builtin = true;
        mark.category = "";
        mark.colorHex = "#f59e0b";
        mark.shape = VisualBlockDef.Shape.STATEMENT;
        mark.body.add(new VisualBlockDef.BodyItem(VisualBlockDef.ItemKind.ROW,
                List.of(
                        new VisualBlockDef.Label("метка:"),
                        new VisualBlockDef.Field("name", "text", "Метка")
                ),
                null, List.of()));
        // No template/code — navigation-only, does not emit .spr.
        blocks.put(mark.fullId, mark);
    }

    // ==================== Lookup ====================

    /** Resolves a block type; normalizes legacy {@code ns:id} to {@code ns.id}. Null if unknown. */
    public static VisualBlockDef find(String type) {
        ensureLoaded();
        if (type == null) return null;
        VisualBlockDef def = blocks.get(type);
        if (def == null && type.contains(":")) def = blocks.get(type.replace(':', '.'));
        if (def == null) def = missingPlaceholders.get(type);
        return def;
    }

    /**
     * Like {@link #find} but registers a red «missing block» placeholder for unknown
     * types instead of returning null — mirrors Studio's registerMissingBlockType.
     */
    public static VisualBlockDef findOrPlaceholder(String type) {
        VisualBlockDef def = find(type);
        if (def != null) return def;
        return missingPlaceholders.computeIfAbsent(type, t -> {
            VisualBlockDef p = new VisualBlockDef(t, "");
            p.missing = true;
            p.category = "";
            p.colorHex = "#b91c1c";
            p.shape = VisualBlockDef.Shape.STATEMENT;
            fillMissingBody(p, t);
            return p;
        });
    }

    /** Always Russian source text — localized at draw via {@code EditorI18n.localize}. */
    private static void fillMissingBody(VisualBlockDef p, String type) {
        String label = type.contains(".") ? type.substring(type.lastIndexOf('.') + 1) : type;
        p.body.clear();
        p.body.add(new VisualBlockDef.BodyItem(VisualBlockDef.ItemKind.ROW,
                List.of(new VisualBlockDef.Label("⚠ устаревший блок: " + label)), null, List.of()));
        p.body.add(new VisualBlockDef.BodyItem(VisualBlockDef.ItemKind.ROW,
                List.of(new VisualBlockDef.Field("CODE", "text",
                        "// тип «" + type + "» не найден — обновите блок")),
                null, List.of()));
        p.body.add(new VisualBlockDef.BodyItem(VisualBlockDef.ItemKind.TEMPLATE,
                null, "/* MISSING BLOCK: " + type + " */\n{CODE}", List.of()));
    }

    public static Map<String, Category> categoryMap() {
        ensureLoaded();
        return categories;
    }

    /** Categories in palette order (user-reordered via drag, then plugin load order). */
    public static synchronized List<Category> orderedCategories() {
        ensureLoaded();
        if (orderedCategoriesCache == null) {
            orderedCategoriesCache = List.copyOf(categories.values());
        }
        return orderedCategoriesCache;
    }

    public static synchronized void moveCategoryRelative(String dragged, String target, boolean after) {
        ensureLoaded();
        if (dragged == null || target == null || dragged.equals(target)) return;
        if (!categories.containsKey(dragged) || !categories.containsKey(target)) return;
        List<String> names = new ArrayList<>(categories.keySet());
        names.remove(dragged);
        int to = names.indexOf(target);
        if (to < 0) return;
        if (after) to++;
        names.add(to, dragged);
        rebuildCategoryOrder(names);
        orderedCategoriesCache = null;
        saveCategoryOrder(names);
    }

    private static Path categoryOrderFile() {
        return workspaceRoot().resolve("editor_category_order.json");
    }

    private static List<String> readCategoryOrder() {
        List<String> out = new ArrayList<>();
        Path f = categoryOrderFile();
        if (!Files.isRegularFile(f)) return out;
        try {
            for (JsonElement el : JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonArray()) {
                if (el.isJsonPrimitive()) out.add(el.getAsString());
            }
        } catch (Exception ignored) {}
        return out;
    }

    private static void saveCategoryOrder(List<String> names) {
        try {
            Files.createDirectories(categoryOrderFile().getParent());
            var arr = new com.google.gson.JsonArray();
            for (String s : names) arr.add(s);
            Files.writeString(categoryOrderFile(),
                    new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(arr),
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.error("[VisualBlocks] Cannot save category order: {}", e.toString());
        }
    }

    private static void applySavedCategoryOrder() {
        List<String> order = readCategoryOrder();
        if (order.isEmpty()) return;
        List<String> names = new ArrayList<>();
        for (String name : order) {
            if (categories.containsKey(name) && !names.contains(name)) names.add(name);
        }
        for (String name : categories.keySet()) {
            if (!names.contains(name)) names.add(name);
        }
        rebuildCategoryOrder(names);
    }

    private static void rebuildCategoryOrder(List<String> names) {
        Map<String, Category> reordered = new LinkedHashMap<>();
        for (String name : names) {
            Category c = categories.get(name);
            if (c != null) reordered.put(name, c);
        }
        for (var e : categories.entrySet()) {
            reordered.putIfAbsent(e.getKey(), e.getValue());
        }
        categories.clear();
        categories.putAll(reordered);
        orderedCategoriesCache = null;
    }

    public static Map<String, VisualBlockDef> all() {
        ensureLoaded();
        return blocks;
    }

    // ==================== Plugin loading ====================

    private static void loadPlugin(Path pluginRoot, Path workspaceRoot) throws IOException {
        String folderName = pluginRoot.getFileName().toString();
        String name = folderName;
        boolean enabled = true;
        PluginInfo info = new PluginInfo();
        info.folder = folderName;
        info.bundled = bundledNames.contains(folderName.toLowerCase(Locale.ROOT));
        Path pluginJson = pluginRoot.resolve("plugin.json");
        if (Files.isRegularFile(pluginJson)) {
            try {
                JsonObject meta = JsonParser.parseString(Files.readString(pluginJson, StandardCharsets.UTF_8)).getAsJsonObject();
                if (meta.has("name") && !meta.get("name").isJsonNull()) name = meta.get("name").getAsString();
                if (meta.has("enabled") && !meta.get("enabled").isJsonNull()) enabled = meta.get("enabled").getAsBoolean();
                if (meta.has("author") && !meta.get("author").isJsonNull()) info.author = meta.get("author").getAsString();
                if (meta.has("description") && !meta.get("description").isJsonNull()) info.description = meta.get("description").getAsString();
                if (meta.has("version") && !meta.get("version").isJsonNull()) info.version = meta.get("version").getAsString();
            } catch (Exception e) {
                LOGGER.warn("[VisualBlocks] Bad plugin.json in {}: {}", folderName, e.toString());
            }
        }
        info.name = name;
        info.enabled = enabled;
        info.scriptCount = countPluginScripts(pluginRoot);
        plugins.add(info);
        String namespace = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
        PluginI18n.load(pluginRoot, folderName, namespace);
        if (!enabled) {
            LOGGER.info("[VisualBlocks] Plugin '{}' disabled, skipping", name);
            return;
        }

        // categories.json — colors/notes (applied before blocks so block colors follow the category)
        Map<String, String[]> catMeta = new LinkedHashMap<>(); // name -> [color, note]
        Path catsFile = pluginRoot.resolve("categories.json");
        if (Files.isRegularFile(catsFile)) {
            try {
                JsonObject cats = JsonParser.parseString(Files.readString(catsFile, StandardCharsets.UTF_8)).getAsJsonObject();
                for (var e : cats.entrySet()) {
                    JsonElement v = e.getValue();
                    if (v.isJsonObject()) {
                        JsonObject o = v.getAsJsonObject();
                        String color = o.has("color") ? o.get("color").getAsString() : "#555555";
                        String note = o.has("note") ? o.get("note").getAsString() : null;
                        catMeta.put(e.getKey(), new String[]{color, note});
                    } else if (v.isJsonPrimitive()) {
                        catMeta.put(e.getKey(), new String[]{v.getAsString(), null});
                    }
                }
            } catch (Exception e) {
                LOGGER.warn("[VisualBlocks] Bad categories.json in {}: {}", folderName, e.toString());
            }
        }
        for (var e : catMeta.entrySet()) {
            Category c = categories.computeIfAbsent(e.getKey(), Category::new);
            c.colorHex = e.getValue()[0];
            if (e.getValue()[1] != null) c.note = e.getValue()[1];
        }

        // blocks/*.spr
        Path blocksDir = pluginRoot.resolve("blocks");
        java.util.Set<String> pluginCats = new java.util.HashSet<>();
        if (Files.isDirectory(blocksDir)) {
            List<Path> files = new ArrayList<>();
            try (var stream = Files.list(blocksDir)) {
                stream.filter(p -> p.getFileName().toString().endsWith(".spr")).sorted().forEach(files::add);
            }
            for (Path f : files) {
                String text = Files.readString(f, StandardCharsets.UTF_8);
                for (VisualBlockDef def : VisualBlockDslParser.parse(text, namespace)) {
                    info.blockCount++;
                    pluginCats.add(def.category);
                    Category cat = categories.computeIfAbsent(def.category, Category::new);
                    if (catMeta.containsKey(def.category)) {
                        def.colorHex = cat.colorHex;
                    } else if (!"#555555".equals(def.colorHex) && "#555555".equals(cat.colorHex)) {
                        cat.colorHex = def.colorHex;
                    } else if ("#555555".equals(def.colorHex)) {
                        def.colorHex = cat.colorHex;
                    }
                    if (!cat.blockIds.contains(def.fullId)) cat.blockIds.add(def.fullId);
                    blocks.put(def.fullId, def);
                }
            }
        }
        info.categoryCount = pluginCats.size();

        // blocks_order.json — palette order inside categories
        Path orderFile = pluginRoot.resolve("blocks_order.json");
        if (Files.isRegularFile(orderFile)) {
            try {
                JsonObject order = JsonParser.parseString(Files.readString(orderFile, StandardCharsets.UTF_8)).getAsJsonObject();
                for (var e : order.entrySet()) {
                    Category cat = categories.get(e.getKey());
                    if (cat == null || !e.getValue().isJsonArray()) continue;
                    List<String> sorted = new ArrayList<>();
                    for (JsonElement el : e.getValue().getAsJsonArray()) {
                        String fullId = namespace + "." + el.getAsString();
                        if (cat.blockIds.remove(fullId)) sorted.add(fullId);
                    }
                    cat.blockIds.addAll(0, sorted);
                }
            } catch (Exception e) {
                LOGGER.warn("[VisualBlocks] Bad blocks_order.json in {}: {}", folderName, e.toString());
            }
        }

        // scripts/ — mirror into scripts/plugins/<folder>/ so import("lib") works
        installPluginScripts(folderName, false);
    }

    private static int countPluginScripts(Path pluginRoot) {
        Path scriptsDir = pluginRoot.resolve("scripts");
        if (!Files.isDirectory(scriptsDir)) return 0;
        try (var stream = Files.walk(scriptsDir)) {
            return (int) stream.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".spr"))
                    .count();
        } catch (IOException e) {
            return 0;
        }
    }

    /**
     * Copies plugins/&lt;folder&gt;/scripts into scripts/plugins/&lt;folder&gt;.
     * @param overwrite if true, replace existing files; if false, only copy missing ones
     */
    public static synchronized void installPluginScripts(String folder, boolean overwrite) {
        org.zonarstudio.spraute_engine.resource.BundledPluginUnpacker
                .installPluginScripts(workspaceRoot(), folder, overwrite);
        LOGGER.info("[VisualBlocks] Installed scripts of '{}' → scripts/plugins/{}", folder, folder);
    }

    /**
     * Pulls edited workspace scripts back into the plugin folder before export,
     * so {@code scripts/plugins/&lt;folder&gt;} changes are saved into the .splugin.
     */
    private static void pullScriptsIntoPlugin(String folder) {
        Path workspaceScripts = workspaceRoot().resolve("scripts").resolve("plugins").resolve(folder);
        if (!Files.isDirectory(workspaceScripts)) return;
        Path pluginScripts = pluginsDir().resolve(folder).resolve("scripts");
        try {
            Files.createDirectories(pluginScripts);
            try (var stream = Files.walk(workspaceScripts)) {
                for (Path file : stream.filter(Files::isRegularFile).toList()) {
                    Path target = pluginScripts.resolve(workspaceScripts.relativize(file).toString()).normalize();
                    if (!target.startsWith(pluginScripts)) continue;
                    Files.createDirectories(target.getParent());
                    Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        } catch (IOException e) {
            LOGGER.warn("[VisualBlocks] Failed to pull scripts into plugin {}: {}", folder, e.toString());
        }
    }

    // Bundled plugin unpack lives in BundledPluginUnpacker (also used on dedicated servers).

    private static Path workspaceRoot() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("spraute_engine");
    }

    // ==================== Plugin order ====================

    private static Path pluginOrderFile() {
        return pluginsDir().resolve("plugins_order.json");
    }

    private static List<String> readPluginOrder() {
        List<String> out = new ArrayList<>();
        Path f = pluginOrderFile();
        if (!Files.isRegularFile(f)) return out;
        try {
            for (JsonElement el : JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonArray()) {
                out.add(el.getAsString());
            }
        } catch (Exception ignored) {}
        return out;
    }

    private static int indexInOrder(List<String> order, String folder) {
        for (int i = 0; i < order.size(); i++) {
            if (order.get(i).equalsIgnoreCase(folder)) return i;
        }
        return Integer.MAX_VALUE;
    }

    /** Moves a plugin up/down in the load order (affects palette category order). */
    public static synchronized void movePlugin(String folder, int delta) {
        // Start from the effective current order (as shown in the manager).
        List<String> current = new ArrayList<>();
        for (PluginInfo p : plugins) current.add(p.folder);
        int idx = -1;
        for (int i = 0; i < current.size(); i++) {
            if (current.get(i).equalsIgnoreCase(folder)) { idx = i; break; }
        }
        if (idx < 0) return;
        int to = Math.max(0, Math.min(current.size() - 1, idx + delta));
        if (to == idx) return;
        current.add(to, current.remove(idx));
        try {
            var arr = new com.google.gson.JsonArray();
            for (String s : current) arr.add(s);
            Files.writeString(pluginOrderFile(),
                    new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(arr),
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.error("[VisualBlocks] Cannot save plugin order: {}", e.toString());
        }
        reload();
    }

    // ==================== Plugin manager API ====================

    /** Metadata of all plugin folders found during the last load (incl. disabled). */
    public static synchronized List<PluginInfo> plugins() {
        ensureLoaded();
        return List.copyOf(plugins);
    }

    public static Path pluginsDir() {
        return workspaceRoot().resolve("plugins");
    }

    /** Enables/disables a plugin by rewriting its plugin.json, then reloads everything. */
    public static synchronized boolean setEnabled(String folder, boolean enabled) {
        Path dir = pluginsDir().resolve(folder);
        if (!Files.isDirectory(dir)) return false;
        Path json = dir.resolve("plugin.json");
        try {
            JsonObject meta = new JsonObject();
            if (Files.isRegularFile(json)) {
                try {
                    meta = JsonParser.parseString(Files.readString(json, StandardCharsets.UTF_8)).getAsJsonObject();
                } catch (Exception ignored) {}
            }
            if (!meta.has("name")) meta.addProperty("name", folder);
            meta.addProperty("enabled", enabled);
            Files.writeString(json, new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(meta),
                    StandardCharsets.UTF_8);
            if (enabled) installPluginScripts(folder, true);
            reload();
            return true;
        } catch (IOException e) {
            LOGGER.error("[VisualBlocks] Cannot toggle plugin {}: {}", folder, e.toString());
            return false;
        }
    }

    /** Creates an empty plugin skeleton (plugin.json, categories, blocks/, README). */
    public static synchronized String createPlugin(String name, String author, String description) {
        if (name == null || !name.matches("[A-Za-z0-9_\\-]+")) {
            return EditorI18n.t("Имя: только латиница, цифры, «-» и «_»");
        }
        Path dir = pluginsDir().resolve(name);
        if (Files.exists(dir)) return EditorI18n.t("Плагин с таким именем уже существует");
        try {
            Files.createDirectories(dir.resolve("blocks"));
            Files.createDirectories(dir.resolve("scripts"));
            Files.createDirectories(dir.resolve("lang"));
            JsonObject meta = new JsonObject();
            meta.addProperty("name", name);
            meta.addProperty("author", author == null ? "" : author);
            meta.addProperty("description", description == null ? "" : description);
            meta.addProperty("version", "1.0.0");
            meta.addProperty("enabled", true);
            var gson = new com.google.gson.GsonBuilder().setPrettyPrinting().create();
            Files.writeString(dir.resolve("plugin.json"), gson.toJson(meta), StandardCharsets.UTF_8);
            Files.writeString(dir.resolve("categories.json"), "{\n    \"" + name + "\": \"#38bdf8\"\n}",
                    StandardCharsets.UTF_8);
            Files.writeString(dir.resolve("blocks_order.json"), "{}", StandardCharsets.UTF_8);
            // Optional English UI map (keys = Russian/source strings from .spr / categories / plugin.json)
            String descKey = description == null || description.isBlank() ? "" : description;
            String enJson = "{\n"
                    + "  \"plugin.name\": " + gson.toJson(name) + ",\n"
                    + (descKey.isEmpty() ? "" : "  \"plugin.description\": " + gson.toJson(descKey) + ",\n")
                    + "  \"привет от " + name + "\": " + gson.toJson("hello from " + name) + ",\n"
                    + "  \"мир\": \"world\"\n"
                    + "}\n";
            Files.writeString(dir.resolve("lang").resolve("en.json"), enJson, StandardCharsets.UTF_8);
            Files.writeString(dir.resolve("blocks").resolve("example.spr"), """
                    #\\ block: hello
                    #\\ category: %s
                    #\\ color: #38bdf8
                    #\\ shape: statement
                    #\\
                    #\\ row: "привет от %s" (who: text: мир)
                    #\\ template: chat("Привет, {who}!")
                    """.formatted(name, name), StandardCharsets.UTF_8);
            Files.writeString(dir.resolve("README.md"),
                    EditorI18n.t("# Плагин ") + name + EditorI18n.t("\n\n**Автор:** ") + (author == null ? "" : author)
                    + "\n\n" + (description == null ? "" : description)
                    + EditorI18n.t("\n\nКладите `.spr` с блоками в `blocks/`, runtime-скрипты — в `scripts/`.\n")
                    + EditorI18n.t("Переводы UI: `lang/en.json` (ключ = русский текст из блоков/категорий).\n")
                    + EditorI18n.t("При установке `.splugin` скрипты копируются в `spraute_engine/scripts/plugins/")
                    + name + "/`.\n",
                    StandardCharsets.UTF_8);
            reload();
            return null;
        } catch (IOException e) {
            return EditorI18n.t("Ошибка: ") + e.getMessage();
        }
    }

    /** Updates plugin.json metadata (name/author/version/description). Returns null on success. */
    public static synchronized String updateMeta(String folder, String name, String author,
                                                 String version, String description) {
        Path dir = pluginsDir().resolve(folder);
        Path json = dir.resolve("plugin.json");
        if (!Files.isDirectory(dir)) return EditorI18n.t("Папка плагина не найдена");
        try {
            JsonObject meta = new JsonObject();
            if (Files.isRegularFile(json)) {
                try {
                    meta = JsonParser.parseString(Files.readString(json, StandardCharsets.UTF_8)).getAsJsonObject();
                } catch (Exception ignored) {}
            }
            if (name != null && !name.isBlank()) meta.addProperty("name", name.trim());
            meta.addProperty("author", author == null ? "" : author.trim());
            meta.addProperty("version", version == null ? "" : version.trim());
            meta.addProperty("description", description == null ? "" : description.trim());
            Files.writeString(json, new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(meta),
                    StandardCharsets.UTF_8);
            reload();
            return null;
        } catch (IOException e) {
            return EditorI18n.t("Ошибка: ") + e.getMessage();
        }
    }

    /** Installs a plugin from a .splugin/.zip archive into plugins/. Returns null on success. */
    public static synchronized String installZip(Path zipFile) {
        if (zipFile == null || !Files.isRegularFile(zipFile)) return EditorI18n.t("Файл не найден");
        String base = zipFile.getFileName().toString().replaceAll("(?i)\\.(zip|splugin)$", "");
        try (java.util.zip.ZipInputStream zin =
                     new java.util.zip.ZipInputStream(Files.newInputStream(zipFile), StandardCharsets.UTF_8)) {
            // If every entry is under one shared top folder, drop that prefix.
            List<String> names = new ArrayList<>();
            java.util.zip.ZipEntry e;
            while ((e = zin.getNextEntry()) != null) {
                if (!e.isDirectory()) names.add(e.getName().replace('\\', '/'));
            }
            if (names.isEmpty()) return EditorI18n.t("Архив пуст");
            String common = null;
            for (String n : names) {
                int slash = n.indexOf('/');
                String top = slash < 0 ? null : n.substring(0, slash);
                if (top == null) { common = null; break; }
                if (common == null) common = top;
                else if (!common.equals(top)) { common = null; break; }
            }
            String targetName = common != null ? common : base;
            Path target = pluginsDir().resolve(targetName);
            try (java.util.zip.ZipInputStream zin2 =
                         new java.util.zip.ZipInputStream(Files.newInputStream(zipFile), StandardCharsets.UTF_8)) {
                java.util.zip.ZipEntry en;
                while ((en = zin2.getNextEntry()) != null) {
                    if (en.isDirectory()) continue;
                    String rel = en.getName().replace('\\', '/');
                    if (common != null) rel = rel.substring(common.length() + 1);
                    if (rel.isEmpty() || rel.contains("..")) continue;
                    Path dest = target.resolve(rel).normalize();
                    if (!dest.startsWith(target)) continue;    // zip-slip guard
                    Files.createDirectories(dest.getParent());
                    Files.copy(zin2, dest, StandardCopyOption.REPLACE_EXISTING);
                }
            }
            // Force-install scripts into scripts/plugins/<name>/ on every .splugin install
            installPluginScripts(targetName, true);
            reload();
            return null;
        } catch (IOException e) {
            return EditorI18n.t("Ошибка: ") + e.getMessage();
        }
    }

    /**
     * Packs {@code plugins/&lt;folder&gt;/} into a {@code .splugin}/{@code .zip} archive
     * (flat entries, same layout Studio expects on import). Returns null on success.
     */
    public static synchronized String exportZip(String folder, Path destFile) {
        if (folder == null || folder.isBlank()) return EditorI18n.t("Не указан плагин");
        if (destFile == null) return EditorI18n.t("Не указан файл");
        Path src = pluginsDir().resolve(folder);
        if (!Files.isDirectory(src)) return EditorI18n.t("Плагин не найден: ") + folder;
        Path dest = destFile.toAbsolutePath().normalize();
        String name = dest.getFileName().toString();
        if (!name.toLowerCase(Locale.ROOT).endsWith(".splugin") && !name.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            dest = dest.resolveSibling(name + ".splugin");
        }
        try {
            // Include latest scripts from scripts/plugins/<folder> inside the archive
            pullScriptsIntoPlugin(folder);
            if (dest.getParent() != null) Files.createDirectories(dest.getParent());
            try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(
                    Files.newOutputStream(dest), StandardCharsets.UTF_8)) {
                try (var walk = Files.walk(src)) {
                    List<Path> files = walk.filter(Files::isRegularFile)
                            .sorted()
                            .toList();
                    for (Path file : files) {
                        String rel = src.relativize(file).toString().replace('\\', '/');
                        if (rel.isEmpty() || rel.contains("..")) continue;
                        zos.putNextEntry(new java.util.zip.ZipEntry(rel));
                        Files.copy(file, zos);
                        zos.closeEntry();
                    }
                }
            }
            return null;
        } catch (IOException e) {
            return EditorI18n.t("Ошибка: ") + e.getMessage();
        }
    }
}
