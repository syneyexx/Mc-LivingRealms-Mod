package dev.livingrealms.sim.ui.dashboard;

import dev.livingrealms.sim.ui.RealmDashboardSnapshot;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Protocol-20 section encode/decode helpers. Wire keys stay identical to the monolithic codec.
 */
public final class DashboardSectionCodec {
    private DashboardSectionCodec() {}

    public static void putOverview(Map<String, Object> root, OverviewSnapshot s) {
        root.put("day", s.day());
        root.put("summary", s.worldSummary());
        root.put("jurisdiction", jurisdiction(s.jurisdiction()));
        root.put("player", player(s.player()));
        root.put("realm", realm(s.realm()));
        root.put("settings", settings(s.settings()));
        root.put("factions", s.factions().stream().map(DashboardSectionCodec::faction).toList());
    }

    public static void putHistory(Map<String, Object> root, OverviewSnapshot s) {
        root.put("history", s.history().stream().map(DashboardSectionCodec::history).toList());
    }

    public static void putEconomy(Map<String, Object> root, EconomySnapshot s) {
        // realm already written by overview; operations is economy logistics surface
        root.put("operations", operations(s.operations()));
    }

    public static void putSettlements(Map<String, Object> root, SettlementSnapshot s) {
        root.put("settlements", s.settlements().stream().map(DashboardSectionCodec::settlement).toList());
    }

    public static void putWar(Map<String, Object> root, WarSnapshot s) {
        root.put("wars", s.wars().stream().map(DashboardSectionCodec::war).toList());
        root.put("warfare", warfare(s.warfare()));
    }

    public static void putLaw(Map<String, Object> root, LawSnapshot s) {
        root.put("bounties", s.bounties().stream().map(DashboardSectionCodec::bounty).toList());
    }

    public static void putEcology(Map<String, Object> root, EcologySnapshot s) {
        root.put("ecology", ecology(s.ecology()));
    }

    public static void putPolitics(Map<String, Object> root, PoliticsSnapshot s) {
        root.put("politics", politics(s.politics()));
    }

    public static void putForces(Map<String, Object> root, ForcesSnapshot s) {
        root.put("forces", forces(s.forces()));
    }

    public static void putMap(Map<String, Object> root, MapSnapshot s) {
        root.put("map", mapView(s.map()));
    }

    public static void putUnderworld(Map<String, Object> root, UnderworldSnapshot s) {
        root.put("underworld", underworld(s.underworld()));
    }

    public static void putWarRoom(Map<String, Object> root, RealmDashboardSnapshot.WarRoomView warRoom) {
        root.put("warRoom", warRoom(warRoom));
    }

    // --- encode maps (protocol 20 field names) ---

    private static Map<String, Object> jurisdiction(RealmDashboardSnapshot.JurisdictionView v) {
        return DashboardCodecJson.map("primaryId", v.primaryFactionId(), "primaryName", v.primaryName(),
                "secondaryId", v.secondaryFactionId(), "secondaryName", v.secondaryName(),
                "claimed", v.claimed(), "contested", v.contested());
    }

    private static Map<String, Object> player(RealmDashboardSnapshot.PlayerView v) {
        return DashboardCodecJson.map("actor", v.actorKey(), "memberFactionId", v.memberFactionId(),
                "memberFactionName", v.memberFactionName(), "rank", v.rank(), "service", v.servicePoints(),
                "reputation", v.localReputation(), "infamy", v.globalInfamy(), "wanted", v.wantedLevel(),
                "bounty", v.bounty(), "notoriety", v.notoriety(), "heat", v.heat(), "custody", v.inCustody(),
                "releaseDay", v.custodyReleaseDay(), "careerTrack", v.careerTrack(), "careerRank", v.careerRank(),
                "influence", new LinkedHashMap<>(v.influence()));
    }

