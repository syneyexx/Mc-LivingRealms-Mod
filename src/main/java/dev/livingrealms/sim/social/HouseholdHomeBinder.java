package dev.livingrealms.sim.social;

import dev.livingrealms.sim.construction.ConstructionIntent;
import dev.livingrealms.sim.construction.HousingCapacity;
import dev.livingrealms.sim.construction.SettlementPlanner;
import dev.livingrealms.sim.construction.StructureRole;
import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.faction.Settlement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Binds households to completed physical house keys instead of raw projection-slot hashes. */
public final class HouseholdHomeBinder {
    private HouseholdHomeBinder() {}

    public static String assignHomeKey(Faction faction, Settlement settlement, long householdId) {
        Objects.requireNonNull(faction, "faction");
        Objects.requireNonNull(settlement, "settlement");
        List<ConstructionIntent> houses = completedHouses(faction, settlement);
        if (houses.isEmpty()) {
            // Fallback only when no completed house exists yet.
            return "house:" + Math.floorMod(householdId, 12);
        }
        // Prefer denser completed houses for larger settlements, then stable round-robin by household id.
        houses.sort(Comparator
                .comparingInt((ConstructionIntent i) -> HousingCapacity.representedResidents(i)).reversed()
                .thenComparing(ConstructionIntent::key));
        ConstructionIntent chosen = houses.get(Math.floorMod(Long.hashCode(householdId), houses.size()));
        return chosen.key();
    }

    public static boolean isValidHomeKey(Faction faction, Settlement settlement, String homeKey) {
        if (homeKey == null || homeKey.isBlank()) return false;
        if (homeKey.startsWith("orphanage:") || homeKey.startsWith("community_care:") || homeKey.startsWith("exile:")) {
            return true;
        }
        if (!homeKey.startsWith("house:")) return false;
        return completedHouses(faction, settlement).stream().anyMatch(i -> i.key().equals(homeKey));
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
