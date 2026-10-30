package org.sprautestudio.st_addon_spraute.runtime;

import org.sprautestudio.st_addon_spraute.core.script.ScriptPerformer;
import org.sprautestudio.st_addon_spraute.core.script.ScriptPerformerAction;

import java.util.function.Supplier;

/**
 * Names a speech slot, reading the name and its colour when the story gets here — so "name alice …" and
 * "namecolor" show in the speech window from the next line on.
 */
public class SpeechNameAction extends ScriptPerformerAction {
    private final int slot;
    private final Supplier<String> name;
    private final Supplier<Integer> color;
    private final String icon;

    public SpeechNameAction(ScriptPerformer performer, int slot, Supplier<String> name, Supplier<Integer> color, String icon) {
        super(performer);
        this.slot = slot;
        this.name = name;
        this.color = color;
        this.icon = icon;
    }

    @Override
    public boolean update() {
        getPerformer().speech().setEntityName(slot, name.get(), color.get(), icon);
        return true;
    }
}
