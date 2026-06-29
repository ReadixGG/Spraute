package org.zonarstudio.spraute_engine.compat;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;

import java.util.Map;
import java.util.UUID;

/** Resolves killer/attacker entities across MC versions and damage source types. */
public final class SprauteDeathCompat {
    private SprauteDeathCompat() {}

    public static Entity resolveAttackerFromSource(DamageSource source) {
        if (source == null) return null;

        Entity entity = source.getEntity();
        Entity fromProjectile = ownerIfProjectile(entity);
        if (fromProjectile != null) return fromProjectile;
        if (entity != null) return entity;

        Entity direct = source.getDirectEntity();
        fromProjectile = ownerIfProjectile(direct);
        if (fromProjectile != null) return fromProjectile;
        if (direct instanceof LivingEntity living) return living;
        return direct;
    }

    public static Entity resolveKiller(LivingEntity victim, DamageSource source, Map<UUID, Entity> lastAttackers) {
        Entity killer = resolveAttackerFromSource(source);

        if (killer == null && victim != null) {
            LivingEntity lastHurt = victim.getLastHurtByMob();
            if (lastHurt != null) killer = lastHurt;
        }

        if (killer == null && victim != null) {
            LivingEntity credit = victim.getKillCredit();
            if (credit != null) killer = credit;
        }

        if (killer == null && victim != null && lastAttackers != null) {
            killer = lastAttackers.get(victim.getUUID());
        }

        return killer;
    }

    private static Entity ownerIfProjectile(Entity entity) {
        if (entity instanceof Projectile projectile) {
            Entity owner = projectile.getOwner();
            if (owner != null) return owner;
        }
        return null;
    }
}
