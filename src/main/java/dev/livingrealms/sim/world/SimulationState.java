package dev.livingrealms.sim.world;

import dev.livingrealms.sim.aviation.*;
import dev.livingrealms.sim.biome.*;
import dev.livingrealms.sim.civilization.*;
import dev.livingrealms.sim.config.SimulationConfig;
import dev.livingrealms.sim.diplomacy.*;
import dev.livingrealms.sim.ecology.*;
import dev.livingrealms.sim.faction.*;
import dev.livingrealms.sim.government.GovernmentEngine;
import dev.livingrealms.sim.industry.*;
import dev.livingrealms.sim.economy.primary.PrimaryEconomyEngine;
import dev.livingrealms.sim.law.*;
import dev.livingrealms.sim.logistics.*;
import dev.livingrealms.sim.military.*;
import dev.livingrealms.sim.naval.*;
import dev.livingrealms.sim.player.*;
import dev.livingrealms.sim.society.*;
import dev.livingrealms.sim.social.*;
import dev.livingrealms.sim.transport.*;
import dev.livingrealms.sim.util.*;
import java.util.*;

/** Canonical authoritative state. Minecraft entities are projections of this model, never the source of truth. */
public final class SimulationState {
    public static final int MAX_RESOURCE_CLAIMS=8_000, MAX_RAIDS=256, MAX_LEGENDS=512, MAX_HOUSEHOLDS=100_000, MAX_EPIDEMICS=512, MAX_MIGRATIONS=512, MAX_JUSTICE_CASES=2_048, MAX_HIDDEN_CACHES=512, MAX_PIRATE_BANDS=128, MAX_DIPLOMATIC_MARRIAGES=512, MAX_CIVIC_EVENTS=512, MAX_INTELLIGENCE_OPERATIONS=512, MAX_PROPAGANDA_CAMPAIGNS=256, MAX_RUIN_SITES=512, MAX_ASSISTANCE_TASKS=2_048, MAX_PIRATE_HIDEOUTS=128;
    private final long seed;
    private final SimClock clock=new SimClock();
    private SimulationConfig config;
    private Map<String,SpeciesDefinition> species;
    private final Map<String,EcoBiome> biomes;
    private final List<EcosystemRegion> regions=new ArrayList<>();
    private final List<Faction> factions=new ArrayList<>();
    private final List<TradeShipment> shipments=new ArrayList<>();
    private final List<Treaty> treaties=new ArrayList<>();
    private final List<WarState> wars=new ArrayList<>();
    private final List<TransportRoute> routes=new ArrayList<>();
    private final List<MilitaryObjective> objectives=new ArrayList<>();
    private final List<SiegeState> sieges=new ArrayList<>();
    private final List<AirWing> airWings=new ArrayList<>();
    private final List<BountyContract> bounties=new ArrayList<>();
    private final List<CustodyRecord> custody=new ArrayList<>();
    private final List<PortState> ports=new ArrayList<>();
    private final List<Fleet> fleets=new ArrayList<>();
    private final List<IndustrialSite> industrialSites=new ArrayList<>();
    private final List<SocialCitizen> socialCitizens=new ArrayList<>();
    private final Map<Long,SettlementCivilizationState> settlementCivilizations=new LinkedHashMap<>();
    private final Map<Long,FactionCivilizationState> factionCivilizations=new LinkedHashMap<>();
    private final List<ResourceClaim> resourceClaims=new ArrayList<>();
    private final List<RaidParty> raids=new ArrayList<>();
    private final List<LegendRecord> legends=new ArrayList<>();
    private final List<HouseholdState> households=new ArrayList<>();
    private final List<EpidemicRecord> epidemics=new ArrayList<>();
    private final List<MigrationGroup> migrationGroups=new ArrayList<>();
    private final List<JusticeCase> justiceCases=new ArrayList<>();
    private final List<HiddenCache> hiddenCaches=new ArrayList<>();
    private final List<PirateBand> pirateBands=new ArrayList<>();
    private final List<PirateHideout> pirateHideouts=new ArrayList<>();
    private final List<DiplomaticMarriage> diplomaticMarriages=new ArrayList<>();
    private final List<CivicEvent> civicEvents=new ArrayList<>();
    private final List<IntelligenceOperation> intelligenceOperations=new ArrayList<>();
    private final List<PropagandaCampaign> propagandaCampaigns=new ArrayList<>();
    private final List<RuinSite> ruinSites=new ArrayList<>();
    private final List<AssistanceTask> assistanceTasks=new ArrayList<>();
    private final Map<Long,DynastyState> dynasties=new LinkedHashMap<>();
    private final Map<String,PlayerStanding> playerStandings=new LinkedHashMap<>();
    private final CrimeLedger crimeLedger=new CrimeLedger();
    private final WorldHistory history=new WorldHistory(20_000);
    private long nextId=1;

