package dev.livingrealms.sim.ui;

import dev.livingrealms.sim.data.MiniJson;
import java.util.*;

/** Strict, size-bounded JSON codec for the read-only dashboard network payload. */
public final class RealmDashboardCodec {
    public static final int MAX_JSON_CHARS=262_144;
    private RealmDashboardCodec() {}

    public static String encode(RealmDashboardSnapshot s){
        Objects.requireNonNull(s,"snapshot");
        Map<String,Object> root=new LinkedHashMap<>();
        root.put("v",s.protocolVersion());root.put("day",s.day());root.put("summary",s.worldSummary());
        root.put("jurisdiction",jurisdiction(s.jurisdiction()));root.put("player",player(s.player()));root.put("realm",realm(s.realm()));root.put("settings",settings(s.settings()));
        root.put("factions",s.factions().stream().map(RealmDashboardCodec::faction).toList());
        root.put("settlements",s.settlements().stream().map(RealmDashboardCodec::settlement).toList());
        root.put("wars",s.wars().stream().map(RealmDashboardCodec::war).toList());
        root.put("warfare",warfare(s.warfare()));
        root.put("bounties",s.bounties().stream().map(RealmDashboardCodec::bounty).toList());
        root.put("operations",operations(s.operations()));
        root.put("ecology",ecology(s.ecology()));
        root.put("politics",politics(s.politics()));
        root.put("forces",forces(s.forces()));
        root.put("map",mapView(s.map()));
        root.put("history",s.history().stream().map(RealmDashboardCodec::history).toList());
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
        return new RealmDashboardSnapshot(v,longNum(r,"day"),str(r,"summary"),
                new RealmDashboardSnapshot.JurisdictionView(longNum(j,"primaryId"),str(j,"primaryName"),longNum(j,"secondaryId"),str(j,"secondaryName"),bool(j,"claimed"),bool(j,"contested")),
                decodePlayer(p),
                decodeRealm(realm),decodeSettings(settings),factions,settlements,wars,warfare,bounties,operations,ecology,politics,forces,map,history);
    }

