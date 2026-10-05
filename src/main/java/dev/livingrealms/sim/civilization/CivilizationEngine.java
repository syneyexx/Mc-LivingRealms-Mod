package dev.livingrealms.sim.civilization;

import dev.livingrealms.sim.civilian.CitizenRole;
import dev.livingrealms.sim.faction.*;
import dev.livingrealms.sim.social.*;
import dev.livingrealms.sim.util.*;
import dev.livingrealms.sim.world.*;
import java.util.*;

/**
 * Additive society simulation. It consumes and modifies the existing canonical population,
 * economy, government, diplomacy, armies and history rather than creating parallel truth.
 */
public final class CivilizationEngine {
    private static final int MAX_ACTIVE_RAIDS=128;
    private final CivilizationLifecycleEngine lifecycleEngine=new CivilizationLifecycleEngine();

    public void simulateDay(SimulationState state,DeterministicRng rng){
        Objects.requireNonNull(state);Objects.requireNonNull(rng);ensureProfilesAndClaims(state);updateSettlements(state);
        DemographyEngine.simulateSettlementAttraction(state);DemographyEngine.updateNamedPeople(state,rng);updateMilitaryAndBanditry(state,rng);advanceRaids(state);
        BanditEconomyEngine.spawnFromPressures(state,rng);
        BanditEconomyEngine.extortRoutes(state,rng);
        long day=state.clock().day();
        if(day%7==0)lifecycleEngine.considerMigration(state);
        if(day%30==0){DemographyEngine.simulateMonthlyDemography(state);simulateFamilies(state);simulateClaims(state);simulateInformationAndTribute(state);promoteLegends(state);BanditEconomyEngine.promoteOutlawLegends(state);}
        lifecycleEngine.simulateDay(state,rng);
    }

    private static void ensureProfilesAndClaims(SimulationState state){
        Map<ClaimKey,ResourceClaim> activeClaims=new HashMap<>(Math.max(16,state.resourceClaims().size()*2));
        for(ResourceClaim claim:state.resourceClaims())if(claim.active())activeClaims.put(new ClaimKey(claim.settlementId(),claim.type()),claim);
        Set<Long> portSettlements=new HashSet<>();for(var port:state.ports())portSettlements.add(port.settlementId());
        for(Faction faction:state.factions()){
            state.ensureFactionCivilization(faction.id());
            for(Settlement settlement:faction.settlements()){
                state.ensureSettlementCivilization(settlement.id(),faction.id());
                ensureClaim(state,activeClaims,faction,settlement,ResourceClaimType.WATER,true);
                ensureClaim(state,activeClaims,faction,settlement,ResourceClaimType.FARMLAND,has(settlement,"farm:"));
                ensureClaim(state,activeClaims,faction,settlement,ResourceClaimType.TIMBER,has(settlement,"lumber_camp:"));
                ensureClaim(state,activeClaims,faction,settlement,ResourceClaimType.MINE,has(settlement,"mine:"));
                ensureClaim(state,activeClaims,faction,settlement,ResourceClaimType.FISHERY,has(settlement,"fishery:")||portSettlements.contains(settlement.id()));
            }
        }
    }

    private static void ensureClaim(SimulationState state,Map<ClaimKey,ResourceClaim> activeClaims,Faction faction,Settlement settlement,ResourceClaimType type,boolean wanted){
        ClaimKey key=new ClaimKey(settlement.id(),type);ResourceClaim existing=activeClaims.get(key);
        if(!wanted){if(existing!=null){existing.deactivate();activeClaims.remove(key);}return;}
        if(existing!=null){if(existing.factionId()!=faction.id())existing.setFactionId(faction.id());return;}
        double angle=Math.toRadians(Math.floorMod(Objects.hash(settlement.id(),type.ordinal()),360));double radius=80+Math.floorMod(settlement.id()+type.ordinal()*41L,120L);
        SimPosition p=new SimPosition(settlement.position().x()+Math.cos(angle)*radius,settlement.position().z()+Math.sin(angle)*radius);
        ResourceClaim created=new ResourceClaim(state.nextId(),faction.id(),settlement.id(),type,p,.45+.08*Math.min(5,settlement.infrastructure()));state.addResourceClaim(created);activeClaims.put(key,created);
    }

    private record ClaimKey(long settlementId,ResourceClaimType type) {}

