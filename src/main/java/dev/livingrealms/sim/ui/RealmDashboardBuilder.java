package dev.livingrealms.sim.ui;

import dev.livingrealms.sim.faction.*;
import dev.livingrealms.sim.civilization.AssistanceTask;
import dev.livingrealms.sim.civilization.*;
import dev.livingrealms.sim.economy.MarketEngine;
import dev.livingrealms.sim.economy.MarketTransactionEngine;
import dev.livingrealms.sim.config.*;
import dev.livingrealms.sim.diplomacy.*;
import dev.livingrealms.sim.aviation.*;
import dev.livingrealms.sim.naval.*;
import dev.livingrealms.sim.military.*;
import dev.livingrealms.sim.law.*;
import dev.livingrealms.sim.player.PlayerStanding;
import dev.livingrealms.sim.society.SocietyDiagnostics;
import dev.livingrealms.sim.society.WorldCauseExplainer;
import dev.livingrealms.sim.territory.*;
import dev.livingrealms.sim.world.*;
import java.util.*;

/** Builds bounded player-specific dashboard snapshots from canonical server state. */
public final class RealmDashboardBuilder {
    public static final int MAX_FACTIONS=32;
    public static final int MAX_SETTLEMENTS=24;
    public static final int MAX_WARS=16;
    public static final int MAX_HISTORY=40;
    public static final int MAX_MAP_SETTLEMENTS=256;
    public static final int MAX_MAP_ROUTES=384;
    public static final int MAX_MAP_ARMIES=64;
    public static final int MAX_MAP_FRONTS=24;
    public static final int MAX_MAP_RESOURCE_CLAIMS=256;
    public static final int MAX_MAP_SHIPMENTS=96;
    public static final int MAX_MAP_RAIDS=64;
    public static final int MAX_MAP_MIGRATIONS=64;
    public static final int MAX_MAP_PORTS=64;
    public static final int MAX_MAP_FLEETS=64;
    public static final int MAX_MAP_AIR_WINGS=64;
    public static final int MAX_MAP_PIRATES=64;
    public static final int MAX_MAP_PIRATE_HIDEOUTS=64;
    public static final int MAX_MAP_EPIDEMICS=64;
    public static final int MAX_MAP_CACHES=64;
    public static final int MAX_MAP_RUINS=96;
    public static final int MAX_BOUNTIES=16;
    public static final int MAX_OBJECTIVES=24;
    public static final int MAX_SIEGES=16;
    public static final int MAX_OPERATION_SHIPMENTS=24;
    public static final int MAX_OPERATION_ROUTES=24;
    public static final int MAX_OPERATION_INDUSTRY=24;
    public static final int MAX_ASSISTANCE_TASKS=16;
    public static final int MAX_ECOLOGY_REGIONS=48;
    public static final int MAX_ECOLOGY_SPECIES_PER_REGION=6;
    public static final int MAX_FORCE_AIR_WINGS=24;
    public static final int MAX_FORCE_FLEETS=24;
    public static final int MAX_FORCE_PORTS=24;
    public static final int MAX_POLITICS_RELATIONS=24;
    public static final int MAX_POLITICS_TREATIES=24;

    private RealmDashboardBuilder() {}

