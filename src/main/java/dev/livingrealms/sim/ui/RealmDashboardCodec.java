package dev.livingrealms.sim.ui;

import dev.livingrealms.sim.data.MiniJson;
import dev.livingrealms.sim.ui.dashboard.DashboardSectionCodec;
import java.util.*;

/** Strict, size-bounded JSON codec for the read-only dashboard network payload (protocol 20).
 * Encode path is composed from domain section codecs; decode remains wire-compatible. */
public final class RealmDashboardCodec {
    public static final int MAX_JSON_CHARS=262_144;
    private RealmDashboardCodec() {}

    public static String encode(RealmDashboardSnapshot s){
        Objects.requireNonNull(s,"snapshot");
        Map<String,Object> root=new LinkedHashMap<>();
        root.put("v",s.protocolVersion());
        DashboardSectionCodec.putOverview(root, s.overviewSection());
        DashboardSectionCodec.putSettlements(root, s.settlementSection());
        DashboardSectionCodec.putWar(root, s.warSection());
        DashboardSectionCodec.putLaw(root, s.lawSection());
        DashboardSectionCodec.putEconomy(root, s.economySection());
        DashboardSectionCodec.putEcology(root, s.ecologySection());
        DashboardSectionCodec.putPolitics(root, s.politicsSection());
        DashboardSectionCodec.putForces(root, s.forcesSection());
        DashboardSectionCodec.putMap(root, s.mapSection());
        DashboardSectionCodec.putHistory(root, s.overviewSection());
        DashboardSectionCodec.putUnderworld(root, s.underworldSection());
        DashboardSectionCodec.putWarRoom(root, s.warRoom());
        String json=MiniJson.stringify(root);if(json.length()>MAX_JSON_CHARS)throw new IllegalStateException("Dashboard payload too large: "+json.length());return json;
    }

    public static RealmDashboardSnapshot decode(String json){
        if(json==null||json.isBlank()||json.length()>MAX_JSON_CHARS)throw new IllegalArgumentException("dashboard json size");
        Map<String,Object> r=obj(MiniJson.parse(json));int v=intNum(r,"v");if(v!=RealmDashboardSnapshot.PROTOCOL_VERSION)throw new IllegalArgumentException("Unsupported dashboard protocol "+v);
        var j=obj(r.get("jurisdiction"));var p=obj(r.get("player"));var realm=obj(r.get("realm"));var settings=obj(r.get("settings"));
        List<RealmDashboardSnapshot.FactionSummary> factions=list(r.get("factions")).stream().limit(RealmDashboardBuilder.MAX_FACTIONS).map(x->decodeFaction(obj(x))).toList();
        List<RealmDashboardSnapshot.SettlementView> settlements=list(r.get("settlements")).stream().limit(RealmDashboardBuilder.MAX_SETTLEMENTS).map(x->decodeSettlement(obj(x))).toList();
        List<RealmDashboardSnapshot.WarView> wars=list(r.get("wars")).stream().limit(RealmDashboardBuilder.MAX_WARS).map(x->decodeWar(obj(x))).toList();
        RealmDashboardSnapshot.WarfareView warfare=decodeWarfare(obj(r.get("warfare")));
        List<RealmDashboardSnapshot.BountyView> bounties=list(r.get("bounties")).stream().limit(RealmDashboardBuilder.MAX_BOUNTIES).map(x->decodeBounty(obj(x))).toList();
        RealmDashboardSnapshot.OperationsView operations=decodeOperations(obj(r.get("operations")));
        RealmDashboardSnapshot.EcologyView ecology=decodeEcology(obj(r.get("ecology")));
        RealmDashboardSnapshot.PoliticsView politics=decodePolitics(obj(r.get("politics")));
        RealmDashboardSnapshot.ForcesView forces=decodeForces(obj(r.get("forces")));
        RealmDashboardSnapshot.StrategicMapView map=decodeMap(obj(r.get("map")));
        List<RealmDashboardSnapshot.HistoryView> history=list(r.get("history")).stream().limit(RealmDashboardBuilder.MAX_HISTORY).map(x->decodeHistory(obj(x))).toList();
        // Protocol 20 compatible: older payloads omit underworld/warRoom → empty defaults.
        RealmDashboardSnapshot.UnderworldView underworld=r.containsKey("underworld")
                ?decodeUnderworld(obj(r.get("underworld"))):RealmDashboardSnapshot.UnderworldView.empty();
        RealmDashboardSnapshot.WarRoomView warRoom=r.containsKey("warRoom")
                ?decodeWarRoom(obj(r.get("warRoom"))):RealmDashboardSnapshot.WarRoomView.empty();
        return new RealmDashboardSnapshot(v,longNum(r,"day"),str(r,"summary"),
                new RealmDashboardSnapshot.JurisdictionView(longNum(j,"primaryId"),str(j,"primaryName"),longNum(j,"secondaryId"),str(j,"secondaryName"),bool(j,"claimed"),bool(j,"contested")),
                decodePlayer(p),
                decodeRealm(realm),decodeSettings(settings),factions,settlements,wars,warfare,bounties,operations,ecology,politics,forces,map,history,underworld,warRoom);
    }

