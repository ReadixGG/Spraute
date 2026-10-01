package org.zonarstudio.spraute_engine.scene.resource;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import org.zonarstudio.spraute_engine.Spraute_engine;
import org.zonarstudio.spraute_engine.registry.CraftJsonBuilder;
import org.zonarstudio.spraute_engine.registry.CustomBlockRegistry;
import org.zonarstudio.spraute_engine.registry.CustomDropRegistry;
import org.zonarstudio.spraute_engine.registry.CustomParticleRegistry;
import org.zonarstudio.spraute_engine.registry.CustomProjectileRegistry;
import org.zonarstudio.spraute_engine.registry.CustomWorldRegistry;
import org.zonarstudio.spraute_engine.scene.SceneDocument;
import org.zonarstudio.spraute_engine.scene.SceneNode;
import org.zonarstudio.spraute_engine.scene.SceneNodeType;
import org.zonarstudio.spraute_engine.scene.SceneRepository;
import org.zonarstudio.spraute_engine.scene.custom.CustomNodeItemBridge;
import org.zonarstudio.spraute_engine.scene.custom.CustomNodeRegistry;
import org.zonarstudio.spraute_engine.scene.custom.CustomNodeTypeDef;
import org.zonarstudio.spraute_engine.util.SprauteResourcePath;

import java.nio.file.Path;
import java.util.ArrayList;

/**
 * Scans all {@code .scene.json} files at mod boot and registers active RES_* nodes
 * into {@link CustomBlockRegistry} (same maps as {@code create block/item/craft} from .spr).
 */
public final class SceneResourceLoader {
    private static final Logger LOGGER = LogUtils.getLogger();

    private SceneResourceLoader() {}

    public static void loadAll(Path gameDir) {
        if (gameDir == null) gameDir = org.zonarstudio.spraute_engine.resource.SprautePaths.gameDir();
        int count = 0;
        for (Path file : SceneRepository.listPlayableScenes(gameDir)) {
            try {
                SceneDocument doc = SceneRepository.load(file);
                count += loadDocument(doc, file.getFileName().toString());
            } catch (Exception e) {
                LOGGER.warn("[Spraute Resources] Failed to load scene '{}': {}",
                        file.getFileName(), e.getMessage());
            }
        }
        if (count > 0) {
            LOGGER.info("[Spraute Resources] Registered {} resource node(s) from scenes", count);
        }
    }

    private static int loadDocument(SceneDocument doc, String sceneFile) {
        if (doc == null || doc.root == null) return 0;
        int[] count = {0};
        CustomNodeRegistry.ensureForDocument(doc);
        doc.walk((node, ignored) -> {
            if (!node.active) return;
            try {
                if (node.type.isResourceDef()) {
                    if (registerNode(node, sceneFile)) count[0]++;
                } else if (node.type == SceneNodeType.CUSTOM) {
                    if (registerCustomItemNode(doc, node, sceneFile)) count[0]++;
                }
            } catch (Exception e) {
                LOGGER.warn("[Spraute Resources] {} / {}: {}", sceneFile, node.id, e.getMessage());
            }
        });
        return count[0];
    }

    private static boolean registerNode(SceneNode node, String sceneFile) {
        return switch (node.type) {
            case RES_BLOCK -> registerBlock(node, sceneFile);
            case RES_ITEM -> registerItem(node, sceneFile);
            case RES_CRAFT -> registerCraft(node, sceneFile);
            case RES_TAB -> registerTab(node, sceneFile);
            case RES_DROP -> registerDrop(node, sceneFile);
            case RES_WORLD -> registerWorld(node, sceneFile);
            case RES_PARTICLE -> registerParticle(node, sceneFile);
            case RES_PROJECTILE -> registerProjectile(node, sceneFile);
            default -> false;
        };
    }

