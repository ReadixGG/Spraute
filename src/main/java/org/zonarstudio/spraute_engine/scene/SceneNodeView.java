package org.zonarstudio.spraute_engine.scene;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.zonarstudio.spraute_engine.entity.SprauteModelEntity;
import org.zonarstudio.spraute_engine.entity.SprauteNpcEntity;
import org.zonarstudio.spraute_engine.script.GeoEntityScriptUtil;
import org.zonarstudio.spraute_engine.script.ItemStackScriptUtil;
import org.zonarstudio.spraute_engine.script.ScriptTransformView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Script-facing view of one scene node ({@code _node}, {@code _scriptNode}, {@code _scene.node(...)}).
 *  NPC/MODEL nodes share the same method/property API as {@code create npc} / model entities:
 *  scene-specific calls are handled here; everything else falls through to the spawned entity
 *  in {@link org.zonarstudio.spraute_engine.script.ScriptExecutor}. */
public final class SceneNodeView {
    /** Returned by {@link #invokeMethod} when the call should be dispatched on the spawned entity. */
    public static final Object UNHANDLED = new Object();
    static final Object HANDLED_NULL = new Object();

    private final SceneActiveState state;
    private final String nodeId;

    SceneNodeView(SceneActiveState state, String nodeId) {
        this.state = state;
        this.nodeId = nodeId;
    }

    public String getNodeId() { return nodeId; }
    public String getSceneId() { return state.sceneId; }

    public SceneNode node() {
        return state.document.findNode(nodeId);
    }

    /** Spawned entity for this node (or first NPC/MODEL in the subtree), or null. */
    public Entity getEntity(CommandSourceStack source) {
        return entity(source);
    }

    public static boolean isUnhandled(Object result) {
        return result == UNHANDLED;
    }

    private SceneHitboxRuntime hitboxRuntime() {
        return state.hitboxRuntime(nodeId);
    }

    private ServerLevel resolveLevel(CommandSourceStack source) {
        if (source != null && source.getLevel() != null) {
            return source.getLevel();
        }
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return null;
        return SceneRuntime.resolveLevelForSync(server, state.runtimeDimension);
    }

    /**
     * Entity bound to this node. Prefab roots are often EMPTY/GROUP — then the first
     * spawned NPC/MODEL descendant is used (needed for npcChat / lookAt / playOnce).
     */
    private Entity entity(CommandSourceStack source) {
        SceneNode n = node();
        if (n != null && (n.type == SceneNodeType.CAMERA || n.type == SceneNodeType.AUDIO)) {
            SceneNode parent = state.document.findParent(n.id);
            if (parent != null && parent.type == SceneNodeType.PLAYER) {
                Entity owner = asLiveEntity(state.getRuntimeObject(parent.id));
                if (owner != null) return owner;
            }
            if (n.type == SceneNodeType.CAMERA) {
                Entity found = PlayerCameraPose.findPlayer(state.document, n);
                if (found != null) return found;
            }
        }
        ServerLevel level = resolveLevel(source);
        Entity direct = level != null
                ? state.resolveEntity(nodeId, level)
                : asLiveEntity(state.getRuntimeObject(nodeId));
        if (direct != null) return direct;
        return findEntityInSubtree(node(), level);
    }

    private Entity findEntityInSubtree(SceneNode root, ServerLevel level) {
        if (root == null) return null;
        Entity fallback = null;
        for (SceneNode child : root.children) {
            Entity e = level != null
                    ? state.resolveEntity(child.id, level)
                    : asLiveEntity(state.getRuntimeObject(child.id));
            if (e instanceof SprauteNpcEntity || e instanceof SprauteModelEntity) return e;
            if (e != null && fallback == null) fallback = e;
            Entity deep = findEntityInSubtree(child, level);
            if (deep instanceof SprauteNpcEntity || deep instanceof SprauteModelEntity) return deep;
            if (deep != null && fallback == null) fallback = deep;
        }
        return fallback;
    }

    private static Entity asLiveEntity(Object obj) {
        return obj instanceof Entity e && !e.isRemoved() ? e : null;
    }

    private UUID playerUuid(SceneNode playerNode, CommandSourceStack source) {
        Entity owner = entity(source);
        if (owner instanceof Player player) return player.getUUID();
        return PlayerSkinOps.parseUuid(playerNode == null ? null : playerNode.playerUuid);
    }

    private static String displayNameOf(SceneNode node, Entity entity) {
        if (node != null && node.name != null && !node.name.isBlank()) return node.name.trim();
        if (entity != null && entity.getCustomName() != null) {
            String cn = entity.getCustomName().getString();
            if (cn != null && !cn.isBlank()) return cn;
        }
        if (node != null) {
            for (SceneNode child : node.children) {
                String n = displayNameOf(child, null);
                if (n != null && !n.isBlank()) return n;
            }
        }
        return "";
    }

