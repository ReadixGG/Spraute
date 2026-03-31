package org.zonarstudio.spraute_engine.ui;

import net.minecraft.world.entity.player.Player;

/**
 * Binds opened {@link UiTemplate} to the active script instance (click handlers).
 */
public interface UiSessionBinding {
    void onOpen(Player player, UiTemplate template);

    void onClose(Player player);
}
