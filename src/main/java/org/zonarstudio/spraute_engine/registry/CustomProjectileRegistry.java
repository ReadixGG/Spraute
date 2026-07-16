package org.zonarstudio.spraute_engine.registry;

import com.mojang.logging.LogUtils;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.RegisterEvent;
import org.slf4j.Logger;
import org.zonarstudio.spraute_engine.Spraute_engine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Mod.EventBusSubscriber(modid = Spraute_engine.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class CustomProjectileRegistry {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final Map<String, ProjectileDef> PROJECTILES = new HashMap<>();
    private static boolean parsed = false;

    public static class ProjectileDef {
        public String id;
        public String texture = "";
        public float width = 0.5f;
        public float height = 0.5f;
        /** 0–255, 255 = opaque. */
        public int alpha = 255;
        public boolean seeThrough = false;
        public boolean glow = false;
        public boolean billboard = true;
        public float gravity = 0.0f;
        public float drag = 1.0f;
        public int lifetime = 200;
        public float damage = 0f;
        public int pierce = 0;
        public float knockback = 0f;
        public float rotation = 0f;
        public boolean collideBlocks = true;
        public boolean collideEntities = true;
    }

    private CustomProjectileRegistry() {}

    public static void ensureParsed() {
        parseScripts();
    }

    /** Re-read {@code create projectile} blocks after /spraute reload. */
    public static void invalidateAndReparse() {
        parsed = false;
        PROJECTILES.clear();
        parseScripts();
    }

    public static ProjectileDef get(String id) {
        ensureParsed();
        if (id == null) return null;
        String key = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
        return PROJECTILES.get(key);
    }

    public static void parseScripts() {
        if (parsed) return;
        parsed = true;

        Path scriptsDir = FMLPaths.GAMEDIR.get().resolve("spraute_engine").resolve("scripts");
        if (!Files.exists(scriptsDir)) return;

        Pattern blockPattern = Pattern.compile("create\\s+projectile\\s+([a-zA-Z0-9_]+)\\s*\\{");
        Pattern texturePattern = Pattern.compile("texture\\s*=\\s*\"([^\"]+)\"");
        Pattern widthPattern = Pattern.compile("width\\s*=\\s*([0-9.]+)");
        Pattern heightPattern = Pattern.compile("height\\s*=\\s*([0-9.]+)");
        Pattern alphaPattern = Pattern.compile("alpha\\s*=\\s*(\\d+)");
        Pattern gravityPattern = Pattern.compile("gravity\\s*=\\s*([0-9.]+)");
        Pattern dragPattern = Pattern.compile("drag\\s*=\\s*([0-9.]+)");
        Pattern lifetimePattern = Pattern.compile("lifetime\\s*=\\s*(\\d+)");
        Pattern damagePattern = Pattern.compile("damage\\s*=\\s*([0-9.]+)");
        Pattern piercePattern = Pattern.compile("pierce\\s*=\\s*(\\d+)");
        Pattern knockbackPattern = Pattern.compile("knockback\\s*=\\s*([0-9.]+)");
        Pattern rotationPattern = Pattern.compile("rotation\\s*=\\s*(-?[0-9.]+)");

        try {
            Files.walk(scriptsDir).filter(p -> p.toString().endsWith(".spr")).forEach(file -> {
                try {
                    String content = Files.readString(file);
                    Matcher m = blockPattern.matcher(content);
                    while (m.find()) {
                        ProjectileDef def = new ProjectileDef();
                        def.id = m.group(1);
                        String body = extractBody(content, m.end());

                        Matcher texM = texturePattern.matcher(body);
                        if (texM.find()) def.texture = texM.group(1);

                        Matcher wM = widthPattern.matcher(body);
                        if (wM.find()) def.width = Float.parseFloat(wM.group(1));

                        Matcher hM = heightPattern.matcher(body);
                        if (hM.find()) def.height = Float.parseFloat(hM.group(1));

                        Matcher aM = alphaPattern.matcher(body);
                        if (aM.find()) def.alpha = Math.min(255, Math.max(0, Integer.parseInt(aM.group(1))));

                        def.seeThrough = parseBool(body, "see_through", false);
                        def.glow = parseBool(body, "glow", false);
                        def.billboard = parseBool(body, "billboard", true);
                        def.collideBlocks = parseBool(body, "collide_blocks", true);
                        def.collideEntities = parseBool(body, "collide_entities", true);

                        Matcher gM = gravityPattern.matcher(body);
                        if (gM.find()) def.gravity = Float.parseFloat(gM.group(1));

                        Matcher dM = dragPattern.matcher(body);
                        if (dM.find()) def.drag = Float.parseFloat(dM.group(1));

                        Matcher lM = lifetimePattern.matcher(body);
                        if (lM.find()) def.lifetime = Integer.parseInt(lM.group(1));

                        Matcher dmgM = damagePattern.matcher(body);
                        if (dmgM.find()) def.damage = Float.parseFloat(dmgM.group(1));

                        Matcher pM = piercePattern.matcher(body);
                        if (pM.find()) def.pierce = Integer.parseInt(pM.group(1));

                        Matcher kbM = knockbackPattern.matcher(body);
                        if (kbM.find()) def.knockback = Float.parseFloat(kbM.group(1));

                        Matcher rM = rotationPattern.matcher(body);
                        if (rM.find()) def.rotation = Float.parseFloat(rM.group(1));

                        PROJECTILES.put(def.id, def);
                        LOGGER.info("[Spraute Engine] Parsed custom projectile: {}", def.id);
                    }
                } catch (IOException e) {
                    LOGGER.error("Failed to read script for projectile parsing: {}", file, e);
                }
            });
        } catch (IOException e) {
            LOGGER.error("Failed to walk scripts for projectile parsing", e);
        }
    }

    @SubscribeEvent
    public static void onRegister(RegisterEvent event) {
        parseScripts();
    }

    private static String extractBody(String content, int start) {
        int depth = 1;
        int i = start;
        while (i < content.length() && depth > 0) {
            char c = content.charAt(i++);
            if (c == '{') depth++;
            else if (c == '}') depth--;
        }
        return content.substring(start, Math.max(start, i - 1));
    }

    private static boolean parseBool(String body, String field, boolean defaultValue) {
        Pattern p = Pattern.compile(Pattern.quote(field) + "\\s*=\\s*(?:\"(true|false)\"|(true|false))");
        Matcher m = p.matcher(body);
        if (!m.find()) return defaultValue;
        String v = m.group(1) != null ? m.group(1) : m.group(2);
        return Boolean.parseBoolean(v);
    }
}