    public static Map<String, Object> realm(RealmDashboardSnapshot.RealmView v) {
        return DashboardCodecJson.map("id", v.factionId(), "name", v.name(), "ruler", v.ruler(),
                "government", v.governmentType(), "succession", v.successionLaw(), "population", v.population(),
                "settlementCount", v.settlementCount(), "treasury", v.treasury(), "technology", v.technology(),
                "stability", v.stability(), "legitimacy", v.legitimacy(), "corruption", v.corruption(),
                "taxRate", v.taxRate(), "armyPersonnel", v.armyPersonnel(), "airframes", v.airframes(),
                "ships", v.ships(), "ports", v.ports(), "industry", v.industrialSites(),
                "shipments", v.activeShipments(), "wars", v.activeWars(), "treaties", v.activeTreaties(),
                "debts", v.activeDebts(), "grandProjects", v.grandProjects(), "campaignPlans", v.campaignPlans(),
                "resources", new LinkedHashMap<>(v.resources()), "marketPrices", new LinkedHashMap<>(v.marketPrices()),
                "marketBuyCosts", new LinkedHashMap<>(v.marketBuyCosts()),
                "marketSellPayouts", new LinkedHashMap<>(v.marketSellPayouts()));
    }

    public static Map<String, Object> settings(RealmDashboardSnapshot.SettingsView v) {
        return DashboardCodecJson.map("profile", v.profile(), "physicalRadius", v.physicalRadius(),
                "regionalRadius", v.regionalRadius(), "wildlife", v.wildlifeBudget(), "caravans", v.caravanBudget(),
                "military", v.militaryBudget(), "naval", v.navalBudget(), "constructionOps", v.constructionOpsPerTick());
    }

    private static Map<String, Object> faction(RealmDashboardSnapshot.FactionSummary v) {
        return DashboardCodecJson.map("id", v.id(), "name", v.name(), "ruler", v.ruler(), "population", v.population(),
                "settlements", v.settlements(), "treasury", v.treasury(), "technology", v.technology(),
                "local", v.localRealm(), "member", v.memberRealm());
    }

    private static Map<String, Object> settlement(RealmDashboardSnapshot.SettlementView v) {
        return DashboardCodecJson.map("id", v.id(), "name", v.name(), "tier", v.tier(),
                "developmentPriority", v.developmentPriority(), "developmentMode", v.developmentMode(),
                "origin", v.origin(), "population", v.population(), "housing", v.housing(),
                "verifiedHousing", v.verifiedHousing(), "housingDeficit", v.housingDeficit(),
                "registeredBuildings", v.registeredBuildings(), "prosperity", v.prosperity(), "unrest", v.unrest(),
                "food", v.foodSecurity(), "order", v.publicOrder(), "employment", v.employment(),
                "housingSat", v.housingSatisfaction(), "goods", v.goodsAccess(), "society", v.societySatisfaction(),
                "pressure", v.primaryPressure(), "pressureSeverity", v.pressureSeverity(), "cause", v.causeSummary(),
                "distance", v.distanceBlocks());
    }

    private static Map<String, Object> war(RealmDashboardSnapshot.WarView v) {
        return DashboardCodecJson.map("id", v.id(), "attackerId", v.attackerFactionId(), "attacker", v.attackerName(),
                "defenderId", v.defenderFactionId(), "defender", v.defenderName(), "goal", v.goal(),
                "targetSettlementId", v.targetSettlementId(), "targetSettlement", v.targetSettlementName(),
                "startDay", v.startDay(), "score", v.attackerScore(),
                "attackerExhaustion", v.attackerExhaustion(), "defenderExhaustion", v.defenderExhaustion());
    }

    private static Map<String, Object> warfare(RealmDashboardSnapshot.WarfareView v) {
        return DashboardCodecJson.map(
                "objectives", v.objectives().stream().map(DashboardSectionCodec::objective).toList(),
                "sieges", v.sieges().stream().map(DashboardSectionCodec::siege).toList(),
                "campaigns", v.campaigns().stream().map(DashboardSectionCodec::campaign).toList());
    }

    private static Map<String, Object> objective(RealmDashboardSnapshot.ObjectiveView v) {
        return DashboardCodecJson.map("id", v.id(), "armyId", v.armyId(), "type", v.type(), "target", v.target(),
                "priority", v.priority(), "distance", v.distanceBlocks());
    }

    private static Map<String, Object> siege(RealmDashboardSnapshot.SiegeView v) {
        return DashboardCodecJson.map("id", v.id(), "attacker", v.attacker(), "defender", v.defender(),
                "settlement", v.settlement(), "startDay", v.startDay(), "progress", v.progress(), "blockade", v.blockade());
    }

