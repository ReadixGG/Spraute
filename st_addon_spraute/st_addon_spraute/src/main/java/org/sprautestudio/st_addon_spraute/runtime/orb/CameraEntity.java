package org.sprautestudio.st_addon_spraute.runtime.orb;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/** Client-only viewpoint the cutscene camera looks through; never added to a level. */
public class CameraEntity extends Entity {
    public CameraEntity(EntityType<? extends CameraEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    @Override
    protected void defineSynchedData() {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public boolean isSpectator() {
        return true;
    }
}