    private static Map<String,Object> jurisdiction(RealmDashboardSnapshot.JurisdictionView v){return map("primaryId",v.primaryFactionId(),"primaryName",v.primaryName(),"secondaryId",v.secondaryFactionId(),"secondaryName",v.secondaryName(),"claimed",v.claimed(),"contested",v.contested());}
    private static Map<String,Object> player(RealmDashboardSnapshot.PlayerView v){return map("actor",v.actorKey(),"memberFactionId",v.memberFactionId(),"memberFactionName",v.memberFactionName(),"rank",v.rank(),"service",v.servicePoints(),"reputation",v.localReputation(),"infamy",v.globalInfamy(),"wanted",v.wantedLevel(),"bounty",v.bounty(),"notoriety",v.notoriety(),"heat",v.heat(),"custody",v.inCustody(),"releaseDay",v.custodyReleaseDay(),"careerTrack",v.careerTrack(),"careerRank",v.careerRank(),"influence",new LinkedHashMap<>(v.influence()));}
    private static Map<String,Object> realm(RealmDashboardSnapshot.RealmView v){return map("id",v.factionId(),"name",v.name(),"ruler",v.ruler(),"government",v.governmentType(),"succession",v.successionLaw(),"population",v.population(),"settlementCount",v.settlementCount(),"treasury",v.treasury(),"technology",v.technology(),"stability",v.stability(),"legitimacy",v.legitimacy(),"corruption",v.corruption(),"taxRate",v.taxRate(),"armyPersonnel",v.armyPersonnel(),"airframes",v.airframes(),"ships",v.ships(),"ports",v.ports(),"industry",v.industrialSites(),"shipments",v.activeShipments(),"wars",v.activeWars(),"treaties",v.activeTreaties(),"debts",v.activeDebts(),"grandProjects",v.grandProjects(),"campaignPlans",v.campaignPlans(),"resources",new LinkedHashMap<>(v.resources()),"marketPrices",new LinkedHashMap<>(v.marketPrices()),"marketBuyCosts",new LinkedHashMap<>(v.marketBuyCosts()),"marketSellPayouts",new LinkedHashMap<>(v.marketSellPayouts()));}
    private static Map<String,Object> settings(RealmDashboardSnapshot.SettingsView v){return map("profile",v.profile(),"physicalRadius",v.physicalRadius(),"regionalRadius",v.regionalRadius(),"wildlife",v.wildlifeBudget(),"caravans",v.caravanBudget(),"military",v.militaryBudget(),"naval",v.navalBudget(),"constructionOps",v.constructionOpsPerTick());}
    private static Map<String,Object> faction(RealmDashboardSnapshot.FactionSummary v){return map("id",v.id(),"name",v.name(),"ruler",v.ruler(),"population",v.population(),"settlements",v.settlements(),"treasury",v.treasury(),"technology",v.technology(),"local",v.localRealm(),"member",v.memberRealm());}
    private static Map<String,Object> settlement(RealmDashboardSnapshot.SettlementView v){return map("id",v.id(),"name",v.name(),"tier",v.tier(),"developmentPriority",v.developmentPriority(),"population",v.population(),"housing",v.housing(),"prosperity",v.prosperity(),"unrest",v.unrest(),"food",v.foodSecurity(),"order",v.publicOrder(),"employment",v.employment(),"housingSat",v.housingSatisfaction(),"goods",v.goodsAccess(),"society",v.societySatisfaction(),"pressure",v.primaryPressure(),"pressureSeverity",v.pressureSeverity(),"cause",v.causeSummary(),"distance",v.distanceBlocks());}
    private static Map<String,Object> war(RealmDashboardSnapshot.WarView v){return map("id",v.id(),"attackerId",v.attackerFactionId(),"attacker",v.attackerName(),"defenderId",v.defenderFactionId(),"defender",v.defenderName(),"goal",v.goal(),"startDay",v.startDay(),"score",v.attackerScore(),"attackerExhaustion",v.attackerExhaustion(),"defenderExhaustion",v.defenderExhaustion());}



    private static Map<String,Object> warfare(RealmDashboardSnapshot.WarfareView v){return map("objectives",v.objectives().stream().map(RealmDashboardCodec::objective).toList(),"sieges",v.sieges().stream().map(RealmDashboardCodec::siege).toList(),"campaigns",v.campaigns().stream().map(RealmDashboardCodec::campaign).toList());}
    private static Map<String,Object> objective(RealmDashboardSnapshot.ObjectiveView v){return map("id",v.id(),"armyId",v.armyId(),"type",v.type(),"target",v.target(),"priority",v.priority(),"distance",v.distanceBlocks());}
    private static Map<String,Object> siege(RealmDashboardSnapshot.SiegeView v){return map("id",v.id(),"attacker",v.attacker(),"defender",v.defender(),"settlement",v.settlement(),"startDay",v.startDay(),"progress",v.progress(),"blockade",v.blockade());}
    private static Map<String,Object> campaign(RealmDashboardSnapshot.CampaignPlanView v){return map("id",v.id(),"type",v.type(),"target",v.target(),"priority",v.priority(),"active",v.active());}

