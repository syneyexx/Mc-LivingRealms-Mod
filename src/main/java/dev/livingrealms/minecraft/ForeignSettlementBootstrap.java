package dev.livingrealms.minecraft;

import dev.livingrealms.sim.construction.ForeignSettlementAdoption;
import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.world.SimulationState;

/**
 * Adopts an existing foreign village/structure footprint without inventing phantom infrastructure.
 * Delegates to the Minecraft-free {@link ForeignSettlementAdoption} authority.
 */
public final class ForeignSettlementBootstrap {
    private ForeignSettlementBootstrap() {}

    public static int preserveExistingInfrastructure(SimulationState state, Faction faction, Settlement settlement) {
        int marked = ForeignSettlementAdoption.preserveExistingInfrastructure(state, faction, settlement);
        // Wave protectorate marker — non-productive FOREIGN_ADOPTED credit only.
        if (settlement.markForeignAdopted("foreign:protectorate")) marked++;
        return marked;
    }
}
