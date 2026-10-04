package dev.livingrealms.minecraft;

import dev.livingrealms.sim.construction.SettlementPlanner;
import dev.livingrealms.sim.construction.StructureRole;
import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.world.SimulationState;

/**
 * Adopts an existing foreign village/structure footprint without inventing phantom infrastructure.
 *
 * <p>Only residential/civic shells that are already implied by the foreign settlement are credited.
 * Mines, fisheries, lumber camps, roads and other Living Realms expansion intents remain pending so
 * the settlement can grow organically around the preserved foreign footprint.
 */
public final class ForeignSettlementBootstrap {
    private ForeignSettlementBootstrap() {}

    public static int preserveExistingInfrastructure(SimulationState state, Faction faction, Settlement settlement) {
        int marked = 0;
        // Explicit adoption marker — not a fake mine/road/fishery.
        if (settlement.markConstructionCompleted("foreign:adopted_footprint")) marked++;
        for (var intent : SettlementPlanner.plan(faction, settlement)) {
            if (!isAdoptedFootprintRole(intent.role())) continue;
            // Cap housing credits to a modest existing footprint; later population growth stays pending.
            if (intent.role() == StructureRole.HOUSE && houseIndex(intent.key()) >= adoptedHouseCount(settlement)) continue;
            if (settlement.markConstructionCompleted(intent.key())) marked++;
        }
        // Intentionally do NOT mark PrimaryEconomyPlanner intents (mine/lumber/fishery) complete.
        // Those require observed/physical verification and must not become phantom production sites.
        return marked;
    }

    private static boolean isAdoptedFootprintRole(StructureRole role) {
        return role == StructureRole.HOUSE
                || role == StructureRole.KEEP
                || role == StructureRole.WELL
                || role == StructureRole.MARKET
                || role == StructureRole.TEMPLE
                || role == StructureRole.FARM;
    }

    private static int adoptedHouseCount(Settlement settlement) {
        // Represent existing housing without claiming every planned LR house already exists.
        int byPop = Math.max(2, Math.min(12, (settlement.population() + 7) / 8));
        return byPop;
    }

    private static int houseIndex(String key) {
        if (key == null || !key.startsWith("house:")) return Integer.MAX_VALUE;
        try {
            return Integer.parseInt(key.substring("house:".length()));
        } catch (NumberFormatException ex) {
            return Integer.MAX_VALUE;
        }
    }
}