    public Object getProperty(String key, CommandSourceStack source) {
        SceneNode node = node();
        if (node == null || key == null) return null;
        String prop = key.toLowerCase(Locale.ROOT);
        if (node.type == SceneNodeType.CAMERA) {
            if ("post".equals(prop) || "postprocess".equals(prop) || "post_process".equals(prop)
                    || "fx".equals(prop) || "effects".equals(prop)) {
                SceneNode post = PlayerCameraNodes.ensurePostProcess(state, node);
                return post == null ? null : state.nodeView(post);
            }
            if (PlayerCameraOps.isCameraProperty(prop)) {
                return PlayerCameraOps.get(node, prop);
            }
        }
        if (node.type == SceneNodeType.AUDIO && PlayerAudioOps.isAudioProperty(prop)) {
            return PlayerAudioOps.get(node, prop);
        }
        if (node.type == SceneNodeType.SKIN_OVERLAY) {
            Object overlayLayer = PlayerSkinLayers.getOverlayProperty(node, prop);
            if (overlayLayer != null) return overlayLayer;
        }
        Entity entity = entity(source);
        if (node.type == SceneNodeType.PLAYER && entity instanceof net.minecraft.world.entity.player.Player player
                && !isStructuralPlayerProperty(prop)) {
            Object playerValue = org.zonarstudio.spraute_engine.script.ScriptPropertyAccess.get(player, key, source);
            if (playerValue != null) return playerValue;
        }

        return switch (prop) {
            case "id", "nodeid", "node_id" -> node.id;
            case "type" -> node.type.name().toLowerCase(Locale.ROOT);
            case "name" -> displayNameOf(node, entity);
            case "uuid" -> entity != null ? entity.getUUID().toString() : null;
            case "parent" -> parent();
            case "parentid", "parent_id" -> {
                SceneNode parent = state.document.findParent(node.id);
                yield parent == null ? "" : parent.id;
            }
            case "children" -> children();
            case "scene" -> state.sceneView();
            case "sceneid", "scene_id" -> state.sceneId;
            case "transform" -> new ScriptTransformView(this, source);
            case "object", "entity" -> entity;
            case "hasobject", "has_object" -> entity != null;
            case "childcount", "child_count", "childrencount" -> node.children.size();
            case "camera", "cam" -> {
                if (node.type != SceneNodeType.PLAYER) yield null;
                SceneNode camera = PlayerCameraNodes.ensureCamera(node, state.document.ensurePlayersNode(),
                        state.document);
                yield camera == null ? null : state.nodeView(camera);
            }
            case "audio", "soundbus", "sound_bus" -> {
                if (node.type != SceneNodeType.PLAYER) yield null;
                SceneNode audio = PlayerAudioNodes.ensureAudio(node, state.document);
                yield audio == null ? null : state.nodeView(audio);
            }
            case "skin", "playerskin", "player_skin" ->
                    node.type == SceneNodeType.PLAYER ? nullToEmpty(node.texture) : null;
            case "script" -> node.script == null ? "" : node.script;
            case "runonscenestart", "run_on_scene_start" -> node.runOnSceneStart;
            case "routefile", "route_file", "file" ->
                    node.type == SceneNodeType.ROUTE ? nullToEmpty(node.routeFile) : null;
            case "routemode", "route_mode", "mode" ->
                    node.type == SceneNodeType.ROUTE ? SceneRouteStorage.normalizeMode(node.routeMode) : null;
            case "routeclosed", "route_closed", "closed", "loop" ->
                    node.type == SceneNodeType.ROUTE ? node.routeClosed : null;
            case "routecurve", "route_curve", "curve", "flight" ->
                    node.type == SceneNodeType.ROUTE
                            ? SceneRouteStorage.normalizeCurve(node.routeCurve) : null;
            case "routesmoothness", "route_smoothness", "smoothness" ->
                    node.type == SceneNodeType.ROUTE ? (double) node.routeSmoothness : null;
            case "spacing", "layoutspacing", "layout_spacing" ->
                    node.type == SceneNodeType.ROUTE ? (double) node.routeLayoutSpacing : null;
            case "offset", "layoutoffset", "layout_offset" ->
                    node.type == SceneNodeType.ROUTE ? (double) node.routeLayoutOffset : null;
            case "layoutanchor", "layout_anchor", "anchor" ->
                    node.type == SceneNodeType.ROUTE ? SceneRouteLayout.normalizeAnchor(node.routeLayoutAnchor) : null;
            case "layoutoverflow", "layout_overflow", "overflow" ->
                    node.type == SceneNodeType.ROUTE ? SceneRouteLayout.normalizeOverflow(node.routeLayoutOverflow) : null;
            case "layoutreverse", "layout_reverse", "reverse" ->
                    node.type == SceneNodeType.ROUTE ? node.routeLayoutReverse : null;
            case "alignyaw", "align_yaw" ->
                    node.type == SceneNodeType.ROUTE ? node.routeLayoutAlignYaw : null;
            case "alignpitch", "align_pitch" ->
                    node.type == SceneNodeType.ROUTE ? node.routeLayoutAlignPitch : null;
            case "layoutcount", "layout_count" ->
                    node.type == SceneNodeType.ROUTE ? SceneRouteLayout.children(node).size() : null;
            case "pointcount", "point_count" ->
                    node.type == SceneNodeType.ROUTE ? SceneRouteOps.points(node).size() : null;
            case "tags" -> SceneNodeTags.copyTags(node);
            case "tagcount", "tag_count" -> SceneNodeTags.tagCount(node);
            case "model" -> entity != null ? GeoEntityScriptUtil.getModel(entity) : nullToEmpty(node.model);
            case "texture" -> entity != null ? GeoEntityScriptUtil.getTexture(entity) : nullToEmpty(node.texture);
            case "animation" -> entity != null ? GeoEntityScriptUtil.getAnimation(entity) : nullToEmpty(node.animation);
            case "idleanim", "idle_anim" -> nullToEmpty(node.idleAnimation);
            case "walkanim", "walk_anim" -> nullToEmpty(node.walkAnimation);
            case "x" -> entity != null ? entity.getX() : worldField(node, "x");
            case "y" -> entity != null ? entity.getY() : worldField(node, "y");
            case "z" -> entity != null ? entity.getZ() : worldField(node, "z");
            case "yaw" -> {
                if (node.type == SceneNodeType.ROUTE_POINT) yield worldField(node, "rotationY");
                yield entity != null ? entity.getYRot() : worldField(node, "rotationY");
            }
            case "pitch" -> {
                if (node.type == SceneNodeType.ROUTE_POINT) yield worldField(node, "rotationX");
                yield entity != null ? entity.getXRot() : worldField(node, "rotationX");
            }
            case "haslook", "has_look", "look", "routehaslook", "route_has_look" ->
                    node.type == SceneNodeType.ROUTE_POINT ? node.routeHasLook : null;
            case "routeindex", "route_index", "routepointindex", "route_point_index" ->
                    entity instanceof SprauteNpcEntity snpc ? snpc.getRouteIndex() : null;
            case "routepointcount", "route_point_count" ->
                    entity instanceof SprauteNpcEntity snpc ? snpc.getRoutePointCount() : null;
            case "followingroute", "following_route", "isfollowingroute", "is_following_route" ->
                    entity instanceof SprauteNpcEntity snpc ? snpc.isFollowingRoute() : null;
            case "rotationx", "rotx" -> entity != null
                    ? GeoEntityScriptUtil.getModelRotationX(entity) : worldField(node, "rotationX");
            case "rotationy", "roty" -> entity != null
                    ? entity.getYRot() : worldField(node, "rotationY");
            case "rotationz", "rotz", "roll" -> entity != null
                    ? GeoEntityScriptUtil.getModelRotationZ(entity) : worldField(node, "rotationZ");
            case "scalex" -> entity != null
                    ? GeoEntityScriptUtil.getModelScaleX(entity) : worldField(node, "scaleX");
            case "scaley" -> entity != null
                    ? GeoEntityScriptUtil.getModelScaleY(entity) : worldField(node, "scaleY");
            case "scalez" -> entity != null
                    ? GeoEntityScriptUtil.getModelScaleZ(entity) : worldField(node, "scaleZ");
            case "scale" -> {
                if (entity != null) {
                    yield (GeoEntityScriptUtil.getModelScaleX(entity)
                            + GeoEntityScriptUtil.getModelScaleY(entity)
                            + GeoEntityScriptUtil.getModelScaleZ(entity)) / 3.0;
                }
                yield worldField(node, "scaleX");
            }
            case "showname", "show_name" -> node.nameVisible;
            case "invulnerable" -> node.invulnerable;
            case "silent" -> node.silent;
            case "glowing" -> node.glowing;
            case "translucent", "transparent" -> entity != null && GeoEntityScriptUtil.isGeoEntity(entity)
                    ? GeoEntityScriptUtil.isTranslucent(entity) : node.translucent;
            case "opacity" -> entity != null && GeoEntityScriptUtil.isGeoEntity(entity)
                    ? GeoEntityScriptUtil.getOpacity(entity) : node.opacity;
            case "lod", "lodenabled", "lod_enabled" -> entity != null && GeoEntityScriptUtil.isGeoEntity(entity)
                    ? GeoEntityScriptUtil.isLodEnabled(entity) : node.lodEnabled;
            case "renderdistance", "render_distance" -> entity != null && GeoEntityScriptUtil.isGeoEntity(entity)
                    ? GeoEntityScriptUtil.getRenderDistance(entity) : node.renderDistance;
            case "forcevisible", "force_visible" -> entity != null && GeoEntityScriptUtil.isGeoEntity(entity)
                    ? GeoEntityScriptUtil.isForceVisible(entity) : node.forceVisible;
            case "loadchunks", "load_chunks", "keepchunks", "keep_chunks", "keeploaded", "keep_loaded" ->
                    entity instanceof org.zonarstudio.spraute_engine.entity.SprauteNpcEntity npc
                            ? npc.isLoadChunks() : node.loadChunks;
            case "nogravity", "no_gravity" -> node.noGravity;
            case "collision", "collide" -> node.type == SceneNodeType.PARTICLE
                    ? node.particleCollide
                    : node.collision;
            case "hp", "maxhp", "max_hp" -> {
                if (entity instanceof LivingEntity living) {
                    if ("hp".equals(prop)) yield living.getHealth();
                    var attr = living.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
                    yield attr != null ? attr.getBaseValue() : living.getMaxHealth();
                }
                yield node.maxHealth;
            }
            case "speed" -> {
                if (entity instanceof LivingEntity living) {
                    var attr = living.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
                    yield attr != null ? attr.getBaseValue() : node.movementSpeed;
                }
                yield node.movementSpeed;
            }
            case "walkturnspeed", "walk_turn_speed" -> {
                if (entity instanceof SprauteNpcEntity npc) yield npc.getWalkTurnSpeed();
                yield node.walkTurnSpeed;
            }
            case "idleturnspeed", "idle_turn_speed" -> {
                if (entity instanceof SprauteNpcEntity npc) yield npc.getIdleTurnSpeed();
                yield node.idleTurnSpeed;
            }
            case "patharriveradius", "path_arrive_radius", "arriveradius", "arrive_radius" -> {
                if (entity instanceof SprauteNpcEntity npc) yield npc.groundPathSettings().arriveRadius;
                yield node.pathArriveRadius;
            }
            case "patharrivevert", "path_arrive_vert" -> {
                if (entity instanceof SprauteNpcEntity npc) yield npc.groundPathSettings().arriveVert;
                yield node.pathArriveVert;
            }
            case "pathstepheight", "path_step_height", "stepheight" -> {
                if (entity instanceof SprauteNpcEntity npc) yield npc.groundPathSettings().stepHeight;
                yield node.pathStepHeight;
            }
            case "pathmaxjump", "path_max_jump", "maxjump" -> {
                if (entity instanceof SprauteNpcEntity npc) yield npc.groundPathSettings().maxJump;
                yield node.pathMaxJump;
            }
            case "pathmaxdrop", "path_max_drop", "maxdrop" -> {
                if (entity instanceof SprauteNpcEntity npc) yield npc.groundPathSettings().maxDrop;
                yield node.pathMaxDrop;
            }
            case "pathmaxrange", "path_max_range", "pathrange" -> {
                if (entity instanceof SprauteNpcEntity npc) yield npc.groundPathSettings().maxRange;
                yield node.pathMaxRange;
            }
            case "pathdiagonal", "path_diagonal", "pathallowdiagonal" -> {
                if (entity instanceof SprauteNpcEntity npc) yield npc.groundPathSettings().allowDiagonal;
                yield node.pathDiagonal;
            }
            case "pathavoidwater", "path_avoid_water", "avoidwater" -> {
                if (entity instanceof SprauteNpcEntity npc) yield npc.groundPathSettings().avoidWater;
                yield node.pathAvoidWater;
            }
            case "pathavoidlava", "path_avoid_lava", "avoidlava" -> {
                if (entity instanceof SprauteNpcEntity npc) yield npc.groundPathSettings().avoidLava;
                yield node.pathAvoidLava;
            }
            case "pathmaxnodes", "path_max_nodes", "pathnodes" -> {
                if (entity instanceof SprauteNpcEntity npc) yield npc.groundPathSettings().maxNodes;
                yield node.pathMaxNodes;
            }
            case "pathsnaponarrive", "path_snap_on_arrive", "pathsnap" -> {
                if (entity instanceof SprauteNpcEntity npc) yield npc.groundPathSettings().snapOnArrive;
                yield node.pathSnapOnArrive;
            }
            case "flyarriveradius", "fly_arrive_radius", "flyarrive" -> {
                if (entity instanceof SprauteNpcEntity npc) yield npc.airPathSettings().arriveRadius;
                yield node.flyArriveRadius;
            }
            case "flymaxrange", "fly_max_range", "flypathrange" -> {
                if (entity instanceof SprauteNpcEntity npc) yield npc.airPathSettings().maxRange;
                yield node.flyMaxRange;
            }
            case "flydiagonal", "fly_diagonal", "flyallowdiagonal" -> {
                if (entity instanceof SprauteNpcEntity npc) yield npc.airPathSettings().allowDiagonal;
                yield node.flyDiagonal;
            }
            case "flyavoidwater", "fly_avoid_water" -> {
                if (entity instanceof SprauteNpcEntity npc) yield npc.airPathSettings().avoidWater;
                yield node.flyAvoidWater;
            }
            case "flyavoidlava", "fly_avoid_lava" -> {
                if (entity instanceof SprauteNpcEntity npc) yield npc.airPathSettings().avoidLava;
                yield node.flyAvoidLava;
            }
            case "flymaxnodes", "fly_max_nodes" -> {
                if (entity instanceof SprauteNpcEntity npc) yield npc.airPathSettings().maxNodes;
                yield node.flyMaxNodes;
            }
            case "flysnaponarrive", "fly_snap_on_arrive", "flysnap" -> {
                if (entity instanceof SprauteNpcEntity npc) yield npc.airPathSettings().snapOnArrive;
                yield node.flySnapOnArrive;
            }
            case "solid", "hitboxsolid", "hitbox_solid" -> {
                SceneHitboxRuntime hb = hitboxRuntime();
                yield hb != null ? hb.solid : node.hitboxSolid;
            }
            case "trigger", "hitboxtrigger", "hitbox_trigger" -> {
                SceneHitboxRuntime hb = hitboxRuntime();
                yield hb != null ? hb.trigger : node.hitboxTrigger;
            }
            case "triggerid", "trigger_id", "hitboxtriggerid", "hitbox_trigger_id" ->
                    nullToEmpty(node.hitboxTriggerId);
            case "w", "width" -> {
                Object billboard = SceneNodeScriptProps.getBillboardSizeAlias(node, prop);
                if (billboard != null) yield billboard;
                if (entity instanceof SprauteNpcEntity npc) yield npc.getHitboxWidth();
                SceneHitboxRuntime hb = hitboxRuntime();
                yield hb != null ? hb.width : node.hitboxWidth;
            }
            case "hitboxw", "hitboxwidth", "hitbox_width" -> {
                if (entity instanceof SprauteNpcEntity npc) yield npc.getHitboxWidth();
                SceneHitboxRuntime hb = hitboxRuntime();
                yield hb != null ? hb.width : node.hitboxWidth;
            }
            case "h", "height" -> {
                Object billboard = SceneNodeScriptProps.getBillboardSizeAlias(node, prop);
                if (billboard != null) yield billboard;
                if (entity instanceof SprauteNpcEntity npc) yield npc.getHitboxHeight();
                SceneHitboxRuntime hb = hitboxRuntime();
                yield hb != null ? hb.height : node.hitboxHeight;
            }
            case "hitboxh", "hitboxheight", "hitbox_height" -> {
                if (entity instanceof SprauteNpcEntity npc) yield npc.getHitboxHeight();
                SceneHitboxRuntime hb = hitboxRuntime();
                yield hb != null ? hb.height : node.hitboxHeight;
            }
            case "depth", "hitboxdepth", "hitbox_depth" -> {
                SceneHitboxRuntime hb = hitboxRuntime();
                yield hb != null ? hb.depth : node.hitboxDepth;
            }
            case "offsetx", "hitboxoffsetx", "hitbox_offset_x" -> {
                if (entity instanceof SprauteNpcEntity npc) yield npc.getHitboxOffsetX();
                SceneHitboxRuntime hb = hitboxRuntime();
                yield hb != null ? hb.offsetX : node.hitboxOffsetX;
            }
            case "offsety", "hitboxoffsety", "hitbox_offset_y" -> {
                if (entity instanceof SprauteNpcEntity npc) yield npc.getHitboxOffsetY();
                SceneHitboxRuntime hb = hitboxRuntime();
                yield hb != null ? hb.offsetY : node.hitboxOffsetY;
            }
            case "offsetz", "hitboxoffsetz", "hitbox_offset_z" -> {
                if (entity instanceof SprauteNpcEntity npc) yield npc.getHitboxOffsetZ();
                SceneHitboxRuntime hb = hitboxRuntime();
                yield hb != null ? hb.offsetZ : node.hitboxOffsetZ;
            }
            case "touching" -> {
                SceneHitboxRuntime hb = hitboxRuntime();
                if (hb == null) yield false;
                Player player = source != null ? ItemStackScriptUtil.resolvePlayer(null, source) : null;
                yield player != null && hb.isTouching(player.getUUID());
            }
            case "level", "lightlevel", "light_level", "light" -> node.lightLevel;
            case "enabled", "lightenabled", "light_enabled", "on" -> {
                if (node.type == SceneNodeType.POST_PROCESS) yield node.post().enabled;
                yield node.type == SceneNodeType.AUDIO_SOURCE ? node.audioEnabled : node.lightEnabled;
            }
            case "showlight", "show_light" -> node.showLight;
            case "audiosound", "audio_sound" ->
                    node.type == SceneNodeType.AUDIO_SOURCE ? node.audioSound : null;
            case "audioshape", "audio_shape" ->
                    node.type == SceneNodeType.AUDIO_SOURCE ? node.audioShape : null;
            case "audiofullradius", "audio_full_radius", "fullradius", "full_radius" ->
                    node.type == SceneNodeType.AUDIO_SOURCE ? node.audioFullRadius : null;
            case "audiomaxdistance", "audio_max_distance", "maxdistance", "max_distance" ->
                    node.type == SceneNodeType.AUDIO_SOURCE ? node.audioMaxDistance : null;
            case "audiovolume", "audio_volume" ->
                    node.type == SceneNodeType.AUDIO_SOURCE ? node.audioVolume : null;
            case "audiopitch", "audio_pitch" ->
                    node.type == SceneNodeType.AUDIO_SOURCE ? node.audioPitch : null;
            case "audioloop", "audio_loop" ->
                    node.type == SceneNodeType.AUDIO_SOURCE ? node.audioLoop : null;
            case "audioreplayonenter", "audio_replay_on_enter", "replayonenter", "replay_on_enter",
                 "playonenter", "play_on_enter", "restartonenter", "restart_on_enter" ->
                    node.type == SceneNodeType.AUDIO_SOURCE ? node.audioReplayOnEnter : null;
            case "audioenabled", "audio_enabled" ->
                    node.type == SceneNodeType.AUDIO_SOURCE ? node.audioEnabled : null;
            case "showaudio", "show_audio" ->
                    node.type == SceneNodeType.AUDIO_SOURCE ? node.showAudio : null;
            case "global", "isglobal", "is_global" ->
                    node.type == SceneNodeType.POST_PROCESS ? node.post().global : null;
            case "priority" ->
                    node.type == SceneNodeType.POST_PROCESS ? node.post().priority : null;
            case "weight" ->
                    node.type == SceneNodeType.POST_PROCESS ? node.post().weight : null;
            case "blenddistance", "blend_distance" ->
                    node.type == SceneNodeType.POST_PROCESS ? node.post().blendDistance : null;
            case "grade" ->
                    node.type == SceneNodeType.POST_PROCESS ? node.post().grade : null;
            case "exposure" ->
                    node.type == SceneNodeType.POST_PROCESS ? node.post().exposure : null;
            case "contrast" ->
                    node.type == SceneNodeType.POST_PROCESS ? node.post().contrast : null;
            case "saturation" ->
                    node.type == SceneNodeType.POST_PROCESS ? node.post().saturation : null;
            case "temperature" ->
                    node.type == SceneNodeType.POST_PROCESS ? node.post().temperature : null;
            case "tint" ->
                    node.type == SceneNodeType.POST_PROCESS ? node.post().tint : null;
            case "filterr", "filter_r" ->
                    node.type == SceneNodeType.POST_PROCESS ? node.post().filterR : null;
            case "filterg", "filter_g" ->
                    node.type == SceneNodeType.POST_PROCESS ? node.post().filterG : null;
            case "filterb", "filter_b" ->
                    node.type == SceneNodeType.POST_PROCESS ? node.post().filterB : null;
            case "vignette" ->
                    node.type == SceneNodeType.POST_PROCESS ? node.post().vignetteIntensity : null;
            case "vignetteintensity", "vignette_intensity" ->
                    node.type == SceneNodeType.POST_PROCESS ? node.post().vignetteIntensity : null;
            case "vignettesmooth", "vignette_smooth" ->
                    node.type == SceneNodeType.POST_PROCESS ? node.post().vignetteSmooth : null;
            case "bloom" ->
                    node.type == SceneNodeType.POST_PROCESS ? node.post().bloomIntensity : null;
            case "bloomintensity", "bloom_intensity" ->
                    node.type == SceneNodeType.POST_PROCESS ? node.post().bloomIntensity : null;
            case "bloomthreshold", "bloom_threshold" ->
                    node.type == SceneNodeType.POST_PROCESS ? node.post().bloomThreshold : null;
            case "chromatic", "chromaticaberration", "chromatic_aberration" ->
                    node.type == SceneNodeType.POST_PROCESS ? node.post().chromaticAmount : null;
            case "chromaticamount", "chromatic_amount" ->
                    node.type == SceneNodeType.POST_PROCESS ? node.post().chromaticAmount : null;
            case "grain" ->
                    node.type == SceneNodeType.POST_PROCESS ? node.post().grainAmount : null;
            case "grainamount", "grain_amount" ->
                    node.type == SceneNodeType.POST_PROCESS ? node.post().grainAmount : null;
            case "sharpen" ->
                    node.type == SceneNodeType.POST_PROCESS ? node.post().sharpenAmount : null;
            case "sharpenamount", "sharpen_amount" ->
                    node.type == SceneNodeType.POST_PROCESS ? node.post().sharpenAmount : null;
            case "showpost", "show_post" ->
                    node.type == SceneNodeType.POST_PROCESS ? node.showPost : null;
            case "customtype", "custom_type", "typeid", "type_id", "customtypeid" -> node.customTypeId;
            case "data", "customdata", "custom_data" -> node.customData;
            default -> {
                Object billboard = SceneNodeScriptProps.getBillboardSizeAlias(node, prop);
                if (billboard != null) yield billboard;
                Object extended = SceneNodeScriptProps.get(node, prop);
                if (extended != null) yield extended;
                if (node.customData != null) {
                    for (var e : node.customData.entrySet()) {
                        if (e.getKey() != null && e.getKey().equalsIgnoreCase(prop)) {
                            yield wrapCustomField(node, e.getKey(), e.getValue());
                        }
                    }
                }
                yield null;
            }
        };
    }