    private static Map<String,Object> operations(RealmDashboardSnapshot.OperationsView v){return map("shipments",v.shipments().stream().map(RealmDashboardCodec::shipment).toList(),"routes",v.routes().stream().map(RealmDashboardCodec::routeOps).toList(),"industry",v.industry().stream().map(RealmDashboardCodec::industryOps).toList(),"assistance",v.assistanceTasks().stream().map(RealmDashboardCodec::assistanceTask).toList());}
    private static Map<String,Object> assistanceTask(RealmDashboardSnapshot.AssistanceTaskView v){return map("id",v.id(),"settlement",v.settlement(),"type",v.type(),"cause",v.cause(),"remaining",v.remainingPressure(),"progress",v.progress(),"expiresDay",v.expiresDay());}
    private static Map<String,Object> shipment(RealmDashboardSnapshot.ShipmentView v){return map("id",v.id(),"seller",v.seller(),"buyer",v.buyer(),"resource",v.resource(),"amount",v.amount(),"value",v.value(),"progress",v.progress(),"distance",v.distanceBlocks());}
    private static Map<String,Object> routeOps(RealmDashboardSnapshot.RouteOpsView v){return map("id",v.id(),"from",v.from(),"to",v.to(),"mode",v.mode(),"quality",v.quality(),"security",v.security(),"capacity",v.capacityPerDay(),"operational",v.operational());}
    private static Map<String,Object> industryOps(RealmDashboardSnapshot.IndustryOpsView v){return map("id",v.id(),"settlement",v.settlement(),"kind",v.kind(),"level",v.level(),"status",v.status(),"condition",v.condition(),"starvedDays",v.starvedDays(),"downtimeDays",v.downtimeDays(),"cycles",v.cycles(),"utilization",v.utilization());}


    private static Map<String,Object> ecology(RealmDashboardSnapshot.EcologyView v){return map("catalogSpecies",v.catalogSpecies(),"regionCount",v.regionCount(),"populationGroups",v.populationGroups(),"totalAnimals",v.totalAnimals(),"regions",v.regions().stream().map(RealmDashboardCodec::ecologyRegion).toList());}
    private static Map<String,Object> ecologyRegion(RealmDashboardSnapshot.RegionEcologyView v){return map("id",v.id(),"biome",v.biome(),"x",v.x(),"z",v.z(),"distance",v.distanceBlocks(),"area",v.areaKm2(),"plants",v.plantBiomass(),"animals",v.animals(),"groups",v.groups(),"species",v.dominantSpecies().stream().map(RealmDashboardCodec::ecologySpecies).toList());}
    private static Map<String,Object> ecologySpecies(RealmDashboardSnapshot.SpeciesPopulationView v){return map("id",v.speciesId(),"name",v.commonName(),"population",v.population(),"health",v.health(),"hunger",v.hunger(),"thirst",v.thirst(),"locomotion",v.locomotion(),"morphology",v.morphology());}

    private static Map<String,Object> bounty(RealmDashboardSnapshot.BountyView v){return map("id",v.id(),"target",v.targetKey(),"issuerId",v.issuerFactionId(),"issuer",v.issuerName(),"reward",v.reward(),"status",v.status(),"assigned",v.assignedToYou());}


    private static Map<String,Object> politics(RealmDashboardSnapshot.PoliticsView v){return map("relations",v.relations().stream().map(RealmDashboardCodec::relation).toList(),"treaties",v.treaties().stream().map(RealmDashboardCodec::treaty).toList());}
    private static Map<String,Object> relation(RealmDashboardSnapshot.RelationView v){return map("factionId",v.factionId(),"name",v.factionName(),"status",v.status(),"opinion",v.opinion(),"trade",v.tradeAgreement());}
    private static Map<String,Object> treaty(RealmDashboardSnapshot.TreatyView v){return map("id",v.id(),"otherFactionId",v.otherFactionId(),"other",v.otherFactionName(),"type",v.type(),"startDay",v.startDay(),"endDay",v.endDay());}


