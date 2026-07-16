package org.zonarstudio.spraute_engine.resource;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.AbstractPackResources;
import net.minecraft.server.packs.PackType;
//? if <1.20.1 {
/*import net.minecraft.server.packs.ResourcePackFileNotFoundException;
*///?}
//? if >=1.20.1 {
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.IoSupplier;
//?}
import org.slf4j.Logger;

import java.io.*;
import java.nio.file.*;
import java.util.*;
//? if <1.20.1 {
/*import java.util.function.Predicate;
*///?}
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A custom resource pack that loads assets from an external folder
 * (e.g. run/spraute_engine/) mapped to the "spraute_engine" namespace.
 *
 * This allows the mod to resolve ResourceLocation("spraute_engine", "textures/entity/...")
 * to files at run/spraute_engine/textures/entity/...
 */
public class ExternalAssetPack extends AbstractPackResources {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String NAMESPACE = "spraute_engine";

    private final Path rootDir;

    /**
     * @param rootDir the root directory containing assets, e.g. "run/spraute_engine/"
     */
    public ExternalAssetPack(Path rootDir) {
        //? if >=1.20.1 {
        super("Spraute Engine External Assets", true);
        //?} else {
        /*super(new File(rootDir.toUri()));
        *///?}
        this.rootDir = rootDir;
    }

    private static String buildTexRef(String rawTexture) {
        if (rawTexture == null || rawTexture.isEmpty()) return null;
        String path = rawTexture.replace('\\', '/').trim();
        String namespace = NAMESPACE;
        int colon = path.indexOf(':');
        if (colon > 0) {
            namespace = path.substring(0, colon);
            path = path.substring(colon + 1);
        }
        // assets/spraute_engine/textures/item/foo.png
        if (path.startsWith("assets/")) {
            String[] parts = path.split("/");
            if (parts.length >= 2) {
                namespace = parts[1];
                path = String.join("/", java.util.Arrays.copyOfRange(parts, 2, parts.length));
            }
        }
        if (path.startsWith("textures/")) {
            path = path.substring("textures/".length());
        }
        if (path.endsWith(".png")) {
            path = path.substring(0, path.length() - 4);
        }
        if (path.isEmpty()) return null;
        return namespace + ":" + path;
    }

    /**
     * Converts a texture path from .spr format (e.g. "textures/item/battery.png")
     * to a valid MC model texture reference (e.g. "spraute_engine:item/battery").
     */
    private static String buildItemTexRef(String rawTexture) {
        String ref = buildTexRef(rawTexture);
        return ref != null ? ref : "minecraft:item/barrier";
    }

    /** {@code minecraft:wooden_sword} → {@code minecraft:item/wooden_sword} for valid item model parents. */
    private static String normalizeItemModelParent(String model) {
        if (model == null || model.isEmpty()) return model;
        int colon = model.indexOf(':');
        if (colon < 0) return model;
        String namespace = model.substring(0, colon);
        String path = model.substring(colon + 1);
        if (!"minecraft".equals(namespace)) return model;
        if (path.contains("/")) return model;
        return namespace + ":item/" + path;
    }

    private static String[] resolveBlockCubeTextures(org.zonarstudio.spraute_engine.registry.CustomBlockRegistry.CustomBlockDef def) {
        String texAll = buildTexRef(def.texture);
        if (texAll == null) texAll = "minecraft:block/stone";

        String texUp = buildTexRef(def.textureUp);
        if (texUp == null) texUp = texAll;

        String texDown = buildTexRef(def.textureDown);
        if (texDown == null) texDown = texUp;

        String texSides = buildTexRef(def.textureSides);

        String texNorth = buildTexRef(def.textureNorth);
        if (texNorth == null) texNorth = texSides != null ? texSides : texAll;

        String texSouth = buildTexRef(def.textureSouth);
        if (texSouth == null) texSouth = texSides != null ? texSides : texAll;

        String texWest = buildTexRef(def.textureWest);
        if (texWest == null) texWest = texSides != null ? texSides : texAll;

        String texEast = buildTexRef(def.textureEast);
        if (texEast == null) texEast = texSides != null ? texSides : texAll;

        if ("minecraft:block/stone".equals(texAll) && texUp != null && !"minecraft:block/stone".equals(texUp)) {
            texAll = texUp;
        }

        return new String[]{texDown, texUp, texNorth, texSouth, texWest, texEast, texAll};
    }

    private Path resolveDiskPath(Path candidate) {
        if (candidate != null && Files.exists(candidate)) {
            return candidate;
        }
        Path parent = candidate != null ? candidate.getParent() : null;
        if (parent == null || !Files.isDirectory(parent)) {
            return null;
        }
        String wanted = candidate.getFileName().toString();
        try (Stream<Path> entries = Files.list(parent)) {
            for (Path entry : entries.collect(Collectors.toList())) {
                if (entry.getFileName().toString().equalsIgnoreCase(wanted)) {
                    return entry;
                }
            }
        } catch (IOException ignored) {
        }
        return null;
    }

