package dev.livingrealms.sim.construction;

import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.world.SimPosition;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Places residential parcels along real street-graph edges. Houses face {@link ParcelFrontage}
 * rather than an abstract spiral grid.
 */
public final class SettlementParcelPlanner {
    /** Door/frontage contact with a road edge. */
    public record ParcelFrontage(
            String roadKey,
            String segmentKey,
            SimPosition point,
            int orientationQuarterTurns,
            double along
    ) {
        public ParcelFrontage {
            if (roadKey == null || roadKey.isBlank()) throw new IllegalArgumentException("roadKey");
            if (segmentKey == null || segmentKey.isBlank()) throw new IllegalArgumentException("segmentKey");
            point = Objects.requireNonNull(point, "point");
            orientationQuarterTurns = Math.floorMod(orientationQuarterTurns, 4);
        }
    }

    public record ParcelPlan(
            String id,
            long settlementId,
            String frontageRoadKey,
            String frontageSegmentKey,
            ParcelFrontage frontage,
            SimPosition center,
            int width,
            int depth,
            int orientationQuarterTurns,
            SettlementDistrict district,
            double terrainSuitability,
            int reservedAccessStrip
    ) {
        public ParcelPlan {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("id");
            if (settlementId <= 0) throw new IllegalArgumentException("settlementId");
            if (frontageRoadKey == null || frontageRoadKey.isBlank()) throw new IllegalArgumentException("frontageRoadKey");
            if (frontageSegmentKey == null || frontageSegmentKey.isBlank()) throw new IllegalArgumentException("frontageSegmentKey");
            frontage = Objects.requireNonNull(frontage, "frontage");
            center = Objects.requireNonNull(center, "center");
            if (width <= 0 || depth <= 0) throw new IllegalArgumentException("footprint");
            orientationQuarterTurns = Math.floorMod(orientationQuarterTurns, 4);
            district = Objects.requireNonNullElse(district, SettlementDistrict.RESIDENTIAL);
            terrainSuitability = Double.isFinite(terrainSuitability) ? Math.max(0, Math.min(1, terrainSuitability)) : 0.5;
            if (reservedAccessStrip < 0) throw new IllegalArgumentException("accessStrip");
        }

        /** True when the house footprint does not overlap the road rectangle of its frontage segment. */
        public boolean footprintClearsRoad(SettlementStreetGraph.RoadSegment segment) {
            Objects.requireNonNull(segment, "segment");
            double halfW = width / 2.0;
            double halfD = depth / 2.0;
            // Conservative AABB check in world XZ against the road corridor AABB expanded by width.
            double roadHalf = segment.width() / 2.0 + 0.5;
            double minX = Math.min(segment.start().x(), segment.end().x()) - roadHalf;
            double maxX = Math.max(segment.start().x(), segment.end().x()) + roadHalf;
            double minZ = Math.min(segment.start().z(), segment.end().z()) - roadHalf;
            double maxZ = Math.max(segment.start().z(), segment.end().z()) + roadHalf;
            double hx0 = center.x() - halfW, hx1 = center.x() + halfW;
            double hz0 = center.z() - halfD, hz1 = center.z() + halfD;
            boolean overlap = hx0 < maxX && hx1 > minX && hz0 < maxZ && hz1 > minZ;
            return !overlap;
        }
    }

    private SettlementParcelPlanner() {}

