package dev.livingrealms.minecraft.worldgen;

import dev.livingrealms.minecraft.LivingRealmsSavedData;
import dev.livingrealms.sim.construction.BlockPlacement;
import dev.livingrealms.sim.construction.ConstructionIntent;
import dev.livingrealms.sim.construction.StructureBlueprint;
import dev.livingrealms.sim.construction.StructureBlueprintFactory;
import dev.livingrealms.sim.construction.StructureRole;
import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.world.StarterCivilizationLayoutPlanner;
import dev.livingrealms.sim.worldgen.SettlementInitialWorldgenPlan;
import dev.livingrealms.sim.worldgen.StarterCivilizationFabricIndex;
import dev.livingrealms.sim.worldgen.StarterRoadsideSitePlanner;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

/**
 * Chunk-lazy starter settlement fabric.
 *
 * <p>Level load stores only the authored strategic layout. A settlement is terrain-refined and its
 * heavy blueprints are derived only when a generating chunk enters that settlement's conservative
 * influence halo. Exact chunk references are then cached for the server lifetime. This keeps fresh
 * world creation out of a global 267-settlement terrain/blueprint pass.</p>
 */
public final class LazyStarterCivilizationFabricIndex {
    private static final int RESOLUTION_HALO_BLOCKS = 768;

    public record ResolvedSettlement(
            StarterCivilizationLayoutPlanner.RealmPlan realm,
            StarterCivilizationLayoutPlanner.SettlementPlan strategic,
            SettlementInitialWorldgenPlan physical
    ) {}

    private record SettlementRef(
            StarterCivilizationLayoutPlanner.RealmPlan realm,
            StarterCivilizationLayoutPlanner.SettlementPlan settlement
    ) {}

    private record Bounds(int minX, int minZ, int maxX, int maxZ) {}

    private final net.minecraft.server.level.ServerLevel level;
    private final LivingRealmsSavedData data;
    private final StarterCivilizationLayoutPlanner.Layout source;
    private final StarterGeneratorTerrainCache terrainCache;
    private final List<SettlementRef> settlements;
    private final Map<Long, SettlementRef> settlementById;
    private final List<StarterRoadsideSitePlanner.SitePlan> roadsideSites;

    private final ConcurrentHashMap<Long, FutureTask<ResolvedSettlement>> resolutionTasks =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, CopyOnWriteArrayList<StarterCivilizationFabricIndex.SettlementFabric>>
            settlementByChunk = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, CopyOnWriteArrayList<StarterCivilizationFabricIndex.UrbanCoreFabric>>
            urbanByChunk = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, CopyOnWriteArrayList<StarterCivilizationFabricIndex.RoadsideFabric>>
            roadsideByChunk = new ConcurrentHashMap<>();

    public LazyStarterCivilizationFabricIndex(
            net.minecraft.server.level.ServerLevel level,
            LivingRealmsSavedData data,
            StarterCivilizationLayoutPlanner.Layout source,
            StarterGeneratorTerrainCache terrainCache) {
        this.level = Objects.requireNonNull(level, "level");
        this.data = Objects.requireNonNull(data, "data");
        this.source = Objects.requireNonNull(source, "source");
        this.terrainCache = Objects.requireNonNull(terrainCache, "terrainCache");

        List<SettlementRef> refs = new ArrayList<>();
        Map<Long, SettlementRef> byId = new HashMap<>();
        for (StarterCivilizationLayoutPlanner.RealmPlan realm : source.realms()) {
            for (StarterCivilizationLayoutPlanner.SettlementPlan settlement : realm.settlements()) {
                SettlementRef ref = new SettlementRef(realm, settlement);
                refs.add(ref);
                byId.put(settlement.id(), ref);
            }
        }
        this.settlements = List.copyOf(refs);
        this.settlementById = Map.copyOf(byId);

        this.roadsideSites = StarterRoadsideSitePlanner.plan(source);
        indexRoadsideSites(this.roadsideSites);
    }

    public StarterCivilizationFabricIndex.ChunkSlice query(int chunkX, int chunkZ) {
        resolveNearby(chunkX, chunkZ);
        long key = pack(chunkX, chunkZ);
        return new StarterCivilizationFabricIndex.ChunkSlice(
                snapshot(settlementByChunk.get(key)),
                snapshot(urbanByChunk.get(key)),
                snapshot(roadsideByChunk.get(key)));
    }

