package org.zonarstudio.spraute_engine.entity.path;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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

/** Block-column A* for walking NPCs. Last waypoint is the exact world goal. */
public final class GroundPathfinder {
    private static final double SQRT2 = Math.sqrt(2.0);
    private static final int[][] CARDINAL = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    private static final int[][] DIAGONAL = {{1, 1}, {1, -1}, {-1, 1}, {-1, -1}};

    private GroundPathfinder() {}

    public static GroundPath find(Level level, GroundPathSettings settings,
                                  Vec3 start, Vec3 goal, float bodyWidth, float bodyHeight) {
        if (level == null || settings == null || start == null || goal == null) return null;
        settings.clamp();
        float width = Math.max(0.2f, bodyWidth);
        float height = Math.max(0.6f, bodyHeight);

        StandCell startCell = locateStand(level, settings, start, width, height);
        StandCell goalCell = locateStand(level, settings, goal, width, height);
        if (startCell == null || goalCell == null) return null;

        double rangeSq = settings.maxRange * settings.maxRange;
        if (start.distanceToSqr(goal) > rangeSq * 1.15) return null;

        if (!settings.skipDirectWalk && hasDirectWalk(level, settings, start, goal, width, height)) {
            return GroundPath.of(goal);
        }

        List<StandCell> cells = astar(level, settings, startCell, goalCell, width, height, rangeSq);
        if (cells == null || cells.isEmpty()) return null;

        List<Vec3> raw = new ArrayList<>(cells.size() + 1);
        for (StandCell cell : cells) {
            raw.add(cell.standPos());
        }
        Vec3 exact = new Vec3(goal.x, goalCell.standY, goal.z);
        if (raw.isEmpty() || raw.get(raw.size() - 1).distanceToSqr(exact) > 0.0001) {
            raw.add(exact);
        } else {
            raw.set(raw.size() - 1, exact);
        }
        return new GroundPath(stringPull(level, settings, raw, width, height));
    }

    /**
     * Unsimplified block-by-block path for route visualization and authored paths.
     * Unlike {@link #find}, this keeps every traversed block column.
     */
    public static GroundPath findGrid(Level level, GroundPathSettings settings,
                                      Vec3 start, Vec3 goal, float bodyWidth, float bodyHeight) {
        if (level == null || settings == null || start == null || goal == null) return null;
        settings.clamp();
        float width = Math.max(0.2f, bodyWidth);
        float height = Math.max(0.6f, bodyHeight);
        StandCell startCell = locateStand(level, settings, start, width, height);
        StandCell goalCell = locateStand(level, settings, goal, width, height);
        if (startCell == null || goalCell == null) return null;
        double rangeSq = settings.maxRange * settings.maxRange;
        if (start.distanceToSqr(goal) > rangeSq * 1.15) return null;
        List<StandCell> cells = astar(level, settings, startCell, goalCell, width, height, rangeSq);
        if (cells == null || cells.isEmpty()) return null;
        List<Vec3> raw = new ArrayList<>(cells.size());
        for (StandCell cell : cells) raw.add(cell.standPos());
        return new GroundPath(raw);
    }

    private static List<StandCell> astar(Level level, GroundPathSettings settings,
                                         StandCell start, StandCell goal,
                                         float width, float height, double rangeSq) {
        PriorityQueue<SearchNode> open = new PriorityQueue<>(Comparator.comparingDouble(n -> n.f));
        Map<Long, Float> bestG = new HashMap<>();
        SearchNode origin = new SearchNode(start, 0f, heuristic(start, goal), null);
        open.add(origin);
        bestG.put(start.key(), 0f);

        int expanded = 0;
        while (!open.isEmpty() && expanded < settings.maxNodes) {
            SearchNode cur = open.poll();
            if (cur.cell.x == goal.x && cur.cell.z == goal.z
                    && Math.abs(cur.cell.floorY - goal.floorY) <= 1) {
                return reconstruct(cur, goal);
            }
            expanded++;
            for (StandCell next : neighbors(level, settings, cur.cell, width, height)) {
                double dx = (next.x + 0.5) - (start.x + 0.5);
                double dz = (next.z + 0.5) - (start.z + 0.5);
                if (dx * dx + dz * dz > rangeSq) continue;
                float step = stepCost(settings, cur.cell, next);
                float g = cur.g + step;
                long key = next.key();
                Float prev = bestG.get(key);
                if (prev != null && g >= prev - 1.0e-4f) continue;
                bestG.put(key, g);
                open.add(new SearchNode(next, g, g + heuristic(next, goal), cur));
            }
        }
        return null;
    }

