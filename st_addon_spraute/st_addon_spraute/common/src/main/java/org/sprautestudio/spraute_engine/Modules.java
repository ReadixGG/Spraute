package org.sprautestudio.spraute_engine;

/** Every part of the engine that listens to the game. A new one goes here. */
final class Modules {
    private Modules() {
    }

    static void common() {
        org.sprautestudio.spraute_engine.core.script.Behaviours.registerHooks();
        org.sprautestudio.spraute_engine.fight.Fights.registerHooks();
        org.sprautestudio.spraute_engine.fx.Casts.registerHooks();
        org.sprautestudio.spraute_engine.hitbox.Hitboxes.registerHooks();
        org.sprautestudio.spraute_engine.path.Walkers.registerHooks();
        org.sprautestudio.spraute_engine.runtime.AttackBoxes.registerHooks();
        org.sprautestudio.spraute_engine.runtime.BossMoves.registerHooks();
        org.sprautestudio.spraute_engine.runtime.GameEvents.registerHooks();
        org.sprautestudio.spraute_engine.runtime.Mechanics.registerHooks();
        org.sprautestudio.spraute_engine.runtime.WhenReady.registerHooks();
    }
}
