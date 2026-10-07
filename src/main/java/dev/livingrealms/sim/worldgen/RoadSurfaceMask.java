package dev.livingrealms.sim.worldgen;

import dev.livingrealms.sim.world.SimPosition;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Deterministic integer road surface from a polyline centerline.
 *
 * <p>Roads must never be derived by rounding independent perpendicular offsets for every sample.
 * That produces checkerboard / diamond holes on diagonals. This mask:</p>
 * <ol>
 *   <li>rasterizes each segment with a supercover discrete line so diagonals cannot skip cells;</li>
 *   <li>dilates that centerline by an integer width brush;</li>
 *   <li>returns a stable, insertion-ordered list of cells.</li>
 * </ol>
 */
public final class RoadSurfaceMask {
    public record Cell(int x, int z) {
        public long packed() {
            return pack(x, z);
        }
    }

    private RoadSurfaceMask() {}

    public static List<Cell> rasterize(List<SimPosition> path, int width) {
        Objects.requireNonNull(path, "path");
        if (path.size() < 2) return List.of();
        if (width < 1) throw new IllegalArgumentException("width");

        LinkedHashSet<Long> centerline = new LinkedHashSet<>();
        for (int i = 0; i < path.size() - 1; i++) {
            SimPosition a = path.get(i);
            SimPosition b = path.get(i + 1);
            int x0 = (int) Math.floor(a.x());
            int z0 = (int) Math.floor(a.z());
            int x1 = (int) Math.floor(b.x());
            int z1 = (int) Math.floor(b.z());
            if (x0 == x1 && z0 == z1) {
                // Zero-length segment after quantization: keep the vertex, skip raster work.
                centerline.add(pack(x0, z0));
                continue;
            }
            supercover(x0, z0, x1, z1, centerline);
        }
        if (centerline.isEmpty()) return List.of();
        return dilate(centerline, width);
    }

    /**
     * Connects an already-sampled centerline (for example regional planned points) with supercover
     * segments before width dilation. Prevents gaps when consecutive samples jump diagonally.
     */
    public static List<Cell> rasterizeCenters(List<Cell> centers, int width) {
        Objects.requireNonNull(centers, "centers");
        if (width < 1) throw new IllegalArgumentException("width");
        if (centers.isEmpty()) return List.of();

        LinkedHashSet<Long> centerline = new LinkedHashSet<>();
        Cell prev = centers.getFirst();
        centerline.add(prev.packed());
        for (int i = 1; i < centers.size(); i++) {
            Cell next = centers.get(i);
            supercover(prev.x(), prev.z(), next.x(), next.z(), centerline);
            prev = next;
        }
        return dilate(centerline, width);
    }

    /**
     * Supercover / thick Bresenham: every grid cell touched by the continuous segment, including
     * both intermediate cells when the line crosses a grid corner diagonally.
     */
    public static void supercover(int x0, int z0, int x1, int z1, Set<Long> out) {
        Objects.requireNonNull(out, "out");
        int x = x0;
        int z = z0;
        out.add(pack(x, z));
        if (x0 == x1 && z0 == z1) return;

        int dx = Math.abs(x1 - x0);
        int dz = Math.abs(z1 - z0);
        int sx = Integer.compare(x1, x0);
        int sz = Integer.compare(z1, z0);
        int err = dx - dz;

        while (x != x1 || z != z1) {
            int e2 = err << 1;
            boolean moveX = e2 > -dz;
            boolean moveZ = e2 < dx;
            if (moveX) {
                err -= dz;
                x += sx;
            }
            if (moveZ) {
                err += dx;
                z += sz;
            }
            if (moveX && moveZ) {
                // Corner cut: keep both adjacent cells so the diagonal surface stays continuous.
                out.add(pack(x - sx, z));
                out.add(pack(x, z - sz));
            }
            out.add(pack(x, z));
        }
    }

    private static List<Cell> dilate(Set<Long> centerline, int width) {
        int minOffset = -(width / 2);
        int maxOffset = (width - 1) / 2;
        LinkedHashSet<Long> surface = new LinkedHashSet<>();
        for (long packed : centerline) {
            int cx = (int) (packed >> 32);
            int cz = (int) packed;
            for (int ox = minOffset; ox <= maxOffset; ox++) {
                for (int oz = minOffset; oz <= maxOffset; oz++) {
                    surface.add(pack(cx + ox, cz + oz));
                }
            }
        }
        List<Cell> out = new ArrayList<>(surface.size());
        for (long packed : surface) {
            out.add(new Cell((int) (packed >> 32), (int) packed));
        }
        return List.copyOf(out);
    }

    public static long pack(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }
}
