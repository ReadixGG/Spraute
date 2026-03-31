package org.zonarstudio.spraute_engine.ui;

import org.zonarstudio.spraute_engine.script.CompiledScript;
import org.zonarstudio.spraute_engine.script.ScriptNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A widget built at runtime inside a dynamic {@code create ui} block.
 * Holds evaluated args/props and compiled on_click handlers.
 */
public final class RuntimeWidget {
    public final String kind;
    public final List<Object> evaluatedArgs;
    public final Map<String, Object> evaluatedProps;
    public final Map<String, List<CompiledScript.Instruction>> eventHandlers;
    public final List<RuntimeWidget> children;

    public RuntimeWidget(String kind, List<Object> evaluatedArgs, Map<String, Object> evaluatedProps,
                         Map<String, List<CompiledScript.Instruction>> eventHandlers) {
        this(kind, evaluatedArgs, evaluatedProps, eventHandlers, new ArrayList<>());
    }

    public RuntimeWidget(String kind, List<Object> evaluatedArgs, Map<String, Object> evaluatedProps,
                         Map<String, List<CompiledScript.Instruction>> eventHandlers,
                         List<RuntimeWidget> children) {
        this.kind = kind;
        this.evaluatedArgs = evaluatedArgs;
        this.evaluatedProps = evaluatedProps;
        this.eventHandlers = eventHandlers;
        this.children = children;
    }
}
