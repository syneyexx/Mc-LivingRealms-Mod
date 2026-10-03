package dev.livingrealms.sim.civilization;

import dev.livingrealms.sim.util.Mathx;

/** Bounded social influence campaign; effects are gradual, never mind-control. */
public final class PropagandaCampaign {
    private final long id,factionId,settlementId,startDay,endDay;
    private final PropagandaTheme theme;
    private double intensity,reach;
    private boolean active=true;
    public PropagandaCampaign(long id,long factionId,long settlementId,long startDay,long endDay,PropagandaTheme theme,double intensity){if(id<=0||factionId<=0||settlementId<0||startDay<0||endDay<startDay||theme==null||!Double.isFinite(intensity))throw new IllegalArgumentException("propaganda campaign");this.id=id;this.factionId=factionId;this.settlementId=settlementId;this.startDay=startDay;this.endDay=endDay;this.theme=theme;this.intensity=Mathx.clamp(intensity,0,1);}
    public long id(){return id;} public long factionId(){return factionId;} public long settlementId(){return settlementId;} public long startDay(){return startDay;} public long endDay(){return endDay;} public PropagandaTheme theme(){return theme;} public double intensity(){return intensity;} public double reach(){return reach;} public boolean active(){return active;}
    public void setReach(double value){reach=Mathx.clamp(value,0,1);} public void adjustIntensity(double delta){intensity=Mathx.clamp(intensity+delta,0,1);} public void finish(){active=false;} public void restore(double intensity,double reach,boolean active){this.intensity=Mathx.clamp(intensity,0,1);this.reach=Mathx.clamp(reach,0,1);this.active=active;}
}
