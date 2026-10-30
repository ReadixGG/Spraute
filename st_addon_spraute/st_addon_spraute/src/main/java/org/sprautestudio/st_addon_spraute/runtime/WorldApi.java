package org.sprautestudio.st_addon_spraute.runtime;

import org.sprautestudio.st_addon_spraute.core.world.Location;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Entities and blocks for stories: properties ({@code z.health}), changes ({@code set z.health = 50}),
 * methods ({@code z.kill()}), summoning and searching, reading and placing blocks.
 */
public final class WorldApi {
    private WorldApi() {
    }

    // ------------------------------------------------------------------ places

    public static ServerLevel level() {
        ServerPlayer p = SprauteRuntime.player();
        if (p != null) return p.serverLevel();
        MinecraftServer s = ServerLifecycleHooks.getCurrentServer();
        if (s == null) throw new SprauteRuntimeException("Мир не загружен");
        return s.overworld();
    }

    /** A point from a value: (x, y, z), an entity (its feet) or a location. */
    public static Vec3 vec(Object v) {
        if (v instanceof EntityRef r) return r.require().position();
        if (v instanceof Vec3 l) return l;
        if (v instanceof List<?> l && l.size() >= 3) return new Vec3(Eval.num(l.get(0)), Eval.num(l.get(1)), Eval.num(l.get(2)));
        throw new SprauteRuntimeException("Ожидалась точка (x, y, z) или сущность");
    }

    public static List<Object> list(Vec3 v) {
        List<Object> out = new ArrayList<>();
        out.add(v.x);
        out.add(v.y);
        out.add(v.z);
        return out;
    }

    private static ResourceLocation id(String s) {
        String t = s.trim().replace("\"", "");
        ResourceLocation rl = ResourceLocation.tryParse(t.contains(":") ? t : "minecraft:" + t);
        if (rl == null) throw new SprauteRuntimeException("Неверный id '" + s + "'");
        return rl;
    }

    // ------------------------------------------------------------------ entities: finding and making

