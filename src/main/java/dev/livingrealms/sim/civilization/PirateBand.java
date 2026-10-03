package dev.livingrealms.sim.civilization;

import dev.livingrealms.sim.util.Mathx;
import dev.livingrealms.sim.world.SimPosition;

/** Strategic piracy group attacking waterborne commerce without pretending to be a sovereign faction. */
public final class PirateBand {
    private final long id,originSettlementId,createdDay;
    private SimPosition position;
    private int strength;
    private double morale=.62,loot;
    private boolean active=true;
    public PirateBand(long id,long originSettlementId,long createdDay,SimPosition position,int strength){if(id<=0||originSettlementId<=0||createdDay<0||position==null||strength<=0)throw new IllegalArgumentException("pirate band");this.id=id;this.originSettlementId=originSettlementId;this.createdDay=createdDay;this.position=position;this.strength=strength;}
    public long id(){return id;} public long originSettlementId(){return originSettlementId;} public long createdDay(){return createdDay;} public SimPosition position(){return position;} public int strength(){return strength;} public double morale(){return morale;} public double loot(){return loot;} public boolean active(){return active&&strength>0;} public void moveToward(SimPosition target,double distance){if(target==null||distance<=0)return;double d=position.distanceTo(target);position=d<=distance?target:position.lerp(target,distance/d);} public void addLoot(double value){loot=Math.max(0,loot+Math.max(0,value));} public double takeLoot(double value){double taken=Math.min(loot,Math.max(0,value));loot-=taken;return taken;} public void loseStrength(int count){strength=Math.max(0,strength-Math.max(0,count));if(strength==0)active=false;} public void adjustMorale(double delta){morale=Mathx.clamp(morale+delta,0,1);} public void disband(){active=false;} public void restore(SimPosition position,int strength,double morale,double loot,boolean active){if(position==null||strength<0||!Double.isFinite(loot))throw new IllegalArgumentException("pirate restore");this.position=position;this.strength=strength;this.morale=Mathx.clamp(morale,0,1);this.loot=Math.max(0,loot);this.active=active;}
}
