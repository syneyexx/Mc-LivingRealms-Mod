package dev.livingrealms.sim.industry;

import dev.livingrealms.sim.util.Mathx;
import java.util.Objects;

/** Persistent strategic factory/workshop; Create blocks are only a physical projection. */
public final class IndustrialSite {
    private final long id;
    private long factionId;
    private final long settlementId;
    private final IndustryKind kind;
    private int level;
    private double condition=1.0;
    private IndustrialSiteStatus status=IndustrialSiteStatus.ACTIVE;
    private int starvedDays;
    private int downtimeDays;
    private int lastCycles;
    private double lastUtilization;

    public IndustrialSite(long id,long factionId,long settlementId,IndustryKind kind,int level){
        if(id<=0||factionId<=0||settlementId<=0)throw new IllegalArgumentException("industrial site identity");
        this.id=id;this.factionId=factionId;this.settlementId=settlementId;this.kind=Objects.requireNonNull(kind,"kind");this.level=clampLevel(level);
    }
    public long id(){return id;} public long factionId(){return factionId;} public long settlementId(){return settlementId;} public IndustryKind kind(){return kind;}
    public int level(){return level;} public double condition(){return condition;} public IndustrialSiteStatus status(){return status;} public int starvedDays(){return starvedDays;}
    public int downtimeDays(){return downtimeDays;} public int lastCycles(){return lastCycles;} public double lastUtilization(){return lastUtilization;}
    public boolean operational(){return status!=IndustrialSiteStatus.OFFLINE&&downtimeDays<=0&&condition>.08;}
    public void transferTo(long newFactionId){if(newFactionId<=0)throw new IllegalArgumentException("faction");factionId=newFactionId;}
    public void upgradeTo(int newLevel){level=Math.max(level,clampLevel(newLevel));}
    public void damage(double amount,int downtime){if(!Double.isFinite(amount)||amount<0)throw new IllegalArgumentException("amount");condition=Mathx.clamp(condition-amount,0,1);downtimeDays=Math.max(downtimeDays,Math.max(0,downtime));status=condition<=.08?IndustrialSiteStatus.OFFLINE:IndustrialSiteStatus.DAMAGED;}
    public void repair(double amount){if(!Double.isFinite(amount)||amount<0)throw new IllegalArgumentException("amount");condition=Mathx.clamp(condition+amount,0,1);if(condition>.12&&downtimeDays<=0)status=IndustrialSiteStatus.REPAIRING;}
    public void tickDowntime(){if(downtimeDays>0)downtimeDays--;}
    public void setDayResult(IndustrialSiteStatus newStatus,int cycles,double utilization){status=Objects.requireNonNull(newStatus,"status");lastCycles=Math.max(0,cycles);lastUtilization=Mathx.clamp(utilization,0,1);if(newStatus==IndustrialSiteStatus.STARVED)starvedDays++;else starvedDays=0;}
    public void restore(double condition,IndustrialSiteStatus status,int starvedDays,int downtimeDays,int lastCycles,double lastUtilization){this.condition=Mathx.clamp(condition,0,1);this.status=Objects.requireNonNull(status,"status");this.starvedDays=Math.max(0,starvedDays);this.downtimeDays=Math.max(0,downtimeDays);this.lastCycles=Math.max(0,lastCycles);this.lastUtilization=Mathx.clamp(lastUtilization,0,1);}
    private static int clampLevel(int value){return Math.max(1,Math.min(5,value));}
}
