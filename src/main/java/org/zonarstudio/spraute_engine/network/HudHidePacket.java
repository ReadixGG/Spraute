package org.zonarstudio.spraute_engine.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import org.zonarstudio.spraute_engine.client.HudHideClient;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

/** S2C: which classic HUD parts are hidden for this player. */
public class HudHidePacket {
    public final List<String> hidden;

    public HudHidePacket(Collection<String> hidden) {
        this.hidden = hidden == null ? List.of() : List.copyOf(hidden);
    }

    public static HudHidePacket of(Collection<String> hidden) {
        return new HudHidePacket(hidden);
    }

    public static HudHidePacket clear() {
        return new HudHidePacket(List.of());
    }

    public static void encode(HudHidePacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.hidden.size());
        for (String id : msg.hidden) buf.writeUtf(id);
    }

    public static HudHidePacket decode(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        List<String> ids = new ArrayList<>(n);
        for (int i = 0; i < n; i++) ids.add(buf.readUtf());
        return new HudHidePacket(ids);
    }

    public static void handle(HudHidePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            if (msg.hidden.isEmpty()) HudHideClient.clear();
            else HudHideClient.replace(msg.hidden);
        }));
        ctx.get().setPacketHandled(true);
    }
}