    public static RealmDashboardSnapshot build(SimulationState state,String actorKey,SimPosition position){
        Objects.requireNonNull(state,"state");
        if(actorKey==null||actorKey.isBlank())throw new IllegalArgumentException("actorKey");
        Objects.requireNonNull(position,"position");

        Jurisdiction jurisdiction=TerritoryEngine.resolve(state.factions(),position);
        PlayerStanding standing=state.findPlayerStanding(actorKey).orElse(null);
        long memberId=standing==null?0:standing.memberFactionId();
        long localId=jurisdiction.claimed()?jurisdiction.primaryFactionId():0;
        long realmId=localId>0?localId:memberId;
        Faction realm=realmId>0?state.findFaction(realmId).orElse(null):null;

        RealmDashboardSnapshot.JurisdictionView jurisdictionView=new RealmDashboardSnapshot.JurisdictionView(
                jurisdiction.primaryFactionId(),nameOf(state,jurisdiction.primaryFactionId()),jurisdiction.secondaryFactionId(),nameOf(state,jurisdiction.secondaryFactionId()),jurisdiction.claimed(),jurisdiction.contested());
        RealmDashboardSnapshot.PlayerView playerView=playerView(state,actorKey,standing,realmId);
        RealmDashboardSnapshot.RealmView realmView=realm==null?RealmDashboardSnapshot.RealmView.none():realmView(state,realm);
        RealmDashboardSnapshot.SettingsView settingsView=settingsView(state.config());

        List<RealmDashboardSnapshot.FactionSummary> factions=state.factions().stream()
                .sorted(Comparator.comparingInt(Faction::population).reversed().thenComparingLong(Faction::id))
                .limit(MAX_FACTIONS)
                .map(f->new RealmDashboardSnapshot.FactionSummary(f.id(),f.name(),f.rulerName(),f.population(),f.settlements().size(),f.treasury(),f.technology(),f.id()==localId,f.id()==memberId))
                .toList();

        List<RealmDashboardSnapshot.SettlementView> settlements=realm==null?List.of():realm.settlements().stream()
                .sorted(Comparator.comparingDouble(s->s.position().distanceTo(position)))
                .limit(MAX_SETTLEMENTS)
                .map(s->{var assessment=SocietyDiagnostics.assess(realm,s);var needs=assessment.needs();String cause=WorldCauseExplainer.compact(WorldCauseExplainer.settlementPressureCause(state,realm,s));return new RealmDashboardSnapshot.SettlementView(s.id(),s.name(),s.tier().name(),s.developmentPriority().name(),s.population(),s.housing(),s.prosperity(),s.unrest(),s.foodSecurity(),s.publicOrder(),s.employment(),needs.housing(),needs.goods(),assessment.satisfaction(),assessment.primaryPressure().name(),assessment.pressureSeverity(),cause,s.position().distanceTo(position));})
                .toList();

        List<RealmDashboardSnapshot.WarView> wars=state.wars().stream().filter(w->w.active()&&(realmId<=0||w.involves(realmId)))
                .sorted(Comparator.comparingLong(dev.livingrealms.sim.diplomacy.WarState::startDay).reversed())
                .limit(MAX_WARS)
                .map(w->new RealmDashboardSnapshot.WarView(w.id(),w.attackerFactionId(),nameOf(state,w.attackerFactionId()),w.defenderFactionId(),nameOf(state,w.defenderFactionId()),w.goal().name(),w.startDay(),w.attackerScore(),w.attackerExhaustion(),w.defenderExhaustion()))
                .toList();

        RealmDashboardSnapshot.WarfareView warfare=warfareView(state,realmId,position);
        List<RealmDashboardSnapshot.BountyView> bounties=bountyViews(state,actorKey,jurisdiction);
        RealmDashboardSnapshot.OperationsView operations=operationsView(state,realmId);
        RealmDashboardSnapshot.EcologyView ecology=ecologyView(state,position);
        RealmDashboardSnapshot.PoliticsView politics=politicsView(state,realmId);
        RealmDashboardSnapshot.ForcesView forces=forcesView(state,realmId,position);
        RealmDashboardSnapshot.StrategicMapView map=mapView(state,position,memberId);

        List<RealmDashboardSnapshot.HistoryView> history=state.history().recent(MAX_HISTORY).stream()
                .map(e->new RealmDashboardSnapshot.HistoryView(e.day(),e.type(),e.message())).toList();

        return new RealmDashboardSnapshot(RealmDashboardSnapshot.PROTOCOL_VERSION,state.clock().day(),state.summary(),jurisdictionView,playerView,realmView,settingsView,factions,settlements,wars,warfare,bounties,operations,ecology,politics,forces,map,history);
    }




    private static RealmDashboardSnapshot.SettingsView settingsView(SimulationConfig c){
        return new RealmDashboardSnapshot.SettingsView(SimulationPreset.labelFor(c),c.physicalRadiusBlocks(),c.regionalRadiusBlocks(),c.maxPhysicalWildlife(),c.maxPhysicalCaravans(),c.maxPhysicalMilitaryEntities(),c.maxPhysicalNavalEntities(),c.constructionBlockOpsPerTick());
    }

    private static RealmDashboardSnapshot.WarfareView warfareView(SimulationState state,long realmId,SimPosition player){
        if(realmId<=0)return RealmDashboardSnapshot.WarfareView.empty();
        List<RealmDashboardSnapshot.ObjectiveView> objectives=state.objectives().stream()
                .filter(o->!o.complete()&&o.ownerFactionId()==realmId)
                .sorted(Comparator.comparingInt(MilitaryObjective::priority).reversed().thenComparingLong(MilitaryObjective::id))
                .limit(MAX_OBJECTIVES)
                .map(o->new RealmDashboardSnapshot.ObjectiveView(o.id(),o.armyId(),o.type().name(),objectiveTargetName(state,o),o.priority(),o.targetPosition().distanceTo(player))).toList();
        List<RealmDashboardSnapshot.SiegeView> sieges=state.sieges().stream()
                .filter(SiegeState::active)
                .filter(s->s.attackerFactionId()==realmId||s.defenderFactionId()==realmId)
                .sorted(Comparator.comparingLong(SiegeState::startDay).thenComparingLong(SiegeState::id))
                .limit(MAX_SIEGES)
                .map(s->new RealmDashboardSnapshot.SiegeView(s.id(),nameOf(state,s.attackerFactionId()),nameOf(state,s.defenderFactionId()),settlementName(state,s.settlementId()),s.startDay(),s.progress(),s.blockade())).toList();
        return new RealmDashboardSnapshot.WarfareView(objectives,sieges);
    }

