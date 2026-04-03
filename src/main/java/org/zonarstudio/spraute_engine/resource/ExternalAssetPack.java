package org.zonarstudio.spraute_engine.resource;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.AbstractPackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.ResourcePackFileNotFoundException;
import org.slf4j.Logger;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.Predicate;
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
        super(new File(rootDir.toUri()));
        this.rootDir = rootDir;
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
                // Remove .ogg extension for the sound event name and the sound path in sounds.json
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

    @Override
    protected InputStream getResource(String resourcePath) throws IOException {
        if (resourcePath.equals("assets/" + NAMESPACE + "/sounds.json")) {
            return generateSoundsJson();
        }
        
        // Handle generated models and blockstates
        if (resourcePath.startsWith("assets/" + NAMESPACE + "/blockstates/")) {
            String id = resourcePath.substring(("assets/" + NAMESPACE + "/blockstates/").length(), resourcePath.length() - 5);
            org.zonarstudio.spraute_engine.registry.CustomBlockRegistry.CustomBlockDef def = org.zonarstudio.spraute_engine.registry.CustomBlockRegistry.BLOCKS.get(id);
            if (def != null && (def.model == null || def.model.isEmpty())) {
                String json = "{\n  \"variants\": {\n    \"\": { \"model\": \"spraute_engine:block/" + id + "\" }\n  }\n}";
                return new ByteArrayInputStream(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
        }
        
        if (resourcePath.startsWith("assets/" + NAMESPACE + "/models/block/")) {
            String id = resourcePath.substring(("assets/" + NAMESPACE + "/models/block/").length(), resourcePath.length() - 5);
            org.zonarstudio.spraute_engine.registry.CustomBlockRegistry.CustomBlockDef def = org.zonarstudio.spraute_engine.registry.CustomBlockRegistry.BLOCKS.get(id);
            if (def != null && (def.model == null || def.model.isEmpty())) {
                String texAll = def.texture != null ? "spraute_engine:" + def.texture : "minecraft:block/stone";
                String texUp = def.textureUp != null ? "spraute_engine:" + def.textureUp : texAll;
                String texDown = def.textureDown != null ? "spraute_engine:" + def.textureDown : texAll;
                String texNorth = def.textureNorth != null ? "spraute_engine:" + def.textureNorth : texAll;
                String texSouth = def.textureSouth != null ? "spraute_engine:" + def.textureSouth : texAll;
                String texEast = def.textureEast != null ? "spraute_engine:" + def.textureEast : texAll;
                String texWest = def.textureWest != null ? "spraute_engine:" + def.textureWest : texAll;
                
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
                if (itemDef.model != null && !itemDef.model.isEmpty()) {
                    String json = "{\n  \"parent\": \"" + itemDef.model + "\",\n  \"textures\": {\n    \"layer0\": \"spraute_engine:" + itemDef.texture + "\"\n  }\n}";
                    return new ByteArrayInputStream(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                } else {
                    String tex = itemDef.texture != null ? "spraute_engine:" + itemDef.texture : "minecraft:item/stick";
                    String json = "{\n  \"parent\": \"minecraft:item/generated\",\n  \"textures\": {\n    \"layer0\": \"" + tex + "\"\n  }\n}";
                    return new ByteArrayInputStream(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }
            }
        }
        
        // Handle server data generation (ores)
        if (resourcePath.startsWith("data/" + NAMESPACE + "/worldgen/configured_feature/")) {
            String id = resourcePath.substring(("data/" + NAMESPACE + "/worldgen/configured_feature/").length(), resourcePath.length() - 5);
            if (id.endsWith("_ore")) {
                String blockId = id.substring(0, id.length() - 4);
                org.zonarstudio.spraute_engine.registry.CustomBlockRegistry.CustomBlockDef def = org.zonarstudio.spraute_engine.registry.CustomBlockRegistry.BLOCKS.get(blockId);
                if (def != null && def.isOre) {
                    String json = "{\n" +
                            "  \"type\": \"minecraft:ore\",\n" +
                            "  \"config\": {\n" +
                            "    \"discard_chance_on_air_exposure\": 0.0,\n" +
                            "    \"size\": " + def.oreVeinSize + ",\n" +
                            "    \"targets\": [\n" +
                            "      {\n" +
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
                            "      }\n" +
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
                    String json = "{\n" +
                            "  \"type\": \"forge:add_features\",\n" +
                            "  \"biomes\": \"#minecraft:is_overworld\",\n" +
                            "  \"features\": \"spraute_engine:" + blockId + "_ore\",\n" +
                            "  \"step\": \"underground_ores\"\n" +
                            "}";
                    return new ByteArrayInputStream(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }
            }
        }
        
        Path resolved = rootDir.resolve(resourcePath);
        if (Files.exists(resolved)) {
            return getFixedJsonStream(resolved);
        }
        throw new ResourcePackFileNotFoundException(rootDir.toFile(), resourcePath);
    }

    @Override
    protected boolean hasResource(String resourcePath) {
        if (resourcePath.equals("assets/" + NAMESPACE + "/sounds.json")) return true;
        if (resourcePath.startsWith("assets/" + NAMESPACE + "/blockstates/")) {
            String id = resourcePath.substring(("assets/" + NAMESPACE + "/blockstates/").length(), resourcePath.length() - 5);
            org.zonarstudio.spraute_engine.registry.CustomBlockRegistry.CustomBlockDef def = org.zonarstudio.spraute_engine.registry.CustomBlockRegistry.BLOCKS.get(id);
            if (def != null && (def.model == null || def.model.isEmpty())) return true;
        }
        if (resourcePath.startsWith("assets/" + NAMESPACE + "/models/block/")) {
            String id = resourcePath.substring(("assets/" + NAMESPACE + "/models/block/").length(), resourcePath.length() - 5);
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
        return Files.exists(rootDir.resolve(resourcePath));
    }

    @Override
    public InputStream getResource(PackType type, ResourceLocation location) throws IOException {
        if ((type == PackType.CLIENT_RESOURCES || type == PackType.SERVER_DATA) && location.getNamespace().equals(NAMESPACE)) {
            String prefix = type == PackType.CLIENT_RESOURCES ? "assets" : "data";
            return getResource(prefix + "/" + NAMESPACE + "/" + location.getPath());
        }
        throw new ResourcePackFileNotFoundException(rootDir.toFile(), String.format("%s/%s/%s", type.getDirectory(), location.getNamespace(), location.getPath()));
    }

    @Override
    public boolean hasResource(PackType type, ResourceLocation location) {
        if ((type == PackType.CLIENT_RESOURCES || type == PackType.SERVER_DATA) && location.getNamespace().equals(NAMESPACE)) {
            String prefix = type == PackType.CLIENT_RESOURCES ? "assets" : "data";
            return hasResource(prefix + "/" + NAMESPACE + "/" + location.getPath());
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

        List<ResourceLocation> list = new ArrayList<>();
        
        if (type == PackType.SERVER_DATA && pathPrefix.startsWith("worldgen/configured_feature")) {
            for (org.zonarstudio.spraute_engine.registry.CustomBlockRegistry.CustomBlockDef def : org.zonarstudio.spraute_engine.registry.CustomBlockRegistry.BLOCKS.values()) {
                if (def.isOre) list.add(new ResourceLocation(NAMESPACE, "worldgen/configured_feature/" + def.id + "_ore.json"));
            }
        }
        if (type == PackType.SERVER_DATA && pathPrefix.startsWith("worldgen/placed_feature")) {
            for (org.zonarstudio.spraute_engine.registry.CustomBlockRegistry.CustomBlockDef def : org.zonarstudio.spraute_engine.registry.CustomBlockRegistry.BLOCKS.values()) {
                if (def.isOre) list.add(new ResourceLocation(NAMESPACE, "worldgen/placed_feature/" + def.id + "_ore.json"));
            }
        }
        if (type == PackType.SERVER_DATA && pathPrefix.startsWith("forge/biome_modifier")) {
            for (org.zonarstudio.spraute_engine.registry.CustomBlockRegistry.CustomBlockDef def : org.zonarstudio.spraute_engine.registry.CustomBlockRegistry.BLOCKS.values()) {
                if (def.isOre) list.add(new ResourceLocation(NAMESPACE, "forge/biome_modifier/" + def.id + "_ore.json"));
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
                        return new ResourceLocation(NAMESPACE, relative);
                    })
                    .filter(filter)
                    .collect(Collectors.toList());
            list.addAll(fileList);
            return list;
        } catch (IOException e) {
            LOGGER.warn("[Spraute Engine] Error scanning external assets in {}: {}", searchDir, e.getMessage());
            return list;
        }
    }

    @Override
    public void close() {
        // Nothing to close
    }

    @Override
    public String getName() {
        return "Spraute Engine External Assets";
    }
}
