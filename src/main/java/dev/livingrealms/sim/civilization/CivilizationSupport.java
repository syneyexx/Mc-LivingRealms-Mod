package dev.livingrealms.sim.civilization;

import dev.livingrealms.sim.civilian.CitizenRole;
import dev.livingrealms.sim.faction.*;
import dev.livingrealms.sim.social.*;
import dev.livingrealms.sim.util.Mathx;
import dev.livingrealms.sim.world.*;
import java.util.*;

/**
 * Shared helpers for civilization lifecycle engines.
 */
final class CivilizationSupport {
    static final int MAX_NAMED_CITIZENS=8_000;

    private CivilizationSupport() {}

    static long parseCitizenKey(String key){if(key==null||!key.startsWith("citizen:"))return 0;try{return Long.parseLong(key.substring(8));}catch(NumberFormatException ignored){return 0;}}

    static String houseName(Faction faction){String clean=faction.name().replaceAll("[^A-Za-z]","");if(clean.isBlank())clean="Realm";return "House "+clean.substring(0,Math.min(8,clean.length()));}

    static Settlement nearestSettlement(SimulationState state,SimPosition p,double max){Settlement best=null;double d=max;for(Faction f:state.factions())for(Settlement s:f.settlements()){double x=s.position().distanceTo(p);if(x<d){d=x;best=s;}}return best;}

    static double specialistShare(SimulationState state,long settlementId,CitizenRole role){long total=0;double weighted=0;for(SocialCitizen c:state.socialCitizens())if(c.alive()&&c.settlementId()==settlementId){total++;if(c.role()==role)weighted+=.45+.55*c.professionSkill();}return total==0?0:weighted/total;}

    static boolean has(Settlement s,String prefix){return s.completedConstruction().stream().anyMatch(k->k.startsWith(prefix));}

    static boolean hasAny(Faction faction,String prefix){for(Settlement s:faction.settlements())if(has(s,prefix))return true;return false;}

    static boolean hasLivingPartner(SimulationState state,SocialCitizen c){return c.relationships().entrySet().stream().filter(e->e.getValue().familyBond()==FamilyBond.PARTNER).mapToLong(e->parseCitizenKey(e.getKey())).anyMatch(id->state.findSocialCitizen(id).filter(SocialCitizen::alive).isPresent());}

    static boolean hasHouseholdPartner(SocialCitizen c,List<SocialCitizen> householdAdults){return c.relationships().entrySet().stream().filter(e->e.getValue().familyBond()==FamilyBond.PARTNER).mapToLong(e->parseCitizenKey(e.getKey())).anyMatch(id->householdAdults.stream().anyMatch(other->other.id()==id&&other.alive()));}

    static CitizenRole chooseProfession(Settlement s,long id){List<CitizenRole> roles=new ArrayList<>(List.of(CitizenRole.FARMER,CitizenRole.BUILDER,CitizenRole.TRADER,CitizenRole.GUARD,CitizenRole.BUTCHER));if(has(s,"mine:"))roles.add(CitizenRole.MINER);if(has(s,"lumber_camp:")){roles.add(CitizenRole.LUMBERJACK);roles.add(CitizenRole.CARPENTER);}if(has(s,"fishery:")){roles.add(CitizenRole.FISHER);roles.add(CitizenRole.SAILOR);}if(has(s,"dock:")){roles.add(CitizenRole.DOCKWORKER);roles.add(CitizenRole.SAILOR);}if(has(s,"workshop:"))roles.add(CitizenRole.ARTISAN);if(has(s,"clinic:"))roles.add(CitizenRole.HEALER);if(has(s,"temple:"))roles.add(CitizenRole.PRIEST);if(has(s,"school:")){roles.add(CitizenRole.SCHOLAR);roles.add(CitizenRole.TEACHER);}if(has(s,"warehouse:")){roles.add(CitizenRole.DOCKWORKER);}return roles.get(Math.floorMod(Long.hashCode(id*31),roles.size()));}

    static long mix(long z){z=(z^(z>>>30))*0xBF58476D1CE4E5B9L;z=(z^(z>>>27))*0x94D049BB133111EBL;return z^(z>>>31);} static double unit(long h,int shift){long z=mix(h^(shift*0x9E3779B97F4A7C15L));return (z>>>11)*0x1.0p-53;} static double lerp(double a,double b,double r){return Mathx.clamp(a+(b-a)*r,0,1);}
}