    private EcologyEngine ecology;
    private final FactionEngine factionEngine=new FactionEngine();
    private final TradeEngine tradeEngine=new TradeEngine();
    private final SocietyEngine societyEngine=new SocietyEngine();
    private final GovernmentEngine governmentEngine=new GovernmentEngine();
    private final RebellionEngine rebellionEngine=new RebellionEngine();
    private final DiplomacyEngine diplomacyEngine=new DiplomacyEngine();
    private final TransportNetworkEngine transportEngine=new TransportNetworkEngine();
    private final MilitaryCommandEngine militaryCommandEngine=new MilitaryCommandEngine();
    private final AviationEngine aviationEngine=new AviationEngine();
    private final CrimeEngine crimeEngine=new CrimeEngine();
    private final BountyOfficeEngine bountyOfficeEngine=new BountyOfficeEngine();
    private final LawEnforcementEngine lawEnforcementEngine=new LawEnforcementEngine();
    private final NavalEngine navalEngine=new NavalEngine();
    private final ReputationEngine reputationEngine=new ReputationEngine();
    private final IndustryEngine industryEngine=new IndustryEngine();
    private final PrimaryEconomyEngine primaryEconomyEngine=new PrimaryEconomyEngine();
    private final SocialPopulationEngine socialPopulationEngine=new SocialPopulationEngine();
    private final CivilizationEngine civilizationEngine=new CivilizationEngine();

    public SimulationState(long seed){this(seed,SpeciesCatalog.starter(),SimulationConfig.defaults());}
    public SimulationState(long seed,Map<String,SpeciesDefinition> initialSpecies){this(seed,initialSpecies,SimulationConfig.defaults());}
    public SimulationState(long seed,Map<String,SpeciesDefinition> initialSpecies,SimulationConfig config){
        this.seed=seed;this.config=Objects.requireNonNull(config,"config");Objects.requireNonNull(initialSpecies,"initialSpecies");
        SpeciesCatalogValidator.Report report=SpeciesCatalogValidator.validate(initialSpecies);report.throwIfInvalid();
        species=Map.copyOf(initialSpecies);biomes=BiomeCatalog.defaults();ecology=new EcologyEngine(species);
    }

    public long seed(){return seed;} public SimClock clock(){return clock;} public SimulationConfig config(){return config;} public void setConfig(SimulationConfig config){this.config=Objects.requireNonNull(config,"config");} public Map<String,SpeciesDefinition> species(){return species;} public Map<String,EcoBiome> biomes(){return biomes;}
    public List<EcosystemRegion> regions(){return Collections.unmodifiableList(regions);} public List<Faction> factions(){return Collections.unmodifiableList(factions);} public List<TradeShipment> shipments(){return Collections.unmodifiableList(shipments);}
    public List<Treaty> treaties(){return Collections.unmodifiableList(treaties);} public List<WarState> wars(){return Collections.unmodifiableList(wars);} public List<TransportRoute> routes(){return Collections.unmodifiableList(routes);} public List<MilitaryObjective> objectives(){return Collections.unmodifiableList(objectives);} public List<SiegeState> sieges(){return Collections.unmodifiableList(sieges);} public List<AirWing> airWings(){return Collections.unmodifiableList(airWings);} public List<BountyContract> bounties(){return Collections.unmodifiableList(bounties);} public List<CustodyRecord> custody(){return Collections.unmodifiableList(custody);} public List<PortState> ports(){return Collections.unmodifiableList(ports);} public List<Fleet> fleets(){return Collections.unmodifiableList(fleets);} public List<IndustrialSite> industrialSites(){return Collections.unmodifiableList(industrialSites);} public List<SocialCitizen> socialCitizens(){return Collections.unmodifiableList(socialCitizens);} public Map<Long,SettlementCivilizationState> settlementCivilizations(){return Collections.unmodifiableMap(settlementCivilizations);} public Map<Long,FactionCivilizationState> factionCivilizations(){return Collections.unmodifiableMap(factionCivilizations);} public List<ResourceClaim> resourceClaims(){return Collections.unmodifiableList(resourceClaims);} public List<RaidParty> raids(){return Collections.unmodifiableList(raids);} public List<LegendRecord> legends(){return Collections.unmodifiableList(legends);} public List<HouseholdState> households(){return Collections.unmodifiableList(households);} public List<EpidemicRecord> epidemics(){return Collections.unmodifiableList(epidemics);} public List<MigrationGroup> migrationGroups(){return Collections.unmodifiableList(migrationGroups);} public List<JusticeCase> justiceCases(){return Collections.unmodifiableList(justiceCases);} public List<HiddenCache> hiddenCaches(){return Collections.unmodifiableList(hiddenCaches);} public List<PirateBand> pirateBands(){return Collections.unmodifiableList(pirateBands);} public List<PirateHideout> pirateHideouts(){return Collections.unmodifiableList(pirateHideouts);} public List<DiplomaticMarriage> diplomaticMarriages(){return Collections.unmodifiableList(diplomaticMarriages);} public List<CivicEvent> civicEvents(){return Collections.unmodifiableList(civicEvents);} public List<IntelligenceOperation> intelligenceOperations(){return Collections.unmodifiableList(intelligenceOperations);} public List<PropagandaCampaign> propagandaCampaigns(){return Collections.unmodifiableList(propagandaCampaigns);} public List<RuinSite> ruinSites(){return Collections.unmodifiableList(ruinSites);} public List<AssistanceTask> assistanceTasks(){return Collections.unmodifiableList(assistanceTasks);} public Map<Long,DynastyState> dynasties(){return Collections.unmodifiableMap(dynasties);} public Map<String,PlayerStanding> playerStandings(){return Collections.unmodifiableMap(playerStandings);} public CrimeLedger crimeLedger(){return crimeLedger;} public WorldHistory history(){return history;}
    public long nextId(){return nextId++;} public long peekNextId(){return nextId;} public void restoreNextId(long v){nextId=Math.max(1,v);}

