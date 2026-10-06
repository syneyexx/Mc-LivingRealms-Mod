package dev.livingrealms;

import dev.livingrealms.sim.construction.SettlementConstructionPolicy;
import dev.livingrealms.sim.construction.SettlementPlanner;
import dev.livingrealms.sim.faction.DevelopmentMode;
import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.faction.SettlementOrigin;
import dev.livingrealms.sim.faction.SettlementRole;
import dev.livingrealms.sim.persistence.ContentMigrationPolicy;
import dev.livingrealms.sim.world.SimPosition;
import java.util.Set;

/** ContentRevision 16 compatibility for pre-graph physical settlement layouts. */
public final class LegacyStreetFabricMigrationTest {
    private LegacyStreetFabricMigrationTest() {}

    public static void main(String[] args) {
        revisionGate();
        materializedLegacyRoadsFreezeWithoutMutation();
        oldMorphologyResetCannotLoseLegacyEvidence();
        graphEraAndUnmaterializedSettlementsRemainEligible();
        System.out.println("PASS legacy street-fabric migration: preserve anchored v15 roads, no duplicate graph rebuild");
    }

    private static void revisionGate() {
        check(ContentMigrationPolicy.shouldFreezeLegacyStreetFabric(15), "v15 must migrate");
        check(!ContentMigrationPolicy.shouldFreezeLegacyStreetFabric(16), "v16 already migrated");
    }

    private static void materializedLegacyRoadsFreezeWithoutMutation() {
        Faction faction = new Faction(8100, "Legacy Realm", "Regent");
        Settlement city = new Settlement(8101, "Legacy City", new SimPosition(1400, -900), 4200, 4800,
                SettlementOrigin.AUTHORED_SEED, true, DevelopmentMode.AUTO, SettlementRole.CAPITAL);
        faction.addSettlement(city);
        city.restoreConstructionCompleted("keep:0");
        city.restoreConstructionCompleted("road:arterial:historic_core:0");
        city.restoreConstructionCompleted("house:0");
        Set<String> beforeReceipts = Set.copyOf(city.completedConstruction());
        SimPosition beforePosition = city.position();
        SettlementOrigin beforeOrigin = city.origin();

        check(SettlementConstructionPolicy.migrateLegacyStreetFabric(city), "legacy road marker added");
        check(SettlementConstructionPolicy.hasLegacyStreetFabric(city), "legacy marker persisted");
        check(city.completedConstruction().containsAll(beforeReceipts), "existing receipts preserved");
        check(city.position().equals(beforePosition), "migration must not move settlement");
        check(city.origin() == beforeOrigin, "migration must not rewrite origin");
        check(!SettlementConstructionPolicy.allowsAutomaticCoreFabric(city),
                "legacy physical core must not reconcile incompatible graph layout");
        check(SettlementPlanner.boundary(faction, city).isEmpty(),
                "transport must not invent graph-era gates on frozen legacy city");
        check(!SettlementConstructionPolicy.migrateLegacyStreetFabric(city), "migration idempotent");
    }

    private static void oldMorphologyResetCannotLoseLegacyEvidence() {
        Settlement old = new Settlement(8151, "Very Old City", new SimPosition(2400, -300), 3500, 3900,
                SettlementOrigin.AUTHORED_SEED, true, DevelopmentMode.AUTO, SettlementRole.CITY);
        old.restoreConstructionCompleted("road:arterial:historic_core:0");
        old.restoreConstructionCompleted("market:0");
        boolean hadLegacyRoad = SettlementConstructionPolicy.hasLegacyRoadReceipt(old);
        check(hadLegacyRoad, "pre-reset legacy road evidence detected");
        old.resetConstructionCompletion(); // revision <10 morphology migration
        check(!SettlementConstructionPolicy.hasLegacyRoadReceipt(old), "old receipts cleared by historic migration");
        check(SettlementConstructionPolicy.markLegacyStreetFabric(old), "captured evidence restores v16 freeze marker");
        check(SettlementConstructionPolicy.hasLegacyStreetFabric(old), "v16 marker survives after old reset");
        check(!SettlementConstructionPolicy.allowsAutomaticCoreFabric(old),
                "very old materialized city must not receive incompatible graph rebuild");
    }

    private static void graphEraAndUnmaterializedSettlementsRemainEligible() {
        Settlement fresh = new Settlement(8201, "Fresh City", new SimPosition(0, 0), 4200, 4800,
                SettlementOrigin.AUTHORED_SEED, false, DevelopmentMode.AUTO, SettlementRole.CAPITAL);
        check(!SettlementConstructionPolicy.migrateLegacyStreetFabric(fresh), "unmaterialized city unchanged");
        check(SettlementConstructionPolicy.allowsAutomaticCoreFabric(fresh), "fresh city uses graph core");

        Settlement graphEra = new Settlement(8202, "Graph City", new SimPosition(6000, 0), 4200, 4800,
                SettlementOrigin.AUTHORED_SEED, true, DevelopmentMode.AUTO, SettlementRole.CAPITAL);
        graphEra.restoreConstructionCompleted("roadgraph:radial_capital:historic_core:0");
        check(!SettlementConstructionPolicy.migrateLegacyStreetFabric(graphEra), "graph-era road is not legacy");
        check(SettlementConstructionPolicy.allowsAutomaticCoreFabric(graphEra),
                "graph-era anchored settlement remains eligible for normal completion");
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
}
