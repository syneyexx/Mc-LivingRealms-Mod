package dev.livingrealms.sim.civilization;

import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.transport.TransportRoute;
import dev.livingrealms.sim.util.Mathx;
import dev.livingrealms.sim.world.SimPosition;
import dev.livingrealms.sim.world.SimulationState;
import java.util.Comparator;

/**
 * Derived map/intelligence quality. It never hides the strategic M-map; it expresses how precise
 * a faction's in-world knowledge is for dialogue, markers and covert discoveries.
 */
public final class CartographicKnowledgeEngine {
    private CartographicKnowledgeEngine(){}

    public static double localPrecision(SimulationState state,long factionId,SimPosition position){
        Faction faction=state.findFaction(factionId).orElse(null);if(faction==null||position==null)return 0;
        Settlement nearest=faction.settlements().stream().min(Comparator.comparingDouble(s->s.position().distanceTo(position))).orElse(null);
        if(nearest==null)return Mathx.clamp(state.ensureFactionCivilization(factionId).intelligence()*.35,0,1);
        SettlementCivilizationState civ=state.ensureSettlementCivilization(nearest.id(),factionId);
        double distance=nearest.position().distanceTo(position);
        double proximity=1.0/(1.0+distance/900.0);
        double routeBonus=state.routes().stream().filter(TransportRoute::operational).filter(r->r.ownerFactionId()==factionId).filter(r->r.fromSettlementId()==nearest.id()||r.toSettlementId()==nearest.id()).mapToDouble(r->.025+.045*r.quality()).sum();
        double intel=state.ensureFactionCivilization(factionId).intelligence();
        return Mathx.clamp(.08+civ.knowledge(KnowledgeDomain.CARTOGRAPHY)*.58+civ.education()*.11+intel*.12+routeBonus,0,1)*(.55+.45*proximity);
    }

    public static double foreignPrecision(SimulationState state,long viewerFactionId,long targetFactionId,SimPosition position){
        if(viewerFactionId<=0)return 0;
        if(viewerFactionId==targetFactionId)return localPrecision(state,viewerFactionId,position);
        FactionCivilizationState civ=state.ensureFactionCivilization(viewerFactionId);
        double own=localPrecision(state,viewerFactionId,position);
        double spy=civ.spyStrength(targetFactionId);
        double completedIntel=state.intelligenceOperations().stream().filter(op->!op.active()&&op.sourceFactionId()==viewerFactionId&&op.targetFactionId()==targetFactionId).mapToDouble(IntelligenceOperation::quality).max().orElse(0);
        return Mathx.clamp(own*.35+spy*.42+completedIntel*.23,0,1);
    }

    public static double uncertaintyBlocks(double precision){
        double p=Mathx.clamp(precision,0,1);
        return 8+(1-p)*(1-p)*480;
    }

    public static boolean knowsRuin(SimulationState state,long viewerFactionId,RuinSite ruin){
        if(ruin==null||viewerFactionId<=0)return false;
        if(viewerFactionId==ruin.originalFactionId())return true;
        return foreignPrecision(state,viewerFactionId,ruin.originalFactionId(),ruin.position())>=.34;
    }

    public static boolean canTriangulateHiddenCache(SimulationState state,long viewerFactionId,HiddenCache cache){
        if(cache==null||viewerFactionId<=0||cache.recovered())return false;
        if(cache.knownToFaction(viewerFactionId))return true;
        return foreignPrecision(state,viewerFactionId,cache.factionId(),cache.position())>=.78;
    }
}
