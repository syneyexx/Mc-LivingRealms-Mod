package dev.livingrealms.sim.presentation;

import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.world.SimulationState;
import java.util.Locale;
import java.util.Objects;

/**
 * Bounded visual state for Living Realms-authored farmland. Does not rewrite biomes or destroy
 * player farms — presentation cue only.
 */
public final class SeasonalFarmPresentation {
    public enum CropLook { LUSH, NORMAL, DRY, FROSTED, FLOODED, DORMANT }

    public record FarmLook(CropLook look, String dialogueHint, double yieldHint) {
        public FarmLook {
            Objects.requireNonNull(look, "look");
            dialogueHint = dialogueHint == null ? "" : dialogueHint;
            yieldHint = Double.isFinite(yieldHint) ? Math.max(0, Math.min(1.5, yieldHint)) : 1.0;
        }
    }

    private SeasonalFarmPresentation() {}

    public static FarmLook forSettlement(SimulationState state, Settlement settlement) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(settlement, "settlement");
        int season = Math.floorMod(state.clock().day(), 360) / 90; // 0 spring … 3 winter
        double food = settlement.foodSecurity();
        // Weather pressure inferred from recent history + food security (canonical engines already applied yield).
        boolean drought = food < 0.4 && season == 1; // summer scarcity
        boolean frost = season == 3 || (season == 0 && food < 0.45);
        boolean flood = state.history().recent(12).stream()
                .anyMatch(e -> e.type().toLowerCase(Locale.ROOT).contains("flood")
                        && e.message().contains(Long.toString(settlement.id())));

        if (flood) {
            return new FarmLook(CropLook.FLOODED, "the fields are waterlogged after the flood", 0.55);
        }
        if (drought) {
            return new FarmLook(CropLook.DRY, "the soil cracks under the drought", Math.max(0.35, food));
        }
        if (frost && season == 3) {
            return new FarmLook(CropLook.FROSTED, "frost bites the crops", 0.5);
        }
        if (season == 3) {
            return new FarmLook(CropLook.DORMANT, "winter fields lie quiet", 0.6);
        }
        if (food > 0.75 && season == 1) {
            return new FarmLook(CropLook.LUSH, "the harvest looks strong", 1.15);
        }
        return new FarmLook(CropLook.NORMAL, "the fields look ordinary for the season", 1.0);
    }
}
