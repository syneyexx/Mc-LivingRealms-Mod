package dev.livingrealms.sim.social;

import dev.livingrealms.sim.construction.ConstructionIntent;
import dev.livingrealms.sim.construction.RegisteredPlayerStructure;
import dev.livingrealms.sim.construction.SettlementPlanner;
import dev.livingrealms.sim.construction.StructureRole;
import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.world.SimulationState;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Binds households to completed physical house keys instead of raw projection-slot hashes. */
public final class HouseholdHomeBinder {
    private HouseholdHomeBinder() {}

    public static String assignHomeKey(Faction faction, Settlement settlement, long householdId) {
        return assignHomeKey(faction, settlement, householdId, null);
    }

    public static String assignHomeKey(Faction faction, Settlement settlement, long householdId, SimulationState state) {
        Objects.requireNonNull(faction, "faction");
        Objects.requireNonNull(settlement, "settlement");
        List<String> keys = new ArrayList<>();
        for (ConstructionIntent intent : completedHouses(faction, settlement)) keys.add(intent.key());
        if (state != null) {
            for (RegisteredPlayerStructure s : state.registeredPlayerStructures()) {
                if (s.valid() && s.settlementId() == settlement.id() && s.role() == RegisteredPlayerStructure.Role.HOUSE) {
                    keys.add(playerHouseKey(s.id()));
                }
            }
        }
        if (keys.isEmpty()) {
            // Fallback only when no completed house exists yet.
            return "house:" + Math.floorMod(householdId, 12);
        }
        keys.sort(Comparator.naturalOrder());
        return keys.get(Math.floorMod(Long.hashCode(householdId), keys.size()));
    }

    public static boolean isValidHomeKey(Faction faction, Settlement settlement, String homeKey) {
        return isValidHomeKey(faction, settlement, homeKey, null);
    }

    public static boolean isValidHomeKey(Faction faction, Settlement settlement, String homeKey, SimulationState state) {
        if (homeKey == null || homeKey.isBlank()) return false;
        if (homeKey.startsWith("orphanage:") || homeKey.startsWith("community_care:") || homeKey.startsWith("exile:")) {
            return true;
        }
        if (homeKey.startsWith("player:house:") && state != null) {
            try {
                long id = Long.parseLong(homeKey.substring("player:house:".length()));
                return state.findRegisteredPlayerStructure(id)
                        .filter(s -> s.valid() && s.settlementId() == settlement.id()
                                && s.role() == RegisteredPlayerStructure.Role.HOUSE)
                        .isPresent();
            } catch (NumberFormatException ex) {
                return false;
            }
        }
        if (!homeKey.startsWith("house:")) return false;
        return completedHouses(faction, settlement).stream().anyMatch(i -> i.key().equals(homeKey));
    }

    public static String playerHouseKey(long registeredStructureId) {
        return "player:house:" + registeredStructureId;
    }

    public static ConstructionIntent resolveHomeIntent(Faction faction, Settlement settlement, String homeKey, int fallbackSlot) {
        List<ConstructionIntent> houses = completedHouses(faction, settlement);
        if (homeKey != null && !homeKey.isBlank()) {
            for (ConstructionIntent intent : houses) {
                if (intent.key().equals(homeKey)) return intent;
            }
        }
        if (houses.isEmpty()) return null;
        return houses.get(Math.floorMod(fallbackSlot, houses.size()));
    }

    public static void rebindIfInvalid(HouseholdState household, Faction faction, Settlement settlement) {
        Objects.requireNonNull(household, "household");
        if (!household.active()) return;
        if (isValidHomeKey(faction, settlement, household.homeKey())) return;
        household.setHomeKey(assignHomeKey(faction, settlement, household.id()));
    }

    private static List<ConstructionIntent> completedHouses(Faction faction, Settlement settlement) {
        List<ConstructionIntent> out = new ArrayList<>();
        for (ConstructionIntent intent : SettlementPlanner.plan(faction, settlement)) {
            if (intent.role() != StructureRole.HOUSE) continue;
            if (!settlement.isConstructionCompleted(intent.key())) continue;
            out.add(intent);
        }
        return out;
    }
}
