package org.zonarstudio.spraute_engine.script;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Shared script output buffer — used by the runtime ({@code chat}/{@code log}, compile/runtime
 * errors) and the in-game editor «Консоль» window. Safe to call from server and client threads.
 */
public final class ScriptConsole {
    public enum Level {
        INFO, WARN, ERROR, SCRIPT
    }

    public record Entry(long timeMs, Level level, String source, String message) {}

    private static final int MAX_ENTRIES = 2000;
    private static final CopyOnWriteArrayList<Entry> ENTRIES = new CopyOnWriteArrayList<>();
    private static final AtomicInteger VERSION = new AtomicInteger();
    private static final AtomicBoolean SCROLL_TO_BOTTOM = new AtomicBoolean(true);

    private ScriptConsole() {}

    public static void info(String source, String message) {
        append(Level.INFO, source, message);
    }

    public static void warn(String source, String message) {
        append(Level.WARN, source, message);
    }

    public static void error(String source, String message) {
        append(Level.ERROR, source, message);
    }

    /** Output produced by script APIs such as {@code chat(...)} / {@code log(...)}. */
    public static void script(String source, String message) {
        append(Level.SCRIPT, source, message);
    }

    public static void compileError(String scriptName, String detail) {
        error("compile", "Ошибка компиляции '" + scriptName + "': " + detail);
    }

    public static void runtimeError(String scriptName, int line, String detail) {
        String where = line >= 0 ? (" (строка " + line + ")") : "";
        error(scriptName != null ? scriptName : "script", "Ошибка выполнения" + where + ": " + detail);
    }

    public static void clear() {
        ENTRIES.clear();
        VERSION.incrementAndGet();
        SCROLL_TO_BOTTOM.set(true);
    }

    public static int version() {
        return VERSION.get();
    }

    public static List<Entry> snapshot() {
        return new ArrayList<>(ENTRIES);
    }

    public static boolean consumeScrollToBottom() {
        return SCROLL_TO_BOTTOM.getAndSet(false);
    }

    public static void requestScrollToBottom() {
        SCROLL_TO_BOTTOM.set(true);
    }

    /** Strip Minecraft formatting codes ({@code §x}) for plain console text. */
    public static String stripFormatting(String text) {
        if (text == null || text.isEmpty()) return "";
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if ((c == '§' || c == '\u00A7') && i + 1 < text.length()) {
                i++;
                continue;
            }
            out.append(c);
        }
        return out.toString();
    }

    private static void append(Level level, String source, String message) {
        String src = source == null || source.isBlank() ? "script" : source.trim();
        String msg = stripFormatting(message == null ? "" : message);
        ENTRIES.add(new Entry(System.currentTimeMillis(), level, src, msg));
        while (ENTRIES.size() > MAX_ENTRIES) {
            ENTRIES.remove(0);
        }
        VERSION.incrementAndGet();
        SCROLL_TO_BOTTOM.set(true);
    }
}
