package dev.livingrealms.sim.construction;

import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.world.SimPosition;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Deterministic settlement planner. Street morphology is derived from geography, tier and capital
 * status via {@link SettlementMorphology}. Connectivity remains walkable and street-facing;
 * variation comes from spines, rings, radials and organic offsets rather than one universal grid.
 */
public final class SettlementPlanner {
    private SettlementPlanner() {}

    public static List<ConstructionIntent> plan(Faction faction, Settlement settlement) {
        Objects.requireNonNull(faction, "faction");
        Objects.requireNonNull(settlement, "settlement");
        SettlementMorphology morph = SettlementMorphology.derive(faction, settlement);
        List<ConstructionIntent> out = new ArrayList<>();
        int tier = settlement.tier().ordinal();
        int baseRotation = Math.floorMod((int) mix(settlement.id() ^ 0x4F1BBCDCBFA54001L), 2);

        boolean capital = faction.settlements().stream()
                .max(Comparator.comparingInt(Settlement::population).thenComparingLong(Settlement::id))
                .map(s -> s.id() == settlement.id()).orElse(false);
        int keepW = capital && tier >= Settlement.Tier.CITY.ordinal() ? 31 : capital ? 23 : tier >= Settlement.Tier.TOWN.ordinal() ? 19 : 15;
        int keepD = capital && tier >= Settlement.Tier.CITY.ordinal() ? 27 : capital ? 21 : tier >= Settlement.Tier.TOWN.ordinal() ? 17 : 15;
        SimPosition keep = civicPoint(settlement, morph, StructureRole.KEEP, baseRotation);
        addAt(out, faction, settlement, StructureRole.KEEP, 0, keep, keepW, keepD, baseRotation, capital ? 190 : 120);

        addRoadNetwork(out, faction, settlement, morph, baseRotation);
        addHousing(out, faction, settlement, morph, baseRotation);
        addFarms(out, faction, settlement, morph, baseRotation);
        addPastures(out, faction, settlement, morph, baseRotation);

        if (tier >= Settlement.Tier.HAMLET.ordinal()) addCivic(out, faction, settlement, morph, baseRotation, StructureRole.WELL, 0, 5, 5, 116);
        if (tier >= Settlement.Tier.VILLAGE.ordinal()) {
            addCivic(out, faction, settlement, morph, baseRotation, StructureRole.IRRIGATION, 0, 7, 31, 74);
            addCivic(out, faction, settlement, morph, baseRotation, StructureRole.MILL, 0, 11, 11, 93);
            addCivic(out, faction, settlement, morph, baseRotation, StructureRole.MARKET, 0, 15, 13, 122);
            addCivic(out, faction, settlement, morph, baseRotation, StructureRole.PLAZA, 0, 13, 13, 118);
            addCivic(out, faction, settlement, morph, baseRotation, StructureRole.WAREHOUSE, 0, 13, 11, 91);
            addCivic(out, faction, settlement, morph, baseRotation, StructureRole.TAVERN, 0, 13, 11, 94);
            addCivic(out, faction, settlement, morph, baseRotation, StructureRole.TEMPLE, 0, 13, 15, 88);
            addCivic(out, faction, settlement, morph, baseRotation, StructureRole.BAKERY, 0, 11, 9, 90);
        }
        if (tier >= Settlement.Tier.TOWN.ordinal()) {
            addCivic(out, faction, settlement, morph, baseRotation, StructureRole.BARRACKS, 0, 15, 11, 104);
            addCivic(out, faction, settlement, morph, baseRotation, StructureRole.WORKSHOP, 0, 13, 11, 92);
            addCivic(out, faction, settlement, morph, baseRotation, StructureRole.BREWERY, 0, 13, 11, 88);
            addCivic(out, faction, settlement, morph, baseRotation, StructureRole.CLINIC, 0, 13, 11, 90);
            addCivic(out, faction, settlement, morph, baseRotation, StructureRole.SCHOOL, 0, 15, 11, 86);
            addCivic(out, faction, settlement, morph, baseRotation, StructureRole.COURTHOUSE, 0, 15, 13, 99);
            addCivic(out, faction, settlement, morph, baseRotation, StructureRole.PRISON, 0, 13, 13, 96);
        }
        if (tier >= Settlement.Tier.CITY.ordinal()) {
            if (faction.technology() >= .55) addCivic(out, faction, settlement, morph, baseRotation, StructureRole.AQUEDUCT, 0, 7, 41, 79);
            addCivic(out, faction, settlement, morph, baseRotation, StructureRole.ORPHANAGE, 0, 15, 13, 79);
            addCivic(out, faction, settlement, morph, baseRotation, StructureRole.MONUMENT, 0, 9, 9, 76);
            if (faction.technology() >= .55) addCivic(out, faction, settlement, morph, baseRotation, StructureRole.OBSERVATORY, 0, 15, 15, 72);
            addCityWalls(out, faction, settlement, morph, baseRotation);
        }
        if (tier >= Settlement.Tier.TOWN.ordinal() && faction.technology() >= .35) {
            addCivic(out, faction, settlement, morph, baseRotation, StructureRole.FACTORY, 0, 19, 15, 78);
        }
        if (tier >= Settlement.Tier.VILLAGE.ordinal() && settlement.geography().shipSuitable()) {
            addCivic(out, faction, settlement, morph, baseRotation, StructureRole.DOCK, 0, 17, 11, 118);
        }
        if (tier >= Settlement.Tier.CITY.ordinal() && faction.technology() >= .75) {
            SimPosition edge = local(settlement, baseRotation, 132, -96);
            addAt(out, faction, settlement, StructureRole.AIRFIELD, 0, edge, 25, 70, baseRotation, 64);
        }

        out.replaceAll(intent -> new ConstructionIntent(
                intent.key(), intent.factionId(), intent.settlementId(), intent.role(), intent.center(),
                intent.width(), intent.depth(), intent.rotationQuarterTurns(),
                adjustPriority(settlement.developmentPriority(), intent.role(), intent.priority())));
        out.sort(Comparator.comparingInt(ConstructionIntent::priority).reversed().thenComparing(ConstructionIntent::key));
        return List.copyOf(out);
    }

