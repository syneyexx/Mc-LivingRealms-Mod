package dev.livingrealms.minecraft.runtime;

import dev.livingrealms.LivingRealms;
import dev.livingrealms.minecraft.EcosystemDiscoveryRuntime;
import dev.livingrealms.minecraft.ForeignSettlementDiscoveryRuntime;
import dev.livingrealms.minecraft.ForeignStructureDiscoveryRuntime;
import dev.livingrealms.minecraft.SpawnKingdomRuntime;
import dev.livingrealms.minecraft.SpeciesDataRegistry;
import dev.livingrealms.minecraft.ambience.FarPresenceRuntime;
import dev.livingrealms.minecraft.ambience.SettlementAmbienceRuntime;
import dev.livingrealms.minecraft.compat.WaystoneSettlementRuntime;
import dev.livingrealms.minecraft.construction.CivicFestivalMaterializer;
import dev.livingrealms.minecraft.construction.IndustrialSiteMaterializer;
import dev.livingrealms.minecraft.construction.PlayerStructureRevalidationRuntime;
import dev.livingrealms.minecraft.construction.RoadsideSiteMaterializer;
import dev.livingrealms.minecraft.construction.SettlementConstructionMaterializer;
import dev.livingrealms.minecraft.construction.SettlementGeographyDiscoveryRuntime;
import dev.livingrealms.minecraft.construction.TransportNetworkMaterializer;
import dev.livingrealms.minecraft.construction.UrbanCoreMaterializer;
import dev.livingrealms.minecraft.construction.HistoricalSiteMaterializer;
import dev.livingrealms.minecraft.entity.AircraftMaterializer;
import dev.livingrealms.minecraft.entity.BountyHunterMaterializer;
import dev.livingrealms.minecraft.entity.CaravanEscortMaterializer;
import dev.livingrealms.minecraft.entity.CitizenConversationRuntime;
import dev.livingrealms.minecraft.entity.CitizenJourneyMaterializer;
import dev.livingrealms.minecraft.entity.FactionCitizenMaterializer;
import dev.livingrealms.minecraft.entity.MilitaryUnitMaterializer;
import dev.livingrealms.minecraft.entity.MobileCivilizationMaterializer;
import dev.livingrealms.minecraft.entity.NavalMaterializer;
import dev.livingrealms.minecraft.entity.RegionalImpostorMaterializer;
import dev.livingrealms.minecraft.entity.SiegeEquipmentMaterializer;
import dev.livingrealms.minecraft.entity.TradeCaravanMaterializer;
import dev.livingrealms.minecraft.entity.WildlifeMaterializer;
import dev.livingrealms.minecraft.law.CustodyRuntime;
import dev.livingrealms.minecraft.player.PlayerOnboardingRuntime;
import dev.livingrealms.minecraft.presentation.CivicChoreographyRuntime;
import dev.livingrealms.minecraft.presentation.SeasonalFarmPresentationRuntime;
import dev.livingrealms.sim.runtime.RuntimeDomain;
import dev.livingrealms.sim.runtime.RuntimePriority;
import java.util.ArrayList;
import java.util.List;
import java.util.function.LongPredicate;

final class LivingRealmsRuntimeTaskCatalog {
    private LivingRealmsRuntimeTaskCatalog() {}

