package dev.livingrealms.minecraft;

import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.livingrealms.LivingRealms;
import dev.livingrealms.minecraft.ambience.SettlementAmbienceRuntime;
import dev.livingrealms.minecraft.entity.LivingRealmsAnimalEntity;
import dev.livingrealms.minecraft.compat.WaystoneSettlementRuntime;
import dev.livingrealms.sim.player.PlayerSettlementFounder;
import dev.livingrealms.sim.world.SimPosition;
import dev.livingrealms.minecraft.entity.WildlifeMaterializer;
import dev.livingrealms.minecraft.entity.WildlifeProjectionIndex;
import dev.livingrealms.minecraft.entity.TradeCaravanEntity;
import dev.livingrealms.minecraft.entity.TradeCaravanIndex;
import dev.livingrealms.minecraft.entity.TradeCaravanMaterializer;
import dev.livingrealms.minecraft.entity.CaravanEscortMaterializer;
import dev.livingrealms.minecraft.entity.FactionCitizenEntity;
import dev.livingrealms.minecraft.entity.FactionCitizenIndex;
import dev.livingrealms.minecraft.entity.FactionCitizenMaterializer;
import dev.livingrealms.minecraft.entity.CitizenConversationRuntime;
import dev.livingrealms.minecraft.entity.MilitaryUnitEntity;
import dev.livingrealms.minecraft.entity.MilitaryUnitIndex;
import dev.livingrealms.minecraft.entity.MilitaryUnitMaterializer;
import dev.livingrealms.minecraft.entity.MobileCivilizationEntity;
import dev.livingrealms.minecraft.entity.MobileCivilizationIndex;
import dev.livingrealms.minecraft.entity.MobileCivilizationKind;
import dev.livingrealms.minecraft.entity.MobileCivilizationMaterializer;
import dev.livingrealms.minecraft.entity.LivingRealmsAircraftEntity;
import dev.livingrealms.minecraft.entity.AircraftProjectionIndex;
import dev.livingrealms.minecraft.entity.AircraftMaterializer;
import dev.livingrealms.minecraft.entity.LivingRealmsShipEntity;
import dev.livingrealms.minecraft.entity.ShipProjectionIndex;
import dev.livingrealms.minecraft.entity.NavalMaterializer;
import dev.livingrealms.minecraft.entity.BountyHunterEntity;
import dev.livingrealms.minecraft.entity.BountyHunterIndex;
import dev.livingrealms.minecraft.entity.BountyHunterMaterializer;
import dev.livingrealms.minecraft.entity.SiegeEquipmentEntity;
import dev.livingrealms.minecraft.entity.SiegeEquipmentIndex;
import dev.livingrealms.minecraft.entity.SiegeEquipmentMaterializer;
import dev.livingrealms.minecraft.law.CrimeRuntime;
import dev.livingrealms.minecraft.law.CustodyRuntime;
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
import dev.livingrealms.minecraft.construction.TransportNetworkMaterializer;
import dev.livingrealms.minecraft.construction.UrbanCoreMaterializer;
import dev.livingrealms.minecraft.construction.IndustrialSiteMaterializer;
import dev.livingrealms.minecraft.construction.HistoricalSiteMaterializer;
import dev.livingrealms.sim.industry.*;
import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.faction.ResourceType;
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

