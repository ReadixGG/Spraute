package org.sprautestudio.st_addon_spraute.ui.code;

import org.sprautestudio.st_addon_spraute.ui.Icon;

import java.util.List;

/** Language services used by {@link CodeEditor}. */
public interface LanguageSupport {
    enum Severity { ERROR, WARNING, INFO }

    record Problem(int start, int end, Severity severity, String message) {}

    enum Kind {
        KEYWORD(Icon.CODE, 0xFFC792EA),
        COMMAND(Icon.BOLT, 0xFF82AAFF),
        SNIPPET(Icon.EDIT, 0xFFB0B7C9),
        CHARACTER(Icon.USER, 0xFF4FD6BE),
        LOCATION(Icon.PIN, 0xFFFFCB6B),
        LABEL(Icon.TAG, 0xFFFF7EB6),
        VARIABLE(Icon.DOT, 0xFF7DCFFF),
        FUNCTION(Icon.CODE, 0xFFFFD580),
        VALUE(Icon.LIST, 0xFFF78C6C);

        public final Icon icon;
        public final int color;

        Kind(Icon icon, int color) {
            this.icon = icon;
            this.color = color;
        }
    }

    /**
     * A completion proposal. {@code insert} may contain snippet placeholders
     * {@code ${1:text}}; it replaces the text from {@code replaceStart} to the caret.
     */
    record Completion(String label, Kind kind, String detail, String insert, String doc, int replaceStart, int priority) {}

    /** Signature hint: the full signature split into parts, with the active part index. */
    record Signature(List<String> parts, int active, String doc) {}

    /** Result of analysing a document, cached by the editor until the next edit. */
    interface Analysis {
        List<Problem> problems();

        /** One ARGB color per character of the analysed text. */
        int[] colors();
    }

    Analysis analyze(String text);

    List<Completion> complete(String text, int caret, Analysis analysis, boolean explicit);

    /** Characters that open the completion popup automatically. */
    default boolean isCompletionTrigger(char typed) {
        return Character.isLetter(typed) || typed == '_';
    }

    String hover(String text, int offset, Analysis analysis);

    Signature signature(String text, int caret, Analysis analysis);

    /** Offset of the declaration of the symbol at {@code offset}, or -1. */
    int definition(String text, int offset, Analysis analysis);

    String lineComment();

    /** Pretty-printed text, or null when formatting is not supported. */
    default String format(String text) {
        return null;
    }
}
