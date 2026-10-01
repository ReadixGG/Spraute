package org.zonarstudio.spraute_engine.client.editor.scene;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.zonarstudio.spraute_engine.scene.SceneActiveState;
import org.zonarstudio.spraute_engine.scene.SceneDocument;
import org.zonarstudio.spraute_engine.scene.SceneNode;
import org.zonarstudio.spraute_engine.scene.SceneNodeType;
import org.zonarstudio.spraute_engine.scene.SceneRepository;
import org.zonarstudio.spraute_engine.scene.SceneRuntime;
import org.zonarstudio.spraute_engine.scene.custom.CustomNodeRegistry;
import org.zonarstudio.spraute_engine.network.ModNetwork;
import org.zonarstudio.spraute_engine.network.SceneActionPacket;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.zonarstudio.spraute_engine.client.editor.EditorI18n;

/**
 * Client-side editing session: the currently open scene, selection,
 * dirty flag, snapshot-based undo/redo and debounced autosave.
 * <p>
 * Selection is multi-node (Unity/Godot-style). {@link #selectedNodeId()} is the
 * primary (gizmo pivot + inspector); {@link #selectedNodeIds()} is the full set.
 */
public final class SceneEditorSession {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int UNDO_LIMIT = 64;
    private static final long AUTOSAVE_DEBOUNCE_MS = 2500;
    private static final long REMOTE_AUTOSAVE_MS = 400;
    private static Path pendingRemoteReload;

    private static SceneDocument document;
    private static Path documentPath;
    /** Primary selection (inspector / gizmo origin). Always ∈ {@link #selectedIds} when non-null. */
    private static String selectedNodeId;
    private static final LinkedHashSet<String> selectedIds = new LinkedHashSet<>();
    /** Shift+click range anchor in the hierarchy. */
    private static String selectionAnchorId;
    private static boolean dirty;
    private static long dirtySinceMs;
    private static String lastError;
    private static Path requestedScriptPath;
    private static Path requestedVisualScriptPath;

    private static final Deque<String> undoStack = new ArrayDeque<>();
    private static final Deque<String> redoStack = new ArrayDeque<>();
    /** Authoring JSON captured at Play. Live inspector/gizmo edits must not persist. */
    private static String prePlaySnapshot;
    private static boolean wasLiveRuntime;

    private SceneEditorSession() {}

    public static SceneDocument document() {
        if (document != null) {
            SceneActiveState live = SceneRuntime.getActiveState(document.id);
            if (live != null) return live.document;
        }
        return document;
    }

    /** True when the open scene id is currently loaded in {@link SceneRuntime} (live world). */
    public static boolean isLiveRuntime() {
        if (document == null) return false;
        if (SceneRuntime.isActive(document.id)) return true;
        return org.zonarstudio.spraute_engine.client.ClientSceneRuntime.isActive(document.id);
    }

    public static String liveSceneId() {
        return isLiveRuntime() ? document.id : null;
    }

