package dev.livingrealms.sim.worldgen;

import dev.livingrealms.sim.construction.ConstructionIntent;
import dev.livingrealms.sim.construction.SettlementPlanner;
import dev.livingrealms.sim.construction.SettlementStreetGraph;
import dev.livingrealms.sim.construction.StructureRole;
import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.faction.SettlementRole;
import dev.livingrealms.sim.world.SimPosition;
import dev.livingrealms.sim.world.StarterCivilizationLayoutPlanner;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Post-planning structural validation for starter civilization worldgen.
 *
 * <p>Runs after strategic + settlement planning and before / alongside physical realization.
 * Failures are collected as invariant violations rather than only logged.</p>
 */
public final class StarterWorldgenValidationEngine {
    public static final double MAX_BUILDING_OVERLAP_RATIO = 0.15;
    /** Distance from building center to nearest road *surface* (centerline minus half-width). */
    public static final double MAX_HOUSE_ROAD_DISTANCE = 20.0;
    public static final double MAX_CIVIC_ROAD_DISTANCE = 28.0;
    public static final double MIN_CAPITAL_KEEP_FOOTPRINT = 27.0;
    public static final double MIN_ROAD_ACCESS_RATIO = 0.80;
    public static final double MIN_CRITICAL_ACCESS_RATIO = 0.90;

    public record Issue(Severity severity, String code, String detail) {
        public enum Severity { ERROR, WARNING }

        public Issue {
            severity = Objects.requireNonNull(severity, "severity");
            if (code == null || code.isBlank()) throw new IllegalArgumentException("code");
            detail = detail == null ? "" : detail;
        }
    }

    public record Report(
            long worldSeed,
            int settlementsChecked,
            int routesChecked,
            List<Issue> issues,
            QualityMetrics metrics
    ) {
        public Report {
            issues = List.copyOf(Objects.requireNonNull(issues, "issues"));
            metrics = Objects.requireNonNull(metrics, "metrics");
        }

        public boolean ok() {
            return issues.stream().noneMatch(i -> i.severity() == Issue.Severity.ERROR);
        }
    }

    public record QualityMetrics(
            double buildingsWithRoadAccessRatio,
            double criticalBuildingsReachableRatio,
            int buildingOverlaps,
            int disconnectedStreetGraphs,
            int gatesMissingApproach,
            int capitalsMissingKeep
    ) {}

    private StarterWorldgenValidationEngine() {}

    public static Report validate(StarterCivilizationLayoutPlanner.Layout layout) {
        Objects.requireNonNull(layout, "layout");
        List<SettlementInitialWorldgenPlan> plans = SettlementInitialWorldgenPlan.buildAll(layout);
        List<StarterRegionalRoutePlanner.RoutePlan> routes = StarterRegionalRoutePlanner.plan(layout);
        return validate(layout, plans, routes);
    }

