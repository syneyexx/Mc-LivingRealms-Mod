package dev.livingrealms.sim.civilization;

import dev.livingrealms.sim.faction.*;
import dev.livingrealms.sim.util.DeterministicRng;
import dev.livingrealms.sim.world.*;
import java.util.*;

/** Ruin formation, looting, reclaim, weathering, and causal historical traces (Wave 16). */
public final class HistoricalLegacyEngine {
    private HistoricalLegacyEngine() {}

    public static void simulateRuins(SimulationState state, DeterministicRng rng) {
        long day = state.clock().day();
        Map<Long, RuinSite> bySettlement = new HashMap<>();
        for (RuinSite r : state.ruinSites()) if (r.active()) bySettlement.put(r.originalSettlementId(), r);
        for (Faction faction : state.factions()) {
            for (Settlement settlement : faction.settlements()) {
                RuinSite ruin = bySettlement.get(settlement.id());
                if (settlement.population() <= 0) {
                    if (ruin == null) {
                        String cause = HistoricalTraceEngine.wartimeCause(state, faction, settlement);
                        ruin = new RuinSite(state.nextId(), settlement.id(), faction.id(), day, settlement.position(), settlement.name(), cause);
                        state.addRuinSite(ruin);
                        state.history().add(new WorldEvent(day, "settlement_became_ruin",
                                "ruin=" + ruin.id() + ", settlement=" + settlement.id() + ", faction=" + faction.id() + ", cause=" + cause));
                    } else if (!ruin.looted() && rng.chance(.002 + .008 * (1 - settlement.publicOrder()))) {
                        ruin.markLooted();
                        state.history().add(new WorldEvent(day, "ruin_looted", "ruin=" + ruin.id() + ", settlement=" + settlement.id()));
                    }
                } else if (ruin != null) {
                    ruin.reclaim();
                    state.history().add(new WorldEvent(day, "ruin_reclaimed",
                            "ruin=" + ruin.id() + ", settlement=" + settlement.id() + ", population=" + settlement.population()));
                }
            }
        }
        if (day % 30 == 0) for (RuinSite ruin : state.ruinSites()) if (ruin.active()) ruin.weather(.0025);
        HistoricalTraceEngine.materializeFromHistory(state);
    }
}