    private static String objectiveTargetName(SimulationState state,MilitaryObjective objective){
        if(objective.targetSettlementId()>0)return settlementName(state,objective.targetSettlementId());
        if(objective.targetFactionId()>0)return nameOf(state,objective.targetFactionId());
        return Math.round(objective.targetPosition().x())+","+Math.round(objective.targetPosition().z());
    }

    private static RealmDashboardSnapshot.OperationsView operationsView(SimulationState state,long realmId){
        if(realmId<=0)return RealmDashboardSnapshot.OperationsView.empty();
        List<RealmDashboardSnapshot.ShipmentView> shipments=state.shipments().stream()
                .filter(s->s.sellerFactionId()==realmId||s.buyerFactionId()==realmId)
                .sorted(Comparator.comparingDouble(dev.livingrealms.sim.logistics.TradeShipment::progress).thenComparingLong(dev.livingrealms.sim.logistics.TradeShipment::id))
                .limit(MAX_OPERATION_SHIPMENTS)
                .map(s->new RealmDashboardSnapshot.ShipmentView(s.id(),nameOf(state,s.sellerFactionId()),nameOf(state,s.buyerFactionId()),s.resource().name(),s.amount(),s.value(),s.progress(),s.distance())).toList();
        List<RealmDashboardSnapshot.RouteOpsView> routes=state.routes().stream().filter(r->r.ownerFactionId()==realmId)
                .sorted(Comparator.comparingLong(dev.livingrealms.sim.transport.TransportRoute::id)).limit(MAX_OPERATION_ROUTES)
                .map(r->new RealmDashboardSnapshot.RouteOpsView(r.id(),settlementName(state,r.fromSettlementId()),settlementName(state,r.toSettlementId()),r.mode().name(),r.quality(),r.security(),r.capacityPerDay(),r.operational())).toList();
        List<RealmDashboardSnapshot.IndustryOpsView> industry=state.industrialSites().stream().filter(i->i.factionId()==realmId)
                .sorted(Comparator.comparingLong(dev.livingrealms.sim.industry.IndustrialSite::id)).limit(MAX_OPERATION_INDUSTRY)
                .map(i->new RealmDashboardSnapshot.IndustryOpsView(i.id(),settlementName(state,i.settlementId()),i.kind().name(),i.level(),i.status().name(),i.condition(),i.starvedDays(),i.downtimeDays(),i.lastCycles(),i.lastUtilization())).toList();
        List<RealmDashboardSnapshot.AssistanceTaskView> tasks=state.assistanceTasks().stream()
                .filter(AssistanceTask::active)
                .filter(t->t.factionId()==realmId)
                .sorted(Comparator.comparingDouble(AssistanceTask::remainingPressure).reversed().thenComparingLong(AssistanceTask::id))
                .limit(MAX_ASSISTANCE_TASKS)
                .map(t->new RealmDashboardSnapshot.AssistanceTaskView(
                        t.id(),settlementName(state,t.settlementId()),t.type().name(),t.causeKey(),
                        t.remainingPressure(),t.progress(),t.expiresDay()))
                .toList();
        return new RealmDashboardSnapshot.OperationsView(shipments,routes,industry,tasks);
    }

    private static String settlementName(SimulationState state,long id){return state.findSettlement(id).map(Settlement::name).orElse("Settlement #"+id);}

    private static List<RealmDashboardSnapshot.BountyView> bountyViews(SimulationState state,String actorKey,Jurisdiction jurisdiction){
        if(!jurisdiction.claimed()||jurisdiction.contested())return List.of();
        long factionId=jurisdiction.primaryFactionId();
        return state.bounties().stream()
                .filter(b->b.issuerFactionId()==factionId)
                .filter(b->b.status()==BountyContract.Status.OPEN||(b.status()==BountyContract.Status.ASSIGNED&&b.hunterKey().equals(actorKey)))
                .filter(b->!b.actorKey().equals(actorKey))
                .sorted(Comparator.<BountyContract,Boolean>comparing(b->!b.hunterKey().equals(actorKey)).thenComparing(Comparator.comparingDouble(BountyContract::reward).reversed()).thenComparingLong(BountyContract::id))
                .limit(MAX_BOUNTIES)
                .map(b->new RealmDashboardSnapshot.BountyView(b.id(),b.actorKey(),b.issuerFactionId(),nameOf(state,b.issuerFactionId()),b.reward(),b.status().name(),b.hunterKey().equals(actorKey)))
                .toList();
    }



