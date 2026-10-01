package org.zonarstudio.spraute_engine.script.function;

import net.minecraft.commands.CommandSourceStack;
import net.minecraftforge.network.PacketDistributor;
import org.zonarstudio.spraute_engine.network.ModNetwork;
import org.zonarstudio.spraute_engine.network.SprauteParticleBurstPacket;
import org.zonarstudio.spraute_engine.particle.ParticleGameplayRuntime;
import org.zonarstudio.spraute_engine.scene.SceneActiveState;
import org.zonarstudio.spraute_engine.scene.SceneNode;
import org.zonarstudio.spraute_engine.scene.SceneNodeView;
import org.zonarstudio.spraute_engine.scene.ScenePathResolver;
import org.zonarstudio.spraute_engine.scene.SceneRuntime;
import org.zonarstudio.spraute_engine.script.ScriptContext;
import org.zonarstudio.spraute_engine.script.ScriptExecutor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Queries and lifecycle helpers for custom Particle System gameplay proxies. */
public final class ParticleSystemFunctions {
    private ParticleSystemFunctions() {}

    public static final class Count implements ScriptFunction {
        @Override public String getName() { return "particleSystemCount"; }
        @Override public int getArgCount() { return 1; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{String.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            return ParticleGameplayRuntime.count(id(args));
        }
    }

    public static final class Positions implements ScriptFunction {
        @Override public String getName() { return "particleSystemPositions"; }
        @Override public int getArgCount() { return 1; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{String.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            List<Map<String, Object>> result = new ArrayList<>();
            for (var position : ParticleGameplayRuntime.positions(id(args))) {
                Map<String, Object> point = new LinkedHashMap<>();
                point.put("x", position.x);
                point.put("y", position.y);
                point.put("z", position.z);
                result.add(point);
            }
            return result;
        }
    }

    public static final class Clear implements ScriptFunction {
        @Override public String getName() { return "particleSystemClear"; }
        @Override public int getArgCount() { return 1; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{String.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            String id = id(args);
            ParticleGameplayRuntime.clear(id);
            if (source != null && source.getServer() != null) {
                ModNetwork.CHANNEL.send(PacketDistributor.ALL.noArg(),
                        new SprauteParticleBurstPacket(id, "", new float[0]));
            }
            return null;
        }
    }

    /** One-shot burst of a PARTICLE scene node: particleEmit(node) / particleEmit("fx"). */
    public static final class Emit implements ScriptFunction {
        @Override public String getName() { return "particleEmit"; }
        @Override public int getArgCount() { return 1; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{Object.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            SceneNodeView view = resolveNode(args);
            if (view != null) view.invokeMethod("emit", List.of(), source);
            return null;
        }
    }

    private static SceneNodeView resolveNode(List<Object> args) {
        if (args == null || args.isEmpty() || args.get(0) == null) return null;
        Object ref = args.get(0);
        if (ref instanceof SceneNodeView view) return view;
        String key = String.valueOf(ref).trim();
        if (key.isEmpty()) return null;
        SceneActiveState current = ScriptExecutor.currentSceneState();
        if (current != null) {
            SceneNode found = ScenePathResolver.findNode(current.document, key);
            if (found != null) return current.nodeView(found.id);
        }
        for (String sceneId : SceneRuntime.listLoaded()) {
            SceneActiveState state = SceneRuntime.getActiveState(sceneId);
            if (state == null) continue;
            SceneNode found = ScenePathResolver.findNode(state.document, key);
            if (found != null) return state.nodeView(found.id);
        }
        return null;
    }

    private static String id(List<Object> args) {
        return args == null || args.isEmpty() || args.get(0) == null ? "" : String.valueOf(args.get(0));
    }
}
