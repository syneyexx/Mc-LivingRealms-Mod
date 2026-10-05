package dev.livingrealms.sim.civilization;

import dev.livingrealms.sim.civilian.CitizenRole;
import dev.livingrealms.sim.faction.*;
import dev.livingrealms.sim.law.*;
import dev.livingrealms.sim.social.*;
import dev.livingrealms.sim.util.*;
import dev.livingrealms.sim.world.*;
import java.util.*;

/**
 * NPC crime spawn and justice case investigation/sentencing/exile.
 */
public final class LawLifecycleEngine {
    private LawLifecycleEngine() {}

    public static void spawnNpcCrime(SimulationState state,DeterministicRng rng){
        long day=state.clock().day();
        for(SocialCitizen c:state.socialCitizens())if(c.alive()&&c.ageYears(day)>=16){Settlement s=state.findSettlement(c.settlementId()).orElse(null);Faction f=state.findFaction(c.factionId()).orElse(null);if(s==null||f==null)continue;boolean pending=state.justiceCases().stream().anyMatch(j->j.active()&&j.accusedKey().equals("citizen:"+c.id()));if(pending)continue;FactionCivilizationState civ=state.ensureFactionCivilization(f.id());FaithCatalog.FaithProfile faith=FaithCatalog.of(civ.faithName());double pressure=(1-c.needs().hunger())*.25+(1-c.needs().status())*.12+c.personality().greed()*.16+c.personality().treachery()*.12+c.personality().aggression()*.08+(1-s.publicOrder())*.18;if(!rng.chance(Mathx.clamp(pressure*.018,0,.06)))continue;CrimeType type;if(CivilizationSupport.has(s,"temple:")&&faith.heresySeverity()>.5&&c.personality().treachery()>.55&&c.personality().loyalty()<.45&&rng.chance(faith.heresySeverity()*.25))type=CrimeType.HERESY;else if(c.role()==CitizenRole.TRADER&&f.government().corruption()>.4&&c.personality().greed()>.6&&rng.chance(.3))type=CrimeType.SMUGGLING;else if(c.personality().aggression()>.72&&rng.chance(.35))type=CrimeType.ASSAULT;else if(c.personality().greed()>.65)type=CrimeType.THEFT;else type=CrimeType.TRESPASS;boolean witnessed=rng.chance(.45+.4*f.government().lawEnforcement());CrimeResult result=state.reportCrime("citizen:"+c.id(),f.id(),type,type==CrimeType.THEFT||type==CrimeType.SMUGGLING?rng.between(4,25):0,s.position(),witnessed,witnessed?1+rng.nextInt(3):0,"settlement:"+s.id(),"npc_simulation");if(result.registered()){JusticeCase jc=new JusticeCase(state.nextId(),f.id(),s.id(),day,"citizen:"+c.id(),type);state.addJusticeCase(jc);}}
    }

    public static void advanceJusticeCases(SimulationState state){
        long day=state.clock().day();
        for(JusticeCase jc:state.justiceCases())if(jc.active()){
            SocialCitizen accused=state.findSocialCitizen(CivilizationSupport.parseCitizenKey(jc.accusedKey())).orElse(null);Faction faction=state.findFaction(jc.factionId()).orElse(null);Settlement settlement=state.findSettlement(jc.settlementId()).orElse(null);if(faction==null||settlement==null){jc.dismiss();continue;}FactionCivilizationState policy=state.ensureFactionCivilization(faction.id());
            if(jc.status()==JusticeStatus.INVESTIGATING){
                long investigateDays=2+Math.round(policy.dueProcess()*2)-(CivilizationSupport.has(settlement,"courthouse:")?1:0);
                if(day-jc.openedDay()>=Math.max(1,investigateDays)){jc.charge();state.history().add(new WorldEvent(day,"court_case_charged","case="+jc.id()+", accused="+jc.accusedKey()+", settlement="+settlement.id()));}
                continue;
            }
            if(jc.status()==JusticeStatus.CHARGED){
                long processDays=1+Math.round(policy.dueProcess()*3)-(CivilizationSupport.has(settlement,"courthouse:")?1:0);if(day-jc.openedDay()<processDays)continue;
                double evidence=evidenceStrength(state,jc);double convictionThreshold=.28+policy.dueProcess()*.30;
                if(evidence<convictionThreshold){jc.dismiss();state.history().add(new WorldEvent(day,"court_case_dismissed","case="+jc.id()+", accused="+jc.accusedKey()+", evidence="+String.format(java.util.Locale.ROOT,"%.2f",evidence)));continue;}
                SentenceType sentence=sentenceFor(jc.crimeType(),policy,settlement);double fine=jc.crimeType().baseBounty()*(.35+.8*policy.lawSeverity());long release=day+(sentence==SentenceType.IMPRISONMENT?Math.max(2,(long)Math.ceil(fine/25.0)):0);jc.sentence(sentence,sentence==SentenceType.FINE||sentence==SentenceType.RESTITUTION?fine:0,release);applySentence(state,jc,accused,faction,settlement);
            }
            else if(jc.status()==JusticeStatus.SERVING&&day>=jc.releaseDay()){jc.complete();state.history().add(new WorldEvent(day,"sentence_completed","case="+jc.id()+", accused="+jc.accusedKey()));}
            else if(jc.status()==JusticeStatus.SENTENCED)jc.complete();
        }
    }