    public static List<ConstructionIntent> pending(Faction faction, Settlement settlement) {
        return plan(faction, settlement).stream().filter(i -> !settlement.isConstructionCompleted(i.key())).toList();
    }

    public static String layoutArchetype(Settlement settlement) {
        Objects.requireNonNull(settlement, "settlement");
        return SettlementMorphology.derive(settlement).wireName();
    }

    public static String layoutArchetype(Faction faction, Settlement settlement) {
        return SettlementMorphology.derive(faction, settlement).wireName();
    }

    private static void addRoadNetwork(List<ConstructionIntent> out, Faction faction, Settlement settlement,
                                       SettlementMorphology morph, int baseRotation) {
        int tier = settlement.tier().ordinal();
        int spacing = spacing(morph);
        int rings = switch (settlement.tier()) {
            case CAMP -> 0; case HAMLET -> 1; case VILLAGE -> 1; case TOWN -> 2; case CITY -> 3; case METROPOLIS -> 4;
        };
        int halfLength = Math.max(42, spacing * (rings + 1));
        int index = 0;
        switch (morph) {
            case COASTAL_PORT -> {
                // Harbor spine toward water (+Z) plus a coastal boulevard and short pier approaches.
                addRoad(out, faction, settlement, index++, local(settlement, baseRotation, 0, 0), 9, halfLength * 2 + 9, baseRotation + 1, 136);
                addRoad(out, faction, settlement, index++, local(settlement, baseRotation, 0, halfLength / 3), 9, halfLength + 18, baseRotation, 128);
                addRoad(out, faction, settlement, index++, local(settlement, baseRotation, 0, -halfLength / 4), 7, halfLength, baseRotation, 118);
                for (int i = 1; i <= Math.max(1, rings); i++) {
                    int off = spacing * i / 2;
                    addRoad(out, faction, settlement, index++, local(settlement, baseRotation, off, halfLength / 5), 5, halfLength, baseRotation + 1, 104);
                    addRoad(out, faction, settlement, index++, local(settlement, baseRotation, -off, halfLength / 5), 5, halfLength, baseRotation + 1, 104);
                }
                addRoad(out, faction, settlement, index++, local(settlement, baseRotation, 0, halfLength / 2 + 12), 7, 28, baseRotation + 1, 120);
            }
            case RIVER_TOWN, LINEAR_VALLEY -> {
                // Long valley/river spine with short perpendicular streets.
                addRoad(out, faction, settlement, index++, local(settlement, baseRotation, 0, 0), 9, halfLength * 2 + 18, baseRotation, 134);
                for (int i = -rings; i <= rings; i++) {
                    if (i == 0) continue;
                    int along = i * Math.max(28, spacing / 2);
                    addRoad(out, faction, settlement, index++, local(settlement, baseRotation, along, 0), 5, spacing + 12, baseRotation + 1, 108);
                }
                if (tier >= Settlement.Tier.VILLAGE.ordinal()) {
                    addRoad(out, faction, settlement, index++, local(settlement, baseRotation, 0, spacing / 2), 5, halfLength, baseRotation, 100);
                    addRoad(out, faction, settlement, index++, local(settlement, baseRotation, 0, -spacing / 2), 5, halfLength, baseRotation, 100);
                }
            }
            case HILL_TOWN -> {
                // Contour rings + limited radials (switchback-friendly orthogonal approximation).
                addRoad(out, faction, settlement, index++, local(settlement, baseRotation, 0, 0), 7, spacing + 18, baseRotation, 130);
                addRoad(out, faction, settlement, index++, local(settlement, baseRotation, 0, 0), 7, spacing + 18, baseRotation + 1, 130);
                for (int ring = 1; ring <= rings; ring++) {
                    int offset = Math.max(24, spacing - 6) * ring;
                    addRoad(out, faction, settlement, index++, local(settlement, baseRotation, offset, 0), 5, offset * 2 + 9, baseRotation + 1, 110);
                    addRoad(out, faction, settlement, index++, local(settlement, baseRotation, -offset, 0), 5, offset * 2 + 9, baseRotation + 1, 110);
                    addRoad(out, faction, settlement, index++, local(settlement, baseRotation, 0, offset), 5, offset * 2 + 9, baseRotation, 110);
                    addRoad(out, faction, settlement, index++, local(settlement, baseRotation, 0, -offset), 5, offset * 2 + 9, baseRotation, 110);
                }
                // One diagonal-ish connector via offset stub roads for hillside access.
                if (rings >= 1) {
                    int mid = Math.max(20, spacing / 2);
                    addRoad(out, faction, settlement, index++, local(settlement, baseRotation, mid, mid / 2), 5, mid + 10, baseRotation, 96);
                    addRoad(out, faction, settlement, index++, local(settlement, baseRotation, -mid / 2, mid), 5, mid + 10, baseRotation + 1, 96);
                }
            }
            case RADIAL_CAPITAL, WALLED_CORE -> {
                addRoad(out, faction, settlement, index++, local(settlement, baseRotation, 0, 0), 11, halfLength * 2 + 11, baseRotation, 140);
                addRoad(out, faction, settlement, index++, local(settlement, baseRotation, 0, 0), 11, halfLength * 2 + 11, baseRotation + 1, 140);
                // Diagonal boulevards approximated as offset arterials from the keep.
                int spoke = Math.max(36, spacing);
                addRoad(out, faction, settlement, index++, local(settlement, baseRotation, spoke / 2, spoke / 2), 7, halfLength, baseRotation, 124);
                addRoad(out, faction, settlement, index++, local(settlement, baseRotation, -spoke / 2, spoke / 2), 7, halfLength, baseRotation + 1, 124);
                addRoad(out, faction, settlement, index++, local(settlement, baseRotation, spoke / 2, -spoke / 2), 7, halfLength, baseRotation + 1, 124);
                addRoad(out, faction, settlement, index++, local(settlement, baseRotation, -spoke / 2, -spoke / 2), 7, halfLength, baseRotation, 124);
                for (int ring = 1; ring <= rings; ring++) {
                    int offset = spacing * ring;
                    addRoad(out, faction, settlement, index++, local(settlement, baseRotation, offset, 0), 7, halfLength * 2 + 7, baseRotation, 112);
                    addRoad(out, faction, settlement, index++, local(settlement, baseRotation, -offset, 0), 7, halfLength * 2 + 7, baseRotation, 112);
                    addRoad(out, faction, settlement, index++, local(settlement, baseRotation, 0, offset), 7, halfLength * 2 + 7, baseRotation + 1, 112);
                    addRoad(out, faction, settlement, index++, local(settlement, baseRotation, 0, -offset), 7, halfLength * 2 + 7, baseRotation + 1, 112);
                }
                // Residential lanes between arterials keep lot access short in capital cores.
                if (tier >= Settlement.Tier.TOWN.ordinal()) {
                    for (int ring = 0; ring < rings; ring++) {
                        int offset = spacing * ring + spacing / 2;
                        addRoad(out, faction, settlement, index++, local(settlement, baseRotation, offset, 0), 5, halfLength, baseRotation, 98);
                        addRoad(out, faction, settlement, index++, local(settlement, baseRotation, 0, offset), 5, halfLength, baseRotation + 1, 98);
                    }
                }
            }
            case INDUSTRIAL_EDGE -> {
                index = addOrthogonalCross(out, faction, settlement, index, halfLength, spacing, rings, baseRotation, tier, true);
                // Factory approach boulevard on the industrial edge.
                addRoad(out, faction, settlement, index, local(settlement, baseRotation, spacing + 20, spacing / 2), 9, halfLength, baseRotation, 120);
            }
            case PLANNED_BOULEVARD -> {
                addRoad(out, faction, settlement, index++, local(settlement, baseRotation, 0, 0), 13, halfLength * 2 + 13, baseRotation, 138);
                addRoad(out, faction, settlement, index++, local(settlement, baseRotation, 0, 0), 9, halfLength * 2 + 9, baseRotation + 1, 130);
                for (int ring = 1; ring <= rings; ring++) {
                    int offset = spacing * ring;
                    addRoad(out, faction, settlement, index++, local(settlement, baseRotation, 0, offset), 7, halfLength * 2 + 7, baseRotation, 114);
                    addRoad(out, faction, settlement, index++, local(settlement, baseRotation, 0, -offset), 7, halfLength * 2 + 7, baseRotation, 114);
                    addRoad(out, faction, settlement, index++, local(settlement, baseRotation, offset, 0), 5, halfLength, baseRotation + 1, 104);
                    addRoad(out, faction, settlement, index++, local(settlement, baseRotation, -offset, 0), 5, halfLength, baseRotation + 1, 104);
                }
            }
            case MARKET_CROSS -> {
                addRoad(out, faction, settlement, index++, local(settlement, baseRotation, 0, 0), 11, halfLength * 2 + 11, baseRotation, 136);
                addRoad(out, faction, settlement, index++, local(settlement, baseRotation, 0, 0), 11, halfLength * 2 + 11, baseRotation + 1, 136);
                // Market plaza ring stubs.
                int plaza = Math.max(18, spacing / 3);
                addRoad(out, faction, settlement, index++, local(settlement, baseRotation, plaza, plaza), 7, plaza * 2, baseRotation, 122);
                addRoad(out, faction, settlement, index++, local(settlement, baseRotation, -plaza, plaza), 7, plaza * 2, baseRotation + 1, 122);
                for (int ring = 1; ring <= rings; ring++) {
                    int offset = spacing * ring;
                    addRoad(out, faction, settlement, index++, local(settlement, baseRotation, offset, 0), 7, halfLength * 2 + 7, baseRotation, 110);
                    addRoad(out, faction, settlement, index++, local(settlement, baseRotation, -offset, 0), 7, halfLength * 2 + 7, baseRotation, 110);
                    addRoad(out, faction, settlement, index++, local(settlement, baseRotation, 0, offset), 7, halfLength * 2 + 7, baseRotation + 1, 110);
                    addRoad(out, faction, settlement, index++, local(settlement, baseRotation, 0, -offset), 7, halfLength * 2 + 7, baseRotation + 1, 110);
                }
                if (tier >= Settlement.Tier.VILLAGE.ordinal()) {
                    for (int ring = 0; ring < rings; ring++) {
                        int offset = spacing * ring + spacing / 2;
                        addRoad(out, faction, settlement, index++, local(settlement, baseRotation, offset, 0), 5, halfLength, baseRotation, 98);
                        addRoad(out, faction, settlement, index++, local(settlement, baseRotation, 0, offset), 5, halfLength, baseRotation + 1, 98);
                    }
                }
            }
            case ORGANIC_MEDIEVAL -> {
                // Deterministic irregular offsets — still axis-aligned segments, not free-form diagonals through houses.
                addRoad(out, faction, settlement, index++, local(settlement, baseRotation, organicShift(settlement, 1), organicShift(settlement, 2)), 9, halfLength * 2 + 9, baseRotation, 132);
                addRoad(out, faction, settlement, index++, local(settlement, baseRotation, organicShift(settlement, 3), organicShift(settlement, 4)), 7, halfLength * 2 + 7, baseRotation + 1, 128);
                for (int ring = 1; ring <= rings; ring++) {
                    int offset = spacing * ring;
                    int jx = organicShift(settlement, 10 + ring);
                    int jz = organicShift(settlement, 20 + ring);
                    addRoad(out, faction, settlement, index++, local(settlement, baseRotation, offset + jx, jz), 5, halfLength * 2 + 5, baseRotation, 108);
                    addRoad(out, faction, settlement, index++, local(settlement, baseRotation, -offset + jx / 2, -jz), 5, halfLength * 2 + 5, baseRotation, 108);
                    addRoad(out, faction, settlement, index++, local(settlement, baseRotation, jx, offset + jz), 5, halfLength * 2 + 5, baseRotation + 1, 108);
                    addRoad(out, faction, settlement, index++, local(settlement, baseRotation, -jx, -offset + jz / 2), 5, halfLength * 2 + 5, baseRotation + 1, 108);
                }
                if (tier >= Settlement.Tier.VILLAGE.ordinal()) {
                    int offset = Math.max(20, spacing / 2);
                    addRoad(out, faction, settlement, index++, local(settlement, baseRotation, offset + organicShift(settlement, 7), offset), 5, spacing + 9, baseRotation, 101);
                    addRoad(out, faction, settlement, index, local(settlement, baseRotation, -offset, offset + organicShift(settlement, 8)), 5, spacing + 9, baseRotation + 1, 101);
                }
            }
        }
    }

