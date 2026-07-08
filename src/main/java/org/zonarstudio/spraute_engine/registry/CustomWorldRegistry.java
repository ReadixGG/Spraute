package org.zonarstudio.spraute_engine.registry;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.zonarstudio.spraute_engine.Spraute_engine;
import org.zonarstudio.spraute_engine.util.SprauteResourcePath;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parsed from {@code create world id { ... }} in .spr scripts — void dimensions with custom sky/fog. */
public final class CustomWorldRegistry {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static boolean parsed = false;

    public static final Map<String, CustomWorldDef> WORLDS = new HashMap<>();
    /** Dimensions still present in a save but removed from scripts — served with fallback data so the save can load. */
    private static final Set<String> ORPHAN_WORLD_IDS = new HashSet<>();
    public static final String ORPHAN_EFFECTS_KEY = "orphan_fallback";

    private CustomWorldRegistry() {}

    public static class CustomWorldDef {
        public String id;
        public int skyColor = 0x87CEEB;
        public int fogColor = 0x5A8FB0;
        public float ambientLight = 0.4f;
        /** {@code null} = day/night cycle; otherwise fixed tick 0–23999. */
        public Long fixedTime = 6000L;
        public boolean hasSkylight = true;
    }

    public static void ensureParsed() {
        if (parsed) return;
        parsed = true;
        parseScripts();
    }

    public static void reloadFromScripts() {
        parsed = false;
        ensureParsed();
    }

    public static void clearOrphanWorldIds() {
        ORPHAN_WORLD_IDS.clear();
    }

    public static void addOrphanWorldIds(Set<String> ids) {
        if (ids == null || ids.isEmpty()) return;
        ORPHAN_WORLD_IDS.addAll(ids);
    }

    public static Set<String> getOrphanWorldIds() {
        return Set.copyOf(ORPHAN_WORLD_IDS);
    }

    public static boolean isOrphanWorld(String worldId) {
        String canon = SprauteResourcePath.canonicalSimpleId(worldId);
        return ORPHAN_WORLD_IDS.contains(canon) && !WORLDS.containsKey(canon);
    }

    public static CustomWorldDef resolveWorldDef(String worldId) {
        String canon = SprauteResourcePath.canonicalSimpleId(worldId);
        CustomWorldDef def = WORLDS.get(canon);
        if (def != null) return def;
        if (ORPHAN_WORLD_IDS.contains(canon)) return defaultOrphanDef(canon);
        return null;
    }

    public static CustomWorldDef defaultOrphanDef(String worldId) {
        CustomWorldDef def = new CustomWorldDef();
        def.id = SprauteResourcePath.canonicalSimpleId(worldId);
        return def;
    }

    public static void parseScripts() {
        WORLDS.clear();
        Path scriptsDir = FMLPaths.GAMEDIR.get().resolve("spraute_engine").resolve("scripts");
        if (!Files.exists(scriptsDir)) return;

        Pattern worldPattern = Pattern.compile("create\\s+world\\s+([a-zA-Z0-9_]+)\\s*\\{([^}]*)\\}", Pattern.DOTALL);
        Pattern skyPattern = Pattern.compile("sky\\s*=\\s*\"([^\"]+)\"");
        Pattern fogPattern = Pattern.compile("fog\\s*=\\s*\"([^\"]+)\"");
        Pattern skyRgbPattern = Pattern.compile("sky_rgb\\s*=\\s*\\[([^\\]]*)\\]");
        Pattern fogRgbPattern = Pattern.compile("fog_rgb\\s*=\\s*\\[([^\\]]*)\\]");
        Pattern ambientPattern = Pattern.compile("ambient\\s*=\\s*([0-9.]+)");
        Pattern fixedTimePattern = Pattern.compile("fixed_time\\s*=\\s*(-?\\d+)");
        Pattern skylightPattern = Pattern.compile("skylight\\s*=\\s*(true|false)", Pattern.CASE_INSENSITIVE);

        try {
            Files.walk(scriptsDir).filter(p -> p.toString().endsWith(".spr")).forEach(file -> {
                try {
                    String content = Files.readString(file);
                    Matcher m = worldPattern.matcher(content);
                    while (m.find()) {
                        String rawId = m.group(1);
                        SprauteResourcePath.Result idCheck =
                                SprauteResourcePath.validateSimpleId(rawId, SprauteResourcePath.Kind.WORLD);
                        if (!idCheck.ok()) {
                            LOGGER.error("[Spraute Engine] Skipping create world '{}' in {}: invalid id (use lowercase a-z, 0-9, _ only). {}",
                                    rawId, file.getFileName(), idCheck.invalidChars());
                            continue;
                        }

                        CustomWorldDef def = new CustomWorldDef();
                        def.id = idCheck.location().getPath();
                        String body = m.group(2);

                        Matcher skyM = skyPattern.matcher(body);
                        if (skyM.find()) {
                            def.skyColor = parseColor(skyM.group(1), def.skyColor);
                        }
                        Matcher skyRgbM = skyRgbPattern.matcher(body);
                        if (skyRgbM.find()) {
                            def.skyColor = parseRgbArray(skyRgbM.group(1), def.skyColor);
                        }

                        Matcher fogM = fogPattern.matcher(body);
                        if (fogM.find()) {
                            def.fogColor = parseColor(fogM.group(1), def.fogColor);
                        } else {
                            def.fogColor = darken(def.skyColor, 0.65f);
                        }
                        Matcher fogRgbM = fogRgbPattern.matcher(body);
                        if (fogRgbM.find()) {
                            def.fogColor = parseRgbArray(fogRgbM.group(1), def.fogColor);
                        }

                        Matcher ambM = ambientPattern.matcher(body);
                        if (ambM.find()) {
                            def.ambientLight = Float.parseFloat(ambM.group(1));
                        }

                        Matcher timeM = fixedTimePattern.matcher(body);
                        if (timeM.find()) {
                            long t = Long.parseLong(timeM.group(1));
                            def.fixedTime = t < 0 ? null : Math.floorMod(t, 24000L);
                        }

                        Matcher lightM = skylightPattern.matcher(body);
                        if (lightM.find()) {
                            def.hasSkylight = Boolean.parseBoolean(lightM.group(1));
                        }

                        WORLDS.put(def.id, def);
                        LOGGER.info("[Spraute Engine] Found custom world declaration: {}", def.id);
                    }
                } catch (IOException e) {
                    LOGGER.error("Failed to read script for world parsing: {}", file, e);
                }
            });
        } catch (IOException e) {
            LOGGER.error("Failed to walk scripts directory for worlds", e);
        }
    }

