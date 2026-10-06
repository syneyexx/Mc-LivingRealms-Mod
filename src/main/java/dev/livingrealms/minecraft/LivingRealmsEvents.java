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
import dev.livingrealms.minecraft.construction.TransportNetworkMaterializer;
import dev.livingrealms.minecraft.construction.CivilizationFabricChunkQueue;
import dev.livingrealms.minecraft.construction.SettlementGeographyDiscoveryRuntime;
import dev.livingrealms.minecraft.worldgen.StarterCivilizationWorldgenContext;
import dev.livingrealms.minecraft.worldgen.ModWorldgenAttachments;
import dev.livingrealms.minecraft.ForeignStructureDiscoveryRuntime;
import dev.livingrealms.minecraft.construction.HistoricalSiteMaterializer;
import dev.livingrealms.minecraft.construction.IndustrialSiteMaterializer;
import dev.livingrealms.minecraft.construction.OutlyingSiteMaterializer;
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
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.minecraft.server.level.ServerLevel;

public final class LivingRealmsEvents {
    private final LivingRealmsRuntimeScheduler runtimeScheduler = new LivingRealmsRuntimeScheduler();

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        runtimeScheduler.tick(event.getServer());
    }

    @SubscribeEvent
    public void onLevelLoad(LevelEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) return;
        StarterCivilizationWorldgenContext.activate(level, SimulationRuntime.data(level.getServer()));
    }

    @SubscribeEvent
    public void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) return;
        // Do not touch chunk blocks here. NeoForge 1.21.1 may fire Load before FULL promotion.
        // Only hand coordinates to the normal server-tick materializers.
        var pos = event.getChunk().getPos();
        CivilizationFabricChunkQueue.onChunkAvailable(level, pos.x, pos.z);
    }


    @SubscribeEvent
    public void onEntityJoin(EntityJoinLevelEvent event) {
        ProjectionEntityLifecycleBridge.onEntityJoin(event);
    }

    @SubscribeEvent
    public void onEntityLeave(EntityLeaveLevelEvent event) {
        ProjectionEntityLifecycleBridge.onEntityLeave(event);
    }

    @SubscribeEvent
    public void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if(!(event.getEntity() instanceof ServerPlayer player))return;
        if(event.getTarget() instanceof FactionCitizenEntity citizen){DialogueSessionRuntime.open(player,citizen);event.setCanceled(true);return;}
        // Vanilla villagers and compatible villager-derived NPCs are adopted into the same social/dialogue system.
        // Sneak-interact intentionally keeps the vanilla/modded trading interaction available.
        if(event.getTarget() instanceof AbstractVillager villager&&!player.isShiftKeyDown()){DialogueSessionRuntime.open(player,villager);event.setCanceled(true);return;}
        // Broader allowlisted civilian adapters (Better Villages / village-derived humanoids). Never hostiles.
        if(!player.isShiftKeyDown()&&dev.livingrealms.minecraft.compat.npc.CivilianNpcAdapter.INSTANCE.isAdoptable(event.getTarget())){
            DialogueSessionRuntime.openAdopted(player,event.getTarget(),dev.livingrealms.minecraft.compat.npc.CivilianNpcAdapter.INSTANCE.inferRole(event.getTarget()));
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
        var data=SimulationRuntime.data(level.getServer());
        ModWorldgenAttachments.forgetAt(level, event.getPos());
        if (data.authoredBlocks().forget(
                event.getPos().getX(), event.getPos().getY(), event.getPos().getZ())) {
            data.setDirty();
        }
        PlayerStructureRevalidationRuntime.onBlockChanged(level, event.getPos());
        var state=data.state();double x=event.getPos().getX()+.5,z=event.getPos().getZ()+.5;
        IndustrySitePlanner.Site nearest=null;double best=7.0D*7.0D;
        for(var faction:state.factions())for(IndustrySitePlanner.Site site:IndustrySitePlanner.plan(faction)){double dx=site.center().x()-x,dz=site.center().z()-z,d=dx*dx+dz*dz;if(d<best){best=d;nearest=site;}}
        if(nearest==null)return;
        final IndustrySitePlanner.Site nearestSite=nearest;
        IndustrialSite canonical=state.industrialSites().stream().filter(s->s.factionId()==nearestSite.factionId()&&s.settlementId()==nearestSite.settlementId()&&s.kind()==nearestSite.kind()).findFirst().orElse(null);
        if(canonical==null)return;
        if(state.recordIndustrialDamage(canonical.id(),.018,0,"player_block_break")){data.setDirty();CrimeRuntime.report(player,canonical.factionId(),CrimeType.SABOTAGE,25,null,"damaged industrial site "+canonical.id());}
    }

    @SubscribeEvent
    public void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (event.getEntity() instanceof ServerPlayer) {
            var data = SimulationRuntime.data(level.getServer());
            ModWorldgenAttachments.forgetAt(level, event.getPos());
            if (data.authoredBlocks().forget(
                    event.getPos().getX(), event.getPos().getY(), event.getPos().getZ())) {
                data.setDirty();
            }
        }
        PlayerStructureRevalidationRuntime.onBlockChanged(level, event.getPos());
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
        RegionalImpostorIndex.clear();
        DashboardRequestLimiter.clear();
        DashboardActionLimiter.clear();
        DialogueRequestLimiter.clear();
        DialogueSessionRuntime.clear();
        FactionContainerTheftRuntime.clear();
        SettlementConstructionMaterializer.clear();
        TransportNetworkMaterializer.clear();
        CivilizationFabricChunkQueue.clear();
        PlayerStructureRevalidationRuntime.clear();
        SettlementGeographyDiscoveryRuntime.clear();
        HistoricalSiteMaterializer.clear();
        IndustrialSiteMaterializer.clear();
        RoadsideSiteMaterializer.clear();
        OutlyingSiteMaterializer.clear();
        CivicFestivalMaterializer.clear();
        CivicChoreographyRuntime.clear();
        SeasonalFarmPresentationRuntime.clear();
        ForeignStructureDiscoveryRuntime.clear();
        StarterCivilizationWorldgenContext.clear();
        dev.livingrealms.minecraft.player.PlayerOnboardingRuntime.clear();
        SimulationRuntime.data(event.getServer()).dayAdvanceScheduler().clear();
        runtimeScheduler.reset();
    }

    @SubscribeEvent
    public void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new SpeciesReloadListener());
    }

    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity().level() instanceof ServerLevel level)) return;
        if (event.getSource().getEntity() instanceof FactionCitizenEntity hunter && hunter.role()==CitizenRole.HUNTER && event.getEntity() instanceof LivingRealmsAnimalEntity animal) {
            // Physical hunt is presentation only. Canonical hunting yield belongs to ecology/economy
            // simulation — never mint FOOD from a loaded-chunk death event.
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
        LivingRealmsCommands.register(event, runtimeScheduler);
    }

}