    public Object invokeMethod(String method, List<Object> args, CommandSourceStack source) {
        if (method == null) return null;
        String m = method.toLowerCase(Locale.ROOT);
        if (node() == null) return null;
        if (node().type == SceneNodeType.CAMERA) {
            if ("post".equals(m) || "postprocess".equals(m) || "post_process".equals(m)
                    || "fx".equals(m) || "effects".equals(m) || "ensurepost".equals(m)) {
                SceneNode post = PlayerCameraNodes.ensurePostProcess(state, node());
                return post == null ? null : state.nodeView(post);
            }
            Object cameraResult = PlayerCameraOps.invoke(state, node(), m, args, source);
            if (cameraResult != UNHANDLED) {
                return cameraResult == HANDLED_NULL ? null : cameraResult;
            }
        }
        if (node().type == SceneNodeType.AUDIO) {
            Object audioResult = PlayerAudioOps.invoke(state, node(), m, args, source);
            if (audioResult != UNHANDLED) {
                return audioResult == HANDLED_NULL ? null : audioResult;
            }
        }

            Object sceneResult = switch (m) {
                case "id", "nodeid", "getid" -> node().id;
                case "child", "get", "node" -> {
                    if (args.isEmpty()) yield null;
                    yield resolveRelative(String.valueOf(args.get(0)));
                }
                case "children" -> children();
            case "camera", "cam" -> {
                SceneNode self = node();
                if (self == null || self.type != SceneNodeType.PLAYER) yield UNHANDLED;
                SceneNode camera = PlayerCameraNodes.ensureCamera(self, state.document.ensurePlayersNode(),
                        state.document);
                yield camera == null ? null : state.nodeView(camera);
            }
            case "audio", "soundbus", "sound_bus" -> {
                SceneNode self = node();
                if (self == null || self.type != SceneNodeType.PLAYER) yield UNHANDLED;
                SceneNode audio = PlayerAudioNodes.ensureAudio(self, state.document);
                yield audio == null ? null : state.nodeView(audio);
            }
            case "addoverlay", "addskinoverlay" -> {
                SceneNode self = node();
                if (self == null || self.type != SceneNodeType.PLAYER) yield UNHANDLED;
                UUID uuid = playerUuid(self, source);
                if (uuid == null) yield null;
                String overlayId = args.size() >= 2 ? String.valueOf(args.get(0)) : "";
                String texture = args.isEmpty() ? "" : String.valueOf(args.get(args.size() >= 2 ? 1 : 0));
                yield PlayerSkinOps.addOverlay(uuid, overlayId, texture);
            }
            case "overlay", "skinoverlay" -> {
                SceneNode self = node();
                if (self == null || self.type != SceneNodeType.PLAYER || args.isEmpty()) yield UNHANDLED;
                UUID uuid = playerUuid(self, source);
                yield uuid == null ? null : PlayerSkinOps.overlayOf(uuid, String.valueOf(args.get(0)));
            }
            case "resetskin", "clearskin" -> {
                SceneNode self = node();
                if (self == null || self.type != SceneNodeType.PLAYER) yield UNHANDLED;
                UUID uuid = playerUuid(self, source);
                if (uuid != null) PlayerSkinOps.setBaseSkin(uuid, "");
                yield HANDLED_NULL;
            }
            case "hideskinlayer", "hideouterlayer", "showskinlayer", "showouterlayer",
                 "setskinlayer", "setouterlayer", "resetskinlayers", "showskinlayers",
                 "clearskinlayers", "isskinlayerhidden", "skinlayerhidden" -> {
                SceneNode self = node();
                if (self != null && self.type == SceneNodeType.SKIN_OVERLAY) {
                    Object overlay = PlayerSkinLayers.invokeOverlay(self, m, args);
                    if (overlay == PlayerSkinOps.UNHANDLED) yield UNHANDLED;
                    yield overlay == PlayerSkinOps.HANDLED_NULL ? HANDLED_NULL : overlay;
                }
                if (self == null || self.type != SceneNodeType.PLAYER) yield UNHANDLED;
                Entity live = entity(source);
                if (!(live instanceof Player player)) yield UNHANDLED;
                Object result = PlayerSkinLayers.invokePlayer(player, m, args);
                if (result == PlayerSkinOps.UNHANDLED) yield UNHANDLED;
                yield result == PlayerSkinOps.HANDLED_NULL ? HANDLED_NULL : result;
            }
                case "points" -> {
                    SceneNode route = node();
                    if (route == null || route.type != SceneNodeType.ROUTE) yield children();
                    List<SceneNodeView> out = new ArrayList<>();
                    for (SceneNode point : SceneRouteOps.points(route)) out.add(state.nodeView(point));
                    yield out;
                }
                case "pointcount", "point_count" -> {
                    SceneNode route = node();
                    yield route != null && route.type == SceneNodeType.ROUTE
                            ? SceneRouteOps.points(route).size() : 0;
                }
                case "point", "getpoint", "get_point" -> {
                    SceneNode route = node();
                    if (route == null || route.type != SceneNodeType.ROUTE || args.isEmpty()
                            || !(args.get(0) instanceof Number number)) yield null;
                    List<SceneNode> points = SceneRouteOps.points(route);
                    int index = number.intValue();
                    yield index >= 0 && index < points.size() ? state.nodeView(points.get(index)) : null;
                }
                case "pointcoords", "pointcoordinates", "point_coords", "coordinates" -> {
                    SceneNode route = node();
                    int index = !args.isEmpty() && args.get(0) instanceof Number number
                            ? number.intValue() : -1;
                    yield SceneRouteOps.coordinates(state.document, route, index);
                }
                case "addpoint", "add_point" -> {
                    SceneNode route = node();
                    if (route == null || route.type != SceneNodeType.ROUTE || args.size() < 3) yield null;
                    SceneNode point = SceneRouteOps.addPoint(state.document, route,
                            ((Number) args.get(0)).doubleValue(),
                            ((Number) args.get(1)).doubleValue(),
                            ((Number) args.get(2)).doubleValue());
                    saveRoute(route, source);
                    yield point == null ? null : state.nodeView(point);
                }
                case "removepoint", "remove_point" -> {
                    SceneNode route = node();
                    int index = !args.isEmpty() && args.get(0) instanceof Number number
                            ? number.intValue() : -1;
                    boolean removed = SceneRouteOps.removePoint(route, index);
                    if (removed) saveRoute(route, source);
                    yield removed;
                }
                case "setpoint", "set_point" -> {
                    SceneNode route = node();
                    if (args.size() < 4 || !(args.get(0) instanceof Number index)) yield false;
                    boolean changed = SceneRouteOps.setCoordinates(state.document, route, index.intValue(),
                            ((Number) args.get(1)).doubleValue(),
                            ((Number) args.get(2)).doubleValue(),
                            ((Number) args.get(3)).doubleValue());
                    if (changed) saveRoute(route, source);
                    yield changed;
                }
                case "setpointlook", "set_point_look" -> {
                    SceneNode route = node();
                    if (route == null || route.type != SceneNodeType.ROUTE || args.size() < 3
                            || !(args.get(0) instanceof Number index)) yield false;
                    List<SceneNode> pts = SceneRouteOps.points(route);
                    int i = index.intValue();
                    if (i < 0 || i >= pts.size()) yield false;
                    SceneNode point = pts.get(i);
                    point.routeHasLook = true;
                    point.transform.ry = ((Number) args.get(1)).doubleValue();
                    point.transform.rx = ((Number) args.get(2)).doubleValue();
                    saveRoute(route, source);
                    yield true;
                }
                case "clear", "clearpoints", "clear_points", "clearroute", "clear_route" -> {
                    SceneNode route = node();
                    if (route == null || route.type != SceneNodeType.ROUTE) yield UNHANDLED;
                    SceneRouteOps.clear(route);
                    saveRoute(route, source);
                    yield true;
                }
                case "saveroute", "save_route" -> {
                    SceneNode route = node();
                    yield route != null && route.type == SceneNodeType.ROUTE && saveRoute(route, source);
                }
                case "parent" -> parent();
                case "find" -> {
                    if (args.isEmpty()) yield null;
                    yield findDescendant(String.valueOf(args.get(0)));
                }
                case "object", "entity" -> entity(source);
                case "playonce", "playloop", "playfreeze", "stopoverlay", "stop" -> {
                    Entity e = entity(source);
                    if (e != null) GeoEntityScriptUtil.executeAnimMethod(e, m, args);
                    yield HANDLED_NULL;
                }
                case "teleport" -> {
                    if (args.size() >= 3) {
                        double x = ((Number) args.get(0)).doubleValue();
                        double y = ((Number) args.get(1)).doubleValue();
                        double z = ((Number) args.get(2)).doubleValue();
                        Entity target = entity(source);
                        if (node().type == SceneNodeType.PLAYER && target != null) {
                            target.teleportTo(x, y, z);
                        } else {
                            setWorldPosition(x, y, z, source);
                        }
                    }
                    yield HANDLED_NULL;
                }
                case "remove" -> {
                    SceneNode n = node();
                    if (n != null) {
                        // Close overlays for canvases inside the removed subtree first.
                        closeCanvasesInSubtree(n, source);
                        // Full live cleanup (document + managers) so reconcile cannot respawn.
                        SceneRuntime.removeLiveSubtree(state.sceneId, n.id);
                    }
                    yield HANDLED_NULL;
                }
                case "setparent", "set_parent", "reparent" -> {
                    if (args.isEmpty()) yield false;
                    Object a = args.get(0);
                    SceneNodeView target = a instanceof SceneNodeView v
                            ? v : state.nodeView(String.valueOf(a));
                    SceneNode self = node();
                    if (target == null || self == null) yield false;
                    SceneNode oldParent = state.document.findParent(self.id);
                    // Keep the node's world position across the reparent (Unity-style).
                    SceneTransform world = state.document.worldTransform(self.id);
                    boolean ok = state.document.reparent(self.id, target.getNodeId());
                    if (ok && world != null && self.hasTransform()) {
                        setWorldPosition(world.px, world.py, world.pz, source);
                    }
                    if (ok) {
                        if (oldParent != null && oldParent.type == SceneNodeType.PLAYER) {
                            PlayerSkinOps.applyFromPlayerNode(oldParent);
                        }
                        SceneNode newParent = target.node();
                        if (newParent != null && newParent.type == SceneNodeType.PLAYER) {
                            PlayerSkinOps.applyFromPlayerNode(newParent);
                        }
                    }
                    yield ok;
                }
                case "sethitbox" -> {
                    Entity e = entity(source);
                    if (e instanceof SprauteNpcEntity npc && args.size() >= 2) {
                        float w = ((Number) args.get(0)).floatValue();
                        float h = ((Number) args.get(1)).floatValue();
                        if (args.size() >= 5) {
                            npc.setHitbox(w, h,
                                    ((Number) args.get(2)).floatValue(),
                                    ((Number) args.get(3)).floatValue(),
                                    ((Number) args.get(4)).floatValue());
                        } else {
                            npc.setHitbox(w, h);
                        }
                        SceneNode n = node();
                        if (n != null) {
                            n.hitboxWidth = npc.getHitboxWidth();
                            n.hitboxHeight = npc.getHitboxHeight();
                            n.hitboxOffsetX = npc.getHitboxOffsetX();
                            n.hitboxOffsetY = npc.getHitboxOffsetY();
                            n.hitboxOffsetZ = npc.getHitboxOffsetZ();
                        }
                    }
                    yield HANDLED_NULL;
                }
                case "sethitboxoffset", "set_hitbox_offset" -> {
                    Entity e = entity(source);
                    if (e instanceof SprauteNpcEntity npc && args.size() >= 3) {
                        npc.setHitboxOffset(
                                ((Number) args.get(0)).floatValue(),
                                ((Number) args.get(1)).floatValue(),
                                ((Number) args.get(2)).floatValue());
                        SceneNode n = node();
                        if (n != null) {
                            n.hitboxOffsetX = npc.getHitboxOffsetX();
                            n.hitboxOffsetY = npc.getHitboxOffsetY();
                            n.hitboxOffsetZ = npc.getHitboxOffsetZ();
                        }
                    }
                    yield HANDLED_NULL;
                }
                case "sethitboxpreset", "set_hitbox_preset" -> {
                    Entity e = entity(source);
                    if (e instanceof SprauteNpcEntity npc && !args.isEmpty()) {
                        npc.setHitboxPreset(String.valueOf(args.get(0)));
                        SceneNode n = node();
                        if (n != null) {
                            n.hitboxWidth = npc.getHitboxWidth();
                            n.hitboxHeight = npc.getHitboxHeight();
                        }
                    }
                    yield HANDLED_NULL;
                }
                case "resethitbox", "reset_hitbox" -> {
                    Entity e = entity(source);
                    if (e instanceof SprauteNpcEntity npc) npc.resetHitbox();
                    yield HANDLED_NULL;
                }
                case "istouching", "is_touching", "touching" -> {
                    SceneHitboxRuntime hb = hitboxRuntime();
                    if (hb == null || args.isEmpty()) yield false;
                    Player player = ItemStackScriptUtil.resolvePlayer(args.get(0), source);
                    yield player != null && hb.isTouching(player.getUUID());
                }
                case "issolid", "is_solid", "issolidfor", "is_solid_for" -> {
                    SceneHitboxRuntime hb = hitboxRuntime();
                    SceneNode n = node();
                    if (hb == null) yield n != null && n.hitboxSolid;
                    if (args.isEmpty()) {
                        Player player = ItemStackScriptUtil.resolvePlayer(null, source);
                        yield player == null || hb.isSolidFor(player.getUUID());
                    }
                    Player player = ItemStackScriptUtil.resolvePlayer(args.get(0), source);
                    yield player != null && hb.isSolidFor(player.getUUID());
                }
                case "setsolidforplayer", "set_solid_for_player", "setsolidfor", "set_solid_for" -> {
                    SceneHitboxRuntime hb = hitboxRuntime();
                    if (hb != null && args.size() >= 2) {
                        Player player = ItemStackScriptUtil.resolvePlayer(args.get(0), source);
                        if (player != null) hb.setSolidForPlayer(player.getUUID(), isTruthy(args.get(1)));
                    }
                    yield HANDLED_NULL;
                }
                case "clearsolidforplayer", "clear_solid_for_player", "clearsolidfor", "clear_solid_for" -> {
                    SceneHitboxRuntime hb = hitboxRuntime();
                    if (hb != null && !args.isEmpty()) {
                        Player player = ItemStackScriptUtil.resolvePlayer(args.get(0), source);
                        if (player != null) hb.clearSolidForPlayer(player.getUUID());
                    }
                    yield HANDLED_NULL;
                }
                case "touchingplayers", "touching_players" -> {
                    SceneHitboxRuntime hb = hitboxRuntime();
                    if (hb == null) yield List.of();
                    List<String> names = new ArrayList<>();
                    if (source != null && source.getLevel() != null && source.getLevel().getServer() != null) {
                        for (UUID uuid : hb.touchingPlayers()) {
                            Player player = source.getLevel().getServer().getPlayerList().getPlayer(uuid);
                            names.add(player != null ? player.getName().getString() : uuid.toString());
                        }
                    }
                    yield names;
                }
                case "hastag", "has_tag" -> {
                    if (args.isEmpty()) yield false;
                    SceneNode n = node();
                    yield n != null && SceneNodeTags.hasTag(n, String.valueOf(args.get(0)));
                }
                case "addtag", "add_tag" -> {
                    if (!args.isEmpty()) {
                        SceneNode n = node();
                        if (n != null) SceneNodeTags.addTag(n, String.valueOf(args.get(0)));
                    }
                    yield HANDLED_NULL;
                }
                case "removetag", "remove_tag" -> {
                    if (!args.isEmpty()) {
                        SceneNode n = node();
                        if (n != null) SceneNodeTags.removeTag(n, String.valueOf(args.get(0)));
                    }
                    yield HANDLED_NULL;
                }
                case "cleartags", "clear_tags" -> {
                    SceneNode n = node();
                    if (n != null) SceneNodeTags.clearTags(n);
                    yield HANDLED_NULL;
                }
                case "settags", "set_tags" -> {
                    SceneNode n = node();
                    if (n != null && !args.isEmpty()) SceneNodeTags.setTags(n, args.get(0));
                    yield HANDLED_NULL;
                }
                case "findbytag", "find_by_tag" -> {
                    if (args.isEmpty()) yield null;
                    SceneNode base = node();
                    if (base == null) yield null;
                    SceneNode found = SceneNodeTags.findByTag(base, String.valueOf(args.get(0)));
                    yield found == null ? null : state.nodeView(found);
                }
                case "nodeswithtag", "nodes_with_tag" -> nodesWithTag(String.valueOf(args.isEmpty() ? "" : args.get(0)));
                case "emit", "burst", "trigger", "fire", "emitparticles", "emit_particles", "playparticles", "play_particles" -> {
                    SceneNode n = node();
                    if (n != null && n.type == SceneNodeType.PARTICLE) {
                        SceneParticleRuntime rt = state.particleRuntime(nodeId);
                        if (rt == null) {
                            state.registerParticle(n);
                            rt = state.particleRuntime(nodeId);
                        }
                        ServerLevel level = resolveLevel(source);
                        if (rt != null && level != null) rt.emitNow(state, level);
                    } else if (SceneWorldCanvasRuntime.isWorldCanvas(n)) {
                        // World-space canvas: rebuild the panel's content right now instead of
                        // waiting for its refresh interval.
                        SceneWorldCanvasRuntime.forceRebuild(state, n);
                    } else if (n != null && n.type == SceneNodeType.CUSTOM) {
                        // Same generic trigger for Java-declared GenericNodeType nodes
                        // (see GenericNodeTypeRegistry) — one "emit" verb for every node kind.
                        ServerLevel level = resolveLevel(source);
                        if (level != null) {
                            org.zonarstudio.spraute_engine.scene.custom.GenericNodeTypeRegistry.fireEmit(state, level, n);
                        }
                    }
                    yield HANDLED_NULL;
                }
                case "play", "start", "startplay", "start_play", "restart", "playaudio", "play_audio" -> {
                    SceneNode n = node();
                    if (n != null && n.type == SceneNodeType.AUDIO_SOURCE) {
                        SceneAudioRuntime rt = state.audioRuntime(nodeId);
                        if (rt == null) {
                            state.registerAudioSource(n);
                            rt = state.audioRuntime(nodeId);
                        }
                        ServerLevel level = resolveLevel(source);
                        if (rt != null && level != null) rt.requestPlay(state, level, n);
                        yield HANDLED_NULL;
                    }
                    yield UNHANDLED;
                }
                default -> UNHANDLED;
            };

            if (sceneResult != UNHANDLED) {
                return sceneResult == HANDLED_NULL ? null : sceneResult;
            }

        // Scene-specific API done — ScriptExecutor unwraps to the spawned NPC/model
        // and runs the same method table as create npc / create model.
        return UNHANDLED;
    }

