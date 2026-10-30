package org.sprautestudio.st_addon_spraute.core.script;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Runs a list of steps one after another. Steps that finish at once all run in the same tick; a blocking step
 * holds the performer until a later tick; a passing one keeps getting ticks while the next steps go on, until a later
 * step finishes. Labels and jumps are local to the performer.
 */
public final class ScriptPerformer {
    /** Steps of one performer in one tick: a loop without a wait goes on in the next tick, the server keeps running. */
    private static final int STEPS_PER_TICK = 4096;
    /** Reserved label names: the very beginning and the very end. */
    public static final String LABEL_START = "начало", LABEL_END = "конец";

    private final ScriptInstance script;
    final List<ScriptPerformerAction> actions = new ArrayList<>();
    final Map<String, Integer> labels = new LinkedHashMap<>();
    /** Label names in the order they appear. */
    public final List<String> labelPointsNames = new ArrayList<>();
    private final SpeechState speech = new SpeechState();
    /** Passing steps that have not finished yet. */
    private final List<ScriptPerformerAction> passing = new ArrayList<>();
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

    private void stopPassing() {
        for (ScriptPerformerAction a : passing) a.stop();
        passing.clear();
    }

    /** The player of the "as" block this performer is in; null — none. */
    public java.util.UUID asPlayer;

    public void tick() {
        org.sprautestudio.st_addon_spraute.runtime.SprauteRuntime.setAs(asPlayer);
        passing.removeIf(ScriptPerformerAction::update);
        for (int i = 0; i < STEPS_PER_TICK && index < actions.size(); i++) {
            ScriptPerformerAction a = actions.get(index);
            if (!entered) {
                entered = true;
                a.start();
            }
            if (a.update()) {
                stopPassing();
            } else if (a.blocking()) {
                return;
            } else {
                passing.add(a);
            }
            entered = false;
            index = jump >= 0 ? jump : index + 1;
            jump = -1;
        }
    }

    public boolean isFinished() {
        return index >= actions.size();
    }

    private Integer labelAt(String label) {
        if (LABEL_START.equals(label)) return 0;
        if (LABEL_END.equals(label)) return actions.size();
        return labels.get(label);
    }

    /** Moves to a label from outside; false when this performer has no such label. */
    public boolean goTo(String label) {
        Integer at = labelAt(label);
        if (at == null) return false;
        stage = label;
        stopCurrent();
        jump = -1;
        index = at;
        entered = false;
        return true;
    }

    /** A jump made by the step running now. */
    void jumpTo(String label) {
        Integer at = labelAt(label);
        if (at == null) return;
        stage = label;
        jump = at;
    }

    private void stopCurrent() {
        stopPassing();
        if (entered && index < actions.size()) actions.get(index).stop();
    }

    public void jumpToIndex(int i) {
        stopCurrent();
        index = Math.max(0, Math.min(actions.size(), i));
        jump = -1;
        entered = false;
    }

    public void restart() {
        jumpToIndex(0);
    }

    /** The story stopped or the branch was removed: what is waiting lets go. */
    void stop() {
        stopCurrent();
        entered = false;
    }

    public String getStage() {
        return stage;
    }

    void setStage(String s) {
        stage = s;
    }

    public int getActionIndex() {
        return index;
    }

    public int getActionsAmount() {
        return actions.size();
    }

    /** What the current step is doing, in words, for the editor. */
    public String describe() {
        if (index >= actions.size()) return "Завершён";
        String d = actions.get(index).describe();
        return d != null ? d : "Выполняет шаг " + (index + 1);
    }
}
