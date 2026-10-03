package dev.livingrealms.sim.territory;

import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.world.SimPosition;
import java.util.List;
import java.util.Objects;

/**
 * Stateless influence-field territory model. Claims are derived from canonical settlements so
 * borders automatically follow settlement growth, conquest and destruction without stale polygons.
 */
public final class TerritoryEngine {
    private TerritoryEngine() {}

    public static Jurisdiction resolve(List<Faction> factions, SimPosition position) {
        return resolve(factions, position, 12.0);
    }

    public static Jurisdiction resolve(List<Faction> factions, SimPosition position, double contestedMargin) {
        Objects.requireNonNull(factions, "factions");
        Objects.requireNonNull(position, "position");
        if (contestedMargin < 0 || !Double.isFinite(contestedMargin)) throw new IllegalArgumentException("contestedMargin");
        long firstId = 0, secondId = 0;
        double first = 0, second = 0;
        for (Faction faction : factions) {
            double strength = factionInfluence(faction, position);
            if (strength > first) {
                second = first; secondId = firstId;
                first = strength; firstId = faction.id();
            } else if (strength > second) {
                second = strength; secondId = faction.id();
            }
        }
        if (first <= 0.0001) return Jurisdiction.wilderness();
        boolean contested = secondId != 0 && first - second <= contestedMargin;
        return new Jurisdiction(firstId, secondId, first, second, contested);
    }

    public static double factionInfluence(Faction faction, SimPosition position) {
        Objects.requireNonNull(faction, "faction");
        Objects.requireNonNull(position, "position");
        double total = 0;
        for (Settlement settlement : faction.settlements()) total += settlementInfluence(settlement, position);
        return total;
    }

    public static double settlementInfluence(Settlement settlement, SimPosition position) {
        double radius = claimRadius(settlement);
        double distance = settlement.position().distanceTo(position);
        if (distance >= radius) return 0;
        double normalized = 1.0 - distance / radius;
        double capital = 10.0 + Math.sqrt(Math.max(1, settlement.population())) * 2.2;
        double infrastructure = 1.0 + Math.min(3.0, settlement.infrastructure()) * .35;
        return capital * infrastructure * normalized * normalized;
    }

    public static double claimRadius(Settlement settlement) {
        double tier = switch (settlement.tier()) {
            case CAMP -> 160;
            case HAMLET -> 240;
            case VILLAGE -> 360;
            case TOWN -> 520;
            case CITY -> 760;
            case METROPOLIS -> 1080;
        };
        return tier * (1.0 + Math.min(2.0, settlement.infrastructure()) * .15);
    }
}