    private static Map<String, Object> campaign(RealmDashboardSnapshot.CampaignPlanView v) {
        return DashboardCodecJson.map("id", v.id(), "type", v.type(), "target", v.target(),
                "priority", v.priority(), "active", v.active());
    }

    private static Map<String, Object> operations(RealmDashboardSnapshot.OperationsView v) {
        return DashboardCodecJson.map(
                "shipments", v.shipments().stream().map(DashboardSectionCodec::shipment).toList(),
                "routes", v.routes().stream().map(DashboardSectionCodec::routeOps).toList(),
                "industry", v.industry().stream().map(DashboardSectionCodec::industryOps).toList(),
                "assistance", v.assistanceTasks().stream().map(DashboardSectionCodec::assistanceTask).toList());
    }

    private static Map<String, Object> assistanceTask(RealmDashboardSnapshot.AssistanceTaskView v) {
        return DashboardCodecJson.map("id", v.id(), "settlement", v.settlement(), "type", v.type(), "cause", v.cause(),
                "remaining", v.remainingPressure(), "progress", v.progress(), "expiresDay", v.expiresDay());
    }

    private static Map<String, Object> shipment(RealmDashboardSnapshot.ShipmentView v) {
        return DashboardCodecJson.map("id", v.id(), "seller", v.seller(), "buyer", v.buyer(), "resource", v.resource(),
                "amount", v.amount(), "value", v.value(), "progress", v.progress(), "distance", v.distanceBlocks());
    }

    private static Map<String, Object> routeOps(RealmDashboardSnapshot.RouteOpsView v) {
        return DashboardCodecJson.map("id", v.id(), "from", v.from(), "to", v.to(), "mode", v.mode(),
                "quality", v.quality(), "security", v.security(), "capacity", v.capacityPerDay(),
                "operational", v.operational());
    }

    private static Map<String, Object> industryOps(RealmDashboardSnapshot.IndustryOpsView v) {
        return DashboardCodecJson.map("id", v.id(), "settlement", v.settlement(), "kind", v.kind(), "level", v.level(),
                "status", v.status(), "condition", v.condition(), "starvedDays", v.starvedDays(),
                "downtimeDays", v.downtimeDays(), "cycles", v.cycles(), "utilization", v.utilization());
    }

    private static Map<String, Object> ecology(RealmDashboardSnapshot.EcologyView v) {
        return DashboardCodecJson.map("catalogSpecies", v.catalogSpecies(), "regionCount", v.regionCount(),
                "populationGroups", v.populationGroups(), "totalAnimals", v.totalAnimals(),
                "regions", v.regions().stream().map(DashboardSectionCodec::ecologyRegion).toList());
    }

    private static Map<String, Object> ecologyRegion(RealmDashboardSnapshot.RegionEcologyView v) {
        return DashboardCodecJson.map("id", v.id(), "biome", v.biome(), "x", v.x(), "z", v.z(),
                "distance", v.distanceBlocks(), "area", v.areaKm2(), "plants", v.plantBiomass(),
                "animals", v.animals(), "groups", v.groups(),
                "species", v.dominantSpecies().stream().map(DashboardSectionCodec::ecologySpecies).toList());
    }

    private static Map<String, Object> ecologySpecies(RealmDashboardSnapshot.SpeciesPopulationView v) {
        return DashboardCodecJson.map("id", v.speciesId(), "name", v.commonName(), "population", v.population(),
                "health", v.health(), "hunger", v.hunger(), "thirst", v.thirst(),
                "locomotion", v.locomotion(), "morphology", v.morphology());
    }

    private static Map<String, Object> bounty(RealmDashboardSnapshot.BountyView v) {
        return DashboardCodecJson.map("id", v.id(), "target", v.targetKey(), "issuerId", v.issuerFactionId(),
                "issuer", v.issuerName(), "reward", v.reward(), "status", v.status(), "assigned", v.assignedToYou());
    }

    private static Map<String, Object> politics(RealmDashboardSnapshot.PoliticsView v) {
        return DashboardCodecJson.map(
                "relations", v.relations().stream().map(DashboardSectionCodec::relation).toList(),
                "treaties", v.treaties().stream().map(DashboardSectionCodec::treaty).toList());
    }