    private static List<StandCell> reconstruct(SearchNode end, StandCell goal) {
        List<StandCell> cells = new ArrayList<>();
        for (SearchNode n = end; n != null; n = n.parent) {
            cells.add(n.cell);
        }
        Collections.reverse(cells);
        StandCell last = cells.get(cells.size() - 1);
        if (last.x != goal.x || last.z != goal.z || last.floorY != goal.floorY) {
            cells.add(goal);
        }
        return cells;
    }

    private static List<StandCell> neighbors(Level level, GroundPathSettings settings,
                                             StandCell from, float width, float height) {
        List<StandCell> out = new ArrayList<>(settings.allowDiagonal ? 8 : 4);
        addDirs(level, settings, from, width, height, CARDINAL, false, out);
        if (settings.allowDiagonal) {
            addDirs(level, settings, from, width, height, DIAGONAL, true, out);
        }
        return out;
    }

    private static void addDirs(Level level, GroundPathSettings settings, StandCell from,
                                float width, float height, int[][] dirs, boolean diagonal,
                                List<StandCell> out) {
        for (int[] d : dirs) {
            int nx = from.x + d[0];
            int nz = from.z + d[1];
            if (diagonal && cutsCorner(level, settings, from, d[0], d[1], width, height)) continue;
            StandCell next = findStepFloor(level, settings, from, nx, nz, width, height);
            if (next != null) out.add(next);
        }
    }

    private static boolean cutsCorner(Level level, GroundPathSettings settings, StandCell from,
                                      int dx, int dz, float width, float height) {
        StandCell a = findFloor(level, settings, from.x + dx, from.z, from.floorY, width, height);
        StandCell b = findFloor(level, settings, from.x, from.z + dz, from.floorY, width, height);
        return a == null && b == null;
    }

    private static float stepCost(GroundPathSettings settings, StandCell from, StandCell to) {
        int adx = Math.abs(to.x - from.x);
        int adz = Math.abs(to.z - from.z);
        float base = (adx != 0 && adz != 0) ? (float) SQRT2 : 1f;
        double rise = to.standY - from.standY;
        double drop = from.standY - to.standY;
        if (rise > settings.stepHeight + 0.02) {
            base += 0.45f + 0.12f * (float) rise;
        } else if (rise > 0) {
            base += 0.06f * (float) rise;
        }
        if (drop > 0) base += 0.15f * (float) drop;
        int dyBlock = to.floorY - from.floorY;
        if (dyBlock > 0 && rise <= settings.stepHeight + 0.02) base += 0.02f * dyBlock;
        return base;
    }

    private static float heuristic(StandCell a, StandCell b) {
        int dx = Math.abs(a.x - b.x);
        int dz = Math.abs(a.z - b.z);
        int dy = Math.abs(a.floorY - b.floorY);
        int min = Math.min(dx, dz);
        int max = Math.max(dx, dz);
        return (float) ((max - min) + SQRT2 * min + dy * 1.05);
    }

