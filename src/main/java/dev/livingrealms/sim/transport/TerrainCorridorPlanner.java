package dev.livingrealms.sim.transport;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.PriorityQueue;

/**
 * Bounded terrain-cost corridor planner for intercity roads. City streets stay orthogonal in
 * {@code SettlementPlanner}; this class is for regional routes that must follow valleys, avoid
 * cliffs, and prefer shallow crossings instead of geometric straight lines over mountains.
 *
 * <p>Pure simulation logic: callers supply height/water/blocker samples. Search is deliberately
 * coarse (cell size) and node-capped so Minecraft adapters never run unbounded pathfinding.
 *
 * <p>Production rule: exhausting the node budget must <em>not</em> fall back to a destructive
 * straight corridor through impossible terrain. Prefer expanded search / pass / waypoint attempts,
 * then return an empty path so the route stays awaiting terrain realization.
 */
public final class TerrainCorridorPlanner {
    public interface TerrainSample {
        /** Ground Y at world column, or {@link Integer#MIN_VALUE} if unloaded/unknown. */
        int height(int x, int z);
        /** True when the column is water/fluid at the surface. */
        boolean water(int x, int z);
        /** True when the column must not be crossed (structures, protected pads). */
        boolean blocked(int x, int z);
    }

    public record Cell(int x, int z) {}

    private TerrainCorridorPlanner() {}

    /**
     * Plans a polyline from {@code from} to {@code to} on a downsampled grid.
     *
     * @param cellSize world blocks per search cell (8–32 recommended)
     * @param maxNodes hard expansion cap for the primary attempt
     * @return path cells, or empty when no acceptable corridor exists within the fallback hierarchy
     */
    public static List<Cell> plan(int fromX, int fromZ, int toX, int toZ, int cellSize, int maxNodes, TerrainSample sample) {
        Objects.requireNonNull(sample, "sample");
        if (cellSize < 4 || maxNodes < 16) throw new IllegalArgumentException("budget");

        // 1) Primary terrain-cost path
        List<Cell> primary = search(fromX, fromZ, toX, toZ, cellSize, maxNodes, sample);
        if (!primary.isEmpty()) return primary;

        // 2) Bounded expanded search (more nodes, slightly finer cells)
        int fine = Math.max(4, cellSize / 2);
        List<Cell> expanded = search(fromX, fromZ, toX, toZ, fine, Math.min(maxNodes * 3, 20_000), sample);
        if (!expanded.isEmpty()) return expanded;

        // 3) Nearby pass search: shift endpoints laterally looking for a valley corridor
        int[] offsets = {cellSize, -cellSize, cellSize * 2, -cellSize * 2, cellSize * 3, -cellSize * 3};
        for (int ox : offsets) {
            for (int oz : offsets) {
                if (ox == 0 && oz == 0) continue;
                List<Cell> pass = search(fromX + ox, fromZ + oz, toX + ox, toZ + oz, cellSize, maxNodes, sample);
                if (!pass.isEmpty()) {
                    ArrayList<Cell> linked = new ArrayList<>();
                    linked.add(new Cell(fromX, fromZ));
                    linked.addAll(pass);
                    linked.add(new Cell(toX, toZ));
                    return List.copyOf(simplify(linked));
                }
            }
        }

        // 4) Intermediate safe waypoint near the midpoint with lowest sampled cost
        int midX = (fromX + toX) / 2;
        int midZ = (fromZ + toZ) / 2;
        int bestX = midX, bestZ = midZ;
        double bestScore = Double.POSITIVE_INFINITY;
        for (int dx = -cellSize * 4; dx <= cellSize * 4; dx += cellSize) {
            for (int dz = -cellSize * 4; dz <= cellSize * 4; dz += cellSize) {
                int x = midX + dx, z = midZ + dz;
                int h = sample.height(x, z);
                if (h == Integer.MIN_VALUE || sample.blocked(x, z)) continue;
                double score = Math.abs(h - 64) + (sample.water(x, z) ? 40 : 0) + Math.hypot(dx, dz) * 0.25;
                if (score < bestScore) { bestScore = score; bestX = x; bestZ = z; }
            }
        }
        if (bestScore < Double.POSITIVE_INFINITY) {
            List<Cell> a = search(fromX, fromZ, bestX, bestZ, cellSize, maxNodes, sample);
            List<Cell> b = search(bestX, bestZ, toX, toZ, cellSize, maxNodes, sample);
            if (!a.isEmpty() && !b.isEmpty()) {
                ArrayList<Cell> joined = new ArrayList<>(a.size() + b.size());
                joined.addAll(a);
                joined.addAll(b.subList(1, b.size()));
                return List.copyOf(simplify(joined));
            }
        }

        // 5–7) No destructive straight fallback. Bridge/tunnel are adapter concerns when a path exists.
        return List.of();
    }

    /**
     * Strictly bounded local corridor search for chunk/region worldgen.
     *
     * <p>Unlike {@link #plan}, this method never runs the 36 shifted-pass hierarchy or midpoint
     * waypoint fallback. Callers provide a blocker that fences the local planning tile. A failed
     * local search returns empty quickly so the Minecraft adapter can use a bounded engineered
     * segment instead of turning one chunk into a multi-kilometre A* job.</p>
     */
    public static List<Cell> planLocal(
            int fromX, int fromZ, int toX, int toZ,
            int cellSize, int maxNodes, TerrainSample sample) {
        Objects.requireNonNull(sample, "sample");
        if (cellSize < 4 || maxNodes < 16) throw new IllegalArgumentException("budget");

        List<Cell> primary = search(
                fromX, fromZ, toX, toZ, cellSize, maxNodes, sample);
        if (!primary.isEmpty()) return primary;

        int fine = Math.max(4, cellSize / 2);
        return search(
                fromX, fromZ, toX, toZ, fine,
                Math.min(maxNodes * 2, 4_000), sample);
    }