    private static Map<String, Object> relation(RealmDashboardSnapshot.RelationView v) {
        return DashboardCodecJson.map("factionId", v.factionId(), "name", v.factionName(), "status", v.status(),
                "opinion", v.opinion(), "trade", v.tradeAgreement());
    }

    private static Map<String, Object> treaty(RealmDashboardSnapshot.TreatyView v) {
        return DashboardCodecJson.map("id", v.id(), "otherFactionId", v.otherFactionId(), "other", v.otherFactionName(),
                "type", v.type(), "startDay", v.startDay(), "endDay", v.endDay());
    }

    private static Map<String, Object> forces(RealmDashboardSnapshot.ForcesView v) {
        return DashboardCodecJson.map(
                "air", v.airWings().stream().map(DashboardSectionCodec::airWing).toList(),
                "fleets", v.fleets().stream().map(DashboardSectionCodec::fleet).toList(),
                "ports", v.ports().stream().map(DashboardSectionCodec::port).toList());
    }

    private static Map<String, Object> airWing(RealmDashboardSnapshot.AirWingView v) {
        return DashboardCodecJson.map("id", v.id(), "model", v.model(), "role", v.role(), "aircraft", v.aircraft(),
                "mission", v.mission(), "fuel", v.fuel(), "readiness", v.readiness(), "experience", v.experience(),
                "distance", v.distanceBlocks());
    }

    private static Map<String, Object> fleet(RealmDashboardSnapshot.FleetView v) {
        return DashboardCodecJson.map("id", v.id(), "ships", v.ships(), "composition", v.composition(),
                "mission", v.mission(), "fuel", v.fuel(), "readiness", v.readiness(), "supply", v.supply(),
                "experience", v.experience(), "embarked", v.embarkedPersonnel(), "power", v.combatPower(),
                "distance", v.distanceBlocks());
    }

    private static Map<String, Object> port(RealmDashboardSnapshot.PortView v) {
        return DashboardCodecJson.map("id", v.id(), "settlement", v.settlement(), "level", v.level(),
                "condition", v.condition(), "security", v.security(), "operational", v.operational(),
                "distance", v.distanceBlocks());
    }

    private static Map<String, Object> history(RealmDashboardSnapshot.HistoryView v) {
        return DashboardCodecJson.map("day", v.day(), "type", v.type(), "message", v.message());
    }

    // Map + underworld encode are large — keep as package helpers used by RealmDashboardCodec until further split.
    public static Map<String, Object> mapView(RealmDashboardSnapshot.StrategicMapView v) {
        return DashboardCodecJson.map(
                "playerX", v.playerX(), "playerZ", v.playerZ(), "minX", v.minX(), "maxX", v.maxX(),
                "minZ", v.minZ(), "maxZ", v.maxZ(),
                "settlements", v.settlements().stream().map(DashboardSectionCodec::mapSettlement).toList(),
                "claims", v.claims().stream().map(DashboardSectionCodec::mapClaim).toList(),
                "routes", v.routes().stream().map(DashboardSectionCodec::mapRoute).toList(),
                "armies", v.armies().stream().map(DashboardSectionCodec::mapArmy).toList(),
                "fronts", v.fronts().stream().map(DashboardSectionCodec::mapFront).toList(),
                "resourceClaims", v.resourceClaims().stream().map(DashboardSectionCodec::mapResourceClaim).toList(),
                "shipments", v.shipments().stream().map(DashboardSectionCodec::mapShipment).toList(),
                "raids", v.raids().stream().map(DashboardSectionCodec::mapRaid).toList(),
                "migrations", v.migrations().stream().map(DashboardSectionCodec::mapMigration).toList(),
                "ports", v.ports().stream().map(DashboardSectionCodec::mapPort).toList(),
                "fleets", v.fleets().stream().map(DashboardSectionCodec::mapFleet).toList(),
                "airWings", v.airWings().stream().map(DashboardSectionCodec::mapAirWing).toList(),
                "pirates", v.pirates().stream().map(DashboardSectionCodec::mapPirate).toList(),
                "pirateHideouts", v.pirateHideouts().stream().map(DashboardSectionCodec::mapPirateHideout).toList(),
                "epidemics", v.epidemics().stream().map(DashboardSectionCodec::mapEpidemic).toList(),
                "caches", v.caches().stream().map(DashboardSectionCodec::mapCache).toList(),
                "ruins", v.ruins().stream().map(DashboardSectionCodec::mapRuin).toList());
    }

