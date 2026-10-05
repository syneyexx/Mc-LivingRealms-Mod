package dev.livingrealms.sim.construction;

import dev.livingrealms.sim.faction.DevelopmentMode;
import dev.livingrealms.sim.faction.DevelopmentModeGuard;
import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.world.SimPosition;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static dev.livingrealms.sim.construction.SettlementPlanner.addAtParcel;
import static dev.livingrealms.sim.construction.SettlementPlanner.houseFootprintFromTemplate;
import static dev.livingrealms.sim.construction.SettlementPlanner.mix;

/**
 * Parcel-bound residential planning extracted from {@link SettlementPlanner}.
 * Preserves PLAYER_LED/HYBRID guards and the road-first invariant.
 */
final class SettlementHousingPlanner {
    private SettlementHousingPlanner() {}

    static void addHousing(List<ConstructionIntent> out, Faction faction, Settlement settlement,
                                   SettlementMorphology morph, int baseRotation,
                                   SettlementStreetGraph streetGraph, CultureArchitecture culture) {
        // PLAYER_LED: Living Realms does not auto-spawn houses over the player's layout.
        if (!DevelopmentModeGuard.allowsOrdinaryHouseEmission(settlement)) return;
        int represented = Math.max(settlement.population(), settlement.housing());
        int softCap = switch (settlement.tier()) {
            case CAMP -> 24; case HAMLET -> 48; case VILLAGE -> 96; case TOWN -> 220; case CITY -> 480; case METROPOLIS -> 900;
        };
        int houses = Math.min(softCap, Math.max(5, (int) Math.ceil(represented / 22.0)));
        // HYBRID: fill genuine deficits only — do not instantly overwrite player design with a full town.
        if (settlement.developmentMode() == DevelopmentMode.HYBRID) {
            int shortage = Math.max(0, settlement.population() - settlement.housing());
            if (shortage <= 0) return;
            houses = Math.min(houses, Math.max(1, (int) Math.ceil(shortage / 22.0)));
        }
        List<SettlementParcelPlanner.ParcelPlan> remaining =
                new java.util.ArrayList<>(SettlementParcelPlanner.plan(streetGraph, faction, settlement, houses));
        // Determine building archetype BEFORE parcel reservation — request a parcel that fits W×D.
        int emitted = 0;
        boolean pressure = settlement.housingShortage() > 40 || settlement.population() > settlement.housing();
        for (int i = 0; i < houses; i++) {
            int variant = Math.floorMod((int) mix(settlement.id() ^ (long) i * 0x9E3779B97F4A7C15L), 7);
            int w, d;
            if (settlement.tier().ordinal() >= Settlement.Tier.CITY.ordinal() && (i % 5 == 0 || (pressure && i % 3 == 0))) {
                w = 13; d = 11;
            } else if (settlement.tier().ordinal() >= Settlement.Tier.TOWN.ordinal() && (i % 6 == 0 || (pressure && i % 4 == 0))) {
                w = 11; d = 9;
            } else if (settlement.tier() == Settlement.Tier.METROPOLIS && i % 2 == 0) {
                w = 13; d = 11;
            } else {
                w = switch (variant) { case 0 -> 9; case 1, 4 -> 11; default -> 9; };
                d = switch (variant) { case 2 -> 11; case 5 -> 9; default -> 9; };
            }
            w = Math.max(w, culture.minHouseWidth());
            d = Math.max(d, culture.minHouseDepth());
            int[] templated = houseFootprintFromTemplate(faction, settlement, culture, w, d);
            w = templated[0];
            d = templated[1];
            SettlementParcelPlanner.ParcelPlan parcel = null;
            int parcelIndex = -1;
            for (int pi = 0; pi < remaining.size(); pi++) {
                SettlementParcelPlanner.ParcelPlan candidate = remaining.get(pi);
                if (candidate.width() >= w && candidate.depth() >= d) {
                    parcel = candidate;
                    parcelIndex = pi;
                    break;
                }
            }
            if (parcel == null) {
                // No parcel fits this archetype — defer rather than place off-street.
                continue;
            }
            remaining.remove(parcelIndex);
            int face = parcel.orientationQuarterTurns();
            // Key by stable demand slot, not by count of successful reservations. If a parcel
            // becomes available later, it fills a previously absent key instead of renumbering/moving
            // already-materialized houses.
            addAtParcel(out, faction, settlement, StructureRole.HOUSE, i, parcel, w, d, face, 88);
            emitted++;
        }
        // Road-first invariant: never spiral-place houses off the street graph.
        // Remaining demand extends side streets / lanes, then fills new frontage parcels.
        // CAMP/HAMLET keep a sparse countryside path — do not grid-extend them.
        if (emitted < houses && settlement.tier().ordinal() >= Settlement.Tier.VILLAGE.ordinal()) {
            extendSideStreetsForHousing(out, faction, settlement, morph, streetGraph, baseRotation,
                    houses - emitted, culture);
        }
    }

    /**
     * When parcels are insufficient, extend short graph-authored residential lanes from existing
     * centerline vertices. ROAD intents are downstream projections of these new graph segments.
     */
    private static int parcelOrdinal(String parcelId) {
        int split = parcelId == null ? -1 : parcelId.lastIndexOf(':');
        if (split >= 0 && split + 1 < parcelId.length()) {
            try { return Math.max(0, Integer.parseInt(parcelId.substring(split + 1))); }
            catch (NumberFormatException ignored) { /* deterministic hash fallback below */ }
        }
        return Math.floorMod(parcelId == null ? 0 : parcelId.hashCode(), 100_000);
    }

