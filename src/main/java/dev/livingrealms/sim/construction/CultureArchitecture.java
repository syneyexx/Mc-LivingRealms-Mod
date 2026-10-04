package dev.livingrealms.sim.construction;

/**
 * Culture families inspired by BuildPaste vernacular house categories
 * (medieval / fantasy / mediterranean / desert / japanese / modern / victorian) plus Nordic.
 * Wired into {@link SettlementPlanner} lot sizing and {@link StructureBlueprintFactory} massing —
 * materials still come from {@code FactionBlockPalette}.
 */
public enum CultureArchitecture {
    /** Stone base + timber frame + steep roof (BuildPaste medieval_houses). */
    MEDIEVAL_FACHWERK,
    /** Deepslate/spruce martial compounds (nordic / highland). */
    NORDIC_FORTRESS,
    /** Sandstone courtyard / adobe (BuildPaste desert_houses + mediterranean_houses). */
    DESERT_COURTYARD,
    /** Ornate fantasy / gothic manor (BuildPaste fantasy_houses). */
    FANTASY_MANOR,
    /** Clean mercantile / modern + victorian townhouse silhouettes. */
    MERCANTILE_TOWNHOUSE,
    /** Coastal prismarine-accent cottages (mediterranean coastal reading). */
    COASTAL_VILLA,
    /** Japanese-inspired timber pavilion (BuildPaste japanese_houses). */
    TIMBER_PAVILION,
    /** Scholarly cherry/quartz villa with deep eaves (victorian manor reading). */
    SCHOLAR_VILLA;

    public static CultureArchitecture fromStyleIndex(int style) {
        return switch (Math.floorMod(style, 8)) {
            case 1 -> NORDIC_FORTRESS;
            case 2 -> DESERT_COURTYARD;
            case 3 -> FANTASY_MANOR;
            case 4 -> MERCANTILE_TOWNHOUSE;
            case 5 -> COASTAL_VILLA;
            case 6 -> TIMBER_PAVILION;
            case 7 -> SCHOLAR_VILLA;
            default -> MEDIEVAL_FACHWERK;
        };
    }

    /** Prefer larger footprints so homes read as finished buildings, not huts. */
    public int minHouseWidth() {
        return switch (this) {
            case MEDIEVAL_FACHWERK, NORDIC_FORTRESS, FANTASY_MANOR -> 9;
            case DESERT_COURTYARD, COASTAL_VILLA, SCHOLAR_VILLA -> 11;
            case MERCANTILE_TOWNHOUSE -> 9;
            case TIMBER_PAVILION -> 11;
        };
    }

    public int minHouseDepth() {
        return switch (this) {
            case DESERT_COURTYARD, SCHOLAR_VILLA -> 11;
            case TIMBER_PAVILION, COASTAL_VILLA -> 9;
            default -> 9;
        };
    }

    public boolean prefersPitchedRoof() {
        return this != MERCANTILE_TOWNHOUSE && this != DESERT_COURTYARD;
    }

    public boolean prefersCourtyard() {
        return this == DESERT_COURTYARD || this == TIMBER_PAVILION || this == SCHOLAR_VILLA;
    }

    public boolean prefersOverhang() {
        return this == MEDIEVAL_FACHWERK || this == FANTASY_MANOR || this == TIMBER_PAVILION || this == SCHOLAR_VILLA;
    }
}
