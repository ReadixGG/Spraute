package org.zonarstudio.spraute_engine.script;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A compiled script ready for execution.
 * Contains a list of bytecode-like instructions.
 */
public class CompiledScript {

    /**
     * Opcodes for script instructions.
     */
    public enum Opcode {
        CALL,        // Call a function. Args: [functionName, ScriptNode... args]
        VAR_DECL,    // Declare variable. Args: [name, ScriptNode initializer, scope "local"|"global"|"world"]
        NPC_BLOCK,   // Spawn NPC via block. Args: [id, Map<String, List<ScriptNode>> props]
        MODEL_BLOCK, // Spawn geo model via block. Args: [id, Map<String, List<ScriptNode>> props]
        HITBOX_BLOCK, // Spawn scene hitbox node. Args: [id, Map<String, List<ScriptNode>> props]
        EMPTY_BLOCK,  // Spawn scene empty node. Args: [id, Map<String, List<ScriptNode>> props]
        SKIN_OVERLAY_BLOCK, // Spawn SKIN_OVERLAY under a PLAYER node. Args: [id, Map props]
        PARTICLE_EMITTER_BLOCK, // Spawn scene particle emitter. Args: [id, Map<String, List<ScriptNode>> props]
        PARTICLE_DEF_BLOCK, // Register custom particle + RES_PARTICLE node. Args: [id, Map<String, List<ScriptNode>> props]
        PROJECTILE_DEF_BLOCK, // Register custom projectile + RES_PROJECTILE node. Args: [id, Map props]
        LIGHT_BLOCK, // Spawn scene light node. Args: [id, Map<String, List<ScriptNode>> props]
        AUDIO_SOURCE_BLOCK, // Spawn scene audio source. Args: [id, Map<String, List<ScriptNode>> props]
        POST_PROCESS_BLOCK, // Spawn scene post-process volume. Args: [id, Map<String, List<ScriptNode>> props]
        CUSTOM_NODE_BLOCK, // Spawn custom schema node. Args: [typeId, id, Map<String, List<ScriptNode>> props]
        NPC_PREFAB_DEF, // Register NPC prefab. Args: [prefabId, Map<String, List<ScriptNode>> props]
        UI_BLOCK,    // create ui: Args: [variableName, Map<String, ScriptNode> rootProps, List<Instruction> bodyInstructions]
        COMMAND_BLOCK, // create command: Args: [commandName, List<Instruction> bodyInstructions]
        SET_PROPERTY, // Set property. Args: [objectId, propName, ScriptNode value]
        AWAIT_TIME,  // Wait for time. Args: [ScriptNode seconds]
        AWAIT_INTERACT, // Wait for interaction. Args: [ScriptNode entityId]
        AWAIT_NEXT,  // Wait for dialogue continuation keybind. Args: none
        CALL_METHOD, // Call method on object. Args: [objName, methodName, List<ScriptNode> args], optional [Boolean awaitBlocking]
        GET_PROPERTY, // Get property from object. Args: [objName, propName]
        VAR_ASSIGN,  // Reassign variable. Args: [name, ScriptNode value]
        JUMP,        // Unconditional jump. Args: [Integer targetIndex]
        JUMP_IF_FALSE, // Jump if condition is false. Args: [ScriptNode condition, Integer targetIndex]
        FUN_DEF,     // Define user function. Args: [name, List<String> params, List<Instruction> bodyInstructions]
        RETURN,      // Return from function. Args: [ScriptNode value] (value may be null)
        REGISTER_ON, // Register event handler. Args: [eventName, List<ScriptNode> eventArgs, handlerId, List<Instruction> bodyInstructions]
        REGISTER_EVERY, // Register periodic timer. Args: [ScriptNode interval, handlerId, List<Instruction> bodyInstructions]
        STOP_HANDLER, // Stop a running handler. Args: [handlerId]
        AWAIT_KEYBIND, // Wait for key press. Args: [ScriptNode keyName]
        AWAIT_DEATH,  // Wait for entity death. Args: [ScriptNode entityId/type]
        AWAIT_KILL,   // Wait for killer to slay victim. Args: [ScriptNode killerFilter, ScriptNode victimFilter?]
        AWAIT_PICKUP, // Wait for NPC to pick up item. Args: [ScriptNode npcId, ScriptNode amount, ScriptNode itemId, ScriptNode nbt?]
        AWAIT_ORB_PICKUP, // Wait for player to pick up orb. Args: [ScriptNode player, ScriptNode amount, ScriptNode texture]
        AWAIT_PROJECTILE_HIT, // Wait for projectile hit. Args: [ScriptNode projectileId, ScriptNode hitType?]
        AWAIT_TRADE_BUY,  // deprecated: prefer AWAIT_CUSTOM("tradeBuy", …)
        AWAIT_TRADE_SELL, // deprecated: prefer AWAIT_CUSTOM("tradeSell", …)
        /** await anyScriptEvent(filters…) — Args: [String eventName, List&lt;ScriptNode&gt; filterArgs] */
        AWAIT_CUSTOM,
        AWAIT_REL_GIFT,   // Wait for NPC gift. Args: [ScriptNode player, ScriptNode npcOrItem?, ScriptNode item?]
        AWAIT_REL_REP_CHANGE, // Wait for rep change. Args: [ScriptNode player, ScriptNode npc?]
        AWAIT_REL_TALK,       // Wait for talk. Args: [ScriptNode player, ScriptNode npc?]
        AWAIT_REL_BUTTON,     // Wait for custom button. Args: [ScriptNode player, ScriptNode btn?, ScriptNode npc?]
        ASYNC_START,  // Start async block. Args: [ScriptNode taskIdExpr?, List<Instruction> bodyInstructions]
        AWAIT_TASK,   // Wait for named task to finish. Args: [ScriptNode taskId]
        STOP_TASK,    // Interrupt a named task. Args: [ScriptNode taskId]
        AWAIT_UI_CLICK, // Wait for UI button or close. Args: [ScriptNode player]
        AWAIT_UI_CLOSE,
        AWAIT_UI_INPUT,
        AWAIT_UI_DROP,
        AWAIT_POSITION,
        AWAIT_INVENTORY,
        AWAIT_CLICK_BLOCK,
        AWAIT_BREAK_BLOCK,
        AWAIT_PLACE_BLOCK,
        AWAIT_OPEN_CHEST,
        AWAIT_OPEN_DOOR,
        AWAIT_USE_BLOCK,
        AWAIT_CHAT,
        AWAIT_UI_TOUCH,
        /** await hitboxTouch(player, [scene], [triggerId]) */
        AWAIT_HITBOX_TOUCH,
        /** await hitboxExit(player, [scene], [triggerId]) */
        AWAIT_HITBOX_EXIT,
        AWAIT_PLAYER_ACTION,
        AWAIT_DIMENSION,
        AWAIT_JOIN,
        AWAIT_LEAVE,
        UI_WIDGET, // Emit a widget into the current UI builder context. Args: [String kind, List<ScriptNode> args, Map<String,ScriptNode> props, Map<String,List<Instruction>> eventHandlers, List<Instruction> childBody (nullable)]
        FADE_IN,   // Fade screen. Args: [Map<String,ScriptNode> props, Boolean fadeOut]
        CAMERA,    // Camera block. Args: [String cameraId, Map<String,List<ScriptNode>> props]
        AWAIT_CAMERA_ROUTE, // await cameraRoute(player, route, [lock, hideGui, returnSmooth])
        INCLUDE,   // Include another script's functions/handlers. Args: [String scriptName]
        SET_INDEX, // Set array/map element. Args: [ScriptNode object, ScriptNode index, ScriptNode value]
        TRY_START, // Start try block. Args: [Integer catchTarget, String catchVar]
        TRY_END    // End try block. Args: none
    }

