package org.zonarstudio.spraute_engine.script;

import net.minecraft.world.entity.player.Player;
import org.zonarstudio.spraute_engine.ui.UiSessionBinding;
import org.zonarstudio.spraute_engine.ui.UiTemplate;

import java.util.function.Function;

/**
 * Holds per-script runtime state that functions can read/write.
 * Unlike SprauteConfig, changes here do NOT persist to disk.
 */
public class ScriptContext {

    private String nameColor = null;
    private String nameFormat = null;
    private Function<String, Boolean> taskChecker = null;
    private UiSessionBinding uiSessionBinding;

    private boolean eventCanceled = false;

    public void cancelEvent() {
        this.eventCanceled = true;
    }

    public void setEventCanceled(boolean canceled) {
        this.eventCanceled = canceled;
    }

    public boolean isEventCanceled() {
        return eventCanceled;
    }

    public String getNameColor() {
        return nameColor;
    }

    public void setNameColor(String nameColor) {
        this.nameColor = nameColor;
    }

    public String getNameFormat() {
        return nameFormat;
    }

    public void setNameFormat(String nameFormat) {
        this.nameFormat = nameFormat;
    }

    public void setTaskChecker(Function<String, Boolean> taskChecker) {
        this.taskChecker = taskChecker;
    }

    public boolean isTaskDone(String taskId) {
        return taskChecker != null && Boolean.TRUE.equals(taskChecker.apply(taskId));
    }

    public void setUiSessionBinding(UiSessionBinding uiSessionBinding) {
        this.uiSessionBinding = uiSessionBinding;
    }

    public void notifyUiOpened(Player player, UiTemplate template) {
        if (uiSessionBinding != null) {
            uiSessionBinding.onOpen(player, template);
        }
    }

    public void notifyUiClosed(Player player) {
        if (uiSessionBinding != null) {
            uiSessionBinding.onClose(player);
        }
    }
}
