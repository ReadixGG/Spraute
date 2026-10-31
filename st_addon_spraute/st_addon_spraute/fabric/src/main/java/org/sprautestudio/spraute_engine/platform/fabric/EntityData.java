package org.sprautestudio.spraute_engine.platform.fabric;

import net.minecraft.nbt.CompoundTag;

/** What the mixin adds to every entity: Forge keeps this data for its mods, here the engine keeps its own. */
public interface EntityData {
    CompoundTag spraute$data();
}
