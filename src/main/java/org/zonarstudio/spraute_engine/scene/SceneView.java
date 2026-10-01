package org.zonarstudio.spraute_engine.scene;

import net.minecraft.commands.CommandSourceStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Script-facing view of a loaded scene ({@code _scene}). */
public final class SceneView {
    private final SceneActiveState state;

    SceneView(SceneActiveState state) {
        this.state = state;
    }

    public String getSceneId() { return state.sceneId; }

    public Object getProperty(String key) {
        if (key == null) return null;
        return switch (key.toLowerCase(Locale.ROOT)) {
            case "id" -> state.sceneId;
            case "dimension" -> state.runtimeDimension != null && !state.runtimeDimension.isEmpty()
                    ? state.runtimeDimension
                    : state.document.dimension;
            case "rootid", "root_id" -> state.document.root.id;
            case "active" -> true;
            case "nodecount", "node_count" -> countNodes(state.document.root);
            default -> null;
        };
    }

    public Object invokeMethod(String method, List<Object> args, CommandSourceStack source) {
        if (method == null) return null;
        String m = method.toLowerCase(Locale.ROOT);
        return switch (m) {
            case "node", "get", "getnode" -> {
                if (args.isEmpty()) yield state.nodeView(state.document.root);
                yield state.nodeView(String.valueOf(args.get(0)));
            }
            case "root" -> state.nodeView(state.document.root);
            case "find" -> {
                if (args.isEmpty()) yield null;
                SceneNode found = ScenePathResolver.findByName(state.document.root, String.valueOf(args.get(0)));
                yield found == null ? null : state.nodeView(found);
            }
            case "findbytag", "find_by_tag" -> {
                if (args.isEmpty()) yield null;
                SceneNode found = SceneNodeTags.findByTag(state.document.root, String.valueOf(args.get(0)));
                yield found == null ? null : state.nodeView(found);
            }
            case "nodeswithtag", "nodes_with_tag" -> {
                if (args.isEmpty()) yield List.of();
                List<SceneNodeView> out = new ArrayList<>();
                for (SceneNode found : SceneNodeTags.findAllByTag(state.document.root, String.valueOf(args.get(0)))) {
                    out.add(state.nodeView(found));
                }
                yield out;
            }
            case "children" -> {
                SceneNodeView root = state.nodeView(state.document.root);
                yield root == null ? List.of() : root.invokeMethod("children", List.of(), source);
            }
            case "list", "listnodes", "nodes" -> state.listNodeViews();
            case "has" -> {
                if (args.isEmpty()) yield false;
                yield state.nodeView(String.valueOf(args.get(0))) != null;
            }
            default -> null;
        };
    }

    private static int countNodes(SceneNode node) {
        int count = 1;
        for (SceneNode child : node.children) count += countNodes(child);
        return count;
    }
}
