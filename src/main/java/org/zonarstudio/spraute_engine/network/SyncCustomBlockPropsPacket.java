package org.zonarstudio.spraute_engine.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;
import org.zonarstudio.spraute_engine.registry.CustomGeoBlock;

import java.util.function.Supplier;

/** Syncs mutable hardness / solid flags for custom blocks to all clients. */
public class SyncCustomBlockPropsPacket {

    private final String blockId;
    private final float hardness;
    private final boolean solid;

    public SyncCustomBlockPropsPacket(String blockId, float hardness, boolean solid) {
        this.blockId = blockId;
        this.hardness = hardness;
        this.solid = solid;
    }

    public static void encode(SyncCustomBlockPropsPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.blockId);
        buf.writeFloat(msg.hardness);
        buf.writeBoolean(msg.solid);
    }

    public static SyncCustomBlockPropsPacket decode(FriendlyByteBuf buf) {
        return new SyncCustomBlockPropsPacket(buf.readUtf(), buf.readFloat(), buf.readBoolean());
    }

    public static void handle(SyncCustomBlockPropsPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> apply(msg)));
        ctx.get().setPacketHandled(true);
    }

    private static void apply(SyncCustomBlockPropsPacket msg) {
        ResourceLocation rl = new ResourceLocation(msg.blockId);
        Block block = ForgeRegistries.BLOCKS.getValue(rl);
        if (block instanceof CustomGeoBlock cgb) {
            cgb.setRuntimeHardness(msg.hardness);
            cgb.setRuntimeSolid(msg.solid);
        }
    }

    public static void broadcast(CustomGeoBlock block) {
        ResourceLocation rl = ForgeRegistries.BLOCKS.getKey(block);
        if (rl == null) return;
        ModNetwork.CHANNEL.send(PacketDistributor.ALL.noArg(),
                new SyncCustomBlockPropsPacket(rl.toString(), block.getRuntimeHardness(), block.isRuntimeSolid()));
    }
}
