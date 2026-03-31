package org.zonarstudio.spraute_engine.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * S2C: patch one property of a widget by id (e.g. text label after server script update).
 */
public class UpdateSprauteUiWidgetPacket {
    private static final int MAX_FIELD = 64;
    private static final int MAX_VALUE = 16_000;

    private final String widgetId;
    private final String field;
    private final String value;

    public UpdateSprauteUiWidgetPacket(String widgetId, String field, String value) {
        this.widgetId = widgetId != null ? widgetId : "";
        this.field = field != null ? field : "";
        this.value = value != null ? value : "";
    }

    public String getWidgetId() {
        return widgetId;
    }

    public String getField() {
        return field;
    }

    public String getValue() {
        return value;
    }

    public static void encode(UpdateSprauteUiWidgetPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.widgetId, 256);
        buf.writeUtf(msg.field, MAX_FIELD);
        buf.writeUtf(msg.value, MAX_VALUE);
    }

    public static UpdateSprauteUiWidgetPacket decode(FriendlyByteBuf buf) {
        return new UpdateSprauteUiWidgetPacket(buf.readUtf(256), buf.readUtf(MAX_FIELD), buf.readUtf(MAX_VALUE));
    }

    public static void handle(UpdateSprauteUiWidgetPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                        org.zonarstudio.spraute_engine.client.SprauteScriptScreen.applyWidgetPatchFromServer(
                                msg.widgetId, msg.field, msg.value)));
        ctx.get().setPacketHandled(true);
    }
}
