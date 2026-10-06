package dev.livingrealms.minecraft.worldgen;

import dev.livingrealms.sim.construction.BlockPlacement;
import dev.livingrealms.sim.construction.ConstructionIntent;
import dev.livingrealms.sim.construction.StructureBlueprint;
import dev.livingrealms.sim.construction.StructureBlueprintFactory;
import dev.livingrealms.sim.world.SimPosition;
import dev.livingrealms.sim.worldgen.WizardTreesInitialWorldgenPlan;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Immutable direct chunk lookup for the day-zero Wizard Trees underground fabric.
 *
 * <p>Terrain resolution happens once on the server thread from ChunkGenerator base-height queries.
 * Worker threads receive only frozen centers, bases and blueprints and never access SavedData.</p>
 */
public final class WizardTreesWorldgenIndex {
    public record WizardFabric(
            String stableKey,
            long factionId,
            long settlementId,
            ConstructionIntent intent,
            StructureBlueprint blueprint,
            int baseY
    ) {
        public WizardFabric {
            if (stableKey == null || stableKey.isBlank()) throw new IllegalArgumentException("stableKey");
            if (factionId <= 0 || settlementId <= 0) throw new IllegalArgumentException("owner ids");
            intent = Objects.requireNonNull(intent, "intent");
            blueprint = Objects.requireNonNull(blueprint, "blueprint");
        }
    }

    public record ChunkSlice(List<WizardFabric> fabric) {
        public static final ChunkSlice EMPTY = new ChunkSlice(List.of());

        public ChunkSlice {
            fabric = List.copyOf(Objects.requireNonNull(fabric, "fabric"));
        }

        public boolean isEmpty() {
            return fabric.isEmpty();
        }
    }

    private record Resolved(ConstructionIntent intent, int baseY) {}

    private final Map<Long, ChunkSlice> byChunk;
    private final List<WizardFabric> allFabric;

    private WizardTreesWorldgenIndex(Map<Long, ChunkSlice> byChunk, List<WizardFabric> allFabric) {
        this.byChunk = Map.copyOf(byChunk);
        this.allFabric = List.copyOf(allFabric);
    }

    public static WizardTreesWorldgenIndex empty() {
        return new WizardTreesWorldgenIndex(Map.of(), List.of());
    }

    public static WizardTreesWorldgenIndex build(
            ServerLevel level,
            List<WizardTreesInitialWorldgenPlan.SettlementPlan> settlements) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(settlements, "settlements");
        if (settlements.isEmpty()) return empty();

        Map<Long, List<WizardFabric>> mutable = new HashMap<>();
        List<WizardFabric> all = new ArrayList<>();

        for (WizardTreesInitialWorldgenPlan.SettlementPlan settlement : settlements) {
            for (ConstructionIntent original : settlement.intents()) {
                Resolved resolved = resolve(level, original);
                if (resolved == null) continue;
                StructureBlueprint blueprint = StructureBlueprintFactory.create(resolved.intent());
                WizardFabric fabric = new WizardFabric(
                        settlement.stableKey() + "/" + original.key(),
                        settlement.factionId(),
                        settlement.settlementId(),
                        resolved.intent(),
                        blueprint,
                        resolved.baseY());
                all.add(fabric);

                Bounds bounds = horizontalBounds(resolved.intent(), blueprint);
                int minChunkX = Math.floorDiv(bounds.minX(), 16);
                int maxChunkX = Math.floorDiv(bounds.maxX(), 16);
                int minChunkZ = Math.floorDiv(bounds.minZ(), 16);
                int maxChunkZ = Math.floorDiv(bounds.maxZ(), 16);
                for (int cx = minChunkX; cx <= maxChunkX; cx++) {
                    for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                        mutable.computeIfAbsent(pack(cx, cz), ignored -> new ArrayList<>()).add(fabric);
                    }
                }
            }
        }

        Map<Long, ChunkSlice> frozen = new HashMap<>(Math.max(16, mutable.size() * 2));
        for (Map.Entry<Long, List<WizardFabric>> entry : mutable.entrySet()) {
            frozen.put(entry.getKey(),
                    new ChunkSlice(List.copyOf(new LinkedHashSet<>(entry.getValue()))));
        }
        return new WizardTreesWorldgenIndex(frozen, all);
    }

    public ChunkSlice query(int chunkX, int chunkZ) {
        return byChunk.getOrDefault(pack(chunkX, chunkZ), ChunkSlice.EMPTY);
    }

    public List<WizardFabric> allFabric() {
        return allFabric;
    }

    public int indexedChunkCount() {
        return byChunk.size();
    }

    private static Resolved resolve(ServerLevel level, ConstructionIntent original) {
        int[][] offsets = {
                {0, 0}, {16, 0}, {-16, 0}, {0, 16}, {0, -16},
                {28, 28}, {28, -28}, {-28, 28}, {-28, -28}
        };
        var chunkSource = level.getChunkSource();
        var generator = chunkSource.getGenerator();
        var randomState = chunkSource.randomState();

        SimPosition bestCenter = null;
        int bestBase = 0;
        double bestScore = Double.POSITIVE_INFINITY;
        for (int[] offset : offsets) {
            int x = (int) Math.round(original.center().x() + offset[0]);
            int z = (int) Math.round(original.center().z() + offset[1]);
            int surface = generator.getBaseHeight(
                    x, z, Heightmap.Types.WORLD_SURFACE_WG, level, randomState) - 1;
            int base = Math.max(level.getMinBuildHeight() + 8, surface - 18);
            if (base + 10 >= surface) continue;

            double score = -surface * 4.0 + Math.hypot(offset[0], offset[1]);
            if (score < bestScore) {
                bestScore = score;
                bestCenter = new SimPosition(
                        original.center().x() + offset[0],
                        original.center().z() + offset[1]);
                bestBase = base;
            }
        }
        return bestCenter == null ? null : new Resolved(original.withCenter(bestCenter), bestBase);
    }

    private record Bounds(int minX, int minZ, int maxX, int maxZ) {}

    private static Bounds horizontalBounds(
            ConstructionIntent intent,
            StructureBlueprint blueprint) {
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
        return new Bounds(cx + minX, cz + minZ, cx + maxX, cz + maxZ);
    }

    private static int[] rotate(int x, int z, int turns) {
        return switch (turns) {
            case 0 -> new int[]{x, z};
            case 1 -> new int[]{-z, x};
            case 2 -> new int[]{-x, -z};
            default -> new int[]{z, -x};
        };
    }

    private static long pack(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) ^ (chunkZ & 0xffffffffL);
    }
}
