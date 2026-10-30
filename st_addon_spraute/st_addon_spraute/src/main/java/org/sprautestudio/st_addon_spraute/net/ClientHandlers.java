package org.sprautestudio.st_addon_spraute.net;

import org.sprautestudio.st_addon_spraute.editor.ClientState;

/** Client-side packet entry points (only loaded on the physical client). */
public final class ClientHandlers {
    private ClientHandlers() {}

    public static void status(Packets.Status msg) {
        ClientState.get().onStatus(msg);
    }

    public static void log(Packets.Log msg) {
        ClientState.get().onLog(msg);
    }

    public static void deployResult(Packets.DeployResult msg) {
        ClientState.get().onDeployResult(msg);
    }

    public static void guiOpen(Packets.GuiOpen msg) {
        org.sprautestudio.st_addon_spraute.gui.client.SprauteGuiScreen.openFromServer(msg);
    }

    public static void guiUpdate(Packets.GuiUpdate msg) {
        org.sprautestudio.st_addon_spraute.gui.client.SprauteGuiScreen.applyUpdate(msg);
        org.sprautestudio.st_addon_spraute.gui.client.HudOverlays.update(msg);
    }

    public static void choiceOpen(Packets.ChoiceOpen msg) {
        org.sprautestudio.st_addon_spraute.engine.client.ChoiceScreen.open(msg);
    }

    public static void speechHead(Packets.SpeechHead msg) {
        org.sprautestudio.st_addon_spraute.engine.client.ChatHeads.bind(msg.scriptId(), msg.character(), msg.entityId(), msg.model());
    }

    public static void cameraPlay(Packets.CameraPlay msg) {
        org.sprautestudio.st_addon_spraute.cutscene.client.CameraDirector.play(msg.scriptId(), msg.name(),
                org.sprautestudio.st_addon_spraute.cutscene.CutsceneDoc.fromJson(msg.json()),
                new net.minecraft.world.phys.Vec3(msg.ox(), msg.oy(), msg.oz()), msg.looks(), null);
    }

    public static void hitboxSync(Packets.HitboxSync msg) {
        if (msg.flags() == 0) org.sprautestudio.st_addon_spraute.hitbox.Hitboxes.CLIENT.remove(msg.entityId());
        else org.sprautestudio.st_addon_spraute.hitbox.Hitboxes.CLIENT.put(msg.entityId(), msg.flags());
    }

    public static void notify(Packets.Notify msg) {
        org.sprautestudio.st_addon_spraute.engine.client.Notices.show(msg.text(), msg.icon(), msg.item(), msg.project(), msg.sound());
    }

    public static void soundPlay(Packets.SoundPlay m) {
        org.sprautestudio.st_addon_spraute.engine.client.ProjectSounds.play(m.project(), m.name(), m.x(), m.y(), m.z(), m.positional(), m.volume(),
                m.pitch(), m.loop(), m.id());
    }

    public static void soundStop(Packets.SoundStop m) {
        org.sprautestudio.st_addon_spraute.engine.client.ProjectSounds.stop(m.id());
    }

    public static void zoneTitle(Packets.ZoneTitle msg) {
        org.sprautestudio.st_addon_spraute.zone.client.ZoneClient.showTitle(msg.title(), msg.subtitle());
    }

    public static void questSync(Packets.QuestSync msg) {
        org.sprautestudio.st_addon_spraute.quest.client.QuestClient.receive(msg.json(), msg.open());
    }

    public static void shopOpen(Packets.ShopOpen msg) {
        org.sprautestudio.st_addon_spraute.shop.client.ShopScreen.show(msg.json(), msg.refresh());
    }

    public static void fxPlay(Packets.FxPlay msg) {
        org.sprautestudio.st_addon_spraute.fx.client.FxEngine.play(msg.id(), msg.project(),
                org.sprautestudio.st_addon_spraute.fx.FxDef.fromJson(msg.json()), msg.x(), msg.y(), msg.z(), msg.entity(), msg.scale(), msg.scriptId());
    }

    public static void fxStop(Packets.FxStop msg) {
        org.sprautestudio.st_addon_spraute.fx.client.FxEngine.stop(msg.id(), msg.scriptId(), false);
    }

    public static void cameraOp(Packets.CameraOp msg) {
        org.sprautestudio.st_addon_spraute.cutscene.client.CameraDirector.op(msg.json());
    }

    public static void overlayShow(Packets.OverlayShow msg) {
        org.sprautestudio.st_addon_spraute.gui.client.HudOverlays.show(msg);
    }

    public static void overlayHide(Packets.OverlayHide msg) {
        org.sprautestudio.st_addon_spraute.gui.client.HudOverlays.hide(msg);
    }

    public static void guiClose(Packets.GuiClose msg) {
        org.sprautestudio.st_addon_spraute.gui.client.SprauteGuiScreen.closeFromServer(msg);
    }

    public static void engineSync(Packets.EngineSync msg) {
        org.sprautestudio.st_addon_spraute.engine.client.EngineClient.receive(msg.json());
    }

    public static void speech(Packets.Speech msg) {
        org.sprautestudio.st_addon_spraute.core.client.SpeechClient.receive(msg);
    }

    public static void speechClear() {
        org.sprautestudio.st_addon_spraute.core.client.SpeechClient.clear();
    }

    public static void screenFx(Packets.ScreenFx msg) {
        org.sprautestudio.st_addon_spraute.core.client.ScreenFx.play(msg.kind(), msg.in(), msg.hold(), msg.out(), msg.argb());
    }

    public static void coins(Packets.CoinsSync msg) {
        org.sprautestudio.st_addon_spraute.core.system.Coins.receive(msg.value());
    }

    public static void playerSkin(Packets.PlayerSkin msg) {
        org.sprautestudio.st_addon_spraute.core.client.PlayerSkins.override = msg.texture().isEmpty() ? null : net.minecraft.resources.ResourceLocation.tryParse(msg.texture());
    }
}