public final class LivingRealmsEvents {
    private long tickCounter;
    private long appliedSpeciesRevision = -1;

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        if (!SpeciesDataRegistry.ready()) return;
        tickCounter++;
        long revision = SpeciesDataRegistry.revision();
        if (revision != appliedSpeciesRevision) {
            var data = SimulationRuntime.data(event.getServer());
            try {
                data.state().replaceSpeciesCatalog(SpeciesDataRegistry.current());
                data.setDirty();
                appliedSpeciesRevision = revision;
            } catch (RuntimeException incompatibleCatalog) {
                LivingRealms.LOGGER.error("Rejected species reload because it would invalidate the live world; restoring previous canonical catalog", incompatibleCatalog);
                SpeciesDataRegistry.install(data.state().species());
                appliedSpeciesRevision = SpeciesDataRegistry.revision();
            }
        }
        if (tickCounter % 200L == 0) {
            var maintenanceData=SimulationRuntime.data(event.getServer());
            EcosystemDiscoveryRuntime.tick(event.getServer(), maintenanceData);
            SpawnKingdomRuntime.ensure(event.getServer().overworld(),maintenanceData);
            WaystoneSettlementRuntime.tick(event.getServer().overworld(), maintenanceData);
        }
        if (tickCounter % 400L == 0) ForeignSettlementDiscoveryRuntime.tick(event.getServer().overworld(),SimulationRuntime.data(event.getServer()));
        if (tickCounter % 600L == 0) ForeignStructureDiscoveryRuntime.tick(event.getServer().overworld(),SimulationRuntime.data(event.getServer()));
        // Spread entity/materializer work across a 20-tick window so frames don't stall together.
        if (tickCounter % 20L == 0) {
            var data = SimulationRuntime.data(event.getServer());
            WildlifeMaterializer.tick(event.getServer(), data);
            FactionCitizenMaterializer.tick(event.getServer(), data);
            if (tickCounter % 100L == 0) dev.livingrealms.minecraft.player.PlayerOnboardingRuntime.tick(event.getServer(), data);
            SettlementAmbienceRuntime.tick(event.getServer().overworld(), data, tickCounter);
        } else if (tickCounter % 20L == 5) {
            var data = SimulationRuntime.data(event.getServer());
            TradeCaravanMaterializer.tick(event.getServer().overworld(), data);
            MilitaryUnitMaterializer.tick(event.getServer(), data);
            CaravanEscortMaterializer.tick(event.getServer(), data);
        } else if (tickCounter % 20L == 10) {
            var data = SimulationRuntime.data(event.getServer());
            MobileCivilizationMaterializer.tick(event.getServer().overworld(), data);
            AircraftMaterializer.tick(event.getServer(), data);
            NavalMaterializer.tick(event.getServer(), data);
            BountyHunterMaterializer.tick(event.getServer(), data);
        } else if (tickCounter % 20L == 15) {
            var data = SimulationRuntime.data(event.getServer());
            SiegeEquipmentMaterializer.tick(event.getServer(), data);
            CustodyRuntime.tick(event.getServer(), data);
            CitizenConversationRuntime.tick(event.getServer(), data, tickCounter);
            HistoricalSiteMaterializer.tick(event.getServer().overworld(), data);
            CivicFestivalMaterializer.tick(event.getServer().overworld(), data);
            SettlementGeographyDiscoveryRuntime.tick(event.getServer().overworld(), data);
        }
        // Time-slice construction systems across ticks so the sim stays live without hitching.
        // Features are not removed — each still runs every 4 ticks with the same per-tick budgets.
        var overworld = event.getServer().overworld();
        var buildData = SimulationRuntime.data(event.getServer());
        int phase = (int) (tickCounter & 3L);
        if (phase == 0) SettlementConstructionMaterializer.tick(overworld, buildData);
        else if (phase == 1) TransportNetworkMaterializer.tick(overworld, buildData);
        else if (phase == 2) UrbanCoreMaterializer.tick(overworld, buildData);
        else IndustrialSiteMaterializer.tick(overworld, buildData);
        // Sparse far-world continuity: when players roam beyond the authored belt, seed frontier outposts.
        if (tickCounter % 100L == 0) {
            var data = SimulationRuntime.data(event.getServer());
            boolean seeded = false;
            for (var player : event.getServer().overworld().players()) {
                int n = dev.livingrealms.sim.world.FrontierExplorationSeeder.ensureNear(
                        data.state(), new SimPosition(player.getX(), player.getZ()));
                if (n > 0) seeded = true;
            }
            if (seeded) data.setDirty();
        }
        // Expensive aggregate simulation runs once per Minecraft day, not 20 times per second.
        if (tickCounter % 24000L == 0) {
            var data = SimulationRuntime.data(event.getServer());
            data.state().advanceDays(data.state().config().strategicDaysPerStep());
            data.setDirty();
            LivingRealms.LOGGER.debug("Simulation: {}", data.state().summary());
        }
    }



    @SubscribeEvent
    public void onEntityJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof LivingRealmsAnimalEntity animal) WildlifeProjectionIndex.joined(animal);
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof TradeCaravanEntity caravan) TradeCaravanIndex.joined(caravan);
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof FactionCitizenEntity citizen) FactionCitizenIndex.joined(citizen);
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof MilitaryUnitEntity unit) MilitaryUnitIndex.joined(unit);
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof MobileCivilizationEntity mobile) MobileCivilizationIndex.joined(mobile);
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof LivingRealmsAircraftEntity aircraft) AircraftProjectionIndex.joined(aircraft);
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof LivingRealmsShipEntity ship) ShipProjectionIndex.joined(ship);
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof BountyHunterEntity hunter) BountyHunterIndex.joined(hunter);
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof SiegeEquipmentEntity siege) SiegeEquipmentIndex.joined(siege);
    }

    @SubscribeEvent
    public void onEntityLeave(EntityLeaveLevelEvent event) {
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
                if(data.state().recordPhysicalCitizenDeath(citizen.settlementId(),citizen.citizenId(),"physical_citizen_death")){citizen.markDeathReported();data.setDirty();}
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
        } else if (event.getEntity() instanceof TradeCaravanEntity caravan) {
            TradeCaravanIndex.left(caravan);
            if (!caravan.isDematerializing() && !caravan.lossReported() && !caravan.isAlive() && event.getLevel() instanceof ServerLevel level) {
                var data=SimulationRuntime.data(level.getServer());
                if(data.state().recordPhysicalShipmentLoss(caravan.shipmentId(),"physical_caravan_destroyed")){caravan.markLossReported();data.setDirty();}
            }
        }
    }

    @SubscribeEvent
    public void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if(!(event.getEntity() instanceof ServerPlayer player))return;
        if(event.getTarget() instanceof FactionCitizenEntity citizen){DialogueSessionRuntime.open(player,citizen);event.setCanceled(true);return;}
        // Vanilla villagers and compatible villager-derived NPCs are adopted into the same social/dialogue system.
        // Sneak-interact intentionally keeps the vanilla/modded trading interaction available.
        if(event.getTarget() instanceof AbstractVillager villager&&!player.isShiftKeyDown()){DialogueSessionRuntime.open(player,villager);event.setCanceled(true);return;}
        // Broader allowlisted civilian adapters (Better Villages / village-derived humanoids). Never hostiles.
        if(!player.isShiftKeyDown()&&dev.livingrealms.minecraft.compat.CivilianNpcAdoption.isAdoptableCivilian(event.getTarget())){
            DialogueSessionRuntime.openAdopted(player,event.getTarget(),dev.livingrealms.minecraft.compat.CivilianNpcAdoption.inferRole(event.getTarget()));
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if(event.getEntity() instanceof ServerPlayer player&&!player.level().isClientSide()){
            if(HiddenCacheRuntime.interact(player,event.getPos()).consumesAction()){event.setCanceled(true);return;}
            FactionContainerTheftRuntime.prepare(player,event.getPos());
        }
    }

    @SubscribeEvent
    public void onContainerOpen(PlayerContainerEvent.Open event) {
        if(event.getEntity() instanceof ServerPlayer player)FactionContainerTheftRuntime.opened(player,event.getContainer());
    }

    @SubscribeEvent
    public void onContainerClose(PlayerContainerEvent.Close event) {
        if(event.getEntity() instanceof ServerPlayer player)FactionContainerTheftRuntime.closed(player,event.getContainer());
    }

    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) return;
        if(HiddenCacheRuntime.broken(player,event.getPos())){event.setCanceled(true);return;}
        if(PirateHideoutRuntime.blockBroken(player,event.getPos()))return;
        HistoricalSiteRuntime.ruinBlockBroken(player,event.getPos());
        var data=SimulationRuntime.data(level.getServer());var state=data.state();double x=event.getPos().getX()+.5,z=event.getPos().getZ()+.5;
        IndustrySitePlanner.Site nearest=null;double best=7.0D*7.0D;
        for(var faction:state.factions())for(IndustrySitePlanner.Site site:IndustrySitePlanner.plan(faction)){double dx=site.center().x()-x,dz=site.center().z()-z,d=dx*dx+dz*dz;if(d<best){best=d;nearest=site;}}
        if(nearest==null)return;
        final IndustrySitePlanner.Site nearestSite=nearest;
        IndustrialSite canonical=state.industrialSites().stream().filter(s->s.factionId()==nearestSite.factionId()&&s.settlementId()==nearestSite.settlementId()&&s.kind()==nearestSite.kind()).findFirst().orElse(null);
        if(canonical==null)return;
        if(state.recordIndustrialDamage(canonical.id(),.018,0,"player_block_break")){data.setDirty();CrimeRuntime.report(player,canonical.factionId(),CrimeType.SABOTAGE,25,null,"damaged industrial site "+canonical.id());}
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        DashboardRequestLimiter.remove(event.getEntity().getUUID());
        DashboardActionLimiter.remove(event.getEntity().getUUID());
        DialogueRequestLimiter.remove(event.getEntity().getUUID());
        DialogueSessionRuntime.close(event.getEntity().getUUID());
        FactionContainerTheftRuntime.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        WildlifeProjectionIndex.clear();
        TradeCaravanIndex.clear();
        FactionCitizenIndex.clear();
        CitizenConversationRuntime.clear();
        MilitaryUnitIndex.clear();
        MobileCivilizationIndex.clear();
        AircraftProjectionIndex.clear();
        ShipProjectionIndex.clear();
        BountyHunterIndex.clear();
        SiegeEquipmentIndex.clear();
        DashboardRequestLimiter.clear();
        DashboardActionLimiter.clear();
        DialogueRequestLimiter.clear();
        DialogueSessionRuntime.clear();
        FactionContainerTheftRuntime.clear();
        SettlementConstructionMaterializer.clear();
        SettlementGeographyDiscoveryRuntime.clear();
        HistoricalSiteMaterializer.clear();
        CivicFestivalMaterializer.clear();
        ForeignStructureDiscoveryRuntime.clear();
        dev.livingrealms.minecraft.player.PlayerOnboardingRuntime.clear();
        tickCounter = 0;
        appliedSpeciesRevision = -1;
    }

    @SubscribeEvent
    public void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new SpeciesReloadListener());
    }

    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity().level() instanceof ServerLevel level)) return;
        if (event.getSource().getEntity() instanceof FactionCitizenEntity hunter && hunter.role()==CitizenRole.HUNTER && event.getEntity() instanceof LivingRealmsAnimalEntity animal) {
            var data=SimulationRuntime.data(level.getServer());
            var faction=data.state().findFaction(hunter.factionId()).orElse(null);
            var species=animal.species();
            if(faction!=null&&species!=null){
                double food=Math.max(.5D,Math.min(18.0D,species.adultMassKg()*.065D));
                faction.stockpile().add(ResourceType.FOOD,food);
                data.setDirty();
            }
            return;
        }
        if (event.getEntity() instanceof ServerPlayer victim && event.getSource().getEntity() instanceof BountyHunterEntity hunter) {
            var data=SimulationRuntime.data(level.getServer());
            var result=data.state().claimBounty(hunter.contractId(),hunter.hunterKey());
            if(result.claimed()){data.setDirty();victim.sendSystemMessage(Component.literal("A bounty hunter collected the bounty issued by faction "+result.issuerFactionId()+"."));hunter.dematerialize();}
            return;
        }
        if (!(event.getSource().getEntity() instanceof ServerPlayer killer)) return;
        if (event.getEntity() instanceof ServerPlayer victim) {
            var data=SimulationRuntime.data(level.getServer());
            var state=data.state();
            String victimKey=CrimeRuntime.actorKey(victim);
            String hunterKey=CrimeRuntime.actorKey(killer);
            boolean claimed=false;
            for(var contract:new java.util.ArrayList<>(state.bounties())){
                if(!contract.actorKey().equals(victimKey))continue;
                if(contract.status()==dev.livingrealms.sim.law.BountyContract.Status.CLAIMED||contract.status()==dev.livingrealms.sim.law.BountyContract.Status.CANCELLED)continue;
                boolean assigned=contract.status()==dev.livingrealms.sim.law.BountyContract.Status.ASSIGNED&&contract.hunterKey().equals(hunterKey);
                boolean lethal=state.lawResponse(victimKey,contract.issuerFactionId()).action()==dev.livingrealms.sim.law.EnforcementAction.LETHAL_FORCE;
                if(!assigned&&!lethal)continue;
                var result=state.claimBounty(contract.id(),hunterKey);
                if(result.claimed()){claimed=true;killer.sendSystemMessage(Component.literal("Bounty claimed: "+Math.round(result.paidReward())+" from faction "+result.issuerFactionId()));}
            }
            if(claimed)data.setDirty();
            else CrimeRuntime.reportInTerritory(killer,CrimeType.MURDER,0,victim,"killed player without valid lethal warrant");
        } else if (event.getEntity() instanceof FactionCitizenEntity citizen) {
            CrimeRuntime.report(killer,citizen.factionId(),CrimeType.MURDER,0,citizen,"killed faction citizen");
        } else if (event.getEntity() instanceof MobileCivilizationEntity mobile) {
            MobileCivilizationIndex.left(mobile);
            if(!mobile.isDematerializing()&&!mobile.lossReported()&&!mobile.isAlive()){
                var data=SimulationRuntime.data(level.getServer());
                boolean changed=mobile.kind()==MobileCivilizationKind.MIGRATION
                        ?data.state().recordPhysicalMigrationLoss(mobile.canonicalId(),mobile.representedPeople(),"player_killed_migration_representative")
                        :data.state().recordPhysicalPirateLoss(mobile.canonicalId(),mobile.representedPeople(),"player_killed_pirate_representative");
                if(changed){mobile.markLossReported();data.setDirty();}
                if(mobile.kind()==MobileCivilizationKind.MIGRATION)CrimeRuntime.report(killer,mobile.factionId(),CrimeType.MURDER,0,mobile,"killed traveling civilian group representative");
            }
        } else if (event.getEntity() instanceof MilitaryUnitEntity unit) {
            MilitaryUnitIndex.left(unit);
            if(!unit.isDematerializing()&&!unit.lossReported()&&!unit.isAlive()){var data=SimulationRuntime.data(level.getServer());if(data.state().recordPhysicalArmyLoss(unit.armyId(),unit.representedPersonnel(),"physical_military_unit_destroyed")){unit.markLossReported();awardCombatService(killer,unit.factionId(),Math.max(2,unit.representedPersonnel()*.35),data);data.setDirty();}}
        } else if (event.getEntity() instanceof LivingRealmsAircraftEntity aircraft) {
            AircraftProjectionIndex.left(aircraft);
            if(!aircraft.isDematerializing()&&!aircraft.lossReported()&&!aircraft.isAlive()){var data=SimulationRuntime.data(level.getServer());if(data.state().recordPhysicalAircraftLoss(aircraft.wingId(),1,"physical_aircraft_destroyed")){aircraft.markLossReported();awardCombatService(killer,aircraft.factionId(),18,data);data.setDirty();}}
        } else if (event.getEntity() instanceof LivingRealmsShipEntity ship) {
            ShipProjectionIndex.left(ship);
            if(!ship.isDematerializing()&&!ship.lossReported()&&!ship.isAlive()){var data=SimulationRuntime.data(level.getServer());if(data.state().recordPhysicalShipLoss(ship.fleetId(),ship.shipClass(),ship.representedShips(),"physical_ship_destroyed")){ship.markLossReported();awardCombatService(killer,ship.factionId(),Math.max(12,ship.representedShips()*12.0),data);data.setDirty();}}
        } else if (event.getEntity() instanceof BountyHunterEntity hunter) {
            CrimeRuntime.report(killer,hunter.issuerFactionId(),CrimeType.MURDER,0,hunter,"killed authorized bounty hunter");
        } else if (event.getEntity() instanceof TradeCaravanEntity caravan) {
            var state=SimulationRuntime.data(level.getServer()).state();
            state.findShipment(caravan.shipmentId()).ifPresent(shipment->CrimeRuntime.report(killer,shipment.sellerFactionId(),CrimeType.ROBBERY,shipment.value(),caravan,"destroyed trade caravan"));
        }
    }

    private static void awardCombatService(ServerPlayer player,long targetFactionId,double points,LivingRealmsSavedData data) {
        var state=data.state();
        String actor=CrimeRuntime.actorKey(player);
        var standing=state.findPlayerStanding(actor).orElse(null);
        if(standing==null||!standing.isMember()||standing.memberFactionId()==targetFactionId)return;
        var memberFaction=state.findFaction(standing.memberFactionId()).orElse(null);
        if(memberFaction==null)return;
        var relation=memberFaction.relations().get(targetFactionId);
        if(relation==null||relation.status()!=dev.livingrealms.sim.faction.RelationStatus.WAR)return;
        var before=standing.rank();
        state.grantFactionService(actor,standing.memberFactionId(),points);
        player.sendSystemMessage(Component.literal("Faction service +"+Math.round(points)+" ("+Math.round(standing.servicePoints())+" total)"));
        if(standing.rank()!=before)player.sendSystemMessage(Component.literal("Promoted to "+standing.rank()+"."));
    }

    @SubscribeEvent
    public void onCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("livingrealms")
                .then(Commands.literal("wanted").executes(ctx -> {
                    ServerPlayer player=ctx.getSource().getPlayerOrException();
                    var state=SimulationRuntime.data(ctx.getSource().getServer()).state();
                    var profile=state.crimeLedger().findProfile(CrimeRuntime.actorKey(player));
                    if(profile.isEmpty()||profile.get().jurisdictions().isEmpty()){ctx.getSource().sendSuccess(()->Component.literal("No active criminal record."),false);return 1;}
                    ctx.getSource().sendSuccess(()->Component.literal("Global infamy: "+Math.round(profile.get().globalInfamy())),false);
                    profile.get().jurisdictions().values().forEach(w->{String realm=state.findFaction(w.factionId()).map(f->f.name()).orElse("Faction "+w.factionId());ctx.getSource().sendSuccess(()->Component.literal(realm+" • "+w.wantedLevel()+" • bounty "+Math.round(w.bounty())+" • heat "+Math.round(w.heat())),false);});return 1;
                }))
                .then(Commands.literal("faction").then(Commands.literal("status").executes(ctx -> {
                    ServerPlayer player=ctx.getSource().getPlayerOrException();
                    var state=SimulationRuntime.data(ctx.getSource().getServer()).state();
                    String actor=CrimeRuntime.actorKey(player);
                    var standing=state.findPlayerStanding(actor);
                    if(standing.isEmpty()){ctx.getSource().sendSuccess(()->Component.literal("No faction standing recorded yet."),false);return 1;}
                    var s=standing.get();
                    String membership=s.isMember()?state.findFaction(s.memberFactionId()).map(f->f.name()+" • rank "+s.rank()+" • service "+Math.round(s.servicePoints())).orElse("Unknown faction "+s.memberFactionId()):"No active faction membership";
                    ctx.getSource().sendSuccess(()->Component.literal(membership),false);
                    if(s.reputations().isEmpty()){ctx.getSource().sendSuccess(()->Component.literal("No faction reputation yet."),false);return 1;}
                    s.reputations().forEach((id,value)->{String name=state.findFaction(id).map(f->f.name()).orElse("Faction "+id);ctx.getSource().sendSuccess(()->Component.literal(name+" • reputation "+Math.round(value)),false);});
                    return 1;
                })).then(Commands.literal("join_here").executes(ctx -> {
                    ServerPlayer player=ctx.getSource().getPlayerOrException();
                    var data=SimulationRuntime.data(ctx.getSource().getServer());
                    var state=data.state();
                    var pos=new dev.livingrealms.sim.world.SimPosition(player.getX(),player.getZ());
                    var jurisdiction=dev.livingrealms.sim.territory.TerritoryEngine.resolve(state.factions(),pos,state.config().borderDisputeThreshold());
                    if(jurisdiction.primaryFactionId()<=0||jurisdiction.contested()){ctx.getSource().sendFailure(Component.literal("You are not inside an uncontested faction jurisdiction."));return 0;}
                    var result=state.joinFaction(CrimeRuntime.actorKey(player),jurisdiction.primaryFactionId());
                    String faction=state.findFaction(jurisdiction.primaryFactionId()).map(f->f.name()).orElse("Faction "+jurisdiction.primaryFactionId());
                    if(!result.success()){ctx.getSource().sendFailure(Component.literal("Could not join "+faction+": "+result.reason()));return 0;}
                    data.setDirty();ctx.getSource().sendSuccess(()->Component.literal("Joined "+faction+" as "+result.rank()+"."),false);return 1;
                })).then(Commands.literal("leave").executes(ctx -> {
                    ServerPlayer player=ctx.getSource().getPlayerOrException();
                    var data=SimulationRuntime.data(ctx.getSource().getServer());
                    if(!data.state().leaveFaction(CrimeRuntime.actorKey(player))){ctx.getSource().sendFailure(Component.literal("You are not a faction member."));return 0;}
                    data.setDirty();ctx.getSource().sendSuccess(()->Component.literal("You left your faction."),false);return 1;
                })))
                .then(Commands.literal("industry").executes(ctx -> {
                    ServerPlayer player=ctx.getSource().getPlayerOrException();var state=SimulationRuntime.data(ctx.getSource().getServer()).state();
                    var pos=new dev.livingrealms.sim.world.SimPosition(player.getX(),player.getZ());var jurisdiction=dev.livingrealms.sim.territory.TerritoryEngine.resolve(state.factions(),pos,state.config().borderDisputeThreshold());
                    long factionId=jurisdiction.primaryFactionId();if(factionId<=0||jurisdiction.contested()){var standing=state.findPlayerStanding(CrimeRuntime.actorKey(player)).orElse(null);factionId=standing!=null&&standing.isMember()?standing.memberFactionId():0;}
                    if(factionId<=0){ctx.getSource().sendFailure(Component.literal("No local or member faction industry to inspect."));return 0;}
                    final long id=factionId;var faction=state.findFaction(id).orElse(null);if(faction==null)return 0;var sites=state.industrialSites().stream().filter(site->site.factionId()==id).toList();
                    long active=sites.stream().filter(site->site.status()==IndustrialSiteStatus.ACTIVE).count(),starved=sites.stream().filter(site->site.status()==IndustrialSiteStatus.STARVED).count(),damaged=sites.stream().filter(site->site.status()==IndustrialSiteStatus.DAMAGED||site.status()==IndustrialSiteStatus.REPAIRING).count(),offline=sites.stream().filter(site->site.status()==IndustrialSiteStatus.OFFLINE).count();
                    ctx.getSource().sendSuccess(()->Component.literal(faction.name()+" industry • sites "+sites.size()+" • active "+active+" • starved "+starved+" • damaged/repair "+damaged+" • offline "+offline),false);
                    ctx.getSource().sendSuccess(()->Component.literal("Fuel "+Math.round(faction.stockpile().get(dev.livingrealms.sim.faction.ResourceType.FUEL))+" • machinery "+Math.round(faction.stockpile().get(dev.livingrealms.sim.faction.ResourceType.MACHINERY))+" • tools "+Math.round(faction.stockpile().get(dev.livingrealms.sim.faction.ResourceType.TOOLS))+" • ammunition "+Math.round(faction.stockpile().get(dev.livingrealms.sim.faction.ResourceType.AMMUNITION))),false);
                    sites.stream().sorted(java.util.Comparator.comparingLong(IndustrialSite::id)).limit(8).forEach(site->{String settlement=state.findSettlement(site.settlementId()).map(x->x.name()).orElse("Settlement "+site.settlementId());ctx.getSource().sendSuccess(()->Component.literal("#"+site.id()+" "+site.kind()+" @ "+settlement+" • L"+site.level()+" • "+site.status()+" • condition "+Math.round(site.condition()*100)+"% • utilization "+Math.round(site.lastUtilization()*100)+"%"),false);});
                    return 1;
                }))
                .then(Commands.literal("bounty")
                    .then(Commands.literal("board").executes(ctx -> {
                        ServerPlayer player=ctx.getSource().getPlayerOrException();var state=SimulationRuntime.data(ctx.getSource().getServer()).state();String actor=CrimeRuntime.actorKey(player);
                        var jurisdiction=dev.livingrealms.sim.territory.TerritoryEngine.resolve(state.factions(),new dev.livingrealms.sim.world.SimPosition(player.getX(),player.getZ()),state.config().borderDisputeThreshold());
                        if(jurisdiction.primaryFactionId()<=0||jurisdiction.contested()){ctx.getSource().sendFailure(Component.literal("No usable bounty board in wilderness/contested territory."));return 0;}
                        long factionId=jurisdiction.primaryFactionId();var contracts=state.bounties().stream().filter(b->b.issuerFactionId()==factionId).filter(b->b.status()==dev.livingrealms.sim.law.BountyContract.Status.OPEN||b.hunterKey().equals(actor)).sorted(java.util.Comparator.comparingDouble(dev.livingrealms.sim.law.BountyContract::reward).reversed()).toList();
                        String realm=state.findFaction(factionId).map(f->f.name()).orElse("Faction "+factionId);ctx.getSource().sendSuccess(()->Component.literal(realm+" bounty board • "+contracts.size()+" active"),false);
                        for(var b:contracts)ctx.getSource().sendSuccess(()->Component.literal("#"+b.id()+" • target "+b.actorKey()+" • reward "+Math.round(b.reward())+" • "+b.status()),false);return 1;
                    }))
                    .then(Commands.literal("accept").executes(ctx -> {
                        ServerPlayer player=ctx.getSource().getPlayerOrException();var data=SimulationRuntime.data(ctx.getSource().getServer());var state=data.state();String actor=CrimeRuntime.actorKey(player);
                        var jurisdiction=dev.livingrealms.sim.territory.TerritoryEngine.resolve(state.factions(),new dev.livingrealms.sim.world.SimPosition(player.getX(),player.getZ()),state.config().borderDisputeThreshold());
                        if(jurisdiction.primaryFactionId()<=0||jurisdiction.contested()){ctx.getSource().sendFailure(Component.literal("Stand inside an uncontested realm to accept its bounties."));return 0;}
                        var contract=state.bounties().stream().filter(b->b.issuerFactionId()==jurisdiction.primaryFactionId()&&b.status()==dev.livingrealms.sim.law.BountyContract.Status.OPEN&&!b.actorKey().equals(actor)).max(java.util.Comparator.comparingDouble(dev.livingrealms.sim.law.BountyContract::reward)).orElse(null);
                        if(contract==null){ctx.getSource().sendFailure(Component.literal("No open bounty is available here."));return 0;}var result=state.acceptBounty(contract.id(),actor);if(!result.success()){ctx.getSource().sendFailure(Component.literal("Could not accept bounty: "+result.reason()));return 0;}data.setDirty();ctx.getSource().sendSuccess(()->Component.literal("Accepted bounty #"+contract.id()+" on "+contract.actorKey()+" for "+Math.round(contract.reward())+"."),false);return 1;
                    }))
                    .then(Commands.literal("active").executes(ctx -> {
                        ServerPlayer player=ctx.getSource().getPlayerOrException();var state=SimulationRuntime.data(ctx.getSource().getServer()).state();String actor=CrimeRuntime.actorKey(player);var contract=state.bounties().stream().filter(b->b.status()==dev.livingrealms.sim.law.BountyContract.Status.ASSIGNED&&b.hunterKey().equals(actor)).findFirst().orElse(null);if(contract==null){ctx.getSource().sendSuccess(()->Component.literal("No active bounty contract."),false);return 1;}ctx.getSource().sendSuccess(()->Component.literal("Active bounty #"+contract.id()+" • target "+contract.actorKey()+" • reward "+Math.round(contract.reward())),false);return 1;
                    }))
                    .then(Commands.literal("abandon").executes(ctx -> {
                        ServerPlayer player=ctx.getSource().getPlayerOrException();var data=SimulationRuntime.data(ctx.getSource().getServer());var state=data.state();String actor=CrimeRuntime.actorKey(player);var contract=state.bounties().stream().filter(b->b.status()==dev.livingrealms.sim.law.BountyContract.Status.ASSIGNED&&b.hunterKey().equals(actor)).findFirst().orElse(null);if(contract==null){ctx.getSource().sendFailure(Component.literal("You have no assigned bounty."));return 0;}var result=state.abandonBounty(contract.id(),actor);if(!result.success()){ctx.getSource().sendFailure(Component.literal("Could not abandon bounty: "+result.reason()));return 0;}data.setDirty();ctx.getSource().sendSuccess(()->Component.literal("Bounty #"+contract.id()+" returned to the board."),false);return 1;
                    })))
                .then(Commands.literal("assist")
                    .then(Commands.literal("board").executes(ctx -> {
                        ServerPlayer player=ctx.getSource().getPlayerOrException();
                        var state=SimulationRuntime.data(ctx.getSource().getServer()).state();
                        String board=PlayerAssistanceRuntime.describeNearby(state,new SimPosition(player.getX(),player.getZ()));
                        ctx.getSource().sendSuccess(()->Component.literal(board),false);
                        return 1;
                    }))
                    .then(Commands.literal("deliver").executes(ctx -> {
                        ServerPlayer player=ctx.getSource().getPlayerOrException();
                        var data=SimulationRuntime.data(ctx.getSource().getServer());
                        var result=PlayerAssistanceRuntime.contributeNearest(data.state(),CrimeRuntime.actorKey(player),player,new SimPosition(player.getX(),player.getZ()));
                        if(!result.success()){ctx.getSource().sendFailure(Component.literal(result.message()));return 0;}
                        if(result.dirty())data.setDirty();
                        ctx.getSource().sendSuccess(()->Component.literal(result.message()),false);
                        return 1;
                    }))
                    .then(Commands.argument("taskId", LongArgumentType.longArg(1L)).executes(ctx -> {
                        ServerPlayer player=ctx.getSource().getPlayerOrException();
                        var data=SimulationRuntime.data(ctx.getSource().getServer());
                        long taskId=LongArgumentType.getLong(ctx,"taskId");
                        var result=PlayerAssistanceRuntime.contribute(data.state(),CrimeRuntime.actorKey(player),player,new SimPosition(player.getX(),player.getZ()),taskId);
                        if(!result.success()){ctx.getSource().sendFailure(Component.literal(result.message()));return 0;}
                        if(result.dirty())data.setDirty();
                        ctx.getSource().sendSuccess(()->Component.literal(result.message()),false);
                        return 1;
                    })))
                .then(Commands.literal("found")
                    .then(Commands.argument("name", StringArgumentType.greedyString()).executes(ctx -> {
                        ServerPlayer player=ctx.getSource().getPlayerOrException();
                        var data=SimulationRuntime.data(ctx.getSource().getServer());
                        String actor=CrimeRuntime.actorKey(player);
                        String name=StringArgumentType.getString(ctx,"name");
                        var result=PlayerSettlementFounder.found(data.state(),actor,player.getGameProfile().getName(),name,new SimPosition(player.getX(),player.getZ()));
                        if(!result.success()){ctx.getSource().sendFailure(Component.literal("Could not found settlement: "+result.reason()));return 0;}
                        data.setDirty();
                        ctx.getSource().sendSuccess(()->Component.literal("Founded "+result.settlementName()+" • "+result.realmName()+". Citizens can now immigrate, build and enter diplomacy/war like any other realm."),true);
                        return 1;
                    })))
                .then(Commands.literal("locate")
                    .then(Commands.literal("mine").executes(ctx -> locateByQuery(ctx.getSource(), "mine")))
                    .then(Commands.literal("settlement").executes(ctx -> locateNearest(ctx.getSource(), settlement -> true, "settlement")))
                    .then(Commands.literal("city").executes(ctx -> locateByQuery(ctx.getSource(), "city")))
                    .then(Commands.literal("town").executes(ctx -> locateNearest(ctx.getSource(), settlement -> settlement.tier() == Settlement.Tier.TOWN, "town")))
                    .then(Commands.literal("village").executes(ctx -> locateNearest(ctx.getSource(), settlement -> settlement.tier() == Settlement.Tier.VILLAGE, "village")))
                    .then(Commands.literal("hamlet").executes(ctx -> locateNearest(ctx.getSource(), settlement -> settlement.tier() == Settlement.Tier.HAMLET, "hamlet")))
                    .then(Commands.literal("kingdom").executes(ctx -> locateByQuery(ctx.getSource(), "kingdom")))
                    .then(Commands.literal("market").executes(ctx -> locateByQuery(ctx.getSource(), "market")))
                    .then(Commands.literal("port").executes(ctx -> locateByQuery(ctx.getSource(), "port")))
                    .then(Commands.literal("wizardtrees").executes(ctx -> locateByQuery(ctx.getSource(), "wizardtrees")))
                    .then(Commands.literal("ruin").executes(ctx -> locateByQuery(ctx.getSource(), "ruin"))))
                .then(Commands.literal("status").requires(src -> src.hasPermission(2)).executes(ctx -> {
                    var state = SimulationRuntime.data(ctx.getSource().getServer()).state();
                    ctx.getSource().sendSuccess(() -> Component.literal(state.summary()), false);
                    return 1;
                }))
                .then(Commands.literal("setday").requires(src -> src.hasPermission(2))
                    .then(Commands.argument("day", LongArgumentType.longArg(0L)).executes(ctx -> {
                        var data=SimulationRuntime.data(ctx.getSource().getServer());
                        long target=LongArgumentType.getLong(ctx,"day"),before=data.state().clock().day();
                        try {
                            long advanced=data.state().advanceToDay(target);
                            if(advanced>0){data.setDirty();dev.livingrealms.minecraft.construction.SettlementConstructionMaterializer.requestCatchup(advanced);}
                            final long moved=advanced;
                            ctx.getSource().sendSuccess(()->Component.literal("Living Realms progressed "+moved+" day(s): "+before+" -> "+data.state().clock().day()+" • "+data.state().summary()),true);
                            return 1;
                        } catch(IllegalArgumentException ex) {
                            ctx.getSource().sendFailure(Component.literal(ex.getMessage()));
                            return 0;
                        }
                    })))
                .then(Commands.literal("advance").requires(src -> src.hasPermission(2))
                    .then(Commands.argument("days", LongArgumentType.longArg(1L, dev.livingrealms.sim.world.SimulationState.MAX_MANUAL_DAY_JUMP)).executes(ctx -> {
                        var data=SimulationRuntime.data(ctx.getSource().getServer());
                        long days=LongArgumentType.getLong(ctx,"days"),before=data.state().clock().day();
                        try {
                            data.state().advanceToDay(before+days);
                            data.setDirty();dev.livingrealms.minecraft.construction.SettlementConstructionMaterializer.requestCatchup(days);
                            ctx.getSource().sendSuccess(()->Component.literal("Living Realms progressed "+days+" day(s): "+before+" -> "+data.state().clock().day()+" • "+data.state().summary()),true);
                            return 1;
                        } catch(IllegalArgumentException ex) {
                            ctx.getSource().sendFailure(Component.literal(ex.getMessage()));
                            return 0;
                        }
                    })))
                .then(Commands.literal("advance_day").requires(src -> src.hasPermission(2)).executes(ctx -> {
                    var data = SimulationRuntime.data(ctx.getSource().getServer());
                    data.state().advanceDays(1);
                    data.setDirty();dev.livingrealms.minecraft.construction.SettlementConstructionMaterializer.requestCatchup(1);
                    ctx.getSource().sendSuccess(() -> Component.literal("Advanced: " + data.state().summary()), true);
                    return 1;
                }))
        );
    }

    private static int locateByQuery(net.minecraft.commands.CommandSourceStack source,String kind) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player=source.getPlayerOrException();
        var data=SimulationRuntime.data(source.getServer());
        var state=data.state();
        SimPosition from=new SimPosition(player.getX(),player.getZ());
        // Seed a far-world outpost if the player is in a civilization desert so locate has something to find later.
        if(dev.livingrealms.sim.world.FrontierExplorationSeeder.ensureNear(state,from)>0)data.setDirty();
        java.util.Optional<dev.livingrealms.sim.world.LocateQuery.Hit> hit=switch(kind){
            case "city" -> dev.livingrealms.sim.world.LocateQuery.nearestCity(state,from);
            case "mine" -> dev.livingrealms.sim.world.LocateQuery.nearestMine(state,from);
            case "kingdom" -> dev.livingrealms.sim.world.LocateQuery.nearestKingdom(state,from);
            case "market" -> dev.livingrealms.sim.world.LocateQuery.nearestMarket(state,from);
            case "port" -> {
                var port=dev.livingrealms.sim.world.LocateQuery.nearestPort(state,from);
                if(port.isPresent())yield port;
                // Fall back to nearest coastal/ship-suitable settlement so far ports still resolve.
                yield dev.livingrealms.sim.world.LocateQuery.nearestSettlement(state,from,s->s.geography().shipSuitable(),"port");
            }
            case "wizardtrees" -> dev.livingrealms.sim.world.LocateQuery.nearestWizardTrees(state,from);
            case "ruin" -> dev.livingrealms.sim.world.LocateQuery.nearestRuin(state,from);
            default -> java.util.Optional.empty();
        };
        if(hit.isEmpty()){source.sendFailure(Component.literal("No Living Realms "+kind+" exists in the canonical world state yet. Explore farther or wait for discovery — locate searches the whole world with no distance limit."));return 0;}
        var found=hit.get();
        final int x=(int)Math.round(found.x()),z=(int)Math.round(found.z());
        final long distance=Math.round(found.distance());
        source.sendSuccess(()->Component.literal("Nearest "+found.label()+": "+found.name()+" • "+found.type()+" • "+found.factionName()+" • X "+x+" Z "+z+" • "+distance+" blocks"+(distance>2_000?" (far — travel or Waystone)":"")),false);
        return 1;
    }

    private static int locateOwnSettlement(net.minecraft.commands.CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player=source.getPlayerOrException();var state=SimulationRuntime.data(source.getServer()).state();
        var standing=state.findPlayerStanding(CrimeRuntime.actorKey(player)).orElse(null);
        if(standing==null||!standing.isMember()){source.sendFailure(Component.literal("You do not currently belong to a Living Realms faction."));return 0;}
        Faction faction=state.findFaction(standing.memberFactionId()).orElse(null);
        if(faction==null||faction.settlements().isEmpty()){source.sendFailure(Component.literal("Your faction has no settlement."));return 0;}
        Settlement nearest=faction.settlements().stream().min(java.util.Comparator.comparingDouble(s->s.position().distanceTo(new SimPosition(player.getX(),player.getZ())))).orElseThrow();
        final int x=(int)Math.round(nearest.position().x()),z=(int)Math.round(nearest.position().z());
        source.sendSuccess(()->Component.literal("Your nearest settlement: "+nearest.name()+" • "+faction.name()+" • X "+x+" Z "+z),false);return 1;
    }

    private static int locateNearest(net.minecraft.commands.CommandSourceStack source, java.util.function.Predicate<Settlement> filter, String label) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player=source.getPlayerOrException();
        var data=SimulationRuntime.data(source.getServer());
        var state=data.state();
        SimPosition from=new SimPosition(player.getX(),player.getZ());
        if(dev.livingrealms.sim.world.FrontierExplorationSeeder.ensureNear(state,from)>0)data.setDirty();
        var hit=dev.livingrealms.sim.world.LocateQuery.nearestSettlement(state,from,filter,label);
        if(hit.isEmpty()){source.sendFailure(Component.literal("No Living Realms "+label+" exists in the canonical world state yet. Locate searches the entire world — if it exists anywhere, it will be found."));return 0;}
        var found=hit.get();
        final long distance=Math.round(found.distance());
        final int x=(int)Math.round(found.x()),z=(int)Math.round(found.z());
        source.sendSuccess(()->Component.literal("Nearest "+label+": "+found.name()+" • "+found.type()+" • "+found.factionName()+" • X "+x+" Z "+z+" • "+distance+" blocks"+(distance>2_000?" (far — travel or Waystone)":"")),false);
        return 1;
    }

}