    private static int addOrthogonalCross(List<ConstructionIntent> out, Faction faction, Settlement settlement,
                                          int index, int halfLength, int spacing, int rings, int baseRotation, int tier, boolean sideStreets) {
        addRoad(out, faction, settlement, index++, local(settlement, baseRotation, 0, 0), 9, halfLength * 2 + 9, baseRotation, 132);
        addRoad(out, faction, settlement, index++, local(settlement, baseRotation, 0, 0), 9, halfLength * 2 + 9, baseRotation + 1, 132);
        for (int ring = 1; ring <= rings; ring++) {
            int offset = spacing * ring;
            addRoad(out, faction, settlement, index++, local(settlement, baseRotation, offset, 0), 9, halfLength * 2 + 9, baseRotation, 112);
            addRoad(out, faction, settlement, index++, local(settlement, baseRotation, -offset, 0), 9, halfLength * 2 + 9, baseRotation, 112);
            addRoad(out, faction, settlement, index++, local(settlement, baseRotation, 0, offset), 9, halfLength * 2 + 9, baseRotation + 1, 112);
            addRoad(out, faction, settlement, index++, local(settlement, baseRotation, 0, -offset), 9, halfLength * 2 + 9, baseRotation + 1, 112);
        }
        if (sideStreets && tier >= Settlement.Tier.VILLAGE.ordinal()) {
            for (int ring = 0; ring < rings; ring++) {
                int offset = spacing * ring + spacing / 2;
                addRoad(out, faction, settlement, index++, local(settlement, baseRotation, offset, 0), 5, halfLength * 2 + 5, baseRotation, 98);
                addRoad(out, faction, settlement, index++, local(settlement, baseRotation, -offset, 0), 5, halfLength * 2 + 5, baseRotation, 98);
                addRoad(out, faction, settlement, index++, local(settlement, baseRotation, 0, offset), 5, halfLength * 2 + 5, baseRotation + 1, 98);
                addRoad(out, faction, settlement, index++, local(settlement, baseRotation, 0, -offset), 5, halfLength * 2 + 5, baseRotation + 1, 98);
            }
        }
        return index;
    }

