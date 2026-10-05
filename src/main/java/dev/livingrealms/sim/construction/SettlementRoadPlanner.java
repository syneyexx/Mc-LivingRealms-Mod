package dev.livingrealms.sim.construction;

import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.world.SimPosition;
import java.util.List;

import static dev.livingrealms.sim.construction.SettlementPlanner.addRoad;
import static dev.livingrealms.sim.construction.SettlementPlanner.local;
import static dev.livingrealms.sim.construction.SettlementPlanner.organicShift;
import static dev.livingrealms.sim.construction.SettlementPlanner.spacing;

/**
 * Deterministic street-network planning extracted from {@link SettlementPlanner}.
 * Owns morphology-specific road topology only; no canonical state or world mutation.
 */
final class SettlementRoadPlanner {
    private SettlementRoadPlanner() {}

    static void addRoadNetwork(List<ConstructionIntent> out, Faction faction, Settlement settlement,
                                       SettlementMorphology morph, int baseRotation) {
        int tier = settlement.tier().ordinal();
        int spacing = spacing(morph);
        // Countryside: one through-path is enough. Full street grids only in inhabited TOWN+ cores.
        if (tier <= Settlement.Tier.HAMLET.ordinal()) {
            int len = Math.max(48, spacing + 18);
            addRoad(out, faction, settlement, 0, local(settlement, baseRotation, 0, 0), 3, len * 2 + 3, baseRotation, 130);
            return;
        }
        if (tier == Settlement.Tier.VILLAGE.ordinal()) {
            int len = Math.max(56, spacing + 24);
            addRoad(out, faction, settlement, 0, local(settlement, baseRotation, 0, 0), 5, len * 2 + 5, baseRotation, 132);
            addRoad(out, faction, settlement, 1, local(settlement, baseRotation, 0, 0), 3, spacing + 16, baseRotation + 1, 118);
            return;
        }
        int rings = switch (settlement.tier()) {
            case CAMP, HAMLET, VILLAGE -> 0; case TOWN -> 1; case CITY -> 2; case METROPOLIS -> 3;
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
                    addRoad(out, faction, settlement, index++, local(settlement, baseRotation, offset + jx, jz), 5, halfLength * 2 + 5, baseRotation, 108, ring);
                    addRoad(out, faction, settlement, index++, local(settlement, baseRotation, -offset + jx / 2, -jz), 5, halfLength * 2 + 5, baseRotation, 108, ring);
                    addRoad(out, faction, settlement, index++, local(settlement, baseRotation, jx, offset + jz), 5, halfLength * 2 + 5, baseRotation + 1, 108, ring);
                    addRoad(out, faction, settlement, index++, local(settlement, baseRotation, -jx, -offset + jz / 2), 5, halfLength * 2 + 5, baseRotation + 1, 108, ring);
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
            addRoad(out, faction, settlement, index++, local(settlement, baseRotation, offset, 0), 9, halfLength * 2 + 9, baseRotation, 112, ring);
            addRoad(out, faction, settlement, index++, local(settlement, baseRotation, -offset, 0), 9, halfLength * 2 + 9, baseRotation, 112, ring);
            addRoad(out, faction, settlement, index++, local(settlement, baseRotation, 0, offset), 9, halfLength * 2 + 9, baseRotation + 1, 112, ring);
            addRoad(out, faction, settlement, index++, local(settlement, baseRotation, 0, -offset), 9, halfLength * 2 + 9, baseRotation + 1, 112, ring);
        }
        if (sideStreets && tier >= Settlement.Tier.VILLAGE.ordinal()) {
            for (int ring = 0; ring < rings; ring++) {
                int offset = spacing * ring + spacing / 2;
                addRoad(out, faction, settlement, index++, local(settlement, baseRotation, offset, 0), 5, halfLength * 2 + 5, baseRotation, 98, ring);
                addRoad(out, faction, settlement, index++, local(settlement, baseRotation, -offset, 0), 5, halfLength * 2 + 5, baseRotation, 98, ring);
                addRoad(out, faction, settlement, index++, local(settlement, baseRotation, 0, offset), 5, halfLength * 2 + 5, baseRotation + 1, 98, ring);
                addRoad(out, faction, settlement, index++, local(settlement, baseRotation, 0, -offset), 5, halfLength * 2 + 5, baseRotation + 1, 98, ring);
            }
        }
        return index;
    }
}