    private void observeCanonicalId(long id){
        if(id<=0)throw new IllegalArgumentException("canonical id");
        if(id==Long.MAX_VALUE)throw new IllegalArgumentException("canonical id exhausted");
        if(id>=nextId)nextId=id+1;
    }
    public void repairNextIdWatermark(){
        long max=0;
        for(EcosystemRegion r:regions){max=Math.max(max,r.id());for(PopulationGroup p:r.populations())max=Math.max(max,p.id());}
        for(Faction f:factions){max=Math.max(max,f.id());for(Settlement st:f.settlements())max=Math.max(max,st.id());for(Army a:f.armies())max=Math.max(max,a.id());}
        for(TradeShipment v:shipments)max=Math.max(max,v.id());for(Treaty v:treaties)max=Math.max(max,v.id());for(WarState v:wars)max=Math.max(max,v.id());for(TransportRoute v:routes)max=Math.max(max,v.id());for(MilitaryObjective v:objectives)max=Math.max(max,v.id());for(SiegeState v:sieges)max=Math.max(max,v.id());for(AirWing v:airWings)max=Math.max(max,v.id());for(BountyContract v:bounties)max=Math.max(max,v.id());for(CustodyRecord v:custody)max=Math.max(max,v.id());for(PortState v:ports)max=Math.max(max,v.id());for(Fleet v:fleets)max=Math.max(max,v.id());for(IndustrialSite v:industrialSites)max=Math.max(max,v.id());for(SocialCitizen v:socialCitizens)max=Math.max(max,v.id());for(ResourceClaim v:resourceClaims)max=Math.max(max,v.id());for(RaidParty v:raids)max=Math.max(max,v.id());for(LegendRecord v:legends)max=Math.max(max,v.id());for(HouseholdState v:households){max=Math.max(max,v.id());for(DependentChild child:v.children())max=Math.max(max,child.id());}for(EpidemicRecord v:epidemics)max=Math.max(max,v.id());for(MigrationGroup v:migrationGroups)max=Math.max(max,v.id());for(JusticeCase v:justiceCases)max=Math.max(max,v.id());for(HiddenCache v:hiddenCaches)max=Math.max(max,v.id());for(PirateBand v:pirateBands)max=Math.max(max,v.id());for(PirateHideout v:pirateHideouts)max=Math.max(max,v.id());for(DiplomaticMarriage v:diplomaticMarriages)max=Math.max(max,v.id());for(CivicEvent v:civicEvents)max=Math.max(max,v.id());for(IntelligenceOperation v:intelligenceOperations)max=Math.max(max,v.id());for(PropagandaCampaign v:propagandaCampaigns)max=Math.max(max,v.id());for(RuinSite v:ruinSites)max=Math.max(max,v.id());for(AssistanceTask v:assistanceTasks)max=Math.max(max,v.id());for(CrimeIncident v:crimeLedger.incidents())max=Math.max(max,v.id());
        if(max==Long.MAX_VALUE)throw new IllegalStateException("canonical id space exhausted");
        if(nextId<=max)nextId=max+1;
    }

