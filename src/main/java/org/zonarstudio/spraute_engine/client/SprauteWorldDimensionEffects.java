package org.zonarstudio.spraute_engine.client;



import net.minecraft.util.Mth;

import net.minecraft.world.phys.Vec3;

import org.zonarstudio.spraute_engine.registry.CustomWorldRegistry;



/** Client sky/fog tint for {@code create world} void dimensions. */

public class SprauteWorldDimensionEffects extends net.minecraft.client.renderer.DimensionSpecialEffects {



    private final Vec3 fogColor;

    private final Vec3 skyColor;



    public SprauteWorldDimensionEffects(CustomWorldRegistry.CustomWorldDef def) {

        super(def.ambientLight, false, SkyType.NORMAL, false, false);

        this.fogColor = rgbToVec(def.fogColor);

        this.skyColor = rgbToVec(def.skyColor);

    }



    private static Vec3 rgbToVec(int rgb) {

        float r = ((rgb >> 16) & 0xFF) / 255.0f;

        float g = ((rgb >> 8) & 0xFF) / 255.0f;

        float b = (rgb & 0xFF) / 255.0f;

        return new Vec3(r, g, b);

    }



    @Override

    public Vec3 getBrightnessDependentFogColor(Vec3 biomeFogColor, float brightness) {

        float t = Mth.clamp(brightness, 0.0f, 1.0f);

        return new Vec3(

                Mth.lerp(t, fogColor.x(), skyColor.x()),

                Mth.lerp(t, fogColor.y(), skyColor.y()),

                Mth.lerp(t, fogColor.z(), skyColor.z())

        );

    }



    @Override

    public boolean isFoggyAt(int blockX, int blockY) {

        return false;

    }



    @Override

    public float[] getSunriseColor(float timeOfDay, float partialTick) {

        return null;

    }

}

