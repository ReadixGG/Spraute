package org.sprautestudio.st_addon_spraute.core.script;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Runs a list of steps one after another. Steps that finish at once all run in the same tick; a step
 * that waits holds the performer until a later tick. Labels and jumps are local to the performer.
 */
public final class ScriptPerformer {
    /** Steps of one performer in one tick: a loop without a wait goes on in the next tick, the server keeps running. */
    private static final int STEPS_PER_TICK = 4096;

    private final ScriptInstance script;
    final List<ScriptPerformerAction> actions = new ArrayList<>();
    final Map<String, Integer> labels = new LinkedHashMap<>();
    /** Label names in the order they appear. */
    public final List<String> labelPointsNames = new ArrayList<>();
    private final SpeechState speech = new SpeechState();
    private int index;
    private boolean entered;
    private int jump = -1;
    private String stage = "";

    ScriptPerformer(ScriptInstance script) {
        this.script = script;
    }

    public ScriptInstance getScript() {
        return script;
    }

    public SpeechState speech() {
        return speech;
    }

    public void tick() {
        for (int i = 0; i < STEPS_PER_TICK && index < actions.size(); i++) {
            ScriptPerformerAction a = actions.get(index);
            if (!entered) {
                entered = true;
                a.start();
            }
            if (!a.update()) return;
            entered = false;
            index = jump >= 0 ? jump : index + 1;
            jump = -1;
        }
    }

    public boolean isFinished() {
        return index >= actions.size();
    }

    /** Moves to a label; false when this performer has no such label. */
    public boolean goTo(String label) {
        Integer at = labels.get(label);
        if (at == null) return false;
        stage = label;
        jump = -1;
        index = at;
        entered = false;
        return true;
    }

    /** A jump made by the step running now. */
    void jumpTo(String label) {
        Integer at = labels.get(label);
        if (at == null) return;
        stage = label;
        jump = at;
    }

    public void jumpToIndex(int i) {
        index = Math.max(0, Math.min(actions.size(), i));
        jump = -1;
        entered = false;
    }

    public void restart() {
        jumpToIndex(0);
    }

    public String getStage() {
        return stage;
    }

    public int getActionIndex() {
        return index;
    }

    public int getActionsAmount() {
        return actions.size();
    }
}
