package org.zonarstudio.spraute_engine.script;

/**
 * Exception thrown during script parsing, compilation, or execution.
 */
public class ScriptException extends RuntimeException {

    private final int line;

    public ScriptException(String message, int line) {
        super("Line " + line + ": " + message);
        this.line = line;
    }

    public ScriptException(String message) {
        super(message);
        this.line = -1;
    }

    public int getScriptLine() {
        return line;
    }
}
