package dev.livingrealms.sim.world;

/** Finite strategic/world position in Minecraft's horizontal coordinate envelope. */
public record SimPosition(double x, double z) {
    public static final double MAX_ABS_COORDINATE = 30_000_000.0D;

    public SimPosition {
        if (!Double.isFinite(x) || !Double.isFinite(z)
                || Math.abs(x) > MAX_ABS_COORDINATE || Math.abs(z) > MAX_ABS_COORDINATE) {
            throw new IllegalArgumentException("position outside finite Minecraft world bounds: " + x + "," + z);
        }
    }

    public double distanceTo(SimPosition other) { return Math.hypot(x - other.x, z - other.z); }
    public SimPosition lerp(SimPosition other, double t) { return new SimPosition(x + (other.x-x)*t, z + (other.z-z)*t); }
}
