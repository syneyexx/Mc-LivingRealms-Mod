package dev.livingrealms.minecraft;

import dev.livingrealms.sim.construction.SettlementPlanner;
import dev.livingrealms.sim.economy.primary.PrimaryEconomyPlanner;
import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.world.SimulationState;

/**
 * Marks the infrastructure already present in an adopted vanilla/modded settlement as satisfied.
 * This prevents Living Realms from bulldozing a foreign village on first discovery. As population
 * grows, newly-created plan keys remain pending and Living Realms expands around the original site.
 */
public final class ForeignSettlementBootstrap {
    private ForeignSettlementBootstrap() {}

    public static int preserveExistingInfrastructure(SimulationState state, Faction faction, Settlement settlement) {
        int marked = 0;
        for (var intent : SettlementPlanner.plan(faction, settlement)) {
            if (settlement.markConstructionCompleted(intent.key())) marked++;
        }
        for (var intent : PrimaryEconomyPlanner.plan(state, faction, settlement)) {
            if (settlement.markConstructionCompleted(intent.key())) marked++;
        }
        return marked;
    }
}
