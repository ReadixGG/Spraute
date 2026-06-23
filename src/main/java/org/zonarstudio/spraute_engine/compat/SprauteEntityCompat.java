package org.zonarstudio.spraute_engine.compat;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

/**
 * Version-specific entity field accessors.
 * In 1.19.2 {@code Entity.level} and {@code Entity.onGround} are public fields,
 * while in 1.20.1+ they became private with {@code level()} / {@code onGround()} accessors.
 */
public final class SprauteEntityCompat {
    private SprauteEntityCompat() {}

    public static Level level(Entity entity) {
        //? if >=1.20.1 {
        return entity.level();
        //?} else {
        /*return entity.level;
        *///?}
    }

    public static boolean onGround(Entity entity) {
        //? if >=1.20.1 {
        return entity.onGround();
        //?} else {
        /*return entity.isOnGround();
        *///?}
    }

    public static ServerLevel serverLevel(ServerPlayer player) {
        //? if >=1.20.1 {
        return player.serverLevel();
        //?} else {
        /*return player.getLevel();
        *///?}
    }

    //? if >=1.20.1 {
    // In 1.20.1 ItemEntity.thrower (SRG f_31988_) is private with no getter; expose it via Forge's
    // reflection helper, which resolves the SRG name correctly in both dev and production runtimes.
    private static java.lang.reflect.Field ITEM_THROWER_FIELD;
    //?}

    /** Returns the UUID of the entity that threw the given item, or {@code null} if unknown. */
    public static java.util.UUID getItemThrower(net.minecraft.world.entity.item.ItemEntity item) {
        //? if >=1.20.1 {
        try {
            if (ITEM_THROWER_FIELD == null) {
                ITEM_THROWER_FIELD = net.minecraftforge.fml.util.ObfuscationReflectionHelper.findField(
                        net.minecraft.world.entity.item.ItemEntity.class, "f_31988_");
            }
            return (java.util.UUID) ITEM_THROWER_FIELD.get(item);
        } catch (Exception e) {
            return null;
        }
        //?} else {
        /*return item.getThrower();
        *///?}
    }
}
