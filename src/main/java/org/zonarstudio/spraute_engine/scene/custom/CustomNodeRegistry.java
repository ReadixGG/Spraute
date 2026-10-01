package org.zonarstudio.spraute_engine.scene.custom;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import org.zonarstudio.spraute_engine.scene.SceneDocument;
import org.zonarstudio.spraute_engine.scene.SceneNode;
import org.zonarstudio.spraute_engine.scene.SceneNodeType;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of {@code declare section} / {@code declare node type} / {@code declare inspector}
 * from scripts attached as {@link SceneNodeType#SCRIPT} nodes of a scene.
 * Hierarchy and schema lookup use the <b>active/open</b> scene only — not the whole scripts folder.
 */
public final class CustomNodeRegistry {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final Map<String, SceneSchema> BY_SCENE = new ConcurrentHashMap<>();
    /** Scene id whose types appear in Hierarchy / default {@link #get(String)}. */
    private static volatile String editorSceneId;
    private static boolean parsed;

    private CustomNodeRegistry() {}

    public static void ensureParsed() {
        if (!parsed) reload();
    }

    /**
     * Reload schema for the editor's open scene (if any). Does not scan the whole scripts folder.
     */
    public static synchronized void reload() {
        parsed = true;
        SceneDocument doc = editorDocumentOrNull();
        if (doc == null) {
            editorSceneId = null;
            LOGGER.debug("[CustomNode] No open scene — custom types cleared from Hierarchy");
            return;
        }
        bindEditorScene(doc);
    }

    /** Parse SCRIPT nodes of {@code doc} and make it the editor-facing active schema. */
    public static synchronized void bindEditorScene(SceneDocument doc) {
        parsed = true;
        if (doc == null) {
            editorSceneId = null;
            return;
        }
        ensureForDocument(doc);
        editorSceneId = doc.id;
    }

    /** Ensure cache for a scene (runtime spawn / trade) without changing Hierarchy focus. */
    public static synchronized SceneSchema ensureForDocument(SceneDocument doc) {
        parsed = true;
        if (doc == null) return SceneSchema.EMPTY;
        Set<String> names = collectScriptNames(doc);
        String fingerprint = scriptFingerprint(doc, names);
        SceneSchema existing = BY_SCENE.get(doc.id);
        if (existing != null && fingerprint.equals(existing.fingerprint)) return existing;
        SceneSchema schema = parseDocument(doc, names, fingerprint);
        BY_SCENE.put(doc.id, schema);
        int inspectorCount = 0;
        for (List<CustomNodeTypeDef> list : schema.inspectorsByScript.values()) inspectorCount += list.size();
        LOGGER.info("[CustomNode] Scene '{}': {} type(s), {} section(s), {} inspector(s) from SCRIPT nodes",
                doc.id, schema.types.size(), schema.sections.size(), inspectorCount);
        return schema;
    }

    /** Drop cached schema so the next ensure/bind re-reads .spr files. */
    public static synchronized void invalidate(String sceneId) {
        if (sceneId == null) return;
        BY_SCENE.remove(sceneId);
    }

    public static synchronized void invalidateAll() {
        BY_SCENE.clear();
        editorSceneId = null;
        parsed = false;
    }

    public static CustomNodeTypeDef get(String typeId) {
        ensureParsed();
        if (typeId == null) return null;
        SceneSchema editor = editorSchema();
        if (editor != null) {
            CustomNodeTypeDef def = editor.types.get(typeId);
            if (def != null) return def;
        }
        // Java-declared types (GenericNodeTypeRegistry.register) are global, not per-scene —
        // fall back to them whenever no .spr `declare` shadows the same id.
        return GenericNodeTypeRegistry.def(typeId);
    }

    public static CustomNodeTypeDef get(SceneDocument doc, String typeId) {
        if (typeId == null) return null;
        if (doc == null) return get(typeId);
        CustomNodeTypeDef def = ensureForDocument(doc).types.get(typeId);
        return def != null ? def : GenericNodeTypeRegistry.def(typeId);
    }

    public static boolean isRegistered(String typeId) {
        return get(typeId) != null;
    }

    public static boolean isRegistered(SceneDocument doc, String typeId) {
        return get(doc, typeId) != null;
    }

    public static Collection<CustomNodeTypeDef> types() {
        ensureParsed();
        SceneSchema s = editorSchema();
        List<CustomNodeTypeDef> out = new ArrayList<>();
        if (s != null) out.addAll(s.types.values());
        out.addAll(GenericNodeTypeRegistry.defs());
        return Collections.unmodifiableList(out);
    }

    public static List<String> sections() {
        ensureParsed();
        SceneSchema s = editorSchema();
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (s != null) out.addAll(s.sections);
        for (CustomNodeTypeDef def : GenericNodeTypeRegistry.defs()) {
            out.add(def.section != null && !def.section.isBlank() ? def.section : "Кастом");
        }
        return List.copyOf(out);
    }

    public static List<CustomNodeTypeDef> typesInSection(String section) {
        ensureParsed();
        SceneSchema s = editorSchema();
        List<CustomNodeTypeDef> out = new ArrayList<>();
        String key = section == null ? "" : section;
        if (s != null) {
            for (CustomNodeTypeDef t : s.types.values()) {
                if (key.equals(t.section != null ? t.section : "Кастом")) out.add(t);
            }
        }
        for (CustomNodeTypeDef t : GenericNodeTypeRegistry.defs()) {
            String tSection = t.section != null && !t.section.isBlank() ? t.section : "Кастом";
            if (key.equals(tSection)) out.add(t);
        }
        return out;
    }

    public static Set<String> typeIds() {
        ensureParsed();
        SceneSchema s = editorSchema();
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (s != null) out.addAll(s.types.keySet());
        for (GenericNodeType t : GenericNodeTypeRegistry.types()) out.add(t.id());
        return Collections.unmodifiableSet(out);
    }

    /**
     * {@code declare inspector} schemas from direct SCRIPT / VISUAL_SCRIPT children of {@code host}.
     * Shown as extra collapsing sections on the parent's inspector.
     */
    public static List<CustomNodeTypeDef> inspectorsFor(SceneDocument doc, SceneNode host) {
        if (host == null || host.children == null || host.children.isEmpty()) return List.of();
        SceneSchema schema = doc == null ? editorSchema() : ensureForDocument(doc);
        if (schema == null) return List.of();
        List<CustomNodeTypeDef> out = new ArrayList<>();
        for (SceneNode child : host.children) {
            if (child == null) continue;
            if (child.type != SceneNodeType.SCRIPT && child.type != SceneNodeType.VISUAL_SCRIPT) continue;
            if (child.script == null || child.script.isBlank()) continue;
            List<CustomNodeTypeDef> defs = schema.inspectorsByScript.get(normalizeScriptName(child.script.trim()));
            if (defs == null || defs.isEmpty()) continue;
            out.addAll(defs);
        }
        return out;
    }

    public static CustomFieldDef fieldOn(SceneDocument doc, SceneNode node, String key) {
        if (node == null || key == null || key.isBlank()) return null;
        if (node.type == SceneNodeType.CUSTOM) {
            CustomNodeTypeDef type = get(doc, node.customTypeId);
            CustomFieldDef field = type == null ? null : type.field(key);
            if (field != null) return field;
        }
        for (CustomNodeTypeDef def : inspectorsFor(doc, node)) {
            CustomFieldDef field = def.field(key);
            if (field != null) return field;
        }
        return null;
    }

    private static SceneSchema editorSchema() {
        String id = editorSceneId;
        if (id == null) return null;
        return BY_SCENE.get(id);
    }

    private static SceneDocument editorDocumentOrNull() {
        try {
            Class<?> session = Class.forName(
                    "org.zonarstudio.spraute_engine.client.editor.scene.SceneEditorSession");
            Object doc = session.getMethod("document").invoke(null);
            return doc instanceof SceneDocument d ? d : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static SceneSchema parseDocument(SceneDocument doc, Set<String> scriptNames, String fingerprint) {
        Map<String, CustomNodeTypeDef> types = new LinkedHashMap<>();
        Set<String> sections = new LinkedHashSet<>();
        Map<String, List<CustomNodeTypeDef>> inspectorsByScript = new LinkedHashMap<>();
        Path scriptsDir = scriptsDir();
        if (!Files.isDirectory(scriptsDir)) {
            return new SceneSchema(types, sections, fingerprint, inspectorsByScript);
        }
        for (String name : scriptNames) {
            Path file = resolveScriptFile(scriptsDir, name);
            if (file == null) {
                LOGGER.warn("[CustomNode] Script '{}' (scene '{}') not found under {}",
                        name, doc.id, scriptsDir);
                continue;
            }
            try {
                String src = Files.readString(file, StandardCharsets.UTF_8);
                CustomNodeSprParser.ParseResult result = CustomNodeSprParser.parse(src);
                sections.addAll(result.sections);
                for (CustomNodeTypeDef type : result.types) {
                    if (type == null || type.id == null || type.id.isBlank()) continue;
                    if (types.containsKey(type.id)) {
                        LOGGER.warn("[CustomNode] Duplicate type '{}' in scene '{}' — overriding",
                                type.id, doc.id);
                    }
                    type.sourceScript = name;
                    types.put(type.id, type);
                    if (type.section != null && !type.section.isBlank()) sections.add(type.section);
                }
                if (!result.inspectors.isEmpty()) {
                    List<CustomNodeTypeDef> attached = new ArrayList<>();
                    for (CustomNodeTypeDef insp : result.inspectors) {
                        if (insp == null || insp.id == null || insp.id.isBlank()) continue;
                        insp.sourceScript = name;
                        attached.add(insp);
                    }
                    if (!attached.isEmpty()) inspectorsByScript.put(name, attached);
                }
            } catch (Exception e) {
                LOGGER.warn("[CustomNode] Parse failed for {} (scene {}): {}",
                        file, doc.id, e.getMessage());
            }
        }
        return new SceneSchema(types, sections, fingerprint, inspectorsByScript);
    }

    public static Set<String> collectScriptNames(SceneDocument doc) {
        Set<String> names = new LinkedHashSet<>();
        if (doc == null) return names;
        doc.walk((node, ignored) -> {
            if (node.type != SceneNodeType.SCRIPT && node.type != SceneNodeType.VISUAL_SCRIPT) return;
            if (node.script == null || node.script.isBlank()) return;
            names.add(normalizeScriptName(node.script.trim()));
        });
        return names;
    }

    private static String normalizeScriptName(String name) {
        String n = name.replace('\\', '/');
        if (n.toLowerCase().endsWith(".sprv")) {
            n = n.substring(0, n.length() - 5);
        }
        if (n.toLowerCase().endsWith(".spr")) {
            n = n.substring(0, n.length() - 4);
        }
        return n;
    }

    private static String scriptFingerprint(SceneDocument doc, Set<String> names) {
        StringBuilder sb = new StringBuilder();
        Path dir = scriptsDir();
        for (String name : names) {
            sb.append(name).append('@');
            Path file = resolveScriptFile(dir, name);
            if (file != null) {
                try {
                    sb.append(Files.getLastModifiedTime(file).toMillis());
                } catch (Exception ignored) {
                    sb.append('0');
                }
            } else {
                sb.append('0');
            }
            sb.append(';');
        }
        return sb.toString();
    }

    private static Path scriptsDir() {
        return org.zonarstudio.spraute_engine.resource.SprautePaths.scriptsDir();
    }

    private static Path resolveScriptFile(Path scriptsDir, String name) {
        String relative = name.replace('\\', '/');
        Path overlay = org.zonarstudio.spraute_engine.resource.WorkspaceContent.resolve("scripts/" + relative + ".spr");
        if (overlay != null) return overlay;
        Path direct = scriptsDir.resolve(relative + ".spr");
        if (Files.isRegularFile(direct)) return direct;
        String shortName = relative.contains("/")
                ? relative.substring(relative.lastIndexOf('/') + 1)
                : relative;
        String want = shortName + ".spr";
        for (Path p : org.zonarstudio.spraute_engine.resource.WorkspaceContent.allSprFiles()) {
            if (p.getFileName().toString().equalsIgnoreCase(want)) return p;
        }
        return null;
    }

    /** Parsed declare schema for one scene. */
    public static final class SceneSchema {
        static final SceneSchema EMPTY = new SceneSchema(Map.of(), Set.of(), "", Map.of());

        final Map<String, CustomNodeTypeDef> types;
        final Set<String> sections;
        final String fingerprint;
        final Map<String, List<CustomNodeTypeDef>> inspectorsByScript;

        SceneSchema(Map<String, CustomNodeTypeDef> types, Set<String> sections, String fingerprint,
                    Map<String, List<CustomNodeTypeDef>> inspectorsByScript) {
            this.types = types;
            this.sections = sections;
            this.fingerprint = fingerprint == null ? "" : fingerprint;
            this.inspectorsByScript = inspectorsByScript;
        }
    }
}
