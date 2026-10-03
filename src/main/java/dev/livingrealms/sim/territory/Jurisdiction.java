package dev.livingrealms.sim.territory;

/** Result of resolving sovereignty at a world position. */
public record Jurisdiction(
        long primaryFactionId,
        long secondaryFactionId,
        double primaryStrength,
        double secondaryStrength,
        boolean contested
) {
    public static Jurisdiction wilderness() { return new Jurisdiction(0, 0, 0, 0, false); }
    public boolean claimed() { return primaryFactionId > 0; }
}
