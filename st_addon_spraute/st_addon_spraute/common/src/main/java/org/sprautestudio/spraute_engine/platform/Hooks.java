package org.sprautestudio.spraute_engine.platform;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.advancements.Advancement;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** What happens in the game, on either side: the engine listens here, and each loader fires these from its own events. */
public final class Hooks {
    private Hooks() {
    }

    /** The game's registries are ready, on the game's thread. */
    public static final Hook<CommonSetup> COMMON_SETUP = new Hook<>();
    public static final Hook<ServerAboutToStart> SERVER_ABOUT_TO_START = new Hook<>();
    public static final Hook<ServerStopping> SERVER_STOPPING = new Hook<>();
    public static final Hook<ServerStopped> SERVER_STOPPED = new Hook<>();
    public static final Hook<LevelSave> LEVEL_SAVE = new Hook<>();
    public static final Hook<RegisterCommands> REGISTER_COMMANDS = new Hook<>();
    public static final Hook<ServerTick> SERVER_TICK = new Hook<>();
    public static final Hook<PlayerTick> PLAYER_TICK = new Hook<>();
    public static final Hook<AdvancementEarn> ADVANCEMENT_EARN = new Hook<>();
    public static final Hook<AnimalTame> ANIMAL_TAME = new Hook<>();
    public static final Hook<ArrowLoose> ARROW_LOOSE = new Hook<>();
    public static final Hook<AttackEntity> ATTACK_ENTITY = new Hook<>();
    public static final Hook<BabySpawn> BABY_SPAWN = new Hook<>();
    public static final Hook<BlockBreak> BLOCK_BREAK = new Hook<>();
    public static final Hook<BlockPlace> BLOCK_PLACE = new Hook<>();
    public static final Hook<Bonemeal> BONEMEAL = new Hook<>();
    public static final Hook<EntityJoin> ENTITY_JOIN = new Hook<>();
    public static final Hook<EntityLeave> ENTITY_LEAVE = new Hook<>();
    public static final Hook<EntityMount> ENTITY_MOUNT = new Hook<>();
    public static final Hook<ExplosionDetonate> EXPLOSION_DETONATE = new Hook<>();
    public static final Hook<FillBucket> FILL_BUCKET = new Hook<>();
    public static final Hook<ItemFished> ITEM_FISHED = new Hook<>();
    public static final Hook<ItemToss> ITEM_TOSS = new Hook<>();
    public static final Hook<LivingAttack> LIVING_ATTACK = new Hook<>();
    public static final Hook<LivingDeath> LIVING_DEATH = new Hook<>();
    public static final Hook<UseItemFinish> USE_ITEM_FINISH = new Hook<>();
    public static final Hook<EquipmentChange> EQUIPMENT_CHANGE = new Hook<>();
    public static final Hook<LivingJump> LIVING_JUMP = new Hook<>();
    public static final Hook<LivingFall> LIVING_FALL = new Hook<>();
    public static final Hook<LivingHurt> LIVING_HURT = new Hook<>();
    public static final Hook<EffectAdded> EFFECT_ADDED = new Hook<>();
    public static final Hook<ContainerClose> CONTAINER_CLOSE = new Hook<>();
    public static final Hook<ContainerOpen> CONTAINER_OPEN = new Hook<>();
    public static final Hook<ItemCrafted> ITEM_CRAFTED = new Hook<>();
    public static final Hook<ItemPickup> ITEM_PICKUP = new Hook<>();
    public static final Hook<ItemSmelted> ITEM_SMELTED = new Hook<>();
    public static final Hook<ChangedDimension> CHANGED_DIMENSION = new Hook<>();
    public static final Hook<PlayerLoggedIn> PLAYER_LOGGED_IN = new Hook<>();
    public static final Hook<PlayerLoggedOut> PLAYER_LOGGED_OUT = new Hook<>();
    public static final Hook<PlayerRespawn> PLAYER_RESPAWN = new Hook<>();
    public static final Hook<StartTracking> START_TRACKING = new Hook<>();
    public static final Hook<EntityInteract> ENTITY_INTERACT = new Hook<>();
    public static final Hook<LeftClickBlock> LEFT_CLICK_BLOCK = new Hook<>();
    public static final Hook<RightClickBlock> RIGHT_CLICK_BLOCK = new Hook<>();
    public static final Hook<RightClickItem> RIGHT_CLICK_ITEM = new Hook<>();
    public static final Hook<SleepInBed> SLEEP_IN_BED = new Hook<>();
    public static final Hook<WakeUp> WAKE_UP = new Hook<>();
    public static final Hook<XpLevelChange> XP_LEVEL_CHANGE = new Hook<>();
    public static final Hook<XpPickup> XP_PICKUP = new Hook<>();
    public static final Hook<ServerChat> SERVER_CHAT = new Hook<>(true);
    public static final Hook<Trade> TRADE = new Hook<>();

