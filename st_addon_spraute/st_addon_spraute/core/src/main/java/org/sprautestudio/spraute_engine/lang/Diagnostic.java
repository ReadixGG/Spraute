package org.sprautestudio.spraute_engine.lang;

public final class Diagnostic {
    public enum Severity { ERROR, WARNING, INFO }

    public final Severity severity;
    public final String message;
    public final int start, end;
    public final int line, col;

    public Diagnostic(Severity severity, String message, int start, int end, int line, int col) {
        this.severity = severity;
        this.message = message;
        this.start = start;
        this.end = Math.max(end, start);
        this.line = line;
        this.col = col;
    }

    public boolean isError() {
        return severity == Severity.ERROR;
    }

    @Override
    public String toString() {
        return (line + 1) + ":" + (col + 1) + " " + severity + ": " + message;
    }
}
