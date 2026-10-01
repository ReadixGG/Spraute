package org.zonarstudio.spraute_engine.scene.custom;

import net.minecraft.server.level.ServerLevel;
import org.zonarstudio.spraute_engine.scene.SceneActiveState;
import org.zonarstudio.spraute_engine.scene.SceneNode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Java-declared scene node type: one place to describe a new PARTICLE/LIGHT-style feature —
 * its inspector fields AND its server behavior — instead of hand-wiring a new
 * {@code SceneNodeType} + {@code applyXProps} + Inspector section + {@code ScriptExecutor}
 * {@code create} case + network payload the way PARTICLE/LIGHT/HITBOX/etc. are built today.
 *
 * <p>Backed entirely by the existing {@code SceneNodeType.CUSTOM} + {@code node.customData}
 * plumbing — the same one {@code declare inspector} .spr schemas already use — so everything
 * that already works for CUSTOM nodes keeps working for free the moment a type is registered
 * with {@link GenericNodeTypeRegistry#register}: no other file needs touching.
 * <ul>
 *   <li>{@code create <id> name { field = value }} from any script</li>
 *   <li>generic property get/set from script ({@code node.field}, {@code node.field = x})</li>
 *   <li>save/load and network sync (it's just {@code node.customData})</li>
 *   <li>auto-generated Inspector UI — {@code CustomNodeInspector} renders {@link #fields()}
 *       the same way it renders a {@code declare inspector} schema</li>
 * </ul>
 * What this interface adds on top of the data-only CUSTOM system is real server behavior:
 * {@link #onCreate}, {@link #onRemove}, {@link #emit}, {@link #tick}.
 *
 * <p>Example — a Java-declared "damage zone" type with one number field and a per-tick effect:
 * <pre>{@code
 * GenericNodeTypeRegistry.register(new GenericNodeType() {
 *     public String id() { return "damageZone"; }
 *     public String label() { return "Damage Zone"; }
 *     public String section() { return "Gameplay"; }
 *
 *     public List<CustomFieldDef> fields() {
 *         CustomFieldDef dmg = new CustomFieldDef();
 *         dmg.id = "damage";
 *         dmg.kind = CustomFieldKind.NUMBER;
 *         dmg.label = "Damage/tick";
 *         dmg.defaultValue = 2;
 *         return List.of(dmg);
 *     }
 *
 *     public void tick(SceneActiveState state, ServerLevel level, SceneNode node) {
 *         // e.g. read node.customData.get("damage") and hurt overlapping players
 *     }
 * });
 * }</pre>
 * Then in any script: {@code create damageZone zone1 { damage = 5; x = 10; y = 64; z = 0; }}
 * — it shows up in the Hierarchy/Inspector exactly like a hand-declared custom node.
 */
public interface GenericNodeType {

    /** Stable id — this is the {@code typeId} used by {@code create <id> ... {}} in scripts. */
    String id();

    default String label() { return id(); }

    /** Inspector section / Hierarchy "create" grouping this type appears under. */
    default String section() { return "Engine"; }

    default String icon() { return "plugin"; }

    /**
     * True if instances of this type have a meaningful world position/rotation/scale (a hazard
     * zone, a trigger volume, …) — {@code create <id> name { x = …; y = …; z = …; }} then places
     * the node like a PARTICLE/LIGHT/HITBOX node instead of the data-only .spr CUSTOM default
     * (which ignores x/y/z on purpose). False by default to match today's CUSTOM behavior.
     */
    default boolean usesTransform() { return false; }

    /** Inspector fields, reusing the exact widgets {@code declare inspector} .spr schemas use. */
    List<CustomFieldDef> fields();

    /** Default {@code node.customData} for a freshly created instance. Override only if you need
     *  something fancier than "one entry per field's resolved default". */
    default Map<String, Object> defaultData() {
        Map<String, Object> data = new LinkedHashMap<>();
        for (CustomFieldDef f : fields()) {
            if (f == null || f.kind == CustomFieldKind.BUTTON) continue;
            data.put(f.id, f.resolvedDefault());
        }
        return data;
    }

    /** Called once, right after the node is attached to a live scene (script {@code create} or scene Play). */
    default void onCreate(SceneActiveState state, SceneNode node) {}

    /** Called once, right before the node is detached from a live scene ({@code /spraute stop}, node removed). */
    default void onRemove(SceneActiveState state, SceneNode node) {}

    /**
     * Explicit trigger — the {@code node.emit()} / {@code particleEmit(node)}-style script call,
     * generalized to any Java-declared type. No-op by default; override for a one-shot action.
     */
    default void emit(SceneActiveState state, ServerLevel level, SceneNode node) {}

    /**
     * Per-server-tick update, if this type needs one (a hazard zone, a timed effect, …).
     * Not wired to a tick loop automatically — call {@link GenericNodeTypeRegistry#tickAll}
     * once from wherever the engine already ticks scene runtimes per server tick (look for the
     * call site of {@code ParticleGameplayRuntime.tick(server)} and add a sibling call next to it).
     */
    default void tick(SceneActiveState state, ServerLevel level, SceneNode node) {}

    /**
     * Once per server tick for the type as a whole (not per node), before the per-node
     * {@link #tick} pass — for global housekeeping such as releasing runtime objects whose node
     * went inactive, got deleted or whose scene was stopped (none of which call {@link #tick}).
     */
    default void serverTick(net.minecraft.server.MinecraftServer server) {}

    /**
     * Editor only: called once when the node is created from the Hierarchy "add" menu, before it
     * is attached — lets a type pre-populate default children (e.g. a CANVAS for worldCanvas).
     * Not called for script {@code create} (scripts pass whatever they need as fields).
     */
    default void onEditorCreate(SceneNode node) {}
}
