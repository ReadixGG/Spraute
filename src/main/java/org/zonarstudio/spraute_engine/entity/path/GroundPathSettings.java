package org.zonarstudio.spraute_engine.entity.path;

/**
 * Tunable ground pathfinding for {@code SprauteNpcEntity}.
 * Node fields, {@code create npc} props and inspector all map 1:1 onto these values.
 */
public final class GroundPathSettings {
    /** Horizontal arrive radius in blocks. 0.1 = tenths of a block. */
    public double arriveRadius = 0.1;
    /** Allowed Y difference when considering the goal reached. */
    public double arriveVert = 1.5;
    /** Max vertical rise without jumping (stairs, slabs). Vanilla default ≈0.6. */
    public double stepHeight = 0.6;
    /** Max blocks up in one step (jump). */
    public int maxJump = 1;
    /** Max blocks down in one step. */
    public int maxDrop = 3;
    /** Max search radius from the start (blocks). */
    public double maxRange = 48;
    public boolean allowDiagonal = true;
    public boolean avoidWater = true;
    public boolean avoidLava = true;
    /** A* expansion cap. */
    public int maxNodes = 8192;
    /** How close to an intermediate waypoint before advancing. */
    public double waypointRadius = 0.4;
    /** Snap XZ to the exact goal when inside {@link #arriveRadius}. */
    public boolean snapOnArrive = true;
    public int repathTicks = 12;
    public int stuckTicks = 60;
    /** When true, never skip A* even if the straight line looks walkable. */
    public boolean skipDirectWalk = false;

    public GroundPathSettings copy() {
        GroundPathSettings c = new GroundPathSettings();
        c.copyFrom(this);
        return c;
    }

    public void copyFrom(GroundPathSettings o) {
        if (o == null) return;
        arriveRadius = o.arriveRadius;
        arriveVert = o.arriveVert;
        stepHeight = o.stepHeight;
        maxJump = o.maxJump;
        maxDrop = o.maxDrop;
        maxRange = o.maxRange;
        allowDiagonal = o.allowDiagonal;
        avoidWater = o.avoidWater;
        avoidLava = o.avoidLava;
        maxNodes = o.maxNodes;
        waypointRadius = o.waypointRadius;
        snapOnArrive = o.snapOnArrive;
        repathTicks = o.repathTicks;
        stuckTicks = o.stuckTicks;
        clamp();
    }

    public void clamp() {
        arriveRadius = clamp(arriveRadius, 0.02, 4.0);
        arriveVert = clamp(arriveVert, 0.25, 16.0);
        stepHeight = clamp(stepHeight, 0.1, 2.0);
        maxJump = clampInt(maxJump, 0, 4);
        maxDrop = clampInt(maxDrop, 0, 16);
        maxRange = clamp(maxRange, 4.0, 256.0);
        maxNodes = clampInt(maxNodes, 256, 65536);
        waypointRadius = clamp(waypointRadius, 0.15, 2.0);
        repathTicks = clampInt(repathTicks, 1, 80);
        stuckTicks = clampInt(stuckTicks, 10, 400);
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    private static int clampInt(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