    private static void addHousing(List<ConstructionIntent> out, Faction faction, Settlement settlement,
                                   SettlementMorphology morph, int baseRotation) {
        int represented = Math.max(settlement.population(), settlement.housing());
        int softCap = switch (settlement.tier()) {
            case CAMP -> 24; case HAMLET -> 48; case VILLAGE -> 96; case TOWN -> 220; case CITY -> 480; case METROPOLIS -> 900;
        };
        int houses = Math.min(softCap, Math.max(5, (int) Math.ceil(represented / 22.0)));
        int spacing = spacing(morph);
        int lotStep = morph == SettlementMorphology.HILL_TOWN ? 12 : morph == SettlementMorphology.ORGANIC_MEDIEVAL ? 13 : 14;
        int emitted = 0, scan = 0, limit = houses * 22 + 400;
        while (emitted < houses && scan < limit) {
            int[] cell = spiral(scan++);
            int lx = cell[0] * lotStep + (morph == SettlementMorphology.ORGANIC_MEDIEVAL ? organicShift(settlement, scan) / 2 : 0);
            int lz = cell[1] * lotStep + (morph == SettlementMorphology.ORGANIC_MEDIEVAL ? organicShift(settlement, scan + 3) / 2 : 0);
            if (Math.abs(lx) < 12 && Math.abs(lz) < 12) continue;
            // Morphology-specific envelope constraints.
            if (morph == SettlementMorphology.COASTAL_PORT && lz < -spacing) continue; // prefer shoreward growth
            if (morph == SettlementMorphology.LINEAR_VALLEY || morph == SettlementMorphology.RIVER_TOWN) {
                if (Math.abs(lz) > spacing + 24) continue; // keep ribbon form
            }
            if (morph == SettlementMorphology.INDUSTRIAL_EDGE && lx > spacing && Math.abs(lz) < spacing / 2) continue; // leave factory edge clear
            int streetStep = settlement.tier().ordinal() >= Settlement.Tier.VILLAGE.ordinal() ? Math.max(18, spacing / 2) : spacing;
            if (distanceToStreet(lx, streetStep) < 5 || distanceToStreet(lz, streetStep) < 5) continue;
            int maxRadius = switch (settlement.tier()) {
                case CAMP -> 40; case HAMLET -> 64; case VILLAGE -> 96; case TOWN -> 148; case CITY -> 220; case METROPOLIS -> 320;
            };
            maxRadius += Math.min(120, (int) Math.sqrt(Math.max(0, represented)) / 2);
            if (morph == SettlementMorphology.HILL_TOWN) maxRadius = (int) (maxRadius * 0.85);
            if (Math.abs(lx) > maxRadius || Math.abs(lz) > maxRadius) continue;
            SimPosition center = local(settlement, baseRotation, lx, lz);
            int variant = Math.floorMod((int) mix(settlement.id() ^ (long) emitted * 0x9E3779B97F4A7C15L), 7);
            int w, d;
            boolean pressure = settlement.housingShortage() > 40 || settlement.population() > settlement.housing();
            if (settlement.tier().ordinal() >= Settlement.Tier.CITY.ordinal() && (emitted % 5 == 0 || (pressure && emitted % 3 == 0))) {
                w = 13; d = 11;
            } else if (settlement.tier().ordinal() >= Settlement.Tier.TOWN.ordinal() && (emitted % 6 == 0 || (pressure && emitted % 4 == 0))) {
                w = 11; d = 9;
            } else if (settlement.tier() == Settlement.Tier.METROPOLIS && emitted % 2 == 0) {
                w = 13; d = 11;
            } else {
                w = switch (variant) { case 0 -> 7; case 1, 4 -> 9; default -> 7; };
                d = switch (variant) { case 2 -> 9; case 5 -> 7; default -> 9; };
            }
            int face = houseFacing(lx, lz, spacing, baseRotation);
            addAt(out, faction, settlement, StructureRole.HOUSE, emitted, center, w, d, face, 88);
            emitted++;
        }
    }

