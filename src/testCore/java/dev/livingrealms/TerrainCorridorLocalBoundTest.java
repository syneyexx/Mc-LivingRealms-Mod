package dev.livingrealms;

import dev.livingrealms.sim.transport.TerrainCorridorPlanner;
import java.util.concurrent.atomic.AtomicInteger;

/** Regression gate: chunk-local route planning must remain hard-bounded. */
public final class TerrainCorridorLocalBoundTest {
    private TerrainCorridorLocalBoundTest() {}

    public static void main(String[] args) {
        flatLocalPathWorks();
        impossibleLocalPathDoesNotEscalateGlobally();
        System.out.println("PASS terrain corridor local bound: bounded A* + no global fallback hierarchy");
    }

    private static void flatLocalPathWorks() {
        TerrainCorridorPlanner.TerrainSample flat = new TerrainCorridorPlanner.TerrainSample() {
            @Override public int height(int x, int z) { return 64; }
            @Override public boolean water(int x, int z) { return false; }
            @Override public boolean blocked(int x, int z) {
                return x < 0 || x > 255 || z < 0 || z > 255;
            }
        };
        var path = TerrainCorridorPlanner.planLocal(
                0, 128, 255, 128, 8, 1_200, flat);
        check(!path.isEmpty(), "flat local route should resolve");
        check(path.getFirst().x() == 0, "local route lost exact start");
        check(path.getLast().x() == 255, "local route lost exact end");
    }

    private static void impossibleLocalPathDoesNotEscalateGlobally() {
        AtomicInteger heights = new AtomicInteger();
        TerrainCorridorPlanner.TerrainSample fenced = new TerrainCorridorPlanner.TerrainSample() {
            @Override public int height(int x, int z) {
                heights.incrementAndGet();
                return 64;
            }
            @Override public boolean water(int x, int z) { return false; }
            @Override public boolean blocked(int x, int z) {
                if (x < 0 || x > 255 || z < 0 || z > 255) return true;
                return x >= 120 && x <= 136;
            }
        };

        var path = TerrainCorridorPlanner.planLocal(
                16, 128, 240, 128, 8, 220, fenced);
        check(path.isEmpty(), "solid local wall should exhaust bounded search");
        check(heights.get() < 20_000,
                "local planner escalated beyond bounded search; height calls=" + heights.get());
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
}
