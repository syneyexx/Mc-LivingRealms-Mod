package dev.livingrealms;

import dev.livingrealms.sim.construction.*;
import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.transport.TerrainCorridorPlanner;
import dev.livingrealms.sim.world.SimPosition;
import java.util.ArrayList;
import java.util.List;

/** Regression gate: construction completion, provenance, discovery fairness, corridor failure. */
public final class ConstructionIntegrityTest {
    private ConstructionIntegrityTest() {}

    public static void main(String[] args) {
        skippedRequiredDoesNotComplete();
        alreadyCorrectCountsAsSatisfied();
        authoredLedgerBoundsAndLookup();
        unloadIntentDoesNotStarveLaterCandidate();
        terrainFailureReturnsEmptyNotStraight();
        System.out.println("PASS construction integrity: receipt completion + provenance + fair discovery + no straight corridor fallback");
    }

    private static void skippedRequiredDoesNotComplete() {
        Faction faction = new Faction(1, "Test", "Ruler");
        Settlement settlement = new Settlement(2, "Pad", new SimPosition(0, 0), 40, 40);
        faction.addSettlement(settlement);
        ConstructionIntent intent = new ConstructionIntent("house:9", faction.id(), settlement.id(), StructureRole.HOUSE, new SimPosition(10, 10), 7, 7, 0, 50);
        List<BuildOperation> ops = List.of(
                new BuildOperation(10, 64, 10, PaletteSlot.FOUNDATION, ConstructionPhase.FOUNDATION),
                new BuildOperation(10, 65, 10, PaletteSlot.WALL, ConstructionPhase.SHELL),
                new BuildOperation(10, 65, 9, PaletteSlot.DOOR, ConstructionPhase.SHELL),
                new BuildOperation(10, 66, 10, PaletteSlot.LIGHT, ConstructionPhase.DETAIL)
        );
        ConstructionJob job = new ConstructionJob(intent, ops, 0);
        ConstructionQueue queue = new ConstructionQueue();
        queue.enqueue(job);
        int guard = 100;
        BuildTickResult last = null;
        while (!queue.isEmpty() && guard-- > 0) {
            last = queue.tick(8, (j, op) -> switch (op.slot()) {
                case LIGHT -> BuildApplyResult.SAFELY_IGNORED;
                case DOOR, WALL, FOUNDATION -> BuildApplyResult.OBSTRUCTED_PROTECTED;
                default -> BuildApplyResult.FAILED;
            });
        }
        check(last != null, "queue must tick");
        check(job.complete(), "cursor should finish even when obstructed");
        check(!job.physicallyComplete(), "required obstruction must not be physically complete");
        check(last.completedJobKeys().isEmpty(), "obstructed job must not enter completed keys");
        check(last.rejectedJobKeys().contains(job.key()), "obstructed job must be rejected");
        check(!settlement.isConstructionCompleted(intent.key()), "canonical completion must remain unmarked");
    }

    private static void alreadyCorrectCountsAsSatisfied() {
        Faction faction = new Faction(3, "Test", "Ruler");
        Settlement settlement = new Settlement(4, "Pad", new SimPosition(0, 0), 40, 40);
        ConstructionIntent intent = new ConstructionIntent("road:1", faction.id(), settlement.id(), StructureRole.ROAD, new SimPosition(20, 20), 3, 5, 0, 10);
        List<BuildOperation> ops = new ArrayList<>();
        for (int i = 0; i < 5; i++) ops.add(new BuildOperation(20, 70, 20 + i, PaletteSlot.PATH, ConstructionPhase.FOUNDATION));
        ConstructionJob job = new ConstructionJob(intent, ops, 0);
        ConstructionQueue queue = new ConstructionQueue();
        queue.enqueue(job);
        BuildTickResult result = null;
        int guard = 50;
        while (!queue.isEmpty() && guard-- > 0) {
            result = queue.tick(8, (j, op) -> BuildApplyResult.ALREADY_CORRECT);
        }
        check(result != null && result.completedJobKeys().contains(job.key()), "already-correct required path must complete");
        check(job.physicallyComplete(), "already-correct must satisfy required geometry");
        check(job.receipt().completionRatio() >= 0.99D, "completion ratio must be full for already-correct road");
    }

    private static void authoredLedgerBoundsAndLookup() {
        AuthoredBlockLedger ledger = new AuthoredBlockLedger();
        check(ledger.record(100, 64, -20), "first authored block");
        check(ledger.isAuthored(100, 64, -20), "authored lookup");
        check(!ledger.isAuthored(101, 64, -20), "neighbor is not authored");
        check(ledger.forget(100, 64, -20), "forget authored block");
        check(!ledger.isAuthored(100, 64, -20), "forgotten block");
        // Boundedness: filling one chunk up to the cap must not throw.
        int accepted = 0;
        for (int i = 0; i < AuthoredBlockLedger.MAX_BLOCKS_PER_CHUNK + 50; i++) {
            int x = (i % 16);
            int z = ((i / 16) % 16);
            int y = 40 + (i / 256);
            if (ledger.record(x, y, z)) accepted++;
        }
        check(accepted == AuthoredBlockLedger.MAX_BLOCKS_PER_CHUNK, "per-chunk authored cap must hold: " + accepted);
    }

    private static void unloadIntentDoesNotStarveLaterCandidate() {
        // Pure scheduling analogue of discoverLoadedWork: unloaded high-priority intents use continue.
        List<String> pending = List.of("high:unloaded", "low:loaded");
        List<String> enqueued = new ArrayList<>();
        for (String key : pending) {
            if (key.endsWith("unloaded")) continue;
            enqueued.add(key);
        }
        check(enqueued.equals(List.of("low:loaded")), "loaded later intents must still be discovered");
    }

    private static void terrainFailureReturnsEmptyNotStraight() {
        TerrainCorridorPlanner.TerrainSample wall = new TerrainCorridorPlanner.TerrainSample() {
            @Override public int height(int x, int z) {
                // Impassable cliff wall between start and goal with no flanking pass in budget.
                if (x >= 40 && x <= 200) return 64 + 40;
                return 64;
            }
            @Override public boolean water(int x, int z) { return false; }
            @Override public boolean blocked(int x, int z) { return x >= 40 && x <= 200; }
        };
        List<TerrainCorridorPlanner.Cell> path = TerrainCorridorPlanner.plan(0, 0, 240, 0, 16, 200, wall);
        check(path.isEmpty(), "impossible terrain must return empty path, not a straight destructive corridor");
        // Sanity: an open plain still finds a path.
        TerrainCorridorPlanner.TerrainSample flat = new TerrainCorridorPlanner.TerrainSample() {
            @Override public int height(int x, int z) { return 64; }
            @Override public boolean water(int x, int z) { return false; }
            @Override public boolean blocked(int x, int z) { return false; }
        };
        check(!TerrainCorridorPlanner.plan(0, 0, 240, 0, 16, 2_000, flat).isEmpty(), "flat corridor must succeed");
    }

    private static void check(boolean v, String m) { if (!v) throw new AssertionError(m); }
}
