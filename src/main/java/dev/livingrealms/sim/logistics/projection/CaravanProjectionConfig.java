package dev.livingrealms.sim.logistics.projection;

public record CaravanProjectionConfig(double physicalRadiusBlocks, int maxPhysicalCaravans) {
    public CaravanProjectionConfig {
        if (!Double.isFinite(physicalRadiusBlocks) || physicalRadiusBlocks <= 0) throw new IllegalArgumentException("physicalRadiusBlocks");
        if (maxPhysicalCaravans < 0) throw new IllegalArgumentException("maxPhysicalCaravans");
    }

    public static CaravanProjectionConfig defaults() { return new CaravanProjectionConfig(384.0, 24); }
}
