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
        register(new DictFunctions.Remove());
        register(new StrLenFunction());
        register(new StrNewlineCountFunction());
        register(new SoundFunctions.PlaySound());
        register(new SoundFunctions.StopSound());
        register(new GetPlayerFunction());
        register(new SetBlockFunction());
        register(new SnapshotFunctions.SaveSnapshot());
        register(new SnapshotFunctions.LoadSnapshot());
        register(new JavaFunctions.JavaClassFunction());
        register(new JavaFunctions.JavaNewFunction());
        register(new JavaFunctions.SendPacketFunction());
        register(new SpawnOrbFunction());
    }

    public static void register(ScriptFunction function) {
        FUNCTIONS.put(function.getName().toLowerCase(), function);
    }

    public static ScriptFunction get(String name) {
        return FUNCTIONS.get(name.toLowerCase());
    }

    public static boolean exists(String name) {
        return FUNCTIONS.containsKey(name.toLowerCase());
    }
}