    private static void updateSettlements(SimulationState state){
        long day=state.clock().day();Map<Long,Integer> contestedBySettlement=new HashMap<>();
        for(ResourceClaim claim:state.resourceClaims())if(claim.active()&&claim.contestedByFactionId()>0)contestedBySettlement.merge(claim.settlementId(),1,Integer::sum);
        for(Faction owner:state.factions())for(Settlement s:owner.settlements()){
            SettlementCivilizationState c=state.ensureSettlementCivilization(s.id(),owner.id());
            double crowd=Mathx.clamp((s.population()-Math.max(1,s.housing()))/(double)Math.max(20,s.population()),0,1);
            boolean well=has(s,"well:"),irrigation=has(s,"irrigation:"),aqueduct=has(s,"aqueduct:"),clinic=has(s,"clinic:"),school=has(s,"school:"),temple=has(s,"temple:"),tavern=has(s,"tavern:");
            double water=Mathx.clamp(.32+s.infrastructure()*.10+(well?.30:0)+(irrigation?.16:0)+(aqueduct?.30:0)+(state.ports().stream().anyMatch(p->p.settlementId()==s.id())?.04:0),0,1);
            double sanitation=Mathx.clamp(.28+water*.30+s.infrastructure()*.14+(clinic?.16:0)+(aqueduct?.10:0)-crowd*.30,0,1);
            double education=Mathx.clamp(.12+owner.technology()*.12+(school?.38:0)+specialistShare(state,s.id(),CitizenRole.SCHOLAR)*.55,0,1);
            double disease=Mathx.clamp(.025+crowd*.34+(1-water)*.22+(1-s.foodSecurity())*.28+(1-sanitation)*.18-(clinic?.20:0),0,1);
            boolean warThreat=state.wars().stream().anyMatch(w->w.active()&&(w.attackerFactionId()==owner.id()||w.defenderFactionId()==owner.id())&&(w.targetSettlementId()==0||w.targetSettlementId()==s.id()));
            double refugees=Mathx.clamp((1-s.foodSecurity())*.30+(1-s.publicOrder())*.26+disease*.18+(warThreat?.32:0),0,1);
            double bandits=Mathx.clamp((1-s.publicOrder())*.34+s.unrest()*.30+(1-s.employment())*.18+(1-s.foodSecurity())*.16,0,1);
            double cohesion=Mathx.clamp(.42+s.prosperity()*.18+(temple?.12:0)+(tavern?.06:0)-s.unrest()*.30-refugees*.08,0,1);
            double assimilationTarget=c.heritageFactionId()==owner.id()?.03:Mathx.clamp(.20+owner.government().legitimacy()*.25+education*.18-s.unrest()*.18,0,1);
            int contested=contestedBySettlement.getOrDefault(s.id(),0);
            double resource=Mathx.clamp((owner.stockpile().get(ResourceType.FOOD)<Math.max(20,owner.population()*.03)?.25:0)+(owner.stockpile().get(ResourceType.IRON)<25?.15:0)+contested*.08,0,1);
            if(irrigation||aqueduct){double irrigationGain=(irrigation?.00035:0)+(aqueduct?.00025:0);s.setFoodSecurity(s.foodSecurity()+irrigationGain*(.4+c.knowledge(KnowledgeDomain.AGRICULTURE)));}
            c.approach(sanitation,disease,education,water,refugees,bandits,cohesion,assimilationTarget,resource,.08);
            if(c.heritageFactionId()!=owner.id()&&c.assimilation()>.94&&c.culturalCohesion()>.48){long old=c.heritageFactionId();c.setHeritageFactionId(owner.id());c.adjustAssimilation(-1);state.history().add(new WorldEvent(day,"cultural_assimilation","settlement="+s.id()+", fromFaction="+old+", toFaction="+owner.id()));}
        }
    }

