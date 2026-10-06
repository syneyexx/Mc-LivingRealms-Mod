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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Main-thread terrain refinement for the pure strategic starter layout.
 *
 * <p>The pure planner remains the authority for identity, hierarchy and role-aware spacing. This
 * resolver only picks a bounded nearby physical center using the active ChunkGenerator; it never
 * requests chunks and publishes a new immutable layout before worker-thread worldgen begins.</p>
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

    private static final int ANGLES_PER_RING = 12;

    private StarterSettlementTerrainResolver() {}

    public static Resolution resolve(
            ServerLevel level,
            StarterCivilizationLayoutPlanner.Layout source) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(source, "source");

        var chunkSource = level.getChunkSource();
        var generator = chunkSource.getGenerator();
        var randomState = chunkSource.randomState();
        Map<Long, Integer> surfaceCache = new HashMap<>();
        Map<Long, Integer> floorCache = new HashMap<>();

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

        Map<Long, SimPosition> resolved = new HashMap<>();
        int moved = 0;
        for (var settlement : order) {
            if (settlement.role() == SettlementRole.SPECIAL) {
                resolved.put(settlement.id(), settlement.position());
                continue;
            }
            SearchSpec spec = searchSpec(settlement.role());
            SimPosition chosen = choose(
                    source.worldSeed(), settlement, all, byId, resolved, spec,
                    level, generator, randomState, surfaceCache, floorCache);
            resolved.put(settlement.id(), chosen);
            if (chosen.distanceTo(settlement.position()) > 0.5) moved++;
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

    private static SimPosition choose(
            long seed,
            StarterCivilizationLayoutPlanner.SettlementPlan settlement,
            List<StarterCivilizationLayoutPlanner.SettlementPlan> all,
            Map<Long, StarterCivilizationLayoutPlanner.SettlementPlan> byId,
            Map<Long, SimPosition> resolved,
            SearchSpec spec,
            ServerLevel level,
            net.minecraft.world.level.chunk.ChunkGenerator generator,
            net.minecraft.world.level.levelgen.RandomState randomState,
            Map<Long, Integer> surfaceCache,
            Map<Long, Integer> floorCache) {
        SimPosition origin = settlement.position();
        SimPosition best = origin;
        double bestScore = Double.POSITIVE_INFINITY;

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

                if (!spacingLegal(settlement, candidate, all, resolved)) continue;
                TerrainStats terrain = sampleTerrain(
                        candidate, spec.sampleRadius(), level, generator, randomState,
                        surfaceCache, floorCache);

                double displacement = candidate.distanceTo(origin);
                double parentPenalty = parentDistancePenalty(
                        settlement, candidate, byId, resolved);
                double excessWater = Math.max(
                        0.0, terrain.waterFraction() - spec.maxWaterFraction());
                double excessRelief = Math.max(
                        0, terrain.relief() - spec.preferredRelief());
                double altitudePenalty = Math.max(0.0, terrain.averageGround() - 170.0) * 1.5;

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
                    best = candidate;
                }
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
            ServerLevel level,
            net.minecraft.world.level.chunk.ChunkGenerator generator,
            net.minecraft.world.level.levelgen.RandomState randomState,
            Map<Long, Integer> surfaceCache,
            Map<Long, Integer> floorCache) {
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
                long key = pack(x, z);
                int surface = surfaceCache.computeIfAbsent(key, ignored ->
                        generator.getBaseHeight(
                                x, z, Heightmap.Types.WORLD_SURFACE_WG, level, randomState) - 1);
                int floor = floorCache.computeIfAbsent(key, ignored ->
                        generator.getBaseHeight(
                                x, z, Heightmap.Types.OCEAN_FLOOR_WG, level, randomState) - 1);
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

    private static long pack(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }
}
