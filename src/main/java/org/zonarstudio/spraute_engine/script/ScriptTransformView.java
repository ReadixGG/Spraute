package org.zonarstudio.spraute_engine.script;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.world.entity.Entity;
import org.zonarstudio.spraute_engine.scene.SceneNodeView;

import java.util.AbstractMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Live transform proxy for {@code entity.transform.x} / {@code _node.transform.x}.
 * Reads and writes go through the same property path as flat {@code .x}/{@code .yaw}/….
 */
public final class ScriptTransformView extends AbstractMap<String, Object> {
    private final Entity entity;
    private final SceneNodeView nodeView;
    private final CommandSourceStack source;
    private final PropertyBridge bridge;

    public interface PropertyBridge {
        Object get(String key);
        boolean set(String key, Object value);
    }

    public ScriptTransformView(Entity entity, PropertyBridge bridge) {
        this.entity = entity;
        this.nodeView = null;
        this.source = null;
        this.bridge = bridge;
    }

    public ScriptTransformView(SceneNodeView nodeView, CommandSourceStack source) {
        this.entity = null;
        this.nodeView = nodeView;
        this.source = source;
        this.bridge = null;
    }

    @Override
    public Object get(Object key) {
        if (key == null) return null;
        String prop = normalize(String.valueOf(key));
        if (bridge != null) return bridge.get(prop);
        if (nodeView != null) return nodeView.getProperty(prop, source);
        return null;
    }

    @Override
    public Object put(String key, Object value) {
        if (key == null) return null;
        String prop = normalize(key);
        Object previous = get(prop);
        boolean ok;
        if (bridge != null) {
            ok = bridge.set(prop, value);
        } else if (nodeView != null) {
            ok = nodeView.setProperty(prop, value, source);
        } else {
            ok = false;
        }
        if (!ok) {
            throw new RuntimeException("Cannot set transform." + prop
                    + (entity != null ? " on entity" : nodeView != null ? " on scene node" : ""));
        }
        return previous;
    }

    @Override
    public boolean containsKey(Object key) {
        return key != null && TRANSFORM_KEYS.contains(normalize(String.valueOf(key)));
    }

    @Override
    public Set<Entry<String, Object>> entrySet() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        for (String key : TRANSFORM_KEYS) {
            Object value = get(key);
            if (value != null) snapshot.put(key, value);
        }
        return snapshot.entrySet();
    }

    private static final Set<String> TRANSFORM_KEYS = Set.of(
            "x", "y", "z",
            "yaw", "pitch",
            "rotationX", "rotationY", "rotationZ",
            "scale", "scaleX", "scaleY", "scaleZ"
    );

    private static String normalize(String key) {
        return switch (key) {
            case "rotation_x", "rot_x", "rotx" -> "rotationX";
            case "rotation_y", "rot_y", "roty" -> "rotationY";
            case "rotation_z", "rot_z", "rotz", "roll" -> "rotationZ";
            case "scale_x" -> "scaleX";
            case "scale_y" -> "scaleY";
            case "scale_z" -> "scaleZ";
            default -> key;
        };
    }
}
