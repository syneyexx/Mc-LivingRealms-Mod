package dev.livingrealms.minecraft;

import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.livingrealms.minecraft.entity.LivingRealmsAnimalEntity;
import dev.livingrealms.minecraft.entity.RegionalImpostorEntity;
import dev.livingrealms.minecraft.entity.RegionalImpostorIndex;
import dev.livingrealms.minecraft.entity.WildlifeProjectionIndex;
import dev.livingrealms.minecraft.entity.TradeCaravanEntity;
import dev.livingrealms.minecraft.entity.TradeCaravanIndex;
import dev.livingrealms.minecraft.entity.FactionCitizenEntity;
import dev.livingrealms.minecraft.entity.FactionCitizenIndex;
import dev.livingrealms.minecraft.entity.CitizenConversationRuntime;
import dev.livingrealms.minecraft.entity.MilitaryUnitEntity;
import dev.livingrealms.minecraft.entity.MilitaryUnitIndex;
import dev.livingrealms.minecraft.entity.MobileCivilizationEntity;
import dev.livingrealms.minecraft.entity.MobileCivilizationIndex;
import dev.livingrealms.minecraft.entity.MobileCivilizationKind;
import dev.livingrealms.minecraft.entity.LivingRealmsAircraftEntity;
import dev.livingrealms.minecraft.entity.AircraftProjectionIndex;
import dev.livingrealms.minecraft.entity.LivingRealmsShipEntity;
import dev.livingrealms.minecraft.entity.ShipProjectionIndex;
import dev.livingrealms.minecraft.entity.BountyHunterEntity;
import dev.livingrealms.minecraft.entity.BountyHunterIndex;
import dev.livingrealms.minecraft.entity.SiegeEquipmentEntity;
import dev.livingrealms.minecraft.entity.SiegeEquipmentIndex;
import dev.livingrealms.minecraft.construction.PlayerStructureRevalidationRuntime;
import dev.livingrealms.minecraft.presentation.CivicChoreographyRuntime;
import dev.livingrealms.minecraft.presentation.SeasonalFarmPresentationRuntime;
import dev.livingrealms.minecraft.runtime.LivingRealmsRuntimePerf;
import dev.livingrealms.minecraft.runtime.LivingRealmsRuntimeScheduler;
import dev.livingrealms.sim.player.PlayerSettlementFounder;
import dev.livingrealms.sim.world.SimPosition;
import dev.livingrealms.minecraft.law.CrimeRuntime;
import dev.livingrealms.minecraft.law.FactionContainerTheftRuntime;
import dev.livingrealms.minecraft.civilization.HiddenCacheRuntime;
import dev.livingrealms.minecraft.civilization.HistoricalSiteRuntime;
import dev.livingrealms.minecraft.civilization.PirateHideoutRuntime;
import dev.livingrealms.minecraft.civilization.PlayerAssistanceRuntime;
import dev.livingrealms.minecraft.construction.CivicFestivalMaterializer;
import dev.livingrealms.minecraft.network.DashboardRequestLimiter;
import dev.livingrealms.minecraft.network.DashboardActionLimiter;
import dev.livingrealms.minecraft.network.DialogueRequestLimiter;
import dev.livingrealms.minecraft.network.DialogueSessionRuntime;
import dev.livingrealms.sim.law.CrimeType;
import dev.livingrealms.minecraft.construction.SettlementConstructionMaterializer;
import dev.livingrealms.minecraft.construction.SettlementGeographyDiscoveryRuntime;
import dev.livingrealms.minecraft.ForeignStructureDiscoveryRuntime;
import dev.livingrealms.minecraft.construction.HistoricalSiteMaterializer;
import dev.livingrealms.minecraft.construction.RoadsideSiteMaterializer;
import dev.livingrealms.sim.industry.*;
import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.civilian.CitizenRole;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.minecraft.server.level.ServerLevel;

/**
 * Projection index lifecycle and physical-loss feedback extracted from LivingRealmsEvents.
 * NeoForge event ordering stays in LivingRealmsEvents; this bridge owns no canonical state.
 */
final class ProjectionEntityLifecycleBridge {
    private ProjectionEntityLifecycleBridge() {}

