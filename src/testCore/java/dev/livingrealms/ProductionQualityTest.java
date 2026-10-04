package dev.livingrealms;

import dev.livingrealms.sim.compat.WaystoneProvenance;
import dev.livingrealms.sim.construction.*;
import dev.livingrealms.sim.faction.*;
import dev.livingrealms.sim.transport.*;
import dev.livingrealms.sim.world.*;
import java.util.*;

/** Regression gate for buildf14 A–Z production gaps: terrain roads, entrances, catch-up, locate, doors, waystones. */
public final class ProductionQualityTest {
    private ProductionQualityTest() {}

    public static void main(String[] args) {
        terrainCorridorAvoidsCliff();
        entranceAccessStairs();
        physicalCatchupBacklog();
        locateCityAndMine();
        doorsAreNotAirSlots();
        waystoneProvenanceHelpers();
        System.out.println("PASS production quality: terrain corridor + entrance access + catch-up deficit + locate + doors + waystone provenance");
    }

    private static void terrainCorridorAvoidsCliff() {
        TerrainCorridorPlanner.TerrainSample mountain = new TerrainCorridorPlanner.TerrainSample() {
            @Override public int height(int x, int z) {
                if (Math.abs(z) <= 8) return 64;
                if (z > 8 && z < 50 && x > 40 && x < 200) return 64 + (z - 8) * 3; // steep ridge on straight path
                return 64 + Math.max(0, z / 20);
            }
            @Override public boolean water(int x, int z) { return false; }
            @Override public boolean blocked(int x, int z) { return false; }
        };
        List<TerrainCorridorPlanner.Cell> path = TerrainCorridorPlanner.plan(0, 0, 240, 0, 16, 4_000, mountain);
        check(!path.isEmpty(), "corridor must return a path");
        int maxGrade = 0;
        for (int i = 1; i < path.size(); i++) {
            int h0 = mountain.height(path.get(i - 1).x(), path.get(i - 1).z());
            int h1 = mountain.height(path.get(i).x(), path.get(i).z());
            maxGrade = Math.max(maxGrade, Math.abs(h1 - h0));
        }
        check(maxGrade <= 12, "corridor must reject extreme cell grades");
        check(TerrainCorridorPlanner.transitionCost(64, 80, false, 16, false) == Double.POSITIVE_INFINITY, "grade>12 must be infinite cost");
        check(TerrainCorridorPlanner.transitionCost(64, 70, false, 16, false) < Double.POSITIVE_INFINITY, "moderate grade must be finite");
    }

    private static void entranceAccessStairs() {
        check(EntranceAccessPlanner.isAccessible(70, 70), "same grade is accessible");
        check(!EntranceAccessPlanner.isAccessible(70, 68), "two-block rise needs repair");
        List<EntranceAccessPlanner.AccessFix> fixes = EntranceAccessPlanner.plan(0, -5, 70, 68);
        check(!fixes.isEmpty(), "door above street must produce stair/landing fixes");
        check(fixes.stream().anyMatch(f -> f.slot() == PaletteSlot.FOUNDATION), "access fix must include foundation steps");
        check(EntranceAccessPlanner.plan(0, -5, 70, 70).isEmpty(), "level entrance needs no fix");
    }

    private static void physicalCatchupBacklog() {
        SimulationState state = new SimulationState(70707L);
        DemoSeeder.seed(state);
        SettlementDensitySeeder.ensureStarterDensity(state);
        Faction faction = state.factions().getFirst();
        Settlement settlement = faction.settlements().getFirst();
        PhysicalDevelopmentReconciler.Deficit day1 = PhysicalDevelopmentReconciler.analyze(faction, settlement);
        check(day1.pendingCount() > 0, "fresh settlement must have physical backlog");
        int intents = PhysicalDevelopmentReconciler.catchupIntentsPerSettlement(101, day1);
        check(intents >= 3, "day 1→102 catch-up must raise intents/settlement above organic 1");
        for (ConstructionIntent intent : day1.backlog().subList(0, Math.min(5, day1.backlog().size()))) {
            settlement.markConstructionCompleted(intent.key());
        }
        PhysicalDevelopmentReconciler.Deficit after = PhysicalDevelopmentReconciler.analyze(faction, settlement);
        check(after.pendingCount() < day1.pendingCount(), "completed keys must reduce reconciler backlog");
    }

    private static void locateCityAndMine() {
        SimulationState state = new SimulationState(80808L);
        DemoSeeder.seed(state);
        SettlementDensitySeeder.ensureStarterDensity(state);
        SimPosition origin = new SimPosition(0, 0);
        var city = LocateQuery.nearestCity(state, origin);
        check(city.isPresent(), "locate city must find a settlement");
        check(city.get().distance() >= 0, "city locate must report a real hit");
        var mine = LocateQuery.nearestMine(state, origin);
        check(mine.isPresent(), "locate mine must find a planned/claimed mine in starter world");
        check(mine.get().label().equals("mine"), "mine locate label");
        var kingdom = LocateQuery.nearestKingdom(state, origin);
        check(kingdom.isPresent(), "locate kingdom must work");
    }

    private static void doorsAreNotAirSlots() {
        ConstructionIntent house = new ConstructionIntent("house:test", 1, 1, StructureRole.HOUSE, new SimPosition(0, 0), 9, 9, 0, 100);
        StructureBlueprint bp = StructureBlueprintFactory.create(house);
        check(bp.placements().stream().anyMatch(p -> p.slot() == PaletteSlot.DOOR), "house blueprints must still emit DOOR slots");
    }

    private static void waystoneProvenanceHelpers() {
        check(WaystoneProvenance.isLivingRealmsName(WaystoneProvenance.authoredName("Asterhold")), "LR prefix marks authored stones");
        check(!WaystoneProvenance.isLivingRealmsName("My Base"), "player names are not LR-authored");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
