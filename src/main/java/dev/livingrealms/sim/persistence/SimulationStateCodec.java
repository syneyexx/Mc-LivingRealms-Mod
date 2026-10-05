package dev.livingrealms.sim.persistence;

import dev.livingrealms.sim.aviation.*;
import dev.livingrealms.sim.config.SimulationConfig;
import dev.livingrealms.sim.civilization.*;
import dev.livingrealms.sim.civilian.AppearanceProfile;
import dev.livingrealms.sim.civilian.CitizenRole;
import dev.livingrealms.sim.construction.ConstructionPhase;
import dev.livingrealms.sim.diplomacy.*;
import dev.livingrealms.sim.ecology.*;
import dev.livingrealms.sim.faction.*;
import dev.livingrealms.sim.government.*;
import dev.livingrealms.sim.industry.*;
import dev.livingrealms.sim.law.*;
import dev.livingrealms.sim.logistics.*;
import dev.livingrealms.sim.military.*;
import dev.livingrealms.sim.naval.*;
import dev.livingrealms.sim.player.*;
import dev.livingrealms.sim.social.*;
import dev.livingrealms.sim.transport.*;
import dev.livingrealms.sim.construction.RegisteredPlayerStructure;
import dev.livingrealms.sim.underworld.StolenGoodsEntry;
import dev.livingrealms.sim.underworld.StolenGoodsLedger;
import dev.livingrealms.sim.underworld.UnderworldContract;
import dev.livingrealms.sim.underworld.UnderworldContractType;
import dev.livingrealms.sim.underworld.UnderworldProfile;
import dev.livingrealms.sim.validation.SimulationValidator;
import dev.livingrealms.sim.world.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.CRC32;

/** Versioned binary codec for canonical Living Realms state. No Minecraft classes required. */
public final class SimulationStateCodec {
    private static final int MAGIC = 0x4C52534D; // LRSM
    public static final int MIN_SUPPORTED_SCHEMA = 1;
    public static final int SCHEMA_VERSION = 20;
    /** Hard ceiling for one canonical world-state payload. Prevents corrupt/local saves from driving unbounded decode work. */
    public static final int MAX_STATE_BYTES = 32 * 1024 * 1024;
    /** Individual canonical text fields are metadata, identifiers or bounded event text; 64 KiB is intentionally generous. */
    public static final int MAX_STRING_BYTES = 64 * 1024;
    private SimulationStateCodec() {}

    /** Returns the embedded schema after validating the binary header. */
    public static int inspectSchema(byte[] data) {
        Objects.requireNonNull(data,"data");
        if(data.length<8)throw new IllegalArgumentException("Living Realms payload is too short");
        if(data.length>MAX_STATE_BYTES)throw new IllegalArgumentException("Living Realms payload exceeds hard size limit: "+data.length);
        int magic=((data[0]&0xFF)<<24)|((data[1]&0xFF)<<16)|((data[2]&0xFF)<<8)|(data[3]&0xFF);
        if(magic!=MAGIC)throw new IllegalArgumentException("Not a Living Realms state payload");
        int version=((data[4]&0xFF)<<24)|((data[5]&0xFF)<<16)|((data[6]&0xFF)<<8)|(data[7]&0xFF);
        if(version<MIN_SUPPORTED_SCHEMA||version>SCHEMA_VERSION)throw new IllegalArgumentException("Unsupported Living Realms schema "+version);
        return version;
    }

    /** Non-zero CRC32 token; zero is reserved for pre-checksum Minecraft SavedData. */
    public static long integrityToken(byte[] data) {
        Objects.requireNonNull(data,"data");
        CRC32 crc=new CRC32();crc.update(data);return crc.getValue()+1L;
    }

