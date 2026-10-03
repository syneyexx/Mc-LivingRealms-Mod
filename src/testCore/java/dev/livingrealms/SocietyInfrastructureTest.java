package dev.livingrealms;

import dev.livingrealms.sim.civilian.*;
import dev.livingrealms.sim.construction.*;
import dev.livingrealms.sim.faction.*;
import dev.livingrealms.sim.world.*;
import java.util.*;

/** Locks visible civic infrastructure and specialist citizen routines into the product. */
public final class SocietyInfrastructureTest {
    private SocietyInfrastructureTest() {}
    public static void main(String[] args){
        SimulationState state=new SimulationState(0xC1A1C5L);DemoSeeder.seed(state);
        Faction faction=state.factions().getFirst();
        Settlement town=faction.settlements().stream().filter(s->s.tier().ordinal()>=Settlement.Tier.TOWN.ordinal()).findFirst().orElseThrow();
        Settlement city=faction.settlements().stream().filter(s->s.tier().ordinal()>=Settlement.Tier.CITY.ordinal()).findFirst().orElseThrow();
        Set<StructureRole> townRoles=roles(SettlementPlanner.plan(faction,town));
        for(StructureRole r:List.of(StructureRole.WELL,StructureRole.TAVERN,StructureRole.TEMPLE,StructureRole.CLINIC,StructureRole.SCHOOL,StructureRole.COURTHOUSE,StructureRole.PRISON))check(townRoles.contains(r),"town missing "+r);
        Set<StructureRole> cityRoles=roles(SettlementPlanner.plan(faction,city));
        for(StructureRole r:List.of(StructureRole.GATE,StructureRole.ORPHANAGE,StructureRole.MONUMENT))check(cityRoles.contains(r),"city missing "+r);
        for(ConstructionIntent i:SettlementPlanner.plan(faction,city)){StructureBlueprint bp=StructureBlueprintFactory.create(i);check(!bp.placements().isEmpty(),"empty blueprint "+i.role());city.markConstructionCompleted(i.key());}
        check(CitizenRoutinePlanner.plan(state,faction,city,CitizenRole.HEALER,101).activity()==CitizenActivity.HEAL,"healer must use clinic");
        check(CitizenRoutinePlanner.plan(state,faction,city,CitizenRole.PRIEST,102).activity()==CitizenActivity.WORSHIP,"priest must use temple");
        check(CitizenRoutinePlanner.plan(state,faction,city,CitizenRole.SCHOLAR,103).activity()==CitizenActivity.STUDY,"scholar must use school/observatory");
        var projections=CitizenMaterializationPlanner.plan(state.factions(),List.of(city.position()),700,80);
        check(projections.stream().anyMatch(p->p.role()==CitizenRole.HEALER),"completed clinic must allow healer projection");
        check(projections.stream().anyMatch(p->p.role()==CitizenRole.PRIEST),"completed temple must allow priest projection");
        check(projections.stream().anyMatch(p->p.role()==CitizenRole.SCHOLAR),"completed school must allow scholar projection");
        System.out.println("PASS visible society infrastructure: wells + taverns + temples + clinics + schools + courts + prisons + gates + monuments + specialist routines");
    }
    private static Set<StructureRole> roles(List<ConstructionIntent> intents){Set<StructureRole> out=EnumSet.noneOf(StructureRole.class);for(ConstructionIntent i:intents)out.add(i.role());return out;}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
