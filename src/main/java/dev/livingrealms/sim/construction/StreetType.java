package dev.livingrealms.sim.construction;

/**
 * Street hierarchy for settlement carriageways. Width/surface guide blueprint generation;
 * maintenanceCost feeds transport upkeep; trafficImportance ranks strategic routes.
 */
public enum StreetType {
    FOOTPATH(1, "dirt", false, false, 1, 0.02),
    ALLEY(2, "dirt", false, false, 2, 0.03),
    RESIDENTIAL_LANE(3, "gravel", true, false, 3, 0.05),
    RESIDENTIAL_STREET(5, "cobble", true, true, 5, 0.08),
    COMMERCIAL_STREET(5, "cobble", true, true, 7, 0.10),
    MARKET_STREET(7, "stone", true, true, 8, 0.12),
    ARTERIAL(7, "stone", true, true, 10, 0.14),
    BOULEVARD(9, "stone", true, true, 12, 0.18),
    REGIONAL_ROAD(7, "gravel", false, true, 11, 0.16),
    ROYAL_ROAD(9, "stone", true, true, 14, 0.22);

    private final int width;
    private final String surfaceKey;
    private final boolean sidewalk;
    private final boolean lighting;
    private final int trafficImportance;
    private final double maintenanceCost;

    StreetType(int width, String surfaceKey, boolean sidewalk, boolean lighting, int trafficImportance, double maintenanceCost) {
        this.width = width;
        this.surfaceKey = surfaceKey;
        this.sidewalk = sidewalk;
        this.lighting = lighting;
        this.trafficImportance = trafficImportance;
        this.maintenanceCost = maintenanceCost;
    }

    public int width() { return width; }
    public String surfaceKey() { return surfaceKey; }
    public boolean sidewalk() { return sidewalk; }
    public boolean lighting() { return lighting; }
    public int trafficImportance() { return trafficImportance; }
    public double maintenanceCost() { return maintenanceCost; }

    /** Map planner road width to the closest hierarchy tier. */
    public static StreetType forWidth(int width, boolean capital, boolean marketDistrict, boolean regional) {
        int w = Math.max(1, width);
        if (regional) return w >= 9 ? ROYAL_ROAD : REGIONAL_ROAD;
        if (capital && w >= 9) return BOULEVARD;
        if (marketDistrict && w >= 7) return MARKET_STREET;
        if (w >= 9) return BOULEVARD;
        if (w >= 7) return ARTERIAL;
        if (w >= 5) return marketDistrict ? COMMERCIAL_STREET : RESIDENTIAL_STREET;
        if (w >= 3) return RESIDENTIAL_LANE;
        if (w >= 2) return ALLEY;
        return FOOTPATH;
    }
}