    private static RealmDashboardSnapshot.WarfareView decodeWarfare(Map<String,Object> m){
        List<RealmDashboardSnapshot.ObjectiveView> objectives=list(m.get("objectives")).stream().limit(RealmDashboardBuilder.MAX_OBJECTIVES).map(x->decodeObjective(obj(x))).toList();
        List<RealmDashboardSnapshot.SiegeView> sieges=list(m.get("sieges")).stream().limit(RealmDashboardBuilder.MAX_SIEGES).map(x->decodeSiege(obj(x))).toList();
        List<RealmDashboardSnapshot.CampaignPlanView> campaigns=list(m.get("campaigns")).stream().limit(RealmDashboardBuilder.MAX_CAMPAIGN_PLANS).map(x->decodeCampaign(obj(x))).toList();
        return new RealmDashboardSnapshot.WarfareView(objectives,sieges,campaigns);
    }
    private static RealmDashboardSnapshot.ObjectiveView decodeObjective(Map<String,Object> m){return new RealmDashboardSnapshot.ObjectiveView(longNum(m,"id"),longNum(m,"armyId"),str(m,"type"),str(m,"target"),intNum(m,"priority"),dbl(m,"distance"));}
    private static RealmDashboardSnapshot.SiegeView decodeSiege(Map<String,Object> m){return new RealmDashboardSnapshot.SiegeView(longNum(m,"id"),str(m,"attacker"),str(m,"defender"),str(m,"settlement"),longNum(m,"startDay"),dbl(m,"progress"),dbl(m,"blockade"));}
    private static RealmDashboardSnapshot.CampaignPlanView decodeCampaign(Map<String,Object> m){return new RealmDashboardSnapshot.CampaignPlanView(longNum(m,"id"),str(m,"type"),str(m,"target"),intNum(m,"priority"),bool(m,"active"));}
    private static RealmDashboardSnapshot.PlayerView decodePlayer(Map<String,Object> p){
        Map<String,Double> influence=new LinkedHashMap<>();
        if(p.containsKey("influence"))for(var e:obj(p.get("influence")).entrySet())influence.put(e.getKey(),number(e.getValue()).doubleValue());
        return new RealmDashboardSnapshot.PlayerView(str(p,"actor"),longNum(p,"memberFactionId"),str(p,"memberFactionName"),str(p,"rank"),dbl(p,"service"),dbl(p,"reputation"),dbl(p,"infamy"),str(p,"wanted"),dbl(p,"bounty"),dbl(p,"notoriety"),dbl(p,"heat"),bool(p,"custody"),longNum(p,"releaseDay"),str(p,"careerTrack"),str(p,"careerRank"),influence);
    }

