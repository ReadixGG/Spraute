package org.zonarstudio.spraute_engine.script;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ClientboundSetExperiencePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import org.zonarstudio.spraute_engine.entity.ModelManager;
import org.zonarstudio.spraute_engine.entity.NpcManager;
import org.zonarstudio.spraute_engine.entity.SprauteNpcEntity;
import org.zonarstudio.spraute_engine.scene.SceneNodeView;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Get/set script-facing properties for lerp / tween (entity, node, map, player). */
public final class ScriptPropertyAccess {
    private ScriptPropertyAccess() {}

    public static String normKey(String key) {
        if (key == null) return "";
        String k = key.trim();
        if (k.isEmpty()) return "";
        // Keep camelCase tokens intact for known props used by scripts.
        return k;
    }

    public static Object get(Object target, String prop, CommandSourceStack source) {
        if (target == null || prop == null || prop.isBlank()) return null;
        String key = normKey(prop);

        if (target instanceof Map<?, ?> map) {
            Object v = map.get(key);
            if (v == null) v = map.get(key.toLowerCase(Locale.ROOT));
            return v;
        }
        if (target instanceof SceneNodeView node) {
            return node.getProperty(key.toLowerCase(Locale.ROOT), source);
        }
        if (target instanceof Player player) {
            return readPlayer(player, key, source);
        }
        Entity entity = resolveEntity(target, source);
        if (entity != null) return readEntity(entity, key);
        return null;
    }

    public static boolean set(Object target, String prop, Object value, CommandSourceStack source) {
        if (target == null || prop == null || prop.isBlank()) return false;
        String key = normKey(prop);

        if (target instanceof Map<?, ?> raw) {
            @SuppressWarnings("unchecked")
            Map<Object, Object> map = (Map<Object, Object>) raw;
            map.put(key, value);
            return true;
        }
        if (target instanceof SceneNodeView node) {
            return node.setProperty(key.toLowerCase(Locale.ROOT), value, source);
        }
        if (target instanceof Player player) {
            return writePlayer(player, key, value);
        }
        Entity entity = resolveEntity(target, source);
        return entity != null && writeEntity(entity, key, value);
    }

    public static String tweenKey(Object target, String prop) {
        String p = prop == null ? "" : prop.toLowerCase(Locale.ROOT);
        if (target instanceof SceneNodeView n) {
            return "node:" + n.getSceneId() + ":" + n.getNodeId() + ":" + p;
        }
        if (target instanceof Entity e) {
            return "ent:" + e.getUUID() + ":" + p;
        }
        if (target instanceof String s) {
            return "id:" + s + ":" + p;
        }
        if (target instanceof Map<?, ?>) {
            return "map:" + System.identityHashCode(target) + ":" + p;
        }
        return "obj:" + System.identityHashCode(target) + ":" + p;
    }

    public static Entity resolveEntity(Object target, CommandSourceStack source) {
        if (target instanceof Entity e) return e;
        if (!(target instanceof String id) || source == null || source.getLevel() == null) return null;
        Entity e = NpcManager.getEntity(id, source.getLevel());
        if (e == null) e = ModelManager.getEntity(id, source.getLevel());
        return e;
    }