    /**
     * Play/Stop from the hierarchy. On a dedicated server the current scene JSON
     * and referenced {@code .spr} files are written to the <b>server</b> workspace first.
     */
    public static void requestPlayToggle() {
        if (document == null) return;
        String sceneId = document.id;
        if (isLiveRuntime()) {
            ModNetwork.CHANNEL.sendToServer(new SceneActionPacket(sceneId, false));
            restorePrePlaySnapshot();
            return;
        }
        restorePrePlaySnapshot();
        org.zonarstudio.spraute_engine.scene.PlayerCameraNodes.flushAuthoring(document);
        if (documentPath != null) {
            try {
                SceneRepository.saveAtomic(document, documentPath);
                dirty = false;
                lastError = null;
            } catch (IOException e) {
                lastError = "Ошибка сохранения: " + e.getMessage();
                LOGGER.error("[Spraute Scene] {}", lastError);
                return;
            }
        }
        Minecraft mc = Minecraft.getInstance();
        boolean dedicated = mc.getSingleplayerServer() == null && mc.getConnection() != null;
        org.zonarstudio.spraute_engine.client.editor.SprauteEngineEditorScreen.flushPendingScripts();
        if (!dedicated) {
            capturePrePlaySnapshot();
            ModNetwork.CHANNEL.sendToServer(new SceneActionPacket(sceneId, true));
            return;
        }
        // Vanilla play-payload cap is 32767 bytes — never send the whole scene in one packet.
        pushSceneToDedicatedServer(document, sceneId);
        org.zonarstudio.spraute_engine.client.editor.RemoteProjectClient.whenWritesIdle(ok -> {
            if (!ok) {
                lastError = "Не удалось залить сцену на сервер. Смотрите консоль.";
                return;
            }
            capturePrePlaySnapshot();
            ModNetwork.CHANNEL.sendToServer(new SceneActionPacket(sceneId, true));
        });
    }

