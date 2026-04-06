package org.zonarstudio.spraute_engine.script;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Compiler that converts an AST (list of ScriptNodes) into a CompiledScript.
 */
public class ScriptCompiler {

    /**
     * Compile a list of AST nodes into a CompiledScript.
     *
     * @param name  the script name (usually filename without extension)
     * @param nodes the parsed AST nodes
     * @return compiled script ready for execution
     */
    public CompiledScript compile(String name, List<ScriptNode> nodes) {
        List<CompiledScript.Instruction> instructions = new ArrayList<>();

        for (ScriptNode node : nodes) {
            compileNode(node, instructions);
        }

        return new CompiledScript(name, instructions);
    }

    private void compileNode(ScriptNode node, List<CompiledScript.Instruction> instructions) {
        if (node instanceof ScriptNode.VariableDeclarationNode varNode) {
            instructions.add(new CompiledScript.Instruction(node.getLine(), node.getColumn(), CompiledScript.Opcode.VAR_DECL,
                    varNode.getName(), varNode.getInitializer(), varNode.getScope()
            ));
        } else if (node instanceof ScriptNode.VariableAssignmentNode assignNode) {
            instructions.add(new CompiledScript.Instruction(node.getLine(), node.getColumn(), CompiledScript.Opcode.VAR_ASSIGN,
                    assignNode.getName(), assignNode.getValue()
            ));
        } else if (node instanceof ScriptNode.FunctionCallNode callNode) {
            if (callNode.getFunctionName().equals("stopTask")) {
                if (callNode.getArgs().isEmpty()) throw new ScriptException("stop_task() requires task id");
                instructions.add(new CompiledScript.Instruction(node.getLine(), node.getColumn(), CompiledScript.Opcode.STOP_TASK, callNode.getArgs().get(0)));
            } else {
                instructions.add(new CompiledScript.Instruction(node.getLine(), node.getColumn(), CompiledScript.Opcode.CALL,
                        callNode.getFunctionName(), callNode.getArgs()
                ));
            }
        } else if (node instanceof ScriptNode.NpcBlockNode blockNode) {
            instructions.add(new CompiledScript.Instruction(node.getLine(), node.getColumn(), CompiledScript.Opcode.NPC_BLOCK,
                    blockNode.getEntityId(), blockNode.getProperties()
            ));
        } else if (node instanceof ScriptNode.UiBlockNode uiBlock) {
            List<CompiledScript.Instruction> bodyInstructions = new ArrayList<>();
            for (ScriptNode stmt : uiBlock.getBodyStatements()) {
                compileNode(stmt, bodyInstructions);
            }
            instructions.add(new CompiledScript.Instruction(node.getLine(), node.getColumn(), CompiledScript.Opcode.UI_BLOCK,
                    uiBlock.getVariableName(), uiBlock.getRootProps(), bodyInstructions
            ));
        } else if (node instanceof ScriptNode.CommandDefNode cmdDef) {
            List<CompiledScript.Instruction> bodyInstructions = new ArrayList<>();
            compileNode(cmdDef.getBody(), bodyInstructions);
            instructions.add(new CompiledScript.Instruction(node.getLine(), node.getColumn(), CompiledScript.Opcode.COMMAND_BLOCK,
                    cmdDef.getCommandName(), bodyInstructions
            ));
        } else if (node instanceof ScriptNode.PropertyAssignmentNode propNode) {
            instructions.add(new CompiledScript.Instruction(node.getLine(), node.getColumn(), CompiledScript.Opcode.SET_PROPERTY,
                    propNode.getObject(), propNode.getPropertyName(), propNode.getValue()
            ));
        } else if (node instanceof ScriptNode.AwaitNode awaitNode) {
            String func = awaitNode.getCall().getFunctionName();
            List<ScriptNode> args = awaitNode.getCall().getArgs();

            if (func.equals("time")) {
                if (args.isEmpty()) throw new ScriptException("await time() requires seconds");
                instructions.add(new CompiledScript.Instruction(node.getLine(), node.getColumn(), CompiledScript.Opcode.AWAIT_TIME, args.get(0)));
            } else if (func.equals("interact")) {
                if (args.isEmpty()) throw new ScriptException("await interact() requires entity id");
                instructions.add(new CompiledScript.Instruction(node.getLine(), node.getColumn(), CompiledScript.Opcode.AWAIT_INTERACT, args.get(0)));
            } else if (func.equals("next")) {
                instructions.add(new CompiledScript.Instruction(node.getLine(), node.getColumn(), CompiledScript.Opcode.AWAIT_NEXT));
            } else if (func.equals("keybind")) {
                if (args.isEmpty()) throw new ScriptException("await keybind() requires key name");
                instructions.add(new CompiledScript.Instruction(node.getLine(), node.getColumn(), CompiledScript.Opcode.AWAIT_KEYBIND, args.get(0)));
            } else if (func.equals("death")) {
                if (args.isEmpty()) throw new ScriptException("await death() requires entity id");
                instructions.add(new CompiledScript.Instruction(node.getLine(), node.getColumn(), CompiledScript.Opcode.AWAIT_DEATH, args.get(0)));
            } else if (func.equals("pickup")) {
                if (args.size() < 3) throw new ScriptException("await pickup(npc_id, amount, item_id, nbt?) requires at least npc_id, amount, item_id");
                instructions.add(new CompiledScript.Instruction(node.getLine(), node.getColumn(), CompiledScript.Opcode.AWAIT_PICKUP,
                        args.get(0), args.get(1), args.get(2), args.size() >= 4 ? args.get(3) : null));
            } else if (func.equals("orbPickup") || func.equals("orb_pickup")) {
                if (args.size() < 2) throw new ScriptException("await orbPickup(player, amount, texture?) requires at least player and amount");
                instructions.add(new CompiledScript.Instruction(node.getLine(), node.getColumn(), CompiledScript.Opcode.AWAIT_ORB_PICKUP,
                        args.get(0), args.get(1), args.size() >= 3 ? args.get(2) : null));
            } else if (func.equals("task")) {
                if (args.isEmpty()) throw new ScriptException("await task() requires task id");
                instructions.add(new CompiledScript.Instruction(node.getLine(), node.getColumn(), CompiledScript.Opcode.AWAIT_TASK, args.get(0)));
            } else if (func.equals("uiClick")) {
                if (args.isEmpty()) throw new ScriptException("await uiClick(player) requires player");
                instructions.add(new CompiledScript.Instruction(node.getLine(), node.getColumn(), CompiledScript.Opcode.AWAIT_UI_CLICK, args.get(0)));
            } else if (func.equals("uiClose")) {
                if (args.isEmpty()) throw new ScriptException("await uiClose(player) requires player");
                instructions.add(new CompiledScript.Instruction(node.getLine(), node.getColumn(), CompiledScript.Opcode.AWAIT_UI_CLOSE, args.get(0)));
            } else if (func.equals("uiInput")) {
                if (args.isEmpty()) throw new ScriptException("await uiInput(player, [widget_id]) requires player");
                instructions.add(new CompiledScript.Instruction(node.getLine(), node.getColumn(), CompiledScript.Opcode.AWAIT_UI_INPUT, args.get(0), args.size() > 1 ? args.get(1) : null));
            } else if (func.equals("position")) {
                if (args.size() < 4) throw new ScriptException("await position(player, x, y, z, [radius]) requires at least 4 arguments");
                instructions.add(new CompiledScript.Instruction(node.getLine(), node.getColumn(), CompiledScript.Opcode.AWAIT_POSITION, args.get(0), args.get(1), args.get(2), args.get(3), args.size() > 4 ? args.get(4) : null));
            } else if (func.equals("inventory") || func.equals("hasItem")) {
                if (args.size() < 2) throw new ScriptException("await inventory(player, item_id, [count]) requires player and item_id");
                instructions.add(new CompiledScript.Instruction(node.getLine(), node.getColumn(), CompiledScript.Opcode.AWAIT_INVENTORY, args.get(0), args.get(1), args.size() > 2 ? args.get(2) : null));
            } else if (func.equals("clickBlock")) {
                if (args.isEmpty()) throw new ScriptException("await clickBlock(player, [block_id/coords]) requires player");
                instructions.add(new CompiledScript.Instruction(node.getLine(), node.getColumn(), CompiledScript.Opcode.AWAIT_CLICK_BLOCK, args));
            } else if (func.equals("breakBlock")) {
                if (args.isEmpty()) throw new ScriptException("await breakBlock(player, [block_id/coords]) requires player");
                instructions.add(new CompiledScript.Instruction(node.getLine(), node.getColumn(), CompiledScript.Opcode.AWAIT_BREAK_BLOCK, args));
            } else if (func.equals("placeBlock")) {
                if (args.isEmpty()) throw new ScriptException("await placeBlock(player, [block_id/coords]) requires player");
                instructions.add(new CompiledScript.Instruction(node.getLine(), node.getColumn(), CompiledScript.Opcode.AWAIT_PLACE_BLOCK, args));
            } else if (func.equals("chat")) {
                if (args.size() < 2) throw new ScriptException("await chat(player, message, [ignore_case], [ignore_punctuation]) requires player and message");
                instructions.add(new CompiledScript.Instruction(node.getLine(), node.getColumn(), CompiledScript.Opcode.AWAIT_CHAT, args));
            } else if (func.equals("uiTouch") || func.equals("uiOverlap")) {
                if (args.size() < 3) throw new ScriptException("await uiTouch(player, id1, id2) requires player, id1, id2");
                instructions.add(new CompiledScript.Instruction(node.getLine(), node.getColumn(), CompiledScript.Opcode.AWAIT_UI_TOUCH, args.get(0), args.get(1), args.get(2)));
            } else if (func.equals("action") || func.equals("playerAction")) {
                if (args.size() < 2) throw new ScriptException("await action(player, action_type, [target]) requires player and action_type");
                instructions.add(new CompiledScript.Instruction(node.getLine(), node.getColumn(), CompiledScript.Opcode.AWAIT_PLAYER_ACTION, args.get(0), args.get(1), args.size() > 2 ? args.get(2) : null));
            } else {
                throw new ScriptException("Unknown await trigger: " + func);
            }
        } else if (node instanceof ScriptNode.MethodCallNode methodNode) {
            instructions.add(new CompiledScript.Instruction(node.getLine(), node.getColumn(), CompiledScript.Opcode.CALL_METHOD,
                    methodNode.getObject(), methodNode.getMethodName(), methodNode.getArgs()
            ));
        } else if (node instanceof ScriptNode.PropertyAccessNode || node instanceof ScriptNode.IndexAccessNode) {
            // Stand-alone access as a statement is a no-op
        } else if (node instanceof ScriptNode.IndexAssignmentNode idxNode) {
            instructions.add(new CompiledScript.Instruction(node.getLine(), node.getColumn(), CompiledScript.Opcode.SET_INDEX,
                    idxNode.getObject(), idxNode.getIndex(), idxNode.getValue()
            ));
        } else if (node instanceof ScriptNode.BlockNode blockNode) {
            for (ScriptNode stmt : blockNode.getStatements()) {
                compileNode(stmt, instructions);
            }
        } else if (node instanceof ScriptNode.WhileNode whileNode) {
            int startIndex = instructions.size();

            int jumpIfFalseIdx = instructions.size();
            instructions.add(new CompiledScript.Instruction(node.getLine(), node.getColumn(), CompiledScript.Opcode.JUMP_IF_FALSE, whileNode.getCondition(), -1));

            compileNode(whileNode.getBody(), instructions);

            instructions.add(new CompiledScript.Instruction(node.getLine(), node.getColumn(), CompiledScript.Opcode.JUMP, startIndex));

            int endIndex = instructions.size();
            instructions.set(jumpIfFalseIdx, new CompiledScript.Instruction(node.getLine(), node.getColumn(), CompiledScript.Opcode.JUMP_IF_FALSE, whileNode.getCondition(), endIndex));

        } else if (node instanceof ScriptNode.IfNode ifNode) {
            compileIf(ifNode, instructions);

        } else if (node instanceof ScriptNode.ForNode forNode) {
            compileFor(forNode, instructions);
        } else if (node instanceof ScriptNode.FunctionDefNode funNode) {
            List<CompiledScript.Instruction> bodyInstructions = new ArrayList<>();
            compileNode(funNode.getBody(), bodyInstructions);
            instructions.add(new CompiledScript.Instruction(node.getLine(), node.getColumn(), CompiledScript.Opcode.FUN_DEF,
                    funNode.getName(), funNode.getParams(), bodyInstructions
            ));
        } else if (node instanceof ScriptNode.ReturnNode returnNode) {
            instructions.add(new CompiledScript.Instruction(node.getLine(), node.getColumn(), CompiledScript.Opcode.RETURN,
                    returnNode.getValue()
            ));
        } else if (node instanceof ScriptNode.OnNode onNode) {
            List<CompiledScript.Instruction> bodyInstructions = new ArrayList<>();
            compileNode(onNode.getBody(), bodyInstructions);
            instructions.add(new CompiledScript.Instruction(node.getLine(), node.getColumn(), CompiledScript.Opcode.REGISTER_ON,
                    onNode.getEventName(), onNode.getEventArgs(), onNode.getHandlerId(), bodyInstructions
            ));
        } else if (node instanceof ScriptNode.EveryNode everyNode) {
            List<CompiledScript.Instruction> bodyInstructions = new ArrayList<>();
            compileNode(everyNode.getBody(), bodyInstructions);
            instructions.add(new CompiledScript.Instruction(node.getLine(), node.getColumn(), CompiledScript.Opcode.REGISTER_EVERY,
                    everyNode.getInterval(), everyNode.getHandlerId(), bodyInstructions
            ));
        } else if (node instanceof ScriptNode.StopNode stopNode) {
            instructions.add(new CompiledScript.Instruction(node.getLine(), node.getColumn(), CompiledScript.Opcode.STOP_HANDLER,
                    stopNode.getHandlerId()
            ));
        } else if (node instanceof ScriptNode.AsyncNode asyncNode) {
            List<CompiledScript.Instruction> bodyInstructions = new ArrayList<>();
            compileNode(asyncNode.getBody(), bodyInstructions);
            instructions.add(new CompiledScript.Instruction(node.getLine(), node.getColumn(), CompiledScript.Opcode.ASYNC_START,
                    asyncNode.getTaskId(),
                    bodyInstructions
            ));
        } else if (node instanceof ScriptNode.UiWidgetNode widgetNode) {
            if ("fadeIn".equals(widgetNode.getKind())) {
                instructions.add(new CompiledScript.Instruction(node.getLine(), node.getColumn(), CompiledScript.Opcode.FADE_IN, widgetNode.getProps()));
                return;
            }
            Map<String, List<CompiledScript.Instruction>> eventHandlers = new HashMap<>();
            for (Map.Entry<String, ScriptNode> e : widgetNode.getEvents().entrySet()) {
                if (!"onClick".equals(e.getKey())) continue;
                ScriptNode body = e.getValue();
                if (!(body instanceof ScriptNode.BlockNode bn)) continue;
                List<CompiledScript.Instruction> ins = new ArrayList<>();
                for (ScriptNode stmt : bn.getStatements()) {
                    compileNode(stmt, ins);
                }
                eventHandlers.put("onClick", ins);
            }
            List<CompiledScript.Instruction> childBody = null;
            if (!widgetNode.getChildren().isEmpty()) {
                childBody = new ArrayList<>();
                for (ScriptNode child : widgetNode.getChildren()) {
                    compileNode(child, childBody);
                }
            }
            instructions.add(new CompiledScript.Instruction(node.getLine(), node.getColumn(), CompiledScript.Opcode.UI_WIDGET,
                    widgetNode.getKind(), widgetNode.getArgs(), widgetNode.getProps(),
                    eventHandlers, childBody
            ));
        } else if (node instanceof ScriptNode.IncludeNode includeNode) {
            instructions.add(new CompiledScript.Instruction(node.getLine(), node.getColumn(), CompiledScript.Opcode.INCLUDE,
                    includeNode.getScriptName()
            ));
        }
    }