    private static Map<String, Object> mapSettlement(RealmDashboardSnapshot.MapSettlement v) {
        return DashboardCodecJson.map("id", v.id(), "factionId", v.factionId(), "name", v.name(), "tier", v.tier(),
                "x", v.x(), "z", v.z(), "population", v.population());
    }
    private static Map<String, Object> mapClaim(RealmDashboardSnapshot.MapClaim v) {
        return DashboardCodecJson.map("settlementId", v.settlementId(), "factionId", v.factionId(),
                "x", v.x(), "z", v.z(), "radius", v.radius());
    }
    private static Map<String, Object> mapRoute(RealmDashboardSnapshot.MapRoute v) {
        return DashboardCodecJson.map("id", v.id(), "factionId", v.factionId(),
                "fromSettlementId", v.fromSettlementId(), "toSettlementId", v.toSettlementId(), "mode", v.mode(),
                "fromX", v.fromX(), "fromZ", v.fromZ(), "toX", v.toX(), "toZ", v.toZ(),
                "quality", v.quality(), "security", v.security(), "operational", v.operational());
    }
    private static Map<String, Object> mapArmy(RealmDashboardSnapshot.MapArmy v) {
        return DashboardCodecJson.map("id", v.id(), "factionId", v.factionId(), "x", v.x(), "z", v.z(),
                "personnel", v.personnel(), "morale", v.morale(), "supply", v.supply());
    }
    private static Map<String, Object> mapFront(RealmDashboardSnapshot.MapFront v) {
        return DashboardCodecJson.map("warId", v.warId(), "attackerFactionId", v.attackerFactionId(),
                "defenderFactionId", v.defenderFactionId(), "fromX", v.fromX(), "fromZ", v.fromZ(),
                "toX", v.toX(), "toZ", v.toZ(), "goal", v.goal());
    }
    private static Map<String, Object> mapResourceClaim(RealmDashboardSnapshot.MapResourceClaim v) {
        return DashboardCodecJson.map("id", v.id(), "factionId", v.factionId(), "type", v.type(),
                "x", v.x(), "z", v.z(), "strength", v.strength(), "contestedByFactionId", v.contestedByFactionId());
    }
    private static Map<String, Object> mapShipment(RealmDashboardSnapshot.MapShipment v) {
        return DashboardCodecJson.map("id", v.id(), "sellerFactionId", v.sellerFactionId(),
                "buyerFactionId", v.buyerFactionId(), "resource", v.resource(), "x", v.x(), "z", v.z(),
                "progress", v.progress(), "value", v.value());
    }
    private static Map<String, Object> mapRaid(RealmDashboardSnapshot.MapRaid v) {
        return DashboardCodecJson.map("id", v.id(), "factionId", v.factionId(),
                "targetSettlementId", v.targetSettlementId(), "bandit", v.bandit(), "x", v.x(), "z", v.z(),
                "manpower", v.manpower(), "morale", v.morale(), "progress", v.progress());
    }
    private static Map<String, Object> mapMigration(RealmDashboardSnapshot.MapMigration v) {
        return DashboardCodecJson.map("id", v.id(), "factionId", v.factionId(), "reason", v.reason(),
                "status", v.status(), "x", v.x(), "z", v.z(), "people", v.people(), "health", v.health());
    }
    private static Map<String, Object> mapPort(RealmDashboardSnapshot.MapPort v) {
        return DashboardCodecJson.map("id", v.id(), "factionId", v.factionId(), "settlementId", v.settlementId(),
                "x", v.x(), "z", v.z(), "level", v.level(), "security", v.security(), "operational", v.operational());
    }
    private static Map<String, Object> mapFleet(RealmDashboardSnapshot.MapFleet v) {
        return DashboardCodecJson.map("id", v.id(), "factionId", v.factionId(), "x", v.x(), "z", v.z(),
                "ships", v.ships(), "mission", v.mission());
    }
    private static Map<String, Object> mapAirWing(RealmDashboardSnapshot.MapAirWing v) {
        return DashboardCodecJson.map("id", v.id(), "factionId", v.factionId(), "x", v.x(), "z", v.z(),
                "aircraft", v.aircraft(), "mission", v.mission());
    }
    private static Map<String, Object> mapPirate(RealmDashboardSnapshot.MapPirate v) {
        return DashboardCodecJson.map("id", v.id(), "x", v.x(), "z", v.z(), "strength", v.strength(),
                "morale", v.morale(), "loot", v.loot());
    }
    private static Map<String, Object> mapPirateHideout(RealmDashboardSnapshot.MapPirateHideout v) {
        return DashboardCodecJson.map("id", v.id(), "bandId", v.bandId(), "x", v.x(), "z", v.z(),
                "defense", v.defense(), "storedLoot", v.storedLoot(), "discovered", v.discovered());
    }
    private static Map<String, Object> mapEpidemic(RealmDashboardSnapshot.MapEpidemic v) {
        return DashboardCodecJson.map("id", v.id(), "settlementId", v.settlementId(), "disease", v.disease(),
                "x", v.x(), "z", v.z(), "severity", v.severity(), "infectedFraction", v.infectedFraction());
    }
    private static Map<String, Object> mapCache(RealmDashboardSnapshot.MapCache v) {
        return DashboardCodecJson.map("id", v.id(), "ownerFactionId", v.ownerFactionId(), "x", v.x(), "z", v.z(),
                "value", v.value(), "compromised", v.compromised());
    }
    private static Map<String, Object> mapRuin(RealmDashboardSnapshot.MapRuin v) {
        return DashboardCodecJson.map("id", v.id(), "originalFactionId", v.originalFactionId(), "name", v.name(),
                "cause", v.cause(), "x", v.x(), "z", v.z(), "preservation", v.preservation(), "looted", v.looted());
    }