    private static void addFarms(List<ConstructionIntent> out, Faction faction, Settlement settlement,
                                 SettlementMorphology morph, int baseRotation) {
        int farms = Math.min(72, Math.max(2, (int) Math.ceil(settlement.population() / 160.0)));
        int urbanRadius = switch (settlement.tier()) {
            case CAMP -> 48; case HAMLET -> 64; case VILLAGE -> 88; case TOWN -> 132; case CITY -> 188; case METROPOLIS -> 244;
        };
        for (int i = 0; i < farms; i++) {
            int side = i & 3, band = i / 4;
            double along = (band - (farms / 8.0)) * 32.0;
            double edge = urbanRadius + 34 + (band % 2) * 18;
            // Keep farms off the harbor/dock face for coastal towns.
            if (morph == SettlementMorphology.COASTAL_PORT && side == 2) side = 0;
            double lx = switch (side) { case 0 -> edge; case 1 -> -edge; default -> along; };
            double lz = switch (side) { case 2 -> edge; case 3 -> -edge; default -> along; };
            int size = 11 + 2 * Math.floorMod(i + (int) settlement.id(), 3);
            addAt(out, faction, settlement, StructureRole.FARM, i, local(settlement, baseRotation, lx, lz), size, size, baseRotation, 62);
        }
    }