    public void addRegion(EcosystemRegion r){EcosystemRegion v=Objects.requireNonNull(r);observeCanonicalId(v.id());for(PopulationGroup p:v.populations())observeCanonicalId(p.id());regions.add(v);} public void addFaction(Faction f){Faction v=Objects.requireNonNull(f);observeCanonicalId(v.id());for(Settlement st:v.settlements())observeCanonicalId(st.id());for(Army a:v.armies())observeCanonicalId(a.id());factions.add(v);} public void addShipment(TradeShipment s){TradeShipment v=Objects.requireNonNull(s);observeCanonicalId(v.id());shipments.add(v);}
    public void addTreaty(Treaty v){Treaty value=Objects.requireNonNull(v);observeCanonicalId(value.id());treaties.add(value);} public void addWar(WarState v){WarState value=Objects.requireNonNull(v);observeCanonicalId(value.id());wars.add(value);} public void addRoute(TransportRoute v){TransportRoute value=Objects.requireNonNull(v);observeCanonicalId(value.id());routes.add(value);} public void addObjective(MilitaryObjective v){MilitaryObjective value=Objects.requireNonNull(v);observeCanonicalId(value.id());objectives.add(value);} public void addSiege(SiegeState v){SiegeState value=Objects.requireNonNull(v);observeCanonicalId(value.id());sieges.add(value);} public void addAirWing(AirWing v){AirWing value=Objects.requireNonNull(v);observeCanonicalId(value.id());airWings.add(value);} public void addBounty(BountyContract v){BountyContract value=Objects.requireNonNull(v);observeCanonicalId(value.id());bounties.add(value);} public void addCustody(CustodyRecord v){CustodyRecord value=Objects.requireNonNull(v);observeCanonicalId(value.id());custody.add(value);} public void addPort(PortState v){PortState value=Objects.requireNonNull(v);observeCanonicalId(value.id());ports.add(value);} public void addFleet(Fleet v){Fleet value=Objects.requireNonNull(v);observeCanonicalId(value.id());fleets.add(value);} public void addIndustrialSite(IndustrialSite v){IndustrialSite value=Objects.requireNonNull(v);observeCanonicalId(value.id());industrialSites.add(value);} public void addSocialCitizen(SocialCitizen v){SocialCitizen value=Objects.requireNonNull(v);observeCanonicalId(value.id());socialCitizens.add(value);}
    public void restoreSettlementCivilization(SettlementCivilizationState value){Objects.requireNonNull(value);settlementCivilizations.put(value.settlementId(),value);} public void restoreFactionCivilization(FactionCivilizationState value){Objects.requireNonNull(value);factionCivilizations.put(value.factionId(),value);}
    public SettlementCivilizationState ensureSettlementCivilization(long settlementId,long ownerFactionId){return settlementCivilizations.computeIfAbsent(settlementId,k->new SettlementCivilizationState(settlementId,ownerFactionId));}
    public Optional<SettlementCivilizationState> findSettlementCivilization(long settlementId){return Optional.ofNullable(settlementCivilizations.get(settlementId));}
    public FactionCivilizationState ensureFactionCivilization(long factionId){Faction faction=findFaction(factionId).orElseThrow(()->new IllegalArgumentException("unknown faction"));return factionCivilizations.computeIfAbsent(factionId,k->new FactionCivilizationState(factionId,CivilizationNaming.culture(factionId,faction.name()),CivilizationNaming.faith(factionId),CivilizationNaming.dialect(factionId)));}
    public Optional<FactionCivilizationState> findFactionCivilization(long factionId){return Optional.ofNullable(factionCivilizations.get(factionId));}
    public void addResourceClaim(ResourceClaim value){Objects.requireNonNull(value);if(resourceClaims.size()>=MAX_RESOURCE_CLAIMS)throw new IllegalStateException("resource claim limit");observeCanonicalId(value.id());resourceClaims.add(value);} public void addRaid(RaidParty value){Objects.requireNonNull(value);if(raids.size()>=MAX_RAIDS)pruneRaids();if(raids.size()>=MAX_RAIDS)throw new IllegalStateException("raid limit");observeCanonicalId(value.id());raids.add(value);} public void addLegend(LegendRecord value){Objects.requireNonNull(value);if(legends.size()>=MAX_LEGENDS)return;observeCanonicalId(value.id());legends.add(value);} public void pruneRaids(){raids.removeIf(r->!r.active());}
    public void addHousehold(HouseholdState value){Objects.requireNonNull(value);if(households.size()>=MAX_HOUSEHOLDS)throw new IllegalStateException("household limit");observeCanonicalId(value.id());households.add(value);} public Optional<HouseholdState> findHousehold(long id){return households.stream().filter(h->h.id()==id).findFirst();} public Optional<DependentChild> findDependentChild(long id){for(HouseholdState household:households){Optional<DependentChild> child=household.findChild(id);if(child.isPresent())return child;}return Optional.empty();}
    public void addEpidemic(EpidemicRecord value){Objects.requireNonNull(value);if(epidemics.size()>=MAX_EPIDEMICS)epidemics.removeIf(e->!e.active());if(epidemics.size()>=MAX_EPIDEMICS)throw new IllegalStateException("epidemic limit");observeCanonicalId(value.id());epidemics.add(value);}
    public void addMigrationGroup(MigrationGroup value){Objects.requireNonNull(value);if(migrationGroups.size()>=MAX_MIGRATIONS)migrationGroups.removeIf(g->!g.active());if(migrationGroups.size()>=MAX_MIGRATIONS)throw new IllegalStateException("migration limit");observeCanonicalId(value.id());migrationGroups.add(value);}
    public void addJusticeCase(JusticeCase value){Objects.requireNonNull(value);if(justiceCases.size()>=MAX_JUSTICE_CASES)justiceCases.removeIf(c->!c.active());if(justiceCases.size()>=MAX_JUSTICE_CASES)throw new IllegalStateException("justice case limit");observeCanonicalId(value.id());justiceCases.add(value);}
    public void addHiddenCache(HiddenCache value){Objects.requireNonNull(value);if(hiddenCaches.size()>=MAX_HIDDEN_CACHES)hiddenCaches.removeIf(HiddenCache::recovered);if(hiddenCaches.size()>=MAX_HIDDEN_CACHES)return;observeCanonicalId(value.id());hiddenCaches.add(value);}
    public void addPirateBand(PirateBand value){Objects.requireNonNull(value);if(pirateBands.size()>=MAX_PIRATE_BANDS)pirateBands.removeIf(p->!p.active());if(pirateBands.size()>=MAX_PIRATE_BANDS)return;observeCanonicalId(value.id());pirateBands.add(value);}
    public void addPirateHideout(PirateHideout value){Objects.requireNonNull(value);if(pirateHideouts.size()>=MAX_PIRATE_HIDEOUTS)pirateHideouts.removeIf(h->!h.active());if(pirateHideouts.size()>=MAX_PIRATE_HIDEOUTS)return;observeCanonicalId(value.id());pirateHideouts.add(value);}
    public void addDiplomaticMarriage(DiplomaticMarriage value){Objects.requireNonNull(value);if(diplomaticMarriages.size()>=MAX_DIPLOMATIC_MARRIAGES)diplomaticMarriages.removeIf(m->!m.active());if(diplomaticMarriages.size()>=MAX_DIPLOMATIC_MARRIAGES)return;observeCanonicalId(value.id());diplomaticMarriages.add(value);}
    public void addCivicEvent(CivicEvent value){Objects.requireNonNull(value);if(civicEvents.size()>=MAX_CIVIC_EVENTS)civicEvents.removeIf(e->!e.active());if(civicEvents.size()>=MAX_CIVIC_EVENTS)return;observeCanonicalId(value.id());civicEvents.add(value);}
    public void addIntelligenceOperation(IntelligenceOperation value){Objects.requireNonNull(value);if(intelligenceOperations.size()>=MAX_INTELLIGENCE_OPERATIONS)intelligenceOperations.removeIf(o->!o.active());if(intelligenceOperations.size()>=MAX_INTELLIGENCE_OPERATIONS)return;observeCanonicalId(value.id());intelligenceOperations.add(value);}
    public void addPropagandaCampaign(PropagandaCampaign value){Objects.requireNonNull(value);if(propagandaCampaigns.size()>=MAX_PROPAGANDA_CAMPAIGNS)propagandaCampaigns.removeIf(c->!c.active());if(propagandaCampaigns.size()>=MAX_PROPAGANDA_CAMPAIGNS)return;observeCanonicalId(value.id());propagandaCampaigns.add(value);}
    public void addRuinSite(RuinSite value){Objects.requireNonNull(value);if(ruinSites.size()>=MAX_RUIN_SITES)ruinSites.removeIf(r->r.reclaimed());if(ruinSites.size()>=MAX_RUIN_SITES)ruinSites.remove(0);observeCanonicalId(value.id());ruinSites.add(value);}
    public void addAssistanceTask(AssistanceTask value){Objects.requireNonNull(value);if(assistanceTasks.size()>=MAX_ASSISTANCE_TASKS)assistanceTasks.removeIf(t->!t.active());if(assistanceTasks.size()>=MAX_ASSISTANCE_TASKS)assistanceTasks.remove(0);observeCanonicalId(value.id());assistanceTasks.add(value);}
    public Optional<AssistanceTask> activeAssistanceTask(long settlementId,AssistanceTaskType type){return assistanceTasks.stream().filter(AssistanceTask::active).filter(t->t.settlementId()==settlementId&&t.type()==type).findFirst();}