    public boolean setProperty(String key, Object value, CommandSourceStack source) {
        SceneNode node = node();
        if (node == null || key == null) return false;
        String prop = key.toLowerCase(Locale.ROOT);
        Entity entity = entity(source);
        if (node.type == SceneNodeType.CAMERA) {
            return PlayerCameraOps.set(state, node, prop, value, source);
        }
        if (node.type == SceneNodeType.AUDIO) {
            return PlayerAudioOps.set(state, node, prop, value, source);
        }
        if (node.type == SceneNodeType.SKIN_OVERLAY
                && PlayerSkinLayers.setOverlayProperty(node, prop, asBoolean(value))) {
            PlayerSkinOps.onNodeChanged(node);
            return true;
        }
        if (node.type == SceneNodeType.PLAYER && entity instanceof net.minecraft.world.entity.player.Player player) {
            return !isStructuralPlayerProperty(prop)
                    && org.zonarstudio.spraute_engine.script.ScriptPropertyAccess.set(player, key, value, source);
        }
        if (node.type == SceneNodeType.PLAYER
                && ("skin".equals(prop) || "texture".equals(prop) || "playerskin".equals(prop)
                || "player_skin".equals(prop))) {
            node.texture = value == null ? "" : String.valueOf(value).trim();
            if ("null".equalsIgnoreCase(node.texture)) node.texture = "";
            PlayerSkinOps.onNodeChanged(node);
            return true;
        }
        if (node.type == SceneNodeType.ROUTE_POINT) {
            if ("haslook".equals(prop) || "has_look".equals(prop) || "look".equals(prop)
                    || "routehaslook".equals(prop) || "route_has_look".equals(prop)) {
                node.routeHasLook = asBoolean(value);
                SceneNode route = state.document.findParent(node.id);
                if (route != null && route.type == SceneNodeType.ROUTE) saveRoute(route, source);
                return true;
            }
            if ("yaw".equals(prop) && value instanceof Number n) {
                node.transform.ry = n.doubleValue();
                node.routeHasLook = true;
                SceneNode route = state.document.findParent(node.id);
                if (route != null && route.type == SceneNodeType.ROUTE) saveRoute(route, source);
                return true;
            }
            if ("pitch".equals(prop) && value instanceof Number n) {
                node.transform.rx = n.doubleValue();
                node.routeHasLook = true;
                SceneNode route = state.document.findParent(node.id);
                if (route != null && route.type == SceneNodeType.ROUTE) saveRoute(route, source);
                return true;
            }
            if (!"x".equals(prop) && !"y".equals(prop) && !"z".equals(prop)) {
                return false;
            }
        }

        switch (prop) {
            case "id", "nodeid", "node_id", "uuid", "type",
                    "parent", "parentid", "parent_id", "children",
                    "scene", "sceneid", "scene_id",
                    "object", "entity", "hasobject", "has_object",
                    "childcount", "child_count", "childrencount" -> {
                return false;
            }
            case "routefile", "route_file", "file" -> {
                if (node.type != SceneNodeType.ROUTE) return false;
                node.routeFile = String.valueOf(value);
                saveRoute(node, source);
                return true;
            }
            case "routemode", "route_mode", "mode" -> {
                if (node.type != SceneNodeType.ROUTE) return false;
                node.routeMode = SceneRouteStorage.normalizeMode(String.valueOf(value));
                saveRoute(node, source);
                return true;
            }
            case "routeclosed", "route_closed", "closed", "loop" -> {
                if (node.type != SceneNodeType.ROUTE) return false;
                node.routeClosed = asBoolean(value);
                saveRoute(node, source);
                syncSubtreeEntities(node, source);
                return true;
            }
            case "routecurve", "route_curve", "curve", "flight" -> {
                if (node.type != SceneNodeType.ROUTE) return false;
                node.routeCurve = SceneRouteStorage.normalizeCurve(String.valueOf(value));
                saveRoute(node, source);
                syncSubtreeEntities(node, source);
                return true;
            }
            case "routesmoothness", "route_smoothness", "smoothness" -> {
                if (node.type != SceneNodeType.ROUTE || !(value instanceof Number number)) return false;
                node.routeSmoothness = SceneRouteStorage.clampSmoothnessEdit(number.floatValue());
                saveRoute(node, source);
                syncSubtreeEntities(node, source);
                return true;
            }
            case "spacing", "layoutspacing", "layout_spacing" -> {
                if (node.type != SceneNodeType.ROUTE || !(value instanceof Number number)) return false;
                node.routeLayoutSpacing = Math.max(0f, number.floatValue());
                saveRoute(node, source);
                syncSubtreeEntities(node, source);
                return true;
            }
            case "offset", "layoutoffset", "layout_offset" -> {
                if (node.type != SceneNodeType.ROUTE || !(value instanceof Number number)) return false;
                node.routeLayoutOffset = number.floatValue();
                saveRoute(node, source);
                syncSubtreeEntities(node, source);
                return true;
            }
            case "layoutanchor", "layout_anchor", "anchor" -> {
                if (node.type != SceneNodeType.ROUTE) return false;
                node.routeLayoutAnchor = SceneRouteLayout.normalizeAnchor(String.valueOf(value));
                saveRoute(node, source);
                syncSubtreeEntities(node, source);
                return true;
            }
            case "layoutoverflow", "layout_overflow", "overflow" -> {
                if (node.type != SceneNodeType.ROUTE) return false;
                node.routeLayoutOverflow = SceneRouteLayout.normalizeOverflow(String.valueOf(value));
                saveRoute(node, source);
                syncSubtreeEntities(node, source);
                return true;
            }
            case "layoutreverse", "layout_reverse", "reverse" -> {
                if (node.type != SceneNodeType.ROUTE) return false;
                node.routeLayoutReverse = asBoolean(value);
                saveRoute(node, source);
                syncSubtreeEntities(node, source);
                return true;
            }
            case "alignyaw", "align_yaw" -> {
                if (node.type != SceneNodeType.ROUTE) return false;
                node.routeLayoutAlignYaw = asBoolean(value);
                saveRoute(node, source);
                syncSubtreeEntities(node, source);
                return true;
            }
            case "alignpitch", "align_pitch" -> {
                if (node.type != SceneNodeType.ROUTE) return false;
                node.routeLayoutAlignPitch = asBoolean(value);
                saveRoute(node, source);
                syncSubtreeEntities(node, source);
                return true;
            }
            case "model" -> {
                if (entity != null) GeoEntityScriptUtil.setModel(entity, String.valueOf(value));
                node.model = String.valueOf(value);
                return true;
            }
            case "texture" -> {
                if (entity != null) GeoEntityScriptUtil.setTexture(entity, String.valueOf(value));
                node.texture = String.valueOf(value);
                if (node.type == SceneNodeType.SKIN_OVERLAY || node.type == SceneNodeType.PLAYER) {
                    PlayerSkinOps.onNodeChanged(node);
                }
                return true;
            }
            case "animation" -> {
                if (entity != null) GeoEntityScriptUtil.setAnimation(entity, String.valueOf(value));
                node.animation = String.valueOf(value);
                return true;
            }
            case "idleanim", "idle_anim" -> {
                node.idleAnimation = String.valueOf(value);
                if (entity instanceof SprauteNpcEntity npc) npc.setIdleAnim(String.valueOf(value));
                else if (entity instanceof SprauteModelEntity model) model.setIdleAnim(String.valueOf(value));
                return true;
            }
            case "walkanim", "walk_anim" -> {
                node.walkAnimation = String.valueOf(value);
                if (entity instanceof SprauteNpcEntity npc) npc.setWalkAnim(String.valueOf(value));
                return true;
            }
            case "name" -> {
                node.name = String.valueOf(value);
                if (entity != null) entity.setCustomName(net.minecraft.network.chat.Component.literal(node.name));
                if (node.type == SceneNodeType.SKIN_OVERLAY) PlayerSkinOps.onNodeChanged(node);
                return true;
            }
            case "showname", "show_name" -> {
                node.nameVisible = isTruthy(value);
                if (entity != null) entity.setCustomNameVisible(node.nameVisible);
                return true;
            }
            case "collision", "collide" -> {
                if (node.type == SceneNodeType.PARTICLE) {
                    node.particleCollide = isTruthy(value);
                    return true;
                }
                node.collision = isTruthy(value);
                if (entity instanceof SprauteNpcEntity npc) npc.setHasCollision(node.collision);
                return true;
            }
            case "hp", "maxhp", "max_hp" -> {
                if (!(value instanceof Number n)) return false;
                node.maxHealth = n.floatValue();
                if (entity instanceof LivingEntity living) {
                    var attr = living.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
                    if (attr != null) attr.setBaseValue(n.doubleValue());
                    if ("hp".equals(prop)) living.setHealth(n.floatValue());
                }
                return true;
            }
            case "speed" -> {
                if (!(value instanceof Number n)) return false;
                node.movementSpeed = n.floatValue();
                if (entity instanceof LivingEntity living) {
                    var attr = living.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
                    if (attr != null) attr.setBaseValue(n.doubleValue());
                }
                return true;
            }
            case "walkturnspeed", "walk_turn_speed" -> {
                if (!(value instanceof Number n)) return false;
                node.walkTurnSpeed = Math.max(0.1f, n.floatValue());
                if (entity instanceof SprauteNpcEntity npc) npc.setWalkTurnSpeed(node.walkTurnSpeed);
                return true;
            }
            case "idleturnspeed", "idle_turn_speed" -> {
                if (!(value instanceof Number n)) return false;
                node.idleTurnSpeed = Math.max(0.1f, n.floatValue());
                if (entity instanceof SprauteNpcEntity npc) npc.setIdleTurnSpeed(node.idleTurnSpeed);
                return true;
            }
            case "patharriveradius", "path_arrive_radius", "arriveradius", "arrive_radius" -> {
                if (!(value instanceof Number n)) return false;
                node.pathArriveRadius = n.floatValue();
                if (entity instanceof SprauteNpcEntity npc) {
                    npc.groundPathSettings().arriveRadius = n.doubleValue();
                    npc.groundPathSettings().clamp();
                }
                return true;
            }
            case "patharrivevert", "path_arrive_vert" -> {
                if (!(value instanceof Number n)) return false;
                node.pathArriveVert = n.floatValue();
                if (entity instanceof SprauteNpcEntity npc) {
                    npc.groundPathSettings().arriveVert = n.doubleValue();
                    npc.groundPathSettings().clamp();
                }
                return true;
            }
            case "pathstepheight", "path_step_height", "stepheight" -> {
                if (!(value instanceof Number n)) return false;
                node.pathStepHeight = n.floatValue();
                if (entity instanceof SprauteNpcEntity npc) {
                    npc.groundPathSettings().stepHeight = n.doubleValue();
                    npc.groundPathSettings().clamp();
                    npc.applyPathStepHeight();
                }
                return true;
            }
            case "pathmaxjump", "path_max_jump", "maxjump" -> {
                if (!(value instanceof Number n)) return false;
                node.pathMaxJump = n.intValue();
                if (entity instanceof SprauteNpcEntity npc) {
                    npc.groundPathSettings().maxJump = n.intValue();
                    npc.groundPathSettings().clamp();
                }
                return true;
            }
            case "pathmaxdrop", "path_max_drop", "maxdrop" -> {
                if (!(value instanceof Number n)) return false;
                node.pathMaxDrop = n.intValue();
                if (entity instanceof SprauteNpcEntity npc) {
                    npc.groundPathSettings().maxDrop = n.intValue();
                    npc.groundPathSettings().clamp();
                }
                return true;
            }
            case "pathmaxrange", "path_max_range", "pathrange" -> {
                if (!(value instanceof Number n)) return false;
                node.pathMaxRange = n.floatValue();
                if (entity instanceof SprauteNpcEntity npc) {
                    npc.groundPathSettings().maxRange = n.doubleValue();
                    npc.groundPathSettings().clamp();
                }
                return true;
            }
            case "pathmaxnodes", "path_max_nodes", "pathnodes" -> {
                if (!(value instanceof Number n)) return false;
                node.pathMaxNodes = n.intValue();
                if (entity instanceof SprauteNpcEntity npc) {
                    npc.groundPathSettings().maxNodes = n.intValue();
                    npc.groundPathSettings().clamp();
                }
                return true;
            }
            case "pathdiagonal", "path_diagonal", "pathallowdiagonal" -> {
                node.pathDiagonal = isTruthy(value);
                if (entity instanceof SprauteNpcEntity npc) npc.groundPathSettings().allowDiagonal = node.pathDiagonal;
                return true;
            }
            case "pathavoidwater", "path_avoid_water", "avoidwater" -> {
                node.pathAvoidWater = isTruthy(value);
                if (entity instanceof SprauteNpcEntity npc) npc.groundPathSettings().avoidWater = node.pathAvoidWater;
                return true;
            }
            case "pathavoidlava", "path_avoid_lava", "avoidlava" -> {
                node.pathAvoidLava = isTruthy(value);
                if (entity instanceof SprauteNpcEntity npc) npc.groundPathSettings().avoidLava = node.pathAvoidLava;
                return true;
            }
            case "pathsnaponarrive", "path_snap_on_arrive", "pathsnap" -> {
                node.pathSnapOnArrive = isTruthy(value);
                if (entity instanceof SprauteNpcEntity npc) npc.groundPathSettings().snapOnArrive = node.pathSnapOnArrive;
                return true;
            }
            case "flyarriveradius", "fly_arrive_radius", "flyarrive" -> {
                if (!(value instanceof Number n)) return false;
                node.flyArriveRadius = n.floatValue();
                if (entity instanceof SprauteNpcEntity npc) {
                    npc.airPathSettings().arriveRadius = n.doubleValue();
                    npc.airPathSettings().clamp();
                }
                return true;
            }
            case "flymaxrange", "fly_max_range", "flypathrange" -> {
                if (!(value instanceof Number n)) return false;
                node.flyMaxRange = n.floatValue();
                if (entity instanceof SprauteNpcEntity npc) {
                    npc.airPathSettings().maxRange = n.doubleValue();
                    npc.airPathSettings().clamp();
                }
                return true;
            }
            case "flymaxnodes", "fly_max_nodes" -> {
                if (!(value instanceof Number n)) return false;
                node.flyMaxNodes = n.intValue();
                if (entity instanceof SprauteNpcEntity npc) {
                    npc.airPathSettings().maxNodes = n.intValue();
                    npc.airPathSettings().clamp();
                }
                return true;
            }
            case "flydiagonal", "fly_diagonal", "flyallowdiagonal" -> {
                node.flyDiagonal = isTruthy(value);
                if (entity instanceof SprauteNpcEntity npc) npc.airPathSettings().allowDiagonal = node.flyDiagonal;
                return true;
            }
            case "flyavoidwater", "fly_avoid_water" -> {
                node.flyAvoidWater = isTruthy(value);
                if (entity instanceof SprauteNpcEntity npc) npc.airPathSettings().avoidWater = node.flyAvoidWater;
                return true;
            }
            case "flyavoidlava", "fly_avoid_lava" -> {
                node.flyAvoidLava = isTruthy(value);
                if (entity instanceof SprauteNpcEntity npc) npc.airPathSettings().avoidLava = node.flyAvoidLava;
                return true;
            }
            case "flysnaponarrive", "fly_snap_on_arrive", "flysnap" -> {
                node.flySnapOnArrive = isTruthy(value);
                if (entity instanceof SprauteNpcEntity npc) npc.airPathSettings().snapOnArrive = node.flySnapOnArrive;
                return true;
            }
            case "rotationx", "rotx" -> {
                if (!(value instanceof Number n)) return false;
                writeWorldRotationComponent("rotationX", n.doubleValue(), source);
                if (entity != null) GeoEntityScriptUtil.setModelRotationX(entity, n.floatValue());
                return true;
            }
            case "rotationy", "roty", "yaw" -> {
                if (!(value instanceof Number n)) return false;
                writeWorldRotationComponent("rotationY", n.doubleValue(), source);
                if (entity != null) {
                    entity.setYRot(n.floatValue());
                    if (entity instanceof LivingEntity living) {
                        living.yBodyRot = n.floatValue();
                        living.yHeadRot = n.floatValue();
                    }
                    // Keep model Y overlay at 0 so transform.rotationY == yaw.
                    if (GeoEntityScriptUtil.isGeoEntity(entity)) {
                        GeoEntityScriptUtil.setModelRotationY(entity, 0f);
                    }
                }
                return true;
            }
            case "rotationz", "rotz", "roll" -> {
                if (!(value instanceof Number n)) return false;
                writeWorldRotationComponent("rotationZ", n.doubleValue(), source);
                if (entity != null) GeoEntityScriptUtil.setModelRotationZ(entity, n.floatValue());
                return true;
            }
            case "pitch" -> {
                if (!(value instanceof Number n)) return false;
                if (entity != null) entity.setXRot(n.floatValue());
                return true;
            }
            case "scale" -> {
                if (!(value instanceof Number n)) return false;
                writeWorldScale(n.doubleValue(), n.doubleValue(), n.doubleValue(), source);
                if (entity != null) GeoEntityScriptUtil.setModelScale(entity, n.floatValue(), n.floatValue(), n.floatValue());
                return true;
            }
            case "scalex" -> {
                if (!(value instanceof Number n)) return false;
                double sy = entity != null ? GeoEntityScriptUtil.getModelScaleY(entity) : worldNumber(node, "scaleY", 1);
                double sz = entity != null ? GeoEntityScriptUtil.getModelScaleZ(entity) : worldNumber(node, "scaleZ", 1);
                writeWorldScale(n.doubleValue(), sy, sz, source);
                if (entity != null) GeoEntityScriptUtil.setModelScale(entity, n.floatValue(), (float) sy, (float) sz);
                return true;
            }
            case "scaley" -> {
                if (!(value instanceof Number n)) return false;
                double sx = entity != null ? GeoEntityScriptUtil.getModelScaleX(entity) : worldNumber(node, "scaleX", 1);
                double sz = entity != null ? GeoEntityScriptUtil.getModelScaleZ(entity) : worldNumber(node, "scaleZ", 1);
                writeWorldScale(sx, n.doubleValue(), sz, source);
                if (entity != null) GeoEntityScriptUtil.setModelScale(entity, (float) sx, n.floatValue(), (float) sz);
                return true;
            }
            case "scalez" -> {
                if (!(value instanceof Number n)) return false;
                double sx = entity != null ? GeoEntityScriptUtil.getModelScaleX(entity) : worldNumber(node, "scaleX", 1);
                double sy = entity != null ? GeoEntityScriptUtil.getModelScaleY(entity) : worldNumber(node, "scaleY", 1);
                writeWorldScale(sx, sy, n.doubleValue(), source);
                if (entity != null) GeoEntityScriptUtil.setModelScale(entity, (float) sx, (float) sy, n.floatValue());
                return true;
            }
            case "x", "y", "z" -> {
                if (!(value instanceof Number n)) return false;
                double x = entity != null ? entity.getX() : worldNumber(node, "x", 0);
                double y = entity != null ? entity.getY() : worldNumber(node, "y", 0);
                double z = entity != null ? entity.getZ() : worldNumber(node, "z", 0);
                switch (prop) {
                    case "x" -> x = n.doubleValue();
                    case "y" -> y = n.doubleValue();
                    case "z" -> z = n.doubleValue();
                }
                return setWorldPosition(x, y, z, source);
            }
            case "solid", "hitboxsolid", "hitbox_solid" -> {
                boolean v = isTruthy(value);
                node.hitboxSolid = v;
                SceneHitboxRuntime hb = hitboxRuntime();
                if (hb != null) hb.solid = v;
                return true;
            }
            case "trigger", "hitboxtrigger", "hitbox_trigger" -> {
                boolean v = isTruthy(value);
                node.hitboxTrigger = v;
                SceneHitboxRuntime hb = hitboxRuntime();
                if (hb != null) hb.trigger = v;
                return true;
            }
            case "triggerid", "trigger_id", "hitboxtriggerid", "hitbox_trigger_id" -> {
                node.hitboxTriggerId = value == null ? "" : String.valueOf(value).trim();
                return true;
            }
            case "w", "width" -> {
                if (SceneNodeScriptProps.setBillboardSizeAlias(node, prop, value)) return true;
                if (!(value instanceof Number n)) return false;
                node.hitboxWidth = n.floatValue();
                if (entity instanceof SprauteNpcEntity npc) {
                    npc.setHitbox(n.floatValue(), npc.getHitboxHeight(),
                            npc.getHitboxOffsetX(), npc.getHitboxOffsetY(), npc.getHitboxOffsetZ());
                }
                SceneHitboxRuntime hb = hitboxRuntime();
                if (hb != null) hb.width = n.floatValue();
                return true;
            }
            case "hitboxw", "hitboxwidth", "hitbox_width" -> {
                if (!(value instanceof Number n)) return false;
                node.hitboxWidth = n.floatValue();
                if (entity instanceof SprauteNpcEntity npc) {
                    npc.setHitbox(n.floatValue(), npc.getHitboxHeight(),
                            npc.getHitboxOffsetX(), npc.getHitboxOffsetY(), npc.getHitboxOffsetZ());
                }
                SceneHitboxRuntime hb = hitboxRuntime();
                if (hb != null) hb.width = n.floatValue();
                return true;
            }
            case "h", "height" -> {
                if (SceneNodeScriptProps.setBillboardSizeAlias(node, prop, value)) return true;
                if (!(value instanceof Number n)) return false;
                node.hitboxHeight = n.floatValue();
                if (entity instanceof SprauteNpcEntity npc) {
                    npc.setHitbox(npc.getHitboxWidth(), n.floatValue(),
                            npc.getHitboxOffsetX(), npc.getHitboxOffsetY(), npc.getHitboxOffsetZ());
                }
                SceneHitboxRuntime hb = hitboxRuntime();
                if (hb != null) hb.height = n.floatValue();
                return true;
            }
            case "hitboxh", "hitboxheight", "hitbox_height" -> {
                if (!(value instanceof Number n)) return false;
                node.hitboxHeight = n.floatValue();
                if (entity instanceof SprauteNpcEntity npc) {
                    npc.setHitbox(npc.getHitboxWidth(), n.floatValue(),
                            npc.getHitboxOffsetX(), npc.getHitboxOffsetY(), npc.getHitboxOffsetZ());
                }
                SceneHitboxRuntime hb = hitboxRuntime();
                if (hb != null) hb.height = n.floatValue();
                return true;
            }
            case "depth", "hitboxdepth", "hitbox_depth" -> {
                if (!(value instanceof Number n)) return false;
                node.hitboxDepth = n.floatValue();
                SceneHitboxRuntime hb = hitboxRuntime();
                if (hb != null) hb.depth = n.floatValue();
                return true;
            }
            case "offsetx", "hitboxoffsetx", "hitbox_offset_x" -> {
                if (!(value instanceof Number n)) return false;
                node.hitboxOffsetX = n.floatValue();
                if (entity instanceof SprauteNpcEntity npc) {
                    npc.setHitbox(npc.getHitboxWidth(), npc.getHitboxHeight(),
                            n.floatValue(), npc.getHitboxOffsetY(), npc.getHitboxOffsetZ());
                }
                SceneHitboxRuntime hb = hitboxRuntime();
                if (hb != null) hb.offsetX = n.floatValue();
                return true;
            }
            case "offsety", "hitboxoffsety", "hitbox_offset_y" -> {
                if (!(value instanceof Number n)) return false;
                node.hitboxOffsetY = n.floatValue();
                if (entity instanceof SprauteNpcEntity npc) {
                    npc.setHitbox(npc.getHitboxWidth(), npc.getHitboxHeight(),
                            npc.getHitboxOffsetX(), n.floatValue(), npc.getHitboxOffsetZ());
                }
                SceneHitboxRuntime hb = hitboxRuntime();
                if (hb != null) hb.offsetY = n.floatValue();
                return true;
            }
            case "offsetz", "hitboxoffsetz", "hitbox_offset_z" -> {
                if (!(value instanceof Number n)) return false;
                node.hitboxOffsetZ = n.floatValue();
                if (entity instanceof SprauteNpcEntity npc) {
                    npc.setHitbox(npc.getHitboxWidth(), npc.getHitboxHeight(),
                            npc.getHitboxOffsetX(), npc.getHitboxOffsetY(), n.floatValue());
                }
                SceneHitboxRuntime hb = hitboxRuntime();
                if (hb != null) hb.offsetZ = n.floatValue();
                return true;
            }
            case "tags" -> {
                SceneNodeTags.setTags(node, value);
                return true;
            }
            case "level", "lightlevel", "light_level", "light" -> {
                if (!(value instanceof Number n)) return false;
                node.lightLevel = Math.max(0, Math.min(15, n.intValue()));
                return true;
            }
            case "enabled", "lightenabled", "light_enabled", "on" -> {
                if (node.type == SceneNodeType.POST_PROCESS) {
                    node.post().enabled = isTruthy(value);
                    return finishPostEdit();
                }
                if (node.type == SceneNodeType.AUDIO_SOURCE) node.audioEnabled = isTruthy(value);
                else node.lightEnabled = isTruthy(value);
                return true;
            }
            case "showlight", "show_light" -> {
                node.showLight = isTruthy(value);
                return true;
            }
            case "audiosound", "audio_sound" -> {
                if (node.type != SceneNodeType.AUDIO_SOURCE) return false;
                node.audioSound = value == null ? "" : String.valueOf(value);
                return true;
            }
            case "audioshape", "audio_shape" -> {
                if (node.type != SceneNodeType.AUDIO_SOURCE) return false;
                node.audioShape = value == null ? "sphere" : String.valueOf(value);
                return true;
            }
            case "audiofullradius", "audio_full_radius", "fullradius", "full_radius" -> {
                if (node.type != SceneNodeType.AUDIO_SOURCE || !(value instanceof Number n)) return false;
                node.audioFullRadius = Math.max(0.05f, n.floatValue());
                return true;
            }
            case "audiomaxdistance", "audio_max_distance", "maxdistance", "max_distance" -> {
                if (node.type != SceneNodeType.AUDIO_SOURCE || !(value instanceof Number n)) return false;
                node.audioMaxDistance = Math.max(0.1f, n.floatValue());
                return true;
            }
            case "audiovolume", "audio_volume" -> {
                if (node.type != SceneNodeType.AUDIO_SOURCE || !(value instanceof Number n)) return false;
                node.audioVolume = Math.max(0f, n.floatValue());
                return true;
            }
            case "audiopitch", "audio_pitch" -> {
                if (node.type != SceneNodeType.AUDIO_SOURCE || !(value instanceof Number n)) return false;
                node.audioPitch = Math.max(0.01f, n.floatValue());
                return true;
            }
            case "audioloop", "audio_loop" -> {
                if (node.type != SceneNodeType.AUDIO_SOURCE) return false;
                node.audioLoop = isTruthy(value);
                return true;
            }
            case "audioreplayonenter", "audio_replay_on_enter", "replayonenter", "replay_on_enter",
                 "playonenter", "play_on_enter", "restartonenter", "restart_on_enter" -> {
                if (node.type != SceneNodeType.AUDIO_SOURCE) return false;
                node.audioReplayOnEnter = isTruthy(value);
                return true;
            }
            case "audioenabled", "audio_enabled" -> {
                if (node.type != SceneNodeType.AUDIO_SOURCE) return false;
                node.audioEnabled = isTruthy(value);
                return true;
            }
            case "showaudio", "show_audio" -> {
                if (node.type != SceneNodeType.AUDIO_SOURCE) return false;
                node.showAudio = isTruthy(value);
                return true;
            }
            case "global", "isglobal", "is_global" -> {
                if (node.type != SceneNodeType.POST_PROCESS) return false;
                node.post().global = isTruthy(value);
                return finishPostEdit();
            }
            case "priority" -> {
                if (node.type != SceneNodeType.POST_PROCESS || !(value instanceof Number n)) return false;
                node.post().priority = n.intValue();
                return finishPostEdit();
            }
            case "weight" -> {
                if (node.type != SceneNodeType.POST_PROCESS || !(value instanceof Number n)) return false;
                node.post().weight = n.floatValue();
                return finishPostEdit();
            }
            case "blenddistance", "blend_distance" -> {
                if (node.type != SceneNodeType.POST_PROCESS || !(value instanceof Number n)) return false;
                node.post().blendDistance = Math.max(0f, n.floatValue());
                return finishPostEdit();
            }
            case "grade" -> {
                if (node.type != SceneNodeType.POST_PROCESS) return false;
                node.post().grade = isTruthy(value);
                return finishPostEdit();
            }
            case "exposure" -> {
                if (node.type != SceneNodeType.POST_PROCESS || !(value instanceof Number n)) return false;
                node.post().grade = true;
                node.post().exposure = n.floatValue();
                return finishPostEdit();
            }
            case "contrast" -> {
                if (node.type != SceneNodeType.POST_PROCESS || !(value instanceof Number n)) return false;
                node.post().grade = true;
                node.post().contrast = n.floatValue();
                return finishPostEdit();
            }
            case "saturation" -> {
                if (node.type != SceneNodeType.POST_PROCESS || !(value instanceof Number n)) return false;
                node.post().grade = true;
                node.post().saturation = n.floatValue();
                return finishPostEdit();
            }
            case "temperature" -> {
                if (node.type != SceneNodeType.POST_PROCESS || !(value instanceof Number n)) return false;
                node.post().grade = true;
                node.post().temperature = n.floatValue();
                return finishPostEdit();
            }
            case "tint" -> {
                if (node.type != SceneNodeType.POST_PROCESS || !(value instanceof Number n)) return false;
                node.post().grade = true;
                node.post().tint = n.floatValue();
                return finishPostEdit();
            }
            case "filterr", "filter_r" -> {
                if (node.type != SceneNodeType.POST_PROCESS || !(value instanceof Number n)) return false;
                node.post().grade = true;
                node.post().filterR = n.floatValue();
                return finishPostEdit();
            }
            case "filterg", "filter_g" -> {
                if (node.type != SceneNodeType.POST_PROCESS || !(value instanceof Number n)) return false;
                node.post().grade = true;
                node.post().filterG = n.floatValue();
                return finishPostEdit();
            }
            case "filterb", "filter_b" -> {
                if (node.type != SceneNodeType.POST_PROCESS || !(value instanceof Number n)) return false;
                node.post().grade = true;
                node.post().filterB = n.floatValue();
                return finishPostEdit();
            }
            case "vignette" -> {
                if (node.type != SceneNodeType.POST_PROCESS) return false;
                if (value instanceof Number n) {
                    node.post().vignette = true;
                    node.post().vignetteIntensity = n.floatValue();
                } else {
                    node.post().vignette = isTruthy(value);
                }
                return finishPostEdit();
            }
            case "vignetteintensity", "vignette_intensity" -> {
                if (node.type != SceneNodeType.POST_PROCESS || !(value instanceof Number n)) return false;
                node.post().vignette = true;
                node.post().vignetteIntensity = n.floatValue();
                return finishPostEdit();
            }
            case "vignettesmooth", "vignette_smooth" -> {
                if (node.type != SceneNodeType.POST_PROCESS || !(value instanceof Number n)) return false;
                node.post().vignette = true;
                node.post().vignetteSmooth = n.floatValue();
                return finishPostEdit();
            }
            case "bloom" -> {
                if (node.type != SceneNodeType.POST_PROCESS) return false;
                if (value instanceof Number n) {
                    node.post().bloom = true;
                    node.post().bloomIntensity = n.floatValue();
                } else {
                    node.post().bloom = isTruthy(value);
                }
                return finishPostEdit();
            }
            case "bloomintensity", "bloom_intensity" -> {
                if (node.type != SceneNodeType.POST_PROCESS || !(value instanceof Number n)) return false;
                node.post().bloom = true;
                node.post().bloomIntensity = n.floatValue();
                return finishPostEdit();
            }
            case "bloomthreshold", "bloom_threshold" -> {
                if (node.type != SceneNodeType.POST_PROCESS || !(value instanceof Number n)) return false;
                node.post().bloom = true;
                node.post().bloomThreshold = n.floatValue();
                return finishPostEdit();
            }
            case "chromatic", "chromaticaberration", "chromatic_aberration",
                 "chromaticamount", "chromatic_amount" -> {
                if (node.type != SceneNodeType.POST_PROCESS) return false;
                if (value instanceof Number n) {
                    node.post().chromatic = true;
                    node.post().chromaticAmount = n.floatValue();
                } else {
                    node.post().chromatic = isTruthy(value);
                }
                return finishPostEdit();
            }
            case "grain", "grainamount", "grain_amount" -> {
                if (node.type != SceneNodeType.POST_PROCESS) return false;
                if (value instanceof Number n) {
                    node.post().grain = true;
                    node.post().grainAmount = n.floatValue();
                } else {
                    node.post().grain = isTruthy(value);
                }
                return finishPostEdit();
            }
            case "sharpen", "sharpenamount", "sharpen_amount" -> {
                if (node.type != SceneNodeType.POST_PROCESS) return false;
                if (value instanceof Number n) {
                    node.post().sharpen = true;
                    node.post().sharpenAmount = n.floatValue();
                } else {
                    node.post().sharpen = isTruthy(value);
                }
                return finishPostEdit();
            }
            case "showpost", "show_post" -> {
                if (node.type != SceneNodeType.POST_PROCESS) return false;
                node.showPost = isTruthy(value);
                return true;
            }
            case "data", "customdata", "custom_data" -> {
                if (value instanceof Map<?, ?> map) {
                    node.customData = new java.util.LinkedHashMap<>();
                    for (var e : map.entrySet()) {
                        node.customData.put(String.valueOf(e.getKey()), e.getValue());
                    }
                    return true;
                }
                return false;
            }
            default -> {
                if (SceneNodeScriptProps.setBillboardSizeAlias(node, prop, value)) return true;
                if (SceneNodeScriptProps.set(node, prop, value, entity)) {
                    if (node.type == SceneNodeType.SKIN_OVERLAY
                            && ("active".equals(prop) || "locked".equals(prop))) {
                        PlayerSkinOps.onNodeChanged(node);
                    }
                    return true;
                }
                if (node.customData == null) node.customData = new java.util.LinkedHashMap<>();
                String dataKey = prop;
                for (String existing : node.customData.keySet()) {
                    if (existing != null && existing.equalsIgnoreCase(prop)) {
                        dataKey = existing;
                        break;
                    }
                }
                node.customData.put(dataKey, unwrapCustomField(value));
                return true;
            }
        }
    }

