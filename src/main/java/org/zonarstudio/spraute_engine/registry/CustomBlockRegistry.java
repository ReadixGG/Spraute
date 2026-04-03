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
        
        public boolean isOre = false;
        public int oreVeinSize = 8;
        public int oreMinY = -64;
        public int oreMaxY = 64;
        public int oreChances = 10;
    }

    public static class CustomItemDef {
        public String id;
        public String model;
        public String texture;
        public int maxStackSize = 64;
    }

    public static final Map<String, CustomItemDef> ITEMS = new HashMap<>();

    private static void parseScripts() {
        if (parsed) return;
        parsed = true;
        
        Path scriptsDir = FMLPaths.GAMEDIR.get().resolve("spraute_engine").resolve("scripts");
        if (!Files.exists(scriptsDir)) return;

        Pattern blockPattern = Pattern.compile("create\\s+block\\s+([a-zA-Z0-9_]+)\\s*\\{([^}]*)\\}");
        Pattern itemPattern = Pattern.compile("create\\s+item\\s+([a-zA-Z0-9_]+)\\s*\\{([^}]*)\\}");
        Pattern modelPattern = Pattern.compile("model\\s*=\\s*\"([^\"]+)\"");
        Pattern texturePattern = Pattern.compile("texture\\s*=\\s*\"([^\"]+)\"");
        Pattern textureUpPattern = Pattern.compile("texture_up\\s*=\\s*\"([^\"]+)\"");
        Pattern textureDownPattern = Pattern.compile("texture_down\\s*=\\s*\"([^\"]+)\"");
        Pattern textureNorthPattern = Pattern.compile("texture_north\\s*=\\s*\"([^\"]+)\"");
        Pattern textureSouthPattern = Pattern.compile("texture_south\\s*=\\s*\"([^\"]+)\"");
        Pattern textureWestPattern = Pattern.compile("texture_west\\s*=\\s*\"([^\"]+)\"");
        Pattern textureEastPattern = Pattern.compile("texture_east\\s*=\\s*\"([^\"]+)\"");
        Pattern collisionPattern = Pattern.compile("collision\\s*=\\s*(true|false)");
        Pattern maxStackPattern = Pattern.compile("maxStackSize\\s*=\\s*(\\d+)");
        Pattern lightPattern = Pattern.compile("light\\s*=\\s*(\\d+)");
        Pattern hardnessPattern = Pattern.compile("hardness\\s*=\\s*([0-9.]+)");
        Pattern dropPattern = Pattern.compile("drop\\s*=\\s*\"([^\"]+)\"");
        Pattern isOrePattern = Pattern.compile("is_ore\\s*=\\s*(true|false)");
        Pattern oreVeinPattern = Pattern.compile("ore_vein\\s*=\\s*(\\d+)");
        Pattern oreMinPattern = Pattern.compile("ore_min\\s*=\\s*(-?\\d+)");
        Pattern oreMaxPattern = Pattern.compile("ore_max\\s*=\\s*(-?\\d+)");
        Pattern oreChancesPattern = Pattern.compile("ore_chances\\s*=\\s*(\\d+)");

        try {
            Files.walk(scriptsDir).filter(p -> p.toString().endsWith(".spr")).forEach(file -> {
                try {
                    String content = Files.readString(file);
                    
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
                        
                        Matcher dropM = dropPattern.matcher(body);
                        if (dropM.find()) def.dropItem = dropM.group(1);

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
                
                Block block = new CustomGeoBlock(props, def.model, def.texture, def.dropItem);
                REGISTERED_BLOCKS.add(block);
                event.register(Registry.BLOCK_REGISTRY, new ResourceLocation(Spraute_engine.MODID, def.id), () -> block);
            }
        }

        if (event.getRegistryKey().equals(Registry.ITEM_REGISTRY)) {
            for (CustomBlockDef def : BLOCKS.values()) {
                Item.Properties props = new Item.Properties();
                Block block = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getValue(new ResourceLocation(Spraute_engine.MODID, def.id));
                if (block != null) {
                    event.register(Registry.ITEM_REGISTRY, new ResourceLocation(Spraute_engine.MODID, def.id), () -> new BlockItem(block, props));
                }
            }
            
            for (CustomItemDef def : ITEMS.values()) {
                Item.Properties props = new Item.Properties().stacksTo(def.maxStackSize);
                event.register(Registry.ITEM_REGISTRY, new ResourceLocation(Spraute_engine.MODID, def.id), () -> new Item(props));
            }
        }
        
        if (event.getRegistryKey().equals(Registry.BLOCK_ENTITY_TYPE_REGISTRY)) {
            if (!REGISTERED_BLOCKS.isEmpty()) {
                Block[] blocksArr = REGISTERED_BLOCKS.toArray(new Block[0]);
                CUSTOM_GEO_BLOCK_ENTITY = BlockEntityType.Builder.of(CustomGeoBlockEntity::new, blocksArr).build(null);
                event.register(Registry.BLOCK_ENTITY_TYPE_REGISTRY, new ResourceLocation(Spraute_engine.MODID, "custom_geo_block"), () -> CUSTOM_GEO_BLOCK_ENTITY);
            }
        }
    }
}