    private static Map<String,Object> forces(RealmDashboardSnapshot.ForcesView v){return map("air",v.airWings().stream().map(RealmDashboardCodec::airWing).toList(),"fleets",v.fleets().stream().map(RealmDashboardCodec::fleet).toList(),"ports",v.ports().stream().map(RealmDashboardCodec::port).toList());}
    private static Map<String,Object> airWing(RealmDashboardSnapshot.AirWingView v){return map("id",v.id(),"model",v.model(),"role",v.role(),"aircraft",v.aircraft(),"mission",v.mission(),"fuel",v.fuel(),"readiness",v.readiness(),"experience",v.experience(),"distance",v.distanceBlocks());}
    private static Map<String,Object> fleet(RealmDashboardSnapshot.FleetView v){return map("id",v.id(),"ships",v.ships(),"composition",v.composition(),"mission",v.mission(),"fuel",v.fuel(),"readiness",v.readiness(),"supply",v.supply(),"experience",v.experience(),"embarked",v.embarkedPersonnel(),"power",v.combatPower(),"distance",v.distanceBlocks());}
    private static Map<String,Object> port(RealmDashboardSnapshot.PortView v){return map("id",v.id(),"settlement",v.settlement(),"level",v.level(),"condition",v.condition(),"security",v.security(),"operational",v.operational(),"distance",v.distanceBlocks());}

