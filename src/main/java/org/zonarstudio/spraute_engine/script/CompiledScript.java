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
        UI_BLOCK,    // create ui: Args: [variableName, Map<String, ScriptNode> rootProps, List<Instruction> bodyInstructions]
        SET_PROPERTY, // Set property. Args: [objectId, propName, ScriptNode value]
        AWAIT_TIME,  // Wait for time. Args: [ScriptNode seconds]
        AWAIT_INTERACT, // Wait for interaction. Args: [ScriptNode entityId]
        AWAIT_NEXT,  // Wait for dialogue continuation keybind. Args: none
        CALL_METHOD, // Call method on object. Args: [objName, methodName, List<ScriptNode> args]
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
        AWAIT_PICKUP, // Wait for NPC to pick up item. Args: [ScriptNode npcId, ScriptNode amount, ScriptNode itemId, ScriptNode nbt?]
        ASYNC_START,  // Start async block. Args: [String taskId?, List<Instruction> bodyInstructions]
        AWAIT_TASK,   // Wait for named task to finish. Args: [ScriptNode taskId]
        STOP_TASK,    // Interrupt a named task. Args: [ScriptNode taskId]
        AWAIT_UI_CLICK, // Wait for UI button or close. Args: [ScriptNode player]
        AWAIT_UI_CLOSE,
        AWAIT_UI_INPUT,
        AWAIT_POSITION,
        AWAIT_INVENTORY,
        AWAIT_CLICK_BLOCK,
        AWAIT_BREAK_BLOCK,
        AWAIT_PLACE_BLOCK,
        AWAIT_CHAT,
        UI_WIDGET, // Emit a widget into the current UI builder context. Args: [String kind, List<ScriptNode> args, Map<String,ScriptNode> props, Map<String,List<Instruction>> eventHandlers, List<Instruction> childBody (nullable)]
        INCLUDE,   // Include another script's functions/handlers. Args: [String scriptName]
        SET_INDEX  // Set array/map element. Args: [ScriptNode object, ScriptNode index, ScriptNode value]
    }

    /**
     * A single compiled instruction.
     */
    public static class Instruction {
        private final Opcode opcode;
        private final Object[] args;

        public Instruction(Opcode opcode, Object... args) {
            this.opcode = opcode;
            this.args = args;
        }

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

    public CompiledScript(String name, List<Instruction> instructions) {
        this.name = name;
        this.instructions = Collections.unmodifiableList(new ArrayList<>(instructions));
    }

    public String getName() {
        return name;
    }

    public List<Instruction> getInstructions() {
        return instructions;
    }

    @Override
    public String toString() {
        return "CompiledScript{'" + name + "', " + instructions.size() + " instructions}";
    }
}
