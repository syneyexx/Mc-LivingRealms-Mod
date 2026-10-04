package dev.livingrealms.sim.construction;

import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.faction.Settlement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Bounded immutable plan cache. Does not cache mutable canonical objects — only frozen intent lists
 * keyed by settlement inputs that affect planner output.
 */
public final class SettlementPlanCache {
    private static final int MAX_ENTRIES = 512;
    private static final Map<String, List<ConstructionIntent>> CACHE = new LinkedHashMap<>(256, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, List<ConstructionIntent>> eldest) {
            return size() > MAX_ENTRIES;
        }
    };

    private SettlementPlanCache() {}

    public static List<ConstructionIntent> plan(Faction faction, Settlement settlement) {
        Objects.requireNonNull(faction, "faction");
        Objects.requireNonNull(settlement, "settlement");
        String key = cacheKey(faction, settlement);
        synchronized (CACHE) {
            List<ConstructionIntent> hit = CACHE.get(key);
            if (hit != null) return hit;
        }
        List<ConstructionIntent> planned = List.copyOf(SettlementPlanner.plan(faction, settlement));
        synchronized (CACHE) {
            CACHE.put(key, planned);
        }
        return planned;
    }

    public static void clear() {
        synchronized (CACHE) {
            CACHE.clear();
        }
    }

    public static int size() {
        synchronized (CACHE) {
            return CACHE.size();
        }
    }

    private static String cacheKey(Faction faction, Settlement settlement) {
        // Bucket population/housing so tiny day-to-day churn does not thrash the cache.
        int popBucket = settlement.population() / 50;
        int houseBucket = settlement.housing() / 50;
        int completed = settlement.completedConstruction().size();
        return faction.id() + ":" + settlement.id() + ":" + settlement.tier() + ":" + popBucket + ":"
                + houseBucket + ":" + settlement.developmentPriority() + ":" + completed + ":"
                + Math.round(faction.technology() * 20);
    }
}
