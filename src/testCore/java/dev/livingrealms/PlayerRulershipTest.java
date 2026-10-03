package dev.livingrealms;

import dev.livingrealms.sim.persistence.SimulationStateCodec;
import dev.livingrealms.sim.player.*;
import dev.livingrealms.sim.world.*;

public final class PlayerRulershipTest {
    private PlayerRulershipTest(){}
    public static void main(String[] args){
        SimulationState state=new SimulationState(77191L);
        var founded=PlayerSettlementFounder.found(state,"player:founder","Founder","Stonehaven",new SimPosition(5000,5000));
        check(founded.success(),"founding failed");
        var standing=state.playerStanding("player:founder");check(standing.rank()==FactionRank.RULER&&standing.isRulerOf(founded.factionId()),"founder not canonical ruler");
        state.advanceDays(40);check(state.socialCitizens().stream().noneMatch(c->c.factionId()==founded.factionId()&&c.name().equals("Founder")),"player duplicated as simulated ruler citizen");
        check(!state.leaveFaction("player:founder"),"ordinary leave action must not silently abdicate a player ruler");
        SimulationState restored=SimulationStateCodec.decode(SimulationStateCodec.encode(state),state.species());
        check(restored.playerStanding("player:founder").rank()==FactionRank.RULER&&restored.playerRuler(founded.factionId()).isPresent(),"rulership did not survive save/load");
        System.out.println("PASS player rulership: founder is authoritative ruler + no fake NPC ruler + no accidental leave + schema16 persistence");
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
