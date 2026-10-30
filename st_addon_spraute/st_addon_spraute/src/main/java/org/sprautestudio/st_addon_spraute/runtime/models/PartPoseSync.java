package org.sprautestudio.st_addon_spraute.runtime.models;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.sprautestudio.st_addon_spraute.St_addon_spraute;
import org.sprautestudio.st_addon_spraute.net.Packets;
import org.sprautestudio.st_addon_spraute.net.SprauteNetwork;

import java.util.ArrayList;
import java.util.List;

/**
 * Tells the server where the bones that carry hitbox parts are: only the client animates bones, but arrows
 * and casts hit on the server. Every other tick, for bodies near the player that have such parts.
 */
@Mod.EventBusSubscriber(modid = St_addon_spraute.MODID, value = Dist.CLIENT)
public final class PartPoseSync {
    private PartPoseSync() {
    }

    private static int tick;

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END || ++tick % 2 != 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.getConnection() == null) return;
        long now = System.currentTimeMillis();
        for (Entity en : mc.level.entitiesForRendering()) {
            if (!(en instanceof SprauteModelEntity me) || en.distanceToSqr(mc.player) > 64 * 64 || me.bonePoses.isEmpty()) continue;
            List<String> bones = new ArrayList<>();
            List<float[]> rows = new ArrayList<>();
            me.bonePoses.forEach((name, p) -> {
                if (now - p.at() > 500) return;
                bones.add(name);
                rows.add(new float[]{p.pos().x, p.pos().y, p.pos().z, p.rot().x, p.rot().y, p.rot().z, p.rot().w});
            });
            if (bones.isEmpty()) continue;
            float[] data = new float[bones.size() * 7];
            for (int i = 0; i < rows.size(); i++) System.arraycopy(rows.get(i), 0, data, i * 7, 7);
            SprauteNetwork.sendToServer(new Packets.PartPoses(en.getId(), bones, data));
        }
    }
}
