package dev.livingrealms.sim.civilization;

import dev.livingrealms.sim.util.Mathx;
import java.util.*;

/** Bounded strategic human-development state attached to one canonical settlement. */
public final class SettlementCivilizationState {
    private final long settlementId;
    private long heritageFactionId;
    private double sanitation=.52;
    private double diseasePressure=.04;
    private double education=.24;
    private double waterSecurity=.62;
    private double refugeePressure;
    private double banditPressure=.04;
    private double culturalCohesion=.66;
    private double assimilation;
    private double resourcePressure=.12;
    private double quarantineStrength;
    private final EnumMap<KnowledgeDomain,Double> knowledge=new EnumMap<>(KnowledgeDomain.class);
    private long lastEpidemicDay=-10_000;
    private long lastMigrationDay=-10_000;

    public SettlementCivilizationState(long settlementId,long heritageFactionId){
        if(settlementId<=0||heritageFactionId<=0)throw new IllegalArgumentException("civilization settlement state");
        this.settlementId=settlementId;this.heritageFactionId=heritageFactionId;
        for(KnowledgeDomain domain:KnowledgeDomain.values())knowledge.put(domain,.12);
    }
    public long settlementId(){return settlementId;} public long heritageFactionId(){return heritageFactionId;}
    public double sanitation(){return sanitation;} public double diseasePressure(){return diseasePressure;} public double education(){return education;} public double waterSecurity(){return waterSecurity;} public double refugeePressure(){return refugeePressure;} public double banditPressure(){return banditPressure;} public double culturalCohesion(){return culturalCohesion;} public double assimilation(){return assimilation;} public double resourcePressure(){return resourcePressure;} public double quarantineStrength(){return quarantineStrength;} public Map<KnowledgeDomain,Double> knowledge(){return Collections.unmodifiableMap(knowledge);} public double knowledge(KnowledgeDomain domain){return knowledge.getOrDefault(domain,0.0);} public long lastEpidemicDay(){return lastEpidemicDay;} public long lastMigrationDay(){return lastMigrationDay;}
    public void setHeritageFactionId(long value){if(value<=0)throw new IllegalArgumentException("heritage faction");heritageFactionId=value;}
    public void approach(double sanitation,double disease,double education,double water,double refugees,double bandits,double cohesion,double assimilation,double resources,double rate){
        if(!Double.isFinite(rate)||rate<0||rate>1)throw new IllegalArgumentException("rate");
        this.sanitation=lerp(this.sanitation,sanitation,rate);this.diseasePressure=lerp(this.diseasePressure,disease,rate);this.education=lerp(this.education,education,rate);this.waterSecurity=lerp(this.waterSecurity,water,rate);this.refugeePressure=lerp(this.refugeePressure,refugees,rate);this.banditPressure=lerp(this.banditPressure,bandits,rate);this.culturalCohesion=lerp(this.culturalCohesion,cohesion,rate);this.assimilation=lerp(this.assimilation,assimilation,rate);this.resourcePressure=lerp(this.resourcePressure,resources,rate);
    }
    public void adjustDisease(double delta){diseasePressure=unit(diseasePressure+delta);} public void setQuarantineStrength(double value){quarantineStrength=unit(value);} public void adjustKnowledge(KnowledgeDomain domain,double delta){if(domain==null||!Double.isFinite(delta))throw new IllegalArgumentException("knowledge");knowledge.put(domain,unit(knowledge(domain)+delta));} public void setKnowledge(KnowledgeDomain domain,double value){if(domain==null)throw new IllegalArgumentException("knowledge");knowledge.put(domain,unit(value));} public void adjustRefugeePressure(double delta){refugeePressure=unit(refugeePressure+delta);} public void adjustBanditPressure(double delta){banditPressure=unit(banditPressure+delta);} public void adjustCohesion(double delta){culturalCohesion=unit(culturalCohesion+delta);} public void adjustAssimilation(double delta){assimilation=unit(assimilation+delta);} public void markEpidemic(long day){lastEpidemicDay=day;} public void markMigration(long day){lastMigrationDay=day;}
    public void restore(long heritageFactionId,double sanitation,double disease,double education,double water,double refugees,double bandits,double cohesion,double assimilation,double resources,long lastEpidemicDay,long lastMigrationDay){
        if(heritageFactionId<=0)throw new IllegalArgumentException("heritage faction");this.heritageFactionId=heritageFactionId;this.sanitation=unit(sanitation);this.diseasePressure=unit(disease);this.education=unit(education);this.waterSecurity=unit(water);this.refugeePressure=unit(refugees);this.banditPressure=unit(bandits);this.culturalCohesion=unit(cohesion);this.assimilation=unit(assimilation);this.resourcePressure=unit(resources);this.lastEpidemicDay=lastEpidemicDay;this.lastMigrationDay=lastMigrationDay;
    }
    public void restoreExtended(double quarantine,Map<KnowledgeDomain,Double> restoredKnowledge){setQuarantineStrength(quarantine);for(KnowledgeDomain d:KnowledgeDomain.values())setKnowledge(d,restoredKnowledge==null ? .12 : restoredKnowledge.getOrDefault(d,.12));}
    private static double lerp(double a,double b,double rate){return unit(a+(unit(b)-a)*rate);} private static double unit(double v){if(!Double.isFinite(v))throw new IllegalArgumentException("civilization metric");return Mathx.clamp(v,0,1);}
}
