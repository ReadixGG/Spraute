package org.zonarstudio.spraute_engine.entity.path;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/** 3D A* through free air cells. Last waypoint is the exact world goal. */
public final class AirPathfinder {
    private static final double SQRT2 = Math.sqrt(2.0);
    private static final double SQRT3 = Math.sqrt(3.0);
    private static final int[][] CARDINAL = {
            {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}
    };
    private static final int[][] EDGES = {
            {1, 1, 0}, {1, -1, 0}, {-1, 1, 0}, {-1, -1, 0},
            {1, 0, 1}, {1, 0, -1}, {-1, 0, 1}, {-1, 0, -1},
            {0, 1, 1}, {0, 1, -1}, {0, -1, 1}, {0, -1, -1}
    };
    private static final int[][] CORNERS = {
            {1, 1, 1}, {1, 1, -1}, {1, -1, 1}, {1, -1, -1},
            {-1, 1, 1}, {-1, 1, -1}, {-1, -1, 1}, {-1, -1, -1}
    };

    private AirPathfinder() {}

    public static GroundPath find(Level level, AirPathSettings settings,
                                  Vec3 start, Vec3 goal, float bodyWidth, float bodyHeight) {
        if (level == null || settings == null || start == null || goal == null) return null;
        settings.clamp();
        float width = Math.max(0.2f, bodyWidth);
        float height = Math.max(0.6f, bodyHeight);

        AirCell startCell = locateFree(level, settings, start, width, height);
        AirCell goalCell = locateFree(level, settings, goal, width, height);
        if (startCell == null || goalCell == null) return null;

        double rangeSq = settings.maxRange * settings.maxRange;
        if (start.distanceToSqr(goal) > rangeSq * 1.15) return null;

        if (hasDirectFly(level, settings, start, goal, width, height)) {
            return GroundPath.of(goal);
        }

        List<AirCell> cells = astar(level, settings, startCell, goalCell, width, height, rangeSq);
        if (cells == null || cells.isEmpty()) return null;

        List<Vec3> raw = new ArrayList<>(cells.size() + 1);
        for (AirCell cell : cells) {
            raw.add(cell.center());
        }
        Vec3 exact = goal;
        if (isFree(level, settings, exact.x, exact.y, exact.z, width, height)) {
            if (raw.isEmpty() || raw.get(raw.size() - 1).distanceToSqr(exact) > 0.0001) {
                raw.add(exact);
            } else {
                raw.set(raw.size() - 1, exact);
            }
        }
        return new GroundPath(stringPull(level, settings, raw, width, height));
    }

    private static List<AirCell> astar(Level level, AirPathSettings settings,
                                       AirCell start, AirCell goal,
                                       float width, float height, double rangeSq) {
        PriorityQueue<SearchNode> open = new PriorityQueue<>(Comparator.comparingDouble(n -> n.f));
        Map<Long, Float> bestG = new HashMap<>();
        Map<Long, Boolean> freeCache = new HashMap<>();
        SearchNode origin = new SearchNode(start, 0f, heuristic(start, goal), null);
        open.add(origin);
        bestG.put(start.key(), 0f);

        int expanded = 0;
        while (!open.isEmpty() && expanded < settings.maxNodes) {
            SearchNode cur = open.poll();
            if (cur.cell.x == goal.x && cur.cell.y == goal.y && cur.cell.z == goal.z) {
                return reconstruct(cur);
            }
            expanded++;
            for (int[] d : neighbors(settings)) {
                int nx = cur.cell.x + d[0];
                int ny = cur.cell.y + d[1];
                int nz = cur.cell.z + d[2];
                double dx = (nx + 0.5) - (start.x + 0.5);
                double dy = ny - start.y;
                double dz = (nz + 0.5) - (start.z + 0.5);
                if (dx * dx + dy * dy + dz * dz > rangeSq) continue;
                if (!cachedFree(level, settings, freeCache, nx, ny, nz, width, height)) continue;
                if (isDiagonal(d) && cutsCorner(level, settings, freeCache, cur.cell, d, width, height)) continue;
                AirCell next = new AirCell(nx, ny, nz);
                float g = cur.g + stepCost(d);
                long key = next.key();
                Float prev = bestG.get(key);
                if (prev != null && g >= prev - 1.0e-4f) continue;
                bestG.put(key, g);
                open.add(new SearchNode(next, g, g + heuristic(next, goal), cur));
            }
        }
        return null;
    }

