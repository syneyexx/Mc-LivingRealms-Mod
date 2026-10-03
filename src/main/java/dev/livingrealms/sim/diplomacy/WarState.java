package dev.livingrealms.sim.diplomacy;

import dev.livingrealms.sim.util.Mathx;

public final class WarState {
    private final long id;
    private final long attackerFactionId;
    private final long defenderFactionId;
    private final WarGoalType goal;
    private final long targetSettlementId;
    private final long startDay;
    private double attackerScore;
    private double attackerExhaustion;
    private double defenderExhaustion;
    private boolean active=true;
    public WarState(long id,long attacker,long defender,WarGoalType goal,long targetSettlementId,long startDay){if(id<=0||attacker<=0||defender<=0||attacker==defender)throw new IllegalArgumentException("war identity");if(goal==null)throw new IllegalArgumentException("goal");if(startDay<0)throw new IllegalArgumentException("startDay");this.id=id;this.attackerFactionId=attacker;this.defenderFactionId=defender;this.goal=goal;this.targetSettlementId=targetSettlementId;this.startDay=startDay;}
    public long id(){return id;} public long attackerFactionId(){return attackerFactionId;} public long defenderFactionId(){return defenderFactionId;} public WarGoalType goal(){return goal;} public long targetSettlementId(){return targetSettlementId;} public long startDay(){return startDay;} public double attackerScore(){return attackerScore;} public double attackerExhaustion(){return attackerExhaustion;} public double defenderExhaustion(){return defenderExhaustion;} public boolean active(){return active;}
    public boolean between(long a,long b){return(attackerFactionId==a&&defenderFactionId==b)||(attackerFactionId==b&&defenderFactionId==a);} public boolean involves(long id){return attackerFactionId==id||defenderFactionId==id;}
    public void adjustScore(double v){attackerScore=Mathx.clamp(attackerScore+v,-100,100);} public void addExhaustion(long factionId,double v){if(factionId==attackerFactionId)attackerExhaustion=Mathx.clamp(attackerExhaustion+v,0,1);else if(factionId==defenderFactionId)defenderExhaustion=Mathx.clamp(defenderExhaustion+v,0,1);else throw new IllegalArgumentException("not belligerent");}
    public void end(){active=false;} public void restore(double score,double attackerExhaustion,double defenderExhaustion,boolean active){this.attackerScore=Mathx.clamp(score,-100,100);this.attackerExhaustion=Mathx.clamp(attackerExhaustion,0,1);this.defenderExhaustion=Mathx.clamp(defenderExhaustion,0,1);this.active=active;}
}