    private static RealmDashboardSnapshot.OperationsView decodeOperations(Map<String,Object> m){
        List<RealmDashboardSnapshot.ShipmentView> shipments=list(m.get("shipments")).stream().limit(RealmDashboardBuilder.MAX_OPERATION_SHIPMENTS).map(x->decodeShipment(obj(x))).toList();
        List<RealmDashboardSnapshot.RouteOpsView> routes=list(m.get("routes")).stream().limit(RealmDashboardBuilder.MAX_OPERATION_ROUTES).map(x->decodeRouteOps(obj(x))).toList();
        List<RealmDashboardSnapshot.IndustryOpsView> industry=list(m.get("industry")).stream().limit(RealmDashboardBuilder.MAX_OPERATION_INDUSTRY).map(x->decodeIndustryOps(obj(x))).toList();
        List<RealmDashboardSnapshot.AssistanceTaskView> assistance=list(m.get("assistance")).stream().limit(RealmDashboardBuilder.MAX_ASSISTANCE_TASKS).map(x->decodeAssistanceTask(obj(x))).toList();
        return new RealmDashboardSnapshot.OperationsView(shipments,routes,industry,assistance);
    }
    private static RealmDashboardSnapshot.AssistanceTaskView decodeAssistanceTask(Map<String,Object> m){return new RealmDashboardSnapshot.AssistanceTaskView(longNum(m,"id"),str(m,"settlement"),str(m,"type"),str(m,"cause"),dbl(m,"remaining"),dbl(m,"progress"),longNum(m,"expiresDay"));}
    private static RealmDashboardSnapshot.ShipmentView decodeShipment(Map<String,Object> m){return new RealmDashboardSnapshot.ShipmentView(longNum(m,"id"),str(m,"seller"),str(m,"buyer"),str(m,"resource"),dbl(m,"amount"),dbl(m,"value"),dbl(m,"progress"),dbl(m,"distance"));}
    private static RealmDashboardSnapshot.RouteOpsView decodeRouteOps(Map<String,Object> m){return new RealmDashboardSnapshot.RouteOpsView(longNum(m,"id"),str(m,"from"),str(m,"to"),str(m,"mode"),dbl(m,"quality"),dbl(m,"security"),dbl(m,"capacity"),bool(m,"operational"));}
    private static RealmDashboardSnapshot.IndustryOpsView decodeIndustryOps(Map<String,Object> m){return new RealmDashboardSnapshot.IndustryOpsView(longNum(m,"id"),str(m,"settlement"),str(m,"kind"),intNum(m,"level"),str(m,"status"),dbl(m,"condition"),intNum(m,"starvedDays"),intNum(m,"downtimeDays"),intNum(m,"cycles"),dbl(m,"utilization"));}


    private static RealmDashboardSnapshot.EcologyView decodeEcology(Map<String,Object> m){
        List<RealmDashboardSnapshot.RegionEcologyView> regions=list(m.get("regions")).stream().limit(RealmDashboardBuilder.MAX_ECOLOGY_REGIONS).map(x->decodeEcologyRegion(obj(x))).toList();
        return new RealmDashboardSnapshot.EcologyView(intNum(m,"catalogSpecies"),intNum(m,"regionCount"),intNum(m,"populationGroups"),dbl(m,"totalAnimals"),regions);
    }
    private static RealmDashboardSnapshot.RegionEcologyView decodeEcologyRegion(Map<String,Object> m){
        List<RealmDashboardSnapshot.SpeciesPopulationView> species=list(m.get("species")).stream().limit(RealmDashboardBuilder.MAX_ECOLOGY_SPECIES_PER_REGION).map(x->decodeEcologySpecies(obj(x))).toList();
        return new RealmDashboardSnapshot.RegionEcologyView(longNum(m,"id"),str(m,"biome"),dbl(m,"x"),dbl(m,"z"),dbl(m,"distance"),dbl(m,"area"),dbl(m,"plants"),dbl(m,"animals"),intNum(m,"groups"),species);
    }
    private static RealmDashboardSnapshot.SpeciesPopulationView decodeEcologySpecies(Map<String,Object> m){return new RealmDashboardSnapshot.SpeciesPopulationView(str(m,"id"),str(m,"name"),dbl(m,"population"),dbl(m,"health"),dbl(m,"hunger"),dbl(m,"thirst"),str(m,"locomotion"),str(m,"morphology"));}

    private static RealmDashboardSnapshot.BountyView decodeBounty(Map<String,Object> m){return new RealmDashboardSnapshot.BountyView(longNum(m,"id"),str(m,"target"),longNum(m,"issuerId"),str(m,"issuer"),dbl(m,"reward"),str(m,"status"),bool(m,"assigned"));}


    private static RealmDashboardSnapshot.PoliticsView decodePolitics(Map<String,Object> m){
        List<RealmDashboardSnapshot.RelationView> relations=list(m.get("relations")).stream().limit(RealmDashboardBuilder.MAX_POLITICS_RELATIONS).map(x->decodeRelation(obj(x))).toList();
        List<RealmDashboardSnapshot.TreatyView> treaties=list(m.get("treaties")).stream().limit(RealmDashboardBuilder.MAX_POLITICS_TREATIES).map(x->decodeTreaty(obj(x))).toList();
        return new RealmDashboardSnapshot.PoliticsView(relations,treaties);
    }
    private static RealmDashboardSnapshot.RelationView decodeRelation(Map<String,Object> m){return new RealmDashboardSnapshot.RelationView(longNum(m,"factionId"),str(m,"name"),str(m,"status"),dbl(m,"opinion"),bool(m,"trade"));}
    private static RealmDashboardSnapshot.TreatyView decodeTreaty(Map<String,Object> m){return new RealmDashboardSnapshot.TreatyView(longNum(m,"id"),longNum(m,"otherFactionId"),str(m,"other"),str(m,"type"),longNum(m,"startDay"),longNum(m,"endDay"));}


