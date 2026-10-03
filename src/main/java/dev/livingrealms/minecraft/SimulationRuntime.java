package dev.livingrealms.minecraft;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Access to the canonical persistent simulation attached to the always-loaded Overworld. */
public final class SimulationRuntime {
    private SimulationRuntime() {}

    public static LivingRealmsSavedData data(MinecraftServer server) {
        if (!SpeciesDataRegistry.ready()) throw new IllegalStateException("Living Realms species datapack catalog is not ready; refusing to load canonical world state against the starter fallback catalog");
        long seed = server.overworld().getSeed();
        SavedData.Factory<LivingRealmsSavedData> factory = new SavedData.Factory<>(
                () -> LivingRealmsSavedData.create(seed, SpeciesDataRegistry.current()),
                LivingRealmsSavedData::load
        );
        return server.overworld().getDataStorage().computeIfAbsent(factory, LivingRealmsSavedData.FILE_ID);
    }
}
