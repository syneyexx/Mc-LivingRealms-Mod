package dev.livingrealms.minecraft.worldgen;

import dev.livingrealms.sim.faction.SettlementRole;
import dev.livingrealms.sim.world.SettlementSpacingPolicy;
import dev.livingrealms.sim.world.SimPosition;
import dev.livingrealms.sim.world.StarterCivilizationLayoutPlanner;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import net.minecraft.server.level.ServerLevel;

/**
 * Main-thread deterministic terrain refinement for the pure strategic starter layout.
 *
 * <p>The settlement choice order remains single-threaded and deterministic because role-aware
 * spacing depends on previously resolved settlements. The expensive generator-only terrain
 * sampling for each settlement's already-legal candidate positions is parallelized. This preserves
 * the exact candidate set, scoring formula and tie-breaking order while using multiple CPU cores.</p>
 */
public final class StarterSettlementTerrainResolver {
    public record Resolution(
            StarterCivilizationLayoutPlanner.Layout layout,
            int movedSettlements
    ) {}

    private record TerrainStats(
            double waterFraction,
            int relief,
            double averageGround
    ) {}

    private record SearchSpec(
            int maxRadius,
            int ringStep,
            int sampleRadius,
            double maxWaterFraction,
            int preferredRelief
    ) {}

    private record Candidate(
            SimPosition position,
            Future<TerrainStats> terrainFuture,
            TerrainStats immediateTerrain
    ) {
        TerrainStats terrain() {
            if (immediateTerrain != null) return immediateTerrain;
            try {
                return terrainFuture.get();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(
                        "Interrupted while refining starter settlement terrain", interrupted);
            } catch (ExecutionException failed) {
                Throwable cause = failed.getCause() == null ? failed : failed.getCause();
                throw new IllegalStateException(
                        "Failed to refine starter settlement terrain", cause);
            }
        }
    }

    private static final int ANGLES_PER_RING = 12;

    private StarterSettlementTerrainResolver() {}

    public static Resolution resolve(
            ServerLevel level,
            StarterCivilizationLayoutPlanner.Layout source) {
        return resolve(level, source, new StarterGeneratorTerrainCache(level));
    }

    static Resolution resolve(
            ServerLevel level,
            StarterCivilizationLayoutPlanner.Layout source,
            StarterGeneratorTerrainCache terrainCache) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(terrainCache, "terrainCache");

        List<StarterCivilizationLayoutPlanner.SettlementPlan> all =
                new ArrayList<>(source.settlements());
        Map<Long, StarterCivilizationLayoutPlanner.SettlementPlan> byId = new HashMap<>();
        for (var settlement : all) byId.put(settlement.id(), settlement);

        List<StarterCivilizationLayoutPlanner.SettlementPlan> order =
                new ArrayList<>(all);
        order.sort(Comparator
                .comparingInt((StarterCivilizationLayoutPlanner.SettlementPlan s) ->
                        rolePriority(s.role()))
                .thenComparingLong(StarterCivilizationLayoutPlanner.SettlementPlan::id));

        int workers = terrainPlanningWorkers(order.size());
        ExecutorService executor = workers <= 1 ? null : Executors.newFixedThreadPool(
                workers, runnable -> {
                    Thread thread = new Thread(runnable, "LivingRealms-Settlement-Terrain");
                    thread.setDaemon(true);
                    return thread;
                });

        Map<Long, SimPosition> resolved = new HashMap<>();
        int moved = 0;
        try {
            for (var settlement : order) {
                if (settlement.role() == SettlementRole.SPECIAL) {
                    resolved.put(settlement.id(), settlement.position());
                    continue;
                }
                SearchSpec spec = searchSpec(settlement.role());
                SimPosition chosen = choose(
                        source.worldSeed(), settlement, all, byId, resolved, spec,
                        terrainCache, executor);
                resolved.put(settlement.id(), chosen);
                if (chosen.distanceTo(settlement.position()) > 0.5) moved++;
            }
        } finally {
            if (executor != null) executor.shutdownNow();
        }