    private static boolean registerParticle(SceneNode node, String sceneFile) {
        SceneParticleDef src = node.particleDef;
        if (src == null) return false;
        String id = resourceId(node, src.id);
        if (id == null) return false;
        SprauteResourcePath.Result idCheck =
                SprauteResourcePath.validateSimpleId(id, SprauteResourcePath.Kind.RESOURCE);
        if (!idCheck.ok()) {
            LOGGER.warn("[Spraute Resources] Invalid particle id '{}' in {}", id, sceneFile);
            return false;
        }
        String bare = idCheck.location().getPath();
        if (CustomParticleRegistry.PARTICLES.containsKey(bare)) {
            LOGGER.warn("[Spraute Resources] Particle '{}' from {} overrides existing def", bare, sceneFile);
        }
        CustomParticleRegistry.put(bare, src.texture);
        return true;
    }

    private static boolean registerProjectile(SceneNode node, String sceneFile) {
        SceneProjectileDef src = node.projectileDef;
        if (src == null) return false;
        String id = resourceId(node, src.id);
        if (id == null) return false;
        SprauteResourcePath.Result idCheck =
                SprauteResourcePath.validateSimpleId(id, SprauteResourcePath.Kind.RESOURCE);
        if (!idCheck.ok()) {
            LOGGER.warn("[Spraute Resources] Invalid projectile id '{}' in {}", id, sceneFile);
            return false;
        }
        String bare = idCheck.location().getPath();
        src.id = bare;
        CustomProjectileRegistry.ProjectileDef def = src.toRegistryDef();
        if (CustomProjectileRegistry.PROJECTILES.containsKey(bare)) {
            LOGGER.warn("[Spraute Resources] Projectile '{}' from {} overrides existing def", bare, sceneFile);
        }
        CustomProjectileRegistry.register(bare, def);
        return true;
    }

    /**
     * Registers only {@link SceneNodeType#RES_WORLD} from scenes into
     * {@link CustomWorldRegistry} — called early so dimension effects see them.
     */
    public static void loadWorlds(Path gameDir) {
        if (gameDir == null) gameDir = org.zonarstudio.spraute_engine.resource.SprautePaths.gameDir();
        for (Path file : SceneRepository.listPlayableScenes(gameDir)) {
            try {
                SceneDocument doc = SceneRepository.load(file);
                if (doc == null || doc.root == null) continue;
                String sceneFile = file.getFileName().toString();
                doc.walk((node, ignored) -> {
                    if (!node.active || node.type != SceneNodeType.RES_WORLD) return;
                    try {
                        registerWorld(node, sceneFile);
                    } catch (Exception e) {
                        LOGGER.warn("[Spraute Resources] {} / {}: {}", sceneFile, node.id, e.getMessage());
                    }
                });
            } catch (Exception e) {
                LOGGER.warn("[Spraute Resources] Failed to load scene worlds '{}': {}",
                        file.getFileName(), e.getMessage());
            }
        }
    }

    private static boolean registerWorld(SceneNode node, String sceneFile) {
        SceneWorldDef src = node.worldDef;
        if (src == null) return false;
        String id = resourceId(node, src.id);
        if (id == null) return false;
        SprauteResourcePath.Result idCheck =
                SprauteResourcePath.validateSimpleId(id, SprauteResourcePath.Kind.WORLD);
        if (!idCheck.ok()) {
            LOGGER.warn("[Spraute Resources] Invalid world id '{}' in {}", id, sceneFile);
            return false;
        }
        CustomWorldRegistry.CustomWorldDef def = new CustomWorldRegistry.CustomWorldDef();
        def.id = idCheck.location().getPath();
        def.skyColor = CustomWorldRegistry.parseColorPublic(src.sky, def.skyColor);
        if (src.fog != null && !src.fog.isBlank()) {
            def.fogColor = CustomWorldRegistry.parseColorPublic(src.fog, def.fogColor);
        } else {
            def.fogColor = CustomWorldRegistry.darkenPublic(def.skyColor, 0.65f);
        }
        def.ambientLight = Math.max(0f, Math.min(1f, src.ambient));
        def.fixedTime = src.fixedTime < 0 ? null : Math.floorMod(src.fixedTime, 24000L);
        def.hasSkylight = src.skylight;
        def.clouds = src.clouds;
        if (CustomWorldRegistry.WORLDS.containsKey(def.id)) {
            LOGGER.warn("[Spraute Resources] World '{}' from {} overrides existing def", def.id, sceneFile);
        }
        CustomWorldRegistry.putWorld(def);
        return true;
    }

