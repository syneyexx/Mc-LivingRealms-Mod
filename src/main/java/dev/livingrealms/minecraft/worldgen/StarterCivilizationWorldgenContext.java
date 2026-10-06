package dev.livingrealms.minecraft.worldgen;

import dev.livingrealms.minecraft.LivingRealmsSavedData;
import dev.livingrealms.sim.world.StarterCivilizationLayoutPlanner;
import dev.livingrealms.sim.worldgen.StarterCivilizationFabricIndex;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;

/**
 * Server-lifetime publication of immutable starter worldgen state.
 *
 * <p>Activation happens on the main thread when the Overworld loads. Generation workers only read
 * the immutable context; they never touch SavedData or canonical mutable simulation collections.</p>
 */
public final class StarterCivilizationWorldgenContext {
    public record Context(
            long worldSeed,
            int worldgenVersion,
            StarterCivilizationFabricIndex fabricIndex
    ) {}

    private static final ConcurrentHashMap<ServerLevel, Context> BY_LEVEL = new ConcurrentHashMap<>();

    private StarterCivilizationWorldgenContext() {}

    public static void activate(ServerLevel level, LivingRealmsSavedData data) {
        if (level == null || data == null || level.dimension() != Level.OVERWORLD) return;
        if (!data.starterWorldgenEnabled()) {
            BY_LEVEL.remove(level);
            return;
        }
        long seed = level.getSeed();
        StarterCivilizationFabricIndex index = StarterCivilizationFabricIndex.build(
                StarterCivilizationLayoutPlanner.plan(seed));
        BY_LEVEL.put(level, new Context(seed, data.civilizationWorldgenVersion(), index));
    }

    public static Optional<Context> context(WorldGenLevel worldGenLevel) {
        if (worldGenLevel == null) return Optional.empty();
        ServerLevel level = worldGenLevel.getLevel();
        if (level.dimension() != Level.OVERWORLD) return Optional.empty();
        Context context = BY_LEVEL.get(level);
        if (context == null || context.worldSeed() != worldGenLevel.getSeed()) return Optional.empty();
        return Optional.of(context);
    }

    public static boolean active(ServerLevel level) {
        return level != null && BY_LEVEL.containsKey(level);
    }

    public static void clear() {
        BY_LEVEL.clear();
    }
}
