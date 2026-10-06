package dev.livingrealms;

import dev.livingrealms.sim.construction.ConstructionIntent;
import dev.livingrealms.sim.construction.StructureRole;
import dev.livingrealms.sim.world.StarterCivilizationLayoutPlanner;
import dev.livingrealms.sim.worldgen.SettlementInitialWorldgenPlan;
import dev.livingrealms.sim.worldgen.StarterCivilizationFabricIndex;
import dev.livingrealms.sim.worldgen.StarterRegionalRoutePlanner;
import java.util.List;

/** Chunk-order, cross-chunk and bounded-lookup gate for starter worldgen fabric indexing. */
public final class StarterCivilizationFabricIndexTest {
    private StarterCivilizationFabricIndexTest() {}

    public static void main(String[] args) {
        chunkQueriesAreOrderIndependent();
        multiChunkCoreFabricIsIndexed();
        multiChunkFabricReusesPrecomputedBlueprint();
        starterRoadsideSitesAreChunkIndexed();
        regionalRoadsUseCityGates();
        emptyChunkLookupIsDirectAndEmpty();
        System.out.println("PASS starter fabric index: order-independent + cross-chunk + gate-connected + bounded lookup");
    }

    private static void chunkQueriesAreOrderIndependent() {
        var layout = StarterCivilizationLayoutPlanner.plan(0x7A11L);
        var index = StarterCivilizationFabricIndex.build(layout);
        var a1 = index.query(-2, -2);
        var b1 = index.query(20, 20);
        var b2 = index.query(20, 20);
        var a2 = index.query(-2, -2);
        check(a1.equals(a2), "A query changed after B");
        check(b1.equals(b2), "B query changed after A");
    }

    private static void multiChunkCoreFabricIsIndexed() {
        var index = StarterCivilizationFabricIndex.build(StarterCivilizationLayoutPlanner.plan(0x5512L));
        SettlementInitialWorldgenPlan capital = index.settlements().stream()
                .filter(s -> s.realmId().equals("aster"))
                .filter(s -> s.role() == dev.livingrealms.sim.faction.SettlementRole.CAPITAL)
                .findFirst().orElseThrow();

        ConstructionIntent keep = capital.intents().stream()
                .filter(i -> i.role() == StructureRole.KEEP).findFirst().orElseThrow();
        check(referenceChunkCount(index, capital.settlementId(), keep.key(), 4) >= 2,
                "capital keep must be represented in every intersecting chunk");

        ConstructionIntent wall = capital.intents().stream()
                .filter(i -> i.role() == StructureRole.WALL).findFirst().orElseThrow();
        check(referenceChunkCount(index, capital.settlementId(), wall.key(), 20) >= 2,
                "city wall run must be cross-chunk indexed");

        check(index.routes().stream().anyMatch(r ->
                        r.fromSettlementId() == capital.settlementId()
                                || r.toSettlementId() == capital.settlementId()),
                "capital must retain pure regional-route topology");
        check(index.query(
                        Math.floorDiv((int) Math.floor(capital.center().x()), 16),
                        Math.floorDiv((int) Math.floor(capital.center().z()), 16))
                        .routes().isEmpty(),
                "surface fabric index must not carry obsolete straight route slices");
    }


    private static void multiChunkFabricReusesPrecomputedBlueprint() {
        var index = StarterCivilizationFabricIndex.build(StarterCivilizationLayoutPlanner.plan(0x7719L));
        SettlementInitialWorldgenPlan capital = index.settlements().stream()
                .filter(s -> s.realmId().equals("aster"))
                .filter(s -> s.role() == dev.livingrealms.sim.faction.SettlementRole.CAPITAL)
                .findFirst().orElseThrow();
        ConstructionIntent keep = capital.intents().stream()
                .filter(i -> i.role() == StructureRole.KEEP).findFirst().orElseThrow();
        int cx = Math.floorDiv((int) Math.floor(keep.center().x()), 16);
        int cz = Math.floorDiv((int) Math.floor(keep.center().z()), 16);

        Object shared = null;
        int references = 0;
        for (int x = cx - 3; x <= cx + 3; x++) {
            for (int z = cz - 3; z <= cz + 3; z++) {
                for (var fabric : index.query(x, z).settlementFabric()) {
                    if (fabric.settlement().settlementId() != capital.settlementId()
                            || !fabric.intent().key().equals(keep.key())) continue;
                    check(fabric.blueprint() != null, "indexed fabric requires precomputed blueprint");
                    if (shared == null) shared = fabric.blueprint();
                    else check(shared == fabric.blueprint(),
                            "multi-chunk slices must reuse one immutable blueprint instance");
                    references++;
                }
            }
        }
        check(references >= 2, "test keep must span multiple chunks");
    }


