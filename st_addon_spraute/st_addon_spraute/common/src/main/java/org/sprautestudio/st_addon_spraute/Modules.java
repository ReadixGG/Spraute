package org.sprautestudio.st_addon_spraute;

/** Every part of the engine that listens to the game. A new one goes here. */
final class Modules {
    private Modules() {
    }

    static void common() {
        org.sprautestudio.st_addon_spraute.core.script.Behaviours.registerHooks();
        org.sprautestudio.st_addon_spraute.fight.Fights.registerHooks();
        org.sprautestudio.st_addon_spraute.fx.Casts.registerHooks();
        org.sprautestudio.st_addon_spraute.hitbox.Hitboxes.registerHooks();
        org.sprautestudio.st_addon_spraute.path.Walkers.registerHooks();
        org.sprautestudio.st_addon_spraute.runtime.AttackBoxes.registerHooks();
        org.sprautestudio.st_addon_spraute.runtime.BossMoves.registerHooks();
        org.sprautestudio.st_addon_spraute.runtime.GameEvents.registerHooks();
        org.sprautestudio.st_addon_spraute.runtime.Mechanics.registerHooks();
        org.sprautestudio.st_addon_spraute.runtime.WhenReady.registerHooks();
    }
}