    private static void pushSceneToDedicatedServer(SceneDocument doc, String sceneId) {
        if (documentPath != null) {
            org.zonarstudio.spraute_engine.client.editor.RemoteProjectClient.pushIfRemote(documentPath);
            Path workspace = documentPath.getParent() == null ? null : documentPath.getParent().getParent();
            if (workspace != null) {
                doc.walk((node, ignored) -> {
                    if (node.type != SceneNodeType.ROUTE) return;
                    org.zonarstudio.spraute_engine.client.editor.RemoteProjectClient.pushIfRemote(
                            org.zonarstudio.spraute_engine.scene.SceneRouteStorage.routePath(workspace, node));
                });
            }
        } else {
            String json = SceneRepository.toJson(doc);
            org.zonarstudio.spraute_engine.client.editor.RemoteProjectClient.writeBytes(
                    "scenes/" + sceneId + SceneRepository.EXTENSION, json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        Path scriptsRoot = Minecraft.getInstance().gameDirectory.toPath()
                .resolve("spraute_engine").resolve("scripts");
        if (!Files.isDirectory(scriptsRoot)) return;
        for (String name : CustomNodeRegistry.collectScriptNames(doc)) {
            Path file = resolveLocalScript(scriptsRoot, name);
            if (file == null) {
                Path sprv = scriptsRoot.resolve(name.replace('\\', '/') + ".sprv");
                file = VisualScriptEditor.ensureRuntimeSpr(sprv);
            }
            org.zonarstudio.spraute_engine.client.editor.RemoteProjectClient.pushIfRemote(file);
        }
    }

    private static Path resolveLocalScript(Path scriptsDir, String name) {
        String relative = name.replace('\\', '/');
        if (relative.endsWith(".spr")) relative = relative.substring(0, relative.length() - 4);
        Path direct = scriptsDir.resolve(relative + ".spr");
        if (Files.isRegularFile(direct)) return direct;
        String shortName = relative.contains("/")
                ? relative.substring(relative.lastIndexOf('/') + 1)
                : relative;
        try (var walk = Files.walk(scriptsDir)) {
            return walk.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().equalsIgnoreCase(shortName + ".spr"))
                    .findFirst()
                    .orElse(null);
        } catch (IOException e) {
            return null;
        }
    }

    public static Path documentPath() { return documentPath; }

    /** Keeps the open scene bound after a rename/move in the project explorer. */
    public static void notifyPathRenamed(Path from, Path to) {
        if (documentPath != null && from != null && documentPath.equals(from)) {
            documentPath = to;
        }
    }
    public static boolean isDirty() { return dirty && !isLiveRuntime(); }
    public static String lastError() {
        return lastError == null ? null : org.zonarstudio.spraute_engine.client.editor.EditorI18n.localize(lastError);
    }

    public static String selectedNodeId() { return selectedNodeId; }

    /** Unmodifiable view of the current multi-selection (insertion order). */
    public static Set<String> selectedNodeIds() {
        return Collections.unmodifiableSet(selectedIds);
    }

    public static int selectedCount() { return selectedIds.size(); }

    public static boolean isSelected(String nodeId) {
        return nodeId != null && selectedIds.contains(nodeId);
    }

    /** Replace selection with a single node (or clear when {@code null}). */
    public static void select(String nodeId) {
        selectedIds.clear();
        selectedNodeId = null;
        selectionAnchorId = null;
        if (nodeId == null || nodeId.isBlank()) return;
        selectedIds.add(nodeId);
        selectedNodeId = nodeId;
        selectionAnchorId = nodeId;
    }

    /**
     * Make {@code nodeId} the primary selection without clearing other selected ids.
     * Used when starting a drag on an already multi-selected node.
     */
    public static void setPrimarySelection(String nodeId) {
        if (nodeId == null || nodeId.isBlank() || !selectedIds.contains(nodeId)) return;
        selectedNodeId = nodeId;
    }

    /** Add/remove {@code nodeId} from selection (Ctrl+click). Becomes primary if added. */
    public static void toggleSelect(String nodeId) {
        if (nodeId == null || nodeId.isBlank()) return;
        if (selectedIds.contains(nodeId)) {
            selectedIds.remove(nodeId);
            if (nodeId.equals(selectedNodeId)) {
                selectedNodeId = selectedIds.isEmpty() ? null : selectedIds.iterator().next();
            }
            if (nodeId.equals(selectionAnchorId)) selectionAnchorId = selectedNodeId;
        } else {
            selectedIds.add(nodeId);
            selectedNodeId = nodeId;
            selectionAnchorId = nodeId;
        }
    }

    /** Select a contiguous range in {@code orderedIds} from anchor → {@code toId} (Shift+click). */
    public static void selectRange(List<String> orderedIds, String toId) {
        if (orderedIds == null || toId == null || toId.isBlank()) return;
        String from = selectionAnchorId != null ? selectionAnchorId : selectedNodeId;
        if (from == null) {
            select(toId);
            return;
        }
        int a = orderedIds.indexOf(from);
        int b = orderedIds.indexOf(toId);
        if (a < 0 || b < 0) {
            select(toId);
            return;
        }
        if (a > b) {
            int t = a;
            a = b;
            b = t;
        }
        selectedIds.clear();
        for (int i = a; i <= b; i++) selectedIds.add(orderedIds.get(i));
        selectedNodeId = toId;
    }

    public static void addToSelection(String nodeId) {
        if (nodeId == null || nodeId.isBlank()) return;
        selectedIds.add(nodeId);
        selectedNodeId = nodeId;
        if (selectionAnchorId == null) selectionAnchorId = nodeId;
    }

    /**
     * Set selection from a list of node ids (e.g. marquee). When {@code additive} is true,
     * ids are merged into the current selection; otherwise the selection is replaced.
     */
    public static void selectIds(java.util.Collection<String> ids, boolean additive) {
        if (!additive) {
            selectedIds.clear();
            selectedNodeId = null;
            selectionAnchorId = null;
        }
        if (ids == null || ids.isEmpty()) return;
        for (String id : ids) {
            if (id == null || id.isBlank()) continue;
            selectedIds.add(id);
            selectedNodeId = id;
            if (selectionAnchorId == null) selectionAnchorId = id;
        }
    }

    public static void clearSelection() {
        select(null);
    }

    public static boolean renameSelectedNodeId(String newId) {
        SceneDocument doc = document();
        if (doc == null || selectedNodeId == null) return false;
        String oldId = selectedNodeId;
        if (isLiveRuntime()) {
            if (!doc.renameNodeId(selectedNodeId, newId)) return false;
            rewriteSelectionId(oldId, newId.trim());
            syncLiveNode(selectedNodeId);
            return true;
        }
        String before = SceneRepository.toJson(doc);
        if (!doc.renameNodeId(selectedNodeId, newId)) return false;
        rewriteSelectionId(oldId, newId.trim());
        pushUndoJson(before);
        markDirty();
        return true;
    }

    private static void rewriteSelectionId(String oldId, String newId) {
        selectedIds.remove(oldId);
        selectedIds.add(newId);
        if (oldId.equals(selectedNodeId)) selectedNodeId = newId;
        if (oldId.equals(selectionAnchorId)) selectionAnchorId = newId;
    }

    public static SceneNode selectedNode() {
        SceneDocument doc = document();
        if (doc == null || selectedNodeId == null) return null;
        return doc.findNode(selectedNodeId);
    }

    /** All currently selected nodes (insertion order), skipping missing ids. */
    public static List<SceneNode> selectedNodes() {
        SceneDocument doc = document();
        if (doc == null || selectedIds.isEmpty()) return List.of();
        List<SceneNode> out = new ArrayList<>();
        for (String id : selectedIds) {
            SceneNode n = doc.findNode(id);
            if (n != null) out.add(n);
        }
        return out;
    }

    /** Selected nodes that should move with a multi-gizmo (skip locked / UI / descendants of other selected). */
    public static List<SceneNode> selectedMovableNodes() {
        SceneDocument doc = document();
        if (doc == null || selectedIds.isEmpty()) return List.of();
        List<SceneNode> out = new ArrayList<>();
        for (String id : selectedIds) {
            SceneNode n = doc.findNode(id);
            if (n == null || !n.hasTransform()
                    || !org.zonarstudio.spraute_engine.client.editor.scene.EditorGizmo.worldEditable(n)) continue;
            if (doc.isEffectivelyLocked(n)) continue;
            if (hasSelectedAncestor(doc, n)) continue;
            out.add(n);
        }
        return out;
    }

    /**
     * Selected nodes that can be reparented together in the hierarchy
     * (roots of the selection only — children of other selected nodes move with their parent).
     */
    public static List<SceneNode> selectedReparentableNodes() {
        SceneDocument doc = document();
        if (doc == null || selectedIds.isEmpty()) return List.of();
        List<SceneNode> out = new ArrayList<>();
        for (String id : selectedIds) {
            SceneNode n = doc.findNode(id);
            if (n == null || !n.type.canReparent()) continue;
            if (hasSelectedAncestor(doc, n)) continue;
            out.add(n);
        }
        java.util.Map<String, Integer> hierarchyOrder = new java.util.HashMap<>();
        int[] index = {0};
        doc.walk((node, ignored) -> hierarchyOrder.put(node.id, index[0]++));
        out.sort(java.util.Comparator.comparingInt(
                node -> hierarchyOrder.getOrDefault(node.id, Integer.MAX_VALUE)));
        return out;
    }

    /**
     * Top-level nodes in the current selection (no selected ancestor).
     * Use for batch delete/duplicate/copy so children are not processed twice.
     */
    public static List<SceneNode> selectedRootNodes() {
        SceneDocument doc = document();
        if (doc == null || selectedIds.isEmpty()) return List.of();
        List<SceneNode> out = new ArrayList<>();
        for (String id : selectedIds) {
            SceneNode n = doc.findNode(id);
            if (n == null || n.type == SceneNodeType.SCENE) continue;
            if (hasSelectedAncestor(doc, n)) continue;
            out.add(n);
        }
        return out;
    }

    public static List<SceneNode> selectedDeletableRoots() {
        List<SceneNode> out = new ArrayList<>();
        for (SceneNode n : selectedRootNodes()) {
            if (n.type.canDelete()) out.add(n);
        }
        return out;
    }

    /** Replace selection with multiple node ids (last id becomes primary). */
    public static void selectMultiple(java.util.Collection<String> nodeIds) {
        selectedIds.clear();
        selectedNodeId = null;
        selectionAnchorId = null;
        if (nodeIds == null) return;
        for (String id : nodeIds) {
            if (id == null || id.isBlank()) continue;
            selectedIds.add(id);
            selectedNodeId = id;
            if (selectionAnchorId == null) selectionAnchorId = id;
        }
    }

    private static boolean hasSelectedAncestor(SceneDocument doc, SceneNode node) {
        SceneNode parent = doc.findParent(node.id);
        while (parent != null) {
            if (selectedIds.contains(parent.id)) return true;
            parent = doc.findParent(parent.id);
        }
        return false;
    }

    public static void requestOpenScript(Path path) {
        requestedScriptPath = path;
    }

    public static Path consumeRequestedScript() {
        Path path = requestedScriptPath;
        requestedScriptPath = null;
        return path;
    }

    public static void requestOpenVisualScript(Path path) {
        requestedVisualScriptPath = path;
    }

    public static Path consumeRequestedVisualScript() {
        Path path = requestedVisualScriptPath;
        requestedVisualScriptPath = null;
        return path;
    }

    public static Path gameDir() {
        return Minecraft.getInstance().gameDirectory.toPath();
    }

    public static void newScene(String sceneId) {
        document = SceneDocument.create(sceneId);
        documentPath = SceneRepository.scenePath(gameDir(), sceneId);
        select(document.root.id);
        undoStack.clear();
        redoStack.clear();
        dirty = true;
        dirtySinceMs = System.currentTimeMillis();
        save();
        org.zonarstudio.spraute_engine.scene.custom.CustomNodeRegistry.bindEditorScene(document);
    }

    public static void open(Path path) {
        try {
            document = SceneRepository.load(path);
            documentPath = path;
            select(document.root.id);
            undoStack.clear();
            redoStack.clear();
            prePlaySnapshot = null;
            wasLiveRuntime = false;
            dirty = false;
            lastError = null;
            pendingRemoteReload = null;
            org.zonarstudio.spraute_engine.scene.custom.CustomNodeRegistry.bindEditorScene(document);
        } catch (IOException e) {
            lastError = "Не удалось открыть сцену: " + e.getMessage();
            LOGGER.warn("[Spraute Scene] {}", lastError);
        }
    }

    /** Another OP saved this scene — reload if we have it open. */
    public static void applyRemoteIfOpen(Path path) {
        if (path == null || documentPath == null) return;
        Path abs = path.toAbsolutePath().normalize();
        if (!documentPath.toAbsolutePath().normalize().equals(abs)) return;
        if (isLiveRuntime()) return;
        if (org.zonarstudio.spraute_engine.client.editor.scene.EditorGizmo.isDragging()) {
            pendingRemoteReload = abs;
            return;
        }
        applyRemoteNow(abs);
    }

    private static void applyRemoteNow(Path path) {
        try {
            SceneDocument incoming = SceneRepository.load(path);
            if (document != null
                    && SceneRepository.toJson(document).equals(SceneRepository.toJson(incoming))) {
                pendingRemoteReload = null;
                return;
            }
            java.util.LinkedHashSet<String> prev = new java.util.LinkedHashSet<>(selectedIds);
            String prevPrimary = selectedNodeId;
            document = incoming;
            documentPath = path;
            undoStack.clear();
            redoStack.clear();
            dirty = false;
            lastError = null;
            pendingRemoteReload = null;
            selectedIds.clear();
            selectedIds.addAll(prev);
            selectedNodeId = prevPrimary;
            pruneSelection();
            org.zonarstudio.spraute_engine.scene.custom.CustomNodeRegistry.bindEditorScene(document);
        } catch (IOException e) {
            LOGGER.warn("[Spraute Scene] remote reload failed: {}", e.getMessage());
        }
    }

    private static void capturePrePlaySnapshot() {
        if (document == null) {
            prePlaySnapshot = null;
            return;
        }
        prePlaySnapshot = SceneRepository.toJson(document);
    }

    /** Drop inspector/gizmo mutations made while the scene was running. */
    private static void restorePrePlaySnapshot() {
        if (prePlaySnapshot == null || document == null) return;
        try {
            SceneDocument restored = SceneRepository.fromJson(prePlaySnapshot);
            if (restored == null || restored.root == null) return;
            document = restored;
            dirty = false;
            lastError = null;
            pruneSelection();
            CustomNodeRegistry.bindEditorScene(document);
        } finally {
            prePlaySnapshot = null;
        }
    }

    public static void close() {
        document = null;
        documentPath = null;
        select(null);
        dirty = false;
        pendingRemoteReload = null;
        prePlaySnapshot = null;
        wasLiveRuntime = false;
        undoStack.clear();
        redoStack.clear();
        org.zonarstudio.spraute_engine.scene.custom.CustomNodeRegistry.bindEditorScene(null);
    }

    /** Call before mutating the document; snapshots current state for undo. */
    public static void pushUndo() {
        if (isLiveRuntime() || document == null) return;
        pushUndoJson(SceneRepository.toJson(document));
    }

    /** Push a pre-captured snapshot (used when the mutation may be rejected). */
    public static void pushUndoJson(String snapshotJson) {
        if (document == null || snapshotJson == null) return;
        undoStack.push(snapshotJson);
        while (undoStack.size() > UNDO_LIMIT) undoStack.removeLast();
        redoStack.clear();
        EditorUndo.mark(EditorUndo.Domain.SCENE);
    }

    public static void markDirty() {
        if (isLiveRuntime()) {
            syncLiveSelection();
            return;
        }
        dirty = true;
        dirtySinceMs = System.currentTimeMillis();
    }

    /** Sync every currently selected node (multi-gizmo live edits). */
    public static void syncLiveSelection() {
        if (!isLiveRuntime()) return;
        for (String id : List.copyOf(selectedIds)) syncLiveNode(id);
    }

    public static boolean canUndo() { return !isLiveRuntime() && !undoStack.isEmpty(); }
    public static boolean canRedo() { return !isLiveRuntime() && !redoStack.isEmpty(); }

    public static void undo() {
        if (isLiveRuntime() || document == null || undoStack.isEmpty()) return;
        redoStack.push(SceneRepository.toJson(document));
        document = SceneRepository.fromJson(undoStack.pop());
        pruneSelection();
        markDirty();
    }

    public static void redo() {
        if (isLiveRuntime() || document == null || redoStack.isEmpty()) return;
        undoStack.push(SceneRepository.toJson(document));
        document = SceneRepository.fromJson(redoStack.pop());
        pruneSelection();
        markDirty();
    }

    /** Drop missing ids from selection; fall back to scene root when nothing remains. */
    public static void pruneSelection() {
        SceneDocument doc = document();
        if (doc == null) {
            select(null);
            return;
        }
        selectedIds.removeIf(id -> doc.findNode(id) == null);
        if (selectedNodeId != null && !selectedIds.contains(selectedNodeId)) {
            selectedNodeId = selectedIds.isEmpty() ? null : selectedIds.iterator().next();
        }
        if (selectionAnchorId != null && !selectedIds.contains(selectionAnchorId)) {
            selectionAnchorId = selectedNodeId;
        }
        if (selectedIds.isEmpty()) {
            select(doc.root.id);
        }
    }

    public static void save() {
        if (document == null || documentPath == null) return;
        if (isLiveRuntime()) {
            lastError = "Сцена запущена — сохранение отключено. Остановите сцену (/spraute stop).";
            return;
        }
        try {
            org.zonarstudio.spraute_engine.scene.PlayerCameraNodes.flushAuthoring(document);
            SceneRepository.saveAtomic(document, documentPath);
            dirty = false;
            lastError = null;
            org.zonarstudio.spraute_engine.client.editor.RemoteProjectClient.pushIfRemote(documentPath);
            Path workspace = documentPath.getParent() == null ? null : documentPath.getParent().getParent();
            if (workspace != null) {
                document.walk((node, ignored) -> {
                    if (node.type == org.zonarstudio.spraute_engine.scene.SceneNodeType.ROUTE) {
                        org.zonarstudio.spraute_engine.client.editor.RemoteProjectClient.pushIfRemote(
                                org.zonarstudio.spraute_engine.scene.SceneRouteStorage.routePath(workspace, node));
                    }
                });
            }
        } catch (IOException e) {
            lastError = "Ошибка сохранения: " + e.getMessage();
            LOGGER.error("[Spraute Scene] {}", lastError);
        }
    }

    /** Debounced autosave; call once per rendered editor frame. */
    public static void tickAutosave() {
        ClientScenePlayerNodes.sync(document);
        if (pendingRemoteReload != null
                && !org.zonarstudio.spraute_engine.client.editor.scene.EditorGizmo.isDragging()) {
            Path pending = pendingRemoteReload;
            pendingRemoteReload = null;
            applyRemoteNow(pending);
        }
        boolean live = isLiveRuntime();
        if (wasLiveRuntime && !live) restorePrePlaySnapshot();
        wasLiveRuntime = live;
        if (live) return;
        long debounce = org.zonarstudio.spraute_engine.client.editor.RemoteProjectClient.isRemote()
                ? REMOTE_AUTOSAVE_MS : AUTOSAVE_DEBOUNCE_MS;
        if (dirty && document != null
                && System.currentTimeMillis() - dirtySinceMs > debounce) {
            save();
        }
    }

    /** After gizmo/inspector mutate a live node, push transforms to world entities. */
    public static void syncLiveNode(String nodeId) {
        if (!isLiveRuntime() || nodeId == null) return;
        String sceneId = document.id;
        SceneNode node = document.findNode(nodeId);
        SceneNode parent = node == null ? null : document.findParent(node.id);
        String syncRoot = node != null && node.type == org.zonarstudio.spraute_engine.scene.SceneNodeType.ROUTE_POINT
                && parent != null && parent.type == org.zonarstudio.spraute_engine.scene.SceneNodeType.ROUTE
                ? parent.id : nodeId;
        var server = Minecraft.getInstance().getSingleplayerServer();
        if (server == null) return;
        server.execute(() -> org.zonarstudio.spraute_engine.scene.SceneLiveSync.syncNodeSubtree(sceneId, syncRoot));
    }

    /** Fire one PARTICLE burst on the live server runtime (inspector «Запустить»). */
    public static void emitLiveParticle(String nodeId) {
        if (!isLiveRuntime() || nodeId == null || document == null) return;
        String sceneId = document.id;
        var server = Minecraft.getInstance().getSingleplayerServer();
        if (server == null) return;
        server.execute(() -> {
            SceneActiveState state = SceneRuntime.getActiveState(sceneId);
            if (state == null) return;
            SceneNode node = state.document.findNode(nodeId);
            if (node == null || node.type != SceneNodeType.PARTICLE) return;
            var runtime = state.particleRuntime(nodeId);
            if (runtime == null) {
                state.registerParticle(node);
                runtime = state.particleRuntime(nodeId);
            }
            var level = SceneRuntime.resolveLevelForSync(server, state.runtimeDimension);
            if (runtime != null && level != null) runtime.emitNow(state, level);
        });
    }

    /** Despawn runtime objects and remove a subtree from the live document. */
    public static void removeLiveNode(String nodeId) {
        if (!isLiveRuntime() || nodeId == null) return;
        String sceneId = document.id;
        var server = Minecraft.getInstance().getSingleplayerServer();
        if (server == null) return;
        server.execute(() -> SceneRuntime.removeLiveSubtree(sceneId, nodeId));
    }
}