    private static RealmDashboardSnapshot.ForcesView decodeForces(Map<String,Object> m){
        List<RealmDashboardSnapshot.AirWingView> air=list(m.get("air")).stream().limit(RealmDashboardBuilder.MAX_FORCE_AIR_WINGS).map(x->decodeAirWing(obj(x))).toList();
        List<RealmDashboardSnapshot.FleetView> fleets=list(m.get("fleets")).stream().limit(RealmDashboardBuilder.MAX_FORCE_FLEETS).map(x->decodeFleet(obj(x))).toList();
        List<RealmDashboardSnapshot.PortView> ports=list(m.get("ports")).stream().limit(RealmDashboardBuilder.MAX_FORCE_PORTS).map(x->decodePort(obj(x))).toList();
        return new RealmDashboardSnapshot.ForcesView(air,fleets,ports);
    }
    private static RealmDashboardSnapshot.AirWingView decodeAirWing(Map<String,Object> m){return new RealmDashboardSnapshot.AirWingView(longNum(m,"id"),str(m,"model"),str(m,"role"),intNum(m,"aircraft"),str(m,"mission"),dbl(m,"fuel"),dbl(m,"readiness"),dbl(m,"experience"),dbl(m,"distance"));}
    private static RealmDashboardSnapshot.FleetView decodeFleet(Map<String,Object> m){return new RealmDashboardSnapshot.FleetView(longNum(m,"id"),intNum(m,"ships"),str(m,"composition"),str(m,"mission"),dbl(m,"fuel"),dbl(m,"readiness"),dbl(m,"supply"),dbl(m,"experience"),intNum(m,"embarked"),dbl(m,"power"),dbl(m,"distance"));}
    private static RealmDashboardSnapshot.PortView decodePort(Map<String,Object> m){return new RealmDashboardSnapshot.PortView(longNum(m,"id"),str(m,"settlement"),intNum(m,"level"),dbl(m,"condition"),dbl(m,"security"),bool(m,"operational"),dbl(m,"distance"));}

