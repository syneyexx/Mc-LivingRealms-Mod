package dev.livingrealms.sim.civilization;

import dev.livingrealms.sim.util.Mathx;

/** Persistent bounded civic gathering tied to one real settlement. */
public final class CivicEvent {
    private final long id,factionId,settlementId,startDay,endDay;
    private final CivicEventType type;
    private final String title;
    private double attendance,intensity;
    private boolean active=true;
    public CivicEvent(long id,long factionId,long settlementId,long startDay,long endDay,CivicEventType type,String title,double intensity){
        if(id<=0||factionId<=0||settlementId<=0||startDay<0||endDay<startDay||type==null||title==null||title.isBlank()||!Double.isFinite(intensity))throw new IllegalArgumentException("civic event");
        this.id=id;this.factionId=factionId;this.settlementId=settlementId;this.startDay=startDay;this.endDay=endDay;this.type=type;this.title=title;this.intensity=Mathx.clamp(intensity,0,1);
    }
    public long id(){return id;} public long factionId(){return factionId;} public long settlementId(){return settlementId;} public long startDay(){return startDay;} public long endDay(){return endDay;} public CivicEventType type(){return type;} public String title(){return title;} public double attendance(){return attendance;} public double intensity(){return intensity;} public boolean active(){return active;}
    public void setAttendance(double value){attendance=Mathx.clamp(value,0,1);} public void adjustIntensity(double delta){intensity=Mathx.clamp(intensity+delta,0,1);} public void finish(){active=false;} public void restore(double attendance,double intensity,boolean active){this.attendance=Mathx.clamp(attendance,0,1);this.intensity=Mathx.clamp(intensity,0,1);this.active=active;}
}