    public static List<ParcelPlan> plan(SettlementStreetGraph graph, Faction faction, Settlement settlement, int houseCount) {
        Objects.requireNonNull(graph, "graph");
        Objects.requireNonNull(faction, "faction");
        Objects.requireNonNull(settlement, "settlement");
        if (houseCount <= 0 || graph.isEmpty()) return List.of();

        CultureArchitecture culture = CultureArchitectureProfile.derive(faction, settlement).architecture();
        int baseW = Math.max(9, culture.minHouseWidth());
        int baseD = Math.max(9, culture.minHouseDepth());
        int accessStrip = 2;
        List<ParcelPlan> out = new ArrayList<>();
        List<SettlementStreetGraph.RoadSegment> roads = new ArrayList<>(graph.segments());
        roads.sort((a, b) -> Integer.compare(b.priority(), a.priority()));

        int emitted = 0;
        for (SettlementStreetGraph.RoadSegment segment : roads) {
            if (emitted >= houseCount) break;
            // Skip very short stubs / gate approaches for housing.
            if (segment.length() < 18) continue;
            boolean alongX = segment.axisAlignedAlongX();
            double len = segment.length();
            // Mixed lot sizes: cottages, townhouses, apartments — sized BEFORE reservation.
            int slots = Math.max(1, (int) (len / (baseW + 4)));
            for (int side = 0; side < 2 && emitted < houseCount; side++) {
                for (int i = 0; i < slots && emitted < houseCount; i++) {
                    int lotW = baseW;
                    int lotD = baseD;
                    if (settlement.tier().ordinal() >= Settlement.Tier.CITY.ordinal()
                            && (emitted % 5 == 0 || (emitted % 3 == 0 && settlement.population() > settlement.housing()))) {
                        lotW = Math.max(lotW, 13);
                        lotD = Math.max(lotD, 11);
                    } else if (settlement.tier().ordinal() >= Settlement.Tier.TOWN.ordinal() && emitted % 6 == 0) {
                        lotW = Math.max(lotW, 11);
                        lotD = Math.max(lotD, 9);
                    } else if (emitted % 7 == 1 || emitted % 7 == 4) {
                        lotW = Math.max(lotW, 11);
                    } else if (emitted % 7 == 2) {
                        lotD = Math.max(lotD, 11);
                    }
                    double t = (i + 0.5) / slots;
                    SimPosition onRoad = segment.start().lerp(segment.end(), t);
                    int setback = segment.width() / 2 + accessStrip + lotD / 2;
                    // Perpendicular offset: for X-aligned road, offset in ±Z; for Z-aligned, ±X.
                    double ox = alongX ? 0 : (side == 0 ? setback : -setback);
                    double oz = alongX ? (side == 0 ? setback : -setback) : 0;
                    SimPosition center = new SimPosition(onRoad.x() + ox, onRoad.z() + oz);
                    if (center.distanceTo(settlement.position()) < 14) continue;
                    // Orientation: door at local -Z must face toward the road (frontage).
                    int orientation = facingToward(center, onRoad);
                    ParcelFrontage frontage = new ParcelFrontage(
                            segment.key(), segment.key(), onRoad, orientation, t * len);
                    SettlementDistrict district = districtFor(settlement, center, lotW, lotD);
                    double suitability = terrainSuitability(settlement, center);
                    if (suitability < 0.2) continue;
                    ParcelPlan parcel = new ParcelPlan(
                            "parcel:house:" + emitted,
                            settlement.id(),
                            segment.key(),
                            segment.key(),
                            frontage,
                            center,
                            lotW,
                            lotD,
                            orientation,
                            district,
                            suitability,
                            accessStrip
                    );
                    if (!parcel.footprintClearsRoad(segment)) continue;
                    // Avoid overlapping earlier parcels — collision uses final footprint.
                    if (overlapsExisting(out, parcel)) continue;
                    out.add(parcel);
                    emitted++;
                }
            }
        }
        return List.copyOf(out);
    }

    /** Find first unused parcel that fits the final building footprint W×D. */
    public static ParcelPlan findFitting(List<ParcelPlan> parcels, int width, int depth) {
        if (parcels == null) return null;
        for (ParcelPlan parcel : parcels) {
            if (parcel.width() >= width && parcel.depth() >= depth) return parcel;
        }
        return null;
    }

    /** Quarter-turns so blueprint local -Z (door) points from house center toward the road point. */
    public static int facingToward(SimPosition houseCenter, SimPosition roadPoint) {
        double dx = roadPoint.x() - houseCenter.x();
        double dz = roadPoint.z() - houseCenter.z();
        if (Math.abs(dx) >= Math.abs(dz)) {
            // Need door along ±X in world: rotation 1 faces -X local→world mapping... 
            // Local door at (0,-depth/2). After rot 0: world -Z. rot 1: world -X. rot 2: +Z. rot 3: +X.
            return dx >= 0 ? 3 : 1;
        }
        return dz >= 0 ? 2 : 0;
    }

    private static SettlementDistrict districtFor(Settlement settlement, SimPosition center, int w, int d) {
        double dist = center.distanceTo(settlement.position());
        boolean apartment = w >= 11 || d >= 11;
        if (apartment && dist < 48) return SettlementDistrict.WEALTHY_QUARTER;
        if (dist < 80) return SettlementDistrict.OLD_TOWN;
        if (dist > 140) return SettlementDistrict.RURAL_FRINGE;
        if (dist > 90) return SettlementDistrict.WORKERS_QUARTER;
        return SettlementDistrict.RESIDENTIAL;
    }

    private static double terrainSuitability(Settlement settlement, SimPosition center) {
        var geo = settlement.geography();
        double slopePenalty = Math.min(1, geo.slope() / 20.0);
        // Prefer flatter parcels; coastal harbor face gets a mild boost for setback lots.
        double base = 1.0 - slopePenalty * 0.7;
        if (geo.shipSuitable() && center.z() > settlement.position().z()) base += 0.05;
        return Math.max(0, Math.min(1, base));
    }

    private static boolean overlapsExisting(List<ParcelPlan> existing, ParcelPlan candidate) {
        double pad = 2;
        for (ParcelPlan other : existing) {
            double dx = Math.abs(other.center().x() - candidate.center().x());
            double dz = Math.abs(other.center().z() - candidate.center().z());
            double needX = (other.width() + candidate.width()) / 2.0 + pad;
            double needZ = (other.depth() + candidate.depth()) / 2.0 + pad;
            if (dx < needX && dz < needZ) return true;
        }
        return false;
    }
}
