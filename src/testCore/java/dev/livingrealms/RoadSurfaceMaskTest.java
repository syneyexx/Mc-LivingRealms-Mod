package dev.livingrealms;

import dev.livingrealms.sim.world.SimPosition;
import dev.livingrealms.sim.worldgen.RoadSurfaceMask;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Guards continuous integer road rasterization against diagonal checkerboard holes. */
public final class RoadSurfaceMaskTest {
    private RoadSurfaceMaskTest() {}

    public static void main(String[] args) {
        diagonalSupercoverHasNoCornerGaps();
        widthDilationIsSolidSquareBrush();
        polylineRasterIsDeterministic();
        System.out.println("PASS road surface mask: supercover + dilation + determinism");
    }

    private static void diagonalSupercoverHasNoCornerGaps() {
        Set<Long> cells = new HashSet<>();
        RoadSurfaceMask.supercover(0, 0, 5, 5, cells);
        // Every diagonal step must include both intermediate cells, so the 4-neighbour walk
        // between consecutive centerline samples never needs a diagonal jump.
        check(cells.contains(RoadSurfaceMask.pack(0, 0)), "start");
        check(cells.contains(RoadSurfaceMask.pack(5, 5)), "end");
        check(cells.contains(RoadSurfaceMask.pack(1, 0)) || cells.contains(RoadSurfaceMask.pack(0, 1)),
                "first corner adjacency missing");
        check(cells.size() >= 11, "supercover diagonal too sparse: " + cells.size());

        List<RoadSurfaceMask.Cell> surface = RoadSurfaceMask.rasterize(
                List.of(new SimPosition(0, 0), new SimPosition(8, 8)), 3);
        Set<Long> packed = new HashSet<>();
        for (RoadSurfaceMask.Cell cell : surface) packed.add(cell.packed());
        // A width-3 brush on a filled diagonal must not leave interior holes around the centerline.
        for (int i = 0; i <= 8; i++) {
            check(packed.contains(RoadSurfaceMask.pack(i, i)), "centerline hole at " + i);
        }
    }

    private static void widthDilationIsSolidSquareBrush() {
        List<RoadSurfaceMask.Cell> surface = RoadSurfaceMask.rasterize(
                List.of(new SimPosition(10, 10)), 1);
        // A path with fewer than two vertices cannot define a segment.
        check(surface.isEmpty(), "single vertex path must be empty");

        // Zero-length quantized segment still anchors the vertex and dilates.
        surface = RoadSurfaceMask.rasterize(
                List.of(new SimPosition(10, 10), new SimPosition(10.4, 10.2)), 1);
        check(surface.size() == 1, "zero-length segment must keep one cell");

        surface = RoadSurfaceMask.rasterize(
                List.of(new SimPosition(10, 10), new SimPosition(12, 10)), 3);
        Set<Long> packed = new HashSet<>();
        for (RoadSurfaceMask.Cell cell : surface) packed.add(cell.packed());
        for (int x = 10; x <= 12; x++) {
            for (int z = 9; z <= 11; z++) {
                check(packed.contains(RoadSurfaceMask.pack(x, z)), "missing brush cell " + x + "," + z);
            }
        }
    }

    private static void polylineRasterIsDeterministic() {
        List<SimPosition> path = List.of(
                new SimPosition(-40.2, 12.7),
                new SimPosition(-10.8, 40.1),
                new SimPosition(20.4, 18.9));
        List<RoadSurfaceMask.Cell> a = RoadSurfaceMask.rasterize(path, 5);
        List<RoadSurfaceMask.Cell> b = RoadSurfaceMask.rasterize(path, 5);
        check(a.equals(b), "rasterize must be deterministic");
        check(a.size() > 40, "polyline mask unexpectedly small: " + a.size());
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