    private static void addPastures(List<ConstructionIntent> out, Faction faction, Settlement settlement,
                                    SettlementMorphology morph, int baseRotation) {
        int pastures = Math.min(24, Math.max(1, (int) Math.ceil(settlement.population() / 280.0)));
        int urbanRadius = switch (settlement.tier()) {
            case CAMP -> 48; case HAMLET -> 64; case VILLAGE -> 88; case TOWN -> 132; case CITY -> 188; case METROPOLIS -> 244;
        };
        for (int i = 0; i < pastures; i++) {
            int side = (i + 1) & 3;
            double along = (i - (pastures / 4.0)) * 28.0;
            double edge = urbanRadius + 58 + (i % 2) * 14;
            if (morph == SettlementMorphology.COASTAL_PORT && side == 2) side = 1;
            double lx = switch (side) { case 0 -> edge; case 1 -> -edge; default -> along; };
            double lz = switch (side) { case 2 -> edge; case 3 -> -edge; default -> along; };
            addAt(out, faction, settlement, StructureRole.PASTURE, i, local(settlement, baseRotation, lx, lz), 13, 13, baseRotation, 58);
        }
    }

    private static void addCityWalls(List<ConstructionIntent> out, Faction faction, Settlement settlement,
                                     SettlementMorphology morph, int baseRotation) {
        int radius = settlement.tier() == Settlement.Tier.METROPOLIS ? 238 : 182;
        if (morph == SettlementMorphology.HILL_TOWN) radius = (int) (radius * 0.82);
        if (morph == SettlementMorphology.WALLED_CORE) radius = (int) (radius * 0.9);
        int segment = 34, index = 0;
        for (int x = -radius; x <= radius; x += segment) {
            addAt(out, faction, settlement, StructureRole.WALL, index++, local(settlement, baseRotation, x, -radius), 5, segment + 4, baseRotation + 1, 108);
            addAt(out, faction, settlement, StructureRole.WALL, index++, local(settlement, baseRotation, x, radius), 5, segment + 4, baseRotation + 1, 108);
        }
        for (int z = -radius + segment; z <= radius - segment; z += segment) {
            addAt(out, faction, settlement, StructureRole.WALL, index++, local(settlement, baseRotation, -radius, z), 5, segment + 4, baseRotation, 108);
            addAt(out, faction, settlement, StructureRole.WALL, index++, local(settlement, baseRotation, radius, z), 5, segment + 4, baseRotation, 108);
        }
        addAt(out, faction, settlement, StructureRole.GATE, 0, local(settlement, baseRotation, 0, -radius), 11, 7, baseRotation, 150);
        addAt(out, faction, settlement, StructureRole.GATE, 1, local(settlement, baseRotation, 0, radius), 11, 7, baseRotation + 2, 150);
        addAt(out, faction, settlement, StructureRole.GATE, 2, local(settlement, baseRotation, -radius, 0), 11, 7, baseRotation + 1, 150);
        addAt(out, faction, settlement, StructureRole.GATE, 3, local(settlement, baseRotation, radius, 0), 11, 7, baseRotation + 3, 150);
    }