    private static RealmDashboardSnapshot.PoliticsView politicsView(SimulationState state,long realmId){
        if(realmId<=0)return RealmDashboardSnapshot.PoliticsView.empty();
        Faction realm=state.findFaction(realmId).orElse(null);
        if(realm==null)return RealmDashboardSnapshot.PoliticsView.empty();
        List<RealmDashboardSnapshot.RelationView> relations=state.factions().stream()
                .filter(f->f.id()!=realmId)
                .map(other->{
                    DiplomaticRelation relation=realm.relations().get(other.id());
                    boolean atWar=state.wars().stream().anyMatch(w->w.active()&&w.involves(realmId)&&w.involves(other.id()));
                    String status=atWar?RelationStatus.WAR.name():(relation==null?RelationStatus.NEUTRAL.name():relation.status().name());
                    double opinion=relation==null?0:relation.opinion();
                    boolean trade=relation!=null&&relation.tradeAgreement()&&!atWar;
                    return new RealmDashboardSnapshot.RelationView(other.id(),other.name(),status,opinion,trade);
                })
                .sorted(Comparator.comparingInt((RealmDashboardSnapshot.RelationView v)->relationPriority(v.status())).thenComparingDouble(RealmDashboardSnapshot.RelationView::opinion).thenComparing(RealmDashboardSnapshot.RelationView::factionName))
                .limit(MAX_POLITICS_RELATIONS).toList();
        List<RealmDashboardSnapshot.TreatyView> treaties=state.treaties().stream()
                .filter(Treaty::active).filter(t->t.involves(realmId))
                .sorted(Comparator.comparingLong(Treaty::endDay).thenComparingLong(Treaty::id))
                .limit(MAX_POLITICS_TREATIES)
                .map(t->{long other=t.factionA()==realmId?t.factionB():t.factionA();return new RealmDashboardSnapshot.TreatyView(t.id(),other,nameOf(state,other),t.type().name(),t.startDay(),t.endDay());})
                .toList();
        return new RealmDashboardSnapshot.PoliticsView(relations,treaties);
    }

    private static int relationPriority(String status){
        return switch(status){case "WAR"->0;case "HOSTILE"->1;case "RIVAL"->2;case "NEUTRAL"->3;case "FRIENDLY"->4;case "ALLIED"->5;default->6;};
    }


    private static RealmDashboardSnapshot.ForcesView forcesView(SimulationState state,long realmId,SimPosition player){
        if(realmId<=0)return RealmDashboardSnapshot.ForcesView.empty();
        List<RealmDashboardSnapshot.AirWingView> air=state.airWings().stream().filter(w->w.factionId()==realmId&&!w.destroyed())
                .sorted(Comparator.comparingDouble((AirWing w)->w.position().distanceTo(player)).thenComparingLong(AirWing::id)).limit(MAX_FORCE_AIR_WINGS)
                .map(w->new RealmDashboardSnapshot.AirWingView(w.id(),w.model().name(),w.model().role().name(),w.aircraft(),w.mission().name(),w.fuel(),w.readiness(),w.experience(),w.position().distanceTo(player))).toList();
        List<RealmDashboardSnapshot.FleetView> fleets=state.fleets().stream().filter(f->f.factionId()==realmId&&!f.destroyed())
                .sorted(Comparator.comparingDouble((Fleet f)->f.position().distanceTo(player)).thenComparingLong(Fleet::id)).limit(MAX_FORCE_FLEETS)
                .map(f->new RealmDashboardSnapshot.FleetView(f.id(),f.totalShips(),fleetComposition(f),f.mission().name(),f.fuel(),f.readiness(),f.supply(),f.experience(),f.embarkedPersonnel(),f.combatPower(),f.position().distanceTo(player))).toList();
        List<RealmDashboardSnapshot.PortView> ports=state.ports().stream().filter(p->p.factionId()==realmId)
                .sorted(Comparator.comparingDouble((PortState p)->p.position().distanceTo(player)).thenComparingLong(PortState::id)).limit(MAX_FORCE_PORTS)
                .map(p->new RealmDashboardSnapshot.PortView(p.id(),settlementName(state,p.settlementId()),p.level(),p.condition(),p.security(),p.operational(),p.position().distanceTo(player))).toList();
        return new RealmDashboardSnapshot.ForcesView(air,fleets,ports);
    }

    private static String fleetComposition(Fleet fleet){
        StringJoiner joiner=new StringJoiner(", ");
        for(ShipClass type:ShipClass.values()){int count=fleet.count(type);if(count>0)joiner.add(type.name()+"×"+count);}
        return joiner.toString();
    }

