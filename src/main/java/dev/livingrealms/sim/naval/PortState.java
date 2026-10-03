package dev.livingrealms.sim.naval;

import dev.livingrealms.sim.util.Mathx;
import dev.livingrealms.sim.world.SimPosition;

/** Canonical strategic port. Runtime coast detection/materialization creates these, not arbitrary inland settlements. */
public final class PortState {
    private final long id;
    private long factionId;
    private final long settlementId;
    private final SimPosition position;
    private int level;
    private double condition=1;
    private double security=.6;
    private boolean operational=true;

    public PortState(long id,long factionId,long settlementId,SimPosition position,int level){
        if(id<=0||factionId<=0||settlementId<=0||position==null||level<1||level>5)throw new IllegalArgumentException("port");
        this.id=id;this.factionId=factionId;this.settlementId=settlementId;this.position=position;this.level=level;
    }
    public long id(){return id;} public long factionId(){return factionId;} public long settlementId(){return settlementId;} public SimPosition position(){return position;} public int level(){return level;} public double condition(){return condition;} public double security(){return security;} public boolean operational(){return operational;}
    public int berthCapacity(){return level*6;} public double repairPerDay(){return .02*level*condition;} public double supplyCapacity(){return 180.0*level*condition;}
    public void setFactionId(long value){if(value<=0)throw new IllegalArgumentException("factionId");factionId=value;}
    public void upgrade(){level=Math.min(5,level+1);} public void damage(double fraction){condition=Mathx.clamp(condition-Math.max(0,fraction),0,1);if(condition<=.05)operational=false;} public void repair(double amount){condition=Mathx.clamp(condition+Math.max(0,amount),0,1);if(condition>.2)operational=true;} public void adjustSecurity(double amount){security=Mathx.clamp(security+amount,0,1);} public void setOperational(boolean value){operational=value;}
    public void restore(int restoredLevel,double restoredCondition,double restoredSecurity,boolean restoredOperational){if(restoredLevel<1||restoredLevel>5)throw new IllegalArgumentException("port level");level=restoredLevel;condition=Mathx.clamp(restoredCondition,0,1);security=Mathx.clamp(restoredSecurity,0,1);operational=restoredOperational;}
}