    public ResolvedSettlement resolveSettlement(long settlementId) {
        SettlementRef ref = settlementById.get(settlementId);
        if (ref == null) throw new IllegalArgumentException("unknown starter settlement " + settlementId);

        FutureTask<ResolvedSettlement> task = resolutionTasks.computeIfAbsent(
                settlementId,
                ignored -> new FutureTask<>(() -> resolveAndIndex(ref)));
        task.run();
        try {
            return task.get();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "Interrupted while resolving starter settlement " + settlementId, interrupted);
        } catch (ExecutionException failed) {
            Throwable cause = failed.getCause() == null ? failed : failed.getCause();
            throw new IllegalStateException(
                    "Failed to resolve starter settlement " + settlementId, cause);
        }
    }

    public int resolvedSettlementCount() {
        int count = 0;
        for (FutureTask<ResolvedSettlement> task : resolutionTasks.values()) {
            if (task.isDone()) count++;
        }
        return count;
    }

    public int totalSettlementCount() {
        return settlements.size();
    }

    public StarterCivilizationLayoutPlanner.Layout sourceLayout() {
        return source;
    }

    public List<StarterRoadsideSitePlanner.SitePlan> roadsideSites() {
        return roadsideSites;
    }

    private void resolveNearby(int chunkX, int chunkZ) {
        int minX = chunkX << 4;
        int minZ = chunkZ << 4;
        int maxX = minX + 15;
        int maxZ = minZ + 15;

        for (SettlementRef ref : settlements) {
            var settlement = ref.settlement();
            int maxShift = StarterSettlementTerrainResolver.maxSearchRadius(settlement.role());
            int halo = maxShift + RESOLUTION_HALO_BLOCKS;
            int sx = (int) Math.round(settlement.position().x());
            int sz = (int) Math.round(settlement.position().z());
            if (sx < minX - halo || sx > maxX + halo || sz < minZ - halo || sz > maxZ + halo) {
                continue;
            }
            resolveSettlement(settlement.id());
        }
    }

    private ResolvedSettlement resolveAndIndex(SettlementRef ref) {
        StarterCivilizationLayoutPlanner.SettlementPlan resolved =
                StarterSettlementTerrainResolver.resolveOne(
                        source, ref.settlement(), terrainCache);

        StarterCivilizationLayoutPlanner.RealmPlan resolvedRealm =
                withResolvedSettlement(ref.realm(), resolved);

        if (!resolved.position().equals(ref.settlement().position())) {
            level.getServer().execute(() -> {
                var canonical = data.state().findSettlement(resolved.id()).orElse(null);
                if (canonical != null && !canonical.position().equals(resolved.position())) {
                    canonical.alignStarterWorldgenPosition(resolved.position());
                    data.setDirty();
                }
            });
        }

        SettlementInitialWorldgenPlan physical =
                SettlementInitialWorldgenPlan.buildOne(resolvedRealm, resolved);

        for (ConstructionIntent intent : physical.intents()) {
            StructureBlueprint blueprint =
                    StructureBlueprintFactory.create(intent, physical.architecture());
            StarterCivilizationFabricIndex.SettlementFabric fabric =
                    new StarterCivilizationFabricIndex.SettlementFabric(
                            physical, intent, blueprint);
            Bounds bounds = horizontalBounds(intent, blueprint);
            forEachChunk(bounds, key -> settlementByChunk
                    .computeIfAbsent(key, ignored -> new CopyOnWriteArrayList<>())
                    .addIfAbsent(fabric));
        }

        if (physical.tier().ordinal() >= Settlement.Tier.CITY.ordinal()) {
            int radius = physical.tier() == Settlement.Tier.METROPOLIS ? 96 : 72;
            StarterCivilizationFabricIndex.UrbanCoreFabric urban =
                    new StarterCivilizationFabricIndex.UrbanCoreFabric(physical, radius);
            int cx = (int) Math.round(physical.center().x());
            int cz = (int) Math.round(physical.center().z());
            addChunkRectangle(urbanByChunk, urban, cx - radius, cz - 1, cx + radius, cz + 1);
            addChunkRectangle(urbanByChunk, urban, cx - 1, cz - radius, cx + 1, cz + radius);
        }

        return new ResolvedSettlement(resolvedRealm, resolved, physical);
    }

    private void indexRoadsideSites(List<StarterRoadsideSitePlanner.SitePlan> sites) {
        for (StarterRoadsideSitePlanner.SitePlan site : sites) {
            StarterCivilizationFabricIndex.RoadsideFabric fabric =
                    new StarterCivilizationFabricIndex.RoadsideFabric(site);
            int x = (int) Math.floor(site.position().x());
            int z = (int) Math.floor(site.position().z());
            addChunkRectangle(
                    roadsideByChunk, fabric, x - 3, z - 3, x + 3, z + 3);
        }
    }

    private static StarterCivilizationLayoutPlanner.RealmPlan withResolvedSettlement(
            StarterCivilizationLayoutPlanner.RealmPlan realm,
            StarterCivilizationLayoutPlanner.SettlementPlan resolved) {
        List<StarterCivilizationLayoutPlanner.SettlementPlan> updated =
                new ArrayList<>(realm.settlements().size());
        for (StarterCivilizationLayoutPlanner.SettlementPlan settlement : realm.settlements()) {
            updated.add(settlement.id() == resolved.id() ? resolved : settlement);
        }
        return new StarterCivilizationLayoutPlanner.RealmPlan(
                realm.definition(), realm.factionId(), realm.armyId(), updated);
    }

    private static Bounds horizontalBounds(
            ConstructionIntent intent,
            StructureBlueprint blueprint) {
        if (intent.role() == StructureRole.ROAD && intent.hasPath()) {
            double minX = intent.path().stream().mapToDouble(p -> p.x())
                    .min().orElse(intent.center().x());
            double maxX = intent.path().stream().mapToDouble(p -> p.x())
                    .max().orElse(intent.center().x());
            double minZ = intent.path().stream().mapToDouble(p -> p.z())
                    .min().orElse(intent.center().z());
            double maxZ = intent.path().stream().mapToDouble(p -> p.z())
                    .max().orElse(intent.center().z());
            int margin = intent.width() / 2 + 2;
            return new Bounds(
                    (int) Math.floor(minX) - margin,
                    (int) Math.floor(minZ) - margin,
                    (int) Math.ceil(maxX) + margin,
                    (int) Math.ceil(maxZ) + margin);
        }

        int minX = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        int turns = Math.floorMod(intent.rotationQuarterTurns(), 4);
        for (BlockPlacement placement : blueprint.placements()) {
            int[] rotated = rotate(placement.dx(), placement.dz(), turns);
            minX = Math.min(minX, rotated[0]);
            maxX = Math.max(maxX, rotated[0]);
            minZ = Math.min(minZ, rotated[1]);
            maxZ = Math.max(maxZ, rotated[1]);
        }
        if (minX == Integer.MAX_VALUE) {
            minX = -intent.width() / 2;
            maxX = intent.width() / 2;
            minZ = -intent.depth() / 2;
            maxZ = intent.depth() / 2;
        }
        int cx = (int) Math.round(intent.center().x());
        int cz = (int) Math.round(intent.center().z());
        return new Bounds(
                cx + minX - 2, cz + minZ - 2,
                cx + maxX + 2, cz + maxZ + 2);
    }

    private static int[] rotate(int x, int z, int turns) {
        return switch (turns) {
            case 0 -> new int[]{x, z};
            case 1 -> new int[]{-z, x};
            case 2 -> new int[]{-x, -z};
            default -> new int[]{z, -x};
        };
    }

    private static void forEachChunk(Bounds bounds, java.util.function.LongConsumer consumer) {
        int minChunkX = Math.floorDiv(bounds.minX(), 16);
        int maxChunkX = Math.floorDiv(bounds.maxX(), 16);
        int minChunkZ = Math.floorDiv(bounds.minZ(), 16);
        int maxChunkZ = Math.floorDiv(bounds.maxZ(), 16);
        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                consumer.accept(pack(cx, cz));
            }
        }
    }

    private static <T> void addChunkRectangle(
            ConcurrentHashMap<Long, CopyOnWriteArrayList<T>> map,
            T value,
            int minX, int minZ, int maxX, int maxZ) {
        int minChunkX = Math.floorDiv(minX, 16);
        int maxChunkX = Math.floorDiv(maxX, 16);
        int minChunkZ = Math.floorDiv(minZ, 16);
        int maxChunkZ = Math.floorDiv(maxZ, 16);
        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                map.computeIfAbsent(pack(cx, cz), ignored -> new CopyOnWriteArrayList<>())
                        .addIfAbsent(value);
            }
        }
    }

    private static <T> List<T> snapshot(CopyOnWriteArrayList<T> values) {
        if (values == null || values.isEmpty()) return List.of();
        return List.copyOf(new LinkedHashSet<>(values));
    }

    private static long pack(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) ^ (chunkZ & 0xffffffffL);
    }
}
