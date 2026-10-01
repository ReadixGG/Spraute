package org.zonarstudio.spraute_engine.scene;

import net.minecraft.network.chat.Component;
import org.zonarstudio.spraute_engine.entity.SprauteNpcEntity;
import org.zonarstudio.spraute_engine.scene.resource.SceneDropRule;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Shared bridge between {@link SceneNode} NPC payload and {@link SprauteNpcEntity}.
 * Used by scene spawn, live inspector sync, and {@code create npc} attach into a scene.
 */
public final class NpcNodeBridge {
    private NpcNodeBridge() {}

    /**
     * Fill a scene NPC node from the live entity, then overlay create-npc block props when present.
     */
    public static void fillNodeFromNpc(SceneNode node, SprauteNpcEntity npc, Map<String, List<Object>> props) {
        if (node == null || npc == null) return;
        fillFromEntity(node, npc);
        if (props != null && !props.isEmpty()) {
            applyCreateNpcProps(node, props);
        }
    }

    /** Copy readable entity state into the node (inspector sees the real NPC). */
    public static void fillFromEntity(SceneNode node, SprauteNpcEntity npc) {
        if (node == null || npc == null) return;
        if (npc.getCustomName() != null) {
            node.name = npc.getCustomName().getString();
        }
        node.nameVisible = npc.isCustomNameVisible();
        node.model = nullToEmpty(npc.getModel());
        node.texture = nullToEmpty(npc.getTexture());
        node.animation = nullToEmpty(npc.getAnimation());
        node.maxHealth = Math.max(1f, npc.getMaxHealth());
        var speed = npc.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
        if (speed != null) node.movementSpeed = (float) speed.getBaseValue();
        var flyingSpeed = npc.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.FLYING_SPEED);
        if (flyingSpeed != null) node.flyingSpeed = (float) flyingSpeed.getBaseValue();
        node.flySmoothing = npc.getFlySmoothing();
        node.walkTurnSpeed = npc.getWalkTurnSpeed();
        node.idleTurnSpeed = npc.getIdleTurnSpeed();
        copyPathToNode(node, npc.groundPathSettings());
        copyAirToNode(node, npc.airPathSettings());
        node.invulnerable = npc.isInvulnerable();
        node.silent = npc.isSilent();
        node.glowing = org.zonarstudio.spraute_engine.compat.SprauteEntityCompat.isGlowing(npc);
        node.translucent = npc.isTranslucent();
        node.opacity = npc.getOpacity();
        node.lodEnabled = npc.isLodEnabled();
        node.renderDistance = npc.getRenderDistance();
        node.forceVisible = npc.isForceVisible();
        node.loadChunks = npc.isLoadChunks();
        node.noGravity = npc.isNoGravity();
        node.collision = npc.getHasCollision();
        node.flying = npc.isFlying();
        node.swimming = npc.isSwimmingScript();
        node.group = nullToEmpty(npc.getGroupName());
        node.playerSkinOverlay = nullToEmpty(npc.getPlayerSkinOverlayUuid());
        node.headBone = nullToEmpty(npc.getHeadBoneName());
        if (node.headBone.isBlank()) node.headBone = "Head";
        node.unlimitedHeadLook = npc.isHeadLookUnlimited();
        node.nameYOffset = npc.getNameYOffset();
        node.hitboxWidth = npc.getHitboxWidth();
        node.hitboxHeight = npc.getHitboxHeight();
        node.hitboxOffsetX = npc.getHitboxOffsetX();
        node.hitboxOffsetY = npc.getHitboxOffsetY();
        node.hitboxOffsetZ = npc.getHitboxOffsetZ();
        node.showHitbox = npc.isShowHitboxDebug();
        node.idleAnimation = nullToEmpty(npc.getIdleAnim());
        node.walkAnimation = nullToEmpty(npc.getWalkAnim());
        node.flyIdleAnimation = nullToEmpty(npc.getFlyIdleAnim());
        node.flyAnimation = nullToEmpty(npc.getFlyWalkAnim());
        node.swimIdleAnimation = nullToEmpty(npc.getSwimIdleAnim());
        node.swimAnimation = nullToEmpty(npc.getSwimWalkAnim());
        node.deathAnimation = nullToEmpty(npc.getDeathAnim());
        node.attackRange = (float) npc.getAttackRange();
        node.attackCooldown = npc.getAttackCooldown();
        node.attackHitDelay = npc.getAttackHitDelay();
        node.attackHand = nullToEmpty(npc.getAttackHand());
        if (node.attackHand.isBlank()) node.attackHand = "right";
        node.attackAnimations = npc.getAttackAnimsCsv();
        node.dropXp = Math.max(0, npc.getDropXp());
        if (node.drops == null) node.drops = new ArrayList<>();
        node.drops.clear();
        for (var rule : npc.customDrops) {
            if (rule == null || rule.itemId == null || rule.itemId.isBlank()) continue;
            if (SceneDropRule.isSelf(rule.itemId)) continue;
            node.drops.add(new SceneDropRule(rule.itemId, rule.min, rule.max, rule.chance));
        }
    }

    /**
     * Overlay create-npc / spawnNpcPrefab property map onto the node
     * (props win over entity snapshot when both are set).
     */
    public static void applyCreateNpcProps(SceneNode node, Map<String, List<Object>> props) {
        if (node == null || props == null) return;
        if (props.containsKey("name")) node.name = String.valueOf(props.get("name").get(0));
        if (props.containsKey("model")) node.model = String.valueOf(props.get("model").get(0));
        if (props.containsKey("texture")) node.texture = String.valueOf(props.get("texture").get(0));
        if (props.containsKey("animation")) node.animation = String.valueOf(props.get("animation").get(0));
        if (props.containsKey("showName")) {
            node.nameVisible = isTruthy(props.get("showName").get(0));
        }
        if (props.containsKey("hp") && props.get("hp").get(0) instanceof Number n) {
            node.maxHealth = Math.max(1f, n.floatValue());
        }
        if (props.containsKey("speed") && props.get("speed").get(0) instanceof Number n) {
            node.movementSpeed = Math.max(0f, n.floatValue());
        }
        if (props.containsKey("flyingSpeed") && props.get("flyingSpeed").get(0) instanceof Number n) {
            node.flyingSpeed = Math.max(0f, n.floatValue());
        }
        if (props.containsKey("flySmoothing") && props.get("flySmoothing").get(0) instanceof Number n) {
            node.flySmoothing = n.floatValue();
        }
        if (props.containsKey("walkTurnSpeed") && props.get("walkTurnSpeed").get(0) instanceof Number n) {
            node.walkTurnSpeed = Math.max(0.1f, n.floatValue());
        }
        if (props.containsKey("idleTurnSpeed") && props.get("idleTurnSpeed").get(0) instanceof Number n) {
            node.idleTurnSpeed = Math.max(0.1f, n.floatValue());
        }
        applyPathProps(node, props);
        if (props.containsKey("collision")) node.collision = isTruthy(props.get("collision").get(0));
        if (props.containsKey("flying")) node.flying = isTruthy(props.get("flying").get(0));
        if (props.containsKey("swimming")) node.swimming = isTruthy(props.get("swimming").get(0));
        if (props.containsKey("invulnerable")) node.invulnerable = isTruthy(props.get("invulnerable").get(0));
        if (props.containsKey("silent")) node.silent = isTruthy(props.get("silent").get(0));
        if (props.containsKey("glowing")) node.glowing = isTruthy(props.get("glowing").get(0));
        if (props.containsKey("translucent") || props.containsKey("transparent")) {
            Object raw = props.containsKey("translucent") ? props.get("translucent").get(0)
                    : props.get("transparent").get(0);
            node.translucent = isTruthy(raw);
        }
        if (props.containsKey("opacity") && props.get("opacity").get(0) instanceof Number n) {
            node.opacity = Math.max(0f, Math.min(1f, n.floatValue()));
        }
        if (props.containsKey("lod") || props.containsKey("lodEnabled")) {
            Object raw = props.containsKey("lodEnabled") ? props.get("lodEnabled").get(0) : props.get("lod").get(0);
            node.lodEnabled = !(raw instanceof String s && "off".equalsIgnoreCase(s)) && isTruthy(raw);
        }
        if (props.containsKey("renderDistance") && props.get("renderDistance").get(0) instanceof Number n) {
            node.renderDistance = Math.max(0f, n.floatValue());
        }
        if (props.containsKey("forceVisible")) node.forceVisible = isTruthy(props.get("forceVisible").get(0));
        if (props.containsKey("loadChunks") || props.containsKey("load_chunks")
                || props.containsKey("keepChunks") || props.containsKey("keepLoaded")) {
            Object raw = props.containsKey("loadChunks") ? props.get("loadChunks").get(0)
                    : props.containsKey("load_chunks") ? props.get("load_chunks").get(0)
                    : props.containsKey("keepChunks") ? props.get("keepChunks").get(0)
                    : props.get("keepLoaded").get(0);
            node.loadChunks = isTruthy(raw);
        }
        if (props.containsKey("noGravity")) node.noGravity = isTruthy(props.get("noGravity").get(0));
        if (props.containsKey("group")) node.group = String.valueOf(props.get("group").get(0));
        if (props.containsKey("playerSkinOverlay") || props.containsKey("player_skin") || props.containsKey("skinPlayer")) {
            Object raw = props.containsKey("playerSkinOverlay") ? props.get("playerSkinOverlay").get(0)
                    : props.containsKey("player_skin") ? props.get("player_skin").get(0)
                    : props.get("skinPlayer").get(0);
            node.playerSkinOverlay = String.valueOf(raw);
        }
        if (props.containsKey("headBone") || props.containsKey("head_bone")) {
            Object raw = props.containsKey("headBone") ? props.get("headBone").get(0) : props.get("head_bone").get(0);
            node.headBone = String.valueOf(raw);
        }
        if (props.containsKey("unlimitedHeadLook") || props.containsKey("unlimited_head_look")) {
            Object raw = props.containsKey("unlimitedHeadLook")
                    ? props.get("unlimitedHeadLook").get(0) : props.get("unlimited_head_look").get(0);
            node.unlimitedHeadLook = isTruthy(raw);
        }
        if (props.containsKey("nameYOffset") && props.get("nameYOffset").get(0) instanceof Number n) {
            node.nameYOffset = n.floatValue();
        }
        if (props.containsKey("showHitbox") || props.containsKey("show_hitbox")) {
            Object raw = props.containsKey("showHitbox") ? props.get("showHitbox").get(0) : props.get("show_hitbox").get(0);
            node.showHitbox = isTruthy(raw);
        }
        if (props.containsKey("idleAnim")) node.idleAnimation = String.valueOf(props.get("idleAnim").get(0));
        if (props.containsKey("walkAnim")) node.walkAnimation = String.valueOf(props.get("walkAnim").get(0));
        if (props.containsKey("flyIdleAnim")) node.flyIdleAnimation = String.valueOf(props.get("flyIdleAnim").get(0));
        if (props.containsKey("flyWalkAnim")) node.flyAnimation = String.valueOf(props.get("flyWalkAnim").get(0));
        if (props.containsKey("swimIdleAnim")) node.swimIdleAnimation = String.valueOf(props.get("swimIdleAnim").get(0));
        if (props.containsKey("swimWalkAnim")) node.swimAnimation = String.valueOf(props.get("swimWalkAnim").get(0));
        if (props.containsKey("deathAnim")) node.deathAnimation = String.valueOf(props.get("deathAnim").get(0));
        if (props.containsKey("attackRange") && props.get("attackRange").get(0) instanceof Number n) {
            node.attackRange = n.floatValue();
        }
        if (props.containsKey("attackCooldown") && props.get("attackCooldown").get(0) instanceof Number n) {
            node.attackCooldown = n.intValue();
        }
        if (props.containsKey("attackHitDelay") && props.get("attackHitDelay").get(0) instanceof Number n) {
            node.attackHitDelay = n.intValue();
        }
        if (props.containsKey("attackHand")) node.attackHand = String.valueOf(props.get("attackHand").get(0));
        if (props.containsKey("attackAnimations") || props.containsKey("attackAnims")) {
            Object raw = props.containsKey("attackAnimations")
                    ? props.get("attackAnimations").get(0) : props.get("attackAnims").get(0);
            if (raw instanceof List<?> list) {
                StringBuilder sb = new StringBuilder();
                for (Object o : list) {
                    if (o == null) continue;
                    if (sb.length() > 0) sb.append(',');
                    sb.append(String.valueOf(o).trim());
                }
                node.attackAnimations = sb.toString();
            } else {
                node.attackAnimations = String.valueOf(raw);
            }
        }
        applyHitboxProps(node, props);
        applyDropProps(node, props);
    }

    /** Push SceneNode NPC fields onto the entity (inspector / scene spawn). */
    public static void applyNodeToNpc(SceneNode node, SprauteNpcEntity npc) {
        if (node == null || npc == null) return;
        if (notBlank(node.model)) npc.setModel(node.model);
        if (notBlank(node.texture)) npc.setTexture(node.texture);
        if (notBlank(node.animation)) npc.setAnimation(node.animation);
        npc.setCustomName(Component.literal(node.name == null ? "" : node.name));
        npc.setCustomNameVisible(node.nameVisible);
        var health = npc.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
        if (health != null) health.setBaseValue(Math.max(1f, node.maxHealth));
        npc.setHealth(Math.min(npc.getHealth(), Math.max(1f, node.maxHealth)));
        var speed = npc.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
        if (speed != null) speed.setBaseValue(Math.max(0f, node.movementSpeed));
        var flyingSpeed = npc.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.FLYING_SPEED);
        if (flyingSpeed != null) flyingSpeed.setBaseValue(Math.max(0f, node.flyingSpeed));
        npc.setFlySmoothing(node.flySmoothing);
        npc.setWalkTurnSpeed(node.walkTurnSpeed);
        npc.setIdleTurnSpeed(node.idleTurnSpeed);
        copyPathToNpc(node, npc);
        copyAirToNpc(node, npc);
        npc.setInvulnerable(node.invulnerable);
        npc.setSilent(node.silent);
        npc.setGlowingTag(node.glowing);
        npc.setTranslucent(node.translucent);
        npc.setOpacity(node.opacity);
        npc.setLodEnabled(node.lodEnabled);
        npc.setRenderDistance(node.renderDistance);
        npc.setForceVisible(node.forceVisible);
        npc.setLoadChunks(node.loadChunks);
        npc.setNoGravity(node.noGravity);
        npc.setHasCollision(node.collision);
        npc.setFlying(node.flying);
        npc.setSwimmingScript(node.swimming);
        npc.setGroupName(node.group);
        if (node.playerSkinOverlay != null && !node.playerSkinOverlay.isBlank()) {
            try {
                npc.setPlayerSkinOverlay(UUID.fromString(node.playerSkinOverlay.trim()));
            } catch (IllegalArgumentException ignored) {
                npc.clearPlayerSkinOverlay();
            }
        } else {
            npc.clearPlayerSkinOverlay();
        }
        npc.setHeadBone(node.headBone);
        npc.setHeadLookUnlimited(node.unlimitedHeadLook);
        npc.setNameYOffset(node.nameYOffset);
        npc.setHitbox(node.hitboxWidth, node.hitboxHeight,
                node.hitboxOffsetX, node.hitboxOffsetY, node.hitboxOffsetZ);
        npc.setShowHitboxDebug(node.showHitbox);
        npc.setIdleAnim(node.idleAnimation);
        npc.setWalkAnim(node.walkAnimation);
        npc.setFlyIdleAnim(node.flyIdleAnimation);
        npc.setFlyWalkAnim(node.flyAnimation);
        npc.setSwimIdleAnim(node.swimIdleAnimation);
        npc.setSwimWalkAnim(node.swimAnimation);
        npc.setDeathAnim(node.deathAnimation);
        npc.setAttackRange(node.attackRange);
        npc.setAttackCooldown(node.attackCooldown);
        npc.setAttackHitDelay(node.attackHitDelay);
        npc.setAttackHand(node.attackHand);
        npc.setAttackAnims(java.util.Arrays.stream(
                        (node.attackAnimations == null ? "" : node.attackAnimations).split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).toArray(String[]::new));
        npc.customDrops.clear();
        if (node.drops != null) {
            for (var rule : node.drops) {
                if (rule == null || rule.itemId == null || rule.itemId.isBlank()) continue;
                if (SceneDropRule.isSelf(rule.itemId)) continue;
                npc.customDrops.add(new org.zonarstudio.spraute_engine.registry.CustomDropRegistry.DropRule(
                        rule.itemId.trim(),
                        Math.max(0, rule.min),
                        Math.max(Math.max(0, rule.min), rule.max),
                        Math.max(0, Math.min(100, rule.chance)),
                        false,
                        null));
            }
        }
        npc.setDropXp(Math.max(0, node.dropXp));
    }

    private static void copyPathToNode(SceneNode node, org.zonarstudio.spraute_engine.entity.path.GroundPathSettings s) {
        if (s == null) return;
        node.pathArriveRadius = (float) s.arriveRadius;
        node.pathArriveVert = (float) s.arriveVert;
        node.pathStepHeight = (float) s.stepHeight;
        node.pathMaxJump = s.maxJump;
        node.pathMaxDrop = s.maxDrop;
        node.pathMaxRange = (float) s.maxRange;
        node.pathDiagonal = s.allowDiagonal;
        node.pathAvoidWater = s.avoidWater;
        node.pathAvoidLava = s.avoidLava;
        node.pathMaxNodes = s.maxNodes;
        node.pathSnapOnArrive = s.snapOnArrive;
    }

    private static void copyPathToNpc(SceneNode node, SprauteNpcEntity npc) {
        var s = npc.groundPathSettings();
        s.arriveRadius = node.pathArriveRadius;
        s.arriveVert = node.pathArriveVert;
        s.stepHeight = node.pathStepHeight;
        s.maxJump = node.pathMaxJump;
        s.maxDrop = node.pathMaxDrop;
        s.maxRange = node.pathMaxRange;
        s.allowDiagonal = node.pathDiagonal;
        s.avoidWater = node.pathAvoidWater;
        s.avoidLava = node.pathAvoidLava;
        s.maxNodes = node.pathMaxNodes;
        s.snapOnArrive = node.pathSnapOnArrive;
        s.clamp();
        npc.applyPathStepHeight();
    }

    private static void copyAirToNode(SceneNode node, org.zonarstudio.spraute_engine.entity.path.AirPathSettings s) {
        if (s == null) return;
        node.flyArriveRadius = (float) s.arriveRadius;
        node.flyMaxRange = (float) s.maxRange;
        node.flyDiagonal = s.allowDiagonal;
        node.flyAvoidWater = s.avoidWater;
        node.flyAvoidLava = s.avoidLava;
        node.flyMaxNodes = s.maxNodes;
        node.flySnapOnArrive = s.snapOnArrive;
    }

    private static void copyAirToNpc(SceneNode node, SprauteNpcEntity npc) {
        var s = npc.airPathSettings();
        s.arriveRadius = node.flyArriveRadius;
        s.maxRange = node.flyMaxRange;
        s.allowDiagonal = node.flyDiagonal;
        s.avoidWater = node.flyAvoidWater;
        s.avoidLava = node.flyAvoidLava;
        s.maxNodes = node.flyMaxNodes;
        s.snapOnArrive = node.flySnapOnArrive;
        s.clamp();
    }

    private static void applyPathProps(SceneNode node, Map<String, List<Object>> props) {
        Float arrive = firstNumber(props, "pathArriveRadius", "pathArrive", "arriveRadius");
        if (arrive != null) node.pathArriveRadius = arrive;
        Float vert = firstNumber(props, "pathArriveVert", "arriveVert");
        if (vert != null) node.pathArriveVert = vert;
        Float step = firstNumber(props, "pathStepHeight", "stepHeight");
        if (step != null) node.pathStepHeight = step;
        Float jump = firstNumber(props, "pathMaxJump", "maxJump");
        if (jump != null) node.pathMaxJump = jump.intValue();
        Float drop = firstNumber(props, "pathMaxDrop", "maxDrop");
        if (drop != null) node.pathMaxDrop = drop.intValue();
        Float range = firstNumber(props, "pathMaxRange", "pathRange");
        if (range != null) node.pathMaxRange = range;
        Float nodes = firstNumber(props, "pathMaxNodes", "pathNodes");
        if (nodes != null) node.pathMaxNodes = nodes.intValue();
        if (props.containsKey("pathDiagonal") || props.containsKey("pathAllowDiagonal")) {
            Object raw = props.containsKey("pathDiagonal")
                    ? props.get("pathDiagonal").get(0) : props.get("pathAllowDiagonal").get(0);
            node.pathDiagonal = isTruthy(raw);
        }
        if (props.containsKey("pathAvoidWater") || props.containsKey("avoidWater")) {
            Object raw = props.containsKey("pathAvoidWater")
                    ? props.get("pathAvoidWater").get(0) : props.get("avoidWater").get(0);
            node.pathAvoidWater = isTruthy(raw);
        }
        if (props.containsKey("pathAvoidLava") || props.containsKey("avoidLava")) {
            Object raw = props.containsKey("pathAvoidLava")
                    ? props.get("pathAvoidLava").get(0) : props.get("avoidLava").get(0);
            node.pathAvoidLava = isTruthy(raw);
        }
        if (props.containsKey("pathSnapOnArrive") || props.containsKey("pathSnap")) {
            Object raw = props.containsKey("pathSnapOnArrive")
                    ? props.get("pathSnapOnArrive").get(0) : props.get("pathSnap").get(0);
            node.pathSnapOnArrive = isTruthy(raw);
        }
        Float flyArrive = firstNumber(props, "flyArriveRadius", "flyArrive");
        if (flyArrive != null) node.flyArriveRadius = flyArrive;
        Float flyRange = firstNumber(props, "flyMaxRange", "flyPathRange");
        if (flyRange != null) node.flyMaxRange = flyRange;
        Float flyNodes = firstNumber(props, "flyMaxNodes");
        if (flyNodes != null) node.flyMaxNodes = flyNodes.intValue();
        if (props.containsKey("flyDiagonal") || props.containsKey("flyAllowDiagonal")) {
            Object raw = props.containsKey("flyDiagonal")
                    ? props.get("flyDiagonal").get(0) : props.get("flyAllowDiagonal").get(0);
            node.flyDiagonal = isTruthy(raw);
        }
        if (props.containsKey("flyAvoidWater")) {
            node.flyAvoidWater = isTruthy(props.get("flyAvoidWater").get(0));
        }
        if (props.containsKey("flyAvoidLava")) {
            node.flyAvoidLava = isTruthy(props.get("flyAvoidLava").get(0));
        }
        if (props.containsKey("flySnapOnArrive") || props.containsKey("flySnap")) {
            Object raw = props.containsKey("flySnapOnArrive")
                    ? props.get("flySnapOnArrive").get(0) : props.get("flySnap").get(0);
            node.flySnapOnArrive = isTruthy(raw);
        }
    }

    private static void applyHitboxProps(SceneNode node, Map<String, List<Object>> props) {
        if (props.containsKey("hitbox")) {
            List<Object> hb = props.get("hitbox");
            if (hb != null && hb.size() >= 2 && hb.get(0) instanceof Number && hb.get(1) instanceof Number) {
                node.hitboxWidth = ((Number) hb.get(0)).floatValue();
                node.hitboxHeight = ((Number) hb.get(1)).floatValue();
                if (hb.size() >= 5) {
                    if (hb.get(2) instanceof Number n) node.hitboxOffsetX = n.floatValue();
                    if (hb.get(3) instanceof Number n) node.hitboxOffsetY = n.floatValue();
                    if (hb.get(4) instanceof Number n) node.hitboxOffsetZ = n.floatValue();
                }
            }
        } else {
            Float w = firstNumber(props, "width", "w", "hitboxWidth", "hitboxwidth", "hitbox_width");
            Float h = firstNumber(props, "height", "h", "hitboxHeight", "hitboxheight", "hitbox_height");
            Float ox = firstNumber(props, "offsetX", "hitboxOffsetX", "hitbox_offset_x");
            Float oy = firstNumber(props, "offsetY", "hitboxOffsetY", "hitbox_offset_y");
            Float oz = firstNumber(props, "offsetZ", "hitboxOffsetZ", "hitbox_offset_z");
            if (w != null) node.hitboxWidth = w;
            if (h != null) node.hitboxHeight = h;
            if (ox != null) node.hitboxOffsetX = ox;
            if (oy != null) node.hitboxOffsetY = oy;
            if (oz != null) node.hitboxOffsetZ = oz;
        }
    }

    private static void applyDropProps(SceneNode node, Map<String, List<Object>> props) {
        if (props.containsKey("drops") || props.containsKey("dropItem")) {
            if (node.drops == null) node.drops = new ArrayList<>();
            node.drops.clear();
            if (props.containsKey("drops") && props.get("drops").get(0) instanceof List<?> rows) {
                for (Object row : rows) {
                    if (!(row instanceof List<?> cells) || cells.isEmpty()) continue;
                    String dItem = String.valueOf(cells.get(0));
                    int dMin = cells.size() > 1 && cells.get(1) instanceof Number n ? n.intValue() : 1;
                    int dMax = cells.size() > 2 && cells.get(2) instanceof Number n ? n.intValue() : dMin;
                    int dChance = cells.size() > 3 && cells.get(3) instanceof Number n ? n.intValue() : 100;
                    node.drops.add(new SceneDropRule(dItem, dMin, dMax, dChance));
                }
            } else if (props.containsKey("dropItem")) {
                String dItem = String.valueOf(props.get("dropItem").get(0));
                int dMin = props.containsKey("dropMin") ? ((Number) props.get("dropMin").get(0)).intValue() : 1;
                int dMax = props.containsKey("dropMax") ? ((Number) props.get("dropMax").get(0)).intValue() : dMin;
                int dChance = props.containsKey("dropChance") ? ((Number) props.get("dropChance").get(0)).intValue() : 100;
                node.drops.add(new SceneDropRule(dItem, dMin, dMax, dChance));
            }
        }
        if (props.containsKey("dropXp") || props.containsKey("xp") || props.containsKey("experience")
                || props.containsKey("drop_xp")) {
            Object raw = props.containsKey("dropXp") ? props.get("dropXp").get(0)
                    : props.containsKey("drop_xp") ? props.get("drop_xp").get(0)
                    : props.containsKey("xp") ? props.get("xp").get(0)
                    : props.get("experience").get(0);
            if (raw instanceof Number n) node.dropXp = Math.max(0, n.intValue());
        }
    }

    private static Float firstNumber(Map<String, List<Object>> props, String... keys) {
        for (String key : keys) {
            List<Object> values = props.get(key);
            if (values != null && !values.isEmpty() && values.get(0) instanceof Number number) {
                return number.floatValue();
            }
        }
        return null;
    }

    private static boolean isTruthy(Object value) {
        if (value instanceof Boolean b) return b;
        if (value instanceof Number n) return n.doubleValue() != 0;
        if (value == null) return false;
        String s = String.valueOf(value).trim();
        return !s.isEmpty() && !"false".equalsIgnoreCase(s) && !"0".equals(s);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