    private static void starterRoadsideSitesAreChunkIndexed() {
        var index = StarterCivilizationFabricIndex.build(
                StarterCivilizationLayoutPlanner.plan(0xA11CE77L));
        check(!index.roadsideSites().isEmpty(), "starter roadside plan must not be empty");
        for (var site : index.roadsideSites()) {
            int x = (int) Math.floor(site.position().x());
            int z = (int) Math.floor(site.position().z());
            int cx = Math.floorDiv(x, 16);
            int cz = Math.floorDiv(z, 16);
            check(index.query(cx, cz).roadsideSites().stream()
                            .anyMatch(f -> f.site().stableSiteId() == site.stableSiteId()),
                    "roadside anchor missing from center chunk " + site.stableKey());
        }
    }

    private static void regionalRoadsUseCityGates() {
        var index = StarterCivilizationFabricIndex.build(StarterCivilizationLayoutPlanner.plan(0xA57E2L));
        SettlementInitialWorldgenPlan capital = index.settlements().stream()
                .filter(s -> s.realmId().equals("aster"))
                .filter(s -> s.role() == dev.livingrealms.sim.faction.SettlementRole.CAPITAL)
                .findFirst().orElseThrow();
        List<ConstructionIntent> gates = capital.intents().stream()
                .filter(i -> i.role() == StructureRole.GATE).toList();
        check(!gates.isEmpty(), "capital requires explicit gates");

        for (StarterRegionalRoutePlanner.RoutePlan route : index.routes()) {
            if (route.fromSettlementId() == capital.settlementId()) {
                check(gates.stream().anyMatch(g -> g.center().distanceTo(route.from()) < 1.0),
                        "capital route must start at a generated gate");
            }
            if (route.toSettlementId() == capital.settlementId()) {
                check(gates.stream().anyMatch(g -> g.center().distanceTo(route.to()) < 1.0),
                        "capital route must end at a generated gate");
            }
        }
    }

    private static void emptyChunkLookupIsDirectAndEmpty() {
        var index = StarterCivilizationFabricIndex.build(StarterCivilizationLayoutPlanner.plan(17L));
        check(index.indexedChunkCount() > 0, "index should contain civilization chunks");
        check(index.indexedReferences() >= index.indexedChunkCount(), "index reference accounting");
        check(index.query(1_000_000, -1_000_000).isEmpty(), "remote wilderness chunk must be empty");
    }

    private static int referenceChunkCount(StarterCivilizationFabricIndex index, long settlementId,
                                           String intentKey, int radiusChunks) {
        SettlementInitialWorldgenPlan settlement = index.settlements().stream()
                .filter(s -> s.settlementId() == settlementId).findFirst().orElseThrow();
        ConstructionIntent intent = settlement.intents().stream()
                .filter(i -> i.key().equals(intentKey)).findFirst().orElseThrow();
        int cx = Math.floorDiv((int) Math.floor(intent.center().x()), 16);
        int cz = Math.floorDiv((int) Math.floor(intent.center().z()), 16);
        int count = 0;
        for (int x = cx - radiusChunks; x <= cx + radiusChunks; x++) {
            for (int z = cz - radiusChunks; z <= cz + radiusChunks; z++) {
                boolean present = index.query(x, z).settlementFabric().stream().anyMatch(f ->
                        f.settlement().settlementId() == settlementId && f.intent().key().equals(intentKey));
                if (present) count++;
            }
        }
        return count;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
