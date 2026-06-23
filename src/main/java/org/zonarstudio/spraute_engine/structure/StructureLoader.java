package org.zonarstudio.spraute_engine.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Loads and places structures from various file formats:
 *   .nbt       – Minecraft native Structure Template
 *   .schem     – Sponge Schematic (WorldEdit 7+, 1.13+)
 *   .schematic – Legacy WorldEdit schematic (1.12, numeric IDs – best-effort)
 *   .litematic – Litematica mod format
 */
public class StructureLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger("Spraute StructureLoader");

    public static final Path STRUCTURES_DIR = Paths.get("spraute_engine", "structures");

    /** Resolves and returns the structure file, trying all supported extensions. */
    public static File resolve(String name) {
        String[] extensions = {".nbt", ".schem", ".litematic", ".schematic"};
        // If name already has a known extension
        for (String ext : extensions) {
            if (name.toLowerCase().endsWith(ext)) {
                File f = STRUCTURES_DIR.resolve(name).toFile();
                return f.exists() ? f : null;
            }
        }
        for (String ext : extensions) {
            File f = STRUCTURES_DIR.resolve(name + ext).toFile();
            if (f.exists()) return f;
        }
        return null;
    }

    /**
     * Places the structure at (ox, oy, oz).
     * @return true on success
     */
    public static boolean place(ServerLevel level, String name, int ox, int oy, int oz,
                                Mirror mirror, Rotation rotation) {
        File file = resolve(name);
        if (file == null) {
            LOGGER.warn("[Script] Structure '{}' not found in {}", name, STRUCTURES_DIR.toAbsolutePath());
            return false;
        }
        String fname = file.getName().toLowerCase();
        try {
            if (fname.endsWith(".nbt")) {
                return placeNbt(level, file, ox, oy, oz, mirror, rotation);
            } else if (fname.endsWith(".schem")) {
                return placeSchem(level, file, ox, oy, oz, mirror, rotation);
            } else if (fname.endsWith(".litematic")) {
                return placeLitematic(level, file, ox, oy, oz, mirror, rotation);
            } else if (fname.endsWith(".schematic")) {
                return placeLegacySchematic(level, file, ox, oy, oz);
            }
        } catch (Exception e) {
            LOGGER.error("[Script] Failed to place structure '{}': {}", name, e.getMessage(), e);
        }
        return false;
    }

    // ─── .nbt (Minecraft native) ──────────────────────────────────────────────

    private static boolean placeNbt(ServerLevel level, File file, int ox, int oy, int oz,
                                    Mirror mirror, Rotation rotation) throws IOException {
        try (FileInputStream fis = new FileInputStream(file)) {
            CompoundTag nbt = NbtIo.readCompressed(fis);
            net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate template =
                    new net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate();
            //? if >=1.20.1 {
            template.load(level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BLOCK), nbt);
            //?} else {
            /*template.load(nbt);
            *///?}
            var settings = new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings()
                    .setMirror(mirror)
                    .setRotation(rotation)
                    .setIgnoreEntities(false);
            BlockPos origin = new BlockPos(ox, oy, oz);
            template.placeInWorld(level, origin, origin, settings, level.random, 2);
            LOGGER.info("[Script] Placed .nbt '{}' at ({},{},{})", file.getName(), ox, oy, oz);
            return true;
        }
    }

    // ─── .schem (Sponge Schematic) ────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private static boolean placeSchem(ServerLevel level, File file, int ox, int oy, int oz,
                                      Mirror mirror, Rotation rotation) throws IOException {
        try (FileInputStream fis = new FileInputStream(file)) {
            CompoundTag root = NbtIo.readCompressed(fis);

            int width  = root.getShort("Width")  & 0xFFFF;
            int height = root.getShort("Height") & 0xFFFF;
            int length = root.getShort("Length") & 0xFFFF;

            // Palette: blockstate string → palette index
            CompoundTag paletteTag = root.getCompound("Palette");
            String[] palette = new String[root.getInt("PaletteMax")];
            for (String key : paletteTag.getAllKeys()) {
                int idx = paletteTag.getInt(key);
                if (idx < palette.length) palette[idx] = key;
            }

            // BlockData: varints
            byte[] rawData = root.getByteArray("BlockData");
            int[] blockData = decodeVarints(rawData, width * height * length);

            // Offset (optional)
            int[] offset = root.contains("Offset") ? root.getIntArray("Offset") : new int[]{0, 0, 0};

            for (int y = 0; y < height; y++) {
                for (int z = 0; z < length; z++) {
                    for (int x = 0; x < width; x++) {
                        int idx = y * width * length + z * width + x;
                        if (idx >= blockData.length) continue;
                        int pi = blockData[idx];
                        if (pi >= palette.length || palette[pi] == null) continue;
                        BlockState state = parseBlockState(palette[pi]);
                        if (state == null) continue;
                        BlockPos pos = transformPos(x - offset[0], y - offset[1], z - offset[2],
                                width, length, mirror, rotation).offset(ox, oy, oz);
                        state = state.mirror(mirror).rotate(rotation);
                        level.setBlock(pos, state, 2);
                    }
                }
            }
            LOGGER.info("[Script] Placed .schem '{}' ({}x{}x{}) at ({},{},{})",
                    file.getName(), width, height, length, ox, oy, oz);
            return true;
        }
    }

    // ─── .litematic ───────────────────────────────────────────────────────────

    private static boolean placeLitematic(ServerLevel level, File file, int ox, int oy, int oz,
                                          Mirror mirror, Rotation rotation) throws IOException {
        try (FileInputStream fis = new FileInputStream(file)) {
            CompoundTag root = NbtIo.readCompressed(fis);
            CompoundTag regions = root.getCompound("Regions");
            if (regions.isEmpty()) {
                LOGGER.warn("[Script] Litematic '{}' has no regions", file.getName());
                return false;
            }
            // Place first region (or all regions with their relative offsets)
            for (String regionName : regions.getAllKeys()) {
                CompoundTag region = regions.getCompound(regionName);
                placeLitematicRegion(level, region, ox, oy, oz, mirror, rotation);
            }
            LOGGER.info("[Script] Placed .litematic '{}' at ({},{},{})", file.getName(), ox, oy, oz);
            return true;
        }
    }

    private static void placeLitematicRegion(ServerLevel level, CompoundTag region,
                                             int ox, int oy, int oz,
                                             Mirror mirror, Rotation rotation) {
        CompoundTag sizeTag = region.getCompound("Size");
        int sx = sizeTag.getInt("x");
        int sy = sizeTag.getInt("y");
        int sz = sizeTag.getInt("z");

        // Size can be negative in litematic (direction of fill)
        int width  = Math.abs(sx);
        int height = Math.abs(sy);
        int length = Math.abs(sz);

        CompoundTag posTag = region.getCompound("Position");
        int rx = posTag.getInt("x");
        int ry = posTag.getInt("y");
        int rz = posTag.getInt("z");

        ListTag paletteList = region.getList("BlockStatePalette", Tag.TAG_COMPOUND);
        int paletteSize = paletteList.size();
        if (paletteSize == 0) return;

        BlockState[] palette = new BlockState[paletteSize];
        for (int i = 0; i < paletteSize; i++) {
            CompoundTag entry = paletteList.getCompound(i);
            palette[i] = parseBlockState(entry);
        }

        long[] blockStates = region.getLongArray("BlockStates");
        int bitsPerEntry = Math.max(2, 32 - Integer.numberOfLeadingZeros(paletteSize - 1));
        int valuesPerLong = 64 / bitsPerEntry;
        long mask = (1L << bitsPerEntry) - 1L;
        int total = width * height * length;

        for (int i = 0; i < total; i++) {
            int longIndex = i / valuesPerLong;
            int bitIndex  = (i % valuesPerLong) * bitsPerEntry;
            if (longIndex >= blockStates.length) break;
            int pi = (int)((blockStates[longIndex] >> bitIndex) & mask);
            if (pi >= palette.length || palette[pi] == null) continue;
            BlockState state = palette[pi];
            if (state.isAir()) continue;

            // litematic index = y*w*l + z*w + x
            int lx = i % width;
            int lz = (i / width) % length;
            int ly = i / (width * length);

            BlockPos pos = transformPos(lx + rx, ly + ry, lz + rz,
                    width, length, mirror, rotation).offset(ox, oy, oz);
            state = state.mirror(mirror).rotate(rotation);
            level.setBlock(pos, state, 2);
        }
    }

    // ─── .schematic (Legacy WorldEdit, 1.12 numeric IDs, best-effort) ─────────

    private static boolean placeLegacySchematic(ServerLevel level, File file,
                                                int ox, int oy, int oz) throws IOException {
        try (FileInputStream fis = new FileInputStream(file)) {
            CompoundTag root = NbtIo.readCompressed(fis);
            int width  = root.getShort("Width")  & 0xFFFF;
            int height = root.getShort("Height") & 0xFFFF;
            int length = root.getShort("Length") & 0xFFFF;
            byte[] blocks = root.getByteArray("Blocks");
            byte[] data   = root.getByteArray("Data");

            LOGGER.warn("[Script] Legacy .schematic uses 1.12 numeric block IDs — some blocks may not map correctly");

            for (int y = 0; y < height; y++) {
                for (int z = 0; z < length; z++) {
                    for (int x = 0; x < width; x++) {
                        int idx = y * width * length + z * width + x;
                        if (idx >= blocks.length) continue;
                        int blockId = blocks[idx] & 0xFF;
                        if (blockId == 0) continue; // air

                        BlockState state = legacyIdToState(blockId, data.length > idx ? data[idx] & 0xF : 0);
                        if (state == null || state.isAir()) continue;
                        level.setBlock(new BlockPos(ox + x, oy + y, oz + z), state, 2);
                    }
                }
            }
            LOGGER.info("[Script] Placed .schematic '{}' ({}x{}x{}) at ({},{},{})",
                    file.getName(), width, height, length, ox, oy, oz);
            return true;
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    /** Decodes a varint-packed byte array into an int array. */
    private static int[] decodeVarints(byte[] data, int count) {
        int[] result = new int[count];
        int i = 0;
        int pos = 0;
        while (i < count && pos < data.length) {
            int value = 0;
            int shift = 0;
            byte b;
            do {
                if (pos >= data.length) break;
                b = data[pos++];
                value |= (b & 0x7F) << shift;
                shift += 7;
            } while ((b & 0x80) != 0);
            result[i++] = value;
        }
        return result;
    }

    /**
     * Parses a blockstate string like "minecraft:stone_slab[type=top,waterlogged=false]"
     * into a BlockState.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static BlockState parseBlockState(String raw) {
        if (raw == null || raw.isEmpty()) return null;
        int bracket = raw.indexOf('[');
        String blockId = bracket >= 0 ? raw.substring(0, bracket) : raw;
        ResourceLocation rl = blockId.contains(":") ? new ResourceLocation(blockId)
                                                     : new ResourceLocation("minecraft", blockId);
        Block block = ForgeRegistries.BLOCKS.getValue(rl);
        if (block == null) return null;
        BlockState state = block.defaultBlockState();
        if (bracket >= 0 && raw.endsWith("]")) {
            String props = raw.substring(bracket + 1, raw.length() - 1);
            for (String part : props.split(",")) {
                String[] kv = part.split("=", 2);
                if (kv.length != 2) continue;
                String key = kv[0].trim();
                String val = kv[1].trim();
                for (Property prop : state.getProperties()) {
                    if (prop.getName().equals(key)) {
                        Optional<?> optVal = prop.getValue(val);
                        if (optVal.isPresent()) {
                            state = state.setValue((Property) prop, (Comparable) optVal.get());
                        }
                        break;
                    }
                }
            }
        }
        return state;
    }

    /** Parses a Litematica palette entry CompoundTag. */
    private static BlockState parseBlockState(CompoundTag tag) {
        String name = tag.getString("Name");
        if (name.isEmpty()) return Blocks.AIR.defaultBlockState();
        ResourceLocation rl = new ResourceLocation(name);
        Block block = ForgeRegistries.BLOCKS.getValue(rl);
        if (block == null) return null;
        BlockState state = block.defaultBlockState();
        if (tag.contains("Properties")) {
            CompoundTag props = tag.getCompound("Properties");
            state = applyProperties(state, props);
        }
        return state;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static BlockState applyProperties(BlockState state, CompoundTag props) {
        for (String key : props.getAllKeys()) {
            String val = props.getString(key);
            for (Property prop : state.getProperties()) {
                if (prop.getName().equals(key)) {
                    Optional<?> optVal = prop.getValue(val);
                    if (optVal.isPresent()) {
                        state = state.setValue((Property) prop, (Comparable) optVal.get());
                    }
                    break;
                }
            }
        }
        return state;
    }

    /** Applies mirror/rotation transform to local coordinates. */
    private static BlockPos transformPos(int x, int y, int z, int width, int length,
                                         Mirror mirror, Rotation rotation) {
        // Apply mirror first
        switch (mirror) {
            case LEFT_RIGHT -> x = width - 1 - x;
            case FRONT_BACK -> z = length - 1 - z;
            default -> {}
        }
        // Apply rotation (around Y axis, origin at 0,0)
        return switch (rotation) {
            case CLOCKWISE_90         -> new BlockPos(length - 1 - z, y, x);
            case CLOCKWISE_180        -> new BlockPos(width - 1 - x, y, length - 1 - z);
            case COUNTERCLOCKWISE_90  -> new BlockPos(z, y, width - 1 - x);
            default                   -> new BlockPos(x, y, z);
        };
    }

    /** Very limited legacy block ID mapping for the most common blocks. */
    private static BlockState legacyIdToState(int id, int meta) {
        // Only the most universal blocks — full mapping would require a huge lookup table
        String name = switch (id) {
            case 1  -> "stone";
            case 2  -> "grass_block";
            case 3  -> "dirt";
            case 4  -> "cobblestone";
            case 5  -> meta == 1 ? "spruce_planks" : meta == 2 ? "birch_planks"
                                 : meta == 3 ? "jungle_planks" : meta == 4 ? "acacia_planks"
                                 : meta == 5 ? "dark_oak_planks" : "oak_planks";
            case 7  -> "bedrock";
            case 12 -> "sand";
            case 13 -> "gravel";
            case 14 -> "gold_ore";
            case 15 -> "iron_ore";
            case 16 -> "coal_ore";
            case 17 -> meta == 1 ? "spruce_log" : meta == 2 ? "birch_log"
                                 : meta == 3 ? "jungle_log" : "oak_log";
            case 20 -> "glass";
            case 21 -> "lapis_ore";
            case 22 -> "lapis_block";
            case 24 -> "sandstone";
            case 41 -> "gold_block";
            case 42 -> "iron_block";
            case 43 -> "smooth_stone_slab";
            case 45 -> "bricks";
            case 46 -> "tnt";
            case 47 -> "bookshelf";
            case 48 -> "mossy_cobblestone";
            case 49 -> "obsidian";
            case 53 -> "oak_stairs";
            case 54 -> "chest";
            case 56 -> "diamond_ore";
            case 57 -> "diamond_block";
            case 58 -> "crafting_table";
            case 61, 62 -> "furnace";
            case 67 -> "cobblestone_stairs";
            case 73, 74 -> "redstone_ore";
            case 79 -> "ice";
            case 80 -> "snow_block";
            case 82 -> "clay";
            case 84 -> "jukebox";
            case 86 -> "carved_pumpkin";
            case 87 -> "netherrack";
            case 88 -> "soul_sand";
            case 89 -> "glowstone";
            case 98 -> "stone_bricks";
            case 99 -> "brown_mushroom_block";
            case 100 -> "red_mushroom_block";
            case 101 -> "iron_bars";
            case 102 -> "glass_pane";
            case 103 -> "melon";
            case 108 -> "brick_stairs";
            case 109 -> "stone_brick_stairs";
            case 112 -> "nether_bricks";
            case 114 -> "nether_brick_stairs";
            case 121 -> "end_stone";
            case 155 -> "quartz_block";
            case 156 -> "quartz_stairs";
            case 159 -> "white_terracotta";
            case 162 -> "acacia_log";
            case 163 -> "acacia_stairs";
            case 164 -> "dark_oak_stairs";
            case 168 -> "prismarine";
            case 169 -> "sea_lantern";
            case 170 -> "hay_block";
            case 172 -> "terracotta";
            case 173 -> "coal_block";
            case 174 -> "packed_ice";
            case 179 -> "red_sandstone";
            case 181 -> "red_sandstone_slab";
            case 182 -> "red_sandstone_stairs";
            case 201 -> "purpur_block";
            case 203 -> "purpur_stairs";
            case 206 -> "end_stone_bricks";
            case 214 -> "magma_block";
            case 215 -> "nether_wart_block";
            case 216 -> "bone_block";
            default -> null;
        };
        if (name == null) return null;
        Block block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("minecraft", name));
        return block != null ? block.defaultBlockState() : null;
    }
}