    private static void addCivic(List<ConstructionIntent> out, Faction faction, Settlement settlement,
                                 SettlementMorphology morph, int baseRotation, StructureRole role, int index, int w, int d, int priority) {
        SimPosition center = civicPoint(settlement, morph, role, baseRotation);
        addAt(out, faction, settlement, role, index, center, w, d, baseRotation + orientationFor(role), priority);
    }

    private static SimPosition civicPoint(Settlement settlement, SettlementMorphology morph, StructureRole role, int baseRotation) {
        double[] p = switch (role) {
            case KEEP -> new double[]{-18, -18};
            case MARKET -> new double[]{18, 18};
            case PLAZA -> new double[]{8, 8};
            case WAREHOUSE -> new double[]{-30, 28};
            case BARRACKS -> new double[]{30, -30};
            case WORKSHOP -> new double[]{54, 18};
            case FACTORY -> new double[]{86, 54};
            case WELL -> new double[]{0, 18};
            case IRRIGATION -> new double[]{92, 44};
            case AQUEDUCT -> new double[]{-126, 32};
            case TAVERN -> new double[]{30, 28};
            case TEMPLE -> new double[]{-32, -30};
            case CLINIC -> new double[]{54, -18};
            case SCHOOL -> new double[]{-54, 18};
            case COURTHOUSE -> new double[]{-18, 54};
            case PRISON -> new double[]{54, 54};
            case ORPHANAGE -> new double[]{-54, 54};
            case MONUMENT -> new double[]{18, 0};
            case OBSERVATORY -> new double[]{-96, -78};
            case MILL -> new double[]{72, -28};
            case BAKERY -> new double[]{42, 42};
            case BREWERY -> new double[]{-42, 42};
            case DOCK -> new double[]{0, 72};
            default -> new double[]{0, 0};
        };
        p = switch (morph) {
            case COASTAL_PORT -> role == StructureRole.DOCK ? new double[]{0, 88}
                    : role == StructureRole.WAREHOUSE ? new double[]{-24, 56}
                    : role == StructureRole.MARKET || role == StructureRole.PLAZA ? new double[]{12, 40}
                    : new double[]{p[0] * 0.85, p[1] * 0.7 + 18};
            case RIVER_TOWN, LINEAR_VALLEY -> new double[]{p[0] * 1.15, p[1] * 0.55};
            case HILL_TOWN -> role == StructureRole.KEEP ? new double[]{0, 0} : new double[]{p[0] * 0.75, p[1] * 0.75};
            case RADIAL_CAPITAL, WALLED_CORE -> new double[]{p[0] * 0.9, p[1] * 0.9};
            case INDUSTRIAL_EDGE -> role == StructureRole.FACTORY || role == StructureRole.WORKSHOP
                    ? new double[]{Math.abs(p[0]) + 40, p[1]} : p;
            case PLANNED_BOULEVARD -> new double[]{p[0] * 0.7, p[1] * 1.1};
            case MARKET_CROSS -> role == StructureRole.MARKET || role == StructureRole.PLAZA ? new double[]{0, 22} : p;
            case ORGANIC_MEDIEVAL -> new double[]{p[0] + organicShift(settlement, role.ordinal()), p[1] + organicShift(settlement, role.ordinal() + 11)};
        };
        return local(settlement, baseRotation, p[0], p[1]);
    }

    private static int orientationFor(StructureRole role) {
        return switch (role) { case BARRACKS, PRISON, OBSERVATORY -> 1; default -> 0; };
    }