    private static Map<String,Object> mapView(RealmDashboardSnapshot.StrategicMapView v){return map("playerX",v.playerX(),"playerZ",v.playerZ(),"minX",v.minX(),"maxX",v.maxX(),"minZ",v.minZ(),"maxZ",v.maxZ(),"settlements",v.settlements().stream().map(RealmDashboardCodec::mapSettlement).toList(),"claims",v.claims().stream().map(RealmDashboardCodec::mapClaim).toList(),"routes",v.routes().stream().map(RealmDashboardCodec::mapRoute).toList(),"armies",v.armies().stream().map(RealmDashboardCodec::mapArmy).toList(),"fronts",v.fronts().stream().map(RealmDashboardCodec::mapFront).toList(),"resourceClaims",v.resourceClaims().stream().map(RealmDashboardCodec::mapResourceClaim).toList(),"shipments",v.shipments().stream().map(RealmDashboardCodec::mapShipment).toList(),"raids",v.raids().stream().map(RealmDashboardCodec::mapRaid).toList(),"migrations",v.migrations().stream().map(RealmDashboardCodec::mapMigration).toList(),"ports",v.ports().stream().map(RealmDashboardCodec::mapPort).toList(),"fleets",v.fleets().stream().map(RealmDashboardCodec::mapFleet).toList(),"airWings",v.airWings().stream().map(RealmDashboardCodec::mapAirWing).toList(),"pirates",v.pirates().stream().map(RealmDashboardCodec::mapPirate).toList(),"pirateHideouts",v.pirateHideouts().stream().map(RealmDashboardCodec::mapPirateHideout).toList(),"epidemics",v.epidemics().stream().map(RealmDashboardCodec::mapEpidemic).toList(),"caches",v.caches().stream().map(RealmDashboardCodec::mapCache).toList(),"ruins",v.ruins().stream().map(RealmDashboardCodec::mapRuin).toList());}
    private static Map<String,Object> mapSettlement(RealmDashboardSnapshot.MapSettlement v){return map("id",v.id(),"factionId",v.factionId(),"name",v.name(),"tier",v.tier(),"x",v.x(),"z",v.z(),"population",v.population());}
    private static Map<String,Object> mapClaim(RealmDashboardSnapshot.MapClaim v){return map("settlementId",v.settlementId(),"factionId",v.factionId(),"x",v.x(),"z",v.z(),"radius",v.radius());}
    private static Map<String,Object> mapRoute(RealmDashboardSnapshot.MapRoute v){return map("id",v.id(),"factionId",v.factionId(),"fromSettlementId",v.fromSettlementId(),"toSettlementId",v.toSettlementId(),"mode",v.mode(),"fromX",v.fromX(),"fromZ",v.fromZ(),"toX",v.toX(),"toZ",v.toZ(),"quality",v.quality(),"security",v.security(),"operational",v.operational());}
    private static Map<String,Object> mapArmy(RealmDashboardSnapshot.MapArmy v){return map("id",v.id(),"factionId",v.factionId(),"x",v.x(),"z",v.z(),"personnel",v.personnel(),"morale",v.morale(),"supply",v.supply());}
    private static Map<String,Object> mapFront(RealmDashboardSnapshot.MapFront v){return map("warId",v.warId(),"attackerFactionId",v.attackerFactionId(),"defenderFactionId",v.defenderFactionId(),"fromX",v.fromX(),"fromZ",v.fromZ(),"toX",v.toX(),"toZ",v.toZ(),"goal",v.goal());}
    private static Map<String,Object> mapResourceClaim(RealmDashboardSnapshot.MapResourceClaim v){return map("id",v.id(),"factionId",v.factionId(),"type",v.type(),"x",v.x(),"z",v.z(),"strength",v.strength(),"contestedByFactionId",v.contestedByFactionId());}
    private static Map<String,Object> mapShipment(RealmDashboardSnapshot.MapShipment v){return map("id",v.id(),"sellerFactionId",v.sellerFactionId(),"buyerFactionId",v.buyerFactionId(),"resource",v.resource(),"x",v.x(),"z",v.z(),"progress",v.progress(),"value",v.value());}
    private static Map<String,Object> mapRaid(RealmDashboardSnapshot.MapRaid v){return map("id",v.id(),"factionId",v.factionId(),"targetSettlementId",v.targetSettlementId(),"bandit",v.bandit(),"x",v.x(),"z",v.z(),"manpower",v.manpower(),"morale",v.morale(),"progress",v.progress());}
    private static Map<String,Object> mapMigration(RealmDashboardSnapshot.MapMigration v){return map("id",v.id(),"factionId",v.factionId(),"reason",v.reason(),"status",v.status(),"x",v.x(),"z",v.z(),"people",v.people(),"health",v.health());}
    private static Map<String,Object> mapPort(RealmDashboardSnapshot.MapPort v){return map("id",v.id(),"factionId",v.factionId(),"settlementId",v.settlementId(),"x",v.x(),"z",v.z(),"level",v.level(),"security",v.security(),"operational",v.operational());}
    private static Map<String,Object> mapFleet(RealmDashboardSnapshot.MapFleet v){return map("id",v.id(),"factionId",v.factionId(),"x",v.x(),"z",v.z(),"ships",v.ships(),"mission",v.mission());}
    private static Map<String,Object> mapAirWing(RealmDashboardSnapshot.MapAirWing v){return map("id",v.id(),"factionId",v.factionId(),"x",v.x(),"z",v.z(),"aircraft",v.aircraft(),"mission",v.mission());}
    private static Map<String,Object> mapPirate(RealmDashboardSnapshot.MapPirate v){return map("id",v.id(),"x",v.x(),"z",v.z(),"strength",v.strength(),"morale",v.morale(),"loot",v.loot());}
    private static Map<String,Object> mapPirateHideout(RealmDashboardSnapshot.MapPirateHideout v){return map("id",v.id(),"bandId",v.bandId(),"x",v.x(),"z",v.z(),"defense",v.defense(),"storedLoot",v.storedLoot(),"discovered",v.discovered());}
    private static Map<String,Object> mapEpidemic(RealmDashboardSnapshot.MapEpidemic v){return map("id",v.id(),"settlementId",v.settlementId(),"disease",v.disease(),"x",v.x(),"z",v.z(),"severity",v.severity(),"infectedFraction",v.infectedFraction());}
    private static Map<String,Object> mapCache(RealmDashboardSnapshot.MapCache v){return map("id",v.id(),"ownerFactionId",v.ownerFactionId(),"x",v.x(),"z",v.z(),"value",v.value(),"compromised",v.compromised());}
    private static Map<String,Object> mapRuin(RealmDashboardSnapshot.MapRuin v){return map("id",v.id(),"originalFactionId",v.originalFactionId(),"name",v.name(),"cause",v.cause(),"x",v.x(),"z",v.z(),"preservation",v.preservation(),"looted",v.looted());}



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