    /** Canonical player vitals for {@code getPlayerStats(player)} and property access. */
    public static Map<String, Object> playerStats(Player player) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (player == null) return out;
        out.put("name", player.getName().getString());
        out.put("uuid", player.getUUID().toString());
        out.put("hp", (double) player.getHealth());
        out.put("maxHp", (double) player.getMaxHealth());
        FoodData food = player.getFoodData();
        out.put("hunger", food.getFoodLevel());
        out.put("saturation", (double) food.getSaturationLevel());
        out.put("air", player.getAirSupply());
        out.put("maxAir", player.getMaxAirSupply());
        out.put("absorption", (double) player.getAbsorptionAmount());
        out.put("armor", player.getArmorValue());
        out.put("selectedSlot", player.getInventory().selected);
        out.put("xpLevel", player.experienceLevel);
        out.put("xpProgress", (double) player.experienceProgress);
        out.put("xpTotal", player.totalExperience);
        out.put("x", player.getX());
        out.put("y", player.getY());
        out.put("z", player.getZ());
        out.put("yaw", (double) player.getYRot());
        out.put("pitch", (double) player.getXRot());
        var look = player.getLookAngle();
        out.put("lookX", look.x);
        out.put("lookY", look.y);
        out.put("lookZ", look.z);
        out.put("sneaking", player.isShiftKeyDown());
        out.put("sprinting", player.isSprinting());
        out.put("onGround", org.zonarstudio.spraute_engine.compat.SprauteEntityCompat.onGround(player));
        out.put("swimming", player.isSwimming());
        out.put("alive", player.isAlive());
        out.put("onFire", player.isOnFire());
        out.put("fireTicks", player.getRemainingFireTicks());
        out.put("frozenTicks", player.getTicksFrozen());
        out.put("fallDistance", (double) player.fallDistance);
        out.put("flightEnabled", player.getAbilities().mayfly);
        out.put("flying", player.getAbilities().flying);
        out.put("flySpeed", (double) player.getAbilities().getFlyingSpeed());
        out.put("walkSpeed", (double) player.getAbilities().getWalkingSpeed());
        out.put("movementSpeed", org.zonarstudio.spraute_engine.compat.SprautePlayerCompat.getAttributeValue(
                player, org.zonarstudio.spraute_engine.compat.SprautePlayerCompat.movementSpeedAttr()));
        out.put("jumpStrength", player instanceof ServerPlayer sp
                ? (double) PlayerJumpStrengthOverrides.get(sp)
                : (double) PlayerJumpStrengthOverrides.VANILLA);
        out.put("stepHeight", (double) org.zonarstudio.spraute_engine.compat.SprautePlayerCompat.getStepHeight(player));
        out.put("attackDamage", org.zonarstudio.spraute_engine.compat.SprautePlayerCompat.getAttributeValue(
                player, org.zonarstudio.spraute_engine.compat.SprautePlayerCompat.attackDamageAttr()));
        out.put("blockReach", org.zonarstudio.spraute_engine.compat.SprautePlayerCompat.getBlockReach(player));
        out.put("invulnerable", player.getAbilities().invulnerable);
        out.put("instabuild", player.getAbilities().instabuild);
        if (player instanceof ServerPlayer sp) {
            out.put("gamemode", sp.gameMode.getGameModeForPlayer().getName());
            out.put("ping", sp.latency);
            out.put("flightEnabled", PlayerFlightOverrides.get(sp));
            out.put("healthEnabled", !PlayerVitalsOverrides.isHealthDisabled(sp));
            out.put("hungerEnabled", !PlayerVitalsOverrides.isHungerDisabled(sp));
            out.put("xpVisible", !PlayerVitalsOverrides.isXpHudDisabled(sp));
            out.put("digSpeed", PlayerDigSpeedOverrides.get(sp));
        }
        return out;
    }

    private static Object readPlayer(Player player, String prop, CommandSourceStack source) {
        String key = playerKey(prop);
        FoodData food = player.getFoodData();
        return switch (key) {
            case "name" -> player.getName().getString();
            case "uuid" -> player.getUUID().toString();
            case "id", "nodeid", "node_id" -> {
                var node = org.zonarstudio.spraute_engine.scene.ScenePlayerNodes.find(player.getUUID().toString());
                yield node == null ? "" : node.id;
            }
            case "node" -> org.zonarstudio.spraute_engine.scene.SceneRuntime.nodeViewFromEntity(player);
            case "sceneid", "scene_id" -> {
                String sid = org.zonarstudio.spraute_engine.scene.SceneRuntime.sceneIdFromEntity(player);
                if (sid != null && !sid.isEmpty()) yield sid;
                var node = org.zonarstudio.spraute_engine.scene.ScenePlayerNodes.find(player.getUUID().toString());
                if (node == null) yield "";
                var view = org.zonarstudio.spraute_engine.scene.SceneRuntime.nodeViewFromEntity(player);
                yield view == null ? "" : view.getSceneId();
            }
            case "x" -> player.getX();
            case "y" -> player.getY();
            case "z" -> player.getZ();
            case "yaw" -> player.getYRot();
            case "pitch" -> player.getXRot();
            case "lookx" -> player.getLookAngle().x;
            case "looky" -> player.getLookAngle().y;
            case "lookz" -> player.getLookAngle().z;
            case "hp", "health" -> player.getHealth();
            case "maxhp", "maxhealth" -> player.getMaxHealth();
            case "hunger", "food", "foodlevel" -> food.getFoodLevel();
            case "saturation" -> food.getSaturationLevel();
            case "air", "oxygen", "airsupply" -> player.getAirSupply();
            case "maxair", "maxoxygen" -> player.getMaxAirSupply();
            case "absorption" -> player.getAbsorptionAmount();
            case "armor", "armorvalue" -> player.getArmorValue();
            case "selectedslot", "slot", "hotbarslot", "heldslot" -> player.getInventory().selected;
            case "xplevel", "level" -> player.experienceLevel;
            case "xp", "xpprogress", "experience" -> player.experienceProgress;
            case "xptotal", "totalxp", "totalexperience" -> player.totalExperience;
            case "fireticks", "fire" -> player.getRemainingFireTicks();
            case "frozenticks", "frozen" -> player.getTicksFrozen();
            case "falldistance" -> player.fallDistance;
            case "issneaking", "sneaking" -> player.isShiftKeyDown();
            case "iscrouching", "crouching" -> player.isCrouching();
            case "issprinting", "sprinting" -> player.isSprinting();
            case "onground", "isonground" -> org.zonarstudio.spraute_engine.compat.SprauteEntityCompat.onGround(player);
            case "isswimming", "swimming" -> player.isSwimming();
            case "issleeping", "sleeping" -> player.isSleeping();
            case "isinwater", "inwater" -> player.isInWater();
            case "isonfire", "onfire" -> player.isOnFire();
            case "isalive", "alive" -> player.isAlive();
            case "language", "locale" -> ScriptLangCatalog.languageOf(player);
            case "lang", "langshort", "languageshort", "shortlang" ->
                    ScriptLangCatalog.shortLang(ScriptLangCatalog.languageOf(player));
            case "isenglish", "english" -> ScriptLangCatalog.isEnglish(player);
            case "camera", "cam" -> player instanceof ServerPlayer sp
                    ? org.zonarstudio.spraute_engine.scene.PlayerCameraNodes.viewFor(sp) : null;
            case "audio", "soundbus", "sound_bus" -> player instanceof ServerPlayer sp
                    ? org.zonarstudio.spraute_engine.scene.PlayerAudioNodes.viewFor(sp) : null;
            case "gamemode", "gametype" -> player instanceof ServerPlayer sp
                    ? sp.gameMode.getGameModeForPlayer().getName() : "";
            case "ping", "latency" -> player instanceof ServerPlayer sp ? sp.latency : 0;
            case "flightenabled", "mayfly" -> player instanceof ServerPlayer sp
                    ? PlayerFlightOverrides.get(sp) : player.getAbilities().mayfly;
            case "lockperspective", "lockf5", "lockcameratype", "lockcameramode", "perspectivelocked" ->
                    player instanceof ServerPlayer sp && PlayerControlOverrides.isPerspectiveLocked(sp);
            case "lockforward", "lockfwd" ->
                    player instanceof ServerPlayer sp && PlayerControlOverrides.isMoveDirLocked(sp, PlayerMoveDirs.FORWARD);
            case "lockback", "lockbackward" ->
                    player instanceof ServerPlayer sp && PlayerControlOverrides.isMoveDirLocked(sp, PlayerMoveDirs.BACK);
            case "lockleft", "lockstrafeleft" ->
                    player instanceof ServerPlayer sp && PlayerControlOverrides.isMoveDirLocked(sp, PlayerMoveDirs.LEFT);
            case "lockright", "lockstraferight" ->
                    player instanceof ServerPlayer sp && PlayerControlOverrides.isMoveDirLocked(sp, PlayerMoveDirs.RIGHT);
            case "locknorth" ->
                    player instanceof ServerPlayer sp && PlayerControlOverrides.isMoveDirLocked(sp, PlayerMoveDirs.NORTH);
            case "locksouth" ->
                    player instanceof ServerPlayer sp && PlayerControlOverrides.isMoveDirLocked(sp, PlayerMoveDirs.SOUTH);
            case "lockeast" ->
                    player instanceof ServerPlayer sp && PlayerControlOverrides.isMoveDirLocked(sp, PlayerMoveDirs.EAST);
            case "lockwest" ->
                    player instanceof ServerPlayer sp && PlayerControlOverrides.isMoveDirLocked(sp, PlayerMoveDirs.WEST);
            case "lockmovedirs", "movedirs", "lockwalkdirs" -> player instanceof ServerPlayer sp
                    ? PlayerMoveDirs.toNames(PlayerControlOverrides.moveDirMask(sp)) : "";
            case "flying" -> player.getAbilities().flying;
            case "flyspeed" -> player.getAbilities().getFlyingSpeed();
            case "walkspeed" -> player.getAbilities().getWalkingSpeed();
            case "movementspeed" -> org.zonarstudio.spraute_engine.compat.SprautePlayerCompat.getAttributeValue(
                    player, org.zonarstudio.spraute_engine.compat.SprautePlayerCompat.movementSpeedAttr());
            case "jumpstrength" -> player instanceof ServerPlayer sp
                    ? (double) PlayerJumpStrengthOverrides.get(sp)
                    : (double) PlayerJumpStrengthOverrides.VANILLA;
            case "stepheight" -> (double) org.zonarstudio.spraute_engine.compat.SprautePlayerCompat.getStepHeight(player);
            case "attackdamage" -> org.zonarstudio.spraute_engine.compat.SprautePlayerCompat.getAttributeValue(
                    player, org.zonarstudio.spraute_engine.compat.SprautePlayerCompat.attackDamageAttr());
            case "digspeed" -> player instanceof ServerPlayer sp ? PlayerDigSpeedOverrides.get(sp) : 1.0;
            case "blockreach", "reach", "placereach", "blockinteractionrange" ->
                    org.zonarstudio.spraute_engine.compat.SprautePlayerCompat.getBlockReach(player);
            case "invulnerable" -> player.getAbilities().invulnerable;
            case "instabuild", "infinitebuild" -> player.getAbilities().instabuild;
            case "healthenabled" -> !(player instanceof ServerPlayer sp)
                    || !PlayerVitalsOverrides.isHealthDisabled(sp);
            case "hungerenabled" -> !(player instanceof ServerPlayer sp)
                    || !PlayerVitalsOverrides.isHungerDisabled(sp);
            case "xpvisible", "xphud", "xpbar", "experiencevisible", "experiencehud", "xpenabled" ->
                    !(player instanceof ServerPlayer sp) || !PlayerVitalsOverrides.isXpHudDisabled(sp);
            case "java" -> player;
            case "skin", "texture", "playerskin", "player_skin" ->
                    org.zonarstudio.spraute_engine.scene.PlayerSkinOps.baseSkin(player.getUUID());
            case "data" -> ScriptManager.getInstance().getPlayerSessionData(player.getUUID());
            case "saveddata" -> {
                if (source == null || source.getLevel() == null || source.getLevel().getServer() == null) yield null;
                yield new ScriptExecutor.PlayerSavedDataMap(
                        player.getUUID(), source.getLevel().getServer(), source.getLevel());
            }
            default -> org.zonarstudio.spraute_engine.scene.PlayerSkinLayers.getProperty(
                    player.getUUID(), key);
        };
    }

    private static boolean writePlayer(Player player, String prop, Object value) {
        String key = playerKey(prop);
        if (isIdentityProp(key)) return false;
        switch (key) {
            case "x" -> {
                Double n = toDouble(value);
                if (n == null) return false;
                return applyPlayerPos(player, n, player.getY(), player.getZ());
            }
            case "y" -> {
                Double n = toDouble(value);
                if (n == null) return false;
                return applyPlayerPos(player, player.getX(), n, player.getZ());
            }
            case "z" -> {
                Double n = toDouble(value);
                if (n == null) return false;
                return applyPlayerPos(player, player.getX(), player.getY(), n);
            }
            case "yaw" -> {
                Double n = toDouble(value);
                if (n == null) return false;
                return applyPlayerLook(player, n.floatValue(), player.getXRot());
            }
            case "pitch" -> {
                Double n = toDouble(value);
                if (n == null) return false;
                return applyPlayerLook(player, player.getYRot(), n.floatValue());
            }
            case "lookx" -> {
                Double n = toDouble(value);
                if (n == null) return false;
                var look = player.getLookAngle();
                return applyLookDir(player, n, look.y, look.z);
            }
            case "looky" -> {
                Double n = toDouble(value);
                if (n == null) return false;
                var look = player.getLookAngle();
                return applyLookDir(player, look.x, n, look.z);
            }
            case "lookz" -> {
                Double n = toDouble(value);
                if (n == null) return false;
                var look = player.getLookAngle();
                return applyLookDir(player, look.x, look.y, n);
            }
            case "hp", "health" -> {
                Double n = toDouble(value);
                if (n == null) return false;
                player.setHealth(n.floatValue());
                if (player instanceof ServerPlayer sp) {
                    PlayerVitalsOverrides.captureHealth(sp, n.floatValue());
                }
            }
            case "maxhp", "maxhealth" -> {
                Double n = toDouble(value);
                if (n == null) return false;
                AttributeInstance attr = player.getAttribute(Attributes.MAX_HEALTH);
                if (attr == null) return false;
                attr.setBaseValue(n);
                if (player.getHealth() > player.getMaxHealth()) {
                    player.setHealth(player.getMaxHealth());
                }
            }
            case "hunger", "food", "foodlevel" -> {
                Double n = toDouble(value);
                if (n == null) return false;
                int food = Math.max(0, Math.min(20, n.intValue()));
                player.getFoodData().setFoodLevel(food);
                if (player instanceof ServerPlayer sp) {
                    PlayerVitalsOverrides.captureHunger(sp, food, player.getFoodData().getSaturationLevel());
                }
            }
            case "saturation" -> {
                Double n = toDouble(value);
                if (n == null) return false;
                float sat = Math.max(0f, n.floatValue());
                player.getFoodData().setSaturation(sat);
                if (player instanceof ServerPlayer sp) {
                    PlayerVitalsOverrides.captureHunger(sp, player.getFoodData().getFoodLevel(), sat);
                }
            }
            case "air", "oxygen", "airsupply" -> {
                Double n = toDouble(value);
                if (n == null) return false;
                player.setAirSupply(Math.max(0, n.intValue()));
            }
            case "absorption" -> {
                Double n = toDouble(value);
                if (n == null) return false;
                player.setAbsorptionAmount(Math.max(0f, n.floatValue()));
            }
            case "selectedslot", "slot", "hotbarslot", "heldslot" -> {
                Double n = toDouble(value);
                if (n == null) return false;
                int slot = Math.max(0, Math.min(8, n.intValue()));
                player.getInventory().selected = slot;
                if (player instanceof ServerPlayer sp) {
                    sp.connection.send(new ClientboundSetCarriedItemPacket(slot));
                }
            }
            case "xplevel", "level" -> {
                Double n = toDouble(value);
                if (n == null) return false;
                player.experienceLevel = Math.max(0, n.intValue());
                syncExperience(player);
            }
            case "xp", "xpprogress", "experience" -> {
                Double n = toDouble(value);
                if (n == null) return false;
                player.experienceProgress = Math.max(0f, Math.min(1f, n.floatValue()));
                syncExperience(player);
            }
            case "xptotal", "totalxp", "totalexperience" -> {
                Double n = toDouble(value);
                if (n == null) return false;
                player.totalExperience = Math.max(0, n.intValue());
                syncExperience(player);
            }
            case "fireticks", "fire" -> {
                Double n = toDouble(value);
                if (n == null) return false;
                player.setRemainingFireTicks(Math.max(0, n.intValue()));
            }
            case "frozenticks", "frozen" -> {
                Double n = toDouble(value);
                if (n == null) return false;
                player.setTicksFrozen(Math.max(0, n.intValue()));
            }
            case "issprinting", "sprinting" -> player.setSprinting(toBool(value));
            case "issneaking", "sneaking" -> player.setShiftKeyDown(toBool(value));
            case "gamemode", "gametype" -> {
                if (!(player instanceof ServerPlayer sp)) return false;
                net.minecraft.world.level.GameType mode =
                        net.minecraft.world.level.GameType.byName(String.valueOf(value).toLowerCase(Locale.ROOT));
                if (mode == null) return false;
                sp.setGameMode(mode);
                PlayerFlightOverrides.apply(sp);
            }
            case "flightenabled", "mayfly" -> {
                if (!(player instanceof ServerPlayer sp)) return false;
                PlayerFlightOverrides.set(sp, toBool(value));
            }
            case "lockperspective", "lockf5", "lockcameratype", "lockcameramode", "perspectivelocked" -> {
                if (!(player instanceof ServerPlayer sp)) return false;
                PlayerControlOverrides.setPerspectiveLocked(sp, toBool(value));
            }
            case "lockforward", "lockfwd" -> {
                if (!(player instanceof ServerPlayer sp)) return false;
                PlayerControlOverrides.setMoveDir(sp, PlayerMoveDirs.FORWARD, toBool(value));
            }
            case "lockback", "lockbackward" -> {
                if (!(player instanceof ServerPlayer sp)) return false;
                PlayerControlOverrides.setMoveDir(sp, PlayerMoveDirs.BACK, toBool(value));
            }
            case "lockleft", "lockstrafeleft" -> {
                if (!(player instanceof ServerPlayer sp)) return false;
                PlayerControlOverrides.setMoveDir(sp, PlayerMoveDirs.LEFT, toBool(value));
            }
            case "lockright", "lockstraferight" -> {
                if (!(player instanceof ServerPlayer sp)) return false;
                PlayerControlOverrides.setMoveDir(sp, PlayerMoveDirs.RIGHT, toBool(value));
            }
            case "locknorth" -> {
                if (!(player instanceof ServerPlayer sp)) return false;
                PlayerControlOverrides.setMoveDir(sp, PlayerMoveDirs.NORTH, toBool(value));
            }
            case "locksouth" -> {
                if (!(player instanceof ServerPlayer sp)) return false;
                PlayerControlOverrides.setMoveDir(sp, PlayerMoveDirs.SOUTH, toBool(value));
            }
            case "lockeast" -> {
                if (!(player instanceof ServerPlayer sp)) return false;
                PlayerControlOverrides.setMoveDir(sp, PlayerMoveDirs.EAST, toBool(value));
            }
            case "lockwest" -> {
                if (!(player instanceof ServerPlayer sp)) return false;
                PlayerControlOverrides.setMoveDir(sp, PlayerMoveDirs.WEST, toBool(value));
            }
            case "lockmovedirs", "movedirs", "lockwalkdirs" -> {
                if (!(player instanceof ServerPlayer sp)) return false;
                PlayerControlOverrides.setMoveDirMask(sp, PlayerMoveDirs.parse(value));
            }
            case "flying" -> {
                player.getAbilities().flying = toBool(value) && player.getAbilities().mayfly;
                syncAbilities(player);
            }
            case "flyspeed" -> {
                Double n = toDouble(value);
                if (n == null) return false;
                player.getAbilities().setFlyingSpeed(Math.max(0f, n.floatValue()));
                syncAbilities(player);
            }
            case "walkspeed" -> {
                Double n = toDouble(value);
                if (n == null) return false;
                player.getAbilities().setWalkingSpeed(Math.max(0f, n.floatValue()));
                syncAbilities(player);
            }
            case "movementspeed" -> {
                Double n = toDouble(value);
                if (n == null) return false;
                return org.zonarstudio.spraute_engine.compat.SprautePlayerCompat.setAttributeBase(
                        player, org.zonarstudio.spraute_engine.compat.SprautePlayerCompat.movementSpeedAttr(), n);
            }
            case "jumpstrength" -> {
                Double n = toDouble(value);
                if (n == null || !(player instanceof ServerPlayer sp)) return false;
                PlayerJumpStrengthOverrides.set(sp, n.floatValue());
            }
            case "stepheight" -> {
                Double n = toDouble(value);
                if (n == null) return false;
                if (player instanceof ServerPlayer sp) PlayerStepHeightOverrides.set(sp, n.floatValue());
                else org.zonarstudio.spraute_engine.compat.SprautePlayerCompat.setStepHeight(player, n.floatValue());
            }
            case "attackdamage" -> {
                Double n = toDouble(value);
                if (n == null) return false;
                return org.zonarstudio.spraute_engine.compat.SprautePlayerCompat.setAttributeBase(
                        player, org.zonarstudio.spraute_engine.compat.SprautePlayerCompat.attackDamageAttr(), n);
            }
            case "digspeed" -> {
                Double n = toDouble(value);
                if (n == null || !(player instanceof ServerPlayer sp)) return false;
                PlayerDigSpeedOverrides.set(sp, n);
            }
            case "blockreach", "reach", "placereach", "blockinteractionrange" -> {
                Double n = toDouble(value);
                if (n == null) return false;
                return org.zonarstudio.spraute_engine.compat.SprautePlayerCompat.setBlockReach(player, n);
            }
            case "invulnerable" -> {
                player.getAbilities().invulnerable = toBool(value);
                syncAbilities(player);
            }
            case "instabuild", "infinitebuild" -> {
                player.getAbilities().instabuild = toBool(value);
                syncAbilities(player);
            }
            case "healthenabled" -> {
                if (!(player instanceof ServerPlayer sp)) return false;
                PlayerVitalsOverrides.setHealthEnabled(sp, toBool(value));
            }
            case "hungerenabled" -> {
                if (!(player instanceof ServerPlayer sp)) return false;
                PlayerVitalsOverrides.setHungerEnabled(sp, toBool(value));
            }
            case "xpvisible", "xphud", "xpbar", "experiencevisible", "experiencehud", "xpenabled" -> {
                if (!(player instanceof ServerPlayer sp)) return false;
                PlayerVitalsOverrides.setXpVisible(sp, toBool(value));
            }
            case "skin", "texture", "playerskin" -> {
                String skin = value == null ? "" : String.valueOf(value).trim();
                if ("null".equalsIgnoreCase(skin)) skin = "";
                org.zonarstudio.spraute_engine.scene.PlayerSkinOps.setBaseSkin(player.getUUID(), skin);
            }
            default -> {
                if (org.zonarstudio.spraute_engine.scene.PlayerSkinLayers.setProperty(
                        player.getUUID(), key, toBool(value))) {
                    return true;
                }
                return false;
            }
        }
        return true;
    }

    private static String playerKey(String prop) {
        return prop == null ? "" : prop.toLowerCase(Locale.ROOT).replace("_", "");
    }

    private static Double toDouble(Object value) {
        if (value instanceof Number n) return n.doubleValue();
        if (value instanceof String s) {
            try {
                return Double.parseDouble(s.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static boolean toBool(Object value) {
        if (value instanceof Boolean b) return b;
        if (value instanceof Number n) return n.doubleValue() != 0;
        if (value instanceof String s) {
            String t = s.trim();
            return "true".equalsIgnoreCase(t) || "1".equals(t) || "yes".equalsIgnoreCase(t);
        }
        return false;
    }

    private static void syncExperience(Player player) {
        if (!(player instanceof ServerPlayer sp)) return;
        sp.connection.send(new ClientboundSetExperiencePacket(
                player.experienceProgress, player.totalExperience, player.experienceLevel));
    }

    private static void syncAbilities(Player player) {
        if (player instanceof ServerPlayer sp) sp.onUpdateAbilities();
    }

    private static boolean applyPlayerPos(Player player, double x, double y, double z) {
        if (player instanceof ServerPlayer sp) {
            PlayerControlOverrides.setPos(sp, x, y, z);
            return true;
        }
        player.teleportTo(x, y, z);
        return true;
    }

    private static boolean applyPlayerLook(Player player, float yaw, float pitch) {
        if (player instanceof ServerPlayer sp) {
            PlayerControlOverrides.setLook(sp, yaw, pitch);
            return true;
        }
        pitch = Math.max(-90f, Math.min(90f, pitch));
        player.setYRot(yaw);
        player.setXRot(pitch);
        player.yHeadRot = yaw;
        player.yRotO = yaw;
        player.xRotO = pitch;
        player.yHeadRotO = yaw;
        return true;
    }

    private static boolean applyLookDir(Player player, double dx, double dy, double dz) {
        float[] yp = org.zonarstudio.spraute_engine.script.util.EntityFacingUtil.yawPitchFromDir(dx, dy, dz);
        if (yp == null) return false;
        return applyPlayerLook(player, yp[0], yp[1]);
    }

    private static Object readEntity(Entity entity, String prop) {
        String key = prop.toLowerCase(Locale.ROOT).replace("_", "");
        return switch (key) {
            case "name" -> entity.getName().getString();
            case "id", "nodeid", "scriptid" -> {
                String id = org.zonarstudio.spraute_engine.scene.SceneRuntime.scriptIdFromEntity(entity);
                yield id == null ? "" : id;
            }
            case "sceneid" -> {
                String sid = org.zonarstudio.spraute_engine.scene.SceneRuntime.sceneIdFromEntity(entity);
                yield sid == null ? "" : sid;
            }
            case "node" -> org.zonarstudio.spraute_engine.scene.SceneRuntime.nodeViewFromEntity(entity);
            case "uuid" -> entity.getUUID().toString();
            case "x" -> entity.getX();
            case "y" -> entity.getY();
            case "z" -> entity.getZ();
            case "yaw" -> entity.getYRot();
            case "pitch" -> entity.getXRot();
            case "rotationx", "rotx" -> GeoEntityScriptUtil.getModelRotationX(entity);
            case "rotationy", "roty" -> entity.getYRot();
            case "rotationz", "rotz", "roll" -> GeoEntityScriptUtil.getModelRotationZ(entity);
            case "scale" -> (GeoEntityScriptUtil.getModelScaleX(entity)
                    + GeoEntityScriptUtil.getModelScaleY(entity)
                    + GeoEntityScriptUtil.getModelScaleZ(entity)) / 3f;
            case "scalex" -> GeoEntityScriptUtil.getModelScaleX(entity);
            case "scaley" -> GeoEntityScriptUtil.getModelScaleY(entity);
            case "scalez" -> GeoEntityScriptUtil.getModelScaleZ(entity);
            case "hp" -> entity instanceof LivingEntity living ? living.getHealth() : null;
            case "speed" -> {
                if (!(entity instanceof LivingEntity living)) yield null;
                var attr = living.getAttribute(Attributes.MOVEMENT_SPEED);
                yield attr != null ? attr.getBaseValue() : null;
            }
            case "w", "width", "hitboxw", "hitboxwidth" ->
                    entity instanceof SprauteNpcEntity npc ? npc.getHitboxWidth() : null;
            case "h", "height", "hitboxh", "hitboxheight" ->
                    entity instanceof SprauteNpcEntity npc ? npc.getHitboxHeight() : null;
            default -> entity instanceof SprauteNpcEntity npc ? npc.customData.get(prop) : null;
        };
    }

    private static boolean writeEntity(Entity entity, String prop, Object value) {
        String key = prop.toLowerCase(Locale.ROOT).replace("_", "");
        if (isIdentityProp(key)) return false;
        if ("name".equals(key)) {
            entity.setCustomName(Component.literal(String.valueOf(value)));
            return true;
        }
        if (LerpMath.looksLikeColor(value) || (value instanceof String && entity instanceof SprauteNpcEntity)) {
            if (entity instanceof SprauteNpcEntity npc && !isKnownNumericProp(key)) {
                npc.customData.put(prop, value);
                return true;
            }
        }
        if (!(value instanceof Number n)) {
            if (entity instanceof SprauteNpcEntity npc) {
                npc.customData.put(prop, value);
                return true;
            }
            return false;
        }
        switch (key) {
            case "x" -> entity.setPos(n.doubleValue(), entity.getY(), entity.getZ());
            case "y" -> entity.setPos(entity.getX(), n.doubleValue(), entity.getZ());
            case "z" -> entity.setPos(entity.getX(), entity.getY(), n.doubleValue());
            case "yaw", "rotationy", "roty" -> {
                entity.setYRot(n.floatValue());
                entity.yRotO = n.floatValue();
                if (entity instanceof LivingEntity living) {
                    living.yHeadRot = n.floatValue();
                    living.yHeadRotO = n.floatValue();
                    living.yBodyRot = n.floatValue();
                    living.yBodyRotO = n.floatValue();
                }
                if (GeoEntityScriptUtil.isGeoEntity(entity)) {
                    GeoEntityScriptUtil.setModelRotationY(entity, 0f);
                }
            }
            case "pitch" -> {
                entity.setXRot(n.floatValue());
                entity.xRotO = n.floatValue();
            }
            case "rotationx", "rotx" -> {
                if (!GeoEntityScriptUtil.isGeoEntity(entity)) return false;
                GeoEntityScriptUtil.setModelRotationX(entity, n.floatValue());
            }
            case "rotationz", "rotz", "roll" -> {
                if (!GeoEntityScriptUtil.isGeoEntity(entity)) return false;
                GeoEntityScriptUtil.setModelRotationZ(entity, n.floatValue());
            }
            case "scale" -> {
                if (!GeoEntityScriptUtil.isGeoEntity(entity)) return false;
                GeoEntityScriptUtil.setModelScale(entity, n.floatValue(), n.floatValue(), n.floatValue());
            }
            case "scalex" -> {
                if (!GeoEntityScriptUtil.isGeoEntity(entity)) return false;
                GeoEntityScriptUtil.setModelScale(entity, n.floatValue(),
                        GeoEntityScriptUtil.getModelScaleY(entity),
                        GeoEntityScriptUtil.getModelScaleZ(entity));
            }
            case "scaley" -> {
                if (!GeoEntityScriptUtil.isGeoEntity(entity)) return false;
                GeoEntityScriptUtil.setModelScale(entity,
                        GeoEntityScriptUtil.getModelScaleX(entity),
                        n.floatValue(),
                        GeoEntityScriptUtil.getModelScaleZ(entity));
            }
            case "scalez" -> {
                if (!GeoEntityScriptUtil.isGeoEntity(entity)) return false;
                GeoEntityScriptUtil.setModelScale(entity,
                        GeoEntityScriptUtil.getModelScaleX(entity),
                        GeoEntityScriptUtil.getModelScaleY(entity),
                        n.floatValue());
            }
            case "hp" -> {
                if (!(entity instanceof LivingEntity living)) return false;
                living.setHealth(n.floatValue());
            }
            case "speed" -> {
                if (!(entity instanceof LivingEntity living)) return false;
                var attr = living.getAttribute(Attributes.MOVEMENT_SPEED);
                if (attr == null) return false;
                attr.setBaseValue(n.doubleValue());
            }
            case "w", "width", "hitboxw", "hitboxwidth" -> {
                if (!(entity instanceof SprauteNpcEntity npc)) return false;
                npc.setHitbox(n.floatValue(), npc.getHitboxHeight());
            }
            case "h", "height", "hitboxh", "hitboxheight" -> {
                if (!(entity instanceof SprauteNpcEntity npc)) return false;
                npc.setHitbox(npc.getHitboxWidth(), n.floatValue());
            }
            default -> {
                if (entity instanceof SprauteNpcEntity npc) {
                    npc.customData.put(prop, value);
                    return true;
                }
                return false;
            }
        }
        return true;
    }

    private static boolean isKnownNumericProp(String key) {
        return switch (key) {
            case "x", "y", "z", "yaw", "pitch", "roll",
                 "rotationx", "rotx", "rotationy", "roty", "rotationz", "rotz",
                 "scale", "scalex", "scaley", "scalez",
                 "hp", "speed", "w", "width", "hitboxw", "hitboxwidth",
                 "h", "height", "hitboxh", "hitboxheight" -> true;
            default -> false;
        };
    }

    private static boolean isIdentityProp(String key) {
        return switch (key) {
            case "id", "nodeid", "scriptid", "uuid", "sceneid", "node", "entity" -> true;
            default -> false;
        };
    }
}
