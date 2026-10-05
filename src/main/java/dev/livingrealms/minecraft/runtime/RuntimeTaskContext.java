package dev.livingrealms.minecraft.runtime;

import dev.livingrealms.minecraft.LivingRealmsSavedData;
import dev.livingrealms.sim.runtime.SimulationTickBudget;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

public final class RuntimeTaskContext {
    private final MinecraftServer server;
    private final LivingRealmsSavedData data;
    private final long tickCounter;
    private final SimulationTickBudget budget;
    private final ServerLevel overworld;

    public RuntimeTaskContext(
            MinecraftServer server,
            LivingRealmsSavedData data,
            long tickCounter,
            SimulationTickBudget budget) {
        this.server = server;
        this.data = data;
        this.tickCounter = tickCounter;
        this.budget = budget;
        this.overworld = server.overworld();
    }

    public MinecraftServer server() {
        return server;
    }

    public LivingRealmsSavedData data() {
        return data;
    }

    public long tickCounter() {
        return tickCounter;
    }

    public SimulationTickBudget budget() {
        return budget;
    }

    public ServerLevel overworld() {
        return overworld;
    }
}
