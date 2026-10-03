package dev.livingrealms.sim.faction;
import java.util.*;
public final class Stockpile {
    private final EnumMap<ResourceType,Double> amounts=new EnumMap<>(ResourceType.class);
    public Stockpile(){for(ResourceType r:ResourceType.values()) amounts.put(r,0d);}    
    public double get(ResourceType r){return amounts.getOrDefault(r,0d);} public void add(ResourceType r,double q){amounts.put(r,Math.max(0,get(r)+q));}
    public void set(ResourceType r,double q){amounts.put(r,Math.max(0,q));}
    public double take(ResourceType r,double q){double v=Math.min(Math.max(0,q),get(r)); add(r,-v); return v;}
    public Map<ResourceType,Double> snapshot(){return Collections.unmodifiableMap(new EnumMap<>(amounts));}
}