    public static String dimensionId(String worldId) {
        String canon = SprauteResourcePath.canonicalSimpleId(worldId);
        return Spraute_engine.MODID + ":" + canon;
    }

    public static boolean hasWorld(String worldId) {
        String canon = SprauteResourcePath.canonicalSimpleId(worldId);
        return WORLDS.containsKey(canon);
    }

    public static String dimensionTypeId(String worldId) {
        return Spraute_engine.MODID + ":" + worldId + "_type";
    }

    public static String effectsId(String worldId) {
        return Spraute_engine.MODID + ":" + worldId;
    }

    public static String buildDimensionJson(CustomWorldDef def) {
        // flat + the_void + пустые слои — пустое измерение без кастомных noise_settings (minecraft:empty не существует в 1.20.1)
        return "{\n" +
                "  \"type\": \"" + dimensionTypeId(def.id) + "\",\n" +
                "  \"generator\": {\n" +
                "    \"type\": \"minecraft:flat\",\n" +
                "    \"settings\": {\n" +
                "      \"biome\": \"minecraft:the_void\",\n" +
                "      \"layers\": [],\n" +
                "      \"structure_overrides\": []\n" +
                "    }\n" +
                "  }\n" +
                "}";
    }

    public static String buildDimensionTypeJson(CustomWorldDef def) {
        return buildDimensionTypeJson(def, false);
    }

    public static String buildDimensionTypeJson(CustomWorldDef def, boolean orphanFallbackEffects) {
        String fixedTime = def.fixedTime == null ? "" :
                "  \"fixed_time\": " + def.fixedTime + ",\n";
        String effectsKey = orphanFallbackEffects ? ORPHAN_EFFECTS_KEY : def.id;
        //? if >=1.20.1 {
        return "{\n" +
                "  \"ultrawarm\": false,\n" +
                "  \"natural\": false,\n" +
                "  \"piglin_safe\": false,\n" +
                "  \"respawn_anchor_works\": false,\n" +
                "  \"bed_works\": true,\n" +
                "  \"has_raids\": false,\n" +
                "  \"has_skylight\": " + def.hasSkylight + ",\n" +
                "  \"has_ceiling\": false,\n" +
                "  \"coordinate_scale\": 1.0,\n" +
                "  \"ambient_light\": " + def.ambientLight + ",\n" +
                fixedTime +
                "  \"logical_height\": 384,\n" +
                "  \"effects\": \"" + effectsId(effectsKey) + "\",\n" +
                "  \"infiniburn\": \"#minecraft:infiniburn_overworld\",\n" +
                "  \"min_y\": -64,\n" +
                "  \"height\": 384,\n" +
                "  \"monster_spawn_light_level\": 15,\n" +
                "  \"monster_spawn_block_light_limit\": 0\n" +
                "}";
        //?} else {
        /*return "{\n" +
                "  \"ultrawarm\": false,\n" +
                "  \"natural\": false,\n" +
                "  \"piglin_safe\": false,\n" +
                "  \"respawn_anchor_works\": false,\n" +
                "  \"bed_works\": true,\n" +
                "  \"has_raids\": false,\n" +
                "  \"has_skylight\": " + def.hasSkylight + ",\n" +
                "  \"has_ceiling\": false,\n" +
                "  \"coordinate_scale\": 1.0,\n" +
                "  \"ambient_light\": " + def.ambientLight + ",\n" +
                fixedTime +
                "  \"logical_height\": 384,\n" +
                "  \"effects\": \"" + effectsId(effectsKey) + "\",\n" +
                "  \"infiniburn\": \"#minecraft:infiniburn_overworld\",\n" +
                "  \"min_y\": -64,\n" +
                "  \"height\": 384\n" +
                "}";
        *///?}
    }

    private static int parseColor(String raw, int fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        String s = raw.trim();
        if (s.startsWith("#")) s = s.substring(1);
        if (s.length() == 6) {
            try {
                return Integer.parseInt(s, 16);
            } catch (NumberFormatException ignored) {}
        }
        return fallback;
    }

    private static int parseRgbArray(String raw, int fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        String[] parts = raw.split("[,;\\s]+");
        if (parts.length < 3) return fallback;
        try {
            int r = clamp255(Integer.parseInt(parts[0].trim()));
            int g = clamp255(Integer.parseInt(parts[1].trim()));
            int b = clamp255(Integer.parseInt(parts[2].trim()));
            return (r << 16) | (g << 8) | b;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static int clamp255(int v) {
        return Math.max(0, Math.min(255, v));
    }

    private static int darken(int rgb, float factor) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        return (((int) (r * factor)) << 16) | (((int) (g * factor)) << 8) | ((int) (b * factor));
    }
}
