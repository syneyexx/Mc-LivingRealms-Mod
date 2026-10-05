package dev.livingrealms.sim.cartography;

import java.util.Locale;
import java.util.Objects;

/**
 * Pure terrain-sample resolution for strategic maps. Client caches supply ACTUAL samples;
 * ecology regions supply REGIONAL_ESTIMATE; everything else is UNKNOWN parchment — never sine relief.
 */
public final class TerrainMapSample {
    public record ActualSample(int color, int height, boolean water) {}

    public record Resolved(TerrainKnowledge knowledge, int color, String legendHint) {
        public Resolved {
            knowledge = Objects.requireNonNull(knowledge, "knowledge");
            legendHint = legendHint == null ? "" : legendHint;
        }
    }

    /** Parchment / fog fill — clearly not topography. */
    public static final int UNKNOWN_PARCHMENT = 0xFFC4B59A;
    public static final int UNKNOWN_GRID = 0xFFB7A888;

    private TerrainMapSample() {}

    public static Resolved resolve(ActualSample actual, String nearestBiome, boolean hasRegionalHint) {
        if (actual != null) {
            return new Resolved(TerrainKnowledge.ACTUAL, actual.color(), "actual terrain");
        }
        if (hasRegionalHint && nearestBiome != null && !nearestBiome.isBlank()) {
            return new Resolved(TerrainKnowledge.REGIONAL_ESTIMATE, regionalTint(nearestBiome), "regional estimate");
        }
        return new Resolved(TerrainKnowledge.UNKNOWN, UNKNOWN_PARCHMENT, "unknown");
    }

    /** Muted broad biome tint for regional estimates — no elevation contours. */
    public static int regionalTint(String biome) {
        String b = biome == null ? "" : biome.toLowerCase(Locale.ROOT);
        int base;
        if (b.contains("ocean") || b.contains("river") || b.contains("aquatic") || b.contains("beach")) base = 0xFF3A6A7A;
        else if (b.contains("desert") || b.contains("arid") || b.contains("badland")) base = 0xFF8A7350;
        else if (b.contains("snow") || b.contains("ice") || b.contains("polar") || b.contains("frozen")) base = 0xFF8FA4B0;
        else if (b.contains("forest") || b.contains("jungle") || b.contains("wood") || b.contains("taiga")) base = 0xFF456650;
        else if (b.contains("swamp") || b.contains("wet") || b.contains("mangrove")) base = 0xFF4A5C48;
        else if (b.contains("mountain") || b.contains("alpine") || b.contains("peak")) base = 0xFF6A6E72;
        else base = 0xFF5A6B55;
        // Desaturate / mute so estimates never read as surveyed topography.
        return mute(base, 0.72);
    }

    public static int unknownFill(int tileX, int tileZ) {
        // Soft parchment hatch — decorative chart texture, never elevation.
        boolean hatch = ((tileX >> 2) + (tileZ >> 2) & 1) == 0;
        return hatch ? UNKNOWN_PARCHMENT : UNKNOWN_GRID;
    }

    private static int mute(int argb, double factor) {
        int r = (int) (((argb >> 16) & 255) * factor);
        int g = (int) (((argb >> 8) & 255) * factor);
        int b = (int) ((argb & 255) * factor);
        return 0xFF000000 | (Math.min(255, r) << 16) | (Math.min(255, g) << 8) | Math.min(255, b);
    }
}
