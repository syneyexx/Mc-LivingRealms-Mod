package dev.livingrealms.sim.construction;

import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.faction.SettlementGeographyProfile;
import java.util.Locale;
import java.util.Objects;

/**
 * Geography- and history-derived settlement street morphology.
 * Selection is deterministic and context-driven — never a pure random pick from a flat enum.
 */
public enum SettlementMorphology {
    ORGANIC_MEDIEVAL,
    MARKET_CROSS,
    RADIAL_CAPITAL,
    LINEAR_VALLEY,
    RIVER_TOWN,
    COASTAL_PORT,
    HILL_TOWN,
    WALLED_CORE,
    INDUSTRIAL_EDGE,
    PLANNED_BOULEVARD;

    public String wireName() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Geography/tier-only derivation when faction context is unavailable. */
    public static SettlementMorphology derive(Settlement settlement) {
        Objects.requireNonNull(settlement, "settlement");
        return derive(settlement, false, 0.1);
    }

    /**
     * Derive morphology from geography, tier, capital status and a stable settlement salt.
     * Randomness only breaks ties between equally plausible patterns.
     */
    public static SettlementMorphology derive(Faction faction, Settlement settlement) {
        Objects.requireNonNull(faction, "faction");
        Objects.requireNonNull(settlement, "settlement");
        boolean capital = faction.settlements().stream()
                .max((a, b) -> {
                    int c = Integer.compare(a.population(), b.population());
                    return c != 0 ? c : Long.compare(a.id(), b.id());
                })
                .map(s -> s.id() == settlement.id())
                .orElse(false);
        return derive(settlement, capital, faction.technology());
    }

    private static SettlementMorphology derive(Settlement settlement, boolean capital, double technology) {
        SettlementGeographyProfile geo = settlement.geography();
        int tier = settlement.tier().ordinal();
        long salt = mix(settlement.id() * 0x9E3779B97F4A7C15L);

        if (geo.shipSuitable() && tier >= Settlement.Tier.VILLAGE.ordinal()) {
            return COASTAL_PORT;
        }
        if (geo.riverSuitable() && !geo.coastal() && tier >= Settlement.Tier.HAMLET.ordinal()) {
            return (salt & 1L) == 0L ? RIVER_TOWN : LINEAR_VALLEY;
        }
        if (geo.slope() >= 8.0 || geo.elevation() >= 110) {
            return HILL_TOWN;
        }
        if (capital && tier >= Settlement.Tier.CITY.ordinal()) {
            return (salt % 3L) == 0L ? RADIAL_CAPITAL : WALLED_CORE;
        }
        if (tier >= Settlement.Tier.CITY.ordinal() && technology >= 0.55) {
            return INDUSTRIAL_EDGE;
        }
        if (tier >= Settlement.Tier.TOWN.ordinal() && (salt & 3L) == 0L) {
            return PLANNED_BOULEVARD;
        }
        if (tier >= Settlement.Tier.VILLAGE.ordinal() && (salt & 2L) != 0L) {
            return MARKET_CROSS;
        }
        if (tier <= Settlement.Tier.HAMLET.ordinal()) {
            return (salt & 1L) == 0L ? ORGANIC_MEDIEVAL : LINEAR_VALLEY;
        }
        return ORGANIC_MEDIEVAL;
    }

    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