    public static Report validate(
            StarterCivilizationLayoutPlanner.Layout layout,
            List<SettlementInitialWorldgenPlan> plans,
            List<StarterRegionalRoutePlanner.RoutePlan> routes) {
        Objects.requireNonNull(layout, "layout");
        Objects.requireNonNull(plans, "plans");
        Objects.requireNonNull(routes, "routes");

        List<Issue> issues = new ArrayList<>();
        StarterCivilizationManifest.build(layout, routes);

        int buildings = 0;
        int buildingsWithRoad = 0;
        int critical = 0;
        int criticalReachable = 0;
        int overlaps = 0;
        int disconnected = 0;
        int gatesMissingApproach = 0;
        int capitalsMissingKeep = 0;

        Set<Long> settlementIds = new HashSet<>();
        Set<String> intentKeys = new HashSet<>();

        for (SettlementInitialWorldgenPlan plan : plans) {
            if (!settlementIds.add(plan.settlementId())) {
                issues.add(error("DUPLICATE_SETTLEMENT_ID",
                        "duplicate settlement id " + plan.settlementId()));
            }

            StarterSettlementWorldgenContract.Report contract =
                    StarterSettlementWorldgenContract.inspect(plan);
            for (String problem : contract.problems()) {
                issues.add(error("SETTLEMENT_CONTRACT",
                        plan.settlementName() + ": " + problem));
            }
            gatesMissingApproach += Math.max(0, contract.gates() - contract.connectedGates());

            for (ConstructionIntent intent : plan.intents()) {
                String globalKey = plan.settlementId() + ":" + intent.key();
                if (!intentKeys.add(globalKey)) {
                    issues.add(error("DUPLICATE_FEATURE_ID", "duplicate feature " + globalKey));
                }
                if (!Double.isFinite(intent.center().x()) || !Double.isFinite(intent.center().z())) {
                    issues.add(error("INVALID_COORDINATE", globalKey));
                }
            }

            List<ConstructionIntent> roads = plan.intents().stream()
                    .filter(i -> i.role() == StructureRole.ROAD).toList();
            SettlementStreetGraph graph = SettlementStreetGraph.fromRoadIntents(plan.settlementId(), roads);
            if (!roads.isEmpty() && !graph.hasConnectedCore()) {
                disconnected++;
                issues.add(error("DISCONNECTED_STREETS",
                        plan.settlementName() + " street graph is not connected"));
            }

            List<ConstructionIntent> fixed = plan.intents().stream()
                    .filter(i -> SettlementPlanner.occupiesExclusiveFootprint(i.role()))
                    .toList();
            int planOverlaps = countOverlaps(fixed);
            overlaps += planOverlaps;
            if (planOverlaps > 0) {
                issues.add(error("BUILDING_OVERLAP",
                        plan.settlementName() + " has overlapping building footprints ("
                                + planOverlaps + ")"));
            }

            if (plan.role() == SettlementRole.CAPITAL) {
                ConstructionIntent keep = plan.intents().stream()
                        .filter(i -> i.role() == StructureRole.KEEP)
                        .findFirst().orElse(null);
                if (keep == null
                        || keep.width() < MIN_CAPITAL_KEEP_FOOTPRINT
                        || keep.depth() < MIN_CAPITAL_KEEP_FOOTPRINT) {
                    capitalsMissingKeep++;
                    issues.add(error("CAPITAL_KEEP",
                            plan.settlementName() + " lacks a capital-scale keep"));
                }
            }

            for (ConstructionIntent building : fixed) {
                if (!isAccessCheckedRole(building.role())) continue;
                buildings++;
                boolean criticalBuilding = isCriticalRole(building.role());
                if (criticalBuilding) critical++;
                double distance = minDistanceToRoad(building.center(), roads);
                double limit = criticalBuilding ? MAX_CIVIC_ROAD_DISTANCE : MAX_HOUSE_ROAD_DISTANCE;
                if (distance <= limit) {
                    buildingsWithRoad++;
                    if (criticalBuilding) criticalReachable++;
                } else {
                    issues.add(warning("ROAD_ACCESS",
                            plan.settlementName() + " " + building.key()
                                    + " is " + String.format("%.1f", distance)
                                    + " from nearest road"));
                }
            }

            if (plan.tier().ordinal() >= Settlement.Tier.VILLAGE.ordinal()) {
                boolean hasPrimary = plan.intents().stream().anyMatch(i ->
                        i.role() == StructureRole.MINE
                                || i.role() == StructureRole.LUMBER_CAMP
                                || i.role() == StructureRole.FISHERY
                                || i.role() == StructureRole.FARM);
                if (!hasPrimary) {
                    issues.add(warning("PRIMARY_ECONOMY",
                            plan.settlementName() + " has no primary production site"));
                }
            }
        }

        Map<Long, SettlementInitialWorldgenPlan> byId = new HashMap<>();
        for (SettlementInitialWorldgenPlan plan : plans) byId.put(plan.settlementId(), plan);

        for (StarterRegionalRoutePlanner.RoutePlan route : routes) {
            SettlementInitialWorldgenPlan from = byId.get(route.fromSettlementId());
            SettlementInitialWorldgenPlan to = byId.get(route.toSettlementId());
            if (from == null || to == null) {
                issues.add(error("ROUTE_ENDPOINT",
                        "route " + route.stableRouteId() + " references missing settlement"));
                continue;
            }
            if (from.tier().ordinal() >= Settlement.Tier.CITY.ordinal()
                    && !touchesGate(from, route.from())) {
                issues.add(error("ROUTE_GATE",
                        "route " + route.stableRouteId() + " does not start at a city gate"));
            }
            if (to.tier().ordinal() >= Settlement.Tier.CITY.ordinal()
                    && !touchesGate(to, route.to())) {
                issues.add(error("ROUTE_GATE",
                        "route " + route.stableRouteId() + " does not end at a city gate"));
            }
        }

        double roadRatio = buildings == 0 ? 1.0 : buildingsWithRoad / (double) buildings;
        double criticalRatio = critical == 0 ? 1.0 : criticalReachable / (double) critical;
        if (roadRatio < MIN_ROAD_ACCESS_RATIO) {
            issues.add(error("ROAD_ACCESS_RATIO",
                    "buildings with road access " + String.format("%.2f", roadRatio)
                            + " < " + MIN_ROAD_ACCESS_RATIO));
        }
        if (criticalRatio < MIN_CRITICAL_ACCESS_RATIO) {
            issues.add(error("CRITICAL_ACCESS_RATIO",
                    "critical buildings reachable " + String.format("%.2f", criticalRatio)
                            + " < " + MIN_CRITICAL_ACCESS_RATIO));
        }

        QualityMetrics metrics = new QualityMetrics(
                roadRatio, criticalRatio, overlaps, disconnected,
                gatesMissingApproach, capitalsMissingKeep);
        return new Report(layout.worldSeed(), plans.size(), routes.size(), issues, metrics);
    }

