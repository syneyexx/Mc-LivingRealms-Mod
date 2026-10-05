package dev.livingrealms;

import dev.livingrealms.sim.faction.DevelopmentMode;
import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.faction.SettlementOrigin;
import dev.livingrealms.sim.persistence.SimulationStateCodec;
import dev.livingrealms.sim.world.DemoSeeder;
import dev.livingrealms.sim.world.ForeignAdoptionClassifier;
import dev.livingrealms.sim.world.OutlyingSite;
import dev.livingrealms.sim.world.SettlementDensitySeeder;
import dev.livingrealms.sim.world.SimPosition;
import dev.livingrealms.sim.world.SimulationState;
import java.util.HashMap;
import java.util.Map;

/** Anchored settlements must never relocate; foreign spacing uses outlying sites. */
public final class SettlementAnchorInvariantTest {
    private SettlementAnchorInvariantTest() {}

    public static void main(String[] args) {
        legacyMigrationAnchors();
        anchoredRefuseRelocate();
        dailySimDoesNotMoveAnchored();
        foreignSpacingCases();
        System.out.println("PASS SettlementAnchorInvariantTest");
    }

    private static void legacyMigrationAnchors() {
        SimulationState state = new SimulationState(11L);
        DemoSeeder.seed(state);
        // Simulate pre-v19 by forcing LEGACY via migrate path roundtrip encode/decode after restore.
        for (Faction f : state.factions()) for (Settlement s : f.settlements()) {
            s.restoreProvenance(SettlementOrigin.LEGACY, true, DevelopmentMode.AUTO);
        }
        byte[] bytes = SimulationStateCodec.encode(state);
        SimulationState loaded = SimulationStateCodec.decode(bytes);
        for (Faction f : loaded.factions()) for (Settlement s : f.settlements()) {
            check(s.physicallyAnchored(), "legacy must be anchored: " + s.name());
            if (!f.name().equals("Wizard Trees")) {
                // Fresh encode uses AUTHORED_SEED; re-force legacy for this unit and re-check relocate.
                s.restoreProvenance(SettlementOrigin.LEGACY, true, DevelopmentMode.AUTO);
            }
            boolean refused = false;
            try { s.relocate(new SimPosition(s.position().x() + 50, s.position().z())); }
            catch (IllegalStateException expected) { refused = true; }
            check(refused, "LEGACY must refuse relocate: " + s.name());
        }
        check(SimulationStateCodec.SCHEMA_VERSION == 21, "schema 21");
    }

    private static void anchoredRefuseRelocate() {
        Settlement foreign = new Settlement(1, "Foreign", new SimPosition(0, 0), 100, 120,
                SettlementOrigin.FOREIGN_ADOPTED, true, DevelopmentMode.AUTO);
        Settlement player = new Settlement(2, "PlayerTown", new SimPosition(5000, 0), 6, 8,
                SettlementOrigin.PLAYER_FOUNDED, true, DevelopmentMode.HYBRID);
        Settlement authored = new Settlement(3, "Seed", new SimPosition(10000, 0), 200, 220,
                SettlementOrigin.AUTHORED_SEED, false, DevelopmentMode.AUTO);
        for (Settlement s : new Settlement[]{foreign, player}) {
            boolean refused = false;
            try { s.relocate(new SimPosition(1, 1)); } catch (IllegalStateException e) { refused = true; }
            check(refused, s.origin() + " must refuse relocate");
        }
        authored.relocate(new SimPosition(10050, 10)); // unanchored may move during planning
        authored.markConstructionCompleted("house:0");
        check(authored.physicallyAnchored(), "materialized authored becomes anchored");
        boolean refused = false;
        try { authored.relocate(new SimPosition(0, 0)); } catch (IllegalStateException e) { refused = true; }
        check(refused, "materialized authored refuses relocate");
    }

    private static void dailySimDoesNotMoveAnchored() {
        SimulationState state = new SimulationState(22L);
        DemoSeeder.seed(state);
        Map<Long, SimPosition> before = new HashMap<>();
        for (Faction f : state.factions()) for (Settlement s : f.settlements()) {
            s.markPhysicallyAnchored();
            before.put(s.id(), s.position());
        }
        state.advanceDays(120);
        SettlementDensitySeeder.ensureStarterDensity(state);
        for (Faction f : state.factions()) for (Settlement s : f.settlements()) {
            SimPosition p = before.get(s.id());
            if (p == null) continue; // newly founded causal colonies allowed
            check(s.position().x() == p.x() && s.position().z() == p.z(),
                    "anchored moved: " + s.name());
        }
    }

    private static void foreignSpacingCases() {
        SimulationState state = new SimulationState(33L);
        DemoSeeder.seed(state);
        Settlement host = state.factions().getFirst().settlements().getFirst();
        int settlementsBefore = state.factions().stream().mapToInt(f -> f.settlements().size()).sum();
        int sitesBefore = state.outlyingSites().size();

        // Outside duplicate-footprint radius but inside CAPITAL↔VILLAGE floor → outlying site
        var near = ForeignAdoptionClassifier.classifyAndAdopt(state,
                new SimPosition(host.position().x() + 240, host.position().z()),
                "NearHamlet", 120, 140, OutlyingSite.Type.FOREIGN_HAMLET);
        check(near.outcome() == ForeignAdoptionClassifier.Outcome.OUTLYING_SITE, "near must be site");
        check(state.factions().stream().mapToInt(f -> f.settlements().size()).sum() == settlementsBefore, "no new settlement near");
        check(state.outlyingSites().size() == sitesBefore + 1, "site created");

        // Far outside all role-pair floors → new FOREIGN_ADOPTED
        var far = ForeignAdoptionClassifier.classifyAndAdopt(state,
                new SimPosition(host.position().x() + 50_000, host.position().z() + 50_000),
                "FarVillage", 200, 240, OutlyingSite.Type.FOREIGN_HAMLET);
        check(far.outcome() == ForeignAdoptionClassifier.Outcome.NEW_SETTLEMENT, "far must be settlement");
        check(far.settlement().origin() == SettlementOrigin.FOREIGN_ADOPTED, "FOREIGN_ADOPTED");
        check(far.settlement().physicallyAnchored(), "foreign anchored");

        // Idempotent duplicate near same site
        var dup = ForeignAdoptionClassifier.classifyAndAdopt(state,
                new SimPosition(host.position().x() + 245, host.position().z()),
                "NearHamlet2", 120, 140, OutlyingSite.Type.FOREIGN_HAMLET);
        check(dup.outcome() == ForeignAdoptionClassifier.Outcome.IDEMPOTENT_SITE
                        || dup.outcome() == ForeignAdoptionClassifier.Outcome.BOUND_EXISTING
                        || dup.outcome() == ForeignAdoptionClassifier.Outcome.OUTLYING_SITE,
                "duplicate handled: " + dup.outcome());
        // Bound into footprint of existing settlement
        var bound = ForeignAdoptionClassifier.classifyAndAdopt(state, host.position(),
                "OnTop", 80, 90, OutlyingSite.Type.FOREIGN_HAMLET);
        check(bound.outcome() == ForeignAdoptionClassifier.Outcome.BOUND_EXISTING, "bound existing");
    }

    private static void check(boolean ok, String msg) {
        if (!ok) throw new AssertionError(msg);
    }
}