    public static final class CommonSetup {
    }

    public static final class ServerAboutToStart {
        private final MinecraftServer server;

        public ServerAboutToStart(MinecraftServer server) {
            this.server = server;
        }

        public MinecraftServer getServer() {
            return server;
        }
    }

    public static final class ServerStopping {
        private final MinecraftServer server;

        public ServerStopping(MinecraftServer server) {
            this.server = server;
        }

        public MinecraftServer getServer() {
            return server;
        }
    }

    public static final class ServerStopped {
        private final MinecraftServer server;

        public ServerStopped(MinecraftServer server) {
            this.server = server;
        }

        public MinecraftServer getServer() {
            return server;
        }
    }

    public static final class LevelSave {
        private final LevelAccessor level;

        public LevelSave(LevelAccessor level) {
            this.level = level;
        }

        public LevelAccessor getLevel() {
            return level;
        }
    }

    public static final class RegisterCommands {
        private final CommandDispatcher<CommandSourceStack> dispatcher;

        public RegisterCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
            this.dispatcher = dispatcher;
        }

        public CommandDispatcher<CommandSourceStack> getDispatcher() {
            return dispatcher;
        }
    }

    public static final class ServerTick {
        public final TickPhase phase;
        private final MinecraftServer server;

        public ServerTick(TickPhase phase, MinecraftServer server) {
            this.phase = phase;
            this.server = server;
        }

        public MinecraftServer getServer() {
            return server;
        }
    }

    public static final class PlayerTick {
        public final TickPhase phase;
        public final Player player;

        public PlayerTick(TickPhase phase, Player player) {
            this.phase = phase;
            this.player = player;
        }
    }

    public static final class AdvancementEarn {
        private final Player entity;
        private final Advancement advancement;

        public AdvancementEarn(Player entity, Advancement advancement) {
            this.entity = entity;
            this.advancement = advancement;
        }

        public Player getEntity() {
            return entity;
        }

        public Advancement getAdvancement() {
            return advancement;
        }
    }

    public static final class AnimalTame extends Cancellable {
        private final Animal animal;
        private final Player tamer;

        public AnimalTame(Animal animal, Player tamer) {
            this.animal = animal;
            this.tamer = tamer;
        }

        public Animal getAnimal() {
            return animal;
        }

        public Player getTamer() {
            return tamer;
        }
    }

    public static final class ArrowLoose {
        private final Player entity;
        private final ItemStack bow;
        private final int charge;

        public ArrowLoose(Player entity, ItemStack bow, int charge) {
            this.entity = entity;
            this.bow = bow;
            this.charge = charge;
        }

        public Player getEntity() {
            return entity;
        }

        public ItemStack getBow() {
            return bow;
        }

        public int getCharge() {
            return charge;
        }
    }

    public static final class AttackEntity extends Cancellable {
        private final Player entity;
        private final Entity target;

        public AttackEntity(Player entity, Entity target) {
            this.entity = entity;
            this.target = target;
        }

        public Player getEntity() {
            return entity;
        }

        public Entity getTarget() {
            return target;
        }
    }

    public static final class BabySpawn extends Cancellable {
        private final AgeableMob child;
        private final Player causedByPlayer;

        public BabySpawn(AgeableMob child, Player causedByPlayer) {
            this.child = child;
            this.causedByPlayer = causedByPlayer;
        }

        public AgeableMob getChild() {
            return child;
        }

        public Player getCausedByPlayer() {
            return causedByPlayer;
        }
    }

    public static final class BlockBreak extends Cancellable {
        private final BlockPos pos;
        private final BlockState state;
        private final Player player;

        public BlockBreak(BlockPos pos, BlockState state, Player player) {
            this.pos = pos;
            this.state = state;
            this.player = player;
        }

        public BlockPos getPos() {
            return pos;
        }

        public BlockState getState() {
            return state;
        }

        public Player getPlayer() {
            return player;
        }
    }

    public static final class BlockPlace extends Cancellable {
        private final Entity entity;
        private final BlockPos pos;
        private final BlockState placedBlock;

        public BlockPlace(Entity entity, BlockPos pos, BlockState placedBlock) {
            this.entity = entity;
            this.pos = pos;
            this.placedBlock = placedBlock;
        }

        public Entity getEntity() {
            return entity;
        }

        public BlockPos getPos() {
            return pos;
        }

        public BlockState getPlacedBlock() {
            return placedBlock;
        }
    }

    public static final class Bonemeal {
        private final Player entity;
        private final BlockPos pos;
        private final BlockState block;

        public Bonemeal(Player entity, BlockPos pos, BlockState block) {
            this.entity = entity;
            this.pos = pos;
            this.block = block;
        }

        public Player getEntity() {
            return entity;
        }

        public BlockPos getPos() {
            return pos;
        }

        public BlockState getBlock() {
            return block;
        }
    }

    public static final class EntityJoin extends Cancellable {
        private final Entity entity;
        private final Level level;

        public EntityJoin(Entity entity, Level level) {
            this.entity = entity;
            this.level = level;
        }

        public Entity getEntity() {
            return entity;
        }

        public Level getLevel() {
            return level;
        }
    }

    public static final class EntityLeave {
        private final Entity entity;
        private final Level level;

        public EntityLeave(Entity entity, Level level) {
            this.entity = entity;
            this.level = level;
        }

        public Entity getEntity() {
            return entity;
        }

        public Level getLevel() {
            return level;
        }
    }

    public static final class EntityMount extends Cancellable {
        private final Entity entityMounting;
        private final Entity entityBeingMounted;
        private final boolean mounting;

        public EntityMount(Entity entityMounting, Entity entityBeingMounted, boolean mounting) {
            this.entityMounting = entityMounting;
            this.entityBeingMounted = entityBeingMounted;
            this.mounting = mounting;
        }

        public Entity getEntityMounting() {
            return entityMounting;
        }

        public Entity getEntityBeingMounted() {
            return entityBeingMounted;
        }

        public boolean isMounting() {
            return mounting;
        }
    }

    public static final class ExplosionDetonate {
        private final Explosion explosion;
        private final Level level;
        private final Vec3 position;

        public ExplosionDetonate(Explosion explosion, Level level, Vec3 position) {
            this.explosion = explosion;
            this.level = level;
            this.position = position;
        }

        public Vec3 getPosition() {
            return position;
        }

        public Explosion getExplosion() {
            return explosion;
        }

        public Level getLevel() {
            return level;
        }
    }

    public static final class FillBucket {
        private final Player entity;
        private final ItemStack emptyBucket;

        public FillBucket(Player entity, ItemStack emptyBucket) {
            this.entity = entity;
            this.emptyBucket = emptyBucket;
        }

        public Player getEntity() {
            return entity;
        }

        public ItemStack getEmptyBucket() {
            return emptyBucket;
        }
    }

    public static final class ItemFished {
        private final Player entity;
        private final NonNullList<ItemStack> drops;

        public ItemFished(Player entity, NonNullList<ItemStack> drops) {
            this.entity = entity;
            this.drops = drops;
        }

        public Player getEntity() {
            return entity;
        }

        public NonNullList<ItemStack> getDrops() {
            return drops;
        }
    }

    public static final class ItemToss extends Cancellable {
        private final ItemEntity entity;
        private final Player player;

        public ItemToss(ItemEntity entity, Player player) {
            this.entity = entity;
            this.player = player;
        }

        public ItemEntity getEntity() {
            return entity;
        }

        public Player getPlayer() {
            return player;
        }
    }

    public static final class LivingAttack extends Cancellable {
        private final LivingEntity entity;
        private final DamageSource source;
        private final float amount;

        public LivingAttack(LivingEntity entity, DamageSource source, float amount) {
            this.entity = entity;
            this.source = source;
            this.amount = amount;
        }

        public LivingEntity getEntity() {
            return entity;
        }

        public DamageSource getSource() {
            return source;
        }

        public float getAmount() {
            return amount;
        }
    }

    public static final class LivingDeath extends Cancellable {
        private final LivingEntity entity;
        private final DamageSource source;

        public LivingDeath(LivingEntity entity, DamageSource source) {
            this.entity = entity;
            this.source = source;
        }

        public LivingEntity getEntity() {
            return entity;
        }

        public DamageSource getSource() {
            return source;
        }
    }

    public static final class UseItemFinish {
        private final LivingEntity entity;
        private final ItemStack item;

        public UseItemFinish(LivingEntity entity, ItemStack item) {
            this.entity = entity;
            this.item = item;
        }

        public LivingEntity getEntity() {
            return entity;
        }

        public ItemStack getItem() {
            return item;
        }
    }

    public static final class EquipmentChange {
        private final LivingEntity entity;
        private final EquipmentSlot slot;
        private final ItemStack to;

        public EquipmentChange(LivingEntity entity, EquipmentSlot slot, ItemStack to) {
            this.entity = entity;
            this.slot = slot;
            this.to = to;
        }

        public LivingEntity getEntity() {
            return entity;
        }

        public EquipmentSlot getSlot() {
            return slot;
        }

        public ItemStack getTo() {
            return to;
        }
    }

    public static final class LivingJump {
        private final LivingEntity entity;

        public LivingJump(LivingEntity entity) {
            this.entity = entity;
        }

        public LivingEntity getEntity() {
            return entity;
        }
    }

    public static final class LivingFall {
        private final LivingEntity entity;
        private float distance;
        private float damageMultiplier;

        public LivingFall(LivingEntity entity, float distance, float damageMultiplier) {
            this.entity = entity;
            this.distance = distance;
            this.damageMultiplier = damageMultiplier;
        }

        public LivingEntity getEntity() {
            return entity;
        }

        public float getDistance() {
            return distance;
        }

        public void setDistance(float value) {
            distance = value;
        }

        public float getDamageMultiplier() {
            return damageMultiplier;
        }

        public void setDamageMultiplier(float value) {
            damageMultiplier = value;
        }
    }

    public static final class LivingHurt extends Cancellable {
        private final LivingEntity entity;
        private final DamageSource source;
        private final float amount;

        public LivingHurt(LivingEntity entity, DamageSource source, float amount) {
            this.entity = entity;
            this.source = source;
            this.amount = amount;
        }

        public LivingEntity getEntity() {
            return entity;
        }

        public DamageSource getSource() {
            return source;
        }

        public float getAmount() {
            return amount;
        }
    }

    public static final class EffectAdded {
        private final LivingEntity entity;
        private final MobEffectInstance effectInstance;

        public EffectAdded(LivingEntity entity, MobEffectInstance effectInstance) {
            this.entity = entity;
            this.effectInstance = effectInstance;
        }

        public LivingEntity getEntity() {
            return entity;
        }

        public MobEffectInstance getEffectInstance() {
            return effectInstance;
        }
    }

    public static final class ContainerClose {
        private final Player entity;
        private final AbstractContainerMenu container;

        public ContainerClose(Player entity, AbstractContainerMenu container) {
            this.entity = entity;
            this.container = container;
        }

        public Player getEntity() {
            return entity;
        }

        public AbstractContainerMenu getContainer() {
            return container;
        }
    }

    public static final class ContainerOpen {
        private final Player entity;
        private final AbstractContainerMenu container;

        public ContainerOpen(Player entity, AbstractContainerMenu container) {
            this.entity = entity;
            this.container = container;
        }

        public Player getEntity() {
            return entity;
        }

        public AbstractContainerMenu getContainer() {
            return container;
        }
    }

    public static final class ItemCrafted {
        private final Player entity;
        private final ItemStack crafting;

        public ItemCrafted(Player entity, ItemStack crafting) {
            this.entity = entity;
            this.crafting = crafting;
        }

        public Player getEntity() {
            return entity;
        }

        public ItemStack getCrafting() {
            return crafting;
        }
    }

    public static final class ItemPickup {
        private final Player entity;
        private final ItemStack stack;

        public ItemPickup(Player entity, ItemStack stack) {
            this.entity = entity;
            this.stack = stack;
        }

        public Player getEntity() {
            return entity;
        }

        public ItemStack getStack() {
            return stack;
        }
    }

    public static final class ItemSmelted {
        private final Player entity;
        private final ItemStack smelting;

        public ItemSmelted(Player entity, ItemStack smelting) {
            this.entity = entity;
            this.smelting = smelting;
        }

        public Player getEntity() {
            return entity;
        }

        public ItemStack getSmelting() {
            return smelting;
        }
    }

    public static final class ChangedDimension {
        private final Player entity;
        private final ResourceKey<Level> from;
        private final ResourceKey<Level> to;

        public ChangedDimension(Player entity, ResourceKey<Level> from, ResourceKey<Level> to) {
            this.entity = entity;
            this.from = from;
            this.to = to;
        }

        public Player getEntity() {
            return entity;
        }

        public ResourceKey<Level> getFrom() {
            return from;
        }

        public ResourceKey<Level> getTo() {
            return to;
        }
    }

    public static final class PlayerLoggedIn {
        private final Player entity;

        public PlayerLoggedIn(Player entity) {
            this.entity = entity;
        }

        public Player getEntity() {
            return entity;
        }
    }

    public static final class PlayerLoggedOut {
        private final Player entity;

        public PlayerLoggedOut(Player entity) {
            this.entity = entity;
        }

        public Player getEntity() {
            return entity;
        }
    }

    public static final class PlayerRespawn {
        private final Player entity;

        public PlayerRespawn(Player entity) {
            this.entity = entity;
        }

        public Player getEntity() {
            return entity;
        }
    }

    public static final class StartTracking {
        private final Player entity;
        private final Entity target;

        public StartTracking(Player entity, Entity target) {
            this.entity = entity;
            this.target = target;
        }

        public Player getEntity() {
            return entity;
        }

        public Entity getTarget() {
            return target;
        }
    }

    public static final class EntityInteract {
        private final Player entity;
        private final Level level;
        private final InteractionHand hand;
        private final Entity target;
        private final ItemStack itemStack;

        public EntityInteract(Player entity, Level level, InteractionHand hand, Entity target, ItemStack itemStack) {
            this.entity = entity;
            this.level = level;
            this.hand = hand;
            this.target = target;
            this.itemStack = itemStack;
        }

        public Player getEntity() {
            return entity;
        }

        public Level getLevel() {
            return level;
        }

        public InteractionHand getHand() {
            return hand;
        }

        public Entity getTarget() {
            return target;
        }

        public ItemStack getItemStack() {
            return itemStack;
        }
    }

    public static final class LeftClickBlock {
        private final Player entity;
        private final Level level;
        private final BlockPos pos;
        private final ItemStack itemStack;
        private final boolean start;

        public LeftClickBlock(Player entity, Level level, BlockPos pos, ItemStack itemStack, boolean start) {
            this.entity = entity;
            this.level = level;
            this.pos = pos;
            this.itemStack = itemStack;
            this.start = start;
        }

        public Player getEntity() {
            return entity;
        }

        public Level getLevel() {
            return level;
        }

        public BlockPos getPos() {
            return pos;
        }

        public ItemStack getItemStack() {
            return itemStack;
        }

        public boolean isStart() {
            return start;
        }
    }

    public static final class RightClickBlock {
        private final Player entity;
        private final Level level;
        private final InteractionHand hand;
        private final BlockPos pos;
        private final ItemStack itemStack;

        public RightClickBlock(Player entity, Level level, InteractionHand hand, BlockPos pos, ItemStack itemStack) {
            this.entity = entity;
            this.level = level;
            this.hand = hand;
            this.pos = pos;
            this.itemStack = itemStack;
        }

        public Player getEntity() {
            return entity;
        }

        public Level getLevel() {
            return level;
        }

        public InteractionHand getHand() {
            return hand;
        }

        public BlockPos getPos() {
            return pos;
        }

        public ItemStack getItemStack() {
            return itemStack;
        }
    }

    public static final class RightClickItem {
        private final Player entity;
        private final ItemStack itemStack;

        public RightClickItem(Player entity, ItemStack itemStack) {
            this.entity = entity;
            this.itemStack = itemStack;
        }

        public Player getEntity() {
            return entity;
        }

        public ItemStack getItemStack() {
            return itemStack;
        }
    }

    public static final class SleepInBed {
        private final Player entity;
        private final BlockPos pos;

        public SleepInBed(Player entity, BlockPos pos) {
            this.entity = entity;
            this.pos = pos;
        }

        public Player getEntity() {
            return entity;
        }

        public BlockPos getPos() {
            return pos;
        }
    }

    public static final class WakeUp {
        private final Player entity;

        public WakeUp(Player entity) {
            this.entity = entity;
        }

        public Player getEntity() {
            return entity;
        }
    }

    public static final class XpLevelChange {
        private final Player entity;
        private final int levels;

        public XpLevelChange(Player entity, int levels) {
            this.entity = entity;
            this.levels = levels;
        }

        public Player getEntity() {
            return entity;
        }

        public int getLevels() {
            return levels;
        }
    }

    public static final class XpPickup {
        private final Player entity;
        private final ExperienceOrb orb;

        public XpPickup(Player entity, ExperienceOrb orb) {
            this.entity = entity;
            this.orb = orb;
        }

        public Player getEntity() {
            return entity;
        }

        public ExperienceOrb getOrb() {
            return orb;
        }
    }

    public static final class ServerChat {
        private final ServerPlayer player;
        private final String rawText;

        public ServerChat(ServerPlayer player, String rawText) {
            this.player = player;
            this.rawText = rawText;
        }

        public ServerPlayer getPlayer() {
            return player;
        }

        public String getRawText() {
            return rawText;
        }
    }

    public static final class Trade {
        private final Player entity;
        private final AbstractVillager abstractVillager;
        private final MerchantOffer merchantOffer;

        public Trade(Player entity, AbstractVillager abstractVillager, MerchantOffer merchantOffer) {
            this.entity = entity;
            this.abstractVillager = abstractVillager;
            this.merchantOffer = merchantOffer;
        }

        public Player getEntity() {
            return entity;
        }

        public AbstractVillager getAbstractVillager() {
            return abstractVillager;
        }

        public MerchantOffer getMerchantOffer() {
            return merchantOffer;
        }
    }
}
