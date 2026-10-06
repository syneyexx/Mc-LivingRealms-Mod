package dev.livingrealms.minecraft.worldgen;

import dev.livingrealms.minecraft.LivingRealmsSavedData;
import dev.livingrealms.minecraft.construction.FactionBlockPalette;
import dev.livingrealms.sim.construction.AuthoredOwnerType;
import dev.livingrealms.sim.world.StarterCivilizationLayoutPlanner;
import dev.livingrealms.sim.world.StarterCultureTraits;
import dev.livingrealms.sim.worldgen.StarterCivilizationFabricIndex;
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
 * Server-lifetime publication of immutable starter worldgen planning.
 *
 * <p>Activation happens on the main thread when the Overworld loads. Generation workers only read
 * the immutable fabric index; provenance is persisted directly on the chunk through a NeoForge
 * attachment, never through mutable SavedData.</p>
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
        private final StarterCivilizationFabricIndex fabricIndex;
        private final WizardTreesWorldgenIndex wizardTreesIndex;
        private final Map<Long, Integer> paletteStyleByFaction;

        private Context(long worldSeed, int worldgenVersion,
                        StarterCivilizationFabricIndex fabricIndex,
                        WizardTreesWorldgenIndex wizardTreesIndex,
                        Map<Long, Integer> paletteStyleByFaction) {
            this.worldSeed = worldSeed;
            this.worldgenVersion = worldgenVersion;
            this.fabricIndex = Objects.requireNonNull(fabricIndex, "fabricIndex");
            this.wizardTreesIndex = Objects.requireNonNull(wizardTreesIndex, "wizardTreesIndex");
            this.paletteStyleByFaction = Map.copyOf(
                    Objects.requireNonNull(paletteStyleByFaction, "paletteStyleByFaction"));
        }

        public long worldSeed() { return worldSeed; }
        public int worldgenVersion() { return worldgenVersion; }
        public StarterCivilizationFabricIndex fabricIndex() { return fabricIndex; }
        public WizardTreesWorldgenIndex wizardTreesIndex() { return wizardTreesIndex; }
        public int paletteStyle(long factionId) {
            return paletteStyleByFaction.getOrDefault(
                    factionId, FactionBlockPalette.cultureStyle(factionId, 0, 0, 0, 0));
        }
    }

    private static final ConcurrentHashMap<ServerLevel, Context> BY_LEVEL = new ConcurrentHashMap<>();

    private StarterCivilizationWorldgenContext() {}

    public static void activate(ServerLevel level, LivingRealmsSavedData data) {
        if (level == null || data == null || level.dimension() != Level.OVERWORLD) return;
        if (!data.starterWorldgenEnabled()) {
            BY_LEVEL.remove(level);
            return;
        }
        long seed = level.getSeed();
        Context existing = BY_LEVEL.get(level);
        if (existing != null && existing.worldSeed() == seed
                && existing.worldgenVersion() == data.civilizationWorldgenVersion()) {
            return;
        }
        StarterCivilizationLayoutPlanner.Layout layout =
                StarterCivilizationLayoutPlanner.plan(seed);
        StarterCivilizationFabricIndex index = StarterCivilizationFabricIndex.build(layout);
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
                    .orElseGet(() -> FactionBlockPalette.cultureStyle(factionId, 0, 0, 0, 0));
            paletteStyles.put(factionId, style);
        }
        BY_LEVEL.put(level, new Context(
                seed, data.civilizationWorldgenVersion(), index, wizardTreesIndex, paletteStyles));
    }

    public static Optional<Context> context(WorldGenLevel worldGenLevel) {
        if (worldGenLevel == null) return Optional.empty();
        ServerLevel level = worldGenLevel.getLevel();
        if (level.dimension() != Level.OVERWORLD) return Optional.empty();
        Context context = BY_LEVEL.get(level);
        if (context == null || context.worldSeed() != worldGenLevel.getSeed()) return Optional.empty();
        return Optional.of(context);
    }

    public static Optional<Context> context(ServerLevel level) {
        if (level == null || level.dimension() != Level.OVERWORLD) return Optional.empty();
        Context context = BY_LEVEL.get(level);
        if (context == null || context.worldSeed() != level.getSeed()) return Optional.empty();
        return Optional.of(context);
    }

    public static boolean active(ServerLevel level) {
        return level != null && BY_LEVEL.containsKey(level);
    }


    public static void clear() {
        BY_LEVEL.clear();
    }

}
