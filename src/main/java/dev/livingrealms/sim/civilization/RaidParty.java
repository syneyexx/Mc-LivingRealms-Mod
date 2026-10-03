package dev.livingrealms.sim.civilization;

import dev.livingrealms.sim.util.Mathx;

/** Small strategic raiding group. It can represent a faction raid or a bandit/deserter group. */
public final class RaidParty {
    private final long id,attackerFactionId,originSettlementId,targetSettlementId,createdDay; private int manpower; private double morale,progress; private boolean active=true; private final boolean bandit;
    public RaidParty(long id,long attackerFactionId,long originSettlementId,long targetSettlementId,long createdDay,int manpower,double morale,boolean bandit){if(id<=0||originSettlementId<=0||targetSettlementId<=0||originSettlementId==targetSettlementId||manpower<=0)throw new IllegalArgumentException("raid");if(!bandit&&attackerFactionId<=0)throw new IllegalArgumentException("raid faction");this.id=id;this.attackerFactionId=Math.max(0,attackerFactionId);this.originSettlementId=originSettlementId;this.targetSettlementId=targetSettlementId;this.createdDay=createdDay;this.manpower=manpower;this.morale=unit(morale);this.bandit=bandit;}
    public long id(){return id;} public long attackerFactionId(){return attackerFactionId;} public long originSettlementId(){return originSettlementId;} public long targetSettlementId(){return targetSettlementId;} public long createdDay(){return createdDay;} public int manpower(){return manpower;} public double morale(){return morale;} public double progress(){return progress;} public boolean active(){return active;} public boolean bandit(){return bandit;}
    public void advance(double value){if(!Double.isFinite(value)||value<0)throw new IllegalArgumentException("raid progress");progress=Mathx.clamp(progress+value,0,1);} public void lose(int count){manpower=Math.max(0,manpower-Math.max(0,count));if(manpower==0)active=false;} public void adjustMorale(double delta){if(!Double.isFinite(delta))throw new IllegalArgumentException("raid morale");morale=unit(morale+delta);if(morale<.08)active=false;} public void finish(){active=false;progress=1;}
    public void restore(int manpower,double morale,double progress,boolean active){this.manpower=Math.max(0,manpower);this.morale=unit(morale);this.progress=unit(progress);this.active=active&&this.manpower>0;}
    private static double unit(double v){if(!Double.isFinite(v))throw new IllegalArgumentException("raid metric");return Mathx.clamp(v,0,1);}
}
