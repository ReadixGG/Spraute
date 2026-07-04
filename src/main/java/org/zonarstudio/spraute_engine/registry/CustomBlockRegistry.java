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
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.RegisterEvent;
import org.slf4j.Logger;
import org.zonarstudio.spraute_engine.Spraute_engine;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
        public String displayName;
        public String dropItem;
        public int dropCount = 1;
        /** When true, the block itself does not drop (only custom drop(s)). */
        public boolean dropReplace = false;
        public final List<BlockDropRule> dropRules = new ArrayList<>();
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
        /** Bonus attack damage (tooltip value, like iron sword +5). */
        public Float damage;
        public Float attackSpeed;
        public Integer durability;
        public Float miningSpeed;
        public Integer miningLevel;
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

        org.zonarstudio.spraute_engine.registry.CustomWorldRegistry.ensureParsed();
        
        Path scriptsDir = FMLPaths.GAMEDIR.get().resolve("spraute_engine").resolve("scripts");
        if (!Files.exists(scriptsDir)) return;

        parseScriptDirectory(scriptsDir);
    }

    private static void parseScriptDirectory(Path scriptsDir) {
        if (!Files.exists(scriptsDir)) return;

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
        Pattern damagePattern = Pattern.compile("damage\\s*=\\s*([0-9.]+)");
        Pattern attackSpeedPattern = Pattern.compile("attack_speed\\s*=\\s*(-?[0-9.]+)");
        Pattern durabilityPattern = Pattern.compile("durability\\s*=\\s*(\\d+)");
        Pattern miningSpeedPattern = Pattern.compile("mining_speed\\s*=\\s*([0-9.]+)");
        Pattern miningLevelPattern = Pattern.compile("mining_level\\s*=\\s*(\\d+)");
        Pattern lightPattern = Pattern.compile("light\\s*=\\s*(\\d+)");
        Pattern hardnessPattern = Pattern.compile("hardness\\s*=\\s*([0-9.]+)");
        Pattern dropPattern = Pattern.compile("drop\\s*=\\s*\"([^\"]+)\"");
        Pattern dropCountPattern = Pattern.compile("drop_count\\s*=\\s*(\\d+)");
        Pattern blockDropRowPattern = Pattern.compile("\\[\\s*\"([^\"]+)\"\\s*,\\s*(\\d+)\\s*,\\s*(\\d+)\\s*,\\s*(\\d+)\\s*\\]");
        Pattern dropBlockPattern = Pattern.compile("block\\s*=\\s*\"([^\"]+)\"");
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

        try {
            Files.walk(scriptsDir).filter(p -> p.toString().endsWith(".spr")).forEach(file -> {
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
                                json.append("\"").append(k).append("\": ").append(ingredientToJson(spec, id, "key_" + k));
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
                                    json.append(ingredientToJson(tokens.get(ti), id, "ing_" + ti));
                                }
                            json.append("], \"result\": {\"item\": \"").append(result).append("\", \"count\": ").append(count).append("}}");
                            recipeValid = true;
                            }
                            
                        } else if (type.equals("smelting") || type.equals("blasting") || type.equals("smoking") || type.equals("campfire_cooking")) {
                            json.append("{\"type\": \"minecraft:").append(type).append("\", \"ingredient\": ");
                            Matcher ingM = craftIngPattern.matcher(body);
                            String ingSpec = ingM.find() ? "\"" + ingM.group(1) + "\"" : "\"minecraft:cobblestone\"";
                            json.append(ingredientToJson(ingSpec, id, "ingredient"));
                            
                            Matcher xpM = craftXpPattern.matcher(body);
                            float xp = xpM.find() ? Float.parseFloat(xpM.group(1)) : 0.1f;
                            
                            Matcher timeM = craftTimePattern.matcher(body);
                            int time = timeM.find() ? Integer.parseInt(timeM.group(1)) : 200;
                            
                            json.append(", \"result\": \"").append(result).append("\", \"experience\": ").append(xp).append(", \"cookingtime\": ").append(time).append("}");
                            recipeValid = true;
                        }
                        
                        if (recipeValid && json.length() > 0) {
                            CUSTOM_RECIPES_JSON.put(id, json.toString());
                        }
                    }

                    Matcher createDropM = createDropPattern.matcher(content);
                    while (createDropM.find()) {
                        String body = extractCreateBody(content, createDropM);
                        Matcher blockM = dropBlockPattern.matcher(body);
                        Matcher itemM = dropItemPattern.matcher(body);
                        if (!blockM.find() || !itemM.find()) continue;
                        String blockId = blockM.group(1);
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
                        if (!blockId.contains(":")) blockId = Spraute_engine.MODID + ":" + blockId;
                        CustomDropRegistry.addBlockDrop(blockId, itemId, min, max, chance, replace, nbt);
                        LOGGER.info("[Spraute Engine] Found custom block drop: {} -> {}", blockId, itemId);
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

                        Matcher lightM = lightPattern.matcher(body);
                        if (lightM.find()) def.lightEmission = Integer.parseInt(lightM.group(1));

                        Matcher hardM = hardnessPattern.matcher(body);
                        if (hardM.find()) def.hardness = Float.parseFloat(hardM.group(1));
                        
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

                        ITEMS.put(def.id, def);
                        LOGGER.info("[Spraute Engine] Found custom item declaration: {}", def.id);
                    }

                } catch (IOException e) {
                    LOGGER.error("Failed to read script for parsing: {}", file, e);
                }
            });
        } catch (IOException e) {
            LOGGER.error("Failed to walk scripts directory", e);
        }
    }

    private static ItemStack makeTabIcon(String iconStr) {
        ResourceLocation rl = new ResourceLocation(iconStr.contains(":") ? iconStr : "minecraft:" + iconStr);
        Item item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(rl);
        return new ItemStack(item != null ? item : Items.STONE);
    }

    private static ItemStack resolveTabItemStack(String spec) {
        if (spec == null || spec.isBlank()) return ItemStack.EMPTY;
        String itemId = spec.trim();
        if (!itemId.contains(":")) itemId = Spraute_engine.MODID + ":" + itemId;
        Item item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(new ResourceLocation(itemId));
        return item != null ? new ItemStack(item) : ItemStack.EMPTY;
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
                
                Block block = new CustomGeoBlock(props, def.model, def.texture, def.dropItem, def.dropCount, def.dropReplace, def.dropRules, def.directional, def.sizeW, def.sizeD, def.sizeH, def.hitbox);
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
        if (type == null) return "shaped";
        return switch (type.toLowerCase()) {
            case "any", "shapeless" -> "shapeless";
            case "slots", "shaped", "grid" -> "shaped";
            default -> type.toLowerCase();
        };
    }

    /** Builds shaped recipe JSON from {@code slot_1}…{@code slot_9} (3×3 grid, empty slots skipped). */
    private static void appendShapedCraftFromSlots(StringBuilder json, String body, String recipeId, String result, int count) {
        String[] grid = new String[9];
        Matcher slotM = Pattern.compile("slot_(\\d+)\\s*=\\s*\"([^\"]*)\"").matcher(body);
        while (slotM.find()) {
            int idx = Integer.parseInt(slotM.group(1));
            if (idx >= 1 && idx <= 9) {
                grid[idx - 1] = slotM.group(2).trim();
            }
        }

        Map<String, Character> itemToLetter = new HashMap<>();
        char next = 'A';
        char[] letters = new char[9];
        for (int i = 0; i < 9; i++) {
            String item = grid[i];
            if (item == null || item.isEmpty()) {
                letters[i] = ' ';
                continue;
            }
            if (!itemToLetter.containsKey(item)) {
                if (next > 'Z') return;
                itemToLetter.put(item, next++);
            }
            letters[i] = itemToLetter.get(item);
        }

        String[] rows = new String[3];
        for (int r = 0; r < 3; r++) {
            rows[r] = "" + letters[r * 3] + letters[r * 3 + 1] + letters[r * 3 + 2];
        }

        int startRow = 0, endRow = 2;
        while (startRow <= endRow && rows[startRow].trim().isEmpty()) startRow++;
        while (endRow >= startRow && rows[endRow].trim().isEmpty()) endRow--;
        if (startRow > endRow) return;

        int startCol = 0, endCol = 2;
        outer:
        while (startCol <= endCol) {
            for (int r = startRow; r <= endRow; r++) {
                if (rows[r].charAt(startCol) != ' ') break outer;
            }
            startCol++;
        }
        outer2:
        while (endCol >= startCol) {
            for (int r = startRow; r <= endRow; r++) {
                if (rows[r].charAt(endCol) != ' ') break outer2;
            }
            endCol--;
        }

        json.append("{\"type\": \"minecraft:crafting_shaped\", \"pattern\": [");
        for (int r = startRow; r <= endRow; r++) {
            if (r > startRow) json.append(",");
            json.append("\"").append(rows[r].substring(startCol, endCol + 1)).append("\"");
        }
        json.append("], \"key\": {");

        Map<Character, String> letterToItem = new HashMap<>();
        for (Map.Entry<String, Character> e : itemToLetter.entrySet()) {
            letterToItem.put(e.getValue(), e.getKey());
        }
        boolean firstKey = true;
        for (char c = 'A'; c < next; c++) {
            String itemSpec = letterToItem.get(c);
            if (itemSpec == null) continue;
            if (!firstKey) json.append(",");
            firstKey = false;
            json.append("\"").append(c).append("\": ").append(ingredientToJson("\"" + itemSpec + "\"", recipeId, "slot_" + c));
        }
        json.append("}, \"result\": {\"item\": \"").append(result).append("\", \"count\": ").append(count).append("}}");
    }

    private static String resolveItemId(String item) {
        if (item == null || item.isEmpty()) return "minecraft:barrier";
        if (!item.contains(":")) return Spraute_engine.MODID + ":" + item;
        return item;
    }

    private static String resolveTagId(String tag) {
        String t = tag.startsWith("#") ? tag.substring(1) : tag;
        if (!t.contains(":")) return "minecraft:" + t;
        return t;
    }

    private static List<String> parseQuotedStrings(String listContent) {
        List<String> out = new ArrayList<>();
        Matcher m = Pattern.compile("\"([^\"]+)\"").matcher(listContent);
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
        String raw = spec.trim();
        if (raw.startsWith("\"") && raw.endsWith("\"")) {
            raw = raw.substring(1, raw.length() - 1);
        }
        if (raw.startsWith("#")) {
            return "{\"tag\": \"" + resolveTagId(raw) + "\"}";
        }
        if (raw.startsWith("[")) {
            List<String> items = parseQuotedStrings(raw);
            if (items.isEmpty()) return "{\"item\": \"minecraft:barrier\"}";
            if (items.size() == 1) {
                return "{\"item\": \"" + resolveItemId(items.get(0)) + "\"}";
            }
            String tagId = "craft_" + recipeId + "_" + slotKey;
            StringBuilder tagJson = new StringBuilder("{\"replace\": false, \"values\": [");
            boolean first = true;
            for (String item : items) {
                if (!first) tagJson.append(",");
                first = false;
                tagJson.append("\"").append(resolveItemId(item)).append("\"");
            }
            tagJson.append("]}");
            CUSTOM_CRAFT_TAGS_JSON.put(tagId, tagJson.toString());
            return "{\"tag\": \"spraute_engine:" + tagId + "\"}";
        }
        return "{\"item\": \"" + resolveItemId(raw) + "\"}";
    }
}

