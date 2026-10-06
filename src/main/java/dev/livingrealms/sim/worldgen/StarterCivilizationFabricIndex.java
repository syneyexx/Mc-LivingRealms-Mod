package dev.livingrealms.sim.worldgen;

import dev.livingrealms.sim.construction.BlockPlacement;
import dev.livingrealms.sim.construction.ConstructionIntent;
import dev.livingrealms.sim.construction.StructureBlueprint;
import dev.livingrealms.sim.construction.StructureBlueprintFactory;
import dev.livingrealms.sim.construction.StructureRole;
import dev.livingrealms.sim.world.StarterCivilizationLayoutPlanner;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable direct chunk -> day-zero civilization fabric lookup.
 *
 * <p>The expensive whole-layout walk happens once when the index is built. Worldgen queries are a
 * single packed-chunk map lookup and never scan all starter settlements or routes.</p>
 */
public final class StarterCivilizationFabricIndex {
    public record SettlementFabric(SettlementInitialWorldgenPlan settlement, ConstructionIntent intent) {
        public SettlementFabric {
            settlement = Objects.requireNonNull(settlement, "settlement");
            intent = Objects.requireNonNull(intent, "intent");
        }
    }

    public record RouteFabric(StarterRegionalRoutePlanner.RoutePlan route) {
        public RouteFabric {
            route = Objects.requireNonNull(route, "route");
        }
    }

    public record ChunkSlice(List<SettlementFabric> settlementFabric, List<RouteFabric> routes) {
        public static final ChunkSlice EMPTY = new ChunkSlice(List.of(), List.of());

        public ChunkSlice {
            settlementFabric = List.copyOf(Objects.requireNonNull(settlementFabric, "settlementFabric"));
            routes = List.copyOf(Objects.requireNonNull(routes, "routes"));
        }

        public boolean isEmpty() {
            return settlementFabric.isEmpty() && routes.isEmpty();
        }
    }

    private record MutableSlice(List<SettlementFabric> settlements, List<RouteFabric> routes) {
        MutableSlice() { this(new ArrayList<>(), new ArrayList<>()); }
    }

    private final StarterCivilizationLayoutPlanner.Layout layout;
    private final List<SettlementInitialWorldgenPlan> settlements;
    private final List<StarterRegionalRoutePlanner.RoutePlan> routes;
    private final Map<Long, ChunkSlice> byChunk;
    private final int indexedReferences;

    private StarterCivilizationFabricIndex(
            StarterCivilizationLayoutPlanner.Layout layout,
            List<SettlementInitialWorldgenPlan> settlements,
            List<StarterRegionalRoutePlanner.RoutePlan> routes,
            Map<Long, ChunkSlice> byChunk,
            int indexedReferences) {
        this.layout = Objects.requireNonNull(layout, "layout");
        this.settlements = List.copyOf(settlements);
        this.routes = List.copyOf(routes);
        this.byChunk = Map.copyOf(byChunk);
        this.indexedReferences = indexedReferences;
    }

    public static StarterCivilizationFabricIndex build(StarterCivilizationLayoutPlanner.Layout layout) {
        Objects.requireNonNull(layout, "layout");
        List<SettlementInitialWorldgenPlan> settlements = SettlementInitialWorldgenPlan.buildAll(layout);
        List<StarterRegionalRoutePlanner.RoutePlan> routes = StarterRegionalRoutePlanner.plan(layout);
        Map<Long, MutableSlice> mutable = new HashMap<>();

        for (SettlementInitialWorldgenPlan settlement : settlements) {
            for (ConstructionIntent intent : settlement.intents()) {
                SettlementFabric fabric = new SettlementFabric(settlement, intent);
                Bounds bounds = horizontalBounds(intent, settlement);
                int minChunkX = Math.floorDiv(bounds.minX(), 16);
                int maxChunkX = Math.floorDiv(bounds.maxX(), 16);
                int minChunkZ = Math.floorDiv(bounds.minZ(), 16);
                int maxChunkZ = Math.floorDiv(bounds.maxZ(), 16);
                for (int cx = minChunkX; cx <= maxChunkX; cx++) {
                    for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                        mutable.computeIfAbsent(pack(cx, cz), ignored -> new MutableSlice()).settlements().add(fabric);
                    }
                }
            }
        }

        for (StarterRegionalRoutePlanner.RoutePlan route : routes) {
            RouteFabric fabric = new RouteFabric(route);
            for (long key : routeChunks(route)) {
                mutable.computeIfAbsent(key, ignored -> new MutableSlice()).routes().add(fabric);
            }
        }

