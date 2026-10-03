package dev.livingrealms.sim.civilization;

import dev.livingrealms.sim.util.Mathx;

/** Persistent outbreak record; settlement disease pressure is the aggregate input/output metric. */
public final class EpidemicRecord {
    private final long id,settlementId,startDay;
    private final String diseaseKey;
    private double severity,infectedFraction;
    private int cumulativeDeaths;
    private long lastUpdateDay;
    private boolean active=true;
    public EpidemicRecord(long id,long settlementId,long startDay,String diseaseKey,double severity,double infectedFraction){if(id<=0||settlementId<=0||startDay<0||diseaseKey==null||diseaseKey.isBlank())throw new IllegalArgumentException("epidemic");this.id=id;this.settlementId=settlementId;this.startDay=startDay;this.diseaseKey=diseaseKey;this.severity=Mathx.clamp(severity,0,1);this.infectedFraction=Mathx.clamp(infectedFraction,0,1);this.lastUpdateDay=startDay;}
    public long id(){return id;} public long settlementId(){return settlementId;} public long startDay(){return startDay;} public String diseaseKey(){return diseaseKey;} public double severity(){return severity;} public double infectedFraction(){return infectedFraction;} public int cumulativeDeaths(){return cumulativeDeaths;} public long lastUpdateDay(){return lastUpdateDay;} public boolean active(){return active;}
    public void update(long day,double severityTarget,double infectedTarget,int deaths){if(day<lastUpdateDay)throw new IllegalArgumentException("epidemic day");severity=Mathx.clamp(severity+(Mathx.clamp(severityTarget,0,1)-severity)*.18,0,1);infectedFraction=Mathx.clamp(infectedFraction+(Mathx.clamp(infectedTarget,0,1)-infectedFraction)*.22,0,1);cumulativeDeaths=Math.max(0,cumulativeDeaths+Math.max(0,deaths));lastUpdateDay=day;if(severity<.08&&infectedFraction<.02)active=false;} public void end(long day){lastUpdateDay=Math.max(lastUpdateDay,day);active=false;} public void restore(double severity,double infected,int deaths,long lastUpdate,boolean active){this.severity=Mathx.clamp(severity,0,1);this.infectedFraction=Mathx.clamp(infected,0,1);this.cumulativeDeaths=Math.max(0,deaths);this.lastUpdateDay=Math.max(startDay,lastUpdate);this.active=active;}
}