    private static Map<String,Object> history(RealmDashboardSnapshot.HistoryView v){return map("day",v.day(),"type",v.type(),"message",v.message());}

    private static RealmDashboardSnapshot.RealmView decodeRealm(Map<String,Object> m){Map<String,Double> resources=new LinkedHashMap<>();for(var e:obj(m.get("resources")).entrySet())resources.put(e.getKey(),number(e.getValue()).doubleValue());Map<String,Double> marketPrices=new LinkedHashMap<>();for(var e:obj(m.get("marketPrices")).entrySet())marketPrices.put(e.getKey(),number(e.getValue()).doubleValue());Map<String,Integer> buy=new LinkedHashMap<>();for(var e:obj(m.get("marketBuyCosts")).entrySet())buy.put(e.getKey(),number(e.getValue()).intValue());Map<String,Integer> sell=new LinkedHashMap<>();for(var e:obj(m.get("marketSellPayouts")).entrySet())sell.put(e.getKey(),number(e.getValue()).intValue());return new RealmDashboardSnapshot.RealmView(longNum(m,"id"),str(m,"name"),str(m,"ruler"),str(m,"government"),str(m,"succession"),intNum(m,"population"),intNum(m,"settlementCount"),dbl(m,"treasury"),dbl(m,"technology"),dbl(m,"stability"),dbl(m,"legitimacy"),dbl(m,"corruption"),dbl(m,"taxRate"),intNum(m,"armyPersonnel"),intNum(m,"airframes"),intNum(m,"ships"),intNum(m,"ports"),intNum(m,"industry"),intNum(m,"shipments"),intNum(m,"wars"),intNum(m,"treaties"),intNum(m,"debts"),intNum(m,"grandProjects"),intNum(m,"campaignPlans"),resources,marketPrices,buy,sell);}
    private static RealmDashboardSnapshot.SettingsView decodeSettings(Map<String,Object> m){return new RealmDashboardSnapshot.SettingsView(str(m,"profile"),dbl(m,"physicalRadius"),dbl(m,"regionalRadius"),intNum(m,"wildlife"),intNum(m,"caravans"),intNum(m,"military"),intNum(m,"naval"),intNum(m,"constructionOps"));}
    private static RealmDashboardSnapshot.FactionSummary decodeFaction(Map<String,Object> m){return new RealmDashboardSnapshot.FactionSummary(longNum(m,"id"),str(m,"name"),str(m,"ruler"),intNum(m,"population"),intNum(m,"settlements"),dbl(m,"treasury"),dbl(m,"technology"),bool(m,"local"),bool(m,"member"));}
    private static RealmDashboardSnapshot.SettlementView decodeSettlement(Map<String,Object> m){return new RealmDashboardSnapshot.SettlementView(longNum(m,"id"),str(m,"name"),str(m,"tier"),str(m,"developmentPriority"),intNum(m,"population"),intNum(m,"housing"),dbl(m,"prosperity"),dbl(m,"unrest"),dbl(m,"food"),dbl(m,"order"),dbl(m,"employment"),dbl(m,"housingSat"),dbl(m,"goods"),dbl(m,"society"),str(m,"pressure"),dbl(m,"pressureSeverity"),str(m,"cause"),dbl(m,"distance"));}
    private static RealmDashboardSnapshot.WarView decodeWar(Map<String,Object> m){return new RealmDashboardSnapshot.WarView(longNum(m,"id"),longNum(m,"attackerId"),str(m,"attacker"),longNum(m,"defenderId"),str(m,"defender"),str(m,"goal"),longNum(m,"startDay"),dbl(m,"score"),dbl(m,"attackerExhaustion"),dbl(m,"defenderExhaustion"));}
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