    public DynastyState ensureDynasty(long factionId,long foundedDay,String houseName){return dynasties.computeIfAbsent(factionId,k->new DynastyState(factionId,foundedDay,houseName));} public void restoreDynasty(DynastyState value){Objects.requireNonNull(value);dynasties.put(value.factionId(),value);}
    public IndustrialSite removeIndustrialSite(long id){for(var it=industrialSites.iterator();it.hasNext();){IndustrialSite site=it.next();if(site.id()==id){it.remove();return site;}}return null;}
    public Optional<IndustrialSite> findIndustrialSite(long id){return industrialSites.stream().filter(s->s.id()==id).findFirst();}
    public Optional<SocialCitizen> findSocialCitizen(long id){return socialCitizens.stream().filter(c->c.id()==id).findFirst();}
    public SocialCitizen ensureSocialCitizen(long factionId,long settlementId,int slot,dev.livingrealms.sim.civilian.CitizenRole role){return socialPopulationEngine.ensureProjectionCitizen(this,factionId,settlementId,slot,role);}
    public TradeShipment removeShipment(long id){for(var it=shipments.iterator();it.hasNext();){TradeShipment s=it.next();if(s.id()==id){it.remove();return s;}}return null;}
    public void removeDestroyedAirWings(){airWings.removeIf(AirWing::destroyed);}
    public void removeDestroyedFleets(){fleets.removeIf(Fleet::destroyed);}

    public Optional<Faction> findFaction(long id){return factions.stream().filter(f->f.id()==id).findFirst();}
    public Optional<Settlement> findSettlement(long id){for(Faction f:factions)for(Settlement s:f.settlements())if(s.id()==id)return Optional.of(s);return Optional.empty();}
    public Optional<Faction> findSettlementOwner(long settlementId){return factions.stream().filter(f->f.settlements().stream().anyMatch(s->s.id()==settlementId)).findFirst();}
    public Optional<Army> findArmy(long id){for(Faction f:factions)for(Army a:f.armies())if(a.id()==id)return Optional.of(a);return Optional.empty();}
    public Optional<TradeShipment> findShipment(long id){return shipments.stream().filter(s->s.id()==id).findFirst();}
    public Optional<WarState> activeWar(long a,long b){return wars.stream().filter(WarState::active).filter(w->w.between(a,b)).findFirst();}
    public Optional<PortState> findPort(long id){return ports.stream().filter(p->p.id()==id).findFirst();}
    public Optional<Fleet> findFleet(long id){return fleets.stream().filter(f->f.id()==id).findFirst();}
    public Optional<MigrationGroup> findMigrationGroup(long id){return migrationGroups.stream().filter(g->g.id()==id).findFirst();}
    public Optional<PirateBand> findPirateBand(long id){return pirateBands.stream().filter(p->p.id()==id).findFirst();} public Optional<PirateHideout> findPirateHideoutByBand(long bandId){return pirateHideouts.stream().filter(PirateHideout::active).filter(h->h.bandId()==bandId).findFirst();}
    public PlayerStanding playerStanding(String actorKey){if(actorKey==null||actorKey.isBlank())throw new IllegalArgumentException("actorKey");return playerStandings.computeIfAbsent(actorKey,PlayerStanding::new);}
    public Optional<PlayerStanding> findPlayerStanding(String actorKey){return Optional.ofNullable(playerStandings.get(actorKey));} public Optional<PlayerStanding> playerRuler(long factionId){return playerStandings.values().stream().filter(p->p.isRulerOf(factionId)).findFirst();}
    public void restorePlayerStanding(PlayerStanding standing){PlayerStanding v=Objects.requireNonNull(standing);playerStandings.put(v.actorKey(),v);}

