package dev.livingrealms.sim.world;

import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.faction.ResourceType;
import dev.livingrealms.sim.faction.Settlement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Keeps Living Realms civilization continuous across the whole Overworld, not only the authored
 * starter belt. When a player travels far from every canonical settlement, this seeder plants a
 * sparse frontier hamlet/village under the nearest surface kingdom so the world never empties out.
 *
 * <p>Spacing matches {@link SettlementDensitySeeder#MIN_SETTLEMENT_SPACING} so wilderness belts stay
 * wide enough for player founding and inter-city travel.</p>
 */
public final class FrontierExplorationSeeder {
    /** Minimum distance from any existing settlement before a frontier seed may spawn. */
    public static final double GAP_BEFORE_SEED = 3_400.0;
    /** Preferred spacing between frontier settlements worldwide. */
    public static final double FRONTIER_SPACING = SettlementDensitySeeder.MIN_SETTLEMENT_SPACING;
    /** Soft cap of exploration-seeded settlements per ordinary realm. */
    public static final int MAX_FRONTIER_PER_REALM = 24;
    private static final String[] SUFFIXES = {
            "Outpost", "March", "Wilds", "Reach", "Camp", "Hold", "Crossing", "Rest",
            "Lookout", "Trailhead", "Hollow", "Ridge", "Ford", "Glen"
    };

    private FrontierExplorationSeeder() {}

    /**
     * Ensures the area around {@code observer} is not a civilization desert. Returns how many
     * settlements were created (0 or 1 per call).
     */
    public static int ensureNear(SimulationState state, SimPosition observer) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(observer, "observer");
        Settlement nearest = nearestSettlement(state, observer);
        if (nearest != null && nearest.position().distanceTo(observer) < GAP_BEFORE_SEED) return 0;

        Faction host = nearestHostFaction(state, observer);
        if (host == null) return 0;
        long frontierCount = host.settlements().stream().filter(s -> isFrontierName(s.name())).count();
        if (frontierCount >= MAX_FRONTIER_PER_REALM) return 0;

        SimPosition candidate = placeAround(state, observer, host.id());
        if (candidate == null) return 0;
        if (tooClose(state, candidate, FRONTIER_SPACING)) return 0;

        String name = uniqueName(state, host, candidate);
        int pop = 54 + Math.floorMod((int) mix(state.seed() ^ host.id() ^ Math.round(candidate.x())), 70); // 54–123
        Settlement settlement = new Settlement(state.nextId(), name, candidate, pop, (int) Math.ceil(pop * 1.18));
        // Frontier outposts start without completion keys — materializer builds well/farm.
        host.addSettlement(settlement);
        host.stockpile().add(ResourceType.GRAIN, 240);
        host.stockpile().add(ResourceType.BREAD, 180);
        host.stockpile().add(ResourceType.WOOD, 180);
        host.stockpile().add(ResourceType.STONE, 220);
        host.addTreasury(160);
        state.history().add(new WorldEvent(state.clock().day(), "frontier_outpost_seeded",
                host.name() + " claimed frontier " + name + " at X " + Math.round(candidate.x()) + " Z " + Math.round(candidate.z())));
        return 1;
    }

    private static Faction nearestHostFaction(SimulationState state, SimPosition observer) {
        Faction best = null;
        double bestDist = Double.POSITIVE_INFINITY;
        for (Faction faction : state.factions()) {
            if (WizardTreesSeeder.isWizardTrees(faction) || faction.settlements().isEmpty()) continue;
            Settlement capital = faction.settlements().stream()
                    .max(Comparator.comparingInt(Settlement::population).thenComparingLong(Settlement::id))
                    .orElse(null);
            if (capital == null) continue;
            double d = capital.position().distanceTo(observer);
            if (d < bestDist) {
                bestDist = d;
                best = faction;
            }
        }
        return best;
    }

    private static Settlement nearestSettlement(SimulationState state, SimPosition from) {
        Settlement best = null;
        double bestDist = Double.POSITIVE_INFINITY;
        for (Faction faction : state.factions()) {
            for (Settlement settlement : faction.settlements()) {
                double d = from.distanceTo(settlement.position());
                if (d < bestDist) {
                    bestDist = d;
                    best = settlement;
                }
            }
        }
        return best;
    }

    private static SimPosition placeAround(SimulationState state, SimPosition observer, long factionId) {
        // Place well outside the founding clearance so players can still found nearby.
        double outer = FRONTIER_SPACING + 100.0;
        for (int attempt = 0; attempt < 24; attempt++) {
            long m = mix(state.seed() ^ factionId ^ (attempt * 0x9E3779B97F4A7C15L) ^ Math.round(observer.x() * 17 + observer.z()));
            double angle = ((m >>> 11) & 0xFFFFL) / 65535.0 * Math.PI * 2.0;
            double radius = outer + ((m >>> 27) & 0x3FFL); // outer..(outer+1023) from the observer
            SimPosition p = new SimPosition(observer.x() + Math.cos(angle) * radius, observer.z() + Math.sin(angle) * radius);
            if (!tooClose(state, p, FRONTIER_SPACING)) return p;
        }
        return null;
    }

    private static boolean tooClose(SimulationState state, SimPosition p, double spacing) {
        for (Faction faction : state.factions()) {
            for (Settlement settlement : faction.settlements()) {
                if (p.distanceTo(settlement.position()) < spacing) return true;
            }
        }
        return false;
    }

    private static String uniqueName(SimulationState state, Faction host, SimPosition at) {
        String prefix = host.name()
                .replace("Kingdom of ", "")
                .replace("High Kingdom of ", "")
                .replace(" Dominion", "")
                .replace(" Crown", "")
                .trim();
        int salt = Math.floorMod((int) mix(state.seed() ^ Math.round(at.x()) ^ (Math.round(at.z()) * 31L)), SUFFIXES.length);
        List<String> tries = new ArrayList<>();
        tries.add(prefix + " " + SUFFIXES[salt]);
        tries.add(prefix + " " + SUFFIXES[(salt + 3) % SUFFIXES.length]);
        tries.add(prefix + " " + SUFFIXES[(salt + 7) % SUFFIXES.length] + " " + (1 + Math.floorMod((int) Math.abs(at.x() + at.z()), 9)));
        for (String name : tries) {
            if (settlement(host, name) == null && globallyUnique(state, name)) return name;
        }
        return prefix + " Frontier " + state.nextId();
    }

    private static boolean isFrontierName(String name) {
        for (String suffix : SUFFIXES) {
            if (name.endsWith(" " + suffix) || name.contains(" " + suffix + " ")) return true;
        }
        return name.contains(" Frontier ");
    }

    private static boolean globallyUnique(SimulationState state, String name) {
        for (Faction faction : state.factions()) if (settlement(faction, name) != null) return false;
        return true;
    }

    private static Settlement settlement(Faction faction, String name) {
        for (Settlement settlement : faction.settlements()) if (settlement.name().equals(name)) return settlement;
        return null;
    }

    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
