package dev.livingrealms.sim.civilization;

import dev.livingrealms.sim.util.Mathx;

/** Persistent promotion of an important person/event into long-term cultural memory. */
public final class LegendRecord {
    private final long id,day,factionId,settlementId; private final String subjectKey,title,description; private double renown; private boolean monumented;
    public LegendRecord(long id,long day,long factionId,long settlementId,String subjectKey,String title,String description,double renown){if(id<=0||factionId<=0||subjectKey==null||subjectKey.isBlank()||title==null||title.isBlank()||description==null||description.isBlank())throw new IllegalArgumentException("legend");this.id=id;this.day=day;this.factionId=factionId;this.settlementId=Math.max(0,settlementId);this.subjectKey=subjectKey;this.title=title;this.description=description;this.renown=unit(renown);}
    public long id(){return id;} public long day(){return day;} public long factionId(){return factionId;} public long settlementId(){return settlementId;} public String subjectKey(){return subjectKey;} public String title(){return title;} public String description(){return description;} public double renown(){return renown;} public boolean monumented(){return monumented;} public void addRenown(double delta){if(!Double.isFinite(delta))throw new IllegalArgumentException("renown");renown=unit(renown+delta);} public void markMonumented(){monumented=true;} public void restore(double renown,boolean monumented){this.renown=unit(renown);this.monumented=monumented;}
    private static double unit(double v){if(!Double.isFinite(v))throw new IllegalArgumentException("legend renown");return Mathx.clamp(v,0,1);}
}