    private Path resolveResourcePath(String resourcePath) {
        Path resolved = resolveDiskPath(rootDir.resolve(resourcePath));
        if (resolved != null) {
            return resolved;
        }
        if (resourcePath.startsWith("assets/" + NAMESPACE + "/")) {
            resolved = resolveDiskPath(rootDir.resolve(resourcePath.substring(("assets/" + NAMESPACE + "/").length())));
            if (resolved != null) {
                return resolved;
            }
        }
        if (resourcePath.startsWith("data/" + NAMESPACE + "/")) {
            resolved = resolveDiskPath(rootDir.resolve(resourcePath.substring(("data/" + NAMESPACE + "/").length())));
            if (resolved != null) {
                return resolved;
            }
        }
        // Fallback: custom item textures from .spr definitions
        if (resourcePath.startsWith("assets/" + NAMESPACE + "/textures/item/") && resourcePath.endsWith(".png")) {
            String id = resourcePath.substring(("assets/" + NAMESPACE + "/textures/item/").length(), resourcePath.length() - 4);
            org.zonarstudio.spraute_engine.registry.CustomBlockRegistry.CustomItemDef def =
                    org.zonarstudio.spraute_engine.registry.CustomBlockRegistry.ITEMS.get(id);
            if (def != null) {
                if (def.texture != null && !def.texture.isEmpty()) {
                    String tex = def.texture.replace('\\', '/');
                    if (tex.startsWith("textures/")) {
                        resolved = resolveDiskPath(rootDir.resolve(tex));
                    } else {
                        resolved = resolveDiskPath(rootDir.resolve("textures").resolve(tex));
                    }
                    if (resolved != null) {
                        return resolved;
                    }
                }
                resolved = resolveDiskPath(rootDir.resolve("textures/item/" + id + ".png"));
                if (resolved != null) {
                    return resolved;
                }
                LOGGER.warn("[Spraute Engine] Texture not found for item '{}': expected {} or texture = \"...\" in create item",
                        id, rootDir.resolve("textures/item/" + id + ".png"));
            }
        }
        return null;
    }

