package org.zonarstudio.spraute_engine.script.function;

import java.util.HashMap;
import java.util.Map;

import org.zonarstudio.spraute_engine.script.function.SnapshotFunctions;

public class FunctionRegistry {
    private static final Map<String, ScriptFunction> FUNCTIONS = new HashMap<>();

    static {
        register(new ChatFunction());
        register(new NpcFunction());
        register(new SayFunction());
        register(new GetNearestPlayerFunction());
        register(new SetNamesColorFunction());
        register(new GetSlotFunction());
        register(new HasItemFunction());
        register(new CountItemFunction());
        register(new ExecuteFunction());
        register(new TaskDoneFunction());
        register(new IntStrFunction("intStr"));
        register(new IntStrFunction("wholeStr"));
        register(new RandomFunction());
        register(new ParticleFunctions.Spawn());
        registerAlias("spawnParticle", new ParticleFunctions.Spawn());
        register(new ParticleFunctions.Line());
        register(new ParticleFunctions.Circle());
        register(new ParticleFunctions.Spiral());
        register(new ParticleFunctions.StartBone());
        register(new ParticleFunctions.StopBone());
        register(new OverlayOpenFunction());
        register(new OverlayCloseFunction());
        register(new ItemFunctions.GiveItem());
        register(new ItemFunctions.GetHeldItem());
        register(new UiOpenFunction());
        register(new UiCloseFunction());
        register(new UiUpdateFunction());
        register(new UiAnimateFunction());
        register(new ListFunctions.Create());
        register(new ListFunctions.ListAlias());
        register(new ListFunctions.Add());
        register(new ListFunctions.Get());
        register(new ListFunctions.Set());
        register(new ListFunctions.Size());
        register(new ListFunctions.Remove());
        register(new DictFunctions.Create());
        register(new DictFunctions.FromPairs());
        register(new DictFunctions.Set());
        register(new DictFunctions.Get());
        register(new DictFunctions.GetOr());
        register(new DictFunctions.Remove());
        register(new SpawnBillboardFunction());
        register(new StrLenFunction());
        register(new StrWidthFunction());
        register(new StrNewlineCountFunction());
        register(new SoundFunctions.PlaySound());
        register(new SoundFunctions.StopSound());
        register(new GetPlayerFunction());
        register(new SetBlockFunction());
        register(new SnapshotFunctions.SaveSnapshot());
        register(new SnapshotFunctions.LoadSnapshot());
        registerAlias("save_snapshot", new SnapshotFunctions.SaveSnapshot());
        registerAlias("load_snapshot", new SnapshotFunctions.LoadSnapshot());
        register(new JavaFunctions.JavaClassFunction());
        register(new JavaFunctions.JavaNewFunction());
        registerAlias("java_class", new JavaFunctions.JavaClassFunction());
        registerAlias("java_new", new JavaFunctions.JavaNewFunction());
        register(new JavaFunctions.SendPacketFunction());
        register(new SpawnOrbFunction());
        register(new RemoveOrbsFunction());
        register(new CancelEventFunction());
        register(new BlockDisplayFunctions.SetBlockDisplay());
        register(new BlockDisplayFunctions.SetBlockDisplayModel());
        register(new BlockDisplayFunctions.SetBlockDisplayBlock());
        register(new BlockDisplayFunctions.RemoveBlockDisplay());
        register(new BlockDisplayFunctions.OpenBlockUi());
        register(new BlockDisplayFunctions.GetBlockSlot());
        register(new DropFunctions.AddMobDropFunction());
        register(new DropFunctions.AddBlockDropFunction());
        register(new ScriptManagementFunctions.StartScriptFunction());
        register(new ScriptManagementFunctions.StopScriptFunction());
        register(new FadeOutFunction());
        register(new StopCameraFunction());
        register(new MoveCameraFunction());
        register(new FindSafeBlockFunction());
        register(new PlaceStructureFunction());
        register(new SaveStructureFunction());
        // Entity utilities
        register(new EntityUtilFunctions.RemoveEntity());
        register(new EntityUtilFunctions.SetBillboardTexture());
        register(new EntityUtilFunctions.TeleportEntity());
        register(new EntityUtilFunctions.GetEntityPos());
        // NPC groups
        register(new NpcGroupFunctions.CreateGroup());
        register(new NpcGroupFunctions.GetGroup());
        register(new NpcGroupFunctions.GroupAdd());
        register(new NpcGroupFunctions.GroupRemove());
        register(new NpcGroupFunctions.GroupClear());
        register(new NpcGroupFunctions.GroupSize());
        // Player list & tags
        register(new PlayerListFunctions.GetPlayers());
        register(new PlayerListFunctions.PlayerCount());
        register(new PlayerListFunctions.GetFirstPlayer());
        register(new PlayerListFunctions.GetLastPlayer());
        register(new PlayerListFunctions.GetPlayerAt());
        register(new PlayerTagFunctions.AddTag());
        register(new PlayerTagFunctions.RemoveTag());
        register(new PlayerTagFunctions.HasTag());
        register(new PlayerTagFunctions.GetByTag());
        register(new PlayerTagFunctions.GetTags());
        register(new TradeEventFunctions.FireTradeBuy());
        register(new TradeEventFunctions.FireTradeSell());
        register(new PlayerSkinFunctions.GetPlayerSkinUrl());
        register(new PlayerSkinFunctions.GetPlayerSkinTexture());
        register(new PlayerSkinFunctions.NpcSetPlayerSkin());
        register(new PlayerSkinFunctions.NpcClearPlayerSkin());
    }

    public static void register(ScriptFunction function) {
        FUNCTIONS.put(function.getName().toLowerCase(), function);
    }

    public static void registerAlias(String alias, ScriptFunction function) {
        FUNCTIONS.put(alias.toLowerCase(), function);
    }

    public static ScriptFunction get(String name) {
        return FUNCTIONS.get(name.toLowerCase());
    }

    public static boolean exists(String name) {
        return FUNCTIONS.containsKey(name.toLowerCase());
    }
}
