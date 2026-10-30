package org.sprautestudio.st_addon_spraute.core.script;

/** One step of a performer. */
public abstract class ScriptPerformerAction {
    private final ScriptPerformer performer;

    protected ScriptPerformerAction(ScriptPerformer performer) {
        this.performer = performer;
    }

    public ScriptPerformer getPerformer() {
        return performer;
    }

    /** The performer has just come to this step. */
    public void start() {
    }

    /** Called every tick while the step lasts; true — it is finished. */
    public abstract boolean update();

    /** Whether the performer waits for this step; a passing one goes on while the next steps run. */
    public boolean blocking() {
        return true;
    }

    /** The step is cut short: the story stopped or moved elsewhere. */
    public void stop() {
    }

    /** What it is doing, in words, for the editor; null — nothing to say. */
    public String describe() {
        return null;
    }
}
