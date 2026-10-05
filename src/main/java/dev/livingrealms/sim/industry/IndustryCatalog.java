package dev.livingrealms.sim.industry;

import dev.livingrealms.sim.faction.ResourceType;
import java.util.List;
import java.util.Map;

public final class IndustryCatalog {
    private IndustryCatalog() {}

    public static List<IndustrialProcess> defaults() {
        return List.of(
                p(IndustryKind.SAWMILL,.05,4,Map.of(ResourceType.WOOD,8.0),Map.of(ResourceType.WOOD,10.0)),
                p(IndustryKind.STONEWORKS,.08,5,Map.of(ResourceType.STONE,10.0),Map.of(ResourceType.STONE,12.0)),
                p(IndustryKind.COKEWORKS,.18,8,Map.of(ResourceType.WOOD,6.0),Map.of(ResourceType.COAL,2.5)),
                p(IndustryKind.METALWORKS,.20,12,Map.of(ResourceType.IRON,5.0,ResourceType.COAL,2.0),Map.of(ResourceType.IRON,6.5)),
                p(IndustryKind.TOOLWORKS,.22,14,Map.of(ResourceType.IRON,3.0,ResourceType.WOOD,2.0),Map.of(ResourceType.TOOLS,2.0)),
                p(IndustryKind.TEXTILE_MILL,.25,10,Map.of(ResourceType.WOOL,5.0),Map.of(ResourceType.TEXTILES,2.0)),
                p(IndustryKind.FUEL_REFINERY,.35,18,Map.of(ResourceType.COAL,5.0),Map.of(ResourceType.FUEL,3.0)),
                p(IndustryKind.MACHINERY_WORKS,.40,22,Map.of(ResourceType.IRON,5.0,ResourceType.TOOLS,2.0),Map.of(ResourceType.MACHINERY,2.0)),
                p(IndustryKind.MUNITIONS,.45,20,Map.of(ResourceType.IRON,3.0,ResourceType.COAL,2.0),Map.of(ResourceType.AMMUNITION,4.0))
        );
    }

    private static IndustrialProcess p(IndustryKind k,double t,double stress,Map<ResourceType,Double> in,Map<ResourceType,Double> out){
        return new IndustrialProcess(k,t,stress,in,out);
    }
}
