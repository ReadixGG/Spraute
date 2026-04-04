package org.zonarstudio.spraute_engine;

import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import org.zonarstudio.spraute_engine.command.SprauteCommands;
import org.zonarstudio.spraute_engine.script.ScriptManager;

import org.zonarstudio.spraute_engine.entity.ModEntities;

/**
 * Spraute Engine — Story scripting engine for Minecraft.
 * 
 * Loads .spr scripts from config/spraute_engine/scripts/ and executes them
 * via /spraute run <name> command.
 */
@Mod(Spraute_engine.MODID)
@Mod.EventBusSubscriber(modid = Spraute_engine.MODID)
public class Spraute_engine {

    public static final String MODID = "spraute_engine";
    private static final Logger LOGGER = LogUtils.getLogger();

    public Spraute_engine() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModEntities.register(modEventBus);

        modEventBus.addListener(this::commonSetup);

        MinecraftForge.EVENT_BUS.register(this);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        org.zonarstudio.spraute_engine.network.ModNetwork.register();
        LOGGER.info("[Spraute Engine] Common setup complete");
    }

    @SubscribeEvent
    public static void onServerTick(net.minecraftforge.event.TickEvent.ServerTickEvent event) {
        if (event.phase == net.minecraftforge.event.TickEvent.Phase.END) {
            org.zonarstudio.spraute_engine.script.ScriptManager.getInstance().tick();
        }
    }

    @SubscribeEvent
    public static void onEntityInteract(net.minecraftforge.event.entity.player.PlayerInteractEvent.EntityInteract event) {
        if (!event.getLevel().isClientSide) {
            org.zonarstudio.spraute_engine.script.ScriptManager.getInstance().onInteract(event.getTarget(), event.getEntity());
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(net.minecraftforge.event.entity.living.LivingDeathEvent event) {
        if (!event.getEntity().level.isClientSide) {
            net.minecraft.world.entity.Entity killer = event.getSource().getEntity();
            org.zonarstudio.spraute_engine.script.ScriptManager.getInstance().onDeath(event.getEntity(), killer);
        }
    }

    @SubscribeEvent
    public static void onLivingDrops(net.minecraftforge.event.entity.living.LivingDropsEvent event) {
        if (!event.getEntity().level.isClientSide) {
            String mobId = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(event.getEntity().getType()).toString();
            java.util.List<org.zonarstudio.spraute_engine.registry.CustomDropRegistry.DropRule> drops = org.zonarstudio.spraute_engine.registry.CustomDropRegistry.MOB_DROPS.get(mobId);
            if (drops != null) {
                boolean replaced = false;
                for (var rule : drops) {
                    if (rule.replace && !replaced) {
                        event.getDrops().clear();
                        replaced = true;
                    }
                    if (event.getEntity().level.random.nextInt(100) < rule.chance) {
                        int count = rule.min + event.getEntity().level.random.nextInt(Math.max(1, rule.max - rule.min + 1));
                        net.minecraft.world.item.Item item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(
                            new net.minecraft.resources.ResourceLocation(rule.itemId.contains(":") ? rule.itemId : "minecraft:" + rule.itemId)
                        );
                        if (item != null && item != net.minecraft.world.item.Items.AIR) {
                            net.minecraft.world.item.ItemStack stack = new net.minecraft.world.item.ItemStack(item, count);
                            if (rule.nbt != null && !rule.nbt.isEmpty()) {
                                try {
                                    stack.setTag(net.minecraft.nbt.TagParser.parseTag(rule.nbt));
                                } catch (Exception e) {}
                            }
                            net.minecraft.world.entity.item.ItemEntity itemEntity = new net.minecraft.world.entity.item.ItemEntity(
                                event.getEntity().level,
                                event.getEntity().getX(), event.getEntity().getY(), event.getEntity().getZ(),
                                stack
                            );
                            itemEntity.setDefaultPickUpDelay();
                            event.getDrops().add(itemEntity);
                        }
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void onLeftClickBlock(net.minecraftforge.event.entity.player.PlayerInteractEvent.LeftClickBlock event) {
        if (!event.getLevel().isClientSide) {
            org.zonarstudio.spraute_engine.script.ScriptManager.getInstance().onClickBlock(event.getEntity(), event.getPos(), event.getLevel().getBlockState(event.getPos()).getBlock(), true);
        }
    }

    @SubscribeEvent
    public static void onRightClickBlock(net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock event) {
        if (!event.getLevel().isClientSide) {
            org.zonarstudio.spraute_engine.script.ScriptManager.getInstance().onClickBlock(event.getEntity(), event.getPos(), event.getLevel().getBlockState(event.getPos()).getBlock(), false);
        }
    }

    @SubscribeEvent
    public static void onBreakBlock(net.minecraftforge.event.level.BlockEvent.BreakEvent event) {
        if (!event.getLevel().isClientSide() && event.getPlayer() != null) {
            org.zonarstudio.spraute_engine.script.ScriptManager.getInstance().onBreakBlock(event.getPlayer(), event.getPos(), event.getState().getBlock());
            
            String blockId = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(event.getState().getBlock()).toString();
            java.util.List<org.zonarstudio.spraute_engine.registry.CustomDropRegistry.DropRule> drops = org.zonarstudio.spraute_engine.registry.CustomDropRegistry.BLOCK_DROPS.get(blockId);
            if (drops != null) {
                boolean replaced = false;
                for (var rule : drops) {
                    if (rule.replace && !replaced) {
                        replaced = true;
                    }
                    if (((net.minecraft.world.level.Level)event.getLevel()).random.nextInt(100) < rule.chance) {
                        int count = rule.min + ((net.minecraft.world.level.Level)event.getLevel()).random.nextInt(Math.max(1, rule.max - rule.min + 1));
                        net.minecraft.world.item.Item item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(
                            new net.minecraft.resources.ResourceLocation(rule.itemId.contains(":") ? rule.itemId : "minecraft:" + rule.itemId)
                        );
                        if (item != null && item != net.minecraft.world.item.Items.AIR) {
                            net.minecraft.world.item.ItemStack stack = new net.minecraft.world.item.ItemStack(item, count);
                            if (rule.nbt != null && !rule.nbt.isEmpty()) {
                                try {
                                    stack.setTag(net.minecraft.nbt.TagParser.parseTag(rule.nbt));
                                } catch (Exception e) {}
                            }
                            net.minecraft.world.entity.item.ItemEntity itemEntity = new net.minecraft.world.entity.item.ItemEntity(
                                (net.minecraft.world.level.Level)event.getLevel(),
                                event.getPos().getX() + 0.5,
                                event.getPos().getY() + 0.5,
                                event.getPos().getZ() + 0.5,
                                stack
                            );
                            itemEntity.setDefaultPickUpDelay();
                            ((net.minecraft.world.level.Level)event.getLevel()).addFreshEntity(itemEntity);
                        }
                    }
                }
                if (replaced) {
                    ((net.minecraft.world.level.Level)event.getLevel()).setBlockAndUpdate(event.getPos(), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
                    event.setCanceled(true);
                    return;
                }
            }

            if (event.getState().getBlock() instanceof org.zonarstudio.spraute_engine.registry.CustomGeoBlock customBlock) {
                String dropId = customBlock.getDropItem();
                if (dropId != null && !dropId.isEmpty()) {
                    net.minecraft.world.item.Item drop = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(new net.minecraft.resources.ResourceLocation(Spraute_engine.MODID, dropId));
                    if (drop == null || drop == net.minecraft.world.item.Items.AIR) {
                        drop = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(new net.minecraft.resources.ResourceLocation("minecraft", dropId));
                    }
                    if (drop != null && drop != net.minecraft.world.item.Items.AIR) {
                        net.minecraft.world.entity.item.ItemEntity itemEntity = new net.minecraft.world.entity.item.ItemEntity(
                                (net.minecraft.world.level.Level)event.getLevel(),
                                event.getPos().getX() + 0.5,
                                event.getPos().getY() + 0.5,
                                event.getPos().getZ() + 0.5,
                                new net.minecraft.world.item.ItemStack(drop)
                        );
                        itemEntity.setDefaultPickUpDelay();
                        ((net.minecraft.world.level.Level)event.getLevel()).addFreshEntity(itemEntity);
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void onPlaceBlock(net.minecraftforge.event.level.BlockEvent.EntityPlaceEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof net.minecraft.world.entity.player.Player player) {
            org.zonarstudio.spraute_engine.script.ScriptManager.getInstance().onPlaceBlock(player, event.getPos(), event.getPlacedBlock().getBlock());
        }
    }

    @SubscribeEvent
    public static void onChat(net.minecraftforge.event.ServerChatEvent event) {
        if (event.getPlayer() != null) {
            org.zonarstudio.spraute_engine.script.ScriptManager.getInstance().onChat(event.getPlayer(), event.getRawText());
        }
    }

    @SubscribeEvent
    public static void onItemToss(net.minecraftforge.event.entity.item.ItemTossEvent event) {
        net.minecraft.world.item.ItemStack stack = event.getEntity().getItem();
        if (stack.hasTag() && stack.getTag().getBoolean("spraute_no_drop")) {
            event.setCanceled(true);
            event.getPlayer().getInventory().add(stack.copy());
            // Sync inventory to client
            if (event.getPlayer() instanceof net.minecraft.server.level.ServerPlayer sp) {
                sp.containerMenu.broadcastChanges();
            }
        }
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("[Spraute Engine] Initializing script manager...");
        java.nio.file.Path gameDir = event.getServer().getServerDirectory().toPath();
        org.zonarstudio.spraute_engine.config.SprauteConfig.load(gameDir);
        org.zonarstudio.spraute_engine.config.ScriptTriggersConfig.load(gameDir);
        ScriptManager.init(gameDir);
        LOGGER.info("[Spraute Engine] Ready! Use /spraute run <script> to run scripts.");
    }

    @SubscribeEvent
    public static void onPlayerLogin(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity().level.isClientSide) return;
        net.minecraft.server.level.ServerPlayer player = (net.minecraft.server.level.ServerPlayer) event.getEntity();
        net.minecraft.server.level.ServerLevel level = player.getLevel();
        net.minecraft.commands.CommandSourceStack source = player.createCommandSourceStack();

        var triggers = org.zonarstudio.spraute_engine.config.ScriptTriggersConfig.get();
        boolean firstJoin = org.zonarstudio.spraute_engine.script.FirstJoinData.get(level).isFirstJoin(player.getUUID());

        if (firstJoin && triggers.on_first_join != null && !triggers.on_first_join.isEmpty()) {
            org.zonarstudio.spraute_engine.script.ScriptManager.getInstance().run(triggers.on_first_join, source);
            org.zonarstudio.spraute_engine.script.FirstJoinData.get(level).markJoined(player.getUUID());
        } else if (triggers.on_join != null && !triggers.on_join.isEmpty()) {
            org.zonarstudio.spraute_engine.script.ScriptManager.getInstance().run(triggers.on_join, source);
        }
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        SprauteCommands.register(event.getDispatcher());
        LOGGER.info("[Spraute Engine] Commands registered");
    }

    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class ModEventBusEvents {
        @SubscribeEvent
        public static void entityAttributeEvent(net.minecraftforge.event.entity.EntityAttributeCreationEvent event) {
            event.put(ModEntities.SPRAUTE_NPC.get(), org.zonarstudio.spraute_engine.entity.SprauteNpcEntity.setAttributes().build());
        }
    }

    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = net.minecraftforge.api.distmarker.Dist.CLIENT)
    public static class ClientModEvents {
        @SubscribeEvent
        public static void registerRenderers(net.minecraftforge.client.event.EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(ModEntities.SPRAUTE_NPC.get(), org.zonarstudio.spraute_engine.entity.client.SprauteNpcRenderer::new);
            event.registerEntityRenderer(ModEntities.SPRAUTE_ORB.get(), org.zonarstudio.spraute_engine.entity.client.SprauteOrbRenderer::new);
            event.registerBlockEntityRenderer(org.zonarstudio.spraute_engine.registry.CustomBlockRegistry.CUSTOM_GEO_BLOCK_ENTITY, org.zonarstudio.spraute_engine.registry.CustomGeoBlockRenderer::new);
        }

        @SubscribeEvent
        public static void registerParticleProviders(net.minecraftforge.client.event.RegisterParticleProvidersEvent event) {
            for (org.zonarstudio.spraute_engine.registry.CustomParticleRegistry.CustomParticleDef def : org.zonarstudio.spraute_engine.registry.CustomParticleRegistry.PARTICLES.values()) {
                net.minecraft.core.particles.SimpleParticleType type = (net.minecraft.core.particles.SimpleParticleType) net.minecraftforge.registries.ForgeRegistries.PARTICLE_TYPES.getValue(new net.minecraft.resources.ResourceLocation(MODID, def.id));
                if (type != null) {
                    event.register(type, spriteSet -> new net.minecraft.client.particle.ParticleProvider<net.minecraft.core.particles.SimpleParticleType>() {
                        @Override
                        public net.minecraft.client.particle.Particle createParticle(net.minecraft.core.particles.SimpleParticleType t, net.minecraft.client.multiplayer.ClientLevel l, double x, double y, double z, double vx, double vy, double vz) {
                            org.zonarstudio.spraute_engine.client.SprauteCustomParticle particle = new org.zonarstudio.spraute_engine.client.SprauteCustomParticle(l, x, y, z, vx, vy, vz);
                            particle.pickSprite(spriteSet);
                            return particle;
                        }
                    });
                }
            }
        }

        @SubscribeEvent
        public static void onRegisterReloadListeners(net.minecraftforge.client.event.RegisterClientReloadListenersEvent event) {
            event.registerReloadListener((net.minecraft.server.packs.resources.PreparableReloadListener)
                (preparationBarrier, resourceManager, profiler1, profiler2, backgroundExecutor, gameExecutor) ->
                    preparationBarrier.wait(null).thenRunAsync(
                        org.zonarstudio.spraute_engine.entity.client.SpModelCache::clearAll,
                        gameExecutor
                    )
            );
        }

        @SubscribeEvent
        public static void onAddPackFinders(net.minecraftforge.event.AddPackFindersEvent event) {
            if (event.getPackType() == net.minecraft.server.packs.PackType.CLIENT_RESOURCES || event.getPackType() == net.minecraft.server.packs.PackType.SERVER_DATA) {
                // Register the run/spraute_engine/ directory as a resource/data pack
                java.nio.file.Path gameDir = net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get();
                java.nio.file.Path assetsDir = gameDir.resolve("spraute_engine");
                
                if (java.nio.file.Files.isDirectory(assetsDir)) {
                    // Create dummy pack.mcmeta so Pack.create can read metadata
                    java.nio.file.Path mcmeta = assetsDir.resolve("pack.mcmeta");
                    if (!java.nio.file.Files.exists(mcmeta)) {
                        try {
                            java.nio.file.Files.writeString(mcmeta, "{\n  \"pack\": {\n    \"pack_format\": 9,\n    \"description\": \"Spraute Engine External Assets\"\n  }\n}");
                        } catch (java.io.IOException e) {
                            LOGGER.error("Failed to generate pack.mcmeta", e);
                        }
                    }

                    LOGGER.info("[Spraute Engine] Registering external " + event.getPackType().name() + " from: {}", assetsDir);
                    event.addRepositorySource((consumer, constructor) -> {
                        var pack = net.minecraft.server.packs.repository.Pack.create(
                                "spraute_engine_external_" + event.getPackType().name().toLowerCase(),
                                true, // required
                                () -> new org.zonarstudio.spraute_engine.resource.ExternalAssetPack(assetsDir),
                                constructor,
                                net.minecraft.server.packs.repository.Pack.Position.TOP,
                                net.minecraft.server.packs.repository.PackSource.BUILT_IN
                        );
                        if (pack != null) {
                            consumer.accept(pack);
                        }
                    });
                } else {
                    if (event.getPackType() == net.minecraft.server.packs.PackType.CLIENT_RESOURCES) {
                        LOGGER.warn("[Spraute Engine] External assets directory not found: {}", assetsDir);
                    }
                }
            }
        }
    }
}