    private static boolean registerBlock(SceneNode node, String sceneFile) {
        SceneBlockDef src = node.blockDef;
        if (src == null) return false;
        src.ensureLookDefaults();
        String id = resourceId(node, src.id);
        if (id == null) return false;
        if (CustomBlockRegistry.BLOCKS.containsKey(id)) {
            LOGGER.warn("[Spraute Resources] Block '{}' from {} overrides existing def", id, sceneFile);
        }
        String kind = src.lookKind == null ? "texture" : src.lookKind.trim().toLowerCase();
        boolean modelMode = "model".equals(kind) || "geo".equals(kind) || "3d".equals(kind);
        boolean oreMode = "ore".equals(kind) || src.isOre;
        String texMode = src.texMode == null ? "single" : src.texMode.trim().toLowerCase();

        CustomBlockRegistry.CustomBlockDef def = new CustomBlockRegistry.CustomBlockDef();
        def.id = id;
        if (modelMode) {
            def.model = emptyToNull(src.model);
            def.texture = emptyToNull(src.texture);
            def.directional = src.directional;
            def.sizeW = Math.max(1, src.sizeW);
            def.sizeD = Math.max(1, src.sizeD);
            def.sizeH = Math.max(1, src.sizeH);
            if (src.hitbox != null) def.hitbox = src.hitbox.clone();
        } else {
            applyBlockTextures(def, src, texMode);
            def.sizeW = 1;
            def.sizeD = 1;
            def.sizeH = 1;
        }
        def.hasCollision = src.hasCollision;
        def.lightEmission = Math.max(0, Math.min(15, src.lightEmission));
        def.hardness = src.hardness;
        def.requiredTool = CustomBlockRegistry.normalizeBlockTool(src.requiredTool);
        def.requiredMiningLevel = Math.max(0, src.requiredMiningLevel);
        def.requiresCorrectTool = src.requiresCorrectTool;
        def.displayName = emptyToNull(src.displayName);
        src.ensureDropDefaults();
        def.dropReplace = src.dropReplace;
        def.dropRules.clear();
        if (src.drops != null && !src.drops.isEmpty()) {
            for (var rule : src.drops) {
                if (rule == null || rule.itemId == null || rule.itemId.isBlank()) continue;
                CustomBlockRegistry.BlockDropRule br = new CustomBlockRegistry.BlockDropRule();
                br.itemId = rule.itemId.trim();
                br.min = Math.max(0, rule.min);
                br.max = Math.max(br.min, rule.max);
                br.chance = Math.max(0, Math.min(100, rule.chance));
                def.dropRules.add(br);
            }
            def.dropItem = null;
            def.dropCount = 1;
        } else {
            def.dropItem = emptyToNull(src.dropItem);
            def.dropCount = Math.max(1, src.dropCount);
            if (def.dropItem != null) def.dropReplace = true;
        }
        def.dropXp = Math.max(0, src.dropXp);
        def.tab = emptyToNull(src.tab);
        def.isOre = oreMode;
        if (oreMode) {
            def.oreVeinSize = src.oreVeinSize;
            def.oreMinY = src.oreMinY;
            def.oreMaxY = src.oreMaxY;
            def.oreChances = src.oreChances;
            def.oreDimension = src.oreDimension == null || src.oreDimension.isBlank()
                    ? "minecraft:overworld" : src.oreDimension;
        }
        CustomBlockRegistry.BLOCKS.put(id, def);
        return true;
    }

