package dev.livingrealms;

import dev.livingrealms.sim.civilization.*;
import dev.livingrealms.sim.faction.*;
import dev.livingrealms.sim.transport.*;
import dev.livingrealms.sim.world.*;
import java.util.Map;

public final class CartographicKnowledgeTest {
    private CartographicKnowledgeTest(){}
    public static void main(String[] args){
        SimulationState state=new SimulationState(771991L);
        Faction atlas=faction(state,"Atlas League",0);Faction rival=faction(state,"Red Vale",700);
        Settlement home=atlas.settlements().getFirst(),target=rival.settlements().getFirst();
        SettlementCivilizationState mapSchool=state.ensureSettlementCivilization(home.id(),atlas.id());mapSchool.setKnowledge(KnowledgeDomain.CARTOGRAPHY,.93);mapSchool.approach(.7,.05,.86,.8,0,.05,.8,0,.1,1);
        state.ensureFactionCivilization(atlas.id()).approach(.7,.4,.8,.1,.72,1);
        state.addRoute(new TransportRoute(state.nextId(),atlas.id(),home.id(),target.id(),TransportMode.ROAD,700,.82,.75,50));
        double local=CartographicKnowledgeEngine.localPrecision(state,atlas.id(),home.position());
        check(local>.7,"educated cartographic center must have high local precision");
        double foreignBefore=CartographicKnowledgeEngine.foreignPrecision(state,atlas.id(),rival.id(),target.position());
        check(foreignBefore<local,"foreign detail must initially be less precise than home territory");
        state.ensureFactionCivilization(atlas.id()).adjustSpyNetwork(rival.id(),.9);
        IntelligenceOperation op=new IntelligenceOperation(state.nextId(),atlas.id(),rival.id(),state.clock().day(),0,IntelligenceOperationType.MILITARY_RECON);op.advance(1,1);op.finish();state.addIntelligenceOperation(op);
        double foreignAfter=CartographicKnowledgeEngine.foreignPrecision(state,atlas.id(),rival.id(),target.position());
        check(foreignAfter>foreignBefore,"spy networks and completed reconnaissance must improve foreign precision");
        HiddenCache cache=new HiddenCache(state.nextId(),rival.id(),target.id(),0,target.position(),Map.of(ResourceType.GOLD,20.0));state.addHiddenCache(cache);
        check(CartographicKnowledgeEngine.canTriangulateHiddenCache(state,atlas.id(),cache),"excellent maps + intelligence must be able to triangulate a hostile cache");
        check(CartographicKnowledgeEngine.uncertaintyBlocks(local)<CartographicKnowledgeEngine.uncertaintyBlocks(.2),"better cartography must reduce locational uncertainty");
        System.out.println("PASS cartographic knowledge: education/routes + espionage/recon -> bounded locational precision without hiding strategic map");
    }
    private static Faction faction(SimulationState state,String name,double x){Faction f=new Faction(state.nextId(),name,"Ruler");f.addSettlement(new Settlement(state.nextId(),name+" City",new SimPosition(x,0),280,320));state.addFaction(f);return f;}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
