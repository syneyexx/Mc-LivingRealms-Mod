package dev.livingrealms.sim.world;

public final class SimClock {
    public static final int TICKS_PER_DAY = 24000;
    private long gameTicks;
    public long gameTicks() { return gameTicks; }
    public long day() { return Math.floorDiv(gameTicks, TICKS_PER_DAY); }
    public int timeOfDay() { return Math.floorMod(gameTicks, TICKS_PER_DAY); }
    public void advance(long ticks) { if (ticks < 0) throw new IllegalArgumentException("ticks"); gameTicks += ticks; }
    public void restore(long ticks) { gameTicks = Math.max(0, ticks); }
}
