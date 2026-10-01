package org.zonarstudio.spraute_engine.scene.resource;

import org.zonarstudio.spraute_engine.registry.CustomProjectileRegistry;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Applies {@code create projectile} / inspector props to {@link SceneProjectileDef}. */
public final class ProjectileDefProps {
    private ProjectileDefProps() {}

    public static void apply(SceneProjectileDef def, Map<String, List<Object>> props) {
        if (def == null || props == null) return;
        Object tex = first(props, "texture");
        if (tex != null && !String.valueOf(tex).isBlank()) def.texture = String.valueOf(tex).trim();

        Object style = first(props, "renderStyle", "render_style", "style", "type");
        if (style != null && !String.valueOf(style).isBlank()) {
            def.renderStyle = String.valueOf(style).trim();
        } else if (props.containsKey("billboard")) {
            def.renderStyle = isTruthy(first(props, "billboard")) ? "billboard" : "velocity";
        }

        Float w = firstFloat(props, "width", "w");
        if (w != null) def.width = w;
        Float h = firstFloat(props, "height", "h");
        if (h != null) def.height = h;
        Integer a = firstInt(props, "alpha");
        if (a != null) def.alpha = Math.min(255, Math.max(0, a));
        if (props.containsKey("see_through") || props.containsKey("seeThrough")) {
            def.seeThrough = isTruthy(first(props, "see_through", "seeThrough"));
        }
        if (props.containsKey("glow")) def.glow = isTruthy(first(props, "glow"));
        Float g = firstFloat(props, "gravity");
        if (g != null) def.gravity = g;
        Float drag = firstFloat(props, "drag");
        if (drag != null) def.drag = drag;
        Integer life = firstInt(props, "lifetime");
        if (life != null) def.lifetime = life;
        Float dmg = firstFloat(props, "damage");
        if (dmg != null) def.damage = dmg;
        Integer pierce = firstInt(props, "pierce");
        if (pierce != null) def.pierce = pierce;
        Float kb = firstFloat(props, "knockback");
        if (kb != null) def.knockback = kb;
        Float rot = firstFloat(props, "rotation");
        if (rot != null) def.rotation = rot;
        if (props.containsKey("collide_blocks") || props.containsKey("collideBlocks")) {
            def.collideBlocks = isTruthy(first(props, "collide_blocks", "collideBlocks"));
        }
        if (props.containsKey("collide_entities") || props.containsKey("collideEntities")) {
            def.collideEntities = isTruthy(first(props, "collide_entities", "collideEntities"));
        }
    }

    public static CustomProjectileRegistry.ProjectileDef toRegistry(Map<String, List<Object>> props, String id) {
        SceneProjectileDef scene = new SceneProjectileDef();
        scene.id = id;
        apply(scene, props);
        return scene.toRegistryDef();
    }

    private static Object first(Map<String, List<Object>> props, String... keys) {
        for (String key : keys) {
            List<Object> v = props.get(key);
            if (v != null && !v.isEmpty()) return v.get(0);
        }
        return null;
    }

    private static Float firstFloat(Map<String, List<Object>> props, String... keys) {
        Object o = first(props, keys);
        if (o instanceof Number n) return n.floatValue();
        return null;
    }

    private static Integer firstInt(Map<String, List<Object>> props, String... keys) {
        Object o = first(props, keys);
        if (o instanceof Number n) return n.intValue();
        return null;
    }

    private static boolean isTruthy(Object value) {
        if (value instanceof Boolean b) return b;
        if (value instanceof Number n) return n.doubleValue() != 0;
        if (value == null) return false;
        String s = String.valueOf(value).trim();
        return !s.isEmpty() && !"false".equalsIgnoreCase(s) && !"0".equals(s);
    }
}
