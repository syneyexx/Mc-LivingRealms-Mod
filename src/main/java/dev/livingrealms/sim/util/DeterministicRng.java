package dev.livingrealms.sim.util;

/** SplitMix64-based deterministic RNG. Simulation results are reproducible for the same world seed. */
public final class DeterministicRng {
    private long state;
    public DeterministicRng(long seed) { this.state = seed; }
    public long nextLong() {
        long z = (state += 0x9E3779B97F4A7C15L);
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
    public double nextDouble() { return (nextLong() >>> 11) * 0x1.0p-53; }
    public int nextInt(int bound) {
        if (bound <= 0) throw new IllegalArgumentException("bound must be positive");
        return (int)Math.floor(nextDouble() * bound);
    }
    public boolean chance(double p) { return nextDouble() < Mathx.clamp(p, 0.0, 1.0); }
    public double between(double min, double max) { return min + (max-min)*nextDouble(); }
}
