package dev.livingrealms.sim.social;

import dev.livingrealms.sim.civilian.CitizenRole;
import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.util.DeterministicRng;
import dev.livingrealms.sim.util.Mathx;
import dev.livingrealms.sim.world.SimulationState;
import dev.livingrealms.sim.world.WorldEvent;
import java.util.*;

/**
 * Bounded career/class mobility for persistent SocialCitizen agents.
 * Does not replace aggregate population; only updates named representative lives.
 */
public final class SocialMobilityEngine {
    public void simulateDay(SimulationState state,DeterministicRng rng){
        Objects.requireNonNull(state);Objects.requireNonNull(rng);
        long day=state.clock().day();
        Map<Long,List<String>> workplacesBySettlement=new HashMap<>();
        for(Faction faction:state.factions()){
            for(Settlement settlement:faction.settlements()){
                workplacesBySettlement.put(settlement.id(),workplaceKeys(settlement));
            }
        }
        for(SocialCitizen citizen:state.socialCitizens()){
            if(!citizen.alive())continue;
            Settlement settlement=state.findSettlement(citizen.settlementId()).orElse(null);
            if(settlement==null)continue;
            assignWorkplace(citizen,settlement,workplacesBySettlement.getOrDefault(settlement.id(),List.of()),rng);
            if(day%14!=Math.floorMod(citizen.id(),14L))continue;
            double opportunity=Mathx.clamp(settlement.prosperity()*.4+settlement.employment()*.35+(has(settlement,"school:")?.12:0)+(has(settlement,"guild:")?.08:0),0,1);
            double stress=Mathx.clamp((1-settlement.foodSecurity())*.35+settlement.unrest()*.3+(1-settlement.employment())*.35,0,1);
            double score=citizen.socialMobilityScore();
            // Upward: skilled educated employed people in healthy economies.
            if(citizen.employmentStatus()!=EmploymentStatus.UNEMPLOYED&&score>.55&&opportunity>.42&&citizen.professionSkill()>.48&&rng.chance(.08+score*.12*opportunity)){
                promote(state,citizen,settlement,day,rng);
            }
            // Downward: debt/poverty/unemployment/stress.
            if((citizen.money()<4||stress>.62||settlement.employment()<.38)&&rng.chance(.06+stress*.1)){
                demote(state,citizen,settlement,day,rng);
            }
            // Class refresh from wealth/profession after mobility.
            refreshClass(citizen);
        }
    }

    private static void assignWorkplace(SocialCitizen citizen,Settlement settlement,List<String> keys,DeterministicRng rng){
        String preferred=preferredWorkplacePrefix(citizen.role());
        if(preferred==null){if(citizen.workplaceKey().isBlank())citizen.setWorkplace("home:"+settlement.id());return;}
        String existing=citizen.workplaceKey();
        if(!existing.isBlank()&&keys.stream().anyMatch(k->k.equals(existing)||k.startsWith(preferred)))return;
        String match=keys.stream().filter(k->k.startsWith(preferred)).findFirst().orElse(null);
        if(match!=null)citizen.setWorkplace(match);
        else if(citizen.workplaceKey().isBlank())citizen.setWorkplace(preferred+"virtual:"+settlement.id());
        else if(rng.chance(.02))citizen.setWorkplace(preferred+"virtual:"+settlement.id());
    }

    private static void promote(SimulationState state,SocialCitizen citizen,Settlement settlement,long day,DeterministicRng rng){
        CitizenRole before=citizen.role();
        CitizenRole next=upwardRole(before,citizen.education(),citizen.professionSkill(),rng);
        if(next!=before){
            citizen.setRole(next);
            citizen.practiceProfession(.08);
            citizen.adjustPersonalInfluence(.04);
            citizen.addMoney(2+rng.between(1,6));
            citizen.remember(new CitizenMemory(day,MemoryType.LOCAL_EVENT,"settlement:"+settlement.id(),"self","Advanced from "+before.name().toLowerCase(Locale.ROOT)+" to "+next.name().toLowerCase(Locale.ROOT)+".",settlement.position(),.55,.85));
            if(rng.chance(.25))state.history().add(new WorldEvent(day,"social_mobility_up",citizen.name()+" became "+next.name().toLowerCase(Locale.ROOT)+" in "+settlement.name()));
        }else if(citizen.employmentStatus()==EmploymentStatus.APPRENTICE){
            citizen.setEmploymentStatus(EmploymentStatus.EMPLOYED);
            citizen.practiceProfession(.05);
        }else if(citizen.employmentStatus()==EmploymentStatus.EMPLOYED&&citizen.professionSkill()>.72){
            citizen.setEmploymentStatus(EmploymentStatus.MASTER);
            citizen.adjustPersonalInfluence(.03);
            citizen.addMoney(4);
        }else{
            citizen.practiceProfession(.04);
            citizen.adjustEducation(.01);
            citizen.addMoney(1);
        }
    }

    private static void demote(SimulationState state,SocialCitizen citizen,Settlement settlement,long day,DeterministicRng rng){
        if(citizen.employmentStatus()==EmploymentStatus.MASTER&&rng.chance(.4)){
            citizen.setEmploymentStatus(EmploymentStatus.EMPLOYED);
            citizen.addMoney(-Math.min(citizen.money()*.15,8));
            return;
        }
        if(citizen.money()<3&&rng.chance(.35)){
            citizen.setEmploymentStatus(EmploymentStatus.UNEMPLOYED);
            citizen.setWorkplace("");
            citizen.adjustPersonalInfluence(-.03);
            citizen.remember(new CitizenMemory(day,MemoryType.LOCAL_EVENT,"settlement:"+settlement.id(),"self","Lost steady work in "+settlement.name()+".",settlement.position(),.5,.8));
            return;
        }
        CitizenRole before=citizen.role();
        CitizenRole next=downwardRole(before,rng);
        if(next!=before){
            citizen.setRole(next);
            citizen.addMoney(-Math.min(citizen.money()*.2,10));
            citizen.adjustPersonalInfluence(-.02);
            if(rng.chance(.2))state.history().add(new WorldEvent(day,"social_mobility_down",citizen.name()+" fell to "+next.name().toLowerCase(Locale.ROOT)+" in "+settlement.name()));
        }
    }

