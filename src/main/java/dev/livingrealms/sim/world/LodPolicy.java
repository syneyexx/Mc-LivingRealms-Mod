package dev.livingrealms.sim.world;
public record LodPolicy(double physicalRadiusBlocks,double regionalRadiusBlocks){
    public LodPolicy{if(physicalRadiusBlocks<=0||regionalRadiusBlocks<=physicalRadiusBlocks)throw new IllegalArgumentException();}
    public SimulationLod forDistance(double d){return d<=physicalRadiusBlocks?SimulationLod.PHYSICAL:d<=regionalRadiusBlocks?SimulationLod.REGIONAL:SimulationLod.ABSTRACT;}
    public static LodPolicy defaultPolicy(){return new LodPolicy(320,2048);}
}
