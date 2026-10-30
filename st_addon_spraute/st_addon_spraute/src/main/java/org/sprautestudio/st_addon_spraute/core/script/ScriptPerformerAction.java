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

    /** Called every tick while the performer stands here; true — go on to the next step. */
    public abstract boolean update();
}