        List<StarterCivilizationLayoutPlanner.RealmPlan> realms =
                new ArrayList<>(source.realms().size());
        for (var realm : source.realms()) {
            List<StarterCivilizationLayoutPlanner.SettlementPlan> settlements =
                    new ArrayList<>(realm.settlements().size());
            for (var settlement : realm.settlements()) {
                settlements.add(new StarterCivilizationLayoutPlanner.SettlementPlan(
                        settlement.id(),
                        settlement.stableKey(),
                        settlement.realmId(),
                        settlement.name(),
                        resolved.getOrDefault(settlement.id(), settlement.position()),
                        settlement.population(),
                        settlement.housing(),
                        settlement.role(),
                        settlement.parentSettlementId()));
            }
            realms.add(new StarterCivilizationLayoutPlanner.RealmPlan(
                    realm.definition(), realm.factionId(), realm.armyId(), settlements));
        }
        return new Resolution(new StarterCivilizationLayoutPlanner.Layout(
                source.worldSeed(), source.layoutVersion(), realms), moved);
    }

    static StarterCivilizationLayoutPlanner.SettlementPlan resolveOne(
            StarterCivilizationLayoutPlanner.Layout source,
            StarterCivilizationLayoutPlanner.SettlementPlan settlement,
            StarterGeneratorTerrainCache terrainCache) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(settlement, "settlement");
        Objects.requireNonNull(terrainCache, "terrainCache");
        if (settlement.role() == SettlementRole.SPECIAL) return settlement;

        List<StarterCivilizationLayoutPlanner.SettlementPlan> all =
                new ArrayList<>(source.settlements());
        Map<Long, StarterCivilizationLayoutPlanner.SettlementPlan> byId = new HashMap<>();
        for (var other : all) byId.put(other.id(), other);

        // Chunk-worldgen must never wait on the original exhaustive 60–100 candidate search.
        // The lazy path samples a deterministic coarse candidate fan with a 3x3 terrain stencil.
        // It preserves role-specific relocation radii, spacing law and the same score components,
        // while bounding one settlement to a few hundred generator-height samples.
        SearchSpec spec = searchSpec(settlement.role());
        SimPosition chosen = chooseLazy(
                source.worldSeed(), settlement, all, byId, spec, terrainCache);
        if (chosen.equals(settlement.position())) return settlement;
        return new StarterCivilizationLayoutPlanner.SettlementPlan(
                settlement.id(),
                settlement.stableKey(),
                settlement.realmId(),
                settlement.name(),
                chosen,
                settlement.population(),
                settlement.housing(),
                settlement.role(),
                settlement.parentSettlementId());
    }

    private static SimPosition chooseLazy(
            long seed,
            StarterCivilizationLayoutPlanner.SettlementPlan settlement,
            List<StarterCivilizationLayoutPlanner.SettlementPlan> all,
            Map<Long, StarterCivilizationLayoutPlanner.SettlementPlan> byId,
            SearchSpec spec,
            StarterGeneratorTerrainCache terrainCache) {
        SimPosition origin = settlement.position();
        List<SimPosition> candidates = new ArrayList<>();
        candidates.add(origin);

        double phase = unitAngle(mix(seed ^ settlement.id()));
        int maxRadius = spec.maxRadius();
        // Keep the central starter capital close enough to world spawn to read as the player's
        // first kingdom. Other capitals retain the full relocation radius for terrain quality.
        if (settlement.role() == SettlementRole.CAPITAL
                && Math.abs(origin.x()) < 1.0
                && Math.abs(origin.z()) < 1.0) {
            maxRadius = Math.min(maxRadius, 192);
        }
        int[] radii = {
                Math.max(spec.ringStep(), maxRadius / 2),
                maxRadius
        };
        for (int radius : radii) {
            if (radius <= 0) continue;
            for (int attempt = 0; attempt < 8; attempt++) {
                double angle = phase + (Math.PI * 2.0 * attempt / 8.0);
                SimPosition candidate = new SimPosition(
                        Math.rint(origin.x() + Math.cos(angle) * radius),
                        Math.rint(origin.z() + Math.sin(angle) * radius));
                if (spacingLegal(settlement, candidate, all, Map.of())) {
                    candidates.add(candidate);
                }
            }
        }

        SimPosition best = origin;
        double bestScore = Double.POSITIVE_INFINITY;
        for (SimPosition position : candidates) {
            boolean detailed = settlement.role() == SettlementRole.CAPITAL
                    || settlement.role() == SettlementRole.CITY;
            TerrainStats terrain = sampleTerrainLazy(
                    position, spec.sampleRadius(), terrainCache, detailed);
            double displacement = position.distanceTo(origin);
            double parentPenalty = parentDistancePenalty(
                    settlement, position, byId, Map.of());
            double excessWater = Math.max(
                    0.0, terrain.waterFraction() - spec.maxWaterFraction());
            double excessRelief = Math.max(
                    0, terrain.relief() - spec.preferredRelief());
            double altitudePenalty =
                    Math.max(0.0, terrain.averageGround() - 170.0) * 1.5;

            double score =
                    terrain.waterFraction() * 3800.0
                            + excessWater * 8000.0
                            + terrain.relief() * 42.0
                            + excessRelief * 80.0
                            + altitudePenalty
                            + displacement * 1.35
                            + parentPenalty * 1.8;
            if (score < bestScore) {
                bestScore = score;
                best = position;
            }
        }
        return best;
    }

    private static TerrainStats sampleTerrainLazy(
            SimPosition center,
            int radius,
            StarterGeneratorTerrainCache terrainCache,
            boolean detailed) {
        int cx = (int) Math.round(center.x());
        int cz = (int) Math.round(center.z());
        int r = Math.max(8, radius);
        int half = Math.max(4, r / 2);
        int[] offsets = detailed
                ? new int[]{-r, -half, 0, half, r}
                : new int[]{-r, 0, r};

        int water = 0;
        int count = 0;
        int minGround = Integer.MAX_VALUE;
        int maxGround = Integer.MIN_VALUE;
        long totalGround = 0L;
        for (int dx : offsets) {
            for (int dz : offsets) {
                int x = cx + dx;
                int z = cz + dz;
                int surface = terrainCache.surfaceY(x, z);
                int floor = terrainCache.groundY(x, z);
                if (surface > floor + 1) water++;
                minGround = Math.min(minGround, floor);
                maxGround = Math.max(maxGround, floor);
                totalGround += floor;
                count++;
            }
        }
        return new TerrainStats(
                water / (double) Math.max(1, count),
                Math.max(0, maxGround - minGround),
                totalGround / (double) Math.max(1, count));
    }

    static int maxSearchRadius(SettlementRole role) {
        return searchSpec(role).maxRadius();
    }

    static int terrainPlanningWorkers(int settlementCount) {
        if (settlementCount <= 1) return 1;
        int processors = Math.max(1, Runtime.getRuntime().availableProcessors());
        return Math.max(1, Math.min(12, processors - 1));
    }

    private static SimPosition choose(
            long seed,
            StarterCivilizationLayoutPlanner.SettlementPlan settlement,
            List<StarterCivilizationLayoutPlanner.SettlementPlan> all,
            Map<Long, StarterCivilizationLayoutPlanner.SettlementPlan> byId,
            Map<Long, SimPosition> resolved,
            SearchSpec spec,
            StarterGeneratorTerrainCache terrainCache,
            ExecutorService executor) {
        SimPosition origin = settlement.position();
        List<Candidate> candidates = new ArrayList<>();

        int rings = Math.max(1, spec.maxRadius() / spec.ringStep());
        double phase = unitAngle(mix(seed ^ settlement.id()));
        for (int ring = 0; ring <= rings; ring++) {
            int radius = ring * spec.ringStep();
            int attempts = ring == 0 ? 1 : ANGLES_PER_RING;
            for (int attempt = 0; attempt < attempts; attempt++) {
                double angle = phase + (Math.PI * 2.0 * attempt / ANGLES_PER_RING);
                SimPosition candidate = ring == 0
                        ? origin
                        : new SimPosition(
                                Math.rint(origin.x() + Math.cos(angle) * radius),
                                Math.rint(origin.z() + Math.sin(angle) * radius));

                // Keep the original ordering rule: spacing is evaluated against the already
                // resolved prefix before any terrain score can affect the next settlement.
                if (!spacingLegal(settlement, candidate, all, resolved)) continue;

                if (executor == null) {
                    candidates.add(new Candidate(
                            candidate, null,
                            sampleTerrain(candidate, spec.sampleRadius(), terrainCache)));
                } else {
                    Future<TerrainStats> future = executor.submit(
                            () -> sampleTerrain(candidate, spec.sampleRadius(), terrainCache));
                    candidates.add(new Candidate(candidate, future, null));
                }
            }
        }

        SimPosition best = origin;
        double bestScore = Double.POSITIVE_INFINITY;
        // Futures are deliberately consumed in the original candidate order. Parallel execution
        // therefore cannot change equal-score tie-breaking or seed determinism.
        for (Candidate candidate : candidates) {
            TerrainStats terrain = candidate.terrain();
            SimPosition position = candidate.position();

            double displacement = position.distanceTo(origin);
            double parentPenalty = parentDistancePenalty(
                    settlement, position, byId, resolved);
            double excessWater = Math.max(
                    0.0, terrain.waterFraction() - spec.maxWaterFraction());
            double excessRelief = Math.max(
                    0, terrain.relief() - spec.preferredRelief());
            double altitudePenalty =
                    Math.max(0.0, terrain.averageGround() - 170.0) * 1.5;

            double score =
                    terrain.waterFraction() * 3800.0
                            + excessWater * 8000.0
                            + terrain.relief() * 42.0
                            + excessRelief * 80.0
                            + altitudePenalty
                            + displacement * 1.35
                            + parentPenalty * 1.8;
            if (score < bestScore) {
                bestScore = score;
                best = position;
            }
        }
        return best;
    }

    private static boolean spacingLegal(
            StarterCivilizationLayoutPlanner.SettlementPlan settlement,
            SimPosition candidate,
            List<StarterCivilizationLayoutPlanner.SettlementPlan> all,
            Map<Long, SimPosition> resolved) {
        for (var other : all) {
            if (other.id() == settlement.id()) continue;
            SimPosition otherPosition = resolved.getOrDefault(other.id(), other.position());
            double floor = SettlementSpacingPolicy.minimumDistance(
                    settlement.role(), other.role());
            if (candidate.distanceTo(otherPosition) + 1.0e-9 < floor) return false;
        }
        return true;
    }

    private static double parentDistancePenalty(
            StarterCivilizationLayoutPlanner.SettlementPlan settlement,
            SimPosition candidate,
            Map<Long, StarterCivilizationLayoutPlanner.SettlementPlan> byId,
            Map<Long, SimPosition> resolved) {
        if (settlement.parentSettlementId() <= 0) return 0.0;
        var parent = byId.get(settlement.parentSettlementId());
        if (parent == null) return 0.0;
        SimPosition parentPosition = resolved.getOrDefault(parent.id(), parent.position());
        double original = settlement.position().distanceTo(parent.position());
        double next = candidate.distanceTo(parentPosition);
        return Math.abs(next - original);
    }

    private static TerrainStats sampleTerrain(
            SimPosition center,
            int radius,
            StarterGeneratorTerrainCache terrainCache) {
        int cx = (int) Math.round(center.x());
        int cz = (int) Math.round(center.z());
        int half = Math.max(8, radius / 2);
        int[] offsets = {-radius, -half, 0, half, radius};

        int water = 0;
        int count = 0;
        int minGround = Integer.MAX_VALUE;
        int maxGround = Integer.MIN_VALUE;
        long totalGround = 0L;
        for (int dx : offsets) {
            for (int dz : offsets) {
                int x = cx + dx;
                int z = cz + dz;
                int surface = terrainCache.surfaceY(x, z);
                int floor = terrainCache.groundY(x, z);
                if (surface > floor + 1) water++;
                minGround = Math.min(minGround, floor);
                maxGround = Math.max(maxGround, floor);
                totalGround += floor;
                count++;
            }
        }
        return new TerrainStats(
                water / (double) Math.max(1, count),
                Math.max(0, maxGround - minGround),
                totalGround / (double) Math.max(1, count));
    }

    private static SearchSpec searchSpec(SettlementRole role) {
        return switch (role) {
            case CAPITAL -> new SearchSpec(384, 64, 96, 0.12, 10);
            case CITY -> new SearchSpec(320, 64, 84, 0.15, 12);
            case TOWN -> new SearchSpec(256, 48, 72, 0.18, 14);
            case VILLAGE -> new SearchSpec(192, 48, 52, 0.22, 16);
            case HAMLET -> new SearchSpec(128, 32, 36, 0.28, 18);
            case SPECIAL -> new SearchSpec(0, 1, 24, 1.0, 64);
        };
    }

    private static int rolePriority(SettlementRole role) {
        return switch (role) {
            case CAPITAL -> 0;
            case CITY -> 1;
            case TOWN -> 2;
            case VILLAGE -> 3;
            case HAMLET -> 4;
            case SPECIAL -> 5;
        };
    }

    private static double unitAngle(long mixed) {
        long bits = (mixed >>> 11) & 0x1FFFFFL;
        return bits / (double) 0x1FFFFF * Math.PI * 2.0;
    }

    private static long mix(long x) {
        x ^= x >>> 33;
        x *= 0xff51afd7ed558ccdl;
        x ^= x >>> 33;
        x *= 0xc4ceb9fe1a85ec53l;
        return x ^ (x >>> 33);
    }
}
