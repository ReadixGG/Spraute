package org.zonarstudio.spraute_engine.script;

import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.zonarstudio.spraute_engine.compat.SprauteEntityCompat;
import org.zonarstudio.spraute_engine.script.function.ScriptFunction;

import java.util.*;

/**
 * Executor that runs CompiledScripts. Supports async execution via ticks.
 */
public class ScriptExecutor {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Legacy snake_case keys в†’ camelCase. */
    private static String normPropKey(String key) {
        if (key == null) return "";
        return switch (key) {
            case "show_name" -> "showName";
            case "idle_anim" -> "idleAnim";
            case "walk_anim" -> "walkAnim";
            case "fly_idle_anim" -> "flyIdleAnim";
            case "fly_walk_anim" -> "flyWalkAnim";
            case "swim_idle_anim" -> "swimIdleAnim";
            case "swim_walk_anim" -> "swimWalkAnim";
            case "max_hp" -> "maxHp";
            case "drop_item" -> "dropItem";
            case "drop_min" -> "dropMin";
            case "drop_max" -> "dropMax";
            case "drop_chance" -> "dropChance";
            case "look_x" -> "lookX";
            case "look_y" -> "lookY";
            case "look_z" -> "lookZ";
            case "held_item_nbt" -> "heldItemNbt";
            case "smooth_time" -> "smoothTime";
            case "look_at" -> "lookAt";
            case "can_close" -> "canClose";
            case "content_h" -> "contentH";
            case "slice_borders" -> "sliceBorders";
            case "slice_scale" -> "sliceScale";
            case "trigger_fade_out" -> "triggerFadeOut";
            case "visible_time" -> "visibleTime";
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
        String next = org.zonarstudio.spraute_engine.config.ScriptTriggersConfig.get().after.get(finishedScriptName);
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
        for (ActiveScript script : activeScripts) {
            script.onUiAction(player, widgetId, closed);
        }
        for (ActiveScript script : scriptsToAdd) {
            script.onUiAction(player, widgetId, closed);
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

    /** Р Р°Р·СЂРµС€РёС‚СЊ ServerLevel РїРѕ СЃС‚СЂРѕРєРµ РІРёРґР° "overworld" / "minecraft:the_nether" / "the_end". */
    public static net.minecraft.server.level.ServerLevel resolveLevel(net.minecraft.server.MinecraftServer server, String dimId) {
        if (server == null || dimId == null || dimId.isBlank()) return null;
        String full = dimId.contains(":") ? dimId : "minecraft:" + dimId;
        //? if >=1.20.1 {
        net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> key =
            net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                new net.minecraft.resources.ResourceLocation(full));
        //?} else {
        /*net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> key =
            net.minecraft.resources.ResourceKey.create(net.minecraft.core.Registry.DIMENSION_REGISTRY,
                new net.minecraft.resources.ResourceLocation(full));
        *///?}
        return server.getLevel(key);
    }

    public void onClickBlock(net.minecraft.world.entity.player.Player player, net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.Block block, boolean isLeft) {
        onClickBlock(player, pos, block, isLeft, null);
    }
    public void onClickBlock(net.minecraft.world.entity.player.Player player, net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.Block block, boolean isLeft, net.minecraft.world.level.Level level) {
        String dimId = level != null ? level.dimension().location().toString() : null;
        for (ActiveScript script : activeScripts) {
            script.onClickBlock(player, pos, block, isLeft, dimId);
        }
        for (ActiveScript script : scriptsToAdd) {
            script.onClickBlock(player, pos, block, isLeft, dimId);
        }
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

    public void onTradeBuy(net.minecraft.server.level.ServerPlayer player, String itemId, int price) {
        for (ActiveScript script : activeScripts) {
            script.onTradeBuy(player, itemId, price);
        }
        for (ActiveScript script : scriptsToAdd) {
            script.onTradeBuy(player, itemId, price);
        }
    }

    public void onTradeSell(net.minecraft.server.level.ServerPlayer player, String itemId, int price) {
        for (ActiveScript script : activeScripts) {
            script.onTradeSell(player, itemId, price);
        }
        for (ActiveScript script : scriptsToAdd) {
            script.onTradeSell(player, itemId, price);
        }
    }

    public void onPlayerAction(net.minecraft.world.entity.player.Player player, String actionType, Object target) {
        for (ActiveScript script : activeScripts) {
            script.onPlayerAction(player, actionType, target);
        }
        for (ActiveScript script : scriptsToAdd) {
            script.onPlayerAction(player, actionType, target);
        }
    }

    /** Stop all running scripts and clear state. */
    public void stopAll() {
        for (ActiveScript script : activeScripts) {
            script.cleanup();
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
                script.cleanup();
                it.remove();
                return true;
            }
        }
        return false;
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
        ScriptManager.getInstance().run(includeName, source);
        bootstrapImportedScript(includeName);
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
    private class ActiveScript {
        private final CompiledScript script;
        private final CommandSourceStack source;
        private final Map<String, Object> variables = new HashMap<>();
        private final ScriptContext context = new ScriptContext();
        private int ip = 0;
        private boolean finished = false;
        
        // Wait state
        private WaitType waitType = WaitType.NONE;
        private double waitTimer = 0;
        private UUID waitEntityUuid = null;
        private boolean interactionMet = false;
        private Object asyncResult = null;
        private String pendingVarName = null;
        private String waitKeybindKey = null;
        private boolean keybindMet = false;
        private net.minecraft.world.entity.player.Player keybindPlayer = null;
        private String waitDeathTarget = null;
        private String waitKillKillerTarget = null;
        private String waitKillVictimTarget = null;
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
        
        private UUID waitOrbPickupPlayerUuid = null;
        private String waitOrbPickupTexture = null;
        private int waitOrbPickupTargetCount = 0;
        private int waitOrbPickupCurrentCount = 0;

        private UUID waitTradePlayerUuid = null;
        private String waitTradeItemId = null;

        /** Target for MOVE_TO wait вЂ” completion is distance-based; navigation alone is unreliable (path null = isDone). */
        private double waitMoveTargetX;
        private double waitMoveTargetY;
        private double waitMoveTargetZ;
        private double waitMoveSpeed = 1.0;

        // New waits: position, inventory, clickBlock, breakBlock, placeBlock, uiInput, chat
        private UUID waitPositionPlayerUuid = null;
        private double waitPositionX, waitPositionY, waitPositionZ, waitPositionRadius;
        private UUID waitInventoryPlayerUuid = null;
        private String waitInventoryItemId = null;
        private int waitInventoryCount = 0;
        private UUID waitBlockPlayerUuid = null;
        private String waitBlockId = null;
        private net.minecraft.core.BlockPos waitBlockPos = null;
        private String waitBlockDim = null;
        private boolean blockEventMet = false;
        private String uiInputWidgetId = "";
        private String uiInputText = "";

        // UI overlap
        private UUID waitUiOverlapPlayerUuid = null;
        private String waitUiOverlapId1 = "";
        private String waitUiOverlapId2 = "";
        private boolean uiOverlapMet = false;
        
        private UUID waitChatPlayerUuid = null;
        private List<String> waitChatMessages = null;
        private boolean waitChatIgnoreCase = true;
        private boolean waitChatIgnorePunct = true;
        private boolean chatEventMet = false;
        private String chatMatchedMessage = "";
        
        // Player Action
        private UUID waitPlayerActionPlayerUuid = null;
        private String waitPlayerActionType = "";
        private String waitPlayerActionTarget = null;
        private boolean playerActionMet = false;

        private UUID waitDimensionPlayerUuid = null;
        private String waitDimensionId = null;
        private boolean dimensionEventMet = false;

        // User-defined functions
        private final Map<String, UserFunction> userFunctions = new HashMap<>();
        // Names of scripts imported via `import` вЂ” functions are resolved lazily from their running ActiveScript
        private final List<String> importedScripts = new ArrayList<>();

        private AsyncTask currentTaskScope = null;

        // Background handlers
        private final Map<String, EventHandler> eventHandlers = new HashMap<>();
        private final Map<String, TimerHandler> timerHandlers = new HashMap<>();
        /** Tracks if pickup handler saw the item last tick (to fire only on transition to "has item"). */
        private final Map<String, Boolean> pickupHandlerHadItem = new HashMap<>();
        /** For SprauteNpcEntity: last count seen (fire when count increases). */
        private final Map<String, Integer> pickupHandlerLastCount = new HashMap<>();
        private final Map<String, Boolean> positionHandlerMet = new HashMap<>();
        private final Map<String, Boolean> inventoryHandlerMet = new HashMap<>();

        /** Async tasks: id -> task. Named tasks can be awaited or stopped. */
        private final Map<String, AsyncTask> asyncTasks = new HashMap<>();
        private final java.util.Stack<TryBlock> tryStack = new java.util.Stack<>();
        private String waitTaskId = null;
        /** {@link #onUiAction} */
        private UUID waitUiPlayerUuid = null;
        private boolean uiClickMet = false;
        private String uiClickWidgetId = "";
        private boolean uiClickClosed = false;

        /** create ui: template bound while UI is open (click handlers). */
        private org.zonarstudio.spraute_engine.ui.UiTemplate boundUiTemplate;
        private UUID boundUiPlayerUuid;

        public ActiveScript(CompiledScript script, CommandSourceStack source) {
            this(script, source, null);
        }

        public ActiveScript(CompiledScript script, CommandSourceStack source, Map<String, Object> initialVariables) {
            this.script = script;
            this.source = source;
            if (initialVariables != null) {
                variables.putAll(initialVariables);
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
        }

        /** Force stop script: deactivate handlers, timers, async tasks. */
        public void forceStop() {
            finished = true;
            eventHandlers.values().forEach(h -> h.active = false);
            timerHandlers.values().forEach(t -> t.active = false);
            asyncTasks.values().forEach(t -> t.cancelled = true);
        }

        /** Cleanup when script is stopped (e.g. /spraute stop). */
        public void cleanup() {
            clearNpcPickupMax(waitPickupNpcId);
            waitPickupNpcUuid = null;
            if (source.getServer() != null) {
                org.zonarstudio.spraute_engine.network.ModNetwork.CHANNEL.send(net.minecraftforge.network.PacketDistributor.ALL.noArg(), new org.zonarstudio.spraute_engine.network.CloseSprauteOverlayPacket());
            }
        }

        private String getWaitStatusString(WaitType wt, double timer, String keybind, String deathTarget, double mx, double my, double mz, UUID followTarget) {
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
                case MOVE_TO -> String.format("move_to (%.0f, %.0f, %.0f)", mx, my, mz);
                case FOLLOW -> "follow (" + followTarget + ")";
                case PICKUP -> "pickup";
                case ORB_PICKUP -> "orbPickup";
                case TRADE_BUY -> "tradeBuy";
                case TRADE_SELL -> "tradeSell";
                case WAIT_TASK -> "task";
                case POSITION -> "position";
                case INVENTORY -> "inventory";
                case CLICK_BLOCK -> "clickBlock";
                case BREAK_BLOCK -> "breakBlock";
                case PLACE_BLOCK -> "placeBlock";
                case OPEN_CHEST -> "openChest";
                case OPEN_DOOR -> "openDoor";
                case UI_INPUT -> "uiInput";
                case CHAT -> "chat";
                case UI_OVERLAP -> "uiOverlap";
                case PLAYER_ACTION -> "action";
                case DIMENSION -> "dimension";
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

            // Tick all active timers
            tickTimers();

            // Tick async tasks
            tickAsyncTasks();

            // Fire "pickup" event handlers (check each tick for item transition)
            // Must run before WAIT_TASK: await task(...) must not suppress pickup notifications.
            if (source.getLevel() != null) {
                for (var entry : eventHandlers.entrySet()) {
                    EventHandler handler = entry.getValue();
                    if (!handler.active || !handler.eventName.equals("pickup")) continue;
                    if (handler.eventArgs.size() < 2) continue;

                    Object npcArg = handler.eventArgs.get(0);
                    net.minecraft.world.entity.Entity entity = resolveEntity(npcArg);

                    String itemId = String.valueOf(handler.eventArgs.get(1));
                    String tag = handler.eventArgs.size() >= 3 ? String.valueOf(handler.eventArgs.get(2)) : null;
                    if ("null".equals(tag) || (tag != null && tag.isEmpty())) tag = null;

                    net.minecraft.world.entity.Entity entityObj = entity; // Reusing previous variable
                    if (entityObj == null && npcArg instanceof String) {
                        entity = org.zonarstudio.spraute_engine.entity.NpcManager.getEntity((String)npcArg, source.getLevel());
                    }
                    if (entity == null && entityObj != null) entity = entityObj;

                    boolean shouldFire = false;
                    if (entity instanceof net.minecraft.world.entity.Mob mob) {
                        if (entity instanceof org.zonarstudio.spraute_engine.entity.SprauteNpcEntity) {
                            int currentCount = countMatchingItems(mob, itemId, tag);
                            int lastCount = pickupHandlerLastCount.getOrDefault(entry.getKey(), 0);
                            if (currentCount > lastCount) {
                                shouldFire = true;
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
                        
                        variables.put("_eventNpc", entity);
                        variables.put("_eventItem", foundStack);
                        variables.put("_eventDropper", eventDropper);
                        variables.put("count", pickupHandlerLastCount.getOrDefault(entry.getKey(), 0));
                        try {
                            executeInstructionBlock(handler.bodyInstructions);
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
                    }
                }
            }

            // Fire position handlers
            if (source.getLevel() != null) {
                for (var entry : eventHandlers.entrySet()) {
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
                            executeInstructionBlock(handler.bodyInstructions);
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
                for (var entry : eventHandlers.entrySet()) {
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
                            executeInstructionBlock(handler.bodyInstructions);
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
                        waitUiPlayerUuid = null;
                        if (pendingVarName != null) {
                            variables.put(pendingVarName, uiClickWidgetId != null ? uiClickWidgetId : "");
                        }
                        variables.put("_ui_closed", uiClickClosed);
                        pendingVarName = null;
                        uiClickWidgetId = "";
                        uiClickClosed = false;
                    } else if (waitType == WaitType.UI_CLOSE && uiClickMet) {
                        uiClickMet = false;
                        uiClickWidgetId = "";
                        uiClickClosed = false;
                        return;
                    } else {
                        return;
                    }
                } else if (waitType == WaitType.UI_INPUT) {
                    if (uiClickMet) {
                        waitType = WaitType.NONE;
                        uiClickMet = false;
                        waitUiPlayerUuid = null;
                        if (pendingVarName != null) {
                            variables.put(pendingVarName, uiInputText != null ? uiInputText : "");
                        }
                        pendingVarName = null;
                        uiInputWidgetId = "";
                        uiInputText = "";
                    } else {
                        return;
                    }
                } else if (waitType == WaitType.TRADE_BUY || waitType == WaitType.TRADE_SELL) {
                    return;
                } else if (waitType == WaitType.CHAT) {
                    if (chatEventMet) {
                        waitType = WaitType.NONE;
                        chatEventMet = false;
                        waitChatPlayerUuid = null;
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
                    if (waitPositionPlayerUuid != null && source.getLevel() != null) {
                        net.minecraft.server.level.ServerPlayer sp = source.getLevel().getServer().getPlayerList().getPlayer(waitPositionPlayerUuid);
                        if (sp != null && sp.distanceToSqr(waitPositionX, waitPositionY, waitPositionZ) <= waitPositionRadius * waitPositionRadius) {
                            waitType = WaitType.NONE;
                            waitPositionPlayerUuid = null;
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
                    if (waitInventoryPlayerUuid != null && source.getLevel() != null) {
                        net.minecraft.server.level.ServerPlayer sp = source.getLevel().getServer().getPlayerList().getPlayer(waitInventoryPlayerUuid);
                        if (sp != null) {
                            if (playerMeetsInventoryRequirement(sp, waitInventoryItemId, waitInventoryCount)) {
                                applyInventoryEventVars(sp, waitInventoryItemId);
                                waitType = WaitType.NONE;
                                waitInventoryPlayerUuid = null;
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
                        || waitType == WaitType.OPEN_CHEST || waitType == WaitType.OPEN_DOOR) {
                    if (blockEventMet) {
                        waitType = WaitType.NONE;
                        blockEventMet = false;
                        waitBlockPlayerUuid = null;
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
                        waitPlayerActionPlayerUuid = null;
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
                        waitDimensionPlayerUuid = null;
                        waitDimensionId = null;
                    } else {
                        return;
                    }
            } else if (waitType == WaitType.MOVE_TO) {
                if (waitEntityUuid != null && source.getLevel() != null) {
                    net.minecraft.world.entity.Entity entity = source.getLevel().getEntity(waitEntityUuid);
                    if (entity == null || !entity.isAlive()) {
                        waitType = WaitType.NONE;
                        waitEntityUuid = null;
                    } else if (entity instanceof net.minecraft.world.entity.Mob mob) {
                        if (isCloseToMoveTarget(mob, waitMoveTargetX, waitMoveTargetY, waitMoveTargetZ)) {
                            waitType = WaitType.NONE;
                            waitEntityUuid = null;
                        } else {
                            var nav = mob.getNavigation();
                            if (nav.isDone() && nav.getPath() == null) {
                                // Р•СЃР»Рё РЅР°РІРёРіР°С†РёСЏ РґСѓРјР°РµС‚, С‡С‚Рѕ РґРѕС€Р»Р°, РЅРѕ isCloseToMoveTarget = false
                                // (Р·Р°СЃС‚СЂСЏР» РёР»Рё РЅРµ РґРѕС€РµР» РёРґРµР°Р»СЊРЅС‹Рµ РїРѕР»Р±Р»РѕРєР°) - РїРµСЂРµР·Р°РїСѓСЃРєР°РµРј РїСѓС‚СЊ
                                mob.getNavigation().moveTo(waitMoveTargetX, waitMoveTargetY, waitMoveTargetZ, waitMoveSpeed);
                            } else if (nav.isStuck()) {
                                // Р•СЃР»Рё Р·Р°СЃС‚СЂСЏР» - С‚РѕР¶Рµ РїС‹С‚Р°РµРјСЃСЏ РїРµСЂРµСЃС‚СЂРѕРёС‚СЊ
                                mob.getNavigation().moveTo(waitMoveTargetX, waitMoveTargetY, waitMoveTargetZ, waitMoveSpeed);
                            }
                            return;
                        }
                    } else {
                        waitType = WaitType.NONE;
                        waitEntityUuid = null;
                    }
                } else {
                    waitType = WaitType.NONE;
                    waitEntityUuid = null;
                }
            } else if (waitType == WaitType.FOLLOW) {
                if (waitEntityUuid != null && waitFollowTargetUuid != null && source.getLevel() != null) {
                    net.minecraft.world.entity.Entity npcEntity = source.getLevel().getEntity(waitEntityUuid);
                    net.minecraft.world.entity.Entity targetEntity = source.getLevel().getEntity(waitFollowTargetUuid);
                    if (npcEntity == null || targetEntity == null || !npcEntity.isAlive() || !targetEntity.isAlive()) {
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
                                mob.getNavigation().moveTo(targetEntity.getX(), targetEntity.getY(), targetEntity.getZ(), 1.0);
                            }
                            return;
                        }
                    }
                } else {
                    waitType = WaitType.NONE;
                    waitEntityUuid = null;
                    waitFollowTargetUuid = null;
                }
            } else if (waitType == WaitType.PICKUP) {
                if ((waitPickupNpcUuid != null || waitPickupNpcId != null) && source.getLevel() != null) {
                    net.minecraft.world.entity.Entity entity = waitPickupNpcUuid != null
                            ? source.getLevel().getEntity(waitPickupNpcUuid)
                            : null;
                    if (entity == null && waitPickupNpcId != null) {
                        entity = org.zonarstudio.spraute_engine.entity.NpcManager.getEntity(waitPickupNpcId, source.getLevel());
                    }
                    if (entity == null || !entity.isAlive()) {
                        clearNpcPickupMax(waitPickupNpcId);
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
                            clearNpcPickupMax(waitPickupNpcId);
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
                        if (source != null) {
                            source.sendFailure(Component.literal(
                                    "В§c[Spraute] Script '" + script.getName() + "' error at line " + instruction.getLine() + ": " + errMsg));
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

            finished = true;
            LOGGER.info("Script '{}' finished.", script.getName());
        }

        private void tickTimers() {
            for (var entry : timerHandlers.entrySet()) {
                TimerHandler timer = entry.getValue();
                if (!timer.active) continue;

                timer.countdown -= 0.05;
                if (timer.countdown <= 0) {
                    timer.countdown = timer.intervalSeconds;
                    try {
                        executeInstructionBlock(timer.bodyInstructions);
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
                        net.minecraft.world.entity.Entity entity = source.getLevel().getEntity(task.waitEntityUuid);
                        if (entity == null || !entity.isAlive()) {
                            task.waitType = WaitType.NONE;
                            task.waitEntityUuid = null;
                            task.ip++;
                        } else if (entity instanceof net.minecraft.world.entity.Mob mob) {
                            if (isCloseToMoveTarget(mob, task.waitMoveTargetX, task.waitMoveTargetY, task.waitMoveTargetZ)) {
                                task.waitType = WaitType.NONE;
                                task.waitEntityUuid = null;
                                task.ip++;
                            } else {
                                var nav = mob.getNavigation();
                                if (nav.isDone() && nav.getPath() == null) {
                                    mob.getNavigation().moveTo(task.waitMoveTargetX, task.waitMoveTargetY, task.waitMoveTargetZ, task.waitMoveSpeed);
                                } else if (nav.isStuck()) {
                                    mob.getNavigation().moveTo(task.waitMoveTargetX, task.waitMoveTargetY, task.waitMoveTargetZ, task.waitMoveSpeed);
                                }
                                continue;
                            }
                        } else {
                            task.waitType = WaitType.NONE;
                            task.waitEntityUuid = null;
                            task.ip++;
                        }
                    } else {
                        task.waitType = WaitType.NONE;
                        task.waitEntityUuid = null;
                        task.ip++;
                    }
                } else if (task.waitType == WaitType.FOLLOW) {
                    if (task.waitEntityUuid != null && task.waitFollowTargetUuid != null && source.getLevel() != null) {
                        net.minecraft.world.entity.Entity npcEntity = source.getLevel().getEntity(task.waitEntityUuid);
                        net.minecraft.world.entity.Entity targetEntity = source.getLevel().getEntity(task.waitFollowTargetUuid);
                        if (npcEntity == null || targetEntity == null || !npcEntity.isAlive() || !targetEntity.isAlive()) {
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
                                    mob.getNavigation().moveTo(targetEntity.getX(), targetEntity.getY(), targetEntity.getZ(), 1.0);
                                }
                                continue;
                            }
                        }
                    } else {
                        task.waitType = WaitType.NONE;
                        task.waitEntityUuid = null;
                        task.waitFollowTargetUuid = null;
                        task.ip++;
                    }
                } else if (task.waitType == WaitType.UI_CLICK || task.waitType == WaitType.UI_CLOSE) {
                    if (task.uiClickMet && (task.waitType == WaitType.UI_CLICK || task.uiClickClosed)) {
                        task.waitType = WaitType.NONE;
                        task.waitUiPlayerUuid = null;
                        if (task.pendingUiClickVarName != null) {
                            putVariable(task.pendingUiClickVarName, task.uiClickWidgetId != null ? task.uiClickWidgetId : "");
                        }
                        variables.put("_ui_closed", task.uiClickClosed);
                        task.pendingUiClickVarName = null;
                        task.uiClickMet = false;
                        task.uiClickWidgetId = "";
                        task.uiClickClosed = false;
                        task.ip++;
                    } else if (task.waitType == WaitType.UI_CLOSE && task.uiClickMet) {
                        task.uiClickMet = false;
                        task.uiClickWidgetId = "";
                        task.uiClickClosed = false;
                        continue;
                    } else {
                        continue;
                    }
                } else if (task.waitType == WaitType.UI_INPUT) {
                    if (task.uiClickMet) {
                        task.waitType = WaitType.NONE;
                        task.waitUiPlayerUuid = null;
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
                } else if (task.waitType == WaitType.TRADE_BUY || task.waitType == WaitType.TRADE_SELL) {
                    continue;
                } else if (task.waitType == WaitType.CHAT) {
                    if (task.chatEventMet) {
                        task.waitType = WaitType.NONE;
                        task.waitChatPlayerUuid = null;
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
                    if (task.waitPositionPlayerUuid != null && source.getLevel() != null) {
                        net.minecraft.server.level.ServerPlayer sp = source.getLevel().getServer().getPlayerList().getPlayer(task.waitPositionPlayerUuid);
                        if (sp != null && sp.distanceToSqr(task.waitPositionX, task.waitPositionY, task.waitPositionZ) <= task.waitPositionRadius * task.waitPositionRadius) {
                            task.waitType = WaitType.NONE;
                            task.waitPositionPlayerUuid = null;
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
                    if (task.waitInventoryPlayerUuid != null && source.getLevel() != null) {
                        net.minecraft.server.level.ServerPlayer sp = source.getLevel().getServer().getPlayerList().getPlayer(task.waitInventoryPlayerUuid);
                        if (sp != null) {
                            if (playerMeetsInventoryRequirement(sp, task.waitInventoryItemId, task.waitInventoryCount)) {
                                applyInventoryEventVars(sp, task.waitInventoryItemId);
                                task.waitType = WaitType.NONE;
                                task.waitInventoryPlayerUuid = null;
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
                        || task.waitType == WaitType.OPEN_CHEST || task.waitType == WaitType.OPEN_DOOR) {
                    if (task.blockEventMet) {
                        task.waitType = WaitType.NONE;
                        task.blockEventMet = false;
                        task.waitBlockPlayerUuid = null;
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
                        task.waitPlayerActionPlayerUuid = null;
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
                        task.waitDimensionPlayerUuid = null;
                        task.waitDimensionId = null;
                        task.ip++;
                    } else {
                        continue;
                    }
                }
                while (task.ip < task.instructions.size() && !task.cancelled) {
                    try {
                        boolean paused = executeTaskInstruction(task);
                        if (paused) break;
                        task.ip++;
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
                    task.finished = true;
                }
                } finally {
                    this.currentTaskScope = null;
                }
            }
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
                    putVariable(name, evaluateExpression((ScriptNode) instr.getArg(1)));
                }
                case VAR_DECL -> {
                    String name = (String) instr.getArg(0);
                    ScriptNode init = (ScriptNode) instr.getArg(1);
                    String scope = instr.getArgCount() >= 3 ? String.valueOf(instr.getArg(2)) : "local";
                    if (init instanceof ScriptNode.AwaitNode awaitNode) {
                        ScriptNode.FunctionCallNode call = awaitNode.getCall();
                        String fn = call.getFunctionName();
                        if ("time".equals(fn) && !call.getArgs().isEmpty()) {
                            Object sec = evaluateExpression(call.getArgs().get(0));
                            if (sec instanceof Number n) {
                                task.waitTimer = n.doubleValue();
                                task.waitType = WaitType.TIME;
                                putVariable(name, 0, scope);
                                return true;
                            }
                        } else if ("uiClick".equals(fn) && !call.getArgs().isEmpty()) {
                            Object playerArg = evaluateExpression(call.getArgs().get(0));
                            net.minecraft.server.level.ServerPlayer sp = resolveServerPlayer(playerArg);
                            LOGGER.info("[Script: {}] await uiClick: playerArg={} ({}), sp={}", script.getName(), playerArg, playerArg != null ? playerArg.getClass().getSimpleName() : "null", sp);
                            if (sp != null) {
                                task.waitUiPlayerUuid = sp.getUUID();
                                task.waitType = WaitType.UI_CLICK;
                                task.pendingUiClickVarName = name;
                                return true;
                            }
                        }
                    }
                    Object val = evaluateExpression(init);
                    putVariable(name, val, scope);
                }
                case CALL -> executeCall(instr);
                case CALL_METHOD -> {
                    if (executeCallMethod(instr, false, task)) return true;
                }
                case NPC_BLOCK -> executeNpcBlock(instr);
                case UI_BLOCK -> executeUiBlock(instr);
                case COMMAND_BLOCK -> executeCommandBlock(instr);
                case FADE_IN -> executeFadeIn(instr);
                case CAMERA -> executeCamera(instr);
                case UI_WIDGET -> executeUiWidget(instr);
                case SET_PROPERTY -> executeSetProperty(instr);
                case AWAIT_TIME -> {
                    Object sec = evaluateExpression((ScriptNode) instr.getArg(0));
                    if (sec instanceof Number n) {
                        task.waitTimer = n.doubleValue();
                        task.waitType = WaitType.TIME;
                        return true;
                    }
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
                    net.minecraft.server.level.ServerPlayer sp = resolveServerPlayer(evaluateExpression(pNode));
                    if (sp != null) {
                        task.waitUiPlayerUuid = sp.getUUID();
                        task.waitType = WaitType.UI_CLICK;
                        return true;
                    }
                    LOGGER.warn("[Script: {}] await uiClick (async): unknown player", script.getName());
                }
                case AWAIT_UI_CLOSE -> {
                    ScriptNode pNode = (ScriptNode) instr.getArg(0);
                    net.minecraft.server.level.ServerPlayer sp = resolveServerPlayer(evaluateExpression(pNode));
                    if (sp != null) {
                        task.waitUiPlayerUuid = sp.getUUID();
                        task.waitType = WaitType.UI_CLOSE;
                        return true;
                    }
                }
                case AWAIT_UI_INPUT -> {
                    ScriptNode pNode = (ScriptNode) instr.getArg(0);
                    ScriptNode wNode = (ScriptNode) instr.getArg(1);
                    net.minecraft.server.level.ServerPlayer sp = resolveServerPlayer(evaluateExpression(pNode));
                    if (sp != null) {
                        task.waitUiPlayerUuid = sp.getUUID();
                        task.uiInputWidgetId = wNode != null ? String.valueOf(evaluateExpression(wNode)) : null;
                        task.waitType = WaitType.UI_INPUT;
                        return true;
                    }
                }
                case AWAIT_TRADE_BUY -> {
                    ScriptNode pNode = (ScriptNode) instr.getArg(0);
                    ScriptNode itemNode = instr.getArgCount() >= 2 && instr.getArg(1) != null ? (ScriptNode) instr.getArg(1) : null;
                    net.minecraft.server.level.ServerPlayer sp = resolveServerPlayer(evaluateExpression(pNode));
                    if (sp != null) {
                        task.waitTradePlayerUuid = sp.getUUID();
                        task.waitTradeItemId = itemNode != null ? String.valueOf(evaluateExpression(itemNode)) : null;
                        task.waitType = WaitType.TRADE_BUY;
                        return true;
                    }
                }
                case AWAIT_TRADE_SELL -> {
                    ScriptNode pNode = (ScriptNode) instr.getArg(0);
                    ScriptNode itemNode = instr.getArgCount() >= 2 && instr.getArg(1) != null ? (ScriptNode) instr.getArg(1) : null;
                    net.minecraft.server.level.ServerPlayer sp = resolveServerPlayer(evaluateExpression(pNode));
                    if (sp != null) {
                        task.waitTradePlayerUuid = sp.getUUID();
                        task.waitTradeItemId = itemNode != null ? String.valueOf(evaluateExpression(itemNode)) : null;
                        task.waitType = WaitType.TRADE_SELL;
                        return true;
                    }
                }
                case AWAIT_POSITION -> {
                    ScriptNode pNode = (ScriptNode) instr.getArg(0);
                    net.minecraft.server.level.ServerPlayer sp = resolveServerPlayer(evaluateExpression(pNode));
                    if (sp != null) {
                        task.waitPositionPlayerUuid = sp.getUUID();
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
                    net.minecraft.server.level.ServerPlayer sp = resolveServerPlayer(evaluateExpression(pNode));
                    if (sp != null) {
                        task.waitInventoryPlayerUuid = sp.getUUID();
                        task.waitInventoryItemId = String.valueOf(evaluateExpression((ScriptNode) instr.getArg(1)));
                        task.waitInventoryCount = instr.getArg(2) != null ? ((Number) evaluateExpression((ScriptNode) instr.getArg(2))).intValue() : 0;
                        task.waitType = WaitType.INVENTORY;
                        return true;
                    }
                }
                case AWAIT_CLICK_BLOCK, AWAIT_BREAK_BLOCK, AWAIT_PLACE_BLOCK, AWAIT_OPEN_CHEST, AWAIT_OPEN_DOOR -> {
                    List<ScriptNode> args = (List<ScriptNode>) instr.getArg(0);
                    net.minecraft.server.level.ServerPlayer sp = resolveServerPlayer(evaluateExpression(args.get(0)));
                    if (sp != null) {
                        task.waitBlockPlayerUuid = sp.getUUID();
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
                        else task.waitType = WaitType.OPEN_DOOR;
                        task.blockEventMet = false;
                        return true;
                    }
                }
                case AWAIT_CHAT -> {
                    List<ScriptNode> args = (List<ScriptNode>) instr.getArg(0);
                    net.minecraft.server.level.ServerPlayer sp = resolveServerPlayer(evaluateExpression(args.get(0)));
                    if (sp != null) {
                        task.waitChatPlayerUuid = sp.getUUID();
                        
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
                    net.minecraft.server.level.ServerPlayer sp = resolveServerPlayer(evaluateExpression(pNode));
                    if (sp != null) {
                        task.waitPlayerActionPlayerUuid = sp.getUUID();
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
                    net.minecraft.server.level.ServerPlayer sp = resolveServerPlayer(evaluateExpression(pNode));
                    if (sp != null) {
                        task.waitDimensionPlayerUuid = sp.getUUID();
                        task.waitDimensionId = String.valueOf(evaluateExpression(dimNode));
                        task.dimensionEventMet = false;
                        task.waitType = WaitType.DIMENSION;
                        return true;
                    }
                }
                case RETURN -> throw new ReturnException(null);
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

            // Fire "interact" event handlers
            for (var entry : eventHandlers.entrySet()) {
                EventHandler handler = entry.getValue();
                if (!handler.active || !handler.eventName.equals("interact")) continue;

                if (!handler.eventArgs.isEmpty()) {
                    if (!matchesInteractTarget(handler.eventArgs.get(0), target)) continue;
                }

                // Save interactor as _eventPlayer for the handler body
                Object prevPlayer = variables.get("_eventPlayer");
                variables.put("_eventPlayer", interactor);
                try {
                    executeInstructionBlock(handler.bodyInstructions);
                } catch (ReturnException e) {
                    // return exits handler body only; use stop handler to deactivate
                } catch (Exception e) {
                    LOGGER.error("[Script: {}] Event handler '{}' error: {}", script.getName(), entry.getKey(), e.getMessage());
                }
                if (prevPlayer != null) variables.put("_eventPlayer", prevPlayer);
                else variables.remove("_eventPlayer");
            }
        }

        private boolean matchesInteractTarget(Object handlerArg, net.minecraft.world.entity.Entity clicked) {
            if (handlerArg == null) return false;
            if (handlerArg instanceof net.minecraft.world.entity.Entity stored) {
                if (clicked.getUUID().equals(stored.getUUID())) return true;
            }
            net.minecraft.world.entity.Entity resolved = resolveEntity(handlerArg);
            if (resolved != null && clicked.getUUID().equals(resolved.getUUID())) return true;
            if (handlerArg instanceof String id) {
                java.util.UUID uid = org.zonarstudio.spraute_engine.entity.NpcManager.get(id);
                if (uid != null && clicked.getUUID().equals(uid)) return true;
            }
            return false;
        }

        public void onKeybind(String key, net.minecraft.world.entity.player.Player player) {
            if (waitType == WaitType.KEYBIND && waitKeybindKey != null && waitKeybindKey.equalsIgnoreCase(key)) {
                keybindMet = true;
                keybindPlayer = player;
            }

            // Fire "keybind" event handlers
            for (var entry : eventHandlers.entrySet()) {
                EventHandler handler = entry.getValue();
                if (!handler.active || !handler.eventName.equals("keybind")) continue;
                if (!handler.eventArgs.isEmpty()) {
                    String expectedKey = String.valueOf(handler.eventArgs.get(0));
                    if (!expectedKey.equalsIgnoreCase(key)) continue;
                }

                Object prevPlayer = variables.get("_eventPlayer");
                variables.put("_eventPlayer", player);
                try {
                    executeInstructionBlock(handler.bodyInstructions);
                } catch (ReturnException e) {
                    // return exits handler body only; use stop handler to deactivate
                } catch (Exception e) {
                    LOGGER.error("[Script: {}] Keybind handler '{}' error: {}", script.getName(), entry.getKey(), e.getMessage(), e);
                    if (source != null) {
                        source.sendFailure(net.minecraft.network.chat.Component.literal(
                                "В§c[Spraute] Keybind handler '" + entry.getKey() + "' error: " + e.getMessage()));
                    }
                }
                if (prevPlayer != null) variables.put("_eventPlayer", prevPlayer);
                else variables.remove("_eventPlayer");
            }
        }

        public void onPlayerJoin(net.minecraft.server.level.ServerPlayer player) {
            for (var entry : eventHandlers.entrySet()) {
                EventHandler handler = entry.getValue();
                if (!handler.active || !handler.eventName.equals("join")) continue;

                Object prevPlayer = variables.get("_eventPlayer");
                variables.put("_eventPlayer", player);
                try {
                    executeInstructionBlock(handler.bodyInstructions);
                } catch (ReturnException e) {
                    // return exits handler body only; use stop handler to deactivate
                } catch (Exception e) {
                    LOGGER.error("[Script: {}] Join handler '{}' error: {}", script.getName(), entry.getKey(), e.getMessage(), e);
                }
                if (prevPlayer != null) variables.put("_eventPlayer", prevPlayer);
                else variables.remove("_eventPlayer");
            }
        }

        public void onPlayerDimensionChange(net.minecraft.server.level.ServerPlayer player, String fromDimension, String toDimension) {
            if (waitType == WaitType.DIMENSION && waitDimensionPlayerUuid != null
                    && waitDimensionPlayerUuid.equals(player.getUUID())
                    && DimensionScriptUtil.matchesDimension(waitDimensionId, toDimension)) {
                dimensionEventMet = true;
            }
            for (AsyncTask task : asyncTasks.values()) {
                if (task.waitType == WaitType.DIMENSION && task.waitDimensionPlayerUuid != null
                        && task.waitDimensionPlayerUuid.equals(player.getUUID())
                        && DimensionScriptUtil.matchesDimension(task.waitDimensionId, toDimension)) {
                    task.dimensionEventMet = true;
                }
            }

            for (var entry : eventHandlers.entrySet()) {
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
                    executeInstructionBlock(handler.bodyInstructions);
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
                    String victimFilter = waitKillVictimTarget != null ? waitKillVictimTarget : "any";
                    if (matchesDeathTarget(entity, victimFilter)) {
                        killMet = true;
                        deathKiller = killer;
                        deadEntity = entity;
                    }
                }
            }

            // Fire "death" event handlers
            for (var entry : eventHandlers.entrySet()) {
                EventHandler handler = entry.getValue();
                if (!handler.active || !handler.eventName.equals("death")) continue;

                if (!handler.eventArgs.isEmpty()) {
                    String targetFilter = String.valueOf(handler.eventArgs.get(0));
                    if (!matchesDeathTarget(entity, targetFilter)) continue;
                }

                Object prevEntity = variables.get("_eventEntity");
                Object prevKiller = variables.get("_eventKiller");
                variables.put("_eventEntity", entity);
                variables.put("_eventKiller", killer);
                try {
                    executeInstructionBlock(handler.bodyInstructions);
                } catch (ReturnException e) {
                    // return exits handler body only; use stop handler to deactivate
                } catch (Exception e) {
                    LOGGER.error("[Script: {}] Death handler '{}' error: {}", script.getName(), entry.getKey(), e.getMessage());
                }
                if (prevEntity != null) variables.put("_eventEntity", prevEntity);
                else variables.remove("_eventEntity");
                if (prevKiller != null) variables.put("_eventKiller", prevKiller);
                else variables.remove("_eventKiller");
            }

            // Fire "kill" event handlers
            if (killer != null) {
                for (var entry : eventHandlers.entrySet()) {
                    EventHandler handler = entry.getValue();
                    if (!handler.active || !handler.eventName.equals("kill")) continue;

                    if (!handler.eventArgs.isEmpty()) {
                        if (!matchesKillFilter(killer, String.valueOf(handler.eventArgs.get(0)))) continue;
                    }
                    if (handler.eventArgs.size() >= 2) {
                        if (!matchesDeathTarget(entity, String.valueOf(handler.eventArgs.get(1)))) continue;
                    }

                    Object prevEntity = variables.get("_eventEntity");
                    Object prevKiller = variables.get("_eventKiller");
                    variables.put("_eventEntity", entity);
                    variables.put("_eventKiller", killer);
                    try {
                        executeInstructionBlock(handler.bodyInstructions);
                    } catch (ReturnException e) {
                        // return exits handler body only; use stop handler to deactivate
                    } catch (Exception e) {
                        LOGGER.error("[Script: {}] Kill handler '{}' error: {}", script.getName(), entry.getKey(), e.getMessage());
                    }
                    if (prevEntity != null) variables.put("_eventEntity", prevEntity);
                    else variables.remove("_eventEntity");
                    if (prevKiller != null) variables.put("_eventKiller", prevKiller);
                    else variables.remove("_eventKiller");
                }
            }
        }

        public void onUiOverlapAction(net.minecraft.server.level.ServerPlayer player, String id1, String id2, boolean overlapping) {
            if (!overlapping) return; // For now, only trigger when they start touching

            // Handle main script await
            if (waitType == WaitType.UI_OVERLAP && waitUiOverlapPlayerUuid != null && waitUiOverlapPlayerUuid.equals(player.getUUID())) {
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
                if (t.waitType == WaitType.UI_OVERLAP && t.waitUiOverlapPlayerUuid != null && t.waitUiOverlapPlayerUuid.equals(player.getUUID())) {
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
            for (var entry : eventHandlers.entrySet()) {
                EventHandler handler = entry.getValue();
                boolean isTouch = handler.eventName.equalsIgnoreCase("uiTouch") || handler.eventName.equalsIgnoreCase("uiOverlap");
                if (!handler.active || !isTouch) continue;

                if (handler.eventArgs.size() >= 3) {
                    net.minecraft.world.entity.Entity targetPlayer = resolveEntity(handler.eventArgs.get(0));
                    if (targetPlayer == null || !player.getUUID().equals(targetPlayer.getUUID())) continue;

                    String expId1 = String.valueOf(handler.eventArgs.get(1));
                    String expId2 = String.valueOf(handler.eventArgs.get(2));

                    if ((expId1.equals(id1) && expId2.equals(id2)) || (expId1.equals(id2) && expId2.equals(id1))) {
                        Object prevPlayer = variables.get("_eventPlayer");
                        variables.put("_eventPlayer", player);
                        try {
                            executeInstructionBlock(handler.bodyInstructions);
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

        public void onUiAction(net.minecraft.server.level.ServerPlayer player, String widgetId, boolean closed) {
            String wid = widgetId != null ? widgetId : "";
            
            if ((waitType == WaitType.UI_CLICK || waitType == WaitType.UI_CLOSE) && waitUiPlayerUuid != null && waitUiPlayerUuid.equals(player.getUUID())) {
                if (waitType == WaitType.UI_CLICK && wid.contains(":")) return; // Skip input events for await uiClick
                if (waitType == WaitType.UI_CLOSE && !closed) return;
                uiClickMet = true;
                uiClickWidgetId = wid;
                uiClickClosed = closed;
            }
            if (waitType == WaitType.UI_INPUT && waitUiPlayerUuid != null && waitUiPlayerUuid.equals(player.getUUID())) {
                if (wid.startsWith("input:") && (uiInputWidgetId == null || wid.equals("input:" + uiInputWidgetId))) {
                    uiClickMet = true;
                    uiInputText = wid.substring(wid.indexOf(":", 6) + 1);
                }
            }

            for (AsyncTask t : asyncTasks.values()) {
                if ((t.waitType == WaitType.UI_CLICK || t.waitType == WaitType.UI_CLOSE) && t.waitUiPlayerUuid != null && t.waitUiPlayerUuid.equals(player.getUUID())) {
                    if (t.waitType == WaitType.UI_CLICK && wid.contains(":")) continue; // Skip input events for await uiClick
                    if (t.waitType == WaitType.UI_CLOSE && !closed) continue;
                    t.uiClickMet = true;
                    t.uiClickWidgetId = wid;
                    t.uiClickClosed = closed;
                }
                if (t.waitType == WaitType.UI_INPUT && t.waitUiPlayerUuid != null && t.waitUiPlayerUuid.equals(player.getUUID())) {
                    if (wid.startsWith("input:") && (t.uiInputWidgetId == null || wid.equals("input:" + t.uiInputWidgetId))) {
                        t.uiClickMet = true;
                        t.uiInputText = wid.substring(wid.indexOf(":", 6) + 1);
                    }
                }
            }

            for (var entry : eventHandlers.entrySet()) {
                EventHandler handler = entry.getValue();
                boolean isUiClick = handler.eventName.equals("uiClick") || handler.eventName.equals("uiclick");
                boolean isUiClose = handler.eventName.equals("uiClose") || handler.eventName.equals("uiclose");
                boolean isUiInput = handler.eventName.equals("uiInput") || handler.eventName.equals("uiinput");
                
                if (!handler.active || (!isUiClick && !isUiClose && !isUiInput)) continue;
                
                if (isUiInput && (!wid.startsWith("input:") || closed)) continue;
                if (isUiClick && (closed || wid.startsWith("input:"))) continue;
                if (isUiClose && !closed) continue;

                if (!handler.eventArgs.isEmpty()) {
                    net.minecraft.world.entity.Entity targetPlayer = resolveEntity(handler.eventArgs.get(0));
                    if (targetPlayer == null || !player.getUUID().equals(targetPlayer.getUUID())) continue;
                }
                
                if (isUiInput && handler.eventArgs.size() > 1) {
                    String reqWid = String.valueOf(handler.eventArgs.get(1));
                    if (!wid.startsWith("input:" + reqWid + ":")) continue;
                }

                Object prevPlayer = variables.get("_eventPlayer");
                Object prevWidget = variables.get("_eventWidget");
                Object prevInput = variables.get("_eventInput");
                variables.put("_eventPlayer", player);
                if (isUiInput) {
                    String[] parts = wid.split(":", 3);
                    variables.put("_eventWidget", parts.length > 1 ? parts[1] : "");
                    variables.put("_eventInput", parts.length > 2 ? parts[2] : "");
                } else {
                    variables.put("_eventWidget", wid);
                }
                
                try {
                    executeInstructionBlock(handler.bodyInstructions);
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
            }

            if (boundUiTemplate != null && boundUiPlayerUuid != null && boundUiPlayerUuid.equals(player.getUUID())) {
                if (!closed && widgetId != null && !widgetId.isEmpty()) {
                    java.util.List<CompiledScript.Instruction> h = boundUiTemplate.getClickHandlers().get(widgetId);
                    if (h != null && !h.isEmpty()) {
                        Object prevPlayer = variables.get("_eventPlayer");
                        Object prevWidget = variables.get("_eventWidget");
                        variables.put("_eventPlayer", player);
                        variables.put("_eventWidget", widgetId);
                        try {
                            executeInstructionBlock(h);
                        } catch (Exception e) {
                            LOGGER.error("[Script: {}] UI on_click '{}': {}", script.getName(), widgetId, e.getMessage());
                        } finally {
                            if (prevPlayer != null) variables.put("_eventPlayer", prevPlayer);
                            else variables.remove("_eventPlayer");
                            if (prevWidget != null) variables.put("_eventWidget", prevWidget);
                            else variables.remove("_eventWidget");
                        }
                    }
                }
                if (closed) {
                    boundUiTemplate = null;
                    boundUiPlayerUuid = null;
                }
            }
        }

        private boolean dimMatches(String waitDim, String eventDim) {
            if (waitDim == null) return true;
            if (eventDim == null) return true;
            String w = waitDim.contains(":") ? waitDim : "minecraft:" + waitDim;
            return w.equals(eventDim);
        }

        public void onClickBlock(net.minecraft.world.entity.player.Player player, net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.Block block, boolean isLeft) {
            onClickBlock(player, pos, block, isLeft, null);
        }
        public void onClickBlock(net.minecraft.world.entity.player.Player player, net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.Block block, boolean isLeft, String dimId) {
            String blockStr = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(block).toString();
            if (waitType == WaitType.CLICK_BLOCK && waitBlockPlayerUuid != null && waitBlockPlayerUuid.equals(player.getUUID())) {
                boolean idMatch = waitBlockId == null || waitBlockId.equals(blockStr) || waitBlockId.equals(blockStr.replace("minecraft:", ""));
                boolean posMatch = waitBlockPos == null || waitBlockPos.equals(pos);
                boolean dimMatch = dimMatches(waitBlockDim, dimId);
                if (idMatch && posMatch && dimMatch) {
                    blockEventMet = true;
                    asyncResult = isLeft ? "left" : "right";
                }
            }
            for (AsyncTask task : asyncTasks.values()) {
                if (task.waitType == WaitType.CLICK_BLOCK && task.waitBlockPlayerUuid != null && task.waitBlockPlayerUuid.equals(player.getUUID())) {
                    boolean idMatch = task.waitBlockId == null || task.waitBlockId.equals(blockStr) || task.waitBlockId.equals(blockStr.replace("minecraft:", ""));
                    boolean posMatch = task.waitBlockPos == null || task.waitBlockPos.equals(pos);
                    boolean dimMatch = dimMatches(task.waitBlockDim, dimId);
                    if (idMatch && posMatch && dimMatch) {
                        task.blockEventMet = true;
                        asyncResult = isLeft ? "left" : "right";
                    }
                }
            }
            fireBlockEvent("clickBlock", player, pos, blockStr, isLeft ? "left" : "right");
        }

        public boolean onBreakBlock(net.minecraft.world.entity.player.Player player, net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.Block block) {
            return onBreakBlock(player, pos, block, null);
        }
        public boolean onBreakBlock(net.minecraft.world.entity.player.Player player, net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.Block block, String dimId) {
            String blockStr = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(block).toString();
            if (waitType == WaitType.BREAK_BLOCK && waitBlockPlayerUuid != null && waitBlockPlayerUuid.equals(player.getUUID())) {
                boolean idMatch = waitBlockId == null || waitBlockId.equals(blockStr) || waitBlockId.equals(blockStr.replace("minecraft:", ""));
                boolean posMatch = waitBlockPos == null || waitBlockPos.equals(pos);
                boolean dimMatch = dimMatches(waitBlockDim, dimId);
                if (idMatch && posMatch && dimMatch) {
                    blockEventMet = true;
                    asyncResult = blockStr;
                }
            }
            for (AsyncTask task : asyncTasks.values()) {
                if (task.waitType == WaitType.BREAK_BLOCK && task.waitBlockPlayerUuid != null && task.waitBlockPlayerUuid.equals(player.getUUID())) {
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
            if (waitType == WaitType.PLACE_BLOCK && waitBlockPlayerUuid != null && waitBlockPlayerUuid.equals(player.getUUID())) {
                boolean idMatch = waitBlockId == null || waitBlockId.equals(blockStr) || waitBlockId.equals(blockStr.replace("minecraft:", ""));
                boolean posMatch = waitBlockPos == null || waitBlockPos.equals(pos);
                boolean dimMatch = dimMatches(waitBlockDim, dimId);
                if (idMatch && posMatch && dimMatch) {
                    blockEventMet = true;
                    asyncResult = blockStr;
                }
            }
            for (AsyncTask task : asyncTasks.values()) {
                if (task.waitType == WaitType.PLACE_BLOCK && task.waitBlockPlayerUuid != null && task.waitBlockPlayerUuid.equals(player.getUUID())) {
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
            if (waitType == WaitType.OPEN_CHEST && waitBlockPlayerUuid != null && waitBlockPlayerUuid.equals(player.getUUID())) {
                if (matchesBlockWait(blockStr, pos, dimId)) {
                    blockEventMet = true;
                    asyncResult = blockStr;
                }
            }
            for (AsyncTask task : asyncTasks.values()) {
                if (task.waitType == WaitType.OPEN_CHEST && task.waitBlockPlayerUuid != null && task.waitBlockPlayerUuid.equals(player.getUUID())) {
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
            if (waitType == WaitType.OPEN_DOOR && waitBlockPlayerUuid != null && waitBlockPlayerUuid.equals(player.getUUID())) {
                if (matchesBlockWait(blockStr, pos, dimId)) {
                    blockEventMet = true;
                    asyncResult = blockStr;
                }
            }
            for (AsyncTask task : asyncTasks.values()) {
                if (task.waitType == WaitType.OPEN_DOOR && task.waitBlockPlayerUuid != null && task.waitBlockPlayerUuid.equals(player.getUUID())) {
                    if (matchesBlockWaitTask(task, blockStr, pos, dimId)) {
                        task.blockEventMet = true;
                        asyncResult = blockStr;
                    }
                }
            }
            return fireBlockEvent("openDoor", player, pos, blockStr, null);
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
            for (var entry : eventHandlers.entrySet()) {
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
                        executeInstructionBlock(handler.bodyInstructions);
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
            if (waitType == WaitType.CHAT && waitChatPlayerUuid != null && waitChatPlayerUuid.equals(player.getUUID())) {
                if (waitChatMessages == null || waitChatMessages.isEmpty() || chatMatches(message, waitChatMessages, waitChatIgnoreCase, waitChatIgnorePunct)) {
                    chatEventMet = true;
                    chatMatchedMessage = message;
                }
            }
            for (AsyncTask task : asyncTasks.values()) {
                if (task.waitType == WaitType.CHAT && task.waitChatPlayerUuid != null && task.waitChatPlayerUuid.equals(player.getUUID())) {
                    if (task.waitChatMessages == null || task.waitChatMessages.isEmpty() || chatMatches(message, task.waitChatMessages, task.waitChatIgnoreCase, task.waitChatIgnorePunct)) {
                        task.chatEventMet = true;
                        task.chatMatchedMessage = message;
                    }
                }
            }

            for (var entry : eventHandlers.entrySet()) {
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

                variables.put("_eventPlayer", player);
                variables.put("_eventMessage", message);

                try {
                    executeInstructionBlock(handler.bodyInstructions);
                } catch (ReturnException e) {
                    // return exits handler body only; use stop handler to deactivate
                } catch (Exception e) {
                    LOGGER.error("[Script: {}] Event handler '{}' error: {}", script.getName(), entry.getKey(), e.getMessage());
                }

                if (prevPlayer != null) variables.put("_eventPlayer", prevPlayer);
                else variables.remove("_eventPlayer");
                if (prevMsg != null) variables.put("_eventMessage", prevMsg);
                else variables.remove("_eventMessage");
            }
        }

        public void onOrbPickup(net.minecraft.server.level.ServerPlayer player, String texture, int amount) {
            if (waitType == WaitType.ORB_PICKUP && waitOrbPickupPlayerUuid != null && waitOrbPickupPlayerUuid.equals(player.getUUID())) {
                if (waitOrbPickupTexture == null || waitOrbPickupTexture.equals(texture)) {
                    waitOrbPickupCurrentCount += amount;
                    if (waitOrbPickupCurrentCount >= waitOrbPickupTargetCount) {
                        waitType = WaitType.NONE;
                    }
                }
            }
            
            for (AsyncTask task : asyncTasks.values()) {
                if (task.waitType == WaitType.ORB_PICKUP && task.waitOrbPickupPlayerUuid != null && task.waitOrbPickupPlayerUuid.equals(player.getUUID())) {
                    if (task.waitOrbPickupTexture == null || task.waitOrbPickupTexture.equals(texture)) {
                        task.waitOrbPickupCurrentCount += amount;
                        if (task.waitOrbPickupCurrentCount >= task.waitOrbPickupTargetCount) {
                            task.waitType = WaitType.NONE;
                        }
                    }
                }
            }

            for (var entry : eventHandlers.entrySet()) {
                EventHandler handler = entry.getValue();
                if (!handler.active || !handler.eventName.equals("orbPickup")) continue;

                if (!handler.eventArgs.isEmpty()) {
                    net.minecraft.world.entity.Entity targetEntity = resolveEntity(handler.eventArgs.get(0));
                    if (targetEntity == null || !player.getUUID().equals(targetEntity.getUUID())) continue;
                }

                if (handler.eventArgs.size() >= 2) {
                    Object texArg = handler.eventArgs.get(1);
                    if (texArg instanceof String s && !s.equals(texture)) continue;
                }

                Object prevPlayer = variables.get("_eventPlayer");
                Object prevTexture = variables.get("_eventTexture");
                Object prevAmount = variables.get("_eventAmount");

                variables.put("_eventPlayer", player);
                variables.put("_eventTexture", texture);
                variables.put("_eventAmount", amount);

                try {
                    executeInstructionBlock(handler.bodyInstructions);
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

        public void onTradeBuy(net.minecraft.server.level.ServerPlayer player, String itemId, int price) {
            handleTradeAwait(player, itemId, price, true);
            fireTradeEventHandlers(player, itemId, price, "tradeBuy");
        }

        public void onTradeSell(net.minecraft.server.level.ServerPlayer player, String itemId, int price) {
            handleTradeAwait(player, itemId, price, false);
            fireTradeEventHandlers(player, itemId, price, "tradeSell");
        }

        private void handleTradeAwait(net.minecraft.server.level.ServerPlayer player, String itemId, int price, boolean buy) {
            WaitType expected = buy ? WaitType.TRADE_BUY : WaitType.TRADE_SELL;
            if (waitType == expected && waitTradePlayerUuid != null && waitTradePlayerUuid.equals(player.getUUID())) {
                if (waitTradeItemId == null || waitTradeItemId.isEmpty() || waitTradeItemId.equals(itemId)) {
                    if (pendingVarName != null) {
                        variables.put(pendingVarName, itemId);
                    }
                    variables.put("_eventItemId", itemId);
                    variables.put("_eventPrice", price);
                    waitType = WaitType.NONE;
                    waitTradePlayerUuid = null;
                    waitTradeItemId = null;
                    pendingVarName = null;
                }
            }
            for (AsyncTask task : asyncTasks.values()) {
                if (task.waitType != expected || task.waitTradePlayerUuid == null
                        || !task.waitTradePlayerUuid.equals(player.getUUID())) continue;
                if (task.waitTradeItemId != null && !task.waitTradeItemId.isEmpty() && !task.waitTradeItemId.equals(itemId)) continue;
                if (task.pendingUiClickVarName != null) {
                    putVariable(task.pendingUiClickVarName, itemId);
                }
                variables.put("_eventItemId", itemId);
                variables.put("_eventPrice", price);
                task.waitType = WaitType.NONE;
                task.waitTradePlayerUuid = null;
                task.waitTradeItemId = null;
                task.pendingUiClickVarName = null;
                task.ip++;
            }
        }

        private void fireTradeEventHandlers(net.minecraft.server.level.ServerPlayer player, String itemId, int price, String eventName) {
            for (var entry : eventHandlers.entrySet()) {
                EventHandler handler = entry.getValue();
                if (!handler.active || !handler.eventName.equals(eventName)) continue;

                if (!handler.eventArgs.isEmpty()) {
                    net.minecraft.world.entity.Entity targetEntity = resolveEntity(handler.eventArgs.get(0));
                    if (targetEntity == null || !player.getUUID().equals(targetEntity.getUUID())) continue;
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
                    executeInstructionBlock(handler.bodyInstructions);
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

        public void onPlayerAction(net.minecraft.world.entity.player.Player player, String actionType, Object target) {
            if (waitType == WaitType.PLAYER_ACTION && player.getUUID().equals(waitPlayerActionPlayerUuid)) {
                if (waitPlayerActionType.equalsIgnoreCase(actionType)) {
                    if (waitPlayerActionTarget == null || (target != null && matchesActionTarget(target, waitPlayerActionTarget))) {
                        playerActionMet = true;
                        asyncResult = target;
                    }
                }
            }
            
            for (var entry : eventHandlers.entrySet()) {
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
                        matched = playerEnt.getUUID().equals(player.getUUID());
                    } else {
                        matched = String.valueOf(arg0).equalsIgnoreCase(actionType);
                    }
                } else {
                    net.minecraft.world.entity.Entity playerEnt = resolveEntity(handler.eventArgs.get(0));
                    if (playerEnt != null && playerEnt.getUUID().equals(player.getUUID())) {
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
                Object prevItemId = variables.get("_eventItemId");
                variables.put("_eventPlayer", player);
                variables.put("_eventTarget", target);
                if (target instanceof net.minecraft.world.item.Item item) {
                    variables.put("_eventItemId", net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(item).toString());
                } else {
                    variables.remove("_eventItemId");
                }

                try {
                    executeInstructionBlock(handler.bodyInstructions);
                } catch (ReturnException e) {
                    // return exits handler body only; use stop handler to deactivate
                } catch (Exception e) {
                    LOGGER.error("[Script: {}] Action handler error: {}", script.getName(), e.getMessage());
                }

                if (prevPlayer != null) variables.put("_eventPlayer", prevPlayer);
                else variables.remove("_eventPlayer");
                if (prevTarget != null) variables.put("_eventTarget", prevTarget);
                else variables.remove("_eventTarget");
                if (prevItemId != null) variables.put("_eventItemId", prevItemId);
                else variables.remove("_eventItemId");
            }

            for (var entry : eventHandlers.entrySet()) {
                EventHandler handler = entry.getValue();
                if (!handler.active || !handler.eventName.equals("jump")) continue;

                if (!handler.eventArgs.isEmpty()) {
                    net.minecraft.world.entity.Entity playerEnt = resolveEntity(handler.eventArgs.get(0));
                    if (playerEnt == null || !playerEnt.getUUID().equals(player.getUUID())) continue;
                }

                Object prevPlayer = variables.get("_eventPlayer");
                variables.put("_eventPlayer", player);

                try {
                    executeInstructionBlock(handler.bodyInstructions);
                } catch (ReturnException e) {
                    // return exits handler body only; use stop handler to deactivate
                } catch (Exception e) {
                    LOGGER.error("[Script: {}] Jump handler error: {}", script.getName(), e.getMessage());
                }

                if (prevPlayer != null) variables.put("_eventPlayer", prevPlayer);
                else variables.remove("_eventPlayer");
            }
            
            for (AsyncTask task : asyncTasks.values()) {
                if (task.waitType == WaitType.PLAYER_ACTION && player.getUUID().equals(task.waitPlayerActionPlayerUuid)) {
                    if (task.waitPlayerActionType.equalsIgnoreCase(actionType)) {
                        if (task.waitPlayerActionTarget == null || (target != null && matchesActionTarget(target, task.waitPlayerActionTarget))) {
                            task.playerActionMet = true;
                            asyncResult = target;
                        }
                    }
                }
            }
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
            return String.valueOf(target).equals(expected);
        }

        private boolean matchesDeathTarget(net.minecraft.world.entity.LivingEntity entity, String target) {
            // Match by NPC script ID
            UUID npcUuid = org.zonarstudio.spraute_engine.entity.NpcManager.get(target);
            if (npcUuid != null) {
                return entity.getUUID().equals(npcUuid);
            }
            // Match by type keyword
            return switch (target) {
                case "player" -> entity instanceof net.minecraft.world.entity.player.Player;
                case "npc" -> entity instanceof org.zonarstudio.spraute_engine.entity.SprauteNpcEntity;
                case "mob" -> !(entity instanceof net.minecraft.world.entity.player.Player)
                        && !(entity instanceof org.zonarstudio.spraute_engine.entity.SprauteNpcEntity);
                case "any" -> true;
                default -> false;
            };
        }

        private boolean matchesKillFilter(net.minecraft.world.entity.Entity entity, String target) {
            if (entity == null) return false;
            if (entity instanceof net.minecraft.world.entity.LivingEntity living) {
                return matchesDeathTarget(living, target);
            }
            UUID npcUuid = org.zonarstudio.spraute_engine.entity.NpcManager.get(target);
            if (npcUuid != null) {
                return entity.getUUID().equals(npcUuid);
            }
            net.minecraft.world.entity.Entity expected = resolveEntity(target);
            if (expected != null) {
                return entity.getUUID().equals(expected.getUUID());
            }
            if (entity instanceof net.minecraft.server.level.ServerPlayer sp
                    && target.equalsIgnoreCase(sp.getName().getString())) {
                return true;
            }
            return false;
        }

        public boolean isFinished() { return finished; }
        public String getScriptName() { return script.getName(); }
        public net.minecraft.commands.CommandSourceStack getSource() { return source; }
        Map<String, UserFunction> getUserFunctions() { return userFunctions; }

        /**
         * Resolve a user function by name: first in own userFunctions, then lazily
         * from running imported scripts.
         */
        private UserFunction resolveFunction(String name) {
            UserFunction f = userFunctions.get(name);
            if (f != null) return f;
            for (String importName : importedScripts) {
                ActiveScript donor = ScriptExecutor.this.findBestDonorScript(importName, name);
                if (donor != null) {
                    f = donor.userFunctions.get(name);
                    if (f != null) return f;
                }
            }
            return null;
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
                return;
            }
            if (variables.containsKey(name)) { variables.put(name, value); return; }
            if (globalVariables.containsKey(name)) { globalVariables.put(name, value); return; }
            net.minecraft.server.level.ServerLevel level = source.getLevel();
            if (level != null) {
                ScriptWorldData world = ScriptWorldData.get(level);
                if (world.has(name)) { world.put(name, value); return; }
            }
            if (currentTaskScope != null) {
                currentTaskScope.taskLocals.put(name, value);
            } else {
                variables.put(name, value);
            }
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
                    if ("global".equals(scope) && globalVariables.containsKey(name)) {
                        // skip вЂ” global already initialized
                    } else if ("world".equals(scope)) {
                        net.minecraft.server.level.ServerLevel lvl = source.getLevel();
                        if (lvl == null || !ScriptWorldData.get(lvl).has(name)) {
                            putVariable(name, evaluateExpression(initializer), scope);
                        }
                    } else {
                        Object value = evaluateExpression(initializer);
                        putVariable(name, value, scope);
                    }
                }
                case CALL -> executeCall(instruction);
                case CALL_METHOD -> executeCallMethod(instruction, false, null);
                case NPC_BLOCK -> executeNpcBlock(instruction);
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
                        Object val = evaluateExpression(node);
                        if ("interact".equals(eventName) && val == null && node instanceof ScriptNode.IdentifierNode idNode) {
                            evaluatedArgs.add(idNode.getName());
                        } else {
                            evaluatedArgs.add(val);
                        }
                    }
                    eventHandlers.put(handlerId, new EventHandler(eventName, evaluatedArgs, bodyInstr));
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
                    String taskId = (String) instruction.getArg(0);
                    @SuppressWarnings("unchecked")
                    List<CompiledScript.Instruction> bodyInstr = (List<CompiledScript.Instruction>) instruction.getArg(1);
                    String id = taskId != null && !taskId.isEmpty() ? taskId : "anon_" + System.nanoTime();
                    asyncTasks.put(id, new AsyncTask(id, bodyInstr));
                }
                case STOP_TASK -> {
                    ScriptNode idNode = (ScriptNode) instruction.getArg(0);
                    String id = String.valueOf(evaluateExpression(idNode));
                    AsyncTask t = asyncTasks.get(id);
                    if (t != null) t.cancelled = true;
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
                         if (call.getFunctionName().equals("interact")) {
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
                             waitDeathTarget = String.valueOf(val);
                             waitType = WaitType.DEATH;
                             pendingVarName = name;
                             return true;
                         } else if (call.getFunctionName().equals("kill")) {
                             if (call.getArgs().isEmpty()) return false;
                             waitKillKillerTarget = String.valueOf(evaluateExpression(call.getArgs().get(0)));
                             waitKillVictimTarget = call.getArgs().size() > 1
                                     ? String.valueOf(evaluateExpression(call.getArgs().get(1))) : "any";
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
                                     if (waitPickupMaxCount >= 0 && e instanceof org.zonarstudio.spraute_engine.entity.SprauteNpcEntity sprauteNpc) {
                                         sprauteNpc.setPickupMaxCount(waitPickupItemId, waitPickupTag, waitPickupBaseCount + waitPickupMaxCount);
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
                             net.minecraft.server.level.ServerPlayer sp = resolveServerPlayer(evaluateExpression(call.getArgs().get(0)));
                             if (sp != null) {
                                 waitOrbPickupPlayerUuid = sp.getUUID();
                                 waitOrbPickupTargetCount = ((Number) evaluateExpression(call.getArgs().get(1))).intValue();
                                 waitOrbPickupTexture = call.getArgs().size() >= 3 && call.getArgs().get(2) != null ? String.valueOf(evaluateExpression(call.getArgs().get(2))) : null;
                                 waitOrbPickupCurrentCount = 0;
                                 waitType = WaitType.ORB_PICKUP;
                                 pendingVarName = name;
                                 return true;
                             }
                         } else if (call.getFunctionName().equals("tradeBuy") || call.getFunctionName().equals("trade_buy")) {
                             if (call.getArgs().isEmpty()) return false;
                             net.minecraft.server.level.ServerPlayer sp = resolveServerPlayer(evaluateExpression(call.getArgs().get(0)));
                             if (sp != null) {
                                 waitTradePlayerUuid = sp.getUUID();
                                 waitTradeItemId = call.getArgs().size() > 1 && call.getArgs().get(1) != null
                                         ? String.valueOf(evaluateExpression(call.getArgs().get(1))) : null;
                                 waitType = WaitType.TRADE_BUY;
                                 pendingVarName = name;
                                 return true;
                             }
                         } else if (call.getFunctionName().equals("tradeSell") || call.getFunctionName().equals("trade_sell")) {
                             if (call.getArgs().isEmpty()) return false;
                             net.minecraft.server.level.ServerPlayer sp = resolveServerPlayer(evaluateExpression(call.getArgs().get(0)));
                             if (sp != null) {
                                 waitTradePlayerUuid = sp.getUUID();
                                 waitTradeItemId = call.getArgs().size() > 1 && call.getArgs().get(1) != null
                                         ? String.valueOf(evaluateExpression(call.getArgs().get(1))) : null;
                                 waitType = WaitType.TRADE_SELL;
                                 pendingVarName = name;
                                 return true;
                             }
                         } else if (call.getFunctionName().equals("uiClick") || call.getFunctionName().equals("uiclick")) {
                             if (call.getArgs().isEmpty()) return false;
                             net.minecraft.server.level.ServerPlayer sp = resolveServerPlayer(evaluateExpression(call.getArgs().get(0)));
                             if (sp != null) {
                                 waitUiPlayerUuid = sp.getUUID();
                                 waitType = WaitType.UI_CLICK;
                                 pendingVarName = name;
                                 return true;
                             }
                         } else if (call.getFunctionName().equals("uiClose") || call.getFunctionName().equals("uiclose")) {
                             if (call.getArgs().isEmpty()) return false;
                             net.minecraft.server.level.ServerPlayer sp = resolveServerPlayer(evaluateExpression(call.getArgs().get(0)));
                             if (sp != null) {
                                 waitUiPlayerUuid = sp.getUUID();
                                 waitType = WaitType.UI_CLOSE;
                                 pendingVarName = name;
                                 return true;
                             }
                         } else if (call.getFunctionName().equals("uiInput")) {
                             if (call.getArgs().isEmpty()) return false;
                             net.minecraft.server.level.ServerPlayer sp = resolveServerPlayer(evaluateExpression(call.getArgs().get(0)));
                             if (sp != null) {
                                 waitUiPlayerUuid = sp.getUUID();
                                 uiInputWidgetId = call.getArgs().size() > 1 ? String.valueOf(evaluateExpression(call.getArgs().get(1))) : null;
                                 waitType = WaitType.UI_INPUT;
                                 pendingVarName = name;
                                 return true;
                             }
                         } else if (call.getFunctionName().equals("position")) {
                             if (call.getArgs().size() < 4) return false;
                             net.minecraft.server.level.ServerPlayer sp = resolveServerPlayer(evaluateExpression(call.getArgs().get(0)));
                             if (sp != null) {
                                 waitPositionPlayerUuid = sp.getUUID();
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
                             net.minecraft.server.level.ServerPlayer sp = resolveServerPlayer(evaluateExpression(call.getArgs().get(0)));
                             if (sp != null) {
                                 waitInventoryPlayerUuid = sp.getUUID();
                                 waitInventoryItemId = String.valueOf(evaluateExpression(call.getArgs().get(1)));
                                 waitInventoryCount = call.getArgs().size() > 2 ? ((Number) evaluateExpression(call.getArgs().get(2))).intValue() : 0;
                                 waitType = WaitType.INVENTORY;
                                 pendingVarName = name;
                                 return true;
                             }
                         } else if (call.getFunctionName().equals("clickBlock") || call.getFunctionName().equals("breakBlock") || call.getFunctionName().equals("placeBlock")
                                 || call.getFunctionName().equals("openChest") || call.getFunctionName().equals("openDoor")) {
                             if (call.getArgs().isEmpty()) return false;
                             net.minecraft.server.level.ServerPlayer sp = resolveServerPlayer(evaluateExpression(call.getArgs().get(0)));
                             if (sp != null) {
                                 waitBlockPlayerUuid = sp.getUUID();
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
                                 else waitType = WaitType.OPEN_DOOR;
                                 blockEventMet = false;
                                 pendingVarName = name;
                                 return true;
                             }
                         } else if (call.getFunctionName().equals("jump")) {
                             if (call.getArgs().isEmpty()) return false;
                             net.minecraft.server.level.ServerPlayer sp = resolveServerPlayer(evaluateExpression(call.getArgs().get(0)));
                             if (sp != null) {
                                 waitPlayerActionPlayerUuid = sp.getUUID();
                                 waitPlayerActionType = "jump";
                                 waitPlayerActionTarget = null;
                                 playerActionMet = false;
                                 waitType = WaitType.PLAYER_ACTION;
                                 pendingVarName = name;
                                 return true;
                             }
                         } else if (call.getFunctionName().equals("action") || call.getFunctionName().equals("playerAction")) {
                             if (call.getArgs().size() < 2) return false;
                             net.minecraft.server.level.ServerPlayer sp = resolveServerPlayer(evaluateExpression(call.getArgs().get(0)));
                             if (sp != null) {
                                 waitPlayerActionPlayerUuid = sp.getUUID();
                                 waitPlayerActionType = String.valueOf(evaluateExpression(call.getArgs().get(1)));
                                 waitPlayerActionTarget = call.getArgs().size() > 2 && call.getArgs().get(2) != null ? String.valueOf(evaluateExpression(call.getArgs().get(2))) : null;
                                 playerActionMet = false;
                                 waitType = WaitType.PLAYER_ACTION;
                                 pendingVarName = name;
                                 return true;
                             }
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
                         if (call.getFunctionName().equals("interact")) {
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
                             waitDeathTarget = String.valueOf(val);
                             waitType = WaitType.DEATH;
                             pendingVarName = name;
                             return true;
                         } else if (call.getFunctionName().equals("kill")) {
                             if (call.getArgs().isEmpty()) return false;
                             waitKillKillerTarget = String.valueOf(evaluateExpression(call.getArgs().get(0)));
                             waitKillVictimTarget = call.getArgs().size() > 1
                                     ? String.valueOf(evaluateExpression(call.getArgs().get(1))) : "any";
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
                                     if (waitPickupMaxCount >= 0 && e instanceof org.zonarstudio.spraute_engine.entity.SprauteNpcEntity sprauteNpc) {
                                         sprauteNpc.setPickupMaxCount(waitPickupItemId, waitPickupTag, waitPickupBaseCount + waitPickupMaxCount);
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
                             net.minecraft.server.level.ServerPlayer sp = resolveServerPlayer(evaluateExpression(call.getArgs().get(0)));
                             if (sp != null) {
                                 waitOrbPickupPlayerUuid = sp.getUUID();
                                 waitOrbPickupTargetCount = ((Number) evaluateExpression(call.getArgs().get(1))).intValue();
                                 waitOrbPickupTexture = call.getArgs().size() >= 3 && call.getArgs().get(2) != null ? String.valueOf(evaluateExpression(call.getArgs().get(2))) : null;
                                 waitOrbPickupCurrentCount = 0;
                                 waitType = WaitType.ORB_PICKUP;
                                 pendingVarName = name;
                                 return true;
                             }
                         } else if (call.getFunctionName().equals("tradeBuy") || call.getFunctionName().equals("trade_buy")) {
                             if (call.getArgs().isEmpty()) return false;
                             net.minecraft.server.level.ServerPlayer sp = resolveServerPlayer(evaluateExpression(call.getArgs().get(0)));
                             if (sp != null) {
                                 waitTradePlayerUuid = sp.getUUID();
                                 waitTradeItemId = call.getArgs().size() > 1 && call.getArgs().get(1) != null
                                         ? String.valueOf(evaluateExpression(call.getArgs().get(1))) : null;
                                 waitType = WaitType.TRADE_BUY;
                                 pendingVarName = name;
                                 return true;
                             }
                         } else if (call.getFunctionName().equals("tradeSell") || call.getFunctionName().equals("trade_sell")) {
                             if (call.getArgs().isEmpty()) return false;
                             net.minecraft.server.level.ServerPlayer sp = resolveServerPlayer(evaluateExpression(call.getArgs().get(0)));
                             if (sp != null) {
                                 waitTradePlayerUuid = sp.getUUID();
                                 waitTradeItemId = call.getArgs().size() > 1 && call.getArgs().get(1) != null
                                         ? String.valueOf(evaluateExpression(call.getArgs().get(1))) : null;
                                 waitType = WaitType.TRADE_SELL;
                                 pendingVarName = name;
                                 return true;
                             }
                         } else if (call.getFunctionName().equals("uiClick") || call.getFunctionName().equals("uiclick")) {
                             if (call.getArgs().isEmpty()) return false;
                             net.minecraft.server.level.ServerPlayer sp = resolveServerPlayer(evaluateExpression(call.getArgs().get(0)));
                             if (sp != null) {
                                 waitUiPlayerUuid = sp.getUUID();
                                 waitType = WaitType.UI_CLICK;
                                 pendingVarName = name;
                                 return true;
                             }
                         } else if (call.getFunctionName().equals("uiClose") || call.getFunctionName().equals("uiclose")) {
                             if (call.getArgs().isEmpty()) return false;
                             net.minecraft.server.level.ServerPlayer sp = resolveServerPlayer(evaluateExpression(call.getArgs().get(0)));
                             if (sp != null) {
                                 waitUiPlayerUuid = sp.getUUID();
                                 waitType = WaitType.UI_CLOSE;
                                 pendingVarName = name;
                                 return true;
                             }
                         } else if (call.getFunctionName().equals("uiInput")) {
                             if (call.getArgs().isEmpty()) return false;
                             net.minecraft.server.level.ServerPlayer sp = resolveServerPlayer(evaluateExpression(call.getArgs().get(0)));
                             if (sp != null) {
                                 waitUiPlayerUuid = sp.getUUID();
                                 uiInputWidgetId = call.getArgs().size() > 1 ? String.valueOf(evaluateExpression(call.getArgs().get(1))) : null;
                                 waitType = WaitType.UI_INPUT;
                                 pendingVarName = name;
                                 return true;
                             }
                         } else if (call.getFunctionName().equals("position")) {
                             if (call.getArgs().size() < 4) return false;
                             net.minecraft.server.level.ServerPlayer sp = resolveServerPlayer(evaluateExpression(call.getArgs().get(0)));
                             if (sp != null) {
                                 waitPositionPlayerUuid = sp.getUUID();
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
                             net.minecraft.server.level.ServerPlayer sp = resolveServerPlayer(evaluateExpression(call.getArgs().get(0)));
                             if (sp != null) {
                                 waitInventoryPlayerUuid = sp.getUUID();
                                 waitInventoryItemId = String.valueOf(evaluateExpression(call.getArgs().get(1)));
                                 waitInventoryCount = call.getArgs().size() > 2 ? ((Number) evaluateExpression(call.getArgs().get(2))).intValue() : 0;
                                 waitType = WaitType.INVENTORY;
                                 pendingVarName = name;
                                 return true;
                             }
                         } else if (call.getFunctionName().equals("clickBlock") || call.getFunctionName().equals("breakBlock") || call.getFunctionName().equals("placeBlock")
                                 || call.getFunctionName().equals("openChest") || call.getFunctionName().equals("openDoor")) {
                             if (call.getArgs().isEmpty()) return false;
                             net.minecraft.server.level.ServerPlayer sp = resolveServerPlayer(evaluateExpression(call.getArgs().get(0)));
                             if (sp != null) {
                                 waitBlockPlayerUuid = sp.getUUID();
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
                                 else waitType = WaitType.OPEN_DOOR;
                                 blockEventMet = false;
                                 pendingVarName = name;
                                 return true;
                             }
                         } else if (call.getFunctionName().equals("jump")) {
                             if (call.getArgs().isEmpty()) return false;
                             net.minecraft.server.level.ServerPlayer sp = resolveServerPlayer(evaluateExpression(call.getArgs().get(0)));
                             if (sp != null) {
                                 waitPlayerActionPlayerUuid = sp.getUUID();
                                 waitPlayerActionType = "jump";
                                 waitPlayerActionTarget = null;
                                 playerActionMet = false;
                                 waitType = WaitType.PLAYER_ACTION;
                                 pendingVarName = name;
                                 return true;
                             }
                         } else if (call.getFunctionName().equals("action") || call.getFunctionName().equals("playerAction")) {
                             if (call.getArgs().size() < 2) return false;
                             net.minecraft.server.level.ServerPlayer sp = resolveServerPlayer(evaluateExpression(call.getArgs().get(0)));
                             if (sp != null) {
                                 waitPlayerActionPlayerUuid = sp.getUUID();
                                 waitPlayerActionType = String.valueOf(evaluateExpression(call.getArgs().get(1)));
                                 waitPlayerActionTarget = call.getArgs().size() > 2 && call.getArgs().get(2) != null ? String.valueOf(evaluateExpression(call.getArgs().get(2))) : null;
                                 playerActionMet = false;
                                 waitType = WaitType.PLAYER_ACTION;
                                 pendingVarName = name;
                                 return true;
                             }
                         }
                    }
                    
                    if ("global".equals(scope) && globalVariables.containsKey(name)) {
                        // skip вЂ” global already initialized
                    } else if ("world".equals(scope)) {
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
                    if (executeCallMethod(instruction, true, null)) return true;
                }
                case NPC_BLOCK -> executeNpcBlock(instruction);
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
                    if (val instanceof Number n) {
                        waitTimer = n.doubleValue();
                        waitType = WaitType.TIME;
                        return true;
                    }
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
                    waitDeathTarget = String.valueOf(val);
                    waitType = WaitType.DEATH;
                    return true;
                }
                case AWAIT_KILL -> {
                    ScriptNode killerNode = (ScriptNode) instruction.getArg(0);
                    ScriptNode victimNode = instruction.getArgCount() >= 2 ? (ScriptNode) instruction.getArg(1) : null;
                    waitKillKillerTarget = String.valueOf(evaluateExpression(killerNode));
                    waitKillVictimTarget = victimNode != null ? String.valueOf(evaluateExpression(victimNode)) : "any";
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
                            if (waitPickupMaxCount >= 0 && e instanceof org.zonarstudio.spraute_engine.entity.SprauteNpcEntity sprauteNpc) {
                                sprauteNpc.setPickupMaxCount(waitPickupItemId, waitPickupTag, waitPickupMaxCount);
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
                    
                    net.minecraft.server.level.ServerPlayer sp = resolveServerPlayer(evaluateExpression(pNode));
                    if (sp != null) {
                        waitOrbPickupPlayerUuid = sp.getUUID();
                        waitOrbPickupTargetCount = ((Number) evaluateExpression(amountNode)).intValue();
                        waitOrbPickupTexture = texNode != null ? String.valueOf(evaluateExpression(texNode)) : null;
                        waitOrbPickupCurrentCount = 0;
                        waitType = WaitType.ORB_PICKUP;
                        return true;
                    }
                }
                case AWAIT_TRADE_BUY -> {
                    ScriptNode pNode = (ScriptNode) instruction.getArg(0);
                    ScriptNode itemNode = instruction.getArgCount() >= 2 && instruction.getArg(1) != null ? (ScriptNode) instruction.getArg(1) : null;
                    net.minecraft.server.level.ServerPlayer sp = resolveServerPlayer(evaluateExpression(pNode));
                    if (sp != null) {
                        waitTradePlayerUuid = sp.getUUID();
                        waitTradeItemId = itemNode != null ? String.valueOf(evaluateExpression(itemNode)) : null;
                        waitType = WaitType.TRADE_BUY;
                        return true;
                    }
                    LOGGER.warn("[Script: {}] await tradeBuy: unknown player", script.getName());
                }
                case AWAIT_TRADE_SELL -> {
                    ScriptNode pNode = (ScriptNode) instruction.getArg(0);
                    ScriptNode itemNode = instruction.getArgCount() >= 2 && instruction.getArg(1) != null ? (ScriptNode) instruction.getArg(1) : null;
                    net.minecraft.server.level.ServerPlayer sp = resolveServerPlayer(evaluateExpression(pNode));
                    if (sp != null) {
                        waitTradePlayerUuid = sp.getUUID();
                        waitTradeItemId = itemNode != null ? String.valueOf(evaluateExpression(itemNode)) : null;
                        waitType = WaitType.TRADE_SELL;
                        return true;
                    }
                    LOGGER.warn("[Script: {}] await tradeSell: unknown player", script.getName());
                }
                case ASYNC_START -> {
                    String taskId = (String) instruction.getArg(0);
                    @SuppressWarnings("unchecked")
                    List<CompiledScript.Instruction> bodyInstr = (List<CompiledScript.Instruction>) instruction.getArg(1);
                    String id = taskId != null && !taskId.isEmpty() ? taskId : "anon_" + System.nanoTime();
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
                    net.minecraft.server.level.ServerPlayer sp = resolveServerPlayer(evaluateExpression(pNode));
                    if (sp != null) {
                        waitUiPlayerUuid = sp.getUUID();
                        waitType = WaitType.UI_CLICK;
                        return true;
                    }
                    LOGGER.warn("[Script: {}] await ui_click: unknown player", script.getName());
                }
                case AWAIT_UI_CLOSE -> {
                    ScriptNode pNode = (ScriptNode) instruction.getArg(0);
                    net.minecraft.server.level.ServerPlayer sp = resolveServerPlayer(evaluateExpression(pNode));
                    if (sp != null) {
                        waitUiPlayerUuid = sp.getUUID();
                        waitType = WaitType.UI_CLOSE;
                        return true;
                    }
                }
                case AWAIT_UI_INPUT -> {
                    ScriptNode pNode = (ScriptNode) instruction.getArg(0);
                    ScriptNode wNode = (ScriptNode) instruction.getArg(1);
                    net.minecraft.server.level.ServerPlayer sp = resolveServerPlayer(evaluateExpression(pNode));
                    if (sp != null) {
                        waitUiPlayerUuid = sp.getUUID();
                        uiInputWidgetId = wNode != null ? String.valueOf(evaluateExpression(wNode)) : null;
                        waitType = WaitType.UI_INPUT;
                        return true;
                    }
                }
                case AWAIT_UI_TOUCH -> {
                    ScriptNode pNode = (ScriptNode) instruction.getArg(0);
                    ScriptNode id1Node = (ScriptNode) instruction.getArg(1);
                    ScriptNode id2Node = (ScriptNode) instruction.getArg(2);
                    net.minecraft.server.level.ServerPlayer sp = resolveServerPlayer(evaluateExpression(pNode));
                    if (sp != null) {
                        waitUiOverlapPlayerUuid = sp.getUUID();
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
                case AWAIT_PLAYER_ACTION -> {
                    ScriptNode pNode = (ScriptNode) instruction.getArg(0);
                    ScriptNode actionNode = (ScriptNode) instruction.getArg(1);
                    ScriptNode targetNode = instruction.getArgCount() >= 3 ? (ScriptNode) instruction.getArg(2) : null;
                    
                    net.minecraft.server.level.ServerPlayer sp = resolveServerPlayer(evaluateExpression(pNode));
                    if (sp != null) {
                        waitPlayerActionPlayerUuid = sp.getUUID();
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
                    net.minecraft.server.level.ServerPlayer sp = resolveServerPlayer(evaluateExpression(pNode));
                    if (sp != null) {
                        waitDimensionPlayerUuid = sp.getUUID();
                        waitDimensionId = String.valueOf(evaluateExpression(dimNode));
                        dimensionEventMet = false;
                        waitType = WaitType.DIMENSION;
                        return true;
                    }
                }
                case AWAIT_POSITION -> {
                    ScriptNode pNode = (ScriptNode) instruction.getArg(0);
                    net.minecraft.server.level.ServerPlayer sp = resolveServerPlayer(evaluateExpression(pNode));
                    if (sp != null) {
                        waitPositionPlayerUuid = sp.getUUID();
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
                    net.minecraft.server.level.ServerPlayer sp = resolveServerPlayer(evaluateExpression(pNode));
                    if (sp != null) {
                        waitInventoryPlayerUuid = sp.getUUID();
                        waitInventoryItemId = String.valueOf(evaluateExpression((ScriptNode) instruction.getArg(1)));
                        waitInventoryCount = instruction.getArg(2) != null ? ((Number) evaluateExpression((ScriptNode) instruction.getArg(2))).intValue() : 0;
                        waitType = WaitType.INVENTORY;
                        return true;
                    }
                }
                case AWAIT_CLICK_BLOCK, AWAIT_BREAK_BLOCK, AWAIT_PLACE_BLOCK, AWAIT_OPEN_CHEST, AWAIT_OPEN_DOOR -> {
                    List<ScriptNode> args = (List<ScriptNode>) instruction.getArg(0);
                    net.minecraft.server.level.ServerPlayer sp = resolveServerPlayer(evaluateExpression(args.get(0)));
                    if (sp != null) {
                        waitBlockPlayerUuid = sp.getUUID();
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
                        else waitType = WaitType.OPEN_DOOR;
                        blockEventMet = false;
                        return true;
                    }
                }
                case AWAIT_CHAT -> {
                    List<ScriptNode> args = (List<ScriptNode>) instruction.getArg(0);
                    net.minecraft.server.level.ServerPlayer sp = resolveServerPlayer(evaluateExpression(args.get(0)));
                    if (sp != null) {
                        waitChatPlayerUuid = sp.getUUID();
                        
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
                    if (t != null) t.cancelled = true;
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
                        Object val = evaluateExpression(node);
                        if ("interact".equals(eventName) && val == null && node instanceof ScriptNode.IdentifierNode idNode) {
                            evaluatedArgs.add(idNode.getName());
                        } else {
                            evaluatedArgs.add(val);
                        }
                    }
                    eventHandlers.put(handlerId, new EventHandler(eventName, evaluatedArgs, bodyInstr));
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
                function.execute(args, source, context);
            } else {
                LOGGER.error("[Script: {}] Unknown function: {}", script.getName(), functionName);
            }
        }

        /** @return true if script should pause (e.g. move_to when blocking) */
        private boolean executeCallMethod(CompiledScript.Instruction instruction, boolean blocking, AsyncTask taskScope) {
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
                if (org.zonarstudio.spraute_engine.entity.NpcManager.get(idStr) != null) {
                    obj = idStr;
                }
            }

            if (obj instanceof String npcId) {
                UUID uuid = org.zonarstudio.spraute_engine.entity.NpcManager.get(npcId);
                if (uuid != null && source.getLevel() != null) {
                    net.minecraft.world.entity.Entity resolved = source.getLevel().getEntity(uuid);
                    if (resolved != null) obj = resolved;
                }
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
                                waitEntityUuid = npc.getUUID();
                                waitMoveTargetX = x;
                                waitMoveTargetY = y;
                                waitMoveTargetZ = z;
                                waitMoveSpeed = speed;
                                waitType = WaitType.MOVE_TO;
                                return true;
                            } else if (taskScope != null) {
                                taskScope.waitEntityUuid = npc.getUUID();
                                taskScope.waitMoveTargetX = x;
                                taskScope.waitMoveTargetY = y;
                                taskScope.waitMoveTargetZ = z;
                                taskScope.waitMoveSpeed = speed;
                                taskScope.waitType = WaitType.MOVE_TO;
                                return true;
                            }
                        } else if (!args.isEmpty()) {
                            net.minecraft.world.entity.Entity target = resolveEntity(args.get(0));
                            double speed = args.size() >= 2 && args.get(1) instanceof Number n ? n.doubleValue() : 1.0;
                            if (target != null) {
                                npc.flyToEntity(target, speed);
                                if (blocking) {
                                    waitEntityUuid = npc.getUUID();
                                    waitFollowTargetUuid = target.getUUID();
                                    waitFollowStopDistance = 2.0;
                                    waitType = WaitType.FOLLOW;
                                    return true;
                                } else if (taskScope != null) {
                                    taskScope.waitEntityUuid = npc.getUUID();
                                    taskScope.waitFollowTargetUuid = target.getUUID();
                                    taskScope.waitFollowStopDistance = 2.0;
                                    taskScope.waitType = WaitType.FOLLOW;
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
                            double speed = args.size() >= 4 ? ((Number) args.get(3)).doubleValue() : 1.0;
                            npc.moveTo(x, y, z, speed);
                            if (blocking) {
                                waitEntityUuid = npc.getUUID();
                                waitMoveTargetX = x;
                                waitMoveTargetY = y;
                                waitMoveTargetZ = z;
                                waitMoveSpeed = speed;
                                waitType = WaitType.MOVE_TO;
                                return true;
                            } else if (taskScope != null) {
                                taskScope.waitEntityUuid = npc.getUUID();
                                taskScope.waitMoveTargetX = x;
                                taskScope.waitMoveTargetY = y;
                                taskScope.waitMoveTargetZ = z;
                                taskScope.waitMoveSpeed = speed;
                                taskScope.waitType = WaitType.MOVE_TO;
                                return true;
                            }
                        }
                    }
                    case "always_move_to", "alwaysmoveto" -> {
                        double speed = 1.0;
                        if (args.size() >= 3 && args.get(0) instanceof Number) {
                            double lx = ((Number) args.get(0)).doubleValue();
                            double ly = ((Number) args.get(1)).doubleValue();
                            double lz = ((Number) args.get(2)).doubleValue();
                            if (args.size() >= 4 && args.get(3) instanceof Number) speed = ((Number) args.get(3)).doubleValue();
                            npc.alwaysMoveTo(lx, ly, lz, speed);
                        } else if (!args.isEmpty()) {
                            net.minecraft.world.entity.Entity target = resolveEntity(args.get(0));
                            if (args.size() >= 2 && args.get(1) instanceof Number) speed = ((Number) args.get(1)).doubleValue();
                            if (target != null) npc.alwaysMoveToEntity(target, speed);
                        }
                    }
                    case "stopMove", "stopmove" -> npc.stopMove();
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
                    case "dropItem", "dropitem", "drop" -> {
                        if (args.size() >= 1 && source.getLevel() != null) {
                            String itemStr = String.valueOf(args.get(0));
                            int count = args.size() >= 2 ? ((Number) args.get(1)).intValue() : 1;
                            boolean checkInv = args.size() >= 3 ? (Boolean) args.get(2) : false;

                            boolean hasItem = !checkInv || npc.countItem(
                                itemStr.contains(":") ? itemStr : "minecraft:" + itemStr
                            ) >= count;

                            if (hasItem) {
                                net.minecraft.world.item.Item item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(
                                    new net.minecraft.resources.ResourceLocation(itemStr.contains(":") ? itemStr : "minecraft:" + itemStr)
                                );
                                if (item != null && item != net.minecraft.world.item.Items.AIR) {
                                    if (checkInv) {
                                        // Try to consume from pickup container
                                        int toRemove = count;
                                        for (int i = 0; i < npc.getPickupContainer().getContainerSize() && toRemove > 0; i++) {
                                            net.minecraft.world.item.ItemStack stack = npc.getPickupContainer().getItem(i);
                                            if (!stack.isEmpty() && stack.getItem() == item) {
                                                int taken = Math.min(toRemove, stack.getCount());
                                                npc.getPickupContainer().removeItem(i, taken);
                                                toRemove -= taken;
                                            }
                                        }
                                    }
                                    net.minecraft.world.item.ItemStack stack = new net.minecraft.world.item.ItemStack(item, count);
                                    net.minecraft.world.entity.item.ItemEntity itementity = new net.minecraft.world.entity.item.ItemEntity(
                                        source.getLevel(), npc.getX(), npc.getY() + 1.0, npc.getZ(), stack
                                    );
                                    itementity.setDefaultPickUpDelay();
                                    
                                    float f = npc.getYRot() * ((float)Math.PI / 180F);
                                    float f1 = npc.getXRot() * ((float)Math.PI / 180F);
                                    float tx = -net.minecraft.util.Mth.sin(f) * net.minecraft.util.Mth.cos(f1);
                                    float tz = net.minecraft.util.Mth.cos(f) * net.minecraft.util.Mth.cos(f1);
                                    float ty = -net.minecraft.util.Mth.sin(f1);
                                    itementity.setDeltaMovement(tx * 0.3F, ty * 0.3F + 0.1F, tz * 0.3F);
                                    
                                    source.getLevel().addFreshEntity(itementity);
                                }
                            }
                        }
                    }
                    case "remove" -> {
                        npc.discard();
                        // NpcManager entries for discarded UUIDs are naturally handled as missing/dead
                    }
                    case "followUntil", "followuntil" -> {
                        net.minecraft.world.entity.Entity target = resolveEntity(args.size() >= 1 ? args.get(0) : null);
                        if (target != null && blocking) {
                            waitEntityUuid = npc.getUUID();
                            waitFollowTargetUuid = target.getUUID();
                            waitFollowStopDistance = args.size() >= 2 ? ((Number) args.get(1)).doubleValue() : 2.0;
                            npc.getNavigation().moveTo(target.getX(), target.getY(), target.getZ(), 1.0);
                            waitType = WaitType.FOLLOW;
                            return true;
                        } else if (target != null && taskScope != null) {
                            taskScope.waitEntityUuid = npc.getUUID();
                            taskScope.waitFollowTargetUuid = target.getUUID();
                            taskScope.waitFollowStopDistance = args.size() >= 2 ? ((Number) args.get(1)).doubleValue() : 2.0;
                            npc.getNavigation().moveTo(target.getX(), target.getY(), target.getZ(), 1.0);
                            taskScope.waitType = WaitType.FOLLOW;
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
            net.minecraft.resources.ResourceLocation rl = new net.minecraft.resources.ResourceLocation(itemId);
            net.minecraft.world.item.Item item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(rl);
            if (item == net.minecraft.world.item.Items.AIR) {
                LOGGER.warn("[Script] Unknown item: {}", itemId);
                return net.minecraft.world.item.ItemStack.EMPTY;
            }
            net.minecraft.world.item.ItemStack stack = new net.minecraft.world.item.ItemStack(item);
            if (nbtString != null && !nbtString.isEmpty()) {
                try {
                    net.minecraft.nbt.CompoundTag tag = net.minecraft.nbt.TagParser.parseTag(nbtString);
                    stack.setTag(tag);
                } catch (Exception e) {
                    LOGGER.warn("[Script] Failed to parse item NBT '{}': {}", nbtString, e.getMessage());
                }
            }
            return stack;
        }

        /**
         * Call a user-defined function, returning its result (or null).
         * Р¤СѓРЅРєС†РёРё РёР· import() РІС‹РїРѕР»РЅСЏСЋС‚СЃСЏ РІ РєРѕРЅС‚РµРєСЃС‚Рµ СЃРєСЂРёРїС‚Р°-Р±РёР±Р»РёРѕС‚РµРєРё (РёС… РїРµСЂРµРјРµРЅРЅС‹Рµ Рё UI).
         */
        private Object callUserFunction(String name, List<Object> args) {
            UserFunction func = userFunctions.get(name);
            if (func != null) {
                return executeUserFunctionBody(func, args);
            }
            for (String importName : importedScripts) {
                ActiveScript donor = ScriptExecutor.this.findBestDonorScript(importName, name);
                if (donor != null && donor.userFunctions.containsKey(name)) {
                    return donor.executeUserFunctionBody(donor.userFunctions.get(name), args);
                }
            }
            throw new RuntimeException("Unknown function: " + name);
        }

        private Object executeUserFunctionBody(UserFunction func, List<Object> args) {
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
            }

            // Restore saved variables
            for (String paramName : func.params) {
                if (savedVars.containsKey(paramName)) {
                    variables.put(paramName, savedVars.get(paramName));
                } else {
                    variables.remove(paramName);
                }
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
                if (org.zonarstudio.spraute_engine.entity.NpcManager.get(idStr) != null) {
                    varObj = idStr;
                }
            }

            if (varObj instanceof java.util.Map map) {
                map.put(propName, value);
                return;
            }

            var entity = varObj instanceof net.minecraft.world.entity.Entity e ? e : null;
            if (entity == null && varObj instanceof String strId) {
                entity = org.zonarstudio.spraute_engine.entity.NpcManager.getEntity(strId, source.getLevel());
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
                            if (entity instanceof net.minecraft.world.entity.LivingEntity living) {
                                living.yHeadRot = n.floatValue();
                                living.yBodyRot = n.floatValue();
                            }
                        }
                    }
                    case "pitch" -> {
                        if (value instanceof Number n) {
                            entity.setXRot(n.floatValue());
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
            } catch (ReturnException re) {
                uiWidgetStack.poll();
                throw re;
            } catch (Exception e) {
                uiWidgetStack.poll();
                LOGGER.error("[Script] create ui '{}' failed: {} ({})", varName, e.getMessage(), e.getClass().getSimpleName(), e);
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
            for (java.util.Map.Entry<String, ScriptNode> entry : propNodes.entrySet()) {
                props.put(entry.getKey(), evaluateExpression(entry.getValue()));
            }

            net.minecraft.server.level.ServerPlayer player = (source.getEntity() instanceof net.minecraft.server.level.ServerPlayer sp) ? sp : null;
            if (player != null) {
                org.zonarstudio.spraute_engine.network.ModNetwork.CHANNEL.send(
                        net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
                        new org.zonarstudio.spraute_engine.network.SyncLoadScreenPacket(props)
                );
            }
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

            net.minecraft.server.level.ServerPlayer player = null;
            if (props.containsKey("target")) {
                Object targetObj = props.get("target").get(0);
                player = resolveServerPlayer(targetObj);
            }
            if (player == null) {
                player = (source.getEntity() instanceof net.minecraft.server.level.ServerPlayer sp) ? sp : null;
            }
            if (player == null) return;

            List<Object> posArgs = props.get("pos");
            double x = player.getX(), y = player.getEyeY(), z = player.getZ();
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
            float yaw = player.getYRot(), pitch = player.getXRot();
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
            boolean smooth = !props.containsKey("smooth") || Boolean.TRUE.equals(props.get("smooth").get(0));
            float smoothTime = props.containsKey("smoothTime") ? ((Number) props.get("smoothTime").get(0)).floatValue() : 0.5f;

            String dimension;
            if (props.containsKey("dimension")) {
                dimension = String.valueOf(props.get("dimension").get(0));
            } else if (props.containsKey("dim")) {
                dimension = String.valueOf(props.get("dim").get(0));
            } else {
                dimension = SprauteEntityCompat.level(player).dimension().location().toString();
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

            final net.minecraft.server.level.ServerPlayer target = player;
            org.zonarstudio.spraute_engine.network.ModNetwork.CHANNEL.send(
                    net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> target),
                    org.zonarstudio.spraute_engine.network.CameraPacket.start(x, y, z, yaw, pitch, time, smooth, smoothTime, dimension,
                            lookAtMode, lookAtEntityId, lookAtX, lookAtY, lookAtZ)
            );

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

        private void executeNpcBlock(CompiledScript.Instruction instruction) {
            String scriptId = (String) instruction.getArg(0);
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
                String name = props.containsKey("name") ? (String) props.get("name").get(0) : scriptId;
                int hp = props.containsKey("hp") ? ((Number) props.get("hp").get(0)).intValue() : 20;
                double speed = props.containsKey("speed") ? ((Number) props.get("speed").get(0)).doubleValue() : 0.3;
                boolean showName = !props.containsKey("showName") || Boolean.TRUE.equals(props.get("showName").get(0));
                boolean collision = !props.containsKey("collision") || Boolean.TRUE.equals(props.get("collision").get(0)) || "true".equalsIgnoreCase(String.valueOf(props.get("collision").get(0)));
                
                List<Object> posArgs = props.get("pos");
                double x = 0, y = 64, z = 0;
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
                float yaw = 0, pitch = 0;
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

                List<Object> dimArgs = props.get("dimension");
                String dimensionId = null;
                if (dimArgs != null && !dimArgs.isEmpty()) {
                    dimensionId = String.valueOf(dimArgs.get(0));
                }

                net.minecraft.server.level.ServerLevel level = source.getLevel();
                if (dimensionId != null) {
                    //? if >=1.20.1 {
                    net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> resKey = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, new net.minecraft.resources.ResourceLocation(dimensionId.contains(":") ? dimensionId : "minecraft:" + dimensionId));
                    //?} else {
                    /*net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> resKey = net.minecraft.resources.ResourceKey.create(net.minecraft.core.Registry.DIMENSION_REGISTRY, new net.minecraft.resources.ResourceLocation(dimensionId.contains(":") ? dimensionId : "minecraft:" + dimensionId));
                    *///?}
                    net.minecraft.server.level.ServerLevel dim = source.getLevel().getServer().getLevel(resKey);
                    if (dim != null) level = dim;
                }

                if (level != null) {
                    net.minecraft.world.entity.Entity existing = org.zonarstudio.spraute_engine.entity.NpcManager.getEntity(scriptId, level);
                    org.zonarstudio.spraute_engine.entity.SprauteNpcEntity npc = null;
                    if (existing instanceof org.zonarstudio.spraute_engine.entity.SprauteNpcEntity e) {
                        npc = e;
                    } else {
                        if (existing != null) existing.discard();
                        npc = org.zonarstudio.spraute_engine.entity.ModEntities.SPRAUTE_NPC.get().create(level);
                    }
                    if (npc != null) {
                        npc.setCustomName(Component.literal(name));
                        npc.setCustomNameVisible(showName);
                        npc.setHasCollision(collision);
                        npc.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(hp);
                        npc.setHealth(hp);
                        npc.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED).setBaseValue(speed);
                        npc.moveTo(x, y, z, yaw, pitch);
                        npc.setYRot(yaw);
                        npc.setYBodyRot(yaw);
                        npc.setYHeadRot(yaw);
                        npc.setXRot(pitch);
                        
                        if (props.containsKey("model")) {
                            npc.setModel(String.valueOf(props.get("model").get(0)));
                        }
                        if (props.containsKey("texture")) {
                            npc.setTexture(String.valueOf(props.get("texture").get(0)));
                        }
                        if (props.containsKey("animation")) {
                            npc.setAnimation(String.valueOf(props.get("animation").get(0)));
                        }
                        if (props.containsKey("idleAnim")) {
                            npc.setIdleAnim(String.valueOf(props.get("idleAnim").get(0)));
                        }
                        if (props.containsKey("walkAnim")) {
                            npc.setWalkAnim(String.valueOf(props.get("walkAnim").get(0)));
                        }
                        if (props.containsKey("flyIdleAnim")) {
                            npc.setFlyIdleAnim(String.valueOf(props.get("flyIdleAnim").get(0)));
                        }
                        if (props.containsKey("flyWalkAnim")) {
                            npc.setFlyWalkAnim(String.valueOf(props.get("flyWalkAnim").get(0)));
                        }
                        if (props.containsKey("swimIdleAnim")) {
                            npc.setSwimIdleAnim(String.valueOf(props.get("swimIdleAnim").get(0)));
                        }
                        if (props.containsKey("swimWalkAnim")) {
                            npc.setSwimWalkAnim(String.valueOf(props.get("swimWalkAnim").get(0)));
                        }
                        if (props.containsKey("dropItem") || props.containsKey("dropMin") || props.containsKey("dropMax") || props.containsKey("dropChance")) {
                            npc.customDrops.clear();
                            String dItem = props.containsKey("dropItem") ? String.valueOf(props.get("dropItem").get(0)) : "minecraft:air";
                            int dMin = props.containsKey("dropMin") ? ((Number)props.get("dropMin").get(0)).intValue() : 1;
                            int dMax = props.containsKey("dropMax") ? ((Number)props.get("dropMax").get(0)).intValue() : 1;
                            int dChance = props.containsKey("dropChance") ? ((Number)props.get("dropChance").get(0)).intValue() : 100;
                            npc.customDrops.add(new org.zonarstudio.spraute_engine.registry.CustomDropRegistry.DropRule(dItem, dMin, dMax, dChance, false, null));
                        }
                        if (props.containsKey("hitbox")) {
                            List<Object> hb = props.get("hitbox");
                            if (hb != null && hb.size() >= 2) {
                                float hw = ((Number) hb.get(0)).floatValue();
                                float hh = ((Number) hb.get(1)).floatValue();
                                float ox = 0f, oy = 0f, oz = 0f;
                                if (hb.size() >= 5) {
                                    ox = ((Number) hb.get(2)).floatValue();
                                    oy = ((Number) hb.get(3)).floatValue();
                                    oz = ((Number) hb.get(4)).floatValue();
                                }
                                npc.setHitbox(hw, hh, ox, oy, oz);
                            }
                        }
                        if (existing != npc) level.addFreshEntity(npc);
                        
                        org.zonarstudio.spraute_engine.entity.NpcManager.track(scriptId, npc.getUUID());
                        variables.put(scriptId, npc);
                    }
                }
            } catch (Exception e) {
                LOGGER.error("Failed to execute NPC block: {}", e.getMessage());
            }
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
                if (currentTaskScope != null && currentTaskScope.taskLocals.containsKey(name)) return currentTaskScope.taskLocals.get(name);
                if (variables.containsKey(name)) return variables.get(name);
                if (globalVariables.containsKey(name)) return globalVariables.get(name);
                net.minecraft.server.level.ServerLevel level = source.getLevel();
                if (level != null) {
                    ScriptWorldData world = ScriptWorldData.get(level);
                    if (world.has(name)) return world.get(name, source.getServer(), level);
                }
                if (org.zonarstudio.spraute_engine.entity.NpcManager.get(name) != null) {
                    return name;
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
                    return function.execute(args, source, context);
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
                    if (org.zonarstudio.spraute_engine.entity.NpcManager.get(objName) != null) {
                        obj = objName;
                    }
                }
                
                if (obj instanceof java.util.Map map) {
                    return map.get(normPropKey(prop.getPropertyName()));
                }

                if (obj instanceof net.minecraft.world.entity.player.Player player) {
                    String playerProp = normPropKey(prop.getPropertyName());
                    return switch (playerProp) {
                        case "name" -> player.getName().getString();
                        case "hp" -> player.getHealth();
                        case "x" -> player.getX();
                        case "y" -> player.getY();
                        case "z" -> player.getZ();
                        case "pitch" -> player.getXRot();
                        case "yaw" -> player.getYRot();
                        case "lookX" -> player.getLookAngle().x;
                        case "lookY" -> player.getLookAngle().y;
                        case "lookZ" -> player.getLookAngle().z;
                        case "uuid" -> player.getUUID().toString();
                        case "isSneaking", "issneaking", "sneaking" -> player.isShiftKeyDown();
                        case "isCrouching", "iscrouching", "crouching" -> player.isCrouching();
                        case "onGround", "onground", "isOnGround" -> org.zonarstudio.spraute_engine.compat.SprauteEntityCompat.onGround(player);
                        case "java" -> player;
                        case "data" -> org.zonarstudio.spraute_engine.script.ScriptManager.getInstance().getPlayerSessionData(player.getUUID());
                        case "savedData" -> new PlayerSavedDataMap(player.getUUID(), source.getLevel().getServer(), source.getLevel());
                        default -> null;
                    };
                }
                
                net.minecraft.world.entity.Entity npcEntity = null;
                if (obj instanceof net.minecraft.world.entity.Entity directEntity) {
                    npcEntity = directEntity;
                } else if (obj instanceof String npcId) {
                    npcEntity = org.zonarstudio.spraute_engine.entity.NpcManager.getEntity(npcId, source.getLevel());
                } else if (obj == null && !objName.isEmpty()) {
                    npcEntity = org.zonarstudio.spraute_engine.entity.NpcManager.getEntity(objName, source.getLevel());
                }
                
                if (npcEntity != null) {
                    String npcProp = normPropKey(prop.getPropertyName());
                    return switch (npcProp) {
                        case "name" -> npcEntity.getCustomName() != null ? npcEntity.getCustomName().getString() : "";
                        case "showName" -> npcEntity.isCustomNameVisible();
                        case "hp" -> npcEntity instanceof net.minecraft.world.entity.LivingEntity living ? living.getHealth() : 0;
                        case "maxHp" -> npcEntity instanceof net.minecraft.world.entity.LivingEntity lh ? (lh.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH) != null 
                            ? lh.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).getBaseValue() : 0) : 0;
                        case "speed" -> npcEntity instanceof net.minecraft.world.entity.LivingEntity ls ? (ls.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED) != null 
                            ? ls.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED).getBaseValue() : 0) : 0;
                        case "x" -> npcEntity.getX();
                        case "y" -> npcEntity.getY();
                        case "z" -> npcEntity.getZ();
                        case "yaw" -> npcEntity.getYRot();
                        case "pitch" -> npcEntity.getXRot();
                        case "uuid" -> npcEntity.getUUID().toString();
                        case "java" -> npcEntity;
                        case "model" -> npcEntity instanceof org.zonarstudio.spraute_engine.entity.SprauteNpcEntity npc ? npc.getModel() : "";
                        case "texture" -> npcEntity instanceof org.zonarstudio.spraute_engine.entity.SprauteNpcEntity npc ? npc.getTexture() : "";
                        case "dropItem" -> npcEntity instanceof org.zonarstudio.spraute_engine.entity.SprauteNpcEntity npc ? (!npc.customDrops.isEmpty() ? npc.customDrops.get(0).itemId : "") : "";
                        case "dropMin" -> npcEntity instanceof org.zonarstudio.spraute_engine.entity.SprauteNpcEntity npc ? (!npc.customDrops.isEmpty() ? npc.customDrops.get(0).min : 0) : 0;
                        case "dropMax" -> npcEntity instanceof org.zonarstudio.spraute_engine.entity.SprauteNpcEntity npc ? (!npc.customDrops.isEmpty() ? npc.customDrops.get(0).max : 0) : 0;
                        case "dropChance" -> npcEntity instanceof org.zonarstudio.spraute_engine.entity.SprauteNpcEntity npc ? (!npc.customDrops.isEmpty() ? npc.customDrops.get(0).chance : 0) : 0;
                        default -> npcEntity instanceof org.zonarstudio.spraute_engine.entity.SprauteNpcEntity npc ? npc.customData.get(prop.getPropertyName()) : null;
                    };
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
                    if (org.zonarstudio.spraute_engine.entity.NpcManager.get(objName) != null) {
                        obj = objName;
                    }
                }
                
                String method = methodCall.getMethodName();
                List<Object> methodArgs = new ArrayList<>();
                for (ScriptNode argNode : methodCall.getArgs()) {
                    methodArgs.add(evaluateExpression(argNode));
                }

                if (obj instanceof String npcId) {
                    UUID uuid = org.zonarstudio.spraute_engine.entity.NpcManager.get(npcId);
                    if (uuid != null && source.getLevel() != null) {
                        net.minecraft.world.entity.Entity resolved = source.getLevel().getEntity(uuid);
                        if (resolved != null) obj = resolved;
                    }
                }
                if (obj == null && !objName.isEmpty() && org.zonarstudio.spraute_engine.entity.NpcManager.get(objName) != null) {
                    UUID uuid = org.zonarstudio.spraute_engine.entity.NpcManager.get(objName);
                    if (source.getLevel() != null) obj = source.getLevel().getEntity(uuid);
                }

                if (obj instanceof net.minecraft.world.entity.Entity entity) {
                    if (method.equals("java")) return entity;
                    if (method.equals("uuid")) return entity.getUUID().toString();
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
                }

                if (obj instanceof net.minecraft.world.entity.player.Player player
                        && org.zonarstudio.spraute_engine.script.PlayerScriptMethods.isKnown(method)) {
                    return org.zonarstudio.spraute_engine.script.PlayerScriptMethods.invoke(
                            player, method, methodArgs, this::performRaycast);
                }

                if (obj instanceof String str) {
                    return switch (method) {
                        case "toInt" -> { try { yield Integer.parseInt(str); } catch(Exception e){ yield null; } }
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
                    return Objects.equals(left, right);
                }
                if (binary.getOperator().getType() == ScriptToken.TokenType.NEQ) {
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
            net.minecraft.world.item.Item targetItem = org.zonarstudio.spraute_engine.script.ItemStackScriptUtil.resolveItem(itemId);
            if (targetItem == null) return 0;
            net.minecraft.resources.ResourceLocation targetRl = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(targetItem);
            int total = 0;
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                net.minecraft.world.item.ItemStack stack = player.getInventory().getItem(i);
                if (stack.isEmpty()) continue;
                if (!net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem()).equals(targetRl)) continue;
                if (nbtTag != null && !nbtTag.isEmpty()) {
                    if (!stack.hasTag()) continue;
                    try {
                        net.minecraft.nbt.CompoundTag required = net.minecraft.nbt.TagParser.parseTag(nbtTag);
                        net.minecraft.nbt.CompoundTag stackTag = stack.getTag();
                        boolean tagMatches = true;
                        for (String key : required.getAllKeys()) {
                            if (!stackTag.contains(key) || !java.util.Objects.equals(stackTag.get(key), required.get(key))) {
                                tagMatches = false;
                                break;
                            }
                        }
                        if (!tagMatches) continue;
                    } catch (Exception e) {
                        continue;
                    }
                }
                total += stack.getCount();
            }
            return total;
        }

        /** Returns total count of matching items. For SprauteNpcEntity uses pickup container (not hand). */
        private int countMatchingItems(net.minecraft.world.entity.Mob mob, String itemId, String nbtTag) {
            if (mob instanceof org.zonarstudio.spraute_engine.entity.SprauteNpcEntity sprauteNpc) {
                return sprauteNpc.countItem(itemId, nbtTag);
            }
            net.minecraft.resources.ResourceLocation targetRl = new net.minecraft.resources.ResourceLocation(itemId);
            int total = 0;
            for (net.minecraft.world.entity.EquipmentSlot slot : net.minecraft.world.entity.EquipmentSlot.values()) {
                net.minecraft.world.item.ItemStack stack = mob.getItemBySlot(slot);
                if (stack.isEmpty()) continue;
                if (!net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem()).equals(targetRl)) continue;
                if (nbtTag != null && !nbtTag.isEmpty()) {
                    if (!stack.hasTag()) continue;
                    try {
                        net.minecraft.nbt.CompoundTag required = net.minecraft.nbt.TagParser.parseTag(nbtTag);
                        net.minecraft.nbt.CompoundTag stackTag = stack.getTag();
                        boolean tagMatches = true;
                        for (String key : required.getAllKeys()) {
                            if (!stackTag.contains(key) || !java.util.Objects.equals(stackTag.get(key), required.get(key))) {
                                tagMatches = false;
                                break;
                            }
                        }
                        if (!tagMatches) continue;
                    } catch (Exception e) {
                        continue;
                    }
                }
                total += stack.getCount();
            }
            return total;
        }

        /** Returns first ItemStack matching itemId and optional nbtTag, or null. For SprauteNpcEntity uses pickup container. */
        private net.minecraft.world.item.ItemStack getMatchingItemStack(net.minecraft.world.entity.Mob mob, String itemId, String nbtTag) {
            if (mob instanceof org.zonarstudio.spraute_engine.entity.SprauteNpcEntity sprauteNpc) {
                net.minecraft.resources.ResourceLocation targetRl = new net.minecraft.resources.ResourceLocation(itemId);
                for (int i = 0; i < sprauteNpc.getPickupContainer().getContainerSize(); i++) {
                    net.minecraft.world.item.ItemStack stack = sprauteNpc.getPickupContainer().getItem(i);
                    if (stack.isEmpty()) continue;
                    if (!net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem()).equals(targetRl)) continue;
                    if (nbtTag != null && !nbtTag.isEmpty()) {
                        if (!stack.hasTag()) continue;
                        try {
                            net.minecraft.nbt.CompoundTag required = net.minecraft.nbt.TagParser.parseTag(nbtTag);
                            net.minecraft.nbt.CompoundTag stackTag = stack.getTag();
                            boolean tagMatches = true;
                            for (String key : required.getAllKeys()) {
                                if (!stackTag.contains(key) || !java.util.Objects.equals(stackTag.get(key), required.get(key))) {
                                    tagMatches = false;
                                    break;
                                }
                            }
                            if (!tagMatches) continue;
                        } catch (Exception e) { continue; }
                    }
                    return stack;
                }
                return null;
            }
            net.minecraft.resources.ResourceLocation targetRl = new net.minecraft.resources.ResourceLocation(itemId);
            for (net.minecraft.world.entity.EquipmentSlot slot : net.minecraft.world.entity.EquipmentSlot.values()) {
                net.minecraft.world.item.ItemStack stack = mob.getItemBySlot(slot);
                if (stack.isEmpty()) continue;
                if (!net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem()).equals(targetRl)) continue;
                if (nbtTag != null && !nbtTag.isEmpty()) {
                    if (!stack.hasTag()) continue;
                    try {
                        net.minecraft.nbt.CompoundTag required = net.minecraft.nbt.TagParser.parseTag(nbtTag);
                        net.minecraft.nbt.CompoundTag stackTag = stack.getTag();
                        boolean tagMatches = true;
                        for (String key : required.getAllKeys()) {
                            if (!stackTag.contains(key) || !java.util.Objects.equals(stackTag.get(key), required.get(key))) {
                                tagMatches = false;
                                break;
                            }
                        }
                        if (!tagMatches) continue;
                    } catch (Exception e) {
                        continue;
                    }
                }
                return stack;
            }
            return null;
        }

        private boolean hasItemMatching(net.minecraft.world.entity.Mob mob, String itemId, String nbtTag) {
            return countMatchingItems(mob, itemId, nbtTag) > 0;
        }

        private void clearNpcPickupMax(String npcId) {
            if (npcId != null && source != null && source.getLevel() != null) {
                net.minecraft.world.entity.Entity e = org.zonarstudio.spraute_engine.entity.NpcManager.getEntity(npcId, source.getLevel());
                if (e instanceof org.zonarstudio.spraute_engine.entity.SprauteNpcEntity sprauteNpc) {
                    sprauteNpc.clearPickupMaxCount();
                }
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
         * ╨Я╤А╨╕╨╝╨╡╨╜╤П╨╡╤В ╨║╨╛╨╝╨░╨╜╨┤╤Г ╨╛╨┤╨╜╨╛╨│╨╛ ╨Э╨Я╨б ╨▓ ╨║╨╛╨╜╤В╨╡╨║╤Б╤В╨╡ ╨│╤А╤Г╨┐╨┐╤Л.
         * ╨Р╨╜╨░╨╗╨╛╨│ ╤З╨░╤Б╤В╨╕ switch ╨▓╨╜╤Г╤В╤А╨╕ executeCallMethod, ╨╜╨╛ ╨▒╨╡╨╖ ╨▒╨╗╨╛╨║╨╕╤А╤Г╤О╤Й╨╕╤Е wait'╨╛╨▓.
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
            }
        }

        private boolean isAdditiveAnimationArg(Object arg) {
            if (arg instanceof Boolean b) return b;
            if (arg == null) return false;
            String s = String.valueOf(arg).trim();
            if ("false".equalsIgnoreCase(s) || "replace".equalsIgnoreCase(s) || "set".equalsIgnoreCase(s) || "noadd".equalsIgnoreCase(s)) return false;
            return "add".equalsIgnoreCase(s) || "additive".equalsIgnoreCase(s) || "true".equalsIgnoreCase(s);
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

        /** True when the mob is close enough to the scripted move_to point (not the same as {@code Navigation#isDone()}). */
        private static boolean isCloseToMoveTarget(net.minecraft.world.entity.Mob mob, double tx, double ty, double tz) {
            double dx = mob.getX() - tx;
            double dy = mob.getY() - ty;
            double dz = mob.getZ() - tz;
            // ╨Ф╨╛╨┐╤Г╤Б╨║: 1.0 ╨▒╨╗╨╛╨║╨░ ╨┐╨╛ ╨│╨╛╤А╨╕╨╖╨╛╨╜╤В╨░╨╗╨╕ (╤З╤В╨╛╨▒╤Л ╤Г╤З╨╕╤В╤Л╨▓╨░╤В╤М ╤А╨░╨╖╨╜╨╕╤Ж╤Г ╨╝╨╡╨╢╨┤╤Г ╤Ж╨╡╨╜╤В╤А╨╛╨╝ ╨╕ ╤Г╨│╨╗╨╛╨╝ ╨▒╨╗╨╛╨║╨░ + ╤Е╨╕╤В╨▒╨╛╨║╤Б), 1.5 ╨▒╨╗╨╛╨║╨░ ╨┐╨╛ ╨▓╨╡╤А╤В╨╕╨║╨░╨╗╨╕
            double horizTol = 1.0;
            return dx * dx + dz * dz <= horizTol * horizTol && Math.abs(dy) <= 1.5;
        }

        /** Async task state. Shares variables with parent. */
        private class AsyncTask {
            final String id;
            final List<CompiledScript.Instruction> instructions;
            int ip;
            boolean finished;
            boolean cancelled;
            WaitType waitType = WaitType.NONE;
            double waitTimer;
            UUID waitEntityUuid;
            UUID waitFollowTargetUuid;
            double waitFollowStopDistance = 2.0;
            double waitMoveTargetX;
            double waitMoveTargetY;
            double waitMoveTargetZ;
            double waitMoveSpeed = 1.0;
            /** {@link WaitType#UI_CLICK} тАФ same semantics as main script await ui_click */
            UUID waitUiPlayerUuid;
            String pendingUiClickVarName;
            boolean uiClickMet;
            String uiClickWidgetId = "";
            boolean uiClickClosed;

            UUID waitPositionPlayerUuid;
            double waitPositionX, waitPositionY, waitPositionZ, waitPositionRadius;
            UUID waitInventoryPlayerUuid;
            String waitInventoryItemId;
            int waitInventoryCount;
            UUID waitBlockPlayerUuid;
            String waitBlockId;
            net.minecraft.core.BlockPos waitBlockPos;
            String waitBlockDim;
            boolean blockEventMet;
            String uiInputWidgetId = "";
            String uiInputText = "";

            UUID waitChatPlayerUuid = null;
            List<String> waitChatMessages = null;
            boolean waitChatIgnoreCase = true;
            boolean waitChatIgnorePunct = true;
            boolean chatEventMet = false;
            String chatMatchedMessage = "";
            
            UUID waitUiOverlapPlayerUuid = null;
            String waitUiOverlapId1 = "";
            String waitUiOverlapId2 = "";
            boolean uiOverlapMet = false;
            
            UUID waitOrbPickupPlayerUuid = null;
            String waitOrbPickupTexture = null;
            int waitOrbPickupTargetCount = 0;
            int waitOrbPickupCurrentCount = 0;

            UUID waitTradePlayerUuid = null;
            String waitTradeItemId = null;

            UUID waitPlayerActionPlayerUuid = null;
            String waitPlayerActionType = "";
            String waitPlayerActionTarget = null;
            boolean playerActionMet = false;

            UUID waitDimensionPlayerUuid = null;
            String waitDimensionId = null;
            boolean dimensionEventMet = false;

            final Map<String, Object> taskLocals = new HashMap<>();

            AsyncTask(String id, List<CompiledScript.Instruction> instructions) {
                this.id = id;
                this.instructions = instructions;
                this.taskLocals.putAll(variables);
                if (currentTaskScope != null) {
                    this.taskLocals.putAll(currentTaskScope.taskLocals);
                }
            }
        }
    }

        private enum WaitType {
        NONE, TIME, INTERACT, NEXT, KEYBIND, DEATH, KILL, UI_CLICK, UI_CLOSE, MOVE_TO, FOLLOW, PICKUP, ORB_PICKUP,
        TRADE_BUY, TRADE_SELL, WAIT_TASK,
        POSITION, INVENTORY, CLICK_BLOCK, BREAK_BLOCK, PLACE_BLOCK, OPEN_CHEST, OPEN_DOOR, UI_INPUT, CHAT, UI_OVERLAP,
        PLAYER_ACTION, DIMENSION
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
            // Not fully implemented for iteration, mainly used for get/put via scripting
            return java.util.Collections.emptySet();
        }
        
        @Override
        public boolean containsKey(Object key) {
            return getData().has(prefix + key);
        }
    }
}