    private static int[][] neighbors(AirPathSettings settings) {
        if (!settings.allowDiagonal) return CARDINAL;
        return ALL_DIAG;
    }

    private static final int[][] ALL_DIAG;

    static {
        ALL_DIAG = new int[CARDINAL.length + EDGES.length + CORNERS.length][];
        int i = 0;
        for (int[] d : CARDINAL) ALL_DIAG[i++] = d;
        for (int[] d : EDGES) ALL_DIAG[i++] = d;
        for (int[] d : CORNERS) ALL_DIAG[i++] = d;
    }

    private static boolean isDiagonal(int[] d) {
        return (Math.abs(d[0]) + Math.abs(d[1]) + Math.abs(d[2])) > 1;
    }

    private static boolean cutsCorner(Level level, AirPathSettings settings, Map<Long, Boolean> cache,
                                      AirCell from, int[] d, float width, float height) {
        if (d[0] != 0 && !cachedFree(level, settings, cache, from.x + d[0], from.y, from.z, width, height)) {
            return true;
        }
        if (d[1] != 0 && !cachedFree(level, settings, cache, from.x, from.y + d[1], from.z, width, height)) {
            return true;
        }
        if (d[2] != 0 && !cachedFree(level, settings, cache, from.x, from.y, from.z + d[2], width, height)) {
            return true;
        }
        return false;
    }

    private static List<AirCell> reconstruct(SearchNode end) {
        List<AirCell> cells = new ArrayList<>();
        for (SearchNode n = end; n != null; n = n.parent) {
            cells.add(n.cell);
        }
        Collections.reverse(cells);
        return cells;
    }

    private static float stepCost(int[] d) {
        int n = Math.abs(d[0]) + Math.abs(d[1]) + Math.abs(d[2]);
        if (n >= 3) return (float) SQRT3;
        if (n == 2) return (float) SQRT2;
        return 1f;
    }

    private static float heuristic(AirCell a, AirCell b) {
        int dx = Math.abs(a.x - b.x);
        int dy = Math.abs(a.y - b.y);
        int dz = Math.abs(a.z - b.z);
        int max = Math.max(dx, Math.max(dy, dz));
        int min = Math.min(dx, Math.min(dy, dz));
        int mid = dx + dy + dz - max - min;
        return (float) ((max - mid) + SQRT2 * (mid - min) + SQRT3 * min);
    }

