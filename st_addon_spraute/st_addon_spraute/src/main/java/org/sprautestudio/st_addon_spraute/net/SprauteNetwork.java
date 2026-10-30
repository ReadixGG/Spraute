package org.sprautestudio.st_addon_spraute.net;

import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.ChatFormatting;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import org.sprautestudio.st_addon_spraute.St_addon_spraute;
import org.sprautestudio.st_addon_spraute.runtime.SprauteRuntime;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

public final class SprauteNetwork {
    private SprauteNetwork() {}

    private static final String PROTOCOL = "2";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(St_addon_spraute.MODID, "main"),
            () -> PROTOCOL,
            v -> v.equals(PROTOCOL) || v.equals(NetworkRegistry.ABSENT.version()) || v.equals(NetworkRegistry.ACCEPTVANILLA),
            v -> v.equals(PROTOCOL) || v.equals(NetworkRegistry.ABSENT.version()) || v.equals(NetworkRegistry.ACCEPTVANILLA));

    public static void register() {
        int id = 0;
        CHANNEL.registerMessage(id++, Packets.Deploy.class, Packets.Deploy::write, Packets.Deploy::read,
                SprauteNetwork::handleDeploy, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, Packets.SourcesSet.class, Packets.SourcesSet::write, Packets.SourcesSet::read,
                SprauteNetwork::handleSourcesSet, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, Packets.Undeploy.class, Packets.Undeploy::write, Packets.Undeploy::read,
                SprauteNetwork::handleUndeploy, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, Packets.RunCommand.class, Packets.RunCommand::write, Packets.RunCommand::read,
                SprauteNetwork::handleCommand, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, Packets.RequestStatus.class, Packets.RequestStatus::write, Packets.RequestStatus::read,
                SprauteNetwork::handleStatusRequest, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, Packets.Status.class, Packets.Status::write, Packets.Status::read,
                (msg, ctx) -> client(ctx, () -> () -> ClientHandlers.status(msg)), Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, Packets.Log.class, Packets.Log::write, Packets.Log::read,
                (msg, ctx) -> client(ctx, () -> () -> ClientHandlers.log(msg)), Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, Packets.DeployResult.class, Packets.DeployResult::write, Packets.DeployResult::read,
                (msg, ctx) -> client(ctx, () -> () -> ClientHandlers.deployResult(msg)), Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, Packets.GuiOpen.class, Packets.GuiOpen::write, Packets.GuiOpen::read,
                (msg, ctx) -> client(ctx, () -> () -> ClientHandlers.guiOpen(msg)), Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, Packets.GuiUpdate.class, Packets.GuiUpdate::write, Packets.GuiUpdate::read,
                (msg, ctx) -> client(ctx, () -> () -> ClientHandlers.guiUpdate(msg)), Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, Packets.GuiClose.class, Packets.GuiClose::write, Packets.GuiClose::read,
                (msg, ctx) -> client(ctx, () -> () -> ClientHandlers.guiClose(msg)), Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, Packets.GuiEvent.class, Packets.GuiEvent::write, Packets.GuiEvent::read,
                SprauteNetwork::handleGuiEvent, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, Packets.OverlayShow.class, Packets.OverlayShow::write, Packets.OverlayShow::read,
                (msg, ctx) -> client(ctx, () -> () -> ClientHandlers.overlayShow(msg)), Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, Packets.OverlayHide.class, Packets.OverlayHide::write, Packets.OverlayHide::read,
                (msg, ctx) -> client(ctx, () -> () -> ClientHandlers.overlayHide(msg)), Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, Packets.SpeechHead.class, Packets.SpeechHead::write, Packets.SpeechHead::read,
                (msg, ctx) -> client(ctx, () -> () -> ClientHandlers.speechHead(msg)), Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, Packets.CameraPlay.class, Packets.CameraPlay::write, Packets.CameraPlay::read,
                (msg, ctx) -> client(ctx, () -> () -> ClientHandlers.cameraPlay(msg)), Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, Packets.CameraOp.class, Packets.CameraOp::write, Packets.CameraOp::read,
                (msg, ctx) -> client(ctx, () -> () -> ClientHandlers.cameraOp(msg)), Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, Packets.CameraEvent.class, Packets.CameraEvent::write, Packets.CameraEvent::read,
                SprauteNetwork::handleCameraEvent, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, Packets.ChoiceOpen.class, Packets.ChoiceOpen::write, Packets.ChoiceOpen::read,
                (msg, ctx) -> client(ctx, () -> () -> ClientHandlers.choiceOpen(msg)), Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, Packets.ChoiceAnswer.class, Packets.ChoiceAnswer::write, Packets.ChoiceAnswer::read,
                SprauteNetwork::handleChoiceAnswer, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, Packets.KeyPress.class, Packets.KeyPress::write, Packets.KeyPress::read, (msg, ctx) -> {
            net.minecraft.server.level.ServerPlayer p = ctx.get().getSender();
            ctx.get().enqueueWork(() -> {
                if (p != null) org.sprautestudio.st_addon_spraute.runtime.Mechanics.keyPressed(p, msg.key());
            });
            ctx.get().setPacketHandled(true);
        }, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, Packets.FxLaunch.class, Packets.FxLaunch::write, Packets.FxLaunch::read,
                (msg, ctx) -> client(ctx, () -> () -> org.sprautestudio.st_addon_spraute.fx.client.FxEngine.launch(msg.id(), msg.project(),
                        org.sprautestudio.st_addon_spraute.fx.FxDef.fromJson(msg.json()), msg.x(), msg.y(), msg.z(), msg.vx(), msg.vy(), msg.vz(), msg.g(),
                        msg.scale(), msg.scriptId())), Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, Packets.FxPlay.class, Packets.FxPlay::write, Packets.FxPlay::read,
                (msg, ctx) -> client(ctx, () -> () -> ClientHandlers.fxPlay(msg)), Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, Packets.FxStop.class, Packets.FxStop::write, Packets.FxStop::read,
                (msg, ctx) -> client(ctx, () -> () -> ClientHandlers.fxStop(msg)), Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, Packets.ShopOpen.class, Packets.ShopOpen::write, Packets.ShopOpen::read,
                (msg, ctx) -> client(ctx, () -> () -> ClientHandlers.shopOpen(msg)), Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, Packets.ShopDeal.class, Packets.ShopDeal::write, Packets.ShopDeal::read,
                SprauteNetwork::handleShopDeal, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, Packets.HitboxSync.class, Packets.HitboxSync::write, Packets.HitboxSync::read,
                (msg, ctx) -> client(ctx, () -> () -> ClientHandlers.hitboxSync(msg)), Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, Packets.ZoneShow.class, Packets.ZoneShow::write, Packets.ZoneShow::read,
                (msg, ctx) -> client(ctx, () -> () -> org.sprautestudio.st_addon_spraute.zone.client.ZoneClient.receiveShown(msg.json())),
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, Packets.ZoneTitle.class, Packets.ZoneTitle::write, Packets.ZoneTitle::read,
                (msg, ctx) -> client(ctx, () -> () -> ClientHandlers.zoneTitle(msg)), Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, Packets.QuestSync.class, Packets.QuestSync::write, Packets.QuestSync::read,
                (msg, ctx) -> client(ctx, () -> () -> ClientHandlers.questSync(msg)), Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, Packets.QuestTrack.class, Packets.QuestTrack::write, Packets.QuestTrack::read,
                SprauteNetwork::handleQuestTrack, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, Packets.EngineSync.class, Packets.EngineSync::write, Packets.EngineSync::read,
                (msg, ctx) -> client(ctx, () -> () -> ClientHandlers.engineSync(msg)), Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, Packets.EngineSet.class, Packets.EngineSet::write, Packets.EngineSet::read,
                SprauteNetwork::handleEngineSet, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, Packets.PartPoses.class, Packets.PartPoses::write, Packets.PartPoses::read, (msg, ctx) -> {
            ServerPlayer p = ctx.get().getSender();
            ctx.get().enqueueWork(() -> {
                if (p == null) return;
                var en = p.level().getEntity(msg.entity());
                // only bodies the player can see from nearby: a client can not move parts of far-away ones
                if (!(en instanceof org.sprautestudio.st_addon_spraute.runtime.models.SprauteModelEntity me) || en.distanceToSqr(p) > 96 * 96) return;
                long now = System.currentTimeMillis();
                float[] d = msg.data();
                for (int i = 0; i < msg.bones().size(); i++) {
                    org.joml.Vector3f pos = new org.joml.Vector3f(d[i * 7], d[i * 7 + 1], d[i * 7 + 2]);
                    if (pos.lengthSquared() > 64 * 64 || !Float.isFinite(pos.lengthSquared())) continue;
                    org.joml.Quaternionf q = new org.joml.Quaternionf(d[i * 7 + 3], d[i * 7 + 4], d[i * 7 + 5], d[i * 7 + 6]).normalize();
                    me.bonePoses.put(msg.bones().get(i), new org.sprautestudio.st_addon_spraute.runtime.models.PartShape.BonePose(pos, q, now));
                }
            });
            ctx.get().setPacketHandled(true);
        }, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, Packets.Notify.class, Packets.Notify::write, Packets.Notify::read,
                (msg, ctx) -> client(ctx, () -> () -> ClientHandlers.notify(msg)), Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, Packets.SoundPlay.class, Packets.SoundPlay::write, Packets.SoundPlay::read,
                (msg, ctx) -> client(ctx, () -> () -> ClientHandlers.soundPlay(msg)), Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, Packets.SoundStop.class, Packets.SoundStop::write, Packets.SoundStop::read,
                (msg, ctx) -> client(ctx, () -> () -> ClientHandlers.soundStop(msg)), Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, Packets.Speech.class, Packets.Speech::write, Packets.Speech::read,
                (msg, ctx) -> client(ctx, () -> () -> ClientHandlers.speech(msg)), Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, Packets.SpeechClear.class, Packets.SpeechClear::write, Packets.SpeechClear::read,
                (msg, ctx) -> client(ctx, () -> () -> ClientHandlers.speechClear(msg)), Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, Packets.CameraPos.class, Packets.CameraPos::write, Packets.CameraPos::read, (msg, ctx) -> {
            ServerPlayer p = ctx.get().getSender();
            ctx.get().enqueueWork(() -> {
                if (p != null) org.sprautestudio.st_addon_spraute.core.system.CameraState.report(p, msg.x(), msg.y(), msg.z(), msg.active());
            });
            ctx.get().setPacketHandled(true);
        }, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, Packets.ChoiceParagraph.class, Packets.ChoiceParagraph::write, Packets.ChoiceParagraph::read, (msg, ctx) -> {
            ctx.get().enqueueWork(() -> org.sprautestudio.st_addon_spraute.runtime.SprauteScriptInstance.paragraph(msg.scriptId(), msg.choiceId(), msg.index()));
            ctx.get().setPacketHandled(true);
        }, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, Packets.SpeechNewest.class, Packets.SpeechNewest::write, Packets.SpeechNewest::read,
                (msg, ctx) -> client(ctx, () -> () -> ClientHandlers.speechNewest(msg)), Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, Packets.ScreenFx.class, Packets.ScreenFx::write, Packets.ScreenFx::read,
                (msg, ctx) -> client(ctx, () -> () -> ClientHandlers.screenFx(msg)), Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, Packets.CoinsSync.class, Packets.CoinsSync::write, Packets.CoinsSync::read,
                (msg, ctx) -> client(ctx, () -> () -> ClientHandlers.coins(msg)), Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, Packets.BigPart.class, Packets.BigPart::write, Packets.BigPart::read,
                SprauteNetwork::handleBig, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, Packets.ContentOffer.class, Packets.ContentOffer::write, Packets.ContentOffer::read,
                (msg, ctx) -> server(ctx, player -> org.sprautestudio.st_addon_spraute.content.ProjectContent.offer(player, msg)), Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, Packets.ContentWant.class, Packets.ContentWant::write, Packets.ContentWant::read,
                (msg, ctx) -> client(ctx, () -> () -> org.sprautestudio.st_addon_spraute.content.ContentClient.wanted(msg)), Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, Packets.ContentIndex.class, Packets.ContentIndex::write, Packets.ContentIndex::read,
                (msg, ctx) -> client(ctx, () -> () -> org.sprautestudio.st_addon_spraute.content.ContentClient.index(msg)), Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, Packets.ContentRequest.class, Packets.ContentRequest::write, Packets.ContentRequest::read, (msg, ctx) -> {
            ServerPlayer p = ctx.get().getSender();
            ctx.get().enqueueWork(() -> {
                if (p != null) org.sprautestudio.st_addon_spraute.content.ProjectContent.requested(p, msg);
            });
            ctx.get().setPacketHandled(true);
        }, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, Packets.ContentData.class, Packets.ContentData::write, Packets.ContentData::read, SprauteNetwork::content);
        CHANNEL.registerMessage(id++, Packets.PlayerSkin.class, Packets.PlayerSkin::write, Packets.PlayerSkin::read,
                (msg, ctx) -> client(ctx, () -> () -> ClientHandlers.playerSkin(msg)), Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    private static void content(Packets.ContentData msg, Supplier<NetworkEvent.Context> ctx) {
        if (ctx.get().getDirection().getReceptionSide().isClient()) {
            client(ctx, () -> () -> org.sprautestudio.st_addon_spraute.content.ContentClient.received(msg));
        } else {
            server(ctx, player -> org.sprautestudio.st_addon_spraute.content.ProjectContent.received(player, msg));
        }
    }

    /** Cutscene progress from the player watching it: any player, no editing rights needed. */
    private static void handleCameraEvent(Packets.CameraEvent msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        ctx.get().enqueueWork(() -> {
            if (player != null) org.sprautestudio.st_addon_spraute.runtime.GameEvents.camera(player, msg.scriptId(), msg.kind(), msg.name());
        });
        ctx.get().setPacketHandled(true);
    }

    /** The player follows a quest of their own. */
    private static void handleQuestTrack(Packets.QuestTrack msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        ctx.get().enqueueWork(() -> {
            if (player != null) org.sprautestudio.st_addon_spraute.quest.Quests.track(player, msg.key());
        });
        ctx.get().setPacketHandled(true);
    }

    /** A deal in a shop: any player; the server checks it all again. */
    private static void handleShopDeal(Packets.ShopDeal msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        ctx.get().enqueueWork(() -> {
            if (player != null) org.sprautestudio.st_addon_spraute.shop.Shops.deal(player, msg);
        });
        ctx.get().setPacketHandled(true);
    }

    /** The player's answer: any player, no editing rights needed. */
    private static void handleChoiceAnswer(Packets.ChoiceAnswer msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer from = ctx.get().getSender();
        ctx.get().enqueueWork(() -> org.sprautestudio.st_addon_spraute.runtime.SprauteScriptInstance.answer(from, msg.scriptId(), msg.choiceId(), msg.answer()));
        ctx.get().setPacketHandled(true);
    }

    private static void handleEngineSet(Packets.EngineSet msg, Supplier<NetworkEvent.Context> ctx) {
        server(ctx, player -> engineSet(player, msg));
    }

    private static void engineSet(ServerPlayer player, Packets.EngineSet msg) {
        {
            org.sprautestudio.st_addon_spraute.engine.EngineServer.set(player.getServer(),
                    org.sprautestudio.st_addon_spraute.engine.EngineConfig.fromJson(msg.json()));
            sendTo(player, new Packets.Log(Packets.Log.SUCCESS, "Spraute", "Настройки движка применены в мире"));
        }
    }

    /** To every player on the server (effects are seen by everyone). */
    /** To everyone who sees the story running now; outside a story, to everyone. */
    public static void sendToAll(Object msg) {
        for (ServerPlayer p : SprauteRuntime.audience()) sendTo(p, msg);
    }

    /** To everyone who sees that story, for what runs outside its steps. */
    public static void sendToAudience(int scriptId, Object msg) {
        for (ServerPlayer p : SprauteRuntime.audience(scriptId)) sendTo(p, msg);
    }

    /** The game takes at most 32 KB in a packet to the server: a story or the engine's settings can be more, and go in pieces. */
    private static final int PART = 30000;
    private static final int MAX_BIG = 32 << 20;
    private static int nextTransfer;

    public static void sendToServer(Object msg) {
        int kind = msg instanceof Packets.Deploy ? 0 : msg instanceof Packets.SourcesSet ? 1 : msg instanceof Packets.EngineSet ? 2 : msg instanceof Packets.ContentOffer ? 3 : -1;
        if (kind < 0) {
            CHANNEL.sendToServer(msg);
            return;
        }
        net.minecraft.network.FriendlyByteBuf buf = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        switch (kind) {
            case 0 -> ((Packets.Deploy) msg).write(buf);
            case 1 -> ((Packets.SourcesSet) msg).write(buf);
            case 2 -> ((Packets.EngineSet) msg).write(buf);
            default -> ((Packets.ContentOffer) msg).write(buf);
        }
        byte[] all = new byte[buf.readableBytes()];
        buf.readBytes(all);
        int total = Math.max(1, (all.length + PART - 1) / PART), transfer = nextTransfer++;
        for (int i = 0; i < total; i++) {
            CHANNEL.sendToServer(new Packets.BigPart(transfer, kind, i, total, java.util.Arrays.copyOfRange(all, i * PART, Math.min(all.length, (i + 1) * PART))));
        }
    }

    private static final java.util.Map<String, java.io.ByteArrayOutputStream> BIG = new java.util.HashMap<>();

    private static void handleBig(Packets.BigPart msg, Supplier<NetworkEvent.Context> ctx) {
        server(ctx, player -> {
            String key = player.getUUID() + "/" + msg.transfer();
            java.io.ByteArrayOutputStream part = BIG.computeIfAbsent(key, k -> new java.io.ByteArrayOutputStream());
            part.writeBytes(msg.data());
            if (part.size() > MAX_BIG) {
                BIG.remove(key);
                return;
            }
            if (msg.part() + 1 < msg.total()) return;
            BIG.remove(key);
            net.minecraft.network.FriendlyByteBuf buf = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.wrappedBuffer(part.toByteArray()));
            switch (msg.kind()) {
                case 0 -> deploy(player, Packets.Deploy.read(buf));
                case 1 -> sourcesSet(player, Packets.SourcesSet.read(buf));
                case 2 -> engineSet(player, Packets.EngineSet.read(buf));
                case 3 -> org.sprautestudio.st_addon_spraute.content.ProjectContent.offer(player, Packets.ContentOffer.read(buf));
                default -> {
                }
            }
        });
    }

    public static void sendTo(ServerPlayer player, Object msg) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), msg);
    }

    private static void client(Supplier<NetworkEvent.Context> ctx, Supplier<Runnable> action) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, action));
        ctx.get().setPacketHandled(true);
    }

    /** Runs {@code action} on the server thread if the sender may edit stories. */
    private static void server(Supplier<NetworkEvent.Context> ctx, java.util.function.Consumer<ServerPlayer> action) {
        ServerPlayer player = ctx.get().getSender();
        ctx.get().enqueueWork(() -> {
            if (player == null) return;
            if (!SprauteRuntime.canEdit(player)) {
                sendTo(player, new Packets.Log(Packets.Log.ERROR, "Spraute", "Нужны права оператора (включите читы)"));
                return;
            }
            action.accept(player);
        });
        ctx.get().setPacketHandled(true);
    }

    // ------------------------------------------------------------------ server

    private static void handleSourcesSet(Packets.SourcesSet msg, Supplier<NetworkEvent.Context> ctx) {
        server(ctx, player -> sourcesSet(player, msg));
    }

    private static void sourcesSet(ServerPlayer player, Packets.SourcesSet msg) {
        SprauteRuntime.get().putGuis(msg.project(), msg.guis());
        org.sprautestudio.st_addon_spraute.zone.Zones.sourcesSaved(msg.project());
    }

    private static void handleDeploy(Packets.Deploy msg, Supplier<NetworkEvent.Context> ctx) {
        server(ctx, player -> deploy(player, msg));
    }

    private static void deploy(ServerPlayer player, Packets.Deploy msg) {
        {
            SprauteRuntime.get().putGuis(msg.project(), msg.guis());
            SprauteRuntime.DeployResult r = SprauteRuntime.get().deploy(msg.project(), msg.source());
            sendTo(player, new Packets.DeployResult(r.ok(), r.id(), r.messages()));
            if (r.ok() && msg.start()) {
                // what runs now is the old text: it stops, the new one starts
                SprauteRuntime.env().ifPresent(env -> env.stop(r.id()));
                runCommand(player, "script start " + r.id());
            }
            sendTo(player, SprauteRuntime.get().status());
        }
    }

    private static void handleUndeploy(Packets.Undeploy msg, Supplier<NetworkEvent.Context> ctx) {
        server(ctx, player -> {
            boolean removed = SprauteRuntime.get().undeploy(msg.id());
            sendTo(player, new Packets.Log(removed ? Packets.Log.SUCCESS : Packets.Log.WARNING, "Spraute",
                    removed ? "Сюжет #" + msg.id() + " выгружен из игры" : "Сюжет #" + msg.id() + " не был загружен"));
            sendTo(player, SprauteRuntime.get().status());
        });
    }

    private static void handleCommand(Packets.RunCommand msg, Supplier<NetworkEvent.Context> ctx) {
        server(ctx, player -> {
            runCommand(player, msg.command());
            sendTo(player, SprauteRuntime.get().status());
        });
    }

    /** GUI events come from any player looking at a story GUI, no editing rights needed. */
    private static void handleGuiEvent(Packets.GuiEvent msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        ctx.get().enqueueWork(() -> {
            if (player != null) SprauteRuntime.get().onGuiEvent(player, msg);
        });
        ctx.get().setPacketHandled(true);
    }

    private static void handleStatusRequest(Packets.RequestStatus msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        ctx.get().enqueueWork(() -> {
            if (player == null) return;
            // polled automatically: answer silently with an empty status when the player may not edit
            if (!SprauteRuntime.canEdit(player)) sendTo(player, new Packets.Status(false, List.of(), List.of()));
            else sendTo(player, SprauteRuntime.get().status());
        });
        ctx.get().setPacketHandled(true);
    }

    /** Executes a command as the player (with op rights) and sends its chat output back as log lines. */
    public static void runCommand(ServerPlayer player, String command) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        String line = command.startsWith("/") ? command.substring(1) : command;
        List<Packets.Log> output = new ArrayList<>();
        CommandSource capture = new CommandSource() {
            @Override
            public void sendSystemMessage(Component message) {
                TextColor color = message.getStyle().getColor();
                boolean failure = color != null && color.equals(TextColor.fromLegacyFormat(ChatFormatting.RED));
                output.add(new Packets.Log(failure ? Packets.Log.ERROR : Packets.Log.OUTPUT, "/" + line, message.getString()));
            }

            @Override
            public boolean acceptsSuccess() {
                return true;
            }

            @Override
            public boolean acceptsFailure() {
                return true;
            }

            @Override
            public boolean shouldInformAdmins() {
                return false;
            }
        };
        CommandSourceStack source = player.createCommandSourceStack().withSource(capture).withPermission(4);
        sendTo(player, new Packets.Log(Packets.Log.INFO, "Spraute", "> /" + line));
        server.getCommands().performPrefixedCommand(source, line);
        for (Packets.Log l : output) sendTo(player, l);
    }
}