    private static boolean isStructuralPlayerProperty(String prop) {
        return switch (prop) {
            case "id", "type", "parentid", "parent_id", "parent", "children", "scene",
                    "sceneid", "scene_id",
                    "object", "entity", "hasobject", "has_object", "childcount",
                    "child_count", "childrencount", "camera", "cam", "audio", "soundbus", "sound_bus",
                    "post", "postprocess",
                    "post_process", "fx", "effects" -> true;
            default -> false;
        };
    }

    private boolean finishPostEdit() {
        SceneNode node = node();
        if (node != null && node.type == SceneNodeType.POST_PROCESS
                && ScenePostProcessRuntime.isCameraBound(state.document, node)) {
            PlayerCameraNodes.onPostAuthoringChanged(state.document, node);
            net.minecraft.server.level.ServerPlayer owner = ScenePostProcessRuntime.resolveOwner(state, node);
            String uuid = owner != null ? owner.getUUID().toString() : "";
            PlayerCameraNodes.applyPostAuthoring(uuid, node.post());
        }
        return true;
    }

    private static boolean asBoolean(Object value) {
        if (value instanceof Boolean bool) return bool;
        if (value instanceof Number number) return number.doubleValue() != 0d;
        String text = String.valueOf(value).trim();
        return "true".equalsIgnoreCase(text) || "1".equals(text)
                || "yes".equalsIgnoreCase(text) || "on".equalsIgnoreCase(text)
                || "вкл".equalsIgnoreCase(text);
    }

