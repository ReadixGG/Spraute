package org.zonarstudio.spraute_engine.entity.path;

/**
 * Tunable air pathfinding for flying {@code SprauteNpcEntity}.
 * Node fields, {@code create npc} props and inspector map 1:1 onto these values.
 */
public final class AirPathSettings {
    /** 3D arrive radius in blocks. 0.1 = tenths of a block. */
    public double arriveRadius = 0.1;
    public double maxRange = 48;
    public boolean allowDiagonal = true;
    public boolean avoidWater = false;
    public boolean avoidLava = true;
    public int maxNodes = 8192;
    public double waypointRadius = 0.55;
    public boolean snapOnArrive = true;
    public int repathTicks = 12;
    public int stuckTicks = 60;

    public AirPathSettings copy() {
        AirPathSettings c = new AirPathSettings();
        c.copyFrom(this);
        return c;
    }

    public void copyFrom(AirPathSettings o) {
        if (o == null) return;
        arriveRadius = o.arriveRadius;
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
