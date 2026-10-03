package dev.livingrealms.sim.civilization;

import dev.livingrealms.sim.util.Mathx;
import java.util.Objects;

/** Human succession context for a faction government. GovernmentState remains the authority for actual rule. */
public final class DynastyState {
    private final long factionId,foundedDay;
    private final String houseName;
    private long rulerCitizenId,heirCitizenId,regentCitizenId;
    private double prestige=.55;
    private boolean successionCrisis;
    private long crisisSinceDay;
    private int generation=1;
    private long lastSuccessionDay=-1;
    public DynastyState(long factionId,long foundedDay,String houseName){if(factionId<=0||foundedDay<0||houseName==null||houseName.isBlank())throw new IllegalArgumentException("dynasty");this.factionId=factionId;this.foundedDay=foundedDay;this.houseName=houseName;}
    public long factionId(){return factionId;} public long foundedDay(){return foundedDay;} public String houseName(){return houseName;} public long rulerCitizenId(){return rulerCitizenId;} public long heirCitizenId(){return heirCitizenId;} public long regentCitizenId(){return regentCitizenId;} public double prestige(){return prestige;} public boolean successionCrisis(){return successionCrisis;} public long crisisSinceDay(){return crisisSinceDay;} public int generation(){return generation;} public long lastSuccessionDay(){return lastSuccessionDay;} public boolean regency(){return regentCitizenId>0;}
    public void setRulerCitizenId(long id){if(id<0)throw new IllegalArgumentException("ruler citizen");rulerCitizenId=id;} public void setHeirCitizenId(long id){if(id<0)throw new IllegalArgumentException("heir citizen");heirCitizenId=id;} public void setRegentCitizenId(long id){if(id<0||id==rulerCitizenId&&id>0)throw new IllegalArgumentException("regent citizen");regentCitizenId=id;} public void adjustPrestige(double delta){prestige=Mathx.clamp(prestige+delta,0,1);} public void startCrisis(long day){successionCrisis=true;crisisSinceDay=Math.max(0,day);} public void endCrisis(){successionCrisis=false;crisisSinceDay=0;} public void beginRegency(long citizenId){setRegentCitizenId(citizenId);} public void endRegency(){regentCitizenId=0;}
    public void recordSuccession(long day,long rulerId){if(day<0||rulerId<0)throw new IllegalArgumentException("succession");rulerCitizenId=rulerId;heirCitizenId=0;generation=Math.max(1,generation+1);lastSuccessionDay=day;}
    public void restore(long ruler,long heir,long regent,double prestige,boolean crisis,long since,int generation,long lastSuccessionDay){if(ruler<0||heir<0||regent<0||generation<1)throw new IllegalArgumentException("dynasty restore");rulerCitizenId=ruler;heirCitizenId=heir;regentCitizenId=regent;this.prestige=Mathx.clamp(prestige,0,1);successionCrisis=crisis;crisisSinceDay=Math.max(0,since);this.generation=generation;this.lastSuccessionDay=lastSuccessionDay;}
    @Override public String toString(){return Objects.toString(houseName);}
}
