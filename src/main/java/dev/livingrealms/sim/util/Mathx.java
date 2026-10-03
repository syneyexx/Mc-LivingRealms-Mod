package dev.livingrealms.sim.util;

public final class Mathx {
    private Mathx() {}
    public static double clamp(double v, double min, double max) { return Math.max(min, Math.min(max, v)); }
    public static int clamp(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }
    public static double safeDiv(double a, double b) { return Math.abs(b) < 1e-12 ? 0.0 : a / b; }
}
