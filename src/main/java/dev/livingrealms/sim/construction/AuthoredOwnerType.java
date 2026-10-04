package dev.livingrealms.sim.construction;

/**
 * Typed Living Realms ownership class for physical blocks.
 *
 * <p>A single boolean "LR authored" is insufficient: transport must not overwrite settlement
 * buildings, industry must not overwrite roads, and historical cleanup must not remove civic shells.
 */
public enum AuthoredOwnerType {
    SETTLEMENT_STRUCTURE(0),
    SETTLEMENT_ROAD(1),
    INTERCITY_ROUTE(2),
    INDUSTRIAL_SITE(3),
    HISTORICAL_RUIN(4),
    HIDDEN_CACHE(5),
    PIRATE_HIDEOUT(6),
    INFRASTRUCTURE(7),
    WIZARD_TREES(8),
    OTHER_LR(9);

    private final int id;

    AuthoredOwnerType(int id) {
        this.id = id;
    }

    public int id() {
        return id;
    }

    public static AuthoredOwnerType byId(int id) {
        for (AuthoredOwnerType type : values()) {
            if (type.id == id) return type;
        }
        return OTHER_LR;
    }

    /** True when {@code incoming} may replace a block already owned by {@code existing}. */
    public static boolean allowsOverwrite(AuthoredOwnerType existing, AuthoredOwnerType incoming) {
        if (existing == null) return true;
        if (incoming == null) return false;
        // Same owner class may repair/rebuild its own geometry.
        if (existing == incoming) return true;
        // Wizard trees may excavate their own underground network only.
        if (existing == WIZARD_TREES && incoming == WIZARD_TREES) return true;
        return false;
    }

    public static AuthoredOwnerType forStructureRole(StructureRole role) {
        if (role == null) return OTHER_LR;
        return switch (role) {
            case ROAD -> SETTLEMENT_ROAD;
            case WIZARD_HALL, WIZARD_GROVE, WIZARD_HOME, WIZARD_TUNNEL -> WIZARD_TREES;
            case MINE, LUMBER_CAMP, FISHERY, FACTORY, WORKSHOP, AIRFIELD, DOCK -> INFRASTRUCTURE;
            default -> SETTLEMENT_STRUCTURE;
        };
    }
}
