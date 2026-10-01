package org.zonarstudio.spraute_engine.entity.client;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import org.zonarstudio.spraute_engine.core.model.SpGeoBounds;

/** Conservative visual bounds kept separate from gameplay collision. */
public final class GeoEntityBounds {
    private GeoEntityBounds() {}

    public static AABB world(Entity entity, SpGeoBounds bounds,
                             float scaleX, float scaleY, float scaleZ, float margin) {
        if (bounds == null || bounds.isEmpty()) return entity.getBoundingBoxForCulling();
        float maxScale=Math.max(Math.abs(scaleX),Math.max(Math.abs(scaleY),Math.abs(scaleZ)));
        double radius=Math.max(.1, bounds.radius()/16.0*maxScale)+Math.max(0,margin);
        AABB box=new AABB(entity.getX()-radius,entity.getY()-radius,entity.getZ()-radius,
                entity.getX()+radius,entity.getY()+radius,entity.getZ()+radius);
        return entity.isCustomNameVisible() && entity.hasCustomName() ? box.inflate(1.5) : box;
    }

    public static float radiusBlocks(SpGeoBounds bounds, float sx,float sy,float sz) {
        if (bounds == null) return 1f;
        return bounds.radius()/16f*Math.max(Math.abs(sx),Math.max(Math.abs(sy),Math.abs(sz)));
    }
}