    public static void requireValid(StarterCivilizationLayoutPlanner.Layout layout) {
        Report report = validate(layout);
        if (report.ok()) return;
        List<String> errors = report.issues().stream()
                .filter(i -> i.severity() == Issue.Severity.ERROR)
                .map(i -> i.code() + ": " + i.detail())
                .toList();
        throw new IllegalStateException(
                "Starter worldgen validation failed: " + String.join("; ", errors));
    }

    private static Issue error(String code, String detail) {
        return new Issue(Issue.Severity.ERROR, code, detail);
    }

    private static Issue warning(String code, String detail) {
        return new Issue(Issue.Severity.WARNING, code, detail);
    }

    private static boolean isAccessCheckedRole(StructureRole role) {
        return switch (role) {
            case HOUSE, KEEP, TOWN_HALL, MARKET, WAREHOUSE, WORKSHOP, BARRACKS, TEMPLE,
                    CLINIC, SCHOOL, COURTHOUSE, PRISON, TAVERN, WELL, MONUMENT, FACTORY,
                    MILL, BAKERY, BREWERY -> true;
            default -> false;
        };
    }

    private static boolean isCriticalRole(StructureRole role) {
        return switch (role) {
            case KEEP, TOWN_HALL, MARKET, GATE, WELL -> true;
            default -> false;
        };
    }

    private static double minDistanceToRoad(SimPosition center, List<ConstructionIntent> roads) {
        double best = Double.POSITIVE_INFINITY;
        for (ConstructionIntent road : roads) {
            double halfWidth = Math.max(0.5, road.width() / 2.0);
            if (!road.hasPath()) {
                best = Math.min(best, Math.max(0.0, center.distanceTo(road.center()) - halfWidth));
                continue;
            }
            for (SimPosition point : road.path()) {
                best = Math.min(best, Math.max(0.0, center.distanceTo(point) - halfWidth));
            }
        }
        return best;
    }

    private static int countOverlaps(List<ConstructionIntent> buildings) {
        int overlaps = 0;
        for (int i = 0; i < buildings.size(); i++) {
            ConstructionIntent a = buildings.get(i);
            for (int j = i + 1; j < buildings.size(); j++) {
                ConstructionIntent b = buildings.get(j);
                if (SettlementPlanner.footprintConflicts(
                        List.of(a), b.center(), b.width(), b.depth(), b.rotationQuarterTurns())) {
                    overlaps++;
                }
            }
        }
        return overlaps;
    }

    private static boolean touchesGate(SettlementInitialWorldgenPlan plan, SimPosition point) {
        for (ConstructionIntent intent : plan.intents()) {
            if (intent.role() == StructureRole.GATE && intent.center().distanceTo(point) < 1.0) {
                return true;
            }
        }
        return false;
    }
}