    static List<RuntimeTask> defaultTasks(SpeciesReloadState speciesReloadState) {
        List<RuntimeTask> tasks = new ArrayList<>();
        tasks.add(speciesReload(speciesReloadState));
        tasks.add(task("discovery.ecosystem", RuntimeDomain.DISCOVERY, RuntimePriority.NORMAL, 200, 12,
                tick -> tick % 200L == 0,
                ctx -> EcosystemDiscoveryRuntime.tick(ctx.server(), ctx.data())));
        tasks.add(task("maintenance.spawn_kingdom", RuntimeDomain.MAINTENANCE, RuntimePriority.NORMAL, 200, 12,
                tick -> tick % 200L == 0,
                ctx -> SpawnKingdomRuntime.ensure(ctx.overworld(), ctx.data())));
        tasks.add(task("maintenance.waystone", RuntimeDomain.MAINTENANCE, RuntimePriority.NORMAL, 200, 12,
                tick -> tick % 200L == 0,
                ctx -> WaystoneSettlementRuntime.tick(ctx.overworld(), ctx.data())));
        tasks.add(task("discovery.foreign_settlement", RuntimeDomain.DISCOVERY, RuntimePriority.NORMAL, 400, 16,
                tick -> tick % 400L == 0,
                ctx -> ForeignSettlementDiscoveryRuntime.tick(ctx.overworld(), ctx.data())));
        tasks.add(task("discovery.foreign_structure", RuntimeDomain.DISCOVERY, RuntimePriority.LOW, 600, 20,
                tick -> tick % 600L == 0,
                ctx -> ForeignStructureDiscoveryRuntime.tick(ctx.overworld(), ctx.data())));

        tasks.add(task("projection.wildlife", RuntimeDomain.PROJECTION, RuntimePriority.HIGH, 20, 8,
                phase(20, 0),
                ctx -> WildlifeMaterializer.tick(ctx.server(), ctx.data())));
        tasks.add(task("projection.faction_citizen", RuntimeDomain.PROJECTION, RuntimePriority.HIGH, 20, 8,
                phase(20, 0),
                ctx -> FactionCitizenMaterializer.tick(ctx.server(), ctx.data())));
        tasks.add(task("presentation.civic_choreography", RuntimeDomain.PRESENTATION, RuntimePriority.LOW, 20, 6,
                phase(20, 0),
                ctx -> CivicChoreographyRuntime.tick(ctx.overworld(), ctx.data(), ctx.tickCounter())));
        tasks.add(task("presentation.player_onboarding", RuntimeDomain.PRESENTATION, RuntimePriority.NORMAL, 100, 10,
                tick -> tick % 20L == 0 && tick % 100L == 0,
                ctx -> PlayerOnboardingRuntime.tick(ctx.server(), ctx.data())));
        tasks.add(task("presentation.settlement_ambience", RuntimeDomain.PRESENTATION, RuntimePriority.DECORATIVE, 20, 4,
                phase(20, 0),
                ctx -> SettlementAmbienceRuntime.tick(ctx.overworld(), ctx.data(), ctx.tickCounter())));

        tasks.add(task("projection.trade_caravan", RuntimeDomain.PROJECTION, RuntimePriority.HIGH, 20, 8,
                phase(20, 5),
                ctx -> TradeCaravanMaterializer.tick(ctx.overworld(), ctx.data())));
        tasks.add(task("projection.military", RuntimeDomain.PROJECTION, RuntimePriority.HIGH, 20, 8,
                phase(20, 5),
                ctx -> MilitaryUnitMaterializer.tick(ctx.server(), ctx.data())));
        tasks.add(task("projection.caravan_escort", RuntimeDomain.PROJECTION, RuntimePriority.HIGH, 20, 8,
                phase(20, 5),
                ctx -> CaravanEscortMaterializer.tick(ctx.server(), ctx.data())));

        tasks.add(task("projection.mobile_civilization", RuntimeDomain.PROJECTION, RuntimePriority.HIGH, 20, 8,
                phase(20, 10),
                ctx -> MobileCivilizationMaterializer.tick(ctx.overworld(), ctx.data())));
        tasks.add(task("projection.citizen_journey", RuntimeDomain.PROJECTION, RuntimePriority.HIGH, 20, 8,
                phase(20, 10),
                ctx -> CitizenJourneyMaterializer.tick(ctx.overworld(), ctx.data())));
        tasks.add(task("projection.aircraft", RuntimeDomain.PROJECTION, RuntimePriority.HIGH, 20, 8,
                phase(20, 10),
                ctx -> AircraftMaterializer.tick(ctx.server(), ctx.data())));
        tasks.add(task("projection.naval", RuntimeDomain.PROJECTION, RuntimePriority.HIGH, 20, 8,
                phase(20, 10),
                ctx -> NavalMaterializer.tick(ctx.server(), ctx.data())));
        tasks.add(task("projection.bounty_hunter", RuntimeDomain.PROJECTION, RuntimePriority.NORMAL, 20, 10,
                phase(20, 10),
                ctx -> BountyHunterMaterializer.tick(ctx.server(), ctx.data())));

        tasks.add(task("projection.siege", RuntimeDomain.PROJECTION, RuntimePriority.HIGH, 20, 8,
                phase(20, 15),
                ctx -> SiegeEquipmentMaterializer.tick(ctx.server(), ctx.data())));
        tasks.add(task("maintenance.custody", RuntimeDomain.MAINTENANCE, RuntimePriority.HIGH, 20, 10,
                phase(20, 15),
                ctx -> CustodyRuntime.tick(ctx.server(), ctx.data())));
        tasks.add(task("presentation.citizen_conversation", RuntimeDomain.PRESENTATION, RuntimePriority.LOW, 20, 6,
                phase(20, 15),
                ctx -> CitizenConversationRuntime.tick(ctx.server(), ctx.data(), ctx.tickCounter())));
        tasks.add(task("construction.historical_site", RuntimeDomain.CONSTRUCTION, RuntimePriority.NORMAL, 20, 10,
                phase(20, 15),
                ctx -> HistoricalSiteMaterializer.tick(ctx.overworld(), ctx.data())));
        tasks.add(task("construction.roadside_site", RuntimeDomain.CONSTRUCTION, RuntimePriority.NORMAL, 20, 10,
                phase(20, 15),
                ctx -> RoadsideSiteMaterializer.tick(ctx.overworld(), ctx.data())));
        tasks.add(task("construction.civic_festival", RuntimeDomain.CONSTRUCTION, RuntimePriority.LOW, 20, 6,
                phase(20, 15),
                ctx -> CivicFestivalMaterializer.tick(ctx.overworld(), ctx.data())));
        tasks.add(task("discovery.settlement_geography", RuntimeDomain.DISCOVERY, RuntimePriority.NORMAL, 20, 10,
                phase(20, 15),
                ctx -> SettlementGeographyDiscoveryRuntime.tick(ctx.overworld(), ctx.data())));
        tasks.add(task("construction.player_structure_revalidation", RuntimeDomain.CONSTRUCTION, RuntimePriority.NORMAL, 20, 10,
                phase(20, 15),
                ctx -> PlayerStructureRevalidationRuntime.tick(ctx.server())));
        tasks.add(task("presentation.seasonal_farm", RuntimeDomain.PRESENTATION, RuntimePriority.DECORATIVE, 20, 4,
                phase(20, 15),
                ctx -> SeasonalFarmPresentationRuntime.tick(ctx.overworld(), ctx.data(), ctx.tickCounter())));

        tasks.add(task("projection.regional_impostor", RuntimeDomain.PROJECTION, RuntimePriority.NORMAL, 100, 12,
                tick -> tick % 100L == 0,
                ctx -> RegionalImpostorMaterializer.tick(ctx.server(), ctx.data())));
        tasks.add(task("presentation.far_presence", RuntimeDomain.PRESENTATION, RuntimePriority.DECORATIVE, 100, 4,
                tick -> tick % 100L == 0,
                ctx -> FarPresenceRuntime.tick(ctx.overworld(), ctx.data(), ctx.tickCounter())));

        tasks.add(task("simulation.day_advance_drain", RuntimeDomain.SIMULATION, RuntimePriority.CRITICAL, 1, 1,
                tick -> true,
                ctx -> {
                    if (!ctx.data().dayAdvanceScheduler().hasPending()) {
                        return;
                    }
                    long drained = ctx.data().dayAdvanceScheduler().drainTick(ctx.data().state());
                    if (drained > 0) {
                        ctx.data().setDirty();
                        SettlementConstructionMaterializer.requestCatchup(drained);
                    }
                }));

        tasks.add(task("construction.settlement", RuntimeDomain.CONSTRUCTION, RuntimePriority.HIGH, 4, 8,
                tick -> (tick & 3L) == 0L,
                ctx -> SettlementConstructionMaterializer.tick(ctx.overworld(), ctx.data())));
        tasks.add(task("construction.transport", RuntimeDomain.CONSTRUCTION, RuntimePriority.HIGH, 4, 8,
                tick -> (tick & 3L) == 1L,
                ctx -> TransportNetworkMaterializer.tick(ctx.overworld(), ctx.data())));
        tasks.add(task("construction.urban_core", RuntimeDomain.CONSTRUCTION, RuntimePriority.HIGH, 4, 8,
                tick -> (tick & 3L) == 2L,
                ctx -> UrbanCoreMaterializer.tick(ctx.overworld(), ctx.data())));
        tasks.add(task("construction.industrial", RuntimeDomain.CONSTRUCTION, RuntimePriority.HIGH, 4, 8,
                tick -> (tick & 3L) == 3L,
                ctx -> IndustrialSiteMaterializer.tick(ctx.overworld(), ctx.data())));

        tasks.add(task("simulation.advance_days", RuntimeDomain.SIMULATION, RuntimePriority.CRITICAL, 24000, 1,
                tick -> tick % 24000L == 0,
                ctx -> {
                    ctx.data().state().advanceDays(ctx.data().state().config().strategicDaysPerStep());
                    ctx.data().setDirty();
                    LivingRealms.LOGGER.debug("Simulation: {}", ctx.data().state().summary());
                }));
        tasks.add(task("simulation.presentation_pulse", RuntimeDomain.SIMULATION, RuntimePriority.HIGH, 24000, 20,
                tick -> tick % 24000L == 12000L,
                ctx -> {
                    ctx.data().state().advancePresentationPulse(0.5);
                    ctx.data().setDirty();
                }));
        return tasks;
    }

