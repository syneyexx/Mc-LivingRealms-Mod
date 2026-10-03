package dev.livingrealms.sim.industry;

import dev.livingrealms.sim.faction.ResourceType;
import java.util.EnumMap;
import java.util.Map;

public record IndustryReport(double stressCapacity,double stressUsed,Map<ResourceType,Double> produced,int cycles) {
    public IndustryReport {
        produced=Map.copyOf(new EnumMap<>(produced));
    }
    public static IndustryReport empty(double capacity){return new IndustryReport(capacity,0,new EnumMap<>(ResourceType.class),0);}
}