    private static void refreshClass(SocialCitizen citizen){
        SocialClass byRole=switch(citizen.role()){
            case OFFICIAL,PRIEST -> SocialClass.ELITE;
            case SCHOLAR,TEACHER,HEALER -> SocialClass.PROFESSIONAL;
            case TRADER -> SocialClass.MERCHANT;
            case ARTISAN,CARPENTER,BUTCHER,BUILDER -> SocialClass.ARTISAN;
            default -> SocialClass.WORKING;
        };
        SocialClass byWealth=citizen.wealthClass();
        // Prefer the lower of role/wealth so poverty is visible on elites who go broke.
        citizen.setSocialClass(byWealth.ordinal()<byRole.ordinal()?byWealth:byRole);
    }

    private static CitizenRole upwardRole(CitizenRole role,double education,double skill,DeterministicRng rng){
        return switch(role){
            case FARMER,HUNTER,FISHER -> skill>.6&&rng.chance(.45)?CitizenRole.TRADER:role==CitizenRole.FARMER&&rng.chance(.3)?CitizenRole.BUTCHER:CitizenRole.ARTISAN;
            case MINER,LUMBERJACK,DOCKWORKER -> skill>.55?CitizenRole.ARTISAN:CitizenRole.BUILDER;
            case BUILDER,CARPENTER,BUTCHER -> skill>.65&&education>.35?CitizenRole.ARTISAN:role;
            case ARTISAN -> education>.45&&skill>.7?CitizenRole.TRADER:role;
            case TRADER -> education>.5&&skill>.75?CitizenRole.OFFICIAL:role;
            case GUARD -> skill>.7?CitizenRole.OFFICIAL:role;
            case TEACHER -> education>.6?CitizenRole.SCHOLAR:role;
            case HEALER,PRIEST,SCHOLAR -> education>.7&&skill>.8?CitizenRole.OFFICIAL:role;
            case SAILOR -> skill>.6?CitizenRole.DOCKWORKER:role;
            default -> role;
        };
    }

    private static CitizenRole downwardRole(CitizenRole role,DeterministicRng rng){
        return switch(role){
            case OFFICIAL -> rng.chance(.5)?CitizenRole.TRADER:CitizenRole.GUARD;
            case SCHOLAR,TEACHER -> CitizenRole.ARTISAN;
            case TRADER -> rng.chance(.5)?CitizenRole.ARTISAN:CitizenRole.DOCKWORKER;
            case ARTISAN,CARPENTER,BUTCHER,BUILDER -> rng.chance(.5)?CitizenRole.FARMER:role;
            case GUARD -> CitizenRole.FARMER;
            case HEALER,PRIEST -> CitizenRole.TEACHER;
            default -> CitizenRole.FARMER;
        };
    }

    private static String preferredWorkplacePrefix(CitizenRole role){
        return switch(role){
            case FARMER -> "farm:";
            case MINER -> "mine:";
            case LUMBERJACK -> "lumber:";
            case HUNTER -> "hunt:";
            case FISHER -> "fishery:";
            case ARTISAN -> "workshop:";
            case TRADER -> "market:";
            case BUILDER -> "construction:";
            case GUARD -> "barracks:";
            case OFFICIAL -> "hall:";
            case HEALER -> "clinic:";
            case PRIEST -> "temple:";
            case SCHOLAR,TEACHER -> "school:";
            case BUTCHER -> "butcher:";
            case CARPENTER -> "carpenter:";
            case SAILOR,DOCKWORKER -> "dock:";
            case SPY -> "archive:";
            default -> null;
        };
    }

    private static List<String> workplaceKeys(Settlement settlement){
        List<String> keys=new ArrayList<>();
        for(String key:settlement.completedConstruction()){
            if(key.startsWith("farm:")||key.startsWith("mine:")||key.startsWith("lumber:")||key.startsWith("fishery:")
                    ||key.startsWith("workshop:")||key.startsWith("market:")||key.startsWith("barracks:")
                    ||key.startsWith("hall:")||key.startsWith("clinic:")||key.startsWith("temple:")
                    ||key.startsWith("school:")||key.startsWith("dock:")||key.startsWith("warehouse:")
                    ||key.startsWith("smithy:")||key.startsWith("bakery:")||key.startsWith("brewery:")
                    ||key.startsWith("butcher:")||key.startsWith("carpenter:")||key.startsWith("factory:")
                    ||key.startsWith("keep:")||key.startsWith("archive:"))keys.add(key);
        }
        // Map smithy/bakery onto artisan workplace preference.
        for(String key:settlement.completedConstruction()){
            if(key.startsWith("smithy:")||key.startsWith("bakery:")||key.startsWith("brewery:")||key.startsWith("factory:"))
                keys.add("workshop:"+key.substring(key.indexOf(':')+1));
        }
        return keys;
    }

    private static boolean has(Settlement settlement,String prefix){
        return settlement.completedConstruction().stream().anyMatch(k->k.startsWith(prefix));
    }
}