    static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof LivingRealmsAnimalEntity animal) WildlifeProjectionIndex.joined(animal);
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof TradeCaravanEntity caravan) TradeCaravanIndex.joined(caravan);
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof FactionCitizenEntity citizen) FactionCitizenIndex.joined(citizen);
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof MilitaryUnitEntity unit) MilitaryUnitIndex.joined(unit);
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof MobileCivilizationEntity mobile) MobileCivilizationIndex.joined(mobile);
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof LivingRealmsAircraftEntity aircraft) AircraftProjectionIndex.joined(aircraft);
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof LivingRealmsShipEntity ship) ShipProjectionIndex.joined(ship);
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof BountyHunterEntity hunter) BountyHunterIndex.joined(hunter);
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof SiegeEquipmentEntity siege) SiegeEquipmentIndex.joined(siege);
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof RegionalImpostorEntity impostor) RegionalImpostorIndex.joined(impostor);
    }

    static void onEntityLeave(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (event.getEntity() instanceof LivingRealmsAnimalEntity animal) {
            WildlifeProjectionIndex.left(animal);
            if (!animal.isDematerializing() && !animal.physicalDeathReported() && !animal.isAlive() && event.getLevel() instanceof ServerLevel level) {
                var data = SimulationRuntime.data(level.getServer());
                if (data.state().recordPhysicalAnimalDeath(animal.populationGroupId(), 1.0D, "physical_entity_death")) {animal.markPhysicalDeathReported();data.setDirty();}
            }
        } else if (event.getEntity() instanceof FactionCitizenEntity citizen) {
            FactionCitizenIndex.left(citizen);
            if (!citizen.isDematerializing() && !citizen.deathReported() && !citizen.isAlive() && event.getLevel() instanceof ServerLevel level) {
                var data=SimulationRuntime.data(level.getServer());
                boolean changed=citizen.isJourneyProjection()
                        ?data.state().recordPhysicalJourneyDeath(citizen.journeyId(),"physical_journey_death")
                        :data.state().recordPhysicalCitizenDeath(citizen.settlementId(),citizen.citizenId(),"physical_citizen_death");
                if(changed){citizen.markDeathReported();data.setDirty();}
            }
        } else if (event.getEntity() instanceof MilitaryUnitEntity unit) {
            MilitaryUnitIndex.left(unit);
            if(!unit.isDematerializing()&&!unit.lossReported()&&!unit.isAlive()&&event.getLevel() instanceof ServerLevel level){var data=SimulationRuntime.data(level.getServer());if(data.state().recordPhysicalArmyLoss(unit.armyId(),unit.representedPersonnel(),"physical_military_unit_destroyed")){unit.markLossReported();data.setDirty();}}
        } else if (event.getEntity() instanceof MobileCivilizationEntity mobile) {
            MobileCivilizationIndex.left(mobile);
            if(!mobile.isDematerializing()&&!mobile.lossReported()&&!mobile.isAlive()&&event.getLevel() instanceof ServerLevel level){
                var data=SimulationRuntime.data(level.getServer());
                boolean changed=mobile.kind()==MobileCivilizationKind.MIGRATION
                        ?data.state().recordPhysicalMigrationLoss(mobile.canonicalId(),mobile.representedPeople(),"physical_migration_representative_destroyed")
                        :data.state().recordPhysicalPirateLoss(mobile.canonicalId(),mobile.representedPeople(),"physical_pirate_representative_destroyed");
                if(changed){mobile.markLossReported();data.setDirty();}
            }
        } else if (event.getEntity() instanceof LivingRealmsAircraftEntity aircraft) {
            AircraftProjectionIndex.left(aircraft);
            if(!aircraft.isDematerializing()&&!aircraft.lossReported()&&!aircraft.isAlive()&&event.getLevel() instanceof ServerLevel level){var data=SimulationRuntime.data(level.getServer());if(data.state().recordPhysicalAircraftLoss(aircraft.wingId(),1,"physical_aircraft_destroyed")){aircraft.markLossReported();data.setDirty();}}
        } else if (event.getEntity() instanceof LivingRealmsShipEntity ship) {
            ShipProjectionIndex.left(ship);
            if(!ship.isDematerializing()&&!ship.lossReported()&&!ship.isAlive()&&event.getLevel() instanceof ServerLevel level){var data=SimulationRuntime.data(level.getServer());if(data.state().recordPhysicalShipLoss(ship.fleetId(),ship.shipClass(),ship.representedShips(),"physical_ship_destroyed")){ship.markLossReported();data.setDirty();}}
        } else if (event.getEntity() instanceof BountyHunterEntity hunter) {
            BountyHunterIndex.left(hunter);
        } else if (event.getEntity() instanceof SiegeEquipmentEntity siege) {
            SiegeEquipmentIndex.left(siege);
        } else if (event.getEntity() instanceof RegionalImpostorEntity impostor) {
            RegionalImpostorIndex.left(impostor);
        } else if (event.getEntity() instanceof TradeCaravanEntity caravan) {
            TradeCaravanIndex.left(caravan);
            if (!caravan.isDematerializing() && !caravan.lossReported() && !caravan.isAlive() && event.getLevel() instanceof ServerLevel level) {
                var data=SimulationRuntime.data(level.getServer());
                if(data.state().recordPhysicalShipmentLoss(caravan.shipmentId(),"physical_caravan_destroyed")){caravan.markLossReported();data.setDirty();}
            }
        }
    }
}