    private static boolean saveRoute(SceneNode route, CommandSourceStack source) {
        try {
            var server = source != null ? source.getServer() : ServerLifecycleHooks.getCurrentServer();
            if (server == null) return false;
            SceneRouteStorage.saveNode(
                    server.getServerDirectory().toPath().resolve("spraute_engine"), route);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    /** Updates this node's world position and repositions descendants. */
    private void closeCanvasesInSubtree(SceneNode node, CommandSourceStack source) {
        if (node == null || source == null || source.getServer() == null) return;
        if (node.type == SceneNodeType.CANVAS) {
            for (net.minecraft.server.level.ServerPlayer player : source.getServer().getPlayerList().getPlayers()) {
                SceneRuntime.closeCanvasFor(state.sceneId, node, player);
            }
        }
        for (SceneNode child : node.children) closeCanvasesInSubtree(child, source);
    }

    public boolean setWorldPosition(double worldX, double worldY, double worldZ, CommandSourceStack source) {
        SceneNode node = node();
        if (node == null || !node.hasTransform()) return false;

        SceneTransform world = state.document.worldTransform(node.id);
        if (world == null) world = new SceneTransform();
        world.px = worldX;
        world.py = worldY;
        world.pz = worldZ;

        SceneTransform parentWorld = state.document.parentWorldTransform(node);
        node.transform = SceneTransform.toLocal(parentWorld, world);

        Entity entity = entity(source);
        if (entity != null) {
            entity.teleportTo(worldX, worldY, worldZ);
        }
        syncSubtreeEntities(node, source);
        if (node.type == SceneNodeType.ROUTE_POINT) {
            SceneNode route = state.document.findParent(node.id);
            if (route != null && route.type == SceneNodeType.ROUTE) saveRoute(route, source);
        }
        return true;
    }

    private void writeWorldRotationComponent(String component, double value, CommandSourceStack source) {
        SceneNode node = node();
        if (node == null || !node.hasTransform()) return;
        SceneTransform world = state.document.worldTransform(node.id);
        if (world == null) world = new SceneTransform();
        switch (component) {
            case "rotationX" -> world.rx = value;
            case "rotationY" -> world.ry = value;
            case "rotationZ" -> world.rz = value;
        }
        SceneTransform parentWorld = state.document.parentWorldTransform(node);
        node.transform = SceneTransform.toLocal(parentWorld, world);
        syncSubtreeEntities(node, source);
    }

    private void writeWorldScale(double sx, double sy, double sz, CommandSourceStack source) {
        SceneNode node = node();
        if (node == null || !node.hasTransform()) return;
        SceneTransform world = state.document.worldTransform(node.id);
        if (world == null) world = new SceneTransform();
        world.sx = sx;
        world.sy = sy;
        world.sz = sz;
        SceneTransform parentWorld = state.document.parentWorldTransform(node);
        node.transform = SceneTransform.toLocal(parentWorld, world);
        syncSubtreeEntities(node, source);
    }

    private void syncSubtreeEntities(SceneNode root, CommandSourceStack source) {
        if (source == null || source.getLevel() == null) return;
        net.minecraft.server.level.ServerLevel level = source.getLevel();
        state.document.walk((node, parentWorld) -> {
            if (!state.document.isSameOrDescendant(root.id, node.id)) return;
            if (!node.hasTransform()) return;
            // Nodes owned by a dedicated runtime (Java-registered CUSTOM types, world-space
            // canvases) get their position rewritten from the node every tick anyway — don't
            // fight that here with a second, independently-computed teleport.
            if (node.type == SceneNodeType.CUSTOM || SceneWorldCanvasRuntime.isWorldCanvas(node)) return;
            SceneTransform world = SceneTransform.composeWorld(parentWorld, node.transform);
            Entity entity = state.resolveEntity(node.id, level);
            if (entity != null) {
                entity.teleportTo(world.px, world.py, world.pz);
                entity.setYRot((float) world.ry);
                if (entity instanceof LivingEntity living) {
                    living.yBodyRot = (float) world.ry;
                    living.yHeadRot = (float) world.ry;
                }
                if (GeoEntityScriptUtil.isGeoEntity(entity)) {
                    GeoEntityScriptUtil.setModelRotationX(entity, (float) world.rx);
                    GeoEntityScriptUtil.setModelRotationZ(entity, (float) world.rz);
                    GeoEntityScriptUtil.setModelScale(entity, (float) world.sx, (float) world.sy, (float) world.sz);
                }
            }
        });
    }

    private SceneNodeView resolveRelative(String pathOrName) {
        SceneNode base = node();
        if (base == null) return null;
        String path = ScenePathResolver.normalizePath(pathOrName);
        if (path.isEmpty()) return null;

        SceneNode global = ScenePathResolver.findNode(state.document, path);
        if (global != null) return state.nodeView(global);

        if (!path.contains(".")) {
            SceneNode child = ScenePathResolver.findDirectChild(base, path);
            return child == null ? null : state.nodeView(child);
        }

        String[] parts = path.split("\\.");
        SceneNode current = base;
        for (String part : parts) {
            current = ScenePathResolver.findDirectChild(current, part);
            if (current == null) return null;
        }
        return state.nodeView(current);
    }

    private List<SceneNodeView> children() {
        SceneNode node = node();
        if (node == null) return List.of();
        List<SceneNodeView> out = new ArrayList<>();
        for (SceneNode child : node.children) {
            out.add(state.nodeView(child));
        }
        return out;
    }

    private SceneNodeView parent() {
        SceneNode node = node();
        if (node == null) return null;
        SceneNode parent = state.document.findParent(node.id);
        return parent == null ? null : state.nodeView(parent);
    }

    private SceneNodeView findDescendant(String nameOrId) {
        SceneNode node = node();
        if (node == null) return null;
        SceneNode found = findInSubtree(node, nameOrId);
        return found == null ? null : state.nodeView(found);
    }

    private List<SceneNodeView> nodesWithTag(String tag) {
        SceneNode base = node();
        if (base == null) return List.of();
        List<SceneNodeView> out = new ArrayList<>();
        for (SceneNode found : SceneNodeTags.findAllByTag(base, tag)) {
            out.add(state.nodeView(found));
        }
        return out;
    }

    private static SceneNode findInSubtree(SceneNode root, String nameOrId) {
        if (ScenePathResolver.matchesIdOrName(root, nameOrId)) return root;
        for (SceneNode child : root.children) {
            SceneNode found = findInSubtree(child, nameOrId);
            if (found != null) return found;
        }
        return null;
    }

    private Object worldField(SceneNode node, String field) {
        Map<String, Object> map = SceneActiveState.transformMap(state.document.worldTransform(node.id));
        return map.get(field);
    }

    private double worldNumber(SceneNode node, String field, double fallback) {
        Object value = worldField(node, field);
        return value instanceof Number n ? n.doubleValue() : fallback;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private Object wrapCustomField(SceneNode node, String key, Object value) {
        if (value instanceof org.zonarstudio.spraute_engine.scene.custom.ScriptCurve) return value;
        var field = org.zonarstudio.spraute_engine.scene.custom.CustomNodeRegistry.fieldOn(
                state.document, node, key);
        if (field != null && field.kind == org.zonarstudio.spraute_engine.scene.custom.CustomFieldKind.CURVE) {
            return org.zonarstudio.spraute_engine.scene.custom.ScriptCurve.wrap(value);
        }
        if (org.zonarstudio.spraute_engine.scene.custom.ScriptCurve.looksLike(value)) {
            return org.zonarstudio.spraute_engine.scene.custom.ScriptCurve.wrap(value);
        }
        return value;
    }

    private static Object unwrapCustomField(Object value) {
        return org.zonarstudio.spraute_engine.scene.custom.ScriptCurve.unwrap(value);
    }

    private static boolean isTruthy(Object value) {
        if (value instanceof Boolean b) return b;
        if (value instanceof Number n) return n.doubleValue() != 0;
        if (value == null) return false;
        String s = String.valueOf(value).trim();
        return !s.isEmpty() && !"false".equalsIgnoreCase(s) && !"0".equals(s);
    }
}
