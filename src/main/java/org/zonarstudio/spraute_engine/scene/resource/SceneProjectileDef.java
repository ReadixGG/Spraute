package org.zonarstudio.spraute_engine.scene.resource;

import org.zonarstudio.spraute_engine.registry.CustomProjectileRegistry;

/** Scene payload mirroring {@code create projectile id { … }}. */
public final class SceneProjectileDef {
    public String id = "";
    public String texture = "";
    /** {@code billboard} | {@code velocity} (стрела по скорости) | {@code cross} (две перп. плоскости). */
    public String renderStyle = "billboard";
    public float width = 0.5f;
    public float height = 0.5f;
    public int alpha = 255;
    public boolean seeThrough;
    public boolean glow;
    public float gravity;
    public float drag = 1.0f;
    public int lifetime = 200;
    public float damage;
    public int pierce;
    public float knockback;
    public float rotation;
    public boolean collideBlocks = true;
    public boolean collideEntities = true;

    public SceneProjectileDef copy() {
        SceneProjectileDef c = new SceneProjectileDef();
        c.id = id;
        c.texture = texture;
        c.renderStyle = renderStyle;
        c.width = width;
        c.height = height;
        c.alpha = alpha;
        c.seeThrough = seeThrough;
        c.glow = glow;
        c.gravity = gravity;
        c.drag = drag;
        c.lifetime = lifetime;
        c.damage = damage;
        c.pierce = pierce;
        c.knockback = knockback;
        c.rotation = rotation;
        c.collideBlocks = collideBlocks;
        c.collideEntities = collideEntities;
        return c;
    }

    public CustomProjectileRegistry.ProjectileDef toRegistryDef() {
        CustomProjectileRegistry.ProjectileDef d = new CustomProjectileRegistry.ProjectileDef();
        d.id = id;
        d.texture = texture == null ? "" : texture;
        d.width = width;
        d.height = height;
        d.alpha = alpha;
        d.seeThrough = seeThrough;
        d.glow = glow;
        d.gravity = gravity;
        d.drag = drag;
        d.lifetime = lifetime;
        d.damage = damage;
        d.pierce = pierce;
        d.knockback = knockback;
        d.rotation = rotation;
        d.collideBlocks = collideBlocks;
        d.collideEntities = collideEntities;
        CustomProjectileRegistry.applyRenderStyle(d, renderStyle);
        return d;
    }
}