    private static RuntimeTask speciesReload(SpeciesReloadState state) {
        return new RuntimeTask() {
            @Override
            public String id() {
                return "critical.species_reload";
            }

            @Override
            public RuntimeDomain domain() {
                return RuntimeDomain.CRITICAL;
            }

            @Override
            public RuntimePriority priority() {
                return RuntimePriority.CRITICAL;
            }

            @Override
            public int normalIntervalTicks() {
                return 1;
            }

            @Override
            public int maxDeferredTicks() {
                return 1;
            }

            @Override
            public boolean isDue(long tickCounter) {
                return true;
            }

            @Override
            public void execute(RuntimeTaskContext ctx) {
                long revision = SpeciesDataRegistry.revision();
                if (revision == state.appliedSpeciesRevision) {
                    return;
                }
                try {
                    ctx.data().state().replaceSpeciesCatalog(SpeciesDataRegistry.current());
                    ctx.data().setDirty();
                    state.appliedSpeciesRevision = revision;
                } catch (RuntimeException incompatibleCatalog) {
                    LivingRealms.LOGGER.error(
                            "Rejected species reload because it would invalidate the live world; restoring previous canonical catalog",
                            incompatibleCatalog);
                    SpeciesDataRegistry.install(ctx.data().state().species());
                    state.appliedSpeciesRevision = SpeciesDataRegistry.revision();
                }
            }
        };
    }

    private static LongPredicate phase(long modulus, long remainder) {
        return tick -> tick % modulus == remainder;
    }

    private interface TaskBody {
        void run(RuntimeTaskContext ctx);
    }

    private static RuntimeTask task(
            String id,
            RuntimeDomain domain,
            RuntimePriority priority,
            int intervalTicks,
            int maxDeferredTicks,
            LongPredicate due,
            TaskBody body) {
        return new RuntimeTask() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public RuntimeDomain domain() {
                return domain;
            }

            @Override
            public RuntimePriority priority() {
                return priority;
            }

            @Override
            public int normalIntervalTicks() {
                return intervalTicks;
            }

            @Override
            public int maxDeferredTicks() {
                return maxDeferredTicks;
            }

            @Override
            public boolean isDue(long tickCounter) {
                return due.test(tickCounter);
            }

            @Override
            public void execute(RuntimeTaskContext ctx) {
                body.run(ctx);
            }
        };
    }

    static final class SpeciesReloadState {
        long appliedSpeciesRevision = -1L;

        void reset() {
            appliedSpeciesRevision = -1L;
        }
    }
}