    public boolean recordPhysicalShipmentLoss(long id,String cause){TradeShipment shipment=removeShipment(id);if(shipment==null)return false;history.add(new WorldEvent(clock.day(),"trade_caravan_lost","shipment="+id+", resource="+shipment.resource()+", amount="+shipment.amount()+", cause="+Objects.requireNonNullElse(cause,"unknown")));return true;}
    public Optional<PopulationGroup> findPopulationGroup(long groupId){for(EcosystemRegion region:regions)for(PopulationGroup group:region.populations())if(group.id()==groupId)return Optional.of(group);return Optional.empty();}
    public boolean recordPhysicalAnimalDeath(long groupId,double count,String cause){if(count<=0||!Double.isFinite(count))throw new IllegalArgumentException("count");PopulationGroup group=findPopulationGroup(groupId).orElse(null);if(group==null||group.extinct())return false;double before=group.population();group.addPopulation(-Math.min(count,before));history.add(new WorldEvent(clock.day(),"wildlife_death","group="+groupId+", species="+group.speciesId()+", count="+count+", cause="+Objects.requireNonNullElse(cause,"unknown")));return true;}
    public boolean recordIndustrialDamage(long siteId,double damage,int downtime,String cause){IndustrialSite site=findIndustrialSite(siteId).orElse(null);if(site==null)return false;site.damage(damage,downtime);history.add(new WorldEvent(clock.day(),"industry_damaged","site="+siteId+", kind="+site.kind()+", damage="+damage+", cause="+Objects.requireNonNullElse(cause,"unknown")));return true;}
    public boolean recordPhysicalCitizenDeath(long settlementId,String cause){return recordPhysicalCitizenDeath(settlementId,0,cause);}
    public boolean recordPhysicalCitizenDeath(long settlementId,long socialCitizenId,String cause){Settlement settlement=findSettlement(settlementId).orElse(null);if(settlement==null||settlement.population()<=0)return false;SocialCitizen citizen=socialCitizenId>0?findSocialCitizen(socialCitizenId).orElse(null):null;if(citizen!=null&&!citizen.alive())return false;if(citizen!=null)citizen.markDead();settlement.addPopulation(-1);settlement.adjustUnrest(.0025);settlement.setPublicOrder(settlement.publicOrder()-.001);history.add(new WorldEvent(clock.day(),"citizen_death","settlement="+settlementId+(citizen==null?"":", citizen="+citizen.id()+", name="+citizen.name())+", cause="+Objects.requireNonNullElse(cause,"unknown")));return true;}
    public boolean recordPhysicalArmyLoss(long armyId,int representedPersonnel,String cause){Army army=findArmy(armyId).orElse(null);if(army==null||army.destroyed())return false;army.recordRepresentativeLoss(representedPersonnel);history.add(new WorldEvent(clock.day(),"military_loss","army="+armyId+", represented="+representedPersonnel+", cause="+Objects.requireNonNullElse(cause,"unknown")));return true;}
    public boolean recordPhysicalAircraftLoss(long wingId,int count,String cause){AirWing wing=airWings.stream().filter(w->w.id()==wingId).findFirst().orElse(null);if(wing==null||wing.destroyed())return false;wing.loseAircraft(count);history.add(new WorldEvent(clock.day(),"aircraft_loss","wing="+wingId+", count="+count+", cause="+Objects.requireNonNullElse(cause,"unknown")));removeDestroyedAirWings();return true;}
    public boolean recordPhysicalShipLoss(long fleetId,ShipClass shipClass,int count,String cause){if(count<=0)throw new IllegalArgumentException("count");Fleet fleet=findFleet(fleetId).orElse(null);if(fleet==null||fleet.destroyed()||fleet.count(shipClass)<=0)return false;int before=fleet.count(shipClass);fleet.loseShips(shipClass,count);int lost=before-fleet.count(shipClass);history.add(new WorldEvent(clock.day(),"ship_loss","fleet="+fleetId+", class="+shipClass+", count="+lost+", cause="+Objects.requireNonNullElse(cause,"unknown")));removeDestroyedFleets();return lost>0;}
    public boolean recordPhysicalMigrationLoss(long groupId,int representedPeople,String cause){if(representedPeople<=0)throw new IllegalArgumentException("representedPeople");MigrationGroup group=findMigrationGroup(groupId).orElse(null);if(group==null||!group.active()||group.people()<=0)return false;int before=group.people();group.losePeople(Math.min(representedPeople,before));int lost=before-group.people();history.add(new WorldEvent(clock.day(),"migration_group_loss","group="+groupId+", people="+lost+", cause="+Objects.requireNonNullElse(cause,"unknown")));return lost>0;}
    public boolean recordPhysicalPirateLoss(long bandId,int representedPirates,String cause){if(representedPirates<=0)throw new IllegalArgumentException("representedPirates");PirateBand band=findPirateBand(bandId).orElse(null);if(band==null||!band.active())return false;int before=band.strength();band.loseStrength(Math.min(representedPirates,before));int lost=before-band.strength();history.add(new WorldEvent(clock.day(),"pirate_loss","band="+bandId+", pirates="+lost+", cause="+Objects.requireNonNullElse(cause,"unknown")));return lost>0;}