    private static RealmDashboardSnapshot.StrategicMapView decodeMap(Map<String,Object> m){
        List<RealmDashboardSnapshot.MapSettlement> settlements=list(m.get("settlements")).stream().limit(RealmDashboardBuilder.MAX_MAP_SETTLEMENTS).map(x->decodeMapSettlement(obj(x))).toList();
        List<RealmDashboardSnapshot.MapClaim> claims=list(m.get("claims")).stream().limit(RealmDashboardBuilder.MAX_MAP_SETTLEMENTS).map(x->decodeMapClaim(obj(x))).toList();
        List<RealmDashboardSnapshot.MapRoute> routes=list(m.get("routes")).stream().limit(RealmDashboardBuilder.MAX_MAP_ROUTES).map(x->decodeMapRoute(obj(x))).toList();
        List<RealmDashboardSnapshot.MapArmy> armies=list(m.get("armies")).stream().limit(RealmDashboardBuilder.MAX_MAP_ARMIES).map(x->decodeMapArmy(obj(x))).toList();
        List<RealmDashboardSnapshot.MapFront> fronts=list(m.get("fronts")).stream().limit(RealmDashboardBuilder.MAX_MAP_FRONTS).map(x->decodeMapFront(obj(x))).toList();
        List<RealmDashboardSnapshot.MapResourceClaim> resourceClaims=list(m.get("resourceClaims")).stream().limit(RealmDashboardBuilder.MAX_MAP_RESOURCE_CLAIMS).map(x->decodeMapResourceClaim(obj(x))).toList();
        List<RealmDashboardSnapshot.MapShipment> shipments=list(m.get("shipments")).stream().limit(RealmDashboardBuilder.MAX_MAP_SHIPMENTS).map(x->decodeMapShipment(obj(x))).toList();
        List<RealmDashboardSnapshot.MapRaid> raids=list(m.get("raids")).stream().limit(RealmDashboardBuilder.MAX_MAP_RAIDS).map(x->decodeMapRaid(obj(x))).toList();
        List<RealmDashboardSnapshot.MapMigration> migrations=list(m.get("migrations")).stream().limit(RealmDashboardBuilder.MAX_MAP_MIGRATIONS).map(x->decodeMapMigration(obj(x))).toList();
        List<RealmDashboardSnapshot.MapPort> ports=list(m.get("ports")).stream().limit(RealmDashboardBuilder.MAX_MAP_PORTS).map(x->decodeMapPort(obj(x))).toList();
        List<RealmDashboardSnapshot.MapFleet> fleets=list(m.get("fleets")).stream().limit(RealmDashboardBuilder.MAX_MAP_FLEETS).map(x->decodeMapFleet(obj(x))).toList();
        List<RealmDashboardSnapshot.MapAirWing> airWings=list(m.get("airWings")).stream().limit(RealmDashboardBuilder.MAX_MAP_AIR_WINGS).map(x->decodeMapAirWing(obj(x))).toList();
        List<RealmDashboardSnapshot.MapPirate> pirates=list(m.get("pirates")).stream().limit(RealmDashboardBuilder.MAX_MAP_PIRATES).map(x->decodeMapPirate(obj(x))).toList();
        List<RealmDashboardSnapshot.MapPirateHideout> pirateHideouts=list(m.get("pirateHideouts")).stream().limit(RealmDashboardBuilder.MAX_MAP_PIRATE_HIDEOUTS).map(x->decodeMapPirateHideout(obj(x))).toList();
        List<RealmDashboardSnapshot.MapEpidemic> epidemics=list(m.get("epidemics")).stream().limit(RealmDashboardBuilder.MAX_MAP_EPIDEMICS).map(x->decodeMapEpidemic(obj(x))).toList();
        List<RealmDashboardSnapshot.MapCache> caches=list(m.get("caches")).stream().limit(RealmDashboardBuilder.MAX_MAP_CACHES).map(x->decodeMapCache(obj(x))).toList();
        List<RealmDashboardSnapshot.MapRuin> ruins=list(m.get("ruins")).stream().limit(RealmDashboardBuilder.MAX_MAP_RUINS).map(x->decodeMapRuin(obj(x))).toList();
        return new RealmDashboardSnapshot.StrategicMapView(dbl(m,"playerX"),dbl(m,"playerZ"),dbl(m,"minX"),dbl(m,"maxX"),dbl(m,"minZ"),dbl(m,"maxZ"),settlements,claims,routes,armies,fronts,resourceClaims,shipments,raids,migrations,ports,fleets,airWings,pirates,pirateHideouts,epidemics,caches,ruins);
    }
    private static RealmDashboardSnapshot.MapSettlement decodeMapSettlement(Map<String,Object> m){return new RealmDashboardSnapshot.MapSettlement(longNum(m,"id"),longNum(m,"factionId"),str(m,"name"),str(m,"tier"),dbl(m,"x"),dbl(m,"z"),intNum(m,"population"));}
    private static RealmDashboardSnapshot.MapClaim decodeMapClaim(Map<String,Object> m){return new RealmDashboardSnapshot.MapClaim(longNum(m,"settlementId"),longNum(m,"factionId"),dbl(m,"x"),dbl(m,"z"),dbl(m,"radius"));}
    private static RealmDashboardSnapshot.MapRoute decodeMapRoute(Map<String,Object> m){return new RealmDashboardSnapshot.MapRoute(longNum(m,"id"),longNum(m,"factionId"),longNum(m,"fromSettlementId"),longNum(m,"toSettlementId"),str(m,"mode"),dbl(m,"fromX"),dbl(m,"fromZ"),dbl(m,"toX"),dbl(m,"toZ"),dbl(m,"quality"),dbl(m,"security"),bool(m,"operational"));}
    private static RealmDashboardSnapshot.MapArmy decodeMapArmy(Map<String,Object> m){return new RealmDashboardSnapshot.MapArmy(longNum(m,"id"),longNum(m,"factionId"),dbl(m,"x"),dbl(m,"z"),intNum(m,"personnel"),dbl(m,"morale"),dbl(m,"supply"));}
    private static RealmDashboardSnapshot.MapFront decodeMapFront(Map<String,Object> m){return new RealmDashboardSnapshot.MapFront(longNum(m,"warId"),longNum(m,"attackerFactionId"),longNum(m,"defenderFactionId"),dbl(m,"fromX"),dbl(m,"fromZ"),dbl(m,"toX"),dbl(m,"toZ"),str(m,"goal"));}
    private static RealmDashboardSnapshot.MapResourceClaim decodeMapResourceClaim(Map<String,Object> m){return new RealmDashboardSnapshot.MapResourceClaim(longNum(m,"id"),longNum(m,"factionId"),str(m,"type"),dbl(m,"x"),dbl(m,"z"),dbl(m,"strength"),longNum(m,"contestedByFactionId"));}
    private static RealmDashboardSnapshot.MapShipment decodeMapShipment(Map<String,Object> m){return new RealmDashboardSnapshot.MapShipment(longNum(m,"id"),longNum(m,"sellerFactionId"),longNum(m,"buyerFactionId"),str(m,"resource"),dbl(m,"x"),dbl(m,"z"),dbl(m,"progress"),dbl(m,"value"));}
    private static RealmDashboardSnapshot.MapRaid decodeMapRaid(Map<String,Object> m){return new RealmDashboardSnapshot.MapRaid(longNum(m,"id"),longNum(m,"factionId"),longNum(m,"targetSettlementId"),bool(m,"bandit"),dbl(m,"x"),dbl(m,"z"),intNum(m,"manpower"),dbl(m,"morale"),dbl(m,"progress"));}
    private static RealmDashboardSnapshot.MapMigration decodeMapMigration(Map<String,Object> m){return new RealmDashboardSnapshot.MapMigration(longNum(m,"id"),longNum(m,"factionId"),str(m,"reason"),str(m,"status"),dbl(m,"x"),dbl(m,"z"),intNum(m,"people"),dbl(m,"health"));}
    private static RealmDashboardSnapshot.MapPort decodeMapPort(Map<String,Object> m){return new RealmDashboardSnapshot.MapPort(longNum(m,"id"),longNum(m,"factionId"),longNum(m,"settlementId"),dbl(m,"x"),dbl(m,"z"),intNum(m,"level"),dbl(m,"security"),bool(m,"operational"));}
    private static RealmDashboardSnapshot.MapFleet decodeMapFleet(Map<String,Object> m){return new RealmDashboardSnapshot.MapFleet(longNum(m,"id"),longNum(m,"factionId"),dbl(m,"x"),dbl(m,"z"),intNum(m,"ships"),str(m,"mission"));}
    private static RealmDashboardSnapshot.MapAirWing decodeMapAirWing(Map<String,Object> m){return new RealmDashboardSnapshot.MapAirWing(longNum(m,"id"),longNum(m,"factionId"),dbl(m,"x"),dbl(m,"z"),intNum(m,"aircraft"),str(m,"mission"));}
    private static RealmDashboardSnapshot.MapPirate decodeMapPirate(Map<String,Object> m){return new RealmDashboardSnapshot.MapPirate(longNum(m,"id"),dbl(m,"x"),dbl(m,"z"),intNum(m,"strength"),dbl(m,"morale"),dbl(m,"loot"));}
    private static RealmDashboardSnapshot.MapPirateHideout decodeMapPirateHideout(Map<String,Object> m){return new RealmDashboardSnapshot.MapPirateHideout(longNum(m,"id"),longNum(m,"bandId"),dbl(m,"x"),dbl(m,"z"),dbl(m,"defense"),dbl(m,"storedLoot"),bool(m,"discovered"));}
    private static RealmDashboardSnapshot.MapEpidemic decodeMapEpidemic(Map<String,Object> m){return new RealmDashboardSnapshot.MapEpidemic(longNum(m,"id"),longNum(m,"settlementId"),str(m,"disease"),dbl(m,"x"),dbl(m,"z"),dbl(m,"severity"),dbl(m,"infectedFraction"));}
    private static RealmDashboardSnapshot.MapCache decodeMapCache(Map<String,Object> m){return new RealmDashboardSnapshot.MapCache(longNum(m,"id"),longNum(m,"ownerFactionId"),dbl(m,"x"),dbl(m,"z"),dbl(m,"value"),bool(m,"compromised"));}
    private static RealmDashboardSnapshot.MapRuin decodeMapRuin(Map<String,Object> m){return new RealmDashboardSnapshot.MapRuin(longNum(m,"id"),longNum(m,"originalFactionId"),str(m,"name"),str(m,"cause"),dbl(m,"x"),dbl(m,"z"),dbl(m,"preservation"),bool(m,"looted"));}

