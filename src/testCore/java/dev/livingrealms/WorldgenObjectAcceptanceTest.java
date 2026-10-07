package dev.livingrealms;

import dev.livingrealms.sim.construction.BlockPlacement;
import dev.livingrealms.sim.construction.ConstructionIntent;
import dev.livingrealms.sim.construction.ConstructionPhase;
import dev.livingrealms.sim.construction.PaletteSlot;
import dev.livingrealms.sim.construction.StructureBlueprint;
import dev.livingrealms.sim.construction.StructureRole;
import dev.livingrealms.sim.world.SimPosition;
import dev.livingrealms.sim.worldgen.WorldgenObjectAcceptance;
import java.util.List;

/** Ensures fixed structures share one global accept/reject + base Y decision. */
public final class WorldgenObjectAcceptanceTest {
    private WorldgenObjectAcceptanceTest() {}

    public static void main(String[] args) {
        flatSiteIsAcceptedWithStableBase();
        extremeCliffIsRejected();
        decisionIsDeterministicAcrossCalls();
        System.out.println("PASS worldgen object acceptance: stable base Y + cliff reject");
    }

    private static void flatSiteIsAcceptedWithStableBase() {
        ConstructionIntent intent = house(100, 200);
        StructureBlueprint blueprint = simpleHouse();
        var decision = WorldgenObjectAcceptance.decide(
                intent, blueprint, (x, z) -> 64, (x, z) -> 64, 0);
        check(decision.accepted(), decision.rejectReason());
        check(decision.baseY() == 64, "baseY=" + decision.baseY());
        check(decision.centerX() == 100 && decision.centerZ() == 200, "center");
    }

    private static void extremeCliffIsRejected() {
        ConstructionIntent intent = house(0, 0);
        StructureBlueprint blueprint = simpleHouse();
        WorldgenObjectAcceptance.HeightSampler surface = (x, z) -> 80;
        WorldgenObjectAcceptance.HeightSampler ground = (x, z) -> x == 0 && z == 0 ? 80 : 0;
        var decision = WorldgenObjectAcceptance.decide(intent, blueprint, surface, ground, 0);
        check(!decision.accepted(), "cliff should reject");
        check(decision.rejectReason().contains("delta"), decision.rejectReason());
    }

    private static void decisionIsDeterministicAcrossCalls() {
        ConstructionIntent intent = house(50, -50);
        StructureBlueprint blueprint = simpleHouse();
        WorldgenObjectAcceptance.HeightSampler heights = (x, z) -> 70 + ((x + z) & 1);
        var a = WorldgenObjectAcceptance.decide(intent, blueprint, heights, heights, -64);
        var b = WorldgenObjectAcceptance.decide(intent, blueprint, heights, heights, -64);
        check(a.equals(b), "acceptance must be deterministic");
    }

    private static ConstructionIntent house(int x, int z) {
        return new ConstructionIntent(
                "house:test:" + x + ":" + z, 1L, 2L, StructureRole.HOUSE,
                new SimPosition(x, z), 7, 7, 0, 10, "", 0, 0, List.of());
    }

    private static StructureBlueprint simpleHouse() {
        List<BlockPlacement> placements = List.of(
                new BlockPlacement(0, 0, 0, PaletteSlot.FOUNDATION, ConstructionPhase.FOUNDATION),
                new BlockPlacement(-2, 0, -2, PaletteSlot.FOUNDATION, ConstructionPhase.FOUNDATION),
                new BlockPlacement(2, 0, -2, PaletteSlot.FOUNDATION, ConstructionPhase.FOUNDATION),
                new BlockPlacement(-2, 0, 2, PaletteSlot.FOUNDATION, ConstructionPhase.FOUNDATION),
                new BlockPlacement(2, 0, 2, PaletteSlot.FOUNDATION, ConstructionPhase.FOUNDATION),
                new BlockPlacement(0, 1, 0, PaletteSlot.FLOOR, ConstructionPhase.FRAME),
                new BlockPlacement(0, 1, -3, PaletteSlot.DOOR, ConstructionPhase.DETAIL));
        return new StructureBlueprint("test_house", 7, 7, 5, placements);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