    private void compileIf(ScriptNode.IfNode ifNode, List<CompiledScript.Instruction> instructions) {
        List<ScriptNode> conditions = ifNode.getConditions();
        List<ScriptNode> bodies = ifNode.getBodies();
        List<Integer> jumpToEndIndexes = new ArrayList<>();

        for (int i = 0; i < conditions.size(); i++) {
            ScriptNode condition = conditions.get(i);

            if (condition != null) {
                int jumpIfFalseIdx = instructions.size();
                instructions.add(new CompiledScript.Instruction(ifNode.getLine(), ifNode.getColumn(), CompiledScript.Opcode.JUMP_IF_FALSE, condition, -1));

                compileNode(bodies.get(i), instructions);

                int jumpToEndIdx = instructions.size();
                instructions.add(new CompiledScript.Instruction(ifNode.getLine(), ifNode.getColumn(), CompiledScript.Opcode.JUMP, -1));
                jumpToEndIndexes.add(jumpToEndIdx);

                int nextBranchStart = instructions.size();
                instructions.set(jumpIfFalseIdx, new CompiledScript.Instruction(ifNode.getLine(), ifNode.getColumn(), CompiledScript.Opcode.JUMP_IF_FALSE, condition, nextBranchStart));
            } else {
                // 'else' branch — no condition
                compileNode(bodies.get(i), instructions);
            }
        }

        int endIndex = instructions.size();
        for (int idx : jumpToEndIndexes) {
            CompiledScript.Instruction old = instructions.get(idx);
            instructions.set(idx, new CompiledScript.Instruction(ifNode.getLine(), ifNode.getColumn(), CompiledScript.Opcode.JUMP, endIndex));
        }
    }

