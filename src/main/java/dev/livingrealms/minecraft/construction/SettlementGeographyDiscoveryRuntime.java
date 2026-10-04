package dev.livingrealms.minecraft.construction;

import dev.livingrealms.minecraft.LivingRealmsSavedData;
import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.faction.SettlementGeographyProfile;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Bounded Minecraft terrain sampler that authors {@link SettlementGeographyProfile} from loaded chunks.
 *
 * <p>Never force-loads chunks. Name heuristics remain only a bootstrap fallback until
 * {@code worldDiscovered=true} after enough samples exist; thereafter terrain wins.
 */
public final class SettlementGeographyDiscoveryRuntime {
    private static final double ACTIVATION_RADIUS = 720.0D;
    private static final double ACTIVATION_RADIUS_SQR = ACTIVATION_RADIUS * ACTIVATION_RADIUS;
    private static final int MAX_SETTLEMENTS_PER_TICK = 3;
    private static final int SAMPLES_PER_SETTLEMENT = 12;
    private static final int MIN_SAMPLES_FOR_DISCOVERY = 36;
    private static final int SAMPLE_RADIUS = 48;

    private static final Map<Long, SampleAccum> ACCUM = new HashMap<>();
    private static int cursor;

    private SettlementGeographyDiscoveryRuntime() {}

    public static void tick(ServerLevel level, LivingRealmsSavedData data) {
        if (level.players().isEmpty()) return;
        List<Settlement> near = new ArrayList<>();
        for (Faction faction : data.state().factions()) {
            for (Settlement settlement : faction.settlements()) {
                if (settlement.geography().worldDiscovered()) continue;
                if (!nearPlayer(level, settlement)) continue;
                near.add(settlement);
            }
        }
        if (near.isEmpty()) return;
        cursor = Math.floorMod(cursor, near.size());
        int scanned = 0;
        boolean dirty = false;
        for (int n = 0; n < near.size() && scanned < MAX_SETTLEMENTS_PER_TICK; n++) {
            Settlement settlement = near.get(Math.floorMod(cursor + n, near.size()));
            scanned++;
            if (sampleSettlement(level, settlement)) dirty = true;
        }
        cursor = Math.floorMod(cursor + Math.max(1, scanned), Math.max(1, near.size()));
        if (dirty) data.setDirty();
    }

    public static void clear() {
        ACCUM.clear();
        cursor = 0;
    }

    private static boolean sampleSettlement(ServerLevel level, Settlement settlement) {
        SampleAccum accum = ACCUM.computeIfAbsent(settlement.id(), id -> new SampleAccum());
        int cx = (int) Math.round(settlement.position().x());
        int cz = (int) Math.round(settlement.position().z());
        int taken = 0;
        // Deterministic ring offsets so progress is stable across ticks.
        int base = accum.samples;
        for (int i = 0; i < SAMPLES_PER_SETTLEMENT; i++) {
            int idx = base + i;
            double angle = (idx * 47) * 0.17D;
            int dist = 4 + (idx * 7) % SAMPLE_RADIUS;
            int x = cx + (int) Math.round(Math.cos(angle) * dist);
            int z = cz + (int) Math.round(Math.sin(angle) * dist);
            BlockPos probe = new BlockPos(x, level.getSeaLevel(), z);
            if (!level.hasChunkAt(probe)) continue;
            int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
            if (surface <= level.getMinBuildHeight() + 1) continue;
            BlockState ground = level.getBlockState(new BlockPos(x, surface, z));
            Holder<Biome> biome = level.getBiome(new BlockPos(x, surface, z));
            ResourceLocation biomeId = biome.unwrapKey().map(k -> k.location()).orElse(null);
            boolean water = !ground.getFluidState().isEmpty() || level.getFluidState(new BlockPos(x, surface, z)).is(FluidTags.WATER)
                    || level.getFluidState(new BlockPos(x, surface + 1, z)).is(FluidTags.WATER);
            boolean ocean = isOceanBiome(biomeId) || (water && surface <= level.getSeaLevel() + 1 && isOceanish(biomeId, ground));
            boolean river = isRiverBiome(biomeId) || (water && !ocean && nearbyFreshEvidence(level, x, z, surface));
            boolean forest = ground.is(BlockTags.LEAVES) || hasNearbyLeaves(level, x, surface, z) || isForestBiome(biomeId);
            boolean fertile = isFertileBiome(biomeId) || ground.is(net.minecraft.world.level.block.Blocks.GRASS_BLOCK)
                    || ground.is(net.minecraft.world.level.block.Blocks.FARMLAND);
            boolean rocky = ground.is(BlockTags.BASE_STONE_OVERWORLD) || isMountainBiome(biomeId);
            int slope = localSlope(level, x, z, surface);
            accum.add(surface, slope, ocean, river, water, forest, fertile, rocky, biomeId == null ? "" : biomeId.toString());
            taken++;
        }
        if (taken == 0) return false;
        if (accum.samples < MIN_SAMPLES_FOR_DISCOVERY) return false;
        SettlementGeographyProfile discovered = accum.toProfile();
        settlement.setGeography(discovered);
        ACCUM.remove(settlement.id());
        return true;
    }