    private static void applyBlockTextures(CustomBlockRegistry.CustomBlockDef def, SceneBlockDef src, String texMode) {
        if ("top_sides".equals(texMode) || "top-sides".equals(texMode) || "sides".equals(texMode)) {
            def.textureUp = emptyToNull(src.textureUp);
            def.textureSides = emptyToNull(src.textureSides);
            def.texture = emptyToNull(src.texture);
        } else if ("all".equals(texMode) || "faces".equals(texMode) || "cube".equals(texMode)) {
            def.textureUp = emptyToNull(src.textureUp);
            def.textureDown = emptyToNull(src.textureDown);
            def.textureNorth = emptyToNull(src.textureNorth);
            def.textureSouth = emptyToNull(src.textureSouth);
            def.textureWest = emptyToNull(src.textureWest);
            def.textureEast = emptyToNull(src.textureEast);
        } else {
            def.texture = emptyToNull(src.texture);
        }
    }

    private static boolean registerItem(SceneNode node, String sceneFile) {
        SceneItemDef src = node.itemDef;
        if (src == null) return false;
        src.ensureLookDefaults();
        String id = resourceId(node, src.id);
        if (id == null) return false;
        if (CustomBlockRegistry.ITEMS.containsKey(id)) {
            LOGGER.warn("[Spraute Resources] Item '{}' from {} overrides existing def", id, sceneFile);
        }
        boolean geoMode = "geo".equalsIgnoreCase(src.lookKind)
                || "3d".equalsIgnoreCase(src.lookKind)
                || "bedrock".equalsIgnoreCase(src.lookKind);

        CustomBlockRegistry.CustomItemDef def = new CustomBlockRegistry.CustomItemDef();
        def.id = id;
        def.texture = emptyToNull(src.texture);
        def.displayName = emptyToNull(src.displayName);
        def.maxStackSize = Math.max(1, Math.min(64, src.maxStackSize));
        def.tab = emptyToNull(src.tab);
        def.toolType = emptyToNull(src.toolType);
        if (def.toolType != null) {
            switch (def.toolType.toLowerCase()) {
                case "sword" -> def.sword = true;
                case "pickaxe" -> def.pickaxe = true;
                case "axe" -> def.axe = true;
                case "shovel" -> def.shovel = true;
                case "hoe" -> def.hoe = true;
                default -> {}
            }
        }
        def.armorType = emptyToNull(src.armorType);
        def.armorTexture = emptyToNull(src.armorTexture);
        if (src.armorModel != null) {
            def.hideArmor = !src.armorModel;
        }
        def.armorDefense = src.armorDefense;
        def.armorToughness = src.armorToughness;
        def.armorKnockback = src.armorKnockback;
        def.damage = src.damage;
        def.attackSpeed = src.attackSpeed;
        def.durability = src.durability;
        def.miningSpeed = src.miningSpeed;
        def.miningLevel = src.miningLevel;
        if (geoMode) {
            def.geo = emptyToNull(src.geo);
            def.handFirst = org.zonarstudio.spraute_engine.item.GeoItemTransform.parse(src.handFirst);
            def.handThird = org.zonarstudio.spraute_engine.item.GeoItemTransform.parse(src.handThird);
            def.handGround = org.zonarstudio.spraute_engine.item.GeoItemTransform.parse(src.handGround);
            def.gui = org.zonarstudio.spraute_engine.item.GeoItemTransform.parse(src.gui);
        } else {
            def.model = emptyToNull(src.model);
        }
        CustomBlockRegistry.ITEMS.put(id, def);
        return true;
    }

    private static boolean registerCustomItemNode(SceneDocument doc, SceneNode node, String sceneFile) {
        CustomNodeTypeDef def = CustomNodeRegistry.get(doc, node.customTypeId);
        if (def == null || !def.createsItem()) return false;
        CustomBlockRegistry.CustomItemDef item = CustomNodeItemBridge.fromNode(node);
        if (item == null) return false;
        if (CustomBlockRegistry.ITEMS.containsKey(item.id)) {
            LOGGER.warn("[Spraute Resources] Item '{}' from {} overrides existing def", item.id, sceneFile);
        }
        CustomBlockRegistry.ITEMS.put(item.id, item);
        return true;
    }

