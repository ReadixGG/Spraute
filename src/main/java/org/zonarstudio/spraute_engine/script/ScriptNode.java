package org.zonarstudio.spraute_engine.script;

/**
 * Base interface for all AST nodes in the script language.
 */
public interface ScriptNode {

    /**
     * A generic function call node, e.g., chat("hello").
     */
    class FunctionCallNode implements ScriptNode {
        private final String functionName;
        private final java.util.List<ScriptNode> args;

        public FunctionCallNode(String functionName, java.util.List<ScriptNode> args) {
            this.functionName = functionName;
            this.args = args;
        }

        public String getFunctionName() {
            return functionName;
        }

        public java.util.List<ScriptNode> getArgs() {
            return args;
        }
    }

    /**
     * A variable declaration: val / global val / world val <name> = <expression>
     * scope: "local" | "global" | "world"
     */
    class VariableDeclarationNode implements ScriptNode {
        private final String name;
        private final ScriptNode initializer;
        private final String scope;

        public VariableDeclarationNode(String name, ScriptNode initializer) {
            this(name, initializer, "local");
        }

        public VariableDeclarationNode(String name, ScriptNode initializer, String scope) {
            this.name = name;
            this.initializer = initializer;
            this.scope = scope;
        }

        public String getName() { return name; }
        public ScriptNode getInitializer() { return initializer; }
        public String getScope() { return scope; }
    }

    /**
     * A binary expression: a + b
     */
    class BinaryExpressionNode implements ScriptNode {
        private final ScriptNode left;
        private final ScriptToken operator;
        private final ScriptNode right;

        public BinaryExpressionNode(ScriptNode left, ScriptToken operator, ScriptNode right) {
            this.left = left;
            this.operator = operator;
            this.right = right;
        }

        public ScriptNode getLeft() { return left; }
        public ScriptToken getOperator() { return operator; }
        public ScriptNode getRight() { return right; }
    }

    /**
     * A literal value (String or Number)
     */
    class LiteralNode implements ScriptNode {
        private final Object value;
        public LiteralNode(Object value) { this.value = value; }
        public Object getValue() { return value; }
    }

    /**
     * A variable or identifier reference
     */
    class IdentifierNode implements ScriptNode {
        private final String name;
        public IdentifierNode(String name) { this.name = name; }
        public String getName() { return name; }
    }

    /**
     * An NPC block: npc_id { ... }
     */
    class NpcBlockNode implements ScriptNode {
        private final String entityId;
        private final java.util.Map<String, java.util.List<ScriptNode>> properties;

        public NpcBlockNode(String entityId, java.util.Map<String, java.util.List<ScriptNode>> properties) {
            this.entityId = entityId;
            this.properties = properties;
        }

        public String getEntityId() { return entityId; }
        public java.util.Map<String, java.util.List<ScriptNode>> getProperties() { return properties; }
    }

    /**
     * Set a property on an object: id.property = expression
     */
    class PropertyAssignmentNode implements ScriptNode {
        private final String objectName;
        private final String propertyName;
        private final ScriptNode value;

        public PropertyAssignmentNode(String objectName, String propertyName, ScriptNode value) {
            this.objectName = objectName;
            this.propertyName = propertyName;
            this.value = value;
        }

        public String getObjectName() { return objectName; }
        public String getPropertyName() { return propertyName; }
        public ScriptNode getValue() { return value; }
    }

    /**
     * An await statement: await function_call
     */
    class AwaitNode implements ScriptNode {
        private final ScriptNode.FunctionCallNode call;

        public AwaitNode(ScriptNode.FunctionCallNode call) {
            this.call = call;
        }

        public ScriptNode.FunctionCallNode getCall() { return call; }
    }

    /**
     * A method call on an object: object.method(args)
     */
    class MethodCallNode implements ScriptNode {
        private final String objectName;
        private final String methodName;
        private final java.util.List<ScriptNode> args;

        public MethodCallNode(String objectName, String methodName, java.util.List<ScriptNode> args) {
            this.objectName = objectName;
            this.methodName = methodName;
            this.args = args;
        }

        public String getObjectName() { return objectName; }
        public String getMethodName() { return methodName; }
        public java.util.List<ScriptNode> getArgs() { return args; }
    }

    /**
     * Access a property on an object: object.property
     */
    class PropertyAccessNode implements ScriptNode {
        private final String objectName;
        private final String propertyName;

        public PropertyAccessNode(String objectName, String propertyName) {
            this.objectName = objectName;
            this.propertyName = propertyName;
        }

