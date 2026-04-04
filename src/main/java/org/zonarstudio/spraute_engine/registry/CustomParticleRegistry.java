package org.zonarstudio.spraute_engine.registry;

import com.mojang.logging.LogUtils;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.RegisterEvent;
import org.slf4j.Logger;
import org.zonarstudio.spraute_engine.Spraute_engine;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Mod.EventBusSubscriber(modid = Spraute_engine.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class CustomParticleRegistry {
    private static final Logger LOGGER = LogUtils.getLogger();
    
    public static final Map<String, CustomParticleDef> PARTICLES = new HashMap<>();
    private static boolean parsed = false;

    public static class CustomParticleDef {
        public String id;
        public String texture;
    }

    public static void parseScripts() {
        if (parsed) return;
        parsed = true;
        
        Path scriptsDir = FMLPaths.GAMEDIR.get().resolve("spraute_engine").resolve("scripts");
        if (!Files.exists(scriptsDir)) return;

        Pattern particlePattern = Pattern.compile("create\\s+particle\\s+([a-zA-Z0-9_]+)\\s*\\{([^}]*)\\}");
        Pattern texturePattern = Pattern.compile("texture\\s*=\\s*\"([^\"]+)\"");

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(scriptsDir, "*.spr")) {
            for (Path file : stream) {
                String content = Files.readString(file);
                
                Matcher m = particlePattern.matcher(content);
                while (m.find()) {
                    String id = m.group(1);
                    String propsStr = m.group(2);
                    
                    CustomParticleDef def = new CustomParticleDef();
                    def.id = id;
                    
                    Matcher texM = texturePattern.matcher(propsStr);
                    if (texM.find()) def.texture = texM.group(1);
                    
                    PARTICLES.put(id, def);
                    LOGGER.info("Parsed custom particle: {}", id);
                }
            }
        } catch (IOException e) {
            LOGGER.error("Failed to read script files for custom particles", e);
        }
    }

    @SubscribeEvent
    public static void onRegister(RegisterEvent event) {
        parseScripts();
        if (event.getRegistryKey().equals(Registry.PARTICLE_TYPE_REGISTRY)) {
            for (CustomParticleDef def : PARTICLES.values()) {
                SimpleParticleType type = new SimpleParticleType(true); // true = alwaysShow
                event.register(Registry.PARTICLE_TYPE_REGISTRY, new ResourceLocation(Spraute_engine.MODID, def.id), () -> type);
            }
        }
    }
}
