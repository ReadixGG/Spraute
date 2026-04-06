package org.zonarstudio.spraute_engine.registry;

import com.mojang.logging.LogUtils;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.Material;
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

    public static class CustomBlockDef {
        public String id;
        public String model;
        public String texture;
        public String textureUp;
        public String textureDown;
        public String textureNorth;
        public String textureSouth;
        public String textureWest;
        public String textureEast;
        public boolean hasCollision = true;
        public int lightEmission = 0;
        public float hardness = 1.5f;
        public String dropItem;
        public String tab;
        public boolean directional = true;
        public float[] hitbox;
        
        public boolean isOre = false;
        public int oreVeinSize = 8;
        public int oreMinY = -64;
        public int oreMaxY = 64;
        public int oreChances = 10;
        public String oreDimension = "minecraft:overworld";
    }

    public static class CustomItemDef {
        public String id;
        public String model;
        public String texture;
        public int maxStackSize = 64;
        public String tab;
    }

    public static final Map<String, String> CUSTOM_RECIPES_JSON = new HashMap<>();

    public static final Map<String, CustomItemDef> ITEMS = new HashMap<>();
    public static final Map<String, net.minecraft.world.item.CreativeModeTab> CUSTOM_TABS = new HashMap<>();

    private static void parseScripts() {
        if (parsed) return;
        parsed = true;
        
        Path scriptsDir = FMLPaths.GAMEDIR.get().resolve("spraute_engine").resolve("scripts");
        if (!Files.exists(scriptsDir)) return;

        Pattern tabPattern = Pattern.compile("create\\s+tab\\s+([a-zA-Z0-9_]+)\\s*\\{([^}]*)\\}");
        Pattern craftPattern = Pattern.compile("create\\s+craft\\s+([a-zA-Z0-9_]+)\\s*\\{([^}]*)\\}");
        Pattern craftTypePattern = Pattern.compile("type\\s*=\\s*\"([^\"]+)\"");
        Pattern craftResultPattern = Pattern.compile("result\\s*=\\s*\"([^\"]+)\"");
        Pattern craftCountPattern = Pattern.compile("count\\s*=\\s*(\\d+)");
        Pattern craftKeyPattern = Pattern.compile("key_([^\\s=]+)\\s*=\\s*\"([^\"]+)\"");
        Pattern craftPattern1Pattern = Pattern.compile("pattern_1\\s*=\\s*\"([^\"]+)\"");
        Pattern craftPattern2Pattern = Pattern.compile("pattern_2\\s*=\\s*\"([^\"]+)\"");
        Pattern craftPattern3Pattern = Pattern.compile("pattern_3\\s*=\\s*\"([^\"]+)\"");
        Pattern craftIngPattern = Pattern.compile("ingredient\\s*=\\s*\"([^\"]+)\"");
        Pattern craftXpPattern = Pattern.compile("xp\\s*=\\s*([0-9.]+)");
        Pattern craftTimePattern = Pattern.compile("time\\s*=\\s*(\\d+)");
        Pattern craftIngsPattern = Pattern.compile("\"([^\"]+)\"");
        Pattern blockPattern = Pattern.compile("create\\s+block\\s+([a-zA-Z0-9_]+)\\s*\\{([^}]*)\\}");
        Pattern itemPattern = Pattern.compile("create\\s+item\\s+([a-zA-Z0-9_]+)\\s*\\{([^}]*)\\}");
        Pattern modelPattern = Pattern.compile("model\\s*=\\s*\"([^\"]+)\"");
        Pattern texturePattern = Pattern.compile("texture\\s*=\\s*\"([^\"]+)\"");
        Pattern iconPattern = Pattern.compile("icon\\s*=\\s*\"([^\"]+)\"");
        Pattern tabIdPattern = Pattern.compile("tab\\s*=\\s*\"([^\"]+)\"");
        Pattern textureUpPattern = Pattern.compile("texture_up\\s*=\\s*\"([^\"]+)\"");
        Pattern textureDownPattern = Pattern.compile("texture_down\\s*=\\s*\"([^\"]+)\"");
        Pattern textureNorthPattern = Pattern.compile("texture_north\\s*=\\s*\"([^\"]+)\"");
        Pattern textureSouthPattern = Pattern.compile("texture_south\\s*=\\s*\"([^\"]+)\"");
        Pattern textureWestPattern = Pattern.compile("texture_west\\s*=\\s*\"([^\"]+)\"");
        Pattern textureEastPattern = Pattern.compile("texture_east\\s*=\\s*\"([^\"]+)\"");
        Pattern collisionPattern = Pattern.compile("collision\\s*=\\s*(true|false)");
        Pattern directionalPattern = Pattern.compile("directional\\s*=\\s*(true|false)");
        Pattern hitboxPattern = Pattern.compile("hitbox\\s*=\\s*\\[([^\\]]*)\\]");
        Pattern maxStackPattern = Pattern.compile("maxStackSize\\s*=\\s*(\\d+)");
        Pattern lightPattern = Pattern.compile("light\\s*=\\s*(\\d+)");
        Pattern hardnessPattern = Pattern.compile("hardness\\s*=\\s*([0-9.]+)");
        Pattern dropPattern = Pattern.compile("drop\\s*=\\s*\"([^\"]+)\"");
        Pattern isOrePattern = Pattern.compile("is_ore\\s*=\\s*(true|false)");
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
                        String body = tabM.group(2);
                        Matcher iconM = iconPattern.matcher(body);
                        String iconStr = iconM.find() ? iconM.group(1) : "minecraft:stone";
                        
                        net.minecraft.world.item.CreativeModeTab customTab = new net.minecraft.world.item.CreativeModeTab("spraute_" + id) {
                            @Override
                            public net.minecraft.world.item.ItemStack makeIcon() {
                                net.minecraft.resources.ResourceLocation rl = new net.minecraft.resources.ResourceLocation(iconStr.contains(":") ? iconStr : "minecraft:" + iconStr);
                                net.minecraft.world.item.Item item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(rl);
                                return new net.minecraft.world.item.ItemStack(item != null ? item : net.minecraft.world.item.Items.STONE);
                            }
                        };
                        CUSTOM_TABS.put(id, customTab);
                    }
                    
                    Matcher craftM = craftPattern.matcher(content);
                    while (craftM.find()) {
                        String id = craftM.group(1);
                        String body = craftM.group(2);
                        
                        Matcher typeM = craftTypePattern.matcher(body);
                        String type = typeM.find() ? typeM.group(1) : "shaped";
                        
                        Matcher resM = craftResultPattern.matcher(body);
                        String result = resM.find() ? resM.group(1) : "minecraft:stone";
                        if (!result.contains(":")) result = Spraute_engine.MODID + ":" + result;
                        
                        Matcher countM = craftCountPattern.matcher(body);
                        int count = countM.find() ? Integer.parseInt(countM.group(1)) : 1;
                        
                        StringBuilder json = new StringBuilder();
                        
                        if (type.equals("shaped")) {
                            json.append("{\"type\": \"minecraft:crafting_shaped\", \"pattern\": [");
                            Matcher p1 = craftPattern1Pattern.matcher(body);
                            Matcher p2 = craftPattern2Pattern.matcher(body);
                            Matcher p3 = craftPattern3Pattern.matcher(body);
                            boolean hasP1 = p1.find(), hasP2 = p2.find(), hasP3 = p3.find();
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
                                String item = keyM.group(2);
                                if (!item.contains(":")) item = Spraute_engine.MODID + ":" + item;
                                json.append("\"").append(k).append("\": {\"item\": \"").append(item).append("\"}");
                            }
                            json.append("}, \"result\": {\"item\": \"").append(result).append("\", \"count\": ").append(count).append("}}");
                            
                        } else if (type.equals("shapeless")) {
                            json.append("{\"type\": \"minecraft:crafting_shapeless\", \"ingredients\": [");
                            // Extract ingredients array: ingredients = ["x", "y"]
                            Pattern arrayPat = Pattern.compile("ingredients\\s*=\\s*\\[([^\\]]+)\\]");
                            Matcher arrM = arrayPat.matcher(body);
                            if (arrM.find()) {
                                Matcher itemM = craftIngsPattern.matcher(arrM.group(1));
                                boolean firstItem = true;
                                while (itemM.find()) {
                                    if (!firstItem) json.append(",");
                                    firstItem = false;
                                    String item = itemM.group(1);
                                    if (!item.contains(":")) item = Spraute_engine.MODID + ":" + item;
                                    json.append("{\"item\": \"").append(item).append("\"}");
                                }
                            }
                            json.append("], \"result\": {\"item\": \"").append(result).append("\", \"count\": ").append(count).append("}}");
                            
                        } else if (type.equals("smelting") || type.equals("blasting") || type.equals("smoking") || type.equals("campfire_cooking")) {
                            json.append("{\"type\": \"minecraft:").append(type).append("\", \"ingredient\": {");
                            Matcher ingM = craftIngPattern.matcher(body);
                            String item = ingM.find() ? ingM.group(1) : "minecraft:cobblestone";
                            if (!item.contains(":")) item = Spraute_engine.MODID + ":" + item;
                            json.append("\"item\": \"").append(item).append("\"}");
                            
                            Matcher xpM = craftXpPattern.matcher(body);
                            float xp = xpM.find() ? Float.parseFloat(xpM.group(1)) : 0.1f;
                            
                            Matcher timeM = craftTimePattern.matcher(body);
                            int time = timeM.find() ? Integer.parseInt(timeM.group(1)) : 200;
                            
                            json.append(", \"result\": \"").append(result).append("\", \"experience\": ").append(xp).append(", \"cookingtime\": ").append(time).append("}");
                        }
                        
                        if (json.length() > 0) {
                            CUSTOM_RECIPES_JSON.put(id, json.toString());
                        }
                    }
                    
                    Matcher m = blockPattern.matcher(content);
                    while (m.find()) {
                        CustomBlockDef def = new CustomBlockDef();
                        def.id = m.group(1);
                        String body = m.group(2);
                        
                        Matcher modelM = modelPattern.matcher(body);
                        if (modelM.find()) def.model = modelM.group(1);
                        
                        Matcher texM = texturePattern.matcher(body);
                        if (texM.find()) def.texture = texM.group(1);

                        Matcher tUpM = textureUpPattern.matcher(body);
                        if (tUpM.find()) def.textureUp = tUpM.group(1);
                        Matcher tDownM = textureDownPattern.matcher(body);
                        if (tDownM.find()) def.textureDown = tDownM.group(1);
                        Matcher tNorthM = textureNorthPattern.matcher(body);
                        if (tNorthM.find()) def.textureNorth = tNorthM.group(1);
                        Matcher tSouthM = textureSouthPattern.matcher(body);
                        if (tSouthM.find()) def.textureSouth = tSouthM.group(1);
                        Matcher tWestM = textureWestPattern.matcher(body);
                        if (tWestM.find()) def.textureWest = tWestM.group(1);
                        Matcher tEastM = textureEastPattern.matcher(body);
                        if (tEastM.find()) def.textureEast = tEastM.group(1);

                        Matcher colM = collisionPattern.matcher(body);
                        if (colM.find()) def.hasCollision = Boolean.parseBoolean(colM.group(1));

                        Matcher lightM = lightPattern.matcher(body);
                        if (lightM.find()) def.lightEmission = Integer.parseInt(lightM.group(1));

                        Matcher hardM = hardnessPattern.matcher(body);
                        if (hardM.find()) def.hardness = Float.parseFloat(hardM.group(1));
                        
                        Matcher dirM = directionalPattern.matcher(body);
                        if (dirM.find()) def.directional = Boolean.parseBoolean(dirM.group(1));

                        Matcher hitM = hitboxPattern.matcher(body);
                        if (hitM.find()) {
                            String[] parts = hitM.group(1).split("[,;\\s]+");
                            if (parts.length >= 6) {
                                def.hitbox = new float[6];
                                for (int i = 0; i < 6; i++) {
                                    def.hitbox[i] = Float.parseFloat(parts[i].trim());
                                }
                            }
                        }

                        Matcher dropM = dropPattern.matcher(body);
                        if (dropM.find()) def.dropItem = dropM.group(1);
                        
                        Matcher tabIdM = tabIdPattern.matcher(body);
                        if (tabIdM.find()) def.tab = tabIdM.group(1);
                        
                        Matcher isOreM = isOrePattern.matcher(body);
                        if (isOreM.find()) def.isOre = Boolean.parseBoolean(isOreM.group(1));

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
                        String body = im.group(2);

                        Matcher modelM = modelPattern.matcher(body);
                        if (modelM.find()) def.model = modelM.group(1);

                        Matcher texM = texturePattern.matcher(body);
                        if (texM.find()) def.texture = texM.group(1);

                        Matcher stackM = maxStackPattern.matcher(body);
                        if (stackM.find()) def.maxStackSize = Integer.parseInt(stackM.group(1));

                        Matcher tabIdM2 = tabIdPattern.matcher(body);
                        if (tabIdM2.find()) def.tab = tabIdM2.group(1);

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

    @SubscribeEvent
    public static void onRegister(RegisterEvent event) {
        parseScripts();

        if (event.getRegistryKey().equals(Registry.BLOCK_REGISTRY)) {
            for (CustomBlockDef def : BLOCKS.values()) {
                BlockBehaviour.Properties props = BlockBehaviour.Properties.of(Material.STONE)
                    .strength(def.hardness, def.hardness * 4.0f)
                    .noOcclusion()
                    .lightLevel(state -> def.lightEmission);
                
                if (!def.hasCollision) props.noCollission();
                
                Block block = new CustomGeoBlock(props, def.model, def.texture, def.dropItem, def.directional, def.hitbox);
                REGISTERED_BLOCKS.add(block);
                event.register(Registry.BLOCK_REGISTRY, new ResourceLocation(Spraute_engine.MODID, def.id), () -> block);
            }
        }

        if (event.getRegistryKey().equals(Registry.ITEM_REGISTRY)) {
            for (CustomBlockDef def : BLOCKS.values()) {
                Item.Properties props = new Item.Properties();
                if (def.tab != null && CUSTOM_TABS.containsKey(def.tab)) {
                    props = props.tab(CUSTOM_TABS.get(def.tab));
                }
                final Item.Properties finalProps = props;
                Block block = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getValue(new ResourceLocation(Spraute_engine.MODID, def.id));
                if (block != null) {
                    event.register(Registry.ITEM_REGISTRY, new ResourceLocation(Spraute_engine.MODID, def.id), () -> new BlockItem(block, finalProps));
                }
            }
            
            for (CustomItemDef def : ITEMS.values()) {
                Item.Properties props = new Item.Properties().stacksTo(def.maxStackSize);
                if (def.tab != null && CUSTOM_TABS.containsKey(def.tab)) {
                    props = props.tab(CUSTOM_TABS.get(def.tab));
                }
                final Item.Properties finalProps = props;
                event.register(Registry.ITEM_REGISTRY, new ResourceLocation(Spraute_engine.MODID, def.id), () -> new Item(finalProps));
            }
        }
        
        if (event.getRegistryKey().equals(Registry.BLOCK_ENTITY_TYPE_REGISTRY)) {
            Block[] blocksArr = REGISTERED_BLOCKS.isEmpty() ? new Block[]{net.minecraft.world.level.block.Blocks.STONE} : REGISTERED_BLOCKS.toArray(new Block[0]);
            // Even if empty, we MUST register the BlockEntityType or Forge will crash later
            // if we try to register a renderer for it, or it will just be null.
            // A BlockEntityType with no valid blocks is allowed, but we pass stone just in case.
            CUSTOM_GEO_BLOCK_ENTITY = BlockEntityType.Builder.of(CustomGeoBlockEntity::new, blocksArr).build(null);
            event.register(Registry.BLOCK_ENTITY_TYPE_REGISTRY, new ResourceLocation(Spraute_engine.MODID, "custom_geo_block"), () -> CUSTOM_GEO_BLOCK_ENTITY);
        }
    }
}

