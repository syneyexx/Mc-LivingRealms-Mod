package dev.livingrealms.sim.property;

import dev.livingrealms.sim.construction.StructureRole;
import dev.livingrealms.sim.law.*;
import dev.livingrealms.sim.world.*;
import java.util.Objects;

/** Converts unauthorized interaction with completed faction property into authoritative crime records. */
public final class PropertyCrimeEngine {
    private PropertyCrimeEngine() {}

    public static CrimeResult reportTheft(SimulationState state,String actorKey,PropertyClaim claim,double value,int witnessCount,String evidence){
        Objects.requireNonNull(state,"state");Objects.requireNonNull(claim,"claim");
        if(actorKey==null||actorKey.isBlank())throw new IllegalArgumentException("actorKey");
        if(!Double.isFinite(value)||value<0||witnessCount<0)throw new IllegalArgumentException("theft report");
        if(state.findPlayerStanding(actorKey).filter(s->s.isMember()&&s.memberFactionId()==claim.factionId()).isPresent())
            return new CrimeResult(false,0,0,WantedLevel.NONE,"authorized_property_access");
        CrimeType type=crimeType(claim.role());
        return state.reportCrime(actorKey,claim.factionId(),type,value,claim.center(),witnessCount>0,witnessCount,"property:"+claim.structureKey(),Objects.requireNonNullElse(evidence,""));
    }

    public static CrimeType crimeType(StructureRole role){
        return switch(role){
            case HOUSE, KEEP -> CrimeType.BURGLARY;
            case BARRACKS, WALL, GATE, FACTORY, AIRFIELD, DOCK, MINE, PRISON, OBSERVATORY, WIZARD_HALL, WIZARD_GROVE, WIZARD_TUNNEL, IRRIGATION, AQUEDUCT -> CrimeType.SABOTAGE;
            case MARKET, WAREHOUSE, WORKSHOP, TAVERN, TEMPLE, CLINIC, SCHOOL, COURTHOUSE, ORPHANAGE, WIZARD_HOME, MILL, BAKERY, BREWERY -> CrimeType.THEFT;
            case FARM, LUMBER_CAMP, FISHERY, WELL, PASTURE -> CrimeType.POACHING;
            case ROAD, PLAZA, MONUMENT -> CrimeType.TRESPASS;
        };
    }
}
