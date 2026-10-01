package org.zonarstudio.spraute_engine.client.editor.scene;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import org.zonarstudio.spraute_engine.client.editor.EditorI18n;

/**
 * Edits bone rest transforms (pivot / rotation) directly in the workspace
 * {@code .geo.json} file, then invalidates the model caches so the editor
 * preview and runtime pick the change up immediately.
 */
public final class GeoBoneEditor {
    private static final int HISTORY_LIMIT = 80;
    private static final float DEFAULT_CUBE = 8f;
    private static final Deque<Snapshot> undoStack = new ArrayDeque<>();
    private static final Deque<Snapshot> redoStack = new ArrayDeque<>();

    private record Snapshot(Path file, String json, String modelPath) {}

    private GeoBoneEditor() {}

    /** Snapshot the geo file before a rest-pose mutation (gizmo begin, inspector activate). */
    public static void pushUndo(String modelWorkspacePath) {
        Path file = resolveGeoFile(modelWorkspacePath);
        if (file == null) return;
        try {
            undoStack.push(new Snapshot(file, Files.readString(file, StandardCharsets.UTF_8), modelWorkspacePath));
            while (undoStack.size() > HISTORY_LIMIT) undoStack.removeLast();
            redoStack.clear();
            EditorUndo.mark(EditorUndo.Domain.GEO);
        } catch (IOException ignored) {
        }
    }

    public static boolean canUndo() { return !undoStack.isEmpty(); }
    public static boolean canRedo() { return !redoStack.isEmpty(); }

    public static void undo() {
        restore(undoStack, redoStack);
    }

    public static void redo() {
        restore(redoStack, undoStack);
    }

    private static void restore(Deque<Snapshot> from, Deque<Snapshot> to) {
        if (from.isEmpty()) return;
        Snapshot snap = from.pop();
        try {
            if (!Files.isRegularFile(snap.file)) {
                from.push(snap);
                return;
            }
            to.push(new Snapshot(snap.file, Files.readString(snap.file, StandardCharsets.UTF_8), snap.modelPath));
            writeRaw(snap.file, snap.json);
            invalidate(snap.modelPath);
        } catch (IOException e) {
            from.push(snap);
        }
    }

    public static Path resolveGeoFile(String modelWorkspacePath) {
        if (modelWorkspacePath == null || modelWorkspacePath.isBlank()) return null;
        Path root = Minecraft.getInstance().gameDirectory.toPath()
                .resolve("spraute_engine").toAbsolutePath().normalize();
        Path file = root.resolve(modelWorkspacePath.replace('\\', '/')).normalize();
        return file.startsWith(root) && Files.isRegularFile(file) ? file : null;
    }

    /** Returns a stable unused name such as {@code bone}, {@code bone_2}, ... */
    public static String uniqueBoneName(String modelWorkspacePath, String requestedBase) {
        Path file = resolveGeoFile(modelWorkspacePath);
        String base = sanitizeBoneName(requestedBase);
        if (file == null) return base;
        try {
            JsonObject root = readRoot(file);
            JsonArray bones = findBones(root);
            if (bones == null || findBone(bones, base) == null) return base;
            for (int i = 2; i < 10000; i++) {
                String candidate = base + "_" + i;
                if (findBone(bones, candidate) == null) return candidate;
            }
        } catch (Exception ignored) {
        }
        return base + "_" + System.currentTimeMillis();
    }

