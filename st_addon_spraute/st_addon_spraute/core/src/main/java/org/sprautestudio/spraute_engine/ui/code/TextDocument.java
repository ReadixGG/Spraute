package org.sprautestudio.spraute_engine.ui.code;

import java.util.ArrayList;
import java.util.List;

/** Plain-text document with line index and grouped undo/redo. Positions are char offsets. */
public final class TextDocument {
    public record Edit(int offset, String removed, String inserted, int caretBefore, int anchorBefore,
                       int caretAfter, int anchorAfter, long time, int kind) {}

    public static final int KIND_OTHER = 0, KIND_TYPE = 1, KIND_DELETE = 2;

    private final StringBuilder text = new StringBuilder();
    private int[] lineStarts = {0};
    private final List<Edit> undo = new ArrayList<>();
    private final List<Edit> redo = new ArrayList<>();
    private int version;
    private int savedVersion;
    /** Undo groups are split when this is called (e.g. caret moved by mouse). */
    private boolean breakGroup;
    private final List<Integer> groupStarts = new ArrayList<>();

    public TextDocument(String initial) {
        text.append(normalize(initial));
        reindex();
    }

    public static String normalize(String s) {
        return s.replace("\r\n", "\n").replace('\r', '\n').replace("\t", "    ");
    }

    private void reindex() {
        List<Integer> starts = new ArrayList<>();
        starts.add(0);
        for (int i = 0; i < text.length(); i++) if (text.charAt(i) == '\n') starts.add(i + 1);
        lineStarts = new int[starts.size()];
        for (int i = 0; i < lineStarts.length; i++) lineStarts[i] = starts.get(i);
    }

    // ------------------------------------------------------------------ queries

    public String text() {
        return text.toString();
    }

    public int length() {
        return text.length();
    }

    public char charAt(int i) {
        return text.charAt(i);
    }

    public String substring(int from, int to) {
        return text.substring(Math.max(0, from), Math.min(text.length(), to));
    }

    public int lineCount() {
        return lineStarts.length;
    }

    public int lineStart(int line) {
        return lineStarts[Math.max(0, Math.min(lineStarts.length - 1, line))];
    }

    public int lineEnd(int line) {
        return line + 1 < lineStarts.length ? lineStarts[line + 1] - 1 : text.length();
    }

    public String line(int line) {
        return text.substring(lineStart(line), lineEnd(line));
    }

    public int lineOf(int offset) {
        int lo = 0, hi = lineStarts.length - 1;
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (lineStarts[mid] <= offset) lo = mid;
            else hi = mid - 1;
        }
        return lo;
    }

    public int colOf(int offset) {
        return offset - lineStart(lineOf(offset));
    }

    public int offsetOf(int line, int col) {
        line = Math.max(0, Math.min(lineCount() - 1, line));
        return Math.min(lineStart(line) + Math.max(0, col), lineEnd(line));
    }

    public int version() {
        return version;
    }

    public boolean isDirty() {
        return version != savedVersion;
    }

    public void markSaved() {
        savedVersion = version;
    }

    // ------------------------------------------------------------------ editing

    /**
     * Replaces [from, to) with {@code inserted}. Caret/anchor values are stored for undo.
     */
    public void replace(int from, int to, String inserted, int caretBefore, int anchorBefore,
                        int caretAfter, int anchorAfter, int kind) {
        from = Math.max(0, Math.min(text.length(), from));
        to = Math.max(from, Math.min(text.length(), to));
        inserted = normalize(inserted);
        String removed = text.substring(from, to);
        if (removed.isEmpty() && inserted.isEmpty()) return;
        text.replace(from, to, inserted);
        reindex();
        version++;
        long now = System.currentTimeMillis();
        Edit e = new Edit(from, removed, inserted, caretBefore, anchorBefore, caretAfter, anchorAfter, now, kind);
        boolean merge = false;
        if (!breakGroup && !undo.isEmpty() && kind != KIND_OTHER) {
            Edit last = undo.get(undo.size() - 1);
            boolean sameKind = last.kind == kind && now - last.time < 1200;
            boolean contiguous = kind == KIND_TYPE
                    ? last.offset + last.inserted.length() == from && !inserted.contains("\n")
                    : (from + removed.length() == last.offset || from == last.offset);
            boolean wordBoundary = kind == KIND_TYPE && inserted.equals(" ") && !last.inserted.endsWith(" ");
            merge = sameKind && contiguous && !wordBoundary;
        }
        if (!merge) groupStarts.add(undo.size());
        undo.add(e);
        redo.clear();
        breakGroup = false;
    }

    public void breakUndoGroup() {
        breakGroup = true;
    }

    public boolean canUndo() {
        return !undo.isEmpty();
    }

    public boolean canRedo() {
        return !redo.isEmpty();
    }

    /** Undoes the last group; returns {caret, anchor} to restore or null. */
    public int[] undo() {
        if (undo.isEmpty()) return null;
        int start = groupStarts.isEmpty() ? 0 : groupStarts.remove(groupStarts.size() - 1);
        int[] sel = null;
        List<Edit> group = new ArrayList<>();
        for (int i = undo.size() - 1; i >= start; i--) {
            Edit e = undo.remove(i);
            text.replace(e.offset, e.offset + e.inserted.length(), e.removed);
            group.add(0, e);
            sel = new int[]{e.caretBefore, e.anchorBefore};
        }
        redo.addAll(group);
        redo.add(null); // group separator
        reindex();
        version++;
        breakGroup = true;
        return sel;
    }

    public int[] redo() {
        if (redo.isEmpty()) return null;
        redo.remove(redo.size() - 1); // separator
        int start = redo.size();
        while (start > 0 && redo.get(start - 1) != null) start--;
        int[] sel = null;
        groupStarts.add(undo.size());
        List<Edit> group = new ArrayList<>(redo.subList(start, redo.size()));
        redo.subList(start, redo.size()).clear();
        for (Edit e : group) {
            text.replace(e.offset, e.offset + e.removed.length(), e.inserted);
            undo.add(e);
            sel = new int[]{e.caretAfter, e.anchorAfter};
        }
        reindex();
        version++;
        breakGroup = true;
        return sel;
    }

    /** Replaces the whole text as one undoable edit. */
    public void setText(String s, int caret) {
        replace(0, text.length(), s, caret, caret, Math.min(caret, normalize(s).length()), Math.min(caret, normalize(s).length()), KIND_OTHER);
    }
}