    private static RealmDashboardSnapshot.UnderworldView decodeUnderworld(Map<String,Object> m){
        List<RealmDashboardSnapshot.UnderworldContractView> contracts=list(m.get("contracts")).stream()
                .limit(RealmDashboardBuilder.MAX_UNDERWORLD_CONTRACTS)
                .map(x->{var c=obj(x);return new RealmDashboardSnapshot.UnderworldContractView(
                        longNum(c,"id"),str(c,"type"),str(c,"jurisdiction"),str(c,"target"),dbl(c,"reward"),
                        longNum(c,"daysLeft"),str(c,"status"),bool(c,"accepted"));})
                .toList();
        List<RealmDashboardSnapshot.StolenLotView> lots=list(m.get("lots")).stream()
                .limit(RealmDashboardBuilder.MAX_STOLEN_LOTS)
                .map(x->{var l=obj(x);return new RealmDashboardSnapshot.StolenLotView(longNum(l,"id"),str(l,"good"),dbl(l,"value"));})
                .toList();
        return new RealmDashboardSnapshot.UnderworldView(dbl(m,"streetCred"),dbl(m,"briberySkill"),intNum(m,"contractsCompleted"),
                bool(m,"blackMarket"),contracts,lots);
    }
    private static RealmDashboardSnapshot.WarRoomView decodeWarRoom(Map<String,Object> m){
        List<RealmDashboardSnapshot.DeclareEnemyView> enemies=list(m.get("enemies")).stream()
                .limit(RealmDashboardBuilder.MAX_WAR_ROOM_ENEMIES)
                .map(x->{var e=obj(x);List<String> goals=list(e.get("goals")).stream().map(g->String.valueOf(g)).toList();
                    return new RealmDashboardSnapshot.DeclareEnemyView(longNum(e,"factionId"),str(e,"name"),goals,
                            longNum(e,"targetId"),str(e,"target"),bool(e,"declare"),bool(e,"petition"),str(e,"deny"));})
                .toList();
        List<RealmDashboardSnapshot.ArmyDetailView> armies=list(m.get("armies")).stream()
                .limit(RealmDashboardBuilder.MAX_WAR_ROOM_ARMIES)
                .map(x->{var a=obj(x);return new RealmDashboardSnapshot.ArmyDetailView(longNum(a,"id"),intNum(a,"personnel"),
                        dbl(a,"morale"),dbl(a,"supply"),dbl(a,"power"),dbl(a,"x"),dbl(a,"z"),str(a,"home"),str(a,"objective"));})
                .toList();
        List<RealmDashboardSnapshot.EscortTargetView> escorts=list(m.get("escorts")).stream()
                .limit(RealmDashboardBuilder.MAX_WAR_ROOM_ESCORTS)
                .map(x->{var t=obj(x);return new RealmDashboardSnapshot.EscortTargetView(longNum(t,"id"),str(t,"kind"),str(t,"label"));})
                .toList();
        List<Long> hostile=list(m.get("hostileSettlements")).stream().map(o->number(o).longValue()).toList();
        return new RealmDashboardSnapshot.WarRoomView(enemies,armies,escorts,hostile,
                longNum(m,"defaultHostile"),longNum(m,"defaultEscort"),longNum(m,"defaultPatrol"));
    }

