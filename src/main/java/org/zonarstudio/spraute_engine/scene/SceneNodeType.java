package org.zonarstudio.spraute_engine.scene;

import java.util.EnumSet;
import java.util.Set;

/**
 * Node types with capability API. Future node types can restrict children,
 * forbid reparenting or deletion without touching the hierarchy UI code.
 */
public enum SceneNodeType {
    SCENE,
    /** System container present in every scene; its PLAYER children mirror online players. */
    PLAYERS,
    /**
     * Singleton container: children are shared world content — spawned once, ticked once,
     * visible/interactable by every connected player at once. This is the old (pre-worlds-split)
     * root behavior, just moved one level down.
     */
    GLOBAL_WORLD,
    /**
     * Singleton container: children are a per-player <b>template</b>. The engine instantiates
     * one private copy of this whole subtree (NPC/model/hitbox/particle/light/audio/script/canvas)
     * for every connected player — visible and interactable only by that one player, all other
     * players' copies invisible/non-solid to them. The node itself under LOCAL_WORLD is never
     * spawned directly; only per-player instances are.
     */
    LOCAL_WORLD,
    /** Live player view. Dynamic PLAYER nodes are never persisted in .scene.json. */
    PLAYER,
    /** Per-player camera rest pose. Lives under PLAYER and is never persisted. */
    CAMERA,
    /**
     * Per-player personal audio bus (non-positional). Lives under PLAYER;
     * script {@code playSound} / {@code player.audio} go through this node.
     */
    AUDIO,
    /** Full-skin overlay composited on top of the player's current skin. Child of PLAYER. */
    SKIN_OVERLAY,
    EMPTY,
    NPC,
    MODEL,
    BILLBOARD,
    HITBOX,
    PARTICLE,
    LIGHT,
    /** Positional looping sound with distance falloff (sphere or cube). */
    AUDIO_SOURCE,
    /** Camera post-process volume (Unity Volume / Unreal Post Process Volume). */
    POST_PROCESS,
    SCRIPT,
    /** Visual script leaf: UI opens .sprv, runtime runs sibling .spr. */
    VISUAL_SCRIPT,
    ANIMATOR,
    /** Ordered path saved to spraute_engine/routes. */
    ROUTE,
    /** Position-only child of a ROUTE node. */
    ROUTE_POINT,
    // GUI: canvas root + 2D widgets (transform = pixels/degrees on the canvas)
    CANVAS,
    UI_GRID,
    UI_TEXT,
    UI_BUTTON,
    UI_IMAGE,
    /** MP4/H.264 video widget (client JPEG cache {@code .spvc}, progressive decode). */
    UI_VIDEO,
    /** Workspace {@code .svg} rasterized to a dynamic GUI texture. */
    UI_SVG,
    UI_RECT,
    UI_INPUT,
    UI_SCROLL,
    UI_DIVIDER,
    UI_ITEM,
    UI_BLOCK,
    UI_ENTITY,
    UI_CROP,
    UI_GROUP,
    /** Draggable GUI container: children move with the node. */
    UI_DRAG,
    /** Drop zone for {@link #UI_DRAG} widgets (matched by group). */
    UI_DROPTARGET,
    // Container slots: a single item slot and the 9x4 player inventory block.
    UI_SLOT,
    UI_INVENTORY,
    // Register-time resource definitions (not spawned into the world)
    RES_BLOCK,
    RES_ITEM,
    RES_CRAFT,
    RES_TAB,
    RES_DROP,
    RES_WORLD,
    /** Custom particle type def ({@code create particle id { texture = … }}). */
    RES_PARTICLE,
    /** Custom projectile def ({@code create projectile id { … }}). */
    RES_PROJECTILE,
    /** Schema-driven data node from {@code declare node type} (no world/canvas transform). */
    CUSTOM;

    private static final Set<SceneNodeType> UI_WIDGETS =
            EnumSet.of(UI_GRID, UI_TEXT, UI_BUTTON, UI_IMAGE, UI_VIDEO, UI_SVG, UI_RECT, UI_INPUT,
                    UI_SCROLL, UI_DIVIDER, UI_ITEM, UI_BLOCK, UI_ENTITY, UI_CROP, UI_GROUP,
                    UI_DRAG, UI_DROPTARGET, UI_SLOT, UI_INVENTORY);

    private static final Set<SceneNodeType> RESOURCE_DEFS =
            EnumSet.of(RES_BLOCK, RES_ITEM, RES_CRAFT, RES_TAB, RES_DROP, RES_WORLD, RES_PARTICLE, RES_PROJECTILE);

    /** True for canvas widget nodes (not the canvas itself). */
    public boolean isUiWidget() {
        return UI_WIDGETS.contains(this);
    }

    /** True for anything belonging to the GUI subtree (canvas or widget). */
    public boolean isUi() {
        return this == CANVAS || isUiWidget();
    }

    /** True for register-time resource definition nodes (block/item/craft/…). */
    public boolean isResourceDef() {
        return RESOURCE_DEFS.contains(this);
    }

    /** Custom schema nodes and resource defs are data-only (no transform). */
    public boolean isDataOnly() {
        return isResourceDef() || this == CUSTOM || this == PLAYERS || this == PLAYER
                || this == SKIN_OVERLAY || this == ROUTE || this == AUDIO
                || this == GLOBAL_WORLD || this == LOCAL_WORLD;
    }

    /** Whether this node may contain children at all. */
    public boolean acceptsChildren() {
        return this != SCRIPT && this != VISUAL_SCRIPT && this != ANIMATOR
                && this != SKIN_OVERLAY && this != ROUTE_POINT;
    }

