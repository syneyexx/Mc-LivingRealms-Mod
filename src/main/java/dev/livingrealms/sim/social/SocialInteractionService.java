package dev.livingrealms.sim.social;

import dev.livingrealms.sim.world.*;
import java.util.Objects;

/** Server-authoritative consequences that require a verified physical action, such as handing over an item. */
public final class SocialInteractionService {
    private SocialInteractionService(){}
    public static double applyGift(SimulationState state,SocialCitizen citizen,String playerKey,String description,double value){
        Objects.requireNonNull(state);Objects.requireNonNull(citizen);if(playerKey==null||playerKey.isBlank()||!Double.isFinite(value)||value<=0)throw new IllegalArgumentException("gift");
        double magnitude=Math.min(1,.08+Math.log1p(value)*.08);CitizenRelationship rel=citizen.relationship(playerKey);rel.adjust(magnitude,Math.min(0,-magnitude*.2),Math.max(0,magnitude*.08),0,magnitude*.7);
        var settlement=state.findSettlement(citizen.settlementId()).orElseThrow();String summary="Received a gift"+(description==null||description.isBlank()?"":" ("+description+")")+" from "+playerKey+".";
        citizen.remember(new CitizenMemory(state.clock().day(),MemoryType.GIFT,playerKey,playerKey,summary,settlement.position(),Math.min(1,.35+magnitude),1));
        return state.playerStanding(playerKey).adjustReputation(citizen.factionId(),Math.min(8,1+value*.25));
    }
}
