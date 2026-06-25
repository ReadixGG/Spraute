package org.zonarstudio.spraute_engine.client;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.zonarstudio.spraute_engine.Spraute_engine;
import org.zonarstudio.spraute_engine.compat.SprautePlayerCompat;

/** Клиентская высота шага (1.19.2 — ванила сбрасывает maxUpStep каждый тик). */
@Mod.EventBusSubscriber(modid = Spraute_engine.MODID, value = Dist.CLIENT)
public final class ClientStepHeightHolder {
    private static float stepHeight = 0.6f;
    private static boolean custom;

    private ClientStepHeightHolder() {}

    public static void set(float blocks) {
        stepHeight = blocks;
        custom = blocks > 0.6f + 1.0e-4f;
        applyNow();
    }

    public static void clear() {
        stepHeight = 0.6f;
        custom = false;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !custom) return;
        applyNow();
    }

    private static void applyNow() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            SprautePlayerCompat.setStepHeight(mc.player, stepHeight);
        }
    }
}