        int refs = 0;
        Map<Long, ChunkSlice> frozen = new HashMap<>(mutable.size() * 2);
        for (Map.Entry<Long, MutableSlice> entry : mutable.entrySet()) {
            MutableSlice slice = entry.getValue();
            List<SettlementFabric> settlementRefs = dedupeSettlements(slice.settlements());
            List<RouteFabric> routeRefs = dedupeRoutes(slice.routes());
            refs += settlementRefs.size() + routeRefs.size();
            frozen.put(entry.getKey(), new ChunkSlice(settlementRefs, routeRefs));
        }
        return new StarterCivilizationFabricIndex(layout, settlements, routes, frozen, refs);
    }

    public ChunkSlice query(int chunkX, int chunkZ) {
        return byChunk.getOrDefault(pack(chunkX, chunkZ), ChunkSlice.EMPTY);
    }

    public StarterCivilizationLayoutPlanner.Layout layout() { return layout; }
    public List<SettlementInitialWorldgenPlan> settlements() { return settlements; }
    public List<StarterRegionalRoutePlanner.RoutePlan> routes() { return routes; }
    public int indexedChunkCount() { return byChunk.size(); }
    public int indexedReferences() { return indexedReferences; }

    private static List<SettlementFabric> dedupeSettlements(List<SettlementFabric> input) {
        LinkedHashSet<SettlementFabric> set = new LinkedHashSet<>(input);
        return List.copyOf(set);
    }

    private static List<RouteFabric> dedupeRoutes(List<RouteFabric> input) {
        LinkedHashSet<RouteFabric> set = new LinkedHashSet<>(input);
        return List.copyOf(set);
    }

    private record Bounds(int minX, int minZ, int maxX, int maxZ) {}

    private static Bounds horizontalBounds(
            ConstructionIntent intent,
            SettlementInitialWorldgenPlan settlement) {
        if (intent.role() == StructureRole.ROAD && intent.hasPath()) {
            double minX = intent.path().stream().mapToDouble(p -> p.x()).min().orElse(intent.center().x());
            double maxX = intent.path().stream().mapToDouble(p -> p.x()).max().orElse(intent.center().x());
            double minZ = intent.path().stream().mapToDouble(p -> p.z()).min().orElse(intent.center().z());
            double maxZ = intent.path().stream().mapToDouble(p -> p.z()).max().orElse(intent.center().z());
            int margin = intent.width() / 2 + 2;
            return new Bounds((int) Math.floor(minX) - margin, (int) Math.floor(minZ) - margin,
                    (int) Math.ceil(maxX) + margin, (int) Math.ceil(maxZ) + margin);
        }

        StructureBlueprint blueprint = StructureBlueprintFactory.create(intent, settlement.architecture());
        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
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
        return new Bounds(cx + minX - 2, cz + minZ - 2, cx + maxX + 2, cz + maxZ + 2);
    }

    private static int[] rotate(int x, int z, int turns) {
        return switch (turns) {
            case 0 -> new int[]{x, z};
            case 1 -> new int[]{-z, x};
            case 2 -> new int[]{-x, -z};
            default -> new int[]{z, -x};
        };
    }

    /**
     * Bounded corridor indexing. Eight-block samples plus a four-block cross-width halo guarantee
     * every chunk touched by the straight deterministic starter centerline is indexed.
     */
    private static Set<Long> routeChunks(StarterRegionalRoutePlanner.RoutePlan route) {
        Set<Long> out = new HashSet<>();
        double dx = route.to().x() - route.from().x();
        double dz = route.to().z() - route.from().z();
        double distance = Math.hypot(dx, dz);
        int steps = Math.max(1, (int) Math.ceil(distance / 8.0));
        double len = Math.max(1.0e-9, distance);
        double nx = -dz / len, nz = dx / len;
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            double x = route.from().x() + dx * t;
            double z = route.from().z() + dz * t;
            addChunk(out, x, z);
            addChunk(out, x + nx * 4.0, z + nz * 4.0);
            addChunk(out, x - nx * 4.0, z - nz * 4.0);
        }
        return out;
    }

    private static void addChunk(Set<Long> into, double x, double z) {
        into.add(pack(Math.floorDiv((int) Math.floor(x), 16), Math.floorDiv((int) Math.floor(z), 16)));
    }

    private static long pack(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) ^ (chunkZ & 0xffffffffL);
    }
}