    private static RealmDashboardSnapshot.EcologyView ecologyView(SimulationState state,SimPosition player){
        int groups=state.regions().stream().mapToInt(r->r.populations().size()).sum();
        double animals=state.regions().stream().flatMap(r->r.populations().stream()).mapToDouble(dev.livingrealms.sim.ecology.PopulationGroup::population).sum();
        List<RealmDashboardSnapshot.RegionEcologyView> regions=state.regions().stream()
                .sorted(Comparator.comparingDouble((dev.livingrealms.sim.ecology.EcosystemRegion r)->r.center().distanceTo(player)).thenComparingLong(dev.livingrealms.sim.ecology.EcosystemRegion::id))
                .limit(MAX_ECOLOGY_REGIONS)
                .map(r->{
                    List<RealmDashboardSnapshot.SpeciesPopulationView> dominant=r.populations().stream()
                            .filter(g->!g.extinct())
                            .sorted(Comparator.comparingDouble(dev.livingrealms.sim.ecology.PopulationGroup::population).reversed().thenComparingLong(dev.livingrealms.sim.ecology.PopulationGroup::id))
                            .limit(MAX_ECOLOGY_SPECIES_PER_REGION)
                            .map(g->{
                                var def=state.species().get(g.speciesId());
                                String common=def==null?g.speciesId():def.commonName();
                                String locomotion=def==null?"UNKNOWN":def.locomotion().name();
                                String morphology=def==null?"UNKNOWN":def.morphology().name();
                                return new RealmDashboardSnapshot.SpeciesPopulationView(g.speciesId(),common,g.population(),g.health(),g.hunger(),g.thirst(),locomotion,morphology);
                            }).toList();
                    double regionAnimals=r.populations().stream().mapToDouble(dev.livingrealms.sim.ecology.PopulationGroup::population).sum();
                    return new RealmDashboardSnapshot.RegionEcologyView(r.id(),r.biome().id(),r.center().x(),r.center().z(),r.center().distanceTo(player),r.areaKm2(),r.plantBiomass(),regionAnimals,r.populations().size(),dominant);
                }).toList();
        return new RealmDashboardSnapshot.EcologyView(state.species().size(),state.regions().size(),groups,animals,regions);
    }