    public CrimeResult reportCrime(String actorKey,long jurisdictionFactionId,CrimeType type,double value,SimPosition position,boolean witnessed,int witnessCount,String victimKey,String evidence){CrimeIncident incident=new CrimeIncident(nextId(),clock.day(),actorKey,jurisdictionFactionId,type,value,position,witnessed,witnessCount,victimKey,evidence);CrimeResult result=crimeEngine.report(this,incident);reputationEngine.onCrime(this,incident,result);return result;}
    public double captureCriminal(String actorKey,long factionId){return crimeEngine.capture(this,actorKey,factionId);} public double payFine(String actorKey,long factionId,double amount){return crimeEngine.payFine(this,actorKey,factionId,amount);}
    public LawResponse lawResponse(String actorKey,long factionId){return lawEnforcementEngine.response(this,actorKey,factionId);}
    public ArrestOutcome arrestCriminal(String actorKey,long factionId,String reason){return lawEnforcementEngine.arrest(this,actorKey,factionId,reason);}
    public BountyClaim claimBounty(long contractId,String hunterKey){return bountyOfficeEngine.claim(this,contractId,hunterKey);}
    public BountyAssignment acceptBounty(long contractId,String hunterKey){return bountyOfficeEngine.accept(this,contractId,hunterKey);}
    public BountyAssignment abandonBounty(long contractId,String hunterKey){return bountyOfficeEngine.abandon(this,contractId,hunterKey);}
    public BountyClaim captureBountyAlive(long contractId,String hunterKey,String reason){
        BountyContract contract=bounties.stream().filter(b->b.id()==contractId).findFirst().orElse(null);
        if(contract==null)return new BountyClaim(false,contractId,"",hunterKey,0,0,0,"unknown_contract");
        LawResponse response=lawResponse(contract.actorKey(),contract.issuerFactionId());
        if(response.action()!=EnforcementAction.ARREST&&response.action()!=EnforcementAction.LETHAL_FORCE)return new BountyClaim(false,contract.id(),contract.actorKey(),hunterKey,contract.issuerFactionId(),contract.reward(),0,"no_capture_warrant");
        double bountyBefore=crimeLedger.findProfile(contract.actorKey()).flatMap(p->p.find(contract.issuerFactionId())).map(JurisdictionWanted::bounty).orElse(0.0);
        BountyClaim claim=bountyOfficeEngine.claim(this,contractId,hunterKey);
        if(!claim.claimed())return claim;
        int sentence=Math.max(1,response.sentenceDays());
        CustodyRecord record=new CustodyRecord(nextId(),contract.actorKey(),contract.issuerFactionId(),clock.day(),clock.day()+sentence,bountyBefore,reason);
        addCustody(record);
        history.add(new WorldEvent(clock.day(),"bounty_target_captured_alive","contract="+contractId+", actor="+contract.actorKey()+", hunter="+hunterKey+", days="+sentence));
        return claim;
    }
    public Optional<CustodyRecord> activeCustody(String actorKey,long factionId){return custody.stream().filter(CustodyRecord::active).filter(c->c.actorKey().equals(actorKey)&&c.factionId()==factionId).findFirst();}
    public FactionJoinResult joinFaction(String actorKey,long factionId){return reputationEngine.join(this,actorKey,factionId);}
    public boolean leaveFaction(String actorKey){return reputationEngine.leave(this,actorKey);}
    public void grantFactionService(String actorKey,long factionId,double points){reputationEngine.grantService(this,actorKey,factionId,points);}
    public boolean pardonCriminal(String actorKey,long factionId){WantedProfile profile=crimeLedger.findProfile(actorKey).orElse(null);if(profile==null)return false;JurisdictionWanted wanted=profile.find(factionId).orElse(null);if(wanted==null)return false;wanted.pardon();history.add(new WorldEvent(clock.day(),"criminal_pardoned","actor="+actorKey+", faction="+factionId));for(BountyContract b:bounties)if(b.actorKey().equals(actorKey)&&b.issuerFactionId()==factionId&&b.status()!=BountyContract.Status.CLAIMED)b.cancel();return true;}

    public void replaceSpeciesCatalog(Map<String,SpeciesDefinition> replacement){
        Objects.requireNonNull(replacement,"replacement");
        SpeciesCatalogValidator.Report report=SpeciesCatalogValidator.validate(replacement);report.throwIfInvalid();
        for(EcosystemRegion region:regions)for(PopulationGroup group:region.populations())if(!replacement.containsKey(group.speciesId()))throw new IllegalArgumentException("Catalog removed live species: "+group.speciesId());
        Map<String,SpeciesDefinition> copy=Map.copyOf(replacement);if(copy.equals(species))return;
        species=copy;ecology=new EcologyEngine(species);
        int colonized=0;for(EcosystemRegion region:regions)colonized+=HabitatPopulationSeeder.seedMissingSpecies(this,region,256);
        if(colonized>0)history.add(new WorldEvent(clock.day(),"species_catalog_colonized","new population groups="+colonized+", catalog species="+species.size()));
    }