    public static double evidenceStrength(SimulationState state,JusticeCase jc){
        return state.crimeLedger().incidents().stream().filter(i->i.actorKey().equals(jc.accusedKey())&&i.jurisdictionFactionId()==jc.factionId()&&i.type()==jc.crimeType()&&i.day()>=jc.openedDay()-1).max(Comparator.comparingLong(CrimeIncident::day).thenComparingLong(CrimeIncident::id)).map(i->Mathx.clamp((i.witnessed()?.42:.08)+Math.min(.38,i.witnessCount()*.11)+(i.evidence().isBlank()?0:.16),0,1)).orElse(.18);
    }

    public static SentenceType sentenceFor(CrimeType crime,FactionCivilizationState policy,Settlement settlement){double severity=policy.lawSeverity();return switch(crime){case TRESPASS,POACHING->severity>.75?SentenceType.FINE:SentenceType.WARNING;case THEFT,BURGLARY,SMUGGLING->severity>.72&&CivilizationSupport.has(settlement,"prison:")?SentenceType.IMPRISONMENT:SentenceType.RESTITUTION;case ASSAULT,ROBBERY,SABOTAGE,ARSON->severity>.5&&CivilizationSupport.has(settlement,"prison:")?SentenceType.IMPRISONMENT:SentenceType.FINE;case HERESY->{FaithCatalog.FaithProfile faith=FaithCatalog.of(policy.faithName());double heresy=faith.heresySeverity();if(heresy>.6&&severity>.55)yield CivilizationSupport.has(settlement,"prison:")?SentenceType.IMPRISONMENT:SentenceType.EXILE;if(heresy>.35)yield SentenceType.FINE;yield SentenceType.WARNING;}case MURDER,REGICIDE,WAR_CRIME->severity>.86?SentenceType.EXECUTION:severity>.45?SentenceType.EXILE:SentenceType.IMPRISONMENT;};}

    public static void applySentence(SimulationState state,JusticeCase jc,SocialCitizen accused,Faction faction,Settlement settlement){long day=state.clock().day();switch(jc.sentence()){case WARNING->{}case FINE->{if(accused!=null){double paid=Math.min(accused.money(),jc.fine());accused.addMoney(-paid);faction.addTreasury(paid);}}case RESTITUTION->{if(accused!=null){double paid=Math.min(accused.money(),jc.fine());accused.addMoney(-paid);settlement.stockpile().add(ResourceType.GOLD,paid*.55);settlement.setPublicOrder(settlement.publicOrder()+.01);faction.addTreasury(paid*.45);}}case IMPRISONMENT->{if(CivilizationSupport.has(settlement,"prison:"))state.addCustody(new CustodyRecord(state.nextId(),jc.accusedKey(),faction.id(),day,jc.releaseDay(),jc.crimeType().baseBounty(),"court_case:"+jc.id()));}case EXILE->{if(accused!=null)exileCitizen(state,accused,faction,settlement);}case EXECUTION->{if(accused!=null&&accused.alive())state.recordPhysicalCitizenDeath(settlement.id(),accused.id(),"lawful_execution");}}state.history().add(new WorldEvent(day,"court_sentence","case="+jc.id()+", accused="+jc.accusedKey()+", sentence="+jc.sentence()+", faction="+faction.id()));}

    public static void exileCitizen(SimulationState state,SocialCitizen accused,Faction faction,Settlement settlement){
        Settlement target=state.factions().stream().filter(f->f.id()!=faction.id()).filter(f->f.relations().get(faction.id())==null||f.relations().get(faction.id()).status()!=RelationStatus.WAR).flatMap(f->f.settlements().stream()).min(Comparator.comparingDouble(s->s.position().distanceTo(settlement.position()))).orElse(null);
        if(target==null){state.ensureSettlementCivilization(settlement.id(),faction.id()).adjustBanditPressure(.08);return;}
        Faction owner=state.findSettlementOwner(target.id()).orElseThrow();long oldHouseholdId=accused.householdId();state.findHousehold(oldHouseholdId).ifPresent(h->h.removeMember(accused.id()));
        HouseholdState exileHousehold=new HouseholdState(state.nextId(),owner.id(),target.id(),state.clock().day());exileHousehold.addMember(accused.id());exileHousehold.adjustWealth(accused.money()*.2);exileHousehold.setHomeKey("exile:"+target.id());state.addHousehold(exileHousehold);
        accused.setHouseholdId(exileHousehold.id());accused.migrateTo(owner.id(),target.id());SocialPopulationEngine.rebindAfterMigration(state,accused);state.history().add(new WorldEvent(state.clock().day(),"citizen_exiled","citizen="+accused.id()+", from="+settlement.id()+", to="+target.id()+", oldHousehold="+oldHouseholdId));
    }
}