    /** summon("minecraft:zombie", (x, y, z)): a new entity, set up like the /summon command does it. */
    public static EntityRef summon(String type, Object at) {
        ServerLevel level = level();
        EntityType<?> et = BuiltInRegistries.ENTITY_TYPE.getOptional(id(type))
                .orElseThrow(() -> new SprauteRuntimeException("Нет такой сущности: " + type));
        Vec3 p = vec(at);
        Entity e = et.create(level);
        if (e == null) throw new SprauteRuntimeException("Сущность " + type + " нельзя создать");
        e.moveTo(p.x, p.y, p.z, level.random.nextFloat() * 360, 0);
        if (e instanceof Mob m) {
            m.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(p)), MobSpawnType.COMMAND, null, null);
            m.setPersistenceRequired();
        }
        level.addFreshEntity(e);
        return new EntityRef(e);
    }

    private static boolean typeMatches(Entity e, String type) {
        String t = type.trim().replace("\"", "");
        if (t.isEmpty() || t.equals("any")) return true;
        if (t.equals("player")) return e instanceof Player;
        if (t.equals("mob")) return e instanceof LivingEntity && !(e instanceof Player);
        String full = t.contains(":") ? t : "minecraft:" + t;
        return String.valueOf(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType())).equals(full);
    }

    /** entities("minecraft:zombie", center, radius): every such entity nearby, nearest first. */
    public static List<Object> entities(String type, Object center, double radius) {
        Vec3 c = vec(center);
        double r = Math.max(0.5, Math.min(256, radius));
        List<Entity> found = level().getEntities((Entity) null, new AABB(c, c).inflate(r),
                e -> e.isAlive() && typeMatches(e, type) && e.position().distanceTo(c) <= r);
        found.sort((a, b) -> Double.compare(a.position().distanceToSqr(c), b.position().distanceToSqr(c)));
        List<Object> out = new ArrayList<>();
        for (Entity e : found) out.add(new EntityRef(e));
        return out;
    }

    public static Object nearest(String type, Object center, double radius) {
        List<Object> l = entities(type, center, radius);
        return l.isEmpty() ? "" : l.get(0);
    }

    // ------------------------------------------------------------------ entities: properties

    public static Object get(EntityRef ref, String name) {
        if (name.equals("alive")) return ref.alive();
        if (name.equals("type")) return ref.type;
        if (name.equals("uuid")) return ref.uuid.toString();
        Entity e = ref.require();
        LivingEntity le = e instanceof LivingEntity l ? l : null;
        switch (name) {
            case "pos", "position" -> {
                return list(e.position());
            }
            case "x" -> {
                return e.getX();
            }
            case "y" -> {
                return e.getY();
            }
            case "z" -> {
                return e.getZ();
            }
            case "eyes" -> {
                return list(e.getEyePosition());
            }
            case "look" -> {
                return list(e.getLookAngle());
            }
            case "yaw" -> {
                return (double) e.getYRot();
            }
            case "pitch" -> {
                return (double) e.getXRot();
            }
            case "speed", "velocity" -> {
                return list(e.getDeltaMovement());
            }
            case "inair" -> {
                return !e.onGround();
            }
            case "flying" -> {
                return e instanceof Player p ? p.getAbilities().flying : !e.onGround() && e.isNoGravity();
            }
            case "canfly" -> {
                return e instanceof Player p && p.getAbilities().mayfly;
            }
            case "flyspeed" -> {
                return e instanceof Player p ? (double) p.getAbilities().getFlyingSpeed() : 0.0;
            }
            case "movespeed" -> {
                AttributeInstance ai = le != null ? le.getAttribute(Attributes.MOVEMENT_SPEED) : null;
                return ai != null ? ai.getBaseValue() : 0.0;
            }
            case "falldistance" -> {
                return (double) e.fallDistance;
            }
            case "fallsafe" -> {
                return Mechanics.fallSafeLeft(e);
            }
            case "undying" -> {
                return Mechanics.undying(e);
            }
            case "onground" -> {
                return e.onGround();
            }
            case "name" -> {
                return e.getName().getString();
            }
            case "id" -> {
                return (double) e.getId();
            }
            case "health", "hp" -> {
                return le != null ? (double) le.getHealth() : 0.0;
            }
            case "maxhealth", "maxhp" -> {
                return le != null ? (double) le.getMaxHealth() : 0.0;
            }
            case "target" -> {
                return e instanceof Mob m && m.getTarget() != null ? new EntityRef(m.getTarget()) : "";
            }
            case "item", "hand" -> {
                return le != null ? String.valueOf(BuiltInRegistries.ITEM.getKey(le.getMainHandItem().getItem())) : "";
            }
            case "sneaking" -> {
                return e.isShiftKeyDown();
            }
            case "sprinting" -> {
                return e.isSprinting();
            }
            case "level" -> {
                return e instanceof Player p ? (double) p.experienceLevel : 0.0;
            }
            case "food" -> {
                return e instanceof Player p ? (double) p.getFoodData().getFoodLevel() : 0.0;
            }
            case "dimension" -> {
                return e.level().dimension().location().toString();
            }
            case "fire" -> {
                return e.isOnFire();
            }
            case "glowing" -> {
                return e.hasGlowingTag();
            }
            case "invisible" -> {
                return e.isInvisible();
            }
            case "dist", "distance" -> {
                ServerPlayer p = SprauteRuntime.player();
                return p != null ? e.position().distanceTo(p.position()) : -1.0;
            }
            default -> throw new SprauteRuntimeException("У сущности нет свойства ." + name + ". Есть: pos, x, y, z, eyes, look, yaw, pitch, speed, "
                    + "onground, name, type, health, maxhealth, target, item, alive, dist, sneaking, sprinting, level, food, dimension, fire, glowing, invisible");
        }
    }

    public static void set(EntityRef ref, String name, Object v) {
        Entity e = ref.require();
        LivingEntity le = e instanceof LivingEntity l ? l : null;
        switch (name) {
            case "health", "hp" -> {
                if (le != null) le.setHealth((float) Math.max(0, Eval.num(v)));
            }
            case "lockbody" -> {
                if (e instanceof org.sprautestudio.st_addon_spraute.core.entity.AnimatedEntity ae) ae.setBodyLocked(Eval.truthy(v));
            }
            case "lockhead" -> {
                if (e instanceof org.sprautestudio.st_addon_spraute.core.entity.AnimatedEntity ae) ae.setHeadLocked(Eval.truthy(v));
            }
            case "noturn" -> {
                if (e instanceof org.sprautestudio.st_addon_spraute.core.entity.AnimatedEntity ae) ae.setNoTurn(Eval.truthy(v));
            }
            case "holdchunk" -> {
                if (e instanceof org.sprautestudio.st_addon_spraute.core.entity.AnimatedEntity ae) ae.setHoldsChunk(Eval.truthy(v));
            }
            case "blink" -> {
                if (e instanceof org.sprautestudio.st_addon_spraute.core.entity.AnimatedEntity ae) ae.blinks = Eval.truthy(v);
            }
            case "maxhealth", "maxhp" -> {
                if (e instanceof org.sprautestudio.st_addon_spraute.core.entity.AnimatedEntity ae) {
                    ae.setMaxHealth((float) Math.max(1, Eval.num(v)));
                    return;
                }
                AttributeInstance a = le != null ? le.getAttribute(Attributes.MAX_HEALTH) : null;
                if (a != null) {
                    a.setBaseValue(Math.max(1, Eval.num(v)));
                    le.setHealth(le.getMaxHealth());
                }
            }
            case "name" -> {
                String s = Eval.str(v);
                e.setCustomName(s.isEmpty() ? null : Component.literal(s));
                e.setCustomNameVisible(!s.isEmpty());
            }
            case "pos", "position" -> {
                Vec3 p = vec(v);
                e.teleportTo(p.x, p.y, p.z);
            }
            case "x", "y", "z" -> {
                double n = Eval.num(v);
                e.teleportTo(name.equals("x") ? n : e.getX(), name.equals("y") ? n : e.getY(), name.equals("z") ? n : e.getZ());
            }
            case "yaw" -> {
                e.setYRot((float) Eval.num(v));
                e.setYHeadRot((float) Eval.num(v));
            }
            case "speed", "velocity" -> {
                e.setDeltaMovement(vec(v));
                e.hurtMarked = true;
            }
            case "target" -> {
                if (!(e instanceof Mob m)) throw new SprauteRuntimeException(".target — только у мобов");
                m.setTarget(v instanceof EntityRef r && r.get() instanceof LivingEntity t ? t : null);
            }
            case "ai" -> {
                if (e instanceof Mob m) m.setNoAi(!Eval.truthy(v));
            }
            case "glowing" -> e.setGlowingTag(Eval.truthy(v));
            case "invisible" -> e.setInvisible(Eval.truthy(v));
            case "gravity" -> e.setNoGravity(!Eval.truthy(v));
            case "canfly" -> {
                if (!(e instanceof Player p)) throw new SprauteRuntimeException(".canfly — только у игрока");
                p.getAbilities().mayfly = Eval.truthy(v);
                if (!Eval.truthy(v) && !p.isCreative() && !p.isSpectator()) p.getAbilities().flying = false;
                p.onUpdateAbilities();
            }
            case "flying" -> {
                if (!(e instanceof Player p)) throw new SprauteRuntimeException(".flying — только у игрока");
                boolean on = Eval.truthy(v);
                if (on) p.getAbilities().mayfly = true;
                p.getAbilities().flying = on;
                p.onUpdateAbilities();
            }
            case "flyspeed" -> {
                if (!(e instanceof Player p)) throw new SprauteRuntimeException(".flyspeed — только у игрока");
                p.getAbilities().setFlyingSpeed((float) Math.max(0, Math.min(1, Eval.num(v))));
                p.onUpdateAbilities();
            }
            case "movespeed" -> {
                AttributeInstance ai = e instanceof LivingEntity l2 ? l2.getAttribute(Attributes.MOVEMENT_SPEED) : null;
                if (ai == null) throw new SprauteRuntimeException(".movespeed — только у живых");
                ai.setBaseValue(Math.max(0, Eval.num(v)));
            }
            case "falldistance" -> e.fallDistance = (float) Math.max(0, Eval.num(v));
            case "fallsafe" -> Mechanics.fallSafe(e, Eval.num(v));
            case "undying" -> Mechanics.setUndying(e, Eval.truthy(v));
            case "silent" -> e.setSilent(Eval.truthy(v));
            case "invulnerable" -> e.setInvulnerable(Eval.truthy(v));
            case "fire" -> e.setSecondsOnFire((int) Math.round(Eval.num(v)));
            case "item", "hand" -> {
                if (le != null) le.setItemSlot(EquipmentSlot.MAINHAND, stack(Eval.str(v), 1));
            }
            case "level" -> {
                if (e instanceof ServerPlayer p) p.setExperienceLevels((int) Eval.num(v));
            }
            case "food" -> {
                if (e instanceof Player p) p.getFoodData().setFoodLevel((int) Eval.num(v));
            }
            default -> throw new SprauteRuntimeException("Свойство ." + name + " нельзя изменить. Можно: health, maxhealth, name, pos, x, y, z, yaw, "
                    + "speed, target, ai, glowing, invisible, gravity, silent, invulnerable, fire, item, level, food");
        }
    }

    private static ItemStack stack(String item, int count) {
        if (item.isBlank() || item.equals("air")) return ItemStack.EMPTY;
        if (ItemSpec.error(item) != null) throw new SprauteRuntimeException("Предмет " + item + ": ошибка в NBT: " + ItemSpec.error(item));
        ItemStack s = ItemSpec.stack(item, count);
        if (s.isEmpty()) throw new SprauteRuntimeException("Нет такого предмета: " + item);
        return s;
    }

    private static EquipmentSlot slot(String s) {
        return switch (s.trim().toLowerCase()) {
            case "offhand", "left" -> EquipmentSlot.OFFHAND;
            case "head", "helmet" -> EquipmentSlot.HEAD;
            case "chest", "chestplate" -> EquipmentSlot.CHEST;
            case "legs", "leggings" -> EquipmentSlot.LEGS;
            case "feet", "boots" -> EquipmentSlot.FEET;
            default -> EquipmentSlot.MAINHAND;
        };
    }

    private static Object arg(List<Object> a, int i, Object def) {
        return i < a.size() ? a.get(i) : def;
    }

    public static Object call(EntityRef ref, String name, List<Object> a) {
        if (name.equals("alive")) return ref.alive();
        Entity e = ref.require();
        LivingEntity le = e instanceof LivingEntity l ? l : null;
        switch (name) {
            case "kill" -> e.kill();
            case "remove", "despawn" -> {
                if (e instanceof Player) throw new SprauteRuntimeException("Игрока нельзя убрать из мира");
                e.discard();
            }
            case "tp", "teleport" -> {
                Vec3 p = vec(arg(a, 0, ""));
                e.teleportTo(p.x, p.y, p.z);
            }
            case "damage", "hurt" -> e.hurt(e.damageSources().generic(), (float) Eval.num(arg(a, 0, 1.0)));
            case "heal" -> {
                if (le != null) le.heal((float) Eval.num(arg(a, 0, 1.0)));
            }
            case "effect" -> {
                if (le == null) break;
                MobEffect eff = BuiltInRegistries.MOB_EFFECT.getOptional(id(Eval.str(arg(a, 0, ""))))
                        .orElseThrow(() -> new SprauteRuntimeException("Нет такого эффекта: " + arg(a, 0, "")));
                int seconds = (int) Eval.num(arg(a, 1, 10.0)), lvl = (int) Eval.num(arg(a, 2, 1.0));
                le.addEffect(new MobEffectInstance(eff, Math.max(1, seconds * 20), Math.max(0, lvl - 1)));
            }
            case "cleareffects" -> {
                if (le != null) le.removeAllEffects();
            }
            case "push" -> {
                e.addDeltaMovement(vec(arg(a, 0, "")));
                e.hurtMarked = true;
            }
            case "dash" -> {
                // a burst along the view, flat (dash(1.5))
                double p = Eval.num(arg(a, 0, 1.0));
                Vec3 look = e.getViewVector(1);
                Vec3 flat = new Vec3(look.x, 0, look.z);
                if (flat.lengthSqr() > 1e-6) flat = flat.normalize();
                e.setDeltaMovement(flat.x * p, Math.max(e.getDeltaMovement().y, 0.15), flat.z * p);
                e.hurtMarked = true;
            }
            case "launch" -> {
                // straight up (launch(1) — about 5 blocks)
                Vec3 m = e.getDeltaMovement();
                e.setDeltaMovement(m.x, Eval.num(arg(a, 0, 1.0)), m.z);
                e.hurtMarked = true;
            }
            case "knockback" -> {
                // away from a point or an entity (knockback(player, 1.5))
                Object from = arg(a, 0, "");
                Vec3 c = from instanceof EntityRef r ? r.require().position() : vec(from);
                Vec3 d = e.position().subtract(c);
                d = new Vec3(d.x, 0, d.z);
                if (d.lengthSqr() < 1e-6) d = new Vec3(0, 0, 1);
                double p = Eval.num(arg(a, 1, 1.0));
                d = d.normalize().scale(p);
                e.push(d.x, 0.3 * p + 0.1, d.z);
                e.hurtMarked = true;
            }
            case "pull" -> {
                // toward a point or an entity (pull(player, 1))
                Object to = arg(a, 0, "");
                Vec3 c = to instanceof EntityRef r ? r.require().position() : vec(to);
                Vec3 d = c.subtract(e.position());
                if (d.lengthSqr() < 1e-6) return ref;
                double p = Eval.num(arg(a, 1, 1.0));
                d = d.normalize().scale(p);
                e.push(d.x, d.y * 0.5 + 0.15, d.z);
                e.hurtMarked = true;
            }
            case "stop" -> {
                e.setDeltaMovement(Vec3.ZERO);
                e.fallDistance = 0;
                e.hurtMarked = true;
            }
            case "lookat" -> e.lookAt(EntityAnchorArgument.Anchor.EYES, a.isEmpty() ? e.position() :
                    arg(a, 0, "") instanceof EntityRef r ? r.require().getEyePosition() : vec(arg(a, 0, "")));
            case "give" -> {
                if (!(e instanceof Player p)) throw new SprauteRuntimeException(".give() — только у игрока");
                ItemStack s = stack(Eval.str(arg(a, 0, "")), (int) Eval.num(arg(a, 1, 1.0)));
                if (!p.addItem(s)) p.drop(s, false);
            }
            case "equip" -> {
                if (le != null) le.setItemSlot(slot(Eval.str(arg(a, 0, "mainhand"))), stack(Eval.str(arg(a, 1, "")), 1));
            }
            case "distance", "dist" -> {
                return e.position().distanceTo(vec(arg(a, 0, "")));
            }
            case "has" -> {
                // has("minecraft:diamond") — the player carries it
                if (!(e instanceof Player p)) return false;
                String item = Eval.str(arg(a, 0, ""));
                stack(item, 1);
                return ItemSpec.count(p, item) >= (int) Eval.num(arg(a, 1, 1.0));
            }
            case "count" -> {
                if (!(e instanceof Player p)) return 0.0;
                String item = Eval.str(arg(a, 0, ""));
                stack(item, 1);
                return (double) ItemSpec.count(p, item);
            }
            default -> throw new SprauteRuntimeException("У сущности нет метода ." + name + "(). Есть: kill, remove, tp, damage, heal, effect, "
                    + "cleareffects, push, dash, launch, knockback, pull, stop, lookat, give, equip, distance, has, count");
        }
        return ref;
    }

    // ------------------------------------------------------------------ what commands change, read back

    /** How many of this item the player carries. */
    public static double itemCount(String item) {
        ServerPlayer p = SprauteRuntime.player();
        if (p == null) return 0;
        stack(item, 1);
        return ItemSpec.count(p, item);
    }

    /** Gives the player items; how many were given. */
    public static double give(String item, int count) {
        ServerPlayer p = SprauteRuntime.player();
        if (p == null || count <= 0) return 0;
        ItemStack s = stack(item, count);
        if (!p.getInventory().add(s.copy())) p.drop(s.copy(), false);
        return count;
    }

    /** Takes items from the player; how many were taken. */
    public static double take(String item, int count) {
        ServerPlayer p = SprauteRuntime.player();
        if (p == null || count <= 0) return 0;
        stack(item, 1);
        return ItemSpec.take(p, item, count);
    }

    /** The item in the main hand of an entity (player, character, mob). */
    public static String heldItem(Object who) {
        if (!(who instanceof EntityRef r) || !(r.require() instanceof LivingEntity le)) return "minecraft:air";
        return String.valueOf(BuiltInRegistries.ITEM.getKey(le.getMainHandItem().getItem()));
    }

    /** An effect acting on an entity, or null. */
    public static MobEffectInstance effect(Object who, String effect) {
        if (!(who instanceof EntityRef r) || !r.alive() || !(r.require() instanceof LivingEntity le)) return null;
        MobEffect eff = BuiltInRegistries.MOB_EFFECT.getOptional(id(effect))
                .orElseThrow(() -> new SprauteRuntimeException("Нет такого эффекта: " + effect));
        return le.getEffect(eff);
    }

    public static String weather() {
        ServerLevel l = level();
        return l.isThundering() ? "thunder" : l.isRaining() ? "rain" : "clear";
    }

    public static String biome(Object at) {
        return level().getBiome(pos(at)).unwrapKey().map(k -> k.location().toString()).orElse("");
    }

    public static double light(Object at) {
        return level().getMaxLocalRawBrightness(pos(at));
    }

    public static boolean sky(Object at) {
        return level().canSeeSky(pos(at));
    }

    // ------------------------------------------------------------------ blocks

    private static BlockPos pos(Object at) {
        return BlockPos.containing(vec(at));
    }

    public static String block(Object at) {
        return String.valueOf(BuiltInRegistries.BLOCK.getKey(level().getBlockState(pos(at)).getBlock()));
    }

    public static Object blockProp(Object at, String prop) {
        BlockState s = level().getBlockState(pos(at));
        for (Property<?> p : s.getProperties()) {
            if (p.getName().equals(prop)) return value(s, p);
        }
        return "";
    }

    private static <T extends Comparable<T>> Object value(BlockState s, Property<T> p) {
        T v = s.getValue(p);
        if (v instanceof Boolean b) return b;
        if (v instanceof Integer i) return (double) i;
        return p.getName(v);
    }

    /** setblock pos "minecraft:lever[facing=south]": the block with its states, like /setblock. */
    public static void setBlock(Object at, String block) {
        ServerLevel level = level();
        String b = block.trim().replace("\"", "");
        if (!b.contains(":") && !b.startsWith("[")) b = "minecraft:" + b;
        BlockState state;
        try {
            state = BlockStateParser.parseForBlock(level.registryAccess().lookupOrThrow(Registries.BLOCK), b, false).blockState();
        } catch (Exception ex) {
            throw new SprauteRuntimeException("Не получилось разобрать блок '" + block + "': " + ex.getMessage());
        }
        level.setBlock(pos(at), state, 3);
    }

    /** setprop pos "facing" "south": changes one state of the block that is there. */
    private static final com.mojang.authlib.GameProfile USER = new com.mojang.authlib.GameProfile(java.util.UUID.nameUUIDFromBytes("spraute:use".getBytes()), "[Spraute]");

    /** A right click on a block by a player or a character. */
    public static void useBlock(Entity who, Object at) {
        ServerLevel level = who.level() instanceof ServerLevel l ? l : level();
        BlockPos p = pos(at);
        BlockState s = level.getBlockState(p);
        if (s.isAir()) return;
        Vec3 from = who.getEyePosition();
        Vec3 middle = Vec3.atCenterOf(p);
        var face = net.minecraft.core.Direction.getNearest(from.x - middle.x, from.y - middle.y, from.z - middle.z);
        var hit = new net.minecraft.world.phys.BlockHitResult(middle, face, p, false);
        var hand = net.minecraft.world.InteractionHand.MAIN_HAND;
        if (who instanceof LivingEntity le) le.swing(hand, true);
        // a block plays its sound to everyone but the one who clicked: his client is supposed to have played it for him.
        // Here no client clicked, so what only moves in the world is clicked by a stand-in, and the player hears it too.
        var block = s.getBlock();
        boolean worldOnly = block instanceof net.minecraft.world.level.block.DoorBlock || block instanceof net.minecraft.world.level.block.TrapDoorBlock
                || block instanceof net.minecraft.world.level.block.FenceGateBlock || block instanceof net.minecraft.world.level.block.ButtonBlock
                || block instanceof net.minecraft.world.level.block.LeverBlock;
        if (who instanceof ServerPlayer player && !worldOnly) {
            s.use(level, player, hand, hit);
            return;
        }
        var user = net.minecraftforge.common.util.FakePlayerFactory.get(level, USER);
        user.moveTo(who.getX(), who.getY(), who.getZ(), who.getYRot(), who.getXRot());
        user.setItemInHand(hand, who instanceof LivingEntity le ? le.getMainHandItem().copy() : ItemStack.EMPTY);
        try {
            s.use(level, user, hand, hit);
        } finally {
            user.setItemInHand(hand, ItemStack.EMPTY);
        }
    }

    public static void setProp(Object at, String prop, Object value) {
        ServerLevel level = level();
        BlockPos p = pos(at);
        BlockState s = level.getBlockState(p);
        for (Property<?> pr : s.getProperties()) {
            if (pr.getName().equals(prop)) {
                level.setBlock(p, with(s, pr, value), 3);
                return;
            }
        }
        throw new SprauteRuntimeException("У блока " + block(at) + " нет свойства '" + prop + "'");
    }

    private static <T extends Comparable<T>> BlockState with(BlockState s, Property<T> p, Object value) {
        String v = value instanceof Double d && d == Math.rint(d) ? String.valueOf(d.longValue()) : Eval.str(value);
        Optional<T> parsed = p.getValue(v.toLowerCase());
        if (parsed.isEmpty()) {
            List<String> ok = new ArrayList<>();
            for (T t : p.getPossibleValues()) ok.add(p.getName(t));
            throw new SprauteRuntimeException("Свойство '" + p.getName() + "' не может быть '" + v + "'. Можно: " + String.join(", ", ok));
        }
        return s.setValue(p, parsed.get());
    }

    /** inbox(pos, corner1, corner2): the point is inside the box between two corners. */
    public static boolean inBox(Object at, Object a, Object b) {
        Vec3 p = vec(at), x = vec(a), y = vec(b);
        return p.x >= Math.min(x.x, y.x) && p.x <= Math.max(x.x, y.x) + 1 && p.y >= Math.min(x.y, y.y) && p.y <= Math.max(x.y, y.y) + 1
                && p.z >= Math.min(x.z, y.z) && p.z <= Math.max(x.z, y.z) + 1;
    }

    /**
     * The nearest place to stand at this x, z: going down from a little above the point to the first solid
     * floor with room for a body, or up out of the ground if the point is inside it. Centre of the block.
     */
    public static Vec3 ground(Vec3 p) {
        ServerLevel lv = level();
        int x = (int) Math.floor(p.x), z = (int) Math.floor(p.z), y0 = (int) Math.floor(p.y);
        // the real top of what can be stood on: slabs, paths, carpets, stairs too — not only full blocks
        java.util.function.IntFunction<Double> top = y -> {
            BlockPos at = new BlockPos(x, y, z);
            var shape = lv.getBlockState(at).getCollisionShape(lv, at);
            if (shape.isEmpty()) return null;
            double t = y + shape.max(net.minecraft.core.Direction.Axis.Y);
            // room for a body above: nothing solid from the feet up two blocks
            for (int k = 0; k < 2; k++) {
                BlockPos above = new BlockPos(x, (int) Math.floor(t + 0.01) + k, z);
                if (above.getY() == y) continue;
                if (!lv.getBlockState(above).getCollisionShape(lv, above).isEmpty()) return null;
            }
            return t;
        };
        for (int dy = 0; dy <= 27; dy++) {
            Double t = top.apply(y0 + 2 - dy);
            if (t != null) return new Vec3(x + 0.5, t, z + 0.5);
        }
        for (int dy = 3; dy <= 24; dy++) {
            Double t = top.apply(y0 + dy);
            if (t != null) return new Vec3(x + 0.5, t, z + 0.5);
        }
        return new Vec3(x + 0.5, p.y, z + 0.5);
    }

    public static Vec3 of(Location l) {
        return l;
    }
}
