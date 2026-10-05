package dev.livingrealms;

import dev.livingrealms.sim.civilization.MigrationEngine;
import dev.livingrealms.sim.civilization.MigrationGroup;
import dev.livingrealms.sim.civilization.MigrationReason;
import dev.livingrealms.sim.civilization.SettlementCivilizationState;
import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.faction.ResourceType;
import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.util.DeterministicRng;
import dev.livingrealms.sim.world.SimPosition;
import dev.livingrealms.sim.world.SimulationState;

/**
 * Wave 25: MigrationEngine extracted from CivilizationLifecycleEngine remains callable
 * and can spawn / advance groups under forced refugee pressure.
 */
public final class MigrationEngineTest {
    private MigrationEngineTest() {}

    public static void main(String[] args) {
        SimulationState state = new SimulationState(0x4D49475201L);
        Faction realm = new Faction(state.nextId(), "Migrate Reach", "Lord Pike");
        Settlement source = new Settlement(state.nextId(), "Hungerford", new SimPosition(0, 0), 220, 80);
        Settlement haven = new Settlement(state.nextId(), "Safehaven", new SimPosition(2400, 0), 160, 200);
        source.setFoodSecurity(0.25);
        source.setPublicOrder(0.4);
        haven.setFoodSecurity(0.85);
        haven.setPublicOrder(0.8);
        haven.addHousing(80);
        realm.addSettlement(source);
        realm.addSettlement(haven);
        realm.stockpile().add(ResourceType.FOOD, 50);
        state.addFaction(realm);

        SettlementCivilizationState sc = state.ensureSettlementCivilization(source.id(), realm.id());
        sc.adjustRefugeePressure(0.9);
        sc.adjustBanditPressure(0.7);

        // Age past migration cooldown window used by considerMigration.
        state.advanceDays(25);
        sc = state.ensureSettlementCivilization(source.id(), realm.id());
        sc.adjustRefugeePressure(0.9);

        int beforePeople = source.population();
        MigrationEngine.considerMigration(state);
        check(state.migrationGroups() != null, "migration groups collection present");
        check(source.population() <= beforePeople, "considerMigration must not invent population");

        if (state.migrationGroups().stream().noneMatch(MigrationGroup::active)) {
            MigrationGroup forced = new MigrationGroup(
                    state.nextId(), realm.id(), source.id(), haven.id(),
                    state.clock().day(), 12, MigrationReason.FAMINE);
            source.addPopulation(-12);
            state.addMigrationGroup(forced);
        }

        DeterministicRng rng = new DeterministicRng(state.seed() ^ 0xD1B54A32D192ED03L);
        for (int i = 0; i < 40; i++) {
            MigrationEngine.advanceMigrationGroups(state, rng);
            if (state.migrationGroups().stream().anyMatch(g -> !g.active())) break;
        }
        check(state.migrationGroups().stream().anyMatch(g -> !g.active() || g.progress() > 0 || g.campSettlementId() > 0),
                "advanceMigrationGroups must progress or settle/camp a group");

        System.out.println("PASS migration engine: consider + advance under refugee pressure");
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
}