    private static void simulateFamilies(SimulationState state){
        long day=state.clock().day();Map<Long,List<SocialCitizen>> bySettlement=new LinkedHashMap<>();for(SocialCitizen p:state.socialCitizens())if(p.alive()&&p.ageYears(day)>=18)bySettlement.computeIfAbsent(p.settlementId(),k->new ArrayList<>()).add(p);
        for(var entry:bySettlement.entrySet()){List<SocialCitizen> people=entry.getValue();people.sort(Comparator.comparingLong(SocialCitizen::id));for(int i=0;i+1<people.size();i+=2){SocialCitizen a=people.get(i),b=people.get(i+1);if(hasPartner(a)||hasPartner(b))continue;if(Math.floorMod(Objects.hash(a.id(),b.id(),day/30),5)!=0)continue;String ak="citizen:"+a.id(),bk="citizen:"+b.id();CitizenRelationship ar=a.relationship(bk),br=b.relationship(ak);ar.adjust(.18,0,.28,0,.18);br.adjust(.18,0,.28,0,.18);ar.setFamilyBond(FamilyBond.PARTNER);br.setFamilyBond(FamilyBond.PARTNER);SimPosition pos=state.findSettlement(entry.getKey()).orElseThrow().position();a.remember(new CitizenMemory(day,MemoryType.FAMILY_EVENT,bk,"self","I formed a household with "+b.name()+".",pos,.72,1));b.remember(new CitizenMemory(day,MemoryType.FAMILY_EVENT,ak,"self","I formed a household with "+a.name()+".",pos,.72,1));state.history().add(new WorldEvent(day,"household_formed","settlement="+entry.getKey()+", citizens="+a.id()+"/"+b.id()));}}
    }

    private static boolean hasPartner(SocialCitizen c){return c.relationships().values().stream().anyMatch(r->r.familyBond()==FamilyBond.PARTNER);}

    private static void updateMilitaryAndBanditry(SimulationState state,DeterministicRng rng){
        long day=state.clock().day();
        for(Faction faction:state.factions())for(Army army:new ArrayList<>(faction.armies())){
            double unrest=faction.settlements().stream().mapToDouble(Settlement::unrest).average().orElse(.1);double moraleDrift=(army.supply()-.55)*.0025+(faction.government().stability()-.5)*.0015-unrest*.0015;army.adjustMorale(moraleDrift);
            if(army.totalPersonnel()>12&&army.supply()<.24&&army.morale()<.34&&rng.chance(.003+.018*(.34-army.morale())+.012*(.24-army.supply()))){
                int deserters=Math.min(24,Math.max(1,(int)Math.round(army.totalPersonnel()*(.01+.025*(1-army.morale())))));
                army.desertPersonnel(deserters);
                Settlement nearest=faction.settlements().stream().min(Comparator.comparingDouble(s->s.position().distanceTo(army.position()))).orElse(null);
                if(nearest!=null){
                    state.ensureSettlementCivilization(nearest.id(),faction.id()).adjustBanditPressure(Math.min(.22,deserters/80.0));
                    // Deserters become a land pirate band with a wilderness hideout (reuse Pirate* authorities).
                    if(state.pirateBands().stream().filter(PirateBand::active).count()<SimulationState.MAX_PIRATE_BANDS
                            &&state.pirateHideouts().stream().filter(PirateHideout::active).count()<SimulationState.MAX_PIRATE_HIDEOUTS
                            &&rng.chance(.55)){
                        double ang=Math.toRadians(Math.floorMod(Long.hashCode(army.id()^day),360));
                        SimPosition camp=new SimPosition(army.position().x()+Math.cos(ang)*90,army.position().z()+Math.sin(ang)*90);
                        PirateBand band=new PirateBand(state.nextId(),nearest.id(),day,camp,Math.max(3,deserters));
                        state.addPirateBand(band);
                        PirateHideout hideout=new PirateHideout(state.nextId(),band.id(),nearest.id(),day,camp);
                        hideout.adjustDefense(.12);
                        state.addPirateHideout(hideout);
                        state.history().add(new WorldEvent(day,"deserter_band_formed","kind=deserters, faction="+faction.id()+", army="+army.id()+", band="+band.id()+", settlement="+nearest.id()+", deserters="+deserters));
                    }
                }
                state.history().add(new WorldEvent(day,"military_desertion","faction="+faction.id()+", army="+army.id()+", deserters="+deserters));
            }
        }
        if(day%7!=0||state.raids().stream().filter(RaidParty::active).count()>=MAX_ACTIVE_RAIDS)return;
        List<Settlement> allSettlements=new ArrayList<>();Map<Long,Faction> owners=new HashMap<>();
        for(Faction faction:state.factions())for(Settlement settlement:faction.settlements()){allSettlements.add(settlement);owners.put(settlement.id(),faction);}
        Set<Long> activeOrigins=new HashSet<>();for(RaidParty raid:state.raids())if(raid.active())activeOrigins.add(raid.originSettlementId());
        for(Faction owner:state.factions())for(Settlement origin:owner.settlements()){
            if(activeOrigins.contains(origin.id()))continue;SettlementCivilizationState c=state.ensureSettlementCivilization(origin.id(),owner.id());
            if(c.banditPressure()>.60){Settlement target=nearestRaidTarget(origin,null,true,allSettlements,owners);if(target!=null){BanditArchetype kind=BanditArchetype.choose(origin,owner,c,day);int men=Math.min(32,Math.max(6,(int)Math.round(origin.population()/120.0*kind.manpowerMul())));state.addRaid(new RaidParty(state.nextId(),0,origin.id(),target.id(),day,men,kind.morale(),true));activeOrigins.add(origin.id());c.adjustBanditPressure(-.12);state.history().add(new WorldEvent(day,"bandit_raid_departed","kind="+kind.key()+", from="+origin.id()+", target="+target.id()+", manpower="+men+", settlement="+origin.id()+", faction="+owner.id()));}continue;}
            Settlement target=nearestRaidTarget(origin,owner,false,allSettlements,owners);if(target!=null&&owner.government().ruler().martial()>.52&&rng.chance(.035)){int men=Math.min(48,Math.max(8,origin.population()/100));state.addRaid(new RaidParty(state.nextId(),owner.id(),origin.id(),target.id(),day,men,.62,false));activeOrigins.add(origin.id());state.history().add(new WorldEvent(day,"faction_raid_departed","faction="+owner.id()+", from="+origin.id()+", target="+target.id()+", manpower="+men));}
        }
    }

