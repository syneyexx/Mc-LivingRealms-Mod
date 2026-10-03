package dev.livingrealms.sim.civilization;

import dev.livingrealms.sim.util.Mathx;

/** Persistent espionage mission derived from faction spy networks. */
public final class IntelligenceOperation {
    private final long id,sourceFactionId,targetFactionId,startDay,agentCitizenId;
    private final IntelligenceOperationType type;
    private double progress,secrecy=.72,quality;
    private boolean discovered,active=true;
    public IntelligenceOperation(long id,long sourceFactionId,long targetFactionId,long startDay,long agentCitizenId,IntelligenceOperationType type){if(id<=0||sourceFactionId<=0||targetFactionId<=0||sourceFactionId==targetFactionId||startDay<0||agentCitizenId<0||type==null)throw new IllegalArgumentException("intelligence operation");this.id=id;this.sourceFactionId=sourceFactionId;this.targetFactionId=targetFactionId;this.startDay=startDay;this.agentCitizenId=agentCitizenId;this.type=type;}
    public long id(){return id;} public long sourceFactionId(){return sourceFactionId;} public long targetFactionId(){return targetFactionId;} public long startDay(){return startDay;} public long agentCitizenId(){return agentCitizenId;} public IntelligenceOperationType type(){return type;} public double progress(){return progress;} public double secrecy(){return secrecy;} public double quality(){return quality;} public boolean discovered(){return discovered;} public boolean active(){return active;}
    public void advance(double amount,double qualityGain){if(!active)return;progress=Mathx.clamp(progress+Math.max(0,amount),0,1);quality=Mathx.clamp(quality+Math.max(0,qualityGain)*(1-quality),0,1);} public void erodeSecrecy(double amount){secrecy=Mathx.clamp(secrecy-Math.max(0,amount),0,1);} public void discover(){discovered=true;} public void finish(){active=false;progress=1;} public void fail(){active=false;} public void restore(double progress,double secrecy,double quality,boolean discovered,boolean active){this.progress=Mathx.clamp(progress,0,1);this.secrecy=Mathx.clamp(secrecy,0,1);this.quality=Mathx.clamp(quality,0,1);this.discovered=discovered;this.active=active;}
}