    private static void addRoad(List<ConstructionIntent> out, Faction f, Settlement s, int i, SimPosition c, int w, int d, int rot, int p) {
        StreetType street = StreetType.forWidth(w, false, w >= 7 && p >= 118, p >= 130);
        // Encode hierarchy in the construction key so morphology/history audits can classify roads.
        String key = "road:" + street.name().toLowerCase(java.util.Locale.ROOT) + ":" + i;
        out.add(new ConstructionIntent(key, f.id(), s.id(), StructureRole.ROAD, c, Math.max(street.width(), w), d, rot, p));
    }

    private static int houseFacing(int x, int z, int spacing, int baseRotation) {
        int dx = signedStreetDelta(x, spacing), dz = signedStreetDelta(z, spacing);
        int local;
        if (Math.abs(dx) <= Math.abs(dz)) local = dx > 0 ? 3 : 1;
        else local = dz > 0 ? 0 : 2;
        return Math.floorMod(baseRotation + local, 4);
    }

    private static int signedStreetDelta(int value, int spacing) {
        int nearest = (int) Math.round(value / (double) spacing) * spacing;
        return nearest - value;
    }

    private static int distanceToStreet(int value, int spacing) {
        return Math.abs(signedStreetDelta(value, spacing));
    }

    private static int[] spiral(int n) {
        if (n == 0) return new int[]{0, 0};
        int k = (int) Math.ceil((Math.sqrt(n + 1) - 1) / 2.0), t = 2 * k + 1, m = t * t;
        t--;
        if (n >= m - t) return new int[]{k - (m - n), -k};
        m -= t;
        if (n >= m - t) return new int[]{-k, -k + (m - n)};
        m -= t;
        if (n >= m - t) return new int[]{-k + (m - n), k};
        return new int[]{k, k - (m - t - n)};
    }

    private static SimPosition local(Settlement s, int quarterTurns, double x, double z) {
        return switch (Math.floorMod(quarterTurns, 4)) {
            case 0 -> new SimPosition(s.position().x() + x, s.position().z() + z);
            case 1 -> new SimPosition(s.position().x() - z, s.position().z() + x);
            case 2 -> new SimPosition(s.position().x() - x, s.position().z() - z);
            default -> new SimPosition(s.position().x() + z, s.position().z() - x);
        };
    }

    private static int adjustPriority(dev.livingrealms.sim.faction.DevelopmentPriority policy, StructureRole role, int base) {
        int bonus = switch (policy) {
            case BALANCED -> 0;
            case FOOD -> (role == StructureRole.FARM || role == StructureRole.FISHERY || role == StructureRole.IRRIGATION
                    || role == StructureRole.AQUEDUCT || role == StructureRole.WELL || role == StructureRole.MILL
                    || role == StructureRole.BAKERY || role == StructureRole.PASTURE) ? 35 : 0;
            case HOUSING -> role == StructureRole.HOUSE ? 60 : 0;
            case INDUSTRY -> (role == StructureRole.WORKSHOP || role == StructureRole.FACTORY || role == StructureRole.MINE
                    || role == StructureRole.LUMBER_CAMP || role == StructureRole.BREWERY) ? 35 : 0;
            case DEFENSE -> (role == StructureRole.KEEP || role == StructureRole.BARRACKS || role == StructureRole.WALL
                    || role == StructureRole.GATE || role == StructureRole.AIRFIELD) ? 35 : 0;
        };
        return Math.min(240, base + bonus);
    }

    private static int spacing(SettlementMorphology morph) {
        return switch (morph) {
            case ORGANIC_MEDIEVAL -> 40;
            case MARKET_CROSS -> 48;
            case RADIAL_CAPITAL, WALLED_CORE -> 50;
            case LINEAR_VALLEY, RIVER_TOWN -> 46;
            case COASTAL_PORT -> 44;
            case HILL_TOWN -> 38;
            case INDUSTRIAL_EDGE -> 52;
            case PLANNED_BOULEVARD -> 56;
        };
    }

    private static int organicShift(Settlement settlement, int salt) {
        return Math.floorMod((int) mix(settlement.id() ^ (salt * 0x85EBCA77C2B2AE63L)), 11) - 5;
    }

    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    private static void addAt(List<ConstructionIntent> out, Faction faction, Settlement settlement, StructureRole role,
                              int index, SimPosition center, int width, int depth, int rotation, int priority) {
        out.add(new ConstructionIntent(key(settlement, role, index), faction.id(), settlement.id(), role, center, width, depth, Math.floorMod(rotation, 4), priority));
    }

    private static String key(Settlement settlement, StructureRole role, int index) {
        String base = role.name().toLowerCase(Locale.ROOT);
        return switch (role) {
            case ROAD, KEEP, WALL, GATE -> base + ":" + settlement.tier().ordinal() + ":" + index;
            default -> base + ":" + index;
        };
    }
}