        public String getObjectName() { return objectName; }
        public String getPropertyName() { return propertyName; }
    }

    /**
     * A block of statements: { statement.. }
     */
    class BlockNode implements ScriptNode {
        private final java.util.List<ScriptNode> statements;

        public BlockNode(java.util.List<ScriptNode> statements) {
            this.statements = statements;
        }

        public java.util.List<ScriptNode> getStatements() { return statements; }
    }

    /**
     * A while loop: while (condition) body
     */
    class WhileNode implements ScriptNode {
        private final ScriptNode condition;
        private final ScriptNode body;

        public WhileNode(ScriptNode condition, ScriptNode body) {
            this.condition = condition;
            this.body = body;
        }

        public ScriptNode getCondition() { return condition; }
        public ScriptNode getBody() { return body; }
    }

    /**
     * An if/else if/else chain.
     * branches: list of (condition, body) pairs. The last may have null condition for 'else'.
     */
    class IfNode implements ScriptNode {
        private final java.util.List<ScriptNode> conditions;
        private final java.util.List<ScriptNode> bodies;

        public IfNode(java.util.List<ScriptNode> conditions, java.util.List<ScriptNode> bodies) {
            this.conditions = conditions;
            this.bodies = bodies;
        }

        public java.util.List<ScriptNode> getConditions() { return conditions; }
        public java.util.List<ScriptNode> getBodies() { return bodies; }
    }

    /**
     * A unary expression: !expr
     */
    class UnaryNotNode implements ScriptNode {
        private final ScriptNode operand;

        public UnaryNotNode(ScriptNode operand) {
            this.operand = operand;
        }

        public ScriptNode getOperand() { return operand; }
    }

    /**
     * Variable reassignment: name = expression
     */
    class VariableAssignmentNode implements ScriptNode {
        private final String name;
        private final ScriptNode value;

        public VariableAssignmentNode(String name, ScriptNode value) {
            this.name = name;
            this.value = value;
        }

        public String getName() { return name; }
        public ScriptNode getValue() { return value; }
    }

    /**
     * User-defined function: fun name(params) { body }
     */
    class FunctionDefNode implements ScriptNode {
        private final String name;
        private final java.util.List<String> params;
        private final ScriptNode body;

        public FunctionDefNode(String name, java.util.List<String> params, ScriptNode body) {
            this.name = name;
            this.params = params;
            this.body = body;
        }

        public String getName() { return name; }
        public java.util.List<String> getParams() { return params; }
        public ScriptNode getBody() { return body; }
    }

    /**
     * Return statement: return expression
     */
    class ReturnNode implements ScriptNode {
        private final ScriptNode value;

        public ReturnNode(ScriptNode value) {
            this.value = value;
        }

        public ScriptNode getValue() { return value; }
    }

    /**
     * Background event handler: on event_name(args) -> handler_id { body }
     * Runs the body every time the event fires, until stopped via stop(handler_id).
     */
    class OnNode implements ScriptNode {
        private final String eventName;
        private final java.util.List<ScriptNode> eventArgs;
        private final String handlerId;
        private final ScriptNode body;

        public OnNode(String eventName, java.util.List<ScriptNode> eventArgs, String handlerId, ScriptNode body) {
            this.eventName = eventName;
            this.eventArgs = eventArgs;
            this.handlerId = handlerId;
            this.body = body;
        }

        public String getEventName() { return eventName; }
        public java.util.List<ScriptNode> getEventArgs() { return eventArgs; }
        public String getHandlerId() { return handlerId; }
        public ScriptNode getBody() { return body; }
    }

    /**
     * Periodic timer: every(seconds) -> handler_id { body }
     * Repeats body every N seconds until stopped via stop(handler_id).
     */
    class EveryNode implements ScriptNode {
        private final ScriptNode interval;
        private final String handlerId;
        private final ScriptNode body;

        public EveryNode(ScriptNode interval, String handlerId, ScriptNode body) {
            this.interval = interval;
            this.handlerId = handlerId;
            this.body = body;
        }

        public ScriptNode getInterval() { return interval; }
        public String getHandlerId() { return handlerId; }
        public ScriptNode getBody() { return body; }
    }

    /**
     * Stop a running handler: stop(handler_id)
     */
    class StopNode implements ScriptNode {
        private final String handlerId;

        public StopNode(String handlerId) {
            this.handlerId = handlerId;
        }

        public String getHandlerId() { return handlerId; }
    }

    /**
     * Async block: async { body } or async "task_id" { body }
     * Runs body in background. Named tasks can be awaited or stopped.
     */
    class AsyncNode implements ScriptNode {
        private final String taskId;  // null = anonymous
        private final ScriptNode body;