    private InputStream getFixedJsonStream(Path file) throws IOException {
        if (file.toString().endsWith(".json")) {
            String content = Files.readString(file);
            content = content.replaceAll("\"post\"\\s*:\\s*\\[", "\"vector\": [");
            content = content.replaceAll("\"pre\"\\s*:\\s*\\[", "\"vector\": [");
            return new ByteArrayInputStream(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        return new BufferedInputStream(Files.newInputStream(file));
    }

    private InputStream generateSoundsJson() {
        Path soundsDir = rootDir.resolve("sounds");
        if (!Files.isDirectory(soundsDir)) {
            return new ByteArrayInputStream("{}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }

        StringBuilder sb = new StringBuilder("{\n");
        boolean first = true;
        try (Stream<Path> stream = Files.walk(soundsDir)) {
            List<Path> oggFiles = stream.filter(p -> p.toString().endsWith(".ogg")).collect(Collectors.toList());
            for (Path p : oggFiles) {
                String relative = soundsDir.relativize(p).toString().replace('\\', '/');
                String name = relative.substring(0, relative.length() - 4);

                if (!first) {
                    sb.append(",\n");
                }
                first = false;

                sb.append("  \"").append(name).append("\": {\n");
                sb.append("    \"category\": \"master\",\n");
                sb.append("    \"sounds\": [\n");
                sb.append("      {\n");
                sb.append("        \"name\": \"").append(NAMESPACE).append(":").append(name).append("\",\n");
                sb.append("        \"stream\": true\n");
                sb.append("      }\n");
                sb.append("    ]\n");
                sb.append("  }");
            }
        } catch (IOException e) {
            LOGGER.error("Failed to scan sounds directory", e);
        }
        sb.append("\n}");
        return new ByteArrayInputStream(sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static void ensureOrphanWorldsDiscovered() {
        org.zonarstudio.spraute_engine.registry.CustomWorldRegistry.ensureParsed();
        org.zonarstudio.spraute_engine.registry.CustomWorldSafety.refreshOrphansFromSaves(
                net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get());
    }

    private static Set<String> allDimensionWorldIds() {
        ensureOrphanWorldsDiscovered();
        Set<String> ids = new HashSet<>(org.zonarstudio.spraute_engine.registry.CustomWorldRegistry.WORLDS.keySet());
        ids.addAll(org.zonarstudio.spraute_engine.registry.CustomWorldRegistry.getOrphanWorldIds());
        return ids;
    }

    private InputStream openResourceByPath(String resourcePath) {
        try {
            org.zonarstudio.spraute_engine.registry.CustomWorldRegistry.ensureParsed();
            org.zonarstudio.spraute_engine.registry.CustomBlockRegistry.ensureParsed();

            if (resourcePath.equals("assets/" + NAMESPACE + "/sounds.json")) {
                return generateSoundsJson();
            }

            if (resourcePath.startsWith("assets/" + NAMESPACE + "/blockstates/")) {
                String id = resourcePath.substring(("assets/" + NAMESPACE + "/blockstates/").length(), resourcePath.length() - 5);
                if ("multiblock_slave".equals(id)) {
                    String json = "{\n  \"variants\": {\n    \"\": { \"model\": \"spraute_engine:block/multiblock_slave\" }\n  }\n}";
                    return new ByteArrayInputStream(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }
                org.zonarstudio.spraute_engine.registry.CustomBlockRegistry.CustomBlockDef def = org.zonarstudio.spraute_engine.registry.CustomBlockRegistry.BLOCKS.get(id);
                if (def != null && (def.model == null || def.model.isEmpty())) {
                    String json = "{\n  \"variants\": {\n    \"\": { \"model\": \"spraute_engine:block/" + id + "\" }\n  }\n}";
                    return new ByteArrayInputStream(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }
            }

            if (resourcePath.startsWith("assets/" + NAMESPACE + "/models/block/")) {
                String id = resourcePath.substring(("assets/" + NAMESPACE + "/models/block/").length(), resourcePath.length() - 5);
                if ("multiblock_slave".equals(id)) {
                    String json = "{\n  \"textures\": { \"particle\": \"minecraft:block/barrier\" },\n  \"elements\": []\n}";
                    return new ByteArrayInputStream(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }
                org.zonarstudio.spraute_engine.registry.CustomBlockRegistry.CustomBlockDef def = org.zonarstudio.spraute_engine.registry.CustomBlockRegistry.BLOCKS.get(id);
                if (def != null && (def.model == null || def.model.isEmpty())) {
                    String[] tex = resolveBlockCubeTextures(def);
                    String texDown = tex[0];
                    String texUp = tex[1];
                    String texNorth = tex[2];
                    String texSouth = tex[3];
                    String texWest = tex[4];
                    String texEast = tex[5];
                    String texAll = tex[6];

                    String json = "{\n" +
                            "  \"parent\": \"minecraft:block/cube\",\n" +
                            "  \"textures\": {\n" +
                            "    \"down\": \"" + texDown + "\",\n" +
                            "    \"up\": \"" + texUp + "\",\n" +
                            "    \"north\": \"" + texNorth + "\",\n" +
                            "    \"south\": \"" + texSouth + "\",\n" +
                            "    \"west\": \"" + texWest + "\",\n" +
                            "    \"east\": \"" + texEast + "\",\n" +
                            "    \"particle\": \"" + texAll + "\"\n" +
                            "  }\n" +
                            "}";
                    return new ByteArrayInputStream(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }
            }

            if (resourcePath.startsWith("assets/" + NAMESPACE + "/models/item/")) {
                String id = resourcePath.substring(("assets/" + NAMESPACE + "/models/item/").length(), resourcePath.length() - 5);

                org.zonarstudio.spraute_engine.registry.CustomBlockRegistry.CustomBlockDef blockDef = org.zonarstudio.spraute_engine.registry.CustomBlockRegistry.BLOCKS.get(id);
                if (blockDef != null && (blockDef.model == null || blockDef.model.isEmpty())) {
                    String json = "{\n  \"parent\": \"spraute_engine:block/" + id + "\"\n}";
                    return new ByteArrayInputStream(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }

                org.zonarstudio.spraute_engine.registry.CustomBlockRegistry.CustomItemDef itemDef = org.zonarstudio.spraute_engine.registry.CustomBlockRegistry.ITEMS.get(id);
                if (itemDef != null) {
                    String texRef = buildItemTexRef(itemDef.texture);
                    boolean hasGeo = itemDef.geo != null && !itemDef.geo.isEmpty();
                    if (hasGeo) {
                        String json = "{\n  \"parent\": \"builtin/entity\"\n}";
                        return new ByteArrayInputStream(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    }
                    if (itemDef.model != null && !itemDef.model.isEmpty()) {
                        String parent = normalizeItemModelParent(itemDef.model);
                        String json = "{\n  \"parent\": \"" + parent + "\",\n  \"textures\": {\n    \"layer0\": \"" + texRef + "\"\n  }\n}";
                        return new ByteArrayInputStream(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    } else {
                        String json = "{\n  \"parent\": \"item/generated\",\n  \"textures\": {\n    \"layer0\": \"" + texRef + "\"\n  }\n}";
                        return new ByteArrayInputStream(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    }
                }
            }

            if (resourcePath.startsWith("data/" + NAMESPACE + "/worldgen/configured_feature/")) {
                String id = resourcePath.substring(("data/" + NAMESPACE + "/worldgen/configured_feature/").length(), resourcePath.length() - 5);
                if (id.endsWith("_ore")) {
                    String blockId = id.substring(0, id.length() - 4);
                    org.zonarstudio.spraute_engine.registry.CustomBlockRegistry.CustomBlockDef def = org.zonarstudio.spraute_engine.registry.CustomBlockRegistry.BLOCKS.get(blockId);
                    if (def != null && def.isOre) {
                        String targets = "";
                        if (def.oreDimension.contains("nether")) {
                            targets = "      {\n" +
                                    "        \"state\": {\n" +
                                    "          \"Name\": \"spraute_engine:" + blockId + "\"\n" +
                                    "        },\n" +
                                    "        \"target\": {\n" +
                                    "          \"predicate_type\": \"minecraft:tag_match\",\n" +
                                    "          \"tag\": \"minecraft:base_stone_nether\"\n" +
                                    "        }\n" +
                                    "      }\n";
                        } else if (def.oreDimension.contains("end")) {
                            targets = "      {\n" +
                                    "        \"state\": {\n" +
                                    "          \"Name\": \"spraute_engine:" + blockId + "\"\n" +
                                    "        },\n" +
                                    "        \"target\": {\n" +
                                    "          \"predicate_type\": \"minecraft:block_match\",\n" +
                                    "          \"block\": \"minecraft:end_stone\"\n" +
                                    "        }\n" +
                                    "      }\n";
                        } else {
                            targets = "      {\n" +
                                    "        \"state\": {\n" +
                                    "          \"Name\": \"spraute_engine:" + blockId + "\"\n" +
                                    "        },\n" +
                                    "        \"target\": {\n" +
                                    "          \"predicate_type\": \"minecraft:tag_match\",\n" +
                                    "          \"tag\": \"minecraft:stone_ore_replaceables\"\n" +
                                    "        }\n" +
                                    "      },\n" +
                                    "      {\n" +
                                    "        \"state\": {\n" +
                                    "          \"Name\": \"spraute_engine:" + blockId + "\"\n" +
                                    "        },\n" +
                                    "        \"target\": {\n" +
                                    "          \"predicate_type\": \"minecraft:tag_match\",\n" +
                                    "          \"tag\": \"minecraft:deepslate_ore_replaceables\"\n" +
                                    "        }\n" +
                                    "      }\n";
                        }
                        String json = "{\n" +
                                "  \"type\": \"minecraft:ore\",\n" +
                                "  \"config\": {\n" +
                                "    \"discard_chance_on_air_exposure\": 0.0,\n" +
                                "    \"size\": " + def.oreVeinSize + ",\n" +
                                "    \"targets\": [\n" + targets +
                                "    ]\n" +
                                "  }\n" +
                                "}";
                        return new ByteArrayInputStream(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    }
                }
            }

            if (resourcePath.startsWith("data/" + NAMESPACE + "/worldgen/placed_feature/")) {
                String id = resourcePath.substring(("data/" + NAMESPACE + "/worldgen/placed_feature/").length(), resourcePath.length() - 5);
                if (id.endsWith("_ore")) {
                    String blockId = id.substring(0, id.length() - 4);
                    org.zonarstudio.spraute_engine.registry.CustomBlockRegistry.CustomBlockDef def = org.zonarstudio.spraute_engine.registry.CustomBlockRegistry.BLOCKS.get(blockId);
                    if (def != null && def.isOre) {
                        String json = "{\n" +
                                "  \"feature\": \"spraute_engine:" + blockId + "_ore\",\n" +
                                "  \"placement\": [\n" +
                                "    {\n" +
                                "      \"type\": \"minecraft:count\",\n" +
                                "      \"count\": " + def.oreChances + "\n" +
                                "    },\n" +
                                "    {\n" +
                                "      \"type\": \"minecraft:in_square\"\n" +
                                "    },\n" +
                                "    {\n" +
                                "      \"type\": \"minecraft:height_range\",\n" +
                                "      \"height\": {\n" +
                                "        \"type\": \"minecraft:uniform\",\n" +
                                "        \"max_inclusive\": {\n" +
                                "          \"absolute\": " + def.oreMaxY + "\n" +
                                "        },\n" +
                                "        \"min_inclusive\": {\n" +
                                "          \"absolute\": " + def.oreMinY + "\n" +
                                "        }\n" +
                                "      }\n" +
                                "    },\n" +
                                "    {\n" +
                                "      \"type\": \"minecraft:biome\"\n" +
                                "    }\n" +
                                "  ]\n" +
                                "}";
                        return new ByteArrayInputStream(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    }
                }
            }

            if (resourcePath.startsWith("data/" + NAMESPACE + "/forge/biome_modifier/")) {
                String id = resourcePath.substring(("data/" + NAMESPACE + "/forge/biome_modifier/").length(), resourcePath.length() - 5);
                if (id.endsWith("_ore")) {
                    String blockId = id.substring(0, id.length() - 4);
                    org.zonarstudio.spraute_engine.registry.CustomBlockRegistry.CustomBlockDef def = org.zonarstudio.spraute_engine.registry.CustomBlockRegistry.BLOCKS.get(blockId);
                    if (def != null && def.isOre) {
                        String biomeTag = switch (def.oreDimension) {
                            case "minecraft:overworld", "overworld" -> "#minecraft:is_overworld";
                            case "minecraft:the_nether", "nether" -> "#minecraft:is_nether";
                            case "minecraft:the_end", "end" -> "#minecraft:is_end";
                            default -> def.oreDimension;
                        };
                        String json = "{\n" +
                                "  \"type\": \"forge:add_features\",\n" +
                                "  \"biomes\": \"" + biomeTag + "\",\n" +
                                "  \"features\": \"spraute_engine:" + blockId + "_ore\",\n" +
                                "  \"step\": \"underground_ores\"\n" +
                                "}";
                        return new ByteArrayInputStream(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    }
                }
            }

            if (resourcePath.startsWith("assets/" + NAMESPACE + "/particles/")) {
                String id = resourcePath.substring(("assets/" + NAMESPACE + "/particles/").length(), resourcePath.length() - 5);
                org.zonarstudio.spraute_engine.registry.CustomParticleRegistry.CustomParticleDef def = org.zonarstudio.spraute_engine.registry.CustomParticleRegistry.PARTICLES.get(id);
                if (def != null && def.texture != null) {
                    String texturePath = def.texture;
                    if (!texturePath.contains(":")) {
                        texturePath = NAMESPACE + ":" + texturePath;
                    }
                    String json = "{\n  \"textures\": [\n    \"" + texturePath + "\"\n  ]\n}";
                    return new ByteArrayInputStream(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }
            }

            if (resourcePath.startsWith("data/" + NAMESPACE + "/recipes/")) {
                String recipeId = resourcePath.substring(("data/" + NAMESPACE + "/recipes/").length(), resourcePath.length() - 5);
                if (org.zonarstudio.spraute_engine.registry.CustomBlockRegistry.CUSTOM_RECIPES_JSON.containsKey(recipeId)) {
                    return new ByteArrayInputStream(org.zonarstudio.spraute_engine.registry.CustomBlockRegistry.CUSTOM_RECIPES_JSON.get(recipeId).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }
            }

            if (resourcePath.startsWith("data/" + NAMESPACE + "/tags/items/")) {
                String tagId = resourcePath.substring(("data/" + NAMESPACE + "/tags/items/").length(), resourcePath.length() - 5);
                if (org.zonarstudio.spraute_engine.registry.CustomBlockRegistry.CUSTOM_CRAFT_TAGS_JSON.containsKey(tagId)) {
                    return new ByteArrayInputStream(org.zonarstudio.spraute_engine.registry.CustomBlockRegistry.CUSTOM_CRAFT_TAGS_JSON.get(tagId).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }
            }

            if (resourcePath.startsWith("data/" + NAMESPACE + "/dimension/")) {
                String id = resourcePath.substring(("data/" + NAMESPACE + "/dimension/").length(), resourcePath.length() - 5);
                ensureOrphanWorldsDiscovered();
                org.zonarstudio.spraute_engine.registry.CustomWorldRegistry.CustomWorldDef def =
                        org.zonarstudio.spraute_engine.registry.CustomWorldRegistry.resolveWorldDef(id);
                if (def != null) {
                    String json = org.zonarstudio.spraute_engine.registry.CustomWorldRegistry.buildDimensionJson(def);
                    return new ByteArrayInputStream(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }
            }

            if (resourcePath.startsWith("data/" + NAMESPACE + "/dimension_type/")) {
                String typeId = resourcePath.substring(("data/" + NAMESPACE + "/dimension_type/").length(), resourcePath.length() - 5);
                if (typeId.endsWith("_type")) {
                    String worldId = typeId.substring(0, typeId.length() - 5);
                    ensureOrphanWorldsDiscovered();
                    org.zonarstudio.spraute_engine.registry.CustomWorldRegistry.CustomWorldDef def =
                            org.zonarstudio.spraute_engine.registry.CustomWorldRegistry.resolveWorldDef(worldId);
                    if (def != null) {
                        boolean orphan = org.zonarstudio.spraute_engine.registry.CustomWorldRegistry.isOrphanWorld(worldId);
                        String json = org.zonarstudio.spraute_engine.registry.CustomWorldRegistry.buildDimensionTypeJson(def, orphan);
                        return new ByteArrayInputStream(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    }
                }
            }

            Path resolved = resolveResourcePath(resourcePath);
            if (resolved != null) {
                return getFixedJsonStream(resolved);
            }

            return null;
        } catch (IOException e) {
            LOGGER.warn("[Spraute Engine] Failed to open external resource {}: {}", resourcePath, e.getMessage());
            return null;
        }
    }

    private boolean hasResourceByPath(String resourcePath) {
        org.zonarstudio.spraute_engine.registry.CustomWorldRegistry.ensureParsed();
        org.zonarstudio.spraute_engine.registry.CustomBlockRegistry.ensureParsed();

        if (resourcePath.equals("assets/" + NAMESPACE + "/sounds.json")) return true;
        if (resourcePath.startsWith("assets/" + NAMESPACE + "/blockstates/")) {
            String id = resourcePath.substring(("assets/" + NAMESPACE + "/blockstates/").length(), resourcePath.length() - 5);
            if ("multiblock_slave".equals(id)) return true;
            org.zonarstudio.spraute_engine.registry.CustomBlockRegistry.CustomBlockDef def = org.zonarstudio.spraute_engine.registry.CustomBlockRegistry.BLOCKS.get(id);
            if (def != null && (def.model == null || def.model.isEmpty())) return true;
        }
        if (resourcePath.startsWith("assets/" + NAMESPACE + "/models/block/")) {
            String id = resourcePath.substring(("assets/" + NAMESPACE + "/models/block/").length(), resourcePath.length() - 5);
            if ("multiblock_slave".equals(id)) return true;
            org.zonarstudio.spraute_engine.registry.CustomBlockRegistry.CustomBlockDef def = org.zonarstudio.spraute_engine.registry.CustomBlockRegistry.BLOCKS.get(id);
            if (def != null && (def.model == null || def.model.isEmpty())) return true;
        }
        if (resourcePath.startsWith("assets/" + NAMESPACE + "/models/item/")) {
            String id = resourcePath.substring(("assets/" + NAMESPACE + "/models/item/").length(), resourcePath.length() - 5);
            if (org.zonarstudio.spraute_engine.registry.CustomBlockRegistry.BLOCKS.containsKey(id) || org.zonarstudio.spraute_engine.registry.CustomBlockRegistry.ITEMS.containsKey(id)) {
                return true;
            }
        }
        if (resourcePath.startsWith("data/" + NAMESPACE + "/worldgen/configured_feature/")) {
            String id = resourcePath.substring(("data/" + NAMESPACE + "/worldgen/configured_feature/").length(), resourcePath.length() - 5);
            if (id.endsWith("_ore")) {
                String blockId = id.substring(0, id.length() - 4);
                org.zonarstudio.spraute_engine.registry.CustomBlockRegistry.CustomBlockDef def = org.zonarstudio.spraute_engine.registry.CustomBlockRegistry.BLOCKS.get(blockId);
                if (def != null && def.isOre) return true;
            }
        }
        if (resourcePath.startsWith("data/" + NAMESPACE + "/worldgen/placed_feature/")) {
            String id = resourcePath.substring(("data/" + NAMESPACE + "/worldgen/placed_feature/").length(), resourcePath.length() - 5);
            if (id.endsWith("_ore")) {
                String blockId = id.substring(0, id.length() - 4);
                org.zonarstudio.spraute_engine.registry.CustomBlockRegistry.CustomBlockDef def = org.zonarstudio.spraute_engine.registry.CustomBlockRegistry.BLOCKS.get(blockId);
                if (def != null && def.isOre) return true;
            }
        }
        if (resourcePath.startsWith("data/" + NAMESPACE + "/forge/biome_modifier/")) {
            String id = resourcePath.substring(("data/" + NAMESPACE + "/forge/biome_modifier/").length(), resourcePath.length() - 5);
            if (id.endsWith("_ore")) {
                String blockId = id.substring(0, id.length() - 4);
                org.zonarstudio.spraute_engine.registry.CustomBlockRegistry.CustomBlockDef def = org.zonarstudio.spraute_engine.registry.CustomBlockRegistry.BLOCKS.get(blockId);
                if (def != null && def.isOre) return true;
            }
        }
        if (resourcePath.startsWith("assets/" + NAMESPACE + "/particles/")) {
            String id = resourcePath.substring(("assets/" + NAMESPACE + "/particles/").length(), resourcePath.length() - 5);
            org.zonarstudio.spraute_engine.registry.CustomParticleRegistry.CustomParticleDef def = org.zonarstudio.spraute_engine.registry.CustomParticleRegistry.PARTICLES.get(id);
            if (def != null) return true;
        }

        if (resourcePath.startsWith("data/" + NAMESPACE + "/recipes/")) {
            String recipeId = resourcePath.substring(("data/" + NAMESPACE + "/recipes/").length(), resourcePath.length() - 5);
            if (org.zonarstudio.spraute_engine.registry.CustomBlockRegistry.CUSTOM_RECIPES_JSON.containsKey(recipeId)) return true;
        }

        if (resourcePath.startsWith("data/" + NAMESPACE + "/tags/items/")) {
            String tagId = resourcePath.substring(("data/" + NAMESPACE + "/tags/items/").length(), resourcePath.length() - 5);
            if (org.zonarstudio.spraute_engine.registry.CustomBlockRegistry.CUSTOM_CRAFT_TAGS_JSON.containsKey(tagId)) return true;
        }

        if (resourcePath.startsWith("data/" + NAMESPACE + "/dimension/")) {
            String id = resourcePath.substring(("data/" + NAMESPACE + "/dimension/").length(), resourcePath.length() - 5);
            ensureOrphanWorldsDiscovered();
            if (org.zonarstudio.spraute_engine.registry.CustomWorldRegistry.resolveWorldDef(id) != null) return true;
        }
        if (resourcePath.startsWith("data/" + NAMESPACE + "/dimension_type/")) {
            String typeId = resourcePath.substring(("data/" + NAMESPACE + "/dimension_type/").length(), resourcePath.length() - 5);
            if (typeId.endsWith("_type")) {
                String worldId = typeId.substring(0, typeId.length() - 5);
                ensureOrphanWorldsDiscovered();
                if (org.zonarstudio.spraute_engine.registry.CustomWorldRegistry.resolveWorldDef(worldId) != null) return true;
            }
        }

        if (resourcePath.startsWith("assets/" + NAMESPACE + "/textures/item/") && resourcePath.endsWith(".png")) {
            String texId = resourcePath.substring(("assets/" + NAMESPACE + "/textures/item/").length(), resourcePath.length() - 4);
            if (org.zonarstudio.spraute_engine.registry.CustomBlockRegistry.ITEMS.containsKey(texId)) {
                return true;
            }
        }

        Path resolved = resolveResourcePath(resourcePath);
        if (resolved != null) return true;

        return false;
    }

    private String packPathForLocation(PackType type, ResourceLocation location) {
        String prefix = type == PackType.CLIENT_RESOURCES ? "assets" : "data";
        return prefix + "/" + NAMESPACE + "/" + location.getPath();
    }

    /** True when {@code scanPrefix} is an ancestor of, equal to, or a descendant of {@code ourPath}. */
    private static boolean prefixOverlaps(String scanPrefix, String ourPath) {
        if (scanPrefix == null || scanPrefix.isEmpty()) return true;
        return ourPath.startsWith(scanPrefix) || scanPrefix.startsWith(ourPath);
    }

    private static void addResourceLocation(List<ResourceLocation> list, String path) {
        ResourceLocation loc = org.zonarstudio.spraute_engine.util.SprauteResourcePath.tryCreateLocation(NAMESPACE, path);
        if (loc != null) {
            list.add(loc);
        } else {
            LOGGER.warn("[Spraute Engine] Skipping invalid resource path: {}", path);
        }
    }

    /** {@code dimension_type} also {@code startsWith("dimension")} — list folders separately. */
    private static boolean isDimensionDataPrefix(String pathPrefix) {
        return pathPrefix.equals("dimension") || pathPrefix.startsWith("dimension/");
    }

    private static boolean isDimensionTypeDataPrefix(String pathPrefix) {
        return pathPrefix.equals("dimension_type") || pathPrefix.startsWith("dimension_type/");
    }

    private List<ResourceLocation> collectResourceLocations(PackType type, String pathPrefix) {
        List<ResourceLocation> list = new ArrayList<>();

        if (type == PackType.SERVER_DATA && pathPrefix.startsWith("worldgen/configured_feature")) {
            for (org.zonarstudio.spraute_engine.registry.CustomBlockRegistry.CustomBlockDef def : org.zonarstudio.spraute_engine.registry.CustomBlockRegistry.BLOCKS.values()) {
                if (def.isOre) addResourceLocation(list, "worldgen/configured_feature/" + def.id + "_ore.json");
            }
        }
        if (type == PackType.SERVER_DATA && pathPrefix.startsWith("worldgen/placed_feature")) {
            for (org.zonarstudio.spraute_engine.registry.CustomBlockRegistry.CustomBlockDef def : org.zonarstudio.spraute_engine.registry.CustomBlockRegistry.BLOCKS.values()) {
                if (def.isOre) addResourceLocation(list, "worldgen/placed_feature/" + def.id + "_ore.json");
            }
        }
        if (type == PackType.SERVER_DATA && pathPrefix.startsWith("forge/biome_modifier")) {
            for (org.zonarstudio.spraute_engine.registry.CustomBlockRegistry.CustomBlockDef def : org.zonarstudio.spraute_engine.registry.CustomBlockRegistry.BLOCKS.values()) {
                if (def.isOre) addResourceLocation(list, "forge/biome_modifier/" + def.id + "_ore.json");
            }
        }

        if (type == PackType.CLIENT_RESOURCES && pathPrefix.startsWith("particles")) {
            for (org.zonarstudio.spraute_engine.registry.CustomParticleRegistry.CustomParticleDef def : org.zonarstudio.spraute_engine.registry.CustomParticleRegistry.PARTICLES.values()) {
                addResourceLocation(list, "particles/" + def.id + ".json");
            }
        }

        if (type == PackType.SERVER_DATA && pathPrefix.startsWith("recipes")) {
            for (String recipeId : org.zonarstudio.spraute_engine.registry.CustomBlockRegistry.CUSTOM_RECIPES_JSON.keySet()) {
                addResourceLocation(list, "recipes/" + recipeId + ".json");
            }
        }

        if (type == PackType.SERVER_DATA && pathPrefix.startsWith("tags/items")) {
            for (String tagId : org.zonarstudio.spraute_engine.registry.CustomBlockRegistry.CUSTOM_CRAFT_TAGS_JSON.keySet()) {
                addResourceLocation(list, "tags/items/" + tagId + ".json");
            }
        }

        if (type == PackType.SERVER_DATA && isDimensionDataPrefix(pathPrefix)) {
            for (String worldId : allDimensionWorldIds()) {
                addResourceLocation(list, "dimension/" + worldId + ".json");
            }
        }
        if (type == PackType.SERVER_DATA && isDimensionTypeDataPrefix(pathPrefix)) {
            for (String worldId : allDimensionWorldIds()) {
                addResourceLocation(list, "dimension_type/" + worldId + "_type.json");
            }
        }

        // NOTE: Minecraft's model/texture scan uses a broad prefix like "models" or "textures",
        // so we must list our generated entries when the requested prefix is an ancestor of our
        // path (e.g. "models" → "models/item/...") OR a descendant of it.
        if (type == PackType.CLIENT_RESOURCES && prefixOverlaps(pathPrefix, "models/item")) {
            for (String itemId : org.zonarstudio.spraute_engine.registry.CustomBlockRegistry.ITEMS.keySet()) {
                addResourceLocation(list, "models/item/" + itemId + ".json");
            }
        }

        if (type == PackType.CLIENT_RESOURCES && prefixOverlaps(pathPrefix, "textures/item")) {
            for (org.zonarstudio.spraute_engine.registry.CustomBlockRegistry.CustomItemDef def
                    : org.zonarstudio.spraute_engine.registry.CustomBlockRegistry.ITEMS.values()) {
                addResourceLocation(list, "textures/item/" + def.id + ".png");
            }
        }

        Path searchDir = rootDir.resolve(pathPrefix);
        if (!Files.isDirectory(searchDir)) {
            return list;
        }

        try (Stream<Path> stream = Files.walk(searchDir)) {
            List<ResourceLocation> fileList = stream
                    .filter(Files::isRegularFile)
                    .map(path -> {
                        String relative = rootDir.relativize(path).toString().replace('\\', '/');
                        return org.zonarstudio.spraute_engine.util.SprauteResourcePath.tryCreateLocation(NAMESPACE, relative);
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            list.addAll(fileList);
        } catch (IOException e) {
            LOGGER.warn("[Spraute Engine] Error scanning external assets in {}: {}", searchDir, e.getMessage());
        }
        return list;
    }

    //? if <1.20.1 {
    /*@Override
    protected InputStream getResource(String resourcePath) throws IOException {
        InputStream stream = openResourceByPath(resourcePath);
        if (stream == null) {
            throw new ResourcePackFileNotFoundException(rootDir.toFile(), resourcePath);
        }
        return stream;
    }

    @Override
    protected boolean hasResource(String resourcePath) {
        return hasResourceByPath(resourcePath);
    }

    @Override
    public InputStream getResource(PackType type, ResourceLocation location) throws IOException {
        if ((type == PackType.CLIENT_RESOURCES || type == PackType.SERVER_DATA) && location.getNamespace().equals(NAMESPACE)) {
            return getResource(packPathForLocation(type, location));
        }
        throw new ResourcePackFileNotFoundException(rootDir.toFile(), String.format("%s/%s/%s", type.getDirectory(), location.getNamespace(), location.getPath()));
    }

    @Override
    public boolean hasResource(PackType type, ResourceLocation location) {
        if ((type == PackType.CLIENT_RESOURCES || type == PackType.SERVER_DATA) && location.getNamespace().equals(NAMESPACE)) {
            return hasResource(packPathForLocation(type, location));
        }
        return false;
    }

    @Override
    public Set<String> getNamespaces(PackType type) {
        if ((type == PackType.CLIENT_RESOURCES || type == PackType.SERVER_DATA) && Files.isDirectory(rootDir)) {
            return Set.of(NAMESPACE);
        }
        return Collections.emptySet();
    }

    @Override
    public Collection<ResourceLocation> getResources(PackType type, String namespace, String pathPrefix, Predicate<ResourceLocation> filter) {
        if ((type != PackType.CLIENT_RESOURCES && type != PackType.SERVER_DATA) || !namespace.equals(NAMESPACE)) {
            return Collections.emptyList();
        }
        return collectResourceLocations(type, pathPrefix).stream().filter(filter).collect(Collectors.toList());
    }

    @Override
    public void close() {
    }

    @Override
    public String getName() {
        return "Spraute Engine External Assets";
    }
    *///?}

    //? if >=1.20.1 {
    @Override
    public IoSupplier<InputStream> getRootResource(String... paths) {
        String resourcePath = String.join("/", paths);
        if (!hasResourceByPath(resourcePath)) {
            Path resolved = rootDir.resolve(resourcePath);
            if (!Files.exists(resolved)) {
                return null;
            }
            return () -> {
                try {
                    return getFixedJsonStream(resolved);
                } catch (IOException e) {
                    LOGGER.warn("[Spraute Engine] Failed to open root resource {}: {}", resourcePath, e.getMessage());
                    return null;
                }
            };
        }
        return () -> openResourceByPath(resourcePath);
    }

    @Override
    public IoSupplier<InputStream> getResource(PackType type, ResourceLocation location) {
        if ((type == PackType.CLIENT_RESOURCES || type == PackType.SERVER_DATA) && location.getNamespace().equals(NAMESPACE)) {
            String resourcePath = packPathForLocation(type, location);
            if (!hasResourceByPath(resourcePath)) {
                return null;
            }
            return () -> openResourceByPath(resourcePath);
        }
        return null;
    }

    @Override
    public void listResources(PackType type, String namespace, String path, PackResources.ResourceOutput resourceOutput) {
        if ((type != PackType.CLIENT_RESOURCES && type != PackType.SERVER_DATA) || !namespace.equals(NAMESPACE)) {
            return;
        }

        for (ResourceLocation location : collectResourceLocations(type, path)) {
            String resourcePath = packPathForLocation(type, location);
            if (hasResourceByPath(resourcePath)) {
                resourceOutput.accept(location, () -> openResourceByPath(resourcePath));
            }
        }
    }

    @Override
    public Set<String> getNamespaces(PackType type) {
        if ((type == PackType.CLIENT_RESOURCES || type == PackType.SERVER_DATA) && Files.isDirectory(rootDir)) {
            return Set.of(NAMESPACE);
        }
        return Collections.emptySet();
    }

    @Override
    public void close() {
    }
    //?}
}