    private static Map<String, Object> underworld(RealmDashboardSnapshot.UnderworldView v) {
        return DashboardCodecJson.map("streetCred", v.streetCred(), "briberySkill", v.briberySkill(),
                "contractsCompleted", v.contractsCompleted(),
                "blackMarket", v.blackMarketEligible(),
                "contracts", v.contracts().stream().map(c -> DashboardCodecJson.map(
                        "id", c.id(), "type", c.type(), "jurisdiction", c.jurisdiction(),
                        "target", c.target(), "reward", c.reward(), "daysLeft", c.daysLeft(),
                        "status", c.status(), "accepted", c.acceptedByYou())).toList(),
                "lots", v.stolenLots().stream().map(l -> DashboardCodecJson.map(
                        "id", l.id(), "good", l.goodKey(), "value", l.value())).toList());
    }

    private static Map<String, Object> warRoom(RealmDashboardSnapshot.WarRoomView v) {
        return DashboardCodecJson.map(
                "enemies", v.enemies().stream().map(e -> DashboardCodecJson.map(
                        "factionId", e.factionId(), "name", e.name(), "goals", e.validGoals(),
                        "targetId", e.suggestedTargetSettlementId(), "target", e.suggestedTargetName(),
                        "declare", e.canDeclare(), "petition", e.petitionOnly(), "deny", e.denyReason())).toList(),
                "armies", v.armies().stream().map(a -> DashboardCodecJson.map(
                        "id", a.id(), "personnel", a.personnel(), "morale", a.morale(), "supply", a.supply(),
                        "power", a.combatPower(), "x", a.x(), "z", a.z(), "home", a.homeSettlement(),
                        "objective", a.objective())).toList(),
                "escorts", v.escortTargets().stream().map(t -> DashboardCodecJson.map(
                        "id", t.id(), "kind", t.kind(), "label", t.label())).toList(),
                "hostileSettlements", v.hostileSettlementIds(),
                "defaultHostile", v.defaultHostileSettlementId(),
                "defaultEscort", v.defaultEscortTargetId(),
                "defaultPatrol", v.defaultPatrolSettlementId());
    }
}