    public EcosystemRegion ensureEcosystemRegion(String biomeId,SimPosition center,double areaKm2,double mergeRadius){
        if(biomeId==null||biomeId.isBlank()||center==null||areaKm2<=0||mergeRadius<0)throw new IllegalArgumentException("ecosystem region parameters");
        EcoBiome biome=biomes.get(biomeId);if(biome==null)throw new IllegalArgumentException("Unknown biome archetype: "+biomeId);
        for(EcosystemRegion region:regions)if(region.biome().id().equals(biomeId)&&region.center().distanceTo(center)<=mergeRadius)return region;
        EcosystemRegion created=new EcosystemRegion(nextId(),biome,areaKm2,center);regions.add(created);
        int seeded=HabitatPopulationSeeder.seedMissingSpecies(this,created,256);
        history.add(new WorldEvent(clock.day(),"ecosystem_region_discovered","biome="+biomeId+", center="+Math.round(center.x())+","+Math.round(center.z())+", species="+seeded));
        return created;
    }

    public static final long MAX_MANUAL_DAY_JUMP = 10_000L;

    /**
     * Advances canonical simulation time to an absolute day without rewinding history.
     * Intended for operator/debug progression commands. The jump is capped so a typo cannot
     * synchronously simulate millions of days on the integrated-server thread.
     */
    public long advanceToDay(long targetDay){
        long current=clock.day();
        if(targetDay<current)throw new IllegalArgumentException("target day cannot be before current day " + current);
        long delta=targetDay-current;
        if(delta>MAX_MANUAL_DAY_JUMP)throw new IllegalArgumentException("maximum manual jump is " + MAX_MANUAL_DAY_JUMP + " days");
        long remaining=delta;
        while(remaining>0){int step=(int)Math.min(remaining,365L);advanceDays(step);remaining-=step;}
        return delta;
    }

    public void advanceDays(int days){
        if(days<0)throw new IllegalArgumentException("days");
        for(int d=0;d<days;d++){
            long day=clock.day();
            for(EcosystemRegion r:regions)ecology.simulate(r,1,new DeterministicRng(seed^(day*0x9E3779B97F4A7C15L)^r.id()));
            factionEngine.simulateDay(this,new DeterministicRng(seed^day^0xC0FFEE1234L));
            primaryEconomyEngine.simulateDay(this);
            industryEngine.simulateDay(this,new DeterministicRng(seed^day^0x243F6A8885A308D3L));
            for(Faction faction:new ArrayList<>(factions))societyEngine.simulateDay(faction);
            socialPopulationEngine.simulateDay(this);
            civilizationEngine.simulateDay(this,new DeterministicRng(seed^day^0xD1B54A32D192ED03L));
            governmentEngine.simulateDay(this,new DeterministicRng(seed^day^0x6A09E667F3BCC909L));
            militaryCommandEngine.simulateDay(this);
            diplomacyEngine.simulateDay(this,new DeterministicRng(seed^day^0xBB67AE8584CAA73BL));
            tradeEngine.simulateDay(this,new DeterministicRng(seed^day^0x7A4DE5B19L));
            transportEngine.simulateDay(this);
            aviationEngine.simulateDay(this,new DeterministicRng(seed^day^0x3C6EF372FE94F82BL));
            navalEngine.simulateDay(this,new DeterministicRng(seed^day^0x510E527FADE682D1L));
            crimeEngine.simulateDay(this,config.crimeHeatDecayPerDay(),config.reputationDecayPerDay());
            bountyOfficeEngine.simulateDay(this);
            lawEnforcementEngine.releaseExpired(this);
            reputationEngine.simulateDay(this);
            rebellionEngine.simulateDay(this,new DeterministicRng(seed^day^0xA54FF53A5F1D36F1L),config.rebellionThreshold());
            clock.advance(SimClock.TICKS_PER_DAY);
            if(day%30==0)history.add(new WorldEvent(day,"monthly_snapshot",summary()));
        }
    }

    public String summary(){long animalGroups=regions.stream().mapToLong(r->r.populations().size()).sum();double animals=regions.stream().flatMap(r->r.populations().stream()).mapToDouble(PopulationGroup::population).sum();int people=factions.stream().mapToInt(Faction::population).sum();long activeWars=wars.stream().filter(WarState::active).count();long activeRaids=raids.stream().filter(RaidParty::active).count();long openBounties=bounties.stream().filter(b->b.status()==BountyContract.Status.OPEN||b.status()==BountyContract.Status.ASSIGNED).count();return "day="+clock.day()+", factions="+factions.size()+", people="+people+", industry="+industrialSites.size()+", animalGroups="+animalGroups+", animals≈"+(long)animals+", wars="+activeWars+", raids="+activeRaids+", shipments="+shipments.size()+", fleets="+fleets.size()+", members="+playerStandings.values().stream().filter(PlayerStanding::isMember).count()+", bounties="+openBounties;}
}
