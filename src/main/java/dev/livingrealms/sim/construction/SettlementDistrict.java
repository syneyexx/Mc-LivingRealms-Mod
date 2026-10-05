package dev.livingrealms.sim.construction;

/**
 * Soft urban district classification derived from existing planner roles/anchors.
 * Not a second settlement system — overlays SettlementPlanner geometry.
 */
public enum SettlementDistrict {
    GOVERNMENT,
    MARKET,
    RESIDENTIAL,
    CRAFTS,
    INDUSTRIAL,
    MILITARY,
    RELIGIOUS,
    HARBOR,
    RURAL_FRINGE,
    OLD_TOWN,
    WEALTHY_QUARTER,
    WORKERS_QUARTER;

    public static SettlementDistrict forRole(StructureRole role) {
        if (role == null) return RESIDENTIAL;
        return switch (role) {
            case KEEP, TOWN_HALL, COURTHOUSE, MONUMENT, OBSERVATORY -> GOVERNMENT;
            case MARKET, PLAZA, WAREHOUSE, TAVERN -> MARKET;
            case WORKSHOP, BAKERY, BREWERY, MILL -> CRAFTS;
            case FACTORY, MINE, LUMBER_CAMP -> INDUSTRIAL;
            case BARRACKS, WALL, GATE, AIRFIELD, PRISON -> MILITARY;
            case TEMPLE -> RELIGIOUS;
            case DOCK, FISHERY -> HARBOR;
            case FARM, PASTURE, IRRIGATION, WELL, AQUEDUCT -> RURAL_FRINGE;
            case HOUSE -> RESIDENTIAL;
            case ROAD, SCHOOL, CLINIC, ORPHANAGE,
                    WIZARD_HALL, WIZARD_GROVE, WIZARD_HOME, WIZARD_TUNNEL -> OLD_TOWN;
        };
    }
}
