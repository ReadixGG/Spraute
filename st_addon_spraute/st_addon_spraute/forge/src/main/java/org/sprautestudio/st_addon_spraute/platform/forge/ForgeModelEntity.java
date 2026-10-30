package org.sprautestudio.st_addon_spraute.platform.forge;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraftforge.entity.PartEntity;
import org.sprautestudio.st_addon_spraute.runtime.models.BodyPart;
import org.sprautestudio.st_addon_spraute.runtime.models.SprauteModelEntity;

/** A character whose extra hitboxes Forge knows of: the game then picks them for hands, arrows and casts. */
final class ForgeModelEntity extends SprauteModelEntity {
    private PartEntity<?>[] forgeParts;

    ForgeModelEntity(EntityType<SprauteModelEntity> type, Level level) {
        super(type, level);
    }

    @Override
    public boolean isMultipartEntity() {
        return true;
    }

    @Override
    public PartEntity<?>[] getParts() {
        if (forgeParts == null) {
            BodyPart[] parts = parts();
            forgeParts = new PartEntity<?>[parts.length];
            for (int i = 0; i < parts.length; i++) forgeParts[i] = (ForgePart) parts[i];
        }
        return forgeParts;
    }
}
