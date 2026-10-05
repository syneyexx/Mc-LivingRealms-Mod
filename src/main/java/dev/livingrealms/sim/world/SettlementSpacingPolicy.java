package dev.livingrealms.sim.world;

import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.faction.SettlementRole;
import java.util.Objects;

/**
 * Type-aware civilization spacing policy.
 *
 * <p>Minimum distances are collision/exclusion floors. Preferred ranges describe placement goals;
 * geography may move a site outside them, but a single global settlement floor must never be used
 * as a substitute for settlement hierarchy.</p>
 */
public final class SettlementSpacingPolicy {
    public record Range(double min, double max) {
        public Range {
            if (!(min >= 0) || !(max >= min)) throw new IllegalArgumentException("range");
        }
        public double span() { return max - min; }
        public double at(double t) {
            double clamped = Math.max(0.0, Math.min(1.0, t));
            return min + span() * clamped;
        }
    }

    public static final Range CAPITAL_TO_CAPITAL = new Range(3000, 4500);
    public static final Range CAPITAL_TO_TOWN = new Range(650, 1200);
    public static final Range TOWN_TO_TOWN = new Range(700, 1300);
    public static final Range CAPITAL_TO_CITY = new Range(1600, 2600);
    public static final Range TOWN_TO_VILLAGE = new Range(350, 650);
    public static final Range VILLAGE_TO_VILLAGE = new Range(300, 600);
    public static final Range VILLAGE_TO_HAMLET = new Range(180, 350);
    public static final Range HAMLET_TO_HAMLET = new Range(150, 300);

    private SettlementSpacingPolicy() {}

    public static double minimumDistance(Settlement a, Settlement b) {
        Objects.requireNonNull(a, "a");
        Objects.requireNonNull(b, "b");
        return minimumDistance(a.role(), b.role());
    }

    public static double minimumDistance(SettlementRole a, SettlementRole b) {
        Objects.requireNonNull(a, "a");
        Objects.requireNonNull(b, "b");
        if (a == SettlementRole.SPECIAL || b == SettlementRole.SPECIAL) return 80.0;

        if (a == SettlementRole.CAPITAL && b == SettlementRole.CAPITAL) return 2500.0;
        if (pair(a, b, SettlementRole.CAPITAL, SettlementRole.CITY)) return 1600.0;
        if (pair(a, b, SettlementRole.CAPITAL, SettlementRole.TOWN)) return 650.0;
        if (pair(a, b, SettlementRole.CAPITAL, SettlementRole.VILLAGE)) return 300.0;
        if (pair(a, b, SettlementRole.CAPITAL, SettlementRole.HAMLET)) return 180.0;

        if (a == SettlementRole.CITY && b == SettlementRole.CITY) return 1600.0;
        if (pair(a, b, SettlementRole.CITY, SettlementRole.TOWN)) return 650.0;
        if (pair(a, b, SettlementRole.CITY, SettlementRole.VILLAGE)) return 300.0;
        if (pair(a, b, SettlementRole.CITY, SettlementRole.HAMLET)) return 180.0;

        if (a == SettlementRole.TOWN && b == SettlementRole.TOWN) return 650.0;
        if (pair(a, b, SettlementRole.TOWN, SettlementRole.VILLAGE)) return 300.0;
        if (pair(a, b, SettlementRole.TOWN, SettlementRole.HAMLET)) return 180.0;

        if (a == SettlementRole.VILLAGE && b == SettlementRole.VILLAGE) return 280.0;
        if (pair(a, b, SettlementRole.VILLAGE, SettlementRole.HAMLET)) return 160.0;

        return 150.0; // hamlet <-> hamlet
    }

    public static Range preferredRange(SettlementRole parent, SettlementRole child) {
        Objects.requireNonNull(parent, "parent");
        Objects.requireNonNull(child, "child");
        if (parent == SettlementRole.SPECIAL || child == SettlementRole.SPECIAL) return new Range(100, 350);
        if (parent == SettlementRole.CAPITAL && child == SettlementRole.CAPITAL) return CAPITAL_TO_CAPITAL;
        if (pair(parent, child, SettlementRole.CAPITAL, SettlementRole.CITY)) return CAPITAL_TO_CITY;
        if (pair(parent, child, SettlementRole.CAPITAL, SettlementRole.TOWN)) return CAPITAL_TO_TOWN;
        if (parent == SettlementRole.TOWN && child == SettlementRole.TOWN) return TOWN_TO_TOWN;
        if (pair(parent, child, SettlementRole.TOWN, SettlementRole.VILLAGE)) return TOWN_TO_VILLAGE;
        if (parent == SettlementRole.VILLAGE && child == SettlementRole.VILLAGE) return VILLAGE_TO_VILLAGE;
        if (pair(parent, child, SettlementRole.VILLAGE, SettlementRole.HAMLET)) return VILLAGE_TO_HAMLET;
        if (parent == SettlementRole.HAMLET && child == SettlementRole.HAMLET) return HAMLET_TO_HAMLET;
        if (pair(parent, child, SettlementRole.TOWN, SettlementRole.HAMLET)) return new Range(220, 500);
        if (pair(parent, child, SettlementRole.CAPITAL, SettlementRole.VILLAGE)) return new Range(450, 900);
        if (pair(parent, child, SettlementRole.CAPITAL, SettlementRole.HAMLET)) return new Range(250, 700);
        return new Range(300, 800);
    }

    private static boolean pair(SettlementRole a, SettlementRole b, SettlementRole x, SettlementRole y) {
        return (a == x && b == y) || (a == y && b == x);
    }
}
