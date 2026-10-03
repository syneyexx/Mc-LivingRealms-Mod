package dev.livingrealms.sim.materialization;

/** Limits physical entity count independently from aggregate population size. */
public record MaterializationConfig(
        double physicalRadiusBlocks,
        double regionalRadiusBlocks,
        int maxAnimalsPerGroup,
        int maxAnimalsPerPlayer
) {
    public MaterializationConfig {
        if (physicalRadiusBlocks <= 0 || regionalRadiusBlocks <= physicalRadiusBlocks) throw new IllegalArgumentException("radii");
        if (maxAnimalsPerGroup <= 0 || maxAnimalsPerPlayer < 0) throw new IllegalArgumentException("caps");
    }
    public static MaterializationConfig defaults() { return new MaterializationConfig(320, 2048, 32, 180); }
}