    private static RealmDashboardSnapshot.StrategicMapView mapView(SimulationState state,SimPosition player,long viewerFactionId){
        record OwnedSettlement(long factionId,Settlement settlement){}
        List<OwnedSettlement> owned=new ArrayList<>();for(Faction faction:state.factions())for(Settlement settlement:faction.settlements())owned.add(new OwnedSettlement(faction.id(),settlement));
        owned.sort(Comparator.comparingDouble((OwnedSettlement o)->o.settlement().position().distanceTo(player)).thenComparingLong(o->o.settlement().id()));if(owned.size()>MAX_MAP_SETTLEMENTS)owned=new ArrayList<>(owned.subList(0,MAX_MAP_SETTLEMENTS));
        Set<Long> settlementIds=new HashSet<>();List<RealmDashboardSnapshot.MapSettlement> settlementViews=new ArrayList<>();List<RealmDashboardSnapshot.MapClaim> claims=new ArrayList<>();
        for(OwnedSettlement o:owned){Settlement st=o.settlement();settlementIds.add(st.id());settlementViews.add(new RealmDashboardSnapshot.MapSettlement(st.id(),o.factionId(),st.name(),st.tier().name(),st.position().x(),st.position().z(),st.population()));claims.add(new RealmDashboardSnapshot.MapClaim(st.id(),o.factionId(),st.position().x(),st.position().z(),TerritoryEngine.claimRadius(st)));}
        Map<Long,OwnedSettlement> byId=new HashMap<>();for(OwnedSettlement o:owned)byId.put(o.settlement().id(),o);
        List<RealmDashboardSnapshot.MapRoute> routes=state.routes().stream().filter(r->settlementIds.contains(r.fromSettlementId())&&settlementIds.contains(r.toSettlementId())).sorted(Comparator.comparingLong(dev.livingrealms.sim.transport.TransportRoute::id)).limit(MAX_MAP_ROUTES).map(r->{Settlement a=byId.get(r.fromSettlementId()).settlement(),b=byId.get(r.toSettlementId()).settlement();return new RealmDashboardSnapshot.MapRoute(r.id(),r.ownerFactionId(),r.fromSettlementId(),r.toSettlementId(),r.mode().name(),a.position().x(),a.position().z(),b.position().x(),b.position().z(),r.quality(),r.security(),r.operational());}).toList();
        List<RealmDashboardSnapshot.MapArmy> armies=state.factions().stream().flatMap(f->f.armies().stream()).sorted(Comparator.comparingDouble((Army a)->a.position().distanceTo(player)).thenComparingLong(Army::id)).limit(MAX_MAP_ARMIES).map(a->new RealmDashboardSnapshot.MapArmy(a.id(),a.factionId(),a.position().x(),a.position().z(),a.totalPersonnel(),a.morale(),a.supply())).toList();
        List<RealmDashboardSnapshot.MapFront> fronts=state.wars().stream().filter(dev.livingrealms.sim.diplomacy.WarState::active).limit(MAX_MAP_FRONTS).map(w->{SimPosition a=forceAnchor(state,w.attackerFactionId(),w.targetSettlementId()),b=forceAnchor(state,w.defenderFactionId(),w.targetSettlementId());return new RealmDashboardSnapshot.MapFront(w.id(),w.attackerFactionId(),w.defenderFactionId(),a.x(),a.z(),b.x(),b.z(),w.goal().name());}).toList();
        List<RealmDashboardSnapshot.MapResourceClaim> resourceClaims=state.resourceClaims().stream().filter(ResourceClaim::active).sorted(Comparator.comparingDouble((ResourceClaim c)->c.position().distanceTo(player)).thenComparingLong(ResourceClaim::id)).limit(MAX_MAP_RESOURCE_CLAIMS).map(c->new RealmDashboardSnapshot.MapResourceClaim(c.id(),c.factionId(),c.type().name(),c.position().x(),c.position().z(),c.strength(),c.contestedByFactionId())).toList();
        List<RealmDashboardSnapshot.MapShipment> shipments=state.shipments().stream().sorted(Comparator.comparingDouble((dev.livingrealms.sim.logistics.TradeShipment sh)->sh.position().distanceTo(player)).thenComparingLong(dev.livingrealms.sim.logistics.TradeShipment::id)).limit(MAX_MAP_SHIPMENTS).map(sh->new RealmDashboardSnapshot.MapShipment(sh.id(),sh.sellerFactionId(),sh.buyerFactionId(),sh.resource().name(),sh.position().x(),sh.position().z(),sh.progress(),sh.value())).toList();
        List<RealmDashboardSnapshot.MapRaid> raids=state.raids().stream().filter(RaidParty::active).map(r->{Settlement a=state.findSettlement(r.originSettlementId()).orElse(null),b=state.findSettlement(r.targetSettlementId()).orElse(null);SimPosition p=a==null?player:(b==null?a.position():a.position().lerp(b.position(),r.progress()));return new RealmDashboardSnapshot.MapRaid(r.id(),r.attackerFactionId(),r.targetSettlementId(),r.bandit(),p.x(),p.z(),r.manpower(),r.morale(),r.progress());}).sorted(Comparator.comparingDouble(v->new SimPosition(v.x(),v.z()).distanceTo(player))).limit(MAX_MAP_RAIDS).toList();
        List<RealmDashboardSnapshot.MapMigration> migrations=state.migrationGroups().stream().filter(MigrationGroup::active).map(g->{Settlement source=state.findSettlement(g.sourceSettlementId()).orElse(null),target=g.campSettlementId()>0?state.findSettlement(g.campSettlementId()).orElse(null):state.findSettlement(g.targetSettlementId()).orElse(null);SimPosition p=source==null?player:(target==null?source.position():source.position().lerp(target.position(),g.progress()));return new RealmDashboardSnapshot.MapMigration(g.id(),g.originFactionId(),g.reason().name(),g.status().name(),p.x(),p.z(),g.people(),g.health());}).sorted(Comparator.comparingDouble(v->new SimPosition(v.x(),v.z()).distanceTo(player))).limit(MAX_MAP_MIGRATIONS).toList();
        List<RealmDashboardSnapshot.MapPort> ports=state.ports().stream().sorted(Comparator.comparingDouble((PortState p)->p.position().distanceTo(player))).limit(MAX_MAP_PORTS).map(p->new RealmDashboardSnapshot.MapPort(p.id(),p.factionId(),p.settlementId(),p.position().x(),p.position().z(),p.level(),p.security(),p.operational())).toList();
        List<RealmDashboardSnapshot.MapFleet> fleets=state.fleets().stream().filter(f->!f.destroyed()).sorted(Comparator.comparingDouble((Fleet f)->f.position().distanceTo(player))).limit(MAX_MAP_FLEETS).map(f->new RealmDashboardSnapshot.MapFleet(f.id(),f.factionId(),f.position().x(),f.position().z(),f.totalShips(),f.mission().name())).toList();
        List<RealmDashboardSnapshot.MapAirWing> airWings=state.airWings().stream().filter(w->!w.destroyed()).sorted(Comparator.comparingDouble((AirWing w)->w.position().distanceTo(player))).limit(MAX_MAP_AIR_WINGS).map(w->new RealmDashboardSnapshot.MapAirWing(w.id(),w.factionId(),w.position().x(),w.position().z(),w.aircraft(),w.mission().name())).toList();
        List<RealmDashboardSnapshot.MapPirate> pirates=state.pirateBands().stream().filter(PirateBand::active).sorted(Comparator.comparingDouble((PirateBand p)->p.position().distanceTo(player))).limit(MAX_MAP_PIRATES).map(p->new RealmDashboardSnapshot.MapPirate(p.id(),p.position().x(),p.position().z(),p.strength(),p.morale(),p.loot())).toList();
        List<RealmDashboardSnapshot.MapPirateHideout> pirateHideouts=(viewerFactionId<=0?state.pirateHideouts().stream().filter(h->false):state.pirateHideouts().stream().filter(PirateHideout::active).filter(h->h.discoveredByFactionId()==viewerFactionId)).sorted(Comparator.comparingDouble((PirateHideout h)->h.position().distanceTo(player))).limit(MAX_MAP_PIRATE_HIDEOUTS).map(h->new RealmDashboardSnapshot.MapPirateHideout(h.id(),h.bandId(),h.position().x(),h.position().z(),h.defense(),h.storedLoot(),h.discoveredByFactionId()==viewerFactionId)).toList();
        List<RealmDashboardSnapshot.MapEpidemic> epidemics=state.epidemics().stream().filter(EpidemicRecord::active).map(e->{Settlement st=state.findSettlement(e.settlementId()).orElse(null);SimPosition p=st==null?player:st.position();return new RealmDashboardSnapshot.MapEpidemic(e.id(),e.settlementId(),e.diseaseKey(),p.x(),p.z(),e.severity(),e.infectedFraction());}).sorted(Comparator.comparingDouble(v->new SimPosition(v.x(),v.z()).distanceTo(player))).limit(MAX_MAP_EPIDEMICS).toList();
        List<RealmDashboardSnapshot.MapCache> caches=viewerFactionId<=0?List.of():state.hiddenCaches().stream().filter(c->!c.recovered()&&CartographicKnowledgeEngine.canTriangulateHiddenCache(state,viewerFactionId,c)).sorted(Comparator.comparingDouble((HiddenCache c)->c.position().distanceTo(player))).limit(MAX_MAP_CACHES).map(c->new RealmDashboardSnapshot.MapCache(c.id(),c.factionId(),c.position().x(),c.position().z(),c.value(),c.discoveredByFactionId()==viewerFactionId)).toList();
        List<RealmDashboardSnapshot.MapRuin> ruins=state.ruinSites().stream().filter(RuinSite::active).sorted(Comparator.comparingDouble((RuinSite r)->r.position().distanceTo(player))).limit(MAX_MAP_RUINS).map(r->new RealmDashboardSnapshot.MapRuin(r.id(),r.originalFactionId(),r.originalName(),r.cause(),r.position().x(),r.position().z(),r.preservation(),r.looted())).toList();
        double minX=player.x(),maxX=player.x(),minZ=player.z(),maxZ=player.z();for(RealmDashboardSnapshot.MapSettlement v:settlementViews){minX=Math.min(minX,v.x()-80);maxX=Math.max(maxX,v.x()+80);minZ=Math.min(minZ,v.z()-80);maxZ=Math.max(maxZ,v.z()+80);}for(RealmDashboardSnapshot.MapArmy v:armies){minX=Math.min(minX,v.x());maxX=Math.max(maxX,v.x());minZ=Math.min(minZ,v.z());maxZ=Math.max(maxZ,v.z());}for(RealmDashboardSnapshot.MapPort v:ports){minX=Math.min(minX,v.x());maxX=Math.max(maxX,v.x());minZ=Math.min(minZ,v.z());maxZ=Math.max(maxZ,v.z());}for(RealmDashboardSnapshot.MapFleet v:fleets){minX=Math.min(minX,v.x());maxX=Math.max(maxX,v.x());minZ=Math.min(minZ,v.z());maxZ=Math.max(maxZ,v.z());}for(RealmDashboardSnapshot.MapMigration v:migrations){minX=Math.min(minX,v.x());maxX=Math.max(maxX,v.x());minZ=Math.min(minZ,v.z());maxZ=Math.max(maxZ,v.z());}
        double centerX=(minX+maxX)*.5,centerZ=(minZ+maxZ)*.5,span=Math.max(512,Math.max(maxX-minX,maxZ-minZ)+320);
        return new RealmDashboardSnapshot.StrategicMapView(player.x(),player.z(),centerX-span*.5,centerX+span*.5,centerZ-span*.5,centerZ+span*.5,settlementViews,claims,routes,armies,fronts,resourceClaims,shipments,raids,migrations,ports,fleets,airWings,pirates,pirateHideouts,epidemics,caches,ruins);
    }