    private void compileFor(ScriptNode.ForNode forNode, List<CompiledScript.Instruction> instructions) {
        // for (i in range(start, end)) { body }
        // Compiled as:
        //   VAR_DECL i = start
        //   JUMP_IF_FALSE (i < end) -> afterLoop
        //   ... body ...
        //   VAR_ASSIGN i = i + 1
        //   JUMP -> condition
        //
        // The iterable expression must be a range() call to get start/end values.
        // We store the range params as nodes and evaluate at runtime.

        ScriptNode iterable = forNode.getIterable();

        if (iterable instanceof ScriptNode.FunctionCallNode rangeCall && rangeCall.getFunctionName().equals("range")) {
            List<ScriptNode> rangeArgs = rangeCall.getArgs();
            ScriptNode startNode;
            ScriptNode endNode;
            if (rangeArgs.size() == 1) {
                startNode = new ScriptNode.LiteralNode(0);
                endNode = rangeArgs.get(0);
            } else if (rangeArgs.size() >= 2) {
                startNode = rangeArgs.get(0);
                endNode = rangeArgs.get(1);
            } else {
                throw new ScriptException("range() requires at least 1 argument");
            }

            String varName = forNode.getVariableName();

            // VAR_DECL i = start
            instructions.add(new CompiledScript.Instruction(forNode.getLine(), forNode.getColumn(), CompiledScript.Opcode.VAR_DECL, varName, startNode));

            int conditionIndex = instructions.size();

            // JUMP_IF_FALSE (i < end) -> afterLoop (placeholder)
            ScriptNode condition = new ScriptNode.BinaryExpressionNode(
                    new ScriptNode.IdentifierNode(varName),
                    new ScriptToken(ScriptToken.TokenType.LT, "<", -1, -1),
                    endNode
            );
            int jumpIfFalseIdx = instructions.size();
            instructions.add(new CompiledScript.Instruction(forNode.getLine(), forNode.getColumn(), CompiledScript.Opcode.JUMP_IF_FALSE, condition, -1));

            // body
            compileNode(forNode.getBody(), instructions);

            // VAR_ASSIGN i = i + 1
            ScriptNode increment = new ScriptNode.BinaryExpressionNode(
                    new ScriptNode.IdentifierNode(varName),
                    new ScriptToken(ScriptToken.TokenType.PLUS, "+", -1, -1),
                    new ScriptNode.LiteralNode(1)
            );
            instructions.add(new CompiledScript.Instruction(forNode.getLine(), forNode.getColumn(), CompiledScript.Opcode.VAR_ASSIGN, varName, increment));

            // JUMP -> condition
            instructions.add(new CompiledScript.Instruction(forNode.getLine(), forNode.getColumn(), CompiledScript.Opcode.JUMP, conditionIndex));

            // Fix up
            int endIndex = instructions.size();
            instructions.set(jumpIfFalseIdx, new CompiledScript.Instruction(forNode.getLine(), forNode.getColumn(), CompiledScript.Opcode.JUMP_IF_FALSE, condition, endIndex));
        } else {
            throw new ScriptException("for loops currently only support range() as iterable, e.g.: for (i in range(10)) { ... }");
        }
    }

}
