package org.sprautestudio.st_addon_spraute.editor.tabs;

import org.sprautestudio.st_addon_spraute.editor.project.Project;
import org.sprautestudio.st_addon_spraute.lang.Analyzer;
import org.sprautestudio.st_addon_spraute.ui.Icon;
import org.sprautestudio.st_addon_spraute.ui.Widget;
import org.sprautestudio.st_addon_spraute.ui.code.LanguageSupport;

import java.io.IOException;
import java.util.List;

/** Content of one editor tab. */
public abstract class EditorTab extends Widget {
    /** A problem with a way to navigate to it. */
    public record Problem(LanguageSupport.Severity severity, String message, String where, Runnable reveal) {}

    public final Project.FileRef file;
    protected final Project project;
    public Runnable onDirtyChanged;

    protected EditorTab(Project project, Project.FileRef file) {
        this.project = project;
        this.file = file;
    }

    public String title() {
        return file != null ? file.name() : "";
    }

    public abstract Icon icon();

    public abstract int iconColor();

    public abstract boolean isDirty();

    public abstract void save() throws IOException;

    /** .sst text to deploy, or null when this tab is not a script. */
    public abstract String source();

    /** Id from the script header, or -1. */
    public abstract int scriptId();

    public abstract List<Problem> problems();

    /** Semantic info for the outline, or null. */
    public abstract Analyzer.Result analysis();

    /** Navigates to a declaration offset in the generated/edited source. */
    public abstract void reveal(int offset);

    public abstract void focusContent();

    public void undo() {
    }

    public void redo() {
    }
}