        public AsyncNode(String taskId, ScriptNode body) {
            this.taskId = taskId;
            this.body = body;
        }

        public String getTaskId() { return taskId; }
        public ScriptNode getBody() { return body; }
    }

    /**
     * A for loop: for (variable in iterable) body
     */
    class ForNode implements ScriptNode {
        private final String variableName;
        private final ScriptNode iterable;
        private final ScriptNode body;

        public ForNode(String variableName, ScriptNode iterable, ScriptNode body) {
            this.variableName = variableName;
            this.iterable = iterable;
            this.body = body;
        }

        public String getVariableName() { return variableName; }
        public ScriptNode getIterable() { return iterable; }
        public ScriptNode getBody() { return body; }
    }

    /**
     * List literal: [ expr, expr, ... ]
     */
    class ListLiteralNode implements ScriptNode {
        private final java.util.List<ScriptNode> elements;

        public ListLiteralNode(java.util.List<ScriptNode> elements) {
            this.elements = elements;
        }

        public java.util.List<ScriptNode> getElements() {
            return elements;
        }
    }

    /**
     * create ui name { ... } — body may contain widget declarations, loops, conditions, etc.
     */
    class UiBlockNode implements ScriptNode {
        private final String variableName;
        private final java.util.Map<String, ScriptNode> rootProps;
        private final java.util.List<ScriptNode> bodyStatements;

        public UiBlockNode(String variableName, java.util.Map<String, ScriptNode> rootProps, java.util.List<ScriptNode> bodyStatements) {
            this.variableName = variableName;
            this.rootProps = rootProps;
            this.bodyStatements = bodyStatements;
        }

        public String getVariableName() {
            return variableName;
        }

        public java.util.Map<String, ScriptNode> getRootProps() {
            return rootProps;
        }

        public java.util.List<ScriptNode> getBodyStatements() {
            return bodyStatements;
        }
    }

    /**
     * Widget inside create ui: text(...) { pos = ... on_click { } }
     */
    class UiWidgetNode implements ScriptNode {
        private final String kind;
        private final java.util.List<ScriptNode> args;
        private final java.util.Map<String, ScriptNode> props;
        /** Event name (e.g. on_click) → block body */
        private final java.util.Map<String, ScriptNode> events;
        /** Nested widgets and arbitrary statements (var, while, assignments) inside scroll { } etc. */
        private final java.util.List<ScriptNode> children;

        public UiWidgetNode(String kind, java.util.List<ScriptNode> args,
                            java.util.Map<String, ScriptNode> props,
                            java.util.Map<String, ScriptNode> events) {
            this(kind, args, props, events, new java.util.ArrayList<>());
        }

        public UiWidgetNode(String kind, java.util.List<ScriptNode> args,
                            java.util.Map<String, ScriptNode> props,
                            java.util.Map<String, ScriptNode> events,
                            java.util.List<ScriptNode> children) {
            this.kind = kind;
            this.args = args;
            this.props = props;
            this.events = events;
            this.children = children;
        }

        public String getKind() {
            return kind;
        }

        public java.util.List<ScriptNode> getArgs() {
            return args;
        }

        public java.util.Map<String, ScriptNode> getProps() {
            return props;
        }

        public java.util.Map<String, ScriptNode> getEvents() {
            return events;
        }

        public java.util.List<ScriptNode> getChildren() {
            return children;
        }
    }

    class IncludeNode implements ScriptNode {
        private final String scriptName;

        public IncludeNode(String scriptName) {
            this.scriptName = scriptName;
        }

        public String getScriptName() {
            return scriptName;
        }
    }

    /**
     * Array or map element access: object[index]
     */
    class IndexAccessNode implements ScriptNode {
        private final ScriptNode object;
        private final ScriptNode index;

        public IndexAccessNode(ScriptNode object, ScriptNode index) {
            this.object = object;
            this.index = index;
        }

        public ScriptNode getObject() { return object; }
        public ScriptNode getIndex() { return index; }
    }

    /**
     * Array or map element assignment: object[index] = value
     */
    class IndexAssignmentNode implements ScriptNode {
        private final ScriptNode object;
        private final ScriptNode index;
        private final ScriptNode value;

        public IndexAssignmentNode(ScriptNode object, ScriptNode index, ScriptNode value) {
            this.object = object;
            this.index = index;
            this.value = value;
        }

        public ScriptNode getObject() { return object; }
        public ScriptNode getIndex() { return index; }
        public ScriptNode getValue() { return value; }
    }
}
