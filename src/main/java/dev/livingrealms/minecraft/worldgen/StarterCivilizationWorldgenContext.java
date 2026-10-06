package dev.livingrealms.minecraft.worldgen;

import dev.livingrealms.LivingRealms;
import dev.livingrealms.minecraft.LivingRealmsSavedData;
import dev.livingrealms.minecraft.construction.FactionBlockPalette;
import dev.livingrealms.sim.construction.AuthoredOwnerType;
import dev.livingrealms.sim.world.StarterCivilizationLayoutPlanner;
import dev.livingrealms.sim.world.StarterCultureTraits;
import dev.livingrealms.sim.worldgen.StarterRegionalRoutePlanner;
import dev.livingrealms.sim.worldgen.WizardTreesInitialWorldgenPlan;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;

/**
 * Server-lifetime publication of starter worldgen planning.
 *
 * <p>Level load publishes only cheap deterministic topology. Terrain-heavy settlement refinement
 * and full regional-route A* are intentionally lazy and run only when actual chunk generation
 * reaches the relevant region. This keeps Minecraft fresh-world preparation out of a global
 * Living Realms terrain pass while preserving true worldgen block placement.</p>
 */
public final class StarterCivilizationWorldgenContext {
    public record AuthoredWrite(int x, int y, int z, AuthoredOwnerType ownerType) {
        public AuthoredWrite {
            ownerType = Objects.requireNonNull(ownerType, "ownerType");
        }
    }

    public static final class Context {
        private final long worldSeed;
        private final int worldgenVersion;
        private final LazyStarterCivilizationFabricIndex fabricIndex;
        private final LazyStarterRegionalRouteGeometryIndex routeGeometryIndex;
        private final WizardTreesWorldgenIndex wizardTreesIndex;
        private final Map<Long, Integer> paletteStyleByFaction;

        private Context(
                long worldSeed,
                int worldgenVersion,
                LazyStarterCivilizationFabricIndex fabricIndex,
                LazyStarterRegionalRouteGeometryIndex routeGeometryIndex,
                WizardTreesWorldgenIndex wizardTreesIndex,
                Map<Long, Integer> paletteStyleByFaction) {
            this.worldSeed = worldSeed;
            this.worldgenVersion = worldgenVersion;
            this.fabricIndex = Objects.requireNonNull(fabricIndex, "fabricIndex");
            this.routeGeometryIndex = Objects.requireNonNull(routeGeometryIndex, "routeGeometryIndex");
            this.wizardTreesIndex = Objects.requireNonNull(wizardTreesIndex, "wizardTreesIndex");
            this.paletteStyleByFaction = Map.copyOf(
                    Objects.requireNonNull(paletteStyleByFaction, "paletteStyleByFaction"));
        }

        public long worldSeed() { return worldSeed; }
        public int worldgenVersion() { return worldgenVersion; }
        public LazyStarterCivilizationFabricIndex fabricIndex() { return fabricIndex; }
        public LazyStarterRegionalRouteGeometryIndex routeGeometryIndex() {
            return routeGeometryIndex;
        }
        public WizardTreesWorldgenIndex wizardTreesIndex() { return wizardTreesIndex; }

        public int paletteStyle(long factionId) {
            return paletteStyleByFaction.getOrDefault(
                    factionId, FactionBlockPalette.cultureStyle(factionId, 0, 0, 0, 0));
        }
    }

    private static final ConcurrentHashMap<ServerLevel, Context> BY_LEVEL =
            new ConcurrentHashMap<>();

    private StarterCivilizationWorldgenContext() {}

    public static void activate(ServerLevel level, LivingRealmsSavedData data) {
        if (level == null || data == null || level.dimension() != Level.OVERWORLD) return;
        if (!data.starterWorldgenEnabled()) {
            BY_LEVEL.remove(level);
            return;
        }

        long seed = level.getSeed();
        Context existing = BY_LEVEL.get(level);
        if (existing != null
                && existing.worldSeed() == seed
                && existing.worldgenVersion() == data.civilizationWorldgenVersion()) {
            return;
        }

        long startedNanos = System.nanoTime();

        // Cheap authored topology only. No settlement terrain scan, no global blueprint index and
        // no all-routes A* are allowed in this activation path.
        StarterCivilizationLayoutPlanner.Layout layout =
                StarterCivilizationLayoutPlanner.plan(seed);
        StarterGeneratorTerrainCache terrainCache =
                new StarterGeneratorTerrainCache(level);

        LazyStarterCivilizationFabricIndex fabricIndex =
                new LazyStarterCivilizationFabricIndex(level, data, layout, terrainCache);

        var starterRoutes = StarterRegionalRoutePlanner.plan(layout);
        LazyStarterRegionalRouteGeometryIndex routeGeometryIndex =
                new LazyStarterRegionalRouteGeometryIndex(
                        level, starterRoutes, fabricIndex, terrainCache);

        // Wizard Trees is intentionally retained as a tiny eager index: its starter footprint is
        // small and bounded, unlike the 267-settlement surface civilization.
        WizardTreesWorldgenIndex wizardTreesIndex = WizardTreesWorldgenIndex.build(
                level, WizardTreesInitialWorldgenPlan.build(data.state()));

        Map<Long, Integer> paletteStyles = new HashMap<>();
        for (StarterCivilizationLayoutPlanner.RealmPlan realm : layout.realms()) {
            long factionId = realm.factionId();
            int style = StarterCultureTraits.resolve(realm.definition())
                    .map(traits -> FactionBlockPalette.cultureStyle(
                            factionId,
                            traits.artistic(),
                            traits.agrarian(),
                            traits.martial(),
                            traits.mercantile()))
                    .orElseGet(() ->
                            FactionBlockPalette.cultureStyle(factionId, 0, 0, 0, 0));
            paletteStyles.put(factionId, style);
        }

        Context published = new Context(
                seed,
                data.civilizationWorldgenVersion(),
                fabricIndex,
                routeGeometryIndex,
                wizardTreesIndex,
                paletteStyles);
        BY_LEVEL.put(level, published);

        long totalMillis = elapsedMillis(startedNanos);
        LivingRealms.LOGGER.info(
                "Starter worldgen context published in {} ms "
                        + "[settlements={} lazy, routes={} lazy, wizardChunks={}]",
                totalMillis,
                fabricIndex.totalSettlementCount(),
                routeGeometryIndex.totalRouteCount(),
                wizardTreesIndex.indexedChunkCount());
    }

    private static long elapsedMillis(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000L;
    }

    public static Optional<Context> context(WorldGenLevel worldGenLevel) {
        if (worldGenLevel == null) return Optional.empty();
        ServerLevel level = worldGenLevel.getLevel();
        if (level.dimension() != Level.OVERWORLD) return Optional.empty();
        Context context = BY_LEVEL.get(level);
        if (context == null || context.worldSeed() != worldGenLevel.getSeed()) {
            return Optional.empty();
        }
        return Optional.of(context);
    }

    public static Optional<Context> context(ServerLevel level) {
        if (level == null || level.dimension() != Level.OVERWORLD) return Optional.empty();
        Context context = BY_LEVEL.get(level);
        if (context == null || context.worldSeed() != level.getSeed()) {
            return Optional.empty();
        }
        return Optional.of(context);
    }

    public static boolean active(ServerLevel level) {
        return level != null && BY_LEVEL.containsKey(level);
    }

    public static void clear() {
        BY_LEVEL.clear();
    }
}