    /** Regular or visual script leaf (both run via ScriptManager on .spr). */
    public boolean isScriptLeaf() {
        return this == SCRIPT || this == VISUAL_SCRIPT;
    }

    /** Which child types can be dropped into this node. */
    public Set<SceneNodeType> allowedChildTypes() {
        return switch (this) {
            // Nothing placeable directly under the scene root any more — everything world-shaped
            // goes either under GLOBAL_WORLD (shared) or LOCAL_WORLD (per-player template).
            case SCENE -> EnumSet.of(PLAYERS, GLOBAL_WORLD, LOCAL_WORLD);
            case GLOBAL_WORLD -> {
                EnumSet<SceneNodeType> set = EnumSet.of(
                        EMPTY, NPC, MODEL, BILLBOARD, HITBOX, PARTICLE, LIGHT, AUDIO_SOURCE,
                        SCRIPT, VISUAL_SCRIPT, ANIMATOR, ROUTE, CANVAS, CUSTOM);
                set.addAll(RESOURCE_DEFS);
                yield set;
            }
            // Same content types as GLOBAL_WORLD, minus resource defs (block/item/craft/… are
            // register-time definitions, not per-player world content — they only make sense once).
            case LOCAL_WORLD -> EnumSet.of(
                    EMPTY, NPC, MODEL, BILLBOARD, HITBOX, PARTICLE, LIGHT, AUDIO_SOURCE,
                    SCRIPT, VISUAL_SCRIPT, ANIMATOR, ROUTE, CANVAS, CUSTOM);
            case EMPTY -> {
                EnumSet<SceneNodeType> set = EnumSet.of(
                        EMPTY, NPC, MODEL, BILLBOARD, HITBOX, PARTICLE, LIGHT, AUDIO_SOURCE,
                        SCRIPT, VISUAL_SCRIPT, ANIMATOR, ROUTE, CANVAS, CUSTOM);
                set.addAll(RESOURCE_DEFS);
                yield set;
            }
            case PLAYERS -> EnumSet.of(PLAYER);
            case PLAYER -> EnumSet.of(CAMERA, AUDIO, AUDIO_SOURCE, SKIN_OVERLAY);
            case CAMERA -> EnumSet.of(POST_PROCESS);
            case AUDIO, SKIN_OVERLAY -> EnumSet.noneOf(SceneNodeType.class);
            case NPC, MODEL, BILLBOARD, HITBOX, PARTICLE, LIGHT, AUDIO_SOURCE ->
                    EnumSet.of(EMPTY, NPC, MODEL, BILLBOARD, HITBOX, PARTICLE, LIGHT, AUDIO_SOURCE,
                            SCRIPT, VISUAL_SCRIPT, ANIMATOR, CANVAS, CUSTOM);
            case POST_PROCESS -> EnumSet.noneOf(SceneNodeType.class);
            case ROUTE -> EnumSet.of(
                    ROUTE_POINT, EMPTY, NPC, MODEL, BILLBOARD, HITBOX, PARTICLE, LIGHT, AUDIO_SOURCE);
            case ROUTE_POINT -> EnumSet.noneOf(SceneNodeType.class);
            case CANVAS, UI_GRID, UI_RECT, UI_SCROLL, UI_CROP, UI_GROUP, UI_DRAG, UI_DROPTARGET -> {
                EnumSet<SceneNodeType> set = EnumSet.copyOf(UI_WIDGETS);
                set.add(SCRIPT);
                set.add(VISUAL_SCRIPT);
                set.add(ANIMATOR);
                yield set;
            }
            case UI_TEXT, UI_BUTTON, UI_IMAGE, UI_VIDEO, UI_SVG, UI_INPUT,
                    UI_DIVIDER, UI_ITEM, UI_BLOCK, UI_ENTITY, UI_SLOT, UI_INVENTORY ->
                    EnumSet.of(SCRIPT, VISUAL_SCRIPT, ANIMATOR);
            case RES_BLOCK, RES_ITEM, RES_CRAFT, RES_TAB, RES_DROP, RES_WORLD, RES_PARTICLE, RES_PROJECTILE ->
                    EnumSet.of(ANIMATOR);
            // CANVAS: content of a Java-declared type that renders a GUI in the world (worldCanvas).
            case CUSTOM -> EnumSet.of(ANIMATOR, CANVAS);
            case SCRIPT, VISUAL_SCRIPT, ANIMATOR ->
                    EnumSet.noneOf(SceneNodeType.class);
        };
    }

    /** Scene roots stay put; everything else may be dragged to a new parent. */
    public boolean canReparent() {
        return this != SCENE && this != PLAYERS && this != PLAYER && this != CAMERA
                && this != AUDIO && this != POST_PROCESS
                && this != GLOBAL_WORLD && this != LOCAL_WORLD;
    }

    public boolean canDelete() {
        return this != SCENE && this != PLAYERS && this != PLAYER && this != CAMERA
                && this != AUDIO && this != POST_PROCESS
                && this != GLOBAL_WORLD && this != LOCAL_WORLD;
    }

    /** Whether the Inspector shows transform fields (scene root / resources / logic leaves). */
    public boolean hasTransform() {
        return this != SCENE && this != PLAYERS && this != PLAYER && this != ROUTE
                && this != SCRIPT && this != VISUAL_SCRIPT && this != ANIMATOR
                && this != POST_PROCESS && this != AUDIO
                && !isResourceDef() && this != CUSTOM && this != SKIN_OVERLAY
                && this != GLOBAL_WORLD && this != LOCAL_WORLD;
    }
}
