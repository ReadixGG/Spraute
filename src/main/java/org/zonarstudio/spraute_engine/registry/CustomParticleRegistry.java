package org.zonarstudio.spraute_engine.registry;

import com.mojang.logging.LogUtils;
//? if >=1.20.1 {
import net.minecraft.core.registries.Registries;
//?} else {
/*import net.minecraft.core.Registry;
*///?}
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegisterEvent;
import org.slf4j.Logger;
import org.zonarstudio.spraute_engine.Spraute_engine;
import org.zonarstudio.spraute_engine.particle.ParticleEffectDef;
import org.zonarstudio.spraute_engine.scene.SceneDocument;
import org.zonarstudio.spraute_engine.scene.SceneNodeType;
import org.zonarstudio.spraute_engine.scene.SceneRepository;

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
        public ParticleEffectDef effect;
    }

    /** Upsert a particle def (scripts, scene RES_PARTICLE, runtime create). */
    public static void put(String id, String texture) {
        if (id == null || id.isBlank()) return;
        String key = id.trim();
        int colon = key.indexOf(':');
        if (colon >= 0) key = key.substring(colon + 1);
        CustomParticleDef def = new CustomParticleDef();
        def.id = key;
        def.texture = texture == null ? "" : texture.trim();
        def.effect = ParticleEffectDef.fromLegacy(null, def.texture);
        PARTICLES.put(key, def);
    }

    /** Hot-reloadable engine-owned definition; no Forge registration is required to use it. */
    public static void put(String id, ParticleEffectDef effect) {
        if (id == null || id.isBlank()) return;
        String key = id.trim();
        int colon = key.indexOf(':');
        if (colon >= 0) key = key.substring(colon + 1);
        CustomParticleDef def = new CustomParticleDef();
        def.id = key;
        def.effect = effect == null ? new ParticleEffectDef() : effect.copy();
        def.effect.normalize();
        def.texture = def.effect.renderer.texture;
        PARTICLES.put(key, def);
    }

    public static void parseScripts() {
        if (parsed) return;
        parsed = true;

        parseScriptFiles();
        parseSceneDefs();
        SyncedRegistrySnapshot.write();
    }

    /** Re-read particle defs after workspace exists / scripts refresh. Does not re-register Forge types. */
    public static void invalidateAndReparse() {
        parsed = false;
        parseScripts();
    }

    private static void parseScriptFiles() {
        Pattern particlePattern = Pattern.compile("create\\s+particle\\s+([a-zA-Z0-9_]+)\\s*\\{([^}]*)\\}");
        Pattern texturePattern = Pattern.compile("texture\\s*=\\s*\"([^\"]+)\"");

        try {
            for (Path file : org.zonarstudio.spraute_engine.resource.WorkspaceContent.allSprFiles()) {
                String content = Files.readString(file);

                Matcher m = particlePattern.matcher(content);
                while (m.find()) {
                    String id = m.group(1);
                    String propsStr = m.group(2);

                    String texture = "";
                    Matcher texM = texturePattern.matcher(propsStr);
                    if (texM.find()) texture = texM.group(1);

                    put(id, texture);
                    LOGGER.info("Parsed custom particle: {}", id);
                }
            }
        } catch (IOException e) {
            LOGGER.error("Failed to read script files for custom particles", e);
        }
    }

    /** Load {@link SceneNodeType#RES_PARTICLE} from .scene.json at boot (Forge register). */
    private static void parseSceneDefs() {
        Path gameDir = org.zonarstudio.spraute_engine.resource.SprautePaths.gameDir();
        for (Path file : SceneRepository.listPlayableScenes(gameDir)) {
            try {
                SceneDocument doc = SceneRepository.load(file);
                if (doc == null || doc.root == null) continue;
                doc.walk((node, ignored) -> {
                    if (!node.active || node.type != SceneNodeType.RES_PARTICLE) return;
                    if (node.particleDef == null) return;
                    String id = node.id;
                    if (node.particleDef.id != null && !node.particleDef.id.isBlank()) {
                        id = node.particleDef.id;
                    }
                    if (id == null || id.isBlank()) return;
                    put(id, node.particleDef.migrate());
                    LOGGER.info("Parsed scene particle '{}': {}", file.getFileName(), id);
                });
            } catch (Exception e) {
                LOGGER.warn("Failed to load scene particles '{}': {}", file.getFileName(), e.getMessage());
            }
        }
    }

    @SubscribeEvent
    public static void onRegister(RegisterEvent event) {
        parseScripts();
        if (event.getRegistryKey().equals(
                //? if >=1.20.1 {
                Registries.PARTICLE_TYPE
                //?} else {
                /*Registry.PARTICLE_TYPE_REGISTRY*/
                //?}
        )) {
            for (CustomParticleDef def : PARTICLES.values()) {
                SimpleParticleType type = new SimpleParticleType(true); // true = alwaysShow
                event.register(
                        //? if >=1.20.1 {
                        Registries.PARTICLE_TYPE
                        //?} else {
                        /*Registry.PARTICLE_TYPE_REGISTRY*/
                        //?}
                , new ResourceLocation(Spraute_engine.MODID, def.id), () -> type);
            }
        }
    }
}
