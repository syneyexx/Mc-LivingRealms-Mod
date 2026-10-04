package dev.livingrealms.sim.faction;

import dev.livingrealms.sim.util.Mathx;
import java.util.Locale;
import java.util.Objects;

/**
 * Canonical geography metadata for a settlement. Used by transport mode selection, ports and
 * placement scoring. Name heuristics remain a backward-compatible fallback when world discovery
 * has not yet authored a profile.
 */
public final class SettlementGeographyProfile {
    private final boolean coastal;
    private final boolean riverAdjacent;
    private final boolean navigableWater;
    private final boolean freshwater;
    private final double harborSuitability;
    private final double elevation;
    private final double slope;
    private final double fertility;
    private final double forest;
    private final double miningPotential;
    private final String biomeId;
    private final boolean worldDiscovered;

    public SettlementGeographyProfile(
            boolean coastal,
            boolean riverAdjacent,
            boolean navigableWater,
            boolean freshwater,
            double harborSuitability,
            double elevation,
            double slope,
            double fertility,
            double forest,
            double miningPotential,
            String biomeId,
            boolean worldDiscovered
    ) {
        this.coastal = coastal;
        this.riverAdjacent = riverAdjacent;
        this.navigableWater = navigableWater;
        this.freshwater = freshwater;
        this.harborSuitability = Mathx.clamp(harborSuitability, 0, 1);
        this.elevation = elevation;
        this.slope = Math.max(0, slope);
        this.fertility = Mathx.clamp(fertility, 0, 1);
        this.forest = Mathx.clamp(forest, 0, 1);
        this.miningPotential = Mathx.clamp(miningPotential, 0, 1);
        this.biomeId = Objects.requireNonNullElse(biomeId, "");
        this.worldDiscovered = worldDiscovered;
    }

    public static SettlementGeographyProfile unknown() {
        return new SettlementGeographyProfile(false, false, false, false, 0, 64, 0, .5, .3, .2, "", false);
    }

    /** Backward-compatible bootstrap used when no Minecraft discovery sample exists yet. */
    public static SettlementGeographyProfile fromNameHeuristic(String settlementName) {
        String n = settlementName == null ? "" : settlementName.toLowerCase(Locale.ROOT);
        boolean river = containsAny(n, "ford", "bridge", "water", "river", "brook", "mere", "fen", "creek");
        boolean coast = containsAny(n, "port", "bay", "sea", "harbor", "harbour", "tide", "coast", "haven");
        boolean navigable = river || coast;
        double harbor = coast ? 0.72 : (river ? 0.35 : 0.05);
        return new SettlementGeographyProfile(coast, river, navigable, river && !coast, harbor, 64, 0, .55, .35, .25, "", false);
    }

    public boolean coastal() { return coastal; }
    public boolean riverAdjacent() { return riverAdjacent; }
    public boolean navigableWater() { return navigableWater; }
    public boolean freshwater() { return freshwater; }
    public double harborSuitability() { return harborSuitability; }
    public double elevation() { return elevation; }
    public double slope() { return slope; }
    public double fertility() { return fertility; }
    public double forest() { return forest; }
    public double miningPotential() { return miningPotential; }
    public String biomeId() { return biomeId; }
    public boolean worldDiscovered() { return worldDiscovered; }

    public boolean watery() { return navigableWater || coastal || riverAdjacent; }
    public boolean shipSuitable() { return coastal && harborSuitability >= 0.45; }
    public boolean riverSuitable() { return riverAdjacent || (navigableWater && freshwater); }

    public SettlementGeographyProfile withDiscovery(
            boolean coastal,
            boolean riverAdjacent,
            boolean navigableWater,
            boolean freshwater,
            double harborSuitability,
            double elevation,
            double slope,
            double fertility,
            double forest,
            double miningPotential,
            String biomeId
    ) {
        return new SettlementGeographyProfile(
                coastal, riverAdjacent, navigableWater, freshwater, harborSuitability,
                elevation, slope, fertility, forest, miningPotential, biomeId, true
        );
    }

    private static boolean containsAny(String n, String... tokens) {
        for (String t : tokens) if (n.contains(t)) return true;
        return false;
    }
}