    private static AirCell locateFree(Level level, AirPathSettings settings,
                                      Vec3 pos, float width, float height) {
        int x = Mth.floor(pos.x);
        int y = Mth.floor(pos.y);
        int z = Mth.floor(pos.z);
        if (isFree(level, settings, x + 0.5, pos.y, z + 0.5, width, height)
                || isFree(level, settings, x + 0.5, y, z + 0.5, width, height)) {
            return new AirCell(x, y, z);
        }
        for (int r = 1; r <= 3; r++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dx = -r; dx <= r; dx++) {
                    for (int dz = -r; dz <= r; dz++) {
                        if (Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz))) != r) continue;
                        int nx = x + dx;
                        int ny = y + dy;
                        int nz = z + dz;
                        if (isFree(level, settings, nx + 0.5, ny, nz + 0.5, width, height)) {
                            return new AirCell(nx, ny, nz);
                        }
                    }
                }
            }
        }
        return null;
    }

    private static boolean cachedFree(Level level, AirPathSettings settings, Map<Long, Boolean> cache,
                                      int x, int y, int z, float width, float height) {
        long key = pack(x, y, z);
        Boolean hit = cache.get(key);
        if (hit != null) return hit;
        boolean free = isFree(level, settings, x + 0.5, y, z + 0.5, width, height);
        cache.put(key, free);
        return free;
    }

    private static boolean isFree(Level level, AirPathSettings settings,
                                  double cx, double y, double cz,
                                  float width, float height) {
        double hw = width * 0.5;
        AABB box = new AABB(cx - hw, y + 0.02, cz - hw,
                cx + hw, y + height - 0.02, cz + hw);
        int minX = Mth.floor(box.minX);
        int maxX = Mth.floor(box.maxX);
        int minY = Mth.floor(box.minY);
        int maxY = Mth.floor(box.maxY);
        int minZ = Mth.floor(box.minZ);
        int maxZ = Mth.floor(box.maxZ);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int by = minY; by <= maxY; by++) {
                for (int z = minZ; z <= maxZ; z++) {
                    pos.set(x, by, z);
                    if (!level.isInWorldBounds(pos) || !level.hasChunkAt(pos)) return false;
                    if (isBannedFluid(level, settings, pos)) return false;
                    VoxelShape shape = level.getBlockState(pos).getCollisionShape(level, pos);
                    if (shape.isEmpty()) continue;
                    if (Shapes.joinIsNotEmpty(shape.move(x, by, z), Shapes.create(box), BooleanOp.AND)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private static boolean isBannedFluid(Level level, AirPathSettings settings, BlockPos pos) {
        FluidState fluid = level.getFluidState(pos);
        if (fluid.isEmpty()) return false;
        if (settings.avoidLava && fluid.is(FluidTags.LAVA)) return true;
        if (settings.avoidWater && fluid.is(FluidTags.WATER)) {
            BlockState state = level.getBlockState(pos);
            return state.getCollisionShape(level, pos).isEmpty();
        }
        return false;
    }

    private static boolean hasDirectFly(Level level, AirPathSettings settings,
                                        Vec3 from, Vec3 to, float width, float height) {
        double dist = from.distanceTo(to);
        if (dist < 1.0e-4) return true;
        if (dist > settings.maxRange) return false;
        int steps = Math.max(2, (int) Math.ceil(dist * 2.0));
        for (int i = 1; i <= steps; i++) {
            double t = (double) i / steps;
            double x = from.x + (to.x - from.x) * t;
            double y = from.y + (to.y - from.y) * t;
            double z = from.z + (to.z - from.z) * t;
            if (!isFree(level, settings, x, y, z, width, height)) return false;
        }
        return true;
    }

    private static List<Vec3> stringPull(Level level, AirPathSettings settings,
                                         List<Vec3> raw, float width, float height) {
        if (raw.size() <= 2) return raw;
        List<Vec3> out = new ArrayList<>();
        int i = 0;
        out.add(raw.get(0));
        while (i < raw.size() - 1) {
            int best = i + 1;
            for (int j = raw.size() - 1; j > i + 1; j--) {
                if (hasDirectFly(level, settings, raw.get(i), raw.get(j), width, height)) {
                    best = j;
                    break;
                }
            }
            out.add(raw.get(best));
            i = best;
        }
        return out;
    }

    private static long pack(int x, int y, int z) {
        return ((x & 0x3FFFFFFL) << 38) | ((z & 0x3FFFFFFL) << 12) | (y + 2048 & 0xFFF);
    }

    private static final class AirCell {
        final int x;
        final int y;
        final int z;

        AirCell(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        long key() {
            return pack(x, y, z);
        }

        Vec3 center() {
            return new Vec3(x + 0.5, y, z + 0.5);
        }
    }

    private static final class SearchNode {
        final AirCell cell;
        final float g;
        final float f;
        final SearchNode parent;

        SearchNode(AirCell cell, float g, float f, SearchNode parent) {
            this.cell = cell;
            this.g = g;
            this.f = f;
            this.parent = parent;
        }
    }
}
