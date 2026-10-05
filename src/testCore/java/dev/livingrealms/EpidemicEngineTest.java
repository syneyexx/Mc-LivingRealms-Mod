package dev.livingrealms;

import dev.livingrealms.sim.civilization.EpidemicEngine;
import dev.livingrealms.sim.civilization.EpidemicRecord;
import dev.livingrealms.sim.civilization.SettlementCivilizationState;
import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.faction.ResourceType;
import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.util.DeterministicRng;
import dev.livingrealms.sim.world.SimPosition;
import dev.livingrealms.sim.world.SimulationState;

/**
 * Wave 25: health/epidemic path extracted as EpidemicEngine.
 * Demography births/deaths remain inside CivilizationEngine (not yet a separate engine).
 */
public final class EpidemicEngineTest {
    private EpidemicEngineTest() {}

    public static void main(String[] args) {
        SimulationState state = new SimulationState(0x48514C5448L);
        Faction realm = new Faction(state.nextId(), "Fever March", "Lady Quill");
        Settlement town = new Settlement(state.nextId(), "Sickford", new SimPosition(10, 10), 180, 100);
        town.setFoodSecurity(0.4);
        town.setPublicOrder(0.5);
        realm.addSettlement(town);
        realm.stockpile().add(ResourceType.FOOD, 80);
        state.addFaction(realm);

        SettlementCivilizationState civ = state.ensureSettlementCivilization(town.id(), realm.id());
        civ.restore(realm.id(), 0.2, 0.85, 0.2, 0.35, 0.1, 0.1, 0.5, 0.0, 0.2, -10_000, -10_000);

        DeterministicRng rng = new DeterministicRng(state.seed() ^ 0xE91D5L);
        EpidemicEngine.simulateEpidemics(state, rng);
        check(!state.epidemics().isEmpty() || civ.diseasePressure() > 0.5,
                "high disease pressure must start epidemic or retain pressure");

        for (int i = 0; i < 20; i++) {
            EpidemicEngine.simulateEpidemics(state, new DeterministicRng(state.seed() ^ (0xE91D5L + i)));
            EpidemicEngine.spreadDiseaseAlongTrade(state, new DeterministicRng(state.seed() ^ (0x74724144L + i)));
        }

        boolean any = false;
        for (EpidemicRecord e : state.epidemics()) {
            any = true;
            check(e.settlementId() == town.id() || e.settlementId() > 0, "epidemic settlement id");
            check(e.diseaseKey() != null && !e.diseaseKey().isBlank(), "disease key");
        }
        check(any || civ.diseasePressure() > 0.4, "health engine path left epidemic or disease pressure");

        System.out.println("PASS epidemic/health engine: disease pressure + simulateEpidemics + trade spread callable");
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
}
