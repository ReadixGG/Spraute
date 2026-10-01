package org.zonarstudio.spraute_engine.script.function;

import net.minecraft.commands.CommandSourceStack;
import org.zonarstudio.spraute_engine.scene.SceneActiveState;
import org.zonarstudio.spraute_engine.scene.SceneNode;
import org.zonarstudio.spraute_engine.scene.SceneNodeIds;
import org.zonarstudio.spraute_engine.scene.SceneNodeType;
import org.zonarstudio.spraute_engine.scene.SceneRouteStorage;
import org.zonarstudio.spraute_engine.script.SceneScriptSpawn;
import org.zonarstudio.spraute_engine.script.ScriptContext;
import org.zonarstudio.spraute_engine.script.ScriptExecutor;

import java.util.List;
import java.util.Map;

/** Creation and lookup entry points; mutations live on the returned ROUTE node. */
public final class RouteFunctions {
    private RouteFunctions() {}

    public static final class CreateRoute implements ScriptFunction {
        @Override public String getName() { return "routeCreate"; }
        @Override public int getArgCount() { return 1; }
        @Override public Class<?>[] getArgTypes() {
            return new Class<?>[]{Object.class};
        }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.isEmpty()) return null;
            SceneActiveState state = SceneScriptSpawn.resolveState(
                    ScriptExecutor.currentVariables(), Map.of());
            if (state == null) return null;
            String requested = String.valueOf(args.get(0)).trim();
            if (requested.isBlank()) requested = "route";
            SceneNode existing = state.document.findNode(requested);
            if (existing != null) return existing.type == SceneNodeType.ROUTE ? state.nodeView(existing) : null;

            SceneNode route = SceneNode.route(requested);
            route.id = uniqueId(state, requested);
            route.routeFile = "routes/" + route.id + SceneRouteStorage.EXTENSION;
            route.routeMode = "free";
            if (args.size() == 2) {
                route.routeClosed = truthy(args.get(1));
            } else if (args.size() > 2) {
                route.routeClosed = truthy(args.get(2));
            }
            if (!state.document.addChild(state.document.root.id, route)) return null;
            save(route, source);
            return state.nodeView(route);
        }
    }

    public static final class GetRoute implements ScriptFunction {
        @Override public String getName() { return "routeGet"; }
        @Override public int getArgCount() { return 1; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{Object.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.isEmpty()) return null;
            SceneActiveState state = SceneScriptSpawn.resolveState(
                    ScriptExecutor.currentVariables(), Map.of());
            if (state == null) return null;
            var view = state.nodeView(String.valueOf(args.get(0)));
            if (view == null) return null;
            SceneNode node = state.document.findNode(view.getNodeId());
            return node != null && node.type == SceneNodeType.ROUTE ? view : null;
        }
    }

    private static String uniqueId(SceneActiveState state, String requested) {
        String clean = requested.replaceAll("[^a-zA-Z0-9_\\-а-яА-ЯёЁ]", "_");
        if (!clean.isBlank() && state.document.findNode(clean) == null) return clean;
        return SceneNodeIds.allocate(state.document, SceneNodeType.ROUTE);
    }

    private static boolean truthy(Object value) {
        if (value instanceof Boolean bool) return bool;
        if (value instanceof Number number) return number.doubleValue() != 0d;
        return "true".equalsIgnoreCase(String.valueOf(value))
                || "1".equals(String.valueOf(value));
    }

    private static void save(SceneNode route, CommandSourceStack source) {
        if (source == null || source.getServer() == null) return;
        try {
            SceneRouteStorage.saveNode(
                    source.getServer().getServerDirectory().toPath().resolve("spraute_engine"), route);
        } catch (Exception ignored) {}
    }
}