    private static Settlement nearestRaidTarget(Settlement origin,Faction attacker,boolean bandit,List<Settlement> candidates,Map<Long,Faction> owners){
        Settlement best=null;double bestDistance=Double.POSITIVE_INFINITY;
        for(Settlement candidate:candidates){if(candidate.id()==origin.id())continue;double distance=origin.position().distanceTo(candidate.position());Faction target=owners.get(candidate.id());if(target==null)continue;
            boolean eligible;if(bandit)eligible=candidate.prosperity()>.35&&distance<1800;else{if(attacker==null||target.id()==attacker.id())continue;DiplomaticRelation rel=attacker.relations().get(target.id());eligible=rel!=null&&(rel.status()==RelationStatus.RIVAL||rel.status()==RelationStatus.HOSTILE)&&distance<2200;}
            if(eligible&&distance<bestDistance){bestDistance=distance;best=candidate;}
        }
        return best;
    }

    private static void advanceRaids(SimulationState state){
        long day=state.clock().day();for(RaidParty raid:new ArrayList<>(state.raids()))if(raid.active()){
            Settlement origin=state.findSettlement(raid.originSettlementId()).orElse(null),target=state.findSettlement(raid.targetSettlementId()).orElse(null);if(origin==null||target==null){raid.finish();continue;}double distance=Math.max(100,origin.position().distanceTo(target.position()));raid.advance(Mathx.clamp(120.0/distance, .08,.32)*(.55+.45*raid.morale()));if(raid.progress()<1)continue;
            Faction defender=state.findSettlementOwner(target.id()).orElse(null);if(defender==null){raid.finish();continue;}long guards=state.socialCitizens().stream().filter(SocialCitizen::alive).filter(c->c.settlementId()==target.id()&&c.role()==CitizenRole.GUARD).count();double fort=has(target,"wall:")?.35:has(target,"barracks:")?.16:0;double defense=Math.max(4,target.population()*.012+guards*2.2+target.publicOrder()*12+fort*20);double attack=raid.manpower()*(.55+.65*raid.morale());boolean success=attack>defense;
            if(success){
                int casualties=Math.min(4,Math.max(0,raid.manpower()/12));target.addPopulation(-casualties);target.adjustUnrest(.035);target.setPublicOrder(target.publicOrder()-.04);
                // Prefer local granary theft, then faction treasury.
                double needFood=raid.manpower()*.8,needGold=raid.manpower()*.05;
                double food=target.stockpile().take(ResourceType.FOOD,Math.min(target.stockpile().get(ResourceType.FOOD),needFood));
                if(food<needFood)food+=defender.stockpile().take(ResourceType.FOOD,needFood-food);
                double gold=target.stockpile().take(ResourceType.GOLD,Math.min(target.stockpile().get(ResourceType.GOLD),needGold));
                if(gold<needGold)gold+=defender.stockpile().take(ResourceType.GOLD,needGold-gold);
                if(raid.bandit()){
                    // Stolen goods go to the nearest active hideout or the origin settlement black market.
                    PirateHideout hideout=state.pirateHideouts().stream().filter(PirateHideout::active)
                            .min(Comparator.comparingDouble(h->h.position().distanceTo(target.position()))).orElse(null);
                    if(hideout!=null&&hideout.position().distanceTo(target.position())<2200)hideout.addLoot(food*.4+gold*8);
                    else origin.stockpile().add(ResourceType.FOOD,food*.55);
                    origin.adjustUnrest(.01); // bandit economy corrodes the origin too
                    state.findSettlementOwner(origin.id()).ifPresent(o->state.ensureSettlementCivilization(origin.id(),o.id()).adjustBanditPressure(.03));
                }else{
                    Faction attacker=state.findFaction(raid.attackerFactionId()).orElse(null);
                    if(attacker!=null){attacker.stockpile().add(ResourceType.FOOD,food);attacker.stockpile().add(ResourceType.GOLD,gold);}
                }
                state.ensureSettlementCivilization(target.id(),defender.id()).adjustBanditPressure(.04);
                state.history().add(new WorldEvent(day,"raid_success","raid="+raid.id()+", target="+target.id()+", casualties="+casualties+", food="+Math.round(food)+", gold="+Math.round(gold)+(raid.bandit()?", bandit=true":"")+(raid.attackerFactionId()>0?", faction="+raid.attackerFactionId():"")));
            }
            else{target.adjustUnrest(-.006);state.history().add(new WorldEvent(day,"raid_repelled","raid="+raid.id()+", target="+target.id()+", settlement="+target.id()+", defendersHeld=true"+(raid.attackerFactionId()>0?", faction="+raid.attackerFactionId():"")));}
            raid.finish();
        }
        state.pruneRaids();
    }