    private static RealmDashboardSnapshot.RealmView decodeRealm(Map<String,Object> m){Map<String,Double> resources=new LinkedHashMap<>();for(var e:obj(m.get("resources")).entrySet())resources.put(e.getKey(),number(e.getValue()).doubleValue());Map<String,Double> marketPrices=new LinkedHashMap<>();for(var e:obj(m.get("marketPrices")).entrySet())marketPrices.put(e.getKey(),number(e.getValue()).doubleValue());Map<String,Integer> buy=new LinkedHashMap<>();for(var e:obj(m.get("marketBuyCosts")).entrySet())buy.put(e.getKey(),number(e.getValue()).intValue());Map<String,Integer> sell=new LinkedHashMap<>();for(var e:obj(m.get("marketSellPayouts")).entrySet())sell.put(e.getKey(),number(e.getValue()).intValue());return new RealmDashboardSnapshot.RealmView(longNum(m,"id"),str(m,"name"),str(m,"ruler"),str(m,"government"),str(m,"succession"),intNum(m,"population"),intNum(m,"settlementCount"),dbl(m,"treasury"),dbl(m,"technology"),dbl(m,"stability"),dbl(m,"legitimacy"),dbl(m,"corruption"),dbl(m,"taxRate"),intNum(m,"armyPersonnel"),intNum(m,"airframes"),intNum(m,"ships"),intNum(m,"ports"),intNum(m,"industry"),intNum(m,"shipments"),intNum(m,"wars"),intNum(m,"treaties"),intNum(m,"debts"),intNum(m,"grandProjects"),intNum(m,"campaignPlans"),resources,marketPrices,buy,sell);}
    private static RealmDashboardSnapshot.SettingsView decodeSettings(Map<String,Object> m){return new RealmDashboardSnapshot.SettingsView(str(m,"profile"),dbl(m,"physicalRadius"),dbl(m,"regionalRadius"),intNum(m,"wildlife"),intNum(m,"caravans"),intNum(m,"military"),intNum(m,"naval"),intNum(m,"constructionOps"));}
    private static RealmDashboardSnapshot.FactionSummary decodeFaction(Map<String,Object> m){return new RealmDashboardSnapshot.FactionSummary(longNum(m,"id"),str(m,"name"),str(m,"ruler"),intNum(m,"population"),intNum(m,"settlements"),dbl(m,"treasury"),dbl(m,"technology"),bool(m,"local"),bool(m,"member"));}
    private static RealmDashboardSnapshot.SettlementView decodeSettlement(Map<String,Object> m){return new RealmDashboardSnapshot.SettlementView(longNum(m,"id"),str(m,"name"),str(m,"tier"),str(m,"developmentPriority"),str(m,"developmentMode"),str(m,"origin"),intNum(m,"population"),intNum(m,"housing"),intNum(m,"verifiedHousing"),intNum(m,"housingDeficit"),intNum(m,"registeredBuildings"),dbl(m,"prosperity"),dbl(m,"unrest"),dbl(m,"food"),dbl(m,"order"),dbl(m,"employment"),dbl(m,"housingSat"),dbl(m,"goods"),dbl(m,"society"),str(m,"pressure"),dbl(m,"pressureSeverity"),str(m,"cause"),dbl(m,"distance"));}
    private static RealmDashboardSnapshot.WarView decodeWar(Map<String,Object> m){return new RealmDashboardSnapshot.WarView(longNum(m,"id"),longNum(m,"attackerId"),str(m,"attacker"),longNum(m,"defenderId"),str(m,"defender"),str(m,"goal"),longNum(m,"targetSettlementId"),str(m,"targetSettlement"),longNum(m,"startDay"),dbl(m,"score"),dbl(m,"attackerExhaustion"),dbl(m,"defenderExhaustion"));}
    private static RealmDashboardSnapshot.HistoryView decodeHistory(Map<String,Object> m){return new RealmDashboardSnapshot.HistoryView(longNum(m,"day"),str(m,"type"),str(m,"message"));}

