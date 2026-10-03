package dev.livingrealms.sim.civilization;

import dev.livingrealms.sim.util.Mathx;
import java.util.*;

/** Culture, information and tributary state layered onto an existing canonical faction. */
public final class FactionCivilizationState {
    public static final int MAX_SPY_NETWORKS=32;
    private final long factionId;
    private final String cultureName,faithName,dialectName;
    private double culturalInfluence=.55,religiousInfluence=.42,education=.28,propaganda=.08,intelligence=.10;
    private double mercantileTradition=.5,martialTradition=.45,agrarianTradition=.5,artisticTradition=.35,religiousTolerance=.55,openness=.5;
    private double lawSeverity=.45,dueProcess=.55,refugeeAcceptance=.55;
    private long tributaryToFactionId;
    private double tributeRate;
    private final LinkedHashMap<Long,Double> spyNetworks=new LinkedHashMap<>();
    public FactionCivilizationState(long factionId,String cultureName,String faithName,String dialectName){
        if(factionId<=0||blank(cultureName)||blank(faithName)||blank(dialectName))throw new IllegalArgumentException("faction civilization state");this.factionId=factionId;this.cultureName=cultureName;this.faithName=faithName;this.dialectName=dialectName;
    }
    public long factionId(){return factionId;} public String cultureName(){return cultureName;} public String faithName(){return faithName;} public String dialectName(){return dialectName;} public double culturalInfluence(){return culturalInfluence;} public double religiousInfluence(){return religiousInfluence;} public double education(){return education;} public double propaganda(){return propaganda;} public double intelligence(){return intelligence;} public double mercantileTradition(){return mercantileTradition;} public double martialTradition(){return martialTradition;} public double agrarianTradition(){return agrarianTradition;} public double artisticTradition(){return artisticTradition;} public double religiousTolerance(){return religiousTolerance;} public double openness(){return openness;} public double lawSeverity(){return lawSeverity;} public double dueProcess(){return dueProcess;} public double refugeeAcceptance(){return refugeeAcceptance;} public long tributaryToFactionId(){return tributaryToFactionId;} public double tributeRate(){return tributeRate;} public Map<Long,Double> spyNetworks(){return Collections.unmodifiableMap(spyNetworks);}
    public void approach(double culture,double religion,double education,double propaganda,double intelligence,double rate){if(!Double.isFinite(rate)||rate<0||rate>1)throw new IllegalArgumentException("rate");culturalInfluence=lerp(culturalInfluence,culture,rate);religiousInfluence=lerp(religiousInfluence,religion,rate);this.education=lerp(this.education,education,rate);this.propaganda=lerp(this.propaganda,propaganda,rate);this.intelligence=lerp(this.intelligence,intelligence,rate);}
    public void setCultureTraits(double mercantile,double martial,double agrarian,double artistic,double tolerance,double openness){mercantileTradition=unit(mercantile);martialTradition=unit(martial);agrarianTradition=unit(agrarian);artisticTradition=unit(artistic);religiousTolerance=unit(tolerance);this.openness=unit(openness);}
    public void setLawPolicy(double severity,double dueProcess,double refugeeAcceptance){lawSeverity=unit(severity);this.dueProcess=unit(dueProcess);this.refugeeAcceptance=unit(refugeeAcceptance);}
    public void setTributary(long overlord,double rate){if(overlord<0||overlord==factionId||!Double.isFinite(rate)||rate<0||rate>.35)throw new IllegalArgumentException("tribute");tributaryToFactionId=overlord;tributeRate=overlord==0?0:rate;}
    public double spyStrength(long targetFactionId){return spyNetworks.getOrDefault(targetFactionId,0.0);}
    public void adjustSpyNetwork(long targetFactionId,double delta){if(targetFactionId<=0||targetFactionId==factionId||!Double.isFinite(delta))return;double next=Mathx.clamp(spyStrength(targetFactionId)+delta,0,1);if(next<.01){spyNetworks.remove(targetFactionId);return;}if(!spyNetworks.containsKey(targetFactionId)&&spyNetworks.size()>=MAX_SPY_NETWORKS){Long first=spyNetworks.keySet().iterator().next();spyNetworks.remove(first);}spyNetworks.put(targetFactionId,next);}
    public void restore(double culture,double religion,double education,double propaganda,double intelligence,long tributaryTo,double tributeRate,Map<Long,Double> spies){culturalInfluence=unit(culture);religiousInfluence=unit(religion);this.education=unit(education);this.propaganda=unit(propaganda);this.intelligence=unit(intelligence);setTributary(tributaryTo,tributaryTo==0?0:Mathx.clamp(tributeRate,0,.35));spyNetworks.clear();if(spies!=null)for(var e:spies.entrySet())adjustSpyNetwork(e.getKey(),unit(e.getValue()));}
    public void restoreExtended(double mercantile,double martial,double agrarian,double artistic,double tolerance,double openness,double lawSeverity,double dueProcess,double refugeeAcceptance){setCultureTraits(mercantile,martial,agrarian,artistic,tolerance,openness);setLawPolicy(lawSeverity,dueProcess,refugeeAcceptance);}
    private static boolean blank(String s){return s==null||s.isBlank();} private static double lerp(double a,double b,double r){return unit(a+(unit(b)-a)*r);} private static double unit(double v){if(!Double.isFinite(v))throw new IllegalArgumentException("civilization metric");return Mathx.clamp(v,0,1);}
}