    public static byte[] encode(SimulationState state) {
        Objects.requireNonNull(state,"state");
        SimulationValidator.validate(state).throwIfInvalid();
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(bytes))) {
                out.writeInt(MAGIC);out.writeInt(SCHEMA_VERSION);out.writeLong(state.seed());out.writeLong(state.clock().gameTicks());out.writeLong(state.peekNextId());
                writeRegions(out,state);writeFactions(out,state);writeShipments(out,state);writeV4Strategic(out,state);writeV5Law(out,state);writeV6NavalAndPlayers(out,state);writeV7Industry(out,state);writeV9Config(out,state);writeV11Social(out,state);writeV12Civilization(out,state);writeV13Humanity(out,state);writeV14PirateHideouts(out,state);writeV15SiegeEquipment(out,state);writeV16SettlementEconomy(out,state);writeV17FinalProduct(out,state);writeV18GoodsAndOrigins(out,state);writeV19ProvenanceAndSites(out,state);writeV20UnderworldContracts(out,state);writeHistory(out,state);
            }
            byte[] payload=bytes.toByteArray();
            if(payload.length>MAX_STATE_BYTES)throw new IllegalStateException("Living Realms state exceeds hard size limit: "+payload.length);
            return payload;
        } catch(IOException e){throw new UncheckedIOException(e);}
    }

    private static void writeRegions(DataOutputStream out,SimulationState state)throws IOException{
        out.writeInt(state.regions().size());
        for(EcosystemRegion r:state.regions()){
            out.writeLong(r.id());writeString(out,r.biome().id());out.writeDouble(r.areaKm2());writePosition(out,r.center());out.writeDouble(r.plantBiomass());out.writeInt(r.populations().size());
            for(PopulationGroup p:r.populations()){
                out.writeLong(p.id());writeString(out,p.speciesId());writeString(out,p.biomeId());out.writeDouble(p.position().x());out.writeDouble(p.position().z());out.writeDouble(p.population());
                out.writeDouble(p.health());out.writeDouble(p.hunger());out.writeDouble(p.reproductiveFraction());
                out.writeDouble(p.thirst());out.writeDouble(p.diseasePressure());out.writeDouble(p.injuryPressure());out.writeDouble(p.averageAgeDays());out.writeDouble(p.migrationPressure());
            }
        }
    }

    private static void writeFactions(DataOutputStream out,SimulationState state)throws IOException{
        out.writeInt(state.factions().size());
        for(Faction f:state.factions()){
            out.writeLong(f.id());writeString(out,f.name());writeString(out,f.rulerName());out.writeDouble(f.treasury());out.writeDouble(f.technology());writeGovernment(out,f.government());
            for(ResourceType rt:ResourceType.values())out.writeDouble(f.stockpile().get(rt));
            out.writeInt(f.settlements().size());
            for(Settlement st:f.settlements()){
                out.writeLong(st.id());writeString(out,st.name());out.writeDouble(st.position().x());out.writeDouble(st.position().z());out.writeInt(st.population());out.writeInt(st.housing());out.writeDouble(st.infrastructure());
                out.writeDouble(st.prosperity());out.writeDouble(st.unrest());out.writeDouble(st.foodSecurity());out.writeDouble(st.publicOrder());out.writeDouble(st.employment());
                var completed=new ArrayList<>(st.completedConstruction());Collections.sort(completed);out.writeInt(completed.size());for(String key:completed)writeString(out,key);out.writeInt(st.developmentPriority().ordinal());
            }
            out.writeInt(f.armies().size());for(Army a:f.armies()){out.writeLong(a.id());out.writeDouble(a.position().x());out.writeDouble(a.position().z());out.writeInt(a.infantry());out.writeInt(a.cavalry());out.writeInt(a.artillery());out.writeInt(a.armor());out.writeInt(a.aircraft());out.writeDouble(a.morale());out.writeDouble(a.supply());}
            var relations=new ArrayList<>(f.relations().entrySet());relations.sort(Map.Entry.comparingByKey());out.writeInt(relations.size());for(var e:relations){out.writeLong(e.getKey());DiplomaticRelation rel=e.getValue();out.writeDouble(rel.opinion());out.writeInt(rel.status().ordinal());out.writeBoolean(rel.tradeAgreement());}
        }
    }

    private static void writeGovernment(DataOutputStream out,GovernmentState g)throws IOException{
        out.writeInt(g.type().ordinal());out.writeInt(g.successionLaw().ordinal());RulerProfile r=g.ruler();out.writeLong(r.id());writeString(out,r.name());out.writeInt(r.ageYears());out.writeDouble(r.health());out.writeDouble(r.diplomacy());out.writeDouble(r.stewardship());out.writeDouble(r.martial());out.writeDouble(r.legitimacy());out.writeDouble(g.stability());out.writeDouble(g.legitimacy());out.writeDouble(g.corruption());out.writeDouble(g.taxRate());out.writeDouble(g.lawEnforcement());out.writeLong(g.yearsInPower());
    }

    private static void writeShipments(DataOutputStream out,SimulationState state)throws IOException{
        out.writeInt(state.shipments().size());for(TradeShipment s:state.shipments()){out.writeLong(s.id());out.writeLong(s.sellerFactionId());out.writeLong(s.buyerFactionId());out.writeInt(s.resource().ordinal());out.writeDouble(s.amount());out.writeDouble(s.value());out.writeDouble(s.origin().x());out.writeDouble(s.origin().z());out.writeDouble(s.destination().x());out.writeDouble(s.destination().z());out.writeDouble(s.progress());}
    }

    private static void writeV4Strategic(DataOutputStream out,SimulationState state)throws IOException{
        out.writeInt(state.treaties().size());for(Treaty t:state.treaties()){out.writeLong(t.id());out.writeLong(t.factionA());out.writeLong(t.factionB());out.writeInt(t.type().ordinal());out.writeLong(t.startDay());out.writeLong(t.endDay());out.writeBoolean(t.active());}
        out.writeInt(state.wars().size());for(WarState w:state.wars()){out.writeLong(w.id());out.writeLong(w.attackerFactionId());out.writeLong(w.defenderFactionId());out.writeInt(w.goal().ordinal());out.writeLong(w.targetSettlementId());out.writeLong(w.startDay());out.writeDouble(w.attackerScore());out.writeDouble(w.attackerExhaustion());out.writeDouble(w.defenderExhaustion());out.writeBoolean(w.active());}
        out.writeInt(state.routes().size());for(TransportRoute r:state.routes()){out.writeLong(r.id());out.writeLong(r.ownerFactionId());out.writeLong(r.fromSettlementId());out.writeLong(r.toSettlementId());out.writeInt(r.mode().ordinal());out.writeDouble(r.distanceBlocks());out.writeDouble(r.quality());out.writeDouble(r.security());out.writeDouble(r.capacityPerDay());out.writeBoolean(r.operational());}
        out.writeInt(state.objectives().size());for(MilitaryObjective o:state.objectives()){out.writeLong(o.id());out.writeLong(o.armyId());out.writeLong(o.ownerFactionId());out.writeInt(o.type().ordinal());out.writeLong(o.targetFactionId());out.writeLong(o.targetSettlementId());out.writeDouble(o.targetPosition().x());out.writeDouble(o.targetPosition().z());out.writeLong(o.createdDay());out.writeInt(o.priority());out.writeBoolean(o.complete());}
        out.writeInt(state.sieges().size());for(SiegeState s:state.sieges()){out.writeLong(s.id());out.writeLong(s.attackerFactionId());out.writeLong(s.defenderFactionId());out.writeLong(s.settlementId());out.writeLong(s.startDay());out.writeDouble(s.progress());out.writeDouble(s.blockade());out.writeBoolean(s.active());}
        out.writeInt(state.airWings().size());for(AirWing w:state.airWings()){out.writeLong(w.id());out.writeLong(w.factionId());out.writeInt(w.model().ordinal());out.writeInt(w.aircraft());writePosition(out,w.position());writePosition(out,w.basePosition());writePosition(out,w.targetPosition());out.writeDouble(w.fuel());out.writeDouble(w.readiness());out.writeDouble(w.experience());out.writeInt(w.mission().ordinal());}
        out.writeInt(state.bounties().size());for(BountyContract b:state.bounties()){out.writeLong(b.id());writeString(out,b.actorKey());out.writeLong(b.issuerFactionId());out.writeLong(b.createdDay());out.writeDouble(b.reward());out.writeInt(b.status().ordinal());writeString(out,b.hunterKey());}
        writeCrimeLedger(out,state.crimeLedger());
    }

    private static void writeV5Law(DataOutputStream out,SimulationState state)throws IOException{
        out.writeInt(state.custody().size());
        for(CustodyRecord c:state.custody()){out.writeLong(c.id());writeString(out,c.actorKey());out.writeLong(c.factionId());out.writeLong(c.startDay());out.writeLong(c.releaseDay());out.writeDouble(c.bountyAtArrest());writeString(out,c.reason());out.writeBoolean(c.active());}
    }

    private static void writeV9Config(DataOutputStream out,SimulationState state)throws IOException{
        SimulationConfig c=state.config();
        out.writeDouble(c.physicalRadiusBlocks());out.writeDouble(c.regionalRadiusBlocks());
        out.writeInt(c.maxPhysicalWildlife());out.writeInt(c.maxPhysicalCaravans());out.writeInt(c.maxPhysicalMilitaryEntities());out.writeInt(c.maxPhysicalNavalEntities());
        out.writeInt(c.constructionBlockOpsPerTick());out.writeInt(c.strategicDaysPerStep());
        out.writeDouble(c.crimeHeatDecayPerDay());out.writeDouble(c.reputationDecayPerDay());out.writeDouble(c.rebellionThreshold());out.writeDouble(c.borderDisputeThreshold());
    }

    private static void writeV6NavalAndPlayers(DataOutputStream out,SimulationState state)throws IOException{
        out.writeInt(state.ports().size());
        for(PortState p:state.ports()){out.writeLong(p.id());out.writeLong(p.factionId());out.writeLong(p.settlementId());writePosition(out,p.position());out.writeInt(p.level());out.writeDouble(p.condition());out.writeDouble(p.security());out.writeBoolean(p.operational());}
        out.writeInt(state.fleets().size());
        for(Fleet f:state.fleets()){out.writeLong(f.id());out.writeLong(f.factionId());out.writeLong(f.homePortId());for(ShipClass c:ShipClass.values())out.writeInt(f.count(c));writePosition(out,f.position());writePosition(out,f.targetPosition());out.writeInt(f.mission().ordinal());out.writeDouble(f.fuel());out.writeDouble(f.readiness());out.writeDouble(f.experience());out.writeDouble(f.supply());out.writeInt(f.embarkedPersonnel());}
        var standings=new ArrayList<>(state.playerStandings().values());standings.sort(Comparator.comparing(PlayerStanding::actorKey));out.writeInt(standings.size());
        for(PlayerStanding ps:standings){writeString(out,ps.actorKey());var reps=new ArrayList<>(ps.reputations().entrySet());reps.sort(Map.Entry.comparingByKey());out.writeInt(reps.size());for(var e:reps){out.writeLong(e.getKey());out.writeDouble(e.getValue());}out.writeLong(ps.memberFactionId());out.writeInt(ps.rank().ordinal());out.writeLong(ps.joinedDay());out.writeDouble(ps.servicePoints());out.writeInt(ps.expulsions());}
    }

    private static void writeV7Industry(DataOutputStream out,SimulationState state)throws IOException{
        out.writeInt(state.industrialSites().size());
        for(IndustrialSite site:state.industrialSites()){out.writeLong(site.id());out.writeLong(site.factionId());out.writeLong(site.settlementId());out.writeInt(site.kind().ordinal());out.writeInt(site.level());out.writeDouble(site.condition());out.writeInt(site.status().ordinal());out.writeInt(site.starvedDays());out.writeInt(site.downtimeDays());out.writeInt(site.lastCycles());out.writeDouble(site.lastUtilization());}
    }

    private static void writeV11Social(DataOutputStream out,SimulationState state)throws IOException{
        out.writeInt(state.socialCitizens().size());
        for(SocialCitizen c:state.socialCitizens()){
            out.writeLong(c.id());out.writeLong(c.factionId());out.writeLong(c.settlementId());out.writeInt(c.projectionSlot());writeString(out,c.name());out.writeInt(c.skinVariant());out.writeInt(c.role().ordinal());out.writeLong(c.birthDay());out.writeDouble(c.health());out.writeDouble(c.money());out.writeBoolean(c.alive());
            CitizenNeeds n=c.needs();out.writeDouble(n.hunger());out.writeDouble(n.safety());out.writeDouble(n.social());out.writeDouble(n.status());out.writeDouble(n.comfort());
            CitizenPersonality p=c.personality();out.writeDouble(p.aggression());out.writeDouble(p.tradeAffinity());out.writeDouble(p.caution());out.writeDouble(p.greed());out.writeDouble(p.loyalty());out.writeDouble(p.treachery());
            List<CitizenMemory> memories=c.memories();out.writeInt(memories.size());for(CitizenMemory m:memories){out.writeLong(m.day());out.writeInt(m.type().ordinal());writeString(out,m.subjectKey());writeString(out,m.sourceKey());writeString(out,m.summary());writePosition(out,m.position());out.writeDouble(m.importance());out.writeDouble(m.confidence());}
            var relationships=new ArrayList<>(c.relationships().values());relationships.sort(Comparator.comparing(CitizenRelationship::targetKey));out.writeInt(relationships.size());for(CitizenRelationship r:relationships){writeString(out,r.targetKey());out.writeDouble(r.friendship());out.writeDouble(r.hostility());out.writeDouble(r.romance());out.writeDouble(r.rivalry());out.writeDouble(r.trust());out.writeInt(r.familyBond().ordinal());}
        }
    }

    private static void writeV12Civilization(DataOutputStream out,SimulationState state)throws IOException{
        var settlements=new ArrayList<>(state.settlementCivilizations().values());settlements.sort(Comparator.comparingLong(SettlementCivilizationState::settlementId));out.writeInt(settlements.size());
        for(SettlementCivilizationState c:settlements){out.writeLong(c.settlementId());out.writeLong(c.heritageFactionId());out.writeDouble(c.sanitation());out.writeDouble(c.diseasePressure());out.writeDouble(c.education());out.writeDouble(c.waterSecurity());out.writeDouble(c.refugeePressure());out.writeDouble(c.banditPressure());out.writeDouble(c.culturalCohesion());out.writeDouble(c.assimilation());out.writeDouble(c.resourcePressure());out.writeLong(c.lastEpidemicDay());out.writeLong(c.lastMigrationDay());}
        var factions=new ArrayList<>(state.factionCivilizations().values());factions.sort(Comparator.comparingLong(FactionCivilizationState::factionId));out.writeInt(factions.size());
        for(FactionCivilizationState c:factions){out.writeLong(c.factionId());writeString(out,c.cultureName());writeString(out,c.faithName());writeString(out,c.dialectName());out.writeDouble(c.culturalInfluence());out.writeDouble(c.religiousInfluence());out.writeDouble(c.education());out.writeDouble(c.propaganda());out.writeDouble(c.intelligence());out.writeLong(c.tributaryToFactionId());out.writeDouble(c.tributeRate());var spies=new ArrayList<>(c.spyNetworks().entrySet());spies.sort(Map.Entry.comparingByKey());out.writeInt(spies.size());for(var e:spies){out.writeLong(e.getKey());out.writeDouble(e.getValue());}}
        out.writeInt(state.resourceClaims().size());for(ResourceClaim c:state.resourceClaims()){out.writeLong(c.id());out.writeLong(c.factionId());out.writeLong(c.settlementId());out.writeInt(c.type().ordinal());writePosition(out,c.position());out.writeDouble(c.strength());out.writeLong(c.contestedByFactionId());out.writeBoolean(c.active());}
        out.writeInt(state.raids().size());for(RaidParty r:state.raids()){out.writeLong(r.id());out.writeLong(r.attackerFactionId());out.writeLong(r.originSettlementId());out.writeLong(r.targetSettlementId());out.writeLong(r.createdDay());out.writeInt(r.manpower());out.writeDouble(r.morale());out.writeDouble(r.progress());out.writeBoolean(r.active());out.writeBoolean(r.bandit());}
        out.writeInt(state.legends().size());for(LegendRecord l:state.legends()){out.writeLong(l.id());out.writeLong(l.day());out.writeLong(l.factionId());out.writeLong(l.settlementId());writeString(out,l.subjectKey());writeString(out,l.title());writeString(out,l.description());out.writeDouble(l.renown());out.writeBoolean(l.monumented());}
    }


    private static void writeV13Humanity(DataOutputStream out,SimulationState state)throws IOException{
        var citizens=new ArrayList<>(state.socialCitizens());citizens.sort(Comparator.comparingLong(SocialCitizen::id));out.writeInt(citizens.size());for(SocialCitizen c:citizens){out.writeLong(c.id());out.writeLong(c.householdId());out.writeDouble(c.professionSkill());}
        var settlements=new ArrayList<>(state.settlementCivilizations().values());settlements.sort(Comparator.comparingLong(SettlementCivilizationState::settlementId));out.writeInt(settlements.size());for(SettlementCivilizationState c:settlements){out.writeLong(c.settlementId());out.writeDouble(c.quarantineStrength());for(KnowledgeDomain d:KnowledgeDomain.values())out.writeDouble(c.knowledge(d));}
        var factions=new ArrayList<>(state.factionCivilizations().values());factions.sort(Comparator.comparingLong(FactionCivilizationState::factionId));out.writeInt(factions.size());for(FactionCivilizationState c:factions){out.writeLong(c.factionId());out.writeDouble(c.mercantileTradition());out.writeDouble(c.martialTradition());out.writeDouble(c.agrarianTradition());out.writeDouble(c.artisticTradition());out.writeDouble(c.religiousTolerance());out.writeDouble(c.openness());out.writeDouble(c.lawSeverity());out.writeDouble(c.dueProcess());out.writeDouble(c.refugeeAcceptance());}
        var households=new ArrayList<>(state.households());households.sort(Comparator.comparingLong(HouseholdState::id));out.writeInt(households.size());for(HouseholdState h:households){out.writeLong(h.id());out.writeLong(h.factionId());out.writeLong(h.settlementId());out.writeLong(h.foundedDay());out.writeBoolean(h.active());out.writeDouble(h.sharedWealth());writeString(out,h.homeKey());out.writeInt(h.memberIds().size());for(long id:h.memberIds())out.writeLong(id);out.writeInt(h.children().size());for(DependentChild child:h.children()){out.writeLong(child.id());out.writeLong(child.birthDay());out.writeLong(child.parentAId());out.writeLong(child.parentBId());out.writeLong(child.adoptiveParentAId());out.writeLong(child.adoptiveParentBId());}}
        var epidemics=new ArrayList<>(state.epidemics());epidemics.sort(Comparator.comparingLong(EpidemicRecord::id));out.writeInt(epidemics.size());for(EpidemicRecord e:epidemics){out.writeLong(e.id());out.writeLong(e.settlementId());out.writeLong(e.startDay());writeString(out,e.diseaseKey());out.writeDouble(e.severity());out.writeDouble(e.infectedFraction());out.writeInt(e.cumulativeDeaths());out.writeLong(e.lastUpdateDay());out.writeBoolean(e.active());}
        var migrations=new ArrayList<>(state.migrationGroups());migrations.sort(Comparator.comparingLong(MigrationGroup::id));out.writeInt(migrations.size());for(MigrationGroup g:migrations){out.writeLong(g.id());out.writeLong(g.originFactionId());out.writeLong(g.sourceSettlementId());out.writeLong(g.targetSettlementId());out.writeLong(g.campSettlementId());out.writeLong(g.createdDay());out.writeInt(g.people());out.writeInt(g.reason().ordinal());out.writeInt(g.status().ordinal());out.writeDouble(g.progress());out.writeDouble(g.food());out.writeDouble(g.health());out.writeInt(g.householdIds().size());for(long h:g.householdIds())out.writeLong(h);}
        var justice=new ArrayList<>(state.justiceCases());justice.sort(Comparator.comparingLong(JusticeCase::id));out.writeInt(justice.size());for(JusticeCase j:justice){out.writeLong(j.id());out.writeLong(j.factionId());out.writeLong(j.settlementId());out.writeLong(j.openedDay());writeString(out,j.accusedKey());out.writeInt(j.crimeType().ordinal());out.writeInt(j.status().ordinal());out.writeInt(j.sentence().ordinal());out.writeDouble(j.fine());out.writeLong(j.releaseDay());}
        var caches=new ArrayList<>(state.hiddenCaches());caches.sort(Comparator.comparingLong(HiddenCache::id));out.writeInt(caches.size());for(HiddenCache c:caches){out.writeLong(c.id());out.writeLong(c.factionId());out.writeLong(c.settlementId());out.writeLong(c.createdDay());writePosition(out,c.position());for(ResourceType r:ResourceType.values())out.writeDouble(c.goods().getOrDefault(r,0.0));out.writeLong(c.discoveredByFactionId());out.writeBoolean(c.recovered());}
        var pirates=new ArrayList<>(state.pirateBands());pirates.sort(Comparator.comparingLong(PirateBand::id));out.writeInt(pirates.size());for(PirateBand b:pirates){out.writeLong(b.id());out.writeLong(b.originSettlementId());out.writeLong(b.createdDay());writePosition(out,b.position());out.writeInt(b.strength());out.writeDouble(b.morale());out.writeDouble(b.loot());out.writeBoolean(b.active());}
        var marriages=new ArrayList<>(state.diplomaticMarriages());marriages.sort(Comparator.comparingLong(DiplomaticMarriage::id));out.writeInt(marriages.size());for(DiplomaticMarriage m:marriages){out.writeLong(m.id());out.writeLong(m.day());out.writeLong(m.citizenA());out.writeLong(m.citizenB());out.writeLong(m.factionA());out.writeLong(m.factionB());out.writeBoolean(m.active());}
        var dynasties=new ArrayList<>(state.dynasties().values());dynasties.sort(Comparator.comparingLong(DynastyState::factionId));out.writeInt(dynasties.size());for(DynastyState d:dynasties){out.writeLong(d.factionId());out.writeLong(d.foundedDay());writeString(out,d.houseName());out.writeLong(d.rulerCitizenId());out.writeLong(d.heirCitizenId());out.writeLong(d.regentCitizenId());out.writeDouble(d.prestige());out.writeBoolean(d.successionCrisis());out.writeLong(d.crisisSinceDay());out.writeInt(d.generation());out.writeLong(d.lastSuccessionDay());}
        var civic=new ArrayList<>(state.civicEvents());civic.sort(Comparator.comparingLong(CivicEvent::id));out.writeInt(civic.size());for(CivicEvent e:civic){out.writeLong(e.id());out.writeLong(e.factionId());out.writeLong(e.settlementId());out.writeLong(e.startDay());out.writeLong(e.endDay());out.writeInt(e.type().ordinal());writeString(out,e.title());out.writeDouble(e.attendance());out.writeDouble(e.intensity());out.writeBoolean(e.active());}
        var intel=new ArrayList<>(state.intelligenceOperations());intel.sort(Comparator.comparingLong(IntelligenceOperation::id));out.writeInt(intel.size());for(IntelligenceOperation o:intel){out.writeLong(o.id());out.writeLong(o.sourceFactionId());out.writeLong(o.targetFactionId());out.writeLong(o.startDay());out.writeLong(o.agentCitizenId());out.writeInt(o.type().ordinal());out.writeDouble(o.progress());out.writeDouble(o.secrecy());out.writeDouble(o.quality());out.writeBoolean(o.discovered());out.writeBoolean(o.active());}
        var propaganda=new ArrayList<>(state.propagandaCampaigns());propaganda.sort(Comparator.comparingLong(PropagandaCampaign::id));out.writeInt(propaganda.size());for(PropagandaCampaign c:propaganda){out.writeLong(c.id());out.writeLong(c.factionId());out.writeLong(c.settlementId());out.writeLong(c.startDay());out.writeLong(c.endDay());out.writeInt(c.theme().ordinal());out.writeDouble(c.intensity());out.writeDouble(c.reach());out.writeBoolean(c.active());}
        var ruins=new ArrayList<>(state.ruinSites());ruins.sort(Comparator.comparingLong(RuinSite::id));out.writeInt(ruins.size());for(RuinSite r:ruins){out.writeLong(r.id());out.writeLong(r.originalSettlementId());out.writeLong(r.originalFactionId());out.writeLong(r.createdDay());writePosition(out,r.position());writeString(out,r.originalName());writeString(out,r.cause());out.writeDouble(r.preservation());out.writeBoolean(r.looted());out.writeBoolean(r.reclaimed());}
        var tasks=new ArrayList<>(state.assistanceTasks());tasks.sort(Comparator.comparingLong(AssistanceTask::id));out.writeInt(tasks.size());for(AssistanceTask t:tasks){out.writeLong(t.id());out.writeLong(t.factionId());out.writeLong(t.settlementId());out.writeLong(t.createdDay());out.writeLong(t.expiresDay());out.writeInt(t.type().ordinal());writeString(out,t.causeKey());out.writeDouble(t.initialPressure());out.writeDouble(t.remainingPressure());out.writeInt(t.status().ordinal());}
    }

    private static void writeV14PirateHideouts(DataOutputStream out,SimulationState state)throws IOException{
        var hideouts=new ArrayList<>(state.pirateHideouts());hideouts.sort(Comparator.comparingLong(PirateHideout::id));out.writeInt(hideouts.size());for(PirateHideout h:hideouts){out.writeLong(h.id());out.writeLong(h.bandId());out.writeLong(h.originSettlementId());out.writeLong(h.createdDay());writePosition(out,h.position());out.writeDouble(h.defense());out.writeDouble(h.storedLoot());out.writeLong(h.discoveredByFactionId());out.writeBoolean(h.active());out.writeBoolean(h.destroyed());}
    }

    private static void writeV15SiegeEquipment(DataOutputStream out,SimulationState state)throws IOException{
        var sieges=new ArrayList<>(state.sieges());sieges.sort(Comparator.comparingLong(SiegeState::id));out.writeInt(sieges.size());for(SiegeState s:sieges){out.writeLong(s.id());out.writeInt(s.rams());out.writeInt(s.ladders());out.writeInt(s.artilleryPieces());out.writeDouble(s.breach());out.writeDouble(s.defenderCountermeasures());}
    }

    private static void writeV16SettlementEconomy(DataOutputStream out,SimulationState state)throws IOException{
        List<Settlement> settlements=new ArrayList<>();
        for(Faction f:state.factions())settlements.addAll(f.settlements());
        settlements.sort(Comparator.comparingLong(Settlement::id));
        out.writeInt(settlements.size());
        for(Settlement s:settlements){
            out.writeLong(s.id());
            out.writeDouble(s.barnCapacity());
            out.writeDouble(s.granaryCapacity());
            for(ResourceType rt:ResourceType.values())out.writeDouble(s.stockpile().get(rt));
        }
    }

    private static void writeV17FinalProduct(DataOutputStream out,SimulationState state)throws IOException{
        var citizens=new ArrayList<>(state.socialCitizens());citizens.sort(Comparator.comparingLong(SocialCitizen::id));
        out.writeInt(citizens.size());
        for(SocialCitizen c:citizens){
            out.writeLong(c.id());out.writeLong(c.appearancePacked());writeString(out,c.cultureKey());writeString(out,c.faithKey());
            out.writeInt(c.socialClass().ordinal());out.writeDouble(c.education());writeString(out,c.workplaceKey());
            out.writeInt(c.wealthClass().ordinal());out.writeDouble(c.personalInfluence());writeString(out,c.professionHistoryKey());
            out.writeInt(c.employmentStatus().ordinal());
        }
        var standings=new ArrayList<>(state.playerStandings().values());standings.sort(Comparator.comparing(PlayerStanding::actorKey));
        out.writeInt(standings.size());
        for(PlayerStanding ps:standings){
            writeString(out,ps.actorKey());
            var factions=new ArrayList<>(ps.influences().entrySet());factions.sort(Map.Entry.comparingByKey());
            out.writeInt(factions.size());
            for(var fe:factions){
                out.writeLong(fe.getKey());
                var inst=new ArrayList<>(fe.getValue().entrySet());inst.sort(Comparator.comparing(e->e.getKey().ordinal()));
                out.writeInt(inst.size());for(var ie:inst){out.writeInt(ie.getKey().ordinal());out.writeDouble(ie.getValue());}
            }
            out.writeInt(ps.careerTrack().ordinal());out.writeInt(ps.careerRankIndex());out.writeDouble(ps.careerService());
        }
        var debts=new ArrayList<>(state.debts());debts.sort(Comparator.comparingLong(SovereignDebt::id));
        out.writeInt(debts.size());
        for(SovereignDebt d:debts){out.writeLong(d.id());out.writeLong(d.debtorFactionId());writeString(out,d.creditorKey());out.writeDouble(d.principal());out.writeDouble(d.interestRate());out.writeDouble(d.remaining());out.writeLong(d.createdDay());out.writeLong(d.dueDay());out.writeDouble(d.risk());out.writeBoolean(d.defaulted());out.writeBoolean(d.active());}
        var projects=new ArrayList<>(state.grandProjects());projects.sort(Comparator.comparingLong(GrandProject::id));
        out.writeInt(projects.size());
        for(GrandProject p:projects){out.writeLong(p.id());out.writeLong(p.sponsorFactionId());out.writeLong(p.settlementId());out.writeInt(p.type().ordinal());out.writeLong(p.createdDay());out.writeDouble(p.progress());out.writeInt(p.phase().ordinal());writeString(out,p.pauseReason());out.writeBoolean(p.complete());out.writeBoolean(p.active());out.writeDouble(p.spentTreasury());out.writeDouble(p.spentFood());out.writeDouble(p.spentTimber());out.writeDouble(p.spentStone());out.writeDouble(p.spentIron());out.writeInt(p.laborApplied());out.writeBoolean(p.effectInfrastructure());out.writeBoolean(p.effectProsperity());out.writeBoolean(p.effectLegitimacy());out.writeBoolean(p.effectLegend());}
        var plans=new ArrayList<>(state.campaignPlans());plans.sort(Comparator.comparingLong(CampaignPlan::id));
        out.writeInt(plans.size());
        for(CampaignPlan p:plans){out.writeLong(p.id());out.writeLong(p.factionId());out.writeLong(p.warId());out.writeInt(p.type().ordinal());out.writeLong(p.targetSettlementId());out.writeInt(p.priority());out.writeLong(p.createdDay());out.writeBoolean(p.active());}
        var shipments=new ArrayList<>(state.shipments());shipments.sort(Comparator.comparingLong(TradeShipment::id));
        out.writeInt(shipments.size());
        for(TradeShipment s:shipments){out.writeLong(s.id());out.writeLong(s.originSettlementId());out.writeLong(s.destinationSettlementId());out.writeLong(s.routeId());out.writeInt(s.transportModeOrdinal());out.writeLong(s.departureDay());out.writeLong(s.expectedArrivalDay());out.writeDouble(s.risk());out.writeDouble(s.escortStrength());out.writeInt(s.lossState().ordinal());out.writeInt(s.delayDays());}
    }

    private static void writeCrimeLedger(DataOutputStream out,CrimeLedger ledger)throws IOException{
        var profiles=new ArrayList<>(ledger.profiles().values());profiles.sort(Comparator.comparing(WantedProfile::actorKey));out.writeInt(profiles.size());
        for(WantedProfile p:profiles){writeString(out,p.actorKey());out.writeDouble(p.globalInfamy());var jurisdictions=new ArrayList<>(p.jurisdictions().values());jurisdictions.sort(Comparator.comparingLong(JurisdictionWanted::factionId));out.writeInt(jurisdictions.size());for(JurisdictionWanted w:jurisdictions){out.writeLong(w.factionId());out.writeDouble(w.bounty());out.writeDouble(w.notoriety());out.writeDouble(w.heat());out.writeLong(w.lastCrimeDay());out.writeInt(w.witnessedCrimes());out.writeInt(w.violentCrimes());out.writeInt(w.captures());}}
        List<CrimeIncident> incidents=ledger.incidents();out.writeInt(incidents.size());for(CrimeIncident c:incidents){out.writeLong(c.id());out.writeLong(c.day());writeString(out,c.actorKey());out.writeLong(c.jurisdictionFactionId());out.writeInt(c.type().ordinal());out.writeDouble(c.stolenOrDamageValue());writePosition(out,c.position());out.writeBoolean(c.witnessed());out.writeInt(c.witnessCount());writeString(out,c.victimKey());writeString(out,c.evidence());}
    }

    private static void writeHistory(DataOutputStream out,SimulationState state)throws IOException{List<WorldEvent> history=state.history().all();out.writeInt(history.size());for(WorldEvent ev:history){out.writeLong(ev.day());writeString(out,ev.type());writeString(out,ev.message());}}

    public static SimulationState decode(byte[] data){return decode(data,SpeciesCatalog.starter());}
    public static SimulationState decode(byte[] data,Map<String,SpeciesDefinition> speciesCatalog){
        Objects.requireNonNull(data,"data");Objects.requireNonNull(speciesCatalog,"speciesCatalog");
        if(data.length>MAX_STATE_BYTES)throw new IllegalArgumentException("Living Realms payload exceeds hard size limit: "+data.length);
        try(DataInputStream in=new DataInputStream(new BufferedInputStream(new ByteArrayInputStream(data)))){
            if(in.readInt()!=MAGIC)throw new IOException("Not a Living Realms state file");int version=in.readInt();if(version<MIN_SUPPORTED_SCHEMA||version>SCHEMA_VERSION)throw new IOException("Unsupported schema "+version);
            long seed=in.readLong();long ticks=in.readLong();long nextId=in.readLong();
            if(ticks<0)throw new IOException("Negative simulation clock: "+ticks);if(nextId<1)throw new IOException("Invalid nextId: "+nextId);
            SimulationState state=new SimulationState(seed,speciesCatalog);state.clock().restore(ticks);state.restoreNextId(nextId);
            readRegions(in,state,version);readFactions(in,state,version);if(version>=3)readShipments(in,state,version);if(version>=4)readV4Strategic(in,state);if(version>=5)readV5Law(in,state);if(version>=6)readV6NavalAndPlayers(in,state);if(version>=7)readV7Industry(in,state);if(version>=9)readV9Config(in,state);if(version>=11)readV11Social(in,state);if(version>=12)readV12Civilization(in,state);if(version>=13)readV13Humanity(in,state,version);if(version>=14)readV14PirateHideouts(in,state);if(version>=15)readV15SiegeEquipment(in,state);if(version>=16)readV16SettlementEconomy(in,state,version);else migratePreV16SettlementEconomy(state);if(version>=17)readV17FinalProduct(in,state);else migratePreV17FinalProduct(state);if(version>=18)readV18GoodsAndOrigins(in,state);else migratePreV18Goods(state);            if(version>=19)readV19ProvenanceAndSites(in,state);else migratePreV19Provenance(state);if(version>=20)readV20UnderworldContracts(in,state);readHistory(in,state);if(in.read()!=-1)throw new IOException("Trailing bytes after Living Realms state");
            state.repairNextIdWatermark();
            if(version==SCHEMA_VERSION){try{SimulationValidator.validate(state).throwIfInvalid();}catch(IllegalStateException invalid){throw new IOException("Current-schema Living Realms state failed semantic validation",invalid);}}
            return state;
        }catch(IOException e){throw new UncheckedIOException(e);}
    }

    private static void readRegions(DataInputStream in,SimulationState state,int version)throws IOException{
        int regionCount=checkedCount(in.readInt(),100000,"regions");for(int i=0;i<regionCount;i++){
            long id=in.readLong();String biomeId=readString(in);var biome=state.biomes().get(biomeId);if(biome==null)throw new IOException("Unknown biome "+biomeId);double area=in.readDouble();SimPosition center=version>=8?readPosition(in):new SimPosition(0,0);EcosystemRegion r=new EcosystemRegion(id,biome,area,center);r.restorePlantBiomass(in.readDouble());int pc=checkedCount(in.readInt(),1000000,"populations");
            double sx=0,sz=0,weight=0;for(int j=0;j<pc;j++){long pid=in.readLong();String sid=readString(in);if(!state.species().containsKey(sid))throw new IOException("Save contains live species missing from active catalog: "+sid);String pb=readString(in);PopulationGroup p=new PopulationGroup(pid,sid,pb,new SimPosition(in.readDouble(),in.readDouble()),in.readDouble());p.setHealth(in.readDouble());p.setHunger(in.readDouble());p.setReproductiveFraction(in.readDouble());if(version>=4){p.setThirst(in.readDouble());p.setDiseasePressure(in.readDouble());p.setInjuryPressure(in.readDouble());p.setAverageAgeDays(in.readDouble());p.setMigrationPressure(in.readDouble());}r.add(p);double w=Math.max(.5,p.population());sx+=p.position().x()*w;sz+=p.position().z()*w;weight+=w;}if(version<8&&weight>0)r.restoreCenter(new SimPosition(sx/weight,sz/weight));state.addRegion(r);
        }
    }

    private static void readFactions(DataInputStream in,SimulationState state,int version)throws IOException{
        int factionCount=checkedCount(in.readInt(),10000,"factions");for(int i=0;i<factionCount;i++){
            long id=in.readLong();Faction f=new Faction(id,readString(in),readString(in));f.restoreTreasury(in.readDouble());f.restoreTechnology(in.readDouble());if(version>=4)f.restoreGovernment(readGovernment(in));
            for(int ri=0;ri<resourceCount(version);ri++)f.stockpile().add(ResourceType.values()[ri],in.readDouble());
            int sc=checkedCount(in.readInt(),100000,"settlements");for(int j=0;j<sc;j++){Settlement st=new Settlement(in.readLong(),readString(in),new SimPosition(in.readDouble(),in.readDouble()),in.readInt(),in.readInt());st.improveInfrastructure(in.readDouble());if(version>=4)st.restoreSociety(in.readDouble(),in.readDouble(),in.readDouble(),in.readDouble(),in.readDouble());if(version>=2){int cc=checkedCount(in.readInt(),1000000,"completed construction");for(int k=0;k<cc;k++)st.markConstructionCompleted(readString(in));}if(version>=10)st.setDevelopmentPriority(DevelopmentPriority.values()[enumOrdinal(in.readInt(),DevelopmentPriority.values().length,"development priority")]);f.addSettlement(st);}
            int ac=checkedCount(in.readInt(),100000,"armies");for(int j=0;j<ac;j++){Army a=new Army(in.readLong(),id,new SimPosition(in.readDouble(),in.readDouble()),in.readInt());a.restoreState(in.readInt(),in.readInt(),in.readInt(),in.readInt(),in.readDouble(),in.readDouble());f.addArmy(a);}
            int rc=checkedCount(in.readInt(),100000,"relations");for(int j=0;j<rc;j++){long other=in.readLong();double opinion=in.readDouble();int ord=enumOrdinal(in.readInt(),RelationStatus.values().length,"relation status");boolean trade=in.readBoolean();f.relationWith(other).restore(opinion,RelationStatus.values()[ord],trade);}state.addFaction(f);
        }
    }

    private static GovernmentState readGovernment(DataInputStream in)throws IOException{
        GovernmentType type=GovernmentType.values()[enumOrdinal(in.readInt(),GovernmentType.values().length,"government type")];SuccessionLaw succession=SuccessionLaw.values()[enumOrdinal(in.readInt(),SuccessionLaw.values().length,"succession law")];
        RulerProfile ruler=new RulerProfile(in.readLong(),readString(in),in.readInt(),in.readDouble(),in.readDouble(),in.readDouble(),in.readDouble(),in.readDouble());GovernmentState g=new GovernmentState(type,succession,ruler);g.restore(in.readDouble(),in.readDouble(),in.readDouble(),in.readDouble(),in.readDouble(),in.readLong());return g;
    }

    private static void readShipments(DataInputStream in,SimulationState state,int version)throws IOException{int tc=checkedCount(in.readInt(),1000000,"trade shipments");for(int i=0;i<tc;i++){long shipmentId=in.readLong(),seller=in.readLong(),buyer=in.readLong();int resourceOrdinal=enumOrdinal(in.readInt(),resourceCount(version),"shipment resource");TradeShipment shipment=new TradeShipment(shipmentId,seller,buyer,ResourceType.values()[resourceOrdinal],in.readDouble(),in.readDouble(),new SimPosition(in.readDouble(),in.readDouble()),new SimPosition(in.readDouble(),in.readDouble()));try{shipment.restoreProgress(in.readDouble());}catch(IllegalArgumentException e){throw new IOException("bad shipment progress",e);}state.addShipment(shipment);}}

    private static void readV4Strategic(DataInputStream in,SimulationState state)throws IOException{
        int n=checkedCount(in.readInt(),100000,"treaties");for(int i=0;i<n;i++){Treaty t=new Treaty(in.readLong(),in.readLong(),in.readLong(),TreatyType.values()[enumOrdinal(in.readInt(),TreatyType.values().length,"treaty")],in.readLong(),in.readLong());t.restoreActive(in.readBoolean());state.addTreaty(t);}
        n=checkedCount(in.readInt(),100000,"wars");for(int i=0;i<n;i++){WarState w=new WarState(in.readLong(),in.readLong(),in.readLong(),WarGoalType.values()[enumOrdinal(in.readInt(),WarGoalType.values().length,"war goal")],in.readLong(),in.readLong());w.restore(in.readDouble(),in.readDouble(),in.readDouble(),in.readBoolean());state.addWar(w);}
        n=checkedCount(in.readInt(),1000000,"routes");for(int i=0;i<n;i++){long id=in.readLong(),owner=in.readLong(),from=in.readLong(),to=in.readLong();TransportMode mode=TransportMode.values()[enumOrdinal(in.readInt(),TransportMode.values().length,"transport mode")];double distance=in.readDouble();TransportRoute r=new TransportRoute(id,owner,from,to,mode,distance,in.readDouble(),in.readDouble(),in.readDouble());r.setOperational(in.readBoolean());state.addRoute(r);}
        n=checkedCount(in.readInt(),1000000,"objectives");for(int i=0;i<n;i++){MilitaryObjective o=new MilitaryObjective(in.readLong(),in.readLong(),in.readLong(),MilitaryObjectiveType.values()[enumOrdinal(in.readInt(),MilitaryObjectiveType.values().length,"objective")],in.readLong(),in.readLong(),new SimPosition(in.readDouble(),in.readDouble()),in.readLong(),in.readInt());o.restoreComplete(in.readBoolean());state.addObjective(o);}
        n=checkedCount(in.readInt(),100000,"sieges");for(int i=0;i<n;i++){SiegeState s=new SiegeState(in.readLong(),in.readLong(),in.readLong(),in.readLong(),in.readLong());s.restore(in.readDouble(),in.readDouble(),in.readBoolean());state.addSiege(s);}
        n=checkedCount(in.readInt(),100000,"air wings");for(int i=0;i<n;i++){long id=in.readLong(),faction=in.readLong();AircraftModel model=AircraftModel.values()[enumOrdinal(in.readInt(),AircraftModel.values().length,"aircraft model")];int aircraft=in.readInt();SimPosition pos=readPosition(in),base=readPosition(in),target=readPosition(in);double fuel=in.readDouble(),readiness=in.readDouble(),experience=in.readDouble();AirMission mission=AirMission.values()[enumOrdinal(in.readInt(),AirMission.values().length,"air mission")];AirWing w=new AirWing(id,faction,model,Math.max(1,aircraft),base);w.restore(aircraft,pos,base,target,fuel,readiness,experience,mission);state.addAirWing(w);}
        n=checkedCount(in.readInt(),1000000,"bounties");for(int i=0;i<n;i++){BountyContract b=new BountyContract(in.readLong(),readString(in),in.readLong(),in.readLong(),in.readDouble());BountyContract.Status status=BountyContract.Status.values()[enumOrdinal(in.readInt(),BountyContract.Status.values().length,"bounty status")];b.restore(status,readString(in));state.addBounty(b);}
        readCrimeLedger(in,state.crimeLedger());
    }

    private static void readV5Law(DataInputStream in,SimulationState state)throws IOException{
        int n=checkedCount(in.readInt(),1000000,"custody");
        for(int i=0;i<n;i++){CustodyRecord c=new CustodyRecord(in.readLong(),readString(in),in.readLong(),in.readLong(),in.readLong(),in.readDouble(),readString(in));c.restoreActive(in.readBoolean());state.addCustody(c);}
    }

    private static void readV6NavalAndPlayers(DataInputStream in,SimulationState state)throws IOException{
        int n=checkedCount(in.readInt(),100000,"ports");
        for(int i=0;i<n;i++){long id=in.readLong(),faction=in.readLong(),settlement=in.readLong();SimPosition pos=readPosition(in);int level=in.readInt();double condition=in.readDouble(),security=in.readDouble();boolean operational=in.readBoolean();PortState port=new PortState(id,faction,settlement,pos,level);port.restore(level,condition,security,operational);state.addPort(port);}
        n=checkedCount(in.readInt(),100000,"fleets");
        for(int i=0;i<n;i++){long id=in.readLong(),faction=in.readLong(),home=in.readLong();EnumMap<ShipClass,Integer> ships=new EnumMap<>(ShipClass.class);for(ShipClass c:ShipClass.values())ships.put(c,checkedCount(in.readInt(),100000,"ship count"));SimPosition pos=readPosition(in),target=readPosition(in);NavalMission mission=NavalMission.values()[enumOrdinal(in.readInt(),NavalMission.values().length,"naval mission")];double fuel=in.readDouble(),readiness=in.readDouble(),experience=in.readDouble(),supply=in.readDouble();int embarked=checkedCount(in.readInt(),10000000,"embarked personnel");ShipClass seed=ShipClass.PATROL_BOAT;int count=Math.max(1,ships.values().stream().mapToInt(Integer::intValue).sum());for(ShipClass c:ShipClass.values())if(ships.getOrDefault(c,0)>0){seed=c;break;}Fleet fleet=new Fleet(id,faction,home,pos,seed,count);fleet.restore(ships,pos,home,target,mission,fuel,readiness,experience,supply,embarked);state.addFleet(fleet);}
        n=checkedCount(in.readInt(),1000000,"player standings");
        for(int i=0;i<n;i++){PlayerStanding ps=new PlayerStanding(readString(in));int rc=checkedCount(in.readInt(),100000,"player reputations");for(int j=0;j<rc;j++)ps.restoreReputation(in.readLong(),in.readDouble());long member=in.readLong();FactionRank rank=FactionRank.values()[enumOrdinal(in.readInt(),FactionRank.values().length,"faction rank")];long joined=in.readLong();double service=in.readDouble();int expulsions=checkedCount(in.readInt(),100000,"expulsions");ps.restoreMembership(member,rank,joined,service,expulsions);state.restorePlayerStanding(ps);}
    }

    private static void readV7Industry(DataInputStream in,SimulationState state)throws IOException{
        int n=checkedCount(in.readInt(),1000000,"industrial sites");
        for(int i=0;i<n;i++){long id=in.readLong(),faction=in.readLong(),settlement=in.readLong();IndustryKind kind=IndustryKind.values()[enumOrdinal(in.readInt(),IndustryKind.values().length,"industry kind")];int level=in.readInt();double condition=in.readDouble();IndustrialSiteStatus status=IndustrialSiteStatus.values()[enumOrdinal(in.readInt(),IndustrialSiteStatus.values().length,"industry status")];int starved=checkedCount(in.readInt(),10000000,"industry starved days"),downtime=checkedCount(in.readInt(),10000000,"industry downtime"),cycles=checkedCount(in.readInt(),10000000,"industry cycles");double utilization=in.readDouble();IndustrialSite site=new IndustrialSite(id,faction,settlement,kind,level);site.restore(condition,status,starved,downtime,cycles,utilization);state.addIndustrialSite(site);}
    }

    private static void readV9Config(DataInputStream in,SimulationState state)throws IOException{
        try{
            state.setConfig(new SimulationConfig(in.readDouble(),in.readDouble(),checkedCount(in.readInt(),100000,"wildlife budget"),checkedCount(in.readInt(),100000,"caravan budget"),checkedCount(in.readInt(),100000,"military budget"),checkedCount(in.readInt(),100000,"naval budget"),checkedCount(in.readInt(),1000000,"construction ops"),checkedCount(in.readInt(),365,"strategic days"),in.readDouble(),in.readDouble(),in.readDouble(),in.readDouble()));
        }catch(IllegalArgumentException ex){throw new IOException("invalid simulation config",ex);}
    }

    private static void readV11Social(DataInputStream in,SimulationState state)throws IOException{
        int count=checkedCount(in.readInt(),100000,"social citizens");
        for(int i=0;i<count;i++){
            long id=in.readLong(),faction=in.readLong(),settlement=in.readLong();int slot=checkedCount(in.readInt(),100000,"citizen projection slot");String name=readString(in);int skin=checkedCount(in.readInt(),47,"citizen skin");CitizenRole role=CitizenRole.values()[enumOrdinal(in.readInt(),CitizenRole.values().length,"citizen role")];long birthDay=in.readLong();double health=in.readDouble(),money=in.readDouble();boolean alive=in.readBoolean();
            CitizenNeeds needs=new CitizenNeeds();needs.restore(in.readDouble(),in.readDouble(),in.readDouble(),in.readDouble(),in.readDouble());CitizenPersonality personality=new CitizenPersonality(in.readDouble(),in.readDouble(),in.readDouble(),in.readDouble(),in.readDouble(),in.readDouble());
            SocialCitizen citizen=new SocialCitizen(id,faction,settlement,slot,name,skin,role,birthDay,personality);citizen.restoreCore(role,health,money,alive);citizen.needs().restore(needs.hunger(),needs.safety(),needs.social(),needs.status(),needs.comfort());
            int memories=checkedCount(in.readInt(),SocialCitizen.MAX_MEMORIES,"citizen memories");for(int j=0;j<memories;j++){CitizenMemory memory=new CitizenMemory(in.readLong(),MemoryType.values()[enumOrdinal(in.readInt(),MemoryType.values().length,"memory type")],readString(in),readString(in),readString(in),readPosition(in),in.readDouble(),in.readDouble());citizen.restoreMemory(memory);}
            int relationships=checkedCount(in.readInt(),SocialCitizen.MAX_RELATIONSHIPS,"citizen relationships");for(int j=0;j<relationships;j++){CitizenRelationship rel=new CitizenRelationship(readString(in));rel.restore(in.readDouble(),in.readDouble(),in.readDouble(),in.readDouble(),in.readDouble(),FamilyBond.values()[enumOrdinal(in.readInt(),FamilyBond.values().length,"family bond")]);citizen.restoreRelationship(rel);}
            state.addSocialCitizen(citizen);
        }
    }

    private static void readV12Civilization(DataInputStream in,SimulationState state)throws IOException{
        int n=checkedCount(in.readInt(),100000,"settlement civilization profiles");for(int i=0;i<n;i++){SettlementCivilizationState c=new SettlementCivilizationState(in.readLong(),in.readLong());c.restore(c.heritageFactionId(),in.readDouble(),in.readDouble(),in.readDouble(),in.readDouble(),in.readDouble(),in.readDouble(),in.readDouble(),in.readDouble(),in.readDouble(),in.readLong(),in.readLong());state.restoreSettlementCivilization(c);}
        n=checkedCount(in.readInt(),10000,"faction civilization profiles");for(int i=0;i<n;i++){long factionId=in.readLong();FactionCivilizationState c=new FactionCivilizationState(factionId,readString(in),readString(in),readString(in));double culture=in.readDouble(),religion=in.readDouble(),education=in.readDouble(),propaganda=in.readDouble(),intelligence=in.readDouble();long overlord=in.readLong();double tribute=in.readDouble();int sc=checkedCount(in.readInt(),FactionCivilizationState.MAX_SPY_NETWORKS,"spy networks");Map<Long,Double> spies=new LinkedHashMap<>();for(int j=0;j<sc;j++)spies.put(in.readLong(),in.readDouble());try{c.restore(culture,religion,education,propaganda,intelligence,overlord,tribute,spies);}catch(IllegalArgumentException bad){throw new IOException("invalid faction civilization profile",bad);}state.restoreFactionCivilization(c);}
        n=checkedCount(in.readInt(),SimulationState.MAX_RESOURCE_CLAIMS,"resource claims");for(int i=0;i<n;i++){ResourceClaim c=new ResourceClaim(in.readLong(),in.readLong(),in.readLong(),ResourceClaimType.values()[enumOrdinal(in.readInt(),ResourceClaimType.values().length,"resource claim type")],readPosition(in),in.readDouble());c.restore(c.strength(),in.readLong(),in.readBoolean());state.addResourceClaim(c);}
        n=checkedCount(in.readInt(),SimulationState.MAX_RAIDS,"raids");for(int i=0;i<n;i++){long id=in.readLong(),attacker=in.readLong(),origin=in.readLong(),target=in.readLong(),created=in.readLong();int manpower=checkedCount(in.readInt(),100000,"raid manpower");double morale=in.readDouble(),progress=in.readDouble();boolean active=in.readBoolean(),bandit=in.readBoolean();RaidParty r=new RaidParty(id,attacker,origin,target,created,Math.max(1,manpower),morale,bandit);r.restore(manpower,morale,progress,active);state.addRaid(r);}
        n=checkedCount(in.readInt(),SimulationState.MAX_LEGENDS,"legends");for(int i=0;i<n;i++){LegendRecord l=new LegendRecord(in.readLong(),in.readLong(),in.readLong(),in.readLong(),readString(in),readString(in),readString(in),in.readDouble());boolean monumented=in.readBoolean();if(monumented)l.markMonumented();state.addLegend(l);}
    }


    private static void readV13Humanity(DataInputStream in,SimulationState state,int version)throws IOException{
        int n=checkedCount(in.readInt(),100000,"citizen humanity links");for(int i=0;i<n;i++){long citizenId=in.readLong(),householdId=in.readLong();double skill=in.readDouble();SocialCitizen c=state.findSocialCitizen(citizenId).orElseThrow(()->new IOException("unknown citizen in schema13 humanity link: "+citizenId));c.restoreHumanity(skill,householdId);}
        n=checkedCount(in.readInt(),100000,"settlement civilization extensions");for(int i=0;i<n;i++){long settlementId=in.readLong();SettlementCivilizationState c=state.findSettlementCivilization(settlementId).orElseThrow(()->new IOException("unknown settlement civilization extension: "+settlementId));double quarantine=in.readDouble();EnumMap<KnowledgeDomain,Double> knowledge=new EnumMap<>(KnowledgeDomain.class);for(KnowledgeDomain d:KnowledgeDomain.values())knowledge.put(d,in.readDouble());c.restoreExtended(quarantine,knowledge);}
        n=checkedCount(in.readInt(),10000,"faction civilization extensions");for(int i=0;i<n;i++){long factionId=in.readLong();FactionCivilizationState c=state.findFactionCivilization(factionId).orElseThrow(()->new IOException("unknown faction civilization extension: "+factionId));c.restoreExtended(in.readDouble(),in.readDouble(),in.readDouble(),in.readDouble(),in.readDouble(),in.readDouble(),in.readDouble(),in.readDouble(),in.readDouble());}
        n=checkedCount(in.readInt(),SimulationState.MAX_HOUSEHOLDS,"households");for(int i=0;i<n;i++){long id=in.readLong(),faction=in.readLong(),settlement=in.readLong(),founded=in.readLong();boolean active=in.readBoolean();double wealth=in.readDouble();String home=readString(in);int mc=checkedCount(in.readInt(),HouseholdState.MAX_NAMED_MEMBERS,"household members");List<Long> members=new ArrayList<>();for(int j=0;j<mc;j++)members.add(in.readLong());int cc=checkedCount(in.readInt(),HouseholdState.MAX_CHILDREN,"household children");List<DependentChild> children=new ArrayList<>();for(int j=0;j<cc;j++){DependentChild child=new DependentChild(in.readLong(),in.readLong(),in.readLong(),in.readLong());child.restoreAdoptiveParents(in.readLong(),in.readLong());children.add(child);}HouseholdState h=new HouseholdState(id,faction,settlement,founded);h.restore(members,children,wealth,home,active);state.addHousehold(h);}
        n=checkedCount(in.readInt(),SimulationState.MAX_EPIDEMICS,"epidemics");for(int i=0;i<n;i++){long id=in.readLong(),settlement=in.readLong(),start=in.readLong();String disease=readString(in);double severity=in.readDouble(),infected=in.readDouble();int deaths=checkedCount(in.readInt(),100000000,"epidemic deaths");long last=in.readLong();boolean active=in.readBoolean();EpidemicRecord e=new EpidemicRecord(id,settlement,start,disease,severity,infected);e.restore(severity,infected,deaths,last,active);state.addEpidemic(e);}
        n=checkedCount(in.readInt(),SimulationState.MAX_MIGRATIONS,"migration groups");for(int i=0;i<n;i++){long id=in.readLong(),origin=in.readLong(),source=in.readLong(),target=in.readLong(),camp=in.readLong(),created=in.readLong();int people=checkedCount(in.readInt(),1000000,"migration people");MigrationReason reason=MigrationReason.values()[enumOrdinal(in.readInt(),MigrationReason.values().length,"migration reason")];MigrationStatus status=MigrationStatus.values()[enumOrdinal(in.readInt(),MigrationStatus.values().length,"migration status")];double progress=in.readDouble(),food=in.readDouble(),health=in.readDouble();int hc=checkedCount(in.readInt(),8,"migration households");List<Long> households=new ArrayList<>();for(int j=0;j<hc;j++)households.add(in.readLong());MigrationGroup g=new MigrationGroup(id,origin,source,target,created,Math.max(1,people),reason);g.restore(target,camp,people,status,progress,food,health,households);state.addMigrationGroup(g);}
        n=checkedCount(in.readInt(),SimulationState.MAX_JUSTICE_CASES,"justice cases");for(int i=0;i<n;i++){long id=in.readLong(),faction=in.readLong(),settlement=in.readLong(),opened=in.readLong();String accused=readString(in);CrimeType crime=CrimeType.values()[enumOrdinal(in.readInt(),CrimeType.values().length,"justice crime")];JusticeStatus status=JusticeStatus.values()[enumOrdinal(in.readInt(),JusticeStatus.values().length,"justice status")];SentenceType sentence=SentenceType.values()[enumOrdinal(in.readInt(),SentenceType.values().length,"sentence")];double fine=in.readDouble();long release=in.readLong();JusticeCase j=new JusticeCase(id,faction,settlement,opened,accused,crime);j.restore(status,sentence,fine,release);state.addJusticeCase(j);}
        n=checkedCount(in.readInt(),SimulationState.MAX_HIDDEN_CACHES,"hidden caches");for(int i=0;i<n;i++){long id=in.readLong(),faction=in.readLong(),settlement=in.readLong(),created=in.readLong();SimPosition pos=readPosition(in);EnumMap<ResourceType,Double> goods=new EnumMap<>(ResourceType.class);for(int ri=0;ri<resourceCount(version);ri++)goods.put(ResourceType.values()[ri],in.readDouble());long discoveredByFaction=in.readLong();boolean recovered=in.readBoolean();HiddenCache c=new HiddenCache(id,faction,settlement,created,pos,goods);c.restore(discoveredByFaction,recovered);state.addHiddenCache(c);}
        n=checkedCount(in.readInt(),SimulationState.MAX_PIRATE_BANDS,"pirate bands");for(int i=0;i<n;i++){long id=in.readLong(),origin=in.readLong(),created=in.readLong();SimPosition pos=readPosition(in);int strength=checkedCount(in.readInt(),100000,"pirate strength");double morale=in.readDouble(),loot=in.readDouble();boolean active=in.readBoolean();PirateBand b=new PirateBand(id,origin,created,pos,Math.max(1,strength));b.restore(pos,strength,morale,loot,active);state.addPirateBand(b);}
        n=checkedCount(in.readInt(),SimulationState.MAX_DIPLOMATIC_MARRIAGES,"diplomatic marriages");for(int i=0;i<n;i++){DiplomaticMarriage m=new DiplomaticMarriage(in.readLong(),in.readLong(),in.readLong(),in.readLong(),in.readLong(),in.readLong());m.restoreActive(in.readBoolean());state.addDiplomaticMarriage(m);}
        n=checkedCount(in.readInt(),10000,"dynasties");for(int i=0;i<n;i++){long faction=in.readLong(),founded=in.readLong();String house=readString(in);long ruler=in.readLong(),heir=in.readLong(),regent=in.readLong();double prestige=in.readDouble();boolean crisis=in.readBoolean();long since=in.readLong();int generation=checkedCount(in.readInt(),100000,"dynasty generation");long lastSuccession=in.readLong();DynastyState d=new DynastyState(faction,founded,house);d.restore(ruler,heir,regent,prestige,crisis,since,Math.max(1,generation),lastSuccession);state.restoreDynasty(d);}
        n=checkedCount(in.readInt(),SimulationState.MAX_CIVIC_EVENTS,"civic events");for(int i=0;i<n;i++){long id=in.readLong(),faction=in.readLong(),settlement=in.readLong(),start=in.readLong(),end=in.readLong();CivicEventType type=CivicEventType.values()[enumOrdinal(in.readInt(),CivicEventType.values().length,"civic event type")];String title=readString(in);double attendance=in.readDouble(),intensity=in.readDouble();boolean active=in.readBoolean();CivicEvent e=new CivicEvent(id,faction,settlement,start,end,type,title,intensity);e.restore(attendance,intensity,active);state.addCivicEvent(e);}
        n=checkedCount(in.readInt(),SimulationState.MAX_INTELLIGENCE_OPERATIONS,"intelligence operations");for(int i=0;i<n;i++){long id=in.readLong(),source=in.readLong(),target=in.readLong(),start=in.readLong(),agent=in.readLong();IntelligenceOperationType type=IntelligenceOperationType.values()[enumOrdinal(in.readInt(),IntelligenceOperationType.values().length,"intelligence operation type")];double progress=in.readDouble(),secrecy=in.readDouble(),quality=in.readDouble();boolean discovered=in.readBoolean(),active=in.readBoolean();IntelligenceOperation o=new IntelligenceOperation(id,source,target,start,agent,type);o.restore(progress,secrecy,quality,discovered,active);state.addIntelligenceOperation(o);}
        n=checkedCount(in.readInt(),SimulationState.MAX_PROPAGANDA_CAMPAIGNS,"propaganda campaigns");for(int i=0;i<n;i++){long id=in.readLong(),faction=in.readLong(),settlement=in.readLong(),start=in.readLong(),end=in.readLong();PropagandaTheme theme=PropagandaTheme.values()[enumOrdinal(in.readInt(),PropagandaTheme.values().length,"propaganda theme")];double intensity=in.readDouble(),reach=in.readDouble();boolean active=in.readBoolean();PropagandaCampaign c=new PropagandaCampaign(id,faction,settlement,start,end,theme,intensity);c.restore(intensity,reach,active);state.addPropagandaCampaign(c);}
        n=checkedCount(in.readInt(),SimulationState.MAX_RUIN_SITES,"ruin sites");for(int i=0;i<n;i++){long id=in.readLong(),settlement=in.readLong(),faction=in.readLong(),created=in.readLong();SimPosition position=readPosition(in);String name=readString(in),cause=readString(in);double preservation=in.readDouble();boolean looted=in.readBoolean(),reclaimed=in.readBoolean();RuinSite r=new RuinSite(id,settlement,faction,created,position,name,cause);r.restore(preservation,looted,reclaimed);state.addRuinSite(r);}
        n=checkedCount(in.readInt(),SimulationState.MAX_ASSISTANCE_TASKS,"assistance tasks");for(int i=0;i<n;i++){long id=in.readLong(),faction=in.readLong(),settlement=in.readLong(),created=in.readLong(),expires=in.readLong();AssistanceTaskType type=AssistanceTaskType.values()[enumOrdinal(in.readInt(),AssistanceTaskType.values().length,"assistance task type")];String cause=readString(in);double initial=in.readDouble(),remaining=in.readDouble();AssistanceTaskStatus status=AssistanceTaskStatus.values()[enumOrdinal(in.readInt(),AssistanceTaskStatus.values().length,"assistance task status")];AssistanceTask task=new AssistanceTask(id,faction,settlement,created,expires,type,cause,initial);task.restore(remaining,status);state.addAssistanceTask(task);}
    }

    private static void readV14PirateHideouts(DataInputStream in,SimulationState state)throws IOException{
        int n=checkedCount(in.readInt(),SimulationState.MAX_PIRATE_HIDEOUTS,"pirate hideouts");for(int i=0;i<n;i++){long id=in.readLong(),band=in.readLong(),origin=in.readLong(),created=in.readLong();SimPosition pos=readPosition(in);double defense=in.readDouble(),loot=in.readDouble();long discovered=in.readLong();boolean active=in.readBoolean(),destroyed=in.readBoolean();PirateHideout h=new PirateHideout(id,band,origin,created,pos);h.restore(defense,loot,discovered,active,destroyed);state.addPirateHideout(h);}
    }

    private static void readV15SiegeEquipment(DataInputStream in,SimulationState state)throws IOException{
        int n=checkedCount(in.readInt(),100000,"siege equipment");for(int i=0;i<n;i++){long id=in.readLong();int rams=checkedCount(in.readInt(),10000,"siege rams"),ladders=checkedCount(in.readInt(),100000,"siege ladders"),artillery=checkedCount(in.readInt(),10000,"siege artillery");double breach=in.readDouble(),counter=in.readDouble();SiegeState siege=state.sieges().stream().filter(s->s.id()==id).findFirst().orElseThrow(()->new IOException("siege equipment references missing siege "+id));siege.restoreEquipment(rams,ladders,artillery,breach,counter);}
    }

    private static void readV16SettlementEconomy(DataInputStream in,SimulationState state,int version)throws IOException{
        int n=checkedCount(in.readInt(),100000,"settlement economy");
        for(int i=0;i<n;i++){
            long id=in.readLong();
            double barn=in.readDouble(),granary=in.readDouble();
            EnumMap<ResourceType,Double> stores=new EnumMap<>(ResourceType.class);
            for(int ri=0;ri<resourceCount(version);ri++)stores.put(ResourceType.values()[ri],in.readDouble());
            Settlement settlement=state.findSettlement(id).orElseThrow(()->new IOException("settlement economy references missing settlement "+id));
            try{settlement.restoreEconomy(barn,granary,stores);}catch(IllegalArgumentException bad){throw new IOException("invalid settlement economy for "+id,bad);}
        }
    }

    /** Pre-schema-16 worlds already receive starter stores from Settlement construction; refresh barn/granary from structures. */
    private static void migratePreV16SettlementEconomy(SimulationState state){
        for(Faction f:state.factions())for(Settlement s:f.settlements()){s.refreshStorageCapacity();s.enforceStorageCaps();}
    }

    private static void readV17FinalProduct(DataInputStream in,SimulationState state)throws IOException{
        int n=checkedCount(in.readInt(),100000,"citizen appearance/social");
        for(int i=0;i<n;i++){
            long id=in.readLong();long packed=in.readLong();String culture=readString(in),faith=readString(in);
            SocialClass socialClass=SocialClass.values()[enumOrdinal(in.readInt(),SocialClass.values().length,"social class")];
            double education=in.readDouble();String workplace=readString(in);
            SocialClass wealth=SocialClass.values()[enumOrdinal(in.readInt(),SocialClass.values().length,"wealth class")];
            double influence=in.readDouble();String historyKey=readString(in);
            EmploymentStatus employment=EmploymentStatus.values()[enumOrdinal(in.readInt(),EmploymentStatus.values().length,"employment")];
            SocialCitizen c=state.findSocialCitizen(id).orElseThrow(()->new IOException("unknown citizen in schema17 social: "+id));
            try{c.restoreSocialExtensions(culture,faith,socialClass,education,workplace,wealth,influence,packed,historyKey,employment);}catch(IllegalArgumentException bad){throw new IOException("invalid citizen social extensions "+id,bad);}
        }
        n=checkedCount(in.readInt(),1000000,"player influence/career");
        for(int i=0;i<n;i++){
            String actorKey=readString(in);PlayerStanding ps=state.playerStanding(actorKey);
            int fc=checkedCount(in.readInt(),100000,"influence factions");
            for(int j=0;j<fc;j++){long factionId=in.readLong();int ic=checkedCount(in.readInt(),InfluenceInstitution.values().length,"influence institutions");for(int k=0;k<ic;k++){InfluenceInstitution inst=InfluenceInstitution.values()[enumOrdinal(in.readInt(),InfluenceInstitution.values().length,"influence institution")];ps.restoreInfluence(factionId,inst,in.readDouble());}}
            CareerTrack track=CareerTrack.values()[enumOrdinal(in.readInt(),CareerTrack.values().length,"career track")];int rankIdx=checkedCount(in.readInt(),CareerRank.ranks(track).length-1,"career rank");double service=in.readDouble();
            try{ps.restoreCareer(track,rankIdx,service);}catch(IllegalArgumentException bad){throw new IOException("invalid career for "+actorKey,bad);}
        }
        n=checkedCount(in.readInt(),SimulationState.MAX_DEBTS,"sovereign debts");
        for(int i=0;i<n;i++){long id=in.readLong(),debtor=in.readLong();String creditor=readString(in);double principal=in.readDouble(),rate=in.readDouble(),remaining=in.readDouble();long created=in.readLong(),due=in.readLong();double risk=in.readDouble();boolean defaulted=in.readBoolean(),active=in.readBoolean();SovereignDebt debt=new SovereignDebt(id,debtor,creditor,principal,rate,created,due,risk);debt.restore(remaining,risk,defaulted,active);state.addDebt(debt);}
        n=checkedCount(in.readInt(),SimulationState.MAX_GRAND_PROJECTS,"grand projects");
        for(int i=0;i<n;i++){long id=in.readLong(),sponsor=in.readLong(),settlement=in.readLong();GrandProjectType type=GrandProjectType.values()[enumOrdinal(in.readInt(),GrandProjectType.values().length,"grand project type")];long created=in.readLong();double progress=in.readDouble();ConstructionPhase phase=ConstructionPhase.values()[enumOrdinal(in.readInt(),ConstructionPhase.values().length,"project phase")];String pause=readString(in);boolean complete=in.readBoolean(),active=in.readBoolean();double spentTreasury=in.readDouble(),spentFood=in.readDouble(),spentTimber=in.readDouble(),spentStone=in.readDouble(),spentIron=in.readDouble();int labor=checkedCount(in.readInt(),100000000,"project labor");boolean ei=in.readBoolean(),ep=in.readBoolean(),el=in.readBoolean(),elegend=in.readBoolean();GrandProject project=new GrandProject(id,sponsor,settlement,type,created);project.restore(progress,phase,pause,complete,active,spentTreasury,spentFood,spentTimber,spentStone,spentIron,labor,ei,ep,el,elegend);state.addGrandProject(project);}
        n=checkedCount(in.readInt(),SimulationState.MAX_CAMPAIGN_PLANS,"campaign plans");
        for(int i=0;i<n;i++){long id=in.readLong(),faction=in.readLong(),war=in.readLong();CampaignPlanType type=CampaignPlanType.values()[enumOrdinal(in.readInt(),CampaignPlanType.values().length,"campaign plan type")];long target=in.readLong();int priority=checkedCount(in.readInt(),1000000,"campaign priority");long created=in.readLong();boolean active=in.readBoolean();CampaignPlan plan=new CampaignPlan(id,faction,war,type,target,priority,created);plan.restore(priority,active);state.addCampaignPlan(plan);}
        n=checkedCount(in.readInt(),1000000,"shipment logistics");
        for(int i=0;i<n;i++){long id=in.readLong();long origin=in.readLong(),dest=in.readLong(),route=in.readLong();int mode=in.readInt();long dep=in.readLong(),eta=in.readLong();double risk=in.readDouble(),escort=in.readDouble();TradeShipment.LossState loss=TradeShipment.LossState.values()[enumOrdinal(in.readInt(),TradeShipment.LossState.values().length,"shipment loss")];int delay=checkedCount(in.readInt(),100000,"shipment delay");TradeShipment shipment=state.findShipment(id).orElseThrow(()->new IOException("shipment logistics missing shipment "+id));try{shipment.restoreLogistics(origin,dest,route,mode,dep,eta,risk,escort,loss,delay);}catch(IllegalArgumentException bad){throw new IOException("invalid shipment logistics "+id,bad);}
        }
    }

    private static void migratePreV17FinalProduct(SimulationState state){
        for(SocialCitizen c:state.socialCitizens()){
            int age=Math.max(1,c.ageYears(state.clock().day()));
            AppearanceProfile profile=AppearanceProfile.forCitizen(state.seed(),c.id(),c.role(),age,c.factionId());
            // Prefer deterministic rebuild from identity when packed appearance was constructor-default only.
            c.restoreAppearance(profile.pack());
            FactionCivilizationState civ=state.findFactionCivilization(c.factionId()).orElse(null);
            if(civ!=null){
                if(c.cultureKey()==null||c.cultureKey().isBlank())c.setCultureKey(civ.cultureName());
                if(c.faithKey()==null||c.faithKey().isBlank())c.setFaithKey(civ.faithName());
            }
        }
    }

    private static void writeV18GoodsAndOrigins(DataOutputStream out,SimulationState state)throws IOException{
        // Construction origins for every completed key (MATERIALIZED default for older saves).
        List<Settlement> settlements=new ArrayList<>();
        for(Faction f:state.factions())settlements.addAll(f.settlements());
        settlements.sort(Comparator.comparingLong(Settlement::id));
        out.writeInt(settlements.size());
        for(Settlement s:settlements){
            out.writeLong(s.id());
            var completed=new ArrayList<>(s.completedConstruction());
            Collections.sort(completed);
            out.writeInt(completed.size());
            for(String key:completed){
                writeString(out,key);
                out.writeInt(s.constructionOrigin(key).ordinal());
            }
        }
    }

    private static void readV18GoodsAndOrigins(DataInputStream in,SimulationState state)throws IOException{
        int n=checkedCount(in.readInt(),100000,"construction origins");
        for(int i=0;i<n;i++){
            long id=in.readLong();
            Settlement settlement=state.findSettlement(id).orElseThrow(()->new IOException("construction origins missing settlement "+id));
            int kc=checkedCount(in.readInt(),1000000,"origin keys");
            for(int k=0;k<kc;k++){
                String key=readString(in);
                ConstructionOrigin origin=ConstructionOrigin.values()[enumOrdinal(in.readInt(),ConstructionOrigin.values().length,"construction origin")];
                if(settlement.isConstructionCompleted(key))settlement.restoreConstructionOrigin(key,origin);
            }
        }
    }

    private static void migratePreV18Goods(SimulationState state){
        for(Faction f:state.factions()){
            migrateStockpileToGoods(f.stockpile());
            for(Settlement s:f.settlements()){
                migrateStockpileToGoods(s.stockpile());
                for(String key:s.completedConstruction())s.restoreConstructionOrigin(key,ConstructionOrigin.MATERIALIZED);
            }
        }
    }

    private static void migrateStockpileToGoods(Stockpile stockpile){
        double food=stockpile.get(ResourceType.FOOD);
        if(food<=0){
            // Already seeded as GRAIN/BREAD (fresh constructors) or empty — keep as-is.
            return;
        }
        // Clear constructor seed for post-legacy goods before mapping FOOD so values are not doubled.
        zeroNewGoods(stockpile);
        ResourceType.migrateLegacyFood(stockpile);
    }

    private static void writeV19ProvenanceAndSites(DataOutputStream out,SimulationState state)throws IOException{
        List<Settlement> settlements=new ArrayList<>();
        for(Faction f:state.factions())settlements.addAll(f.settlements());
        settlements.sort(Comparator.comparingLong(Settlement::id));
        out.writeInt(settlements.size());
        for(Settlement s:settlements){
            out.writeLong(s.id());
            out.writeInt(s.origin().ordinal());
            out.writeBoolean(s.physicallyAnchored());
            out.writeInt(s.developmentMode().ordinal());
        }
        List<OutlyingSite> sites=new ArrayList<>(state.outlyingSites());
        sites.sort(Comparator.comparingLong(OutlyingSite::id));
        out.writeInt(sites.size());
        for(OutlyingSite site:sites){
            out.writeLong(site.id());out.writeLong(site.settlementId());out.writeLong(site.factionId());
            out.writeInt(site.type().ordinal());writePosition(out,site.position());writeString(out,site.name());
            out.writeInt(site.representedPopulation());out.writeDouble(site.housingCredit());
            out.writeBoolean(site.foreign());out.writeBoolean(site.active());
        }
        List<CitizenJourney> journeys=new ArrayList<>(state.citizenJourneys());
        journeys.sort(Comparator.comparingLong(CitizenJourney::id));
        out.writeInt(journeys.size());
        for(CitizenJourney j:journeys){
            out.writeLong(j.id());out.writeLong(j.citizenId());out.writeLong(j.factionId());
            out.writeLong(j.originSettlementId());out.writeLong(j.targetSettlementId());out.writeLong(j.routeId());
            out.writeInt(j.purpose().ordinal());out.writeLong(j.createdDay());out.writeDouble(j.progress());
            out.writeInt(j.status().ordinal());writeString(out,j.payload());
        }
        List<RoadsideSite> roadside=new ArrayList<>(state.roadsideSites());
        roadside.sort(Comparator.comparingLong(RoadsideSite::id));
        out.writeInt(roadside.size());
        for(RoadsideSite site:roadside){
            out.writeLong(site.id());out.writeInt(site.type().ordinal());writePosition(out,site.position());
            writeString(out,site.name());out.writeLong(site.relatedSettlementId());out.writeLong(site.relatedRouteId());
            out.writeLong(site.createdDay());out.writeInt(site.lifecycle().ordinal());out.writeBoolean(site.active());
        }
        List<UnderworldProfile> underworld=new ArrayList<>(state.underworldProfiles().values());
        underworld.sort(Comparator.comparing(UnderworldProfile::actorKey));
        out.writeInt(underworld.size());
        for(UnderworldProfile p:underworld){
            writeString(out,p.actorKey());
            out.writeInt(p.contractsCompleted());
            out.writeDouble(p.streetCred());
            out.writeDouble(p.briberySkill());
            out.writeLong(p.lastContractDay());
            out.writeLong(p.lastBribeDay());
            out.writeBoolean(p.blackMarketAccess());
        }
        List<RegisteredPlayerStructure> playerStructures=new ArrayList<>(state.registeredPlayerStructures());
        playerStructures.sort(Comparator.comparingLong(RegisteredPlayerStructure::id));
        out.writeInt(playerStructures.size());
        for(RegisteredPlayerStructure s:playerStructures){
            out.writeLong(s.id());out.writeLong(s.settlementId());writeString(out,s.ownerActorKey());
            out.writeInt(s.role().ordinal());
            out.writeInt(s.minX());out.writeInt(s.minY());out.writeInt(s.minZ());
            out.writeInt(s.maxX());out.writeInt(s.maxY());out.writeInt(s.maxZ());
            out.writeInt(s.doorX());out.writeInt(s.doorY());out.writeInt(s.doorZ());
            out.writeInt(s.capacity());out.writeLong(s.registrationDay());out.writeLong(s.fingerprint());
            out.writeBoolean(s.valid());out.writeLong(s.lastValidatedDay());
        }

    }

    private static void readV19ProvenanceAndSites(DataInputStream in,SimulationState state)throws IOException{
        int n=checkedCount(in.readInt(),100000,"settlement provenance");
        for(int i=0;i<n;i++){
            long id=in.readLong();
            SettlementOrigin origin=SettlementOrigin.values()[enumOrdinal(in.readInt(),SettlementOrigin.values().length,"settlement origin")];
            boolean anchored=in.readBoolean();
            DevelopmentMode mode=DevelopmentMode.values()[enumOrdinal(in.readInt(),DevelopmentMode.values().length,"development mode")];
            Settlement settlement=state.findSettlement(id).orElseThrow(()->new IOException("provenance missing settlement "+id));
            settlement.restoreProvenance(origin,anchored,mode);
        }
        n=checkedCount(in.readInt(),SimulationState.MAX_OUTLYING_SITES,"outlying sites");
        for(int i=0;i<n;i++){
            long id=in.readLong(),settlementId=in.readLong(),factionId=in.readLong();
            OutlyingSite.Type type=OutlyingSite.Type.values()[enumOrdinal(in.readInt(),OutlyingSite.Type.values().length,"outlying type")];
            SimPosition pos=readPosition(in);String name=readString(in);
            int pop=in.readInt();double housing=in.readDouble();boolean foreign=in.readBoolean();boolean active=in.readBoolean();
            OutlyingSite site=new OutlyingSite(id,settlementId,factionId,type,pos,name,pop,housing,foreign);
            site.restoreActive(active);
            state.addOutlyingSite(site);
        }
        n=checkedCount(in.readInt(),SimulationState.MAX_CITIZEN_JOURNEYS,"citizen journeys");
        for(int i=0;i<n;i++){
            long id=in.readLong(),citizenId=in.readLong(),factionId=in.readLong();
            long originId=in.readLong(),targetId=in.readLong(),routeId=in.readLong();
            CitizenJourney.Purpose purpose=CitizenJourney.Purpose.values()[enumOrdinal(in.readInt(),CitizenJourney.Purpose.values().length,"journey purpose")];
            long created=in.readLong();double progress=in.readDouble();
            CitizenJourney.Status status=CitizenJourney.Status.values()[enumOrdinal(in.readInt(),CitizenJourney.Status.values().length,"journey status")];
            String payload=readString(in);
            CitizenJourney journey=new CitizenJourney(id,citizenId,factionId,originId,targetId,routeId,purpose,created);
            journey.restore(progress,status,payload,routeId);
            state.addCitizenJourney(journey);
        }
        n=checkedCount(in.readInt(),SimulationState.MAX_ROADSIDE_SITES,"roadside sites");
        for(int i=0;i<n;i++){
            long id=in.readLong();
            RoadsideSite.Type type=RoadsideSite.Type.values()[enumOrdinal(in.readInt(),RoadsideSite.Type.values().length,"roadside type")];
            SimPosition pos=readPosition(in);String name=readString(in);
            long relatedSettlement=in.readLong(),relatedRoute=in.readLong(),created=in.readLong();
            RoadsideSite.Lifecycle lifecycle=RoadsideSite.Lifecycle.values()[enumOrdinal(in.readInt(),RoadsideSite.Lifecycle.values().length,"roadside lifecycle")];
            boolean active=in.readBoolean();
            RoadsideSite site=new RoadsideSite(id,type,pos,name,relatedSettlement,relatedRoute,created);
            site.restore(lifecycle,active);
            state.addRoadsideSite(site);
        }
        n=checkedCount(in.readInt(),SimulationState.MAX_UNDERWORLD_PROFILES,"underworld profiles");
        for(int i=0;i<n;i++){
            String actor=readString(in);
            UnderworldProfile profile=new UnderworldProfile(actor);
            profile.restore(in.readInt(),in.readDouble(),in.readDouble(),in.readLong(),in.readLong(),in.readBoolean());
            state.restoreUnderworldProfile(profile);
        }
        n=checkedCount(in.readInt(),SimulationState.MAX_REGISTERED_PLAYER_STRUCTURES,"registered player structures");
        for(int i=0;i<n;i++){
            long id=in.readLong(),settlementId=in.readLong();String owner=readString(in);
            RegisteredPlayerStructure.Role role=RegisteredPlayerStructure.Role.values()[enumOrdinal(in.readInt(),RegisteredPlayerStructure.Role.values().length,"player structure role")];
            int minX=in.readInt(),minY=in.readInt(),minZ=in.readInt(),maxX=in.readInt(),maxY=in.readInt(),maxZ=in.readInt();
            int doorX=in.readInt(),doorY=in.readInt(),doorZ=in.readInt();
            int capacity=in.readInt();long regDay=in.readLong();long fingerprint=in.readLong();
            boolean valid=in.readBoolean();long lastVal=in.readLong();
            RegisteredPlayerStructure s=new RegisteredPlayerStructure(id,settlementId,owner,role,minX,minY,minZ,maxX,maxY,maxZ,doorX,doorY,doorZ,capacity,regDay,fingerprint);
            s.restore(valid,capacity,fingerprint,lastVal,role);
            state.addRegisteredPlayerStructure(s);
        }

    }

    private static void writeV20UnderworldContracts(DataOutputStream out,SimulationState state)throws IOException{
        List<UnderworldContract> contracts=new ArrayList<>(state.underworldContracts());
        contracts.sort(Comparator.comparingLong(UnderworldContract::id));
        out.writeInt(contracts.size());
        for(UnderworldContract c:contracts){
            out.writeLong(c.id());
            out.writeInt(c.type().ordinal());
            out.writeInt(c.status().ordinal());
            out.writeLong(c.jurisdictionFactionId());
            writeString(out,c.targetVictimKey());
            out.writeDouble(c.minValue());
            out.writeDouble(c.reward());
            out.writeLong(c.createdDay());
            out.writeLong(c.expiresDay());
            writeString(out,c.acceptorActorKey());
            out.writeLong(c.acceptedDay());
            out.writeLong(c.closedDay());
            out.writeLong(c.matchingCrimeId());
        }
        List<StolenGoodsEntry> goods=new ArrayList<>(state.stolenGoodsLedger().entries());
        goods.sort(Comparator.comparingLong(StolenGoodsEntry::id));
        out.writeInt(goods.size());
        for(StolenGoodsEntry e:goods){
            out.writeLong(e.id());
            writeString(out,e.actorKey());
            writeString(out,e.goodKey());
            out.writeDouble(e.value());
            out.writeInt(e.quantity());
            out.writeLong(e.sourceCrimeId());
            out.writeLong(e.acquiredDay());
            out.writeBoolean(e.sold());
            out.writeLong(e.soldDay());
            out.writeDouble(e.salePrice());
        }
    }

    private static void readV20UnderworldContracts(DataInputStream in,SimulationState state)throws IOException{
        int n=checkedCount(in.readInt(),SimulationState.MAX_UNDERWORLD_CONTRACTS,"underworld contracts");
        for(int i=0;i<n;i++){
            long id=in.readLong();
            UnderworldContractType type=UnderworldContractType.values()[enumOrdinal(in.readInt(),UnderworldContractType.values().length,"underworld contract type")];
            UnderworldContract.Status status=UnderworldContract.Status.values()[enumOrdinal(in.readInt(),UnderworldContract.Status.values().length,"underworld contract status")];
            long jurisdiction=in.readLong();
            String target=readString(in);
            double minValue=in.readDouble();
            double reward=in.readDouble();
            long created=in.readLong();
            long expires=in.readLong();
            String acceptor=readString(in);
            long acceptedDay=in.readLong();
            long closedDay=in.readLong();
            long matchingCrime=in.readLong();
            UnderworldContract contract=new UnderworldContract(id,type,jurisdiction,target,minValue,reward,created,expires);
            contract.restore(status,acceptor,acceptedDay,closedDay,matchingCrime);
            state.addUnderworldContract(contract);
        }
        n=checkedCount(in.readInt(),StolenGoodsLedger.DEFAULT_MAX_ENTRIES,"stolen goods");
        for(int i=0;i<n;i++){
            long id=in.readLong();
            String actor=readString(in);
            String goodKey=readString(in);
            double value=in.readDouble();
            int quantity=in.readInt();
            long sourceCrime=in.readLong();
            long acquired=in.readLong();
            boolean sold=in.readBoolean();
            long soldDay=in.readLong();
            double salePrice=in.readDouble();
            StolenGoodsEntry entry=new StolenGoodsEntry(id,actor,goodKey,value,quantity,sourceCrime,acquired);
            entry.restore(sold,soldDay,salePrice);
            state.stolenGoodsLedger().restore(entry);
        }
    }

    /** Schema ≤18 settlements become LEGACY + physicallyAnchored — never assume they lack world geometry. */
    private static void migratePreV19Provenance(SimulationState state){
        for(Faction f:state.factions()){
            boolean wizard=f.name().equals("Wizard Trees");
            for(Settlement s:f.settlements()){
                if(wizard)s.restoreProvenance(SettlementOrigin.WIZARD_TREES,true,DevelopmentMode.AUTO);
                else s.restoreProvenance(SettlementOrigin.LEGACY,true,DevelopmentMode.AUTO);
            }
        }
    }

    private static void zeroNewGoods(Stockpile stockpile){
        for(int i=ResourceType.LEGACY_COUNT;i<ResourceType.values().length;i++)stockpile.set(ResourceType.values()[i],0);
    }

    private static int resourceCount(int version){
        return version>=18?ResourceType.values().length:ResourceType.LEGACY_COUNT;
    }

    private static void readCrimeLedger(DataInputStream in,CrimeLedger ledger)throws IOException{
        int n=checkedCount(in.readInt(),1000000,"wanted profiles");for(int i=0;i<n;i++){String actor=readString(in);WantedProfile p=ledger.profile(actor);p.restoreInfamy(in.readDouble());int jc=checkedCount(in.readInt(),100000,"wanted jurisdictions");for(int j=0;j<jc;j++){long faction=in.readLong();JurisdictionWanted w=p.in(faction);w.restore(in.readDouble(),in.readDouble(),in.readDouble(),in.readLong(),in.readInt(),in.readInt(),in.readInt());}}
        n=checkedCount(in.readInt(),20000,"crime incidents");for(int i=0;i<n;i++){CrimeIncident c=new CrimeIncident(in.readLong(),in.readLong(),readString(in),in.readLong(),CrimeType.values()[enumOrdinal(in.readInt(),CrimeType.values().length,"crime type")],in.readDouble(),readPosition(in),in.readBoolean(),in.readInt(),readString(in),readString(in));ledger.restoreIncident(c);}
    }

    private static void readHistory(DataInputStream in,SimulationState state)throws IOException{int hc=checkedCount(in.readInt(),20000,"history");for(int i=0;i<hc;i++)state.history().add(new WorldEvent(in.readLong(),readString(in),readString(in)));}

    private static void writePosition(DataOutput out,SimPosition p)throws IOException{out.writeDouble(p.x());out.writeDouble(p.z());} private static SimPosition readPosition(DataInput in)throws IOException{return new SimPosition(in.readDouble(),in.readDouble());}
    private static int enumOrdinal(int v,int length,String name)throws IOException{if(v<0||v>=length)throw new IOException("bad "+name+" ordinal: "+v);return v;}
    private static int checkedCount(int v,int max,String name)throws IOException{if(v<0||v>max)throw new IOException("Invalid "+name+" count: "+v);return v;}
    private static void writeString(DataOutput out,String s)throws IOException{
        byte[] b=Objects.requireNonNullElse(s,"").getBytes(StandardCharsets.UTF_8);
        if(b.length>MAX_STRING_BYTES)throw new IOException("string too long: "+b.length);
        out.writeInt(b.length);out.write(b);
    }
    private static String readString(DataInput in)throws IOException{
        int n=checkedCount(in.readInt(),MAX_STRING_BYTES,"string bytes");byte[] b=new byte[n];in.readFully(b);
        try{return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(b)).toString();}
        catch(CharacterCodingException malformed){throw new IOException("Malformed UTF-8 in Living Realms save",malformed);}
    }
}