    private static Map<String,Object> map(Object... values){Map<String,Object> m=new LinkedHashMap<>();for(int i=0;i<values.length;i+=2)m.put((String)values[i],values[i+1]);return m;}
    @SuppressWarnings("unchecked") private static Map<String,Object> obj(Object o){if(!(o instanceof Map<?,?> raw))throw new IllegalArgumentException("Expected object");for(Object k:raw.keySet())if(!(k instanceof String))throw new IllegalArgumentException("Non-string key");return (Map<String,Object>)raw;}
    @SuppressWarnings("unchecked") private static List<Object> list(Object o){if(!(o instanceof List<?> raw))throw new IllegalArgumentException("Expected list");return (List<Object>)raw;}
    private static Number number(Object o){if(!(o instanceof Number n))throw new IllegalArgumentException("Expected number");return n;}
    private static long longNum(Map<String,Object> m,String k){return number(required(m,k)).longValue();}
    private static int intNum(Map<String,Object> m,String k){return Math.toIntExact(longNum(m,k));}
    private static double dbl(Map<String,Object> m,String k){return number(required(m,k)).doubleValue();}
    private static String str(Map<String,Object> m,String k){Object o=required(m,k);if(!(o instanceof String s))throw new IllegalArgumentException("Expected string: "+k);return s;}
    private static boolean bool(Map<String,Object> m,String k){Object o=required(m,k);if(!(o instanceof Boolean b))throw new IllegalArgumentException("Expected boolean: "+k);return b;}
    private static Object required(Map<String,Object> m,String k){if(!m.containsKey(k))throw new IllegalArgumentException("Missing field: "+k);return m.get(k);}
}