    private static SimPosition forceAnchor(SimulationState state,long factionId,long targetSettlementId){
        Faction faction=state.findFaction(factionId).orElse(null);if(faction==null)return new SimPosition(0,0);
        Army army=faction.armies().stream().max(Comparator.comparingDouble(Army::combatPower)).orElse(null);if(army!=null)return army.position();
        Settlement target=targetSettlementId>0?state.findSettlement(targetSettlementId).orElse(null):null;
        return faction.settlements().stream().min(Comparator.comparingDouble(s->target==null?0:s.position().distanceTo(target.position()))).map(Settlement::position).orElse(new SimPosition(0,0));
    }

    private static RealmDashboardSnapshot.PlayerView playerView(SimulationState state,String actorKey,PlayerStanding standing,long localFactionId){
        long memberId=standing==null?0:standing.memberFactionId();
        String memberName=nameOf(state,memberId);
        String rank=standing==null?"OUTSIDER":standing.rank().name();
        double service=standing==null?0:standing.servicePoints();
        double reputation=standing==null||localFactionId<=0?0:standing.reputationWith(localFactionId);
        WantedProfile profile=state.crimeLedger().findProfile(actorKey).orElse(null);
        double infamy=profile==null?0:profile.globalInfamy();
        JurisdictionWanted wanted=profile==null||localFactionId<=0?null:profile.find(localFactionId).orElse(null);
        String wantedLevel=wanted==null?WantedLevel.NONE.name():wanted.wantedLevel().name();
        double bounty=wanted==null?0:wanted.bounty(),notoriety=wanted==null?0:wanted.notoriety(),heat=wanted==null?0:wanted.heat();
        var custody=localFactionId<=0?Optional.<CustodyRecord>empty():state.activeCustody(actorKey,localFactionId);
        return new RealmDashboardSnapshot.PlayerView(actorKey,memberId,memberName,rank,service,reputation,infamy,wantedLevel,bounty,notoriety,heat,custody.isPresent(),custody.map(CustodyRecord::releaseDay).orElse(-1L));
    }

