package org.zonarstudio.spraute_engine.registry;

import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
//? if >=1.20.1 {
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.CreativeModeTab;
//?} else {
/*import net.minecraft.core.Registry;
import net.minecraft.world.level.material.Material;
*///?}
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegisterEvent;
import org.slf4j.Logger;
import org.zonarstudio.spraute_engine.Spraute_engine;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.zonarstudio.spraute_engine.scene.custom.CustomNodeItemBridge;
import org.zonarstudio.spraute_engine.scene.custom.CustomNodeSprParser;

@Mod.EventBusSubscriber(modid = Spraute_engine.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class CustomBlockRegistry {
    private static final Logger LOGGER = LogUtils.getLogger();
    
    public static final Map<String, CustomBlockDef> BLOCKS = new HashMap<>();
    public static final List<Block> REGISTERED_BLOCKS = new ArrayList<>();
    private static boolean parsed = false;

    public static BlockEntityType<CustomGeoBlockEntity> CUSTOM_GEO_BLOCK_ENTITY;
    public static Block MULTIBLOCK_SLAVE_BLOCK;
    public static BlockEntityType<MultiblockSlaveBlockEntity> MULTIBLOCK_SLAVE_BLOCK_ENTITY;

    public static class CustomBlockDef {
        public String id;
        public String model;
        public String texture;
        public String textureUp;
        public String textureDown;
        public String textureSides;
        public String textureNorth;
        public String textureSouth;
        public String textureWest;
        public String textureEast;
        public boolean hasCollision = true;
        public int lightEmission = 0;
        public float hardness = 1.5f;
        /** Preferred dig tool: pickaxe / axe / shovel / hoe, or null/blank. */
        public String requiredTool;
        /** Min tool tier level (0 wood … 4 netherite). */
        public int requiredMiningLevel;
        /** Wrong tool → slow dig + no drops. */
        public boolean requiresCorrectTool;
        public String displayName;
        public String dropItem;
        public int dropCount = 1;
        /** When true, the block itself does not drop (only custom drop(s)). */
        public boolean dropReplace = false;
        public final List<BlockDropRule> dropRules = new ArrayList<>();
        /** XP orbs on break (0 = none). Silk touch suppresses. */
        public int dropXp;
        public String tab;
        public boolean directional = true;
        public float[] hitbox;
        /** Footprint in block cells: width (right), depth (back), height (up). */
        public int sizeW = 1;
        public int sizeD = 1;
        public int sizeH = 1;
        
        public boolean isOre = false;
        public int oreVeinSize = 8;
        public int oreMinY = -64;
        public int oreMaxY = 64;
        public int oreChances = 10;
        public String oreDimension = "minecraft:overworld";
    }

    public static class BlockDropRule {
        public String itemId;
        public int min = 1;
        public int max = 1;
        public int chance = 100;
    }

    public static class CustomItemDef {
        public String id;
        public String model;
        public String texture;
        public String displayName;
        public int maxStackSize = 64;
        public String tab;
        /** {@code sword} / {@code pickaxe} / {@code axe} / {@code shovel} / {@code hoe} */
        public String toolType;
        public boolean sword;
        public boolean pickaxe;
        public boolean axe;
        public boolean shovel;
        public boolean hoe;
        /** {@code helmet} / {@code chestplate} / {@code leggings} / {@code boots} */
        public String armorType;
        /** Base path for worn armor layers, e.g. {@code textures/models/armor/crystal}. */
        public String armorTexture;
        /**
         * When {@code true}, {@link net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer} is skipped.
         * {@code null} = hide if {@code armorTexture} is empty (clothing overlay).
         */
        public Boolean hideArmor;
        /** Full-skin overlay (64×64) while this armor is worn. */
        public String skinOverlay;
        /** Second-layer mask: auto / none / hat / torso / jacket / sleeves / pants. */
        public String skinHideLayer;
        public Integer armorDefense;
        public Float armorToughness;
        public Float armorKnockback;
        /** Bonus attack damage (tooltip value, like iron sword +5). */
        public Float damage;
        public Float attackSpeed;
        public Integer durability;
        public Float miningSpeed;
        public Integer miningLevel;
        /** Bedrock .geo.json path (e.g. geo/sword.geo.json) — enables 3D in-hand rendering. */
        public String geo;
        public org.zonarstudio.spraute_engine.item.GeoItemTransform handFirst;
        public org.zonarstudio.spraute_engine.item.GeoItemTransform handThird;
        public org.zonarstudio.spraute_engine.item.GeoItemTransform handGround;
        public org.zonarstudio.spraute_engine.item.GeoItemTransform gui;
    }

    public static final Map<String, String> CUSTOM_RECIPES_JSON = new HashMap<>();
    /** Dynamic item tags for craft ingredient alternatives (e.g. any wood plank). */
    public static final Map<String, String> CUSTOM_CRAFT_TAGS_JSON = new HashMap<>();

    public static final Map<String, CustomItemDef> ITEMS = new HashMap<>();
    //? if >=1.20.1 {
    public static class CustomTabDef {
        public String id;
        public String icon;
        public final List<String> extraItems = new ArrayList<>();
    }
    public static final Map<String, CustomTabDef> TAB_DEFS = new HashMap<>();
    //?}
    /** Extra items listed in {@code create tab} (both versions). */
    public static final Map<String, List<String>> TAB_EXTRA_ITEMS = new HashMap<>();
    /** Display names from {@code name = "..."} in {@code create tab} (both versions). */
    public static final Map<String, String> TAB_DISPLAY_NAMES = new HashMap<>();
    public static final Map<String, net.minecraft.world.item.CreativeModeTab> CUSTOM_TABS = new HashMap<>();

    /** Ensures {@code create item/block/craft} from .spr scripts are parsed (safe to call multiple times). */
    public static void ensureParsed() {
        parseScripts();
    }

    private static void parseScripts() {
        if (parsed) return;
        parsed = true;

        SyncedRegistrySnapshot.clearCraftSnippets();
        org.zonarstudio.spraute_engine.registry.CustomWorldRegistry.ensureParsed();

        parseScriptDirectory(org.zonarstudio.spraute_engine.resource.SprautePaths.scriptsDir());
        // Scene RES_* and custom creates=item nodes override / add after .spr.
        org.zonarstudio.spraute_engine.scene.resource.SceneResourceLoader.loadAll(
                org.zonarstudio.spraute_engine.resource.SprautePaths.gameDir());
        SyncedRegistrySnapshot.write();
    }

    private static void parseScriptDirectory(Path scriptsDir) {
        Set<String> itemCreatingTypes = collectItemCreatingTypes();
        Pattern tabPattern = Pattern.compile("create\\s+tab\\s+([a-zA-Z0-9_]+)\\s*\\{");
        Pattern craftPattern = Pattern.compile("create\\s+craft\\s+([a-zA-Z0-9_]+)\\s*\\{");
        Pattern craftTypePattern = Pattern.compile("type\\s*=\\s*\"([^\"]+)\"");
        Pattern craftResultPattern = Pattern.compile("result\\s*=\\s*\"([^\"]+)\"");
        Pattern craftCountPattern = Pattern.compile("count\\s*=\\s*(\\d+)");
        Pattern craftKeyPattern = Pattern.compile("key_([^\\s=]+)\\s*=\\s*(\"[^\"]+\"|\\[[^\\]]+\\])");
        Pattern craftPattern1Pattern = Pattern.compile("pattern_1\\s*=\\s*\"([^\"]+)\"");
        Pattern craftPattern2Pattern = Pattern.compile("pattern_2\\s*=\\s*\"([^\"]+)\"");
        Pattern craftPattern3Pattern = Pattern.compile("pattern_3\\s*=\\s*\"([^\"]+)\"");
        Pattern craftSlotPattern = Pattern.compile("slot_(\\d+)\\s*=\\s*\"([^\"]*)\"");
        Pattern craftIngPattern = Pattern.compile("ingredient\\s*=\\s*\"([^\"]+)\"");
        Pattern craftXpPattern = Pattern.compile("xp\\s*=\\s*([0-9.]+)");
        Pattern craftTimePattern = Pattern.compile("time\\s*=\\s*(\\d+)");
        Pattern blockPattern = Pattern.compile("create\\s+block\\s+([a-zA-Z0-9_]+)\\s*\\{");
        Pattern itemPattern = Pattern.compile("create\\s+item\\s+([a-zA-Z0-9_]+)\\s*\\{");
        Pattern createDropPattern = Pattern.compile("create\\s+drop\\s+([a-zA-Z0-9_]+)\\s*\\{");
        Pattern modelPattern = Pattern.compile("model\\s*=\\s*\"([^\"]+)\"");
        Pattern geoPattern = Pattern.compile("geo\\s*=\\s*\"([^\"]+)\"");
        Pattern texturePattern = Pattern.compile("texture\\s*=\\s*\"([^\"]+)\"");
        Pattern iconPattern = Pattern.compile("icon\\s*=\\s*\"([^\"]+)\"");
        Pattern tabIdPattern = Pattern.compile("tab\\s*=\\s*\"([^\"]+)\"");
        Pattern textureUpPattern = Pattern.compile("texture_up\\s*=\\s*\"([^\"]+)\"");
        Pattern textureDownPattern = Pattern.compile("texture_down\\s*=\\s*\"([^\"]+)\"");
        Pattern textureSidesPattern = Pattern.compile("texture_sides\\s*=\\s*\"([^\"]+)\"");
        Pattern textureNorthPattern = Pattern.compile("texture_north\\s*=\\s*\"([^\"]+)\"");
        Pattern textureSouthPattern = Pattern.compile("texture_south\\s*=\\s*\"([^\"]+)\"");
        Pattern textureWestPattern = Pattern.compile("texture_west\\s*=\\s*\"([^\"]+)\"");
        Pattern textureEastPattern = Pattern.compile("texture_east\\s*=\\s*\"([^\"]+)\"");
        Pattern hitboxPattern = Pattern.compile("hitbox\\s*=\\s*\\[([^\\]]*)\\]");
        Pattern sizePattern = Pattern.compile("size\\s*=\\s*\\[([^\\]]*)\\]");
        Pattern maxStackPattern = Pattern.compile("maxStackSize\\s*=\\s*(\\d+)");
        Pattern itemNamePattern = Pattern.compile("name\\s*=\\s*\"([^\"]+)\"");
        Pattern toolTypePattern = Pattern.compile("tool_type\\s*=\\s*\"([^\"]+)\"");
        Pattern armorTypePattern = Pattern.compile("armor_type\\s*=\\s*\"([^\"]+)\"");
        Pattern armorTexturePattern = Pattern.compile("armor_texture\\s*=\\s*\"([^\"]+)\"");
        Pattern skinOverlayPattern = Pattern.compile("(?:skin_overlay|overlay)\\s*=\\s*\"([^\"]+)\"");
        Pattern skinHideLayerPattern = Pattern.compile("hide_layer\\s*=\\s*\"([^\"]+)\"");
        Pattern armorDefensePattern = Pattern.compile("(?:armor_defense|defense)\\s*=\\s*(\\d+)");
        Pattern armorToughnessPattern = Pattern.compile("(?:armor_toughness|toughness)\\s*=\\s*([0-9.]+)");
        Pattern armorKnockbackPattern = Pattern.compile("(?:armor_knockback|knockback_resistance)\\s*=\\s*([0-9.]+)");
        Pattern damagePattern = Pattern.compile("damage\\s*=\\s*([0-9.]+)");
        Pattern attackSpeedPattern = Pattern.compile("attack_speed\\s*=\\s*(-?[0-9.]+)");
        Pattern durabilityPattern = Pattern.compile("durability\\s*=\\s*(\\d+)");
        Pattern miningSpeedPattern = Pattern.compile("mining_speed\\s*=\\s*([0-9.]+)");
        Pattern miningLevelPattern = Pattern.compile("(?:mining_level|harvest_level|required_level)\\s*=\\s*(\\d+)");
        Pattern requiredToolPattern = Pattern.compile("(?:required_tool|harvest_tool|tool)\\s*=\\s*\"([^\"]+)\"");
        Pattern lightPattern = Pattern.compile("light\\s*=\\s*(\\d+)");
        Pattern hardnessPattern = Pattern.compile("hardness\\s*=\\s*(-?[0-9.]+)");
        Pattern dropPattern = Pattern.compile("drop\\s*=\\s*\"([^\"]+)\"");
        Pattern dropCountPattern = Pattern.compile("drop_count\\s*=\\s*(\\d+)");
        Pattern dropXpPattern = Pattern.compile("(?:drop_xp|dropXp|experience|xp)\\s*=\\s*(\\d+)");
        Pattern blockDropRowPattern = Pattern.compile("\\[\\s*\"([^\"]+)\"\\s*,\\s*(\\d+)\\s*,\\s*(\\d+)\\s*,\\s*(\\d+)\\s*\\]");
        Pattern dropBlockPattern = Pattern.compile("block\\s*=\\s*\"([^\"]+)\"");
        Pattern dropMobPattern = Pattern.compile("(?:mob|entity)\\s*=\\s*\"([^\"]+)\"");
        Pattern dropItemPattern = Pattern.compile("item\\s*=\\s*\"([^\"]+)\"");
        Pattern dropMinPattern = Pattern.compile("min\\s*=\\s*(\\d+)");
        Pattern dropMaxPattern = Pattern.compile("max\\s*=\\s*(\\d+)");
        Pattern dropChancePattern = Pattern.compile("chance\\s*=\\s*(\\d+)");
        Pattern dropNbtPattern = Pattern.compile("nbt\\s*=\\s*\"([^\"]+)\"");
        Pattern oreVeinPattern = Pattern.compile("ore_vein\\s*=\\s*(\\d+)");
        Pattern oreMinPattern = Pattern.compile("ore_min\\s*=\\s*(-?\\d+)");
        Pattern oreMaxPattern = Pattern.compile("ore_max\\s*=\\s*(-?\\d+)");
        Pattern oreChancesPattern = Pattern.compile("ore_chances\\s*=\\s*(\\d+)");
        Pattern oreDimensionPattern = Pattern.compile("ore_dimension\\s*=\\s*\"([^\"]+)\"");

        org.zonarstudio.spraute_engine.resource.WorkspaceContent.allSprFiles().forEach(file -> {
                try {
                    String content = Files.readString(file);
                    
                    Matcher tabM = tabPattern.matcher(content);
                    while (tabM.find()) {
                        String id = tabM.group(1);
                        String body = extractCreateBody(content, tabM);
                        Matcher iconM = iconPattern.matcher(body);
                        String iconStr = iconM.find() ? iconM.group(1) : "minecraft:stone";
                        String tabItemsBody = extractBracketArrayValue(body, "items");
                        List<String> tabItemIds = tabItemsBody != null ? parseQuotedStrings(tabItemsBody) : List.of();
                        Matcher tabNameM = itemNamePattern.matcher(body);
                        if (tabNameM.find()) {
                            TAB_DISPLAY_NAMES.put(id, tabNameM.group(1));
                        }

                        //? if >=1.20.1 {
                        CustomTabDef tabDef = new CustomTabDef();
                        tabDef.id = id;
                        tabDef.icon = iconStr;
                        tabDef.extraItems.addAll(tabItemIds);
                        TAB_DEFS.put(id, tabDef);
                        //?} else {
                        /*net.minecraft.world.item.CreativeModeTab customTab = new net.minecraft.world.item.CreativeModeTab("spraute_" + id) {
                            @Override
                            public net.minecraft.network.chat.Component getDisplayName() {
                                return creativeTabTitle(id);
                            }

                            @Override
                            public net.minecraft.world.item.ItemStack makeIcon() {
                                return makeTabIcon(iconStr);
                            }

                            @Override
                            public void fillItemList(net.minecraft.core.NonNullList<net.minecraft.world.item.ItemStack> items) {
                                super.fillItemList(items);
                                List<String> extra = TAB_EXTRA_ITEMS.get(id);
                                if (extra != null) {
                                    for (String spec : extra) {
                                        ItemStack stack = resolveTabItemStack(spec);
                                        if (!stack.isEmpty()) {
                                            items.add(stack);
                                        }
                                    }
                                }
                            }
                        };
                        CUSTOM_TABS.put(id, customTab);
                        *///?}
                        if (!tabItemIds.isEmpty()) {
                            TAB_EXTRA_ITEMS.put(id, new ArrayList<>(tabItemIds));
                        }
                    }
                    
                    Matcher craftM = craftPattern.matcher(content);
                    while (craftM.find()) {
                        String id = craftM.group(1);
                        String body = extractCreateBody(content, craftM);
                        
                        Matcher typeM = craftTypePattern.matcher(body);
                        String type = normalizeCraftType(typeM.find() ? typeM.group(1) : "shaped");
                        
                        Matcher resM = craftResultPattern.matcher(body);
                        String result = resM.find() ? resM.group(1) : "minecraft:stone";
                        if (!result.contains(":")) result = Spraute_engine.MODID + ":" + result;
                        
                        Matcher countM = craftCountPattern.matcher(body);
                        int count = countM.find() ? Integer.parseInt(countM.group(1)) : 1;
                        
                        StringBuilder json = new StringBuilder();
                        boolean recipeValid = false;

                        if (type.equals("shaped")) {
                            Matcher slotMatcher = craftSlotPattern.matcher(body);
                            boolean hasSlots = false;
                            while (slotMatcher.find()) {
                                if (!slotMatcher.group(2).isBlank()) {
                                    hasSlots = true;
                                    break;
                                }
                            }
                            if (hasSlots) {
                                int jsonBefore = json.length();
                                appendShapedCraftFromSlots(json, body, id, result, count);
                                recipeValid = json.length() > jsonBefore;
                            } else {
                            Matcher p1 = craftPattern1Pattern.matcher(body);
                            Matcher p2 = craftPattern2Pattern.matcher(body);
                            Matcher p3 = craftPattern3Pattern.matcher(body);
                            boolean hasP1 = p1.find(), hasP2 = p2.find(), hasP3 = p3.find();
                            if (hasP1 || hasP2 || hasP3) {
                            json.append("{\"type\": \"minecraft:crafting_shaped\", \"pattern\": [");
                            if (hasP1) json.append("\"").append(p1.group(1)).append("\"");
                            if (hasP2) json.append(",\"").append(p2.group(1)).append("\"");
                            if (hasP3) json.append(",\"").append(p3.group(1)).append("\"");
                            json.append("], \"key\": {");
                            
                            Matcher keyM = craftKeyPattern.matcher(body);
                            boolean firstKey = true;
                            while (keyM.find()) {
                                if (!firstKey) json.append(",");
                                firstKey = false;
                                String k = keyM.group(1);
                                String spec = keyM.group(2);
                                json.append("\"").append(k).append("\": ").append(CraftJsonBuilder.ingredientToJson(spec, id, "key_" + k));
                            }
                            json.append("}, \"result\": {\"item\": \"").append(result).append("\", \"count\": ").append(count).append("}}");
                            recipeValid = true;
                            }
                            }
                            
                        } else if (type.equals("shapeless")) {
                            String ingredientsBody = extractBracketArrayValue(body, "ingredients");
                            List<String> tokens = ingredientsBody != null ? splitIngredientTokens(ingredientsBody) : List.of();
                            if (!tokens.isEmpty()) {
                            json.append("{\"type\": \"minecraft:crafting_shapeless\", \"ingredients\": [");
                                boolean firstItem = true;
                                for (int ti = 0; ti < tokens.size(); ti++) {
                                    if (!firstItem) json.append(",");
                                    firstItem = false;
                                    json.append(CraftJsonBuilder.ingredientToJson(tokens.get(ti), id, "ing_" + ti));
                                }
                            json.append("], \"result\": {\"item\": \"").append(result).append("\", \"count\": ").append(count).append("}}");
                            recipeValid = true;
                            }
                            
                        } else if (type.equals("smelting") || type.equals("blasting") || type.equals("smoking") || type.equals("campfire_cooking")) {
                            json.append("{\"type\": \"minecraft:").append(type).append("\", \"ingredient\": ");
                            Matcher ingM = craftIngPattern.matcher(body);
                            String ingSpec = ingM.find() ? "\"" + ingM.group(1) + "\"" : "\"minecraft:cobblestone\"";
                            json.append(CraftJsonBuilder.ingredientToJson(ingSpec, id, "ingredient"));
                            
                            Matcher xpM = craftXpPattern.matcher(body);
                            float xp = xpM.find() ? Float.parseFloat(xpM.group(1)) : 0.1f;
                            
                            Matcher timeM = craftTimePattern.matcher(body);
                            int time = timeM.find() ? Integer.parseInt(timeM.group(1)) : 200;
                            
                            json.append(", \"result\": \"").append(result).append("\", \"experience\": ").append(xp).append(", \"cookingtime\": ").append(time).append("}");
                            recipeValid = true;
                        }
                        
                        if (recipeValid && json.length() > 0) {
                            CUSTOM_RECIPES_JSON.put(id, json.toString());
                            SyncedRegistrySnapshot.putCraftSnippet(id,
                                    "create craft " + id + " {\n" + body.trim() + "\n}");
                        }
                    }

                    Matcher createDropM = createDropPattern.matcher(content);
                    while (createDropM.find()) {
                        String body = extractCreateBody(content, createDropM);
                        Matcher blockM = dropBlockPattern.matcher(body);
                        Matcher mobM = dropMobPattern.matcher(body);
                        Matcher itemM = dropItemPattern.matcher(body);
                        if (!itemM.find()) continue;
                        boolean isMob = mobM.find();
                        boolean isBlock = blockM.find();
                        if (!isMob && !isBlock) continue;
                        String targetId = isMob ? mobM.group(1) : blockM.group(1);
                        String itemId = itemM.group(1);
                        int min = 1, max = 1, chance = 100;
                        boolean replace = false;
                        String nbt = null;
                        Matcher minM = dropMinPattern.matcher(body);
                        if (minM.find()) min = Integer.parseInt(minM.group(1));
                        Matcher maxM = dropMaxPattern.matcher(body);
                        if (maxM.find()) max = Integer.parseInt(maxM.group(1));
                        Matcher chanceM = dropChancePattern.matcher(body);
                        if (chanceM.find()) chance = Integer.parseInt(chanceM.group(1));
                        replace = parseBoolField(body, "replace", false);
                        Matcher nbtM = dropNbtPattern.matcher(body);
                        if (nbtM.find()) nbt = nbtM.group(1);
                        int xp = 0;
                        Matcher xpM = dropXpPattern.matcher(body);
                        if (xpM.find()) xp = Integer.parseInt(xpM.group(1));
                        if (!targetId.contains(":")) targetId = Spraute_engine.MODID + ":" + targetId;
                        if (isMob) {
                            CustomDropRegistry.addMobDrop(targetId, itemId, min, max, chance, replace, nbt, xp);
                            LOGGER.info("[Spraute Engine] Found custom mob drop: {} -> {} (xp={})", targetId, itemId, xp);
                        } else {
                            CustomDropRegistry.addBlockDrop(targetId, itemId, min, max, chance, replace, nbt, xp);
                            LOGGER.info("[Spraute Engine] Found custom block drop: {} -> {} (xp={})", targetId, itemId, xp);
                        }
                    }
                    
                    Matcher m = blockPattern.matcher(content);
                    while (m.find()) {
                        CustomBlockDef def = new CustomBlockDef();
                        def.id = m.group(1);
                        String body = extractCreateBody(content, m);
                        
                        Matcher modelM = modelPattern.matcher(body);
                        if (modelM.find()) def.model = modelM.group(1);
                        
                        Matcher texM = texturePattern.matcher(body);
                        if (texM.find()) def.texture = texM.group(1);

                        Matcher tUpM = textureUpPattern.matcher(body);
                        if (tUpM.find()) def.textureUp = tUpM.group(1);
                        Matcher tDownM = textureDownPattern.matcher(body);
                        if (tDownM.find()) def.textureDown = tDownM.group(1);
                        Matcher tSidesM = textureSidesPattern.matcher(body);
                        if (tSidesM.find()) def.textureSides = tSidesM.group(1);
                        Matcher tNorthM = textureNorthPattern.matcher(body);
                        if (tNorthM.find()) def.textureNorth = tNorthM.group(1);
                        Matcher tSouthM = textureSouthPattern.matcher(body);
                        if (tSouthM.find()) def.textureSouth = tSouthM.group(1);
                        Matcher tWestM = textureWestPattern.matcher(body);
                        if (tWestM.find()) def.textureWest = tWestM.group(1);
                        Matcher tEastM = textureEastPattern.matcher(body);
                        if (tEastM.find()) def.textureEast = tEastM.group(1);

                        def.hasCollision = parseBoolField(body, "collision", true);
                        // solid = alias for collision (твёрдый блок / можно ли через него пройти)
                        if (hasBoolField(body, "solid")) {
                            def.hasCollision = parseBoolField(body, "solid", true);
                        }

                        Matcher lightM = lightPattern.matcher(body);
                        if (lightM.find()) def.lightEmission = Integer.parseInt(lightM.group(1));

                        Matcher hardM = hardnessPattern.matcher(body);
                        if (hardM.find()) def.hardness = Float.parseFloat(hardM.group(1));

                        Matcher reqToolM = requiredToolPattern.matcher(body);
                        if (reqToolM.find()) def.requiredTool = normalizeBlockTool(reqToolM.group(1));
                        Matcher reqLevelM = miningLevelPattern.matcher(body);
                        if (reqLevelM.find()) def.requiredMiningLevel = Integer.parseInt(reqLevelM.group(1));
                        if (hasBoolField(body, "requires_tool") || hasBoolField(body, "requiresCorrectTool")) {
                            def.requiresCorrectTool = parseBoolField(body, "requires_tool",
                                    parseBoolField(body, "requiresCorrectTool", true));
                        } else {
                            def.requiresCorrectTool = def.requiredTool != null && !def.requiredTool.isBlank();
                        }
                        
                        def.directional = parseBoolField(body, "directional", true);

                        Matcher nameM = itemNamePattern.matcher(body);
                        if (nameM.find()) def.displayName = nameM.group(1);

                        String hitboxBody = extractBracketArrayValue(body, "hitbox");
                        if (hitboxBody != null) {
                            def.hitbox = parseBlockHitbox(hitboxBody);
                        } else {
                            Matcher hitM = hitboxPattern.matcher(body);
                            if (hitM.find()) {
                                def.hitbox = parseBlockHitbox(hitM.group(1));
                            }
                        }

                        String sizeBody = extractBracketArrayValue(body, "size");
                        if (sizeBody != null) {
                            int[] size = parseBlockSize(sizeBody);
                            def.sizeW = size[0];
                            def.sizeD = size[1];
                            def.sizeH = size[2];
                        } else {
                            Matcher sizeM = sizePattern.matcher(body);
                            if (sizeM.find()) {
                                int[] size = parseBlockSize(sizeM.group(1));
                                def.sizeW = size[0];
                                def.sizeD = size[1];
                                def.sizeH = size[2];
                            }
                        }

                        Matcher dropM = dropPattern.matcher(body);
                        if (dropM.find()) {
                            def.dropItem = dropM.group(1);
                            def.dropReplace = true;
                        }

                        Matcher dropCountM = dropCountPattern.matcher(body);
                        if (dropCountM.find()) def.dropCount = Integer.parseInt(dropCountM.group(1));

                        Matcher dropXpM = dropXpPattern.matcher(body);
                        if (dropXpM.find()) def.dropXp = Integer.parseInt(dropXpM.group(1));

                        def.dropReplace = parseBoolField(body, "drop_replace", def.dropReplace);

                        String dropsBody = extractBracketArrayValue(body, "drops");
                        if (dropsBody != null) {
                            def.dropReplace = true;
                            Matcher rowM = blockDropRowPattern.matcher(dropsBody);
                            while (rowM.find()) {
                                BlockDropRule rule = new BlockDropRule();
                                rule.itemId = rowM.group(1);
                                rule.min = Integer.parseInt(rowM.group(2));
                                rule.max = Integer.parseInt(rowM.group(3));
                                rule.chance = Integer.parseInt(rowM.group(4));
                                def.dropRules.add(rule);
                            }
                        }
                        
                        Matcher tabIdM = tabIdPattern.matcher(body);
                        if (tabIdM.find()) def.tab = tabIdM.group(1);
                        
                        def.isOre = parseBoolField(body, "is_ore", false);

                        Matcher oreVeinM = oreVeinPattern.matcher(body);
                        if (oreVeinM.find()) def.oreVeinSize = Integer.parseInt(oreVeinM.group(1));

                        Matcher oreMinM = oreMinPattern.matcher(body);
                        if (oreMinM.find()) def.oreMinY = Integer.parseInt(oreMinM.group(1));

                        Matcher oreMaxM = oreMaxPattern.matcher(body);
                        if (oreMaxM.find()) def.oreMaxY = Integer.parseInt(oreMaxM.group(1));

                        Matcher oreChancesM = oreChancesPattern.matcher(body);
                        if (oreChancesM.find()) def.oreChances = Integer.parseInt(oreChancesM.group(1));

                        Matcher oreDimM = oreDimensionPattern.matcher(body);
                        if (oreDimM.find()) def.oreDimension = oreDimM.group(1);

                        BLOCKS.put(def.id, def);
                        LOGGER.info("[Spraute Engine] Found custom block declaration: {}", def.id);
                    }

                    Matcher im = itemPattern.matcher(content);
                    while (im.find()) {
                        CustomItemDef def = new CustomItemDef();
                        def.id = im.group(1);
                        String body = extractCreateBody(content, im);

                        Matcher modelM = modelPattern.matcher(body);
                        if (modelM.find()) def.model = modelM.group(1);

                        Matcher texM = texturePattern.matcher(body);
                        if (texM.find()) def.texture = texM.group(1);

                        Matcher stackM = maxStackPattern.matcher(body);
                        if (stackM.find()) def.maxStackSize = Integer.parseInt(stackM.group(1));

                        Matcher nameM = itemNamePattern.matcher(body);
                        if (nameM.find()) def.displayName = nameM.group(1);

                        Matcher tabIdM2 = tabIdPattern.matcher(body);
                        if (tabIdM2.find()) def.tab = tabIdM2.group(1);

                        Matcher toolTypeM = toolTypePattern.matcher(body);
                        if (toolTypeM.find()) def.toolType = toolTypeM.group(1);

                        def.sword = parseBoolField(body, "sword", false);
                        def.pickaxe = parseBoolField(body, "pickaxe", false);
                        def.axe = parseBoolField(body, "axe", false);
                        def.shovel = parseBoolField(body, "shovel", false);
                        def.hoe = parseBoolField(body, "hoe", false);

                        Matcher armorTypeM = armorTypePattern.matcher(body);
                        if (armorTypeM.find()) def.armorType = armorTypeM.group(1);
                        Matcher armorTexM = armorTexturePattern.matcher(body);
                        if (armorTexM.find()) def.armorTexture = armorTexM.group(1);
                        if (hasBoolField(body, "hide_armor")) {
                            def.hideArmor = parseBoolField(body, "hide_armor", true);
                        } else if (hasBoolField(body, "armor_model")) {
                            def.hideArmor = !parseBoolField(body, "armor_model", true);
                        }
                        Matcher skinOvM = skinOverlayPattern.matcher(body);
                        if (skinOvM.find()) def.skinOverlay = skinOvM.group(1);
                        Matcher hideLayerM = skinHideLayerPattern.matcher(body);
                        if (hideLayerM.find()) def.skinHideLayer = hideLayerM.group(1);
                        Matcher armorDefM = armorDefensePattern.matcher(body);
                        if (armorDefM.find()) def.armorDefense = Integer.parseInt(armorDefM.group(1));
                        Matcher armorToughM = armorToughnessPattern.matcher(body);
                        if (armorToughM.find()) def.armorToughness = Float.parseFloat(armorToughM.group(1));
                        Matcher armorKbM = armorKnockbackPattern.matcher(body);
                        if (armorKbM.find()) def.armorKnockback = Float.parseFloat(armorKbM.group(1));

                        Matcher damageM = damagePattern.matcher(body);
                        if (damageM.find()) def.damage = Float.parseFloat(damageM.group(1));

                        Matcher attackSpeedM = attackSpeedPattern.matcher(body);
                        if (attackSpeedM.find()) def.attackSpeed = Float.parseFloat(attackSpeedM.group(1));

                        Matcher durabilityM = durabilityPattern.matcher(body);
                        if (durabilityM.find()) def.durability = Integer.parseInt(durabilityM.group(1));

                        Matcher miningSpeedM = miningSpeedPattern.matcher(body);
                        if (miningSpeedM.find()) def.miningSpeed = Float.parseFloat(miningSpeedM.group(1));

                        Matcher miningLevelM = miningLevelPattern.matcher(body);
                        if (miningLevelM.find()) def.miningLevel = Integer.parseInt(miningLevelM.group(1));

                        Matcher geoM = geoPattern.matcher(body);
                        if (geoM.find()) def.geo = geoM.group(1);

                        def.handFirst = parseGeoItemTransform(body, "hand_first");
                        def.handThird = parseGeoItemTransform(body, "hand_third");
                        def.handGround = parseGeoItemTransform(body, "hand_ground");
                        def.gui = parseGeoItemTransform(body, "gui");

                        ITEMS.put(def.id, def);
                        LOGGER.info("[Spraute Engine] Found custom item declaration: {}", def.id);
                    }

                    parseCustomItemCreates(content, itemCreatingTypes);

                } catch (IOException e) {
                    LOGGER.error("Failed to read script for parsing: {}", file, e);
                }
            });
        org.zonarstudio.spraute_engine.item.GeoItemVisualRegistry.rebuild();
    }

    private static final Set<String> RESERVED_CREATE = Set.of(
            "item", "block", "tab", "craft", "drop", "npc", "ui", "particle",
            "world", "camera", "light", "audio", "postprocess", "npc_prefab");

    private static Set<String> collectItemCreatingTypes() {
        Set<String> out = new HashSet<>();
        org.zonarstudio.spraute_engine.resource.WorkspaceContent.allSprFiles().forEach(file -> {
            try {
                String content = Files.readString(file);
                for (var type : CustomNodeSprParser.parse(content).types) {
                    if (type == null || type.id == null || type.id.isBlank()) continue;
                    if (!type.createsItem()) continue;
                    if (RESERVED_CREATE.contains(type.id.toLowerCase())) continue;
                    out.add(type.id);
                }
            } catch (Exception ignored) {}
        });
        return out;
    }

    private static void parseCustomItemCreates(String content, Set<String> typeIds) {
        if (content == null || typeIds == null || typeIds.isEmpty()) return;
        for (String typeId : typeIds) {
            Pattern p = Pattern.compile("create\\s+" + Pattern.quote(typeId) + "\\s+([a-zA-Z0-9_]+)\\s*\\{");
            Matcher m = p.matcher(content);
            while (m.find()) {
                if (isInLineComment(content, m.start())) continue;
                CustomItemDef def = CustomNodeItemBridge.fromCreateBody(m.group(1), extractCreateBody(content, m));
                if (def == null) continue;
                ITEMS.put(def.id, def);
                LOGGER.info("[Spraute Engine] Found custom item from create {}: {}", typeId, def.id);
            }
        }
    }

    private static boolean isInLineComment(String content, int index) {
        int lineStart = content.lastIndexOf('\n', index - 1) + 1;
        int hash = content.indexOf('#', lineStart);
        return hash >= 0 && hash < index;
    }

    private static ItemStack makeTabIcon(String iconStr) {
        ItemStack stack = org.zonarstudio.spraute_engine.script.ItemStackScriptUtil.createStack(iconStr, 1);
        return stack.isEmpty() ? new ItemStack(Items.STONE) : stack;
    }

    private static ItemStack resolveTabItemStack(String spec) {
        if (spec == null || spec.isBlank()) return ItemStack.EMPTY;
        ItemStack stack = org.zonarstudio.spraute_engine.script.ItemStackScriptUtil.createStack(spec, 1);
        if (!stack.isEmpty()) return stack;
        var parsed = org.zonarstudio.spraute_engine.script.ItemStackScriptUtil.parseSpec(spec);
        if (!parsed.id().contains(":")) {
            return org.zonarstudio.spraute_engine.script.ItemStackScriptUtil.createStack(
                    org.zonarstudio.spraute_engine.script.ItemStackScriptUtil.formatSpec(
                            Spraute_engine.MODID + ":" + parsed.id(), parsed.nbt()), 1);
        }
        return ItemStack.EMPTY;
    }

    private static Component creativeTabTitle(String tabId) {
        String raw = TAB_DISPLAY_NAMES.get(tabId);
        if (raw != null && !raw.isBlank()) {
            return Component.literal(raw.replace("&", "§"));
        }
        return Component.literal(tabId.replace('_', ' '));
    }

    //? if >=1.20.1 {
    private static void populateCreativeTab(String tabId, CreativeModeTab.Output output) {
        for (CustomBlockDef def : BLOCKS.values()) {
            if (tabId.equals(def.tab)) {
                Item item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(new ResourceLocation(Spraute_engine.MODID, def.id));
                if (item != null) {
                    output.accept(new ItemStack(item));
                }
            }
        }
        for (CustomItemDef def : ITEMS.values()) {
            if (tabId.equals(def.tab)) {
                Item item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(new ResourceLocation(Spraute_engine.MODID, def.id));
                if (item != null) {
                    output.accept(new ItemStack(item));
                }
            }
        }
        List<String> extra = TAB_EXTRA_ITEMS.get(tabId);
        if (extra != null) {
            for (String spec : extra) {
                ItemStack stack = resolveTabItemStack(spec);
                if (stack != null && !stack.isEmpty()) {
                    output.accept(stack);
                }
            }
        }
    }
    //?}

    @SubscribeEvent
    public static void onRegister(RegisterEvent event) {
        parseScripts();

        //? if >=1.20.1 {
        if (event.getRegistryKey().equals(Registries.CREATIVE_MODE_TAB)) {
            for (CustomTabDef tabDef : TAB_DEFS.values()) {
                final String tabId = tabDef.id;
                final String icon = tabDef.icon;
                event.register(Registries.CREATIVE_MODE_TAB, new ResourceLocation(Spraute_engine.MODID, "spraute_" + tabId), () -> {
                    CreativeModeTab tab = CreativeModeTab.builder()
                            .title(creativeTabTitle(tabId))
                            .icon(() -> makeTabIcon(icon))
                            .displayItems((params, output) -> populateCreativeTab(tabId, output))
                            .build();
                    CUSTOM_TABS.put(tabId, tab);
                    return tab;
                });
            }
        }
        //?}

        if (event.getRegistryKey().equals(
                //? if >=1.20.1 {
                Registries.BLOCK
                //?} else {
                /*Registry.BLOCK_REGISTRY*/
                //?}
        )) {
            BlockBehaviour.Properties slaveProps =
                    //? if >=1.20.1 {
                    BlockBehaviour.Properties.of()
                    //?} else {
                    /*BlockBehaviour.Properties.of(Material.STONE)
                    *///?}
                    .strength(-1f, 3600000f).noOcclusion().noLootTable();
            MultiblockSlaveBlock slaveBlock = new MultiblockSlaveBlock(slaveProps);
            MULTIBLOCK_SLAVE_BLOCK = slaveBlock;
            event.register(
                    //? if >=1.20.1 {
                    Registries.BLOCK
                    //?} else {
                    /*Registry.BLOCK_REGISTRY*/
                    //?}
            , new ResourceLocation(Spraute_engine.MODID, "multiblock_slave"), () -> slaveBlock);

            for (CustomBlockDef def : BLOCKS.values()) {
                BlockBehaviour.Properties props =
                    //? if >=1.20.1 {
                    BlockBehaviour.Properties.of()
                    //?} else {
                    /*BlockBehaviour.Properties.of(Material.STONE)
                    *///?}
                    .strength(def.hardness, def.hardness * 4.0f)
                    .noOcclusion()
                    .lightLevel(state -> def.lightEmission);
                
                if (!def.hasCollision) props.noCollission();
                if (def.requiresCorrectTool) props.requiresCorrectToolForDrops();
                
                Block block = new CustomGeoBlock(props, def.model, def.texture, def.dropItem, def.dropCount,
                        def.dropReplace, def.dropRules, def.directional, def.sizeW, def.sizeD, def.sizeH,
                        def.hitbox, def.hardness, def.hasCollision,
                        def.requiredTool, def.requiredMiningLevel, def.requiresCorrectTool);
                if (block instanceof CustomGeoBlock geo) geo.setDropXp(def.dropXp);
                REGISTERED_BLOCKS.add(block);
                event.register(
                        //? if >=1.20.1 {
                        Registries.BLOCK
                        //?} else {
                        /*Registry.BLOCK_REGISTRY*/
                        //?}
                , new ResourceLocation(Spraute_engine.MODID, def.id), () -> block);
            }
        }

        if (event.getRegistryKey().equals(
                //? if >=1.20.1 {
                Registries.ITEM
                //?} else {
                /*Registry.ITEM_REGISTRY*/
                //?}
        )) {
            for (CustomBlockDef def : BLOCKS.values()) {
                Item.Properties props = new Item.Properties();
                //? if <1.20.1 {
                /*if (def.tab != null && CUSTOM_TABS.containsKey(def.tab)) {
                    props = props.tab(CUSTOM_TABS.get(def.tab));
                }
                *///?}
                final Item.Properties finalProps = props;
                Block block = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getValue(new ResourceLocation(Spraute_engine.MODID, def.id));
                if (block != null) {
                    final String blockDisplayName = def.displayName;
                    event.register(
                            //? if >=1.20.1 {
                            Registries.ITEM
                            //?} else {
                            /*Registry.ITEM_REGISTRY*/
                            //?}
                    , new ResourceLocation(Spraute_engine.MODID, def.id), () -> {
                        if (blockDisplayName != null && !blockDisplayName.isEmpty()) {
                            return new org.zonarstudio.spraute_engine.item.ScriptBlockItem(block, finalProps, blockDisplayName);
                        }
                        return new BlockItem(block, finalProps);
                    });
                }
            }
            
            for (CustomItemDef def : ITEMS.values()) {
                Item.Properties props = new Item.Properties().stacksTo(def.maxStackSize);
                //? if <1.20.1 {
                /*if (def.tab != null && CUSTOM_TABS.containsKey(def.tab)) {
                    props = props.tab(CUSTOM_TABS.get(def.tab));
                }
                *///?}
                final Item.Properties finalProps = props;
                final String itemDisplayName = def.displayName;
                event.register(
                        //? if >=1.20.1 {
                        Registries.ITEM
                        //?} else {
                        /*Registry.ITEM_REGISTRY*/
                        //?}
                , new ResourceLocation(Spraute_engine.MODID, def.id),
                        () -> org.zonarstudio.spraute_engine.item.ScriptCustomItemFactory.create(def, finalProps, itemDisplayName));
            }
        }
        
        if (event.getRegistryKey().equals(
                //? if >=1.20.1 {
                Registries.BLOCK_ENTITY_TYPE
                //?} else {
                /*Registry.BLOCK_ENTITY_TYPE_REGISTRY*/
                //?}
        )) {
            Block[] blocksArr = REGISTERED_BLOCKS.isEmpty() ? new Block[]{net.minecraft.world.level.block.Blocks.STONE} : REGISTERED_BLOCKS.toArray(new Block[0]);
            // Even if empty, we MUST register the BlockEntityType or Forge will crash later
            // if we try to register a renderer for it, or it will just be null.
            // A BlockEntityType with no valid blocks is allowed, but we pass stone just in case.
            CUSTOM_GEO_BLOCK_ENTITY = BlockEntityType.Builder.of(CustomGeoBlockEntity::new, blocksArr).build(null);
            event.register(
                    //? if >=1.20.1 {
                    Registries.BLOCK_ENTITY_TYPE
                    //?} else {
                    /*Registry.BLOCK_ENTITY_TYPE_REGISTRY*/
                    //?}
            , new ResourceLocation(Spraute_engine.MODID, "custom_geo_block"), () -> CUSTOM_GEO_BLOCK_ENTITY);

            MULTIBLOCK_SLAVE_BLOCK_ENTITY = BlockEntityType.Builder.of(
                    MultiblockSlaveBlockEntity::new,
                    MULTIBLOCK_SLAVE_BLOCK != null ? MULTIBLOCK_SLAVE_BLOCK : net.minecraft.world.level.block.Blocks.STONE
            ).build(null);
            event.register(
                    //? if >=1.20.1 {
                    Registries.BLOCK_ENTITY_TYPE
                    //?} else {
                    /*Registry.BLOCK_ENTITY_TYPE_REGISTRY*/
                    //?}
            , new ResourceLocation(Spraute_engine.MODID, "multiblock_slave"), () -> MULTIBLOCK_SLAVE_BLOCK_ENTITY);
        }
    }

    /**
     * Хитбокс блока (координаты 0–16 для полного формата):
     * 6 чисел — min/max; 3 — ширина, глубина, высота; 2 — ширина и высота (квадрат в плане).
     */
    private static float[] parseBlockHitbox(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String[] parts = raw.split("[,;\\s]+");
        if (parts.length >= 6) {
            float[] box = new float[6];
            for (int i = 0; i < 6; i++) {
                box[i] = Float.parseFloat(parts[i].trim());
            }
            return box;
        }
        if (parts.length == 3) {
            float w = Float.parseFloat(parts[0].trim());
            float d = Float.parseFloat(parts[1].trim());
            float h = Float.parseFloat(parts[2].trim());
            return centeredHitbox(w, d, h);
        }
        if (parts.length == 2) {
            float w = Float.parseFloat(parts[0].trim());
            float h = Float.parseFloat(parts[1].trim());
            return centeredHitbox(w, w, h);
        }
        return null;
    }

    private static float[] centeredHitbox(float width, float depth, float height) {
        float halfW = Math.max(0f, width) * 8f;
        float halfD = Math.max(0f, depth) * 8f;
        float maxY = Math.max(0f, height) * 16f;
        return new float[]{
                8f - halfW, 0f, 8f - halfD,
                8f + halfW, maxY, 8f + halfD
        };
    }

    /** {@code [w, d, h]} or {@code [w, d]} (height 1) in block cells. */
    private static int[] parseBlockSize(String raw) {
        if (raw == null || raw.isBlank()) return new int[]{1, 1, 1};
        String[] parts = raw.split("[,;\\s]+");
        try {
            if (parts.length >= 3) {
                return new int[]{
                        Math.max(1, Integer.parseInt(parts[0].trim())),
                        Math.max(1, Integer.parseInt(parts[1].trim())),
                        Math.max(1, Integer.parseInt(parts[2].trim()))
                };
            }
            if (parts.length == 2) {
                return new int[]{
                        Math.max(1, Integer.parseInt(parts[0].trim())),
                        Math.max(1, Integer.parseInt(parts[1].trim())),
                        1
                };
            }
            if (parts.length == 1) {
                int n = Math.max(1, Integer.parseInt(parts[0].trim()));
                return new int[]{n, n, 1};
            }
        } catch (NumberFormatException ignored) {}
        return new int[]{1, 1, 1};
    }

    /** {@code any}/{@code shapeless} — порядок не важен; {@code slots}/{@code shaped} — схема в слотах. */
    private static String normalizeCraftType(String type) {
        return CraftJsonBuilder.normalizeType(type);
    }

    /** Builds shaped recipe JSON from {@code slot_1}…{@code slot_9} (3×3 grid, empty slots skipped). */
    private static void appendShapedCraftFromSlots(StringBuilder json, String body, String recipeId, String result, int count) {
        CraftJsonBuilder.appendShapedCraftFromSlots(json, body, recipeId, result, count);
    }

    private static String resolveItemId(String item) {
        return CraftJsonBuilder.resolveItemId(item);
    }

    private static String resolveTagId(String tag) {
        return CraftJsonBuilder.resolveTagId(tag);
    }

    private static List<String> parseQuotedStrings(String listContent) {
        List<String> out = new ArrayList<>();
        java.util.regex.Matcher m = Pattern.compile("\"([^\"]+)\"").matcher(listContent);
        while (m.find()) out.add(m.group(1));
        return out;
    }

    /** Balanced-brace body of a {@code create kind id { ... }} declaration. */
    private static String extractCreateBody(String content, Matcher headerMatch) {
        int bracePos = headerMatch.end() - 1;
        int depth = 0;
        int i = bracePos;
        while (i < content.length()) {
            char c = content.charAt(i++);
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return content.substring(bracePos + 1, i - 1);
                }
            }
        }
        return "";
    }

    /** Reads {@code field = true/false} or {@code field = "true"/"false"} from a static create block body. */
    private static boolean parseBoolField(String body, String field, boolean defaultValue) {
        Pattern p = Pattern.compile(Pattern.quote(field) + "\\s*=\\s*(?:\"(true|false)\"|(true|false))");
        Matcher m = p.matcher(body);
        if (!m.find()) return defaultValue;
        String v = m.group(1) != null ? m.group(1) : m.group(2);
        return Boolean.parseBoolean(v);
    }

    private static boolean hasBoolField(String body, String field) {
        Pattern p = Pattern.compile(Pattern.quote(field) + "\\s*=\\s*(?:\"(?:true|false)\"|(?:true|false))");
        return p.matcher(body).find();
    }

    public static String normalizeBlockTool(String raw) {
        if (raw == null || raw.isBlank()) return null;
        return switch (raw.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "pickaxe", "кирка", "pick" -> "pickaxe";
            case "axe", "топор" -> "axe";
            case "shovel", "лопата" -> "shovel";
            case "hoe", "мотыга" -> "hoe";
            case "none", "any", "hand", "" -> null;
            default -> raw.trim().toLowerCase(java.util.Locale.ROOT);
        };
    }

    /** Resolve a custom Spraute block by id ({@code my_block} or {@code spraute_engine:my_block}). */
    public static CustomGeoBlock resolveCustomGeoBlock(String blockId) {
        if (blockId == null || blockId.isEmpty()) return null;
        String id = blockId;
        if (id.contains(":")) {
            ResourceLocation rl = new ResourceLocation(id);
            if (!Spraute_engine.MODID.equals(rl.getNamespace())) {
                Block b = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getValue(rl);
                return b instanceof CustomGeoBlock cgb ? cgb : null;
            }
            id = rl.getPath();
        }
        Block b = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getValue(
                new ResourceLocation(Spraute_engine.MODID, id));
        return b instanceof CustomGeoBlock cgb ? cgb : null;
    }

    private static String defKey(String blockId) {
        if (blockId == null || blockId.isEmpty()) return null;
        if (blockId.contains(":")) return new ResourceLocation(blockId).getPath();
        return blockId;
    }

    public static boolean setBlockHardness(String blockId, float hardness) {
        CustomGeoBlock block = resolveCustomGeoBlock(blockId);
        if (block == null) return false;
        block.setRuntimeHardness(hardness);
        CustomBlockDef def = BLOCKS.get(defKey(blockId));
        if (def != null) def.hardness = hardness;
        org.zonarstudio.spraute_engine.network.SyncCustomBlockPropsPacket.broadcast(block);
        return true;
    }

    public static Float getBlockHardness(String blockId) {
        CustomGeoBlock block = resolveCustomGeoBlock(blockId);
        return block != null ? block.getRuntimeHardness() : null;
    }

    public static boolean setBlockSolid(String blockId, boolean solid) {
        CustomGeoBlock block = resolveCustomGeoBlock(blockId);
        if (block == null) return false;
        block.setRuntimeSolid(solid);
        CustomBlockDef def = BLOCKS.get(defKey(blockId));
        if (def != null) def.hasCollision = solid;
        org.zonarstudio.spraute_engine.network.SyncCustomBlockPropsPacket.broadcast(block);
        return true;
    }

    public static Boolean getBlockSolid(String blockId) {
        CustomGeoBlock block = resolveCustomGeoBlock(blockId);
        return block != null ? block.isRuntimeSolid() : null;
    }

    private static org.zonarstudio.spraute_engine.item.GeoItemTransform parseGeoItemTransform(String body, String field) {
        String raw = extractBracketArrayValue(body, field);
        if (raw == null) return null;
        return org.zonarstudio.spraute_engine.item.GeoItemTransform.parse(raw);
    }

    /** Извлекает содержимое массива после {@code key = [}, учитывая вложенные скобки. */
    private static String extractBracketArrayValue(String body, String key) {
        Pattern p = Pattern.compile(Pattern.quote(key) + "\\s*=\\s*\\[");
        Matcher m = p.matcher(body);
        if (!m.find()) return null;
        int start = m.end();
        int depth = 1;
        int i = start;
        while (i < body.length() && depth > 0) {
            char c = body.charAt(i++);
            if (c == '[') depth++;
            else if (c == ']') depth--;
        }
        return body.substring(start, i - 1);
    }

    private static List<String> splitIngredientTokens(String arrayBody) {
        List<String> tokens = new ArrayList<>();
        int i = 0;
        while (i < arrayBody.length()) {
            while (i < arrayBody.length()) {
                char c = arrayBody.charAt(i);
                if (Character.isWhitespace(c) || c == ',') i++;
                else break;
            }
            if (i >= arrayBody.length()) break;
            char c = arrayBody.charAt(i);
            if (c == '"') {
                int end = arrayBody.indexOf('"', i + 1);
                if (end < 0) break;
                tokens.add(arrayBody.substring(i, end + 1));
                i = end + 1;
            } else if (c == '[') {
                int depth = 1;
                int j = i + 1;
                while (j < arrayBody.length() && depth > 0) {
                    char ch = arrayBody.charAt(j++);
                    if (ch == '[') depth++;
                    else if (ch == ']') depth--;
                }
                tokens.add(arrayBody.substring(i, j));
                i = j;
            } else {
                break;
            }
        }
        return tokens;
    }

    /**
     * Ingredient spec: {@code "item"}, {@code "#tag"}, or {@code ["item_a", "item_b"]} (any of).
     */
    private static String ingredientToJson(String spec, String recipeId, String slotKey) {
        return CraftJsonBuilder.ingredientToJson(spec, recipeId, slotKey);
    }
}

