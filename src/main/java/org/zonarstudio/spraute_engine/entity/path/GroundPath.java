package org.zonarstudio.spraute_engine.entity.path;

import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Ordered ground waypoints. The last point is the exact scripted goal (fractional XZ). */
public final class GroundPath {
    private final List<Vec3> waypoints;

    public GroundPath(List<Vec3> waypoints) {
        this.waypoints = waypoints == null ? List.of() : List.copyOf(waypoints);
    }

    public static GroundPath of(Vec3... points) {
        List<Vec3> list = new ArrayList<>();
        if (points != null) {
            Collections.addAll(list, points);
        }
        return new GroundPath(list);
    }

    public boolean isEmpty() {
        return waypoints.isEmpty();
    }

    public int size() {
        return waypoints.size();
    }

    public Vec3 get(int index) {
        return waypoints.get(index);
    }

    public Vec3 last() {
        return waypoints.get(waypoints.size() - 1);
    }

    public List<Vec3> waypoints() {
        return waypoints;
    }
}
