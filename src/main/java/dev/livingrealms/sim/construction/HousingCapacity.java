package dev.livingrealms.sim.construction;

import java.util.Objects;

/**
 * Density/household compression: physical houses represent many canonical residents without a 1:1
 * entity or block-house per person. Used by {@link PhysicalDevelopmentReconciler}.
 */
public final class HousingCapacity {
    private HousingCapacity() {}

    public static int representedResidents(ConstructionIntent intent) {
        Objects.requireNonNull(intent, "intent");
        if (intent.role() != StructureRole.HOUSE) return 0;
        return representedResidents(intent.width(), intent.depth());
    }

    public static int representedResidents(int width, int depth) {
        int w = Math.max(1, width), d = Math.max(1, depth);
        if (w >= 11 || d >= 11) return 48; // apartment block
        if (w >= 9 || d >= 9) return 18;   // townhouse / longhouse scale
        return 8;                          // cottage / rural house
    }

    public static int physicalHousingEstimate(Iterable<ConstructionIntent> completedHouseIntents) {
        int total = 0;
        for (ConstructionIntent intent : completedHouseIntents) total += representedResidents(intent);
        return total;
    }
}
