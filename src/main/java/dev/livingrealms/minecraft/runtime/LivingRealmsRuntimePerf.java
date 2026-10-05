package dev.livingrealms.minecraft.runtime;

import dev.livingrealms.minecraft.SimulationRuntime;
import dev.livingrealms.minecraft.construction.SettlementConstructionMaterializer;
import dev.livingrealms.minecraft.entity.AircraftProjectionIndex;
import dev.livingrealms.minecraft.entity.BountyHunterIndex;
import dev.livingrealms.minecraft.entity.FactionCitizenIndex;
import dev.livingrealms.minecraft.entity.MilitaryUnitIndex;
import dev.livingrealms.minecraft.entity.MobileCivilizationIndex;
import dev.livingrealms.minecraft.entity.RegionalImpostorIndex;
import dev.livingrealms.minecraft.entity.ShipProjectionIndex;
import dev.livingrealms.minecraft.entity.SiegeEquipmentIndex;
import dev.livingrealms.minecraft.entity.TradeCaravanIndex;
import dev.livingrealms.minecraft.entity.WildlifeProjectionIndex;
import dev.livingrealms.sim.runtime.RuntimeDomain;
import dev.livingrealms.sim.runtime.RuntimeTelemetry;
import dev.livingrealms.sim.runtime.RuntimeTelemetryRegistry;
import java.util.Locale;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

public final class LivingRealmsRuntimePerf {
    private LivingRealmsRuntimePerf() {}

    public static void print(CommandSourceStack source, LivingRealmsRuntimeScheduler scheduler) {
        RuntimeTelemetryRegistry telemetry = scheduler.telemetry();
        source.sendSuccess(() -> Component.literal("Living Realms runtime • tick " + scheduler.tickCounter()
                + " • budget pressure uses wall clock (not sim determinism)"), false);
        for (RuntimeDomain domain : RuntimeDomain.values()) {
            RuntimeTelemetry stats = telemetry.domain(domain);
            if (stats.executedCount() == 0L && stats.deferredCount() == 0L) {
                continue;
            }
            source.sendSuccess(() -> Component.literal(formatDomain(domain, stats)), false);
        }
        MinecraftServer server = source.getServer();
        var data = SimulationRuntime.data(server);
        var state = data.state();
        long settlementCount = state.factions().stream().mapToLong(f -> f.settlements().size()).sum();
        source.sendSuccess(() -> Component.literal(
                "Canonical • day " + state.clock().day()
                        + " • settlements " + settlementCount
                        + " • pending day jumps " + data.dayAdvanceScheduler().pendingDays()), false);
        source.sendSuccess(() -> Component.literal(
                "Projections loaded • wildlife " + WildlifeProjectionIndex.snapshots().size()
                        + " • citizens " + FactionCitizenIndex.loaded().size()
                        + " • caravans " + TradeCaravanIndex.loaded().size()
                        + " • military " + MilitaryUnitIndex.loaded().size()
                        + " • mobile " + MobileCivilizationIndex.loaded().size()
                        + " • aircraft " + AircraftProjectionIndex.loaded().size()
                        + " • ships " + ShipProjectionIndex.loaded().size()
                        + " • bounty " + BountyHunterIndex.loaded().size()
                        + " • siege " + SiegeEquipmentIndex.loaded().size()
                        + " • impostors " + RegionalImpostorIndex.loaded().size()), false);
        source.sendSuccess(() -> Component.literal(
                "Construction catchup "
                        + (SettlementConstructionMaterializer.catchupActive() ? "active" : "idle")), false);
    }

    public static void reset(LivingRealmsRuntimeScheduler scheduler) {
        scheduler.telemetry().resetPeaksAndDeferred();
    }

    private static String formatDomain(RuntimeDomain domain, RuntimeTelemetry stats) {
        return domain.name().toLowerCase(Locale.ROOT)
                + " • ema " + formatMillis(stats.emaNanos())
                + " • last " + formatMillis(stats.lastNanos())
                + " • peak " + formatMillis(stats.peakNanos())
                + " • deferred " + stats.deferredCount()
                + " • starvation " + stats.starvationRuns()
                + " • runs " + stats.executedCount();
    }

    private static String formatMillis(double nanos) {
        return String.format(Locale.ROOT, "%.2fms", nanos / 1_000_000D);
    }
}