    private static boolean nearPlayer(ServerLevel level, Settlement settlement) {
        double x = settlement.position().x(), z = settlement.position().z();
        return level.players().stream().anyMatch(p -> {
            double dx = p.getX() - x, dz = p.getZ() - z;
            return dx * dx + dz * dz <= ACTIVATION_RADIUS_SQR;
        });
    }

    private static int localSlope(ServerLevel level, int x, int z, int surface) {
        int max = 0;
        int[][] offs = {{4, 0}, {-4, 0}, {0, 4}, {0, -4}};
        for (int[] o : offs) {
            BlockPos probe = new BlockPos(x + o[0], level.getSeaLevel(), z + o[1]);
            if (!level.hasChunkAt(probe)) continue;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x + o[0], z + o[1]) - 1;
            max = Math.max(max, Math.abs(y - surface));
        }
        return max;
    }

    private static boolean hasNearbyLeaves(ServerLevel level, int x, int y, int z) {
        for (BlockPos p : BlockPos.betweenClosed(x - 2, y, z - 2, x + 2, y + 4, z + 2)) {
            if (!level.hasChunkAt(p)) continue;
            if (level.getBlockState(p).is(BlockTags.LEAVES)) return true;
        }
        return false;
    }

    private static boolean nearbyFreshEvidence(ServerLevel level, int x, int z, int surface) {
        for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
            BlockPos p = new BlockPos(x + dx, surface, z + dz);
            if (!level.hasChunkAt(p)) continue;
            if (level.getFluidState(p).is(FluidTags.WATER) && !isOceanBiome(level.getBiome(p).unwrapKey().map(k -> k.location()).orElse(null))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isOceanBiome(ResourceLocation id) {
        if (id == null) return false;
        String p = id.getPath().toLowerCase(Locale.ROOT);
        return p.contains("ocean") || p.contains("beach") || p.contains("stony_shore");
    }

    private static boolean isRiverBiome(ResourceLocation id) {
        if (id == null) return false;
        String p = id.getPath().toLowerCase(Locale.ROOT);
        return p.contains("river") || p.contains("stream") || p.contains("creek");
    }

    private static boolean isForestBiome(ResourceLocation id) {
        if (id == null) return false;
        String p = id.getPath().toLowerCase(Locale.ROOT);
        return p.contains("forest") || p.contains("wood") || p.contains("taiga") || p.contains("jungle");
    }

    private static boolean isFertileBiome(ResourceLocation id) {
        if (id == null) return false;
        String p = id.getPath().toLowerCase(Locale.ROOT);
        return p.contains("plains") || p.contains("meadow") || p.contains("flower") || p.contains("sunflower") || p.contains("cherry");
    }

    private static boolean isMountainBiome(ResourceLocation id) {
        if (id == null) return false;
        String p = id.getPath().toLowerCase(Locale.ROOT);
        return p.contains("mountain") || p.contains("peak") || p.contains("cliff") || p.contains("windswept") || p.contains("grove");
    }

    private static boolean isOceanish(ResourceLocation id, BlockState ground) {
        return isOceanBiome(id) || ground.getFluidState().is(FluidTags.WATER);
    }

    private static final class SampleAccum {
        int samples;
        long elevationSum;
        long slopeSum;
        int ocean;
        int river;
        int water;
        int forest;
        int fertile;
        int rocky;
        final Map<String, Integer> biomes = new HashMap<>();

        void add(int elevation, int slope, boolean ocean, boolean river, boolean water, boolean forest, boolean fertile, boolean rocky, String biomeId) {
            samples++;
            elevationSum += elevation;
            slopeSum += slope;
            if (ocean) this.ocean++;
            if (river) this.river++;
            if (water) this.water++;
            if (forest) this.forest++;
            if (fertile) this.fertile++;
            if (rocky) this.rocky++;
            if (biomeId != null && !biomeId.isBlank()) biomes.merge(biomeId, 1, Integer::sum);
        }

        SettlementGeographyProfile toProfile() {
            double n = Math.max(1, samples);
            boolean coastal = ocean / n >= 0.18D;
            boolean riverAdjacent = river / n >= 0.12D || (!coastal && water / n >= 0.20D);
            boolean navigable = coastal || riverAdjacent || water / n >= 0.25D;
            boolean freshwater = riverAdjacent && !coastal;
            double harbor = coastal ? Math.min(1.0D, 0.45D + ocean / n) : (riverAdjacent ? 0.28D : 0.05D);
            double elevation = elevationSum / n;
            double slope = slopeSum / n;
            double fertility = fertile / n;
            double forestCover = forest / n;
            double mining = Math.min(1.0D, rocky / n + slope / 24.0D);
            String biomeId = biomes.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse("");
            return SettlementGeographyProfile.unknown().withDiscovery(
                    coastal, riverAdjacent, navigable, freshwater, harbor, elevation, slope, fertility, forestCover, mining, biomeId
            );
        }
    }
}
