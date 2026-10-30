package org.sprautestudio.st_addon_spraute;

/** {@link Modules} of the client: this class loads client classes, so a dedicated server never touches it. */
final class ClientModules {
    private ClientModules() {
    }

    static void client() {
        org.sprautestudio.st_addon_spraute.content.ContentClient.registerHooks();
        org.sprautestudio.st_addon_spraute.core.client.ScreenFx.registerHooks();
        org.sprautestudio.st_addon_spraute.core.client.SlotsClient.registerHooks();
        org.sprautestudio.st_addon_spraute.core.client.SpeechClient.Keys.registerHooks();
        org.sprautestudio.st_addon_spraute.core.client.SpeechClient.registerHooks();
        org.sprautestudio.st_addon_spraute.cutscene.client.CameraDirector.registerHooks();
        org.sprautestudio.st_addon_spraute.editor.ClientEvents.ForgeBus.registerHooks();
        org.sprautestudio.st_addon_spraute.editor.ClientEvents.ModBus.registerHooks();
        org.sprautestudio.st_addon_spraute.editor.cutscene.CutsceneRecorder.registerHooks();
        org.sprautestudio.st_addon_spraute.editor.cutscene.CutsceneWorldEditor.registerHooks();
        org.sprautestudio.st_addon_spraute.editor.path.PathWorldEditor.registerHooks();
        org.sprautestudio.st_addon_spraute.editor.zone.ZoneWorldEditor.registerHooks();
        org.sprautestudio.st_addon_spraute.engine.client.ChatHeads.Setup.registerHooks();
        org.sprautestudio.st_addon_spraute.engine.client.EngineClient.Overlays.registerHooks();
        org.sprautestudio.st_addon_spraute.engine.client.EngineClient.registerHooks();
        org.sprautestudio.st_addon_spraute.engine.client.Notices.registerHooks();
        org.sprautestudio.st_addon_spraute.engine.client.OrbRenderer.Registration.registerHooks();
        org.sprautestudio.st_addon_spraute.fx.client.FxEngine.registerHooks();
        org.sprautestudio.st_addon_spraute.gui.client.HudOverlays.registerHooks();
        org.sprautestudio.st_addon_spraute.quest.client.QuestClient.ModBus.registerHooks();
        org.sprautestudio.st_addon_spraute.quest.client.QuestClient.registerHooks();
        org.sprautestudio.st_addon_spraute.runtime.KeyReporter.registerHooks();
        org.sprautestudio.st_addon_spraute.runtime.models.PartPoseSync.registerHooks();
        org.sprautestudio.st_addon_spraute.zone.client.ZoneClient.registerHooks();
    }
}
