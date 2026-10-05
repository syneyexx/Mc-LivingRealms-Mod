package dev.livingrealms.sim.construction;

import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.world.SimulationState;
import java.util.Locale;
import java.util.Objects;

/**
 * Capability query for settlement infrastructure by function, independent of authorship.
 * A player-registered town hall or a Living Realms materialized town hall both satisfy
 * {@link StructureRole#TOWN_HALL} without forging fake construction receipts.
 */
public final class SettlementInfrastructure {
    private SettlementInfrastructure() {}

    public static boolean hasFunctionalRole(SimulationState state, Settlement settlement, StructureRole role) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(settlement, "settlement");
        Objects.requireNonNull(role, "role");
        if (hasMaterializedRole(settlement, role)) return true;
        if (hasRegisteredPlayerRole(state, settlement, role)) return true;
        return false;
    }

    public static boolean hasMaterializedRole(Settlement settlement, StructureRole role) {
        Objects.requireNonNull(settlement, "settlement");
        Objects.requireNonNull(role, "role");
        String prefix = role.name().toLowerCase(Locale.ROOT) + ":";
        // Legacy civic keys for town hall before StructureRole.TOWN_HALL existed.
        if (role == StructureRole.TOWN_HALL) {
            return settlement.completedConstruction().stream().anyMatch(k ->
                    k.startsWith("town_hall:") || k.startsWith("townhall:") || k.startsWith(prefix));
        }
        return settlement.completedConstruction().stream().anyMatch(k -> k.startsWith(prefix));
    }

    public static boolean hasRegisteredPlayerRole(SimulationState state, Settlement settlement, StructureRole role) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(settlement, "settlement");
        Objects.requireNonNull(role, "role");
        RegisteredPlayerStructure.Role playerRole = toPlayerRole(role);
        if (playerRole == null) return false;
        for (RegisteredPlayerStructure s : state.registeredPlayerStructures()) {
            if (s.settlementId() == settlement.id() && s.valid() && s.role() == playerRole) return true;
        }
        return false;
    }

    public static RegisteredPlayerStructure.Role toPlayerRole(StructureRole role) {
        return switch (role) {
            case HOUSE -> RegisteredPlayerStructure.Role.HOUSE;
            case TOWN_HALL -> RegisteredPlayerStructure.Role.TOWN_HALL;
            case WAREHOUSE -> RegisteredPlayerStructure.Role.WAREHOUSE;
            case WORKSHOP -> RegisteredPlayerStructure.Role.WORKSHOP;
            case MARKET -> RegisteredPlayerStructure.Role.MARKET;
            case TAVERN -> RegisteredPlayerStructure.Role.TAVERN;
            case TEMPLE -> RegisteredPlayerStructure.Role.TEMPLE;
            case BARRACKS -> RegisteredPlayerStructure.Role.BARRACKS;
            case FARM -> RegisteredPlayerStructure.Role.FARM;
            case PASTURE -> RegisteredPlayerStructure.Role.PASTURE;
            case CLINIC -> RegisteredPlayerStructure.Role.CLINIC;
            case SCHOOL -> RegisteredPlayerStructure.Role.SCHOOL;
            default -> null;
        };
    }
}
