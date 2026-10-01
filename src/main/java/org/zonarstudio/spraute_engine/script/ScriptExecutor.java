package org.zonarstudio.spraute_engine.script;

import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.zonarstudio.spraute_engine.compat.SprauteEntityCompat;
import org.zonarstudio.spraute_engine.scene.SceneActiveState;
import org.zonarstudio.spraute_engine.scene.SceneRuntime;
import org.zonarstudio.spraute_engine.script.function.ScriptFunction;
import org.zonarstudio.spraute_engine.util.SprauteOrbTextures;

import java.util.*;

/**
 * Executor that runs CompiledScripts. Supports async execution via ticks.
 */
public class ScriptExecutor {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final ThreadLocal<Deque<ActiveScript>> CURRENT_ACTIVE_STACK =
            ThreadLocal.withInitial(ArrayDeque::new);

    /** Variables of the currently executing script instance, if any. */
    public static Map<String, Object> currentVariables() {
        Deque<ActiveScript> stack = CURRENT_ACTIVE_STACK.get();
        ActiveScript active = stack.isEmpty() ? null : stack.peek();
        return active == null ? null : active.variables;
    }

    /** Instance id of the currently executing script, if any. */
    public static String currentInstanceId() {
        Deque<ActiveScript> stack = CURRENT_ACTIVE_STACK.get();
        ActiveScript active = stack.isEmpty() ? null : stack.peek();
        return active == null ? null : active.instanceId;
    }

    /**
     * Active scene for the running script chain: locals ({@code _scene}), instance id
     * ({@code scene:...:node:...}), entity scene tags, then the only loaded scene.
     */
    public static SceneActiveState currentSceneState() {
        Deque<ActiveScript> stack = CURRENT_ACTIVE_STACK.get();
        for (ActiveScript active : stack) {
            SceneActiveState fromVars = SceneScriptSpawn.stateFromVariables(active.variables);
            if (fromVars != null) return fromVars;
            SceneActiveState fromInstance = SceneRuntime.resolveActiveStateForScript(active.instanceId);
            if (fromInstance != null) return fromInstance;
            if (active.source != null && active.source.getEntity() != null) {
                SceneActiveState fromEntity = SceneScriptSpawn.stateFromEntity(active.source.getEntity());
                if (fromEntity != null) return fromEntity;
            }
        }
        return SceneRuntime.getSingletonActiveState();
    }

    /** Текущий выполняемый скрипт (для spawnNpcPrefab и др.). */
    public static Object spawnNpcPrefabForActive(String prefabId, String instanceId,
            double x, double y, double z, Map<String, Object> overrides) {
        Deque<ActiveScript> stack = CURRENT_ACTIVE_STACK.get();
        ActiveScript active = stack.isEmpty() ? null : stack.peek();
        if (active == null) return null;
        return active.spawnNpcPrefab(prefabId, instanceId, x, y, z, overrides);
    }

    /** Legacy snake_case keys в†’ camelCase. */
    private static String normPropKey(String key) {
        if (key == null) return "";
        return switch (key) {
            case "show_name" -> "showName";
            case "idle_anim" -> "idleAnim";
            case "walk_anim" -> "walkAnim";
            case "walk_turn_speed" -> "walkTurnSpeed";
            case "idle_turn_speed" -> "idleTurnSpeed";
            case "fly_idle_anim" -> "flyIdleAnim";
            case "fly_walk_anim" -> "flyWalkAnim";
            case "swim_idle_anim" -> "swimIdleAnim";
            case "swim_walk_anim" -> "swimWalkAnim";
            case "death_anim" -> "deathAnim";
            case "max_hp" -> "maxHp";
            case "drop_item" -> "dropItem";
            case "throw_item" -> "throwItem";
            case "drop_min" -> "dropMin";
            case "drop_max" -> "dropMax";
            case "drop_chance" -> "dropChance";
            case "look_x" -> "lookX";
            case "look_y" -> "lookY";
            case "look_z" -> "lookZ";
            case "rotation_x", "rot_x" -> "rotationX";
            case "rotation_y", "rot_y" -> "rotationY";
            case "rotation_z", "rot_z" -> "rotationZ";
            case "scale_x" -> "scaleX";
            case "scale_y" -> "scaleY";
            case "scale_z" -> "scaleZ";
            case "hitbox_preset", "hitboxpreset" -> "hitboxPreset";
            case "held_item_nbt" -> "heldItemNbt";
            case "smooth_time" -> "smoothTime";
            case "fly_smooth" -> "flySmooth";
            case "fly_time" -> "flyTime";
            case "look_at" -> "lookAt";
            case "can_close" -> "canClose";
            case "content_h" -> "contentH";
            case "slice_borders" -> "sliceBorders";
            case "slice_scale" -> "sliceScale";
            case "fill_amount" -> "fillAmount";
            case "fill_origin" -> "fillOrigin";
            case "fill_mode" -> "fill";
            case "src_u" -> "srcU";
            case "src_v" -> "srcV";
            case "src_w" -> "srcW";
            case "src_h" -> "srcH";
            case "crop_shape", "src_shape" -> "cropShape";
            case "crop_feather", "edge_blur" -> "cropFeather";
            case "feather_l", "feather_left" -> "featherL";
            case "feather_t", "feather_top" -> "featherT";
            case "feather_r", "feather_right" -> "featherR";
            case "feather_b", "feather_bottom" -> "featherB";
            case "feather_smooth" -> "featherSmooth";
            case "trigger_fade_out" -> "triggerFadeOut";
            case "visible_time" -> "visibleTime";
            case "transparent" -> "translucent";
            case "lod_enabled" -> "lodEnabled";
            case "render_distance" -> "renderDistance";
            case "force_visible" -> "forceVisible";
            case "load_chunks", "keep_chunks", "keep_loaded" -> "loadChunks";
            default -> key;
        };
    }

    /** Global variables вЂ” shared across all scripts, cleared on server stop */
    private static final Map<String, Object> globalVariables = new HashMap<>();

    private final List<ActiveScript> activeScripts = new ArrayList<>();
    private final List<ActiveScript> scriptsToAdd = new ArrayList<>();

    public void start(CompiledScript script, CommandSourceStack source) {
        start(script, source, null);
    }

    public void start(CompiledScript script, CommandSourceStack source, Map<String, Object> initialVariables) {
        if (findRunningScript(script.getName()) != null) {
            LOGGER.debug("Skipping duplicate start for script '{}'", script.getName());
            return;
        }
        LOGGER.info("Starting script: {}", script.getName());
        ActiveScript active = new ActiveScript(script, source, initialVariables);
        scriptsToAdd.add(active);
    }

    /** Starts an independently addressable instance, allowing one script on several scene nodes. */
    public boolean startInstance(CompiledScript script, CommandSourceStack source,
                                 Map<String, Object> initialVariables, String instanceId) {
        return startInstance(script, source, initialVariables, instanceId, false);
    }

    /**
     * @param shareVariablesMap when true, {@code initialVariables} is used as the live locals map
     *                          (mutations persist for the caller — used by the editor console REPL).
     */
    public boolean startInstance(CompiledScript script, CommandSourceStack source,
                                 Map<String, Object> initialVariables, String instanceId,
                                 boolean shareVariablesMap) {
        if (findRunningInstance(instanceId) != null) return false;
        LOGGER.info("Starting script instance '{}': {}", instanceId, script.getName());
        scriptsToAdd.add(new ActiveScript(script, source, initialVariables, instanceId, shareVariablesMap));
        return true;
    }

    public boolean isInstanceRunning(String instanceId) {
        return findRunningInstance(instanceId) != null;
    }

    public void tick() {
        activeScripts.addAll(scriptsToAdd);
        scriptsToAdd.clear();

        Iterator<ActiveScript> cleanup = activeScripts.iterator();
        while (cleanup.hasNext()) {
            ActiveScript script = cleanup.next();
            if (script.isFinished()) {
                String finishedName = script.getScriptName();
                net.minecraft.commands.CommandSourceStack source = script.getSource();
                script.cleanup();
                cleanup.remove();
                triggerAfterScript(finishedName, source);
            }
        }

        // Snapshot: bootstrap import() may start more scripts mid-tick
        List<ActiveScript> snapshot = new ArrayList<>(activeScripts);
        for (ActiveScript script : snapshot) {
            if (script.isFinished()) continue;
            script.tick();
        }
    }

    private void triggerAfterScript(String finishedScriptName, net.minecraft.commands.CommandSourceStack source) {
        String next = org.zonarstudio.spraute_engine.script.ScriptTriggerRegistry.get().getRunAfter(finishedScriptName);
        if (next != null && !next.isEmpty() && source != null) {
            org.zonarstudio.spraute_engine.script.ScriptManager.getInstance().run(next, source);
        }
    }

    public void onInteract(net.minecraft.world.entity.Entity target, net.minecraft.world.entity.Entity interactor) {
        for (ActiveScript script : activeScripts) {
            script.onInteract(target, interactor);
        }
    }

    public void onKeybind(String key, net.minecraft.world.entity.player.Player player) {
        for (ActiveScript script : activeScripts) {
            script.onKeybind(key, player);
        }
    }

    public void onPlayerJoin(net.minecraft.server.level.ServerPlayer player) {
        for (ActiveScript script : activeScripts) {
            script.onPlayerJoin(player);
        }
        for (ActiveScript script : scriptsToAdd) {
            script.onPlayerJoin(player);
        }
    }

    public void onDeath(net.minecraft.world.entity.LivingEntity entity, net.minecraft.world.entity.Entity killer) {
        for (ActiveScript script : activeScripts) {
            script.onDeath(entity, killer);
        }
        for (ActiveScript script : scriptsToAdd) {
            script.onDeath(entity, killer);
        }
    }

    public void onUiAction(net.minecraft.server.level.ServerPlayer player, String widgetId, boolean closed) {
        onUiAction(player, closed
                ? org.zonarstudio.spraute_engine.network.SprauteUiActionPacket.ACTION_CLOSE
                : org.zonarstudio.spraute_engine.network.SprauteUiActionPacket.ACTION_CLICK,
                widgetId, closed ? -1 : 0);
    }

    public void onUiAction(net.minecraft.server.level.ServerPlayer player, byte action, String widgetId, int mouseButton) {
        for (ActiveScript script : activeScripts) {
            script.onUiAction(player, action, widgetId, mouseButton);
        }
        for (ActiveScript script : scriptsToAdd) {
            script.onUiAction(player, action, widgetId, mouseButton);
        }
    }

    public void cancelUiWaitsForPlayer(net.minecraft.server.level.ServerPlayer player) {
        if (player == null) return;
        for (ActiveScript script : activeScripts) {
            script.cancelUiWaitsForPlayer(player);
        }
        for (ActiveScript script : scriptsToAdd) {
            script.cancelUiWaitsForPlayer(player);
        }
    }

    /** Fire leave handlers/waits, then stop scripts owned by the departing player. */
    public void onPlayerLoggedOut(net.minecraft.server.level.ServerPlayer player) {
        if (player == null) return;
        for (ActiveScript script : activeScripts) script.onPlayerLeave(player);
        for (ActiveScript script : scriptsToAdd) script.onPlayerLeave(player);
        onPlayerLoggedOut(player.getUUID());
    }

    /** Stop scripts started by this player when the player leaves the server. */
    public void onPlayerLoggedOut(java.util.UUID playerId) {
        if (playerId == null) return;
        for (ActiveScript script : activeScripts) {
            if (playerId.equals(script.ownerPlayerId)) script.forceStop();
        }
        for (ActiveScript script : scriptsToAdd) {
            if (playerId.equals(script.ownerPlayerId)) script.forceStop();
        }
    }

    public void onUiOverlapAction(net.minecraft.server.level.ServerPlayer player, String id1, String id2, boolean overlapping) {
        for (ActiveScript script : activeScripts) {
            script.onUiOverlapAction(player, id1, id2, overlapping);
        }
        for (ActiveScript script : scriptsToAdd) {
            script.onUiOverlapAction(player, id1, id2, overlapping);
        }
    }

    public void onSceneHitboxTouch(net.minecraft.server.level.ServerPlayer player, String sceneId,
                                   String nodeId, String nodeName, String triggerId, boolean entering) {
        for (ActiveScript script : activeScripts) {
            script.onSceneHitboxTouch(player, sceneId, nodeId, nodeName, triggerId, entering);
        }
        for (ActiveScript script : scriptsToAdd) {
            script.onSceneHitboxTouch(player, sceneId, nodeId, nodeName, triggerId, entering);
        }
    }

    /** Разрешить ServerLevel по строке вида "overworld" / "minecraft:the_nether" / "the_end". */
    public static net.minecraft.server.level.ServerLevel resolveLevel(net.minecraft.server.MinecraftServer server, String dimId) {
        if (server == null || dimId == null || dimId.isBlank()) return null;
        String full = dimId.contains(":") ? dimId : "minecraft:" + dimId;
        org.zonarstudio.spraute_engine.util.SprauteResourcePath.Result parsed =
                org.zonarstudio.spraute_engine.util.SprauteResourcePath.parse(full,
                        org.zonarstudio.spraute_engine.util.SprauteResourcePath.Kind.RESOURCE);
        if (!parsed.ok()) {
            LOGGER.warn("[Script] Invalid dimension id '{}': {}", dimId, parsed.invalidChars());
            return null;
        }
        //? if >=1.20.1 {
        net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> key =
            net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                parsed.location());
        //?} else {
        /*net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> key =
            net.minecraft.resources.ResourceKey.create(net.minecraft.core.Registry.DIMENSION_REGISTRY,
                parsed.location());
        *///?}
        return server.getLevel(key);
    }

    public boolean onClickBlock(net.minecraft.world.entity.player.Player player, net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.Block block, boolean isLeft) {
        return onClickBlock(player, pos, block, isLeft, null);
    }
    public boolean onClickBlock(net.minecraft.world.entity.player.Player player, net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.Block block, boolean isLeft, net.minecraft.world.level.Level level) {
        String dimId = level != null ? level.dimension().location().toString() : null;
        boolean canceled = false;
        for (ActiveScript script : activeScripts) {
            if (script.onClickBlock(player, pos, block, isLeft, dimId)) canceled = true;
        }
        for (ActiveScript script : scriptsToAdd) {
            if (script.onClickBlock(player, pos, block, isLeft, dimId)) canceled = true;
        }
        return canceled;
    }

    public boolean onBreakBlock(net.minecraft.world.entity.player.Player player, net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.Block block) {
        return onBreakBlock(player, pos, block, null);
    }
    public boolean onBreakBlock(net.minecraft.world.entity.player.Player player, net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.Block block, net.minecraft.world.level.Level level) {
        String dimId = level != null ? level.dimension().location().toString() : null;
        boolean canceled = false;
        for (ActiveScript script : activeScripts) {
            if (script.onBreakBlock(player, pos, block, dimId)) canceled = true;
        }
        for (ActiveScript script : scriptsToAdd) {
            if (script.onBreakBlock(player, pos, block, dimId)) canceled = true;
        }
        return canceled;
    }

    public boolean onPlaceBlock(net.minecraft.world.entity.player.Player player, net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.Block block) {
        return onPlaceBlock(player, pos, block, null);
    }
    public boolean onPlaceBlock(net.minecraft.world.entity.player.Player player, net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.Block block, net.minecraft.world.level.Level level) {
        String dimId = level != null ? level.dimension().location().toString() : null;
        boolean canceled = false;
        for (ActiveScript script : activeScripts) {
            if (script.onPlaceBlock(player, pos, block, dimId)) canceled = true;
        }
        for (ActiveScript script : scriptsToAdd) {
            if (script.onPlaceBlock(player, pos, block, dimId)) canceled = true;
        }
        return canceled;
    }

    public boolean onOpenChest(net.minecraft.world.entity.player.Player player, net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.Block block, net.minecraft.world.level.Level level) {
        String dimId = level != null ? level.dimension().location().toString() : null;
        boolean canceled = false;
        for (ActiveScript script : activeScripts) {
            if (script.onOpenChest(player, pos, block, dimId)) canceled = true;
        }
        for (ActiveScript script : scriptsToAdd) {
            if (script.onOpenChest(player, pos, block, dimId)) canceled = true;
        }
        return canceled;
    }

    public boolean onOpenDoor(net.minecraft.world.entity.player.Player player, net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.Block block, net.minecraft.world.level.Level level) {
        String dimId = level != null ? level.dimension().location().toString() : null;
        boolean canceled = false;
        for (ActiveScript script : activeScripts) {
            if (script.onOpenDoor(player, pos, block, dimId)) canceled = true;
        }
        for (ActiveScript script : scriptsToAdd) {
            if (script.onOpenDoor(player, pos, block, dimId)) canceled = true;
        }
        return canceled;
    }

    public boolean onUseBlock(net.minecraft.world.entity.player.Player player, net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.Block block, net.minecraft.world.level.Level level) {
        String dimId = level != null ? level.dimension().location().toString() : null;
        boolean canceled = false;
        for (ActiveScript script : activeScripts) {
            if (script.onUseBlock(player, pos, block, dimId)) canceled = true;
        }
        for (ActiveScript script : scriptsToAdd) {
            if (script.onUseBlock(player, pos, block, dimId)) canceled = true;
        }
        return canceled;
    }

    public void onChat(net.minecraft.server.level.ServerPlayer player, String message) {
        for (ActiveScript script : activeScripts) {
            script.onChat(player, message);
        }
        for (ActiveScript script : scriptsToAdd) {
            script.onChat(player, message);
        }
    }

    public void onPlayerDimensionChange(net.minecraft.server.level.ServerPlayer player, String fromDimension, String toDimension) {
        for (ActiveScript script : activeScripts) {
            script.onPlayerDimensionChange(player, fromDimension, toDimension);
        }
        for (ActiveScript script : scriptsToAdd) {
            script.onPlayerDimensionChange(player, fromDimension, toDimension);
        }
    }

    public void onOrbPickup(net.minecraft.server.level.ServerPlayer player, String texture, int amount) {
        for (ActiveScript script : activeScripts) {
            script.onOrbPickup(player, texture, amount);
        }
        for (ActiveScript script : scriptsToAdd) {
            script.onOrbPickup(player, texture, amount);
        }
    }

    public void onProjectileHit(String projectileId, String hitType, double x, double y, double z,
                                net.minecraft.world.entity.Entity target, net.minecraft.world.entity.Entity owner) {
        for (ActiveScript script : activeScripts) {
            script.onProjectileHit(projectileId, hitType, x, y, z, target, owner);
        }
        for (ActiveScript script : scriptsToAdd) {
            script.onProjectileHit(projectileId, hitType, x, y, z, target, owner);
        }
    }

    public void onTradeBuy(net.minecraft.server.level.ServerPlayer player, String itemId, int price) {
        onCustomEvent("tradeBuy", java.util.List.of(player, itemId, price));
    }

    public void onTradeSell(net.minecraft.server.level.ServerPlayer player, String itemId, int price) {
        onCustomEvent("tradeSell", java.util.List.of(player, itemId, price));
    }

    public void onCustomEvent(String eventName, java.util.List<Object> payload) {
        if (eventName == null || eventName.isBlank()) return;
        java.util.List<Object> args = payload != null ? payload : java.util.List.of();
        for (ActiveScript script : activeScripts) {
            script.onCustomEvent(eventName, args);
        }
        for (ActiveScript script : scriptsToAdd) {
            script.onCustomEvent(eventName, args);
        }
    }

    public void onRelGift(net.minecraft.server.level.ServerPlayer player, String npcId, String itemId, int repDelta) {
        for (ActiveScript script : activeScripts) {
            script.onRelGift(player, npcId, itemId, repDelta);
        }
        for (ActiveScript script : scriptsToAdd) {
            script.onRelGift(player, npcId, itemId, repDelta);
        }
    }

    public void onRelRepChange(net.minecraft.server.level.ServerPlayer player, String npcId, int oldRep, int newRep) {
        for (ActiveScript script : activeScripts) {
            script.onRelRepChange(player, npcId, oldRep, newRep);
        }
        for (ActiveScript script : scriptsToAdd) {
            script.onRelRepChange(player, npcId, oldRep, newRep);
        }
    }

    public void onRelTalk(net.minecraft.server.level.ServerPlayer player, String npcId) {
        for (ActiveScript script : activeScripts) {
            script.onRelTalk(player, npcId);
        }
        for (ActiveScript script : scriptsToAdd) {
            script.onRelTalk(player, npcId);
        }
    }

    public void onRelButton(net.minecraft.server.level.ServerPlayer player, String npcId, String btnId) {
        for (ActiveScript script : activeScripts) {
            script.onRelButton(player, npcId, btnId);
        }
        for (ActiveScript script : scriptsToAdd) {
            script.onRelButton(player, npcId, btnId);
        }
    }

    public boolean onPlayerAction(net.minecraft.world.entity.player.Player player, String actionType, Object target) {
        boolean canceled = false;
        for (ActiveScript script : activeScripts) {
            if (script.onPlayerAction(player, actionType, target)) canceled = true;
        }
        for (ActiveScript script : scriptsToAdd) {
            if (script.onPlayerAction(player, actionType, target)) canceled = true;
        }
        return canceled;
    }

    public boolean onPlayerAttack(net.minecraft.world.entity.player.Player player, net.minecraft.world.entity.Entity target) {
        return onPlayerAction(player, "attack", target);
    }

    public boolean onPlayerSprint(net.minecraft.world.entity.player.Player player) {
        return onPlayerAction(player, "sprint", null);
    }

    /** Stop all running scripts and clear state. */
    public void stopAll() {
        for (ActiveScript script : activeScripts) {
            script.cleanupAndCloseOverlays();
        }
        activeScripts.clear();
        scriptsToAdd.clear();
    }

    /** Get names of currently running scripts. */
    public Set<String> getRunningScriptNames() {
        Set<String> names = new HashSet<>();
        for (ActiveScript s : activeScripts) {
            names.add(s.getScriptName());
        }
        return names;
    }

    /** Stop a specific script by name. Returns true if found and stopped. */
    public boolean stopScript(String scriptName) {
        for (Iterator<ActiveScript> it = activeScripts.iterator(); it.hasNext(); ) {
            ActiveScript script = it.next();
            if (script.getScriptName().equals(scriptName)) {
                script.forceStop();
                script.cleanupAndCloseOverlays();
                it.remove();
                return true;
            }
        }
        return false;
    }

    public boolean stopInstance(String instanceId) {
        for (Iterator<ActiveScript> it = activeScripts.iterator(); it.hasNext(); ) {
            ActiveScript script = it.next();
            if (script.getInstanceId().equals(instanceId)) {
                script.forceStop();
                script.cleanupAndCloseOverlays();
                it.remove();
                return true;
            }
        }
        for (Iterator<ActiveScript> it = scriptsToAdd.iterator(); it.hasNext(); ) {
            ActiveScript script = it.next();
            if (script.getInstanceId().equals(instanceId)) {
                script.forceStop();
                script.cleanupAndCloseOverlays();
                it.remove();
                return true;
            }
        }
        return false;
    }

    private ActiveScript findRunningInstance(String instanceId) {
        for (ActiveScript s : activeScripts) {
            if (s.getInstanceId().equals(instanceId) && !s.isFinished()) return s;
        }
        for (ActiveScript s : scriptsToAdd) {
            if (s.getInstanceId().equals(instanceId) && !s.isFinished()) return s;
        }
        return null;
    }

    /** Find a running ActiveScript by name (searches both active and pending). */
    private ActiveScript findRunningScript(String name) {
        for (ActiveScript s : activeScripts) {
            if (s.getScriptName().equals(name) && !s.isFinished()) return s;
        }
        for (ActiveScript s : scriptsToAdd) {
            if (s.getScriptName().equals(name) && !s.isFinished()) return s;
        }
        return null;
    }

    /**
     * Pick the best running instance for import()/function resolution.
     * When several copies of the same script exist, prefer one that already registered fun.
     */
    private ActiveScript findBestDonorScript(String name, String functionName) {
        ActiveScript best = null;
        for (ActiveScript s : activeScripts) {
            if (!s.getScriptName().equals(name) || s.isFinished()) continue;
            best = preferDonor(best, s, functionName);
        }
        for (ActiveScript s : scriptsToAdd) {
            if (!s.getScriptName().equals(name) || s.isFinished()) continue;
            best = preferDonor(best, s, functionName);
        }
        return best;
    }

    /** Running instance first; library scripts that finished after registering {@code fun} are still valid donors. */
    private ActiveScript findDonorScript(String name, String functionName) {
        ActiveScript running = findBestDonorScript(name, functionName);
        if (running != null) return running;
        ActiveScript best = null;
        for (ActiveScript s : activeScripts) {
            if (!s.getScriptName().equals(name) || !s.isFinished()) continue;
            if (functionName != null && !functionName.isEmpty() && !s.userFunctions.containsKey(functionName)) continue;
            best = preferDonor(best, s, functionName);
        }
        for (ActiveScript s : scriptsToAdd) {
            if (!s.getScriptName().equals(name) || !s.isFinished()) continue;
            if (functionName != null && !functionName.isEmpty() && !s.userFunctions.containsKey(functionName)) continue;
            best = preferDonor(best, s, functionName);
        }
        return best;
    }

    private static ActiveScript preferDonor(ActiveScript current, ActiveScript candidate, String functionName) {
        if (current == null) return candidate;
        if (functionName != null && !functionName.isEmpty()) {
            boolean cHas = candidate.userFunctions.containsKey(functionName);
            boolean curHas = current.userFunctions.containsKey(functionName);
            if (cHas && !curHas) return candidate;
            if (curHas && !cHas) return current;
        }
        int cSize = candidate.userFunctions.size();
        int curSize = current.userFunctions.size();
        if (cSize != curSize) return cSize > curSize ? candidate : current;
        if (candidate.ip != current.ip) return candidate.ip > current.ip ? candidate : current;
        return current;
    }

    private static boolean needsBootstrap(ActiveScript script) {
        return !script.isFinished() && script.userFunctions.isEmpty();
    }

    private void bootstrapUntilReady(ActiveScript target) {
        if (target == null || !needsBootstrap(target)) return;
        int guard = 0;
        while (!target.isFinished() && guard++ < 1000) {
            target.tick();
            if (!target.userFunctions.isEmpty()) return;
        }
    }

    /** import() also starts the library script if it is not already running. */
    private void ensureImportedScriptRunning(String includeName, CommandSourceStack source, String callerScriptName) {
        if (includeName == null || includeName.isEmpty()) return;
        if (includeName.equals(callerScriptName)) return;
        ActiveScript existing = findBestDonorScript(includeName, null);
        if (existing != null) {
            bootstrapUntilReady(existing);
            return;
        }
        ScriptManager.getInstance().run(includeName, source, inheritedVarsForImport());
        bootstrapImportedScript(includeName);
    }

    private Map<String, Object> inheritedVarsForImport() {
        Map<String, Object> vars = new HashMap<>();
        ScriptManager.seedInheritedSceneContext(vars);
        return vars.isEmpty() ? null : vars;
    }

    /** РЎРёРЅС…СЂРѕРЅРЅРѕ РІС‹РїРѕР»РЅРёС‚СЊ РёРјРїРѕСЂС‚РёСЂРѕРІР°РЅРЅС‹Р№ СЃРєСЂРёРїС‚ РґРѕ СЂРµРіРёСЃС‚СЂР°С†РёРё fun/on (РІ С‚РѕРј Р¶Рµ С‚РёРєРµ, С‡С‚Рѕ Рё import). */
    private void bootstrapImportedScript(String includeName) {
        ActiveScript imported = findBestDonorScript(includeName, null);
        if (imported == null) return;
        bootstrapUntilReady(imported);
        if (needsBootstrap(imported)) {
            int guard = 0;
            while (!imported.isFinished() && guard++ < 1000) {
                imported.tick();
            }
        }
    }

    /** РћС‡РёСЃС‚РёС‚СЊ РІСЃРµ РіР»РѕР±Р°Р»СЊРЅС‹Рµ РїРµСЂРµРјРµРЅРЅС‹Рµ СЃРєСЂРёРїС‚РѕРІ (РґРѕ РїРµСЂРµР·Р°РїСѓСЃРєР° СЃРµСЂРІРµСЂР°). */
    public void clearGlobalVariables() {
        globalVariables.clear();
    }

    /** РЈРґР°Р»РёС‚СЊ РѕРґРЅСѓ РіР»РѕР±Р°Р»СЊРЅСѓСЋ РїРµСЂРµРјРµРЅРЅСѓСЋ. {@code true}, РµСЃР»Рё РєР»СЋС‡ Р±С‹Р». */
    public boolean removeGlobalVariable(String name) {
        return globalVariables.remove(name) != null;
    }

    public java.util.Set<String> getGlobalVariableNames() {
        return new java.util.HashSet<>(globalVariables.keySet());
    }

    public List<org.zonarstudio.spraute_engine.network.SyncDebugStatePacket.ScriptDebugData> getDebugData() {
        List<org.zonarstudio.spraute_engine.network.SyncDebugStatePacket.ScriptDebugData> list = new ArrayList<>();
        for (ActiveScript script : activeScripts) {
            list.add(script.getDebugData());
        }
        return list;
    }

    // --- Internal: user-defined function storage ---
    public static class TryBlock {
        public final int catchIp;
        public final String catchVar;
        public TryBlock(int catchIp, String catchVar) {
            this.catchIp = catchIp;
            this.catchVar = catchVar;
        }
    }

    private static class UserFunction {
        final List<String> params;
        final List<CompiledScript.Instruction> bodyInstructions;

        UserFunction(List<String> params, List<CompiledScript.Instruction> bodyInstructions) {
            this.params = params;
            this.bodyInstructions = bodyInstructions;
        }
    }

    // --- Internal: event handler ---
    private static class EventHandler {
        final String eventName;
        final List<Object> eventArgs;
        final List<CompiledScript.Instruction> bodyInstructions;
        boolean active = true;

        EventHandler(String eventName, List<Object> eventArgs, List<CompiledScript.Instruction> bodyInstructions) {
            this.eventName = eventName;
            this.eventArgs = eventArgs;
            this.bodyInstructions = bodyInstructions;
        }
    }

    // --- Internal: periodic timer ---
    private static class TimerHandler {
        final double intervalSeconds;
        final List<CompiledScript.Instruction> bodyInstructions;
        double countdown;
        boolean active = true;

        TimerHandler(double intervalSeconds, List<CompiledScript.Instruction> bodyInstructions) {
            this.intervalSeconds = intervalSeconds;
            this.bodyInstructions = bodyInstructions;
            this.countdown = intervalSeconds;
        }
    }

    /**
     * Thrown inside function execution to unwind the call stack and return a value.
     */
    private static class ReturnException extends RuntimeException {
        final Object value;
        ReturnException(Object value) {
            super(null, null, true, false);
            this.value = value;
        }
    }

    /**
     * Holds the state of a running script.
     */
    private static String orbTextureFilter(Object raw) {
        if (raw == null) return null;
        String normalized = SprauteOrbTextures.normalize(String.valueOf(raw));
        return normalized.isEmpty() ? null : normalized;
    }

    private class ActiveScript {
        private final CompiledScript script;
        private CommandSourceStack source;
        /** Null for server/global/scene scripts; player scripts follow the player across dimensions. */
        private final UUID ownerPlayerId;
        private final String instanceId;
        private final Map<String, Object> variables;
        private final ScriptContext context = new ScriptContext();
        private int ip = 0;
        private boolean finished = false;
        
        // Wait state
        private WaitType waitType = WaitType.NONE;
        private double waitTimer = 0;
        private UUID waitEntityUuid = null;
        private String waitNpcTrackId = null;
        private boolean interactionMet = false;
        private Object asyncResult = null;
        private String pendingVarName = null;
        private String waitKeybindKey = null;
        private boolean keybindMet = false;
        private net.minecraft.world.entity.player.Player keybindPlayer = null;
        private Object waitDeathTarget = null;
        private Object waitKillKillerTarget = null;
        private Object waitKillVictimTarget = null;
        private boolean deathMet = false;
        private boolean killMet = false;
        private net.minecraft.world.entity.LivingEntity deadEntity = null;
        private net.minecraft.world.entity.Entity deathKiller = null;
        private UUID waitFollowTargetUuid = null;
        private double waitFollowStopDistance = 2.0;
        private String waitPickupNpcId = null;
        private UUID waitPickupNpcUuid = null;
        private String waitPickupItemId = null;
        private String waitPickupTag = null;
        /** Count NPC had when we started waiting (to detect only NEW pickups). */
        private int waitPickupBaseCount = 0;
        /** Max items to pick up (cap). -1 = no cap. */
        private int waitPickupMaxCount = -1;
        /** Separate from {@code npc.pickupMax()} / {@code on pickup(..., max)} so await can end without wiping them. */
        private final Object pickupAwaitCapSource = new Object();
        
        private PlayerFilter waitOrbPickupPlayers = null;
        private String waitOrbPickupTexture = null;
        private int waitOrbPickupTargetCount = 0;
        private int waitOrbPickupCurrentCount = 0;

        private String waitProjectileHitId = null;
        private String waitProjectileHitType = null;

        private PlayerFilter waitTradePlayers = null;
        private String waitTradeItemId = null;

        /** Script-registered custom event await (fireEvent / on name). */
        private String waitCustomEventName = null;
        private java.util.List<Object> waitCustomFilters = null;

        private PlayerFilter waitRelGiftPlayers = null;
        private String waitRelGiftNpcId = null;
        private String waitRelGiftItemId = null;

        private PlayerFilter waitRelSimplePlayers = null;
        private String waitRelSimpleNpcId = null;
        private String waitRelSimpleBtnId = null;

        /** Target for MOVE_TO wait вЂ” completion is distance-based; navigation alone is unreliable (path null = isDone). */
        private double waitMoveTargetX;
        private double waitMoveTargetY;
        private double waitMoveTargetZ;
        private double waitMoveSpeed = 1.0;

        // New waits: position, inventory, clickBlock, breakBlock, placeBlock, uiInput, chat
        private PlayerFilter waitPositionPlayers = null;
        private double waitPositionX, waitPositionY, waitPositionZ, waitPositionRadius;
        private PlayerFilter waitInventoryPlayers = null;
        private String waitInventoryItemId = null;
        private int waitInventoryCount = 0;
        private PlayerFilter waitBlockPlayers = null;
        private String waitBlockId = null;
        private net.minecraft.core.BlockPos waitBlockPos = null;
        private String waitBlockDim = null;
        private boolean blockEventMet = false;
        private String uiInputWidgetId = "";
        private String uiInputText = "";

        // UI overlap
        private PlayerFilter waitUiOverlapPlayers = null;
        private String waitUiOverlapId1 = "";
        private String waitUiOverlapId2 = "";
        private boolean uiOverlapMet = false;
        
        private PlayerFilter waitChatPlayers = null;
        private List<String> waitChatMessages = null;
        private boolean waitChatIgnoreCase = true;
        private boolean waitChatIgnorePunct = true;
        private boolean chatEventMet = false;
        private String chatMatchedMessage = "";
        
        // Player Action
        private PlayerFilter waitPlayerActionPlayers = null;
        private String waitPlayerActionType = "";
        private String waitPlayerActionTarget = null;
        private boolean playerActionMet = false;

        private PlayerFilter waitDimensionPlayers = null;
        private String waitDimensionId = null;
        private boolean dimensionEventMet = false;

        private PlayerFilter waitJoinPlayers = null;
        private boolean waitFirstJoinOnly = false;
        private boolean joinEventMet = false;
        private UUID waitJoinPlayerUuid = null;
        private PlayerFilter waitLeavePlayers = null;

        private PlayerFilter waitHitboxPlayers = null;
        private String waitHitboxScene = null;
        private String waitHitboxTriggerId = null;
        private boolean hitboxEventMet = false;
        private boolean leaveEventMet = false;
        private net.minecraft.server.level.ServerPlayer waitLeavePlayer = null;

        // User-defined functions
        private final Map<String, UserFunction> userFunctions = new HashMap<>();
        // Names of scripts imported via `import` вЂ” functions are resolved lazily from their running ActiveScript
        private final List<String> importedScripts = new ArrayList<>();

        private AsyncTask currentTaskScope = null;

        // Background handlers
        private final Map<String, EventHandler> eventHandlers = new HashMap<>();

        private java.util.List<java.util.Map.Entry<String, EventHandler>> snapshotEventHandlers() {
            return new java.util.ArrayList<>(eventHandlers.entrySet());
        }
        private final Map<String, TimerHandler> timerHandlers = new HashMap<>();
        /** Tracks if pickup handler saw the item last tick (to fire only on transition to "has item"). */
        private final Map<String, Boolean> pickupHandlerHadItem = new HashMap<>();
        /** For SprauteNpcEntity: last count seen (fire when count increases). */
        private final Map<String, Integer> pickupHandlerLastCount = new HashMap<>();
        private final java.util.Set<org.zonarstudio.spraute_engine.entity.SprauteNpcEntity> pickupAllowNpcs =
                java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        private final Map<String, Boolean> positionHandlerMet = new HashMap<>();
        private final Map<String, Boolean> inventoryHandlerMet = new HashMap<>();

        /** Async tasks: id -> task. Named tasks can be awaited or stopped. */
        private final Map<String, AsyncTask> asyncTasks = new HashMap<>();
        private final java.util.Stack<TryBlock> tryStack = new java.util.Stack<>();
        private String waitTaskId = null;
        /** {@link #onUiAction} */
        private PlayerFilter waitUiPlayers = null;
        private boolean uiClickMet = false;
        private String uiClickWidgetId = "";
        private boolean uiClickClosed = false;
        private int uiClickMouseButton = 0;

        /** create ui: template bound while UI is open (click handlers). */
        private org.zonarstudio.spraute_engine.ui.UiTemplate boundUiTemplate;
        private UUID boundUiPlayerUuid;

        public ActiveScript(CompiledScript script, CommandSourceStack source) {
            this(script, source, null);
        }

        public ActiveScript(CompiledScript script, CommandSourceStack source, Map<String, Object> initialVariables) {
            this(script, source, initialVariables, script.getName());
        }

        public ActiveScript(CompiledScript script, CommandSourceStack source,
                            Map<String, Object> initialVariables, String instanceId) {
            this(script, source, initialVariables, instanceId, false);
        }

        public ActiveScript(CompiledScript script, CommandSourceStack source,
                            Map<String, Object> initialVariables, String instanceId,
                            boolean shareVariablesMap) {
            this.script = script;
            this.source = source;
            this.instanceId = instanceId;
            // Scene instances belong to the loaded scene, not the player who pressed Play.
            this.ownerPlayerId = isSceneInstance() ? null
                    : (source != null
                    && source.getEntity() instanceof net.minecraft.server.level.ServerPlayer player
                    ? player.getUUID() : null);
            if (shareVariablesMap && initialVariables != null) {
                this.variables = initialVariables;
            } else {
                this.variables = new HashMap<>();
                if (initialVariables != null) {
                    this.variables.putAll(initialVariables);
                }
            }
            context.setTaskChecker(id -> {
                AsyncTask t = asyncTasks.get(id);
                return t == null || t.finished || t.cancelled;
            });
            context.setUiSessionBinding(new org.zonarstudio.spraute_engine.ui.UiSessionBinding() {
                @Override
                public void onOpen(net.minecraft.world.entity.player.Player player, org.zonarstudio.spraute_engine.ui.UiTemplate template) {
                    boundUiTemplate = template;
                    boundUiPlayerUuid = player.getUUID();
                }

                @Override
                public void onClose(net.minecraft.world.entity.player.Player player) {
                    if (boundUiPlayerUuid != null && boundUiPlayerUuid.equals(player.getUUID())) {
                        boundUiTemplate = null;
                        boundUiPlayerUuid = null;
                    }
                }
            });
            context.setScriptName(script.getName());
        }

        /** Force stop script: deactivate handlers, timers, async tasks. */
        public void forceStop() {
            finished = true;
            eventHandlers.values().forEach(h -> h.active = false);
            timerHandlers.values().forEach(t -> t.active = false);
            asyncTasks.values().forEach(t -> t.cancelled = true);
        }

        /**
         * Lightweight cleanup run when a script finishes naturally each tick.
         * Must NOT close overlays — many short-lived scripts finish every tick and a blanket
         * close-all would nuke unrelated overlays (e.g. the chat bar) a frame after they open.
         */
        public void cleanup() {
            clearSyncedPickupAllows();
            clearNpcPickupAwaitCap(waitPickupNpcId, waitPickupNpcUuid, pickupAwaitCapSource);
            for (AsyncTask task : asyncTasks.values()) {
                clearNpcPickupAwaitCap(task.waitPickupNpcId, task.waitPickupNpcUuid, task);
            }
            waitPickupNpcUuid = null;
        }

        /** Full cleanup for explicit stops (/spraute stop, stopAll): also closes overlays and returns camera. */
        public void cleanupAndCloseOverlays() {
            cleanup();
            if (source.getServer() != null) {
                org.zonarstudio.spraute_engine.script.ScriptSoundTracker.stopForInstance(
                        source.getServer(), instanceId);
                org.zonarstudio.spraute_engine.script.CameraScriptUtil.resetOwnedBy(
                        source.getServer(), java.util.List.of(instanceId));
                org.zonarstudio.spraute_engine.network.ModNetwork.CHANNEL.send(net.minecraftforge.network.PacketDistributor.ALL.noArg(), new org.zonarstudio.spraute_engine.network.CloseSprauteOverlayPacket());
            }
        }

        private String getWaitStatusString(WaitType wt, double timer, String keybind, Object deathTarget, double mx, double my, double mz, UUID followTarget) {
            return switch (wt) {
                case NONE -> "Running";
                case TIME -> String.format("time (%.1fs)", timer);
                case INTERACT -> "interaction";
                case NEXT -> "next";
                case KEYBIND -> "keybind (" + keybind + ")";
                case DEATH -> "death (" + deathTarget + ")";
                case KILL -> "kill (" + deathTarget + ")";
                case UI_CLICK -> "uiClick";
                case UI_CLOSE -> "uiClose";
                case UI_DROP -> "uiDrop";
                case MOVE_TO -> String.format("move_to (%.0f, %.0f, %.0f)", mx, my, mz);
                case FOLLOW_ROUTE -> "follow_route";
                case FOLLOW -> "follow (" + followTarget + ")";
                case PICKUP -> "pickup";
                case ORB_PICKUP -> "orbPickup";
                case PROJECTILE_HIT -> "projectileHit";
                case TRADE_BUY -> "tradeBuy";
                case TRADE_SELL -> "tradeSell";
                case CUSTOM_EVENT -> "event (" + (waitCustomEventName != null ? waitCustomEventName : "?") + ")";
                case REL_GIFT -> "relGift";
                case REL_REP_CHANGE -> "relRepChange";
                case REL_TALK -> "relTalk";
                case REL_BUTTON -> "relButton";
                case WAIT_TASK -> "task";
                case POSITION -> "position";
                case INVENTORY -> "inventory";
                case CLICK_BLOCK -> "clickBlock";
                case BREAK_BLOCK -> "breakBlock";
                case PLACE_BLOCK -> "placeBlock";
                case OPEN_CHEST -> "openChest";
                case OPEN_DOOR -> "openDoor";
                case USE_BLOCK -> "useBlock";
                case UI_INPUT -> "uiInput";
                case CHAT -> "chat";
                case UI_OVERLAP -> "uiOverlap";
                case PLAYER_ACTION -> "action";
                case DIMENSION -> "dimension";
                case JOIN -> "join";
                case LEAVE -> "leave";
                case HITBOX_TOUCH -> "hitboxTouch";
                case HITBOX_EXIT -> "hitboxExit";
            };
        }

        public org.zonarstudio.spraute_engine.network.SyncDebugStatePacket.ScriptDebugData getDebugData() {
            List<org.zonarstudio.spraute_engine.network.SyncDebugStatePacket.TaskDebugData> tasks = new ArrayList<>();
            
            // Add main script task
            String mainStatus = getWaitStatusString(waitType, waitTimer, waitKeybindKey, waitDeathTarget, waitMoveTargetX, waitMoveTargetY, waitMoveTargetZ, waitFollowTargetUuid);
            if (waitType != WaitType.NONE) {
                tasks.add(new org.zonarstudio.spraute_engine.network.SyncDebugStatePacket.TaskDebugData("main", "Awaiting: " + mainStatus));
            } else {
                tasks.add(new org.zonarstudio.spraute_engine.network.SyncDebugStatePacket.TaskDebugData("main", "Running"));
            }

            // Add async tasks
            for (AsyncTask task : asyncTasks.values()) {
                if (task.finished || task.cancelled) continue;
                String taskStatus = getWaitStatusString(task.waitType, task.waitTimer, null, null, task.waitMoveTargetX, task.waitMoveTargetY, task.waitMoveTargetZ, task.waitFollowTargetUuid);
                if (task.waitType != WaitType.NONE) {
                    tasks.add(new org.zonarstudio.spraute_engine.network.SyncDebugStatePacket.TaskDebugData(task.id, "Awaiting: " + taskStatus));
                } else {
                    tasks.add(new org.zonarstudio.spraute_engine.network.SyncDebugStatePacket.TaskDebugData(task.id, "Running"));
                }
            }

            return new org.zonarstudio.spraute_engine.network.SyncDebugStatePacket.ScriptDebugData(script.getName(), tasks);
        }

        public void tick() {
            if (finished) return;

            CURRENT_ACTIVE_STACK.get().push(this);
            try {
                tickBody();
            } finally {
                Deque<ActiveScript> stack = CURRENT_ACTIVE_STACK.get();
                if (!stack.isEmpty() && stack.peek() == this) stack.pop();
            }
        }

        private void tickBody() {
            refreshSourceContext();
            if (finished) return;

            // Tick all active timers
            tickTimers();

            // Tick async tasks
            tickAsyncTasks();

            // Fire "pickup" event handlers (check each tick for item transition)
            // Must run before WAIT_TASK: await task(...) must not suppress pickup notifications.
            if (source.getLevel() != null) {
                syncNpcPickupAllows();
                for (var entry : snapshotEventHandlers()) {
                    EventHandler handler = entry.getValue();
                    if (!handler.active || !handler.eventName.equals("pickup")) continue;
                    if (handler.eventArgs.size() < 2) continue;

                    Object npcArg = handler.eventArgs.get(0);
                    net.minecraft.world.entity.Entity entity = resolveEntity(npcArg);

                    String itemId = String.valueOf(handler.eventArgs.get(1));
                    String tag = pickupHandlerNbt(handler.eventArgs);

                    net.minecraft.world.entity.Entity entityObj = entity; // Reusing previous variable
                    if (entityObj == null && npcArg instanceof String) {
                        entity = org.zonarstudio.spraute_engine.entity.NpcManager.getEntity((String)npcArg, source.getLevel());
                    }
                    if (entity == null && entityObj != null) entity = entityObj;

                    boolean shouldFire = false;
                    int pickupDelta = 0;
                    int pickupTotal = 0;
                    if (entity instanceof net.minecraft.world.entity.Mob mob) {
                        if (entity instanceof org.zonarstudio.spraute_engine.entity.SprauteNpcEntity) {
                            int currentCount = countMatchingItems(mob, itemId, tag);
                            int lastCount = pickupHandlerLastCount.getOrDefault(entry.getKey(), 0);
                            if (currentCount > lastCount) {
                                shouldFire = true;
                                pickupDelta = currentCount - lastCount;
                                pickupTotal = currentCount;
                                pickupHandlerLastCount.put(entry.getKey(), currentCount);
                            }
                        } else {
                            boolean hasItem = hasItemMatching(mob, itemId, tag);
                            boolean hadItem = pickupHandlerHadItem.getOrDefault(entry.getKey(), false);
                            if (hasItem && !hadItem) {
                                shouldFire = true;
                                pickupHandlerHadItem.put(entry.getKey(), true);
                            } else if (!hasItem) {
                                pickupHandlerHadItem.put(entry.getKey(), false);
                            }
                        }
                    }

                    if (shouldFire) {
                        net.minecraft.world.item.ItemStack foundStack = entity instanceof net.minecraft.world.entity.Mob mob
                            ? getMatchingItemStack(mob, itemId, tag) : null;
                        net.minecraft.world.entity.Entity eventDropper = null;
                        if (entity instanceof org.zonarstudio.spraute_engine.entity.SprauteNpcEntity sprauteNpc) {
                            java.util.UUID throwerUuid = sprauteNpc.getLastPickupThrower();
                            if (throwerUuid != null && source.getLevel() != null) {
                                eventDropper = source.getLevel().getEntity(throwerUuid);
                                if (eventDropper == null) eventDropper = source.getLevel().getPlayerByUUID(throwerUuid);
                            }
                        }
                        
                        Object prevNpc = variables.get("_eventNpc");
                        Object prevItem = variables.get("_eventItem");
                        Object prevDropper = variables.get("_eventDropper");
                        Object prevCount = variables.get("count");
                        Object prevItemCount = variables.get("_eventItemCount");
                        
                        variables.put("_eventNpc", entity);
                        variables.put("_eventItem", foundStack);
                        variables.put("_eventDropper", eventDropper);
                        variables.put("count", pickupDelta);
                        variables.put("_eventItemCount", pickupTotal);
                        try {
                            runEventHandlerBody(entry.getKey(), handler);
                        } catch (ReturnException e) {
                            // return exits handler body only; use stop handler to deactivate
                        } catch (Exception e) {
                            LOGGER.error("[Script: {}] Pickup handler '{}' error: {}", script.getName(), entry.getKey(), e.getMessage());
                        }
                        if (prevNpc != null) variables.put("_eventNpc", prevNpc);
                        else variables.remove("_eventNpc");
                        if (prevItem != null) variables.put("_eventItem", prevItem);
                        else variables.remove("_eventItem");
                        if (prevDropper != null) variables.put("_eventDropper", prevDropper);
                        else variables.remove("_eventDropper");
                        if (prevCount != null) variables.put("count", prevCount);
                        else variables.remove("count");
                        if (prevItemCount != null) variables.put("_eventItemCount", prevItemCount);
                        else variables.remove("_eventItemCount");
                    }
                }
            }

            // Fire position handlers
            if (source.getLevel() != null) {
                for (var entry : snapshotEventHandlers()) {
                    EventHandler handler = entry.getValue();
                    if (!handler.active || !handler.eventName.equals("position")) continue;
                    if (handler.eventArgs.size() < 4) continue;
                    
                    net.minecraft.world.entity.Entity playerEnt = resolveEntity(handler.eventArgs.get(0));
                    if (!(playerEnt instanceof net.minecraft.server.level.ServerPlayer sp)) continue;
                    
                    double px = ((Number) handler.eventArgs.get(1)).doubleValue();
                    double py = ((Number) handler.eventArgs.get(2)).doubleValue();
                    double pz = ((Number) handler.eventArgs.get(3)).doubleValue();
                    double r = handler.eventArgs.size() > 4 ? ((Number) handler.eventArgs.get(4)).doubleValue() : 1.5;
                    
                    boolean inRange = sp.distanceToSqr(px, py, pz) <= r * r;
                    boolean wasInRange = positionHandlerMet.getOrDefault(entry.getKey(), false);
                    
                    if (inRange && !wasInRange) {
                        positionHandlerMet.put(entry.getKey(), true);
                        Object prevPlayer = variables.get("_eventPlayer");
                        variables.put("_eventPlayer", sp);
                        try {
                            runEventHandlerBody(entry.getKey(), handler);
                        } catch (ReturnException e) {
                            // return exits handler body only; use stop handler to deactivate
                        } catch (Exception e) {
                            LOGGER.error("[Script: {}] Position handler '{}' error: {}", script.getName(), entry.getKey(), e.getMessage());
                        }
                        if (prevPlayer != null) variables.put("_eventPlayer", prevPlayer);
                        else variables.remove("_eventPlayer");
                    } else if (!inRange) {
                        positionHandlerMet.put(entry.getKey(), false);
                    }
                }
            }

            // Fire inventory handlers
            if (source.getLevel() != null) {
                for (var entry : snapshotEventHandlers()) {
                    EventHandler handler = entry.getValue();
                    if (!handler.active || (!handler.eventName.equals("inventory") && !handler.eventName.equals("hasItem"))) continue;
                    if (handler.eventArgs.size() < 2) continue;
                    
                    net.minecraft.world.entity.Entity playerEnt = resolveEntity(handler.eventArgs.get(0));
                    if (!(playerEnt instanceof net.minecraft.server.level.ServerPlayer sp)) continue;
                    
                    String itemId = String.valueOf(handler.eventArgs.get(1));
                    int count = handler.eventArgs.size() > 2 ? ((Number) handler.eventArgs.get(2)).intValue() : 0;
                    
                    boolean hasItems = playerMeetsInventoryRequirement(sp, itemId, count);
                    boolean didHaveItems = inventoryHandlerMet.getOrDefault(entry.getKey(), false);
                    
                    if (hasItems && !didHaveItems) {
                        inventoryHandlerMet.put(entry.getKey(), true);
                        Object prevPlayer = variables.get("_eventPlayer");
                        Object prevItemId = variables.get("_eventItemId");
                        Object prevItemCount = variables.get("_eventItemCount");
                        applyInventoryEventVars(sp, itemId);
                        try {
                            runEventHandlerBody(entry.getKey(), handler);
                        } catch (ReturnException e) {
                            // return exits handler body only; use stop handler to deactivate
                        } catch (Exception e) {
                            LOGGER.error("[Script: {}] Inventory handler '{}' error: {}", script.getName(), entry.getKey(), e.getMessage());
                        }
                        if (prevPlayer != null) variables.put("_eventPlayer", prevPlayer);
                        else variables.remove("_eventPlayer");
                        if (prevItemId != null) variables.put("_eventItemId", prevItemId);
                        else variables.remove("_eventItemId");
                        if (prevItemCount != null) variables.put("_eventItemCount", prevItemCount);
                        else variables.remove("_eventItemCount");
                    } else if (!hasItems) {
                        inventoryHandlerMet.put(entry.getKey(), false);
                    }
                }
            }

            // Handle WAIT_TASK (after pickup handlers so await task does not block pickup events)
            if (waitType == WaitType.WAIT_TASK && waitTaskId != null) {
                AsyncTask t = asyncTasks.get(waitTaskId);
                if (t == null || t.finished || t.cancelled) {
                    waitType = WaitType.NONE;
                    waitTaskId = null;
                } else {
                    return;
                }
            }

            // Handle waiting
            if (waitType == WaitType.TIME) {
                waitTimer -= 0.05;
                if (waitTimer <= 0) {
                    waitType = WaitType.NONE;
                } else {
                    return;
                }
            } else if (waitType == WaitType.NEXT) {
                waitType = WaitType.NONE;
            } else if (waitType == WaitType.INTERACT) {
                if (interactionMet) {
                    waitType = WaitType.NONE;
                    interactionMet = false;
                    if (pendingVarName != null && asyncResult != null) {
                        variables.put(pendingVarName, asyncResult);
                    }
                    asyncResult = null;
                    pendingVarName = null;
                } else {
                    return;
                }
            } else if (waitType == WaitType.KEYBIND) {
                if (keybindMet) {
                    waitType = WaitType.NONE;
                    keybindMet = false;
                    if (pendingVarName != null && keybindPlayer != null) {
                        variables.put(pendingVarName, keybindPlayer);
                    }
                    keybindPlayer = null;
                    pendingVarName = null;
                } else {
                    return;
                }
            } else if (waitType == WaitType.DEATH) {
                if (deathMet) {
                    waitType = WaitType.NONE;
                    deathMet = false;
                    if (pendingVarName != null && deathKiller != null) {
                        variables.put(pendingVarName, deathKiller);
                    }
                    deadEntity = null;
                    deathKiller = null;
                    pendingVarName = null;
                } else {
                    return;
                }
            } else if (waitType == WaitType.KILL) {
                if (killMet) {
                    waitType = WaitType.NONE;
                    killMet = false;
                    if (pendingVarName != null && deadEntity != null) {
                        variables.put(pendingVarName, deadEntity);
                    }
                    deadEntity = null;
                    deathKiller = null;
                    waitKillKillerTarget = null;
                    waitKillVictimTarget = null;
                    pendingVarName = null;
                } else {
                    return;
                }
                } else if (waitType == WaitType.UI_CLICK || waitType == WaitType.UI_CLOSE) {
                    if (uiClickMet && (waitType == WaitType.UI_CLICK || uiClickClosed)) {
                        waitType = WaitType.NONE;
                        uiClickMet = false;
                        waitUiPlayers = null;
                        if (pendingVarName != null) {
                            variables.put(pendingVarName, uiClickWidgetId != null ? uiClickWidgetId : "");
                            variables.put("_eventMouseButton",
                                    org.zonarstudio.spraute_engine.network.SprauteUiActionPacket.mouseButtonLabel(uiClickMouseButton));
                        }
                        variables.put("_ui_closed", uiClickClosed);
                        pendingVarName = null;
                        uiClickWidgetId = "";
                        uiClickClosed = false;
                        uiClickMouseButton = 0;
                    } else if (waitType == WaitType.UI_CLOSE && uiClickMet) {
                        uiClickMet = false;
                        uiClickWidgetId = "";
                        uiClickClosed = false;
                        uiClickMouseButton = 0;
                        return;
                    } else {
                        return;
                    }
                } else if (waitType == WaitType.UI_DROP) {
                    if (uiClickMet) {
                        waitType = WaitType.NONE;
                        uiClickMet = false;
                        waitUiPlayers = null;
                        applyUiDropVars(uiClickWidgetId, pendingVarName);
                        pendingVarName = null;
                        uiClickWidgetId = "";
                    } else {
                        return;
                    }
                } else if (waitType == WaitType.UI_INPUT) {
                    if (uiClickMet) {
                        waitType = WaitType.NONE;
                        uiClickMet = false;
                        waitUiPlayers = null;
                        if (pendingVarName != null) {
                            variables.put(pendingVarName, uiInputText != null ? uiInputText : "");
                        }
                        pendingVarName = null;
                        uiInputWidgetId = "";
                        uiInputText = "";
                    } else {
                        return;
                    }
                } else if (waitType == WaitType.TRADE_BUY || waitType == WaitType.TRADE_SELL
                        || waitType == WaitType.CUSTOM_EVENT
                        || waitType == WaitType.REL_GIFT || waitType == WaitType.REL_REP_CHANGE
                        || waitType == WaitType.REL_TALK || waitType == WaitType.REL_BUTTON
                        || waitType == WaitType.PROJECTILE_HIT) {
                    return;
                } else if (waitType == WaitType.CHAT) {
                    if (chatEventMet) {
                        waitType = WaitType.NONE;
                        chatEventMet = false;
                        waitChatPlayers = null;
                        if (pendingVarName != null) {
                            variables.put(pendingVarName, chatMatchedMessage);
                        }
                        pendingVarName = null;
                        chatMatchedMessage = "";
                        waitChatMessages = null;
                    } else {
                        return;
                    }
                } else if (waitType == WaitType.POSITION) {
                    if (waitPositionPlayers != null && source.getLevel() != null) {
                        net.minecraft.server.level.ServerPlayer sp = findOnlinePlayerAtPosition(waitPositionPlayers, waitPositionX, waitPositionY, waitPositionZ, waitPositionRadius);
                        if (sp != null) {
                            waitType = WaitType.NONE;
                            waitPositionPlayers = null;
                            if (pendingVarName != null) {
                                variables.put(pendingVarName, sp);
                            }
                            pendingVarName = null;
                        } else {
                            return;
                        }
                    } else {
                        return;
                    }
                } else if (waitType == WaitType.INVENTORY) {
                    if (waitInventoryPlayers != null && source.getLevel() != null) {
                        net.minecraft.server.level.ServerPlayer sp = findOnlinePlayerForFilter(waitInventoryPlayers);
                        if (sp != null) {
                            if (playerMeetsInventoryRequirement(sp, waitInventoryItemId, waitInventoryCount)) {
                                applyInventoryEventVars(sp, waitInventoryItemId);
                                waitType = WaitType.NONE;
                                waitInventoryPlayers = null;
                                if (pendingVarName != null) {
                                    variables.put(pendingVarName, sp);
                                }
                                pendingVarName = null;
                            } else {
                                return;
                            }
                        } else {
                            return;
                        }
                    } else {
                        return;
                    }
                } else if (waitType == WaitType.CLICK_BLOCK || waitType == WaitType.BREAK_BLOCK || waitType == WaitType.PLACE_BLOCK
                        || waitType == WaitType.OPEN_CHEST || waitType == WaitType.OPEN_DOOR || waitType == WaitType.USE_BLOCK) {
                    if (blockEventMet) {
                        waitType = WaitType.NONE;
                        blockEventMet = false;
                        waitBlockPlayers = null;
                        if (pendingVarName != null && asyncResult != null) {
                            variables.put(pendingVarName, asyncResult);
                        }
                        asyncResult = null;
                        pendingVarName = null;
                    } else {
                        return;
                    }
                } else if (waitType == WaitType.PLAYER_ACTION) {
                    if (playerActionMet) {
                        waitType = WaitType.NONE;
                        playerActionMet = false;
                        waitPlayerActionPlayers = null;
                        waitPlayerActionType = null;
                        waitPlayerActionTarget = null;
                        if (pendingVarName != null && asyncResult != null) {
                            variables.put(pendingVarName, asyncResult);
                        }
                        asyncResult = null;
                        pendingVarName = null;
                    } else {
                        return;
                    }
                } else if (waitType == WaitType.DIMENSION) {
                    if (dimensionEventMet) {
                        waitType = WaitType.NONE;
                        dimensionEventMet = false;
                        waitDimensionPlayers = null;
                        waitDimensionId = null;
                    } else {
                        return;
                    }
                } else if (waitType == WaitType.JOIN) {
                    if (joinEventMet) {
                        waitType = WaitType.NONE;
                        joinEventMet = false;
                        waitJoinPlayers = null;
                        waitFirstJoinOnly = false;
                        applyJoinWaitPlayer(waitJoinPlayerUuid);
                        waitJoinPlayerUuid = null;
                    } else {
                        return;
                    }
                } else if (waitType == WaitType.LEAVE) {
                    if (leaveEventMet) {
                        waitType = WaitType.NONE;
                        leaveEventMet = false;
                        waitLeavePlayers = null;
                        applyLeaveWaitPlayer(waitLeavePlayer);
                        waitLeavePlayer = null;
                    } else {
                        return;
                    }
                } else if (waitType == WaitType.HITBOX_TOUCH || waitType == WaitType.HITBOX_EXIT) {
                    if (hitboxEventMet) {
                        waitType = WaitType.NONE;
                        hitboxEventMet = false;
                        waitHitboxPlayers = null;
                        waitHitboxScene = null;
                        waitHitboxTriggerId = null;
                    } else {
                        return;
                    }
            } else if (waitType == WaitType.MOVE_TO) {
                if (waitEntityUuid != null && source.getLevel() != null) {
                    net.minecraft.world.entity.Entity entity = resolveWaitNpc(waitEntityUuid, waitNpcTrackId);
                    if (entity == null) {
                        if (npcWaitShouldHold(waitEntityUuid, waitNpcTrackId)) return;
                        waitType = WaitType.NONE;
                        waitEntityUuid = null;
                        waitNpcTrackId = null;
                    } else {
                        waitEntityUuid = entity.getUUID();
                        if (!entity.isAlive()) {
                            waitType = WaitType.NONE;
                            waitEntityUuid = null;
                            waitNpcTrackId = null;
                        } else if (entity instanceof net.minecraft.world.entity.Mob mob) {
                        if (isCloseToMoveTarget(mob, waitMoveTargetX, waitMoveTargetY, waitMoveTargetZ)) {
                            if (mob instanceof org.zonarstudio.spraute_engine.entity.SprauteNpcEntity snpc) {
                                snpc.clearScriptedMoveGoal();
                            }
                            waitType = WaitType.NONE;
                            waitEntityUuid = null;
                            waitNpcTrackId = null;
                        } else {
                                if (mob instanceof org.zonarstudio.spraute_engine.entity.SprauteNpcEntity snpc) {
                                    if (snpc.needsGroundMoveRetry()) {
                                        snpc.retryMoveTo(waitMoveTargetX, waitMoveTargetY, waitMoveTargetZ, waitMoveSpeed);
                                    }
                                } else {
                                    var nav = mob.getNavigation();
                                    if (nav.isDone() || nav.isStuck()) {
                                        mob.getNavigation().moveTo(waitMoveTargetX, waitMoveTargetY, waitMoveTargetZ, waitMoveSpeed);
                                    }
                                }
                            return;
                        }
                    } else {
                        return;
                    }
                    }
                } else {
                    return;
                }
            } else if (waitType == WaitType.FOLLOW_ROUTE) {
                if (waitEntityUuid != null && source.getLevel() != null) {
                    net.minecraft.world.entity.Entity entity = resolveWaitNpc(waitEntityUuid, waitNpcTrackId);
                    if (entity == null) {
                        if (npcWaitShouldHold(waitEntityUuid, waitNpcTrackId)) return;
                        waitType = WaitType.NONE;
                        waitEntityUuid = null;
                        waitNpcTrackId = null;
                    } else if (entity instanceof org.zonarstudio.spraute_engine.entity.SprauteNpcEntity snpc
                            && snpc.isAlive() && snpc.isFollowingRoute()) {
                        waitEntityUuid = snpc.getUUID();
                        return;
                    } else {
                        waitType = WaitType.NONE;
                        waitEntityUuid = null;
                        waitNpcTrackId = null;
                    }
                } else {
                    return;
                }
            } else if (waitType == WaitType.FOLLOW) {
                if (waitEntityUuid != null && waitFollowTargetUuid != null && source.getLevel() != null) {
                    net.minecraft.world.entity.Entity npcEntity = findWaitEntity(waitEntityUuid);
                    net.minecraft.world.entity.Entity targetEntity = findWaitEntity(waitFollowTargetUuid);
                    if (npcEntity == null || targetEntity == null) {
                        return;
                    }
                    if (!npcEntity.isAlive() || !targetEntity.isAlive()) {
                        waitType = WaitType.NONE;
                        waitEntityUuid = null;
                        waitFollowTargetUuid = null;
                    } else {
                        double dist = npcEntity.distanceTo(targetEntity);
                        if (dist <= waitFollowStopDistance) {
                            waitType = WaitType.NONE;
                            waitEntityUuid = null;
                            waitFollowTargetUuid = null;
                        } else {
                            if (npcEntity instanceof net.minecraft.world.entity.Mob mob) {
                                if (npcEntity instanceof org.zonarstudio.spraute_engine.entity.SprauteNpcEntity snpc) {
                                    snpc.retryMoveTo(targetEntity.getX(), targetEntity.getY(), targetEntity.getZ(), 1.0);
                                } else {
                                    mob.getNavigation().moveTo(targetEntity.getX(), targetEntity.getY(), targetEntity.getZ(), 1.0);
                                }
                            }
                            return;
                        }
                    }
                } else {
                    return;
                }
            } else if (waitType == WaitType.PICKUP) {
                if ((waitPickupNpcUuid != null || waitPickupNpcId != null) && source.getLevel() != null) {
                    net.minecraft.world.entity.Entity entity = waitPickupNpcUuid != null
                            ? findWaitEntity(waitPickupNpcUuid)
                            : null;
                    if (entity == null && waitPickupNpcId != null) {
                        entity = org.zonarstudio.spraute_engine.entity.NpcManager.getEntity(waitPickupNpcId, source.getLevel());
                    }
                    if (entity == null) {
                        return;
                    }
                    if (!entity.isAlive()) {
                        clearNpcPickupAwaitCap(waitPickupNpcId, waitPickupNpcUuid, pickupAwaitCapSource);
                        waitType = WaitType.NONE;
                        waitPickupNpcUuid = null;
                        waitPickupNpcId = null;
                        waitPickupItemId = null;
                        waitPickupTag = null;
                        waitPickupBaseCount = 0;
                        waitPickupMaxCount = -1;
                        pendingVarName = null;
                    } else if (entity instanceof net.minecraft.world.entity.Mob mob) {
                        int currentCount = countMatchingItems(mob, waitPickupItemId, waitPickupTag);
                        int pickedUp = currentCount - waitPickupBaseCount;
                        boolean done = false;
                        if (waitPickupMaxCount >= 0) {
                            // Has a cap: wait until we've picked up enough
                            if (pickedUp >= waitPickupMaxCount) {
                                pickedUp = waitPickupMaxCount;
                                done = true;
                            }
                        } else {
                            // No cap: complete on first pickup
                            if (pickedUp > 0) {
                                done = true;
                            }
                        }
                        if (done) {
                            if (pendingVarName != null) {
                                variables.put(pendingVarName, pickedUp);
                                pendingVarName = null;
                            }
                            clearNpcPickupAwaitCap(waitPickupNpcId, waitPickupNpcUuid, pickupAwaitCapSource);
                            waitType = WaitType.NONE;
                            waitPickupNpcUuid = null;
                            waitPickupNpcId = null;
                            waitPickupItemId = null;
                            waitPickupTag = null;
                            waitPickupBaseCount = 0;
                            waitPickupMaxCount = -1;
                        } else {
                            return;
                        }
                    } else {
                        return;
                    }
                } else {
                    waitType = WaitType.NONE;
                    waitPickupNpcUuid = null;
                    waitPickupNpcId = null;
                    waitPickupItemId = null;
                    waitPickupTag = null;
                    waitPickupBaseCount = 0;
                    waitPickupMaxCount = -1;
                    pendingVarName = null;
                }
            }

            // Execute instructions until we hit a wait or finish
            while (ip < script.getInstructions().size()) {
                CompiledScript.Instruction instruction = script.getInstructions().get(ip);
                try {
                    boolean shouldPause = executeInstruction(instruction, tryStack, null);
                    ip++;
                    if (shouldPause) return;
                } catch (Exception e) {
                    if (!tryStack.isEmpty()) {
                        TryBlock tb = tryStack.pop();
                        ip = tb.catchIp;
                        if (tb.catchVar != null) {
                            variables.put(tb.catchVar, e.getMessage() != null ? e.getMessage() : e.toString());
                        }
                    } else {
                        String errMsg = e.getMessage() != null ? e.getMessage() : e.toString();
                        LOGGER.error("[Script: {}] Runtime error at line {}: {} - {}",
                                script.getName(), instruction.getLine(), instruction.getOpcode(), errMsg);
                        ScriptConsole.runtimeError(script.getName(), instruction.getLine(), errMsg);
                        if (source != null) {
                            source.sendFailure(Component.literal(
                                    SprauteMessages.chatRuntimeError(script.getName(), instruction.getLine(), errMsg,
                                            SprauteMessages.isEnglish(source))));
                        }
                        finished = true;
                        return;
                    }
                }
            }

            // Main bytecode finished: keep script alive while any on/every handler is active or async tasks run
            // (subscriptions from on keybind / on interact / etc. must not terminate the script early)
            if (eventHandlers.values().stream().anyMatch(h -> h.active)
                    || timerHandlers.values().stream().anyMatch(h -> h.active)
                    || !asyncTasks.isEmpty()) {
                ip = script.getInstructions().size(); // prevent re-execution of top-level instructions
                return;
            }

            // A started script remains registered even after its top-level bytecode
            // completes. It is stopped only explicitly, on server shutdown/reload,
            // or when a player-owned (non-scene) script's owner leaves.
            ip = script.getInstructions().size();
        }

        private boolean isSceneInstance() {
            return instanceId != null && instanceId.startsWith("scene:");
        }

        /**
         * Player-owned scripts are not bound to the dimension in which they were
         * started. Their command context follows the owner every tick.
         * Scene scripts stay alive even if the player who pressed Play leaves.
         */
        private void refreshSourceContext() {
            if (source == null || source.getServer() == null) return;
            if (isSceneInstance()) {
                refreshSceneSourceContext();
                return;
            }
            if (ownerPlayerId == null) return;
            net.minecraft.server.level.ServerPlayer player =
                    source.getServer().getPlayerList().getPlayer(ownerPlayerId);
            if (player == null) {
                forceStop();
                return;
            }
            source = source.withEntity(player)
                    .withLevel(org.zonarstudio.spraute_engine.compat.SprauteEntityCompat.serverLevel(player))
                    .withPosition(player.position())
                    .withRotation(new net.minecraft.world.phys.Vec2(player.getXRot(), player.getYRot()));
        }

        private void refreshSceneSourceContext() {
            net.minecraft.world.entity.Entity current = source.getEntity();
            if (current instanceof net.minecraft.server.level.ServerPlayer sp
                    && source.getServer().getPlayerList().getPlayer(sp.getUUID()) == sp) {
                source = source.withEntity(sp)
                        .withLevel(org.zonarstudio.spraute_engine.compat.SprauteEntityCompat.serverLevel(sp))
                        .withPosition(sp.position())
                        .withRotation(new net.minecraft.world.phys.Vec2(sp.getXRot(), sp.getYRot()));
                return;
            }
            var server = source.getServer();
            var fallback = server.createCommandSourceStack().withPermission(4);
            if (source.getLevel() != null) fallback = fallback.withLevel(source.getLevel());
            source = fallback;
        }

        private void tickTimers() {
            for (var entry : timerHandlers.entrySet()) {
                TimerHandler timer = entry.getValue();
                if (!timer.active) continue;

                timer.countdown -= 0.05;
                if (timer.countdown <= 0) {
                    timer.countdown = timer.intervalSeconds;
                    try {
                        runEventHandlerBody("every:" + entry.getKey(), timer.bodyInstructions);
                    } catch (ReturnException e) {
                        // return exits timer body only; use stop handler to deactivate
                    } catch (Exception e) {
                        LOGGER.error("[Script: {}] Timer '{}' error: {}", script.getName(), entry.getKey(), e.getMessage());
                        timer.active = false;
                    }
                }
            }
        }

        private void tickAsyncTasks() {
            List<Map.Entry<String, AsyncTask>> snapshot = new java.util.ArrayList<>(asyncTasks.entrySet());
            Iterator<Map.Entry<String, AsyncTask>> it = snapshot.iterator();
            while (it.hasNext()) {
                Map.Entry<String, AsyncTask> entry = it.next();
                if (!asyncTasks.containsKey(entry.getKey())) continue;
                AsyncTask task = entry.getValue();
                if (task.cancelled || task.finished) {
                    asyncTasks.remove(entry.getKey());
                    continue;
                }
                this.currentTaskScope = task;
                try {
                    if (task.waitType == WaitType.TIME) {
                    task.waitTimer -= 0.05;
                    if (task.waitTimer <= 0) {
                        task.waitType = WaitType.NONE;
                        task.ip++;
                    } else {
                        continue;
                    }
                } else if (task.waitType == WaitType.MOVE_TO) {
                    if (task.waitEntityUuid != null && source.getLevel() != null) {
                        net.minecraft.world.entity.Entity entity = resolveWaitNpc(task.waitEntityUuid, task.waitNpcTrackId);
                        if (entity == null) {
                            if (npcWaitShouldHold(task.waitEntityUuid, task.waitNpcTrackId)) continue;
                            task.waitType = WaitType.NONE;
                            task.waitEntityUuid = null;
                            task.waitNpcTrackId = null;
                            task.ip++;
                        } else {
                            task.waitEntityUuid = entity.getUUID();
                            if (!entity.isAlive()) {
                                task.waitType = WaitType.NONE;
                                task.waitEntityUuid = null;
                                task.waitNpcTrackId = null;
                                task.ip++;
                            } else if (entity instanceof net.minecraft.world.entity.Mob mob) {
                            if (isCloseToMoveTarget(mob, task.waitMoveTargetX, task.waitMoveTargetY, task.waitMoveTargetZ)) {
                                if (mob instanceof org.zonarstudio.spraute_engine.entity.SprauteNpcEntity snpc) {
                                    snpc.clearScriptedMoveGoal();
                                }
                                task.waitType = WaitType.NONE;
                                task.waitEntityUuid = null;
                                task.waitNpcTrackId = null;
                                task.ip++;
                            } else {
                                if (mob instanceof org.zonarstudio.spraute_engine.entity.SprauteNpcEntity snpc) {
                                    if (snpc.needsGroundMoveRetry()) {
                                        snpc.retryMoveTo(task.waitMoveTargetX, task.waitMoveTargetY, task.waitMoveTargetZ, task.waitMoveSpeed);
                                    }
                                } else {
                                    var nav = mob.getNavigation();
                                    if (nav.isDone() || nav.isStuck()) {
                                        mob.getNavigation().moveTo(task.waitMoveTargetX, task.waitMoveTargetY, task.waitMoveTargetZ, task.waitMoveSpeed);
                                    }
                                }
                                continue;
                            }
                            } else {
                                continue;
                            }
                        }
                    } else {
                        continue;
                    }
                } else if (task.waitType == WaitType.FOLLOW_ROUTE) {
                    if (task.waitEntityUuid != null && source.getLevel() != null) {
                        net.minecraft.world.entity.Entity entity = resolveWaitNpc(task.waitEntityUuid, task.waitNpcTrackId);
                        if (entity == null) {
                            if (npcWaitShouldHold(task.waitEntityUuid, task.waitNpcTrackId)) continue;
                            task.waitType = WaitType.NONE;
                            task.waitEntityUuid = null;
                            task.waitNpcTrackId = null;
                            task.ip++;
                        } else if (entity instanceof org.zonarstudio.spraute_engine.entity.SprauteNpcEntity snpc
                                && snpc.isAlive() && snpc.isFollowingRoute()) {
                            task.waitEntityUuid = snpc.getUUID();
                            continue;
                        } else {
                            task.waitType = WaitType.NONE;
                            task.waitEntityUuid = null;
                            task.waitNpcTrackId = null;
                            task.ip++;
                        }
                    } else {
                        continue;
                    }
                } else if (task.waitType == WaitType.FOLLOW) {
                    if (task.waitEntityUuid != null && task.waitFollowTargetUuid != null && source.getLevel() != null) {
                        net.minecraft.world.entity.Entity npcEntity = findWaitEntity(task.waitEntityUuid);
                        net.minecraft.world.entity.Entity targetEntity = findWaitEntity(task.waitFollowTargetUuid);
                        if (npcEntity == null || targetEntity == null) {
                            continue;
                        }
                        if (!npcEntity.isAlive() || !targetEntity.isAlive()) {
                            task.waitType = WaitType.NONE;
                            task.waitEntityUuid = null;
                            task.waitFollowTargetUuid = null;
                            task.ip++;
                        } else {
                            double dist = npcEntity.distanceTo(targetEntity);
                            if (dist <= task.waitFollowStopDistance) {
                                task.waitType = WaitType.NONE;
                                task.waitEntityUuid = null;
                                task.waitFollowTargetUuid = null;
                                task.ip++;
                            } else {
                                if (npcEntity instanceof net.minecraft.world.entity.Mob mob) {
                                    if (npcEntity instanceof org.zonarstudio.spraute_engine.entity.SprauteNpcEntity snpc) {
                                        snpc.retryMoveTo(targetEntity.getX(), targetEntity.getY(), targetEntity.getZ(), 1.0);
                                    } else {
                                        mob.getNavigation().moveTo(targetEntity.getX(), targetEntity.getY(), targetEntity.getZ(), 1.0);
                                    }
                                }
                                continue;
                            }
                        }
                    } else {
                        continue;
                    }
                } else if (task.waitType == WaitType.UI_CLICK || task.waitType == WaitType.UI_CLOSE) {
                    if (task.uiClickMet && (task.waitType == WaitType.UI_CLICK || task.uiClickClosed)) {
                        task.waitType = WaitType.NONE;
                        task.waitUiPlayers = null;
                        if (task.pendingUiClickVarName != null) {
                            putVariable(task.pendingUiClickVarName, task.uiClickWidgetId != null ? task.uiClickWidgetId : "");
                            putVariable("_eventMouseButton",
                                    org.zonarstudio.spraute_engine.network.SprauteUiActionPacket.mouseButtonLabel(task.uiClickMouseButton));
                        }
                        variables.put("_ui_closed", task.uiClickClosed);
                        task.pendingUiClickVarName = null;
                        task.uiClickMet = false;
                        task.uiClickWidgetId = "";
                        task.uiClickClosed = false;
                        task.uiClickMouseButton = 0;
                        task.ip++;
                    } else if (task.waitType == WaitType.UI_CLOSE && task.uiClickMet) {
                        task.uiClickMet = false;
                        task.uiClickWidgetId = "";
                        task.uiClickClosed = false;
                        continue;
                    } else {
                        continue;
                    }
                } else if (task.waitType == WaitType.UI_DROP) {
                    if (task.uiClickMet) {
                        task.waitType = WaitType.NONE;
                        task.waitUiPlayers = null;
                        applyUiDropVars(task.uiClickWidgetId, task.pendingUiClickVarName);
                        task.pendingUiClickVarName = null;
                        task.uiClickMet = false;
                        task.uiClickWidgetId = "";
                        task.ip++;
                    } else {
                        continue;
                    }
                } else if (task.waitType == WaitType.UI_INPUT) {
                    if (task.uiClickMet) {
                        task.waitType = WaitType.NONE;
                        task.waitUiPlayers = null;
                        if (task.pendingUiClickVarName != null) {
                            putVariable(task.pendingUiClickVarName, task.uiInputText != null ? task.uiInputText : "");
                        }
                        task.pendingUiClickVarName = null;
                        task.uiClickMet = false;
                        task.uiInputWidgetId = "";
                        task.uiInputText = "";
                        task.ip++;
                    } else {
                        continue;
                    }
                } else if (task.waitType == WaitType.TRADE_BUY || task.waitType == WaitType.TRADE_SELL
                        || task.waitType == WaitType.CUSTOM_EVENT
                        || task.waitType == WaitType.REL_GIFT || task.waitType == WaitType.REL_REP_CHANGE
                        || task.waitType == WaitType.REL_TALK || task.waitType == WaitType.REL_BUTTON) {
                    continue;
                } else if (task.waitType == WaitType.CHAT) {
                    if (task.chatEventMet) {
                        task.waitType = WaitType.NONE;
                        task.waitChatPlayers = null;
                        if (task.pendingUiClickVarName != null) {
                            putVariable(task.pendingUiClickVarName, task.chatMatchedMessage);
                        }
                        task.pendingUiClickVarName = null;
                        task.chatEventMet = false;
                        task.chatMatchedMessage = "";
                        task.waitChatMessages = null;
                        task.ip++;
                    } else {
                        continue;
                    }
                } else if (task.waitType == WaitType.POSITION) {
                    if (task.waitPositionPlayers != null && source.getLevel() != null) {
                        net.minecraft.server.level.ServerPlayer sp = findOnlinePlayerAtPosition(task.waitPositionPlayers, task.waitPositionX, task.waitPositionY, task.waitPositionZ, task.waitPositionRadius);
                        if (sp != null) {
                            task.waitType = WaitType.NONE;
                            task.waitPositionPlayers = null;
                            if (task.pendingUiClickVarName != null) {
                                putVariable(task.pendingUiClickVarName, sp);
                            }
                            task.pendingUiClickVarName = null;
                            task.ip++;
                        } else {
                            continue;
                        }
                    } else {
                        continue;
                    }
                } else if (task.waitType == WaitType.INVENTORY) {
                    if (task.waitInventoryPlayers != null && source.getLevel() != null) {
                        net.minecraft.server.level.ServerPlayer sp = findOnlinePlayerForFilter(task.waitInventoryPlayers);
                        if (sp != null) {
                            if (playerMeetsInventoryRequirement(sp, task.waitInventoryItemId, task.waitInventoryCount)) {
                                applyInventoryEventVars(sp, task.waitInventoryItemId);
                                task.waitType = WaitType.NONE;
                                task.waitInventoryPlayers = null;
                                if (task.pendingUiClickVarName != null) {
                                    putVariable(task.pendingUiClickVarName, sp);
                                }
                                task.pendingUiClickVarName = null;
                                task.ip++;
                            } else {
                                continue;
                            }
                        } else {
                            continue;
                        }
                    } else {
                        continue;
                    }
                } else if (task.waitType == WaitType.CLICK_BLOCK || task.waitType == WaitType.BREAK_BLOCK || task.waitType == WaitType.PLACE_BLOCK
                        || task.waitType == WaitType.OPEN_CHEST || task.waitType == WaitType.OPEN_DOOR || task.waitType == WaitType.USE_BLOCK) {
                    if (task.blockEventMet) {
                        task.waitType = WaitType.NONE;
                        task.blockEventMet = false;
                        task.waitBlockPlayers = null;
                        if (task.pendingUiClickVarName != null && asyncResult != null) {
                            putVariable(task.pendingUiClickVarName, asyncResult);
                        }
                        asyncResult = null;
                        task.pendingUiClickVarName = null;
                        task.ip++;
                    } else {
                        continue;
                    }
                } else if (task.waitType == WaitType.PLAYER_ACTION) {
                    if (task.playerActionMet) {
                        task.waitType = WaitType.NONE;
                        task.playerActionMet = false;
                        task.waitPlayerActionPlayers = null;
                        task.waitPlayerActionType = null;
                        task.waitPlayerActionTarget = null;
                        if (task.pendingUiClickVarName != null && asyncResult != null) {
                            putVariable(task.pendingUiClickVarName, asyncResult);
                        }
                        asyncResult = null;
                        task.pendingUiClickVarName = null;
                        task.ip++;
                    } else {
                        continue;
                    }
                } else if (task.waitType == WaitType.DIMENSION) {
                    if (task.dimensionEventMet) {
                        task.waitType = WaitType.NONE;
                        task.dimensionEventMet = false;
                        task.waitDimensionPlayers = null;
                        task.waitDimensionId = null;
                        task.ip++;
                    } else {
                        continue;
                    }
                } else if (task.waitType == WaitType.JOIN) {
                    if (task.joinEventMet) {
                        task.waitType = WaitType.NONE;
                        task.joinEventMet = false;
                        task.waitJoinPlayers = null;
                        task.waitFirstJoinOnly = false;
                        applyJoinWaitPlayer(task.waitJoinPlayerUuid);
                        task.waitJoinPlayerUuid = null;
                        task.ip++;
                    } else {
                        continue;
                    }
                } else if (task.waitType == WaitType.LEAVE) {
                    if (task.leaveEventMet) {
                        task.waitType = WaitType.NONE;
                        task.leaveEventMet = false;
                        task.waitLeavePlayers = null;
                        applyLeaveWaitPlayer(task.waitLeavePlayer);
                        task.waitLeavePlayer = null;
                        task.ip++;
                    } else {
                        continue;
                    }
                } else if (task.waitType == WaitType.NEXT) {
                    task.waitType = WaitType.NONE;
                    task.ip++;
                } else if (task.waitType == WaitType.WAIT_TASK) {
                    if (task.waitTaskId != null) {
                        AsyncTask other = asyncTasks.get(task.waitTaskId);
                        if (other == null || other.finished || other.cancelled) {
                            task.waitType = WaitType.NONE;
                            task.waitTaskId = null;
                            task.ip++;
                        } else {
                            continue;
                        }
                    } else {
                        task.waitType = WaitType.NONE;
                        task.ip++;
                    }
                } else if (task.waitType == WaitType.INTERACT) {
                    if (task.interactionMet) {
                        task.waitType = WaitType.NONE;
                        task.interactionMet = false;
                        task.waitEntityUuid = null;
                        if (task.pendingUiClickVarName != null && task.waitResult != null) {
                            putVariable(task.pendingUiClickVarName, task.waitResult);
                        }
                        task.waitResult = null;
                        task.pendingUiClickVarName = null;
                        task.ip++;
                    } else {
                        continue;
                    }
                } else if (task.waitType == WaitType.KEYBIND) {
                    if (task.keybindMet) {
                        task.waitType = WaitType.NONE;
                        task.keybindMet = false;
                        if (task.pendingUiClickVarName != null && task.keybindPlayer != null) {
                            putVariable(task.pendingUiClickVarName, task.keybindPlayer);
                        }
                        task.keybindPlayer = null;
                        task.pendingUiClickVarName = null;
                        task.ip++;
                    } else {
                        continue;
                    }
                } else if (task.waitType == WaitType.DEATH) {
                    if (task.deathMet) {
                        task.waitType = WaitType.NONE;
                        task.deathMet = false;
                        if (task.pendingUiClickVarName != null && task.deathKiller != null) {
                            putVariable(task.pendingUiClickVarName, task.deathKiller);
                        }
                        task.deadEntity = null;
                        task.deathKiller = null;
                        task.pendingUiClickVarName = null;
                        task.ip++;
                    } else {
                        continue;
                    }
                } else if (task.waitType == WaitType.KILL) {
                    if (task.killMet) {
                        task.waitType = WaitType.NONE;
                        task.killMet = false;
                        if (task.pendingUiClickVarName != null && task.deadEntity != null) {
                            putVariable(task.pendingUiClickVarName, task.deadEntity);
                        }
                        task.deadEntity = null;
                        task.deathKiller = null;
                        task.waitKillKillerTarget = null;
                        task.waitKillVictimTarget = null;
                        task.pendingUiClickVarName = null;
                        task.ip++;
                    } else {
                        continue;
                    }
                } else if (task.waitType == WaitType.PICKUP) {
                    if ((task.waitPickupNpcUuid != null || task.waitPickupNpcId != null) && source.getLevel() != null) {
                        net.minecraft.world.entity.Entity entity = task.waitPickupNpcUuid != null
                                ? findWaitEntity(task.waitPickupNpcUuid) : null;
                        if (entity == null && task.waitPickupNpcId != null) {
                            entity = org.zonarstudio.spraute_engine.entity.NpcManager.getEntity(task.waitPickupNpcId, source.getLevel());
                        }
                        if (entity == null) {
                            continue;
                        }
                        if (!entity.isAlive()) {
                            clearNpcPickupAwaitCap(task.waitPickupNpcId, task.waitPickupNpcUuid, task);
                            task.waitType = WaitType.NONE;
                            task.waitPickupNpcUuid = null;
                            task.waitPickupNpcId = null;
                            task.waitPickupItemId = null;
                            task.waitPickupTag = null;
                            task.waitPickupBaseCount = 0;
                            task.waitPickupMaxCount = -1;
                            task.pendingUiClickVarName = null;
                            task.ip++;
                        } else if (entity instanceof net.minecraft.world.entity.Mob mob) {
                            int currentCount = countMatchingItems(mob, task.waitPickupItemId, task.waitPickupTag);
                            int pickedUp = currentCount - task.waitPickupBaseCount;
                            boolean done = false;
                            if (task.waitPickupMaxCount >= 0) {
                                if (pickedUp >= task.waitPickupMaxCount) {
                                    pickedUp = task.waitPickupMaxCount;
                                    done = true;
                                }
                            } else if (pickedUp > 0) {
                                done = true;
                            }
                            if (done) {
                                if (task.pendingUiClickVarName != null) {
                                    putVariable(task.pendingUiClickVarName, pickedUp);
                                    task.pendingUiClickVarName = null;
                                }
                                clearNpcPickupAwaitCap(task.waitPickupNpcId, task.waitPickupNpcUuid, task);
                                task.waitType = WaitType.NONE;
                                task.waitPickupNpcUuid = null;
                                task.waitPickupNpcId = null;
                                task.waitPickupItemId = null;
                                task.waitPickupTag = null;
                                task.waitPickupBaseCount = 0;
                                task.waitPickupMaxCount = -1;
                                task.ip++;
                            } else {
                                continue;
                            }
                        } else {
                            continue;
                        }
                    } else {
                        task.waitType = WaitType.NONE;
                        task.waitPickupNpcUuid = null;
                        task.waitPickupNpcId = null;
                        task.ip++;
                    }
                } else if (task.waitType == WaitType.ORB_PICKUP) {
                    continue;
                } else if (task.waitType == WaitType.PROJECTILE_HIT) {
                    continue;
                } else if (task.waitType == WaitType.UI_OVERLAP) {
                    if (task.uiOverlapMet) {
                        task.waitType = WaitType.NONE;
                        task.waitUiOverlapPlayers = null;
                        task.waitUiOverlapId1 = "";
                        task.waitUiOverlapId2 = "";
                        task.uiOverlapMet = false;
                        task.ip++;
                    } else {
                        continue;
                    }
                } else if (task.waitType == WaitType.HITBOX_TOUCH || task.waitType == WaitType.HITBOX_EXIT) {
                    if (task.hitboxEventMet) {
                        task.waitType = WaitType.NONE;
                        task.hitboxEventMet = false;
                        task.waitHitboxPlayers = null;
                        task.waitHitboxScene = null;
                        task.waitHitboxTriggerId = null;
                        task.ip++;
                    } else {
                        continue;
                    }
                }
                while (task.ip < task.instructions.size() && !task.cancelled) {
                    try {
                        boolean paused = executeTaskInstruction(task);
                        if (paused) break;
                        if (!task.skipIpIncrement) {
                            task.ip++;
                        }
                        task.skipIpIncrement = false;
                    } catch (ReturnException e) {
                        task.finished = true;
                        break;
                    } catch (Exception e) {
                        LOGGER.error("[Script: {}] Async task '{}' error: {}", script.getName(), task.id, e.getMessage());
                        task.finished = true;
                        break;
                    }
                }
                if (task.ip >= task.instructions.size()) {
                    if (task.leaveUserFunction(null, variables)) {
                        // resumed parent function frame
                    } else {
                        task.finished = true;
                    }
                }
                } finally {
                    this.currentTaskScope = null;
                }
            }
        }

        /** Starts await from `val x = await fn(...)` / `x = await fn(...)` inside an async task. */
        private boolean beginTaskAwaitFromCall(AsyncTask task, String varName, ScriptNode.FunctionCallNode call) {
            String fn = call.getFunctionName();
            if (fn.equals("time") && !call.getArgs().isEmpty()) {
                Object sec = evaluateExpression(call.getArgs().get(0));
                task.waitTimer = coerceSeconds(sec, task.id);
                task.waitType = WaitType.TIME;
                return true;
            } else if (fn.equals("next")) {
                task.waitType = WaitType.NEXT;
                return true;
            } else if (fn.equals("interact")) {
                if (call.getArgs().isEmpty()) return false;
                Object val = evaluateExpression(call.getArgs().get(0));
                net.minecraft.world.entity.Entity targetEntity = resolveEntity(val);
                if (targetEntity != null) {
                    task.waitEntityUuid = targetEntity.getUUID();
                    task.waitType = WaitType.INTERACT;
                    task.interactionMet = false;
                    task.pendingUiClickVarName = varName;
                    return true;
                }
            } else if (fn.equals("death")) {
                if (call.getArgs().isEmpty()) return false;
                task.waitDeathTarget = normalizeEntityWaitTarget(evaluateExpression(call.getArgs().get(0)));
                task.deathMet = false;
                task.waitType = WaitType.DEATH;
                task.pendingUiClickVarName = varName;
                return true;
            } else if (fn.equals("kill")) {
                if (call.getArgs().isEmpty()) return false;
                task.waitKillKillerTarget = normalizeEntityWaitTarget(evaluateExpression(call.getArgs().get(0)));
                task.waitKillVictimTarget = call.getArgs().size() > 1
                        ? normalizeEntityWaitTarget(evaluateExpression(call.getArgs().get(1))) : "any";
                task.killMet = false;
                task.waitType = WaitType.KILL;
                task.pendingUiClickVarName = varName;
                return true;
            } else if (fn.equals("keybind")) {
                if (call.getArgs().isEmpty()) return false;
                task.waitKeybindKey = String.valueOf(evaluateExpression(call.getArgs().get(0)));
                task.keybindMet = false;
                task.waitType = WaitType.KEYBIND;
                task.pendingUiClickVarName = varName;
                return true;
            } else if (fn.equals("chat")) {
                if (call.getArgs().size() < 2) return false;
                Object playerArg = evaluateExpression(call.getArgs().get(0));
                PlayerFilter playerFilter = buildPlayerFilter(playerArg);
                if (playerFilter != null) {
                    task.waitChatPlayers = playerFilter;
                    Object msgs = evaluateExpression(call.getArgs().get(1));
                    task.waitChatMessages = new ArrayList<>();
                    if (msgs instanceof List list) {
                        for (Object o : list) task.waitChatMessages.add(String.valueOf(o));
                    } else {
                        task.waitChatMessages.add(String.valueOf(msgs));
                    }
                    task.waitChatIgnoreCase = true;
                    task.waitChatIgnorePunct = true;
                    if (call.getArgs().size() > 2) task.waitChatIgnoreCase = (Boolean) evaluateExpression(call.getArgs().get(2));
                    if (call.getArgs().size() > 3) task.waitChatIgnorePunct = (Boolean) evaluateExpression(call.getArgs().get(3));
                    task.chatEventMet = false;
                    task.chatMatchedMessage = "";
                    task.waitType = WaitType.CHAT;
                    task.pendingUiClickVarName = varName;
                    return true;
                }
            } else if (fn.equals("pickup")) {
                if (call.getArgs().size() < 3) return false;
                Object pickupNpcArg = evaluateExpression(call.getArgs().get(0));
                net.minecraft.world.entity.Entity pickupNpcEntity = resolveEntity(pickupNpcArg);
                task.waitPickupNpcId = pickupNpcArg instanceof String s ? s : null;
                task.waitPickupNpcUuid = pickupNpcEntity != null ? pickupNpcEntity.getUUID() : null;
                Object amountVal = evaluateExpression(call.getArgs().get(1));
                task.waitPickupMaxCount = amountVal instanceof Number n ? n.intValue() : 0;
                task.waitPickupItemId = String.valueOf(evaluateExpression(call.getArgs().get(2)));
                task.waitPickupTag = call.getArgs().size() >= 4 ? String.valueOf(evaluateExpression(call.getArgs().get(3))) : null;
                if ("null".equals(task.waitPickupTag) || (task.waitPickupTag != null && task.waitPickupTag.isEmpty())) task.waitPickupTag = null;
                task.waitPickupBaseCount = 0;
                if (source.getLevel() != null) {
                    net.minecraft.world.entity.Entity e = pickupNpcEntity;
                    if (e == null && task.waitPickupNpcId != null) {
                        e = org.zonarstudio.spraute_engine.entity.NpcManager.getEntity(task.waitPickupNpcId, source.getLevel());
                    }
                    if (e instanceof net.minecraft.world.entity.Mob mob) {
                        task.waitPickupBaseCount = countMatchingItems(mob, task.waitPickupItemId, task.waitPickupTag);
                        if (task.waitPickupMaxCount >= 0) {
                            setNpcPickupAwaitCap(e, task.waitPickupItemId, task.waitPickupTag,
                                    task.waitPickupBaseCount + task.waitPickupMaxCount, task);
                        }
                    }
                }
                task.waitType = WaitType.PICKUP;
                for (var he : eventHandlers.entrySet()) {
                    if (!he.getValue().active || !"pickup".equals(he.getValue().eventName) || he.getValue().eventArgs.size() < 2) continue;
                    net.minecraft.world.entity.Entity handlerNpc = resolveEntity(he.getValue().eventArgs.get(0));
                    if (handlerNpc != null && task.waitPickupNpcUuid != null
                            && handlerNpc.getUUID().equals(task.waitPickupNpcUuid)
                            && String.valueOf(he.getValue().eventArgs.get(1)).equals(task.waitPickupItemId)) {
                        pickupHandlerLastCount.put(he.getKey(), task.waitPickupBaseCount);
                    }
                }
                task.pendingUiClickVarName = varName;
                return true;
            } else if (fn.equals("orbPickup") || fn.equals("orb_pickup")) {
                if (call.getArgs().size() < 2) return false;
                Object playerArg = evaluateExpression(call.getArgs().get(0));
                PlayerFilter playerFilter = buildPlayerFilter(playerArg);
                if (playerFilter != null) {
                    task.waitOrbPickupPlayers = playerFilter;
                    task.waitOrbPickupTargetCount = ((Number) evaluateExpression(call.getArgs().get(1))).intValue();
                    task.waitOrbPickupTexture = call.getArgs().size() >= 3 && call.getArgs().get(2) != null
                            ? orbTextureFilter(evaluateExpression(call.getArgs().get(2))) : null;
                    task.waitOrbPickupCurrentCount = 0;
                    task.waitType = WaitType.ORB_PICKUP;
                    task.pendingUiClickVarName = varName;
                    return true;
                }
            } else if (fn.equals("projectileHit") || fn.equals("projectile_hit")) {
                if (call.getArgs().isEmpty()) return false;
                task.waitProjectileHitId = String.valueOf(evaluateExpression(call.getArgs().get(0)));
                task.waitProjectileHitType = call.getArgs().size() > 1 && call.getArgs().get(1) != null
                        ? String.valueOf(evaluateExpression(call.getArgs().get(1))) : null;
                task.waitType = WaitType.PROJECTILE_HIT;
                task.pendingUiClickVarName = varName;
                return true;
            } else if (fn.equals("tradeBuy") || fn.equals("trade_buy")
                    || fn.equals("tradeSell") || fn.equals("trade_sell")) {
                beginTaskCustomEventAwait(task, fn.contains("sell") || fn.contains("Sell") ? "tradeSell" : "tradeBuy",
                        call.getArgs(), varName);
                return true;
            } else if (fn.equals("relGift") || fn.equals("rel_gift")) {
                if (call.getArgs().isEmpty()) return false;
                Object playerArg = evaluateExpression(call.getArgs().get(0));
                PlayerFilter playerFilter = buildPlayerFilter(playerArg);
                if (playerFilter != null) {
                    String filter2 = call.getArgs().size() > 1 && call.getArgs().get(1) != null
                            ? String.valueOf(evaluateExpression(call.getArgs().get(1))) : null;
                    String filter3 = call.getArgs().size() > 2 && call.getArgs().get(2) != null
                            ? String.valueOf(evaluateExpression(call.getArgs().get(2))) : null;
                    String[] npcOut = new String[1];
                    String[] itemOut = new String[1];
                    applyRelGiftWaitFilters(filter2, filter3, npcOut, itemOut);
                    task.waitRelGiftPlayers = playerFilter;
                    task.waitRelGiftNpcId = npcOut[0];
                    task.waitRelGiftItemId = itemOut[0];
                    task.waitType = WaitType.REL_GIFT;
                    task.pendingUiClickVarName = varName;
                    return true;
                }
            } else if (fn.equals("relRepChange") || fn.equals("rel_rep_change")) {
                if (call.getArgs().isEmpty()) return false;
                Object playerArg = evaluateExpression(call.getArgs().get(0));
                PlayerFilter playerFilter = buildPlayerFilter(playerArg);
                if (playerFilter != null) {
                    task.waitRelSimplePlayers = playerFilter;
                    task.waitRelSimpleNpcId = call.getArgs().size() > 1 && call.getArgs().get(1) != null
                            ? String.valueOf(evaluateExpression(call.getArgs().get(1))) : null;
                    task.waitType = WaitType.REL_REP_CHANGE;
                    task.pendingUiClickVarName = varName;
                    return true;
                }
            } else if (fn.equals("relTalk") || fn.equals("rel_talk")) {
                if (call.getArgs().isEmpty()) return false;
                Object playerArg = evaluateExpression(call.getArgs().get(0));
                PlayerFilter playerFilter = buildPlayerFilter(playerArg);
                if (playerFilter != null) {
                    task.waitRelSimplePlayers = playerFilter;
                    task.waitRelSimpleNpcId = call.getArgs().size() > 1 && call.getArgs().get(1) != null
                            ? String.valueOf(evaluateExpression(call.getArgs().get(1))) : null;
                    task.waitType = WaitType.REL_TALK;
                    task.pendingUiClickVarName = varName;
                    return true;
                }
            } else if (fn.equals("relButton") || fn.equals("rel_button")) {
                if (call.getArgs().isEmpty()) return false;
                Object playerArg = evaluateExpression(call.getArgs().get(0));
                PlayerFilter playerFilter = buildPlayerFilter(playerArg);
                if (playerFilter != null) {
                    task.waitRelSimplePlayers = playerFilter;
                    task.waitRelSimpleBtnId = call.getArgs().size() > 1 && call.getArgs().get(1) != null
                            ? String.valueOf(evaluateExpression(call.getArgs().get(1))) : null;
                    task.waitRelSimpleNpcId = call.getArgs().size() > 2 && call.getArgs().get(2) != null
                            ? String.valueOf(evaluateExpression(call.getArgs().get(2))) : null;
                    task.waitType = WaitType.REL_BUTTON;
                    task.pendingUiClickVarName = varName;
                    return true;
                }
            } else if (fn.equals("uiClick") || fn.equals("uiclick")) {
                if (call.getArgs().isEmpty()) return false;
                Object playerArg = evaluateExpression(call.getArgs().get(0));
                PlayerFilter playerFilter = buildPlayerFilter(playerArg);
                if (playerFilter != null) {
                    task.waitUiPlayers = playerFilter;
                    task.waitType = WaitType.UI_CLICK;
                    task.pendingUiClickVarName = varName;
                    return true;
                }
            } else if (fn.equals("uiDrop") || fn.equals("uidrop")) {
                if (call.getArgs().isEmpty()) return false;
                Object playerArg = evaluateExpression(call.getArgs().get(0));
                PlayerFilter playerFilter = buildPlayerFilter(playerArg);
                if (playerFilter != null) {
                    task.waitUiPlayers = playerFilter;
                    task.waitType = WaitType.UI_DROP;
                    task.pendingUiClickVarName = varName;
                    return true;
                }
            } else if (fn.equals("uiClose") || fn.equals("uiclose")) {
                if (call.getArgs().isEmpty()) return false;
                Object playerArg = evaluateExpression(call.getArgs().get(0));
                PlayerFilter playerFilter = buildPlayerFilter(playerArg);
                if (playerFilter != null) {
                    task.waitUiPlayers = playerFilter;
                    task.waitType = WaitType.UI_CLOSE;
                    task.pendingUiClickVarName = varName;
                    return true;
                }
            } else if (fn.equals("uiInput")) {
                if (call.getArgs().isEmpty()) return false;
                Object playerArg = evaluateExpression(call.getArgs().get(0));
                PlayerFilter playerFilter = buildPlayerFilter(playerArg);
                if (playerFilter != null) {
                    task.waitUiPlayers = playerFilter;
                    task.uiInputWidgetId = call.getArgs().size() > 1 ? String.valueOf(evaluateExpression(call.getArgs().get(1))) : null;
                    task.waitType = WaitType.UI_INPUT;
                    task.pendingUiClickVarName = varName;
                    return true;
                }
            } else if (fn.equals("position")) {
                if (call.getArgs().size() < 4) return false;
                Object playerArg = evaluateExpression(call.getArgs().get(0));
                PlayerFilter playerFilter = buildPlayerFilter(playerArg);
                if (playerFilter != null) {
                    task.waitPositionPlayers = playerFilter;
                    task.waitPositionX = ((Number) evaluateExpression(call.getArgs().get(1))).doubleValue();
                    task.waitPositionY = ((Number) evaluateExpression(call.getArgs().get(2))).doubleValue();
                    task.waitPositionZ = ((Number) evaluateExpression(call.getArgs().get(3))).doubleValue();
                    task.waitPositionRadius = call.getArgs().size() > 4 ? ((Number) evaluateExpression(call.getArgs().get(4))).doubleValue() : 1.5;
                    task.waitType = WaitType.POSITION;
                    task.pendingUiClickVarName = varName;
                    return true;
                }
            } else if (fn.equals("inventory") || fn.equals("hasItem")) {
                if (call.getArgs().size() < 2) return false;
                Object playerArg = evaluateExpression(call.getArgs().get(0));
                PlayerFilter playerFilter = buildPlayerFilter(playerArg);
                if (playerFilter != null) {
                    task.waitInventoryPlayers = playerFilter;
                    task.waitInventoryItemId = String.valueOf(evaluateExpression(call.getArgs().get(1)));
                    task.waitInventoryCount = call.getArgs().size() > 2 ? ((Number) evaluateExpression(call.getArgs().get(2))).intValue() : 0;
                    task.waitType = WaitType.INVENTORY;
                    task.pendingUiClickVarName = varName;
                    return true;
                }
            } else if (fn.equals("clickBlock") || fn.equals("breakBlock") || fn.equals("placeBlock")
                    || fn.equals("openChest") || fn.equals("openDoor") || fn.equals("useBlock")) {
                if (call.getArgs().isEmpty()) return false;
                Object playerArg = evaluateExpression(call.getArgs().get(0));
                PlayerFilter playerFilter = buildPlayerFilter(playerArg);
                if (playerFilter != null) {
                    task.waitBlockPlayers = playerFilter;
                    task.waitBlockId = null;
                    task.waitBlockPos = null;
                    if (call.getArgs().size() == 2) {
                        task.waitBlockId = String.valueOf(evaluateExpression(call.getArgs().get(1)));
                    } else if (call.getArgs().size() >= 4) {
                        task.waitBlockPos = new net.minecraft.core.BlockPos(
                                ((Number) evaluateExpression(call.getArgs().get(1))).intValue(),
                                ((Number) evaluateExpression(call.getArgs().get(2))).intValue(),
                                ((Number) evaluateExpression(call.getArgs().get(3))).intValue());
                        if (call.getArgs().size() >= 5) task.waitBlockId = String.valueOf(evaluateExpression(call.getArgs().get(4)));
                    }
                    if (fn.equals("clickBlock")) task.waitType = WaitType.CLICK_BLOCK;
                    else if (fn.equals("breakBlock")) task.waitType = WaitType.BREAK_BLOCK;
                    else if (fn.equals("placeBlock")) task.waitType = WaitType.PLACE_BLOCK;
                    else if (fn.equals("openChest")) task.waitType = WaitType.OPEN_CHEST;
                    else if (fn.equals("openDoor")) task.waitType = WaitType.OPEN_DOOR;
                    else task.waitType = WaitType.USE_BLOCK;
                    task.blockEventMet = false;
                    task.pendingUiClickVarName = varName;
                    return true;
                }
            } else if (fn.equals("jump")) {
                if (call.getArgs().isEmpty()) return false;
                Object playerArg = evaluateExpression(call.getArgs().get(0));
                PlayerFilter playerFilter = buildPlayerFilter(playerArg);
                if (playerFilter != null) {
                    task.waitPlayerActionPlayers = playerFilter;
                    task.waitPlayerActionType = "jump";
                    task.waitPlayerActionTarget = null;
                    task.playerActionMet = false;
                    task.waitType = WaitType.PLAYER_ACTION;
                    task.pendingUiClickVarName = varName;
                    return true;
                }
            } else if (fn.equals("action") || fn.equals("playerAction")) {
                if (call.getArgs().size() < 2) return false;
                Object playerArg = evaluateExpression(call.getArgs().get(0));
                PlayerFilter playerFilter = buildPlayerFilter(playerArg);
                if (playerFilter != null) {
                    task.waitPlayerActionPlayers = playerFilter;
                    task.waitPlayerActionType = String.valueOf(evaluateExpression(call.getArgs().get(1)));
                    task.waitPlayerActionTarget = call.getArgs().size() > 2 && call.getArgs().get(2) != null
                            ? String.valueOf(evaluateExpression(call.getArgs().get(2))) : null;
                    task.playerActionMet = false;
                    task.waitType = WaitType.PLAYER_ACTION;
                    task.pendingUiClickVarName = varName;
                    return true;
                }
            } else if (ScriptCustomEvents.canAwaitAsCustom(fn)) {
                // Custom / script-registered events: await tradeBuy(...), await myEvent(...), …
                return beginTaskCustomEventAwait(task, fn, call.getArgs(), varName);
            }
            return false;
        }

        private boolean executeTaskInstruction(AsyncTask task) {
            CompiledScript.Instruction instr = task.instructions.get(task.ip);
            switch (instr.getOpcode()) {
                case JUMP -> {
                    int target = (Integer) instr.getArg(0);
                    task.ip = target - 1;
                }
                case JUMP_IF_FALSE -> {
                    ScriptNode cond = (ScriptNode) instr.getArg(0);
                    int target = (Integer) instr.getArg(1);
                    if (!isTruthy(evaluateExpression(cond))) task.ip = target - 1;
                }
                case VAR_ASSIGN -> {
                    String name = (String) instr.getArg(0);
                    ScriptNode valueNode = (ScriptNode) instr.getArg(1);
                    if (valueNode instanceof ScriptNode.FunctionCallNode fcall) {
                        UserFunction uf = resolveFunction(fcall.getFunctionName());
                        if (uf != null) {
                            beginTaskUserFunction(task, uf, evaluateCallArgs(fcall.getArgs()), name, "local");
                            return false;
                        }
                    }
                    if (valueNode instanceof ScriptNode.AwaitNode awaitNode) {
                        if (awaitNode.getCall() != null && beginTaskAwaitFromCall(task, name, awaitNode.getCall())) return true;
                    }
                    putVariable(name, evaluateExpression(valueNode));
                }
                case VAR_DECL -> {
                    String name = (String) instr.getArg(0);
                    ScriptNode init = (ScriptNode) instr.getArg(1);
                    String scope = instr.getArgCount() >= 3 ? String.valueOf(instr.getArg(2)) : "local";
                    if (init instanceof ScriptNode.FunctionCallNode fcall) {
                        UserFunction uf = resolveFunction(fcall.getFunctionName());
                        if (uf != null) {
                            beginTaskUserFunction(task, uf, evaluateCallArgs(fcall.getArgs()), name, scope);
                            return false;
                        }
                    }
                    if (init instanceof ScriptNode.AwaitNode awaitNode) {
                        if (awaitNode.getCall() != null && beginTaskAwaitFromCall(task, name, awaitNode.getCall())) {
                            if ("time".equals(awaitNode.getCall().getFunctionName())) {
                                putVariable(name, 0, scope);
                            }
                            return true;
                        }
                    }
                    Object val = evaluateExpression(init);
                    putVariable(name, val, scope);
                }
                case CALL -> {
                    String functionName = (String) instr.getArg(0);
                    @SuppressWarnings("unchecked")
                    List<ScriptNode> argsNodes = (List<ScriptNode>) instr.getArg(1);
                    UserFunction uf = resolveFunction(functionName);
                    if (uf != null) {
                        if (findDonorForFunction(functionName) != null) {
                            callUserFunction(functionName, evaluateCallArgs(argsNodes));
                        } else {
                            beginTaskUserFunction(task, uf, evaluateCallArgs(argsNodes), null, "local");
                        }
                        return false;
                    }
                    executeCall(instr);
                }
                case CALL_METHOD -> {
                    if (executeCallMethod(instr, false, task)) return true;
                }
                case NPC_BLOCK -> executeNpcBlock(instr);
                case MODEL_BLOCK -> executeModelBlock(instr);
                case HITBOX_BLOCK -> executeHitboxBlock(instr);
                case EMPTY_BLOCK -> executeEmptyBlock(instr);
                case SKIN_OVERLAY_BLOCK -> executeSkinOverlayBlock(instr);
                case PARTICLE_EMITTER_BLOCK -> executeParticleEmitterBlock(instr);
                case PARTICLE_DEF_BLOCK -> executeParticleDefBlock(instr);
                case PROJECTILE_DEF_BLOCK -> executeProjectileDefBlock(instr);
                case LIGHT_BLOCK -> executeLightBlock(instr);
                case AUDIO_SOURCE_BLOCK -> executeAudioSourceBlock(instr);
                case POST_PROCESS_BLOCK -> executePostProcessBlock(instr);
                case CUSTOM_NODE_BLOCK -> executeCustomNodeBlock(instr);
                case NPC_PREFAB_DEF -> executeNpcPrefabDef(instr);
                case UI_BLOCK -> executeUiBlock(instr);
                case COMMAND_BLOCK -> executeCommandBlock(instr);
                case FADE_IN -> executeFadeIn(instr);
                case CAMERA -> executeCamera(instr);
                case UI_WIDGET -> executeUiWidget(instr);
                case SET_PROPERTY -> executeSetProperty(instr);
                case AWAIT_TIME -> {
                    Object sec = evaluateExpression((ScriptNode) instr.getArg(0));
                    task.waitTimer = coerceSeconds(sec, task.id);
                    task.waitType = WaitType.TIME;
                    return true;
                }
                case AWAIT_CAMERA_ROUTE -> {
                    Double sec = awaitCameraRouteDuration(instr);
                    if (sec != null) {
                        task.waitTimer = sec;
                        task.waitType = WaitType.TIME;
                        return true;
                    }
                }
                case AWAIT_UI_CLICK -> {
                    ScriptNode pNode = (ScriptNode) instr.getArg(0);
                    Object playerArg = evaluateExpression(pNode);
                    PlayerFilter playerFilter = buildPlayerFilter(playerArg);
                    if (playerFilter != null) {
                        task.waitUiPlayers = playerFilter;
                        task.waitType = WaitType.UI_CLICK;
                        return true;
                    }
                    LOGGER.warn("[Script: {}] await uiClick (async): unknown player", script.getName());
                }
                case AWAIT_UI_DROP -> {
                    ScriptNode pNode = (ScriptNode) instr.getArg(0);
                    Object playerArg = evaluateExpression(pNode);
                    PlayerFilter playerFilter = buildPlayerFilter(playerArg);
                    if (playerFilter != null) {
                        task.waitUiPlayers = playerFilter;
                        task.waitType = WaitType.UI_DROP;
                        return true;
                    }
                    LOGGER.warn("[Script: {}] await uiDrop (async): unknown player", script.getName());
                }
                case AWAIT_UI_CLOSE -> {
                    ScriptNode pNode = (ScriptNode) instr.getArg(0);
                    Object playerArg = evaluateExpression(pNode);
                    PlayerFilter playerFilter = buildPlayerFilter(playerArg);
                    if (playerFilter != null) {
                        task.waitUiPlayers = playerFilter;
                        task.waitType = WaitType.UI_CLOSE;
                        return true;
                    }
                }
                case AWAIT_UI_INPUT -> {
                    ScriptNode pNode = (ScriptNode) instr.getArg(0);
                    ScriptNode wNode = (ScriptNode) instr.getArg(1);
                    Object playerArg = evaluateExpression(pNode);
                    PlayerFilter playerFilter = buildPlayerFilter(playerArg);
                    if (playerFilter != null) {
                        task.waitUiPlayers = playerFilter;
                        task.uiInputWidgetId = wNode != null ? String.valueOf(evaluateExpression(wNode)) : null;
                        task.waitType = WaitType.UI_INPUT;
                        return true;
                    }
                }
                case AWAIT_TRADE_BUY -> {
                    @SuppressWarnings("unchecked")
                    java.util.List<ScriptNode> filters = new java.util.ArrayList<>();
                    filters.add((ScriptNode) instr.getArg(0));
                    if (instr.getArgCount() >= 2 && instr.getArg(1) != null) filters.add((ScriptNode) instr.getArg(1));
                    beginTaskCustomEventAwait(task, "tradeBuy", filters, null);
                    return true;
                }
                case AWAIT_TRADE_SELL -> {
                    java.util.List<ScriptNode> filters = new java.util.ArrayList<>();
                    filters.add((ScriptNode) instr.getArg(0));
                    if (instr.getArgCount() >= 2 && instr.getArg(1) != null) filters.add((ScriptNode) instr.getArg(1));
                    beginTaskCustomEventAwait(task, "tradeSell", filters, null);
                    return true;
                }
                case AWAIT_CUSTOM -> {
                    String evName = String.valueOf(instr.getArg(0));
                    @SuppressWarnings("unchecked")
                    java.util.List<ScriptNode> filters = instr.getArgCount() >= 2 && instr.getArg(1) instanceof java.util.List
                            ? (java.util.List<ScriptNode>) instr.getArg(1) : java.util.List.of();
                    beginTaskCustomEventAwait(task, evName, filters, null);
                    return task.waitType == WaitType.CUSTOM_EVENT;
                }
                case AWAIT_REL_GIFT -> {
                    ScriptNode pNode = (ScriptNode) instr.getArg(0);
                    ScriptNode filter2Node = instr.getArgCount() >= 2 && instr.getArg(1) != null ? (ScriptNode) instr.getArg(1) : null;
                    ScriptNode filter3Node = instr.getArgCount() >= 3 && instr.getArg(2) != null ? (ScriptNode) instr.getArg(2) : null;
                    Object playerArg = evaluateExpression(pNode);
                    PlayerFilter playerFilter = buildPlayerFilter(playerArg);
                    if (playerFilter != null) {
                        String filter2 = filter2Node != null ? String.valueOf(evaluateExpression(filter2Node)) : null;
                        String filter3 = filter3Node != null ? String.valueOf(evaluateExpression(filter3Node)) : null;
                        String[] npcOut = new String[1];
                        String[] itemOut = new String[1];
                        applyRelGiftWaitFilters(filter2, filter3, npcOut, itemOut);
                        task.waitRelGiftPlayers = playerFilter;
                        task.waitRelGiftNpcId = npcOut[0];
                        task.waitRelGiftItemId = itemOut[0];
                        task.waitType = WaitType.REL_GIFT;
                        return true;
                    }
                }
                case AWAIT_REL_REP_CHANGE, AWAIT_REL_TALK -> {
                    ScriptNode pNode = (ScriptNode) instr.getArg(0);
                    ScriptNode npcNode = instr.getArgCount() >= 2 && instr.getArg(1) != null ? (ScriptNode) instr.getArg(1) : null;
                    Object playerArg = evaluateExpression(pNode);
                    PlayerFilter playerFilter = buildPlayerFilter(playerArg);
                    if (playerFilter != null) {
                        task.waitRelSimplePlayers = playerFilter;
                        task.waitRelSimpleNpcId = npcNode != null ? String.valueOf(evaluateExpression(npcNode)) : null;
                        task.waitType = instr.getOpcode() == CompiledScript.Opcode.AWAIT_REL_REP_CHANGE
                                ? WaitType.REL_REP_CHANGE : WaitType.REL_TALK;
                        return true;
                    }
                }
                case AWAIT_REL_BUTTON -> {
                    ScriptNode pNode = (ScriptNode) instr.getArg(0);
                    ScriptNode btnNode = instr.getArgCount() >= 2 && instr.getArg(1) != null ? (ScriptNode) instr.getArg(1) : null;
                    ScriptNode npcNode = instr.getArgCount() >= 3 && instr.getArg(2) != null ? (ScriptNode) instr.getArg(2) : null;
                    Object playerArg = evaluateExpression(pNode);
                    PlayerFilter playerFilter = buildPlayerFilter(playerArg);
                    if (playerFilter != null) {
                        task.waitRelSimplePlayers = playerFilter;
                        task.waitRelSimpleBtnId = btnNode != null ? String.valueOf(evaluateExpression(btnNode)) : null;
                        task.waitRelSimpleNpcId = npcNode != null ? String.valueOf(evaluateExpression(npcNode)) : null;
                        task.waitType = WaitType.REL_BUTTON;
                        return true;
                    }
                }
                case AWAIT_POSITION -> {
                    ScriptNode pNode = (ScriptNode) instr.getArg(0);
                    Object playerArg = evaluateExpression(pNode);
                    PlayerFilter playerFilter = buildPlayerFilter(playerArg);
                    if (playerFilter != null) {
                        task.waitPositionPlayers = playerFilter;
                        task.waitPositionX = ((Number) evaluateExpression((ScriptNode) instr.getArg(1))).doubleValue();
                        task.waitPositionY = ((Number) evaluateExpression((ScriptNode) instr.getArg(2))).doubleValue();
                        task.waitPositionZ = ((Number) evaluateExpression((ScriptNode) instr.getArg(3))).doubleValue();
                        task.waitPositionRadius = instr.getArg(4) != null ? ((Number) evaluateExpression((ScriptNode) instr.getArg(4))).doubleValue() : 1.5;
                        task.waitType = WaitType.POSITION;
                        return true;
                    }
                }
                case AWAIT_INVENTORY -> {
                    ScriptNode pNode = (ScriptNode) instr.getArg(0);
                    Object playerArg = evaluateExpression(pNode);
                    PlayerFilter playerFilter = buildPlayerFilter(playerArg);
                    if (playerFilter != null) {
                        task.waitInventoryPlayers = playerFilter;
                        task.waitInventoryItemId = String.valueOf(evaluateExpression((ScriptNode) instr.getArg(1)));
                        task.waitInventoryCount = instr.getArg(2) != null ? ((Number) evaluateExpression((ScriptNode) instr.getArg(2))).intValue() : 0;
                        task.waitType = WaitType.INVENTORY;
                        return true;
                    }
                }
                case AWAIT_CLICK_BLOCK, AWAIT_BREAK_BLOCK, AWAIT_PLACE_BLOCK, AWAIT_OPEN_CHEST, AWAIT_OPEN_DOOR, AWAIT_USE_BLOCK -> {
                    List<ScriptNode> args = (List<ScriptNode>) instr.getArg(0);
                    Object playerArg = evaluateExpression(args.get(0));
                    PlayerFilter playerFilter = buildPlayerFilter(playerArg);
                    if (playerFilter != null) {
                        task.waitBlockPlayers = playerFilter;
                        task.waitBlockId = null;
                        task.waitBlockPos = null;
                        if (args.size() == 2) {
                            task.waitBlockId = String.valueOf(evaluateExpression(args.get(1)));
                        } else if (args.size() >= 4) {
                            int x = ((Number) evaluateExpression(args.get(1))).intValue();
                            int y = ((Number) evaluateExpression(args.get(2))).intValue();
                            int z = ((Number) evaluateExpression(args.get(3))).intValue();
                            task.waitBlockPos = new net.minecraft.core.BlockPos(x, y, z);
                            if (args.size() >= 5) {
                                task.waitBlockId = String.valueOf(evaluateExpression(args.get(4)));
                            }
                        }
                        if (instr.getOpcode() == CompiledScript.Opcode.AWAIT_CLICK_BLOCK) task.waitType = WaitType.CLICK_BLOCK;
                        else if (instr.getOpcode() == CompiledScript.Opcode.AWAIT_BREAK_BLOCK) task.waitType = WaitType.BREAK_BLOCK;
                        else if (instr.getOpcode() == CompiledScript.Opcode.AWAIT_PLACE_BLOCK) task.waitType = WaitType.PLACE_BLOCK;
                        else if (instr.getOpcode() == CompiledScript.Opcode.AWAIT_OPEN_CHEST) task.waitType = WaitType.OPEN_CHEST;
                        else if (instr.getOpcode() == CompiledScript.Opcode.AWAIT_OPEN_DOOR) task.waitType = WaitType.OPEN_DOOR;
                        else task.waitType = WaitType.USE_BLOCK;
                        task.blockEventMet = false;
                        return true;
                    }
                }
                case AWAIT_CHAT -> {
                    List<ScriptNode> args = (List<ScriptNode>) instr.getArg(0);
                    Object playerArg = evaluateExpression(args.get(0));
                    PlayerFilter playerFilter = buildPlayerFilter(playerArg);
                    if (playerFilter != null) {
                        task.waitChatPlayers = playerFilter;
                        
                        Object msgs = evaluateExpression(args.get(1));
                        task.waitChatMessages = new ArrayList<>();
                        if (msgs instanceof List list) {
                            for (Object o : list) task.waitChatMessages.add(String.valueOf(o));
                        } else {
                            task.waitChatMessages.add(String.valueOf(msgs));
                        }

                        if (args.size() > 2) task.waitChatIgnoreCase = (Boolean) evaluateExpression(args.get(2));
                        if (args.size() > 3) task.waitChatIgnorePunct = (Boolean) evaluateExpression(args.get(3));
                        
                        task.chatEventMet = false;
                        task.chatMatchedMessage = "";
                        task.waitType = WaitType.CHAT;
                        return true;
                    }
                }
                case AWAIT_PLAYER_ACTION -> {
                    ScriptNode pNode = (ScriptNode) instr.getArg(0);
                    ScriptNode actionNode = (ScriptNode) instr.getArg(1);
                    ScriptNode targetNode = instr.getArgCount() >= 3 ? (ScriptNode) instr.getArg(2) : null;
                    Object playerArg = evaluateExpression(pNode);
                    PlayerFilter playerFilter = buildPlayerFilter(playerArg);
                    if (playerFilter != null) {
                        task.waitPlayerActionPlayers = playerFilter;
                        task.waitPlayerActionType = String.valueOf(evaluateExpression(actionNode));
                        task.waitPlayerActionTarget = targetNode != null ? String.valueOf(evaluateExpression(targetNode)) : null;
                        task.playerActionMet = false;
                        task.waitType = WaitType.PLAYER_ACTION;
                        return true;
                    }
                }
                case AWAIT_DIMENSION -> {
                    ScriptNode pNode = (ScriptNode) instr.getArg(0);
                    ScriptNode dimNode = (ScriptNode) instr.getArg(1);
                    Object playerArg = evaluateExpression(pNode);
                    PlayerFilter playerFilter = buildPlayerFilter(playerArg);
                    if (playerFilter != null) {
                        task.waitDimensionPlayers = playerFilter;
                        task.waitDimensionId = String.valueOf(evaluateExpression(dimNode));
                        task.dimensionEventMet = false;
                        task.waitType = WaitType.DIMENSION;
                        return true;
                    }
                }
                case AWAIT_JOIN -> {
                    ScriptNode pNode = (ScriptNode) instr.getArg(0);
                    boolean firstOnly = Boolean.TRUE.equals(instr.getArg(1));
                    Object playerArg = evaluateExpression(pNode);
                    PlayerFilter playerFilter = buildPlayerFilter(playerArg);
                    if (playerFilter != null) {
                        task.waitJoinPlayers = playerFilter;
                        task.waitFirstJoinOnly = firstOnly;
                        task.joinEventMet = false;
                        task.waitJoinPlayerUuid = null;
                        task.waitType = WaitType.JOIN;
                        return true;
                    }
                }
                case AWAIT_LEAVE -> {
                    Object playerArg = evaluateExpression((ScriptNode) instr.getArg(0));
                    PlayerFilter playerFilter = buildPlayerFilter(playerArg);
                    if (playerFilter != null) {
                        task.waitLeavePlayers = playerFilter;
                        task.leaveEventMet = false;
                        task.waitLeavePlayer = null;
                        task.waitType = WaitType.LEAVE;
                        return true;
                    }
                }
                case AWAIT_NEXT -> {
                    task.waitType = WaitType.NEXT;
                    return true;
                }
                case AWAIT_INTERACT -> {
                    Object val = evaluateExpression((ScriptNode) instr.getArg(0));
                    net.minecraft.world.entity.Entity targetEntity = resolveEntity(val);
                    if (targetEntity != null) {
                        task.waitEntityUuid = targetEntity.getUUID();
                        task.interactionMet = false;
                        task.waitType = WaitType.INTERACT;
                        return true;
                    }
                    LOGGER.warn("[Script: {}] await interact (async): unknown target '{}'", script.getName(), String.valueOf(val));
                }
                case AWAIT_KEYBIND -> {
                    task.waitKeybindKey = String.valueOf(evaluateExpression((ScriptNode) instr.getArg(0)));
                    task.keybindMet = false;
                    task.waitType = WaitType.KEYBIND;
                    return true;
                }
                case AWAIT_DEATH -> {
                    task.waitDeathTarget = normalizeEntityWaitTarget(evaluateExpression((ScriptNode) instr.getArg(0)));
                    task.deathMet = false;
                    task.waitType = WaitType.DEATH;
                    return true;
                }
                case AWAIT_KILL -> {
                    task.waitKillKillerTarget = normalizeEntityWaitTarget(evaluateExpression((ScriptNode) instr.getArg(0)));
                    ScriptNode victimNode = instr.getArgCount() >= 2 ? (ScriptNode) instr.getArg(1) : null;
                    task.waitKillVictimTarget = victimNode != null ? normalizeEntityWaitTarget(evaluateExpression(victimNode)) : "any";
                    task.killMet = false;
                    task.waitType = WaitType.KILL;
                    return true;
                }
                case AWAIT_PICKUP -> {
                    Object pickupNpcArg = evaluateExpression((ScriptNode) instr.getArg(0));
                    net.minecraft.world.entity.Entity pickupNpcEntity = resolveEntity(pickupNpcArg);
                    task.waitPickupNpcId = pickupNpcArg instanceof String s ? s : null;
                    task.waitPickupNpcUuid = pickupNpcEntity != null ? pickupNpcEntity.getUUID() : null;
                    Object amountVal = evaluateExpression((ScriptNode) instr.getArg(1));
                    task.waitPickupMaxCount = amountVal instanceof Number n ? n.intValue() : 0;
                    task.waitPickupItemId = String.valueOf(evaluateExpression((ScriptNode) instr.getArg(2)));
                    ScriptNode nbtNode = instr.getArgCount() >= 4 ? (ScriptNode) instr.getArg(3) : null;
                    task.waitPickupTag = nbtNode != null ? String.valueOf(evaluateExpression(nbtNode)) : null;
                    if ("null".equals(task.waitPickupTag) || (task.waitPickupTag != null && task.waitPickupTag.isEmpty())) task.waitPickupTag = null;
                    task.waitPickupBaseCount = 0;
                    if (source.getLevel() != null) {
                        net.minecraft.world.entity.Entity e = pickupNpcEntity;
                        if (e == null && task.waitPickupNpcId != null) {
                            e = org.zonarstudio.spraute_engine.entity.NpcManager.getEntity(task.waitPickupNpcId, source.getLevel());
                        }
                        if (e instanceof net.minecraft.world.entity.Mob mob) {
                            task.waitPickupBaseCount = countMatchingItems(mob, task.waitPickupItemId, task.waitPickupTag);
                            if (task.waitPickupMaxCount >= 0) {
                                setNpcPickupAwaitCap(e, task.waitPickupItemId, task.waitPickupTag,
                                        task.waitPickupBaseCount + task.waitPickupMaxCount, task);
                            }
                        }
                    }
                    task.waitType = WaitType.PICKUP;
                    return true;
                }
                case AWAIT_ORB_PICKUP -> {
                    Object playerArg = evaluateExpression((ScriptNode) instr.getArg(0));
                    PlayerFilter playerFilter = buildPlayerFilter(playerArg);
                    if (playerFilter != null) {
                        task.waitOrbPickupPlayers = playerFilter;
                        task.waitOrbPickupTargetCount = ((Number) evaluateExpression((ScriptNode) instr.getArg(1))).intValue();
                        ScriptNode texNode = instr.getArgCount() >= 3 && instr.getArg(2) != null ? (ScriptNode) instr.getArg(2) : null;
                        task.waitOrbPickupTexture = texNode != null ? orbTextureFilter(evaluateExpression(texNode)) : null;
                        task.waitOrbPickupCurrentCount = 0;
                        task.waitType = WaitType.ORB_PICKUP;
                        return true;
                    }
                }
                case AWAIT_PROJECTILE_HIT -> {
                    Object idArg = evaluateExpression((ScriptNode) instr.getArg(0));
                    ScriptNode typeNode = instr.getArgCount() >= 2 && instr.getArg(1) != null ? (ScriptNode) instr.getArg(1) : null;
                    task.waitProjectileHitId = String.valueOf(idArg);
                    task.waitProjectileHitType = typeNode != null ? String.valueOf(evaluateExpression(typeNode)) : null;
                    task.waitType = WaitType.PROJECTILE_HIT;
                    return true;
                }
                case AWAIT_TASK -> {
                    task.waitTaskId = String.valueOf(evaluateExpression((ScriptNode) instr.getArg(0)));
                    task.waitType = WaitType.WAIT_TASK;
                    return true;
                }
                case AWAIT_UI_TOUCH -> {
                    Object playerArg = evaluateExpression((ScriptNode) instr.getArg(0));
                    PlayerFilter playerFilter = buildPlayerFilter(playerArg);
                    if (playerFilter != null) {
                        task.waitUiOverlapPlayers = playerFilter;
                        net.minecraft.server.level.ServerPlayer sp = findOnlinePlayerForFilter(playerFilter);
                        if (sp != null) {
                            task.waitUiOverlapId1 = String.valueOf(evaluateExpression((ScriptNode) instr.getArg(1)));
                            task.waitUiOverlapId2 = String.valueOf(evaluateExpression((ScriptNode) instr.getArg(2)));
                            task.uiOverlapMet = false;
                            org.zonarstudio.spraute_engine.network.ModNetwork.CHANNEL.send(
                                    net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> sp),
                                    new org.zonarstudio.spraute_engine.network.SprauteUiMonitorOverlapPacket(task.waitUiOverlapId1, task.waitUiOverlapId2, true));
                            task.waitType = WaitType.UI_OVERLAP;
                            return true;
                        }
                    }
                }
                case AWAIT_HITBOX_TOUCH, AWAIT_HITBOX_EXIT -> {
                    Object playerArg = evaluateExpression((ScriptNode) instr.getArg(0));
                    PlayerFilter playerFilter = buildPlayerFilter(playerArg);
                    if (playerFilter != null) {
                        task.waitHitboxPlayers = playerFilter;
                        ScriptNode sceneNode = instr.getArgCount() > 1 ? (ScriptNode) instr.getArg(1) : null;
                        ScriptNode triggerNode = instr.getArgCount() > 2 ? (ScriptNode) instr.getArg(2) : null;
                        task.waitHitboxScene = sceneNode != null ? String.valueOf(evaluateExpression(sceneNode)) : null;
                        task.waitHitboxTriggerId = triggerNode != null ? String.valueOf(evaluateExpression(triggerNode)) : null;
                        if (triggerNode == null && sceneNode != null) {
                            String only = task.waitHitboxScene;
                            if (only != null && !only.isBlank() && !"*".equals(only.trim())
                                    && org.zonarstudio.spraute_engine.scene.SceneRuntime.getActiveState(only.trim()) == null) {
                                task.waitHitboxTriggerId = only;
                                task.waitHitboxScene = null;
                            }
                        }
                        task.hitboxEventMet = false;
                        task.waitType = instr.getOpcode() == CompiledScript.Opcode.AWAIT_HITBOX_EXIT
                                ? WaitType.HITBOX_EXIT : WaitType.HITBOX_TOUCH;
                        return true;
                    }
                }
                case RETURN -> {
                    ScriptNode valueNode = instr.getArgCount() > 0 ? (ScriptNode) instr.getArg(0) : null;
                    Object result = valueNode != null ? evaluateExpression(valueNode) : null;
                    if (task.leaveUserFunction(result, variables)) {
                        return false;
                    }
                    throw new ReturnException(result);
                }
                default -> executeStatementInstruction(instr);
            }
            return false;
        }

        public void onInteract(net.minecraft.world.entity.Entity target, net.minecraft.world.entity.Entity interactor) {
            // Main script await
            if (waitType == WaitType.INTERACT && waitEntityUuid != null) {
                if (target.getUUID().equals(waitEntityUuid)) {
                    interactionMet = true;
                    asyncResult = interactor;
                }
            }

            for (AsyncTask task : asyncTasks.values()) {
                if (task.waitType == WaitType.INTERACT && task.waitEntityUuid != null
                        && target.getUUID().equals(task.waitEntityUuid)) {
                    task.interactionMet = true;
                    task.waitResult = interactor;
                }
            }

            // Fire "interact" event handlers
            for (var entry : snapshotEventHandlers()) {
                EventHandler handler = entry.getValue();
                if (!handler.active || !handler.eventName.equals("interact")) continue;

                if (!handler.eventArgs.isEmpty()) {
                    if (!matchesInteractTarget(handler.eventArgs.get(0), target)) continue;
                }

                Object prevPlayer = variables.get("_eventPlayer");
                Object prevNpc = variables.get("_eventNpc");
                Object prevEntity = variables.get("_eventEntity");
                Object prevEntityType = variables.get("_eventEntityType");
                Object prevEntityX = variables.get("_eventEntityX");
                Object prevEntityY = variables.get("_eventEntityY");
                Object prevEntityZ = variables.get("_eventEntityZ");
                variables.put("_eventPlayer", interactor);
                variables.put("_eventNpc", target);
                putEventEntityVars(target);
                try {
                    // wait/await inside on-handlers run as async (sync block ignores AWAIT_*)
                    runEventHandlerBody(entry.getKey(), handler);
                } catch (ReturnException e) {
                    // return exits handler body only; use stop handler to deactivate
                } catch (Exception e) {
                    LOGGER.error("[Script: {}] Event handler '{}' error: {}", script.getName(), entry.getKey(), e.getMessage());
                }
                if (prevPlayer != null) variables.put("_eventPlayer", prevPlayer);
                else variables.remove("_eventPlayer");
                if (prevNpc != null) variables.put("_eventNpc", prevNpc);
                else variables.remove("_eventNpc");
                restoreEventEntityVars(prevEntity, prevEntityType, prevEntityX, prevEntityY, prevEntityZ);
            }
        }

        private static String handlerTaskId(String handlerId) {
            return "on:" + handlerId;
        }

        /**
         * Event/timer bodies run via {@link #executeInstructionBlock}, which does not pause on
         * {@code await}/{@code wait}. If the body contains awaits, start one AsyncTask instead
         * (and skip re-entry while that task is still running). Event vars are snapshotted into
         * {@link AsyncTask#taskLocals} at start.
         */
        private void runEventHandlerBody(String handlerId, EventHandler handler) {
            if (handler == null) return;
            runEventHandlerBody(handlerId, handler.bodyInstructions);
        }

        private void runEventHandlerBody(String handlerId, List<CompiledScript.Instruction> body) {
            if (body == null) return;
            if (bodyHasAwait(body)) {
                String taskId = handlerTaskId(handlerId);
                AsyncTask existing = asyncTasks.get(taskId);
                if (existing != null && !existing.finished && !existing.cancelled) {
                    // Already in a dialogue / await chain — let the event satisfy awaits only.
                    return;
                }
                asyncTasks.put(taskId, new AsyncTask(taskId, body));
                return;
            }
            executeInstructionBlock(body);
        }

        private static boolean bodyHasAwait(List<CompiledScript.Instruction> instructions) {
            if (instructions == null) return false;
            for (CompiledScript.Instruction instr : instructions) {
                if (instr == null) continue;
                CompiledScript.Opcode op = instr.getOpcode();
                if (op == null) continue;
                String name = op.name();
                if (name.startsWith("AWAIT_")) return true;
                // Nested async { } still needs its outer waits if any; nested bodies are separate.
                for (int a = 0; a < instr.getArgCount(); a++) {
                    Object arg = instr.getArg(a);
                    if (arg instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof CompiledScript.Instruction) {
                        @SuppressWarnings("unchecked")
                        List<CompiledScript.Instruction> nested = (List<CompiledScript.Instruction>) list;
                        if (bodyHasAwait(nested)) return true;
                    }
                }
            }
            return false;
        }

        private boolean matchesInteractTarget(Object handlerArg, net.minecraft.world.entity.Entity clicked) {
            return matchesEntityRef(clicked, handlerArg);
        }

        private void putEventEntityVars(net.minecraft.world.entity.Entity entity) {
            if (entity != null) {
                variables.put("_eventEntity", entity);
                variables.put("_eventEntityType", EntityScriptUtil.getTypeId(entity));
                variables.put("_eventEntityX", entity.getX());
                variables.put("_eventEntityY", entity.getY());
                variables.put("_eventEntityZ", entity.getZ());
            } else {
                variables.remove("_eventEntity");
                variables.remove("_eventEntityType");
                variables.remove("_eventEntityX");
                variables.remove("_eventEntityY");
                variables.remove("_eventEntityZ");
            }
        }

        private void restoreEventEntityVars(Object prevEntity, Object prevEntityType,
                                           Object prevX, Object prevY, Object prevZ) {
            restoreEventVar("_eventEntity", prevEntity);
            restoreEventVar("_eventEntityType", prevEntityType);
            restoreEventVar("_eventEntityX", prevX);
            restoreEventVar("_eventEntityY", prevY);
            restoreEventVar("_eventEntityZ", prevZ);
        }

        private void putEventKillerVars(net.minecraft.world.entity.Entity killer) {
            if (killer != null) {
                variables.put("_eventKiller", killer);
                variables.put("_eventKillerType", EntityScriptUtil.getTypeId(killer));
                variables.put("_eventKillerX", killer.getX());
                variables.put("_eventKillerY", killer.getY());
                variables.put("_eventKillerZ", killer.getZ());
            } else {
                variables.remove("_eventKiller");
                variables.remove("_eventKillerType");
                variables.remove("_eventKillerX");
                variables.remove("_eventKillerY");
                variables.remove("_eventKillerZ");
            }
        }

        private void restoreEventKillerVars(Object prevKiller, Object prevKillerType,
                                           Object prevX, Object prevY, Object prevZ) {
            restoreEventVar("_eventKiller", prevKiller);
            restoreEventVar("_eventKillerType", prevKillerType);
            restoreEventVar("_eventKillerX", prevX);
            restoreEventVar("_eventKillerY", prevY);
            restoreEventVar("_eventKillerZ", prevZ);
        }

        private void restoreEventVar(String name, Object prev) {
            if (prev != null) variables.put(name, prev);
            else variables.remove(name);
        }

        private void putEventTargetVars(Object target) {
            if (target == null) {
                variables.remove("_eventTarget");
                variables.remove("_eventTargetType");
                return;
            }
            variables.put("_eventTarget", target);
            if (target instanceof net.minecraft.world.entity.Entity entity) {
                variables.put("_eventTargetType", EntityScriptUtil.getTypeId(entity));
            } else {
                variables.remove("_eventTargetType");
            }
        }

        private void restoreEventTargetVars(Object prevTarget, Object prevTargetType) {
            if (prevTarget != null) variables.put("_eventTarget", prevTarget);
            else variables.remove("_eventTarget");
            if (prevTargetType != null) variables.put("_eventTargetType", prevTargetType);
            else variables.remove("_eventTargetType");
        }

        /**
         * True when {@code candidate} is the same entity the script meant by {@code ref}.
         * Accepts Entity, SceneNodeView, UUID string, create-npc / scene node id or display name,
         * and keywords any/player/npc/mob.
         */
        private boolean matchesEntityRef(net.minecraft.world.entity.Entity candidate, Object ref) {
            if (candidate == null || ref == null) return false;
            if (ref instanceof String s) {
                String t = s.trim();
                if (t.isEmpty()) return false;
                if ("any".equalsIgnoreCase(t)) return true;
                if ("player".equalsIgnoreCase(t)) return candidate instanceof net.minecraft.world.entity.player.Player;
                if ("npc".equalsIgnoreCase(t)) {
                    return candidate instanceof org.zonarstudio.spraute_engine.entity.SprauteNpcEntity;
                }
                if ("mob".equalsIgnoreCase(t)) {
                    return candidate instanceof net.minecraft.world.entity.LivingEntity
                            && !(candidate instanceof net.minecraft.world.entity.player.Player)
                            && !(candidate instanceof org.zonarstudio.spraute_engine.entity.SprauteNpcEntity);
                }
                if (EntityScriptUtil.matchesTypeId(t, candidate)) return true;
                if (t.regionMatches(true, 0, "scenenode:", 0, 10)) {
                    String rest = t.substring(10);
                    int sep = rest.indexOf(':');
                    if (sep > 0) {
                        String nodeId = rest.substring(sep + 1);
                        return candidate.getTags().contains("spraute_scene_node_" + nodeId);
                    }
                }
                try {
                    if (candidate.getUUID().equals(java.util.UUID.fromString(t))) return true;
                } catch (IllegalArgumentException ignored) {}
            }
            if (ref instanceof net.minecraft.world.entity.Entity expectedEntity) {
                return candidate.getUUID().equals(expectedEntity.getUUID());
            }
            if (ref instanceof org.zonarstudio.spraute_engine.scene.SceneNodeView view) {
                net.minecraft.world.entity.Entity expected = view.getEntity(source);
                if (expected != null) return candidate.getUUID().equals(expected.getUUID());
                return candidate.getTags().contains("spraute_scene_node_" + view.getNodeId());
            }
            net.minecraft.world.entity.Entity resolved = resolveEntity(ref);
            return resolved != null && candidate.getUUID().equals(resolved.getUUID());
        }

        private static boolean isEntityFilterKeyword(String value) {
            if (value == null) return false;
            String t = value.trim();
            return "any".equalsIgnoreCase(t) || "player".equalsIgnoreCase(t)
                    || "npc".equalsIgnoreCase(t) || "mob".equalsIgnoreCase(t);
        }

        /**
         * Event filters like {@code player}/{@code any} must stay keywords. Evaluating them
         * to the Play-clicker's entity would make late joiners miss {@code on kill} etc.
         */
        private Object evaluateEventArg(String eventName, ScriptNode node) {
            if (node instanceof ScriptNode.IdentifierNode idNode && isEntityFilterKeyword(idNode.getName())) {
                return idNode.getName();
            }
            Object val = evaluateExpression(node);
            if (val instanceof String s && isEntityFilterKeyword(s)) return s;
            if ("interact".equals(eventName) && val == null && node instanceof ScriptNode.IdentifierNode id) {
                return id.getName();
            }
            return val;
        }

        /**
         * Store a stable await/on target: keywords and ids stay as strings; Entity / SceneNodeView
         * become UUID (or scenenode:scene:id fallback) so matching works for the whole wait.
         */
        private Object normalizeEntityWaitTarget(Object val) {
            if (val == null) return null;
            if (val instanceof String s) {
                String t = s.trim();
                return t.isEmpty() ? null : t;
            }
            net.minecraft.world.entity.Entity e = resolveEntity(val);
            if (e != null) return e.getUUID().toString();
            if (val instanceof org.zonarstudio.spraute_engine.scene.SceneNodeView view) {
                return "scenenode:" + view.getSceneId() + ":" + view.getNodeId();
            }
            return String.valueOf(val);
        }

        private boolean matchesDeathTarget(net.minecraft.world.entity.LivingEntity entity, Object target) {
            return matchesEntityRef(entity, target);
        }

        private boolean matchesKillFilter(net.minecraft.world.entity.Entity entity, Object target) {
            return matchesEntityRef(entity, target);
        }

        public void onKeybind(String key, net.minecraft.world.entity.player.Player player) {
            if (waitType == WaitType.KEYBIND && waitKeybindKey != null && waitKeybindKey.equalsIgnoreCase(key)) {
                keybindMet = true;
                keybindPlayer = player;
            }

            for (AsyncTask task : asyncTasks.values()) {
                if (task.waitType == WaitType.KEYBIND && task.waitKeybindKey != null
                        && task.waitKeybindKey.equalsIgnoreCase(key)) {
                    task.keybindMet = true;
                    task.keybindPlayer = player;
                }
            }

            // Fire "keybind" event handlers
            for (var entry : snapshotEventHandlers()) {
                EventHandler handler = entry.getValue();
                if (!handler.active || !handler.eventName.equals("keybind")) continue;
                if (!handler.eventArgs.isEmpty()) {
                    String expectedKey = String.valueOf(handler.eventArgs.get(0));
                    if (!expectedKey.equalsIgnoreCase(key)) continue;
                }

                Object prevPlayer = variables.get("_eventPlayer");
                variables.put("_eventPlayer", player);
                try {
                    runEventHandlerBody(entry.getKey(), handler);
                } catch (ReturnException e) {
                    // return exits handler body only; use stop handler to deactivate
                } catch (Exception e) {
                    LOGGER.error("[Script: {}] Keybind handler '{}' error: {}", script.getName(), entry.getKey(), e.getMessage(), e);
                    ScriptConsole.error(script.getName(), "Обработчик keybind '" + entry.getKey() + "': " + e.getMessage());
                    if (source != null) {
                        source.sendFailure(net.minecraft.network.chat.Component.literal(
                                "§c[Spraute] Keybind handler '" + entry.getKey() + "' error: " + e.getMessage()));
                    }
                }
                if (prevPlayer != null) variables.put("_eventPlayer", prevPlayer);
                else variables.remove("_eventPlayer");
            }
        }

        public void onPlayerJoin(net.minecraft.server.level.ServerPlayer player) {
            handleJoinWait(player, false);

            for (var entry : snapshotEventHandlers()) {
                EventHandler handler = entry.getValue();
                if (!handler.active) continue;
                if (!isJoinEventName(handler.eventName)) continue;
                if (isFirstJoinEventName(handler.eventName)) {
                    if (!FirstJoinData.get(org.zonarstudio.spraute_engine.compat.SprauteEntityCompat.serverLevel(player)).isFirstJoin(player.getUUID())) continue;
                }
                if (!matchesJoinHandler(handler, player)) continue;

                Object prevPlayer = variables.get("_eventPlayer");
                variables.put("_eventPlayer", player);
                try {
                    runEventHandlerBody(entry.getKey(), handler);
                } catch (ReturnException e) {
                    // return exits handler body only; use stop handler to deactivate
                } catch (Exception e) {
                    LOGGER.error("[Script: {}] Join handler '{}' error: {}", script.getName(), entry.getKey(), e.getMessage(), e);
                }
                if (prevPlayer != null) variables.put("_eventPlayer", prevPlayer);
                else variables.remove("_eventPlayer");
            }
        }

        public void onPlayerLeave(net.minecraft.server.level.ServerPlayer player) {
            if (waitType == WaitType.LEAVE && waitLeavePlayers != null
                    && waitLeavePlayers.matches(player.getUUID())) {
                leaveEventMet = true;
                waitLeavePlayer = player;
            }
            for (AsyncTask task : asyncTasks.values()) {
                if (task.waitType == WaitType.LEAVE && task.waitLeavePlayers != null
                        && task.waitLeavePlayers.matches(player.getUUID())) {
                    task.leaveEventMet = true;
                    task.waitLeavePlayer = player;
                }
            }

            for (var entry : snapshotEventHandlers()) {
                EventHandler handler = entry.getValue();
                if (!handler.active || !isLeaveEventName(handler.eventName)) continue;
                if (!matchesJoinHandler(handler, player)) continue;
                Object prevPlayer = variables.get("_eventPlayer");
                variables.put("_eventPlayer", player);
                try {
                    runEventHandlerBody(entry.getKey(), handler);
                } catch (ReturnException ignored) {
                } catch (Exception e) {
                    LOGGER.error("[Script: {}] Leave handler '{}' error: {}",
                            script.getName(), entry.getKey(), e.getMessage(), e);
                }
                if (prevPlayer != null) variables.put("_eventPlayer", prevPlayer);
                else variables.remove("_eventPlayer");
            }
        }

        private void handleJoinWait(net.minecraft.server.level.ServerPlayer player, boolean asyncTask) {
            net.minecraft.server.level.ServerLevel level = org.zonarstudio.spraute_engine.compat.SprauteEntityCompat.serverLevel(player);
            boolean firstJoin = FirstJoinData.get(level).isFirstJoin(player.getUUID());

            if (!asyncTask) {
                if (waitType == WaitType.JOIN && waitJoinPlayers != null && waitJoinPlayers.matches(player.getUUID())) {
                    if (!waitFirstJoinOnly || firstJoin) {
                        joinEventMet = true;
                        waitJoinPlayerUuid = player.getUUID();
                    }
                }
            }
            for (AsyncTask task : asyncTasks.values()) {
                if (task.waitType == WaitType.JOIN && task.waitJoinPlayers != null
                        && task.waitJoinPlayers.matches(player.getUUID())) {
                    if (!task.waitFirstJoinOnly || firstJoin) {
                        task.joinEventMet = true;
                        task.waitJoinPlayerUuid = player.getUUID();
                    }
                }
            }
        }

        private static boolean isJoinEventName(String eventName) {
            if (eventName == null) return false;
            String n = eventName.toLowerCase();
            return n.equals("join") || n.equals("firstjoin") || n.equals("first_join");
        }

        private static boolean isFirstJoinEventName(String eventName) {
            if (eventName == null) return false;
            String n = eventName.toLowerCase();
            return n.equals("firstjoin") || n.equals("first_join");
        }

        private static boolean isLeaveEventName(String eventName) {
            if (eventName == null) return false;
            String n = eventName.toLowerCase();
            return n.equals("leave") || n.equals("logout")
                    || n.equals("playerleave") || n.equals("player_leave");
        }

        private boolean matchesJoinHandler(EventHandler handler, net.minecraft.server.level.ServerPlayer player) {
            if (handler.eventArgs == null || handler.eventArgs.isEmpty()) return true;
            Object arg0 = handler.eventArgs.get(0);
            net.minecraft.world.entity.Entity ent = resolveEntity(arg0);
            if (ent instanceof net.minecraft.server.level.ServerPlayer sp) {
                return sp.getUUID().equals(player.getUUID());
            }
            PlayerFilter filter = buildPlayerFilter(arg0);
            return filter != null && filter.matches(player.getUUID());
        }

        public void onPlayerDimensionChange(net.minecraft.server.level.ServerPlayer player, String fromDimension, String toDimension) {
            if (waitType == WaitType.DIMENSION && waitDimensionPlayers != null
                    && waitDimensionPlayers.matches(player.getUUID())
                    && DimensionScriptUtil.matchesDimension(waitDimensionId, toDimension)) {
                dimensionEventMet = true;
            }
            for (AsyncTask task : asyncTasks.values()) {
                if (task.waitType == WaitType.DIMENSION && task.waitDimensionPlayers != null
                        && task.waitDimensionPlayers.matches(player.getUUID())
                        && DimensionScriptUtil.matchesDimension(task.waitDimensionId, toDimension)) {
                    task.dimensionEventMet = true;
                }
            }

            for (var entry : snapshotEventHandlers()) {
                EventHandler handler = entry.getValue();
                if (!handler.active || !handler.eventName.equals("dimension")) continue;

                String expectedDim = null;
                if (handler.eventArgs.size() == 1) {
                    Object arg0 = handler.eventArgs.get(0);
                    net.minecraft.world.entity.Entity ent = resolveEntity(arg0);
                    if (ent instanceof net.minecraft.server.level.ServerPlayer sp) {
                        if (!sp.getUUID().equals(player.getUUID())) continue;
                    } else {
                        expectedDim = String.valueOf(arg0);
                    }
                } else if (handler.eventArgs.size() >= 2) {
                    net.minecraft.world.entity.Entity ent = resolveEntity(handler.eventArgs.get(0));
                    if (ent == null || !ent.getUUID().equals(player.getUUID())) continue;
                    expectedDim = String.valueOf(handler.eventArgs.get(1));
                }

                if (expectedDim != null && !DimensionScriptUtil.matchesDimension(expectedDim, toDimension)) continue;

                Object prevPlayer = variables.get("_eventPlayer");
                Object prevDim = variables.get("_eventDimension");
                Object prevFrom = variables.get("_eventFromDimension");
                variables.put("_eventPlayer", player);
                variables.put("_eventDimension", toDimension);
                variables.put("_eventFromDimension", fromDimension);
                try {
                    runEventHandlerBody(entry.getKey(), handler);
                } catch (ReturnException e) {
                    // return exits handler body only; use stop handler to deactivate
                } catch (Exception e) {
                    LOGGER.error("[Script: {}] Dimension handler '{}' error: {}", script.getName(), entry.getKey(), e.getMessage());
                }
                if (prevPlayer != null) variables.put("_eventPlayer", prevPlayer);
                else variables.remove("_eventPlayer");
                if (prevDim != null) variables.put("_eventDimension", prevDim);
                else variables.remove("_eventDimension");
                if (prevFrom != null) variables.put("_eventFromDimension", prevFrom);
                else variables.remove("_eventFromDimension");
            }
        }

        public void onDeath(net.minecraft.world.entity.LivingEntity entity, net.minecraft.world.entity.Entity killer) {
            // Main script await death
            if (waitType == WaitType.DEATH && waitDeathTarget != null) {
                if (matchesDeathTarget(entity, waitDeathTarget)) {
                    deathMet = true;
                    deathKiller = killer;
                    deadEntity = entity;
                }
            }

            // Main script await kill
            if (waitType == WaitType.KILL && waitKillKillerTarget != null && killer != null) {
                if (matchesKillFilter(killer, waitKillKillerTarget)) {
                    Object victimFilter = waitKillVictimTarget != null ? waitKillVictimTarget : "any";
                    if (matchesDeathTarget(entity, victimFilter)) {
                        killMet = true;
                        deathKiller = killer;
                        deadEntity = entity;
                    }
                }
            }

            for (AsyncTask task : asyncTasks.values()) {
                if (task.waitType == WaitType.DEATH && task.waitDeathTarget != null
                        && matchesDeathTarget(entity, task.waitDeathTarget)) {
                    task.deathMet = true;
                    task.deathKiller = killer;
                    task.deadEntity = entity;
                }
                if (task.waitType == WaitType.KILL && task.waitKillKillerTarget != null && killer != null) {
                    if (matchesKillFilter(killer, task.waitKillKillerTarget)) {
                        Object victimFilter = task.waitKillVictimTarget != null ? task.waitKillVictimTarget : "any";
                        if (matchesDeathTarget(entity, victimFilter)) {
                            task.killMet = true;
                            task.deathKiller = killer;
                            task.deadEntity = entity;
                        }
                    }
                }
            }

            // Fire "death" event handlers
            for (var entry : snapshotEventHandlers()) {
                EventHandler handler = entry.getValue();
                if (!handler.active || !handler.eventName.equals("death")) continue;

                if (!handler.eventArgs.isEmpty()) {
                    if (!matchesEntityRef(entity, handler.eventArgs.get(0))) continue;
                }

                Object prevEntity = variables.get("_eventEntity");
                Object prevEntityType = variables.get("_eventEntityType");
                Object prevEntityX = variables.get("_eventEntityX");
                Object prevEntityY = variables.get("_eventEntityY");
                Object prevEntityZ = variables.get("_eventEntityZ");
                Object prevKiller = variables.get("_eventKiller");
                Object prevKillerType = variables.get("_eventKillerType");
                Object prevKillerX = variables.get("_eventKillerX");
                Object prevKillerY = variables.get("_eventKillerY");
                Object prevKillerZ = variables.get("_eventKillerZ");
                putEventEntityVars(entity);
                putEventKillerVars(killer);
                try {
                    runEventHandlerBody(entry.getKey(), handler);
                } catch (ReturnException e) {
                    // return exits handler body only; use stop handler to deactivate
                } catch (Exception e) {
                    LOGGER.error("[Script: {}] Death handler '{}' error: {}", script.getName(), entry.getKey(), e.getMessage());
                }
                restoreEventEntityVars(prevEntity, prevEntityType, prevEntityX, prevEntityY, prevEntityZ);
                restoreEventKillerVars(prevKiller, prevKillerType, prevKillerX, prevKillerY, prevKillerZ);
            }

            // Fire "kill" event handlers
            if (killer != null) {
                for (var entry : snapshotEventHandlers()) {
                    EventHandler handler = entry.getValue();
                    if (!handler.active || !handler.eventName.equals("kill")) continue;

                    if (!handler.eventArgs.isEmpty()) {
                        if (!matchesEntityRef(killer, handler.eventArgs.get(0))) continue;
                    }
                    if (handler.eventArgs.size() >= 2) {
                        if (!matchesEntityRef(entity, handler.eventArgs.get(1))) continue;
                    }

                    Object prevEntity = variables.get("_eventEntity");
                    Object prevEntityType = variables.get("_eventEntityType");
                    Object prevEntityX = variables.get("_eventEntityX");
                    Object prevEntityY = variables.get("_eventEntityY");
                    Object prevEntityZ = variables.get("_eventEntityZ");
                    Object prevKiller = variables.get("_eventKiller");
                    Object prevKillerType = variables.get("_eventKillerType");
                    Object prevKillerX = variables.get("_eventKillerX");
                    Object prevKillerY = variables.get("_eventKillerY");
                    Object prevKillerZ = variables.get("_eventKillerZ");
                    putEventEntityVars(entity);
                    putEventKillerVars(killer);
                    try {
                        runEventHandlerBody(entry.getKey(), handler);
                    } catch (ReturnException e) {
                        // return exits handler body only; use stop handler to deactivate
                    } catch (Exception e) {
                        LOGGER.error("[Script: {}] Kill handler '{}' error: {}", script.getName(), entry.getKey(), e.getMessage());
                    }
                    restoreEventEntityVars(prevEntity, prevEntityType, prevEntityX, prevEntityY, prevEntityZ);
                    restoreEventKillerVars(prevKiller, prevKillerType, prevKillerX, prevKillerY, prevKillerZ);
                }
            }
        }

        public void onUiOverlapAction(net.minecraft.server.level.ServerPlayer player, String id1, String id2, boolean overlapping) {
            if (!overlapping) return; // For now, only trigger when they start touching

            // Handle main script await
            if (waitType == WaitType.UI_OVERLAP && waitUiOverlapPlayers != null && waitUiOverlapPlayers.matches(player.getUUID())) {
                if ((waitUiOverlapId1.equals(id1) && waitUiOverlapId2.equals(id2)) ||
                    (waitUiOverlapId1.equals(id2) && waitUiOverlapId2.equals(id1))) {
                    uiOverlapMet = true;
                    org.zonarstudio.spraute_engine.network.ModNetwork.CHANNEL.send(
                        net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
                        new org.zonarstudio.spraute_engine.network.SprauteUiMonitorOverlapPacket(id1, id2, false)
                    );
                }
            }

            // Handle async tasks await
            for (AsyncTask t : asyncTasks.values()) {
                if (t.waitType == WaitType.UI_OVERLAP && t.waitUiOverlapPlayers != null && t.waitUiOverlapPlayers.matches(player.getUUID())) {
                    if ((t.waitUiOverlapId1.equals(id1) && t.waitUiOverlapId2.equals(id2)) ||
                        (t.waitUiOverlapId1.equals(id2) && t.waitUiOverlapId2.equals(id1))) {
                        t.uiOverlapMet = true;
                        org.zonarstudio.spraute_engine.network.ModNetwork.CHANNEL.send(
                            net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
                            new org.zonarstudio.spraute_engine.network.SprauteUiMonitorOverlapPacket(id1, id2, false)
                        );
                    }
                }
            }

            // Handle "on uiTouch" events
            for (var entry : snapshotEventHandlers()) {
                EventHandler handler = entry.getValue();
                boolean isTouch = handler.eventName.equalsIgnoreCase("uiTouch") || handler.eventName.equalsIgnoreCase("uiOverlap");
                if (!handler.active || !isTouch) continue;

                if (handler.eventArgs.size() >= 3) {
                    net.minecraft.world.entity.Entity targetPlayer = resolveEntity(handler.eventArgs.get(0));
                    if (!matchesEventPlayerArg(handler.eventArgs.get(0), player.getUUID())) continue;

                    String expId1 = String.valueOf(handler.eventArgs.get(1));
                    String expId2 = String.valueOf(handler.eventArgs.get(2));

                    if ((expId1.equals(id1) && expId2.equals(id2)) || (expId1.equals(id2) && expId2.equals(id1))) {
                        Object prevPlayer = variables.get("_eventPlayer");
                        variables.put("_eventPlayer", player);
                        try {
                            runEventHandlerBody(entry.getKey(), handler);
                        } catch (ReturnException e) {
                            // return exits handler body only; use stop handler to deactivate
                        } catch (Exception e) {
                            LOGGER.error("[Script: {}] uiTouch handler '{}' error: {}", script.getName(), entry.getKey(), e.getMessage());
                        }
                        if (prevPlayer != null) variables.put("_eventPlayer", prevPlayer);
                        else variables.remove("_eventPlayer");
                    }
                }
            }
        }

        public void onSceneHitboxTouch(net.minecraft.server.level.ServerPlayer player, String sceneId,
                                       String nodeId, String nodeName, String triggerId, boolean entering) {
            org.zonarstudio.spraute_engine.scene.SceneActiveState state =
                    org.zonarstudio.spraute_engine.scene.SceneRuntime.getActiveState(sceneId);
            org.zonarstudio.spraute_engine.scene.SceneNodeView nodeView =
                    state == null ? null : state.nodeView(nodeId);
            String tid = triggerId == null ? "" : triggerId.trim();
            if (tid.isEmpty()) tid = nodeId == null ? "" : nodeId;

            // await hitboxTouch / hitboxExit (main + async)
            if ((waitType == WaitType.HITBOX_TOUCH && entering) || (waitType == WaitType.HITBOX_EXIT && !entering)) {
                if (waitHitboxPlayers != null && waitHitboxPlayers.matches(player.getUUID())
                        && matchesHitboxSceneArg(waitHitboxScene, sceneId)
                        && matchesHitboxNodeArg(waitHitboxTriggerId, sceneId, nodeId, nodeName, tid)) {
                    hitboxEventMet = true;
                    variables.put("_eventPlayer", player);
                    variables.put("_eventScene", sceneId);
                    if (nodeView != null) variables.put("_eventNode", nodeView);
                    variables.put("_eventEnter", entering);
                    variables.put("_eventTriggerId", tid);
                }
            }
            for (AsyncTask t : asyncTasks.values()) {
                if ((t.waitType == WaitType.HITBOX_TOUCH && entering) || (t.waitType == WaitType.HITBOX_EXIT && !entering)) {
                    if (t.waitHitboxPlayers != null && t.waitHitboxPlayers.matches(player.getUUID())
                            && matchesHitboxSceneArg(t.waitHitboxScene, sceneId)
                            && matchesHitboxNodeArg(t.waitHitboxTriggerId, sceneId, nodeId, nodeName, tid)) {
                        t.hitboxEventMet = true;
                        variables.put("_eventPlayer", player);
                        variables.put("_eventScene", sceneId);
                        if (nodeView != null) variables.put("_eventNode", nodeView);
                        variables.put("_eventEnter", entering);
                        variables.put("_eventTriggerId", tid);
                    }
                }
            }

            for (var entry : snapshotEventHandlers()) {
                EventHandler handler = entry.getValue();
                if (!handler.active) continue;
                String event = handler.eventName == null ? "" : handler.eventName.toLowerCase(java.util.Locale.ROOT);
                boolean isEnter = event.equals("hitboxtouch") || event.equals("hitboxenter");
                boolean isExit = event.equals("hitboxexit") || event.equals("hitboxleave");
                if (!isEnter && !isExit) continue;
                if (isEnter && !entering) continue;
                if (isExit && entering) continue;

                if (handler.eventArgs.size() >= 1
                        && !matchesEventPlayerArg(handler.eventArgs.get(0), player.getUUID())) continue;
                if (handler.eventArgs.size() >= 2) {
                    if (!matchesHitboxSceneArg(handler.eventArgs.get(1), sceneId)) continue;
                }
                if (handler.eventArgs.size() >= 3) {
                    if (!matchesHitboxNodeArg(handler.eventArgs.get(2), sceneId, nodeId, nodeName, tid)) continue;
                }

                Object prevPlayer = variables.get("_eventPlayer");
                Object prevScene = variables.get("_eventScene");
                Object prevNode = variables.get("_eventNode");
                Object prevEnter = variables.get("_eventEnter");
                Object prevTrigger = variables.get("_eventTriggerId");
                variables.put("_eventPlayer", player);
                variables.put("_eventScene", sceneId);
                if (nodeView != null) variables.put("_eventNode", nodeView);
                variables.put("_eventEnter", entering);
                variables.put("_eventTriggerId", tid);
                try {
                    runEventHandlerBody(entry.getKey(), handler);
                } catch (ReturnException e) {
                    // return exits handler body only
                } catch (Exception e) {
                    LOGGER.error("[Script: {}] hitboxTouch handler '{}' error: {}",
                            script.getName(), entry.getKey(), e.getMessage());
                }
                if (prevPlayer != null) variables.put("_eventPlayer", prevPlayer);
                else variables.remove("_eventPlayer");
                if (prevScene != null) variables.put("_eventScene", prevScene);
                else variables.remove("_eventScene");
                if (prevNode != null) variables.put("_eventNode", prevNode);
                else variables.remove("_eventNode");
                if (prevEnter != null) variables.put("_eventEnter", prevEnter);
                else variables.remove("_eventEnter");
                if (prevTrigger != null) variables.put("_eventTriggerId", prevTrigger);
                else variables.remove("_eventTriggerId");
            }
        }

        private boolean matchesHitboxSceneArg(Object arg, String sceneId) {
            if (arg == null) return true;
            String token = String.valueOf(arg).trim();
            return token.isEmpty() || "*".equals(token) || token.equals(sceneId);
        }

        private boolean matchesHitboxNodeArg(Object arg, String sceneId, String nodeId, String nodeName, String triggerId) {
            if (arg == null) return true;
            String token = String.valueOf(arg).trim();
            if (token.isEmpty() || "*".equals(token)) return true;
            if (token.equals(nodeId) || token.equals(nodeName)) return true;
            if (triggerId != null && !triggerId.isEmpty() && token.equals(triggerId)) return true;
            org.zonarstudio.spraute_engine.scene.SceneActiveState state =
                    org.zonarstudio.spraute_engine.scene.SceneRuntime.getActiveState(sceneId);
            if (state == null) return false;
            org.zonarstudio.spraute_engine.scene.SceneNode found =
                    org.zonarstudio.spraute_engine.scene.ScenePathResolver.findNode(state.document, token);
            return found != null && nodeId.equals(found.id);
        }

        public void onUiAction(net.minecraft.server.level.ServerPlayer player, String widgetId, boolean closed) {
            onUiAction(player, closed
                    ? org.zonarstudio.spraute_engine.network.SprauteUiActionPacket.ACTION_CLOSE
                    : org.zonarstudio.spraute_engine.network.SprauteUiActionPacket.ACTION_CLICK,
                    widgetId, closed ? -1 : 0);
        }

        public void onUiAction(net.minecraft.server.level.ServerPlayer player, byte action, String widgetId, int mouseButton) {
            String wid = widgetId != null ? widgetId : "";
            boolean closed = action == org.zonarstudio.spraute_engine.network.SprauteUiActionPacket.ACTION_CLOSE;
            boolean isClick = action == org.zonarstudio.spraute_engine.network.SprauteUiActionPacket.ACTION_CLICK;
            boolean isHoverEnter = action == org.zonarstudio.spraute_engine.network.SprauteUiActionPacket.ACTION_HOVER_ENTER;
            boolean isHoverLeave = action == org.zonarstudio.spraute_engine.network.SprauteUiActionPacket.ACTION_HOVER_LEAVE;
            boolean isDragStart = action == org.zonarstudio.spraute_engine.network.SprauteUiActionPacket.ACTION_DRAG_START;
            boolean isDrop = action == org.zonarstudio.spraute_engine.network.SprauteUiActionPacket.ACTION_DROP;
            boolean isDragEnd = action == org.zonarstudio.spraute_engine.network.SprauteUiActionPacket.ACTION_DRAG_END;

            if (isClick || closed) {
                if ((waitType == WaitType.UI_CLICK || waitType == WaitType.UI_CLOSE) && waitUiPlayers != null && waitUiPlayers.matches(player.getUUID())) {
                    if (waitType == WaitType.UI_CLICK && (wid.contains(":") || !isClick)) return;
                    if (waitType == WaitType.UI_CLOSE && !closed) return;
                    uiClickMet = true;
                    uiClickWidgetId = wid;
                    uiClickClosed = closed;
                    uiClickMouseButton = mouseButton;
                }
                if (waitType == WaitType.UI_INPUT && isClick && waitUiPlayers != null && waitUiPlayers.matches(player.getUUID())) {
                    if (wid.startsWith("input:") && (uiInputWidgetId == null || wid.equals("input:" + uiInputWidgetId))) {
                        uiClickMet = true;
                        uiInputText = wid.substring(wid.indexOf(":", 6) + 1);
                    }
                }

                for (AsyncTask t : asyncTasks.values()) {
                    if ((t.waitType == WaitType.UI_CLICK || t.waitType == WaitType.UI_CLOSE) && t.waitUiPlayers != null && t.waitUiPlayers.matches(player.getUUID())) {
                        if (t.waitType == WaitType.UI_CLICK && (wid.contains(":") || !isClick)) continue;
                        if (t.waitType == WaitType.UI_CLOSE && !closed) continue;
                        t.uiClickMet = true;
                        t.uiClickWidgetId = wid;
                        t.uiClickClosed = closed;
                        t.uiClickMouseButton = mouseButton;
                    }
                    if (t.waitType == WaitType.UI_INPUT && isClick && t.waitUiPlayers != null && t.waitUiPlayers.matches(player.getUUID())) {
                        if (wid.startsWith("input:") && (t.uiInputWidgetId == null || wid.equals("input:" + t.uiInputWidgetId))) {
                            t.uiClickMet = true;
                            t.uiInputText = wid.substring(wid.indexOf(":", 6) + 1);
                        }
                    }
                }
            }

            if (isDrop) {
                var dropMsg = org.zonarstudio.spraute_engine.network.SprauteUiActionPacket.parseDrop(wid);
                if (dropMsg.dropId != null && !dropMsg.dropId.isEmpty()) {
                    if (waitType == WaitType.UI_DROP && waitUiPlayers != null && waitUiPlayers.matches(player.getUUID())) {
                        uiClickMet = true;
                        uiClickWidgetId = wid;
                    }
                    for (AsyncTask t : asyncTasks.values()) {
                        if (t.waitType == WaitType.UI_DROP && t.waitUiPlayers != null && t.waitUiPlayers.matches(player.getUUID())) {
                            t.uiClickMet = true;
                            t.uiClickWidgetId = wid;
                        }
                    }
                }
            }

            for (var entry : snapshotEventHandlers()) {
                EventHandler handler = entry.getValue();
                boolean isUiClick = handler.eventName.equals("uiClick") || handler.eventName.equals("uiclick");
                boolean isUiClose = handler.eventName.equals("uiClose") || handler.eventName.equals("uiclose");
                boolean isUiInput = handler.eventName.equals("uiInput") || handler.eventName.equals("uiinput");
                boolean isUiHover = handler.eventName.equals("uiHover") || handler.eventName.equals("uihover");
                boolean isUiDragStart = handler.eventName.equals("uiDragStart") || handler.eventName.equals("uidragstart");
                boolean isUiDropEv = handler.eventName.equals("uiDrop") || handler.eventName.equals("uidrop");
                boolean isUiDragEnd = handler.eventName.equals("uiDragEnd") || handler.eventName.equals("uidragend");

                if (!handler.active || (!isUiClick && !isUiClose && !isUiInput && !isUiHover
                        && !isUiDragStart && !isUiDropEv && !isUiDragEnd)) continue;

                if (isUiHover) {
                    if (!isHoverEnter && !isHoverLeave) continue;
                    if (wid.isEmpty()) continue;
                } else if (isUiInput && (!wid.startsWith("input:") || closed || !isClick)) continue;
                else if (isUiClick && (closed || !isClick || wid.startsWith("input:"))) continue;
                else if (isUiClose && !closed) continue;
                else if (isUiDragStart && !isDragStart) continue;
                else if (isUiDropEv && !isDrop) continue;
                else if (isUiDragEnd && !isDragEnd) continue;

                var dragMsg = (isUiDragStart || isUiDropEv || isUiDragEnd)
                        ? (isUiDragStart
                            ? org.zonarstudio.spraute_engine.network.SprauteUiActionPacket.parseDragStart(wid)
                            : org.zonarstudio.spraute_engine.network.SprauteUiActionPacket.parseDrop(wid))
                        : null;
                if (isUiDropEv && (dragMsg == null || dragMsg.dropId.isEmpty())) continue;

                if (!handler.eventArgs.isEmpty()) {
                    net.minecraft.world.entity.Entity targetPlayer = resolveEntity(handler.eventArgs.get(0));
                    if (!matchesEventPlayerArg(handler.eventArgs.get(0), player.getUUID())) continue;
                }

                if (isUiInput && handler.eventArgs.size() > 1) {
                    String reqWid = String.valueOf(handler.eventArgs.get(1));
                    if (!wid.startsWith("input:" + reqWid + ":")) continue;
                }
                if (isUiHover && handler.eventArgs.size() > 1) {
                    String reqWid = String.valueOf(handler.eventArgs.get(1));
                    if (!reqWid.equals(wid)) continue;
                }
                if (isUiDropEv && handler.eventArgs.size() > 1) {
                    String reqWid = String.valueOf(handler.eventArgs.get(1));
                    if (!reqWid.equals(dragMsg.dropId) && !reqWid.equals(dragMsg.dragId)) continue;
                }

                Object prevPlayer = variables.get("_eventPlayer");
                Object prevWidget = variables.get("_eventWidget");
                Object prevInput = variables.get("_eventInput");
                Object prevMouseButton = variables.get("_eventMouseButton");
                Object prevHover = variables.get("_eventHover");
                Object prevDrag = variables.get("_eventDrag");
                Object prevDrop = variables.get("_eventDrop");
                Object prevPayload = variables.get("_eventPayload");
                Object prevX = variables.get("_eventX");
                Object prevY = variables.get("_eventY");
                variables.put("_eventPlayer", player);
                if (isUiInput) {
                    String[] parts = wid.split(":", 3);
                    variables.put("_eventWidget", parts.length > 1 ? parts[1] : "");
                    variables.put("_eventInput", parts.length > 2 ? parts[2] : "");
                } else if (isUiDragStart) {
                    variables.put("_eventWidget", dragMsg.dragId);
                    variables.put("_eventPayload", dragMsg.payload);
                } else if (isUiDropEv) {
                    variables.put("_eventWidget", dragMsg.dropId);
                    variables.put("_eventDrop", dragMsg.dropId);
                    variables.put("_eventDrag", dragMsg.dragId);
                    variables.put("_eventPayload", dragMsg.payload);
                    variables.put("_eventX", dragMsg.x);
                    variables.put("_eventY", dragMsg.y);
                } else if (isUiDragEnd) {
                    variables.put("_eventWidget", dragMsg.dragId);
                    variables.put("_eventDrag", dragMsg.dragId);
                    variables.put("_eventDrop", dragMsg.dropId);
                    variables.put("_eventPayload", dragMsg.payload);
                } else {
                    variables.put("_eventWidget", wid);
                }
                if (isUiClick) {
                    variables.put("_eventMouseButton",
                            org.zonarstudio.spraute_engine.network.SprauteUiActionPacket.mouseButtonLabel(mouseButton));
                }
                if (isUiHover) {
                    variables.put("_eventHover", isHoverEnter ? "enter" : "leave");
                }

                try {
                    runEventHandlerBody(entry.getKey(), handler);
                } catch (ReturnException e) {
                    // return exits handler body only; use stop handler to deactivate
                } catch (Exception e) {
                    LOGGER.error("[Script: {}] Event handler '{}' error: {}", script.getName(), entry.getKey(), e.getMessage());
                }

                if (prevPlayer != null) variables.put("_eventPlayer", prevPlayer);
                else variables.remove("_eventPlayer");
                if (prevWidget != null) variables.put("_eventWidget", prevWidget);
                else variables.remove("_eventWidget");
                if (prevInput != null) variables.put("_eventInput", prevInput);
                else variables.remove("_eventInput");
                if (prevMouseButton != null) variables.put("_eventMouseButton", prevMouseButton);
                else variables.remove("_eventMouseButton");
                if (prevHover != null) variables.put("_eventHover", prevHover);
                else variables.remove("_eventHover");
                if (prevDrag != null) variables.put("_eventDrag", prevDrag);
                else variables.remove("_eventDrag");
                if (prevDrop != null) variables.put("_eventDrop", prevDrop);
                else variables.remove("_eventDrop");
                if (prevPayload != null) variables.put("_eventPayload", prevPayload);
                else variables.remove("_eventPayload");
                if (prevX != null) variables.put("_eventX", prevX);
                else variables.remove("_eventX");
                if (prevY != null) variables.put("_eventY", prevY);
                else variables.remove("_eventY");
            }

            if (boundUiTemplate != null && boundUiPlayerUuid != null && boundUiPlayerUuid.equals(player.getUUID())) {
                if (isClick && widgetId != null && !widgetId.isEmpty() && !wid.contains(":")) {
                    java.util.List<CompiledScript.Instruction> h = boundUiTemplate.getClickHandlers().get(widgetId);
                    if (h != null && !h.isEmpty()) {
                        Object prevPlayer = variables.get("_eventPlayer");
                        Object prevWidget = variables.get("_eventWidget");
                        Object prevMouseButton = variables.get("_eventMouseButton");
                        variables.put("_eventPlayer", player);
                        variables.put("_eventWidget", widgetId);
                        variables.put("_eventMouseButton",
                                org.zonarstudio.spraute_engine.network.SprauteUiActionPacket.mouseButtonLabel(mouseButton));
                        try {
                            runEventHandlerBody("ui_onclick:" + widgetId, h);
                        } catch (Exception e) {
                            LOGGER.error("[Script: {}] UI on_click '{}': {}", script.getName(), widgetId, e.getMessage());
                        } finally {
                            if (prevPlayer != null) variables.put("_eventPlayer", prevPlayer);
                            else variables.remove("_eventPlayer");
                            if (prevWidget != null) variables.put("_eventWidget", prevWidget);
                            else variables.remove("_eventWidget");
                            if (prevMouseButton != null) variables.put("_eventMouseButton", prevMouseButton);
                            else variables.remove("_eventMouseButton");
                        }
                    }
                }
                if (isDragStart) {
                    var msg = org.zonarstudio.spraute_engine.network.SprauteUiActionPacket.parseDragStart(wid);
                    runBoundUiEvent(player, "dragStart", msg.dragId, msg, "ui_ondragstart:");
                }
                if (isDrop) {
                    var msg = org.zonarstudio.spraute_engine.network.SprauteUiActionPacket.parseDrop(wid);
                    if (!msg.dropId.isEmpty()) {
                        runBoundUiEvent(player, "drop", msg.dropId, msg, "ui_ondrop:");
                    }
                }
                if (isDragEnd) {
                    var msg = org.zonarstudio.spraute_engine.network.SprauteUiActionPacket.parseDrop(wid);
                    runBoundUiEvent(player, "dragEnd", msg.dragId, msg, "ui_ondragend:");
                }
                if (closed) {
                    boundUiTemplate = null;
                    boundUiPlayerUuid = null;
                }
            }
        }

        private void runBoundUiEvent(net.minecraft.server.level.ServerPlayer player, String prefix, String widgetId,
                                     org.zonarstudio.spraute_engine.network.SprauteUiActionPacket.UiDragMsg msg,
                                     String logPrefix) {
            if (boundUiTemplate == null || widgetId == null || widgetId.isEmpty()) return;
            java.util.List<CompiledScript.Instruction> h = boundUiTemplate.getWidgetHandler(prefix, widgetId);
            if (h == null || h.isEmpty()) return;
            Object prevPlayer = variables.get("_eventPlayer");
            Object prevWidget = variables.get("_eventWidget");
            Object prevDrag = variables.get("_eventDrag");
            Object prevDrop = variables.get("_eventDrop");
            Object prevPayload = variables.get("_eventPayload");
            Object prevX = variables.get("_eventX");
            Object prevY = variables.get("_eventY");
            variables.put("_eventPlayer", player);
            variables.put("_eventWidget", "drop".equals(prefix) ? msg.dropId : msg.dragId);
            variables.put("_eventDrag", msg.dragId);
            variables.put("_eventDrop", msg.dropId);
            variables.put("_eventPayload", msg.payload);
            variables.put("_eventX", msg.x);
            variables.put("_eventY", msg.y);
            try {
                runEventHandlerBody(logPrefix + widgetId, h);
            } catch (Exception e) {
                LOGGER.error("[Script: {}] UI {} '{}': {}", script.getName(), logPrefix, widgetId, e.getMessage());
            } finally {
                if (prevPlayer != null) variables.put("_eventPlayer", prevPlayer);
                else variables.remove("_eventPlayer");
                if (prevWidget != null) variables.put("_eventWidget", prevWidget);
                else variables.remove("_eventWidget");
                if (prevDrag != null) variables.put("_eventDrag", prevDrag);
                else variables.remove("_eventDrag");
                if (prevDrop != null) variables.put("_eventDrop", prevDrop);
                else variables.remove("_eventDrop");
                if (prevPayload != null) variables.put("_eventPayload", prevPayload);
                else variables.remove("_eventPayload");
                if (prevX != null) variables.put("_eventX", prevX);
                else variables.remove("_eventX");
                if (prevY != null) variables.put("_eventY", prevY);
                else variables.remove("_eventY");
            }
        }

        private void applyUiDropVars(String encoded, String pendingVar) {
            var msg = org.zonarstudio.spraute_engine.network.SprauteUiActionPacket.parseDrop(encoded);
            if (pendingVar != null) {
                variables.put(pendingVar, msg.dropId);
            }
            variables.put("_eventWidget", msg.dropId);
            variables.put("_eventDrop", msg.dropId);
            variables.put("_eventDrag", msg.dragId);
            variables.put("_eventPayload", msg.payload);
            variables.put("_eventX", msg.x);
            variables.put("_eventY", msg.y);
        }

        public void cancelUiWaitsForPlayer(net.minecraft.server.level.ServerPlayer player) {
            if (player == null) return;
            java.util.UUID uuid = player.getUUID();
            if ((waitType == WaitType.UI_CLICK || waitType == WaitType.UI_CLOSE || waitType == WaitType.UI_INPUT
                    || waitType == WaitType.UI_DROP)
                    && waitUiPlayers != null && waitUiPlayers.matches(uuid)) {
                waitType = WaitType.NONE;
                waitUiPlayers = null;
                uiClickMet = true;
                uiClickWidgetId = "__reset__";
                uiClickClosed = true;
            }
            for (AsyncTask task : asyncTasks.values()) {
                if ((task.waitType == WaitType.UI_CLICK || task.waitType == WaitType.UI_CLOSE || task.waitType == WaitType.UI_INPUT
                        || task.waitType == WaitType.UI_DROP)
                        && task.waitUiPlayers != null && task.waitUiPlayers.matches(uuid)) {
                    task.waitType = WaitType.NONE;
                    task.waitUiPlayers = null;
                    task.uiClickMet = true;
                    task.uiClickWidgetId = "__reset__";
                    task.uiClickClosed = true;
                }
            }
        }

        private boolean dimMatches(String waitDim, String eventDim) {
            if (waitDim == null) return true;
            if (eventDim == null) return true;
            String w = waitDim.contains(":") ? waitDim : "minecraft:" + waitDim;
            return w.equals(eventDim);
        }

        public boolean onClickBlock(net.minecraft.world.entity.player.Player player, net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.Block block, boolean isLeft) {
            return onClickBlock(player, pos, block, isLeft, null);
        }
        public boolean onClickBlock(net.minecraft.world.entity.player.Player player, net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.Block block, boolean isLeft, String dimId) {
            String blockStr = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(block).toString();
            if (waitType == WaitType.CLICK_BLOCK && waitBlockPlayers != null && waitBlockPlayers.matches(player.getUUID())) {
                boolean idMatch = waitBlockId == null || waitBlockId.equals(blockStr) || waitBlockId.equals(blockStr.replace("minecraft:", ""));
                boolean posMatch = waitBlockPos == null || waitBlockPos.equals(pos);
                boolean dimMatch = dimMatches(waitBlockDim, dimId);
                if (idMatch && posMatch && dimMatch) {
                    blockEventMet = true;
                    asyncResult = isLeft ? "left" : "right";
                }
            }
            for (AsyncTask task : asyncTasks.values()) {
                if (task.waitType == WaitType.CLICK_BLOCK && task.waitBlockPlayers != null && task.waitBlockPlayers.matches(player.getUUID())) {
                    boolean idMatch = task.waitBlockId == null || task.waitBlockId.equals(blockStr) || task.waitBlockId.equals(blockStr.replace("minecraft:", ""));
                    boolean posMatch = task.waitBlockPos == null || task.waitBlockPos.equals(pos);
                    boolean dimMatch = dimMatches(task.waitBlockDim, dimId);
                    if (idMatch && posMatch && dimMatch) {
                        task.blockEventMet = true;
                        asyncResult = isLeft ? "left" : "right";
                    }
                }
            }
            return fireBlockEvent("clickBlock", player, pos, blockStr, isLeft ? "left" : "right");
        }

        public boolean onBreakBlock(net.minecraft.world.entity.player.Player player, net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.Block block) {
            return onBreakBlock(player, pos, block, null);
        }
        public boolean onBreakBlock(net.minecraft.world.entity.player.Player player, net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.Block block, String dimId) {
            String blockStr = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(block).toString();
            if (waitType == WaitType.BREAK_BLOCK && waitBlockPlayers != null && waitBlockPlayers.matches(player.getUUID())) {
                boolean idMatch = waitBlockId == null || waitBlockId.equals(blockStr) || waitBlockId.equals(blockStr.replace("minecraft:", ""));
                boolean posMatch = waitBlockPos == null || waitBlockPos.equals(pos);
                boolean dimMatch = dimMatches(waitBlockDim, dimId);
                if (idMatch && posMatch && dimMatch) {
                    blockEventMet = true;
                    asyncResult = blockStr;
                }
            }
            for (AsyncTask task : asyncTasks.values()) {
                if (task.waitType == WaitType.BREAK_BLOCK && task.waitBlockPlayers != null && task.waitBlockPlayers.matches(player.getUUID())) {
                    boolean idMatch = task.waitBlockId == null || task.waitBlockId.equals(blockStr) || task.waitBlockId.equals(blockStr.replace("minecraft:", ""));
                    boolean posMatch = task.waitBlockPos == null || task.waitBlockPos.equals(pos);
                    boolean dimMatch = dimMatches(task.waitBlockDim, dimId);
                    if (idMatch && posMatch && dimMatch) {
                        task.blockEventMet = true;
                        asyncResult = blockStr;
                    }
                }
            }
            return fireBlockEvent("breakBlock", player, pos, blockStr, null);
        }

        public boolean onPlaceBlock(net.minecraft.world.entity.player.Player player, net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.Block block) {
            return onPlaceBlock(player, pos, block, null);
        }
        public boolean onPlaceBlock(net.minecraft.world.entity.player.Player player, net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.Block block, String dimId) {
            String blockStr = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(block).toString();
            if (waitType == WaitType.PLACE_BLOCK && waitBlockPlayers != null && waitBlockPlayers.matches(player.getUUID())) {
                boolean idMatch = waitBlockId == null || waitBlockId.equals(blockStr) || waitBlockId.equals(blockStr.replace("minecraft:", ""));
                boolean posMatch = waitBlockPos == null || waitBlockPos.equals(pos);
                boolean dimMatch = dimMatches(waitBlockDim, dimId);
                if (idMatch && posMatch && dimMatch) {
                    blockEventMet = true;
                    asyncResult = blockStr;
                }
            }
            for (AsyncTask task : asyncTasks.values()) {
                if (task.waitType == WaitType.PLACE_BLOCK && task.waitBlockPlayers != null && task.waitBlockPlayers.matches(player.getUUID())) {
                    boolean idMatch = task.waitBlockId == null || task.waitBlockId.equals(blockStr) || task.waitBlockId.equals(blockStr.replace("minecraft:", ""));
                    boolean posMatch = task.waitBlockPos == null || task.waitBlockPos.equals(pos);
                    boolean dimMatch = dimMatches(task.waitBlockDim, dimId);
                    if (idMatch && posMatch && dimMatch) {
                        task.blockEventMet = true;
                        asyncResult = blockStr;
                    }
                }
            }
            return fireBlockEvent("placeBlock", player, pos, blockStr, null);
        }

        public boolean onOpenChest(net.minecraft.world.entity.player.Player player, net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.Block block) {
            return onOpenChest(player, pos, block, null);
        }

        public boolean onOpenChest(net.minecraft.world.entity.player.Player player, net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.Block block, String dimId) {
            String blockStr = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(block).toString();
            if (waitType == WaitType.OPEN_CHEST && waitBlockPlayers != null && waitBlockPlayers.matches(player.getUUID())) {
                if (matchesBlockWait(blockStr, pos, dimId)) {
                    blockEventMet = true;
                    asyncResult = blockStr;
                }
            }
            for (AsyncTask task : asyncTasks.values()) {
                if (task.waitType == WaitType.OPEN_CHEST && task.waitBlockPlayers != null && task.waitBlockPlayers.matches(player.getUUID())) {
                    if (matchesBlockWaitTask(task, blockStr, pos, dimId)) {
                        task.blockEventMet = true;
                        asyncResult = blockStr;
                    }
                }
            }
            return fireBlockEvent("openChest", player, pos, blockStr, null);
        }

        public boolean onOpenDoor(net.minecraft.world.entity.player.Player player, net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.Block block) {
            return onOpenDoor(player, pos, block, null);
        }

        public boolean onOpenDoor(net.minecraft.world.entity.player.Player player, net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.Block block, String dimId) {
            String blockStr = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(block).toString();
            if (waitType == WaitType.OPEN_DOOR && waitBlockPlayers != null && waitBlockPlayers.matches(player.getUUID())) {
                if (matchesBlockWait(blockStr, pos, dimId)) {
                    blockEventMet = true;
                    asyncResult = blockStr;
                }
            }
            for (AsyncTask task : asyncTasks.values()) {
                if (task.waitType == WaitType.OPEN_DOOR && task.waitBlockPlayers != null && task.waitBlockPlayers.matches(player.getUUID())) {
                    if (matchesBlockWaitTask(task, blockStr, pos, dimId)) {
                        task.blockEventMet = true;
                        asyncResult = blockStr;
                    }
                }
            }
            return fireBlockEvent("openDoor", player, pos, blockStr, null);
        }

        public boolean onUseBlock(net.minecraft.world.entity.player.Player player, net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.Block block) {
            return onUseBlock(player, pos, block, null);
        }

        public boolean onUseBlock(net.minecraft.world.entity.player.Player player, net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.Block block, String dimId) {
            String blockStr = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(block).toString();
            if (waitType == WaitType.USE_BLOCK && waitBlockPlayers != null && waitBlockPlayers.matches(player.getUUID())) {
                if (matchesBlockWait(blockStr, pos, dimId)) {
                    blockEventMet = true;
                    asyncResult = blockStr;
                }
            }
            for (AsyncTask task : asyncTasks.values()) {
                if (task.waitType == WaitType.USE_BLOCK && task.waitBlockPlayers != null && task.waitBlockPlayers.matches(player.getUUID())) {
                    if (matchesBlockWaitTask(task, blockStr, pos, dimId)) {
                        task.blockEventMet = true;
                        asyncResult = blockStr;
                    }
                }
            }
            return fireBlockEvent("useBlock", player, pos, blockStr, null);
        }

        private boolean matchesBlockWait(String blockStr, net.minecraft.core.BlockPos pos, String dimId) {
            boolean idMatch = waitBlockId == null || waitBlockId.equals(blockStr) || waitBlockId.equals(blockStr.replace("minecraft:", ""));
            boolean posMatch = waitBlockPos == null || waitBlockPos.equals(pos);
            boolean dimMatch = dimMatches(waitBlockDim, dimId);
            return idMatch && posMatch && dimMatch;
        }

        private boolean matchesBlockWaitTask(AsyncTask task, String blockStr, net.minecraft.core.BlockPos pos, String dimId) {
            boolean idMatch = task.waitBlockId == null || task.waitBlockId.equals(blockStr) || task.waitBlockId.equals(blockStr.replace("minecraft:", ""));
            boolean posMatch = task.waitBlockPos == null || task.waitBlockPos.equals(pos);
            boolean dimMatch = dimMatches(task.waitBlockDim, dimId);
            return idMatch && posMatch && dimMatch;
        }

        private boolean fireBlockEvent(String eventName, net.minecraft.world.entity.player.Player player, net.minecraft.core.BlockPos pos, String blockStr, String extraAction) {
            boolean canceled = false;
            for (var entry : snapshotEventHandlers()) {
                EventHandler handler = entry.getValue();
                if (!handler.active || !handler.eventName.equals(eventName)) continue;
                
                boolean idMatch = true;
                boolean posMatch = true;
                
                if (!handler.eventArgs.isEmpty()) {
                    if (handler.eventArgs.size() == 1) {
                        String idArg = String.valueOf(handler.eventArgs.get(0));
                        idMatch = idArg.equals(blockStr) || idArg.equals(blockStr.replace("minecraft:", ""));
                    } else if (handler.eventArgs.size() >= 3) {
                        int x = ((Number) handler.eventArgs.get(0)).intValue();
                        int y = ((Number) handler.eventArgs.get(1)).intValue();
                        int z = ((Number) handler.eventArgs.get(2)).intValue();
                        posMatch = pos.getX() == x && pos.getY() == y && pos.getZ() == z;
                        if (handler.eventArgs.size() >= 4) {
                            String idArg = String.valueOf(handler.eventArgs.get(3));
                            idMatch = idArg.equals(blockStr) || idArg.equals(blockStr.replace("minecraft:", ""));
                        }
                    }
                }
                
                if (idMatch && posMatch) {
                    Object prevPlayer = variables.get("_eventPlayer");
                    Object prevX = variables.get("_eventX");
                    Object prevY = variables.get("_eventY");
                    Object prevZ = variables.get("_eventZ");
                    Object prevBlock = variables.get("_eventBlock");
                    Object prevAction = variables.get("_eventAction");
                    boolean prevCanceled = context.isEventCanceled();
                    
                    variables.put("_eventPlayer", player);
                    variables.put("_eventX", pos.getX());
                    variables.put("_eventY", pos.getY());
                    variables.put("_eventZ", pos.getZ());
                    variables.put("_eventBlock", blockStr);
                    context.setEventCanceled(false);

                    if (extraAction != null) variables.put("_eventAction", extraAction);
                    
                    try {
                        runEventHandlerBody(entry.getKey(), handler);
                    } catch (ReturnException e) {
                        // return exits handler body only; use stop handler to deactivate
                    } catch (Exception e) {
                        LOGGER.error("[Script: {}] Event handler '{}' error: {}", script.getName(), entry.getKey(), e.getMessage());
                    }
                    
                    if (context.isEventCanceled() || Boolean.TRUE.equals(variables.get("_eventCanceled"))) {
                        canceled = true;
                    }
                    
                    if (prevPlayer != null) variables.put("_eventPlayer", prevPlayer); else variables.remove("_eventPlayer");
                    if (prevX != null) variables.put("_eventX", prevX); else variables.remove("_eventX");
                    if (prevY != null) variables.put("_eventY", prevY); else variables.remove("_eventY");
                    if (prevZ != null) variables.put("_eventZ", prevZ); else variables.remove("_eventZ");
                    if (prevBlock != null) variables.put("_eventBlock", prevBlock); else variables.remove("_eventBlock");
                    if (extraAction != null) {
                        if (prevAction != null) variables.put("_eventAction", prevAction); else variables.remove("_eventAction");
                    }
                    context.setEventCanceled(prevCanceled);
                }
            }
            return canceled;
        }

        private boolean chatMatches(String input, List<String> options, boolean ignoreCase, boolean ignorePunct) {
            String sanitizedInput = ignorePunct ? input.replaceAll("\\p{Punct}", "") : input;
            if (ignoreCase) sanitizedInput = sanitizedInput.toLowerCase();
            
            for (String opt : options) {
                String sanitizedOpt = ignorePunct ? opt.replaceAll("\\p{Punct}", "") : opt;
                if (ignoreCase) sanitizedOpt = sanitizedOpt.toLowerCase();
                if (sanitizedInput.equals(sanitizedOpt)) return true;
            }
            return false;
        }

        public void onChat(net.minecraft.server.level.ServerPlayer player, String message) {
            if (waitType == WaitType.CHAT && waitChatPlayers != null && waitChatPlayers.matches(player.getUUID())) {
                if (waitChatMessages == null || waitChatMessages.isEmpty() || chatMatches(message, waitChatMessages, waitChatIgnoreCase, waitChatIgnorePunct)) {
                    chatEventMet = true;
                    chatMatchedMessage = message;
                }
            }
            for (AsyncTask task : asyncTasks.values()) {
                if (task.waitType == WaitType.CHAT && task.waitChatPlayers != null && task.waitChatPlayers.matches(player.getUUID())) {
                    if (task.waitChatMessages == null || task.waitChatMessages.isEmpty() || chatMatches(message, task.waitChatMessages, task.waitChatIgnoreCase, task.waitChatIgnorePunct)) {
                        task.chatEventMet = true;
                        task.chatMatchedMessage = message;
                    }
                }
            }

            for (var entry : snapshotEventHandlers()) {
                EventHandler handler = entry.getValue();
                if (!handler.active || !handler.eventName.equals("chat")) continue;

                boolean messageFiltered = false;
                if (!handler.eventArgs.isEmpty()) {
                    Object arg0 = handler.eventArgs.get(0);
                    if (arg0 != null) {
                        net.minecraft.world.entity.Entity targetEntity = resolveEntity(arg0);
                        if (targetEntity != null) {
                            if (!player.getUUID().equals(targetEntity.getUUID())) continue;
                        } else if (handler.eventArgs.size() == 1) {
                            List<String> options = List.of(String.valueOf(arg0));
                            if (!chatMatches(message, options, true, true)) continue;
                            messageFiltered = true;
                        } else {
                            continue;
                        }
                    }
                }

                if (!messageFiltered && handler.eventArgs.size() >= 2) {
                    Object msgsArg = handler.eventArgs.get(1);
                    List<String> options = new ArrayList<>();
                    if (msgsArg instanceof List list) {
                        for (Object o : list) options.add(String.valueOf(o));
                    } else {
                        options.add(String.valueOf(msgsArg));
                    }
                    
                    boolean ignoreCase = true;
                    boolean ignorePunct = true;
                    if (handler.eventArgs.size() >= 3) ignoreCase = (Boolean) handler.eventArgs.get(2);
                    if (handler.eventArgs.size() >= 4) ignorePunct = (Boolean) handler.eventArgs.get(3);

                    if (!options.isEmpty() && !chatMatches(message, options, ignoreCase, ignorePunct)) continue;
                }

                Object prevPlayer = variables.get("_eventPlayer");
                Object prevMsg = variables.get("_eventMessage");
                Object prevText = variables.get("_eventText");

                variables.put("_eventPlayer", player);
                variables.put("_eventMessage", message);
                variables.put("_eventText", message);

                try {
                    runEventHandlerBody(entry.getKey(), handler);
                } catch (ReturnException e) {
                    // return exits handler body only; use stop handler to deactivate
                } catch (Exception e) {
                    LOGGER.error("[Script: {}] Event handler '{}' error: {}", script.getName(), entry.getKey(), e.getMessage());
                }

                if (prevPlayer != null) variables.put("_eventPlayer", prevPlayer);
                else variables.remove("_eventPlayer");
                if (prevMsg != null) variables.put("_eventMessage", prevMsg);
                else variables.remove("_eventMessage");
                if (prevText != null) variables.put("_eventText", prevText);
                else variables.remove("_eventText");
            }
        }

        public void onOrbPickup(net.minecraft.server.level.ServerPlayer player, String texture, int amount) {
            if (waitType == WaitType.ORB_PICKUP && waitOrbPickupPlayers != null && waitOrbPickupPlayers.matches(player.getUUID())) {
                if (waitOrbPickupTexture == null || SprauteOrbTextures.matches(waitOrbPickupTexture, texture)) {
                    waitOrbPickupCurrentCount += amount;
                    if (waitOrbPickupCurrentCount >= waitOrbPickupTargetCount) {
                        if (pendingVarName != null) {
                            variables.put(pendingVarName, waitOrbPickupCurrentCount);
                            pendingVarName = null;
                        }
                        waitType = WaitType.NONE;
                    }
                }
            }
            
            for (AsyncTask task : asyncTasks.values()) {
                if (task.waitType == WaitType.ORB_PICKUP && task.waitOrbPickupPlayers != null && task.waitOrbPickupPlayers.matches(player.getUUID())) {
                    if (task.waitOrbPickupTexture == null || SprauteOrbTextures.matches(task.waitOrbPickupTexture, texture)) {
                        task.waitOrbPickupCurrentCount += amount;
                        if (task.waitOrbPickupCurrentCount >= task.waitOrbPickupTargetCount) {
                            if (task.pendingUiClickVarName != null) {
                                putVariable(task.pendingUiClickVarName, task.waitOrbPickupCurrentCount);
                                task.pendingUiClickVarName = null;
                            }
                            task.waitType = WaitType.NONE;
                            task.ip++;
                        }
                    }
                }
            }

            for (var entry : snapshotEventHandlers()) {
                EventHandler handler = entry.getValue();
                if (!handler.active || !handler.eventName.equals("orbPickup")) continue;

                if (!handler.eventArgs.isEmpty()) {
                    net.minecraft.world.entity.Entity targetEntity = resolveEntity(handler.eventArgs.get(0));
                    if (!matchesEventPlayerArg(handler.eventArgs.get(0), player.getUUID())) continue;
                }

                if (handler.eventArgs.size() >= 2) {
                    Object texArg = handler.eventArgs.get(1);
                    if (texArg instanceof String s && !SprauteOrbTextures.matches(s, texture)) continue;
                }

                Object prevPlayer = variables.get("_eventPlayer");
                Object prevTexture = variables.get("_eventTexture");
                Object prevAmount = variables.get("_eventAmount");

                variables.put("_eventPlayer", player);
                variables.put("_eventTexture", texture);
                variables.put("_eventAmount", amount);

                try {
                    runEventHandlerBody(entry.getKey(), handler);
                } catch (ReturnException e) {
                    // return exits handler body only; use stop handler to deactivate
                } catch (Exception e) {
                    LOGGER.error("[Script: {}] Event handler '{}' error: {}", script.getName(), entry.getKey(), e.getMessage());
                }

                if (prevPlayer != null) variables.put("_eventPlayer", prevPlayer); else variables.remove("_eventPlayer");
                if (prevTexture != null) variables.put("_eventTexture", prevTexture); else variables.remove("_eventTexture");
                if (prevAmount != null) variables.put("_eventAmount", prevAmount); else variables.remove("_eventAmount");
            }
        }

        public void onProjectileHit(String projectileId, String hitType, double x, double y, double z,
                                    net.minecraft.world.entity.Entity target, net.minecraft.world.entity.Entity owner) {
            handleProjectileHitAwait(projectileId, hitType, x, y, z, target, owner, false, null);
            fireProjectileHitEventHandlers(projectileId, hitType, x, y, z, target, owner);
        }

        private void handleProjectileHitAwait(String projectileId, String hitType, double x, double y, double z,
                                              net.minecraft.world.entity.Entity target, net.minecraft.world.entity.Entity owner,
                                              boolean async, AsyncTask task) {
            if (async) {
                if (task.waitType != WaitType.PROJECTILE_HIT) return;
                if (!matchesProjectileHitFilter(task.waitProjectileHitId, projectileId)) return;
                if (!matchesProjectileHitTypeFilter(task.waitProjectileHitType, hitType)) return;
                applyProjectileHitEventVars(projectileId, hitType, x, y, z, target, owner);
                task.waitType = WaitType.NONE;
                task.waitProjectileHitId = null;
                task.waitProjectileHitType = null;
                task.ip++;
                return;
            }
            if (waitType == WaitType.PROJECTILE_HIT) {
                if (!matchesProjectileHitFilter(waitProjectileHitId, projectileId)) return;
                if (!matchesProjectileHitTypeFilter(waitProjectileHitType, hitType)) return;
                applyProjectileHitEventVars(projectileId, hitType, x, y, z, target, owner);
                waitType = WaitType.NONE;
                waitProjectileHitId = null;
                waitProjectileHitType = null;
            }
            for (AsyncTask t : asyncTasks.values()) {
                handleProjectileHitAwait(projectileId, hitType, x, y, z, target, owner, true, t);
            }
        }

        private void fireProjectileHitEventHandlers(String projectileId, String hitType, double x, double y, double z,
                                                    net.minecraft.world.entity.Entity target, net.minecraft.world.entity.Entity owner) {
            for (var entry : snapshotEventHandlers()) {
                EventHandler handler = entry.getValue();
                if (!handler.active || !handler.eventName.equals("projectileHit")) continue;

                if (!handler.eventArgs.isEmpty()) {
                    if (!matchesProjectileHitFilter(handler.eventArgs.get(0), projectileId)) continue;
                }
                if (handler.eventArgs.size() >= 2) {
                    if (!matchesProjectileHitTypeFilter(handler.eventArgs.get(1), hitType)) continue;
                }

                Object prevProjectileId = variables.get("_eventProjectileId");
                Object prevHitType = variables.get("_eventHitType");
                Object prevHitX = variables.get("_eventHitX");
                Object prevHitY = variables.get("_eventHitY");
                Object prevHitZ = variables.get("_eventHitZ");
                Object prevTarget = variables.get("_eventTarget");
                Object prevTargetType = variables.get("_eventTargetType");
                Object prevOwner = variables.get("_eventOwner");

                applyProjectileHitEventVars(projectileId, hitType, x, y, z, target, owner);

                try {
                    runEventHandlerBody(entry.getKey(), handler);
                } catch (ReturnException e) {
                    // return exits handler body only; use stop handler to deactivate
                } catch (Exception e) {
                    LOGGER.error("[Script: {}] Event handler '{}' error: {}", script.getName(), entry.getKey(), e.getMessage());
                }

                if (prevProjectileId != null) variables.put("_eventProjectileId", prevProjectileId); else variables.remove("_eventProjectileId");
                if (prevHitType != null) variables.put("_eventHitType", prevHitType); else variables.remove("_eventHitType");
                if (prevHitX != null) variables.put("_eventHitX", prevHitX); else variables.remove("_eventHitX");
                if (prevHitY != null) variables.put("_eventHitY", prevHitY); else variables.remove("_eventHitY");
                if (prevHitZ != null) variables.put("_eventHitZ", prevHitZ); else variables.remove("_eventHitZ");
                restoreEventTargetVars(prevTarget, prevTargetType);
                if (prevOwner != null) variables.put("_eventOwner", prevOwner); else variables.remove("_eventOwner");
            }
        }

        private void applyProjectileHitEventVars(String projectileId, String hitType, double x, double y, double z,
                                                 net.minecraft.world.entity.Entity target, net.minecraft.world.entity.Entity owner) {
            variables.put("_eventProjectileId", projectileId);
            variables.put("_eventHitType", hitType);
            variables.put("_eventHitX", x);
            variables.put("_eventHitY", y);
            variables.put("_eventHitZ", z);
            putEventTargetVars(target);
            variables.put("_eventOwner", owner);
        }

        private boolean matchesProjectileHitFilter(Object filter, String projectileId) {
            if (filter == null) return true;
            String f = String.valueOf(filter);
            if ("any".equalsIgnoreCase(f)) return true;
            return f.equals(projectileId);
        }

        private boolean matchesProjectileHitTypeFilter(Object filter, String hitType) {
            if (filter == null) return true;
            String f = String.valueOf(filter);
            if ("any".equalsIgnoreCase(f)) return true;
            return f.equalsIgnoreCase(hitType);
        }

        public void onTradeBuy(net.minecraft.server.level.ServerPlayer player, String itemId, int price) {
            onCustomEvent("tradeBuy", java.util.List.of(player, itemId, price));
        }

        public void onTradeSell(net.minecraft.server.level.ServerPlayer player, String itemId, int price) {
            onCustomEvent("tradeSell", java.util.List.of(player, itemId, price));
        }

        /**
         * Script-registered custom events: {@code fireEvent("name", ...)} / {@code on name(...)} / {@code await name(...)}.
         * Also resumes legacy {@code WaitType.TRADE_BUY/SELL} when name is tradeBuy/tradeSell.
         */
        public void onCustomEvent(String eventName, java.util.List<Object> payload) {
            if (eventName == null || eventName.isBlank()) return;
            java.util.List<Object> args = payload != null ? payload : java.util.List.of();
            String key = ScriptCustomEvents.key(eventName);

            // Legacy trade awaits (compiled before AWAIT_CUSTOM)
            if ("tradebuy".equals(key) && args.size() >= 3 && args.get(0) instanceof net.minecraft.server.level.ServerPlayer sp) {
                handleTradeAwait(sp, String.valueOf(args.get(1)),
                        args.get(2) instanceof Number n ? n.intValue() : 0, true);
            } else if ("tradesell".equals(key) && args.size() >= 3 && args.get(0) instanceof net.minecraft.server.level.ServerPlayer sp) {
                handleTradeAwait(sp, String.valueOf(args.get(1)),
                        args.get(2) instanceof Number n ? n.intValue() : 0, false);
            }

            handleCustomEventAwait(eventName, args);
            fireCustomEventHandlers(eventName, args);
        }

        private void handleCustomEventAwait(String eventName, java.util.List<Object> payload) {
            String key = ScriptCustomEvents.key(eventName);
            Object awaitResult = customEventAwaitResult(payload);

            if (waitType == WaitType.CUSTOM_EVENT && waitCustomEventName != null
                    && ScriptCustomEvents.key(waitCustomEventName).equals(key)
                    && matchesCustomFilters(waitCustomFilters, payload)) {
                applyCustomEventVars(eventName, payload);
                if (pendingVarName != null) {
                    variables.put(pendingVarName, awaitResult);
                }
                waitType = WaitType.NONE;
                waitCustomEventName = null;
                waitCustomFilters = null;
                pendingVarName = null;
            }

            for (AsyncTask task : asyncTasks.values()) {
                if (task.waitType != WaitType.CUSTOM_EVENT || task.waitCustomEventName == null) continue;
                if (!ScriptCustomEvents.key(task.waitCustomEventName).equals(key)) continue;
                if (!matchesCustomFilters(task.waitCustomFilters, payload)) continue;
                applyCustomEventVars(eventName, payload);
                if (task.pendingUiClickVarName != null) {
                    putVariable(task.pendingUiClickVarName, awaitResult);
                }
                task.waitType = WaitType.NONE;
                task.waitCustomEventName = null;
                task.waitCustomFilters = null;
                task.pendingUiClickVarName = null;
                task.ip++;
            }
        }

        private static Object customEventAwaitResult(java.util.List<Object> payload) {
            if (payload == null || payload.isEmpty()) return null;
            // Common pattern: (player, data…) → return first data arg (e.g. itemId for tradeBuy)
            if (payload.size() >= 2 && payload.get(0) instanceof net.minecraft.world.entity.player.Player) {
                return payload.get(1);
            }
            return payload.size() == 1 ? payload.get(0) : new java.util.ArrayList<>(payload);
        }

        private void applyCustomEventVars(String eventName, java.util.List<Object> payload) {
            variables.put("_eventName", eventName);
            variables.put("_eventArgs", new java.util.ArrayList<>(payload));
            for (int i = 0; i < payload.size(); i++) {
                variables.put("_event" + i, payload.get(i));
            }
            if (!payload.isEmpty() && payload.get(0) instanceof net.minecraft.server.level.ServerPlayer sp) {
                variables.put("_eventPlayer", sp);
            }
            // Compat for tradeBuy/tradeSell handlers that used dedicated vars
            String key = ScriptCustomEvents.key(eventName);
            if (("tradebuy".equals(key) || "tradesell".equals(key)) && payload.size() >= 3) {
                variables.put("_eventItemId", String.valueOf(payload.get(1)));
                Object price = payload.get(2);
                variables.put("_eventPrice", price instanceof Number n ? n.intValue() : price);
            }
        }

        private boolean matchesCustomFilters(java.util.List<Object> filters, java.util.List<Object> payload) {
            if (filters == null || filters.isEmpty()) return true;
            for (int i = 0; i < filters.size(); i++) {
                Object filter = filters.get(i);
                if (filter == null) continue;
                String fs = String.valueOf(filter);
                if (fs.isEmpty() || "any".equalsIgnoreCase(fs)) continue;
                if (i >= payload.size()) return false;
                Object actual = payload.get(i);

                if (actual instanceof net.minecraft.server.level.ServerPlayer sp) {
                    if (!matchesEventPlayerArg(filter, sp.getUUID())) return false;
                    continue;
                }
                if (filter instanceof Number fn && actual instanceof Number an) {
                    if (Double.compare(fn.doubleValue(), an.doubleValue()) != 0) return false;
                    continue;
                }
                if (!fs.equals(String.valueOf(actual))) return false;
            }
            return true;
        }

        private void fireCustomEventHandlers(String eventName, java.util.List<Object> payload) {
            String key = ScriptCustomEvents.key(eventName);
            for (var entry : snapshotEventHandlers()) {
                EventHandler handler = entry.getValue();
                if (!handler.active) continue;
                if (!ScriptCustomEvents.key(handler.eventName).equals(key)) continue;
                if (!matchesCustomFilters(handler.eventArgs, payload)) continue;

                java.util.Map<String, Object> saved = new java.util.HashMap<>();
                saveEventVar(saved, "_eventName");
                saveEventVar(saved, "_eventArgs");
                saveEventVar(saved, "_eventPlayer");
                saveEventVar(saved, "_eventItemId");
                saveEventVar(saved, "_eventPrice");
                for (int i = 0; i < Math.max(payload.size(), 8); i++) {
                    saveEventVar(saved, "_event" + i);
                }

                applyCustomEventVars(eventName, payload);
                try {
                    runEventHandlerBody(entry.getKey(), handler);
                } catch (ReturnException e) {
                    // return exits handler body only
                } catch (Exception e) {
                    LOGGER.error("[Script: {}] Event handler '{}' error: {}", script.getName(), entry.getKey(), e.getMessage());
                }

                for (var e : saved.entrySet()) {
                    if (e.getValue() == SAVED_ABSENT) variables.remove(e.getKey());
                    else variables.put(e.getKey(), e.getValue());
                }
            }
        }

        private static final Object SAVED_ABSENT = new Object();

        private void saveEventVar(java.util.Map<String, Object> saved, String key) {
            if (variables.containsKey(key)) saved.put(key, variables.get(key));
            else saved.put(key, SAVED_ABSENT);
        }

        private boolean beginCustomEventAwait(String eventName, java.util.List<ScriptNode> filterNodes, String pendingVar) {
            java.util.List<Object> filters = new java.util.ArrayList<>();
            if (filterNodes != null) {
                for (ScriptNode n : filterNodes) {
                    filters.add(n != null ? evaluateExpression(n) : null);
                }
            }
            waitCustomEventName = eventName;
            waitCustomFilters = filters;
            waitType = WaitType.CUSTOM_EVENT;
            pendingVarName = pendingVar;
            maybeCompleteAssetSyncAwait(null);
            return waitType == WaitType.CUSTOM_EVENT;
        }

        private boolean beginTaskCustomEventAwait(AsyncTask task, String eventName,
                                               java.util.List<ScriptNode> filterNodes, String pendingVar) {
            java.util.List<Object> filters = new java.util.ArrayList<>();
            if (filterNodes != null) {
                for (ScriptNode n : filterNodes) {
                    filters.add(n != null ? evaluateExpression(n) : null);
                }
            }
            task.waitCustomEventName = eventName;
            task.waitCustomFilters = filters;
            task.waitType = WaitType.CUSTOM_EVENT;
            task.pendingUiClickVarName = pendingVar;
            maybeCompleteAssetSyncAwait(task);
            return task.waitType == WaitType.CUSTOM_EVENT;
        }

        private void maybeCompleteAssetSyncAwait(AsyncTask task) {
            String eventName = task != null ? task.waitCustomEventName : waitCustomEventName;
            java.util.List<Object> filters = task != null ? task.waitCustomFilters : waitCustomFilters;
            if (!org.zonarstudio.spraute_engine.resource.AssetSyncTracker.isEngineEvent(eventName)) return;
            net.minecraft.server.level.ServerPlayer player = playerFromAssetSyncFilters(filters);
            if (player == null) return;
            java.util.List<Object> payload =
                    org.zonarstudio.spraute_engine.resource.AssetSyncTracker.currentPayload(eventName, player);
            if (payload == null) return;
            if (!matchesCustomFilters(filters, payload)) return;
            applyCustomEventVars(eventName, payload);
            Object result = customEventAwaitResult(payload);
            if (task != null) {
                if (task.pendingUiClickVarName != null) {
                    putVariable(task.pendingUiClickVarName, result);
                    task.pendingUiClickVarName = null;
                }
                task.waitType = WaitType.NONE;
                task.waitCustomEventName = null;
                task.waitCustomFilters = null;
            } else {
                if (pendingVarName != null) {
                    variables.put(pendingVarName, result);
                    pendingVarName = null;
                }
                waitType = WaitType.NONE;
                waitCustomEventName = null;
                waitCustomFilters = null;
            }
        }

        private net.minecraft.server.level.ServerPlayer playerFromAssetSyncFilters(java.util.List<Object> filters) {
            if (filters != null) {
                for (Object f : filters) {
                    if (f == null) continue;
                    String fs = String.valueOf(f);
                    if (fs.isEmpty() || "any".equalsIgnoreCase(fs)) continue;
                    net.minecraft.world.entity.Entity ent = resolveEntity(f);
                    if (ent instanceof net.minecraft.server.level.ServerPlayer sp) return sp;
                }
            }
            if (source.getEntity() instanceof net.minecraft.server.level.ServerPlayer sp) return sp;
            return null;
        }

        public void onRelGift(net.minecraft.server.level.ServerPlayer player, String npcId, String itemId, int repDelta) {
            handleRelGiftAwait(player, npcId, itemId, repDelta);
            fireRelGiftEventHandlers(player, npcId, itemId, repDelta);
        }

        public void onRelRepChange(net.minecraft.server.level.ServerPlayer player, String npcId, int oldRep, int newRep) {
            handleRelRepChangeAwait(player, npcId);
            fireRelRepChangeEventHandlers(player, npcId, oldRep, newRep);
        }

        public void onRelTalk(net.minecraft.server.level.ServerPlayer player, String npcId) {
            handleRelTalkAwait(player, npcId);
            fireRelTalkEventHandlers(player, npcId);
        }

        public void onRelButton(net.minecraft.server.level.ServerPlayer player, String npcId, String btnId) {
            handleRelButtonAwait(player, npcId, btnId);
            fireRelButtonEventHandlers(player, npcId, btnId);
        }

        private static boolean matchesRelNpcHandlerArgs(java.util.List<Object> eventArgs, String npcId) {
            if (eventArgs.size() >= 2) {
                String arg2 = String.valueOf(eventArgs.get(1));
                if (!arg2.isEmpty() && !"any".equalsIgnoreCase(arg2) && !arg2.equals(npcId)) {
                    return false;
                }
            }
            return true;
        }

        private static boolean matchesRelButtonHandlerArgs(java.util.List<Object> eventArgs, String btnId) {
            if (eventArgs.size() >= 2) {
                String arg2 = String.valueOf(eventArgs.get(1));
                if (!arg2.isEmpty() && !"any".equalsIgnoreCase(arg2) && !arg2.equals(btnId)) {
                    return false;
                }
            }
            return true;
        }

        private void applyRelRepChangeEventVars(net.minecraft.server.level.ServerPlayer player, String npcId, int oldRep, int newRep) {
            variables.put("_eventPlayer", player);
            variables.put("_eventTarget", npcId);
            variables.put("rel_event_npc", npcId);
            variables.put("rel_event_old_rep", oldRep);
            variables.put("rel_event_new_rep", newRep);
        }

        private void applyRelTalkEventVars(net.minecraft.server.level.ServerPlayer player, String npcId) {
            variables.put("_eventPlayer", player);
            variables.put("_eventTarget", npcId);
            variables.put("rel_event_npc", npcId);
        }

        private void applyRelButtonEventVars(net.minecraft.server.level.ServerPlayer player, String npcId, String btnId) {
            variables.put("_eventPlayer", player);
            variables.put("_eventTarget", btnId);
            variables.put("rel_event_npc", npcId);
            variables.put("rel_event_button", btnId);
        }

        private void handleRelRepChangeAwait(net.minecraft.server.level.ServerPlayer player, String npcId) {
            if (waitType == WaitType.REL_REP_CHANGE && waitRelSimplePlayers != null && waitRelSimplePlayers.matches(player.getUUID())) {
                if (matchesRelNpcWait(npcId, waitRelSimpleNpcId)) {
                    waitType = WaitType.NONE;
                    waitRelSimplePlayers = null;
                    waitRelSimpleNpcId = null;
                    pendingVarName = null;
                }
            }
            for (AsyncTask task : asyncTasks.values()) {
                if (task.waitType != WaitType.REL_REP_CHANGE || task.waitRelSimplePlayers == null
                        || !task.waitRelSimplePlayers.matches(player.getUUID())) continue;
                if (!matchesRelNpcWait(npcId, task.waitRelSimpleNpcId)) continue;
                task.waitType = WaitType.NONE;
                task.waitRelSimplePlayers = null;
                task.waitRelSimpleNpcId = null;
                task.pendingUiClickVarName = null;
                task.ip++;
            }
        }

        private void handleRelTalkAwait(net.minecraft.server.level.ServerPlayer player, String npcId) {
            if (waitType == WaitType.REL_TALK && waitRelSimplePlayers != null && waitRelSimplePlayers.matches(player.getUUID())) {
                if (matchesRelNpcWait(npcId, waitRelSimpleNpcId)) {
                    waitType = WaitType.NONE;
                    waitRelSimplePlayers = null;
                    waitRelSimpleNpcId = null;
                    pendingVarName = null;
                }
            }
            for (AsyncTask task : asyncTasks.values()) {
                if (task.waitType != WaitType.REL_TALK || task.waitRelSimplePlayers == null
                        || !task.waitRelSimplePlayers.matches(player.getUUID())) continue;
                if (!matchesRelNpcWait(npcId, task.waitRelSimpleNpcId)) continue;
                task.waitType = WaitType.NONE;
                task.waitRelSimplePlayers = null;
                task.waitRelSimpleNpcId = null;
                task.pendingUiClickVarName = null;
                task.ip++;
            }
        }

        private void handleRelButtonAwait(net.minecraft.server.level.ServerPlayer player, String npcId, String btnId) {
            if (waitType == WaitType.REL_BUTTON && waitRelSimplePlayers != null && waitRelSimplePlayers.matches(player.getUUID())) {
                if (matchesRelNpcWait(npcId, waitRelSimpleNpcId) && matchesRelButtonWait(btnId, waitRelSimpleBtnId)) {
                    waitType = WaitType.NONE;
                    waitRelSimplePlayers = null;
                    waitRelSimpleNpcId = null;
                    waitRelSimpleBtnId = null;
                    pendingVarName = null;
                }
            }
            for (AsyncTask task : asyncTasks.values()) {
                if (task.waitType != WaitType.REL_BUTTON || task.waitRelSimplePlayers == null
                        || !task.waitRelSimplePlayers.matches(player.getUUID())) continue;
                if (!matchesRelNpcWait(npcId, task.waitRelSimpleNpcId)) continue;
                if (!matchesRelButtonWait(btnId, task.waitRelSimpleBtnId)) continue;
                task.waitType = WaitType.NONE;
                task.waitRelSimplePlayers = null;
                task.waitRelSimpleNpcId = null;
                task.waitRelSimpleBtnId = null;
                task.pendingUiClickVarName = null;
                task.ip++;
            }
        }

        private static boolean matchesRelNpcWait(String npcId, String waitNpcId) {
            if (waitNpcId != null && !waitNpcId.isEmpty() && !waitNpcId.equals(npcId)) return false;
            return true;
        }

        private static boolean matchesRelButtonWait(String btnId, String waitBtnId) {
            if (waitBtnId != null && !waitBtnId.isEmpty() && !waitBtnId.equals(btnId)) return false;
            return true;
        }

        private void fireRelRepChangeEventHandlers(net.minecraft.server.level.ServerPlayer player, String npcId, int oldRep, int newRep) {
            for (var entry : snapshotEventHandlers()) {
                EventHandler handler = entry.getValue();
                if (!handler.active || !handler.eventName.equals("relRepChange")) continue;
                if (!handler.eventArgs.isEmpty()) {
                    if (!matchesEventPlayerArg(handler.eventArgs.get(0), player.getUUID())) continue;
                }
                if (!matchesRelNpcHandlerArgs(handler.eventArgs, npcId)) continue;
                runRelEventHandler(entry, () -> applyRelRepChangeEventVars(player, npcId, oldRep, newRep));
            }
        }

        private void fireRelTalkEventHandlers(net.minecraft.server.level.ServerPlayer player, String npcId) {
            for (var entry : snapshotEventHandlers()) {
                EventHandler handler = entry.getValue();
                if (!handler.active || !handler.eventName.equals("relTalk")) continue;
                if (!handler.eventArgs.isEmpty()) {
                    if (!matchesEventPlayerArg(handler.eventArgs.get(0), player.getUUID())) continue;
                }
                if (!matchesRelNpcHandlerArgs(handler.eventArgs, npcId)) continue;
                runRelEventHandler(entry, () -> applyRelTalkEventVars(player, npcId));
            }
        }

        private void fireRelButtonEventHandlers(net.minecraft.server.level.ServerPlayer player, String npcId, String btnId) {
            for (var entry : snapshotEventHandlers()) {
                EventHandler handler = entry.getValue();
                if (!handler.active || !handler.eventName.equals("relButton")) continue;
                if (!handler.eventArgs.isEmpty()) {
                    if (!matchesEventPlayerArg(handler.eventArgs.get(0), player.getUUID())) continue;
                }
                if (!matchesRelButtonHandlerArgs(handler.eventArgs, btnId)) continue;
                runRelEventHandler(entry, () -> applyRelButtonEventVars(player, npcId, btnId));
            }
        }

        private void runRelEventHandler(java.util.Map.Entry<String, EventHandler> entry, Runnable applyVars) {
            Object prevPlayer = variables.get("_eventPlayer");
            Object prevTarget = variables.get("_eventTarget");
            Object prevNpc = variables.get("rel_event_npc");
            Object prevBtn = variables.get("rel_event_button");
            Object prevOld = variables.get("rel_event_old_rep");
            Object prevNew = variables.get("rel_event_new_rep");
            applyVars.run();
            try {
                runEventHandlerBody(entry.getKey(), entry.getValue());
            } catch (ReturnException e) {
                // return exits handler body only
            } catch (Exception e) {
                LOGGER.error("[Script: {}] Event handler '{}' error: {}", script.getName(), entry.getKey(), e.getMessage());
            }
            if (prevPlayer != null) variables.put("_eventPlayer", prevPlayer); else variables.remove("_eventPlayer");
            if (prevTarget != null) variables.put("_eventTarget", prevTarget); else variables.remove("_eventTarget");
            if (prevNpc != null) variables.put("rel_event_npc", prevNpc); else variables.remove("rel_event_npc");
            if (prevBtn != null) variables.put("rel_event_button", prevBtn); else variables.remove("rel_event_button");
            if (prevOld != null) variables.put("rel_event_old_rep", prevOld); else variables.remove("rel_event_old_rep");
            if (prevNew != null) variables.put("rel_event_new_rep", prevNew); else variables.remove("rel_event_new_rep");
        }

        private static boolean matchesRelGiftHandlerArgs(java.util.List<Object> eventArgs, String npcId, String itemId) {
            if (eventArgs.size() >= 2) {
                String arg2 = String.valueOf(eventArgs.get(1));
                if (!arg2.isEmpty() && !"any".equalsIgnoreCase(arg2)) {
                    if (eventArgs.size() >= 3) {
                        String arg3 = String.valueOf(eventArgs.get(2));
                        if (!arg3.isEmpty() && !"any".equalsIgnoreCase(arg3)) {
                            return arg2.equals(npcId) && arg3.equals(itemId);
                        }
                    }
                    if (arg2.contains(":")) {
                        return arg2.equals(itemId);
                    }
                    return arg2.equals(npcId);
                }
            }
            return true;
        }

        private static void applyRelGiftWaitFilters(String filter2, String filter3, String[] outNpc, String[] outItem) {
            outNpc[0] = null;
            outItem[0] = null;
            if (filter2 != null && !filter2.isEmpty() && !"any".equalsIgnoreCase(filter2)) {
                if (filter3 != null && !filter3.isEmpty() && !"any".equalsIgnoreCase(filter3)) {
                    outNpc[0] = filter2;
                    outItem[0] = filter3;
                } else if (filter2.contains(":")) {
                    outItem[0] = filter2;
                } else {
                    outNpc[0] = filter2;
                }
            }
        }

        private static boolean matchesRelGiftWait(String npcId, String itemId, String waitNpcId, String waitItemId) {
            if (waitNpcId != null && !waitNpcId.isEmpty() && !waitNpcId.equals(npcId)) return false;
            if (waitItemId != null && !waitItemId.isEmpty() && !waitItemId.equals(itemId)) return false;
            return true;
        }

        private void handleRelGiftAwait(net.minecraft.server.level.ServerPlayer player, String npcId, String itemId, int repDelta) {
            if (waitType == WaitType.REL_GIFT && waitRelGiftPlayers != null && waitRelGiftPlayers.matches(player.getUUID())) {
                if (matchesRelGiftWait(npcId, itemId, waitRelGiftNpcId, waitRelGiftItemId)) {
                    applyRelGiftEventVars(player, npcId, itemId, repDelta);
                    if (pendingVarName != null) {
                        variables.put(pendingVarName, itemId);
                    }
                    waitType = WaitType.NONE;
                    waitRelGiftPlayers = null;
                    waitRelGiftNpcId = null;
                    waitRelGiftItemId = null;
                    pendingVarName = null;
                }
            }
            for (AsyncTask task : asyncTasks.values()) {
                if (task.waitType != WaitType.REL_GIFT || task.waitRelGiftPlayers == null
                        || !task.waitRelGiftPlayers.matches(player.getUUID())) continue;
                if (!matchesRelGiftWait(npcId, itemId, task.waitRelGiftNpcId, task.waitRelGiftItemId)) continue;
                applyRelGiftEventVars(player, npcId, itemId, repDelta);
                if (task.pendingUiClickVarName != null) {
                    putVariable(task.pendingUiClickVarName, itemId);
                }
                task.waitType = WaitType.NONE;
                task.waitRelGiftPlayers = null;
                task.waitRelGiftNpcId = null;
                task.waitRelGiftItemId = null;
                task.pendingUiClickVarName = null;
                task.ip++;
            }
        }

        private void applyRelGiftEventVars(net.minecraft.server.level.ServerPlayer player, String npcId, String itemId, int repDelta) {
            variables.put("_eventPlayer", player);
            variables.put("_eventTarget", npcId);
            variables.put("_eventItemId", itemId);
            variables.put("rel_event_npc", npcId);
            variables.put("rel_event_item", itemId);
            variables.put("rel_event_rep", repDelta);
        }

        private void fireRelGiftEventHandlers(net.minecraft.server.level.ServerPlayer player, String npcId, String itemId, int repDelta) {
            for (var entry : snapshotEventHandlers()) {
                EventHandler handler = entry.getValue();
                if (!handler.active || !handler.eventName.equals("relGift")) continue;

                if (!handler.eventArgs.isEmpty()) {
                    if (!matchesEventPlayerArg(handler.eventArgs.get(0), player.getUUID())) continue;
                }
                if (!matchesRelGiftHandlerArgs(handler.eventArgs, npcId, itemId)) continue;

                Object prevPlayer = variables.get("_eventPlayer");
                Object prevTarget = variables.get("_eventTarget");
                Object prevItem = variables.get("_eventItemId");
                Object prevNpc = variables.get("rel_event_npc");
                Object prevRelItem = variables.get("rel_event_item");
                Object prevRep = variables.get("rel_event_rep");

                applyRelGiftEventVars(player, npcId, itemId, repDelta);

                try {
                    runEventHandlerBody(entry.getKey(), handler);
                } catch (ReturnException e) {
                    // return exits handler body only; use stop handler to deactivate
                } catch (Exception e) {
                    LOGGER.error("[Script: {}] Event handler '{}' error: {}", script.getName(), entry.getKey(), e.getMessage());
                }

                if (prevPlayer != null) variables.put("_eventPlayer", prevPlayer); else variables.remove("_eventPlayer");
                if (prevTarget != null) variables.put("_eventTarget", prevTarget); else variables.remove("_eventTarget");
                if (prevItem != null) variables.put("_eventItemId", prevItem); else variables.remove("_eventItemId");
                if (prevNpc != null) variables.put("rel_event_npc", prevNpc); else variables.remove("rel_event_npc");
                if (prevRelItem != null) variables.put("rel_event_item", prevRelItem); else variables.remove("rel_event_item");
                if (prevRep != null) variables.put("rel_event_rep", prevRep); else variables.remove("rel_event_rep");
            }
        }

        private void handleTradeAwait(net.minecraft.server.level.ServerPlayer player, String itemId, int price, boolean buy) {
            WaitType expected = buy ? WaitType.TRADE_BUY : WaitType.TRADE_SELL;
            if (waitType == expected && waitTradePlayers != null && waitTradePlayers.matches(player.getUUID())) {
                if (waitTradeItemId == null || waitTradeItemId.isEmpty() || waitTradeItemId.equals(itemId)) {
                    if (pendingVarName != null) {
                        variables.put(pendingVarName, itemId);
                    }
                    variables.put("_eventItemId", itemId);
                    variables.put("_eventPrice", price);
                    waitType = WaitType.NONE;
                    waitTradePlayers = null;
                    waitTradeItemId = null;
                    pendingVarName = null;
                }
            }
            for (AsyncTask task : asyncTasks.values()) {
                if (task.waitType != expected || task.waitTradePlayers == null
                        || !task.waitTradePlayers.matches(player.getUUID())) continue;
                if (task.waitTradeItemId != null && !task.waitTradeItemId.isEmpty() && !task.waitTradeItemId.equals(itemId)) continue;
                if (task.pendingUiClickVarName != null) {
                    putVariable(task.pendingUiClickVarName, itemId);
                }
                variables.put("_eventItemId", itemId);
                variables.put("_eventPrice", price);
                task.waitType = WaitType.NONE;
                task.waitTradePlayers = null;
                task.waitTradeItemId = null;
                task.pendingUiClickVarName = null;
                task.ip++;
            }
        }

        private void fireTradeEventHandlers(net.minecraft.server.level.ServerPlayer player, String itemId, int price, String eventName) {
            for (var entry : snapshotEventHandlers()) {
                EventHandler handler = entry.getValue();
                if (!handler.active || !handler.eventName.equals(eventName)) continue;

                if (!handler.eventArgs.isEmpty()) {
                    net.minecraft.world.entity.Entity targetEntity = resolveEntity(handler.eventArgs.get(0));
                    if (!matchesEventPlayerArg(handler.eventArgs.get(0), player.getUUID())) continue;
                }
                if (handler.eventArgs.size() >= 2) {
                    String expectedItem = String.valueOf(handler.eventArgs.get(1));
                    if (!expectedItem.equals(itemId)) continue;
                }

                Object prevPlayer = variables.get("_eventPlayer");
                Object prevItem = variables.get("_eventItemId");
                Object prevPrice = variables.get("_eventPrice");

                variables.put("_eventPlayer", player);
                variables.put("_eventItemId", itemId);
                variables.put("_eventPrice", price);

                try {
                    runEventHandlerBody(entry.getKey(), handler);
                } catch (ReturnException e) {
                    // return exits handler body only; use stop handler to deactivate
                } catch (Exception e) {
                    LOGGER.error("[Script: {}] Event handler '{}' error: {}", script.getName(), entry.getKey(), e.getMessage());
                }

                if (prevPlayer != null) variables.put("_eventPlayer", prevPlayer); else variables.remove("_eventPlayer");
                if (prevItem != null) variables.put("_eventItemId", prevItem); else variables.remove("_eventItemId");
                if (prevPrice != null) variables.put("_eventPrice", prevPrice); else variables.remove("_eventPrice");
            }
        }

        public boolean onPlayerAction(net.minecraft.world.entity.player.Player player, String actionType, Object target) {
            if (waitType == WaitType.PLAYER_ACTION && waitPlayerActionPlayers != null && waitPlayerActionPlayers.matches(player.getUUID())) {
                if (waitPlayerActionType.equalsIgnoreCase(actionType)) {
                    if (waitPlayerActionTarget == null || (target != null && matchesActionTarget(target, waitPlayerActionTarget))) {
                        playerActionMet = true;
                        asyncResult = target;
                    }
                }
            }

            boolean canceled = false;

            for (var entry : snapshotEventHandlers()) {
                EventHandler handler = entry.getValue();
                if (!handler.active) continue;
                String en = handler.eventName;
                if (!en.equals("action") && !en.equals("playerAction")) continue;

                boolean matched = false;
                if (handler.eventArgs.isEmpty()) {
                    matched = true;
                } else if (handler.eventArgs.size() == 1) {
                    Object arg0 = handler.eventArgs.get(0);
                    net.minecraft.world.entity.Entity playerEnt = resolveEntity(arg0);
                    if (playerEnt != null) {
                        matched = matchesEventPlayerArg(arg0, player.getUUID());
                    } else {
                        matched = String.valueOf(arg0).equalsIgnoreCase(actionType);
                    }
                } else {
                    net.minecraft.world.entity.Entity playerEnt = resolveEntity(handler.eventArgs.get(0));
                    if (matchesEventPlayerArg(handler.eventArgs.get(0), player.getUUID())) {
                        // form: on action(player, "actionType") or on action(player, "actionType", "target")
                        String expectedAction = String.valueOf(handler.eventArgs.get(1));
                        if (expectedAction.equalsIgnoreCase(actionType)) {
                            if (handler.eventArgs.size() <= 2) {
                                matched = true;
                            } else {
                                String expectedTarget = String.valueOf(handler.eventArgs.get(2));
                                matched = matchesActionTarget(target, expectedTarget);
                            }
                        }
                    } else if (playerEnt == null) {
                        // form: on action("actionType", "target")
                        String expectedAction = String.valueOf(handler.eventArgs.get(0));
                        if (expectedAction.equalsIgnoreCase(actionType)) {
                            String expectedTarget = String.valueOf(handler.eventArgs.get(1));
                            matched = matchesActionTarget(target, expectedTarget);
                        }
                    }
                }
                if (!matched) continue;

                Object prevPlayer = variables.get("_eventPlayer");
                Object prevTarget = variables.get("_eventTarget");
                Object prevTargetType = variables.get("_eventTargetType");
                Object prevItemId = variables.get("_eventItemId");
                boolean prevCanceled = context.isEventCanceled();
                variables.put("_eventPlayer", player);
                putEventTargetVars(target);
                context.setEventCanceled(false);
                if (target instanceof net.minecraft.world.item.Item item) {
                    variables.put("_eventItemId", net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(item).toString());
                } else {
                    variables.remove("_eventItemId");
                }

                try {
                    runEventHandlerBody(entry.getKey(), handler);
                } catch (ReturnException e) {
                    // return exits handler body only; use stop handler to deactivate
                } catch (Exception e) {
                    LOGGER.error("[Script: {}] Action handler error: {}", script.getName(), e.getMessage());
                }

                if (context.isEventCanceled() || Boolean.TRUE.equals(variables.get("_eventCanceled"))) {
                    canceled = true;
                }

                if (prevPlayer != null) variables.put("_eventPlayer", prevPlayer);
                else variables.remove("_eventPlayer");
                restoreEventTargetVars(prevTarget, prevTargetType);
                if (prevItemId != null) variables.put("_eventItemId", prevItemId);
                else variables.remove("_eventItemId");
                context.setEventCanceled(prevCanceled);
            }

            // Dedicated named events: on jump / on attack / on sprint
            if ("jump".equalsIgnoreCase(actionType)) {
                if (fireSimplePlayerEvent("jump", player, target)) canceled = true;
            } else if ("attack".equalsIgnoreCase(actionType)) {
                if (fireSimplePlayerEvent("attack", player, target)) canceled = true;
            } else if ("sprint".equalsIgnoreCase(actionType)) {
                if (fireSimplePlayerEvent("sprint", player, target)) canceled = true;
            }

            for (AsyncTask task : asyncTasks.values()) {
                if (task.waitType == WaitType.PLAYER_ACTION && task.waitPlayerActionPlayers != null && task.waitPlayerActionPlayers.matches(player.getUUID())) {
                    if (task.waitPlayerActionType.equalsIgnoreCase(actionType)) {
                        if (task.waitPlayerActionTarget == null || (target != null && matchesActionTarget(target, task.waitPlayerActionTarget))) {
                            task.playerActionMet = true;
                            asyncResult = target;
                        }
                    }
                }
            }
            return canceled;
        }

        /** Runs handlers named {@code eventName} (e.g. jump/attack/sprint) with cancelEvent support. */
        private boolean fireSimplePlayerEvent(String eventName, net.minecraft.world.entity.player.Player player, Object target) {
            boolean canceled = false;
            for (var entry : snapshotEventHandlers()) {
                EventHandler handler = entry.getValue();
                if (!handler.active || !handler.eventName.equals(eventName)) continue;

                if (!handler.eventArgs.isEmpty()) {
                    if (!matchesEventPlayerArg(handler.eventArgs.get(0), player.getUUID())) continue;
                }

                Object prevPlayer = variables.get("_eventPlayer");
                Object prevTarget = variables.get("_eventTarget");
                Object prevTargetType = variables.get("_eventTargetType");
                boolean prevCanceled = context.isEventCanceled();
                variables.put("_eventPlayer", player);
                putEventTargetVars(target);
                context.setEventCanceled(false);

                try {
                    runEventHandlerBody(entry.getKey(), handler);
                } catch (ReturnException e) {
                    // return exits handler body only; use stop handler to deactivate
                } catch (Exception e) {
                    LOGGER.error("[Script: {}] {} handler error: {}", script.getName(), eventName, e.getMessage());
                }

                if (context.isEventCanceled() || Boolean.TRUE.equals(variables.get("_eventCanceled"))) {
                    canceled = true;
                }

                if (prevPlayer != null) variables.put("_eventPlayer", prevPlayer);
                else variables.remove("_eventPlayer");
                restoreEventTargetVars(prevTarget, prevTargetType);
                context.setEventCanceled(prevCanceled);
            }
            return canceled;
        }
        
        private boolean matchesActionTarget(Object target, String expected) {
            if (target == null) return false;
            if (target instanceof net.minecraft.world.item.Item item) {
                String regName = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(item).toString();
                return regName.equals(expected)
                        || regName.replace("minecraft:", "").equals(expected)
                        || regName.replace("spraute_engine:", "").equals(expected);
            }
            if (target instanceof net.minecraft.world.level.block.Block block) {
                String regName = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(block).toString();
                return regName.equals(expected) || regName.replace("minecraft:", "").equals(expected);
            }
            if (target instanceof net.minecraft.world.entity.Entity entity) {
                String name = entity.getName().getString();
                String typeId = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(entity.getType()).toString();
                return expected.equalsIgnoreCase(name)
                        || expected.equalsIgnoreCase(typeId)
                        || expected.equalsIgnoreCase(typeId.replace("minecraft:", ""))
                        || expected.equalsIgnoreCase(entity.getUUID().toString());
            }
            return String.valueOf(target).equals(expected);
        }

        public boolean isFinished() { return finished; }
        public String getScriptName() { return script.getName(); }
        public String getInstanceId() { return instanceId; }
        public net.minecraft.commands.CommandSourceStack getSource() { return source; }
        Map<String, UserFunction> getUserFunctions() { return userFunctions; }

        /**
         * Resolve a user function by name: first in own userFunctions, then lazily
         * from running imported scripts.
         */
        private UserFunction resolveFunction(String name) {
            UserFunction f = userFunctions.get(name);
            if (f != null) return f;
            ActiveScript donor = findDonorForFunction(name);
            if (donor != null) {
                return donor.userFunctions.get(name);
            }
            return null;
        }

        private List<Object> evaluateCallArgs(List<ScriptNode> argNodes) {
            List<Object> args = new ArrayList<>();
            for (ScriptNode argNode : argNodes) {
                args.add(evaluateExpression(argNode));
            }
            return args;
        }

        private boolean beginTaskUserFunction(AsyncTask task, UserFunction func, List<Object> args, String returnVar, String returnScope) {
            task.enterUserFunction(func, args, returnVar, returnScope, variables);
            return true;
        }

        /**
         * Coerce an `await time(x)` argument to seconds. Never returns a "skip" — a broken
         * (non-numeric) value falls back to a safe default so a chat/overlay never disappears
         * within a single frame because the wait was silently skipped.
         */
        private double coerceSeconds(Object sec, String taskId) {
            if (sec instanceof Number n) return n.doubleValue();
            if (sec instanceof String s) {
                try { return Double.parseDouble(s.trim()); } catch (NumberFormatException ignored) {}
            }
            LOGGER.warn("[Script: {}] await time() (task '{}') expected number, got {} — using 5s fallback",
                    script.getName(), taskId, sec);
            return 5.0;
        }

        private Object getVariable(String name) {
            if (currentTaskScope != null && currentTaskScope.taskLocals.containsKey(name)) {
                return currentTaskScope.taskLocals.get(name);
            }
            if (variables.containsKey(name)) return variables.get(name);
            if (globalVariables.containsKey(name)) return globalVariables.get(name);
            net.minecraft.server.level.ServerLevel level = source.getLevel();
            if (level != null) {
                ScriptWorldData world = ScriptWorldData.get(level);
                if (world.has(name)) return world.get(name, source.getServer(), level);
            }
            return null;
        }

        private void putVariable(String name, Object value) {
            if (currentTaskScope != null && currentTaskScope.taskLocals.containsKey(name)) {
                currentTaskScope.taskLocals.put(name, value);
            }
            if (variables.containsKey(name)) { variables.put(name, value); return; }
            if (globalVariables.containsKey(name)) { globalVariables.put(name, value); return; }
            net.minecraft.server.level.ServerLevel level = source.getLevel();
            if (level != null) {
                ScriptWorldData world = ScriptWorldData.get(level);
                if (world.has(name)) { world.put(name, value); return; }
            }
            // New variables always go to variables (not task locals),
            // so they don't accidentally pollute the parent task scope.
            variables.put(name, value);
        }

        private void putVariable(String name, Object value, String scope) {
            if ("global".equals(scope)) {
                globalVariables.put(name, value);
                return;
            }
            if ("world".equals(scope)) {
                net.minecraft.server.level.ServerLevel level = source.getLevel();
                if (level != null) ScriptWorldData.get(level).put(name, value);
                return;
            }
            if (currentTaskScope != null) {
                currentTaskScope.taskLocals.put(name, value);
            } else {
                variables.put(name, value);
            }
        }

        /**
         * Execute a block of instructions (for handlers/functions). Synchronous, no pausing.
         */
        private void executeInstructionBlock(List<CompiledScript.Instruction> instructions) {
            CURRENT_ACTIVE_STACK.get().push(this);
            try {
                executeInstructionBlockInner(instructions);
            } finally {
                Deque<ActiveScript> stack = CURRENT_ACTIVE_STACK.get();
                if (!stack.isEmpty() && stack.peek() == this) stack.pop();
            }
        }

        private void executeInstructionBlockInner(List<CompiledScript.Instruction> instructions) {
            java.util.Stack<TryBlock> localTryStack = new java.util.Stack<>();
            for (int i = 0; i < instructions.size(); i++) {
                CompiledScript.Instruction instr = instructions.get(i);
                try {
                    if (instr.getOpcode() == CompiledScript.Opcode.RETURN) {
                        ScriptNode valueNode = (ScriptNode) instr.getArg(0);
                        Object result = valueNode != null ? evaluateExpression(valueNode) : null;
                        throw new ReturnException(result);
                    }
                    if (instr.getOpcode() == CompiledScript.Opcode.JUMP) {
                        int target = (Integer) instr.getArg(0);
                        i = target - 1;
                        continue;
                    }
                    if (instr.getOpcode() == CompiledScript.Opcode.JUMP_IF_FALSE) {
                        ScriptNode condNode = (ScriptNode) instr.getArg(0);
                        int target = (Integer) instr.getArg(1);
                        if (!isTruthy(evaluateExpression(condNode))) {
                            i = target - 1;
                        }
                        continue;
                    }
                    if (instr.getOpcode() == CompiledScript.Opcode.TRY_START) {
                        int catchIp = (Integer) instr.getArg(0);
                        String catchVar = (String) instr.getArg(1);
                        localTryStack.push(new TryBlock(catchIp, catchVar));
                        continue;
                    }
                    if (instr.getOpcode() == CompiledScript.Opcode.TRY_END) {
                        if (!localTryStack.isEmpty()) localTryStack.pop();
                        continue;
                    }
                    executeStatementInstruction(instr);
                } catch (ReturnException e) {
                    throw e;
                } catch (Exception e) {
                    if (!localTryStack.isEmpty()) {
                        TryBlock tb = localTryStack.pop();
                        i = tb.catchIp - 1; // -1 because loop increments
                        if (tb.catchVar != null) {
                            putVariable(tb.catchVar, e.getMessage() != null ? e.getMessage() : e.toString());
                        }
                    } else {
                        throw new ScriptException(e.getMessage() != null ? e.getMessage() : e.toString(), instr.getLine());
                    }
                }
            }
        }

        /**
         * Execute a single non-flow-control instruction (shared between main loop and sub-blocks).
         */
        private void executeStatementInstruction(CompiledScript.Instruction instruction) {
            switch (instruction.getOpcode()) {
                case VAR_ASSIGN -> {
                    String name = (String) instruction.getArg(0);
                    ScriptNode valueNode = (ScriptNode) instruction.getArg(1);
                    putVariable(name, evaluateExpression(valueNode));
                }
                case VAR_DECL -> {
                    String name = (String) instruction.getArg(0);
                    ScriptNode initializer = (ScriptNode) instruction.getArg(1);
                    String scope = instruction.getArgCount() >= 3 ? String.valueOf(instruction.getArg(2)) : "local";
                    if (initializer instanceof ScriptNode.AwaitNode) {
                        LOGGER.error("[Script: {}] await in event handler/timer runs synchronously and will not wait (line {}). Wrap the body in async {{ ... }}.",
                                script.getName(), instruction.getLine());
                        putVariable(name, null, scope);
                        break;
                    }
                    if ("world".equals(scope)) {
                        net.minecraft.server.level.ServerLevel lvl = source.getLevel();
                        if (lvl == null || !ScriptWorldData.get(lvl).has(name)) {
                            putVariable(name, evaluateExpression(initializer), scope);
                        }
                    } else {
                        putVariable(name, evaluateExpression(initializer), scope);
                    }
                }
                case CALL -> executeCall(instruction);
                case CALL_METHOD -> executeCallMethod(instruction, false, null);
                case NPC_BLOCK -> executeNpcBlock(instruction);
                case MODEL_BLOCK -> executeModelBlock(instruction);
                case HITBOX_BLOCK -> executeHitboxBlock(instruction);
                case EMPTY_BLOCK -> executeEmptyBlock(instruction);
                case SKIN_OVERLAY_BLOCK -> executeSkinOverlayBlock(instruction);
                case PARTICLE_EMITTER_BLOCK -> executeParticleEmitterBlock(instruction);
                case PARTICLE_DEF_BLOCK -> executeParticleDefBlock(instruction);
                case PROJECTILE_DEF_BLOCK -> executeProjectileDefBlock(instruction);
                case LIGHT_BLOCK -> executeLightBlock(instruction);
                case AUDIO_SOURCE_BLOCK -> executeAudioSourceBlock(instruction);
                case POST_PROCESS_BLOCK -> executePostProcessBlock(instruction);
                case CUSTOM_NODE_BLOCK -> executeCustomNodeBlock(instruction);
                case NPC_PREFAB_DEF -> executeNpcPrefabDef(instruction);
                case CAMERA -> executeCamera(instruction);
                case UI_BLOCK -> executeUiBlock(instruction);
                case COMMAND_BLOCK -> executeCommandBlock(instruction);
                case SET_PROPERTY -> executeSetProperty(instruction);
                case SET_INDEX -> executeSetIndex(instruction);
                case FUN_DEF -> {
                    String name = (String) instruction.getArg(0);
                    List<String> params = (List<String>) instruction.getArg(1);
                    List<CompiledScript.Instruction> bodyInstr = (List<CompiledScript.Instruction>) instruction.getArg(2);
                    userFunctions.put(name, new UserFunction(params, bodyInstr));
                }
                case INCLUDE -> {
                    String includeName = (String) instruction.getArg(0);
                    if (!importedScripts.contains(includeName)) {
                        importedScripts.add(includeName);
                        ScriptExecutor.this.ensureImportedScriptRunning(includeName, source, script.getName());
                        LOGGER.info("[Script: {}] import '{}' registered (functions resolved lazily)", script.getName(), includeName);
                    }
                }
                case REGISTER_ON -> {
                    String eventName = (String) instruction.getArg(0);
                    List<ScriptNode> eventArgNodes = (List<ScriptNode>) instruction.getArg(1);
                    String handlerId = (String) instruction.getArg(2);
                    List<CompiledScript.Instruction> bodyInstr = (List<CompiledScript.Instruction>) instruction.getArg(3);

                    List<Object> evaluatedArgs = new ArrayList<>();
                    for (ScriptNode node : eventArgNodes) {
                        evaluatedArgs.add(evaluateEventArg(eventName, node));
                    }
                    eventHandlers.put(handlerId, new EventHandler(eventName, evaluatedArgs, bodyInstr));
                    seedPickupHandlerBaseline(handlerId, eventName, evaluatedArgs);
                }
                case REGISTER_EVERY -> {
                    ScriptNode intervalNode = (ScriptNode) instruction.getArg(0);
                    String handlerId = (String) instruction.getArg(1);
                    List<CompiledScript.Instruction> bodyInstr = (List<CompiledScript.Instruction>) instruction.getArg(2);

                    double interval = ((Number) evaluateExpression(intervalNode)).doubleValue();
                    timerHandlers.put(handlerId, new TimerHandler(interval, bodyInstr));
                }
                case STOP_HANDLER -> {
                    String handlerId = (String) instruction.getArg(0);
                    if (eventHandlers.containsKey(handlerId)) {
                        eventHandlers.get(handlerId).active = false;
                    }
                    if (timerHandlers.containsKey(handlerId)) {
                        timerHandlers.get(handlerId).active = false;
                    }
                }
                case ASYNC_START -> {
                    @SuppressWarnings("unchecked")
                    List<CompiledScript.Instruction> bodyInstr = (List<CompiledScript.Instruction>) instruction.getArg(1);
                    String id = resolveAsyncTaskId(instruction.getArg(0));
                    asyncTasks.put(id, new AsyncTask(id, bodyInstr));
                }
                case STOP_TASK -> {
                    ScriptNode idNode = (ScriptNode) instruction.getArg(0);
                    String id = String.valueOf(evaluateExpression(idNode));
                    AsyncTask t = asyncTasks.get(id);
                    if (t != null) {
                        t.cancelled = true;
                        asyncTasks.remove(id);
                    }
                }
                case UI_WIDGET -> executeUiWidget(instruction);
                default -> {}
            }
        }

        /**
         * Returns true if execution should pause (async wait).
         */
        private boolean executeInstruction(CompiledScript.Instruction instruction) {
            return executeInstruction(instruction, this.tryStack, null);
        }

        private boolean executeInstruction(CompiledScript.Instruction instruction, java.util.Stack<TryBlock> currentTryStack, AsyncTask taskScope) {
            switch (instruction.getOpcode()) {
                case JUMP -> {
                    int targetIndex = (Integer) instruction.getArg(0);
                    ip = targetIndex - 1;
                }
                case JUMP_IF_FALSE -> {
                    ScriptNode conditionNode = (ScriptNode) instruction.getArg(0);
                    int targetIndex = (Integer) instruction.getArg(1);
                    
                    if (!isTruthy(evaluateExpression(conditionNode))) {
                        ip = targetIndex - 1;
                    }
                }
                case VAR_ASSIGN -> {
                    String name = (String) instruction.getArg(0);
                    ScriptNode valueNode = (ScriptNode) instruction.getArg(1);
                    if (asyncResult == null && valueNode instanceof ScriptNode.AwaitNode awaitNode) {
                         ScriptNode.FunctionCallNode call = awaitNode.getCall();
                         if (call == null) {
                             // Method awaits compile to CALL_METHOD; nothing to do here.
                         } else if (call.getFunctionName().equals("interact")) {
                             ScriptNode entityIdNode = call.getArgs().get(0);
                             Object val = evaluateExpression(entityIdNode);
                             net.minecraft.world.entity.Entity targetEntity = resolveEntity(val);
                             if (targetEntity != null) {
                                 waitEntityUuid = targetEntity.getUUID();
                                 waitType = WaitType.INTERACT;
                                 pendingVarName = name;
                                 return true;
                             }
                         } else if (call.getFunctionName().equals("death")) {
                             ScriptNode targetNode = call.getArgs().get(0);
                             Object val = evaluateExpression(targetNode);
                             waitDeathTarget = normalizeEntityWaitTarget(val);
                             waitType = WaitType.DEATH;
                             pendingVarName = name;
                             return true;
                         } else if (call.getFunctionName().equals("kill")) {
                             if (call.getArgs().isEmpty()) return false;
                             waitKillKillerTarget = normalizeEntityWaitTarget(evaluateExpression(call.getArgs().get(0)));
                             waitKillVictimTarget = call.getArgs().size() > 1
                                     ? normalizeEntityWaitTarget(evaluateExpression(call.getArgs().get(1))) : "any";
                             killMet = false;
                             waitType = WaitType.KILL;
                             pendingVarName = name;
                             return true;
                         } else if (call.getFunctionName().equals("keybind")) {
                             ScriptNode keyNode = call.getArgs().get(0);
                             Object val = evaluateExpression(keyNode);
                             waitKeybindKey = String.valueOf(val);
                             waitType = WaitType.KEYBIND;
                             pendingVarName = name;
                             return true;
                         } else if (call.getFunctionName().equals("chat")) {
                             if (call.getArgs().size() < 2) return false;
                             Object playerArg = evaluateExpression(call.getArgs().get(0));
                             PlayerFilter playerFilter = buildPlayerFilter(playerArg);
                             if (playerFilter != null) {
                                 waitChatPlayers = playerFilter;
                                 Object msgs = evaluateExpression(call.getArgs().get(1));
                                 waitChatMessages = new ArrayList<>();
                                 if (msgs instanceof List list) {
                                     for (Object o : list) waitChatMessages.add(String.valueOf(o));
                                 } else {
                                     waitChatMessages.add(String.valueOf(msgs));
                                 }
                                 waitChatIgnoreCase = true;
                                 waitChatIgnorePunct = true;
                                 if (call.getArgs().size() > 2) waitChatIgnoreCase = (Boolean) evaluateExpression(call.getArgs().get(2));
                                 if (call.getArgs().size() > 3) waitChatIgnorePunct = (Boolean) evaluateExpression(call.getArgs().get(3));
                                 chatEventMet = false;
                                 chatMatchedMessage = "";
                                 waitType = WaitType.CHAT;
                                 pendingVarName = name;
                                 return true;
                             }
                         } else if (call.getFunctionName().equals("pickup")) {
                             if (call.getArgs().size() < 3) return false;
                             Object pickupNpcArg = evaluateExpression(call.getArgs().get(0));
                             net.minecraft.world.entity.Entity pickupNpcEntity = resolveEntity(pickupNpcArg);
                             waitPickupNpcId = pickupNpcArg instanceof String s ? s : null;
                             waitPickupNpcUuid = pickupNpcEntity != null ? pickupNpcEntity.getUUID() : null;
                             Object amountVal = evaluateExpression(call.getArgs().get(1));
                             waitPickupMaxCount = amountVal instanceof Number n ? n.intValue() : 0;
                             waitPickupItemId = String.valueOf(evaluateExpression(call.getArgs().get(2)));
                             waitPickupTag = call.getArgs().size() >= 4 ? String.valueOf(evaluateExpression(call.getArgs().get(3))) : null;
                             if ("null".equals(waitPickupTag) || (waitPickupTag != null && waitPickupTag.isEmpty())) waitPickupTag = null;
                             waitPickupBaseCount = 0;
                             if (source.getLevel() != null) {
                                 net.minecraft.world.entity.Entity e = pickupNpcEntity;
                                 if (e == null && waitPickupNpcId != null) {
                                     e = org.zonarstudio.spraute_engine.entity.NpcManager.getEntity(waitPickupNpcId, source.getLevel());
                                 }
                                 if (e instanceof net.minecraft.world.entity.Mob mob) {
                                     waitPickupBaseCount = countMatchingItems(mob, waitPickupItemId, waitPickupTag);
                                     if (waitPickupMaxCount >= 0) {
                                         setNpcPickupAwaitCap(e, waitPickupItemId, waitPickupTag,
                                                 waitPickupBaseCount + waitPickupMaxCount, pickupAwaitCapSource);
                                     }
                                 }
                             }
                             waitType = WaitType.PICKUP;
                             // Sync pickup handlers: when entering this await, reset lastCount so we fire on each new batch
                             for (var he : eventHandlers.entrySet()) {
                                 if (!he.getValue().active || !"pickup".equals(he.getValue().eventName) || he.getValue().eventArgs.size() < 2) continue;
                                 net.minecraft.world.entity.Entity handlerNpc = resolveEntity(he.getValue().eventArgs.get(0));
                                 if (handlerNpc != null && waitPickupNpcUuid != null
                                         && handlerNpc.getUUID().equals(waitPickupNpcUuid)
                                         && String.valueOf(he.getValue().eventArgs.get(1)).equals(waitPickupItemId)) {
                                     pickupHandlerLastCount.put(he.getKey(), waitPickupBaseCount);
                                 }
                             }
                             pendingVarName = name;
                             return true;
                         } else if (call.getFunctionName().equals("orbPickup") || call.getFunctionName().equals("orb_pickup")) {
                             if (call.getArgs().size() < 2) return false;
                             Object playerArg = evaluateExpression(call.getArgs().get(0));
                             PlayerFilter playerFilter = buildPlayerFilter(playerArg);
                             if (playerFilter != null) {
                                 waitOrbPickupPlayers = playerFilter;
                                 waitOrbPickupTargetCount = ((Number) evaluateExpression(call.getArgs().get(1))).intValue();
                                 waitOrbPickupTexture = call.getArgs().size() >= 3 && call.getArgs().get(2) != null ? orbTextureFilter(evaluateExpression(call.getArgs().get(2))) : null;
                                 waitOrbPickupCurrentCount = 0;
                                 waitType = WaitType.ORB_PICKUP;
                                 pendingVarName = name;
                                 return true;
                             }
                         } else if (call.getFunctionName().equals("projectileHit") || call.getFunctionName().equals("projectile_hit")) {
                             if (call.getArgs().isEmpty()) return false;
                             waitProjectileHitId = String.valueOf(evaluateExpression(call.getArgs().get(0)));
                             waitProjectileHitType = call.getArgs().size() > 1 && call.getArgs().get(1) != null
                                     ? String.valueOf(evaluateExpression(call.getArgs().get(1))) : null;
                             waitType = WaitType.PROJECTILE_HIT;
                             pendingVarName = name;
                             return true;
                         } else if (call.getFunctionName().equals("tradeBuy") || call.getFunctionName().equals("trade_buy")) {
                             beginCustomEventAwait("tradeBuy", call.getArgs(), name);
                             return true;
                         } else if (call.getFunctionName().equals("tradeSell") || call.getFunctionName().equals("trade_sell")) {
                             beginCustomEventAwait("tradeSell", call.getArgs(), name);
                             return true;
                         } else if (call.getFunctionName().equals("relGift") || call.getFunctionName().equals("rel_gift")) {
                             if (call.getArgs().isEmpty()) return false;
                             Object playerArg = evaluateExpression(call.getArgs().get(0));
                             PlayerFilter playerFilter = buildPlayerFilter(playerArg);
                             if (playerFilter != null) {
                                 String filter2 = call.getArgs().size() > 1 && call.getArgs().get(1) != null
                                         ? String.valueOf(evaluateExpression(call.getArgs().get(1))) : null;
                                 String filter3 = call.getArgs().size() > 2 && call.getArgs().get(2) != null
                                         ? String.valueOf(evaluateExpression(call.getArgs().get(2))) : null;
                                 String[] npcOut = new String[1];
                                 String[] itemOut = new String[1];
                                 applyRelGiftWaitFilters(filter2, filter3, npcOut, itemOut);
                                 waitRelGiftPlayers = playerFilter;
                                 waitRelGiftNpcId = npcOut[0];
                                 waitRelGiftItemId = itemOut[0];
                                 waitType = WaitType.REL_GIFT;
                                 pendingVarName = name;
                                 return true;
                             }
                         } else if (call.getFunctionName().equals("relRepChange") || call.getFunctionName().equals("rel_rep_change")) {
                             if (call.getArgs().isEmpty()) return false;
                             Object playerArg = evaluateExpression(call.getArgs().get(0));
                             PlayerFilter playerFilter = buildPlayerFilter(playerArg);
                             if (playerFilter != null) {
                                 waitRelSimplePlayers = playerFilter;
                                 waitRelSimpleNpcId = call.getArgs().size() > 1 && call.getArgs().get(1) != null
                                         ? String.valueOf(evaluateExpression(call.getArgs().get(1))) : null;
                                 waitType = WaitType.REL_REP_CHANGE;
                                 pendingVarName = name;
                                 return true;
                             }
                         } else if (call.getFunctionName().equals("relTalk") || call.getFunctionName().equals("rel_talk")) {
                             if (call.getArgs().isEmpty()) return false;
                             Object playerArg = evaluateExpression(call.getArgs().get(0));
                             PlayerFilter playerFilter = buildPlayerFilter(playerArg);
                             if (playerFilter != null) {
                                 waitRelSimplePlayers = playerFilter;
                                 waitRelSimpleNpcId = call.getArgs().size() > 1 && call.getArgs().get(1) != null
                                         ? String.valueOf(evaluateExpression(call.getArgs().get(1))) : null;
                                 waitType = WaitType.REL_TALK;
                                 pendingVarName = name;
                                 return true;
                             }
                         } else if (call.getFunctionName().equals("relButton") || call.getFunctionName().equals("rel_button")) {
                             if (call.getArgs().isEmpty()) return false;
                             Object playerArg = evaluateExpression(call.getArgs().get(0));
                             PlayerFilter playerFilter = buildPlayerFilter(playerArg);
                             if (playerFilter != null) {
                                 waitRelSimplePlayers = playerFilter;
                                 waitRelSimpleBtnId = call.getArgs().size() > 1 && call.getArgs().get(1) != null
                                         ? String.valueOf(evaluateExpression(call.getArgs().get(1))) : null;
                                 waitRelSimpleNpcId = call.getArgs().size() > 2 && call.getArgs().get(2) != null
                                         ? String.valueOf(evaluateExpression(call.getArgs().get(2))) : null;
                                 waitType = WaitType.REL_BUTTON;
                                 pendingVarName = name;
                                 return true;
                             }
                         } else if (call.getFunctionName().equals("uiClick") || call.getFunctionName().equals("uiclick")) {
                             if (call.getArgs().isEmpty()) return false;
                             Object playerArg = evaluateExpression(call.getArgs().get(0));
                             PlayerFilter playerFilter = buildPlayerFilter(playerArg);
                             if (playerFilter != null) {
                                 waitUiPlayers = playerFilter;
                                 waitType = WaitType.UI_CLICK;
                                 pendingVarName = name;
                                 return true;
                             }
                         } else if (call.getFunctionName().equals("uiDrop") || call.getFunctionName().equals("uidrop")) {
                             if (call.getArgs().isEmpty()) return false;
                             Object playerArg = evaluateExpression(call.getArgs().get(0));
                             PlayerFilter playerFilter = buildPlayerFilter(playerArg);
                             if (playerFilter != null) {
                                 waitUiPlayers = playerFilter;
                                 waitType = WaitType.UI_DROP;
                                 pendingVarName = name;
                                 return true;
                             }
                         } else if (call.getFunctionName().equals("uiClose") || call.getFunctionName().equals("uiclose")) {
                             if (call.getArgs().isEmpty()) return false;
                             Object playerArg = evaluateExpression(call.getArgs().get(0));
                             PlayerFilter playerFilter = buildPlayerFilter(playerArg);
                             if (playerFilter != null) {
                                 waitUiPlayers = playerFilter;
                                 waitType = WaitType.UI_CLOSE;
                                 pendingVarName = name;
                                 return true;
                             }
                         } else if (call.getFunctionName().equals("uiInput")) {
                             if (call.getArgs().isEmpty()) return false;
                             Object playerArg = evaluateExpression(call.getArgs().get(0));
                             PlayerFilter playerFilter = buildPlayerFilter(playerArg);
                             if (playerFilter != null) {
                                 waitUiPlayers = playerFilter;
                                 uiInputWidgetId = call.getArgs().size() > 1 ? String.valueOf(evaluateExpression(call.getArgs().get(1))) : null;
                                 waitType = WaitType.UI_INPUT;
                                 pendingVarName = name;
                                 return true;
                             }
                         } else if (call.getFunctionName().equals("position")) {
                             if (call.getArgs().size() < 4) return false;
                             Object playerArg = evaluateExpression(call.getArgs().get(0));
                             PlayerFilter playerFilter = buildPlayerFilter(playerArg);
                             if (playerFilter != null) {
                                 waitPositionPlayers = playerFilter;
                                 waitPositionX = ((Number) evaluateExpression(call.getArgs().get(1))).doubleValue();
                                 waitPositionY = ((Number) evaluateExpression(call.getArgs().get(2))).doubleValue();
                                 waitPositionZ = ((Number) evaluateExpression(call.getArgs().get(3))).doubleValue();
                                 waitPositionRadius = call.getArgs().size() > 4 ? ((Number) evaluateExpression(call.getArgs().get(4))).doubleValue() : 1.5;
                                 waitType = WaitType.POSITION;
                                 pendingVarName = name;
                                 return true;
                             }
                         } else if (call.getFunctionName().equals("inventory") || call.getFunctionName().equals("hasItem")) {
                             if (call.getArgs().size() < 2) return false;
                             Object playerArg = evaluateExpression(call.getArgs().get(0));
                             PlayerFilter playerFilter = buildPlayerFilter(playerArg);
                             if (playerFilter != null) {
                                 waitInventoryPlayers = playerFilter;
                                 waitInventoryItemId = String.valueOf(evaluateExpression(call.getArgs().get(1)));
                                 waitInventoryCount = call.getArgs().size() > 2 ? ((Number) evaluateExpression(call.getArgs().get(2))).intValue() : 0;
                                 waitType = WaitType.INVENTORY;
                                 pendingVarName = name;
                                 return true;
                             }
                         } else if (call.getFunctionName().equals("clickBlock") || call.getFunctionName().equals("breakBlock") || call.getFunctionName().equals("placeBlock")
                                 || call.getFunctionName().equals("openChest") || call.getFunctionName().equals("openDoor")
                                 || call.getFunctionName().equals("useBlock")) {
                             if (call.getArgs().isEmpty()) return false;
                             Object playerArg = evaluateExpression(call.getArgs().get(0));
                             PlayerFilter playerFilter = buildPlayerFilter(playerArg);
                             if (playerFilter != null) {
                                 waitBlockPlayers = playerFilter;
                                 waitBlockId = null;
                                 waitBlockPos = null;
                                 if (call.getArgs().size() == 2) {
                                     waitBlockId = String.valueOf(evaluateExpression(call.getArgs().get(1)));
                                 } else if (call.getArgs().size() >= 4) {
                                     waitBlockPos = new net.minecraft.core.BlockPos(
                                         ((Number) evaluateExpression(call.getArgs().get(1))).intValue(),
                                         ((Number) evaluateExpression(call.getArgs().get(2))).intValue(),
                                         ((Number) evaluateExpression(call.getArgs().get(3))).intValue()
                                     );
                                     if (call.getArgs().size() >= 5) waitBlockId = String.valueOf(evaluateExpression(call.getArgs().get(4)));
                                 }
                                 if (call.getFunctionName().equals("clickBlock")) waitType = WaitType.CLICK_BLOCK;
                                 else if (call.getFunctionName().equals("breakBlock")) waitType = WaitType.BREAK_BLOCK;
                                 else if (call.getFunctionName().equals("placeBlock")) waitType = WaitType.PLACE_BLOCK;
                                 else if (call.getFunctionName().equals("openChest")) waitType = WaitType.OPEN_CHEST;
                                 else if (call.getFunctionName().equals("openDoor")) waitType = WaitType.OPEN_DOOR;
                                 else waitType = WaitType.USE_BLOCK;
                                 blockEventMet = false;
                                 pendingVarName = name;
                                 return true;
                             }
                         } else if (call.getFunctionName().equals("jump")) {
                             if (call.getArgs().isEmpty()) return false;
                             Object playerArg = evaluateExpression(call.getArgs().get(0));
                             PlayerFilter playerFilter = buildPlayerFilter(playerArg);
                             if (playerFilter != null) {
                                 waitPlayerActionPlayers = playerFilter;
                                 waitPlayerActionType = "jump";
                                 waitPlayerActionTarget = null;
                                 playerActionMet = false;
                                 waitType = WaitType.PLAYER_ACTION;
                                 pendingVarName = name;
                                 return true;
                             }
                         } else if (call.getFunctionName().equals("action") || call.getFunctionName().equals("playerAction")) {
                             if (call.getArgs().size() < 2) return false;
                             Object playerArg = evaluateExpression(call.getArgs().get(0));
                             PlayerFilter playerFilter = buildPlayerFilter(playerArg);
                             if (playerFilter != null) {
                                 waitPlayerActionPlayers = playerFilter;
                                 waitPlayerActionType = String.valueOf(evaluateExpression(call.getArgs().get(1)));
                                 waitPlayerActionTarget = call.getArgs().size() > 2 && call.getArgs().get(2) != null ? String.valueOf(evaluateExpression(call.getArgs().get(2))) : null;
                                 playerActionMet = false;
                                 waitType = WaitType.PLAYER_ACTION;
                                 pendingVarName = name;
                                 return true;
                             }
                         } else if (ScriptCustomEvents.canAwaitAsCustom(call.getFunctionName())) {
                             return beginCustomEventAwait(call.getFunctionName(), call.getArgs(), name);
                         }
                    }
                    putVariable(name, evaluateExpression(valueNode));
                }
                case VAR_DECL -> {
                    String name = (String) instruction.getArg(0);
                    ScriptNode initializer = (ScriptNode) instruction.getArg(1);
                    String scope = instruction.getArgCount() >= 3 ? String.valueOf(instruction.getArg(2)) : "local";
                    
                    if (asyncResult == null && initializer instanceof ScriptNode.AwaitNode awaitNode) {
                         ScriptNode.FunctionCallNode call = awaitNode.getCall();
                         if (call == null) {
                             // Method awaits compile to CALL_METHOD.
                         } else if (call.getFunctionName().equals("interact")) {
                             ScriptNode entityIdNode = call.getArgs().get(0);
                             Object val = evaluateExpression(entityIdNode);
                             net.minecraft.world.entity.Entity targetEntity = resolveEntity(val);
                             if (targetEntity != null) {
                                 waitEntityUuid = targetEntity.getUUID();
                                 waitType = WaitType.INTERACT;
                                 pendingVarName = name;
                                 return true;
                             }
                         } else if (call.getFunctionName().equals("death")) {
                             ScriptNode targetNode = call.getArgs().get(0);
                             Object val = evaluateExpression(targetNode);
                             waitDeathTarget = normalizeEntityWaitTarget(val);
                             waitType = WaitType.DEATH;
                             pendingVarName = name;
                             return true;
                         } else if (call.getFunctionName().equals("kill")) {
                             if (call.getArgs().isEmpty()) return false;
                             waitKillKillerTarget = normalizeEntityWaitTarget(evaluateExpression(call.getArgs().get(0)));
                             waitKillVictimTarget = call.getArgs().size() > 1
                                     ? normalizeEntityWaitTarget(evaluateExpression(call.getArgs().get(1))) : "any";
                             killMet = false;
                             waitType = WaitType.KILL;
                             pendingVarName = name;
                             return true;
                         } else if (call.getFunctionName().equals("keybind")) {
                             ScriptNode keyNode = call.getArgs().get(0);
                             Object val = evaluateExpression(keyNode);
                             waitKeybindKey = String.valueOf(val);
                             waitType = WaitType.KEYBIND;
                             pendingVarName = name;
                             return true;
                         } else if (call.getFunctionName().equals("chat")) {
                             if (call.getArgs().size() < 2) return false;
                             Object playerArg = evaluateExpression(call.getArgs().get(0));
                             PlayerFilter playerFilter = buildPlayerFilter(playerArg);
                             if (playerFilter != null) {
                                 waitChatPlayers = playerFilter;
                                 Object msgs = evaluateExpression(call.getArgs().get(1));
                                 waitChatMessages = new ArrayList<>();
                                 if (msgs instanceof List list) {
                                     for (Object o : list) waitChatMessages.add(String.valueOf(o));
                                 } else {
                                     waitChatMessages.add(String.valueOf(msgs));
                                 }
                                 waitChatIgnoreCase = true;
                                 waitChatIgnorePunct = true;
                                 if (call.getArgs().size() > 2) waitChatIgnoreCase = (Boolean) evaluateExpression(call.getArgs().get(2));
                                 if (call.getArgs().size() > 3) waitChatIgnorePunct = (Boolean) evaluateExpression(call.getArgs().get(3));
                                 chatEventMet = false;
                                 chatMatchedMessage = "";
                                 waitType = WaitType.CHAT;
                                 pendingVarName = name;
                                 return true;
                             }
                         } else if (call.getFunctionName().equals("pickup")) {
                             if (call.getArgs().size() < 3) return false;
                             Object pickupNpcArg = evaluateExpression(call.getArgs().get(0));
                             net.minecraft.world.entity.Entity pickupNpcEntity = resolveEntity(pickupNpcArg);
                             waitPickupNpcId = pickupNpcArg instanceof String s ? s : null;
                             waitPickupNpcUuid = pickupNpcEntity != null ? pickupNpcEntity.getUUID() : null;
                             Object amountVal = evaluateExpression(call.getArgs().get(1));
                             waitPickupMaxCount = amountVal instanceof Number n ? n.intValue() : 0;
                             waitPickupItemId = String.valueOf(evaluateExpression(call.getArgs().get(2)));
                             waitPickupTag = call.getArgs().size() >= 4 ? String.valueOf(evaluateExpression(call.getArgs().get(3))) : null;
                             if ("null".equals(waitPickupTag) || (waitPickupTag != null && waitPickupTag.isEmpty())) waitPickupTag = null;
                             waitPickupBaseCount = 0;
                             if (source.getLevel() != null) {
                                 net.minecraft.world.entity.Entity e = pickupNpcEntity;
                                 if (e == null && waitPickupNpcId != null) {
                                     e = org.zonarstudio.spraute_engine.entity.NpcManager.getEntity(waitPickupNpcId, source.getLevel());
                                 }
                                 if (e instanceof net.minecraft.world.entity.Mob mob) {
                                     waitPickupBaseCount = countMatchingItems(mob, waitPickupItemId, waitPickupTag);
                                     if (waitPickupMaxCount >= 0) {
                                         setNpcPickupAwaitCap(e, waitPickupItemId, waitPickupTag,
                                                 waitPickupBaseCount + waitPickupMaxCount, pickupAwaitCapSource);
                                     }
                                 }
                             }
                             waitType = WaitType.PICKUP;
                             // Sync pickup handlers: when entering this await, reset lastCount so we fire on each new batch
                             for (var he : eventHandlers.entrySet()) {
                                 if (!he.getValue().active || !"pickup".equals(he.getValue().eventName) || he.getValue().eventArgs.size() < 2) continue;
                                 net.minecraft.world.entity.Entity handlerNpc = resolveEntity(he.getValue().eventArgs.get(0));
                                 if (handlerNpc != null && waitPickupNpcUuid != null
                                         && handlerNpc.getUUID().equals(waitPickupNpcUuid)
                                         && String.valueOf(he.getValue().eventArgs.get(1)).equals(waitPickupItemId)) {
                                     pickupHandlerLastCount.put(he.getKey(), waitPickupBaseCount);
                                 }
                             }
                             pendingVarName = name;
                             return true;
                         } else if (call.getFunctionName().equals("orbPickup") || call.getFunctionName().equals("orb_pickup")) {
                             if (call.getArgs().size() < 2) return false;
                             Object playerArg = evaluateExpression(call.getArgs().get(0));
                             PlayerFilter playerFilter = buildPlayerFilter(playerArg);
                             if (playerFilter != null) {
                                 waitOrbPickupPlayers = playerFilter;
                                 waitOrbPickupTargetCount = ((Number) evaluateExpression(call.getArgs().get(1))).intValue();
                                 waitOrbPickupTexture = call.getArgs().size() >= 3 && call.getArgs().get(2) != null ? orbTextureFilter(evaluateExpression(call.getArgs().get(2))) : null;
                                 waitOrbPickupCurrentCount = 0;
                                 waitType = WaitType.ORB_PICKUP;
                                 pendingVarName = name;
                                 return true;
                             }
                         } else if (call.getFunctionName().equals("projectileHit") || call.getFunctionName().equals("projectile_hit")) {
                             if (call.getArgs().isEmpty()) return false;
                             waitProjectileHitId = String.valueOf(evaluateExpression(call.getArgs().get(0)));
                             waitProjectileHitType = call.getArgs().size() > 1 && call.getArgs().get(1) != null
                                     ? String.valueOf(evaluateExpression(call.getArgs().get(1))) : null;
                             waitType = WaitType.PROJECTILE_HIT;
                             pendingVarName = name;
                             return true;
                         } else if (call.getFunctionName().equals("tradeBuy") || call.getFunctionName().equals("trade_buy")) {
                             beginCustomEventAwait("tradeBuy", call.getArgs(), name);
                             return true;
                         } else if (call.getFunctionName().equals("tradeSell") || call.getFunctionName().equals("trade_sell")) {
                             beginCustomEventAwait("tradeSell", call.getArgs(), name);
                             return true;
                         } else if (call.getFunctionName().equals("relGift") || call.getFunctionName().equals("rel_gift")) {
                             if (call.getArgs().isEmpty()) return false;
                             Object playerArg = evaluateExpression(call.getArgs().get(0));
                             PlayerFilter playerFilter = buildPlayerFilter(playerArg);
                             if (playerFilter != null) {
                                 String filter2 = call.getArgs().size() > 1 && call.getArgs().get(1) != null
                                         ? String.valueOf(evaluateExpression(call.getArgs().get(1))) : null;
                                 String filter3 = call.getArgs().size() > 2 && call.getArgs().get(2) != null
                                         ? String.valueOf(evaluateExpression(call.getArgs().get(2))) : null;
                                 String[] npcOut = new String[1];
                                 String[] itemOut = new String[1];
                                 applyRelGiftWaitFilters(filter2, filter3, npcOut, itemOut);
                                 waitRelGiftPlayers = playerFilter;
                                 waitRelGiftNpcId = npcOut[0];
                                 waitRelGiftItemId = itemOut[0];
                                 waitType = WaitType.REL_GIFT;
                                 pendingVarName = name;
                                 return true;
                             }
                         } else if (call.getFunctionName().equals("relRepChange") || call.getFunctionName().equals("rel_rep_change")) {
                             if (call.getArgs().isEmpty()) return false;
                             Object playerArg = evaluateExpression(call.getArgs().get(0));
                             PlayerFilter playerFilter = buildPlayerFilter(playerArg);
                             if (playerFilter != null) {
                                 waitRelSimplePlayers = playerFilter;
                                 waitRelSimpleNpcId = call.getArgs().size() > 1 && call.getArgs().get(1) != null
                                         ? String.valueOf(evaluateExpression(call.getArgs().get(1))) : null;
                                 waitType = WaitType.REL_REP_CHANGE;
                                 pendingVarName = name;
                                 return true;
                             }
                         } else if (call.getFunctionName().equals("relTalk") || call.getFunctionName().equals("rel_talk")) {
                             if (call.getArgs().isEmpty()) return false;
                             Object playerArg = evaluateExpression(call.getArgs().get(0));
                             PlayerFilter playerFilter = buildPlayerFilter(playerArg);
                             if (playerFilter != null) {
                                 waitRelSimplePlayers = playerFilter;
                                 waitRelSimpleNpcId = call.getArgs().size() > 1 && call.getArgs().get(1) != null
                                         ? String.valueOf(evaluateExpression(call.getArgs().get(1))) : null;
                                 waitType = WaitType.REL_TALK;
                                 pendingVarName = name;
                                 return true;
                             }
                         } else if (call.getFunctionName().equals("relButton") || call.getFunctionName().equals("rel_button")) {
                             if (call.getArgs().isEmpty()) return false;
                             Object playerArg = evaluateExpression(call.getArgs().get(0));
                             PlayerFilter playerFilter = buildPlayerFilter(playerArg);
                             if (playerFilter != null) {
                                 waitRelSimplePlayers = playerFilter;
                                 waitRelSimpleBtnId = call.getArgs().size() > 1 && call.getArgs().get(1) != null
                                         ? String.valueOf(evaluateExpression(call.getArgs().get(1))) : null;
                                 waitRelSimpleNpcId = call.getArgs().size() > 2 && call.getArgs().get(2) != null
                                         ? String.valueOf(evaluateExpression(call.getArgs().get(2))) : null;
                                 waitType = WaitType.REL_BUTTON;
                                 pendingVarName = name;
                                 return true;
                             }
                         } else if (call.getFunctionName().equals("uiClick") || call.getFunctionName().equals("uiclick")) {
                             if (call.getArgs().isEmpty()) return false;
                             Object playerArg = evaluateExpression(call.getArgs().get(0));
                             PlayerFilter playerFilter = buildPlayerFilter(playerArg);
                             if (playerFilter != null) {
                                 waitUiPlayers = playerFilter;
                                 waitType = WaitType.UI_CLICK;
                                 pendingVarName = name;
                                 return true;
                             }
                         } else if (call.getFunctionName().equals("uiDrop") || call.getFunctionName().equals("uidrop")) {
                             if (call.getArgs().isEmpty()) return false;
                             Object playerArg = evaluateExpression(call.getArgs().get(0));
                             PlayerFilter playerFilter = buildPlayerFilter(playerArg);
                             if (playerFilter != null) {
                                 waitUiPlayers = playerFilter;
                                 waitType = WaitType.UI_DROP;
                                 pendingVarName = name;
                                 return true;
                             }
                         } else if (call.getFunctionName().equals("uiClose") || call.getFunctionName().equals("uiclose")) {
                             if (call.getArgs().isEmpty()) return false;
                             Object playerArg = evaluateExpression(call.getArgs().get(0));
                             PlayerFilter playerFilter = buildPlayerFilter(playerArg);
                             if (playerFilter != null) {
                                 waitUiPlayers = playerFilter;
                                 waitType = WaitType.UI_CLOSE;
                                 pendingVarName = name;
                                 return true;
                             }
                         } else if (call.getFunctionName().equals("uiInput")) {
                             if (call.getArgs().isEmpty()) return false;
                             Object playerArg = evaluateExpression(call.getArgs().get(0));
                             PlayerFilter playerFilter = buildPlayerFilter(playerArg);
                             if (playerFilter != null) {
                                 waitUiPlayers = playerFilter;
                                 uiInputWidgetId = call.getArgs().size() > 1 ? String.valueOf(evaluateExpression(call.getArgs().get(1))) : null;
                                 waitType = WaitType.UI_INPUT;
                                 pendingVarName = name;
                                 return true;
                             }
                         } else if (call.getFunctionName().equals("position")) {
                             if (call.getArgs().size() < 4) return false;
                             Object playerArg = evaluateExpression(call.getArgs().get(0));
                             PlayerFilter playerFilter = buildPlayerFilter(playerArg);
                             if (playerFilter != null) {
                                 waitPositionPlayers = playerFilter;
                                 waitPositionX = ((Number) evaluateExpression(call.getArgs().get(1))).doubleValue();
                                 waitPositionY = ((Number) evaluateExpression(call.getArgs().get(2))).doubleValue();
                                 waitPositionZ = ((Number) evaluateExpression(call.getArgs().get(3))).doubleValue();
                                 waitPositionRadius = call.getArgs().size() > 4 ? ((Number) evaluateExpression(call.getArgs().get(4))).doubleValue() : 1.5;
                                 waitType = WaitType.POSITION;
                                 pendingVarName = name;
                                 return true;
                             }
                         } else if (call.getFunctionName().equals("inventory") || call.getFunctionName().equals("hasItem")) {
                             if (call.getArgs().size() < 2) return false;
                             Object playerArg = evaluateExpression(call.getArgs().get(0));
                             PlayerFilter playerFilter = buildPlayerFilter(playerArg);
                             if (playerFilter != null) {
                                 waitInventoryPlayers = playerFilter;
                                 waitInventoryItemId = String.valueOf(evaluateExpression(call.getArgs().get(1)));
                                 waitInventoryCount = call.getArgs().size() > 2 ? ((Number) evaluateExpression(call.getArgs().get(2))).intValue() : 0;
                                 waitType = WaitType.INVENTORY;
                                 pendingVarName = name;
                                 return true;
                             }
                         } else if (call.getFunctionName().equals("clickBlock") || call.getFunctionName().equals("breakBlock") || call.getFunctionName().equals("placeBlock")
                                 || call.getFunctionName().equals("openChest") || call.getFunctionName().equals("openDoor")
                                 || call.getFunctionName().equals("useBlock")) {
                             if (call.getArgs().isEmpty()) return false;
                             Object playerArg = evaluateExpression(call.getArgs().get(0));
                             PlayerFilter playerFilter = buildPlayerFilter(playerArg);
                             if (playerFilter != null) {
                                 waitBlockPlayers = playerFilter;
                                 waitBlockId = null;
                                 waitBlockPos = null;
                                 if (call.getArgs().size() == 2) {
                                     waitBlockId = String.valueOf(evaluateExpression(call.getArgs().get(1)));
                                 } else if (call.getArgs().size() >= 4) {
                                     waitBlockPos = new net.minecraft.core.BlockPos(
                                         ((Number) evaluateExpression(call.getArgs().get(1))).intValue(),
                                         ((Number) evaluateExpression(call.getArgs().get(2))).intValue(),
                                         ((Number) evaluateExpression(call.getArgs().get(3))).intValue()
                                     );
                                     if (call.getArgs().size() >= 5) waitBlockId = String.valueOf(evaluateExpression(call.getArgs().get(4)));
                                 }
                                 if (call.getFunctionName().equals("clickBlock")) waitType = WaitType.CLICK_BLOCK;
                                 else if (call.getFunctionName().equals("breakBlock")) waitType = WaitType.BREAK_BLOCK;
                                 else if (call.getFunctionName().equals("placeBlock")) waitType = WaitType.PLACE_BLOCK;
                                 else if (call.getFunctionName().equals("openChest")) waitType = WaitType.OPEN_CHEST;
                                 else if (call.getFunctionName().equals("openDoor")) waitType = WaitType.OPEN_DOOR;
                                 else waitType = WaitType.USE_BLOCK;
                                 blockEventMet = false;
                                 pendingVarName = name;
                                 return true;
                             }
                         } else if (call.getFunctionName().equals("jump")) {
                             if (call.getArgs().isEmpty()) return false;
                             Object playerArg = evaluateExpression(call.getArgs().get(0));
                             PlayerFilter playerFilter = buildPlayerFilter(playerArg);
                             if (playerFilter != null) {
                                 waitPlayerActionPlayers = playerFilter;
                                 waitPlayerActionType = "jump";
                                 waitPlayerActionTarget = null;
                                 playerActionMet = false;
                                 waitType = WaitType.PLAYER_ACTION;
                                 pendingVarName = name;
                                 return true;
                             }
                         } else if (call.getFunctionName().equals("action") || call.getFunctionName().equals("playerAction")) {
                             if (call.getArgs().size() < 2) return false;
                             Object playerArg = evaluateExpression(call.getArgs().get(0));
                             PlayerFilter playerFilter = buildPlayerFilter(playerArg);
                             if (playerFilter != null) {
                                 waitPlayerActionPlayers = playerFilter;
                                 waitPlayerActionType = String.valueOf(evaluateExpression(call.getArgs().get(1)));
                                 waitPlayerActionTarget = call.getArgs().size() > 2 && call.getArgs().get(2) != null ? String.valueOf(evaluateExpression(call.getArgs().get(2))) : null;
                                 playerActionMet = false;
                                 waitType = WaitType.PLAYER_ACTION;
                                 pendingVarName = name;
                                 return true;
                             }
                         } else if (ScriptCustomEvents.canAwaitAsCustom(call.getFunctionName())) {
                             return beginCustomEventAwait(call.getFunctionName(), call.getArgs(), name);
                         }
                    }
                    
                    if ("world".equals(scope)) {
                        net.minecraft.server.level.ServerLevel lvl = source.getLevel();
                        if (lvl == null || !ScriptWorldData.get(lvl).has(name)) {
                            putVariable(name, evaluateExpression(initializer), scope);
                        }
                    } else {
                        putVariable(name, evaluateExpression(initializer), scope);
                    }
                }
                case CALL -> executeCall(instruction);
                case CALL_METHOD -> {
                    if (executeCallMethod(instruction, false, null)) return true;
                }
                case NPC_BLOCK -> executeNpcBlock(instruction);
                case MODEL_BLOCK -> executeModelBlock(instruction);
                case HITBOX_BLOCK -> executeHitboxBlock(instruction);
                case EMPTY_BLOCK -> executeEmptyBlock(instruction);
                case SKIN_OVERLAY_BLOCK -> executeSkinOverlayBlock(instruction);
                case PARTICLE_EMITTER_BLOCK -> executeParticleEmitterBlock(instruction);
                case PARTICLE_DEF_BLOCK -> executeParticleDefBlock(instruction);
                case PROJECTILE_DEF_BLOCK -> executeProjectileDefBlock(instruction);
                case LIGHT_BLOCK -> executeLightBlock(instruction);
                case AUDIO_SOURCE_BLOCK -> executeAudioSourceBlock(instruction);
                case POST_PROCESS_BLOCK -> executePostProcessBlock(instruction);
                case CUSTOM_NODE_BLOCK -> executeCustomNodeBlock(instruction);
                case NPC_PREFAB_DEF -> executeNpcPrefabDef(instruction);
                case UI_BLOCK -> executeUiBlock(instruction);
                case COMMAND_BLOCK -> executeCommandBlock(instruction);
                case FADE_IN -> executeFadeIn(instruction);
                case CAMERA -> executeCamera(instruction);
                case UI_WIDGET -> executeUiWidget(instruction);
                case SET_PROPERTY -> executeSetProperty(instruction);
                case SET_INDEX -> executeSetIndex(instruction);
                case AWAIT_TIME -> {
                    ScriptNode secondsNode = (ScriptNode) instruction.getArg(0);
                    Object val = evaluateExpression(secondsNode);
                    waitTimer = coerceSeconds(val, "main");
                    waitType = WaitType.TIME;
                    return true;
                }
                case AWAIT_CAMERA_ROUTE -> {
                    Double sec = awaitCameraRouteDuration(instruction);
                    if (sec != null) {
                        waitTimer = sec;
                        waitType = WaitType.TIME;
                        return true;
                    }
                }
                case AWAIT_NEXT -> {
                    waitType = WaitType.NEXT;
                    return true;
                }
                case AWAIT_INTERACT -> {
                    ScriptNode entityIdNode = (ScriptNode) instruction.getArg(0);
                    Object val = evaluateExpression(entityIdNode); 
                    net.minecraft.world.entity.Entity targetEntity = resolveEntity(val);
                    if (targetEntity != null) {
                        waitEntityUuid = targetEntity.getUUID();
                        interactionMet = false;
                        waitType = WaitType.INTERACT;
                        return true;
                    } else {
                        LOGGER.warn("Cannot await interact: Unknown target '{}'", String.valueOf(val));
                    }
                }
                case AWAIT_KEYBIND -> {
                    ScriptNode keyNode = (ScriptNode) instruction.getArg(0);
                    Object val = evaluateExpression(keyNode);
                    waitKeybindKey = String.valueOf(val);
                    waitType = WaitType.KEYBIND;
                    return true;
                }
                case AWAIT_DEATH -> {
                    ScriptNode targetNode = (ScriptNode) instruction.getArg(0);
                    Object val = evaluateExpression(targetNode);
                    waitDeathTarget = normalizeEntityWaitTarget(val);
                    waitType = WaitType.DEATH;
                    return true;
                }
                case AWAIT_KILL -> {
                    ScriptNode killerNode = (ScriptNode) instruction.getArg(0);
                    ScriptNode victimNode = instruction.getArgCount() >= 2 ? (ScriptNode) instruction.getArg(1) : null;
                    waitKillKillerTarget = normalizeEntityWaitTarget(evaluateExpression(killerNode));
                    waitKillVictimTarget = victimNode != null ? normalizeEntityWaitTarget(evaluateExpression(victimNode)) : "any";
                    killMet = false;
                    waitType = WaitType.KILL;
                    return true;
                }
                case AWAIT_PICKUP -> {
                    ScriptNode npcNode = (ScriptNode) instruction.getArg(0);
                    ScriptNode amountNode = (ScriptNode) instruction.getArg(1);
                    ScriptNode itemNode = (ScriptNode) instruction.getArg(2);
                    ScriptNode nbtNode = instruction.getArgCount() >= 4 ? (ScriptNode) instruction.getArg(3) : null;
                    Object pickupNpcArg = evaluateExpression(npcNode);
                    net.minecraft.world.entity.Entity pickupNpcEntity = resolveEntity(pickupNpcArg);
                    waitPickupNpcId = pickupNpcArg instanceof String s ? s : null;
                    waitPickupNpcUuid = pickupNpcEntity != null ? pickupNpcEntity.getUUID() : null;
                    Object amountVal = evaluateExpression(amountNode);
                    waitPickupMaxCount = amountVal instanceof Number n ? n.intValue() : 0;
                    waitPickupItemId = String.valueOf(evaluateExpression(itemNode));
                    waitPickupTag = nbtNode != null ? String.valueOf(evaluateExpression(nbtNode)) : null;
                    if ("null".equals(waitPickupTag) || (waitPickupTag != null && waitPickupTag.isEmpty())) waitPickupTag = null;
                    waitPickupBaseCount = 0;
                    if (source.getLevel() != null) {
                        net.minecraft.world.entity.Entity e = pickupNpcEntity;
                        if (e == null && waitPickupNpcId != null) {
                            e = org.zonarstudio.spraute_engine.entity.NpcManager.getEntity(waitPickupNpcId, source.getLevel());
                        }
                        if (e instanceof net.minecraft.world.entity.Mob mob) {
                            waitPickupBaseCount = countMatchingItems(mob, waitPickupItemId, waitPickupTag);
                            if (waitPickupMaxCount >= 0) {
                                setNpcPickupAwaitCap(e, waitPickupItemId, waitPickupTag,
                                        waitPickupBaseCount + waitPickupMaxCount, pickupAwaitCapSource);
                            }
                        }
                    }
                    waitType = WaitType.PICKUP;
                    return true;
                }
                case AWAIT_ORB_PICKUP -> {
                    ScriptNode pNode = (ScriptNode) instruction.getArg(0);
                    ScriptNode amountNode = (ScriptNode) instruction.getArg(1);
                    ScriptNode texNode = instruction.getArgCount() >= 3 && instruction.getArg(2) != null ? (ScriptNode) instruction.getArg(2) : null;
                    
                    Object playerArg = evaluateExpression(pNode);
                    PlayerFilter playerFilter = buildPlayerFilter(playerArg);
                    if (playerFilter != null) {
                        waitOrbPickupPlayers = playerFilter;
                        waitOrbPickupTargetCount = ((Number) evaluateExpression(amountNode)).intValue();
                        waitOrbPickupTexture = texNode != null ? orbTextureFilter(evaluateExpression(texNode)) : null;
                        waitOrbPickupCurrentCount = 0;
                        waitType = WaitType.ORB_PICKUP;
                        return true;
                    }
                }
                case AWAIT_PROJECTILE_HIT -> {
                    ScriptNode idNode = (ScriptNode) instruction.getArg(0);
                    ScriptNode typeNode = instruction.getArgCount() >= 2 && instruction.getArg(1) != null ? (ScriptNode) instruction.getArg(1) : null;
                    waitProjectileHitId = String.valueOf(evaluateExpression(idNode));
                    waitProjectileHitType = typeNode != null ? String.valueOf(evaluateExpression(typeNode)) : null;
                    waitType = WaitType.PROJECTILE_HIT;
                    return true;
                }
                case AWAIT_TRADE_BUY -> {
                    java.util.List<ScriptNode> filters = new java.util.ArrayList<>();
                    filters.add((ScriptNode) instruction.getArg(0));
                    if (instruction.getArgCount() >= 2 && instruction.getArg(1) != null) filters.add((ScriptNode) instruction.getArg(1));
                    beginCustomEventAwait("tradeBuy", filters, null);
                    return true;
                }
                case AWAIT_TRADE_SELL -> {
                    java.util.List<ScriptNode> filters = new java.util.ArrayList<>();
                    filters.add((ScriptNode) instruction.getArg(0));
                    if (instruction.getArgCount() >= 2 && instruction.getArg(1) != null) filters.add((ScriptNode) instruction.getArg(1));
                    beginCustomEventAwait("tradeSell", filters, null);
                    return true;
                }
                case AWAIT_CUSTOM -> {
                    String evName = String.valueOf(instruction.getArg(0));
                    @SuppressWarnings("unchecked")
                    java.util.List<ScriptNode> filters = instruction.getArgCount() >= 2 && instruction.getArg(1) instanceof java.util.List
                            ? (java.util.List<ScriptNode>) instruction.getArg(1) : java.util.List.of();
                    beginCustomEventAwait(evName, filters, null);
                    return waitType == WaitType.CUSTOM_EVENT;
                }
                case AWAIT_REL_GIFT -> {
                    ScriptNode pNode = (ScriptNode) instruction.getArg(0);
                    ScriptNode filter2Node = instruction.getArgCount() >= 2 && instruction.getArg(1) != null ? (ScriptNode) instruction.getArg(1) : null;
                    ScriptNode filter3Node = instruction.getArgCount() >= 3 && instruction.getArg(2) != null ? (ScriptNode) instruction.getArg(2) : null;
                    Object playerArg = evaluateExpression(pNode);
                    PlayerFilter playerFilter = buildPlayerFilter(playerArg);
                    if (playerFilter != null) {
                        String filter2 = filter2Node != null ? String.valueOf(evaluateExpression(filter2Node)) : null;
                        String filter3 = filter3Node != null ? String.valueOf(evaluateExpression(filter3Node)) : null;
                        String[] npcOut = new String[1];
                        String[] itemOut = new String[1];
                        applyRelGiftWaitFilters(filter2, filter3, npcOut, itemOut);
                        waitRelGiftPlayers = playerFilter;
                        waitRelGiftNpcId = npcOut[0];
                        waitRelGiftItemId = itemOut[0];
                        waitType = WaitType.REL_GIFT;
                        return true;
                    }
                    LOGGER.warn("[Script: {}] await relGift: unknown player", script.getName());
                }
                case AWAIT_REL_REP_CHANGE, AWAIT_REL_TALK -> {
                    ScriptNode pNode = (ScriptNode) instruction.getArg(0);
                    ScriptNode npcNode = instruction.getArgCount() >= 2 && instruction.getArg(1) != null ? (ScriptNode) instruction.getArg(1) : null;
                    Object playerArg = evaluateExpression(pNode);
                    PlayerFilter playerFilter = buildPlayerFilter(playerArg);
                    if (playerFilter != null) {
                        waitRelSimplePlayers = playerFilter;
                        waitRelSimpleNpcId = npcNode != null ? String.valueOf(evaluateExpression(npcNode)) : null;
                        waitType = instruction.getOpcode() == CompiledScript.Opcode.AWAIT_REL_REP_CHANGE
                                ? WaitType.REL_REP_CHANGE : WaitType.REL_TALK;
                        return true;
                    }
                    LOGGER.warn("[Script: {}] await rel event: unknown player", script.getName());
                }
                case AWAIT_REL_BUTTON -> {
                    ScriptNode pNode = (ScriptNode) instruction.getArg(0);
                    ScriptNode btnNode = instruction.getArgCount() >= 2 && instruction.getArg(1) != null ? (ScriptNode) instruction.getArg(1) : null;
                    ScriptNode npcNode = instruction.getArgCount() >= 3 && instruction.getArg(2) != null ? (ScriptNode) instruction.getArg(2) : null;
                    Object playerArg = evaluateExpression(pNode);
                    PlayerFilter playerFilter = buildPlayerFilter(playerArg);
                    if (playerFilter != null) {
                        waitRelSimplePlayers = playerFilter;
                        waitRelSimpleBtnId = btnNode != null ? String.valueOf(evaluateExpression(btnNode)) : null;
                        waitRelSimpleNpcId = npcNode != null ? String.valueOf(evaluateExpression(npcNode)) : null;
                        waitType = WaitType.REL_BUTTON;
                        return true;
                    }
                    LOGGER.warn("[Script: {}] await relButton: unknown player", script.getName());
                }
                case ASYNC_START -> {
                    @SuppressWarnings("unchecked")
                    List<CompiledScript.Instruction> bodyInstr = (List<CompiledScript.Instruction>) instruction.getArg(1);
                    String id = resolveAsyncTaskId(instruction.getArg(0));
                    asyncTasks.put(id, new AsyncTask(id, bodyInstr));
                }
                case AWAIT_TASK -> {
                    ScriptNode idNode = (ScriptNode) instruction.getArg(0);
                    waitTaskId = String.valueOf(evaluateExpression(idNode));
                    waitType = WaitType.WAIT_TASK;
                    return true;
                }
                case AWAIT_UI_CLICK -> {
                    ScriptNode pNode = (ScriptNode) instruction.getArg(0);
                    Object playerArg = evaluateExpression(pNode);
                    PlayerFilter playerFilter = buildPlayerFilter(playerArg);
                    if (playerFilter != null) {
                        waitUiPlayers = playerFilter;
                        waitType = WaitType.UI_CLICK;
                        return true;
                    }
                    LOGGER.warn("[Script: {}] await ui_click: unknown player", script.getName());
                }
                case AWAIT_UI_DROP -> {
                    ScriptNode pNode = (ScriptNode) instruction.getArg(0);
                    Object playerArg = evaluateExpression(pNode);
                    PlayerFilter playerFilter = buildPlayerFilter(playerArg);
                    if (playerFilter != null) {
                        waitUiPlayers = playerFilter;
                        waitType = WaitType.UI_DROP;
                        return true;
                    }
                    LOGGER.warn("[Script: {}] await uiDrop: unknown player", script.getName());
                }
                case AWAIT_UI_CLOSE -> {
                    ScriptNode pNode = (ScriptNode) instruction.getArg(0);
                    Object playerArg = evaluateExpression(pNode);
                    PlayerFilter playerFilter = buildPlayerFilter(playerArg);
                    if (playerFilter != null) {
                        waitUiPlayers = playerFilter;
                        waitType = WaitType.UI_CLOSE;
                        return true;
                    }
                }
                case AWAIT_UI_INPUT -> {
                    ScriptNode pNode = (ScriptNode) instruction.getArg(0);
                    ScriptNode wNode = (ScriptNode) instruction.getArg(1);
                    Object playerArg = evaluateExpression(pNode);
                    PlayerFilter playerFilter = buildPlayerFilter(playerArg);
                    if (playerFilter != null) {
                        waitUiPlayers = playerFilter;
                        uiInputWidgetId = wNode != null ? String.valueOf(evaluateExpression(wNode)) : null;
                        waitType = WaitType.UI_INPUT;
                        return true;
                    }
                }
                case AWAIT_UI_TOUCH -> {
                    ScriptNode pNode = (ScriptNode) instruction.getArg(0);
                    ScriptNode id1Node = (ScriptNode) instruction.getArg(1);
                    ScriptNode id2Node = (ScriptNode) instruction.getArg(2);
                    Object playerArg = evaluateExpression(pNode);
                    PlayerFilter playerFilter = buildPlayerFilter(playerArg);
                    if (playerFilter != null) {
                        waitUiOverlapPlayers = playerFilter;
                        net.minecraft.server.level.ServerPlayer sp = findOnlinePlayerForFilter(playerFilter);
                        if (sp != null) {
                            waitUiOverlapId1 = String.valueOf(evaluateExpression(id1Node));
                            waitUiOverlapId2 = String.valueOf(evaluateExpression(id2Node));
                            uiOverlapMet = false;
                            
                            org.zonarstudio.spraute_engine.network.ModNetwork.CHANNEL.send(
                                net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> sp),
                                new org.zonarstudio.spraute_engine.network.SprauteUiMonitorOverlapPacket(waitUiOverlapId1, waitUiOverlapId2, true)
                            );
                            
                            waitType = WaitType.UI_OVERLAP;
                            return true;
                        }
                    }
                }
                case AWAIT_HITBOX_TOUCH, AWAIT_HITBOX_EXIT -> {
                    ScriptNode pNode = (ScriptNode) instruction.getArg(0);
                    ScriptNode sceneNode = instruction.getArgCount() > 1 ? (ScriptNode) instruction.getArg(1) : null;
                    ScriptNode triggerNode = instruction.getArgCount() > 2 ? (ScriptNode) instruction.getArg(2) : null;
                    Object playerArg = evaluateExpression(pNode);
                    PlayerFilter playerFilter = buildPlayerFilter(playerArg);
                    if (playerFilter != null) {
                        waitHitboxPlayers = playerFilter;
                        waitHitboxScene = sceneNode != null ? String.valueOf(evaluateExpression(sceneNode)) : null;
                        waitHitboxTriggerId = triggerNode != null ? String.valueOf(evaluateExpression(triggerNode)) : null;
                        // 2-arg form: await hitboxTouch(player, "triggerId") — treat as trigger filter
                        if (triggerNode == null && sceneNode != null) {
                            String only = waitHitboxScene;
                            if (only != null && !only.isBlank() && !"*".equals(only.trim())
                                    && org.zonarstudio.spraute_engine.scene.SceneRuntime.getActiveState(only.trim()) == null) {
                                waitHitboxTriggerId = only;
                                waitHitboxScene = null;
                            }
                        }
                        hitboxEventMet = false;
                        waitType = instruction.getOpcode() == CompiledScript.Opcode.AWAIT_HITBOX_EXIT
                                ? WaitType.HITBOX_EXIT : WaitType.HITBOX_TOUCH;
                        return true;
                    }
                }
                case AWAIT_PLAYER_ACTION -> {
                    ScriptNode pNode = (ScriptNode) instruction.getArg(0);
                    ScriptNode actionNode = (ScriptNode) instruction.getArg(1);
                    ScriptNode targetNode = instruction.getArgCount() >= 3 ? (ScriptNode) instruction.getArg(2) : null;
                    
                    Object playerArg = evaluateExpression(pNode);
                    PlayerFilter playerFilter = buildPlayerFilter(playerArg);
                    if (playerFilter != null) {
                        waitPlayerActionPlayers = playerFilter;
                        waitPlayerActionType = String.valueOf(evaluateExpression(actionNode));
                        waitPlayerActionTarget = targetNode != null ? String.valueOf(evaluateExpression(targetNode)) : null;
                        playerActionMet = false;
                        waitType = WaitType.PLAYER_ACTION;
                        return true;
                    }
                }
                case AWAIT_DIMENSION -> {
                    ScriptNode pNode = (ScriptNode) instruction.getArg(0);
                    ScriptNode dimNode = (ScriptNode) instruction.getArg(1);
                    Object playerArg = evaluateExpression(pNode);
                    PlayerFilter playerFilter = buildPlayerFilter(playerArg);
                    if (playerFilter != null) {
                        waitDimensionPlayers = playerFilter;
                        waitDimensionId = String.valueOf(evaluateExpression(dimNode));
                        dimensionEventMet = false;
                        waitType = WaitType.DIMENSION;
                        return true;
                    }
                }
                case AWAIT_JOIN -> {
                    ScriptNode pNode = (ScriptNode) instruction.getArg(0);
                    boolean firstOnly = Boolean.TRUE.equals(instruction.getArg(1));
                    Object playerArg = evaluateExpression(pNode);
                    PlayerFilter playerFilter = buildPlayerFilter(playerArg);
                    if (playerFilter != null) {
                        waitJoinPlayers = playerFilter;
                        waitFirstJoinOnly = firstOnly;
                        joinEventMet = false;
                        waitJoinPlayerUuid = null;
                        waitType = WaitType.JOIN;
                        return true;
                    }
                }
                case AWAIT_LEAVE -> {
                    Object playerArg = evaluateExpression((ScriptNode) instruction.getArg(0));
                    PlayerFilter playerFilter = buildPlayerFilter(playerArg);
                    if (playerFilter != null) {
                        waitLeavePlayers = playerFilter;
                        leaveEventMet = false;
                        waitLeavePlayer = null;
                        waitType = WaitType.LEAVE;
                        return true;
                    }
                }
                case AWAIT_POSITION -> {
                    ScriptNode pNode = (ScriptNode) instruction.getArg(0);
                    Object playerArg = evaluateExpression(pNode);
                    PlayerFilter playerFilter = buildPlayerFilter(playerArg);
                    if (playerFilter != null) {
                        waitPositionPlayers = playerFilter;
                        waitPositionX = ((Number) evaluateExpression((ScriptNode) instruction.getArg(1))).doubleValue();
                        waitPositionY = ((Number) evaluateExpression((ScriptNode) instruction.getArg(2))).doubleValue();
                        waitPositionZ = ((Number) evaluateExpression((ScriptNode) instruction.getArg(3))).doubleValue();
                        waitPositionRadius = instruction.getArg(4) != null ? ((Number) evaluateExpression((ScriptNode) instruction.getArg(4))).doubleValue() : 1.5;
                        waitType = WaitType.POSITION;
                        return true;
                    }
                }
                case AWAIT_INVENTORY -> {
                    ScriptNode pNode = (ScriptNode) instruction.getArg(0);
                    Object playerArg = evaluateExpression(pNode);
                    PlayerFilter playerFilter = buildPlayerFilter(playerArg);
                    if (playerFilter != null) {
                        waitInventoryPlayers = playerFilter;
                        waitInventoryItemId = String.valueOf(evaluateExpression((ScriptNode) instruction.getArg(1)));
                        waitInventoryCount = instruction.getArg(2) != null ? ((Number) evaluateExpression((ScriptNode) instruction.getArg(2))).intValue() : 0;
                        waitType = WaitType.INVENTORY;
                        return true;
                    }
                }
                case AWAIT_CLICK_BLOCK, AWAIT_BREAK_BLOCK, AWAIT_PLACE_BLOCK, AWAIT_OPEN_CHEST, AWAIT_OPEN_DOOR, AWAIT_USE_BLOCK -> {
                    List<ScriptNode> args = (List<ScriptNode>) instruction.getArg(0);
                    Object playerArg = evaluateExpression(args.get(0));
                    PlayerFilter playerFilter = buildPlayerFilter(playerArg);
                    if (playerFilter != null) {
                        waitBlockPlayers = playerFilter;
                        waitBlockId = null;
                        waitBlockPos = null;
                        waitBlockDim = null;
                        // Args: player [, blockId] [, x, y, z [, blockId]] [, dimension]
                        // РС‰РµРј СЃС‚СЂРѕРєРѕРІС‹Р№ Р°СЂРіСѓРјРµРЅС‚ СЃ ':' РёР»Рё СЃС‚Р°РЅРґР°СЂС‚РЅРѕРµ РёРјСЏ РєР°Рє dimension РІ РєРѕРЅС†Рµ
                        List<Object> evaled = new ArrayList<>();
                        for (int _i = 1; _i < args.size(); _i++) evaled.add(evaluateExpression(args.get(_i)));
                        // Check last arg for dimension keyword
                        if (!evaled.isEmpty()) {
                            Object last = evaled.get(evaled.size()-1);
                            if (last instanceof String s && (s.contains(":") || s.equals("overworld") || s.equals("nether") || s.equals("the_end"))) {
                                waitBlockDim = s;
                                evaled = evaled.subList(0, evaled.size()-1);
                            }
                        }
                        if (evaled.size() == 1) {
                            waitBlockId = String.valueOf(evaled.get(0));
                        } else if (evaled.size() >= 3) {
                            int x = ((Number) evaled.get(0)).intValue();
                            int y = ((Number) evaled.get(1)).intValue();
                            int z = ((Number) evaled.get(2)).intValue();
                            waitBlockPos = new net.minecraft.core.BlockPos(x, y, z);
                            if (evaled.size() >= 4) waitBlockId = String.valueOf(evaled.get(3));
                        }
                        if (instruction.getOpcode() == CompiledScript.Opcode.AWAIT_CLICK_BLOCK) waitType = WaitType.CLICK_BLOCK;
                        else if (instruction.getOpcode() == CompiledScript.Opcode.AWAIT_BREAK_BLOCK) waitType = WaitType.BREAK_BLOCK;
                        else if (instruction.getOpcode() == CompiledScript.Opcode.AWAIT_PLACE_BLOCK) waitType = WaitType.PLACE_BLOCK;
                        else if (instruction.getOpcode() == CompiledScript.Opcode.AWAIT_OPEN_CHEST) waitType = WaitType.OPEN_CHEST;
                        else if (instruction.getOpcode() == CompiledScript.Opcode.AWAIT_OPEN_DOOR) waitType = WaitType.OPEN_DOOR;
                        else waitType = WaitType.USE_BLOCK;
                        blockEventMet = false;
                        return true;
                    }
                }
                case AWAIT_CHAT -> {
                    List<ScriptNode> args = (List<ScriptNode>) instruction.getArg(0);
                    Object playerArg = evaluateExpression(args.get(0));
                    PlayerFilter playerFilter = buildPlayerFilter(playerArg);
                    if (playerFilter != null) {
                        waitChatPlayers = playerFilter;
                        
                        Object msgs = evaluateExpression(args.get(1));
                        waitChatMessages = new ArrayList<>();
                        if (msgs instanceof List list) {
                            for (Object o : list) waitChatMessages.add(String.valueOf(o));
                        } else {
                            waitChatMessages.add(String.valueOf(msgs));
                        }

                        if (args.size() > 2) waitChatIgnoreCase = (Boolean) evaluateExpression(args.get(2));
                        if (args.size() > 3) waitChatIgnorePunct = (Boolean) evaluateExpression(args.get(3));
                        
                        chatEventMet = false;
                        chatMatchedMessage = "";
                        waitType = WaitType.CHAT;
                        return true;
                    }
                }
                case STOP_TASK -> {
                    ScriptNode idNode = (ScriptNode) instruction.getArg(0);
                    String id = String.valueOf(evaluateExpression(idNode));
                    AsyncTask t = asyncTasks.get(id);
                    if (t != null) {
                        t.cancelled = true;
                        asyncTasks.remove(id);
                    }
                }
                case FUN_DEF -> {
                    String name = (String) instruction.getArg(0);
                    List<String> params = (List<String>) instruction.getArg(1);
                    List<CompiledScript.Instruction> bodyInstr = (List<CompiledScript.Instruction>) instruction.getArg(2);
                    userFunctions.put(name, new UserFunction(params, bodyInstr));
                }
                case INCLUDE -> {
                    String includeName = (String) instruction.getArg(0);
                    if (!importedScripts.contains(includeName)) {
                        importedScripts.add(includeName);
                        ScriptExecutor.this.ensureImportedScriptRunning(includeName, source, script.getName());
                        LOGGER.info("[Script: {}] import '{}' registered (functions resolved lazily)", script.getName(), includeName);
                    }
                }
                case RETURN -> {
                    // Return in top-level script just finishes execution
                    finished = true;
                    return true;
                }
                case REGISTER_ON -> {
                    String eventName = (String) instruction.getArg(0);
                    List<ScriptNode> eventArgNodes = (List<ScriptNode>) instruction.getArg(1);
                    String handlerId = (String) instruction.getArg(2);
                    List<CompiledScript.Instruction> bodyInstr = (List<CompiledScript.Instruction>) instruction.getArg(3);

                    List<Object> evaluatedArgs = new ArrayList<>();
                    for (ScriptNode node : eventArgNodes) {
                        evaluatedArgs.add(evaluateEventArg(eventName, node));
                    }
                    eventHandlers.put(handlerId, new EventHandler(eventName, evaluatedArgs, bodyInstr));
                    seedPickupHandlerBaseline(handlerId, eventName, evaluatedArgs);
                }
                case REGISTER_EVERY -> {
                    ScriptNode intervalNode = (ScriptNode) instruction.getArg(0);
                    String handlerId = (String) instruction.getArg(1);
                    List<CompiledScript.Instruction> bodyInstr = (List<CompiledScript.Instruction>) instruction.getArg(2);

                    double interval = ((Number) evaluateExpression(intervalNode)).doubleValue();
                    timerHandlers.put(handlerId, new TimerHandler(interval, bodyInstr));
                }
                case STOP_HANDLER -> {
                    String handlerId = (String) instruction.getArg(0);
                    if (eventHandlers.containsKey(handlerId)) {
                        eventHandlers.get(handlerId).active = false;
                    }
                    if (timerHandlers.containsKey(handlerId)) {
                        timerHandlers.get(handlerId).active = false;
                    }
                }
            }
            return false;
        }

        private void executeCall(CompiledScript.Instruction instruction) {
            String functionName = (String) instruction.getArg(0);
            List<ScriptNode> argsNodes = (List<ScriptNode>) instruction.getArg(1);
            
            List<Object> args = new ArrayList<>();
            for (ScriptNode argNode : argsNodes) {
                args.add(evaluateExpression(argNode));
            }

            // run_script(name) вЂ” РІС‹Р·РІР°С‚СЊ РґСЂСѓРіРѕР№ СЃРєСЂРёРїС‚ СЃ РїРµСЂРµРґР°С‡РµР№ РїРµСЂРµРјРµРЅРЅС‹С…
            if (functionName.equals("runScript")) {
                if (!args.isEmpty()) {
                    String scriptName = String.valueOf(args.get(0));
                    Map<String, Object> vars = new HashMap<>(variables);
                    org.zonarstudio.spraute_engine.script.ScriptManager.getInstance().run(scriptName, source, vars);
                }
                return;
            }

            // Check user-defined functions (own + imported)
            UserFunction uf = resolveFunction(functionName);
            if (uf != null) {
                callUserFunction(functionName, args);
                return;
            }

            var function = org.zonarstudio.spraute_engine.script.function.FunctionRegistry.get(functionName);
            if (function != null) {
                function.execute(
                        org.zonarstudio.spraute_engine.script.function.FunctionArgs.coerce(
                                function, unwrapPlayerNodeArgs(args)),
                        source, context);
            } else {
                LOGGER.error("[Script: {}] Unknown function: {}", script.getName(), functionName);
            }
        }

        private static boolean callMethodShouldBlock(CompiledScript.Instruction instruction, boolean contextBlocking) {
            if (instruction.getArgCount() >= 4 && instruction.getArg(3) instanceof Boolean b) {
                return b;
            }
            return contextBlocking;
        }

        private List<Object> unwrapPlayerNodeArgs(List<Object> args) {
            if (args == null || args.isEmpty()) return args;
            List<Object> normalized = new ArrayList<>(args.size());
            for (Object arg : args) {
                if (arg instanceof org.zonarstudio.spraute_engine.scene.SceneNodeView nodeView
                        && nodeView.getEntity(source) instanceof net.minecraft.world.entity.player.Player player) {
                    normalized.add(player);
                } else {
                    normalized.add(arg);
                }
            }
            return normalized;
        }

        private boolean beginMoveToWait(org.zonarstudio.spraute_engine.entity.SprauteNpcEntity npc,
                                        double x, double y, double z, double speed,
                                        AsyncTask taskScope) {
            if (taskScope != null) {
                bindWaitNpc(npc, taskScope);
                taskScope.waitMoveTargetX = x;
                taskScope.waitMoveTargetY = y;
                taskScope.waitMoveTargetZ = z;
                taskScope.waitMoveSpeed = speed;
                taskScope.waitType = WaitType.MOVE_TO;
            } else {
                bindWaitNpc(npc, null);
                waitMoveTargetX = x;
                waitMoveTargetY = y;
                waitMoveTargetZ = z;
                waitMoveSpeed = speed;
                waitType = WaitType.MOVE_TO;
            }
            return true;
        }

        private java.util.List<net.minecraft.world.phys.Vec3> resolveRouteWaypoints(Object arg) {
            return resolveRouteWaypoints(arg, false);
        }

        private java.util.List<net.minecraft.world.phys.Vec3> resolveRouteWaypoints(Object arg, boolean fly) {
            org.zonarstudio.spraute_engine.scene.SceneActiveState state = null;
            org.zonarstudio.spraute_engine.scene.SceneNode route = null;
            if (arg instanceof org.zonarstudio.spraute_engine.scene.SceneNodeView view) {
                state = org.zonarstudio.spraute_engine.scene.SceneRuntime.getActiveState(view.getSceneId());
                if (state == null) {
                    state = org.zonarstudio.spraute_engine.script.SceneScriptSpawn.resolveState(
                            currentVariables(), java.util.Map.of());
                }
                if (state != null) {
                    org.zonarstudio.spraute_engine.scene.SceneNode node = state.document.findNode(view.getNodeId());
                    if (node != null && node.type == org.zonarstudio.spraute_engine.scene.SceneNodeType.ROUTE) {
                        route = node;
                    }
                }
            } else if (arg != null) {
                state = org.zonarstudio.spraute_engine.script.SceneScriptSpawn.resolveState(
                        currentVariables(), java.util.Map.of());
                if (state != null) {
                    org.zonarstudio.spraute_engine.scene.SceneNode node =
                            state.document.findNode(String.valueOf(arg).trim());
                    if (node != null && node.type == org.zonarstudio.spraute_engine.scene.SceneNodeType.ROUTE) {
                        route = node;
                    }
                }
            }
            if (state == null || route == null) return java.util.List.of();
            // Ground walk keeps corners; flying follows the smoothed polyline.
            if (fly) {
                return org.zonarstudio.spraute_engine.scene.SceneRouteOps.samplePolyline(state.document, route);
            }
            return org.zonarstudio.spraute_engine.scene.SceneRouteOps.followWaypoints(state.document, route);
        }

        private double followRouteSpeed(java.util.List<Object> args) {
            if (args.size() >= 2 && args.get(args.size() - 1) instanceof Number number) {
                return number.doubleValue();
            }
            return 1.0;
        }

        private boolean startFollowRoute(org.zonarstudio.spraute_engine.entity.SprauteNpcEntity npc,
                                         java.util.List<Object> args, boolean loop, boolean fly,
                                         boolean blocking, AsyncTask taskScope) {
            if (args.isEmpty()) return false;
            npc.followRoute(resolveRouteWaypoints(args.get(0), fly), loop, fly, followRouteSpeed(args));
            if (blocking && !loop && npc.isFollowingRoute()) {
                if (taskScope != null) {
                    bindWaitNpc(npc, taskScope);
                    taskScope.waitType = WaitType.FOLLOW_ROUTE;
                } else {
                    bindWaitNpc(npc, null);
                    waitType = WaitType.FOLLOW_ROUTE;
                }
                return true;
            }
            return false;
        }

        /** @return true if script should pause (e.g. move_to when blocking) */
        private boolean executeCallMethod(CompiledScript.Instruction instruction, boolean blocking, AsyncTask taskScope) {
            blocking = callMethodShouldBlock(instruction, blocking);
            ScriptNode objNode = (ScriptNode) instruction.getArg(0);
            String methodName = (String) instruction.getArg(1);
            List<ScriptNode> argsNodes = (List<ScriptNode>) instruction.getArg(2);
            
            List<Object> args = new ArrayList<>();
            for (ScriptNode argNode : argsNodes) {
                args.add(evaluateExpression(argNode));
            }

            Object obj = evaluateExpression(objNode);
            
            if (obj == null && objNode instanceof ScriptNode.IdentifierNode idNode) {
                String idStr = idNode.getName();
                if (org.zonarstudio.spraute_engine.entity.NpcManager.get(idStr) != null
                        || org.zonarstudio.spraute_engine.entity.ModelManager.get(idStr) != null
                        || resolveEntity(idStr) != null) {
                    obj = idStr;
                }
            }

            Object curveCall = org.zonarstudio.spraute_engine.scene.custom.ScriptCurve.invoke(obj, methodName, args);
            if (curveCall != org.zonarstudio.spraute_engine.scene.custom.ScriptCurve.UNHANDLED) {
                return false;
            }

            if (obj instanceof String entityId) {
                net.minecraft.world.entity.Entity resolved = resolveEntity(entityId);
                if (resolved != null) obj = resolved;
            }

            // ===== NpcGroup method dispatch =====
            if (obj instanceof org.zonarstudio.spraute_engine.entity.NpcGroup group
                    && source.getLevel() != null && !source.getLevel().isClientSide) {
                net.minecraft.server.level.ServerLevel sl = (net.minecraft.server.level.ServerLevel) source.getLevel();
                String method = methodName != null ? methodName.toLowerCase() : "";
                java.util.List<org.zonarstudio.spraute_engine.entity.SprauteNpcEntity> members = group.resolveNpcs(sl);
                for (org.zonarstudio.spraute_engine.entity.SprauteNpcEntity npc : members) {
                    executeNpcGroupMethod(npc, method, args, blocking, null);
                }
                return false;
            }

            if (obj instanceof org.zonarstudio.spraute_engine.scene.SceneNodeView sceneNode) {
                String m = methodName != null ? methodName.toLowerCase() : "";
                Object sceneResult = sceneNode.invokeMethod(m, args, source);
                if (!org.zonarstudio.spraute_engine.scene.SceneNodeView.isUnhandled(sceneResult)) {
                    return false;
                }
                // Same API as create npc / model: dispatch on the spawned entity.
                net.minecraft.world.entity.Entity spawned = sceneNode.getEntity(source);
                if (spawned == null) return false;
                obj = spawned;
            }
            if (obj instanceof org.zonarstudio.spraute_engine.scene.SceneView scene) {
                scene.invokeMethod(methodName != null ? methodName.toLowerCase() : "", args, source);
                return false;
            }

            if (obj instanceof net.minecraft.world.entity.player.Player player) {
                Object skinResult = org.zonarstudio.spraute_engine.scene.PlayerSkinOps.invokePlayer(
                        player, methodName, args);
                if (skinResult != org.zonarstudio.spraute_engine.scene.PlayerSkinOps.UNHANDLED) {
                    return false;
                }
            }

            if (obj instanceof org.zonarstudio.spraute_engine.entity.SprauteNpcEntity npc) {
                String method = methodName != null ? methodName.toLowerCase() : "";
                switch (method) {
                    case "playonce" -> {
                        if (!args.isEmpty()) {
                            boolean additive = args.size() < 2 || isAdditiveAnimationArg(args.get(1));
                            npc.playOnce(String.valueOf(args.get(0)), additive);
                        }
                    }
                    case "playloop" -> {
                        if (!args.isEmpty()) {
                            boolean additive = args.size() < 2 || isAdditiveAnimationArg(args.get(1));
                            npc.playLoop(String.valueOf(args.get(0)), additive);
                        }
                    }
                    case "playfreeze" -> {
                        if (!args.isEmpty()) {
                            boolean additive = args.size() < 2 || isAdditiveAnimationArg(args.get(1));
                            npc.playFreeze(String.valueOf(args.get(0)), additive);
                        }
                    }
                    case "stopoverlay" -> npc.stopOverlayAnimation();
                    case "stop" -> {
                        if (!args.isEmpty()) npc.stopOverlayAnimation(String.valueOf(args.get(0)));
                    }
                    case "sethitbox" -> {
                        if (args.size() >= 2) {
                            float w = ((Number) args.get(0)).floatValue();
                            float h = ((Number) args.get(1)).floatValue();
                            if (args.size() >= 5) {
                                float ox = ((Number) args.get(2)).floatValue();
                                float oy = ((Number) args.get(3)).floatValue();
                                float oz = ((Number) args.get(4)).floatValue();
                                npc.setHitbox(w, h, ox, oy, oz);
                            } else {
                                npc.setHitbox(w, h);
                            }
                        }
                    }
                    case "sethitboxoffset", "set_hitbox_offset" -> {
                        if (args.size() >= 3) {
                            npc.setHitboxOffset(
                                    ((Number) args.get(0)).floatValue(),
                                    ((Number) args.get(1)).floatValue(),
                                    ((Number) args.get(2)).floatValue());
                        }
                    }
                    case "resethitbox", "reset_hitbox" -> npc.resetHitbox();
                    case "sethitboxpreset", "set_hitbox_preset" -> {
                        if (!args.isEmpty()) npc.setHitboxPreset(String.valueOf(args.get(0)));
                    }
                    case "addbonehitbox", "add_bone_hitbox" -> {
                        if (args.size() >= 4) {
                            if (args.size() >= 8 && args.get(0) instanceof String idArg && args.get(1) instanceof String boneArg) {
                                npc.addBoneHitbox(
                                        String.valueOf(idArg), String.valueOf(boneArg),
                                        ((Number) args.get(2)).floatValue(),
                                        ((Number) args.get(3)).floatValue(),
                                        ((Number) args.get(4)).floatValue(),
                                        ((Number) args.get(5)).floatValue(),
                                        ((Number) args.get(6)).floatValue(),
                                        ((Number) args.get(7)).floatValue());
                            } else {
                                npc.addBoneHitbox(
                                        String.valueOf(args.get(0)),
                                        ((Number) args.get(1)).floatValue(),
                                        ((Number) args.get(2)).floatValue(),
                                        ((Number) args.get(3)).floatValue());
                            }
                        }
                    }
                    case "removebonehitbox", "remove_bone_hitbox" -> {
                        if (!args.isEmpty()) npc.removeBoneHitbox(String.valueOf(args.get(0)));
                    }
                    case "clearbonehitboxes", "clear_bone_hitboxes" -> npc.clearBoneHitboxes();
                    case "showhitboxdebug", "show_hitbox_debug" -> {
                        if (!args.isEmpty()) npc.setShowHitboxDebug(isTruthy(args.get(0)));
                    }
                    case "setflying" -> {
                        if (!args.isEmpty()) {
                            boolean flying = isTruthy(args.get(0));
                            npc.setFlying(flying);
                        }
                    }
                    case "setflyidleanim" -> {
                        if (!args.isEmpty()) {
                            npc.setFlyIdleAnim(String.valueOf(args.get(0)));
                        }
                    }
                    case "setflywalkanim" -> {
                        if (!args.isEmpty()) {
                            npc.setFlyWalkAnim(String.valueOf(args.get(0)));
                        }
                    }
                    case "flyto", "fly_to" -> {
                        if (args.size() >= 3 && args.get(0) instanceof Number) {
                            double x = ((Number) args.get(0)).doubleValue();
                            double y = ((Number) args.get(1)).doubleValue();
                            double z = ((Number) args.get(2)).doubleValue();
                            double speed = args.size() >= 4 ? ((Number) args.get(3)).doubleValue() : 1.0;
                            npc.flyTo(x, y, z, speed);
                            if (blocking) {
                                return beginMoveToWait(npc, x, y, z, speed, taskScope);
                            }
                        } else if (!args.isEmpty()) {
                            net.minecraft.world.entity.Entity target = resolveEntity(args.get(0));
                            double speed = args.size() >= 2 && args.get(1) instanceof Number n ? n.doubleValue() : 1.0;
                            if (target != null) {
                                npc.flyToEntity(target, speed);
                                if (blocking) {
                                    if (taskScope != null) {
                                        taskScope.waitEntityUuid = npc.getUUID();
                                        taskScope.waitFollowTargetUuid = target.getUUID();
                                        taskScope.waitFollowStopDistance = 2.0;
                                        taskScope.waitType = WaitType.FOLLOW;
                                    } else {
                                        waitEntityUuid = npc.getUUID();
                                        waitFollowTargetUuid = target.getUUID();
                                        waitFollowStopDistance = 2.0;
                                        waitType = WaitType.FOLLOW;
                                    }
                                    return true;
                                }
                            }
                        }
                    }
                    case "alwaysflyto", "always_fly_to" -> {
                        double speed = 1.0;
                        if (args.size() >= 3 && args.get(0) instanceof Number) {
                            double lx = ((Number) args.get(0)).doubleValue();
                            double ly = ((Number) args.get(1)).doubleValue();
                            double lz = ((Number) args.get(2)).doubleValue();
                            if (args.size() >= 4 && args.get(3) instanceof Number) speed = ((Number) args.get(3)).doubleValue();
                            npc.alwaysFlyTo(lx, ly, lz, speed);
                        } else if (!args.isEmpty()) {
                            net.minecraft.world.entity.Entity target = resolveEntity(args.get(0));
                            if (args.size() >= 2 && args.get(1) instanceof Number) speed = ((Number) args.get(1)).doubleValue();
                            if (target != null) npc.alwaysFlyToEntity(target, speed);
                        }
                    }
                    case "setswimming" -> {
                        if (!args.isEmpty()) {
                            boolean swimming = isTruthy(args.get(0));
                            npc.setSwimmingScript(swimming);
                        }
                    }
                    case "setswimidleanim" -> {
                        if (!args.isEmpty()) {
                            npc.setSwimIdleAnim(String.valueOf(args.get(0)));
                        }
                    }
                    case "setswimwalkanim" -> {
                        if (!args.isEmpty()) {
                            npc.setSwimWalkAnim(String.valueOf(args.get(0)));
                        }
                    }
                    case "setdeathanim" -> {
                        if (!args.isEmpty()) {
                            npc.setDeathAnim(String.valueOf(args.get(0)));
                        }
                    }
                    case "setadditiveweight" -> {
                        if (!args.isEmpty()) {
                            float w = ((Number) args.get(0)).floatValue();
                            npc.setAdditiveWeight(net.minecraft.util.Mth.clamp(w, 0f, 1f));
                        }
                    }
                    case "moveTo", "moveto" -> {
                        if (args.size() >= 3) {
                            double x = ((Number) args.get(0)).doubleValue();
                            double y = ((Number) args.get(1)).doubleValue();
                            double z = ((Number) args.get(2)).doubleValue();
                            double speed = args.size() >= 4 && args.get(3) instanceof Number n
                                    ? n.doubleValue() : 1.0;
                            boolean faceWalk = args.size() < 5 || isTruthy(args.get(4));
                            LOGGER.info("[NPC-WALK] script {} moveTo goal=({},{},{}) speed={} faceWalk={} blocking={}",
                                    npc.getCustomName() != null ? npc.getCustomName().getString() : npc.getUUID(),
                                    x, y, z, speed, faceWalk, blocking);
                            npc.moveTo(x, y, z, speed, faceWalk);
                            if (blocking) {
                                return beginMoveToWait(npc, x, y, z, speed, taskScope);
                            }
                        }
                    }
                    case "always_move_to", "alwaysmoveto" -> {
                        double speed = 1.0;
                        if (args.size() >= 3 && args.get(0) instanceof Number) {
                            double lx = ((Number) args.get(0)).doubleValue();
                            double ly = ((Number) args.get(1)).doubleValue();
                            double lz = ((Number) args.get(2)).doubleValue();
                            if (args.size() >= 4 && args.get(3) instanceof Number n) speed = n.doubleValue();
                            boolean faceWalk = args.size() < 5 || isTruthy(args.get(4));
                            npc.alwaysMoveTo(lx, ly, lz, speed, faceWalk);
                        } else if (!args.isEmpty()) {
                            net.minecraft.world.entity.Entity target = resolveEntity(args.get(0));
                            if (args.size() >= 2 && args.get(1) instanceof Number n) speed = n.doubleValue();
                            boolean faceWalk = args.size() < 3 || isTruthy(args.get(2));
                            if (target != null) npc.alwaysMoveToEntity(target, speed, faceWalk);
                        }
                    }
                    case "stopMove", "stopmove" -> npc.stopMove();
                    case "followroute", "follow_route", "walkroute", "walk_route" -> {
                        if (startFollowRoute(npc, args, false, false, blocking, taskScope)) return true;
                    }
                    case "alwaysfollowroute", "always_follow_route", "alwayswalkroute", "always_walk_route" ->
                            startFollowRoute(npc, args, true, false, false, taskScope);
                    case "flyroute", "fly_route" -> {
                        if (startFollowRoute(npc, args, false, true, blocking, taskScope)) return true;
                    }
                    case "alwaysflyroute", "always_fly_route" ->
                            startFollowRoute(npc, args, true, true, false, taskScope);
                    case "setidleanim" -> {
                        if (!args.isEmpty()) npc.setIdleAnim(String.valueOf(args.get(0)));
                        else npc.setIdleAnim("");
                    }
                    case "setwalkanim" -> {
                        if (!args.isEmpty()) npc.setWalkAnim(String.valueOf(args.get(0)));
                        else npc.setWalkAnim("");
                    }
                    case "setitem" -> {
                        // setItem("right", "minecraft:diamond_sword") or setItem("right", "minecraft:diamond_sword", "{Enchantments:[...]}")
                        if (args.size() >= 2) {
                            String hand = String.valueOf(args.get(0));
                            String itemId = String.valueOf(args.get(1));
                            String nbt = args.size() >= 3 ? String.valueOf(args.get(2)) : null;
                            net.minecraft.world.item.ItemStack stack = resolveItemStack(itemId, nbt);
                            npc.setHandItem(hand, stack);
                        }
                    }
                    case "removeitem" -> {
                        // removeItem("right") or removeItem("left")
                        if (!args.isEmpty()) {
                            npc.clearHandItem(String.valueOf(args.get(0)));
                        } else {
                            npc.clearHandItem("right");
                            npc.clearHandItem("left");
                        }
                    }
                    case "addDrop", "adddrop" -> {
                        if (args.size() >= 1) {
                            String item = String.valueOf(args.get(0));
                            int min = args.size() >= 2 ? ((Number) args.get(1)).intValue() : 1;
                            int max = args.size() >= 3 ? ((Number) args.get(2)).intValue() : 1;
                            int chance = args.size() >= 4 ? ((Number) args.get(3)).intValue() : 100;
                            String nbt = args.size() >= 5 ? String.valueOf(args.get(4)) : null;
                            npc.customDrops.add(new org.zonarstudio.spraute_engine.registry.CustomDropRegistry.DropRule(item, min, max, chance, false, nbt));
                        }
                    }
                    case "dropItem", "dropitem", "drop", "throwItem", "throwitem", "throw" -> {
                        if (!args.isEmpty()) {
                            String itemStr = String.valueOf(args.get(0));
                            int count = args.size() >= 2 && args.get(1) instanceof Number n ? n.intValue() : 1;
                            npc.throwItem(itemStr, count);
                        }
                    }
                    case "attack", "attacktarget", "attack_target" -> {
                        net.minecraft.world.entity.Entity target = resolveEntity(args.isEmpty() ? null : args.get(0));
                        if (target != null) {
                            double speed = args.size() >= 2 && args.get(1) instanceof Number n ? n.doubleValue() : 1.0;
                            if (args.size() >= 3 && args.get(2) instanceof Number r) {
                                npc.attackEntity(target, speed, r.doubleValue());
                            } else {
                                npc.attackEntity(target, speed);
                            }
                        }
                    }
                    case "stopattack", "stop_attack" -> npc.stopAttack();
                    case "setattackanims", "set_attack_anims" -> {
                        if (args.isEmpty()) npc.setAttackAnims("attack");
                        else if (args.get(0) instanceof java.util.List<?> list) npc.setAttackAnims(list);
                        else {
                            String[] arr = new String[args.size()];
                            for (int i = 0; i < args.size(); i++) arr[i] = String.valueOf(args.get(i));
                            npc.setAttackAnims(arr);
                        }
                    }
                    case "setattackhand", "set_attack_hand" -> {
                        if (!args.isEmpty()) npc.setAttackHand(String.valueOf(args.get(0)));
                    }
                    case "setattackrange", "set_attack_range" -> {
                        if (!args.isEmpty() && args.get(0) instanceof Number n) npc.setAttackRange(n.doubleValue());
                    }
                    case "setattackcooldown", "set_attack_cooldown" -> {
                        if (!args.isEmpty() && args.get(0) instanceof Number n) npc.setAttackCooldown(n.intValue());
                    }
                    case "setattackhitdelay", "set_attack_hit_delay" -> {
                        if (!args.isEmpty() && args.get(0) instanceof Number n) npc.setAttackHitDelay(n.intValue());
                    }
                    case "remove" -> {
                        // Scene-owned NPCs must leave the live document, otherwise reconcile respawns them.
                        if (!org.zonarstudio.spraute_engine.scene.SceneRuntime.removeSceneEntity(npc)) {
                            npc.discard();
                        }
                    }
                    case "followUntil", "followuntil" -> {
                        net.minecraft.world.entity.Entity target = resolveEntity(args.size() >= 1 ? args.get(0) : null);
                        if (target != null && blocking) {
                            if (taskScope != null) {
                                taskScope.waitEntityUuid = npc.getUUID();
                                taskScope.waitFollowTargetUuid = target.getUUID();
                                taskScope.waitFollowStopDistance = args.size() >= 2 ? ((Number) args.get(1)).doubleValue() : 2.0;
                                npc.getNavigation().moveTo(target.getX(), target.getY(), target.getZ(), 1.0);
                                taskScope.waitType = WaitType.FOLLOW;
                            } else {
                                waitEntityUuid = npc.getUUID();
                                waitFollowTargetUuid = target.getUUID();
                                waitFollowStopDistance = args.size() >= 2 ? ((Number) args.get(1)).doubleValue() : 2.0;
                                npc.getNavigation().moveTo(target.getX(), target.getY(), target.getZ(), 1.0);
                                waitType = WaitType.FOLLOW;
                            }
                            return true;
                        } else if (target != null) {
                            npc.getNavigation().moveTo(target.getX(), target.getY(), target.getZ(), 1.0);
                        }
                    }
                    case "pickup_only_from", "pickuponlyfrom" -> {
                        net.minecraft.world.entity.Entity dropper = resolveEntity(args.size() >= 1 ? args.get(0) : null);
                        npc.setPickupDropperFilter(dropper != null ? dropper.getUUID() : null);
                    }
                    case "pickupAny", "pickupany" -> npc.clearPickupDropperFilter();
                    case "pickupMax", "pickupmax", "setPickupMax", "setpickupmax" -> {
                        if (args.size() >= 2) {
                            String item = String.valueOf(args.get(0));
                            int max = args.get(1) instanceof Number n ? n.intValue() : Integer.parseInt(String.valueOf(args.get(1)));
                            String nbt = args.size() >= 3 ? String.valueOf(args.get(2)) : null;
                            if ("null".equals(nbt) || (nbt != null && nbt.isEmpty())) nbt = null;
                            npc.setPickupMaxCount(item, nbt, max);
                        }
                    }
                    case "clearPickupMax", "clearpickupmax", "pickupMaxClear", "pickupmaxclear" -> {
                        if (args.isEmpty()) npc.clearPickupMaxCount();
                        else {
                            String item = String.valueOf(args.get(0));
                            String nbt = args.size() >= 2 ? String.valueOf(args.get(1)) : null;
                            if ("null".equals(nbt) || (nbt != null && nbt.isEmpty())) nbt = null;
                            npc.clearPickupMaxCount(item, nbt);
                        }
                    }
                    case "lookat" -> {
                        if (args.size() >= 3 && args.get(0) instanceof Number) {
                            double lx = ((Number) args.get(0)).doubleValue();
                            double ly = ((Number) args.get(1)).doubleValue();
                            double lz = ((Number) args.get(2)).doubleValue();
                            npc.lookAt(lx, ly, lz);
                        } else if (!args.isEmpty()) {
                            net.minecraft.world.entity.Entity target = resolveEntity(args.get(0));
                            if (target != null) npc.lookAtEntity(target);
                        }
                    }
                    case "alwayslookat" -> {
                        if (args.size() >= 3 && args.get(0) instanceof Number) {
                            double lx = ((Number) args.get(0)).doubleValue();
                            double ly = ((Number) args.get(1)).doubleValue();
                            double lz = ((Number) args.get(2)).doubleValue();
                            npc.alwaysLookAt(lx, ly, lz);
                        } else if (!args.isEmpty()) {
                            net.minecraft.world.entity.Entity target = resolveEntity(args.get(0));
                            if (target != null) npc.alwaysLookAtEntity(target);
                        }
                    }
                    case "stoplookat" -> npc.stopLook();
                    case "setheadlookunlimited", "set_head_look_unlimited" -> {
                        boolean unlimited = args.isEmpty() || !Boolean.FALSE.equals(args.get(0));
                        if (!args.isEmpty() && args.get(0) instanceof Boolean b) unlimited = b;
                        npc.setHeadLookUnlimited(unlimited);
                    }
                    case "setplayerskin" -> {
                        if (!args.isEmpty()) {
                            Object target = args.get(0);
                            if (target instanceof net.minecraft.server.level.ServerPlayer sp) {
                                npc.setPlayerSkinOverlay(sp.getUUID());
                            } else if (target instanceof net.minecraft.world.entity.Entity e) {
                                npc.setPlayerSkinOverlay(e.getUUID());
                            }
                        }
                    }
                    case "clearplayerskin" -> npc.clearPlayerSkinOverlay();
                    case "setheadbone" -> {
                        if (!args.isEmpty()) npc.setHeadBone(String.valueOf(args.get(0)));
                        else npc.setHeadBone("head");
                    }
                    case "countItem", "countitem" -> {
                        if (!args.isEmpty()) {
                            String itemId = String.valueOf(args.get(0));
                            if (args.size() >= 2) {
                                String nbt = String.valueOf(args.get(1));
                                if ("null".equals(nbt) || (nbt != null && nbt.isEmpty())) nbt = null;
                                npc.countItem(itemId, nbt);
                            } else {
                                npc.countItem(itemId);
                            }
                        }
                    }
                }
            }
            if (obj instanceof org.zonarstudio.spraute_engine.entity.SprauteModelEntity model) {
                String method = methodName != null ? methodName.toLowerCase() : "";
                if (GeoEntityScriptUtil.executeAnimMethod(model, method, args)) {
                    return false;
                }
                switch (method) {
                    case "teleport" -> {
                        if (args.size() >= 3) {
                            double tx = ((Number) args.get(0)).doubleValue();
                            double ty = ((Number) args.get(1)).doubleValue();
                            double tz = ((Number) args.get(2)).doubleValue();
                            model.teleportTo(tx, ty, tz);
                        }
                    }
                    case "remove" -> model.remove(net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
                }
            }
            if (obj instanceof java.util.List list) {
                String method = methodName != null ? methodName.toLowerCase() : "";
                switch (method) {
                    case "add" -> { list.add(args.isEmpty() ? null : args.get(0)); return false; }
                    case "remove" -> {
                        if (!args.isEmpty() && args.get(0) instanceof Number n) {
                            int idx = n.intValue();
                            if (idx >= 0 && idx < list.size()) list.remove(idx);
                        }
                        return false;
                    }
                }
            }
            if (obj instanceof java.util.Map map) {
                String method = methodName != null ? methodName.toLowerCase() : "";
                switch (method) {
                    case "put", "set" -> {
                        if (args.size() >= 2) map.put(String.valueOf(args.get(0)), args.get(1));
                        return false;
                    }
                    case "remove" -> {
                        if (!args.isEmpty()) map.remove(String.valueOf(args.get(0)));
                        return false;
                    }
                }
            }

            if (obj instanceof net.minecraft.world.entity.Entity entity) {
                String method = methodName != null ? methodName.toLowerCase() : "";
                if (entity instanceof net.minecraft.world.entity.player.Player player
                        && org.zonarstudio.spraute_engine.script.PlayerScriptMethods.isKnown(method)) {
                    org.zonarstudio.spraute_engine.script.PlayerScriptMethods.invoke(
                            player, method, args, this::performRaycast);
                    return false;
                }
                switch (method) {
                    case "damage" -> {
                        if (entity instanceof net.minecraft.world.entity.LivingEntity living) {
                            float amount = args.isEmpty() ? 1f : ((Number) args.get(0)).floatValue();
                            
                            // Check if source is provided (second argument)
                            net.minecraft.world.entity.Entity sourceEntity = null;
                            if (args.size() > 1 && args.get(1) instanceof net.minecraft.world.entity.Entity e) {
                                sourceEntity = e;
                            } else if (args.size() > 1 && args.get(1) instanceof String id) {
                                sourceEntity = resolveEntity(id);
                            }
                            
                            if (sourceEntity != null) {
                                //? if >=1.20.1 {
                                living.hurt(new net.minecraft.world.damagesource.DamageSource(
                                        SprauteEntityCompat.level(living).registryAccess()
                                                .registryOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE)
                                                .getHolderOrThrow(net.minecraft.world.damagesource.DamageTypes.GENERIC),
                                        sourceEntity), amount);
                                //?} else {
                                /*living.hurt(new net.minecraft.world.damagesource.EntityDamageSource("generic", sourceEntity), amount);
                                *///?}
                            } else {
                                //? if >=1.20.1 {
                                living.hurt(SprauteEntityCompat.level(living).damageSources().generic(), amount);
                                //?} else {
                                /*living.hurt(net.minecraft.world.damagesource.DamageSource.GENERIC, amount);
                                *///?}
                            }
                        }
                        return false;
                    }
                    case "teleport", "tp" -> {
                        if (args.size() >= 3) {
                            double tx = ((Number) args.get(0)).doubleValue();
                            double ty = ((Number) args.get(1)).doubleValue();
                            double tz = ((Number) args.get(2)).doubleValue();
                            if (entity instanceof net.minecraft.server.level.ServerPlayer sp) {
                                sp.teleportTo((net.minecraft.server.level.ServerLevel) SprauteEntityCompat.level(entity), tx, ty, tz, sp.getYRot(), sp.getXRot());
                            } else {
                                entity.teleportTo(tx, ty, tz);
                            }
                        }
                        return false;
                    }
                }
            }

            return false;
        }

        private Map<String, Object> performRaycast(net.minecraft.world.entity.LivingEntity entity, double maxDistance) {
            net.minecraft.world.level.Level level = SprauteEntityCompat.level(entity);
            net.minecraft.world.phys.Vec3 eyePos = entity.getEyePosition();
            net.minecraft.world.phys.Vec3 viewVec = entity.getViewVector(1.0f);
            if (entity instanceof net.minecraft.world.entity.player.Player player) {
                net.minecraft.world.phys.Vec3[] screen = org.zonarstudio.spraute_engine.scene.PlayerCameraPose.screenPickRay(
                        player, org.zonarstudio.spraute_engine.scene.PlayerCameraSync.restOf(player), 1f, maxDistance);
                if (screen != null) {
                    eyePos = screen[0];
                    viewVec = screen[2];
                }
            }
            net.minecraft.world.phys.Vec3 endPos = eyePos.add(viewVec.scale(maxDistance));
            net.minecraft.world.phys.AABB aabb = entity.getBoundingBox().expandTowards(viewVec.scale(maxDistance)).inflate(1.0D, 1.0D, 1.0D);

            net.minecraft.world.phys.HitResult blockHit = level.clip(new net.minecraft.world.level.ClipContext(eyePos, endPos, net.minecraft.world.level.ClipContext.Block.OUTLINE, net.minecraft.world.level.ClipContext.Fluid.NONE, entity));
            
            if (blockHit.getType() != net.minecraft.world.phys.HitResult.Type.MISS) {
                endPos = blockHit.getLocation();
            }

            net.minecraft.world.phys.EntityHitResult entityHit = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(level, entity, eyePos, endPos, aabb, e -> !e.isSpectator() && e.isPickable());
            
            Map<String, Object> result = new HashMap<>();
            
            if (entityHit != null) {
                result.put("type", "entity");
                result.put("entity", entityHit.getEntity());
                result.put("x", entityHit.getLocation().x);
                result.put("y", entityHit.getLocation().y);
                result.put("z", entityHit.getLocation().z);
            } else if (blockHit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
                result.put("type", "block");
                net.minecraft.world.phys.BlockHitResult bh = (net.minecraft.world.phys.BlockHitResult) blockHit;
                net.minecraft.core.BlockPos pos = bh.getBlockPos();
                result.put("x", pos.getX());
                result.put("y", pos.getY());
                result.put("z", pos.getZ());
                String blockId = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(level.getBlockState(pos).getBlock()).toString();
                result.put("block", blockId);
            } else {
                result.put("type", "miss");
            }
            return result;
        }

        private net.minecraft.world.item.ItemStack resolveItemStack(String itemId, String nbtString) {
            net.minecraft.world.item.ItemStack stack = org.zonarstudio.spraute_engine.script.ItemStackScriptUtil.createStack(itemId, 1, nbtString);
            if (stack.isEmpty()) {
                LOGGER.warn("[Script] Unknown item: {}", itemId);
            }
            return stack;
        }

        /**
         * Call a user-defined function, returning its result (or null).
         * Imported library functions run in the donor script's variable context (not the caller's).
         */
        private ActiveScript findDonorForFunction(String name) {
            for (String importName : importedScripts) {
                ScriptExecutor.this.ensureImportedScriptRunning(importName, source, script.getName());
                ActiveScript donor = ScriptExecutor.this.findDonorScript(importName, name);
                if (donor != null && donor.userFunctions.containsKey(name)) {
                    return donor;
                }
            }
            return null;
        }

        private Object callUserFunction(String name, List<Object> args) {
            ActiveScript donor = findDonorForFunction(name);
            if (donor != null) {
                return donor.executeUserFunctionBody(donor.userFunctions.get(name), args);
            }
            UserFunction func = userFunctions.get(name);
            if (func != null) {
                return executeUserFunctionBody(func, args);
            }
            throw new RuntimeException("Unknown function: " + name);
        }

        private Object executeUserFunctionBody(UserFunction func, List<Object> args) {
            // User functions must not inherit the caller's async task locals (e.g. story dialogue
            // task overwriting chat_fade's hold timer with unrelated values).
            AsyncTask savedScope = this.currentTaskScope;
            this.currentTaskScope = null;
            // Save existing variables and set params
            Map<String, Object> savedVars = new HashMap<>();
            for (int i = 0; i < func.params.size(); i++) {
                String paramName = func.params.get(i);
                if (variables.containsKey(paramName)) {
                    savedVars.put(paramName, variables.get(paramName));
                }
                variables.put(paramName, i < args.size() ? args.get(i) : null);
            }

            Object result = null;
            try {
                executeInstructionBlock(func.bodyInstructions);
            } catch (ReturnException e) {
                result = e.value;
            } finally {
                // Restore saved variables
                for (String paramName : func.params) {
                    if (savedVars.containsKey(paramName)) {
                        variables.put(paramName, savedVars.get(paramName));
                    } else {
                        variables.remove(paramName);
                    }
                }
                this.currentTaskScope = savedScope;
            }

            return result;
        }

        @SuppressWarnings("unchecked")
        private void executeSetIndex(CompiledScript.Instruction instruction) {
            ScriptNode objectNode = (ScriptNode) instruction.getArg(0);
            ScriptNode indexNode = (ScriptNode) instruction.getArg(1);
            ScriptNode valueNode = (ScriptNode) instruction.getArg(2);

            Object obj = evaluateExpression(objectNode);
            Object index = evaluateExpression(indexNode);
            Object value = evaluateExpression(valueNode);

            if (obj instanceof java.util.List list) {
                if (index instanceof Number n) {
                    int idx = n.intValue();
                    if (idx >= 0 && idx < list.size()) {
                        list.set(idx, value);
                    } else if (idx == list.size()) {
                        list.add(value);
                    }
                }
            } else if (obj instanceof java.util.Map map) {
                map.put(String.valueOf(index), value);
            } else {
                throw new RuntimeException("Cannot index assign to " + (obj != null ? obj.getClass().getSimpleName() : "null"));
            }
        }

        private void executeSetProperty(CompiledScript.Instruction instruction) {
            ScriptNode objectNode = (ScriptNode) instruction.getArg(0);
            String propName = normPropKey((String) instruction.getArg(1));
            ScriptNode valueNode = (ScriptNode) instruction.getArg(2);
            Object value = evaluateExpression(valueNode);

            Object varObj = evaluateExpression(objectNode);
            
            if (varObj == null && objectNode instanceof ScriptNode.IdentifierNode idNode) {
                String idStr = idNode.getName();
                if (org.zonarstudio.spraute_engine.entity.NpcManager.get(idStr) != null
                        || org.zonarstudio.spraute_engine.entity.ModelManager.get(idStr) != null
                        || resolveEntity(idStr) != null) {
                    varObj = idStr;
                }
            }

            if (varObj instanceof org.zonarstudio.spraute_engine.scene.SceneNodeView sceneNode) {
                if (sceneNode.setProperty(propName, value, source)) return;
                net.minecraft.world.entity.Entity spawned = sceneNode.getEntity(source);
                if (spawned != null) varObj = spawned;
            }

            if (varObj instanceof java.util.Map map) {
                map.put(propName, value);
                return;
            }

            if (varObj instanceof net.minecraft.world.entity.player.Player player
                    && org.zonarstudio.spraute_engine.script.ScriptPropertyAccess.set(
                            player, (String) instruction.getArg(1), value, source)) {
                return;
            }

            var entity = varObj instanceof net.minecraft.world.entity.Entity e ? e : null;
            if (entity == null && varObj instanceof String strId) {
                entity = resolveEntity(strId);
            }

            if (entity != null) {
                switch (propName) {
                    case "name" -> entity.setCustomName(Component.literal(String.valueOf(value)));
                    case "showName" -> {
                        boolean visible = false;
                        if (value instanceof Boolean b) visible = b;
                        else if (value instanceof String s) visible = s.equalsIgnoreCase("true");
                        entity.setCustomNameVisible(visible);
                    }
                    case "isSprinting", "issprinting", "sprinting" -> {
                        if (entity instanceof net.minecraft.world.entity.player.Player player) {
                            boolean sprint = false;
                            if (value instanceof Boolean b) sprint = b;
                            else if (value instanceof Number n) sprint = n.doubleValue() != 0;
                            else if (value instanceof String s) sprint = s.equalsIgnoreCase("true") || s.equals("1");
                            player.setSprinting(sprint);
                        }
                    }
                    case "collision" -> {
                        if (entity instanceof org.zonarstudio.spraute_engine.entity.SprauteNpcEntity npc) {
                            boolean col = true;
                            if (value instanceof Boolean b) col = b;
                            else if (value instanceof String s) col = s.equalsIgnoreCase("true");
                            npc.setHasCollision(col);
                        }
                    }
                    case "hp" -> {
                        if (value instanceof Number n && entity instanceof net.minecraft.world.entity.LivingEntity living) {
                            float hp = n.floatValue();
                            var attr = living.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
                            if (attr != null) attr.setBaseValue(hp);
                            living.setHealth(hp);
                        }
                    }
                    case "speed" -> {
                        if (value instanceof Number n && entity instanceof net.minecraft.world.entity.LivingEntity living) {
                            var attr = living.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
                            if (attr != null) attr.setBaseValue(n.doubleValue());
                        }
                    }
                    case "flySpeed", "flyspeed" -> {
                        if (value instanceof Number n && entity instanceof net.minecraft.world.entity.LivingEntity living) {
                            var attr = living.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.FLYING_SPEED);
                            if (attr != null) attr.setBaseValue(n.doubleValue());
                        }
                    }
                    case "nameYOffset", "nameyoffset", "name_y_offset" -> {
                        if (value instanceof Number n && entity instanceof org.zonarstudio.spraute_engine.entity.SprauteNpcEntity npc) {
                            npc.setNameYOffset(n.floatValue());
                        }
                    }
                    case "flySmoothing", "flysmoothing", "fly_smoothing" -> {
                        if (value instanceof Number n && entity instanceof org.zonarstudio.spraute_engine.entity.SprauteNpcEntity npc) {
                            npc.setFlySmoothing(n.floatValue());
                        }
                    }
                    case "walkTurnSpeed", "walkturnspeed", "walk_turn_speed" -> {
                        if (value instanceof Number n && entity instanceof org.zonarstudio.spraute_engine.entity.SprauteNpcEntity npc) {
                            npc.setWalkTurnSpeed(n.floatValue());
                        }
                    }
                    case "idleTurnSpeed", "idleturnspeed", "idle_turn_speed" -> {
                        if (value instanceof Number n && entity instanceof org.zonarstudio.spraute_engine.entity.SprauteNpcEntity npc) {
                            npc.setIdleTurnSpeed(n.floatValue());
                        }
                    }
                    case "w", "width", "hitboxW", "hitboxw", "hitboxWidth", "hitboxwidth", "hitbox_width" -> {
                        if (value instanceof Number n && entity instanceof org.zonarstudio.spraute_engine.entity.SprauteNpcEntity npc) {
                            npc.setHitbox(n.floatValue(), npc.getHitboxHeight());
                        }
                    }
                    case "h", "height", "hitboxH", "hitboxh", "hitboxHeight", "hitboxheight", "hitbox_height" -> {
                        if (value instanceof Number n && entity instanceof org.zonarstudio.spraute_engine.entity.SprauteNpcEntity npc) {
                            npc.setHitbox(npc.getHitboxWidth(), n.floatValue());
                        }
                    }
                    case "x" -> {
                        if (value instanceof Number n) {
                            entity.setPos(n.doubleValue(), entity.getY(), entity.getZ());
                        }
                    }
                    case "y" -> {
                        if (value instanceof Number n) {
                            entity.setPos(entity.getX(), n.doubleValue(), entity.getZ());
                        }
                    }
                    case "z" -> {
                        if (value instanceof Number n) {
                            entity.setPos(entity.getX(), entity.getY(), n.doubleValue());
                        }
                    }
                    case "yaw" -> {
                        if (value instanceof Number n) {
                            entity.setYRot(n.floatValue());
                            entity.yRotO = n.floatValue();
                            if (entity instanceof net.minecraft.world.entity.LivingEntity living) {
                                living.yHeadRot = n.floatValue();
                                living.yHeadRotO = n.floatValue();
                                living.yBodyRot = n.floatValue();
                                living.yBodyRotO = n.floatValue();
                            }
                        }
                    }
                    case "pitch" -> {
                        if (value instanceof Number n) {
                            entity.setXRot(n.floatValue());
                            entity.xRotO = n.floatValue();
                            if (entity instanceof org.zonarstudio.spraute_engine.entity.SprauteNpcEntity npc) {
                                npc.setModelRotationX(n.floatValue());
                            }
                        }
                    }
                    case "rotationX", "rotX" -> {
                        if (value instanceof Number n && GeoEntityScriptUtil.isGeoEntity(entity)) {
                            GeoEntityScriptUtil.setModelRotationX(entity, n.floatValue());
                        }
                    }
                    case "rotationY", "rotY" -> {
                        if (value instanceof Number n && GeoEntityScriptUtil.isGeoEntity(entity)) {
                            GeoEntityScriptUtil.setModelRotationY(entity, n.floatValue());
                        }
                    }
                    case "rotationZ", "rotZ", "roll" -> {
                        if (value instanceof Number n && GeoEntityScriptUtil.isGeoEntity(entity)) {
                            GeoEntityScriptUtil.setModelRotationZ(entity, n.floatValue());
                        }
                    }
                    case "scale" -> {
                        if (value instanceof Number n && GeoEntityScriptUtil.isGeoEntity(entity)) {
                            GeoEntityScriptUtil.setModelScale(entity, n.floatValue(), n.floatValue(), n.floatValue());
                        }
                    }
                    case "scaleX" -> {
                        if (value instanceof Number n && GeoEntityScriptUtil.isGeoEntity(entity)) {
                            GeoEntityScriptUtil.setModelScale(entity, n.floatValue(),
                                    GeoEntityScriptUtil.getModelScaleY(entity),
                                    GeoEntityScriptUtil.getModelScaleZ(entity));
                        }
                    }
                    case "scaleY" -> {
                        if (value instanceof Number n && GeoEntityScriptUtil.isGeoEntity(entity)) {
                            GeoEntityScriptUtil.setModelScale(entity,
                                    GeoEntityScriptUtil.getModelScaleX(entity),
                                    n.floatValue(),
                                    GeoEntityScriptUtil.getModelScaleZ(entity));
                        }
                    }
                    case "scaleZ" -> {
                        if (value instanceof Number n && GeoEntityScriptUtil.isGeoEntity(entity)) {
                            GeoEntityScriptUtil.setModelScale(entity,
                                    GeoEntityScriptUtil.getModelScaleX(entity),
                                    GeoEntityScriptUtil.getModelScaleY(entity),
                                    n.floatValue());
                        }
                    }
                    case "translucent", "transparent" -> {
                        if (GeoEntityScriptUtil.isGeoEntity(entity)) {
                            GeoEntityScriptUtil.setTranslucent(entity, isTruthy(value));
                        }
                    }
                    case "opacity" -> {
                        if (value instanceof Number n && GeoEntityScriptUtil.isGeoEntity(entity)) {
                            GeoEntityScriptUtil.setOpacity(entity, n.floatValue());
                        }
                    }
                    case "lod", "lodEnabled" -> {
                        if (GeoEntityScriptUtil.isGeoEntity(entity)) {
                            GeoEntityScriptUtil.setLodEnabled(entity,
                                    !(value instanceof String s && "off".equalsIgnoreCase(s)) && isTruthy(value));
                        }
                    }
                    case "renderDistance" -> {
                        if (value instanceof Number n && GeoEntityScriptUtil.isGeoEntity(entity)) {
                            GeoEntityScriptUtil.setRenderDistance(entity, n.floatValue());
                        }
                    }
                    case "forceVisible" -> {
                        if (GeoEntityScriptUtil.isGeoEntity(entity)) {
                            GeoEntityScriptUtil.setForceVisible(entity, isTruthy(value));
                        }
                    }
                    case "loadChunks", "keepChunks", "keepLoaded" -> {
                        if (entity instanceof org.zonarstudio.spraute_engine.entity.SprauteNpcEntity npc) {
                            npc.setLoadChunks(isTruthy(value));
                        }
                    }
                    default -> {
                        if (entity instanceof org.zonarstudio.spraute_engine.entity.SprauteNpcEntity npc) {
                            switch (propName) {
                                case "model" -> npc.setModel(String.valueOf(value));
                                case "texture" -> npc.setTexture(String.valueOf(value));
                                case "idleAnim" -> npc.setIdleAnim(String.valueOf(value));
                                case "walkAnim" -> npc.setWalkAnim(String.valueOf(value));
                                case "flyIdleAnim" -> npc.setFlyIdleAnim(String.valueOf(value));
                                case "flyWalkAnim" -> npc.setFlyWalkAnim(String.valueOf(value));
                                case "swimIdleAnim" -> npc.setSwimIdleAnim(String.valueOf(value));
                                case "swimWalkAnim" -> npc.setSwimWalkAnim(String.valueOf(value));
                                case "deathAnim" -> npc.setDeathAnim(String.valueOf(value));
                                case "animation" -> npc.setAnimation(String.valueOf(value));
                                case "dropItem" -> {
                                    if (npc.customDrops.isEmpty()) npc.customDrops.add(new org.zonarstudio.spraute_engine.registry.CustomDropRegistry.DropRule(String.valueOf(value), 1, 1, 100, false, null));
                                    else npc.customDrops.get(0).itemId = String.valueOf(value);
                                }
                                case "dropMin" -> {
                                    if (npc.customDrops.isEmpty()) npc.customDrops.add(new org.zonarstudio.spraute_engine.registry.CustomDropRegistry.DropRule("minecraft:air", value instanceof Number n ? n.intValue() : 1, 1, 100, false, null));
                                    else npc.customDrops.get(0).min = value instanceof Number n ? n.intValue() : 1;
                                }
                                case "dropMax" -> {
                                    if (npc.customDrops.isEmpty()) npc.customDrops.add(new org.zonarstudio.spraute_engine.registry.CustomDropRegistry.DropRule("minecraft:air", 1, value instanceof Number n ? n.intValue() : 1, 100, false, null));
                                    else npc.customDrops.get(0).max = value instanceof Number n ? n.intValue() : 1;
                                }
                                case "dropChance" -> {
                                    if (npc.customDrops.isEmpty()) npc.customDrops.add(new org.zonarstudio.spraute_engine.registry.CustomDropRegistry.DropRule("minecraft:air", 1, 1, value instanceof Number n ? n.intValue() : 100, false, null));
                                    else npc.customDrops.get(0).chance = value instanceof Number n ? n.intValue() : 100;
                                }
                                default -> npc.customData.put(propName, value);
                            }
                        } else if (entity instanceof org.zonarstudio.spraute_engine.entity.SprauteModelEntity model) {
                            switch (propName) {
                                case "model" -> model.setModel(String.valueOf(value));
                                case "texture" -> model.setTexture(String.valueOf(value));
                                case "animation" -> model.setAnimation(String.valueOf(value));
                                case "idleAnim" -> model.setIdleAnim(String.valueOf(value));
                                default -> {}
                            }
                        }
                    }
                }
                return;
            }

            if (varObj != null) {
                org.zonarstudio.spraute_engine.script.util.ForgeReflection.setField(varObj, propName, value);
            }
        }

        /** Stack of widget collectors for nested create ui / scroll { } blocks. */
        private final java.util.Deque<java.util.List<org.zonarstudio.spraute_engine.ui.RuntimeWidget>> uiWidgetStack = new java.util.ArrayDeque<>();

        private void executeUiBlock(CompiledScript.Instruction instruction) {
            String varName = (String) instruction.getArg(0);
            @SuppressWarnings("unchecked")
            java.util.Map<String, ScriptNode> rootProps = (java.util.Map<String, ScriptNode>) instruction.getArg(1);
            @SuppressWarnings("unchecked")
            java.util.List<CompiledScript.Instruction> bodyInstructions =
                    (java.util.List<CompiledScript.Instruction>) instruction.getArg(2);
            try {
                java.util.List<org.zonarstudio.spraute_engine.ui.RuntimeWidget> collector = new java.util.ArrayList<>();
                uiWidgetStack.push(collector);
                executeInstructionBlock(bodyInstructions);
                uiWidgetStack.poll();
                if (!rootProps.containsKey("id")) {
                    rootProps.put("id", new ScriptNode.LiteralNode(varName));
                }
                org.zonarstudio.spraute_engine.ui.UiTemplate t =
                        org.zonarstudio.spraute_engine.ui.UiTemplate.buildFromRuntime(this::evaluateExpression, rootProps, collector);
                variables.put(varName, t);
                if (currentTaskScope != null) {
                    currentTaskScope.taskLocals.put(varName, t);
                }
            } catch (ReturnException re) {
                uiWidgetStack.poll();
                throw re;
            } catch (Exception e) {
                uiWidgetStack.poll();
                if (currentTaskScope != null) currentTaskScope.taskLocals.remove(varName);
                variables.remove(varName);
                String err = e.getMessage() != null ? e.getMessage() : e.toString();
                LOGGER.error("[Script] create ui '{}' failed: {} ({})", varName, err, e.getClass().getSimpleName(), e);
                ScriptConsole.error(script.getName(),
                        "create ui '" + varName + "' не создан: " + err
                                + " — переменная '" + varName + "' не определена");
            }
        }

        private void executeCommandBlock(CompiledScript.Instruction instruction) {
            String cmdName = (String) instruction.getArg(0);
            @SuppressWarnings("unchecked")
            java.util.List<CompiledScript.Instruction> bodyInstructions = (java.util.List<CompiledScript.Instruction>) instruction.getArg(1);

            try {
                com.mojang.brigadier.CommandDispatcher<CommandSourceStack> dispatcher = source.getServer().getCommands().getDispatcher();
                
                com.mojang.brigadier.Command<CommandSourceStack> executeLogic = ctx -> {
                    net.minecraft.server.level.ServerPlayer p = null;
                    try {
                        p = ctx.getSource().getPlayerOrException();
                    } catch (Exception ignored) {}
                    
                    if (p != null) {
                        String taskId = "cmd_" + cmdName + "_" + java.util.UUID.randomUUID().toString().substring(0, 8);
                        AsyncTask task = new AsyncTask(taskId, bodyInstructions);
                        task.taskLocals.put("_eventPlayer", p);
                        
                        java.util.List<Object> argsList = new java.util.ArrayList<>();
                        try {
                            String argsStr = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "args");
                            if (argsStr != null && !argsStr.trim().isEmpty()) {
                                java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"([^\"]*)\"|(\\S+)").matcher(argsStr);
                                while (m.find()) {
                                    String val = m.group(1) != null ? m.group(1) : m.group(2);
                                    try {
                                        argsList.add(Double.parseDouble(val));
                                    } catch (NumberFormatException ex) {
                                        if (val.equalsIgnoreCase("true")) argsList.add(true);
                                        else if (val.equalsIgnoreCase("false")) argsList.add(false);
                                        else argsList.add(val);
                                    }
                                }
                            }
                        } catch (IllegalArgumentException e) {
                            // Argument not provided, argsList stays empty
                        }
                        
                        task.taskLocals.put("_eventArgs", argsList);
                        asyncTasks.put(taskId, task);
                    }
                    return 1;
                };

                com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> builder =
                        net.minecraft.commands.Commands.literal(cmdName)
                                .executes(executeLogic)
                                .then(net.minecraft.commands.Commands.argument("args", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                                        .executes(executeLogic));

                dispatcher.register(builder);
                for (net.minecraft.server.level.ServerPlayer player : source.getServer().getPlayerList().getPlayers()) {
                    source.getServer().getCommands().sendCommands(player);
                }
                LOGGER.info("[Script: {}] Registered custom command /{}", script.getName(), cmdName);
            } catch (Exception e) {
                LOGGER.error("[Script: {}] Failed to register command /{}", script.getName(), cmdName, e);
            }
        }

        @SuppressWarnings("unchecked")
        private void executeFadeIn(CompiledScript.Instruction instruction) {
            java.util.Map<String, ScriptNode> propNodes = (java.util.Map<String, ScriptNode>) instruction.getArg(0);
            java.util.Map<String, Object> props = new java.util.LinkedHashMap<>();
            if (instruction.getArgCount() > 1 && Boolean.TRUE.equals(instruction.getArg(1))) {
                props.put("triggerFadeOut", true);
            }
            Object targetObj = null;
            for (java.util.Map.Entry<String, ScriptNode> entry : propNodes.entrySet()) {
                String key = entry.getKey();
                if ("target".equals(key) || "player".equals(key)) {
                    if (targetObj == null) {
                        try {
                            targetObj = evaluateExpression(entry.getValue());
                        } catch (RuntimeException e) {
                            LOGGER.warn("[Script: {}] fadeIn target: {}", script.getName(), e.getMessage());
                        }
                    }
                    continue;
                }
                Object value = evaluateExpression(entry.getValue());
                if ("color".equals(key)) {
                    value = SprauteColors.toRgbInt(value);
                } else if ("fadeout".equals(key)) {
                    value = parseFadeBool(value);
                }
                props.put(key, value);
            }

            attachCurrentSceneId(props);
            net.minecraft.server.level.ServerPlayer targetPlayer = resolveFadeTargetPlayer(targetObj);
            if (targetPlayer != null) {
                final net.minecraft.server.level.ServerPlayer fadePlayer = targetPlayer;
                org.zonarstudio.spraute_engine.network.ModNetwork.CHANNEL.send(
                        net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> fadePlayer),
                        new org.zonarstudio.spraute_engine.network.SyncLoadScreenPacket(props)
                );
            } else {
                LOGGER.warn("[Script: {}] fadeIn: no target player", script.getName());
            }
        }

        private net.minecraft.server.level.ServerPlayer resolveFadeTargetPlayer(Object targetObj) {
            net.minecraft.server.level.ServerPlayer targetPlayer = targetObj != null
                    ? resolveServerPlayer(targetObj) : null;
            if (targetPlayer == null && source.getEntity() instanceof net.minecraft.server.level.ServerPlayer sp) {
                targetPlayer = sp;
            }
            if (targetPlayer == null && source.getServer() != null) {
                var players = source.getServer().getPlayerList().getPlayers();
                if (!players.isEmpty()) targetPlayer = players.get(0);
            }
            return targetPlayer;
        }

        private static void attachCurrentSceneId(java.util.Map<String, Object> props) {
            if (props == null || props.containsKey("sceneId") || props.containsKey("scene")) return;
            org.zonarstudio.spraute_engine.scene.SceneActiveState state = currentSceneState();
            if (state != null && state.sceneId != null && !state.sceneId.isBlank()) {
                props.put("sceneId", state.sceneId);
            }
        }

        private static boolean parseFadeBool(Object value) {
            if (value instanceof Boolean b) return b;
            if (value instanceof String s) return "true".equalsIgnoreCase(s.trim());
            return false;
        }

        /**
         * Plays a camera route (await cameraRoute(player, routeName, [opts...])) and returns its
         * duration in seconds, or null if it could not be started (so the caller skips the wait).
         */
        private Double awaitCameraRouteDuration(CompiledScript.Instruction instr) {
            int n = instr.getArgCount();
            if (n < 2) return null;
            java.util.List<Object> evalArgs = new java.util.ArrayList<>();
            for (int i = 0; i < n; i++) {
                Object a = instr.getArg(i);
                evalArgs.add(a instanceof ScriptNode sn ? evaluateExpression(sn) : a);
            }
            net.minecraft.server.level.ServerPlayer player = resolveServerPlayer(evalArgs.get(0));
            if (player == null) return null;
            String routeName = String.valueOf(evalArgs.get(1));
            try {
                org.zonarstudio.spraute_engine.script.CameraRouteScriptUtil.PlayOptions options =
                        org.zonarstudio.spraute_engine.script.CameraRouteScriptUtil.parsePlayOptions(evalArgs, 2);
                float dur = org.zonarstudio.spraute_engine.script.CameraRouteScriptUtil.playRoute(player, routeName, options);
                return (double) dur;
            } catch (Exception e) {
                LOGGER.warn("[Script: {}] await cameraRoute '{}' failed: {}", script.getName(), routeName, e.getMessage());
                return null;
            }
        }

        @SuppressWarnings("unchecked")
        private void executeCamera(CompiledScript.Instruction instruction) {
            String cameraId = (String) instruction.getArg(0);
            Map<String, List<ScriptNode>> propsNodes = (Map<String, List<ScriptNode>>) instruction.getArg(1);

            Map<String, List<Object>> props = new HashMap<>();
            for (var entry : propsNodes.entrySet()) {
                List<Object> values = new ArrayList<>();
                for (ScriptNode node : entry.getValue()) {
                    values.add(evaluateExpression(node));
                }
                props.put(normPropKey(entry.getKey()), values);
            }

            boolean explicitTarget = props.containsKey("target");
            net.minecraft.server.level.ServerPlayer player = null;
            if (explicitTarget) {
                Object targetObj = props.get("target").get(0);
                player = resolveServerPlayer(targetObj);
            }
            if (player == null && !explicitTarget) {
                player = (source.getEntity() instanceof net.minecraft.server.level.ServerPlayer sp) ? sp : null;
            }
            boolean sceneBroadcast = !explicitTarget && instanceId != null && instanceId.startsWith("scene:")
                    && source.getServer() != null;
            if (player == null && !sceneBroadcast) return;

            List<Object> posArgs = props.get("pos");
            double x = player != null ? player.getX() : source.getPosition().x;
            double y = player != null ? player.getEyeY() : source.getPosition().y;
            double z = player != null ? player.getZ() : source.getPosition().z;
            if (posArgs != null && !posArgs.isEmpty()) {
                Object first = posArgs.get(0);
                if (first instanceof java.util.List<?> l && l.size() >= 3) {
                    x = ((Number) l.get(0)).doubleValue();
                    y = ((Number) l.get(1)).doubleValue();
                    z = ((Number) l.get(2)).doubleValue();
                } else if (posArgs.size() >= 3) {
                    x = ((Number) posArgs.get(0)).doubleValue();
                    y = ((Number) posArgs.get(1)).doubleValue();
                    z = ((Number) posArgs.get(2)).doubleValue();
                }
            }

            List<Object> rotArgs = props.get("rotate");
            float yaw = player != null ? player.getYRot() : source.getRotation().y;
            float pitch = player != null ? player.getXRot() : source.getRotation().x;
            if (rotArgs != null && !rotArgs.isEmpty()) {
                Object first = rotArgs.get(0);
                if (first instanceof java.util.List<?> l && l.size() >= 2) {
                    yaw = ((Number) l.get(0)).floatValue();
                    pitch = ((Number) l.get(1)).floatValue();
                } else if (rotArgs.size() >= 2) {
                    yaw = ((Number) rotArgs.get(0)).floatValue();
                    pitch = ((Number) rotArgs.get(1)).floatValue();
                }
            }

            float time = props.containsKey("time") ? ((Number) props.get("time").get(0)).floatValue() : 0f;
            boolean smoothExplicit = props.containsKey("smooth") || props.containsKey("flySmooth");
            boolean timeExplicit = props.containsKey("smoothTime") || props.containsKey("flyTime");
            float smoothTime = 0f;
            if (timeExplicit) {
                Object raw = props.containsKey("smoothTime")
                        ? props.get("smoothTime").get(0) : props.get("flyTime").get(0);
                if (raw instanceof Number n) smoothTime = Math.max(0f, n.floatValue());
            }
            boolean smooth = smoothTime > 0f;
            if (smoothExplicit) {
                Object flag = props.containsKey("smooth")
                        ? props.get("smooth").get(0) : props.get("flySmooth").get(0);
                smooth = Boolean.TRUE.equals(flag) && smoothTime > 0f;
            }
            if (!smooth) smoothTime = 0f;

            String dimension;
            if (props.containsKey("dimension")) {
                dimension = String.valueOf(props.get("dimension").get(0));
            } else if (props.containsKey("dim")) {
                dimension = String.valueOf(props.get("dim").get(0));
            } else if (player != null) {
                dimension = SprauteEntityCompat.level(player).dimension().location().toString();
            } else {
                dimension = source.getLevel().dimension().location().toString();
            }

            byte lookAtMode = org.zonarstudio.spraute_engine.network.CameraPacket.LOOK_NONE;
            int lookAtEntityId = -1;
            double lookAtX = 0, lookAtY = 0, lookAtZ = 0;

            List<Object> lookAtArgs = props.get("lookAt");

            if (lookAtArgs != null && !lookAtArgs.isEmpty()) {
                boolean track = props.containsKey("track") && Boolean.TRUE.equals(props.get("track").get(0));
                lookAtMode = track
                        ? org.zonarstudio.spraute_engine.network.CameraPacket.LOOK_TRACK
                        : org.zonarstudio.spraute_engine.network.CameraPacket.LOOK_ONCE;

                Object lookTarget = lookAtArgs.get(0);
                net.minecraft.world.entity.Entity lookEntity = resolveEntity(lookTarget);
                if (lookEntity != null) {
                    lookAtEntityId = lookEntity.getId();
                    lookAtX = lookEntity.getX();
                    lookAtY = lookEntity.getEyeY();
                    lookAtZ = lookEntity.getZ();
                } else if (lookAtArgs.size() >= 3) {
                    lookAtX = ((Number) lookAtArgs.get(0)).doubleValue();
                    lookAtY = ((Number) lookAtArgs.get(1)).doubleValue();
                    lookAtZ = ((Number) lookAtArgs.get(2)).doubleValue();
                    lookAtEntityId = -1;
                }
            }

            org.zonarstudio.spraute_engine.script.CameraControls.Settings controls =
                    org.zonarstudio.spraute_engine.script.CameraControls.fromProps(props);
            java.util.List<net.minecraft.server.level.ServerPlayer> recipients = new java.util.ArrayList<>();
            if (sceneBroadcast) {
                recipients.addAll(source.getServer().getPlayerList().getPlayers());
            } else if (player != null) {
                recipients.add(player);
            }
            for (net.minecraft.server.level.ServerPlayer target : recipients) {
                org.zonarstudio.spraute_engine.script.CameraScriptUtil.sendStart(
                        target, x, y, z, yaw, pitch, time, smooth, smoothTime, dimension,
                        new org.zonarstudio.spraute_engine.script.CameraScriptUtil.LookAt(
                                lookAtMode, lookAtEntityId, lookAtX, lookAtY, lookAtZ),
                        controls);
            }

            variables.put(cameraId, cameraId);
        }

        @SuppressWarnings("unchecked")
        private void executeUiWidget(CompiledScript.Instruction instruction) {
            String kind = (String) instruction.getArg(0);
            java.util.List<ScriptNode> argNodes = (java.util.List<ScriptNode>) instruction.getArg(1);
            java.util.Map<String, ScriptNode> propNodes = (java.util.Map<String, ScriptNode>) instruction.getArg(2);
            java.util.Map<String, java.util.List<CompiledScript.Instruction>> eventHandlerInstr =
                    (java.util.Map<String, java.util.List<CompiledScript.Instruction>>) instruction.getArg(3);
            java.util.List<CompiledScript.Instruction> childBody =
                    instruction.getArgCount() > 4 ? (java.util.List<CompiledScript.Instruction>) instruction.getArg(4) : null;

            java.util.List<Object> evaluatedArgs = new java.util.ArrayList<>();
            for (ScriptNode node : argNodes) {
                evaluatedArgs.add(evaluateExpression(node));
            }

            java.util.Map<String, Object> evaluatedProps = new java.util.LinkedHashMap<>();
            for (java.util.Map.Entry<String, ScriptNode> entry : propNodes.entrySet()) {
                evaluatedProps.put(entry.getKey(), evaluateExpression(entry.getValue()));
            }

            java.util.List<org.zonarstudio.spraute_engine.ui.RuntimeWidget> children = new java.util.ArrayList<>();
            if (childBody != null && !childBody.isEmpty()) {
                uiWidgetStack.push(children);
                try {
                    executeInstructionBlock(childBody);
                } finally {
                    uiWidgetStack.poll();
                }
            }

            org.zonarstudio.spraute_engine.ui.RuntimeWidget widget = new org.zonarstudio.spraute_engine.ui.RuntimeWidget(
                    kind, evaluatedArgs, evaluatedProps, eventHandlerInstr, children);

            java.util.List<org.zonarstudio.spraute_engine.ui.RuntimeWidget> currentCollector = uiWidgetStack.peek();
            if (currentCollector != null) {
                currentCollector.add(widget);
            } else {
                LOGGER.warn("[Script] UI widget '{}' emitted outside of create ui block", kind);
            }
        }

        private void executeNpcPrefabDef(CompiledScript.Instruction instruction) {
            String prefabId = (String) instruction.getArg(0);
            @SuppressWarnings("unchecked")
            Map<String, List<ScriptNode>> propsNodes = (Map<String, List<ScriptNode>>) instruction.getArg(1);
            NpcPrefabRegistry.register(prefabId, propsNodes);
        }

        private Object spawnNpcPrefab(String prefabId, String instanceId,
                double x, double y, double z, Map<String, Object> overrides) {
            Map<String, List<ScriptNode>> templateNodes = NpcPrefabRegistry.get(prefabId);
            if (templateNodes == null) {
                LOGGER.warn("[spawnNpcPrefab] unknown prefab '{}'", prefabId);
                return null;
            }
            Map<String, List<ScriptNode>> mergedNodes = NpcPrefabRegistry.copyProps(templateNodes);
            Map<String, List<Object>> props = new HashMap<>();
            for (var entry : mergedNodes.entrySet()) {
                List<Object> values = new ArrayList<>();
                for (ScriptNode node : entry.getValue()) {
                    values.add(evaluateExpression(node));
                }
                props.put(normPropKey(entry.getKey()), values);
            }
            if (overrides != null) {
                for (var entry : overrides.entrySet()) {
                    props.put(normPropKey(entry.getKey()), List.of(entry.getValue()));
                }
            }
            props.put("pos", List.of(x, y, z));
            var entity = NpcSpawnHelper.spawn(instanceId, props, source, variables);
            SceneActiveState sceneState = SceneScriptSpawn.resolveState(variables, props);
            if (entity != null && sceneState != null) {
                String parentId = SceneScriptSpawn.resolveParentId(sceneState, variables, props);
                SceneScriptSpawn.attachEntity(sceneState, parentId, instanceId,
                        org.zonarstudio.spraute_engine.scene.SceneNodeType.NPC, props, entity, variables);
            }
            return entity;
        }

        private void executeNpcBlock(CompiledScript.Instruction instruction) {
            String scriptId = (String) instruction.getArg(0);
            @SuppressWarnings("unchecked")
            Map<String, List<ScriptNode>> propsNodes = (Map<String, List<ScriptNode>>) instruction.getArg(1);

            Map<String, List<Object>> props = new HashMap<>();
            for (var entry : propsNodes.entrySet()) {
                List<Object> values = new ArrayList<>();
                for (ScriptNode node : entry.getValue()) {
                    values.add(evaluateExpression(node));
                }
                props.put(normPropKey(entry.getKey()), values);
            }

            try {
                var entity = NpcSpawnHelper.spawn(scriptId, props, source, variables);
                SceneActiveState sceneState = SceneScriptSpawn.resolveState(variables, props);
                if (entity != null && sceneState != null) {
                    String parentId = SceneScriptSpawn.resolveParentId(sceneState, variables, props);
                    SceneScriptSpawn.attachEntity(sceneState, parentId, scriptId,
                            org.zonarstudio.spraute_engine.scene.SceneNodeType.NPC, props, entity, variables);
                } else if (entity != null && !SceneRuntime.listLoaded().isEmpty()) {
                    LOGGER.warn("[Spraute Scene] NPC '{}' spawned without hierarchy — укажите scene/parent или оставьте одну сцену запущенной",
                            scriptId);
                }
            } catch (Exception e) {
                LOGGER.error("Failed to execute NPC block: {}", e.getMessage());
            }
        }

        private void executeModelBlock(CompiledScript.Instruction instruction) {
            String scriptId = (String) instruction.getArg(0);
            @SuppressWarnings("unchecked")
            Map<String, List<ScriptNode>> propsNodes = (Map<String, List<ScriptNode>>) instruction.getArg(1);

            Map<String, List<Object>> props = new HashMap<>();
            for (var entry : propsNodes.entrySet()) {
                List<Object> values = new ArrayList<>();
                for (ScriptNode node : entry.getValue()) {
                    values.add(evaluateExpression(node));
                }
                props.put(normPropKey(entry.getKey()), values);
            }

            try {
                var entity = ModelSpawnHelper.spawn(scriptId, props, source, variables);
                SceneActiveState sceneState = SceneScriptSpawn.resolveState(variables, props);
                if (entity != null && sceneState != null) {
                    String parentId = SceneScriptSpawn.resolveParentId(sceneState, variables, props);
                    SceneScriptSpawn.attachEntity(sceneState, parentId, scriptId,
                            org.zonarstudio.spraute_engine.scene.SceneNodeType.MODEL, props, entity, variables);
                } else if (entity != null && !SceneRuntime.listLoaded().isEmpty()) {
                    LOGGER.warn("[Spraute Scene] Model '{}' spawned without hierarchy — укажите scene/parent или оставьте одну сцену запущенной",
                            scriptId);
                }
            } catch (Exception e) {
                LOGGER.error("Failed to execute model block: {}", e.getMessage());
            }
        }

        private void executeHitboxBlock(CompiledScript.Instruction instruction) {
            String scriptId = (String) instruction.getArg(0);
            @SuppressWarnings("unchecked")
            Map<String, List<ScriptNode>> propsNodes = (Map<String, List<ScriptNode>>) instruction.getArg(1);

            Map<String, List<Object>> props = new HashMap<>();
            for (var entry : propsNodes.entrySet()) {
                List<Object> values = new ArrayList<>();
                for (ScriptNode node : entry.getValue()) {
                    values.add(evaluateExpression(node));
                }
                props.put(normPropKey(entry.getKey()), values);
            }

            try {
                SceneActiveState sceneState = SceneScriptSpawn.resolveState(variables, props);
                String parentId = SceneScriptSpawn.resolveParentId(sceneState, variables, props);
                SceneScriptSpawn.spawnHitbox(sceneState, parentId, scriptId, props, variables);
            } catch (Exception e) {
                LOGGER.error("Failed to execute hitbox block: {}", e.getMessage());
                throw new RuntimeException(e.getMessage(), e);
            }
        }

        private void executeEmptyBlock(CompiledScript.Instruction instruction) {
            String scriptId = (String) instruction.getArg(0);
            @SuppressWarnings("unchecked")
            Map<String, List<ScriptNode>> propsNodes = (Map<String, List<ScriptNode>>) instruction.getArg(1);

            Map<String, List<Object>> props = new HashMap<>();
            for (var entry : propsNodes.entrySet()) {
                List<Object> values = new ArrayList<>();
                for (ScriptNode node : entry.getValue()) {
                    values.add(evaluateExpression(node));
                }
                props.put(normPropKey(entry.getKey()), values);
            }

            try {
                SceneActiveState sceneState = SceneScriptSpawn.resolveState(variables, props);
                String parentId = SceneScriptSpawn.resolveParentId(sceneState, variables, props);
                SceneScriptSpawn.spawnEmpty(sceneState, parentId, scriptId, props, variables);
            } catch (Exception e) {
                LOGGER.error("Failed to execute empty block: {}", e.getMessage());
                throw new RuntimeException(e.getMessage(), e);
            }
        }

        private void executeSkinOverlayBlock(CompiledScript.Instruction instruction) {
            String scriptId = (String) instruction.getArg(0);
            @SuppressWarnings("unchecked")
            Map<String, List<ScriptNode>> propsNodes = (Map<String, List<ScriptNode>>) instruction.getArg(1);

            Map<String, List<Object>> props = new HashMap<>();
            for (var entry : propsNodes.entrySet()) {
                List<Object> values = new ArrayList<>();
                for (ScriptNode node : entry.getValue()) {
                    values.add(evaluateExpression(node));
                }
                props.put(normPropKey(entry.getKey()), values);
            }

            try {
                SceneActiveState sceneState = SceneScriptSpawn.resolveState(variables, props);
                String parentId = SceneScriptSpawn.resolveParentId(sceneState, variables, props);
                SceneScriptSpawn.spawnSkinOverlay(sceneState, parentId, scriptId, props, variables);
            } catch (Exception e) {
                LOGGER.error("Failed to execute skin_overlay block: {}", e.getMessage());
                throw new RuntimeException(e.getMessage(), e);
            }
        }

        private void executeParticleEmitterBlock(CompiledScript.Instruction instruction) {
            String scriptId = (String) instruction.getArg(0);
            @SuppressWarnings("unchecked")
            Map<String, List<ScriptNode>> propsNodes = (Map<String, List<ScriptNode>>) instruction.getArg(1);

            Map<String, List<Object>> props = new HashMap<>();
            for (var entry : propsNodes.entrySet()) {
                List<Object> values = new ArrayList<>();
                for (ScriptNode node : entry.getValue()) {
                    values.add(evaluateExpression(node));
                }
                props.put(normPropKey(entry.getKey()), values);
            }

            try {
                SceneActiveState sceneState = SceneScriptSpawn.resolveState(variables, props);
                String parentId = SceneScriptSpawn.resolveParentId(sceneState, variables, props);
                SceneScriptSpawn.spawnParticleEmitter(sceneState, parentId, scriptId, props, variables);
            } catch (Exception e) {
                LOGGER.error("Failed to execute particleEmitter block: {}", e.getMessage());
                throw new RuntimeException(e.getMessage(), e);
            }
        }

        private void executeParticleDefBlock(CompiledScript.Instruction instruction) {
            String scriptId = (String) instruction.getArg(0);
            @SuppressWarnings("unchecked")
            Map<String, List<ScriptNode>> propsNodes = (Map<String, List<ScriptNode>>) instruction.getArg(1);

            Map<String, List<Object>> props = new HashMap<>();
            for (var entry : propsNodes.entrySet()) {
                List<Object> values = new ArrayList<>();
                for (ScriptNode node : entry.getValue()) {
                    values.add(evaluateExpression(node));
                }
                props.put(normPropKey(entry.getKey()), values);
            }

            try {
                SceneActiveState sceneState = SceneScriptSpawn.resolveState(variables, props);
                String parentId = SceneScriptSpawn.resolveParentId(sceneState, variables, props);
                SceneScriptSpawn.spawnParticleDef(sceneState, parentId, scriptId, props, variables);
            } catch (Exception e) {
                LOGGER.error("Failed to execute particle block: {}", e.getMessage());
                throw new RuntimeException(e.getMessage(), e);
            }
        }

        private void executeProjectileDefBlock(CompiledScript.Instruction instruction) {
            String scriptId = (String) instruction.getArg(0);
            @SuppressWarnings("unchecked")
            Map<String, List<ScriptNode>> propsNodes = (Map<String, List<ScriptNode>>) instruction.getArg(1);

            Map<String, List<Object>> props = new HashMap<>();
            for (var entry : propsNodes.entrySet()) {
                List<Object> values = new ArrayList<>();
                for (ScriptNode node : entry.getValue()) {
                    values.add(evaluateExpression(node));
                }
                props.put(normPropKey(entry.getKey()), values);
            }

            try {
                SceneActiveState sceneState = SceneScriptSpawn.resolveState(variables, props);
                String parentId = SceneScriptSpawn.resolveParentId(sceneState, variables, props);
                SceneScriptSpawn.spawnProjectileDef(sceneState, parentId, scriptId, props, variables);
            } catch (Exception e) {
                LOGGER.error("Failed to execute projectile block: {}", e.getMessage());
                throw new RuntimeException(e.getMessage(), e);
            }
        }

        private void executeLightBlock(CompiledScript.Instruction instruction) {
            String scriptId = (String) instruction.getArg(0);
            @SuppressWarnings("unchecked")
            Map<String, List<ScriptNode>> propsNodes = (Map<String, List<ScriptNode>>) instruction.getArg(1);

            Map<String, List<Object>> props = new HashMap<>();
            for (var entry : propsNodes.entrySet()) {
                List<Object> values = new ArrayList<>();
                for (ScriptNode node : entry.getValue()) {
                    values.add(evaluateExpression(node));
                }
                props.put(normPropKey(entry.getKey()), values);
            }

            try {
                SceneActiveState sceneState = SceneScriptSpawn.resolveState(variables, props);
                String parentId = SceneScriptSpawn.resolveParentId(sceneState, variables, props);
                SceneScriptSpawn.spawnLight(sceneState, parentId, scriptId, props, variables);
            } catch (Exception e) {
                LOGGER.error("Failed to execute light block: {}", e.getMessage());
                throw new RuntimeException(e.getMessage(), e);
            }
        }

        private void executeAudioSourceBlock(CompiledScript.Instruction instruction) {
            String scriptId = (String) instruction.getArg(0);
            @SuppressWarnings("unchecked")
            Map<String, List<ScriptNode>> propsNodes = (Map<String, List<ScriptNode>>) instruction.getArg(1);

            Map<String, List<Object>> props = new HashMap<>();
            for (var entry : propsNodes.entrySet()) {
                List<Object> values = new ArrayList<>();
                for (ScriptNode node : entry.getValue()) {
                    values.add(evaluateExpression(node));
                }
                props.put(normPropKey(entry.getKey()), values);
            }

            try {
                SceneActiveState sceneState = SceneScriptSpawn.resolveState(variables, props);
                String parentId = SceneScriptSpawn.resolveParentId(sceneState, variables, props);
                SceneScriptSpawn.spawnAudioSource(sceneState, parentId, scriptId, props, variables);
            } catch (Exception e) {
                LOGGER.error("Failed to execute audioSource block: {}", e.getMessage());
                throw new RuntimeException(e.getMessage(), e);
            }
        }

        private void executePostProcessBlock(CompiledScript.Instruction instruction) {
            String scriptId = (String) instruction.getArg(0);
            @SuppressWarnings("unchecked")
            Map<String, List<ScriptNode>> propsNodes = (Map<String, List<ScriptNode>>) instruction.getArg(1);

            Map<String, List<Object>> props = new HashMap<>();
            for (var entry : propsNodes.entrySet()) {
                List<Object> values = new ArrayList<>();
                for (ScriptNode node : entry.getValue()) {
                    values.add(evaluateExpression(node));
                }
                props.put(normPropKey(entry.getKey()), values);
            }

            try {
                SceneActiveState sceneState = SceneScriptSpawn.resolveState(variables, props);
                String parentId = SceneScriptSpawn.resolveParentId(sceneState, variables, props);
                SceneScriptSpawn.spawnPostProcess(sceneState, parentId, scriptId, props, variables);
            } catch (Exception e) {
                LOGGER.error("Failed to execute postProcess block: {}", e.getMessage());
                throw new RuntimeException(e.getMessage(), e);
            }
        }

        private void executeCustomNodeBlock(CompiledScript.Instruction instruction) {
            String typeId = (String) instruction.getArg(0);
            String scriptId = (String) instruction.getArg(1);
            @SuppressWarnings("unchecked")
            Map<String, List<ScriptNode>> propsNodes = (Map<String, List<ScriptNode>>) instruction.getArg(2);

            Map<String, List<Object>> props = new HashMap<>();
            for (var entry : propsNodes.entrySet()) {
                List<Object> values = new ArrayList<>();
                for (ScriptNode node : entry.getValue()) {
                    values.add(evaluateExpression(node));
                }
                props.put(normPropKey(entry.getKey()), values);
            }

            try {
                SceneActiveState sceneState = SceneScriptSpawn.resolveState(variables, props);
                String parentId = SceneScriptSpawn.resolveParentId(sceneState, variables, props);
                SceneScriptSpawn.spawnCustom(sceneState, parentId, typeId, scriptId, props, variables);
            } catch (Exception e) {
                LOGGER.error("Failed to execute custom node block '{}': {}", typeId, e.getMessage());
                throw new RuntimeException(e.getMessage(), e);
            }
        }

        private String resolveAsyncTaskId(Object taskIdArg) {
            if (taskIdArg instanceof ScriptNode node) {
                Object evaluated = evaluateExpression(node);
                if (evaluated == null) {
                    return "anon_" + System.nanoTime();
                }
                String id = String.valueOf(evaluated).trim();
                if (id.isEmpty() || "null".equals(id)) {
                    return "anon_" + System.nanoTime();
                }
                return id;
            }
            if (taskIdArg instanceof String s && !s.isEmpty()) {
                return s;
            }
            return "anon_" + System.nanoTime();
        }

        private Object evaluateExpression(ScriptNode node) {
            if (node instanceof ScriptNode.LiteralNode literal) {
                return literal.getValue();
            }
            if (node instanceof ScriptNode.ListLiteralNode listNode) {
                List<Object> out = new ArrayList<>();
                for (ScriptNode el : listNode.getElements()) {
                    out.add(evaluateExpression(el));
                }
                return out;
            }
            if (node instanceof ScriptNode.MapLiteralNode mapNode) {
                java.util.Map<String, Object> out = new java.util.LinkedHashMap<>();
                for (var e : mapNode.getEntries().entrySet()) {
                    out.put(e.getKey(), evaluateExpression(e.getValue()));
                }
                return out;
            }
            if (node instanceof ScriptNode.IndexAccessNode idxNode) {
                Object obj = evaluateExpression(idxNode.getObject());
                Object key = evaluateExpression(idxNode.getIndex());
                if (obj instanceof java.util.List list) {
                    if (key instanceof Number n) {
                        int i = n.intValue();
                        if (i >= 0 && i < list.size()) return list.get(i);
                    }
                    return null;
                }
                if (obj instanceof java.util.Map map) {
                    return map.get(String.valueOf(key));
                }
                throw new RuntimeException("Cannot index into " + (obj != null ? obj.getClass().getSimpleName() : "null"));
            }
            if (node instanceof ScriptNode.IdentifierNode id) {
                String name = id.getName();
                if (name == null || "null".equals(name)) return null;
                if (currentTaskScope != null && currentTaskScope.taskLocals.containsKey(name)) {
                    return currentTaskScope.taskLocals.get(name);
                }
                if (variables.containsKey(name)) return variables.get(name);
                if (globalVariables.containsKey(name)) return globalVariables.get(name);
                net.minecraft.server.level.ServerLevel level = source.getLevel();
                if (level != null) {
                    ScriptWorldData world = ScriptWorldData.get(level);
                    if (world.has(name)) return world.get(name, source.getServer(), level);
                }
                if (org.zonarstudio.spraute_engine.entity.NpcManager.get(name) != null
                        || org.zonarstudio.spraute_engine.entity.ModelManager.get(name) != null) {
                    return name;
                }
                if (org.zonarstudio.spraute_engine.script.ItemStackScriptUtil.isAnyPlayerKeyword(name)) {
                    return "any";
                }
                throw new RuntimeException("Undefined variable: " + name);
            }
            if (node instanceof ScriptNode.FunctionCallNode call) {
                // Check user-defined functions (own + imported) first
                UserFunction uf2 = resolveFunction(call.getFunctionName());
                if (uf2 != null) {
                    List<Object> args = new ArrayList<>();
                    for (ScriptNode argNode : call.getArgs()) {
                        args.add(evaluateExpression(argNode));
                    }
                    return callUserFunction(call.getFunctionName(), args);
                }

                var function = org.zonarstudio.spraute_engine.script.function.FunctionRegistry.get(call.getFunctionName());
                if (function != null) {
                    List<Object> args = new ArrayList<>();
                    for (ScriptNode argNode : call.getArgs()) {
                        args.add(evaluateExpression(argNode));
                    }
                    return function.execute(
                            org.zonarstudio.spraute_engine.script.function.FunctionArgs.coerce(
                                    function, unwrapPlayerNodeArgs(args)),
                            source, context);
                }
                
                // Built-in variable checks
                if (("hasVar".equals(call.getFunctionName()) || "has_var".equals(call.getFunctionName())) && !call.getArgs().isEmpty()) {
                    Object arg = evaluateExpression(call.getArgs().get(0));
                    String name = String.valueOf(arg);
                    if (variables.containsKey(name) || globalVariables.containsKey(name)) return true;
                    net.minecraft.server.level.ServerLevel level = source.getLevel();
                    if (level != null && ScriptWorldData.get(level).has(name)) return true;
                    return false;
                }
                
                throw new RuntimeException("Unknown function: " + call.getFunctionName());
            }
            if (node instanceof ScriptNode.PropertyAccessNode prop) {
                Object obj = evaluateExpression(prop.getObject());
                String objName = "";
                if (obj == null && prop.getObject() instanceof ScriptNode.IdentifierNode idNode) {
                    objName = idNode.getName();
                    if (resolveEntity(objName) != null) {
                        obj = objName;
                    }
                }
                
                if (obj instanceof java.util.Map map) {
                    return map.get(normPropKey(prop.getPropertyName()));
                }

                if (obj instanceof org.zonarstudio.spraute_engine.scene.SceneNodeView sceneNode) {
                    Object v = sceneNode.getProperty(normPropKey(prop.getPropertyName()), source);
                    if (v != null) return v;
                    net.minecraft.world.entity.Entity spawned = sceneNode.getEntity(source);
                    if (spawned != null) obj = spawned;
                    else return null;
                }
                if (obj instanceof org.zonarstudio.spraute_engine.scene.SceneView scene) {
                    return scene.getProperty(normPropKey(prop.getPropertyName()));
                }

                if (obj instanceof net.minecraft.world.entity.player.Player player) {
                    return org.zonarstudio.spraute_engine.script.ScriptPropertyAccess.get(
                            player, prop.getPropertyName(), source);
                }
                
                net.minecraft.world.entity.Entity npcEntity = null;
                if (obj instanceof net.minecraft.world.entity.Entity directEntity) {
                    npcEntity = directEntity;
                } else if (obj instanceof String entityId) {
                    npcEntity = resolveEntity(entityId);
                } else if (obj == null && !objName.isEmpty()) {
                    npcEntity = resolveEntity(objName);
                }
                
                if (npcEntity != null) {
                    final net.minecraft.world.entity.Entity transformEntity = npcEntity;
                    String npcProp = normPropKey(prop.getPropertyName());
                    return switch (npcProp) {
                        case "name" -> transformEntity.getName().getString();
                        case "id", "nodeid", "node_id", "scriptid", "script_id" -> {
                            String id = org.zonarstudio.spraute_engine.scene.SceneRuntime.scriptIdFromEntity(transformEntity);
                            yield id == null ? "" : id;
                        }
                        case "sceneid", "scene_id" -> {
                            String sid = org.zonarstudio.spraute_engine.scene.SceneRuntime.sceneIdFromEntity(transformEntity);
                            yield sid == null ? "" : sid;
                        }
                        case "node" -> org.zonarstudio.spraute_engine.scene.SceneRuntime.nodeViewFromEntity(transformEntity);
                        case "showName" -> transformEntity.isCustomNameVisible();
                        case "hp" -> transformEntity instanceof net.minecraft.world.entity.LivingEntity living ? living.getHealth() : 0;
                        case "maxHp" -> transformEntity instanceof net.minecraft.world.entity.LivingEntity lh ? (lh.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH) != null 
                            ? lh.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).getBaseValue() : 0) : 0;
                        case "speed" -> transformEntity instanceof net.minecraft.world.entity.LivingEntity ls ? (ls.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED) != null 
                            ? ls.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED).getBaseValue() : 0) : 0;
                        case "walkTurnSpeed" -> transformEntity instanceof org.zonarstudio.spraute_engine.entity.SprauteNpcEntity snpc
                                ? snpc.getWalkTurnSpeed() : 0f;
                        case "idleTurnSpeed" -> transformEntity instanceof org.zonarstudio.spraute_engine.entity.SprauteNpcEntity snpc
                                ? snpc.getIdleTurnSpeed() : 0f;
                        case "x" -> transformEntity.getX();
                        case "y" -> transformEntity.getY();
                        case "z" -> transformEntity.getZ();
                        case "yaw" -> transformEntity.getYRot();
                        case "pitch" -> transformEntity.getXRot();
                        case "routeindex", "route_index", "routepointindex", "route_point_index" ->
                                transformEntity instanceof org.zonarstudio.spraute_engine.entity.SprauteNpcEntity snpc
                                        ? snpc.getRouteIndex() : -1;
                        case "routepointcount", "route_point_count", "routepoints" ->
                                transformEntity instanceof org.zonarstudio.spraute_engine.entity.SprauteNpcEntity snpc
                                        ? snpc.getRoutePointCount() : 0;
                        case "followingroute", "following_route", "isfollowingroute", "is_following_route" ->
                                transformEntity instanceof org.zonarstudio.spraute_engine.entity.SprauteNpcEntity snpc
                                        && snpc.isFollowingRoute();
                        case "transform" -> new ScriptTransformView(transformEntity, new ScriptTransformView.PropertyBridge() {
                            @Override public Object get(String key) {
                                return readEntityTransformProp(transformEntity, key);
                            }
                            @Override public boolean set(String key, Object value) {
                                return writeEntityTransformProp(transformEntity, key, value);
                            }
                        });
                        case "rotationX", "rotX" -> GeoEntityScriptUtil.getModelRotationX(transformEntity);
                        case "rotationY", "rotY" -> GeoEntityScriptUtil.getModelRotationY(transformEntity);
                        case "rotationZ", "rotZ", "roll" -> GeoEntityScriptUtil.getModelRotationZ(transformEntity);
                        case "scale" -> (GeoEntityScriptUtil.getModelScaleX(transformEntity)
                                + GeoEntityScriptUtil.getModelScaleY(transformEntity)
                                + GeoEntityScriptUtil.getModelScaleZ(transformEntity)) / 3f;
                        case "scaleX" -> GeoEntityScriptUtil.getModelScaleX(transformEntity);
                        case "scaleY" -> GeoEntityScriptUtil.getModelScaleY(transformEntity);
                        case "scaleZ" -> GeoEntityScriptUtil.getModelScaleZ(transformEntity);
                        case "w", "width", "hitboxW", "hitboxw", "hitboxWidth", "hitboxwidth", "hitbox_width" ->
                                transformEntity instanceof org.zonarstudio.spraute_engine.entity.SprauteNpcEntity npc
                                        ? npc.getHitboxWidth() : 0f;
                        case "h", "height", "hitboxH", "hitboxh", "hitboxHeight", "hitboxheight", "hitbox_height" ->
                                transformEntity instanceof org.zonarstudio.spraute_engine.entity.SprauteNpcEntity npc
                                        ? npc.getHitboxHeight() : 0f;
                        case "uuid" -> transformEntity.getUUID().toString();
                        case "type", "entityType", "entityId", "mobId", "mob_id" ->
                                EntityScriptUtil.getTypeId(transformEntity);
                        case "pathfailed", "path_failed" -> transformEntity instanceof org.zonarstudio.spraute_engine.entity.SprauteNpcEntity npc ? npc.isPathFailed() : false;
                        case "isAlive", "alive" -> transformEntity.isAlive();
                        case "java" -> transformEntity;
                        case "model" -> GeoEntityScriptUtil.getModel(transformEntity);
                        case "texture" -> GeoEntityScriptUtil.getTexture(transformEntity);
                        case "animation" -> GeoEntityScriptUtil.getAnimation(transformEntity);
                        case "translucent", "transparent" -> GeoEntityScriptUtil.isTranslucent(transformEntity);
                        case "opacity" -> GeoEntityScriptUtil.getOpacity(transformEntity);
                        case "lod", "lodEnabled" -> GeoEntityScriptUtil.isLodEnabled(transformEntity);
                        case "renderDistance" -> GeoEntityScriptUtil.getRenderDistance(transformEntity);
                        case "forceVisible" -> GeoEntityScriptUtil.isForceVisible(transformEntity);
                        case "loadChunks", "keepChunks", "keepLoaded" ->
                                transformEntity instanceof org.zonarstudio.spraute_engine.entity.SprauteNpcEntity npc
                                        && npc.isLoadChunks();
                        case "dropItem" -> transformEntity instanceof org.zonarstudio.spraute_engine.entity.SprauteNpcEntity npc ? (!npc.customDrops.isEmpty() ? npc.customDrops.get(0).itemId : "") : "";
                        case "dropMin" -> transformEntity instanceof org.zonarstudio.spraute_engine.entity.SprauteNpcEntity npc ? (!npc.customDrops.isEmpty() ? npc.customDrops.get(0).min : 0) : 0;
                        case "dropMax" -> transformEntity instanceof org.zonarstudio.spraute_engine.entity.SprauteNpcEntity npc ? (!npc.customDrops.isEmpty() ? npc.customDrops.get(0).max : 0) : 0;
                        case "dropChance" -> transformEntity instanceof org.zonarstudio.spraute_engine.entity.SprauteNpcEntity npc ? (!npc.customDrops.isEmpty() ? npc.customDrops.get(0).chance : 0) : 0;
                        default -> transformEntity instanceof org.zonarstudio.spraute_engine.entity.SprauteNpcEntity npc ? npc.customData.get(prop.getPropertyName()) : null;                    };
                }
                
                if (obj != null) {
                    Object reflectionResult = org.zonarstudio.spraute_engine.script.util.ForgeReflection.getField(obj, prop.getPropertyName());
                    if (reflectionResult != null) return reflectionResult;
                }
                
                if (obj == null) {
                    LOGGER.warn("[Script] Cannot access property '{}' on null object '{}'", prop.getPropertyName(), objName);
                }
                return null;
            }
            if (node instanceof ScriptNode.MethodCallNode methodCall) {
                Object obj = evaluateExpression(methodCall.getObject());
                String objName = "";
                if (obj == null && methodCall.getObject() instanceof ScriptNode.IdentifierNode idNode) {
                    objName = idNode.getName();
                    if (resolveEntity(objName) != null) {
                        obj = objName;
                    }
                }
                
                String method = methodCall.getMethodName();
                List<Object> methodArgs = new ArrayList<>();
                for (ScriptNode argNode : methodCall.getArgs()) {
                    methodArgs.add(evaluateExpression(argNode));
                }

                if (obj instanceof String npcId) {
                    net.minecraft.world.entity.Entity resolved = resolveEntity(npcId);
                    if (resolved != null) obj = resolved;
                }
                if (obj == null && !objName.isEmpty()) {
                    obj = resolveEntity(objName);
                }

                if (obj instanceof org.zonarstudio.spraute_engine.scene.SceneNodeView sceneNode) {
                    Object sceneResult = sceneNode.invokeMethod(
                            method != null ? method.toLowerCase() : "", methodArgs, source);
                    if (!org.zonarstudio.spraute_engine.scene.SceneNodeView.isUnhandled(sceneResult)) {
                        return sceneResult;
                    }
                    net.minecraft.world.entity.Entity spawned = sceneNode.getEntity(source);
                    if (spawned != null) obj = spawned;
                    else return null;
                }

                if (obj instanceof net.minecraft.world.entity.Entity entity) {
                    if (method.equals("java")) return entity;
                    String em = method != null ? method.toLowerCase() : "";
                    if (em.equals("uuid")) return entity.getUUID().toString();
                    if (em.equals("id") || em.equals("nodeid") || em.equals("scriptid") || em.equals("getid")) {
                        String id = org.zonarstudio.spraute_engine.scene.SceneRuntime.scriptIdFromEntity(entity);
                        return id == null ? "" : id;
                    }
                    if ((em.equals("node") || em.equals("scenenode")) && methodArgs.isEmpty()) {
                        return org.zonarstudio.spraute_engine.scene.SceneRuntime.nodeViewFromEntity(entity);
                    }
                    if (em.equals("sceneid") && methodArgs.isEmpty()) {
                        String sid = org.zonarstudio.spraute_engine.scene.SceneRuntime.sceneIdFromEntity(entity);
                        return sid == null ? "" : sid;
                    }
                    if (entity instanceof net.minecraft.world.entity.LivingEntity living) {
                        String m = method != null ? method.toLowerCase() : "";
                        if (m.equals("facing") || m.equals("direction") || m.equals("lookvector") || m.equals("look")) {
                            return org.zonarstudio.spraute_engine.script.util.EntityFacingUtil.buildFacing(living);
                        }
                    }
                    if (method.equals("distanceTo") || method.equals("distanceto")) {
                        net.minecraft.world.entity.Entity other = resolveEntity(methodArgs.isEmpty() ? null : methodArgs.get(0));
                        if (other != null) return entity.distanceTo(other);
                        return 0.0;
                    }
                    if (entity instanceof net.minecraft.world.entity.player.Player player) {
                        Object skinResult = org.zonarstudio.spraute_engine.scene.PlayerSkinOps.invokePlayer(
                                player, method, methodArgs);
                        if (skinResult != org.zonarstudio.spraute_engine.scene.PlayerSkinOps.UNHANDLED) {
                            return skinResult == org.zonarstudio.spraute_engine.scene.PlayerSkinOps.HANDLED_NULL
                                    ? null : skinResult;
                        }
                    }
                }

                Object curveCall = org.zonarstudio.spraute_engine.scene.custom.ScriptCurve.invoke(obj, method, methodArgs);
                if (curveCall != org.zonarstudio.spraute_engine.scene.custom.ScriptCurve.UNHANDLED) {
                    return curveCall;
                }

                if (obj instanceof java.util.List list) {
                    return switch (method) {
                        case "add" -> { list.add(methodArgs.isEmpty() ? null : methodArgs.get(0)); yield null; }
                        case "get" -> {
                            if (!methodArgs.isEmpty() && methodArgs.get(0) instanceof Number n) {
                                int idx = n.intValue();
                                if (idx >= 0 && idx < list.size()) yield list.get(idx);
                            }
                            yield null;
                        }
                        case "size" -> list.size();
                        case "remove" -> {
                            if (!methodArgs.isEmpty() && methodArgs.get(0) instanceof Number n) {
                                int idx = n.intValue();
                                if (idx >= 0 && idx < list.size()) list.remove(idx);
                            }
                            yield null;
                        }
                        default -> null;
                    };
                }

                if (obj instanceof java.util.Map map) {
                    return switch (method) {
                        case "put", "set" -> {
                            if (methodArgs.size() >= 2) map.put(String.valueOf(methodArgs.get(0)), methodArgs.get(1));
                            yield null;
                        }
                        case "get" -> {
                            if (!methodArgs.isEmpty()) yield map.get(String.valueOf(methodArgs.get(0)));
                            yield null;
                        }
                        case "size" -> map.size();
                        case "remove" -> {
                            if (!methodArgs.isEmpty()) map.remove(String.valueOf(methodArgs.get(0)));
                            yield null;
                        }
                        default -> null;
                    };
                }

                if (obj instanceof org.zonarstudio.spraute_engine.entity.SprauteNpcEntity npc) {
                    String m = method != null ? method.toLowerCase() : "";
                    if ("countItem".equals(m) || "countitem".equals(m)) {
                        if (!methodArgs.isEmpty()) {
                            String itemId = String.valueOf(methodArgs.get(0));
                            if (methodArgs.size() >= 2) {
                                String nbt = String.valueOf(methodArgs.get(1));
                                if ("null".equals(nbt) || (nbt != null && nbt.isEmpty())) nbt = null;
                                return npc.countItem(itemId, nbt);
                            }
                            return npc.countItem(itemId);
                        }
                        return 0;
                    }
                    if ("canreach".equals(m) && methodArgs.size() >= 3) {
                        return npc.canReach(
                                ((Number) methodArgs.get(0)).doubleValue(),
                                ((Number) methodArgs.get(1)).doubleValue(),
                                ((Number) methodArgs.get(2)).doubleValue());
                    }
                }

                if (obj instanceof net.minecraft.world.entity.player.Player player
                        && org.zonarstudio.spraute_engine.script.PlayerScriptMethods.isKnown(method)) {
                    return org.zonarstudio.spraute_engine.script.PlayerScriptMethods.invoke(
                            player, method, methodArgs, this::performRaycast);
                }

                if (obj instanceof String str) {
                    return switch (method) {
                        case "toInt" -> {
                            String trimmed = str.trim();
                            try {
                                yield Integer.parseInt(trimmed);
                            } catch (Exception e) {
                                try {
                                    yield (int) Double.parseDouble(trimmed);
                                } catch (Exception e2) {
                                    yield null;
                                }
                            }
                        }
                        case "toDouble" -> { try { yield Double.parseDouble(str); } catch(Exception e){ yield null; } }
                        case "toString" -> str;
                        case "length" -> str.length();
                        case "split" -> {
                            String regex = methodArgs.isEmpty() ? " " : String.valueOf(methodArgs.get(0));
                            yield new ArrayList<>(java.util.Arrays.asList(str.split(regex)));
                        }
                        case "contains" -> {
                            String seq = methodArgs.isEmpty() ? "" : String.valueOf(methodArgs.get(0));
                            yield str.contains(seq);
                        }
                        case "replace" -> {
                            String target = methodArgs.isEmpty() ? "" : String.valueOf(methodArgs.get(0));
                            String replacement = methodArgs.size() > 1 ? String.valueOf(methodArgs.get(1)) : "";
                            yield str.replace(target, replacement);
                        }
                        default -> org.zonarstudio.spraute_engine.script.util.ForgeReflection.invokeMethod(obj, method, methodArgs);
                    };
                }

                if (obj instanceof Number num) {
                    return switch (method) {
                        case "toInt" -> num.intValue();
                        case "toDouble" -> num.doubleValue();
                        case "toString" -> num.toString();
                        default -> org.zonarstudio.spraute_engine.script.util.ForgeReflection.invokeMethod(obj, method, methodArgs);
                    };
                }

                // Fallback to Reflection
                if (obj != null) {
                    Object reflectionResult = org.zonarstudio.spraute_engine.script.util.ForgeReflection.invokeMethod(obj, method, methodArgs);
                    if (reflectionResult != null) return reflectionResult;
                }

                return null;
            }
            if (node instanceof ScriptNode.UnaryNotNode unaryNot) {
                Object operand = evaluateExpression(unaryNot.getOperand());
                return !isTruthy(operand);
            }
            if (node instanceof ScriptNode.UnaryMinusNode unaryMinus) {
                Object operand = evaluateExpression(unaryMinus.getOperand());
                if (operand instanceof Integer i) return -i;
                if (operand instanceof Long l) return -l;
                if (operand instanceof Float f) return -f;
                if (operand instanceof Number n) return -n.doubleValue();
                return 0;
            }
            if (node instanceof ScriptNode.BinaryExpressionNode binary) {
                if (binary.getOperator().getType() == ScriptToken.TokenType.AND) {
                    Object left = evaluateExpression(binary.getLeft());
                    if (!isTruthy(left)) return false;
                    return isTruthy(evaluateExpression(binary.getRight()));
                }
                if (binary.getOperator().getType() == ScriptToken.TokenType.OR) {
                    Object left = evaluateExpression(binary.getLeft());
                    if (isTruthy(left)) return true;
                    return isTruthy(evaluateExpression(binary.getRight()));
                }

                Object left = evaluateExpression(binary.getLeft());
                Object right = evaluateExpression(binary.getRight());

                if (left instanceof Number l && right instanceof Number r) {
                    double dl = l.doubleValue();
                    double dr = r.doubleValue();

                    return switch (binary.getOperator().getType()) {
                        case PLUS -> (l instanceof Integer && r instanceof Integer) ? (int)(dl + dr) : dl + dr;
                        case MINUS -> (l instanceof Integer && r instanceof Integer) ? (int)(dl - dr) : dl - dr;
                        case STAR -> (l instanceof Integer && r instanceof Integer) ? (int)(dl * dr) : dl * dr;
                        case SLASH -> (l instanceof Integer && r instanceof Integer) ? (int)(dl / dr) : dl / dr;
                        case SLASH_SLASH -> {
                            if (l instanceof Integer li && r instanceof Integer ri) {
                                yield Math.floorDiv(li, ri);
                            }
                            if (l instanceof Long li && r instanceof Long ri) {
                                yield Math.floorDiv(li, ri);
                            }
                            yield Math.floor(dl / dr);
                        }
                        case PERCENT -> {
                            if (l instanceof Integer li && r instanceof Integer ri) {
                                yield Math.floorMod(li, ri);
                            }
                            if (l instanceof Long li && r instanceof Long ri) {
                                yield Math.floorMod(li, ri);
                            }
                            yield dl % dr;
                        }
                        case STAR_STAR -> Math.pow(dl, dr);
                        case EQ -> dl == dr;
                        case NEQ -> dl != dr;
                        case GT -> dl > dr;
                        case LT -> dl < dr;
                        case GTE -> dl >= dr;
                        case LTE -> dl <= dr;
                        default -> 0;
                    };
                }
                
                if (binary.getOperator().getType() == ScriptToken.TokenType.PLUS) {
                    return String.valueOf(left) + String.valueOf(right);
                }
                
                if (binary.getOperator().getType() == ScriptToken.TokenType.EQ) {
                    if (left instanceof net.minecraft.world.entity.Entity entity && right instanceof String typeId) {
                        return EntityScriptUtil.matchesTypeId(typeId, entity);
                    }
                    if (right instanceof net.minecraft.world.entity.Entity entity && left instanceof String typeId) {
                        return EntityScriptUtil.matchesTypeId(typeId, entity);
                    }
                    return Objects.equals(left, right);
                }
                if (binary.getOperator().getType() == ScriptToken.TokenType.NEQ) {
                    if (left instanceof net.minecraft.world.entity.Entity entity && right instanceof String typeId) {
                        return !EntityScriptUtil.matchesTypeId(typeId, entity);
                    }
                    if (right instanceof net.minecraft.world.entity.Entity entity && left instanceof String typeId) {
                        return !EntityScriptUtil.matchesTypeId(typeId, entity);
                    }
                    return !Objects.equals(left, right);
                }
            }
            return null;
        }

        private boolean isTruthy(Object value) {
            if (value == null) return false;
            if (value instanceof Boolean b) return b;
            if (value instanceof Number n) return n.doubleValue() != 0;
            if (value instanceof String s) return !s.isEmpty();
            return true;
        }

        /** Player inventory + armor (all container slots). */
        private static int inventoryRequiredMin(int requiredCount) {
            return requiredCount <= 0 ? 1 : requiredCount;
        }

        private boolean playerMeetsInventoryRequirement(net.minecraft.server.level.ServerPlayer player, String itemId, int requiredCount) {
            return countMatchingItemsInPlayer(player, itemId, null) >= inventoryRequiredMin(requiredCount);
        }

        private void applyInventoryEventVars(net.minecraft.server.level.ServerPlayer player, String itemId) {
            variables.put("_eventPlayer", player);
            net.minecraft.world.item.Item item = org.zonarstudio.spraute_engine.script.ItemStackScriptUtil.resolveItem(itemId);
            if (item != null) {
                variables.put("_eventItemId", net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(item).toString());
            } else {
                variables.put("_eventItemId", itemId);
            }
            variables.put("_eventItemCount", countMatchingItemsInPlayer(player, itemId, null));
        }

        /** Player inventory + armor (all container slots). */
        private int countMatchingItemsInPlayer(net.minecraft.server.level.ServerPlayer player, String itemId, String nbtTag) {
            return org.zonarstudio.spraute_engine.script.ItemStackScriptUtil.countMatching(player, itemId, nbtTag);
        }

        /** Returns total count of matching items. For SprauteNpcEntity uses pickup container (not hand). */
        private int countMatchingItems(net.minecraft.world.entity.Mob mob, String itemId, String nbtTag) {
            if (mob instanceof org.zonarstudio.spraute_engine.entity.SprauteNpcEntity sprauteNpc) {
                return sprauteNpc.countItem(itemId, nbtTag);
            }
            int total = 0;
            for (net.minecraft.world.entity.EquipmentSlot slot : net.minecraft.world.entity.EquipmentSlot.values()) {
                net.minecraft.world.item.ItemStack stack = mob.getItemBySlot(slot);
                if (org.zonarstudio.spraute_engine.script.ItemStackScriptUtil.matches(stack, itemId, nbtTag)) {
                    total += stack.getCount();
                }
            }
            return total;
        }

        /** Returns first ItemStack matching itemId and optional nbtTag, or null. For SprauteNpcEntity uses pickup container. */
        private net.minecraft.world.item.ItemStack getMatchingItemStack(net.minecraft.world.entity.Mob mob, String itemId, String nbtTag) {
            if (mob instanceof org.zonarstudio.spraute_engine.entity.SprauteNpcEntity sprauteNpc) {
                for (int i = 0; i < sprauteNpc.getPickupContainer().getContainerSize(); i++) {
                    net.minecraft.world.item.ItemStack stack = sprauteNpc.getPickupContainer().getItem(i);
                    if (org.zonarstudio.spraute_engine.script.ItemStackScriptUtil.matches(stack, itemId, nbtTag)) {
                        return stack;
                    }
                }
                return null;
            }
            for (net.minecraft.world.entity.EquipmentSlot slot : net.minecraft.world.entity.EquipmentSlot.values()) {
                net.minecraft.world.item.ItemStack stack = mob.getItemBySlot(slot);
                if (org.zonarstudio.spraute_engine.script.ItemStackScriptUtil.matches(stack, itemId, nbtTag)) {
                    return stack;
                }
            }
            return null;
        }

        private boolean hasItemMatching(net.minecraft.world.entity.Mob mob, String itemId, String nbtTag) {
            return countMatchingItems(mob, itemId, nbtTag) > 0;
        }

        private void setNpcPickupAwaitCap(net.minecraft.world.entity.Entity entity, String itemId, String nbtTag,
                                          int max, Object sourceKey) {
            if (max < 0 || sourceKey == null) return;
            if (!(entity instanceof org.zonarstudio.spraute_engine.entity.SprauteNpcEntity npc)) return;
            String key = org.zonarstudio.spraute_engine.entity.SprauteNpcEntity.pickupAllowKey(itemId, nbtTag);
            java.util.Map<String, Integer> cap = new java.util.HashMap<>();
            cap.put(key, max);
            npc.setPickupMaxFrom(sourceKey, cap);
        }

        private void clearNpcPickupAwaitCap(String npcId, java.util.UUID npcUuid, Object sourceKey) {
            if (sourceKey == null || source == null || source.getLevel() == null) return;
            net.minecraft.world.entity.Entity e = npcUuid != null ? source.getLevel().getEntity(npcUuid) : null;
            if (e == null && npcId != null) {
                e = org.zonarstudio.spraute_engine.entity.NpcManager.getEntity(npcId, source.getLevel());
            }
            if (e instanceof org.zonarstudio.spraute_engine.entity.SprauteNpcEntity npc) {
                npc.setPickupMaxFrom(sourceKey, null);
            }
        }

        private void seedPickupHandlerBaseline(String handlerId, String eventName, List<Object> evaluatedArgs) {
            if (handlerId == null || !"pickup".equals(eventName) || evaluatedArgs == null || evaluatedArgs.size() < 2) {
                return;
            }
            if (source == null || source.getLevel() == null) return;
            net.minecraft.world.entity.Entity entity = resolveEntity(evaluatedArgs.get(0));
            if (entity == null && evaluatedArgs.get(0) instanceof String s) {
                entity = org.zonarstudio.spraute_engine.entity.NpcManager.getEntity(s, source.getLevel());
            }
            if (!(entity instanceof net.minecraft.world.entity.Mob mob)) return;
            String itemId = String.valueOf(evaluatedArgs.get(1));
            String tag = pickupHandlerNbt(evaluatedArgs);
            pickupHandlerLastCount.put(handlerId, countMatchingItems(mob, itemId, tag));
        }

        private void clearSyncedPickupAllows() {
            for (org.zonarstudio.spraute_engine.entity.SprauteNpcEntity npc : pickupAllowNpcs) {
                npc.clearPickupAllowsFrom(this);
            }
            pickupAllowNpcs.clear();
        }

        private static String pickupHandlerNbt(List<Object> args) {
            if (args == null || args.size() < 3) return null;
            Object a2 = args.get(2);
            if (a2 instanceof Number) return null;
            String tag = String.valueOf(a2);
            if ("null".equals(tag) || tag.isEmpty()) return null;
            return tag;
        }

        private static Integer pickupHandlerMax(List<Object> args) {
            if (args == null || args.size() < 3) return null;
            Object a2 = args.get(2);
            if (a2 instanceof Number n) return n.intValue();
            if (args.size() >= 4 && args.get(3) instanceof Number n) return n.intValue();
            return null;
        }

        private void syncNpcPickupAllows() {
            java.util.Map<org.zonarstudio.spraute_engine.entity.SprauteNpcEntity, java.util.Set<String>> desired =
                    new java.util.IdentityHashMap<>();
            java.util.Map<org.zonarstudio.spraute_engine.entity.SprauteNpcEntity, java.util.Map<String, Integer>> desiredMax =
                    new java.util.IdentityHashMap<>();
            if (source.getLevel() != null) {
                for (var entry : snapshotEventHandlers()) {
                    EventHandler handler = entry.getValue();
                    if (!handler.active || !"pickup".equals(handler.eventName) || handler.eventArgs.size() < 2) continue;
                    Object npcArg = handler.eventArgs.get(0);
                    net.minecraft.world.entity.Entity entity = resolveEntity(npcArg);
                    if (entity == null && npcArg instanceof String s) {
                        entity = org.zonarstudio.spraute_engine.entity.NpcManager.getEntity(s, source.getLevel());
                    }
                    if (!(entity instanceof org.zonarstudio.spraute_engine.entity.SprauteNpcEntity npc) || !npc.isAlive()) {
                        continue;
                    }
                    String itemId = String.valueOf(handler.eventArgs.get(1));
                    String tag = pickupHandlerNbt(handler.eventArgs);
                    String key = org.zonarstudio.spraute_engine.entity.SprauteNpcEntity.pickupAllowKey(itemId, tag);
                    desired.computeIfAbsent(npc, k -> new java.util.HashSet<>()).add(key);
                    Integer max = pickupHandlerMax(handler.eventArgs);
                    if (max != null && max >= 0) {
                        desiredMax.computeIfAbsent(npc, k -> new java.util.HashMap<>()).put(key, max);
                    }
                }
            }
            for (org.zonarstudio.spraute_engine.entity.SprauteNpcEntity prev : pickupAllowNpcs) {
                if (!desired.containsKey(prev)) prev.clearPickupAllowsFrom(this);
            }
            pickupAllowNpcs.clear();
            pickupAllowNpcs.addAll(desired.keySet());
            for (var e : desired.entrySet()) {
                e.getKey().setPickupAllowsFrom(this, e.getValue());
                e.getKey().setPickupMaxFrom(this, desiredMax.get(e.getKey()));
            }
        }

        private net.minecraft.world.entity.Entity findNearestEntity(
                java.util.function.Predicate<net.minecraft.world.entity.Entity> filter) {
            if (source == null || source.getLevel() == null) return null;
            net.minecraft.server.level.ServerLevel level = source.getLevel();
            net.minecraft.world.entity.Entity origin = source.getEntity();
            if (origin != null) {
                List<net.minecraft.world.entity.Entity> entities = level.getEntities(origin,
                        origin.getBoundingBox().inflate(64.0),
                        e -> e != null && e.isAlive() && filter.test(e));
                net.minecraft.world.entity.Entity nearest = null;
                double best = Double.MAX_VALUE;
                for (net.minecraft.world.entity.Entity e : entities) {
                    double d = origin.distanceToSqr(e);
                    if (d < best) {
                        best = d;
                        nearest = e;
                    }
                }
                return nearest;
            }
            net.minecraft.world.phys.Vec3 pos = source.getPosition();
            net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(pos, pos).inflate(64.0);
            List<net.minecraft.world.entity.Entity> entities = level.getEntitiesOfClass(net.minecraft.world.entity.Entity.class, box,
                    e -> e != null && e.isAlive() && filter.test(e));
            net.minecraft.world.entity.Entity nearest = null;
            double best = Double.MAX_VALUE;
            for (net.minecraft.world.entity.Entity e : entities) {
                double d = e.distanceToSqr(pos);
                if (d < best) {
                    best = d;
                    nearest = e;
                }
            }
            return nearest;
        }


        /**
         * тХи╨птХд╨РтХитХХтХитХЭтХитХбтХитХЬтХд╨ЯтХитХбтХд╨Т тХитХСтХитХЫтХитХЭтХитЦСтХитХЬтХитФдтХд╨У тХитХЫтХитФдтХитХЬтХитХЫтХитФВтХитХЫ тХи╨нтХи╨птХи╨▒ тХитЦУ тХитХСтХитХЫтХитХЬтХд╨ТтХитХбтХитХСтХд╨СтХд╨ТтХитХб тХитФВтХд╨РтХд╨УтХитФРтХитФРтХд╨Ы.
         * тХи╨атХитХЬтХитЦСтХитХЧтХитХЫтХитФВ тХд╨ЧтХитЦСтХд╨СтХд╨ТтХитХХ switch тХитЦУтХитХЬтХд╨УтХд╨ТтХд╨РтХитХХ executeCallMethod, тХитХЬтХитХЫ тХитЦТтХитХбтХитХЦ тХитЦТтХитХЧтХитХЫтХитХСтХитХХтХд╨РтХд╨УтХд╨ЮтХд╨ЩтХитХХтХд╨Х wait'тХитХЫтХитЦУ.
         */
        private void executeNpcGroupMethod(org.zonarstudio.spraute_engine.entity.SprauteNpcEntity npc,
                                           String method, java.util.List<Object> args,
                                           boolean blocking, AsyncTask taskScope) {
            switch (method) {
                case "moveto" -> {
                    if (args.size() >= 3) {
                        double x = ((Number) args.get(0)).doubleValue();
                        double y = ((Number) args.get(1)).doubleValue();
                        double z = ((Number) args.get(2)).doubleValue();
                        double speed = args.size() >= 4 ? ((Number) args.get(3)).doubleValue() : 1.0;
                        npc.moveTo(x, y, z, speed);
                    }
                }
                case "alwaysmoveto", "always_move_to" -> {
                    double speed = args.size() >= 2 && args.get(args.size()-1) instanceof Number n2 ? n2.doubleValue() : 1.0;
                    if (args.size() >= 3 && args.get(0) instanceof Number) {
                        double x = ((Number) args.get(0)).doubleValue();
                        double y = ((Number) args.get(1)).doubleValue();
                        double z = ((Number) args.get(2)).doubleValue();
                        if (args.size() >= 4) speed = ((Number) args.get(3)).doubleValue();
                        npc.alwaysMoveTo(x, y, z, speed);
                    } else if (!args.isEmpty()) {
                        net.minecraft.world.entity.Entity target = resolveEntity(args.get(0));
                        if (target != null) npc.alwaysMoveToEntity(target, speed);
                    }
                }
                case "stopmove" -> npc.stopMove();
                case "followroute", "follow_route", "walkroute", "walk_route" ->
                        startFollowRoute(npc, args, false, false, false, taskScope);
                case "alwaysfollowroute", "always_follow_route", "alwayswalkroute", "always_walk_route" ->
                        startFollowRoute(npc, args, true, false, false, taskScope);
                case "flyroute", "fly_route" ->
                        startFollowRoute(npc, args, false, true, false, taskScope);
                case "alwaysflyroute", "always_fly_route" ->
                        startFollowRoute(npc, args, true, true, false, taskScope);
                case "playonce" -> { if (!args.isEmpty()) npc.playOnce(String.valueOf(args.get(0)), args.size() < 2 || isAdditiveAnimationArg(args.get(1))); }
                case "playloop" -> { if (!args.isEmpty()) npc.playLoop(String.valueOf(args.get(0)), args.size() < 2 || isAdditiveAnimationArg(args.get(1))); }
                case "playfreeze" -> { if (!args.isEmpty()) npc.playFreeze(String.valueOf(args.get(0)), args.size() < 2 || isAdditiveAnimationArg(args.get(1))); }
                case "stopoverlay" -> npc.stopOverlayAnimation();
                case "stop" -> { if (!args.isEmpty()) npc.stopOverlayAnimation(String.valueOf(args.get(0))); }
                case "setitem" -> {
                    if (args.size() >= 2) {
                        String hand = String.valueOf(args.get(0));
                        String itemId = String.valueOf(args.get(1));
                        String nbt = args.size() >= 3 ? String.valueOf(args.get(2)) : null;
                        npc.setHandItem(hand, resolveItemStack(itemId, nbt));
                    }
                }
                case "removeitem" -> {
                    if (!args.isEmpty()) npc.clearHandItem(String.valueOf(args.get(0)));
                    else { npc.clearHandItem("right"); npc.clearHandItem("left"); }
                }
                case "setflying" -> {
                    if (!args.isEmpty()) npc.setFlying(args.get(0) instanceof Boolean b ? b : Boolean.parseBoolean(String.valueOf(args.get(0))));
                }
                case "teleport" -> {
                    if (args.size() >= 3) {
                        double tx = ((Number) args.get(0)).doubleValue();
                        double ty = ((Number) args.get(1)).doubleValue();
                        double tz = ((Number) args.get(2)).doubleValue();
                        npc.teleportTo(tx, ty, tz);
                    }
                }
                case "remove" -> npc.remove(net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
                case "setidleanim" -> { if (!args.isEmpty()) npc.setIdleAnim(String.valueOf(args.get(0))); else npc.setIdleAnim(""); }
                case "setwalkanim" -> { if (!args.isEmpty()) npc.setWalkAnim(String.valueOf(args.get(0))); else npc.setWalkAnim(""); }
                case "lookat" -> {
                    if (!args.isEmpty()) {
                        net.minecraft.world.entity.Entity target = resolveEntity(args.get(0));
                        if (target != null) npc.lookAtEntity(target);
                    }
                }
                case "stoplookat" -> npc.stopLook();
                case "attack", "attacktarget", "attack_target" -> {
                    if (!args.isEmpty()) {
                        net.minecraft.world.entity.Entity target = resolveEntity(args.get(0));
                        if (target != null) {
                            double speed = args.size() >= 2 && args.get(1) instanceof Number n ? n.doubleValue() : 1.0;
                            if (args.size() >= 3 && args.get(2) instanceof Number r) {
                                npc.attackEntity(target, speed, r.doubleValue());
                            } else {
                                npc.attackEntity(target, speed);
                            }
                        }
                    }
                }
                case "stopattack", "stop_attack" -> npc.stopAttack();
                case "setattackanims", "set_attack_anims" -> {
                    if (args.isEmpty()) npc.setAttackAnims("attack");
                    else if (args.get(0) instanceof java.util.List<?> list) npc.setAttackAnims(list);
                    else {
                        String[] arr = new String[args.size()];
                        for (int i = 0; i < args.size(); i++) arr[i] = String.valueOf(args.get(i));
                        npc.setAttackAnims(arr);
                    }
                }
                case "setattackhand", "set_attack_hand" -> {
                    if (!args.isEmpty()) npc.setAttackHand(String.valueOf(args.get(0)));
                }
            }
        }

        private boolean isAdditiveAnimationArg(Object arg) {
            if (arg instanceof Boolean b) return b;
            if (arg == null) return false;
            String s = String.valueOf(arg).trim();
            if ("false".equalsIgnoreCase(s) || "replace".equalsIgnoreCase(s) || "set".equalsIgnoreCase(s) || "noadd".equalsIgnoreCase(s)) return false;
            return "add".equalsIgnoreCase(s) || "additive".equalsIgnoreCase(s) || "true".equalsIgnoreCase(s);
        }

        private PlayerFilter buildPlayerFilter(Object arg) {
            return PlayerFilter.from(arg, this::resolveServerPlayer, this::resolveEntity);
        }

        private void applyJoinWaitPlayer(UUID playerUuid) {
            if (playerUuid == null || source.getLevel() == null) return;
            net.minecraft.server.level.ServerPlayer joined = source.getLevel().getServer().getPlayerList().getPlayer(playerUuid);
            if (joined != null) variables.put("_eventPlayer", joined);
        }

        private void applyLeaveWaitPlayer(net.minecraft.server.level.ServerPlayer player) {
            if (player != null) variables.put("_eventPlayer", player);
        }

        /** Live read of entity.transform.* keys (position/rotation/scale). */
        private Object readEntityTransformProp(net.minecraft.world.entity.Entity entity, String key) {
            return switch (key) {
                case "x" -> entity.getX();
                case "y" -> entity.getY();
                case "z" -> entity.getZ();
                case "yaw" -> entity.getYRot();
                case "pitch" -> entity.getXRot();
                case "rotationX" -> GeoEntityScriptUtil.getModelRotationX(entity);
                case "rotationY" -> GeoEntityScriptUtil.getModelRotationY(entity);
                case "rotationZ" -> GeoEntityScriptUtil.getModelRotationZ(entity);
                case "scale" -> (GeoEntityScriptUtil.getModelScaleX(entity)
                        + GeoEntityScriptUtil.getModelScaleY(entity)
                        + GeoEntityScriptUtil.getModelScaleZ(entity)) / 3f;
                case "scaleX" -> GeoEntityScriptUtil.getModelScaleX(entity);
                case "scaleY" -> GeoEntityScriptUtil.getModelScaleY(entity);
                case "scaleZ" -> GeoEntityScriptUtil.getModelScaleZ(entity);
                default -> null;
            };
        }

        /** Live write of entity.transform.* keys. @return false for unknown keys/values. */
        private boolean writeEntityTransformProp(net.minecraft.world.entity.Entity entity, String key, Object value) {
            Number n;
            if (value instanceof Number num) {
                n = num;
            } else {
                try {
                    n = Double.parseDouble(String.valueOf(value));
                } catch (Exception e) {
                    return false;
                }
            }
            float f = n.floatValue();
            switch (key) {
                case "x" -> entity.teleportTo(n.doubleValue(), entity.getY(), entity.getZ());
                case "y" -> entity.teleportTo(entity.getX(), n.doubleValue(), entity.getZ());
                case "z" -> entity.teleportTo(entity.getX(), entity.getY(), n.doubleValue());
                case "yaw" -> entity.setYRot(f);
                case "pitch" -> entity.setXRot(f);
                case "rotationX" -> GeoEntityScriptUtil.setModelRotationX(entity, f);
                case "rotationY" -> GeoEntityScriptUtil.setModelRotationY(entity, f);
                case "rotationZ" -> GeoEntityScriptUtil.setModelRotationZ(entity, f);
                case "scale" -> GeoEntityScriptUtil.setModelScale(entity, f, f, f);
                case "scaleX" -> GeoEntityScriptUtil.setModelScale(entity, f,
                        GeoEntityScriptUtil.getModelScaleY(entity), GeoEntityScriptUtil.getModelScaleZ(entity));
                case "scaleY" -> GeoEntityScriptUtil.setModelScale(entity,
                        GeoEntityScriptUtil.getModelScaleX(entity), f, GeoEntityScriptUtil.getModelScaleZ(entity));
                case "scaleZ" -> GeoEntityScriptUtil.setModelScale(entity,
                        GeoEntityScriptUtil.getModelScaleX(entity), GeoEntityScriptUtil.getModelScaleY(entity), f);
                default -> {
                    return false;
                }
            }
            return true;
        }

        private PlayerFilter buildPlayerFilterFromPlayer(net.minecraft.server.level.ServerPlayer sp) {
            return sp != null ? PlayerFilter.of(sp.getUUID()) : null;
        }

        private boolean matchesEventPlayerArg(Object arg, UUID playerUuid) {
            return PlayerFilter.matchesEventArg(arg, playerUuid, this::resolveServerPlayer, this::resolveEntity);
        }

        private net.minecraft.server.level.ServerPlayer findOnlinePlayerForFilter(PlayerFilter filter) {
            if (filter == null || source.getLevel() == null) return null;
            for (net.minecraft.server.level.ServerPlayer sp : source.getLevel().getServer().getPlayerList().getPlayers()) {
                if (filter.matches(sp.getUUID())) return sp;
            }
            return null;
        }

        private net.minecraft.server.level.ServerPlayer findOnlinePlayerAtPosition(PlayerFilter filter, double x, double y, double z, double radius) {
            if (filter == null || source.getLevel() == null) return null;
            double r2 = radius * radius;
            for (net.minecraft.server.level.ServerPlayer sp : source.getLevel().getServer().getPlayerList().getPlayers()) {
                if (!filter.matches(sp.getUUID())) continue;
                if (sp.distanceToSqr(x, y, z) <= r2) return sp;
            }
            return null;
        }

        private net.minecraft.server.level.ServerPlayer resolveServerPlayer(Object arg) {
            if (arg instanceof net.minecraft.server.level.ServerPlayer sp) return sp;
            if (source.getLevel() == null || source.getLevel().isClientSide) return null;
            if (arg instanceof String name) {
                return source.getLevel().getServer().getPlayerList().getPlayerByName(name);
            }
            net.minecraft.world.entity.Entity e = resolveEntity(arg);
            if (e instanceof net.minecraft.server.level.ServerPlayer sp) return sp;
            if (e instanceof net.minecraft.world.entity.player.Player p) {
                return source.getLevel().getServer().getPlayerList().getPlayer(p.getUUID());
            }
            return null;
        }

        private net.minecraft.world.entity.Entity resolveEntity(Object arg) {
            if (arg instanceof net.minecraft.world.entity.Entity e) return e;
            if (arg instanceof String idOrKeyword) {
                Object varVal = getVariable(idOrKeyword);
                if (varVal instanceof net.minecraft.world.entity.Entity ve) return ve;
                if ("player".equalsIgnoreCase(idOrKeyword)) {
                    if (source != null && source.getLevel() != null) {
                        net.minecraft.world.entity.Entity origin = source.getEntity();
                        if (origin != null) return source.getLevel().getNearestPlayer(origin, 64.0);
                        net.minecraft.world.phys.Vec3 pos = source.getPosition();
                        return source.getLevel().getNearestPlayer(pos.x, pos.y, pos.z, 64.0, false);
                    }
                    return null;
                }
                if ("npc".equalsIgnoreCase(idOrKeyword)) {
                    return findNearestEntity(e -> e instanceof org.zonarstudio.spraute_engine.entity.SprauteNpcEntity);
                }
                if ("mob".equalsIgnoreCase(idOrKeyword)) {
                    return findNearestEntity(e ->
                            e instanceof net.minecraft.world.entity.LivingEntity
                                    && !(e instanceof net.minecraft.world.entity.player.Player)
                                    && !(e instanceof org.zonarstudio.spraute_engine.entity.SprauteNpcEntity));
                }
                if ("any".equalsIgnoreCase(idOrKeyword)) {
                    return findNearestEntity(e -> e instanceof net.minecraft.world.entity.LivingEntity);
                }
                UUID uuid = org.zonarstudio.spraute_engine.entity.NpcManager.get(idOrKeyword);
                if (uuid != null && source.getLevel() != null) {
                    net.minecraft.world.entity.Entity e = source.getLevel().getEntity(uuid);
                    if (e != null) return e;
                }
                UUID billboardUuid = org.zonarstudio.spraute_engine.entity.BillboardManager.get(idOrKeyword);
                if (billboardUuid != null && source.getLevel() != null) {
                    net.minecraft.world.entity.Entity e = source.getLevel().getEntity(billboardUuid);
                    if (e != null) return e;
                }
                if (source.getLevel() != null) {
                    net.minecraft.server.level.ServerPlayer byName = source.getLevel().getServer().getPlayerList().getPlayerByName(idOrKeyword);
                    if (byName != null) return byName;
                }
            }
            return null;
        }

        /**
         * Resolves coordinate arguments into [x, y, z].
         */
        private double[] resolveCoords(List<Object> args) {
            if (args.size() >= 3 && args.get(0) instanceof Number) {
                return new double[]{
                    ((Number) args.get(0)).doubleValue(),
                    ((Number) args.get(1)).doubleValue(),
                    ((Number) args.get(2)).doubleValue()
                };
            }
            if (args.size() == 1 && args.get(0) instanceof net.minecraft.world.entity.Entity entity) {
                return new double[]{entity.getX(), entity.getY() + 1.6, entity.getZ()};
            }
            return null;
        }

        /**
         * Resolve an entity for script waits across loaded levels.
         * {@code null} means unloaded/missing — callers must keep waiting, not treat as success.
         */
        private net.minecraft.world.entity.Entity findWaitEntity(UUID uuid) {
            if (uuid == null || source == null || source.getLevel() == null) return null;
            net.minecraft.world.entity.Entity local = source.getLevel().getEntity(uuid);
            if (local != null) return local;
            if (source.getServer() != null) {
                for (net.minecraft.server.level.ServerLevel level : source.getServer().getAllLevels()) {
                    net.minecraft.world.entity.Entity e = level.getEntity(uuid);
                    if (e != null) return e;
                }
            }
            return null;
        }

        private void bindWaitNpc(org.zonarstudio.spraute_engine.entity.SprauteNpcEntity npc, AsyncTask taskScope) {
            java.util.UUID uuid = npc.getUUID();
            String track = org.zonarstudio.spraute_engine.entity.NpcManager.findId(uuid);
            if (taskScope != null) {
                taskScope.waitEntityUuid = uuid;
                taskScope.waitNpcTrackId = track;
            } else {
                waitEntityUuid = uuid;
                waitNpcTrackId = track;
            }
        }

        private net.minecraft.world.entity.Entity resolveWaitNpc(UUID uuid, String trackId) {
            net.minecraft.world.entity.Entity entity = findWaitEntity(uuid);
            if (entity != null && entity.isAlive() && !entity.isRemoved()) return entity;
            if (trackId == null || source == null) return null;
            if (source.getLevel() != null) {
                entity = org.zonarstudio.spraute_engine.entity.NpcManager.getEntity(trackId, source.getLevel());
                if (entity != null && entity.isAlive() && !entity.isRemoved()) return entity;
            }
            if (source.getServer() != null) {
                entity = org.zonarstudio.spraute_engine.entity.NpcManager.getEntity(trackId, source.getServer());
                if (entity != null && entity.isAlive() && !entity.isRemoved()) return entity;
            }
            return null;
        }

        private boolean npcWaitShouldHold(UUID uuid, String trackId) {
            if (resolveWaitNpc(uuid, trackId) != null) return false;
            if (trackId != null) {
                if (org.zonarstudio.spraute_engine.entity.NpcManager.get(trackId) != null) return true;
                for (String sceneId : org.zonarstudio.spraute_engine.scene.SceneRuntime.listLoaded()) {
                    var state = org.zonarstudio.spraute_engine.scene.SceneRuntime.getActiveState(sceneId);
                    if (state == null) continue;
                    var node = state.document.findNode(trackId);
                    if (node != null && node.active
                            && node.type == org.zonarstudio.spraute_engine.scene.SceneNodeType.NPC) {
                        return true;
                    }
                }
            }
            return findWaitEntity(uuid) == null && trackId != null;
        }

        /**
         * True when the mob is close enough to the scripted move_to point.
         * Same distance rule on 1.19.2 and 1.20.1 — never treat {@code Navigation#isDone()} as arrived
         * (incomplete/null paths also report done).
         */
        private static boolean isCloseToMoveTarget(net.minecraft.world.entity.Mob mob, double tx, double ty, double tz) {
            boolean close;
            if (mob instanceof org.zonarstudio.spraute_engine.entity.SprauteNpcEntity snpc) {
                if (snpc.getScriptedMoveGoal() != null) {
                    close = snpc.hasReachedMoveGoal();
                } else {
                    // Entity clears the goal on arrival so one-shot moveTo won't resume after a push.
                    close = snpc.isCloseToScriptedCoords(tx, ty, tz);
                }
            } else {
                close = org.zonarstudio.spraute_engine.entity.SprauteNpcEntity.isCloseToPoint(mob, tx, ty, tz);
            }
            if (close) {
                LOGGER.info("[NPC-WALK] ARRIVED {} at ({},{},{}) waitTarget=({},{},{})",
                        mob.getCustomName() != null ? mob.getCustomName().getString() : mob.getUUID(),
                        mob.getX(), mob.getY(), mob.getZ(), tx, ty, tz);
            }
            return close;
        }

        /** Async task state. Shares variables with parent. */
        private class AsyncTask {
            private static final class SavedCallFrame {
                final List<CompiledScript.Instruction> instructions;
                final int returnIp;
                final String returnVar;
                final String returnScope;
                final Map<String, Object> savedParamValues;
                final List<String> paramNames;

                SavedCallFrame(List<CompiledScript.Instruction> instructions, int returnIp,
                                 String returnVar, String returnScope,
                                 Map<String, Object> savedParamValues, List<String> paramNames) {
                    this.instructions = instructions;
                    this.returnIp = returnIp;
                    this.returnVar = returnVar;
                    this.returnScope = returnScope;
                    this.savedParamValues = savedParamValues;
                    this.paramNames = paramNames;
                }
            }

            final String id;
            final List<CompiledScript.Instruction> rootInstructions;
            List<CompiledScript.Instruction> instructions;
            int ip;
            boolean finished;
            boolean cancelled;
            boolean skipIpIncrement;
            final Deque<SavedCallFrame> callFrames = new ArrayDeque<>();
            WaitType waitType = WaitType.NONE;
            double waitTimer;
            UUID waitEntityUuid;
            String waitNpcTrackId;
            UUID waitFollowTargetUuid;
            double waitFollowStopDistance = 2.0;
            double waitMoveTargetX;
            double waitMoveTargetY;
            double waitMoveTargetZ;
            double waitMoveSpeed = 1.0;
            /** {@link WaitType#UI_CLICK} ╤В╨Р╨д same semantics as main script await ui_click */
            PlayerFilter waitUiPlayers;
            String pendingUiClickVarName;
            boolean uiClickMet;
            String uiClickWidgetId = "";
            boolean uiClickClosed;
            int uiClickMouseButton = 0;

            PlayerFilter waitPositionPlayers;
            double waitPositionX, waitPositionY, waitPositionZ, waitPositionRadius;
            PlayerFilter waitInventoryPlayers;
            String waitInventoryItemId;
            int waitInventoryCount;
            PlayerFilter waitBlockPlayers;
            String waitBlockId;
            net.minecraft.core.BlockPos waitBlockPos;
            String waitBlockDim;
            boolean blockEventMet;
            String uiInputWidgetId = "";
            String uiInputText = "";

            PlayerFilter waitChatPlayers = null;
            List<String> waitChatMessages = null;
            boolean waitChatIgnoreCase = true;
            boolean waitChatIgnorePunct = true;
            boolean chatEventMet = false;
            String chatMatchedMessage = "";
            
            PlayerFilter waitUiOverlapPlayers = null;
            String waitUiOverlapId1 = "";
            String waitUiOverlapId2 = "";
            boolean uiOverlapMet = false;
            
            PlayerFilter waitOrbPickupPlayers = null;
            String waitOrbPickupTexture = null;
            int waitOrbPickupTargetCount = 0;
            int waitOrbPickupCurrentCount = 0;

            String waitProjectileHitId = null;
            String waitProjectileHitType = null;

            PlayerFilter waitTradePlayers = null;
            String waitTradeItemId = null;

            PlayerFilter waitRelGiftPlayers = null;
            String waitRelGiftNpcId = null;
            String waitRelGiftItemId = null;

            PlayerFilter waitRelSimplePlayers = null;
            String waitRelSimpleNpcId = null;
            String waitRelSimpleBtnId = null;

            PlayerFilter waitPlayerActionPlayers = null;
            String waitPlayerActionType = "";
            String waitPlayerActionTarget = null;
            boolean playerActionMet = false;

            PlayerFilter waitDimensionPlayers = null;
            String waitDimensionId = null;
            boolean dimensionEventMet = false;

            PlayerFilter waitJoinPlayers = null;
            boolean waitFirstJoinOnly = false;
            boolean joinEventMet = false;
            UUID waitJoinPlayerUuid = null;

            PlayerFilter waitLeavePlayers = null;
            boolean leaveEventMet = false;
            net.minecraft.server.level.ServerPlayer waitLeavePlayer = null;

            PlayerFilter waitHitboxPlayers = null;
            String waitHitboxScene = null;
            String waitHitboxTriggerId = null;
            boolean hitboxEventMet = false;

            String waitCustomEventName = null;
            java.util.List<Object> waitCustomFilters = null;

            boolean interactionMet = false;
            Object waitResult = null;
            String waitKeybindKey = null;
            boolean keybindMet = false;
            net.minecraft.world.entity.player.Player keybindPlayer = null;
            Object waitDeathTarget = null;
            Object waitKillKillerTarget = null;
            Object waitKillVictimTarget = null;
            boolean deathMet = false;
            boolean killMet = false;
            net.minecraft.world.entity.LivingEntity deadEntity = null;
            net.minecraft.world.entity.Entity deathKiller = null;
            String waitPickupNpcId = null;
            UUID waitPickupNpcUuid = null;
            String waitPickupItemId = null;
            String waitPickupTag = null;
            int waitPickupBaseCount = 0;
            int waitPickupMaxCount = -1;
            String waitTaskId = null;

            final Map<String, Object> taskLocals = new HashMap<>();

            AsyncTask(String id, List<CompiledScript.Instruction> instructions) {
                this.id = id;
                this.rootInstructions = instructions;
                this.instructions = instructions;
                this.taskLocals.putAll(variables);
                if (currentTaskScope != null) {
                    this.taskLocals.putAll(currentTaskScope.taskLocals);
                }
            }

            void enterUserFunction(UserFunction func, List<Object> args, String returnVar, String returnScope,
                                   Map<String, Object> scriptVars) {
                Map<String, Object> saved = new HashMap<>();
                for (String param : func.params) {
                    if (scriptVars.containsKey(param)) {
                        saved.put(param, scriptVars.get(param));
                    }
                }
                callFrames.push(new SavedCallFrame(instructions, ip + 1, returnVar, returnScope, saved, func.params));
                for (int i = 0; i < func.params.size(); i++) {
                    scriptVars.put(func.params.get(i), i < args.size() ? args.get(i) : null);
                }
                instructions = func.bodyInstructions;
                ip = 0;
                skipIpIncrement = true;
            }

            boolean leaveUserFunction(Object returnValue, Map<String, Object> scriptVars) {
                if (callFrames.isEmpty()) return false;
                SavedCallFrame frame = callFrames.pop();
                for (String param : frame.paramNames) {
                    if (frame.savedParamValues.containsKey(param)) {
                        scriptVars.put(param, frame.savedParamValues.get(param));
                    } else {
                        scriptVars.remove(param);
                    }
                }
                if (frame.returnVar != null) {
                    ActiveScript.this.putVariable(frame.returnVar, returnValue, frame.returnScope);
                }
                instructions = frame.instructions;
                ip = frame.returnIp;
                skipIpIncrement = true;
                return true;
            }
        }
    }

        private enum WaitType {
        NONE, TIME, INTERACT, NEXT, KEYBIND, DEATH, KILL, UI_CLICK, UI_CLOSE, UI_DROP, MOVE_TO, FOLLOW_ROUTE, FOLLOW, PICKUP, ORB_PICKUP, PROJECTILE_HIT,
        TRADE_BUY, TRADE_SELL, REL_GIFT, REL_REP_CHANGE, REL_TALK, REL_BUTTON, WAIT_TASK,
        POSITION, INVENTORY, CLICK_BLOCK, BREAK_BLOCK, PLACE_BLOCK, OPEN_CHEST, OPEN_DOOR, USE_BLOCK, UI_INPUT, CHAT, UI_OVERLAP,
        PLAYER_ACTION, DIMENSION, JOIN, LEAVE, HITBOX_TOUCH, HITBOX_EXIT, CUSTOM_EVENT
    }

    public static class PlayerSavedDataMap extends java.util.AbstractMap<String, Object> {
        private final UUID playerUuid;
        private final net.minecraft.server.MinecraftServer server;
        private final net.minecraft.server.level.ServerLevel level;
        private final String prefix;

        public PlayerSavedDataMap(UUID playerUuid, net.minecraft.server.MinecraftServer server, net.minecraft.server.level.ServerLevel level) {
            this.playerUuid = playerUuid;
            this.server = server;
            this.level = level;
            this.prefix = "p_" + playerUuid.toString() + "_";
        }

        private ScriptWorldData getData() {
            return ScriptWorldData.get(level);
        }

        @Override
        public Object get(Object key) {
            return getData().get(prefix + key, server, level);
        }

        @Override
        public Object put(String key, Object value) {
            Object old = get(key);
            getData().put(prefix + key, value);
            return old;
        }

        @Override
        public java.util.Set<Entry<String, Object>> entrySet() {
            return getData().playerSavedSnapshot(playerUuid, server, level).entrySet();
        }

        @Override
        public Object remove(Object key) {
            if (key == null) return null;
            Object old = get(key);
            getData().remove(prefix + key);
            return old;
        }
        
        @Override
        public boolean containsKey(Object key) {
            return getData().has(prefix + key);
        }
    }
}
