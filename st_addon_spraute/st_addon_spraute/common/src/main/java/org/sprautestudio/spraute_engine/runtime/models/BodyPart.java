package org.sprautestudio.spraute_engine.runtime.models;

import net.minecraft.world.entity.Entity;

/**
 * One extra hitbox of a Spraute character (a head, a tail, a weak spot), like the parts of the ender
 * dragon: the game picks it for hands, arrows and casts on its own, and hits on it go to the body with
 * the part's name and damage multiplier. Not saved and not sent: both sides build the same parts.
 * What is an entity here depends on the loader, so the implementation comes from the platform.
 */
public interface BodyPart {
    SprauteModelEntity body();

    int index();

    /** Placed by the body this tick (a part of a type without that many parts stays off). */
    boolean active();

    void setActive(boolean active);

    void resize(float w, float h);

    default Entity entity() {
        return (Entity) this;
    }
}