    private static void simulateClaims(SimulationState state){
        List<ResourceClaim> active=state.resourceClaims().stream().filter(ResourceClaim::active).toList();for(ResourceClaim c:active)c.setContestedByFactionId(0);
        for(int i=0;i<active.size();i++)for(int j=i+1;j<active.size();j++){ResourceClaim a=active.get(i),b=active.get(j);if(a.type()!=b.type()||a.factionId()==b.factionId()||a.position().distanceTo(b.position())>520)continue;a.setContestedByFactionId(b.factionId());b.setContestedByFactionId(a.factionId());state.findSettlementOwner(a.settlementId()).ifPresent(f->f.relationWith(b.factionId()).adjust(-.08));state.findSettlementOwner(b.settlementId()).ifPresent(f->f.relationWith(a.factionId()).adjust(-.08));}
    }

    private static void simulateInformationAndTribute(SimulationState state){
        long day=state.clock().day();for(Faction faction:state.factions()){
            FactionCivilizationState c=state.ensureFactionCivilization(faction.id());double education=faction.settlements().stream().mapToDouble(s->state.ensureSettlementCivilization(s.id(),faction.id()).education()).average().orElse(.2);double unrest=faction.settlements().stream().mapToDouble(Settlement::unrest).average().orElse(.1);boolean atWar=state.wars().stream().anyMatch(w->w.active()&&(w.attackerFactionId()==faction.id()||w.defenderFactionId()==faction.id()));double propaganda=Mathx.clamp(unrest*.42+(atWar?.28:0)+faction.government().corruption()*.12,0,1);double intel=Mathx.clamp(.12+education*.35+faction.technology()*.12,0,1);c.approach(.45+faction.government().legitimacy()*.35,.28+religiousBuildingShare(faction)*.45,education,propaganda,intel,.15);
            for(var rel:faction.relations().entrySet()){long other=rel.getKey();if(rel.getValue().status()==RelationStatus.RIVAL||rel.getValue().status()==RelationStatus.HOSTILE||rel.getValue().status()==RelationStatus.WAR)c.adjustSpyNetwork(other,.015*intel);else c.adjustSpyNetwork(other,-.008);}
            handleTribute(state,faction,c);
        }
    }

    private static void handleTribute(SimulationState state,Faction faction,FactionCivilizationState c){
        long overlordId=c.tributaryToFactionId();if(overlordId>0){Faction overlord=state.findFaction(overlordId).orElse(null);DiplomaticRelation rel=faction.relations().get(overlordId);if(overlord==null||rel==null||rel.status()==RelationStatus.WAR||power(faction)>power(overlord)*.72){c.setTributary(0,0);state.history().add(new WorldEvent(state.clock().day(),"tribute_ended","faction="+faction.id()+", formerOverlord="+overlordId));return;}double due=Math.min(faction.treasury(),Math.max(.1,faction.population()*c.tributeRate()*.003));if(due>0){faction.addTreasury(-due);overlord.addTreasury(due);}return;}
        Faction candidate=null;double bestRatio=0;for(var e:faction.relations().entrySet()){if(e.getValue().status()!=RelationStatus.HOSTILE)continue;Faction stronger=state.findFaction(e.getKey()).orElse(null);if(stronger==null)continue;double ratio=power(stronger)/Math.max(1,power(faction));if(ratio>3.4&&ratio>bestRatio){bestRatio=ratio;candidate=stronger;}}
        if(candidate!=null&&Math.floorMod(state.clock().day()+faction.id()*17,180L)==0){c.setTributary(candidate.id(),.055);faction.relationWith(candidate.id()).adjust(8);candidate.relationWith(faction.id()).adjust(4);state.history().add(new WorldEvent(state.clock().day(),"tributary_state","faction="+faction.id()+", overlord="+candidate.id()+", rate=0.055"));}
    }

