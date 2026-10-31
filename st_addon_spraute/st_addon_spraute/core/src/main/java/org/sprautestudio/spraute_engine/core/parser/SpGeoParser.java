package org.sprautestudio.spraute_engine.core.parser;

import com.google.gson.*;
import org.sprautestudio.spraute_engine.core.math.SpVec3;
import org.sprautestudio.spraute_engine.core.model.*;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Parses Bedrock .geo.json (format_version 1.12.0+) into {@link SpGeoModel}.
 * Uses GSON — no Minecraft dependencies except GSON which ships with Forge.
 */
public final class SpGeoParser {

    private SpGeoParser() {}

    /** Parse from an InputStream (typically from Minecraft's resource system). */
    public static SpGeoModel parse(InputStream inputStream) {
        JsonObject root = JsonParser.parseReader(
            new InputStreamReader(inputStream, StandardCharsets.UTF_8)
        ).getAsJsonObject();
        return parseRoot(root);
    }

    /** Parse from a raw JSON string. */
    public static SpGeoModel parse(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        return parseRoot(root);
    }

    private static SpGeoModel parseRoot(JsonObject root) {
        JsonArray geometries = root.getAsJsonArray("minecraft:geometry");
        if (geometries == null || geometries.isEmpty()) {
            throw new JsonParseException("No minecraft:geometry array found in geo.json");
        }
        JsonObject geo = geometries.get(0).getAsJsonObject();
        return parseGeometry(geo);
    }

    private static SpGeoModel parseGeometry(JsonObject geo) {
        JsonObject desc = geo.getAsJsonObject("description");
        String identifier = desc.has("identifier") ? desc.get("identifier").getAsString() : "unknown";
        int texW = desc.has("texture_width") ? desc.get("texture_width").getAsInt() : 64;
        int texH = desc.has("texture_height") ? desc.get("texture_height").getAsInt() : 64;

        JsonArray bonesArray = geo.getAsJsonArray("bones");
        if (bonesArray == null) {
            return new SpGeoModel(identifier, texW, texH, Collections.emptyList(), Collections.emptyMap());
        }

        Map<String, SpBone> boneMap = new LinkedHashMap<>();
        Map<String, String> parentNames = new LinkedHashMap<>();

        for (JsonElement el : bonesArray) {
            JsonObject boneObj = el.getAsJsonObject();
            String name = boneObj.get("name").getAsString();

            SpVec3 pivot = parseVec3(boneObj, "pivot", new SpVec3());
            SpVec3 rotation = parseVec3(boneObj, "rotation", new SpVec3());
            float boneInflate = boneObj.has("inflate") ? boneObj.get("inflate").getAsFloat() : 0f;
            boolean boneMirror = boneObj.has("mirror") && boneObj.get("mirror").getAsBoolean();
            Map<String, SpLocator> locators = parseLocators(boneObj);

            List<SpCube> cubes = new ArrayList<>();
            if (boneObj.has("cubes")) {
                for (JsonElement cubeEl : boneObj.getAsJsonArray("cubes")) {
                    cubes.add(parseCube(cubeEl.getAsJsonObject(), boneInflate, boneMirror));
                }
            }

            SpBone bone = new SpBone(name, pivot, rotation, boneInflate, boneMirror, cubes, locators);
            boneMap.put(name, bone);

            if (boneObj.has("parent")) {
                parentNames.put(name, boneObj.get("parent").getAsString());
            }
        }

        List<SpBone> rootBones = new ArrayList<>();
        for (Map.Entry<String, SpBone> entry : boneMap.entrySet()) {
            String parentName = parentNames.get(entry.getKey());
            SpBone bone = entry.getValue();
            if (parentName != null) {
                SpBone parent = boneMap.get(parentName);
                if (parent != null) {
                    bone.parent = parent;
                    parent.children.add(bone);
                } else {
                    rootBones.add(bone);
                }
            } else {
                rootBones.add(bone);
            }
        }

        return new SpGeoModel(identifier, texW, texH, rootBones, boneMap);
    }

