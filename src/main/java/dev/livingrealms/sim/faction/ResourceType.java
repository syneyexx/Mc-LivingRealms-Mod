package dev.livingrealms.sim.faction;

/**
 * Canonical stockpile commodities.
 * <p>Ordinals 0–11 are frozen for schema ≤17 compatibility. New goods append after TEXTILES.
 * {@link #FOOD} remains the UI/emergency aggregate; mills and bakeries use GRAIN/FLOUR/BREAD.
 */
public enum ResourceType {
    FOOD,
    WOOD,
    STONE,
    IRON,
    COAL,
    COPPER,
    GOLD,
    FUEL,
    AMMUNITION,
    TOOLS,
    MACHINERY,
    TEXTILES,
    // schema 18+
    GRAIN,
    FLOUR,
    BREAD,
    MEAT,
    ALE,
    WOOL;

    /** Resource count written/read by schema ≤17 stockpile blocks. */
    public static final int LEGACY_COUNT = 12;

    /** Edible goods preferred by consumption (then GRAIN as emergency ration). */
    public static boolean isEdible(ResourceType type) {
        return type == BREAD || type == MEAT || type == ALE || type == GRAIN || type == FOOD || type == FLOUR;
    }

    /** Approximate food-value units per stockpile unit (bakery/mill conservation tests). */
    public static double foodValue(ResourceType type) {
        return switch (type) {
            case BREAD -> 1.0;
            case MEAT -> 1.1;
            case ALE -> 0.55;
            case GRAIN -> 0.85;
            case FLOUR -> 0.9;
            case FOOD -> 1.0;
            default -> 0.0;
        };
    }

    /** Split legacy FOOD into grain + bread so migrated worlds do not starve on day 1. */
    public static void migrateLegacyFood(Stockpile stockpile) {
        double food = stockpile.get(FOOD);
        if (food <= 0) return;
        // Preserve total food-value: 55% GRAIN + 45% BREAD of the FOOD pool, then clear FOOD.
        double grain = food * 0.55;
        double bread = food * 0.45;
        stockpile.set(FOOD, 0);
        stockpile.add(GRAIN, grain);
        stockpile.add(BREAD, bread);
    }
}