    private static void extendSideStreetsForHousing(List<ConstructionIntent> out, Faction faction, Settlement settlement,
                                                   SettlementMorphology morph, SettlementStreetGraph streetGraph,
                                                   int baseRotation, int deficit, CultureArchitecture culture) {
        if (deficit <= 0 || streetGraph == null || streetGraph.segmentByKey().isEmpty()) return;
        int lanesNeeded = Math.min(24, Math.max(2, (int) Math.ceil(deficit / 3.0)));
        int lane = 0;
        List<SettlementStreetGraph.RoadSegment> spines = new ArrayList<>(streetGraph.segmentByKey().values());
        spines.sort(Comparator.comparingInt(SettlementStreetGraph.RoadSegment::priority).reversed()
                .thenComparing(SettlementStreetGraph.RoadSegment::key));
        List<SettlementStreetGraph.RoadSegment> additions = new ArrayList<>();

        for (int ring = 1; ring <= 4 && lane < lanesNeeded; ring++) {
            for (SettlementStreetGraph.RoadSegment spine : spines) {
                if (lane >= lanesNeeded) break;
                List<SimPosition> path = spine.centerline();
                if (spine.length() < 12 || path.size() < 2) continue;

                int attachIndex = path.size() >= 3
                        ? 1 + Math.floorMod(lane + ring, path.size() - 2)
                        : 0;
                SimPosition attach = path.get(attachIndex);
                SimPosition neighbor = path.get(Math.min(path.size() - 1, attachIndex + 1));
                if (neighbor.distanceTo(attach) < 1.0e-6 && attachIndex > 0) neighbor = path.get(attachIndex - 1);
                double dx = neighbor.x() - attach.x(), dz = neighbor.z() - attach.z();
                double len = Math.hypot(dx, dz);
                if (len < 1.0e-6) continue;
                double tx = dx / len, tz = dz / len;
                double nx = -tz, nz = tx;
                double sign = (lane & 1) == 0 ? 1.0 : -1.0;
                double laneLength = 24.0 + ring * 8.0;
                double bend = ((Math.floorMod((int) (settlement.id() + lane * 17L), 7) - 3) * 1.5);
                SimPosition mid = new SimPosition(
                        attach.x() + nx * sign * laneLength * .52 + tx * bend,
                        attach.z() + nz * sign * laneLength * .52 + tz * bend);
                SimPosition endPoint = new SimPosition(
                        attach.x() + nx * sign * laneLength,
                        attach.z() + nz * sign * laneLength);
                SettlementGrowthLayer layer = SettlementGrowthLayer.forRing(
                        ring, morph, settlement.tier().ordinal());
                String key = "roadgraph:housing:" + settlement.id() + ":" + lane;
                additions.add(new SettlementStreetGraph.RoadSegment(
                        key, StreetType.RESIDENTIAL_STREET, StreetType.RESIDENTIAL_STREET.width(),
                        attach, endPoint, List.of(attach, mid, endPoint), 90, layer));
                lane++;
            }
        }
        if (additions.isEmpty()) return;

        SettlementStreetGraph extended = streetGraph.withAdditionalSegments(additions);
        SettlementRoadPlanner.addRoadSegments(out, faction, settlement, additions);
        java.util.Set<String> extensionRoadKeys = additions.stream()
                .map(SettlementStreetGraph.RoadSegment::key)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));

        int alreadyHouses = (int) out.stream().filter(i -> i.role() == StructureRole.HOUSE).count();
        List<SettlementParcelPlanner.ParcelPlan> extraParcels = new ArrayList<>(
                SettlementParcelPlanner.plan(extended, faction, settlement, alreadyHouses + deficit));
        List<SimPosition> occupied = new ArrayList<>(out.stream()
                .filter(i -> i.role() == StructureRole.HOUSE)
                .map(ConstructionIntent::center)
                .toList());
        int placed = 0;
        int w = Math.max(9, culture.minHouseWidth());
        int d = Math.max(9, culture.minHouseDepth());
        for (SettlementParcelPlanner.ParcelPlan parcel : extraParcels) {
            if (placed >= deficit) break;
            // Fallback houses belong only to the graph lanes created for the shortage. Reusing
            // base-street parcels would let later ordinary demand steal them and move house keys.
            if (!extensionRoadKeys.contains(parcel.frontageSegmentKey())) continue;
            if (parcel.width() < w || parcel.depth() < d) continue;
            boolean clash = false;
            for (SimPosition pos : occupied) {
                if (Math.abs(pos.x() - parcel.center().x()) < (w + parcel.width()) / 2.0 + 2
                        && Math.abs(pos.z() - parcel.center().z()) < (d + parcel.depth()) / 2.0 + 2) {
                    clash = true;
                    break;
                }
            }
            if (clash) continue;
            int stableHouseIndex = 900 + parcelOrdinal(parcel.id());
            addAtParcel(out, faction, settlement, StructureRole.HOUSE, stableHouseIndex, parcel,
                    Math.min(parcel.width(), w + 2), Math.min(parcel.depth(), d + 2),
                    parcel.orientationQuarterTurns(), 86);
            occupied.add(parcel.center());
            placed++;
        }
    }
}
