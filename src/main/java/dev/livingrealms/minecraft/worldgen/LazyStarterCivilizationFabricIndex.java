package dev.livingrealms.minecraft.worldgen;

import dev.livingrealms.minecraft.LivingRealmsSavedData;
import dev.livingrealms.sim.construction.BlockPlacement;
import dev.livingrealms.sim.construction.ConstructionIntent;
import dev.livingrealms.sim.construction.StructureBlueprint;
import dev.livingrealms.sim.construction.StructureBlueprintFactory;
import dev.livingrealms.sim.construction.StructureRole;
import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.world.SimPosition;
import dev.livingrealms.sim.world.StarterCivilizationLayoutPlanner;
import dev.livingrealms.sim.worldgen.SettlementInitialWorldgenPlan;
import dev.livingrealms.sim.worldgen.StarterCivilizationFabricIndex;
import dev.livingrealms.sim.worldgen.StarterRoadsideSitePlanner;
import dev.livingrealms.sim.worldgen.StarterWorldgenCompletion;
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
    private static final int RESOLUTION_HALO_BLOCKS = 256;

    public record ResolvedSettlement(
            StarterCivilizationLayoutPlanner.RealmPlan realm,
            StarterCivilizationLayoutPlanner.SettlementPlan strategic,
            SettlementInitialWorldgenPlan physical
    ) {}

    private record SettlementRef(
            StarterCivilizationLayoutPlanner.RealmPlan realm,
            StarterCivilizationLayoutPlanner.SettlementPlan settlement
    ) {}

    private record BlueprintKey(long settlementId, String intentKey) {}

    private record IndexedIntent(
            SettlementInitialWorldgenPlan physical,
            ConstructionIntent intent
    ) {}

    private final net.minecraft.server.level.ServerLevel level;
    private final LivingRealmsSavedData data;
    private final StarterCivilizationLayoutPlanner.Layout source;
    private final StarterGeneratorTerrainCache terrainCache;
    private final List<SettlementRef> settlements;
    private final Map<Long, SettlementRef> settlementById;
    private final List<StarterRoadsideSitePlanner.SitePlan> roadsideSites;
    private final Map<Long, List<StarterRoadsideSitePlanner.SitePlan>> roadsideByRoute;
    private final java.util.Set<Long> resolvedRoadsideSites = ConcurrentHashMap.newKeySet();

    private final ConcurrentHashMap<Long, FutureTask<ResolvedSettlement>> resolutionTasks =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<BlueprintKey, StructureBlueprint> blueprintCache =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, CopyOnWriteArrayList<IndexedIntent>>
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
        Map<Long, List<StarterRoadsideSitePlanner.SitePlan>> groupedRoadside = new HashMap<>();
        for (StarterRoadsideSitePlanner.SitePlan site : roadsideSites) {
            groupedRoadside.computeIfAbsent(
                    site.relatedRouteId(), ignored -> new ArrayList<>()).add(site);
        }
        Map<Long, List<StarterRoadsideSitePlanner.SitePlan>> frozenRoadside = new HashMap<>();
        for (var entry : groupedRoadside.entrySet()) {
            frozenRoadside.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        this.roadsideByRoute = Map.copyOf(frozenRoadside);
    }

    public StarterCivilizationFabricIndex.ChunkSlice query(int chunkX, int chunkZ) {
        resolveNearby(chunkX, chunkZ);
        long key = pack(chunkX, chunkZ);
        return new StarterCivilizationFabricIndex.ChunkSlice(
                settlementFabricForChunk(chunkX, chunkZ),
                snapshot(urbanByChunk.get(key)),
                snapshot(roadsideByChunk.get(key)));
    }

    private List<StarterCivilizationFabricIndex.SettlementFabric> settlementFabricForChunk(
            int chunkX,
            int chunkZ) {
        List<IndexedIntent> indexed = snapshot(settlementByChunk.get(pack(chunkX, chunkZ)));
        if (indexed.isEmpty()) return List.of();

        List<StarterCivilizationFabricIndex.SettlementFabric> out =
                new ArrayList<>(indexed.size());
        for (IndexedIntent ref : indexed) {
            SettlementInitialWorldgenPlan physical = ref.physical();
            ConstructionIntent intent = ref.intent();
            BlueprintKey blueprintKey =
                    new BlueprintKey(physical.settlementId(), intent.key());
            StructureBlueprint blueprint = blueprintCache.computeIfAbsent(
                    blueprintKey,
                    ignored -> StructureBlueprintFactory.create(
                            intent, physical.architecture()));
            out.add(new StarterCivilizationFabricIndex.SettlementFabric(
                    physical, intent, blueprint));
        }
        return List.copyOf(out);
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

        SettlementInitialWorldgenPlan physical =
                SettlementInitialWorldgenPlan.buildOne(resolvedRealm, resolved);

        // Canonical mutation remains on the server thread. Relocation and exact day-zero WORLDGEN
        // receipts are adopted together only when this settlement actually enters chunk worldgen.
        level.getServer().execute(() -> {
            boolean dirty = false;
            var canonical = data.state().findSettlement(resolved.id()).orElse(null);
            if (canonical != null && !canonical.position().equals(resolved.position())) {
                canonical.alignStarterWorldgenPosition(resolved.position());
                dirty = true;
            }
            if (StarterWorldgenCompletion.adoptPlannedBaseline(
                    data.state(), List.of(physical)) > 0) {
                dirty = true;
            }
            if (dirty) data.setDirty();
        });

        // Index lightweight intent references once. Chunk queries no longer rescan every resolved
        // settlement and every one of its intents while the player explores the world. Blueprints
        // themselves remain lazy and are created only for the chunk that actually requests them.
        indexSettlementIntents(physical);

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

    private void indexSettlementIntents(SettlementInitialWorldgenPlan physical) {
        for (ConstructionIntent intent : physical.intents()) {
            int minX;
            int minZ;
            int maxX;
            int maxZ;
            if (intent.hasPath()) {
                double pathMinX = intent.path().stream()
                        .mapToDouble(SimPosition::x).min().orElse(intent.center().x());
                double pathMaxX = intent.path().stream()
                        .mapToDouble(SimPosition::x).max().orElse(intent.center().x());
                double pathMinZ = intent.path().stream()
                        .mapToDouble(SimPosition::z).min().orElse(intent.center().z());
                double pathMaxZ = intent.path().stream()
                        .mapToDouble(SimPosition::z).max().orElse(intent.center().z());
                int margin = Math.max(2, intent.width() / 2 + 2);
                minX = (int) Math.floor(pathMinX) - margin;
                maxX = (int) Math.ceil(pathMaxX) + margin;
                minZ = (int) Math.floor(pathMinZ) - margin;
                maxZ = (int) Math.ceil(pathMaxZ) + margin;
            } else {
                int turns = Math.floorMod(intent.rotationQuarterTurns(), 4);
                int width = (turns & 1) == 0 ? intent.width() : intent.depth();
                int depth = (turns & 1) == 0 ? intent.depth() : intent.width();
                int cx = (int) Math.round(intent.center().x());
                int cz = (int) Math.round(intent.center().z());
                int margin = 16;
                minX = cx - width / 2 - margin;
                maxX = cx + (width - 1) / 2 + margin;
                minZ = cz - depth / 2 - margin;
                maxZ = cz + (depth - 1) / 2 + margin;
            }
            addChunkRectangle(
                    settlementByChunk,
                    new IndexedIntent(physical, intent),
                    minX, minZ, maxX, maxZ);
        }
    }

    void indexRoadsideForRouteWindow(
            long routeId,
            List<StarterRegionalRouteGeometryIndex.PlannedPoint> points,
            int minX, int minZ, int maxX, int maxZ) {
        if (points == null || points.isEmpty()) return;

        List<StarterRoadsideSitePlanner.SitePlan> sites =
                roadsideByRoute.getOrDefault(routeId, List.of());
        for (StarterRoadsideSitePlanner.SitePlan site : sites) {
            int sx = (int) Math.floor(site.position().x());
            int sz = (int) Math.floor(site.position().z());
            // A site is owned by the deterministic route window containing its strategic anchor.
            // The margin allows the local terrain corridor to pull the road modestly sideways.
            int margin = 48;
            if (sx < minX - margin || sx > maxX + margin
                    || sz < minZ - margin || sz > maxZ + margin) {
                continue;
            }
            if (!resolvedRoadsideSites.add(site.stableSiteId())) continue;

            StarterRoadsideSitePlanner.SitePlan resolved =
                    alignRoadsideSite(site, points);
            StarterCivilizationFabricIndex.RoadsideFabric fabric =
                    new StarterCivilizationFabricIndex.RoadsideFabric(resolved);
            int x = (int) Math.floor(resolved.position().x());
            int z = (int) Math.floor(resolved.position().z());
            addChunkRectangle(
                    roadsideByChunk, fabric, x - 3, z - 3, x + 3, z + 3);

            if (!resolved.position().equals(site.position())) {
                level.getServer().execute(() -> {
                    var canonical = data.state().findRoadsideSite(resolved.stableSiteId()).orElse(null);
                    if (canonical != null && !canonical.position().equals(resolved.position())) {
                        canonical.relocate(resolved.position());
                        data.setDirty();
                    }
                });
            }
        }
    }

    private static StarterRoadsideSitePlanner.SitePlan alignRoadsideSite(
            StarterRoadsideSitePlanner.SitePlan site,
            List<StarterRegionalRouteGeometryIndex.PlannedPoint> points) {
        if (points == null || points.isEmpty()) return site;

        StarterRegionalRouteGeometryIndex.PlannedPoint nearest = null;
        double best = Double.POSITIVE_INFINITY;
        for (StarterRegionalRouteGeometryIndex.PlannedPoint point : points) {
            double dx = site.position().x() - point.x();
            double dz = site.position().z() - point.z();
            double d2 = dx * dx + dz * dz;
            if (d2 < best) {
                best = d2;
                nearest = point;
            }
        }
        if (nearest == null) return site;

        int x = nearest.x();
        int z = nearest.z();
        if (!nearest.water()) {
            int nx = nearest.dz() == 0 ? 0 : Integer.signum(nearest.dz());
            int nz = nearest.dx() == 0 ? 0 : -Integer.signum(nearest.dx());
            if (nx == 0 && nz == 0) nx = 1;
            int side = (site.stableSiteId() & 1L) == 0L ? 1 : -1;
            x += nx * 4 * side;
            z += nz * 4 * side;
        }

        return new StarterRoadsideSitePlanner.SitePlan(
                site.stableSiteId(),
                site.stableKey(),
                site.type(),
                new SimPosition(x, z),
                site.relatedSettlementId(),
                site.relatedRouteId());
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
