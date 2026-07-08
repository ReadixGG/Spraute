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

import org.zonarstudio.spraute_engine.compat.SprauteEntityCompat;
import org.zonarstudio.spraute_engine.entity.ModEntities;

/**
 * Spraute Engine — Story scripting engine for Minecraft.
 * 
 * Loads .spr scripts from config/spraute_engine/scripts/ and executes them
 * via /spraute run <name> command.
 */
@Mod(Spraute_engine.MODID)
@Mod.EventBusSubscriber(modid = Spraute_engine.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class Spraute_engine {

    /** Last entity that hurt a living mob (fallback when death damage source has no attacker). */
    private static final java.util.Map<java.util.UUID, net.minecraft.world.entity.Entity> LAST_ATTACKERS =
            new java.util.concurrent.ConcurrentHashMap<>();

    public static final String MODID = "spraute_engine";
    private static final Logger LOGGER = LogUtils.getLogger();

    public Spraute_engine() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModEntities.register(modEventBus);
        org.zonarstudio.spraute_engine.registry.ModItems.register(modEventBus);
        org.zonarstudio.spraute_engine.registry.ModCreativeTabs.register(modEventBus);

        modEventBus.addListener(this::commonSetup);

        MinecraftForge.EVENT_BUS.register(this);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        org.zonarstudio.spraute_engine.network.ModNetwork.register();
        LOGGER.info("[Spraute Engine] Common setup complete");
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
        if (!SprauteEntityCompat.level(event.getEntity()).isClientSide && event.getEntity() instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            org.zonarstudio.spraute_engine.script.ScriptWorldData data = org.zonarstudio.spraute_engine.script.ScriptWorldData.get(SprauteEntityCompat.serverLevel(serverPlayer));
            boolean showScreen = true;
            Object val = data.get("_sys_load_screen_off", serverPlayer.getServer(), SprauteEntityCompat.serverLevel(serverPlayer));
            if (val instanceof Boolean b && b) {
                showScreen = false;
            }
            org.zonarstudio.spraute_engine.network.ModNetwork.CHANNEL.send(
                    net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> serverPlayer),
                    new org.zonarstudio.spraute_engine.network.SyncLoadScreenPacket(showScreen)
            );
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        java.util.UUID id = event.getEntity().getUUID();
        org.zonarstudio.spraute_engine.script.PlayerDigSpeedOverrides.clear(id);
        org.zonarstudio.spraute_engine.script.PlayerStepHeightOverrides.clear(id);
        org.zonarstudio.spraute_engine.script.ItemUsageRestrictions.clear(id);
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer sp) {
            org.zonarstudio.spraute_engine.compat.SprauteStepHeightCompat.clear(sp);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(net.minecraftforge.event.TickEvent.PlayerTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) return;
        if (SprauteEntityCompat.level(event.player).isClientSide) return;
        if (event.player instanceof net.minecraft.server.level.ServerPlayer sp) {
            org.zonarstudio.spraute_engine.script.PlayerStepHeightOverrides.apply(sp);
        }
    }

    @SubscribeEvent
    public static void onPlayerBreakSpeed(net.minecraftforge.event.entity.player.PlayerEvent.BreakSpeed event) {
        if (!(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer sp)) return;
        event.setNewSpeed(org.zonarstudio.spraute_engine.script.PlayerDigSpeedOverrides.apply(sp, event.getNewSpeed()));
    }

    @SubscribeEvent
    public static void onServerTick(net.minecraftforge.event.TickEvent.ServerTickEvent event) {
        if (event.phase == net.minecraftforge.event.TickEvent.Phase.END) {
            org.zonarstudio.spraute_engine.script.ScriptManager.getInstance().tick();
        }
    }

    @SubscribeEvent
    public static void onEntityInteract(net.minecraftforge.event.entity.player.PlayerInteractEvent.EntityInteract event) {
        if (!event.getLevel().isClientSide && !(event.getTarget() instanceof org.zonarstudio.spraute_engine.entity.SprauteNpcEntity)) {
            org.zonarstudio.spraute_engine.script.ScriptManager.getInstance().onInteract(event.getTarget(), event.getEntity());
        }
    }

    @SubscribeEvent
    public static void onEntityInteractSpecific(net.minecraftforge.event.entity.player.PlayerInteractEvent.EntityInteractSpecific event) {
        if (!event.getLevel().isClientSide && !(event.getTarget() instanceof org.zonarstudio.spraute_engine.entity.SprauteNpcEntity)) {
            org.zonarstudio.spraute_engine.script.ScriptManager.getInstance().onInteract(event.getTarget(), event.getEntity());
        }
    }

    @SubscribeEvent
    public static void onRightClickItem(net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickItem event) {
        if (!event.getLevel().isClientSide) {
            if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer sp) {
                net.minecraft.world.item.ItemStack stack = event.getItemStack();
                if (stack.getItem() instanceof net.minecraft.world.item.ArmorItem
                        && org.zonarstudio.spraute_engine.script.ItemUsageRestrictions.isForbidden(
                        sp, stack, org.zonarstudio.spraute_engine.script.ItemUsageRestrictions.Action.WEAR)) {
                    event.setCanceled(true);
                    org.zonarstudio.spraute_engine.script.ItemUsageRestrictions.notifyBlocked(sp);
                    return;
                }
                if (org.zonarstudio.spraute_engine.script.ItemUsageRestrictions.isForbidden(
                        sp, stack, org.zonarstudio.spraute_engine.script.ItemUsageRestrictions.Action.USE)) {
                    event.setCanceled(true);
                    org.zonarstudio.spraute_engine.script.ItemUsageRestrictions.notifyBlocked(sp);
                    return;
                }
            }
            net.minecraft.world.item.Item item = event.getItemStack().getItem();
            org.zonarstudio.spraute_engine.script.ScriptManager.getInstance()
                    .onPlayerAction(event.getEntity(), "use", item);
            net.minecraft.resources.ResourceLocation key =
                    net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(item);
            if (key != null && Spraute_engine.MODID.equals(key.getNamespace())) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onItemUseFinish(net.minecraftforge.event.entity.living.LivingEntityUseItemEvent.Finish event) {
        if (!SprauteEntityCompat.level(event.getEntity()).isClientSide && event.getEntity() instanceof net.minecraft.world.entity.player.Player player) {
            org.zonarstudio.spraute_engine.script.ScriptManager.getInstance().onPlayerAction(player, "eat", event.getItem().getItem());
        }
    }

    @SubscribeEvent
    public static void onItemFished(net.minecraftforge.event.entity.player.ItemFishedEvent event) {
        if (!SprauteEntityCompat.level(event.getEntity()).isClientSide) {
            org.zonarstudio.spraute_engine.script.ScriptManager.getInstance().onPlayerAction(event.getEntity(), "fish", null);
        }
    }

    @SubscribeEvent
    public static void onBlockToolModification(net.minecraftforge.event.level.BlockEvent.BlockToolModificationEvent event) {
        if (!event.getLevel().isClientSide() && event.getPlayer() != null) {
            if (event.getPlayer() instanceof net.minecraft.server.level.ServerPlayer sp
                    && org.zonarstudio.spraute_engine.script.ItemUsageRestrictions.isForbidden(
                    sp, sp.getMainHandItem(), org.zonarstudio.spraute_engine.script.ItemUsageRestrictions.Action.USE)) {
                event.setCanceled(true);
                org.zonarstudio.spraute_engine.script.ItemUsageRestrictions.notifyBlocked(sp);
                return;
            }
            if (event.getToolAction() == net.minecraftforge.common.ToolActions.HOE_TILL) {
                org.zonarstudio.spraute_engine.script.ScriptManager.getInstance().onPlayerAction(event.getPlayer(), "hoe", event.getState().getBlock());
            }
        }
    }

    @SubscribeEvent
    public static void onLivingJump(net.minecraftforge.event.entity.living.LivingEvent.LivingJumpEvent event) {
        if (!SprauteEntityCompat.level(event.getEntity()).isClientSide && event.getEntity() instanceof net.minecraft.world.entity.player.Player player) {
            org.zonarstudio.spraute_engine.script.ScriptManager.getInstance().onPlayerAction(player, "jump", null);
        }
    }

    @SubscribeEvent
    public static void onPlayerSleep(net.minecraftforge.event.entity.player.PlayerSleepInBedEvent event) {
        if (!SprauteEntityCompat.level(event.getEntity()).isClientSide) {
            org.zonarstudio.spraute_engine.script.ScriptManager.getInstance().onPlayerAction(event.getEntity(), "sleep", null);
        }
    }

    @SubscribeEvent
    public static void onItemCrafted(net.minecraftforge.event.entity.player.PlayerEvent.ItemCraftedEvent event) {
        if (!SprauteEntityCompat.level(event.getEntity()).isClientSide) {
            org.zonarstudio.spraute_engine.script.ScriptManager.getInstance().onPlayerAction(event.getEntity(), "craft", event.getCrafting().getItem());
        }
    }

    @SubscribeEvent
    public static void onItemTossed(net.minecraftforge.event.entity.item.ItemTossEvent event) {
        if (!SprauteEntityCompat.level(event.getPlayer()).isClientSide) {
            org.zonarstudio.spraute_engine.script.ScriptManager.getInstance().onPlayerAction(event.getPlayer(), "drop", event.getEntity().getItem().getItem());
        }
    }

    @SubscribeEvent
    public static void onLivingHurt(net.minecraftforge.event.entity.living.LivingHurtEvent event) {
        if (SprauteEntityCompat.level(event.getEntity()).isClientSide) return;
        net.minecraft.world.entity.Entity attacker =
                org.zonarstudio.spraute_engine.compat.SprauteDeathCompat.resolveAttackerFromSource(event.getSource());
        if (attacker != null) {
            LAST_ATTACKERS.put(event.getEntity().getUUID(), attacker);
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(net.minecraftforge.event.entity.living.LivingDeathEvent event) {
        if (!SprauteEntityCompat.level(event.getEntity()).isClientSide) {
            java.util.UUID victimId = event.getEntity().getUUID();
            net.minecraft.world.entity.Entity killer = org.zonarstudio.spraute_engine.compat.SprauteDeathCompat.resolveKiller(
                    event.getEntity(), event.getSource(), LAST_ATTACKERS);
            LAST_ATTACKERS.remove(victimId);
            org.zonarstudio.spraute_engine.script.ScriptManager.getInstance().onDeath(event.getEntity(), killer);
        }
    }

    @SubscribeEvent
    public static void onLivingDrops(net.minecraftforge.event.entity.living.LivingDropsEvent event) {
        if (!SprauteEntityCompat.level(event.getEntity()).isClientSide) {
            String mobId = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(event.getEntity().getType()).toString();
            java.util.List<org.zonarstudio.spraute_engine.registry.CustomDropRegistry.DropRule> drops = org.zonarstudio.spraute_engine.registry.CustomDropRegistry.MOB_DROPS.get(mobId);
            if (drops != null) {
                boolean replaced = false;
                for (var rule : drops) {
                    if (rule.replace && !replaced) {
                        event.getDrops().clear();
                        replaced = true;
                    }
                    net.minecraft.world.level.Level entityLevel = SprauteEntityCompat.level(event.getEntity());
                    if (entityLevel.random.nextInt(100) < rule.chance) {
                        int count = rule.min + entityLevel.random.nextInt(Math.max(1, rule.max - rule.min + 1));
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
                                entityLevel,
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
        if (event.getLevel().isClientSide()) return;
        net.minecraft.world.level.block.state.BlockState state = event.getLevel().getBlockState(event.getPos());
        net.minecraft.world.level.block.Block block = state.getBlock();
        net.minecraft.world.entity.player.Player player = event.getEntity();

        if (org.zonarstudio.spraute_engine.script.BlockInteractionUtil.isChestLike(block)) {
            boolean canceled = org.zonarstudio.spraute_engine.script.ScriptManager.getInstance()
                    .onOpenChest(player, event.getPos(), block, event.getLevel());
            if (canceled) {
                event.setCanceled(true);
                event.setCancellationResult(net.minecraft.world.InteractionResult.FAIL);
                return;
            }
            if (event.getLevel() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                org.zonarstudio.spraute_engine.script.ChestLootManager.applyOnOpen(serverLevel, event.getPos(), state);
            }
        } else if (org.zonarstudio.spraute_engine.script.BlockInteractionUtil.isDoorLike(block)) {
            boolean canceled = org.zonarstudio.spraute_engine.script.ScriptManager.getInstance()
                    .onOpenDoor(player, event.getPos(), block, event.getLevel());
            if (canceled) {
                event.setCanceled(true);
                event.setCancellationResult(net.minecraft.world.InteractionResult.FAIL);
                return;
            }
        }

        org.zonarstudio.spraute_engine.script.ScriptManager.getInstance()
                .onClickBlock(player, event.getPos(), block, false);
    }

    @SubscribeEvent
    public static void onAttackEntity(net.minecraftforge.event.entity.player.AttackEntityEvent event) {
        if (!SprauteEntityCompat.level(event.getEntity()).isClientSide
                && event.getEntity() instanceof net.minecraft.server.level.ServerPlayer sp) {
            if (org.zonarstudio.spraute_engine.script.ItemUsageRestrictions.isForbidden(
                    sp, sp.getMainHandItem(), org.zonarstudio.spraute_engine.script.ItemUsageRestrictions.Action.ATTACK)) {
                event.setCanceled(true);
                org.zonarstudio.spraute_engine.script.ItemUsageRestrictions.notifyBlocked(sp);
            }
        }
    }

    @SubscribeEvent
    public static void onEquipmentChange(net.minecraftforge.event.entity.living.LivingEquipmentChangeEvent event) {
        if (SprauteEntityCompat.level(event.getEntity()).isClientSide) return;
        if (!(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer sp)) return;
        net.minecraft.world.entity.EquipmentSlot slot = event.getSlot();
        if (slot.getType() != net.minecraft.world.entity.EquipmentSlot.Type.ARMOR) return;
        net.minecraft.world.item.ItemStack to = event.getTo();
        if (to.isEmpty()) return;
        if (!org.zonarstudio.spraute_engine.script.ItemUsageRestrictions.isForbidden(
                sp, to, org.zonarstudio.spraute_engine.script.ItemUsageRestrictions.Action.WEAR)) return;

        net.minecraft.world.item.ItemStack from = event.getFrom();
        sp.setItemSlot(slot, from);
        if (!sp.getInventory().add(to)) {
            sp.drop(to, false);
        }
        org.zonarstudio.spraute_engine.script.ItemUsageRestrictions.notifyBlocked(sp);
    }

    @SubscribeEvent
    public static void onBreakBlock(net.minecraftforge.event.level.BlockEvent.BreakEvent event) {
        if (!event.getLevel().isClientSide() && event.getPlayer() != null) {
            if (event.getPlayer() instanceof net.minecraft.server.level.ServerPlayer sp) {
                if (org.zonarstudio.spraute_engine.script.ItemUsageRestrictions.isForbidden(
                        sp, sp.getMainHandItem(), org.zonarstudio.spraute_engine.script.ItemUsageRestrictions.Action.MINE)) {
                    event.setCanceled(true);
                    org.zonarstudio.spraute_engine.script.ItemUsageRestrictions.notifyBlocked(sp);
                    return;
                }
            }
            boolean scriptCanceled = org.zonarstudio.spraute_engine.script.ScriptManager.getInstance()
                    .onBreakBlock(event.getPlayer(), event.getPos(), event.getState().getBlock());
            if (scriptCanceled) {
                event.setCanceled(true);
                return;
            }

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

            // Drops are handled via CustomGeoBlock.getDrops(); create drop rules use CustomDropRegistry above.
        }
    }

    @SubscribeEvent
    public static void onPlaceBlock(net.minecraftforge.event.level.BlockEvent.EntityPlaceEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof net.minecraft.world.entity.player.Player player) {
            boolean canceled = org.zonarstudio.spraute_engine.script.ScriptManager.getInstance().onPlaceBlock(player, event.getPos(), event.getPlacedBlock().getBlock());
            if (canceled) {
                event.setCanceled(true);
            }
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
        org.zonarstudio.spraute_engine.resource.WorkspaceInitializer.ensureWorkspace(gameDir);
        org.zonarstudio.spraute_engine.config.SprauteConfig.load(gameDir);
        org.zonarstudio.spraute_engine.config.ScriptTriggersConfig.load(gameDir);
        org.zonarstudio.spraute_engine.registry.CustomWorldRegistry.reloadFromScripts();
        ScriptManager.init(gameDir);
        LOGGER.info("[Spraute Engine] Ready! Use /spraute run <script> to run scripts.");
    }

    @SubscribeEvent
    public void onServerStarted(net.minecraftforge.event.server.ServerStartedEvent event) {
        org.zonarstudio.spraute_engine.registry.CustomWorldSafety.refreshOrphansFromServer(event.getServer());
        org.zonarstudio.spraute_engine.registry.CustomWorldSafety.rescueAllPlayers(event.getServer());
        org.zonarstudio.spraute_engine.script.ScriptManager.getInstance().runAutorunScripts(event.getServer());
    }

    @SubscribeEvent
    public static void onEntityJoinLevel(net.minecraftforge.event.entity.EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)) return;
        org.zonarstudio.spraute_engine.registry.CustomWorldSafety.rescuePlayerIfNeeded(player);
    }

    @SubscribeEvent
    public static void onPlayerLogin(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
        if (SprauteEntityCompat.level(event.getEntity()).isClientSide) return;
        net.minecraft.server.level.ServerPlayer player = (net.minecraft.server.level.ServerPlayer) event.getEntity();
        org.zonarstudio.spraute_engine.registry.CustomWorldSafety.rescuePlayerIfNeeded(player);
        net.minecraft.server.level.ServerLevel level = SprauteEntityCompat.serverLevel(player);
        net.minecraft.commands.CommandSourceStack source = player.createCommandSourceStack();

        var triggers = org.zonarstudio.spraute_engine.script.ScriptTriggerRegistry.get();
        boolean firstJoin = org.zonarstudio.spraute_engine.script.FirstJoinData.get(level).isFirstJoin(player.getUUID());

        if (firstJoin && triggers.getOnFirstJoin() != null && !triggers.getOnFirstJoin().isEmpty()) {
            org.zonarstudio.spraute_engine.script.ScriptManager.getInstance().run(triggers.getOnFirstJoin(), source);
            org.zonarstudio.spraute_engine.script.FirstJoinData.get(level).markJoined(player.getUUID());
        } else if (triggers.getOnJoin() != null && !triggers.getOnJoin().isEmpty()) {
            org.zonarstudio.spraute_engine.script.ScriptManager.getInstance().run(triggers.getOnJoin(), source);
        }

        org.zonarstudio.spraute_engine.script.ScriptManager.getInstance().onPlayerJoin(player);
    }

    @SubscribeEvent
    public static void onPlayerRespawn(net.minecraftforge.event.entity.player.PlayerEvent.PlayerRespawnEvent event) {
        if (SprauteEntityCompat.level(event.getEntity()).isClientSide) return;
        if (!(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)) return;
        org.zonarstudio.spraute_engine.registry.CustomWorldSafety.rescuePlayerIfNeeded(player);
        org.zonarstudio.spraute_engine.script.PlayerStepHeightOverrides.apply(player);
        float step = org.zonarstudio.spraute_engine.script.PlayerStepHeightOverrides.get(player);
        org.zonarstudio.spraute_engine.script.PlayerStepHeightOverrides.set(player, step);
        org.zonarstudio.spraute_engine.script.ScriptManager.getInstance().onPlayerJoin(player);
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(net.minecraftforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent event) {
        if (SprauteEntityCompat.level(event.getEntity()).isClientSide) return;
        if (!(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)) return;
        String from = event.getFrom().location().toString();
        String to = event.getTo().location().toString();
        org.zonarstudio.spraute_engine.script.ScriptManager.getInstance().onPlayerDimensionChange(player, from, to);
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
        public static void registerAdditionalModels(net.minecraftforge.client.event.ModelEvent.RegisterAdditional event) {
            org.zonarstudio.spraute_engine.registry.CustomBlockRegistry.ensureParsed();
            for (String itemId : org.zonarstudio.spraute_engine.registry.CustomBlockRegistry.ITEMS.keySet()) {
                event.register(new net.minecraft.client.resources.model.ModelResourceLocation(
                        new net.minecraft.resources.ResourceLocation(Spraute_engine.MODID, itemId), "inventory"));
            }
            for (String blockId : org.zonarstudio.spraute_engine.registry.CustomBlockRegistry.BLOCKS.keySet()) {
                event.register(new net.minecraft.client.resources.model.ModelResourceLocation(
                        new net.minecraft.resources.ResourceLocation(Spraute_engine.MODID, blockId), "inventory"));
            }
        }

        @SubscribeEvent
        public static void registerRenderers(net.minecraftforge.client.event.EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(ModEntities.SPRAUTE_NPC.get(), org.zonarstudio.spraute_engine.entity.client.SprauteNpcRenderer::new);
            event.registerEntityRenderer(ModEntities.SPRAUTE_ORB.get(), org.zonarstudio.spraute_engine.entity.client.SprauteOrbRenderer::new);
            event.registerEntityRenderer(ModEntities.SPRAUTE_BILLBOARD.get(), org.zonarstudio.spraute_engine.entity.client.SprauteBillboardRenderer::new);
            
            // Only register if we actually have custom blocks that need it
            if (org.zonarstudio.spraute_engine.registry.CustomBlockRegistry.CUSTOM_GEO_BLOCK_ENTITY != null) {
                try {
                    event.registerBlockEntityRenderer(org.zonarstudio.spraute_engine.registry.CustomBlockRegistry.CUSTOM_GEO_BLOCK_ENTITY, org.zonarstudio.spraute_engine.registry.CustomGeoBlockRenderer::new);
                } catch (Exception e) {
                    LOGGER.error("Failed to register BlockEntityRenderer: ", e);
                }
            }
        }

        @SubscribeEvent
        public static void registerDimensionEffects(net.minecraftforge.client.event.RegisterDimensionSpecialEffectsEvent event) {
            org.zonarstudio.spraute_engine.registry.CustomWorldRegistry.ensureParsed();
            for (org.zonarstudio.spraute_engine.registry.CustomWorldRegistry.CustomWorldDef def
                    : org.zonarstudio.spraute_engine.registry.CustomWorldRegistry.WORLDS.values()) {
                net.minecraft.resources.ResourceLocation effectsId =
                        org.zonarstudio.spraute_engine.util.SprauteResourcePath.tryCreateLocation(Spraute_engine.MODID, def.id);
                if (effectsId == null) {
                    LOGGER.error("[Spraute Engine] Cannot register dimension effects for invalid world id '{}'", def.id);
                    continue;
                }
                event.register(
                        effectsId,
                        new org.zonarstudio.spraute_engine.client.SprauteWorldDimensionEffects(def));
            }
            net.minecraft.resources.ResourceLocation orphanEffectsId =
                    org.zonarstudio.spraute_engine.util.SprauteResourcePath.tryCreateLocation(
                            Spraute_engine.MODID, org.zonarstudio.spraute_engine.registry.CustomWorldRegistry.ORPHAN_EFFECTS_KEY);
            if (orphanEffectsId != null) {
                event.register(
                        orphanEffectsId,
                        new org.zonarstudio.spraute_engine.client.SprauteWorldDimensionEffects(
                                org.zonarstudio.spraute_engine.registry.CustomWorldRegistry.defaultOrphanDef(
                                        org.zonarstudio.spraute_engine.registry.CustomWorldRegistry.ORPHAN_EFFECTS_KEY)));
            }
        }

        @SubscribeEvent
        public static void registerParticleProviders(net.minecraftforge.client.event.RegisterParticleProvidersEvent event) {
            for (org.zonarstudio.spraute_engine.registry.CustomParticleRegistry.CustomParticleDef def : org.zonarstudio.spraute_engine.registry.CustomParticleRegistry.PARTICLES.values()) {
                net.minecraft.core.particles.SimpleParticleType type = (net.minecraft.core.particles.SimpleParticleType) net.minecraftforge.registries.ForgeRegistries.PARTICLE_TYPES.getValue(new net.minecraft.resources.ResourceLocation(MODID, def.id));
                if (type != null) {
                    //? if >=1.20.1 {
                    event.registerSpriteSet(type, spriteSet -> new net.minecraft.client.particle.ParticleProvider<net.minecraft.core.particles.SimpleParticleType>() {
                        @Override
                        public net.minecraft.client.particle.Particle createParticle(net.minecraft.core.particles.SimpleParticleType t, net.minecraft.client.multiplayer.ClientLevel l, double x, double y, double z, double vx, double vy, double vz) {
                            org.zonarstudio.spraute_engine.client.SprauteCustomParticle particle = new org.zonarstudio.spraute_engine.client.SprauteCustomParticle(l, x, y, z, vx, vy, vz);
                            particle.pickSprite(spriteSet);
                            return particle;
                        }
                    });
                    //?} else {
                    /*event.register(type, spriteSet -> new net.minecraft.client.particle.ParticleProvider<net.minecraft.core.particles.SimpleParticleType>() {
                        @Override
                        public net.minecraft.client.particle.Particle createParticle(net.minecraft.core.particles.SimpleParticleType t, net.minecraft.client.multiplayer.ClientLevel l, double x, double y, double z, double vx, double vy, double vz) {
                            org.zonarstudio.spraute_engine.client.SprauteCustomParticle particle = new org.zonarstudio.spraute_engine.client.SprauteCustomParticle(l, x, y, z, vx, vy, vz);
                            particle.pickSprite(spriteSet);
                            return particle;
                        }
                    });
                    *///?}
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
        public static void onConstruct(net.minecraftforge.fml.event.lifecycle.FMLConstructModEvent event) {
            org.zonarstudio.spraute_engine.resource.WorkspaceInitializer.ensureWorkspace(
                    net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get());
        }

        @SubscribeEvent
        public static void onAddPackFinders(net.minecraftforge.event.AddPackFindersEvent event) {
            if (event.getPackType() == net.minecraft.server.packs.PackType.CLIENT_RESOURCES || event.getPackType() == net.minecraft.server.packs.PackType.SERVER_DATA) {
                java.nio.file.Path gameDir = net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get();
                org.zonarstudio.spraute_engine.resource.WorkspaceInitializer.ensureWorkspace(gameDir);
                java.nio.file.Path assetsDir = gameDir.resolve("spraute_engine");

                LOGGER.info("[Spraute Engine] Registering external " + event.getPackType().name() + " from: {}", assetsDir);
                    //? if >=1.20.1 {
                    event.addRepositorySource(packConsumer -> {
                        var pack = net.minecraft.server.packs.repository.Pack.readMetaAndCreate(
                                "spraute_engine_external_" + event.getPackType().name().toLowerCase(),
                                net.minecraft.network.chat.Component.literal("Spraute Engine External Assets"),
                                true,
                                packId -> new org.zonarstudio.spraute_engine.resource.ExternalAssetPack(assetsDir),
                                event.getPackType(),
                                net.minecraft.server.packs.repository.Pack.Position.TOP,
                                net.minecraft.server.packs.repository.PackSource.BUILT_IN
                        );
                        if (pack != null) {
                            packConsumer.accept(pack);
                        }
                    });
                    //?} else {
                    /*event.addRepositorySource((consumer, constructor) -> {
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
                    *///?}
            }
        }
    }
}

