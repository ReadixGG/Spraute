package org.sprautestudio.spraute_engine;

/** {@link Modules} of the client: this class loads client classes, so a dedicated server never touches it. */
final class ClientModules {
    private ClientModules() {
    }

    static void client() {
        org.sprautestudio.spraute_engine.content.ContentClient.registerHooks();
        org.sprautestudio.spraute_engine.core.client.ScreenFx.registerHooks();
        org.sprautestudio.spraute_engine.core.client.SlotsClient.registerHooks();
        org.sprautestudio.spraute_engine.core.client.SpeechClient.Keys.registerHooks();
        // Screen effects and cinematic fades must draw before speech; its 3D portrait writes GUI depth.
        org.sprautestudio.spraute_engine.cutscene.client.CameraDirector.registerHooks();
        org.sprautestudio.spraute_engine.core.client.SpeechClient.registerHooks();
        org.sprautestudio.spraute_engine.editor.ClientEvents.ForgeBus.registerHooks();
        org.sprautestudio.spraute_engine.editor.ClientEvents.ModBus.registerHooks();
        org.sprautestudio.spraute_engine.editor.cutscene.CutsceneRecorder.registerHooks();
        org.sprautestudio.spraute_engine.editor.cutscene.CutsceneWorldEditor.registerHooks();
        org.sprautestudio.spraute_engine.editor.path.PathWorldEditor.registerHooks();
        org.sprautestudio.spraute_engine.editor.zone.ZoneWorldEditor.registerHooks();
        org.sprautestudio.spraute_engine.engine.client.ChatHeads.Setup.registerHooks();
        org.sprautestudio.spraute_engine.engine.client.EngineClient.Overlays.registerHooks();
        org.sprautestudio.spraute_engine.engine.client.EngineClient.registerHooks();
        org.sprautestudio.spraute_engine.engine.client.Notices.registerHooks();
        org.sprautestudio.spraute_engine.engine.client.OrbRenderer.Registration.registerHooks();
        org.sprautestudio.spraute_engine.fx.client.FxEngine.registerHooks();
        org.sprautestudio.spraute_engine.gui.client.HudOverlays.registerHooks();
        org.sprautestudio.spraute_engine.quest.client.QuestClient.ModBus.registerHooks();
        org.sprautestudio.spraute_engine.quest.client.QuestClient.registerHooks();
        org.sprautestudio.spraute_engine.runtime.KeyReporter.registerHooks();
        org.sprautestudio.spraute_engine.runtime.models.PartPoseSync.registerHooks();
        org.sprautestudio.spraute_engine.zone.client.ZoneClient.registerHooks();
    }
}
