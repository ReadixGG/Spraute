package org.zonarstudio.spraute_engine.scene.custom;

/**
 * Legacy marker for the old {@code worldCanvas} CUSTOM node — a data node that wrapped a child
 * CANVAS and drew it in the world.
 *
 * <p>A 3D canvas is no longer a node type of its own: a CANVAS carries its own render mode
 * ({@code canvasWorldSpace}) and holds the widgets directly, so it is created straight from the
 * GUI menu, inspected like any canvas, and driven by
 * {@link org.zonarstudio.spraute_engine.scene.SceneWorldCanvasRuntime}. Nothing registers this
 * type any more; only {@code SceneRepository}'s format-5 migration still needs the old id to
 * recognise wrappers in scenes saved before the change.
 */
public final class WorldCanvasNodeType {
    /** {@code customTypeId} of the pre-format-5 wrapper node. */
    public static final String ID = "worldCanvas";

    private WorldCanvasNodeType() {}
}