    private static void promoteLegends(SimulationState state){
        long day=state.clock().day();Set<String> existing=new HashSet<>();for(LegendRecord l:state.legends())existing.add(l.subjectKey());List<WorldEvent> recent=state.history().recent(180);for(WorldEvent e:recent){if(!legendWorthy(e.type()))continue;String subject=e.type()+":"+Integer.toUnsignedString(e.message().hashCode());if(existing.contains(subject))continue;long factionId=extractLong(e.message(),"faction=");if(factionId<=0)factionId=extractLong(e.message(),"toFaction=");if(factionId<=0&&state.factions().size()==1)factionId=state.factions().getFirst().id();if(factionId<=0)continue;long settlementId=extractLong(e.message(),"settlement=");String title=legendTitle(e.type());state.addLegend(new LegendRecord(state.nextId(),e.day(),factionId,settlementId,subject,title,e.message(),renownFor(e.type())));existing.add(subject);if(state.legends().size()>=SimulationState.MAX_LEGENDS)break;}
        for(LegendRecord legend:state.legends())if(!legend.monumented()&&legend.settlementId()>0){Settlement s=state.findSettlement(legend.settlementId()).orElse(null);if(s!=null&&has(s,"monument:")){legend.markMonumented();state.history().add(new WorldEvent(day,"legend_memorialized","legend="+legend.id()+", settlement="+s.id()));}}
    }

    private static boolean legendWorthy(String type){String t=type.toLowerCase(Locale.ROOT);return t.contains("siege_won")||t.contains("settlement_captured")||t.contains("rebell")||t.contains("epidemic")||t.contains("raid_repelled")||t.contains("succession")||t.equals("battle")||t.equals("naval_battle")||t.contains("outlaw_legend")||t.contains("deserter_band_formed")||t.contains("bandit_band_formed");}
    private static String legendTitle(String type){String clean=type.replace('_',' ').trim();if(clean.isEmpty())return "Remembered Event";if(clean.contains("outlaw")||clean.contains("bandit")||clean.contains("deserter"))return "Outlaw Legend";return Character.toUpperCase(clean.charAt(0))+clean.substring(1);}
    private static double renownFor(String type){String t=type.toLowerCase(Locale.ROOT);if(t.contains("captured")||t.contains("siege"))return .82;if(t.contains("rebell")||t.contains("epidemic"))return .72;if(t.contains("outlaw")||t.contains("bandit")||t.contains("deserter"))return .68;if(t.contains("battle"))return .65;return .55;}

    private static double power(Faction f){return f.armies().stream().mapToDouble(Army::combatPower).sum()+f.population()*.018+f.treasury()*.01;}
    private static double specialistShare(SimulationState state,long settlementId,CitizenRole role){long total=state.socialCitizens().stream().filter(SocialCitizen::alive).filter(c->c.settlementId()==settlementId).count();if(total==0)return 0;long count=state.socialCitizens().stream().filter(SocialCitizen::alive).filter(c->c.settlementId()==settlementId&&c.role()==role).count();return count/(double)total;}
    private static double religiousBuildingShare(Faction f){if(f.settlements().isEmpty())return 0;long n=f.settlements().stream().filter(s->has(s,"temple:")).count();return n/(double)f.settlements().size();}
    private static boolean has(Settlement s,String prefix){return s.completedConstruction().stream().anyMatch(k->k.startsWith(prefix));}
    private static long extractLong(String text,String marker){int p=text.indexOf(marker);if(p<0)return 0;p+=marker.length();int end=p;while(end<text.length()&&Character.isDigit(text.charAt(end)))end++;if(end==p)return 0;try{return Long.parseLong(text.substring(p,end));}catch(NumberFormatException ignored){return 0;}}
}