    /** Add a root/child bone with a default cube. Bedrock pivots are absolute, so a child starts at its parent's pivot. */
    public static String addBone(String modelWorkspacePath, String boneName, String parentName) {
        Path file = resolveGeoFile(modelWorkspacePath);
        if (file == null) return notEditable();
        String name = sanitizeBoneName(boneName);
        try {
            JsonObject root = readRoot(file);
            JsonArray bones = ensureBones(root);
            if (bones == null) return EditorI18n.t("В geo.json не найден minecraft:geometry");
            if (findBone(bones, name) != null) return EditorI18n.t("Кость уже существует: ") + name;
            JsonObject parent = parentName == null || parentName.isBlank() ? null : findBone(bones, parentName);
            if (parentName != null && !parentName.isBlank() && parent == null) {
                return EditorI18n.t("Родительская кость не найдена: ") + parentName;
            }
            pushUndo(modelWorkspacePath);
            JsonObject bone = new JsonObject();
            bone.addProperty("name", name);
            if (parent != null) bone.addProperty("parent", parentName);
            float[] pivot = new float[3];
            if (parent != null) fillVec(parent.get("pivot"), pivot, 0);
            bone.add("pivot", toArray(pivot));
            addCenteredCube(bone, pivot, new float[]{DEFAULT_CUBE, DEFAULT_CUBE, DEFAULT_CUBE});
            bones.add(bone);
            writeRoot(file, root);
        } catch (IOException | RuntimeException e) {
            return EditorI18n.t("Ошибка добавления кости: ") + e.getMessage();
        }
        invalidate(modelWorkspacePath);
        return null;
    }

    /** Rename a bone and update all parent references. */
    public static String renameBone(String modelWorkspacePath, String oldName, String requestedName) {
        Path file = resolveGeoFile(modelWorkspacePath);
        if (file == null) return notEditable();
        String newName = sanitizeBoneName(requestedName);
        if (oldName != null && oldName.equals(newName)) return null;
        try {
            JsonObject root = readRoot(file);
            JsonArray bones = findBones(root);
            JsonObject bone = bones == null ? null : findBone(bones, oldName);
            if (bone == null) return EditorI18n.t("Кость не найдена: ") + oldName;
            if (findBone(bones, newName) != null) return EditorI18n.t("Кость уже существует: ") + newName;
            pushUndo(modelWorkspacePath);
            bone.addProperty("name", newName);
            for (JsonElement element : bones) {
                if (!element.isJsonObject()) continue;
                JsonObject candidate = element.getAsJsonObject();
                if (candidate.has("parent") && oldName.equals(candidate.get("parent").getAsString())) {
                    candidate.addProperty("parent", newName);
                }
            }
            writeRoot(file, root);
        } catch (IOException | RuntimeException e) {
            return EditorI18n.t("Ошибка переименования кости: ") + e.getMessage();
        }
        invalidate(modelWorkspacePath);
        return null;
    }

    /**
     * Delete a bone. With {@code deleteChildren=false}, direct children are reparented to the
     * deleted bone's parent; with {@code true}, the complete hierarchy is removed.
     */
    public static String deleteBone(String modelWorkspacePath, String boneName, boolean deleteChildren) {
        Path file = resolveGeoFile(modelWorkspacePath);
        if (file == null) return notEditable();
        try {
            JsonObject root = readRoot(file);
            JsonArray bones = findBones(root);
            JsonObject bone = bones == null ? null : findBone(bones, boneName);
            if (bone == null) return EditorI18n.t("Кость не найдена: ") + boneName;
            pushUndo(modelWorkspacePath);
            String parentName = bone.has("parent") ? bone.get("parent").getAsString() : null;
            java.util.Set<String> remove = new java.util.LinkedHashSet<>();
            remove.add(boneName);
            if (deleteChildren) collectDescendantNames(bones, boneName, remove);
            else {
                for (JsonElement element : bones) {
                    if (!element.isJsonObject()) continue;
                    JsonObject candidate = element.getAsJsonObject();
                    if (!candidate.has("parent") || !boneName.equals(candidate.get("parent").getAsString())) continue;
                    if (parentName == null || parentName.isBlank()) candidate.remove("parent");
                    else candidate.addProperty("parent", parentName);
                }
            }
            for (int i = bones.size() - 1; i >= 0; i--) {
                JsonElement element = bones.get(i);
                if (element.isJsonObject() && element.getAsJsonObject().has("name")
                        && remove.contains(element.getAsJsonObject().get("name").getAsString())) {
                    bones.remove(i);
                }
            }
            writeRoot(file, root);
        } catch (IOException | RuntimeException e) {
            return EditorI18n.t("Ошибка удаления кости: ") + e.getMessage();
        }
        invalidate(modelWorkspacePath);
        return null;
    }