    private static RealmDashboardSnapshot.RealmView realmView(SimulationState state,Faction faction){
        Map<String,Double> resources=new LinkedHashMap<>();
        Map<String,Double> marketPrices=new LinkedHashMap<>();Map<String,Integer> marketBuyCosts=new LinkedHashMap<>();Map<String,Integer> marketSellPayouts=new LinkedHashMap<>();
        for(ResourceType type:ResourceType.values()){
            resources.put(type.name(),faction.stockpile().get(type));
            if(MarketTransactionEngine.playerTradable().contains(type)){
                marketPrices.put(type.name(),MarketEngine.unitPrice(faction,type));
                marketBuyCosts.put(type.name(),MarketTransactionEngine.quote(faction,type,MarketTransactionEngine.Side.BUY_FROM_REALM).emeralds());
                marketSellPayouts.put(type.name(),MarketTransactionEngine.quote(faction,type,MarketTransactionEngine.Side.SELL_TO_REALM).emeralds());
            }
        }
        int armyPersonnel=faction.armies().stream().mapToInt(Army::totalPersonnel).sum();
        int airframes=state.airWings().stream().filter(w->w.factionId()==faction.id()).mapToInt(w->w.aircraft()).sum();
        int ships=state.fleets().stream().filter(f->f.factionId()==faction.id()).mapToInt(f->f.totalShips()).sum();
        int ports=(int)state.ports().stream().filter(p->p.factionId()==faction.id()).count();
        int industry=(int)state.industrialSites().stream().filter(i->i.factionId()==faction.id()).count();
        int shipments=(int)state.shipments().stream().filter(s->s.sellerFactionId()==faction.id()||s.buyerFactionId()==faction.id()).count();
        int wars=(int)state.wars().stream().filter(w->w.active()&&w.involves(faction.id())).count();
        int treaties=(int)state.treaties().stream().filter(t->t.active()&&t.involves(faction.id())).count();
        var g=faction.government();
        return new RealmDashboardSnapshot.RealmView(faction.id(),faction.name(),faction.rulerName(),g.type().name(),g.successionLaw().name(),faction.population(),faction.settlements().size(),faction.treasury(),faction.technology(),g.stability(),g.legitimacy(),g.corruption(),g.taxRate(),armyPersonnel,airframes,ships,ports,industry,shipments,wars,treaties,resources,marketPrices,marketBuyCosts,marketSellPayouts);
    }

    private static String nameOf(SimulationState state,long id){return id<=0?"":state.findFaction(id).map(Faction::name).orElse("Unknown #"+id);}
}
