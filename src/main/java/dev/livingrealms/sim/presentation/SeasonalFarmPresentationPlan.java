package dev.livingrealms.sim.presentation;

import dev.livingrealms.sim.presentation.SeasonalFarmPresentation.CropLook;
import java.util.Objects;

/**
 * Headless visual opcodes for LR-authored farmland presentation.
 * Yield hints stay dialogue-only — never mint harvest into the economy.
 */
public final class SeasonalFarmPresentationPlan {
    public enum CropVisual {
        WHEAT_AGE_0,
        WHEAT_AGE_1,
        WHEAT_AGE_2,
        WHEAT_AGE_4,
        WHEAT_AGE_7,
        SNOW_COVER,
        WATER_SHEET,
        DEAD_BUSH
    }

    public record VisualPlan(CropLook look, CropVisual crop, int farmlandMoisture, String dialogueHint, double yieldHint) {
        public VisualPlan {
            Objects.requireNonNull(look, "look");
            Objects.requireNonNull(crop, "crop");
            farmlandMoisture = Math.max(0, Math.min(7, farmlandMoisture));
            dialogueHint = dialogueHint == null ? "" : dialogueHint;
            yieldHint = Double.isFinite(yieldHint) ? Math.max(0, Math.min(1.5, yieldHint)) : 1.0;
        }
    }

    private SeasonalFarmPresentationPlan() {}

    public static VisualPlan fromLook(SeasonalFarmPresentation.FarmLook farmLook) {
        Objects.requireNonNull(farmLook, "farmLook");
        CropVisual crop = switch (farmLook.look()) {
            case LUSH -> CropVisual.WHEAT_AGE_7;
            case NORMAL -> CropVisual.WHEAT_AGE_4;
            case DRY -> CropVisual.WHEAT_AGE_1;
            case FROSTED -> CropVisual.SNOW_COVER;
            case FLOODED -> CropVisual.WATER_SHEET;
            case DORMANT -> CropVisual.DEAD_BUSH;
        };
        int moisture = switch (farmLook.look()) {
            case LUSH, NORMAL -> 7;
            case FLOODED -> 7;
            case DRY, FROSTED, DORMANT -> 0;
        };
        return new VisualPlan(farmLook.look(), crop, moisture, farmLook.dialogueHint(), farmLook.yieldHint());
    }

    public static int wheatAge(CropVisual visual) {
        Objects.requireNonNull(visual, "visual");
        return switch (visual) {
            case WHEAT_AGE_0 -> 0;
            case WHEAT_AGE_1 -> 1;
            case WHEAT_AGE_2 -> 2;
            case WHEAT_AGE_4 -> 4;
            case WHEAT_AGE_7 -> 7;
            default -> -1;
        };
    }
}