    private static StandCell locateStand(Level level, GroundPathSettings settings,
                                         Vec3 pos, float width, float height) {
        int x = Mth.floor(pos.x);
        int z = Mth.floor(pos.z);
        int around = Mth.floor(pos.y);
        StandCell direct = findFloor(level, settings, x, z, around, width, height);
        if (direct != null) return direct;
        for (int r = 1; r <= 3; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.abs(dx) != r && Math.abs(dz) != r) continue;
                    StandCell cell = findFloor(level, settings, x + dx, z + dz, around, width, height);
                    if (cell != null) return cell;
                }
            }
        }
        return null;
    }

    private static StandCell findFloor(Level level, GroundPathSettings settings,
                                       int x, int z, int aroundY, float width, float height) {
        return findFloorInRange(level, settings, x, z,
                aroundY + settings.maxJump, aroundY - settings.maxDrop - 1,
                null, width, height, true);
    }

    /** Neighbor floor: closest reachable standY to {@code from} (stairs/slabs). */
    private static StandCell findStepFloor(Level level, GroundPathSettings settings,
                                           StandCell from, int x, int z,
                                           float width, float height) {
        int y0 = Mth.floor(from.standY + settings.maxJump + 0.5);
        int y1 = Mth.floor(from.standY - settings.maxDrop - 1);
        return findFloorInRange(level, settings, x, z, y0, y1, from, width, height, false);
    }

    private static StandCell findFloorInRange(Level level, GroundPathSettings settings,
                                              int x, int z, int y0, int y1,
                                              StandCell from, float width, float height,
                                              boolean preferHighest) {
        StandCell best = null;
        double bestDy = Double.POSITIVE_INFINITY;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = y0; y >= y1; y--) {
            StandCell cell = tryFloorCell(level, settings, x, z, y, pos, width, height);
            if (cell == null) continue;
            if (from != null && !isReachable(settings, from, cell)) continue;
            if (from == null && preferHighest) return cell;
            double dy = Math.abs(cell.standY - from.standY);
            if (dy < bestDy) {
                bestDy = dy;
                best = cell;
            }
        }
        return best;
    }

    private static StandCell tryFloorCell(Level level, GroundPathSettings settings,
                                          int x, int z, int y, BlockPos.MutableBlockPos pos,
                                          float width, float height) {
        if (!level.isInWorldBounds(pos.set(x, y, z))) return null;
        if (!level.hasChunkAt(pos)) return null;
        BlockState floor = level.getBlockState(pos);
        VoxelShape shape = floor.getCollisionShape(level, pos);
        if (shape.isEmpty()) return null;
        if (isBannedFluid(level, settings, pos.above())) return null;
        double standY = standYAt(shape, y, 0.5, 0.5);
        if (standY <= y) {
            double mx = shape.max(Direction.Axis.Y);
            if (mx <= 0.0) return null;
            standY = y + mx;
        }
        if (standY <= y) return null;
        if (!bodyClear(level, settings, x + 0.5, standY, z + 0.5, width, height)) return null;
        return new StandCell(x, y, z, standY);
    }

    private static boolean isReachable(GroundPathSettings settings, StandCell from, StandCell to) {
        double rise = to.standY - from.standY;
        double drop = from.standY - to.standY;
        if (drop > settings.maxDrop + 0.25) return false;
        return rise <= settings.maxJump + 0.35;
    }

    /**
     * Lowest collision top at (x,z) that is above {@code feetY}.
     * Stairs expose a 0.5 lip first; a full block exposes 1.0.
     */
    static double firstLipAbove(Level level, double x, double z, double feetY, double maxUp) {
        if (level == null) return Double.NEGATIVE_INFINITY;
        int bx = Mth.floor(x);
        int bz = Mth.floor(z);
        double lx = x - bx;
        double lz = z - bz;
        int y0 = Mth.floor(feetY);
        int y1 = Mth.floor(feetY + Math.max(0.1, maxUp));
        double best = Double.POSITIVE_INFINITY;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = y0; y <= y1; y++) {
            if (!level.isInWorldBounds(pos.set(bx, y, bz))) continue;
            if (!level.hasChunkAt(pos)) continue;
            VoxelShape shape = level.getBlockState(pos).getCollisionShape(level, pos);
            if (shape.isEmpty()) continue;
            for (AABB box : shape.toAabbs()) {
                if (lx < box.minX - 1.0e-3 || lx > box.maxX + 1.0e-3
                        || lz < box.minZ - 1.0e-3 || lz > box.maxZ + 1.0e-3) {
                    continue;
                }
                double top = y + box.maxY;
                if (top > feetY + 0.02 && top < best) best = top;
            }
        }
        return best == Double.POSITIVE_INFINITY ? Double.NEGATIVE_INFINITY : best;
    }

    /** Stand height at a local XZ inside the block (stairs/slabs), else the shape max. */
    private static double standYAt(VoxelShape shape, int y, double lx, double lz) {
        double top = Double.NEGATIVE_INFINITY;
        for (AABB box : shape.toAabbs()) {
            if (lx + 1.0e-4 >= box.minX && lx - 1.0e-4 <= box.maxX
                    && lz + 1.0e-4 >= box.minZ && lz - 1.0e-4 <= box.maxZ) {
                top = Math.max(top, box.maxY);
            }
        }
        return top == Double.NEGATIVE_INFINITY ? Double.NEGATIVE_INFINITY : y + top;
    }

    private static boolean bodyClear(Level level, GroundPathSettings settings,
                                     double cx, double standY, double cz,
                                     float width, float height) {
        double hw = width * 0.5;
        AABB box = new AABB(cx - hw, standY + 0.02, cz - hw,
                cx + hw, standY + height - 0.02, cz + hw);
        int minX = Mth.floor(box.minX);
        int maxX = Mth.floor(box.maxX);
        int minY = Mth.floor(box.minY);
        int maxY = Mth.floor(box.maxY);
        int minZ = Mth.floor(box.minZ);
        int maxZ = Mth.floor(box.maxZ);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    pos.set(x, y, z);
                    if (!level.hasChunkAt(pos)) return false;
                    if (isBannedFluid(level, settings, pos)) return false;
                    VoxelShape shape = level.getBlockState(pos).getCollisionShape(level, pos);
                    if (shape.isEmpty()) continue;
                    if (Shapes.joinIsNotEmpty(shape.move(x, y, z), Shapes.create(box), BooleanOp.AND)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private static boolean isBannedFluid(Level level, GroundPathSettings settings, BlockPos pos) {
        FluidState fluid = level.getFluidState(pos);
        if (fluid.isEmpty()) return false;
        if (settings.avoidLava && fluid.is(FluidTags.LAVA)) return true;
        if (settings.avoidWater && fluid.is(FluidTags.WATER)) {
            BlockState state = level.getBlockState(pos);
            return state.getCollisionShape(level, pos).isEmpty();
        }
        return false;
    }

    private static boolean hasDirectWalk(Level level, GroundPathSettings settings,
                                         Vec3 from, Vec3 to, float width, float height) {
        double dx = to.x - from.x;
        double dz = to.z - from.z;
        double horiz = Math.sqrt(dx * dx + dz * dz);
        if (horiz > 8.0) return false;
        if (Math.abs(to.y - from.y) > settings.maxJump + 0.35) return false;
        if (from.y - to.y > settings.maxDrop + 0.25) return false;
        int steps = Math.max(2, (int) Math.ceil(horiz * 2.0));
        for (int i = 1; i <= steps; i++) {
            double t = (double) i / steps;
            double x = from.x + dx * t;
            double z = from.z + dz * t;
            double y = from.y + (to.y - from.y) * t;
            if (!bodyClear(level, settings, x, y, z, width, height)) return false;
            int fx = Mth.floor(x);
            int fz = Mth.floor(z);
            if (findFloor(level, settings, fx, fz, Mth.floor(y), width, height) == null) return false;
        }
        return true;
    }

    private static List<Vec3> stringPull(Level level, GroundPathSettings settings,
                                         List<Vec3> raw, float width, float height) {
        if (raw.size() <= 2) return raw;
        List<Vec3> out = new ArrayList<>();
        int i = 0;
        out.add(raw.get(0));
        while (i < raw.size() - 1) {
            int best = i + 1;
            for (int j = raw.size() - 1; j > i + 1; j--) {
                if (hasDirectWalk(level, settings, raw.get(i), raw.get(j), width, height)) {
                    best = j;
                    break;
                }
            }
            out.add(raw.get(best));
            i = best;
        }
        return out;
    }

    private static final class StandCell {
        final int x;
        final int floorY;
        final int z;
        final double standY;

        StandCell(int x, int floorY, int z, double standY) {
            this.x = x;
            this.floorY = floorY;
            this.z = z;
            this.standY = standY;
        }

        long key() {
            return ((x & 0x3FFFFFFL) << 38) | ((z & 0x3FFFFFFL) << 12) | (floorY + 2048 & 0xFFF);
        }

        Vec3 standPos() {
            return new Vec3(x + 0.5, standY, z + 0.5);
        }
    }

    private static final class SearchNode {
        final StandCell cell;
        final float g;
        final float f;
        final SearchNode parent;

        SearchNode(StandCell cell, float g, float f, SearchNode parent) {
            this.cell = cell;
            this.g = g;
            this.f = f;
            this.parent = parent;
        }
    }
}