    public static java.util.List<String> boneNames(String modelWorkspacePath) {
        Path file = resolveGeoFile(modelWorkspacePath);
        if (file == null) return java.util.List.of();
        try {
            JsonArray bones = findBones(readRoot(file));
            if (bones == null) return java.util.List.of();
            java.util.List<String> result = new java.util.ArrayList<>();
            for (JsonElement element : bones) {
                if (element.isJsonObject() && element.getAsJsonObject().has("name")) {
                    result.add(element.getAsJsonObject().get("name").getAsString());
                }
            }
            return result;
        } catch (Exception ignored) {
            return java.util.List.of();
        }
    }

    public static String readParent(String modelWorkspacePath, String boneName) {
        Path file = resolveGeoFile(modelWorkspacePath);
        if (file == null) return null;
        try {
            JsonObject bone = findBone(readRoot(file), boneName);
            return bone != null && bone.has("parent") ? bone.get("parent").getAsString() : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    /** Change hierarchy without touching animation files. Cycles are rejected. */
    public static String reparentBone(String modelWorkspacePath, String boneName, String parentName) {
        Path file = resolveGeoFile(modelWorkspacePath);
        if (file == null) return notEditable();
        try {
            JsonObject root = readRoot(file);
            JsonArray bones = findBones(root);
            JsonObject bone = bones == null ? null : findBone(bones, boneName);
            if (bone == null) return EditorI18n.t("Кость не найдена: ") + boneName;
            JsonObject parent = parentName == null || parentName.isBlank() ? null : findBone(bones, parentName);
            if (parentName != null && !parentName.isBlank() && parent == null) {
                return EditorI18n.t("Родительская кость не найдена: ") + parentName;
            }
            if (boneName.equals(parentName)) return EditorI18n.t("Кость не может быть родителем самой себе");
            java.util.Set<String> descendants = new java.util.HashSet<>();
            collectDescendantNames(bones, boneName, descendants);
            if (parentName != null && descendants.contains(parentName)) {
                return EditorI18n.t("Нельзя переместить кость в её дочернюю ветку");
            }
            pushUndo(modelWorkspacePath);
            if (parent == null) bone.remove("parent");
            else bone.addProperty("parent", parentName);
            writeRoot(file, root);
        } catch (IOException | RuntimeException e) {
            return EditorI18n.t("Ошибка смены родителя кости: ") + e.getMessage();
        }
        invalidate(modelWorkspacePath);
        return null;
    }

    /** Read pivot+rotation of a bone from the geo file; null when not found. Returns [px,py,pz,rx,ry,rz]. */
    public static float[] readBone(String modelWorkspacePath, String boneName) {
        Path file = resolveGeoFile(modelWorkspacePath);
        if (file == null) return null;
        try {
            JsonObject boneObj = findBone(
                    JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject(),
                    boneName);
            if (boneObj == null) return null;
            float[] result = new float[6];
            fillVec(boneObj.get("pivot"), result, 0);
            fillVec(boneObj.get("rotation"), result, 3);
            return result;
        } catch (Exception e) {
            return null;
        }
    }

    /** Bounding size of this bone's complete geometry hierarchy, in model pixels. */
    public static float[] readBoneSize(String modelWorkspacePath, String boneName) {
        Path file = resolveGeoFile(modelWorkspacePath);
        if (file == null) return null;
        try {
            JsonObject root = JsonParser.parseString(
                    Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonArray bones = findBones(root);
            if (bones == null || findBone(bones, boneName) == null) return null;
            return treeSize(bones, boneName);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Write the rest transform into the geo file. Moving the pivot also
     * translates this bone's cubes and its complete child hierarchy, so the
     * operation behaves like moving a node instead of merely relocating the
     * rotation origin.
     */
    public static String writeBone(String modelWorkspacePath, String boneName, float[] pivot, float[] rotation) {
        Path file = resolveGeoFile(modelWorkspacePath);
        if (file == null) return EditorI18n.t("Файл модели не найден в spraute_engine (встроенные ресурсы редактировать нельзя)");
        try {
            JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject boneObj = findBone(root, boneName);
            if (boneObj == null) return EditorI18n.t("Кость '") + boneName + EditorI18n.t("' не найдена в ") + file.getFileName();
            float[] oldPivot = new float[3];
            fillVec(boneObj.get("pivot"), oldPivot, 0);
            float[] delta = {
                    pivot[0] - oldPivot[0],
                    pivot[1] - oldPivot[1],
                    pivot[2] - oldPivot[2]
            };
            if (delta[0] != 0f || delta[1] != 0f || delta[2] != 0f) {
                JsonArray bones = findBones(root);
                if (bones != null) translateBoneTree(bones, boneName, delta, new java.util.HashSet<>());
            }
            if (rotation[0] == 0f && rotation[1] == 0f && rotation[2] == 0f) {
                boneObj.remove("rotation");
            } else {
                boneObj.add("rotation", toArray(rotation));
            }
            writeRoot(file, root);
        } catch (IOException | RuntimeException e) {
            return EditorI18n.t("Ошибка записи: ") + e.getMessage();
        }
        invalidate(modelWorkspacePath);
        return null;
    }

    /**
     * Resize the complete bone hierarchy around its pivot. Bedrock stores size
     * in cubes, not in a bone-level scale field.
     */
    public static String writeBoneSize(String modelWorkspacePath, String boneName, float[] requestedSize) {
        Path file = resolveGeoFile(modelWorkspacePath);
        if (file == null) return EditorI18n.t("Файл модели не найден в spraute_engine (встроенные ресурсы редактировать нельзя)");
        try {
            JsonObject root = JsonParser.parseString(
                    Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonArray bones = findBones(root);
            JsonObject bone = bones == null ? null : findBone(bones, boneName);
            if (bone == null) return EditorI18n.t("Кость '") + boneName + EditorI18n.t("' не найдена в ") + file.getFileName();
            float[] current = treeSize(bones, boneName);
            float[] pivot = new float[3];
            fillVec(bone.get("pivot"), pivot, 0);
            if (!hasGeometry(current)) {
                addCenteredCube(bone, pivot, requestedSize);
                writeRoot(file, root);
                invalidate(modelWorkspacePath);
                return null;
            }
            float[] factors = new float[3];
            for (int i = 0; i < 3; i++) {
                float desired = Math.max(0.001f, requestedSize[i]);
                factors[i] = current[i] > 0.0001f ? desired / current[i] : 1f;
            }
            scaleBoneTree(bones, boneName, pivot, factors, true, new java.util.HashSet<>());
            writeRoot(file, root);
        } catch (IOException | RuntimeException e) {
            return EditorI18n.t("Ошибка записи размера: ") + e.getMessage();
        }
        invalidate(modelWorkspacePath);
        return null;
    }

    private static void invalidate(String modelWorkspacePath) {
        org.zonarstudio.spraute_engine.entity.client.SpModelCache.invalidate(modelWorkspacePath);
        org.zonarstudio.spraute_engine.core.model.SpResourceCache.invalidateModel(modelWorkspacePath);
        org.zonarstudio.spraute_engine.entity.NpcBonePoseSolver.clear();
        ScenePreviewManager.clearInstances();
        EditorModelPreviewRenderer.invalidate();
        EditorAnimClipPreview.invalidate();
    }

    private static void translateBoneTree(JsonArray bones, String boneName, float[] delta,
                                          java.util.Set<String> visited) {
        if (!visited.add(boneName)) return;
        JsonObject bone = findBone(bones, boneName);
        if (bone == null) return;
        translateVec(bone, "pivot", delta);
        JsonArray cubes = bone.getAsJsonArray("cubes");
        if (cubes != null) {
            for (JsonElement cubeEl : cubes) {
                if (cubeEl.isJsonObject()) translateVec(cubeEl.getAsJsonObject(), "origin", delta);
            }
        }
        for (JsonElement candidateEl : bones) {
            if (!candidateEl.isJsonObject()) continue;
            JsonObject candidate = candidateEl.getAsJsonObject();
            if (!candidate.has("name") || !candidate.has("parent")) continue;
            if (boneName.equals(candidate.get("parent").getAsString())) {
                translateBoneTree(bones, candidate.get("name").getAsString(), delta, visited);
            }
        }
    }

    private static void translateVec(JsonObject object, String key, float[] delta) {
        float[] value = new float[3];
        fillVec(object.get(key), value, 0);
        value[0] += delta[0];
        value[1] += delta[1];
        value[2] += delta[2];
        object.add(key, toArray(value));
    }

    private static float[] treeSize(JsonArray bones, String boneName) {
        float[] bounds = {
                Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY,
                Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY
        };
        collectBounds(bones, boneName, bounds, new java.util.HashSet<>());
        if (!Float.isFinite(bounds[0])) return new float[]{0f, 0f, 0f};
        return new float[]{
                bounds[3] - bounds[0], bounds[4] - bounds[1], bounds[5] - bounds[2]
        };
    }

    private static boolean hasGeometry(float[] size) {
        return size != null && (size[0] > 0.0001f || size[1] > 0.0001f || size[2] > 0.0001f);
    }

    /** Empty bones have no cubes — size lives in cubes, so create a box around the pivot. */
    private static void addCenteredCube(JsonObject bone, float[] pivot, float[] size) {
        JsonArray cubes = bone.getAsJsonArray("cubes");
        if (cubes == null) {
            cubes = new JsonArray();
            bone.add("cubes", cubes);
        }
        float sx = Math.max(0.001f, size[0]);
        float sy = Math.max(0.001f, size[1]);
        float sz = Math.max(0.001f, size[2]);
        JsonObject cube = new JsonObject();
        cube.add("origin", toArray(new float[]{
                pivot[0] - sx * 0.5f,
                pivot[1] - sy * 0.5f,
                pivot[2] - sz * 0.5f
        }));
        cube.add("size", toArray(new float[]{sx, sy, sz}));
        JsonArray uv = new JsonArray();
        uv.add(0);
        uv.add(0);
        cube.add("uv", uv);
        cubes.add(cube);
    }

    private static void collectBounds(JsonArray bones, String boneName, float[] bounds,
                                      java.util.Set<String> visited) {
        if (!visited.add(boneName)) return;
        JsonObject bone = findBone(bones, boneName);
        if (bone == null) return;
        JsonArray cubes = bone.getAsJsonArray("cubes");
        if (cubes != null) {
            for (JsonElement cubeEl : cubes) {
                if (!cubeEl.isJsonObject()) continue;
                JsonObject cube = cubeEl.getAsJsonObject();
                float[] origin = new float[3];
                float[] size = new float[3];
                fillVec(cube.get("origin"), origin, 0);
                fillVec(cube.get("size"), size, 0);
                for (int axis = 0; axis < 3; axis++) {
                    float end = origin[axis] + size[axis];
                    bounds[axis] = Math.min(bounds[axis], Math.min(origin[axis], end));
                    bounds[axis + 3] = Math.max(bounds[axis + 3], Math.max(origin[axis], end));
                }
            }
        }
        forEachChild(bones, boneName, child -> collectBounds(bones, child, bounds, visited));
    }

    private static void scaleBoneTree(JsonArray bones, String boneName, float[] rootPivot,
                                      float[] factors, boolean root, java.util.Set<String> visited) {
        if (!visited.add(boneName)) return;
        JsonObject bone = findBone(bones, boneName);
        if (bone == null) return;
        if (!root) scalePoint(bone, "pivot", rootPivot, factors);
        JsonArray cubes = bone.getAsJsonArray("cubes");
        if (cubes != null) {
            for (JsonElement cubeEl : cubes) {
                if (!cubeEl.isJsonObject()) continue;
                JsonObject cube = cubeEl.getAsJsonObject();
                scalePoint(cube, "origin", rootPivot, factors);
                float[] size = new float[3];
                fillVec(cube.get("size"), size, 0);
                for (int i = 0; i < 3; i++) size[i] *= factors[i];
                cube.add("size", toArray(size));
            }
        }
        forEachChild(bones, boneName,
                child -> scaleBoneTree(bones, child, rootPivot, factors, false, visited));
    }

    private static void scalePoint(JsonObject object, String key, float[] pivot, float[] factors) {
        float[] value = new float[3];
        fillVec(object.get(key), value, 0);
        for (int i = 0; i < 3; i++) value[i] = pivot[i] + (value[i] - pivot[i]) * factors[i];
        object.add(key, toArray(value));
    }

    private static void forEachChild(JsonArray bones, String parentName,
                                     java.util.function.Consumer<String> consumer) {
        for (JsonElement candidateEl : bones) {
            if (!candidateEl.isJsonObject()) continue;
            JsonObject candidate = candidateEl.getAsJsonObject();
            if (!candidate.has("name") || !candidate.has("parent")) continue;
            if (parentName.equals(candidate.get("parent").getAsString())) {
                consumer.accept(candidate.get("name").getAsString());
            }
        }
    }

    private static void collectDescendantNames(JsonArray bones, String parentName,
                                               java.util.Set<String> out) {
        forEachChild(bones, parentName, child -> {
            if (out.add(child)) collectDescendantNames(bones, child, out);
        });
    }

    private static String sanitizeBoneName(String requested) {
        String value = requested == null ? "" : requested.trim();
        if (value.isEmpty()) value = "bone";
        value = value.replaceAll("[^A-Za-z0-9_.-]+", "_");
        value = value.replaceAll("^_+|_+$", "");
        return value.isEmpty() ? "bone" : value;
    }

    private static String notEditable() {
        return EditorI18n.t("Файл модели не найден в spraute_engine (встроенные ресурсы редактировать нельзя)");
    }

    private static JsonObject readRoot(Path file) throws IOException {
        return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static JsonArray ensureBones(JsonObject root) {
        JsonElement geometry = root.get("minecraft:geometry");
        if (geometry == null || !geometry.isJsonArray() || geometry.getAsJsonArray().isEmpty()) return null;
        JsonElement first = geometry.getAsJsonArray().get(0);
        if (!first.isJsonObject()) return null;
        JsonObject geo = first.getAsJsonObject();
        JsonArray bones = geo.getAsJsonArray("bones");
        if (bones == null) {
            bones = new JsonArray();
            geo.add("bones", bones);
        }
        return bones;
    }

    private static void writeRoot(Path file, JsonObject root) throws IOException {
        writeRaw(file, new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(root));
    }

    private static void writeRaw(Path file, String json) throws IOException {
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(temp, json, StandardCharsets.UTF_8);
        try {
            Files.move(temp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
            Files.move(temp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        org.zonarstudio.spraute_engine.client.editor.RemoteProjectClient.pushIfRemote(file);
    }

    private static JsonObject findBone(JsonObject root, String boneName) {
        JsonArray bones = findBones(root);
        return bones == null ? null : findBone(bones, boneName);
    }

    private static JsonArray findBones(JsonObject root) {
        JsonElement geometry = root.get("minecraft:geometry");
        if (geometry == null || !geometry.isJsonArray()) return null;
        for (JsonElement geoEl : geometry.getAsJsonArray()) {
            if (!geoEl.isJsonObject()) continue;
            JsonArray bones = geoEl.getAsJsonObject().getAsJsonArray("bones");
            if (bones != null) return bones;
        }
        return null;
    }

    private static JsonObject findBone(JsonArray bones, String boneName) {
        for (JsonElement boneEl : bones) {
            if (!boneEl.isJsonObject()) continue;
            JsonObject boneObj = boneEl.getAsJsonObject();
            if (boneObj.has("name") && boneName.equals(boneObj.get("name").getAsString())) return boneObj;
        }
        return null;
    }

    private static void fillVec(JsonElement el, float[] out, int offset) {
        if (el == null || !el.isJsonArray()) return;
        JsonArray arr = el.getAsJsonArray();
        for (int i = 0; i < 3 && i < arr.size(); i++) {
            try {
                out[offset + i] = arr.get(i).getAsFloat();
            } catch (Exception ignored) {
            }
        }
    }

    private static JsonArray toArray(float[] vec) {
        JsonArray arr = new JsonArray();
        for (int i = 0; i < 3; i++) {
            float v = Math.round(vec[i] * 1000f) / 1000f;
            if (v == Math.floor(v)) arr.add((int) v);
            else arr.add(v);
        }
        return arr;
    }
}