    private static boolean registerCraft(SceneNode node, String sceneFile) {
        SceneCraftDef src = node.craftDef;
        if (src == null) return false;
        src.ensureSlots();
        String id = resourceId(node, src.id);
        if (id == null) return false;
        String type = CraftJsonBuilder.normalizeType(src.type);
        String result = src.result == null || src.result.isBlank() ? "minecraft:stone" : src.result;
        String json;
        if ("shaped".equals(type)) {
            json = CraftJsonBuilder.shapedFromSlots(id, src.slots, result, src.count);
        } else if ("shapeless".equals(type)) {
            json = CraftJsonBuilder.shapeless(id, src.ingredients, result, src.count);
        } else {
            json = CraftJsonBuilder.cooking(type, id, src.ingredient, result, src.xp, src.time);
        }
        if (json == null || json.isBlank()) {
            LOGGER.warn("[Spraute Resources] Craft '{}' from {} has no valid recipe", id, sceneFile);
            return false;
        }
        if (CustomBlockRegistry.CUSTOM_RECIPES_JSON.containsKey(id)) {
            LOGGER.warn("[Spraute Resources] Craft '{}' from {} overrides existing recipe", id, sceneFile);
        }
        CustomBlockRegistry.CUSTOM_RECIPES_JSON.put(id, json);
        return true;
    }

    private static boolean registerTab(SceneNode node, String sceneFile) {
        SceneTabDef src = node.tabDef;
        if (src == null) return false;
        String id = resourceId(node, src.id);
        if (id == null) return false;
        //? if >=1.20.1 {
        CustomBlockRegistry.CustomTabDef def = new CustomBlockRegistry.CustomTabDef();
        def.id = id;
        def.icon = src.icon == null || src.icon.isBlank() ? "minecraft:stone" : src.icon;
        if (src.items != null) def.extraItems.addAll(src.items);
        CustomBlockRegistry.TAB_DEFS.put(id, def);
        //?}
        if (src.items != null && !src.items.isEmpty()) {
            CustomBlockRegistry.TAB_EXTRA_ITEMS.put(id, new ArrayList<>(src.items));
        }
        if (src.displayName != null && !src.displayName.isBlank()) {
            CustomBlockRegistry.TAB_DISPLAY_NAMES.put(id, src.displayName);
        }
        return true;
    }

    private static boolean registerDrop(SceneNode node, String sceneFile) {
        SceneDropDef src = node.dropDef;
        if (src == null) return false;
        src.ensureDrops();
        boolean mob = src.isMob();
        String targetId = mob
                ? (src.mob == null || src.mob.isBlank() ? null : src.mob.trim())
                : (src.block == null || src.block.isBlank() ? null : src.block.trim());
        if (targetId == null) return false;
        if (!targetId.contains(":")) targetId = Spraute_engine.MODID + ":" + targetId;
        String nbt = src.nbt == null || src.nbt.isBlank() ? null : src.nbt;
        boolean any = false;
        if (src.drops != null) {
            for (var rule : src.drops) {
                if (rule == null || rule.itemId == null || rule.itemId.isBlank()) continue;
                if (mob) {
                    CustomDropRegistry.addMobDrop(targetId, rule.itemId.trim(), rule.min, rule.max, rule.chance,
                            src.replace, nbt, src.xp);
                } else {
                    CustomDropRegistry.addBlockDrop(targetId, rule.itemId.trim(), rule.min, rule.max, rule.chance,
                            src.replace, nbt, src.xp);
                }
                any = true;
            }
        }
        return any;
    }

    private static String resourceId(SceneNode node, String defId) {
        String id = defId != null && !defId.isBlank() ? defId.trim() : node.id;
        if (id == null || id.isBlank()) return null;
        if (!id.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            LOGGER.warn("[Spraute Resources] Invalid resource id '{}'", id);
            return null;
        }
        return id;
    }

    private static String emptyToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