    /**
     * Bedrock locators:
     * <pre>
     * "locators": { "lead": [0, 12, 0] }
     * "locators": { "held": { "offset": [...], "rotation": [...], "ignore_inherited_scale": false } }
     * </pre>
     */
    private static Map<String, SpLocator> parseLocators(JsonObject boneObj) {
        if (!boneObj.has("locators") || !boneObj.get("locators").isJsonObject()) {
            return Collections.emptyMap();
        }
        Map<String, SpLocator> out = new LinkedHashMap<>();
        JsonObject locs = boneObj.getAsJsonObject("locators");
        for (Map.Entry<String, JsonElement> e : locs.entrySet()) {
            String locName = e.getKey();
            JsonElement val = e.getValue();
            if (val == null || val.isJsonNull()) continue;
            if (val.isJsonArray()) {
                JsonArray arr = val.getAsJsonArray();
                if (arr.size() < 3) continue;
                out.put(locName, new SpLocator(locName, new SpVec3(
                        arr.get(0).getAsFloat(), arr.get(1).getAsFloat(), arr.get(2).getAsFloat())));
            } else if (val.isJsonObject()) {
                JsonObject obj = val.getAsJsonObject();
                SpVec3 offset = parseVec3(obj, "offset", new SpVec3());
                SpVec3 rot = parseVec3(obj, "rotation", new SpVec3());
                boolean ignoreScale = obj.has("ignore_inherited_scale")
                        && obj.get("ignore_inherited_scale").getAsBoolean();
                out.put(locName, new SpLocator(locName, offset, rot, ignoreScale));
            }
        }
        return out;
    }

    private static SpCube parseCube(JsonObject obj, float boneInflate, boolean boneMirror) {
        SpVec3 origin = parseVec3(obj, "origin", new SpVec3());
        SpVec3 size = parseVec3(obj, "size", new SpVec3());
        // Cube inflate/mirror override bone defaults when present (Bedrock / GeckoLib).
        float inflate = obj.has("inflate") ? obj.get("inflate").getAsFloat() : boneInflate;
        SpVec3 pivot = parseVec3(obj, "pivot", new SpVec3());
        SpVec3 rotation = parseVec3(obj, "rotation", new SpVec3());
        boolean mirror = obj.has("mirror") ? obj.get("mirror").getAsBoolean() : boneMirror;

        Map<SpCube.SpFace, SpFaceUV> faceUVs = new EnumMap<>(SpCube.SpFace.class);
        boolean boxUv = false;

        if (obj.has("uv")) {
            JsonElement uvEl = obj.get("uv");
            if (uvEl.isJsonObject()) {
                JsonObject uvObj = uvEl.getAsJsonObject();
                for (Map.Entry<String, JsonElement> faceEntry : uvObj.entrySet()) {
                    SpCube.SpFace face = SpCube.SpFace.fromString(faceEntry.getKey());
                    if (face == null) continue;
                    JsonObject faceObj = faceEntry.getValue().getAsJsonObject();
                    JsonArray uvArr = faceObj.getAsJsonArray("uv");
                    JsonArray uvSizeArr = faceObj.getAsJsonArray("uv_size");
                    if (uvArr != null && uvSizeArr != null) {
                        int uvRot = faceObj.has("uv_rotation")
                                ? faceObj.get("uv_rotation").getAsInt() : 0;
                        faceUVs.put(face, new SpFaceUV(
                            uvArr.get(0).getAsFloat(),
                            uvArr.get(1).getAsFloat(),
                            uvSizeArr.get(0).getAsFloat(),
                            uvSizeArr.get(1).getAsFloat(),
                            uvRot
                        ));
                    }
                }
            } else if (uvEl.isJsonArray()) {
                boxUv = true;
                JsonArray uvArr = uvEl.getAsJsonArray();
                float u = uvArr.get(0).getAsFloat();
                float v = uvArr.get(1).getAsFloat();
                float w = size.x, h = size.y, d = size.z;
                faceUVs.put(SpCube.SpFace.SOUTH, new SpFaceUV(u + d + w + d, v + d, w, h));
                faceUVs.put(SpCube.SpFace.NORTH, new SpFaceUV(u + d, v + d, w, h));
                faceUVs.put(SpCube.SpFace.EAST,  new SpFaceUV(u, v + d, d, h));
                faceUVs.put(SpCube.SpFace.WEST,  new SpFaceUV(u + d + w, v + d, d, h));
                faceUVs.put(SpCube.SpFace.UP,    new SpFaceUV(u + d, v, w, d));
                faceUVs.put(SpCube.SpFace.DOWN,  new SpFaceUV(u + d + w, v + d, w, -d));
            }
        }

        return new SpCube(origin, size, inflate, pivot, rotation, mirror, boxUv, faceUVs);
    }

    private static SpVec3 parseVec3(JsonObject obj, String key, SpVec3 fallback) {
        if (!obj.has(key)) return fallback;
        JsonArray arr = obj.getAsJsonArray(key);
        if (arr == null || arr.size() < 3) return fallback;
        return new SpVec3(arr.get(0).getAsFloat(), arr.get(1).getAsFloat(), arr.get(2).getAsFloat());
    }
}