    private static List<Cell> search(int fromX, int fromZ, int toX, int toZ, int cellSize, int maxNodes, TerrainSample sample) {
        int sx = quantize(fromX, cellSize), sz = quantize(fromZ, cellSize);
        int gx = quantize(toX, cellSize), gz = quantize(toZ, cellSize);
        if (sx == gx && sz == gz) return List.of(new Cell(fromX, fromZ), new Cell(toX, toZ));

        record Node(int x, int z, double g, double f) {}
        PriorityQueue<Node> open = new PriorityQueue<>(Comparator.comparingDouble(n -> n.f));
        HashMap<Long, Double> bestG = new HashMap<>();
        HashMap<Long, Long> cameFrom = new HashMap<>();
        long startKey = key(sx, sz);
        open.add(new Node(sx, sz, 0, heuristic(sx, sz, gx, gz)));
        bestG.put(startKey, 0.0);
        int expanded = 0;
        long goalKey = -1L;

        while (!open.isEmpty() && expanded < maxNodes) {
            Node cur = open.poll();
            long ck = key(cur.x, cur.z);
            Double known = bestG.get(ck);
            if (known == null || cur.g > known + 1e-9) continue;
            expanded++;
            if (cur.x == gx && cur.z == gz) { goalKey = ck; break; }

            int[][] dirs = {{cellSize, 0}, {-cellSize, 0}, {0, cellSize}, {0, -cellSize},
                    {cellSize, cellSize}, {cellSize, -cellSize}, {-cellSize, cellSize}, {-cellSize, -cellSize}};
            int curH = sample.height(cur.x, cur.z);
            if (curH == Integer.MIN_VALUE) continue;
            for (int[] d : dirs) {
                int nx = cur.x + d[0], nz = cur.z + d[1];
                if (sample.blocked(nx, nz)) continue;
                int nh = sample.height(nx, nz);
                if (nh == Integer.MIN_VALUE) continue;
                int grade = Math.abs(nh - curH);
                if (grade > 12) continue; // hard cliff reject at cell scale
                boolean diagonal = d[0] != 0 && d[1] != 0;
                double step = (diagonal ? 1.414 : 1.0) * cellSize;
                double cost = step;
                cost += grade * grade * 4.5; // prefer gradual slopes / passes
                if (sample.water(nx, nz)) cost += cellSize * 6.0; // prefer fords/bridges later, not swimming whole route
                if (grade > 4) cost += (grade - 4) * cellSize * 2.0;
                double ng = cur.g + cost;
                long nk = key(nx, nz);
                Double prev = bestG.get(nk);
                if (prev != null && ng >= prev) continue;
                bestG.put(nk, ng);
                cameFrom.put(nk, ck);
                open.add(new Node(nx, nz, ng, ng + heuristic(nx, nz, gx, gz)));
            }
        }
        if (goalKey < 0) return List.of();
        ArrayList<Cell> rev = new ArrayList<>();
        long walk = goalKey;
        while (walk >= 0) {
            rev.add(new Cell((int) (walk >> 32), (int) walk));
            Long parent = cameFrom.get(walk);
            if (parent == null) break;
            walk = parent;
        }
        ArrayList<Cell> path = new ArrayList<>(rev.size() + 2);
        path.add(new Cell(fromX, fromZ));
        for (int i = rev.size() - 1; i >= 0; i--) path.add(rev.get(i));
        path.add(new Cell(toX, toZ));
        return List.copyOf(simplify(path));
    }

    /** Cost helper exposed for tests: higher is worse. */
    public static double transitionCost(int fromH, int toH, boolean water, int cellSize, boolean diagonal) {
        int grade = Math.abs(toH - fromH);
        if (grade > 12) return Double.POSITIVE_INFINITY;
        double step = (diagonal ? 1.414 : 1.0) * cellSize;
        double cost = step + grade * grade * 4.5;
        if (water) cost += cellSize * 6.0;
        if (grade > 4) cost += (grade - 4) * cellSize * 2.0;
        return cost;
    }

    private static List<Cell> simplify(List<Cell> path) {
        if (path.size() <= 2) return path;
        ArrayList<Cell> out = new ArrayList<>();
        out.add(path.getFirst());
        for (int i = 1; i < path.size() - 1; i++) {
            Cell a = out.getLast(), b = path.get(i), c = path.get(i + 1);
            int abx = b.x() - a.x(), abz = b.z() - a.z(), bcx = c.x() - b.x(), bcz = c.z() - b.z();
            if (abx * bcz - abz * bcx != 0) out.add(b);
        }
        out.add(path.getLast());
        return out;
    }

    private static double heuristic(int x, int z, int gx, int gz) {
        return Math.hypot(gx - x, gz - z);
    }

    private static int quantize(int v, int cell) {
        return Math.floorDiv(v, cell) * cell;
    }

    private static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }
}
