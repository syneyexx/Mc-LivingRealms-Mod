package dev.livingrealms.sim.construction;

import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.world.SimulationState;

/**
 * Headless foreign-footprint adoption. Marks housing/civic shells as {@code FOREIGN_ADOPTED}
 * so production engines ignore them until Living Realms materializes real structures.
 */
public final class ForeignSettlementAdoption {
    private ForeignSettlementAdoption() {}

    public static int preserveExistingInfrastructure(SimulationState state, Faction faction, Settlement settlement) {
        int marked = 0;
        if (settlement.markForeignAdopted("foreign:adopted_footprint")) marked++;
        for (var intent : SettlementPlanner.plan(faction, settlement)) {
            if (!isAdoptedFootprintRole(intent.role())) continue;
            if (intent.role() == StructureRole.HOUSE && houseIndex(intent.key()) >= adoptedHouseCount(settlement)) continue;
            if (settlement.markForeignAdopted(intent.key())) marked++;
        }
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
        return Math.max(2, Math.min(12, (settlement.population() + 7) / 8));
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
