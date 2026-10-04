package dev.livingrealms.sim.ui;

import java.util.List;
import java.util.Map;

/**
 * Bounded, read-only view of canonical simulation state for a single player.
 * This DTO is deliberately detached from Minecraft classes so it can be tested headlessly
 * and transported over the network without exposing mutable simulation objects.
 */
public record RealmDashboardSnapshot(
        int protocolVersion,
        long day,
        String worldSummary,
        JurisdictionView jurisdiction,
        PlayerView player,
        RealmView realm,
        SettingsView settings,
        List<FactionSummary> factions,
        List<SettlementView> settlements,
        List<WarView> wars,
        WarfareView warfare,
        List<BountyView> bounties,
        OperationsView operations,
        EcologyView ecology,
        PoliticsView politics,
        ForcesView forces,
        StrategicMapView map,
        List<HistoryView> history
) {
    public static final int PROTOCOL_VERSION = 15;

    public RealmDashboardSnapshot {
        if (protocolVersion != PROTOCOL_VERSION) throw new IllegalArgumentException("protocolVersion");
        if (day < 0) throw new IllegalArgumentException("day");
        worldSummary = safe(worldSummary);
        jurisdiction = jurisdiction == null ? JurisdictionView.wilderness() : jurisdiction;
        player = player == null ? PlayerView.empty() : player;
        realm = realm == null ? RealmView.none() : realm;
        settings = settings == null ? SettingsView.defaults() : settings;
        factions = List.copyOf(factions == null ? List.of() : factions);
        settlements = List.copyOf(settlements == null ? List.of() : settlements);
        wars = List.copyOf(wars == null ? List.of() : wars);
        warfare = warfare == null ? WarfareView.empty() : warfare;
        bounties = List.copyOf(bounties == null ? List.of() : bounties);
        operations = operations == null ? OperationsView.empty() : operations;
        ecology = ecology == null ? EcologyView.empty() : ecology;
        politics = politics == null ? PoliticsView.empty() : politics;
        forces = forces == null ? ForcesView.empty() : forces;
        map = map == null ? StrategicMapView.empty() : map;
        history = List.copyOf(history == null ? List.of() : history);
    }

    private static String safe(String value) { return value == null ? "" : value; }

    public record JurisdictionView(long primaryFactionId, String primaryName, long secondaryFactionId,
                                   String secondaryName, boolean claimed, boolean contested) {
        public JurisdictionView {
            primaryName = safe(primaryName); secondaryName = safe(secondaryName);
        }
        public static JurisdictionView wilderness(){return new JurisdictionView(0,"",0,"",false,false);}
    }

    public record PlayerView(String actorKey, long memberFactionId, String memberFactionName, String rank,
                             double servicePoints, double localReputation, double globalInfamy,
                             String wantedLevel, double bounty, double notoriety, double heat,
                             boolean inCustody, long custodyReleaseDay) {
        public PlayerView {
            actorKey=safe(actorKey);memberFactionName=safe(memberFactionName);rank=safe(rank);wantedLevel=safe(wantedLevel);
            servicePoints=finite(servicePoints);localReputation=finite(localReputation);globalInfamy=finite(globalInfamy);
            bounty=Math.max(0,finite(bounty));notoriety=Math.max(0,finite(notoriety));heat=Math.max(0,finite(heat));
        }
        public static PlayerView empty(){return new PlayerView("",0,"","OUTSIDER",0,0,0,"NONE",0,0,0,false,-1);}
    }

    public record RealmView(long factionId, String name, String ruler, String governmentType, String successionLaw,
                            int population, int settlementCount, double treasury, double technology,
                            double stability, double legitimacy, double corruption, double taxRate,
                            int armyPersonnel, int airframes, int ships, int ports, int industrialSites,
                            int activeShipments, int activeWars, int activeTreaties, Map<String,Double> resources, Map<String,Double> marketPrices, Map<String,Integer> marketBuyCosts, Map<String,Integer> marketSellPayouts) {
        public RealmView {
            name=safe(name);ruler=safe(ruler);governmentType=safe(governmentType);successionLaw=safe(successionLaw);
            population=Math.max(0,population);settlementCount=Math.max(0,settlementCount);treasury=Math.max(0,finite(treasury));technology=Math.max(0,finite(technology));
            stability=bounded(stability);legitimacy=bounded(legitimacy);corruption=bounded(corruption);taxRate=Math.max(0,finite(taxRate));
            armyPersonnel=Math.max(0,armyPersonnel);airframes=Math.max(0,airframes);ships=Math.max(0,ships);ports=Math.max(0,ports);industrialSites=Math.max(0,industrialSites);activeShipments=Math.max(0,activeShipments);activeWars=Math.max(0,activeWars);activeTreaties=Math.max(0,activeTreaties);
            resources=Map.copyOf(resources==null?Map.of():resources);marketPrices=Map.copyOf(marketPrices==null?Map.of():marketPrices);marketBuyCosts=Map.copyOf(marketBuyCosts==null?Map.of():marketBuyCosts);marketSellPayouts=Map.copyOf(marketSellPayouts==null?Map.of():marketSellPayouts);
        }
        public static RealmView none(){return new RealmView(0,"","","","",0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,Map.of(),Map.of(),Map.of(),Map.of());}
    }

    public record SettingsView(String profile,double physicalRadius,double regionalRadius,int wildlifeBudget,int caravanBudget,int militaryBudget,int navalBudget,int constructionOpsPerTick){
        public SettingsView{profile=safe(profile);physicalRadius=Math.max(1,finite(physicalRadius));regionalRadius=Math.max(physicalRadius,finite(regionalRadius));wildlifeBudget=Math.max(0,wildlifeBudget);caravanBudget=Math.max(0,caravanBudget);militaryBudget=Math.max(0,militaryBudget);navalBudget=Math.max(0,navalBudget);constructionOpsPerTick=Math.max(1,constructionOpsPerTick);}
        public static SettingsView defaults(){return new SettingsView("BALANCED",320,2048,160,24,96,32,160);}
    }

    public record FactionSummary(long id,String name,String ruler,int population,int settlements,double treasury,double technology,boolean localRealm,boolean memberRealm){
        public FactionSummary{name=safe(name);ruler=safe(ruler);population=Math.max(0,population);settlements=Math.max(0,settlements);treasury=Math.max(0,finite(treasury));technology=Math.max(0,finite(technology));}
    }

    public record SettlementView(long id,String name,String tier,String developmentPriority,int population,int housing,double prosperity,double unrest,double foodSecurity,double publicOrder,double employment,double housingSatisfaction,double goodsAccess,double societySatisfaction,String primaryPressure,double pressureSeverity,String causeSummary,double distanceBlocks){
        public SettlementView{name=safe(name);tier=safe(tier);developmentPriority=safe(developmentPriority);population=Math.max(0,population);housing=Math.max(0,housing);prosperity=bounded(prosperity);unrest=bounded(unrest);foodSecurity=bounded(foodSecurity);publicOrder=bounded(publicOrder);employment=bounded(employment);housingSatisfaction=bounded(housingSatisfaction);goodsAccess=bounded(goodsAccess);societySatisfaction=bounded(societySatisfaction);primaryPressure=safe(primaryPressure);pressureSeverity=bounded(pressureSeverity);causeSummary=trimCause(causeSummary);distanceBlocks=Math.max(0,finite(distanceBlocks));}
        private static String trimCause(String value){
            String s=safe(value);
            return s.length()<=160?s:s.substring(0,157)+"...";
        }
    }

    public record WarView(long id,long attackerFactionId,String attackerName,long defenderFactionId,String defenderName,String goal,long startDay,double attackerScore,double attackerExhaustion,double defenderExhaustion){
        public WarView{attackerName=safe(attackerName);defenderName=safe(defenderName);goal=safe(goal);attackerScore=finite(attackerScore);attackerExhaustion=bounded(attackerExhaustion);defenderExhaustion=bounded(defenderExhaustion);}
    }



    public record WarfareView(List<ObjectiveView> objectives,List<SiegeView> sieges){
        public WarfareView{objectives=List.copyOf(objectives==null?List.of():objectives);sieges=List.copyOf(sieges==null?List.of():sieges);}
        public static WarfareView empty(){return new WarfareView(List.of(),List.of());}
    }
    public record ObjectiveView(long id,long armyId,String type,String target,int priority,double distanceBlocks){
        public ObjectiveView{if(id<=0||armyId<=0)throw new IllegalArgumentException("objective identity");type=safe(type);target=safe(target);priority=Math.max(0,priority);distanceBlocks=Math.max(0,finite(distanceBlocks));}
    }
    public record SiegeView(long id,String attacker,String defender,String settlement,long startDay,double progress,double blockade){
        public SiegeView{if(id<=0)throw new IllegalArgumentException("siege id");attacker=safe(attacker);defender=safe(defender);settlement=safe(settlement);progress=bounded(progress);blockade=bounded(blockade);}
    }

    public record BountyView(long id,String targetKey,long issuerFactionId,String issuerName,double reward,String status,boolean assignedToYou){
        public BountyView{if(id<=0)throw new IllegalArgumentException("id");targetKey=safe(targetKey);issuerName=safe(issuerName);reward=Math.max(0,finite(reward));status=safe(status);}
    }


    public record OperationsView(List<ShipmentView> shipments,List<RouteOpsView> routes,List<IndustryOpsView> industry){
        public OperationsView{shipments=List.copyOf(shipments==null?List.of():shipments);routes=List.copyOf(routes==null?List.of():routes);industry=List.copyOf(industry==null?List.of():industry);}
        public static OperationsView empty(){return new OperationsView(List.of(),List.of(),List.of());}
    }
    public record ShipmentView(long id,String seller,String buyer,String resource,double amount,double value,double progress,double distanceBlocks){
        public ShipmentView{seller=safe(seller);buyer=safe(buyer);resource=safe(resource);amount=Math.max(0,finite(amount));value=Math.max(0,finite(value));progress=bounded(progress);distanceBlocks=Math.max(0,finite(distanceBlocks));}
    }
    public record RouteOpsView(long id,String from,String to,String mode,double quality,double security,double capacityPerDay,boolean operational){
        public RouteOpsView{from=safe(from);to=safe(to);mode=safe(mode);quality=bounded(quality);security=bounded(security);capacityPerDay=Math.max(0,finite(capacityPerDay));}
    }
    public record IndustryOpsView(long id,String settlement,String kind,int level,String status,double condition,int starvedDays,int downtimeDays,int cycles,double utilization){
        public IndustryOpsView{settlement=safe(settlement);kind=safe(kind);level=Math.max(1,level);status=safe(status);condition=bounded(condition);starvedDays=Math.max(0,starvedDays);downtimeDays=Math.max(0,downtimeDays);cycles=Math.max(0,cycles);utilization=bounded(utilization);}
    }


    public record EcologyView(int catalogSpecies,int regionCount,int populationGroups,double totalAnimals,List<RegionEcologyView> regions){
        public EcologyView{catalogSpecies=Math.max(0,catalogSpecies);regionCount=Math.max(0,regionCount);populationGroups=Math.max(0,populationGroups);totalAnimals=Math.max(0,finite(totalAnimals));regions=List.copyOf(regions==null?List.of():regions);}
        public static EcologyView empty(){return new EcologyView(0,0,0,0,List.of());}
    }
    public record RegionEcologyView(long id,String biome,double x,double z,double distanceBlocks,double areaKm2,double plantBiomass,double animals,int groups,List<SpeciesPopulationView> dominantSpecies){
        public RegionEcologyView{if(id<=0)throw new IllegalArgumentException("id");biome=safe(biome);x=finite(x);z=finite(z);distanceBlocks=Math.max(0,finite(distanceBlocks));areaKm2=Math.max(0,finite(areaKm2));plantBiomass=Math.max(0,finite(plantBiomass));animals=Math.max(0,finite(animals));groups=Math.max(0,groups);dominantSpecies=List.copyOf(dominantSpecies==null?List.of():dominantSpecies);}
    }
    public record SpeciesPopulationView(String speciesId,String commonName,double population,double health,double hunger,double thirst,String locomotion,String morphology){
        public SpeciesPopulationView{speciesId=safe(speciesId);commonName=safe(commonName);population=Math.max(0,finite(population));health=bounded(health);hunger=bounded(hunger);thirst=bounded(thirst);locomotion=safe(locomotion);morphology=safe(morphology);}
    }


    public record PoliticsView(List<RelationView> relations,List<TreatyView> treaties){
        public PoliticsView{relations=List.copyOf(relations==null?List.of():relations);treaties=List.copyOf(treaties==null?List.of():treaties);}
        public static PoliticsView empty(){return new PoliticsView(List.of(),List.of());}
    }
    public record RelationView(long factionId,String factionName,String status,double opinion,boolean tradeAgreement){
        public RelationView{if(factionId<=0)throw new IllegalArgumentException("factionId");factionName=safe(factionName);status=safe(status);opinion=Math.max(-100,Math.min(100,finite(opinion)));}
    }
    public record TreatyView(long id,long otherFactionId,String otherFactionName,String type,long startDay,long endDay){
        public TreatyView{if(id<=0||otherFactionId<=0)throw new IllegalArgumentException("treaty identity");otherFactionName=safe(otherFactionName);type=safe(type);if(startDay<0||endDay<startDay)throw new IllegalArgumentException("treaty days");}
    }


    public record ForcesView(List<AirWingView> airWings,List<FleetView> fleets,List<PortView> ports){
        public ForcesView{airWings=List.copyOf(airWings==null?List.of():airWings);fleets=List.copyOf(fleets==null?List.of():fleets);ports=List.copyOf(ports==null?List.of():ports);}
        public static ForcesView empty(){return new ForcesView(List.of(),List.of(),List.of());}
    }
    public record AirWingView(long id,String model,String role,int aircraft,String mission,double fuel,double readiness,double experience,double distanceBlocks){
        public AirWingView{if(id<=0)throw new IllegalArgumentException("id");model=safe(model);role=safe(role);aircraft=Math.max(0,aircraft);mission=safe(mission);fuel=bounded(fuel);readiness=bounded(readiness);experience=bounded(experience);distanceBlocks=Math.max(0,finite(distanceBlocks));}
    }
    public record FleetView(long id,int ships,String composition,String mission,double fuel,double readiness,double supply,double experience,int embarkedPersonnel,double combatPower,double distanceBlocks){
        public FleetView{if(id<=0)throw new IllegalArgumentException("id");ships=Math.max(0,ships);composition=safe(composition);mission=safe(mission);fuel=bounded(fuel);readiness=bounded(readiness);supply=bounded(supply);experience=bounded(experience);embarkedPersonnel=Math.max(0,embarkedPersonnel);combatPower=Math.max(0,finite(combatPower));distanceBlocks=Math.max(0,finite(distanceBlocks));}
    }
    public record PortView(long id,String settlement,int level,double condition,double security,boolean operational,double distanceBlocks){
        public PortView{if(id<=0)throw new IllegalArgumentException("id");settlement=safe(settlement);level=Math.max(1,level);condition=bounded(condition);security=bounded(security);distanceBlocks=Math.max(0,finite(distanceBlocks));}
    }

    public record StrategicMapView(double playerX,double playerZ,double minX,double maxX,double minZ,double maxZ,
                                   List<MapSettlement> settlements,List<MapClaim> claims,List<MapRoute> routes,
                                   List<MapArmy> armies,List<MapFront> fronts,List<MapResourceClaim> resourceClaims,
                                   List<MapShipment> shipments,List<MapRaid> raids,List<MapMigration> migrations,
                                   List<MapPort> ports,List<MapFleet> fleets,List<MapAirWing> airWings,
                                   List<MapPirate> pirates,List<MapPirateHideout> pirateHideouts,List<MapEpidemic> epidemics,List<MapCache> caches,List<MapRuin> ruins){
        public StrategicMapView{
            playerX=finite(playerX);playerZ=finite(playerZ);minX=finite(minX);maxX=finite(maxX);minZ=finite(minZ);maxZ=finite(maxZ);
            if(maxX<minX){double t=minX;minX=maxX;maxX=t;} if(maxZ<minZ){double t=minZ;minZ=maxZ;maxZ=t;}
            settlements=List.copyOf(settlements==null?List.of():settlements);claims=List.copyOf(claims==null?List.of():claims);routes=List.copyOf(routes==null?List.of():routes);armies=List.copyOf(armies==null?List.of():armies);fronts=List.copyOf(fronts==null?List.of():fronts);resourceClaims=List.copyOf(resourceClaims==null?List.of():resourceClaims);shipments=List.copyOf(shipments==null?List.of():shipments);raids=List.copyOf(raids==null?List.of():raids);migrations=List.copyOf(migrations==null?List.of():migrations);ports=List.copyOf(ports==null?List.of():ports);fleets=List.copyOf(fleets==null?List.of():fleets);airWings=List.copyOf(airWings==null?List.of():airWings);pirates=List.copyOf(pirates==null?List.of():pirates);pirateHideouts=List.copyOf(pirateHideouts==null?List.of():pirateHideouts);epidemics=List.copyOf(epidemics==null?List.of():epidemics);caches=List.copyOf(caches==null?List.of():caches);ruins=List.copyOf(ruins==null?List.of():ruins);
        }
        public static StrategicMapView empty(){return new StrategicMapView(0,0,-1,1,-1,1,List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of());}
    }

    public record MapSettlement(long id,long factionId,String name,String tier,double x,double z,int population){
        public MapSettlement{name=safe(name);tier=safe(tier);x=finite(x);z=finite(z);population=Math.max(0,population);}
    }
    public record MapClaim(long settlementId,long factionId,double x,double z,double radius){
        public MapClaim{x=finite(x);z=finite(z);radius=Math.max(0,finite(radius));}
    }
    public record MapRoute(long id,long factionId,long fromSettlementId,long toSettlementId,String mode,double fromX,double fromZ,double toX,double toZ,double quality,double security,boolean operational){
        public MapRoute{mode=safe(mode);fromX=finite(fromX);fromZ=finite(fromZ);toX=finite(toX);toZ=finite(toZ);quality=bounded(quality);security=bounded(security);}
    }
    public record MapArmy(long id,long factionId,double x,double z,int personnel,double morale,double supply){
        public MapArmy{x=finite(x);z=finite(z);personnel=Math.max(0,personnel);morale=bounded(morale);supply=bounded(supply);}
    }
    public record MapFront(long warId,long attackerFactionId,long defenderFactionId,double fromX,double fromZ,double toX,double toZ,String goal){
        public MapFront{fromX=finite(fromX);fromZ=finite(fromZ);toX=finite(toX);toZ=finite(toZ);goal=safe(goal);}
    }

    public record MapResourceClaim(long id,long factionId,String type,double x,double z,double strength,long contestedByFactionId){public MapResourceClaim{type=safe(type);x=finite(x);z=finite(z);strength=bounded(strength);}}
    public record MapShipment(long id,long sellerFactionId,long buyerFactionId,String resource,double x,double z,double progress,double value){public MapShipment{resource=safe(resource);x=finite(x);z=finite(z);progress=bounded(progress);value=Math.max(0,finite(value));}}
    public record MapRaid(long id,long factionId,long targetSettlementId,boolean bandit,double x,double z,int manpower,double morale,double progress){public MapRaid{x=finite(x);z=finite(z);manpower=Math.max(0,manpower);morale=bounded(morale);progress=bounded(progress);}}
    public record MapMigration(long id,long factionId,String reason,String status,double x,double z,int people,double health){public MapMigration{reason=safe(reason);status=safe(status);x=finite(x);z=finite(z);people=Math.max(0,people);health=bounded(health);}}
    public record MapPort(long id,long factionId,long settlementId,double x,double z,int level,double security,boolean operational){public MapPort{x=finite(x);z=finite(z);level=Math.max(1,level);security=bounded(security);}}
    public record MapFleet(long id,long factionId,double x,double z,int ships,String mission){public MapFleet{x=finite(x);z=finite(z);ships=Math.max(0,ships);mission=safe(mission);}}
    public record MapAirWing(long id,long factionId,double x,double z,int aircraft,String mission){public MapAirWing{x=finite(x);z=finite(z);aircraft=Math.max(0,aircraft);mission=safe(mission);}}
    public record MapPirate(long id,double x,double z,int strength,double morale,double loot){public MapPirate{x=finite(x);z=finite(z);strength=Math.max(0,strength);morale=bounded(morale);loot=Math.max(0,finite(loot));}}
    public record MapPirateHideout(long id,long bandId,double x,double z,double defense,double storedLoot,boolean discovered){public MapPirateHideout{x=finite(x);z=finite(z);defense=bounded(defense);storedLoot=Math.max(0,finite(storedLoot));}}
    public record MapEpidemic(long id,long settlementId,String disease,double x,double z,double severity,double infectedFraction){public MapEpidemic{disease=safe(disease);x=finite(x);z=finite(z);severity=bounded(severity);infectedFraction=bounded(infectedFraction);}}
    public record MapCache(long id,long ownerFactionId,double x,double z,double value,boolean compromised){public MapCache{x=finite(x);z=finite(z);value=Math.max(0,finite(value));}}
    public record MapRuin(long id,long originalFactionId,String name,String cause,double x,double z,double preservation,boolean looted){public MapRuin{name=safe(name);cause=safe(cause);x=finite(x);z=finite(z);preservation=bounded(preservation);}}

    public record HistoryView(long day,String type,String message){
        public HistoryView{if(day<0)throw new IllegalArgumentException("history day");type=safe(type);message=safe(message);}
    }

    private static double finite(double value){return Double.isFinite(value)?value:0;}
    private static double bounded(double value){return Math.max(0,Math.min(1,finite(value)));}
}