    /**
     * A single compiled instruction.
     */
    public static class Instruction {
        private final Opcode opcode;
        private final Object[] args;
        private final int line;
        private final int column;

        public Instruction(Opcode opcode, Object... args) {
            this(-1, -1, opcode, args);
        }

        public Instruction(int line, int column, Opcode opcode, Object... args) {
            this.line = line;
            this.column = column;
            this.opcode = opcode;
            this.args = args;
        }

        public int getLine() { return line; }
        public int getColumn() { return column; }

        public Opcode getOpcode() {
            return opcode;
        }

        public Object getArg(int index) {
            return args[index];
        }

        public int getArgCount() {
            return args.length;
        }

        @Override
        public String toString() {
            return opcode + "(" + java.util.Arrays.toString(args) + ")";
        }
    }

    private final String name;
    private final List<Instruction> instructions;
    private final boolean autorunSelf;
    private final List<String> autorunTargets;

    public CompiledScript(String name, List<Instruction> instructions) {
        this(name, instructions, false, List.of());
    }

    public CompiledScript(String name, List<Instruction> instructions, boolean autorunSelf, List<String> autorunTargets) {
        this.name = name;
        this.instructions = Collections.unmodifiableList(new ArrayList<>(instructions));
        this.autorunSelf = autorunSelf;
        this.autorunTargets = autorunTargets != null ? List.copyOf(autorunTargets) : List.of();
    }

    public String getName() {
        return name;
    }

    public List<Instruction> getInstructions() {
        return instructions;
    }

    public boolean isAutorunSelf() {
        return autorunSelf;
    }

    public List<String> getAutorunTargets() {
        return autorunTargets;
    }

    @Override
    public String toString() {
        return "CompiledScript{'" + name + "', " + instructions.size() + " instructions}";
    }
}
